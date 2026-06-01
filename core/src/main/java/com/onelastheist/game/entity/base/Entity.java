package com.onelastheist.game.entity.base;

/**
 * Base for everything placed in the world. Carries a position in world units
 * (LibGDX convention: +Y up, origin bottom-left of the map) and a {@code visible}
 * flag the renderer respects — useful for actors that exist in the simulation
 * but should not be drawn yet (e.g. the homeowner before they spawn).
 */
public class Entity {
    private float x;
    private float y;
    private boolean visible = true;

    public float getX() { return x; }
    public float getY() { return y; }
    public boolean isVisible() { return visible; }
    public void setPosition(float x, float y) { this.x = x; this.y = y; }
    public void setVisible(boolean visible) { this.visible = visible; }
}
