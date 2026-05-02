package com.onelastheist.game.quest;

import com.onelastheist.game.world.ObjectiveTracker;

public class MainLootObjective implements Objective {
    private final ObjectiveTracker tracker;
    public MainLootObjective(ObjectiveTracker tracker) { this.tracker = tracker; }
    @Override public String getName() { return "Collect enough money and escape"; }
    @Override public ObjectiveStatus getStatus() { return tracker.hasEnoughMoney() ? ObjectiveStatus.COMPLETE : ObjectiveStatus.ACTIVE; }
    @Override public void update() {}
}
