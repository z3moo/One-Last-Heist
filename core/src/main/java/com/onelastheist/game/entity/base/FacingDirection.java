package com.onelastheist.game.entity.base;

/** 8 huong nhan vat, theo dung thu tu row trong spritesheet 48x48. */
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
