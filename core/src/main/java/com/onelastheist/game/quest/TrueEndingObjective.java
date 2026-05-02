package com.onelastheist.game.quest;

import com.onelastheist.game.world.ObjectiveTracker;

public class TrueEndingObjective implements Objective {
    private final ObjectiveTracker tracker;
    public TrueEndingObjective(ObjectiveTracker tracker) { this.tracker = tracker; }
    @Override public String getName() { return "Collect evidence and survive"; }
    @Override public ObjectiveStatus getStatus() { return tracker.getEvidenceCount() >= 3 ? ObjectiveStatus.COMPLETE : ObjectiveStatus.ACTIVE; }
    @Override public void update() {}
}
