package com.onelastheist.game.entity.base;

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

    public boolean isMoving() { return moving; }
    public FacingDirection getFacingDirection() { return facingDirection; }
    public void setFacingDirection(FacingDirection facingDirection) { this.facingDirection = facingDirection; }
    public float getSpeed() { return speed; }
    public void setSpeed(float speed) { this.speed = speed; }
}
