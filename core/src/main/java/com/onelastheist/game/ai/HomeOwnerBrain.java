package com.onelastheist.game.ai;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.onelastheist.game.config.BalanceConfig;
import com.onelastheist.game.entity.npc.HomeOwner;
import com.onelastheist.game.entity.npc.NpcState;
import com.onelastheist.game.entity.player.Player;
import com.onelastheist.game.world.CollisionMap;

import java.util.ArrayList;
import java.util.List;

/**
 * Per-frame AI for the {@link HomeOwner} ("the neighbour"). Activates when the
 * world clock drops below {@link BalanceConfig#homeOwnerArrivalSecondsRemaining}
 * and the player has at least once entered the main house. From there the
 * homeowner walks to the front door (still on the exterior map), is
 * teleported into the interior by the world layer when in range, and from
 * then on hunts the player.
 *
 * <p>State sequence:
 * <pre>
 *   WAITING      -- clock above threshold, brain inert
 *   APPROACHING  -- on exterior, BFSing toward the door
 *   HUNTING      -- on interior, wandering tile-center targets across the house
 *                   while running detection (vision cone + hearing)
 *   CHASING      -- detection succeeded; closing on player.
 * </pre>
 *
 * <p>Detection is the key difference from {@link DogBrain}:
 * <ul>
 *   <li><b>Vision cone</b> — player center must be within
 *       {@link BalanceConfig#homeOwnerVisionRange} world units AND inside the
 *       homeowner's facing-direction half-cone of
 *       {@link BalanceConfig#homeOwnerVisionAngleDegrees}/2 degrees AND a
 *       step-sampled raycast from homeowner to player must clear all
 *       solids in the active collision map. Furniture and walls block sight.</li>
 *   <li><b>Hearing</b> — same {@code isMakingNoise} contract as the dog,
 *       within {@link BalanceConfig#homeOwnerHearingRange}.</li>
 * </ul>
 *
 * <p>The homeowner is intentionally distraction-proof: meat does not affect
 * him, and there is no sleep / drugged state. Once chasing, he commits
 * until he loses contact for {@link BalanceConfig#homeOwnerNoiseLostSeconds}
 * or grabs the player.
 */
public class HomeOwnerBrain {
    /** How close (world units) to the front door triggers the interior transition. */
    private static final float DOOR_ENTER_RADIUS = 64f;
    /** BFS replan period while chasing. Same shape as DogBrain. */
    private static final float PATH_REPLAN_SECONDS = 0.6f;
    /** Distance at which a path waypoint is considered reached. */
    private static final float WAYPOINT_REACH_THRESHOLD = 24f;
    /** Distance at which a goal target is considered reached. */
    private static final float REACH_THRESHOLD = 16f;
    /** Wander targets must sit at least this far from the homeowner so each pick is visibly progressive. */
    private static final float WANDER_MIN_DISTANCE = 144f;
    /** Stuck-detector threshold (world units²/s). Same as DogBrain. */
    private static final float STUCK_MIN_DELTA_SQ = 4f * 4f;
    private static final float STUCK_WINDOW_SECONDS = 0.6f;
    /**
     * Consecutive stuck events that trigger an escalation. Below this we
     * just clear the plan and let HUNTING re-pick. At/above this we force
     * a path back to the interior spawn (known walkable). One more after
     * that and we teleport — visual cost, but it guarantees the homeowner
     * never burns the whole heist standing in a doorway.
     */
    private static final int STUCK_ESCALATE_THRESHOLD = 3;
    private static final int STUCK_TELEPORT_THRESHOLD = 6;
    /** Clears the consecutive-stuck counter when the homeowner has actually moved. */
    private static final float STUCK_RESET_DELTA_SQ = 64f * 64f;
    /**
     * Hard ceiling on the approach phase. If the homeowner is still
     * APPROACHING after this many seconds — usually because the path-find
     * grid disconnects his spawn from the door cell, or both fallbacks
     * have him wedged on a fence — we force the interior transition so
     * the player still sees him appear inside the house. Cosmetic outside
     * walk is nice but not essential to the gameplay loop.
     */
    private static final float APPROACH_TIMEOUT_SECONDS = 6f;
    /** How many steps to sample along a vision ray for the line-of-sight test. Each step is a tile probe. */
    private static final int LOS_SAMPLE_STEPS = 16;
    /** Tiny probe size for the LoS sampler — small enough to thread between sub-tile collision rects. */
    private static final float LOS_PROBE_SIZE = 4f;

