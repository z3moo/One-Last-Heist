package com.onelastheist.game.ai;

import com.onelastheist.game.entity.npc.HomeOwner;
import com.onelastheist.game.trap.AlarmEvent;

public class HomeOwnerBrain {
    private final HomeOwner owner;
    public HomeOwnerBrain(HomeOwner owner) { this.owner = owner; }
    public void onAlarm(AlarmEvent event) { owner.reactToAlarm(event); }
}
