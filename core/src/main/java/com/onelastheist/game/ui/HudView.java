package com.onelastheist.game.ui;

import com.onelastheist.game.world.GameWorld;

public class HudView {
    private final GameWorld world;
    public HudView(GameWorld world) { this.world = world; }
    public GameWorld getWorld() { return world; }
}