    /** High-level brain state. */
    public enum Phase { WAITING, APPROACHING, HUNTING, CHASING }

    private final HomeOwner homeOwner;
    private final BalanceConfig balance;
    /** Bounds the homeowner samples for wander targets while inside. Maps roughly to the playable interior. */
    private Rectangle interiorWanderBounds;
    /** World coords of the front door the homeowner walks to during approach. */
    private float doorTargetX;
    private float doorTargetY;
    /** World-space spawn the world layer should teleport the homeowner to on entry. */
    private float interiorSpawnX;
    private float interiorSpawnY;

    private Phase phase = Phase.WAITING;
    private CollisionMap activeCollisionMap;
    private Pathfinder pathfinder;
    private final List<float[]> waypoints = new ArrayList<>();
    private float lastPlanGoalX;
    private float lastPlanGoalY;
    private float replanTimer;
    private float silenceTimer;
    private float stuckRefX;
    private float stuckRefY;
    private float stuckTimer;
    /** Consecutive stuck events without intervening movement. Drives the escalation in updateStuckDetector. */
    private int stuckCount;
    /** Reference position used to detect "actually moved a lot" so we can clear stuckCount. */
    private float stuckClearRefX;
    private float stuckClearRefY;
    /** Seconds spent in APPROACHING. Capped by {@link #APPROACH_TIMEOUT_SECONDS}. */
    private float approachTimer;
    private Runnable onCatch = () -> {};
    /**
     * Hook invoked when the approach phase reaches the door. The world
     * layer wires this to the interior-teleport so the brain doesn't need
     * to know about map swaps directly.
     */
    private Runnable onEnterInterior = () -> {};

    public HomeOwnerBrain(HomeOwner homeOwner, BalanceConfig balance) {
        this.homeOwner = homeOwner;
        this.balance = balance;
    }

    public Phase getPhase() { return phase; }

    /** Set the world-space target for the door the homeowner approaches. */
    public void setApproachTarget(float doorX, float doorY) {
        this.doorTargetX = doorX;
        this.doorTargetY = doorY;
    }

    /** Set the world-space spawn the homeowner is teleported to on interior entry. */
    public void setInteriorSpawn(float x, float y) {
        this.interiorSpawnX = x;
        this.interiorSpawnY = y;
    }

    public float getInteriorSpawnX() { return interiorSpawnX; }
    public float getInteriorSpawnY() { return interiorSpawnY; }

    /** Set the rect inside which the homeowner samples wander targets while hunting. */
    public void setInteriorWanderBounds(Rectangle bounds) {
        this.interiorWanderBounds = bounds;
    }

    public void setOnCatch(Runnable cb) { this.onCatch = cb == null ? () -> {} : cb; }
    public void setOnEnterInterior(Runnable cb) { this.onEnterInterior = cb == null ? () -> {} : cb; }

    /**
     * Kick the brain out of {@code WAITING} and into {@code APPROACHING}
     * with the given exterior collision map for pathfinding to the door.
     * Idempotent: subsequent calls do nothing.
     */
    public void startApproach(CollisionMap exteriorCollision) {
        if (phase != Phase.WAITING) return;
        phase = Phase.APPROACHING;
        homeOwner.setVisible(true);
        homeOwner.setOnInterior(false);
        homeOwner.setSpeed(balance.homeOwnerWanderSpeed);
        homeOwner.setState(NpcState.WANDERING);
        bindCollision(exteriorCollision);
        stuckRefX = homeOwner.getX();
        stuckRefY = homeOwner.getY();
        approachTimer = 0f;
        planPathTo(doorTargetX, doorTargetY);
    }

    /**
     * Switch the brain to interior hunting. Called by the world layer
     * after it has teleported the homeowner to the interior spawn.
     */
    public void enterInterior(CollisionMap interiorCollision) {
        phase = Phase.HUNTING;
        homeOwner.setOnInterior(true);
        homeOwner.setSpeed(balance.homeOwnerWanderSpeed);
        homeOwner.setState(NpcState.WANDERING);
        bindCollision(interiorCollision);
        waypoints.clear();
        stuckRefX = homeOwner.getX();
        stuckRefY = homeOwner.getY();
        silenceTimer = 0f;
    }

    private void bindCollision(CollisionMap collisionMap) {
        this.activeCollisionMap = collisionMap;
        this.pathfinder = collisionMap == null
            ? null
            : new Pathfinder(collisionMap,
                homeOwner.getHitboxOffsetX(), homeOwner.getHitboxOffsetY(),
                homeOwner.getHitboxWidth(), homeOwner.getHitboxHeight());
    }

