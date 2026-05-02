package com.onelastheist.game.entity.npc;

import com.onelastheist.game.entity.base.MovableEntity;
import com.onelastheist.game.trap.AlarmEvent;

public class HomeOwner extends MovableEntity {
    private NpcState state = NpcState.IDLE;

    public void reactToAlarm(AlarmEvent event) { state = NpcState.INVESTIGATING; }
    public NpcState getState() { return state; }
}
