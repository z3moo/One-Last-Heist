package com.onelastheist.game.ai;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.onelastheist.game.world.CollisionMap;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;

/**
 * Tile-grid BFS pathfinder used by {@link DogBrain}. Builds a per-tile
 * walkability grid lazily on first use from a {@link CollisionMap}: a cell is
 * walkable when an axis-aligned box of {@link #agentWidth} × {@link #agentHeight}
 * centered on the cell does not collide with any solid.
 *
 * <p>BFS over 4-connected neighbors. That gives shortest paths in tile-counts
 * which is fine for the dog wandering between rooms — the dog moves fast
 * enough that diagonal stair-stepping along an L-shaped path looks natural.
 *
 * <p>The grid only needs to be built once per map; it never changes during a
 * heist. Subsequent calls reuse it. A pathfinder instance is therefore
 * inexpensive to query but tied to a single CollisionMap — the world rebuilds
 * one each time it constructs the dog's brain (i.e. every interior load).
 */
public class Pathfinder {
    private final CollisionMap collisionMap;
    private final int width;
    private final int height;
    private final float tileSize;
    private final float agentOffsetX;
    private final float agentOffsetY;
    private final float agentWidth;
    private final float agentHeight;
    /** True when the cell at (x,y) — flat-indexed as y*width+x — is solid for an agent of this size. */
    private final boolean[] blocked;

    public Pathfinder(CollisionMap collisionMap, float agentOffsetX, float agentOffsetY, float agentWidth, float agentHeight) {
        this.collisionMap = collisionMap;
        this.width = collisionMap.getTileWidth();
        this.height = collisionMap.getTileHeight();
        this.tileSize = collisionMap.getTileSize();
        this.agentOffsetX = agentOffsetX;
        this.agentOffsetY = agentOffsetY;
        this.agentWidth = agentWidth;
        this.agentHeight = agentHeight;
        this.blocked = buildWalkabilityGrid();
    }

    /**
     * Find a path from world position (sx,sy) to (gx,gy). Returns world-space
     * waypoints (one per tile, cell-centered) excluding the start. The first
     * entry is the next tile to walk into; the last is the goal cell.
     * Returns an empty list if no path exists or start/goal are blocked.
     *
     * <p>{@code sx}/{@code sy} and {@code gx}/{@code gy} are entity-origin
     * coordinates (the agent's sprite top-left, what {@code Entity.getX/Y}
     * returns), not hitbox-center coordinates. We tilize by adding the
     * hitbox-center offset so the BFS starts in the cell the dog actually
     * stands in, not the cell its sprite origin happens to land in. This was
     * the cause of "dog animates but never moves": the sprite origin sat in
     * a wall cell while the hitbox center was a tile to the south-east, so
     * BFS rejected the start and every wander attempt failed.
     *
     * <p>If start and goal land in the same cell, the goal world-space point
     * is returned as a single waypoint so the caller can finish the last step.
     */
    public List<float[]> findPath(float sx, float sy, float gx, float gy) {
        int startX = worldToTileX(sx + agentOffsetX + agentWidth / 2f);
        int startY = worldToTileY(sy + agentOffsetY + agentHeight / 2f);
        int goalX = worldToTileX(gx + agentOffsetX + agentWidth / 2f);
        int goalY = worldToTileY(gy + agentOffsetY + agentHeight / 2f);
        if (!inBounds(startX, startY) || !inBounds(goalX, goalY)) return java.util.Collections.emptyList();

        if (startX == goalX && startY == goalY) {
            List<float[]> single = new ArrayList<>(1);
            single.add(new float[] {gx, gy});
            return single;
        }

        // Guard: if the start cell tests blocked but the dog's actual hitbox
        // fits at its current origin (common when the sprite extends into a
        // wall but the body doesn't), force the start cell open. Same trick
        // for the goal — pruned-overhead cells often test blocked even though
        // a real hitbox fits.
        int startIdx = startY * width + startX;
        int goalIdx = goalY * width + goalX;
        boolean startForcedOpen = blocked[startIdx]
            && !collisionMap.rectCollides(
                sx + agentOffsetX, sy + agentOffsetY, agentWidth, agentHeight);
        if (blocked[startIdx] && !startForcedOpen) return java.util.Collections.emptyList();
        boolean goalForcedOpen = blocked[goalIdx]
            && !collisionMap.rectCollides(
                gx + agentOffsetX, gy + agentOffsetY, agentWidth, agentHeight);
        if (blocked[goalIdx] && !goalForcedOpen) return java.util.Collections.emptyList();

        int total = width * height;
        int[] cameFrom = new int[total];
        Arrays.fill(cameFrom, -1);
        cameFrom[startIdx] = startIdx;

        Deque<Integer> frontier = new ArrayDeque<>();
        frontier.add(startIdx);
        boolean found = false;
        while (!frontier.isEmpty()) {
            int cur = frontier.poll();
            if (cur == goalIdx) { found = true; break; }
            int cx = cur % width;
            int cy = cur / width;
            // 4-connected neighbors. Diagonals would let the dog corner-cut
            // through walls; sticking to N/E/S/W keeps the BFS legal.
            tryEnqueue(frontier, cameFrom, cx + 1, cy, cur, goalForcedOpen, goalIdx);
            tryEnqueue(frontier, cameFrom, cx - 1, cy, cur, goalForcedOpen, goalIdx);
            tryEnqueue(frontier, cameFrom, cx, cy + 1, cur, goalForcedOpen, goalIdx);
            tryEnqueue(frontier, cameFrom, cx, cy - 1, cur, goalForcedOpen, goalIdx);
        }
        if (!found) return java.util.Collections.emptyList();

        // Walk back from goal to start to reconstruct the path.
        List<float[]> reverse = new ArrayList<>();
        int cur = goalIdx;
        while (cur != startIdx) {
            int cx = cur % width;
            int cy = cur / width;
            reverse.add(new float[] {tileCenterX(cx), tileCenterY(cy)});
            cur = cameFrom[cur];
        }
        // Replace the final waypoint (goal cell center) with the actual goal
        // world coords so the dog stops on the meat / target, not at its tile center.
        if (!reverse.isEmpty()) reverse.set(0, new float[] {gx, gy});
        java.util.Collections.reverse(reverse);
        return reverse;
    }

