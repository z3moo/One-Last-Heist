package com.onelastheist.game.entity.base;

import com.onelastheist.game.world.CollisionMap;

/**
 * Base class for any entity that can move under its own power: the player, the
 * homeowner, the dog. Provides two travel modes:
 * <ul>
 *   <li>{@link #move(float, float, float)} — free movement, no collision check.
 *       Used during the tutorial phase where the world map is not loaded.</li>
 *   <li>{@link #tryMove(float, float, float, CollisionMap)} — axis-separated
 *       collision-aware movement, so the entity slides along a wall instead of
 *       getting stuck on it.</li>
 * </ul>
 *
 * <p>Subclasses can override the four hitbox methods to shape their collision
 * footprint relative to the sprite anchor.
 */
public class MovableEntity extends Entity {
    private float speed = 180f;
    private boolean moving;
    private FacingDirection facingDirection = FacingDirection.SOUTH;

    /** Free movement: applies the input vector directly with no collision check. */
    public void move(float dx, float dy, float deltaSeconds) {
        moving = dx != 0f || dy != 0f;
        if (!moving) {
            return;
        }

        facingDirection = FacingDirection.fromVector(dx, dy);

        // Normalize so diagonal travel is not faster than cardinal travel.
        float length = (float) Math.sqrt(dx * dx + dy * dy);
        dx /= length;
        dy /= length;
        setPosition(getX() + dx * speed * deltaSeconds, getY() + dy * speed * deltaSeconds);
    }

    /**
     * Collision-aware movement. Tries each axis independently so when the entity
     * walks into a corner diagonally, the blocked component is dropped while the
     * free component still applies — this is what gives the smooth "slide along
     * the wall" feel instead of a hard stop.
     */
    public void tryMove(float dx, float dy, float deltaSeconds, CollisionMap collisionMap) {
        moving = dx != 0f || dy != 0f;
        if (!moving) {
            return;
        }

        FacingDirection requested = FacingDirection.fromVector(dx, dy);

        float length = (float) Math.sqrt(dx * dx + dy * dy);
        dx /= length;
        dy /= length;
        float stepX = dx * speed * deltaSeconds;
        float stepY = dy * speed * deltaSeconds;

        float hbW = getHitboxWidth();
        float hbH = getHitboxHeight();
        float hbOffX = getHitboxOffsetX();
        float hbOffY = getHitboxOffsetY();

        // X axis first.
        float newX = getX();
        if (stepX != 0f) {
            float candidate = newX + stepX;
            if (!collisionMap.rectCollides(candidate + hbOffX, getY() + hbOffY, hbW, hbH)) {
                newX = candidate;
            }
        }

        // Then Y, against the already-resolved X. Resolving Y against newX
        // (not getX()) is what lets the entity slip past corners that block
        // a single axis but leave the other axis free.
        float newY = getY();
        if (stepY != 0f) {
            float candidate = newY + stepY;
            if (!collisionMap.rectCollides(newX + hbOffX, candidate + hbOffY, hbW, hbH)) {
                newY = candidate;
            }
        }

        boolean actuallyMoved = newX != getX() || newY != getY();
        moving = actuallyMoved;
        if (actuallyMoved) {
            // Only update facing on real movement so the sprite does not snap
            // to a direction we never actually traveled (e.g. stuck against a wall).
            facingDirection = requested;
            setPosition(newX, newY);
        }
    }

    public boolean isMoving() { return moving; }
    public FacingDirection getFacingDirection() { return facingDirection; }
    public void setFacingDirection(FacingDirection facingDirection) { this.facingDirection = facingDirection; }
    public float getSpeed() { return speed; }
    public void setSpeed(float speed) { this.speed = speed; }

    // Default hitbox: roughly the bottom-center "feet" rectangle of a 144x144 sprite.
    // Subclasses (e.g. Dog) override these to match smaller sprites.
    public float getHitboxWidth() { return 60f; }
    public float getHitboxHeight() { return 36f; }
    public float getHitboxOffsetX() { return 42f; }
    public float getHitboxOffsetY() { return 12f; }
}
