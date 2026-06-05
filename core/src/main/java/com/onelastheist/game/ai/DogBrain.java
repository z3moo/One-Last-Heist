package com.onelastheist.game.ai;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.onelastheist.game.config.BalanceConfig;
import com.onelastheist.game.entity.npc.Dog;
import com.onelastheist.game.entity.npc.NpcState;
import com.onelastheist.game.entity.player.Player;
import com.onelastheist.game.environment.DroppedMeat;
import com.onelastheist.game.world.CollisionMap;

import java.util.ArrayList;
import java.util.List;

/**
 * Per-frame AI for the guard {@link Dog}. Owns no mutable state of its own
 * beyond the path waypoints, sense timers, and stuck detector — every behavior
 * change mutates the {@link Dog} directly so there's exactly one place the
 * dog's authoritative state lives.
 *
 * <p>State cycle and transitions:
 * <pre>
 *   SLEEPING -timer-> WANDERING -timer-> SLEEPING (in place — no return home)
 *
 *   awake + audible player -> INVESTIGATING_NOISE
 *     - reached player: BITE (player.catchPlayer()) -> SLEEPING in place
 *     - silence for {@link BalanceConfig#dogNoiseLostSeconds}s -> WANDERING
 *       briefly (5-10s) then SLEEPING in place
 *
 *   awake + meat-in-range -> INVESTIGATING_MEAT
 *     - ate drugged: DRUGGED -timer-> WANDERING (briefly), then SLEEPING in place
 *     - ate plain  : WANDERING (briefly), then SLEEPING in place
 * </pre>
 *
 * <p>The dog never auto-returns to its spawn. It sleeps wherever the wander
 * timer happens to expire — natural behaviour for a guard dog that's done
 * sweeping, and avoids the "predictable circuit back to bed" exploit.
 *
 * <p>Wander targets are sampled anywhere inside {@link #wanderBounds} (with a
 * minimum distance from the dog's current position) so the dog drifts across
 * the whole house instead of orbiting one spot.
 *
 * <p>Hearing: a dog hears the player when the player is within
 * {@link BalanceConfig#dogHearingRange} <em>and</em> the player is moving
 * without crouch. Crouch makes the player silent; standing still also silent.
 * If the player goes silent (crouches or stops) for
 * {@link BalanceConfig#dogNoiseLostSeconds} seconds the dog gives up the chase.
 *
 * <p>Smell: closest unconsumed meat within {@link BalanceConfig#dogMeatSenseRange}.
 * Meat steals attention from a noise chase — drop meat at the dog's feet to
 * break a pursuit.
 *
 * <p>Pathfinding uses {@link Pathfinder} (BFS over a per-tile walkability
 * grid). Re-planned periodically while chasing a moving target.
 */
public class DogBrain {
    /** Squared minimum movement (world units²) per {@link #STUCK_WINDOW_SECONDS} window before the dog is declared stuck. */
    private static final float STUCK_MIN_DELTA_SQ = 4f * 4f;
    /** How long the dog can move less than {@link #STUCK_MIN_DELTA_SQ} before its target is reset. */
    private static final float STUCK_WINDOW_SECONDS = 0.6f;
    /** Distance (world units) within which a target counts as "reached". */
    private static final float REACH_THRESHOLD = 16f;
    /** Distance to a path waypoint that counts as "reached" — slightly larger than REACH_THRESHOLD so the dog flows through corners. */
    private static final float WAYPOINT_REACH_THRESHOLD = 24f;
    /** When chasing a moving target, recompute the path this often (seconds). */
    private static final float PATH_REPLAN_SECONDS = 0.6f;
    /** Minimum distance (world units) a wander target must sit from the dog's current position. ~3 tiles — keeps wandering visibly progressive. */
    private static final float WANDER_MIN_DISTANCE = 144f;
    /** Maximum {@code INVESTIGATING_NOISE} budget (seconds). The dog gives up if the chase drags on. */
    private static final float INVESTIGATING_NOISE_TIMEOUT = 12f;
    /** Maximum {@code INVESTIGATING_MEAT} budget (seconds). Same rationale. */
    private static final float INVESTIGATING_MEAT_TIMEOUT = 30f;

