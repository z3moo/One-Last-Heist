package com.onelastheist.game.entity.npc;

import com.onelastheist.game.entity.base.MovableEntity;

/**
 * The guard dog. Idles in place, then falls asleep after {@link #SLEEP_DELAY_SECONDS}
 * of uninterrupted calm — a sleeping dog is safe to walk past. Loud actions
 * (future noise system) are expected to call {@link #wake()} to reset the timer.
 *
 * <p>Hitbox is overridden to be tighter than the default 144x144 character sprite
 * because the dog uses a smaller sheet.
 */
public class Dog extends MovableEntity {
    /** Seconds of continuous idleness before the dog falls asleep. */
    public static final float SLEEP_DELAY_SECONDS = 20f;

    private NpcState state = NpcState.IDLE;
    private float idleTimer;

    /** Per-frame tick. Sleeping dogs ignore further updates until {@link #wake()}. */
    public void update(float deltaSeconds) {
        if (state == NpcState.SLEEPING) return;
        idleTimer += deltaSeconds;
        if (idleTimer >= SLEEP_DELAY_SECONDS) sleep();
    }

    public void sleep() {
        state = NpcState.SLEEPING;
        idleTimer = 0f;
    }

    public void wake() {
        state = NpcState.IDLE;
        idleTimer = 0f;
    }

    public boolean isSleeping() { return state == NpcState.SLEEPING; }
    public NpcState getState() { return state; }

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
