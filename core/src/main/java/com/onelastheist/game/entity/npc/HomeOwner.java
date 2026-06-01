package com.onelastheist.game.entity.npc;

import com.onelastheist.game.entity.base.MovableEntity;
import com.onelastheist.game.trap.AlarmEvent;

/**
 * The homeowner NPC. Currently a placeholder: starts {@code IDLE} and flips to
 * {@code INVESTIGATING} when an alarm event fires. AI behavior (patrol, search,
 * detection) lives in the {@code com.onelastheist.game.ai} package and will
 * read/write this state.
 */
public class HomeOwner extends MovableEntity {
    private NpcState state = NpcState.IDLE;

    /** Wakes the homeowner up so the AI brain switches to its search routine. */
    public void reactToAlarm(AlarmEvent event) { state = NpcState.INVESTIGATING; }
    public NpcState getState() { return state; }
}
