package com.onelastheist.game.world;

import com.badlogic.gdx.math.Rectangle;

/**
 * Mot canh cua dan toi map khac. Hinh chu nhat trong toa do the gioi (sau MAP_UNIT_SCALE),
 * cong them ID cua map dich de PlayScreen quyet dinh dieu huong khi nguoi choi nhan E.
 * Cua khoa van hien prompt nhung khong cho qua va se nhay flash "LOCKED" khi an E.
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

    /** Hinh chu nhat nguoi choi co giao voi cua sau khi mo rong them radius. */
    public boolean playerInRange(float px, float py, float pw, float ph, float radius) {
        float ax = bounds.x - radius;
        float ay = bounds.y - radius;
        float aw = bounds.width + radius * 2f;
        float ah = bounds.height + radius * 2f;
        return px < ax + aw && px + pw > ax && py < ay + ah && py + ph > ay;
    }
}

