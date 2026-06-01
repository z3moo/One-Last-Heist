package com.onelastheist.game.entity.npc;

import com.onelastheist.game.entity.base.MovableEntity;

public class Dog extends MovableEntity {
    public static final float SLEEP_DELAY_SECONDS = 20f;

    private NpcState state = NpcState.IDLE;
    private float idleTimer;

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

    @Override
    public float getHitboxWidth() { return 56f; }
    @Override
    public float getHitboxHeight() { return 32f; }
    @Override
    public float getHitboxOffsetX() { return 32f; }
    @Override
    public float getHitboxOffsetY() { return 10f; }
}