    private void tryEnqueue(Deque<Integer> frontier, int[] cameFrom, int x, int y, int from,
                            boolean goalForcedOpen, int goalIdx) {
        if (!inBounds(x, y)) return;
        int idx = y * width + x;
        if (cameFrom[idx] != -1) return;                 // visited
        if (blocked[idx] && !(idx == goalIdx && goalForcedOpen)) return;
        cameFrom[idx] = from;
        frontier.add(idx);
    }

    /**
     * Bake the per-cell walkability flag once. The dog is wider than one tile
     * (56 wu hitbox vs 48 wu tile), so a full-size probe marks almost every
     * doorway-adjacent cell blocked and the dog never finds a path out of the
     * starting room. Use a compact center probe for the grid, then let real
     * movement use the full hitbox through {@link com.onelastheist.game.entity.base.MovableEntity#tryMove}.
     *
     * <p>This keeps pathfinding broad enough for the hand-authored interior
     * map. The stuck detector in {@link DogBrain} still tears down any plan
     * that cannot be physically followed and asks for a new wander target.
     */
    private boolean[] buildWalkabilityGrid() {
        boolean[] grid = new boolean[width * height];
        // Full dog hitbox (56x32) made many doorway-adjacent cells look blocked;
        // the old half-tile probe (24x24) was too permissive and let paths graze
        // walls/furniture. This keeps a door-friendly but body-shaped probe.
        float probeW = Math.min(agentWidth, tileSize - 8f);
        float probeH = Math.min(agentHeight, tileSize - 8f);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                float px = x * tileSize + (tileSize - probeW) / 2f;
                float py = y * tileSize + (tileSize - probeH) / 2f;
                grid[y * width + x] = collisionMap.rectCollides(px, py, probeW, probeH);
            }
        }
        return grid;
    }

    /** True if the world-space point lands on a walkable tile. */
    public boolean isWalkable(float worldX, float worldY) {
        int tx = worldToTileX(worldX);
        int ty = worldToTileY(worldY);
        if (!inBounds(tx, ty)) return false;
        return !blocked[ty * width + tx];
    }

    /** True if the agent's actual hitbox can stand at this entity origin. */
    public boolean isDestinationClear(float entityX, float entityY) {
        return !collisionMap.rectCollides(entityX + agentOffsetX, entityY + agentOffsetY, agentWidth, agentHeight);
    }

    /**
     * Pick a random clear tile-centered entity origin inside the given bounds.
     * Returning tile centers instead of raw random pixels keeps wander goals away
     * from walls and furniture, then DogBrain still verifies full route reachability.
     */
    public float[] randomWalkablePoint(Rectangle bounds, float fromX, float fromY, float minDistance, int attempts) {
        int minTx = Math.max(0, worldToTileX(bounds.x));
        int maxTx = Math.min(width - 1, worldToTileX(bounds.x + bounds.width));
        int minTy = Math.max(0, worldToTileY(bounds.y));
        int maxTy = Math.min(height - 1, worldToTileY(bounds.y + bounds.height));
        if (maxTx < minTx || maxTy < minTy) return null;

        float minDistSq = minDistance * minDistance;
        for (int i = 0; i < attempts; i++) {
            int tx = MathUtils.random(minTx, maxTx);
            int ty = MathUtils.random(minTy, maxTy);
            if (blocked[ty * width + tx]) continue;
            float ex = tileCenterX(tx);
            float ey = tileCenterY(ty);
            float dx = ex - fromX;
            float dy = ey - fromY;
            if (dx * dx + dy * dy < minDistSq) continue;
            if (!isDestinationClear(ex, ey)) continue;
            return new float[] {ex, ey};
        }
        return null;
    }

    private int worldToTileX(float wx) { return (int) (wx / tileSize); }
    private int worldToTileY(float wy) { return (int) (wy / tileSize); }
    private float tileCenterX(int tx) { return tx * tileSize + tileSize / 2f - agentWidth / 2f - agentOffsetX; }
    private float tileCenterY(int ty) { return ty * tileSize + tileSize / 2f - agentHeight / 2f - agentOffsetY; }
    private boolean inBounds(int tx, int ty) { return tx >= 0 && tx < width && ty >= 0 && ty < height; }
}
