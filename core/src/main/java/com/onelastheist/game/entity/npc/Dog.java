package com.onelastheist.game.entity.npc;

import com.onelastheist.game.entity.base.MovableEntity;

/**
 * The guard dog. Owns its FSM state, wander/sleep timers, and an optional
 * movement target. Per-frame decision-making (state transitions, target
 * selection, movement application) lives in
 * {@link com.onelastheist.game.ai.DogBrain} — Dog itself only stores data,
 * which keeps state changes auditable and unit-testable without a tick.
 *
 * <p>States the dog can occupy (see {@link NpcState}):
 * <ul>
 *   <li>{@link NpcState#WANDERING} — picking a random target inside the house and walking toward it.</li>
 *   <li>{@link NpcState#SLEEPING} — natural nap; will wake on a loud noise (player walking nearby) or after the timer expires.</li>
 *   <li>{@link NpcState#INVESTIGATING_NOISE} — heard the player and is closing on their last known position.</li>
 *   <li>{@link NpcState#INVESTIGATING_MEAT} — sees a meat pickup and is moving to eat it.</li>
 *   <li>{@link NpcState#DRUGGED} — ate sleeping-pill meat; deep sleep, cannot wake until the timer expires.</li>
 * </ul>
 *
 * <p>Hitbox is overridden to be tighter than the default 144x144 character sprite
 * because the dog uses a smaller sheet.
 */
public class Dog extends MovableEntity {
    private NpcState state = NpcState.SLEEPING;
    /** Time remaining in the current state. Each state's transition function refreshes it on entry. */
    private float stateTimer;
    /** Optional move target (world coords). Null while sleeping/drugged or when no target chosen this frame. */
    private float targetX;
    private float targetY;
    private boolean hasTarget;
    /** Home position the dog returns to before each natural sleep. Set once on spawn. */
    private float homeX;
    private float homeY;

    public NpcState getState() { return state; }
    public boolean isSleeping() { return state == NpcState.SLEEPING || state == NpcState.DRUGGED; }
    public boolean isDrugged() { return state == NpcState.DRUGGED; }
    /** True when the dog can be roused by external stimuli (noise / meat). Drugged dogs cannot. */
    public boolean canBeDisturbed() { return state != NpcState.DRUGGED; }

    public float getStateTimer() { return stateTimer; }
    public void setStateTimer(float seconds) { this.stateTimer = seconds; }
    public void tickStateTimer(float delta) { this.stateTimer -= delta; }

    public boolean hasTarget() { return hasTarget; }
    public float getTargetX() { return targetX; }
    public float getTargetY() { return targetY; }
    public void setTarget(float x, float y) {
        this.targetX = x;
        this.targetY = y;
        this.hasTarget = true;
    }
    public void clearTarget() { this.hasTarget = false; }

    public float getHomeX() { return homeX; }
    public float getHomeY() { return homeY; }
    /** Set the dog's bedding spot — the position it walks back to before each natural sleep cycle. */
    public void setHome(float x, float y) { this.homeX = x; this.homeY = y; }

    /**
     * Atomic state transition. Sets the new state, clears any active movement
     * target (each state's controller picks its own target afterward), and
     * arms the timer with the duration the brain wants the dog to spend in
     * this state.
     */
    public void enterState(NpcState newState, float durationSeconds) {
        this.state = newState;
        this.stateTimer = durationSeconds;
        this.hasTarget = false;
    }

    // Smaller footprint than the default human-shaped hitbox — fits the dog sprite.
    @Override
    public float getHitboxWidth() { return 56f; }
    @Override
    public float getHitboxHeight() { return 32f; }
    @Override
    public float getHitboxOffsetX() { return 32f; }
    @Override
    public float getHitboxOffsetY() { return 10f; }
}
