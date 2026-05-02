package com.onelastheist.game.entity.base;

public class MovableEntity extends Entity {
    private float speed = 180f;

    public void move(float dx, float dy, float deltaSeconds) {
        setPosition(getX() + dx * speed * deltaSeconds, getY() + dy * speed * deltaSeconds);
    }

    public float getSpeed() { return speed; }
    public void setSpeed(float speed) { this.speed = speed; }
}
