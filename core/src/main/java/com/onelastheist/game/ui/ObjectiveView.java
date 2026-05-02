package com.onelastheist.game.ui;

import com.onelastheist.game.world.ObjectiveTracker;

public class ObjectiveView {
    private final ObjectiveTracker tracker;
    public ObjectiveView(ObjectiveTracker tracker) { this.tracker = tracker; }
    public ObjectiveTracker getTracker() { return tracker; }
}
