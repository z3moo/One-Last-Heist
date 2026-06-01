package com.onelastheist.game.world;

import com.badlogic.gdx.maps.MapLayer;
import com.badlogic.gdx.maps.MapLayers;
import com.badlogic.gdx.maps.MapObject;
import com.badlogic.gdx.maps.MapGroupLayer;
import com.badlogic.gdx.maps.objects.RectangleMapObject;
import com.badlogic.gdx.maps.tiled.TiledMap;
import com.badlogic.gdx.maps.tiled.TiledMapTileLayer;
import com.badlogic.gdx.math.Rectangle;

import java.util.ArrayList;
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

    private final float worldWidth;
    private final float worldHeight;
    private final List<Rectangle> solids;

    public CollisionMap(TiledMap tiledMap, float unitScale, String collisionLayerName, String[] fallbackSolidLayerNames) {
        TiledMapTileLayer reference = findFirstTileLayer(tiledMap.getLayers());
        int tilesWide = reference.getWidth();
        int tilesHigh = reference.getHeight();
        float tileSize = reference.getTileWidth() * unitScale;
        this.worldWidth = tilesWide * tileSize;
        this.worldHeight = tilesHigh * tileSize;
        this.solids = new ArrayList<>();

        boolean loadedFromObjects = loadCollisionObjects(tiledMap.getLayers(), collisionLayerName, unitScale);
        if (!loadedFromObjects) {
            loadFromTileLayers(tiledMap, unitScale, tilesWide, tilesHigh, tileSize, fallbackSolidLayerNames);
        }
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

    private static MapLayer findLayerByName(MapLayers layers, String name) {
        for (MapLayer layer : layers) {
            if (name.equals(layer.getName())) return layer;
            if (layer instanceof MapGroupLayer) {
                MapLayer nested = findLayerByName(((MapGroupLayer) layer).getLayers(), name);
                if (nested != null) return nested;
            }
        }
        return null;
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
