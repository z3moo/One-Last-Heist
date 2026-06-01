package com.onelastheist.game.entity.base;

/**
 * Eight-way facing for top-down sprites. Order matches the row layout of the
 * 48x48 spritesheets in {@code assets/characters/}: row 0 is south-facing,
 * row 1 south-east, and so on. {@link com.onelastheist.game.render.WorldRenderer}
 * uses {@link #getSpriteRow()} to pick the correct animation row each frame.
 */
public enum FacingDirection {
    SOUTH(0),
    SOUTH_EAST(1),
    EAST(2),
    NORTH_EAST(3),
    NORTH(4),
    NORTH_WEST(5),
    WEST(6),
    SOUTH_WEST(7);

    private final int spriteRow;

    FacingDirection(int spriteRow) {
        this.spriteRow = spriteRow;
    }

    public int getSpriteRow() {
        return spriteRow;
    }

    /**
     * Maps a movement vector to one of the eight directions. Uses {@link Float#compare}
     * to bucket each axis into -1/0/+1 first so floating-point noise near zero does
     * not cause the sprite to flicker between adjacent rows when the player stops moving.
     */
    public static FacingDirection fromVector(float dx, float dy) {
        int sx = Float.compare(dx, 0f);
        int sy = Float.compare(dy, 0f);

        if (sy < 0 && sx == 0) return SOUTH;
        if (sy < 0 && sx > 0) return SOUTH_EAST;
        if (sy == 0 && sx > 0) return EAST;
        if (sy > 0 && sx > 0) return NORTH_EAST;
        if (sy > 0 && sx == 0) return NORTH;
        if (sy > 0 && sx < 0) return NORTH_WEST;
        if (sy == 0 && sx < 0) return WEST;
        if (sy < 0 && sx < 0) return SOUTH_WEST;

        return SOUTH;
    }
}