    private final Dog dog;
    private final BalanceConfig balance;
    private final Rectangle wanderBounds;
    private final CollisionMap collisionMap;
    private final Pathfinder pathfinder;
    /** Position last sampled for the stuck check. */
    private float stuckRefX;
    private float stuckRefY;
    private float stuckTimer;
    /** Current path's remaining waypoints (world coords). Empty when the dog has no plan. */
    private final List<float[]> waypoints = new ArrayList<>();
    /** Last goal we planned a path to, in world coords. */
    private float lastPlanGoalX;
    private float lastPlanGoalY;
    private float replanTimer;
    /** Time (s) since the dog last heard the player. Resets to 0 each frame the player is audible; counts up otherwise. Drives the noise-lost give-up. */
    private float silenceTimer;

    public DogBrain(Dog dog, BalanceConfig balance, Rectangle wanderBounds, CollisionMap collisionMap) {
        this.dog = dog;
        this.balance = balance;
        this.wanderBounds = wanderBounds;
        this.collisionMap = collisionMap;
        this.pathfinder = collisionMap == null
            ? null
            : new Pathfinder(collisionMap,
                dog.getHitboxOffsetX(), dog.getHitboxOffsetY(),
                dog.getHitboxWidth(), dog.getHitboxHeight());
        this.stuckRefX = dog.getX();
        this.stuckRefY = dog.getY();
        // Home is recorded but only used as a fallback target. The dog does
        // not actively return to it — it sleeps wherever wandering ends.
        dog.setHome(dog.getX(), dog.getY());
        dog.enterState(NpcState.SLEEPING, randomBetween(balance.dogSleepMinSeconds, balance.dogSleepMaxSeconds));
    }

    /**
     * Per-frame update. Called by {@link com.onelastheist.game.world.GameWorld}
     * only when the interior is the active map (dog doesn't exist outside).
     */
    public void update(float delta, Player player, List<DroppedMeat> meats) {
        dog.tickStateTimer(delta);
        replanTimer -= delta;
        updateStuckDetector(delta);

        // Noise sensing first: if the player is making footsteps, the dog must
        // target the player immediately instead of continuing a wander target.
        boolean heardPlayer = false;
        if (dog.canBeDisturbed() && playerIsAudible(player)) {
            heardPlayer = true;
            silenceTimer = 0f;
            float px = dogOriginAtPlayerCenterX(player);
            float py = dogOriginAtPlayerCenterY(player);
            if (dog.getState() != NpcState.INVESTIGATING_NOISE) {
                dog.enterState(NpcState.INVESTIGATING_NOISE, INVESTIGATING_NOISE_TIMEOUT);
                planPathTo(px, py);
            } else if (replanTimer <= 0f) {
                planPathTo(px, py);
            }
        }

        // Meat only steals attention once the player has gone quiet. That keeps
        // footsteps reliable while still letting dropped meat distract a dog when
        // the player crouches/stops after baiting it.
        DroppedMeat meatTarget = heardPlayer ? null : senseMeat(meats);
        if (meatTarget != null && dog.canBeDisturbed() && dog.getState() != NpcState.INVESTIGATING_MEAT) {
            dog.enterState(NpcState.INVESTIGATING_MEAT, INVESTIGATING_MEAT_TIMEOUT);
            planPathTo(dogOriginAtPointX(meatTarget.getX()), dogOriginAtPointY(meatTarget.getY()));
        }
        if (!heardPlayer) silenceTimer += delta;

        // Lost-noise: dog was chasing, player went silent (crouch/stop) long
        // enough → drift into a short wander, then sleep wherever it ends up.
        if (dog.getState() == NpcState.INVESTIGATING_NOISE
            && silenceTimer >= balance.dogNoiseLostSeconds) {
            dog.enterState(NpcState.WANDERING,
                randomBetween(balance.dogPostInvestigateWanderMinSeconds, balance.dogPostInvestigateWanderMaxSeconds));
            waypoints.clear();
        }

        runStateBehavior(delta, meatTarget, player);

        if (dog.getStateTimer() <= 0f) {
            transitionFromTimerExpiry();
        }
    }

