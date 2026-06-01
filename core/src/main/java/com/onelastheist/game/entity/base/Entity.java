package com.onelastheist.game.entity.base;

/** Thuc the mien co ban: vi tri va co dang trong canh hay khong. */
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
