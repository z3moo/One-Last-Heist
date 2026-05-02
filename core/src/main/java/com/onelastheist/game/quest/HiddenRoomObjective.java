package com.onelastheist.game.quest;

import com.onelastheist.game.world.ObjectiveTracker;

public class HiddenRoomObjective implements Objective {
    private final ObjectiveTracker tracker;
    public HiddenRoomObjective(ObjectiveTracker tracker) { this.tracker = tracker; }
    @Override public String getName() { return "Explore the hidden route"; }
    @Override public ObjectiveStatus getStatus() { return tracker.isHiddenRouteEntered() ? ObjectiveStatus.ACTIVE : ObjectiveStatus.LOCKED; }
    @Override public void update() {}
}
