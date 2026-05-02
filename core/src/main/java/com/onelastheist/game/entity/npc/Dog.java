package com.onelastheist.game.entity.npc;

import com.onelastheist.game.entity.base.MovableEntity;

public class Dog extends MovableEntity {
    private NpcState state = NpcState.IDLE;

    public void sleep() { state = NpcState.SLEEPING; }
    public NpcState getState() { return state; }
}