    /**
     * Reset target if the dog hasn't actually moved over a short rolling
     * window. Catches paths the BFS approved but axis-separated movement
     * can't thread (corner-clip, doorway not lined up).
     */
    private void updateStuckDetector(float delta) {
        stuckTimer += delta;
        if (stuckTimer < STUCK_WINDOW_SECONDS) return;
        float dx = dog.getX() - stuckRefX;
        float dy = dog.getY() - stuckRefY;
        if (dx * dx + dy * dy < STUCK_MIN_DELTA_SQ && (dog.hasTarget() || hasPath())) {
            // Drop the plan; the active state will pick a new target next tick.
            waypoints.clear();
            dog.clearTarget();
        }
        stuckRefX = dog.getX();
        stuckRefY = dog.getY();
        stuckTimer = 0f;
    }

    private void runStateBehavior(float delta, DroppedMeat meatTarget, Player player) {
        switch (dog.getState()) {
            case WANDERING:
                if (!hasPath()) pickWanderTarget();
                stepTowardTarget(delta);
                if (reachedFinalGoal()) pickWanderTarget();
                break;
            case INVESTIGATING_NOISE:
                stepTowardTarget(delta);
                // Bite: contact range with player ends the chase. The dog
                // catches the player and lies down on the spot — what
                // happens next is for higher-level systems to decide.
                if (distanceToPlayerHitbox(player) <= balance.dogBiteRange) {
                    player.catchPlayer();
                    dog.enterState(NpcState.SLEEPING,
                        randomBetween(balance.dogSleepMinSeconds, balance.dogSleepMaxSeconds));
                    waypoints.clear();
                    break;
                }
                if (reachedFinalGoal()) {
                    // Got to the noise but nobody there. Drift into a short
                    // wander, then sleep wherever it ends.
                    dog.enterState(NpcState.WANDERING,
                        randomBetween(balance.dogPostInvestigateWanderMinSeconds, balance.dogPostInvestigateWanderMaxSeconds));
                    waypoints.clear();
                }
                break;
            case INVESTIGATING_MEAT:
                if (meatTarget == null) {
                    // Meat gone — short wander then sleep.
                    dog.enterState(NpcState.WANDERING,
                        randomBetween(balance.dogPostInvestigateWanderMinSeconds, balance.dogPostInvestigateWanderMaxSeconds));
                    waypoints.clear();
                    break;
                }
                float meatGoalX = dogOriginAtPointX(meatTarget.getX());
                float meatGoalY = dogOriginAtPointY(meatTarget.getY());
                if (replanTimer <= 0f
                    && (Math.abs(lastPlanGoalX - meatGoalX) > 1f
                        || Math.abs(lastPlanGoalY - meatGoalY) > 1f)) {
                    planPathTo(meatGoalX, meatGoalY);
                }
                stepTowardTarget(delta);
                if (distanceFromDogCenterTo(meatTarget.getX(), meatTarget.getY()) <= balance.dogMeatEatRange) {
                    meatTarget.consume();
                    if (meatTarget.isDrugged()) {
                        dog.enterState(NpcState.DRUGGED,
                            randomBetween(balance.dogDruggedMinSeconds, balance.dogDruggedMaxSeconds));
                    } else {
                        dog.enterState(NpcState.WANDERING,
                            randomBetween(balance.dogPostInvestigateWanderMinSeconds, balance.dogPostInvestigateWanderMaxSeconds));
                    }
                    waypoints.clear();
                }
                break;
            case SLEEPING:
            case DRUGGED:
            default:
                applyMove(0f, 0f, delta);
                break;
        }
    }

