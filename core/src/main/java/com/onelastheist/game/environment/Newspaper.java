package com.onelastheist.game.environment;

import com.onelastheist.game.entity.base.Entity;

/**
 * A stationary newspaper the player can interact with (E key) to open
 * a full-screen broadside overlay. Lives only on the main-house interior;
 * never moves, never collides with anything, never gets picked up — pressing
 * E just toggles the {@link com.onelastheist.game.screen.PlayScreen}'s
 * "newspaper open" state.
 *
 * <p>Position is stored as the entity's world-space center (not its sprite
 * origin) so the prompt and Y-sort logic can read straight off it without
 * an offset dance. A 28-wu interaction radius keeps the prompt from
 * popping in until the player is visibly standing on the tile.
 */
public class Newspaper extends Entity {
    /**
     * How close the player must stand for the "E to Read" prompt to fire.
     * Bumped from 28 wu — the newspaper draws on top of everything (final
     * render pass), so the player often stands right next to or partly
     * over it visually. A larger radius lets E land reliably without
     * pixel-hunting the exact tile center.
     */
    public static final float INTERACT_RADIUS = 72f;

    public Newspaper(float x, float y) {
        setPosition(x, y);
    }

    /**
     * True if the player's hitbox-center sits within {@link #INTERACT_RADIUS}
     * of the newspaper's center. Same shape as {@link com.onelastheist.game.world.Door#playerInRange}
     * — proximity-only, no rectangle overlap. Newspapers don't have a footprint.
     */
    public boolean playerInRange(float playerCenterX, float playerCenterY) {
        float dx = playerCenterX - getX();
        float dy = playerCenterY - getY();
        return dx * dx + dy * dy <= INTERACT_RADIUS * INTERACT_RADIUS;
    }
}