    /**
     * Per-frame update. {@code activeMap} is the collision map for whatever
     * map the homeowner currently sits on; if it differs from what the
     * brain last saw, we rebuild the pathfinder. {@code playerOnSameMap}
     * is true when the player is on the same map — detection only fires
     * in that case.
     */
    public void update(float delta, Player player, CollisionMap activeMap, boolean playerOnSameMap) {
        if (phase == Phase.WAITING) return;
        if (activeMap != activeCollisionMap) bindCollision(activeMap);
        replanTimer -= delta;
        updateStuckDetector(delta);

        switch (phase) {
            case APPROACHING:
                approachTimer += delta;
                // Self-heal: try to replan from where we stand. If BFS still
                // returns nothing (the door cell is registered as a solid via
                // registerDoorsAsSolids and an unfortunate seed cell can
                // disconnect from it through the rasterized walkability grid),
                // fall back to a direct steer toward the target. Collision
                // sliding handles the lawn-and-fence geometry; the worst case
                // is he bumps a fence and we just retry next frame.
                if (!hasPath()) planPathTo(doorTargetX, doorTargetY);
                if (hasPath()) stepTowardTarget(delta);
                else directWalkToward(doorTargetX, doorTargetY, delta);
                if (distanceTo(doorTargetX, doorTargetY) <= DOOR_ENTER_RADIUS
                    || approachTimer >= APPROACH_TIMEOUT_SECONDS) {
                    onEnterInterior.run();
                }
                break;
            case HUNTING:
                if (playerOnSameMap && (canSeePlayer(player) || canHearPlayer(player))) {
                    enterChase(player);
                    break;
                }
                if (!hasPath()) pickWanderTarget();
                stepTowardTarget(delta);
                if (reachedFinalGoal()) pickWanderTarget();
                break;
            case CHASING:
                if (!playerOnSameMap) {
                    // Player escaped this map. Resume hunting; he may come back.
                    phase = Phase.HUNTING;
                    homeOwner.setSpeed(balance.homeOwnerWanderSpeed);
                    homeOwner.setState(NpcState.WANDERING);
                    waypoints.clear();
                    silenceTimer = 0f;
                    break;
                }
                if (distanceToPlayerHitbox(player) <= balance.homeOwnerCatchRange) {
                    onCatch.run();
                    homeOwner.setSpeed(0f);
                    waypoints.clear();
                    break;
                }
                if (canSeePlayer(player) || canHearPlayer(player)) {
                    silenceTimer = 0f;
                    if (replanTimer <= 0f) planPathTo(playerOriginX(player), playerOriginY(player));
                } else {
                    silenceTimer += delta;
                    if (silenceTimer >= balance.homeOwnerNoiseLostSeconds) {
                        // Lost contact. Drop back to hunting.
                        phase = Phase.HUNTING;
                        homeOwner.setSpeed(balance.homeOwnerWanderSpeed);
                        homeOwner.setState(NpcState.WANDERING);
                        waypoints.clear();
                        break;
                    }
                }
                stepTowardTarget(delta);
                break;
            default:
                break;
        }
    }

    private void enterChase(Player player) {
        phase = Phase.CHASING;
        homeOwner.setSpeed(balance.homeOwnerChaseSpeed);
        homeOwner.setState(NpcState.ALERTED);
        silenceTimer = 0f;
        planPathTo(playerOriginX(player), playerOriginY(player));
    }

    /**
     * Vision cone test. True if the player is within range, inside the
     * homeowner's facing half-cone, and an unblocked ray exists between
     * the two centers.
     */
    public boolean canSeePlayer(Player player) {
        float px = playerCenterX(player);
        float py = playerCenterY(player);
        float dx = px - homeOwnerCenterX();
        float dy = py - homeOwnerCenterY();
        float distSq = dx * dx + dy * dy;
        if (distSq > balance.homeOwnerVisionRange * balance.homeOwnerVisionRange) return false;
        if (distSq < 1f) return true;
        // Cone check.
        float[] forward = forwardVector();
        float dist = (float) Math.sqrt(distSq);
        float cosAngle = (forward[0] * dx + forward[1] * dy) / dist;
        float halfConeRad = (balance.homeOwnerVisionAngleDegrees * 0.5f) * MathUtils.degreesToRadians;
        float minCos = (float) Math.cos(halfConeRad);
        if (cosAngle < minCos) return false;
        // Line of sight.
        return lineOfSightClear(homeOwnerCenterX(), homeOwnerCenterY(), px, py);
    }

