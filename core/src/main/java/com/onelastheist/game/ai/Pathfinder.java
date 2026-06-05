package com.onelastheist.game.ai;

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
     * <p>If start and goal land in the same cell, the goal world-space point
     * is returned as a single waypoint so the caller can finish the last step.
     */
    public List<float[]> findPath(float sx, float sy, float gx, float gy) {
        int startX = worldToTileX(sx);
        int startY = worldToTileY(sy);
        int goalX = worldToTileX(gx);
        int goalY = worldToTileY(gy);
        if (!inBounds(startX, startY) || !inBounds(goalX, goalY)) return java.util.Collections.emptyList();

        if (startX == goalX && startY == goalY) {
            List<float[]> single = new ArrayList<>(1);
            single.add(new float[] {gx, gy});
            return single;
        }

        // Guard: if the goal cell is blocked but the agent's actual destination
        // (small hitbox in the cell) would fit, snap goal walkability to true
        // for the BFS. The dog might be heading to a cell where furniture is
        // pruned-overhead and a tile-grid bake says blocked but rectCollides
        // says clear. Cheap safety net.
        int startIdx = startY * width + startX;
        int goalIdx = goalY * width + goalX;
        if (blocked[startIdx]) return java.util.Collections.emptyList();
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
     * Bake the per-cell walkability flag once. The probe uses the agent's
     * <em>full</em> hitbox so the BFS only approves cells the agent can
     * physically occupy. An earlier version clamped the probe to
     * {@code min(agentSize, tileSize/2)} to keep narrow corridors passable,
     * but the resulting walkability grid disagreed with what
     * {@link com.onelastheist.game.entity.base.MovableEntity#tryMove} could
     * actually thread — the BFS routed the dog into cells where its 56×32
     * hitbox didn't fit because of nearby furniture leg solids, and the dog
     * stuck against the leg every time.
     *
     * <p>Trade-off: any 1-tile-wide passage (48 wu) is correctly rejected
     * for the 56-wide dog. The map needs every passage the dog must
     * traverse to be at least 2 tiles wide (96 wu) — that leaves 40 wu of
     * margin on the cell farther from each wall and keeps a 4-connected
     * BFS solvable through it.
     */
    private boolean[] buildWalkabilityGrid() {
        boolean[] grid = new boolean[width * height];
        float probeW = agentWidth;
        float probeH = agentHeight;
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

    private int worldToTileX(float wx) { return (int) (wx / tileSize); }
    private int worldToTileY(float wy) { return (int) (wy / tileSize); }
    private float tileCenterX(int tx) { return tx * tileSize + tileSize / 2f - agentWidth / 2f - agentOffsetX; }
    private float tileCenterY(int ty) { return ty * tileSize + tileSize / 2f - agentHeight / 2f - agentOffsetY; }
    private boolean inBounds(int tx, int ty) { return tx >= 0 && tx < width && ty >= 0 && ty < height; }
}
