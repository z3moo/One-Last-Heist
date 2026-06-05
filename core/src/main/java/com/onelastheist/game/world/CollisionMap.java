package com.onelastheist.game.world;

import com.badlogic.gdx.maps.MapLayer;
import com.badlogic.gdx.maps.MapLayers;
import com.badlogic.gdx.maps.MapObject;
import com.badlogic.gdx.maps.MapGroupLayer;
import com.badlogic.gdx.maps.objects.RectangleMapObject;
import com.badlogic.gdx.maps.tiled.TiledMap;
import com.badlogic.gdx.maps.tiled.TiledMapTileLayer;
import com.badlogic.gdx.maps.tiled.objects.TiledMapTileMapObject;
import com.badlogic.gdx.math.Rectangle;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Collision data derived from a TiledMap. Reads the {@code <objectgroup name="Collisions">}
 * authoring layer when present and converts each rectangle into a world-space AABB;
 * {@link com.onelastheist.game.entity.base.MovableEntity} then tests its hitbox
 * against this list during {@code tryMove}.
 *
 * <p>Each Tiled rectangle is shrunk by {@link #SOLID_INSET} on every side at
 * load time. This compensates for artists drawing their collision rect a pixel
 * or two larger than the visible sprite, which after {@code unitScale = 3}
 * becomes ~5–10 world units of phantom edge sticking past every corner —
 * exactly what makes the player feel like the corners are "poking" them.
 *
 * <p>If no {@code Collisions} object group exists, the constructor falls back
 * to rasterizing the listed solid tile layers — every non-empty cell becomes
 * a tile-sized solid rect. Less precise, but enough for prototype maps.
 *
 * <p>External systems (e.g. {@link WorldFactory} registering door blockers)
 * can append more solids via {@link #addSolid(float, float, float, float)}.
 */
public class CollisionMap {
    /** Inset applied to each Tiled rect on load (world units). ~1.3 source pixels. */
    private static final float SOLID_INSET = 4f;
    /** Substring that flags an object group as overhead — rects under its tile-placed objects get pruned so the player walks freely under tall furniture. */
    private static final String OVERHEAD_NAME_TOKEN = "overhead";

    private final float worldWidth;
    private final float worldHeight;
    private final int tilesWide;
    private final int tilesHigh;
    private final float tileSize;
    private final List<Rectangle> solids;

    public CollisionMap(TiledMap tiledMap, float unitScale, String collisionLayerName, String[] fallbackSolidLayerNames) {
        this(tiledMap, unitScale, collisionLayerName, fallbackSolidLayerNames, new String[0]);
    }

    /**
     * Construct with an additional list of tile-layer names that are always
     * rasterized as solid, on top of any objectgroup. Use for layers like
     * {@code walls} / {@code wall_top} where the artist may have only roughly
     * outlined collision in the objectgroup — the tile data itself is the
     * authoritative source of "this cell is solid". Duplicate rects are
     * harmless; rectCollides short-circuits on the first hit.
     */
    public CollisionMap(TiledMap tiledMap, float unitScale, String collisionLayerName,
                        String[] fallbackSolidLayerNames, String[] alwaysRasterizeLayerNames) {
        this(tiledMap, unitScale, collisionLayerName, fallbackSolidLayerNames, alwaysRasterizeLayerNames, null, null);
    }

    /**
     * Construct with an optional pair of "wall-top backfill" layers. Each cell
     * in {@code wallTopBackfillLayerNames} that does <em>not</em> have a tile
     * directly south of it in {@code wallBodyLayerName} is rasterized as a
     * solid — the heuristic is "the artist drew only the visual top, forgot
     * to author the wall body". Skipping cells where the body exists avoids
     * doubling the collision footprint of normal walls (which would seal the
     * room around the player's spawn).
     */
    public CollisionMap(TiledMap tiledMap, float unitScale, String collisionLayerName,
                        String[] fallbackSolidLayerNames, String[] alwaysRasterizeLayerNames,
                        String[] wallTopBackfillLayerNames, String wallBodyLayerName) {
        TiledMapTileLayer reference = findFirstTileLayer(tiledMap.getLayers());
        this.tilesWide = reference.getWidth();
        this.tilesHigh = reference.getHeight();
        this.tileSize = reference.getTileWidth() * unitScale;
        this.worldWidth = tilesWide * tileSize;
        this.worldHeight = tilesHigh * tileSize;
        this.solids = new ArrayList<>();

        boolean loadedFromObjects = loadCollisionObjects(tiledMap.getLayers(), collisionLayerName, unitScale);
        if (!loadedFromObjects) {
            loadFromTileLayers(tiledMap, unitScale, tilesWide, tilesHigh, tileSize, fallbackSolidLayerNames);
        }
        if (alwaysRasterizeLayerNames != null && alwaysRasterizeLayerNames.length > 0) {
            loadFromTileLayers(tiledMap, unitScale, tilesWide, tilesHigh, tileSize, alwaysRasterizeLayerNames);
        }
        if (wallTopBackfillLayerNames != null && wallTopBackfillLayerNames.length > 0 && wallBodyLayerName != null) {
            loadWallTopBackfill(tiledMap, unitScale, tilesWide, tilesHigh, tileSize,
                wallTopBackfillLayerNames, wallBodyLayerName);
        }
        pruneSolidsUnderOverheadObjects(tiledMap.getLayers(), unitScale);
    }

    public boolean rectCollides(float worldX, float worldY, float width, float height) {
        if (worldX < 0f || worldY < 0f) return true;
        if (worldX + width > worldWidth || worldY + height > worldHeight) return true;

        float maxX = worldX + width;
        float maxY = worldY + height;
        for (int i = 0, n = solids.size(); i < n; i++) {
            Rectangle r = solids.get(i);
            if (worldX < r.x + r.width && maxX > r.x && worldY < r.y + r.height && maxY > r.y) {
                return true;
            }
        }
        return false;
    }

    /** Tile pitch in world units. Same as the source map's tile size after {@code unitScale}. */
    public float getTileSize() { return tileSize; }
    /** Map width in tiles. */
    public int getTileWidth() { return tilesWide; }
    /** Map height in tiles. */
    public int getTileHeight() { return tilesHigh; }

    public float getWorldWidth() { return worldWidth; }
    public float getWorldHeight() { return worldHeight; }

    /** Them mot solid rect tu nguon ngoai (vi du Door) sau khi tao xong CollisionMap. */
    public void addSolid(float x, float y, float width, float height) {
        solids.add(new Rectangle(x, y, width, height));
    }

    private boolean loadCollisionObjects(MapLayers layers, String name, float unitScale) {
        MapLayer layer = findLayerByName(layers, name);
        if (layer == null) return false;
        boolean any = false;
        for (MapObject object : layer.getObjects()) {
            if (object instanceof RectangleMapObject) {
                Rectangle r = ((RectangleMapObject) object).getRectangle();
                float x = r.x * unitScale + SOLID_INSET;
                float y = r.y * unitScale + SOLID_INSET;
                float w = Math.max(1f, r.width * unitScale - SOLID_INSET * 2f);
                float h = Math.max(1f, r.height * unitScale - SOLID_INSET * 2f);
                solids.add(new Rectangle(x, y, w, h));
                any = true;
            }
        }
        return any;
    }

    private void loadFromTileLayers(TiledMap tiledMap, float unitScale, int tilesWide, int tilesHigh, float tileSize, String[] solidLayerNames) {
        if (solidLayerNames == null) return;
        boolean[][] blocked = new boolean[tilesWide][tilesHigh];
        for (String name : solidLayerNames) {
            TiledMapTileLayer tileLayer = findTileLayer(tiledMap.getLayers(), name);
            if (tileLayer == null) continue;
            int w = Math.min(tilesWide, tileLayer.getWidth());
            int h = Math.min(tilesHigh, tileLayer.getHeight());
            for (int x = 0; x < w; x++) {
                for (int y = 0; y < h; y++) {
                    if (tileLayer.getCell(x, y) != null) blocked[x][y] = true;
                }
            }
        }
        for (int x = 0; x < tilesWide; x++) {
            for (int y = 0; y < tilesHigh; y++) {
                if (blocked[x][y]) {
                    solids.add(new Rectangle(x * tileSize, y * tileSize, tileSize, tileSize));
                }
            }
        }
    }

    /**
     * Wall-top backfill: rasterize a top-decoration tile layer's cell <em>only
     * when</em> the body layer has no tile directly south of it (one libGDX
     * row lower in Y). The heuristic targets the case where an artist drew a
     * wall as just the visual top (no body cell), which would otherwise be
     * walk-through-able with the body-only collision rasterization.
     *
     * <p>For normal walls — body cell present, top cell present above it —
     * the body layer's south-neighbor check passes, so we skip the top cell.
     * That avoids extending every wall's collision one tile north into
     * playable space, which would seal rooms around the player's spawn.
     */
    private void loadWallTopBackfill(TiledMap tiledMap, float unitScale, int tilesWide, int tilesHigh, float tileSize,
                                     String[] topLayerNames, String wallBodyLayerName) {
        TiledMapTileLayer body = findTileLayer(tiledMap.getLayers(), wallBodyLayerName);
        if (body == null) return;
        int bodyW = body.getWidth();
        int bodyH = body.getHeight();
        for (String topName : topLayerNames) {
            TiledMapTileLayer top = findTileLayer(tiledMap.getLayers(), topName);
            if (top == null) continue;
            int w = Math.min(tilesWide, top.getWidth());
            int h = Math.min(tilesHigh, top.getHeight());
            for (int x = 0; x < w; x++) {
                for (int y = 0; y < h; y++) {
                    if (top.getCell(x, y) == null) continue;
                    // South neighbor in libGDX bottom-up = lower Y. If the
                    // body layer has a tile there, the top is just decorative
                    // for an existing wall — don't double its collision.
                    int sy = y - 1;
                    if (sy >= 0 && sy < bodyH && x < bodyW && body.getCell(x, sy) != null) continue;
                    solids.add(new Rectangle(x * tileSize, y * tileSize, tileSize, tileSize));
                }
            }
        }
    }

    private static MapLayer findLayerByName(MapLayers layers, String name) {
        for (MapLayer layer : layers) {
            // Case-insensitive: different artists name the layer "Collisions" vs "collisions";
            // matching exactly would silently drop one or the other.
            if (layer.getName() != null && layer.getName().equalsIgnoreCase(name)) return layer;
            if (layer instanceof MapGroupLayer) {
                MapLayer nested = findLayerByName(((MapGroupLayer) layer).getLayers(), name);
                if (nested != null) return nested;
            }
        }
        return null;
    }

    /** Solids larger than this in either dimension (world units) are never pruned. ~3 tiles — distinguishes a long wall rect from a small furniture leg. */
    private static final float PRUNE_MAX_DIMENSION = 144f;

    /**
     * Drop collision rects that lie entirely under a tile-placed object in an
     * overhead-named object group <em>and</em> are small enough to be a piece
     * of furniture rather than a wall section. Tiled authors furniture as
     * "table top in {@code object_overhead}, table legs in {@code collisions}"
     * so the player passes <em>visually</em> behind the top while the legs
     * still block — but with the renderer drawing the top above the player,
     * the leg collisions read as "invisible blocks under the furniture".
     * Pruning them lets the player walk freely under tall furniture.
     *
     * <p>The double gate is what keeps walls intact:
     * <ul>
     *   <li><b>Fully contained.</b> A solid that pokes outside any overhead
     *       object's footprint isn't decoration leg — could be the corner of
     *       a wall.</li>
     *   <li><b>Both dimensions ≤ {@link #PRUNE_MAX_DIMENSION}.</b> Walls in
     *       this map can run 240+ source-px long after scale (~720+ world
     *       units). Even when a long overhead decoration covers a long wall
     *       section, the wall's height usually exceeds the cutoff. The
     *       hard-and-tall test catches the no-collision-on-walls regression
     *       the previous center-only and contained-only rules both leaked.</li>
     * </ul>
     */
    private void pruneSolidsUnderOverheadObjects(MapLayers layers, float unitScale) {
        List<Rectangle> overheadRects = new ArrayList<>();
        collectOverheadObjectRects(layers, unitScale, overheadRects);
        if (overheadRects.isEmpty()) return;

        for (Iterator<Rectangle> it = solids.iterator(); it.hasNext(); ) {
            Rectangle solid = it.next();
            // Furniture-leg sized? Both dims under the cutoff. Walls are long
            // in at least one axis and survive on this gate alone.
            if (solid.width > PRUNE_MAX_DIMENSION || solid.height > PRUNE_MAX_DIMENSION) continue;
            for (int i = 0, n = overheadRects.size(); i < n; i++) {
                Rectangle r = overheadRects.get(i);
                if (solid.x >= r.x
                    && solid.y >= r.y
                    && solid.x + solid.width <= r.x + r.width
                    && solid.y + solid.height <= r.y + r.height) {
                    it.remove();
                    break;
                }
            }
        }
    }

    /**
     * Walk the layer tree (including nested groups) and append a world-space
     * AABB for every {@link TiledMapTileMapObject} living in an object group
     * whose name contains {@link #OVERHEAD_NAME_TOKEN}. Plain RectangleMapObject
     * children of those groups are ignored — only tile-placed visuals count as
     * "the thing the player should walk under".
     */
    private static void collectOverheadObjectRects(MapLayers layers, float unitScale, List<Rectangle> out) {
        for (MapLayer layer : layers) {
            if (layer instanceof MapGroupLayer) {
                collectOverheadObjectRects(((MapGroupLayer) layer).getLayers(), unitScale, out);
                continue;
            }
            if (layer instanceof TiledMapTileLayer) continue;
            String name = layer.getName();
            if (name == null || !name.toLowerCase().contains(OVERHEAD_NAME_TOKEN)) continue;
            for (MapObject obj : layer.getObjects()) {
                if (!(obj instanceof TiledMapTileMapObject)) continue;
                TiledMapTileMapObject tileObj = (TiledMapTileMapObject) obj;
                Object wProp = obj.getProperties().get("width");
                Object hProp = obj.getProperties().get("height");
                float w = (wProp instanceof Number ? ((Number) wProp).floatValue()
                    : tileObj.getTile().getTextureRegion().getRegionWidth()) * unitScale;
                float h = (hProp instanceof Number ? ((Number) hProp).floatValue()
                    : tileObj.getTile().getTextureRegion().getRegionHeight()) * unitScale;
                out.add(new Rectangle(tileObj.getX() * unitScale, tileObj.getY() * unitScale, w, h));
            }
        }
    }

    private static TiledMapTileLayer findFirstTileLayer(MapLayers layers) {
        for (MapLayer layer : layers) {
            if (layer instanceof TiledMapTileLayer) return (TiledMapTileLayer) layer;
            if (layer instanceof MapGroupLayer) {
                TiledMapTileLayer nested = findFirstTileLayer(((MapGroupLayer) layer).getLayers());
                if (nested != null) return nested;
            }
        }
        throw new IllegalArgumentException("TiledMap khong co tile layer nao");
    }

    private static TiledMapTileLayer findTileLayer(MapLayers layers, String name) {
        for (MapLayer layer : layers) {
            if (layer instanceof TiledMapTileLayer && name.equals(layer.getName())) {
                return (TiledMapTileLayer) layer;
            }
            if (layer instanceof MapGroupLayer) {
                TiledMapTileLayer nested = findTileLayer(((MapGroupLayer) layer).getLayers(), name);
                if (nested != null) return nested;
            }
        }
        return null;
    }
}