    private void transitionFromTimerExpiry() {
        switch (dog.getState()) {
            case WANDERING:
                // Done wandering — sleep where you stand.
                dog.enterState(NpcState.SLEEPING,
                    randomBetween(balance.dogSleepMinSeconds, balance.dogSleepMaxSeconds));
                waypoints.clear();
                break;
            case SLEEPING:
                dog.enterState(NpcState.WANDERING,
                    randomBetween(balance.dogWanderMinSeconds, balance.dogWanderMaxSeconds));
                break;
            case DRUGGED:
                // Wake up groggy — short wander then nap again.
                dog.enterState(NpcState.WANDERING,
                    randomBetween(balance.dogPostInvestigateWanderMinSeconds, balance.dogPostInvestigateWanderMaxSeconds));
                waypoints.clear();
                break;
            case INVESTIGATING_NOISE:
            case INVESTIGATING_MEAT:
                // Searched too long without resolving — short wander, then sleep.
                dog.enterState(NpcState.WANDERING,
                    randomBetween(balance.dogPostInvestigateWanderMinSeconds, balance.dogPostInvestigateWanderMaxSeconds));
                waypoints.clear();
                break;
            default:
                break;
        }
    }

    private boolean playerIsAudible(Player player) {
        if (!player.isMakingNoise()) return false;
        return distanceToPlayerHitbox(player) <= balance.dogHearingRange;
    }

    private float distanceToPlayerHitbox(Player player) {
        float px = player.getX() + player.getHitboxOffsetX() + player.getHitboxWidth() / 2f;
        float py = player.getY() + player.getHitboxOffsetY() + player.getHitboxHeight() / 2f;
        return distanceFromDogCenterTo(px, py);
    }

    /** Closest unconsumed meat within smell range, or null. */
    private DroppedMeat senseMeat(List<DroppedMeat> meats) {
        if (meats.isEmpty()) return null;
        DroppedMeat best = null;
        float bestDistSq = balance.dogMeatSenseRange * balance.dogMeatSenseRange;
        for (int i = 0, n = meats.size(); i < n; i++) {
            DroppedMeat m = meats.get(i);
            if (m.isConsumed()) continue;
            float dx = m.getX() - dogCenterX();
            float dy = m.getY() - dogCenterY();
            float distSq = dx * dx + dy * dy;
            if (distSq <= bestDistSq) {
                best = m;
                bestDistSq = distSq;
            }
        }
        return best;
    }

    /**
     * Pick a wander target anywhere inside {@link #wanderBounds} that's at
     * least {@link #WANDER_MIN_DISTANCE} from the dog's current position.
     * Sampling across the whole authored region — instead of around home —
     * is what makes the dog drift through the house. The pathfinder rejects
     * targets behind walls; up to 16 retries before giving up for this frame.
     */
    private void pickWanderTarget() {
        if (pathfinder != null) {
            for (int i = 0; i < 48; i++) {
                float[] target = pathfinder.randomWalkablePoint(
                    wanderBounds, dog.getX(), dog.getY(), WANDER_MIN_DISTANCE, 4);
                if (target != null && planPathTo(target[0], target[1])) return;
            }
            // No reachable tile-center target this tick — sit out, retry next frame.
            return;
        }

        float minDistSq = WANDER_MIN_DISTANCE * WANDER_MIN_DISTANCE;
        for (int i = 0; i < 16; i++) {
            float x = MathUtils.random(wanderBounds.x, wanderBounds.x + wanderBounds.width);
            float y = MathUtils.random(wanderBounds.y, wanderBounds.y + wanderBounds.height);
            float dx = x - dog.getX();
            float dy = y - dog.getY();
            if (dx * dx + dy * dy < minDistSq) continue;
            if (planPathTo(x, y)) return;
        }
    }

    /**
     * Plan a route from the dog's current position to the given world goal.
     * Stores the waypoints and updates the dog's authoritative target.
     *
     * @return true if a path was found; false otherwise.
     */
    private boolean planPathTo(float gx, float gy) {
        replanTimer = PATH_REPLAN_SECONDS;
        lastPlanGoalX = gx;
        lastPlanGoalY = gy;
        waypoints.clear();
        if (pathfinder == null) {
            waypoints.add(new float[] {gx, gy});
            dog.setTarget(gx, gy);
            return true;
        }
        List<float[]> path = pathfinder.findPath(dog.getX(), dog.getY(), gx, gy);
        if (path.isEmpty()) {
            dog.clearTarget();
            return false;
        }
        waypoints.addAll(path);
        dog.setTarget(gx, gy);
        return true;
    }

