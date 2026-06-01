package com.onelastheist.game.entity.base;

import com.onelastheist.game.world.CollisionMap;

public class MovableEntity extends Entity {
    private float speed = 180f;
    private boolean moving;
    private FacingDirection facingDirection = FacingDirection.SOUTH;

    public void move(float dx, float dy, float deltaSeconds) {
        moving = dx != 0f || dy != 0f;
        if (!moving) {
            return;
        }

        facingDirection = FacingDirection.fromVector(dx, dy);

        float length = (float) Math.sqrt(dx * dx + dy * dy);
        dx /= length;
        dy /= length;
        setPosition(getX() + dx * speed * deltaSeconds, getY() + dy * speed * deltaSeconds);
    }

    /** Di chuyen voi va cham theo CollisionMap; truot doc theo truc neu chan mot ben. */
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

        float newX = getX();
        if (stepX != 0f) {
            float candidate = newX + stepX;
            if (!collisionMap.rectCollides(candidate + hbOffX, getY() + hbOffY, hbW, hbH)) {
                newX = candidate;
            }
        }

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
            facingDirection = requested;
            setPosition(newX, newY);
        }
    }

    public boolean isMoving() { return moving; }
    public FacingDirection getFacingDirection() { return facingDirection; }
    public void setFacingDirection(FacingDirection facingDirection) { this.facingDirection = facingDirection; }
    public float getSpeed() { return speed; }
    public void setSpeed(float speed) { this.speed = speed; }

    public float getHitboxWidth() { return 60f; }
    public float getHitboxHeight() { return 36f; }
    public float getHitboxOffsetX() { return 42f; }
    public float getHitboxOffsetY() { return 12f; }
}
