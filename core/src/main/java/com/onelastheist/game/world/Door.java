package com.onelastheist.game.world;

import com.badlogic.gdx.math.Rectangle;

/**
 * A single interactive door anchored in world coordinates. Carries everything
 * {@link com.onelastheist.game.screen.PlayScreen} needs to render the prompt
 * and resolve the E-key press: bounds, the target interior map id, a display
 * label, and a locked flag.
 *
 * <p>Locked doors still render the prompt — they simply trigger a red flash
 * on E instead of changing screens. The bounds rectangle is also reused as a
 * solid in the collision map so the player cannot walk through the doorway.
 */
public class Door {
    private final Rectangle bounds;
    private final String targetMapId;
    private final String label;
    private final boolean locked;

    public Door(float x, float y, float width, float height, String targetMapId, String label, boolean locked) {
        this.bounds = new Rectangle(x, y, width, height);
        this.targetMapId = targetMapId;
        this.label = label;
        this.locked = locked;
    }

    public Rectangle getBounds() { return bounds; }
    public String getTargetMapId() { return targetMapId; }
    public String getLabel() { return label; }
    public boolean isLocked() { return locked; }
    public float getCenterX() { return bounds.x + bounds.width / 2f; }
    public float getCenterY() { return bounds.y + bounds.height / 2f; }

    /**
     * Tests whether the player's hitbox intersects the door bounds expanded by
     * {@code radius} on each side. The radius is what makes the prompt appear
     * a step before the player is literally touching the door.
     */
    public boolean playerInRange(float px, float py, float pw, float ph, float radius) {
        float ax = bounds.x - radius;
        float ay = bounds.y - radius;
        float aw = bounds.width + radius * 2f;
        float ah = bounds.height + radius * 2f;
        return px < ax + aw && px + pw > ax && py < ay + ah && py + ph > ay;
    }
}