    private boolean hasPath() { return !waypoints.isEmpty(); }

    /** True once the dog has consumed every waypoint, i.e. arrived at the BFS goal. */
    private boolean reachedFinalGoal() {
        if (!waypoints.isEmpty()) return false;
        if (!dog.hasTarget()) return true;
        return distanceTo(dog.getTargetX(), dog.getTargetY()) <= REACH_THRESHOLD;
    }

    /**
     * Walk one step toward the next waypoint. When that waypoint is reached,
     * pop it and continue toward the next. Reaching the last clears the path.
     */
    private void stepTowardTarget(float delta) {
        if (waypoints.isEmpty()) {
            applyMove(0f, 0f, delta);
            return;
        }
        float[] wp = waypoints.get(0);
        float dx = wp[0] - dog.getX();
        float dy = wp[1] - dog.getY();
        if (dx * dx + dy * dy <= WAYPOINT_REACH_THRESHOLD * WAYPOINT_REACH_THRESHOLD) {
            waypoints.remove(0);
            if (waypoints.isEmpty()) {
                applyMove(0f, 0f, delta);
                return;
            }
            wp = waypoints.get(0);
            dx = wp[0] - dog.getX();
            dy = wp[1] - dog.getY();
        }
        if (Math.abs(dx) < 1f) dx = 0f;
        if (Math.abs(dy) < 1f) dy = 0f;
        // Slow patrol when wandering, fast lunge when chasing or smelling meat.
        // Visual tell that the dog has noticed the player — the speed bump alone
        // is enough cue without an exclamation mark.
        dog.setSpeed(speedForState());
        applyMove(dx, dy, delta);
    }

    private float distanceTo(float x, float y) {
        float dx = x - dog.getX();
        float dy = y - dog.getY();
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    private float distanceFromDogCenterTo(float x, float y) {
        float dx = x - dogCenterX();
        float dy = y - dogCenterY();
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    private float dogCenterX() {
        return dog.getX() + dog.getHitboxOffsetX() + dog.getHitboxWidth() / 2f;
    }

    private float dogCenterY() {
        return dog.getY() + dog.getHitboxOffsetY() + dog.getHitboxHeight() / 2f;
    }

    private float dogOriginAtPlayerCenterX(Player player) {
        return dogOriginAtPointX(player.getX() + player.getHitboxOffsetX() + player.getHitboxWidth() / 2f);
    }

    private float dogOriginAtPlayerCenterY(Player player) {
        return dogOriginAtPointY(player.getY() + player.getHitboxOffsetY() + player.getHitboxHeight() / 2f);
    }

    private float dogOriginAtPointX(float centerX) {
        return centerX - dog.getHitboxOffsetX() - dog.getHitboxWidth() / 2f;
    }

    private float dogOriginAtPointY(float centerY) {
        return centerY - dog.getHitboxOffsetY() - dog.getHitboxHeight() / 2f;
    }

    /**
     * Return the speed (world units / second) the dog should move at right
     * now. Wandering and post-investigate drift are slow patrol; reacting to
     * a noise or smelling meat is the fast lunge that should outpace an
     * upright player. Drugged / sleeping returns the chase speed defensively
     * but the brain never asks those states to move.
     */
    private float speedForState() {
        switch (dog.getState()) {
            case WANDERING:
                return balance.dogWanderSpeed;
            case INVESTIGATING_NOISE:
            case INVESTIGATING_MEAT:
                return balance.dogChaseSpeed;
            default:
                return balance.dogChaseSpeed;
        }
    }

    private void applyMove(float dx, float dy, float delta) {
        if (collisionMap != null) {
            dog.tryMove(dx, dy, delta, collisionMap);
        } else {
            dog.move(dx, dy, delta);
        }
    }

    private float randomBetween(float lo, float hi) {
        return MathUtils.random(lo, hi);
    }
}