    public float getVisionRange() { return balance.homeOwnerVisionRange; }
    public float getVisionAngleDegrees() { return balance.homeOwnerVisionAngleDegrees; }

    private boolean canHearPlayer(Player player) {
        if (!player.isMakingNoise()) return false;
        return distanceToPlayerHitbox(player) <= balance.homeOwnerHearingRange;
    }

    /**
     * Walk the line from (fromX,fromY) to (toX,toY) in {@link #LOS_SAMPLE_STEPS}
     * steps and probe each sample with a tiny rect. Skips the endpoints so
     * the ray doesn't reject itself if the homeowner / player happens to
     * sit a hair inside a collision rect.
     */
    public boolean lineOfSightClear(float fromX, float fromY, float toX, float toY) {
        if (activeCollisionMap == null) return true;
        float dx = toX - fromX;
        float dy = toY - fromY;
        for (int i = 1; i < LOS_SAMPLE_STEPS; i++) {
            float t = i / (float) LOS_SAMPLE_STEPS;
            float x = fromX + dx * t;
            float y = fromY + dy * t;
            if (activeCollisionMap.rectCollides(x - LOS_PROBE_SIZE / 2f, y - LOS_PROBE_SIZE / 2f,
                LOS_PROBE_SIZE, LOS_PROBE_SIZE)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Walk a ray of length {@code maxRange} from {@code (fromX,fromY)} in
     * the unit-direction {@code (dirX,dirY)} and return the distance to the
     * first solid hit, or {@code maxRange} if the ray clears. Step count is
     * proportional to the range so longer rays don't lose resolution. The
     * renderer uses this to terminate each cone-edge segment at the first
     * piece of geometry, so the visualized cone matches the actual sight
     * test.
     */
    public float raycastDistance(float fromX, float fromY, float dirX, float dirY, float maxRange) {
        if (activeCollisionMap == null) return maxRange;
        // Scale step density to range — same step every ~15 wu.
        int steps = Math.max(8, (int) (maxRange / 15f));
        float stepLen = maxRange / steps;
        for (int i = 1; i <= steps; i++) {
            float dist = i * stepLen;
            float x = fromX + dirX * dist;
            float y = fromY + dirY * dist;
            if (activeCollisionMap.rectCollides(x - LOS_PROBE_SIZE / 2f, y - LOS_PROBE_SIZE / 2f,
                LOS_PROBE_SIZE, LOS_PROBE_SIZE)) {
                // Step back by half a step so the cone edge stops just
                // shy of the wall instead of inside it.
                return Math.max(0f, dist - stepLen * 0.5f);
            }
        }
        return maxRange;
    }

    /**
     * Vision cone origin in world coords (homeowner's hitbox center).
     * Exposed so the renderer can draw a cone aligned with the same point
     * the brain uses for sight checks.
     */
    public float getVisionOriginX() { return homeOwnerCenterX(); }
    public float getVisionOriginY() { return homeOwnerCenterY(); }

    private void updateStuckDetector(float delta) {
        // If the homeowner has visibly progressed since we last reset stuckCount,
        // forgive the prior stuck history. Without this, a single rough patch
        // permanently arms the teleport escalation for the rest of the heist.
        float clearDx = homeOwner.getX() - stuckClearRefX;
        float clearDy = homeOwner.getY() - stuckClearRefY;
        if (clearDx * clearDx + clearDy * clearDy >= STUCK_RESET_DELTA_SQ) {
            stuckCount = 0;
            stuckClearRefX = homeOwner.getX();
            stuckClearRefY = homeOwner.getY();
        }

        stuckTimer += delta;
        if (stuckTimer < STUCK_WINDOW_SECONDS) return;
        float dx = homeOwner.getX() - stuckRefX;
        float dy = homeOwner.getY() - stuckRefY;
        boolean wedged = dx * dx + dy * dy < STUCK_MIN_DELTA_SQ && hasPath();
        if (wedged) {
            stuckCount++;
            // Tier 1 (default): drop the plan, HUNTING re-picks next frame.
            waypoints.clear();

            // Tier 2 (>= 3 consecutive sticks): aim straight for the
            // interior spawn cell. That cell was authored to be a known-
            // walkable open spot, so BFS from anywhere reasonable should
            // connect to it. Resets the path, so HUNTING is preempted by
            // a clean goal until he's moved at least once.
            if (stuckCount >= STUCK_ESCALATE_THRESHOLD && phase == Phase.HUNTING
                && pathfinder != null) {
                planPathTo(interiorSpawnX, interiorSpawnY);
            }

            // Tier 3 (>= 6): the geometry has him pinned and BFS refuses
            // to find a route. Hard-teleport to the interior spawn so the
            // chase stays alive. Visual blip for the player but better
            // than him standing inert until time runs out.
            if (stuckCount >= STUCK_TELEPORT_THRESHOLD && phase != Phase.APPROACHING) {
                homeOwner.setPosition(interiorSpawnX, interiorSpawnY);
                stuckCount = 0;
                waypoints.clear();
                if (pathfinder != null) planPathTo(interiorSpawnX, interiorSpawnY);
            }
        }
        stuckRefX = homeOwner.getX();
        stuckRefY = homeOwner.getY();
        stuckTimer = 0f;
    }

    private void pickWanderTarget() {
        if (pathfinder == null || interiorWanderBounds == null) return;
        // Round 1: full minimum distance — keeps wandering visibly progressive
        // when the homeowner has plenty of room. Mirrors DogBrain's 48-retry
        // budget; with the wider human hitbox, more samples land near walls
        // and the BFS rejects them, so the larger budget is justified.
        for (int i = 0; i < 48; i++) {
            float[] target = pathfinder.randomWalkablePoint(
                interiorWanderBounds, homeOwner.getX(), homeOwner.getY(), WANDER_MIN_DISTANCE, 4);
            if (target != null && planPathTo(target[0], target[1])) return;
        }
        // Round 2: shorter min-distance, cornered fallback. When the homeowner
        // is wedged in a doorway or small room, every "far" target is on the
        // other side of a clipped wall — relax the spacing rule so he can at
        // least step to an adjacent open cell. Without this, hunting can stall
        // for whole seconds while pickWanderTarget burns through retries with
        // nothing to show.
        for (int i = 0; i < 32; i++) {
            float[] target = pathfinder.randomWalkablePoint(
                interiorWanderBounds, homeOwner.getX(), homeOwner.getY(), 48f, 4);
            if (target != null && planPathTo(target[0], target[1])) return;
        }
    }

    /** Plan a path from the homeowner's current origin to the world goal. */
    private boolean planPathTo(float gx, float gy) {
        replanTimer = PATH_REPLAN_SECONDS;
        lastPlanGoalX = gx;
        lastPlanGoalY = gy;
        waypoints.clear();
        if (pathfinder == null) {
            waypoints.add(new float[] {gx, gy});
            return true;
        }
        List<float[]> path = pathfinder.findPath(homeOwner.getX(), homeOwner.getY(), gx, gy);
        if (path.isEmpty()) return false;
        waypoints.addAll(path);
        return true;
    }

    private boolean hasPath() { return !waypoints.isEmpty(); }

    /**
     * True if the brain has at least one waypoint queued. Exposed so the
     * world layer can detect a failed {@link #startApproach(CollisionMap)}
     * (BFS returned empty because the spawn or door target was blocked)
     * and re-seed the homeowner from a known-walkable fallback position
     * before he sits inert at the south edge of the map.
     */
    public boolean hasActivePath() { return !waypoints.isEmpty(); }

    private boolean reachedFinalGoal() {
        if (!waypoints.isEmpty()) return false;
        return distanceTo(lastPlanGoalX, lastPlanGoalY) <= REACH_THRESHOLD;
    }

    private void stepTowardTarget(float delta) {
        if (waypoints.isEmpty()) {
            applyMove(0f, 0f, delta);
            return;
        }
        float[] wp = waypoints.get(0);
        float dx = wp[0] - homeOwner.getX();
        float dy = wp[1] - homeOwner.getY();
        if (dx * dx + dy * dy <= WAYPOINT_REACH_THRESHOLD * WAYPOINT_REACH_THRESHOLD) {
            waypoints.remove(0);
            if (waypoints.isEmpty()) {
                applyMove(0f, 0f, delta);
                return;
            }
            wp = waypoints.get(0);
            dx = wp[0] - homeOwner.getX();
            dy = wp[1] - homeOwner.getY();
        }
        if (Math.abs(dx) < 1f) dx = 0f;
        if (Math.abs(dy) < 1f) dy = 0f;
        // Refresh speed per-frame so a phase change between frames (HUNTING
        // → CHASING after detection, or CHASING → HUNTING after silence)
        // never leaves him moving at the wrong tempo. Same idiom as
        // DogBrain.stepTowardTarget.
        homeOwner.setSpeed(speedForPhase());
        applyMove(dx, dy, delta);
    }

    /**
     * Speed for the current phase. APPROACHING and HUNTING are the calm
     * patrol cadence; CHASING is the lunge that should outpace an upright
     * player. Mirrors {@code DogBrain.speedForState}.
     */
    private float speedForPhase() {
        switch (phase) {
            case CHASING:
                return balance.homeOwnerChaseSpeed;
            case APPROACHING:
            case HUNTING:
            default:
                return balance.homeOwnerWanderSpeed;
        }
    }

    private void applyMove(float dx, float dy, float delta) {
        if (activeCollisionMap != null) {
            homeOwner.tryMove(dx, dy, delta, activeCollisionMap);
        } else {
            homeOwner.move(dx, dy, delta);
        }
    }

    /**
     * BFS-free fallback used by {@code APPROACHING} when the pathfinder
     * cannot find a path (rasterized walkability grid disconnects the
     * homeowner's spawn cell from the door cell, or the door target lies
     * inside a registered solid). Steers straight toward the target and
     * relies on {@link MovableEntity#tryMove(float, float, float, CollisionMap)}'s
     * axis-separated sliding to navigate around fences and house walls.
     * Without this, a single BFS miss leaves the homeowner stuck on the
     * curb forever.
     */
    private void directWalkToward(float targetX, float targetY, float delta) {
        float dx = targetX - homeOwner.getX();
        float dy = targetY - homeOwner.getY();
        float lenSq = dx * dx + dy * dy;
        if (lenSq < 1f) {
            applyMove(0f, 0f, delta);
            return;
        }
        applyMove(dx, dy, delta);
    }

    private float distanceTo(float x, float y) {
        float dx = x - homeOwner.getX();
        float dy = y - homeOwner.getY();
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    private float distanceToPlayerHitbox(Player player) {
        float dx = playerCenterX(player) - homeOwnerCenterX();
        float dy = playerCenterY(player) - homeOwnerCenterY();
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    private float homeOwnerCenterX() {
        return homeOwner.getX() + homeOwner.getHitboxOffsetX() + homeOwner.getHitboxWidth() / 2f;
    }

    private float homeOwnerCenterY() {
        return homeOwner.getY() + homeOwner.getHitboxOffsetY() + homeOwner.getHitboxHeight() / 2f;
    }

    private static float playerCenterX(Player player) {
        return player.getX() + player.getHitboxOffsetX() + player.getHitboxWidth() / 2f;
    }

    private static float playerCenterY(Player player) {
        return player.getY() + player.getHitboxOffsetY() + player.getHitboxHeight() / 2f;
    }

    private float playerOriginX(Player player) {
        // Path-target conversion: feed the BFS the homeowner-origin that
        // would put his hitbox center on top of the player's center.
        return playerCenterX(player) - homeOwner.getHitboxOffsetX() - homeOwner.getHitboxWidth() / 2f;
    }

    private float playerOriginY(Player player) {
        return playerCenterY(player) - homeOwner.getHitboxOffsetY() - homeOwner.getHitboxHeight() / 2f;
    }

    /**
     * Unit-length forward vector derived from the homeowner's 8-way facing.
     * Used for the vision-cone dot product.
     */
    public float[] forwardVector() {
        float a = forwardAngleRadians();
        return new float[] { (float) Math.cos(a), (float) Math.sin(a) };
    }

    /**
     * Forward direction in radians (libGDX bottom-up: 0 = east, PI/2 = north).
     * The {@link com.onelastheist.game.entity.base.FacingDirection} enum is
     * the source of truth — derive the angle from its name rather than
     * carrying a parallel mapping.
     */
    public float forwardAngleRadians() {
        switch (homeOwner.getFacingDirection()) {
            case EAST:       return 0f;
            case NORTH_EAST: return MathUtils.PI / 4f;
            case NORTH:      return MathUtils.PI / 2f;
            case NORTH_WEST: return 3f * MathUtils.PI / 4f;
            case WEST:       return MathUtils.PI;
            case SOUTH_WEST: return -3f * MathUtils.PI / 4f;
            case SOUTH:      return -MathUtils.PI / 2f;
            case SOUTH_EAST: return -MathUtils.PI / 4f;
            default:         return -MathUtils.PI / 2f;
        }
    }
}
