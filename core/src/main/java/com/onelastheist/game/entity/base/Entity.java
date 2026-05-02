package com.onelastheist.game.entity.base;

/** Thuc the mien co ban, hien chi luu vi tri. */
public class Entity {
    private float x;
    private float y;

    public float getX() { return x; }
    public float getY() { return y; }
    public void setPosition(float x, float y) { this.x = x; this.y = y; }
}
