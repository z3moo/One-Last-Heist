package com.onelastheist.game.render;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.maps.MapLayer;
import com.badlogic.gdx.maps.MapLayers;
import com.badlogic.gdx.maps.MapGroupLayer;
import com.badlogic.gdx.maps.MapObject;
import com.badlogic.gdx.maps.objects.RectangleMapObject;
import com.badlogic.gdx.maps.tiled.TiledMap;
import com.badlogic.gdx.maps.tiled.TiledMapTileLayer;
import com.badlogic.gdx.maps.tiled.objects.TiledMapTileMapObject;
import com.badlogic.gdx.maps.tiled.renderers.OrthogonalTiledMapRenderer;
import com.badlogic.gdx.utils.Disposable;
import com.onelastheist.game.entity.base.MovableEntity;
import com.onelastheist.game.entity.npc.Dog;
import com.onelastheist.game.entity.player.Player;
import com.onelastheist.game.environment.DroppedMeat;
import com.onelastheist.game.environment.KeyPickup;
import com.onelastheist.game.environment.MeatPickup;
import com.onelastheist.game.world.GameWorld;
import com.onelastheist.game.world.WorldFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Renders {@link GameWorld} state every frame. Owns no gameplay logic — it only
 * reads the world's positions/states and draws them.
 *
 * <h3>Render plan</h3>
 *
 * <p>Walks every TMX top-level layer in declaration order but splits them into
 * three buckets:
 *
 * <ol>
 *   <li><b>Floor pass</b> — floors, walls' body, decor, bathroom, furniture
 *       objects. Drawn first; everything else lays on top.</li>
 *   <li><b>Y-sort pass</b> — wall caps ({@code wall_top}/{@code wall_top2}),
 *       ceiling trim ({@code overhead}/{@code overhead_decor}), tall furniture
 *       in {@code object_overhead}, and the actors (dog, player, homeowner,
 *       meat placeholders). Each entry has an anchor Y; the list is sorted by
 *       anchor Y <em>descending</em> so the highest-Y items draw first
 *       (further into the back) and the lowest-Y items draw last (on top in
 *       a libGDX bottom-up world). That gives the desired effect: a player
 *       in the hallway south of a wall has a lower foot Y than the wall cap
 *       and ends up on top; a player who steps north into the room has a
 *       higher foot Y than the cap and the cap occludes them.</li>
 *   <li><b>Anything else</b> — any remaining layers after the last named
 *       Y-sort layer simply append to a small post pass. The TMX has none
 *       today; the bucket exists in case art adds a future "always over
 *       everything" UI / lighting overlay.</li>
 * </ol>
 *
 * <p><b>Why we manually draw tile-placed objects:</b> in libGDX 1.14,
 * {@code BatchTiledMapRenderer.renderObject(MapObject)} is an empty stub.
 * Calling {@code renderObjects(layer)} therefore draws nothing. Every tile
 * placed in Tiled as an <em>object</em> rather than a tile-layer cell —
 * which is most of the indoor furniture, the bushes/decor in the exterior's
 * {@code Objects_bh}, and the overhead rooftops in {@code object_overhead} —
 * gets dropped on the floor unless we draw them ourselves. We do that in
 * {@link #drawObjectGroup} (single-pass case) and in
 * {@link YSortObjectItem#draw} (Y-sort case) by reading each
 * {@link TiledMapTileMapObject}'s tile region and drawing it at the object's
 * world coords.
 *
 * <p>The map can be swapped at runtime (exterior ↔ interior). The renderer
 * watches {@link GameWorld#getMapVersion()} and rebuilds its cached layer plan
 * whenever the version changes, so a single WorldRenderer instance survives
 * every map swap.
 *
 * <p>Animation handling: every walk/idle sheet is split into 8 directional rows
 * of {@link #FRAME_SIZE}px frames; {@link #createAnimations} converts each row
 * into a LibGDX {@link Animation}. The crouch sheet starts its useful frames
 * at column 3 (frames 0-2 are skipped via {@link #CROUCH_WALK_STARTS}).
 */
public class WorldRenderer implements Disposable {
    /**
     * Layer-name substrings (case-insensitive) that mark a layer as Y-sortable
     * — its tiles/objects are drawn interleaved with actors based on anchor Y.
     * Tile layers in this list are decomposed cell-by-cell at map-load time
     * into {@link YSortTileItem}s so each cell can race the player on Y.
     * Object groups become per-object {@link YSortObjectItem}s.
     *
     * <p>Tokens covered:
     * <ul>
     *   <li>"wall" — every wall tile layer (walls, wall_top, walls_top,
     *       wall_decor, wall_top2). All wall art needs Y-sort: a player
     *       whose foot is below a wall cell's bottom (south) draws ON TOP,
     *       while a player whose foot is above (north, inside the room)
     *       draws BEHIND.</li>
     *   <li>"bathroom" — bathroom-furniture tile layer carries tubs/toilets/
     *       sinks (multi-row tall fixtures). They must occlude the player
     *       when stepped behind.</li>
     *   <li>"overhead" — despite the name, the interior {@code overhead} and
     *       {@code overhead_decor} layers carry the north-face wall art of
     *       upper rooms; {@code object_overhead} carries tall furniture
     *       tops (plants, bookcases, stuffed animals). All three need
     *       Y-sort, not always-on-top — otherwise upper rooms appear
     *       sealed by an "invisible wall" that the player can never step
     *       in front of.</li>
     * </ul>
     *
     * <p>If a future map adds a true ceiling-canopy layer (player walks
     * <em>under</em> it, e.g. a doorway top), introduce a separate
     * always-over-actors post-pass with its own token list.
     */
    private static final String[] Y_SORT_LAYER_TOKENS = {
        "wall",
        "bathroom",
        "overhead",
    };
    /** Object group whose name (case-insensitive) matches this is collision-only and never rendered. */
    private static final String COLLISION_LAYER_NAME = "collisions";
    private static final int FRAME_SIZE = 48;
    private static final int DIRECTION_COUNT = 8;
    private static final int FRAME_COUNT = 5;
    /** World-space draw size for human-sized actors (player, homeowner). */
    private static final float DRAW_SIZE = 144f;
    /** Slightly smaller than humans so the dog reads visually as a pet. */
    private static final float DOG_DRAW_SIZE = 120f;
    /** Max world-space dimension for floor item sprites. */
    private static final float ITEM_DRAW_SIZE = 36f;
    private static final float FRAME_DURATION = 0.14f;
    private static final float CROUCH_IDLE_FRAME_DURATION = 0.22f;
    private static final float DOG_SLEEP_FRAME_DURATION = 0.32f;
    /** Column index where each row's crouch-walk loop actually starts. */
    private static final int[] CROUCH_WALK_STARTS = {3, 3, 3, 3, 3, 3, 3, 3};
    private static final int[] CROUCH_IDLE_STARTS = {3, 3, 3, 3, 3, 3, 3, 3};
    /** First three columns of the dog_sleep sheet are wake-up frames; the looping sleep cycle starts at column 3. */
    private static final int DOG_SLEEP_FRAME_START = 3;

    private final GameWorld world;
    private final SpriteBatch batch = new SpriteBatch();
    private OrthogonalTiledMapRenderer mapRenderer;
    /** Map layers drawn before the Y-sort pass, in TMX order — floors and floor decor (the "always under" stuff). */
    private final List<LayerOp> floorPass = new ArrayList<>();
    /** Static (map-derived) Y-sortable items. Built once per map; reused every frame. */
    private final List<YSortItem> staticYSortItems = new ArrayList<>();
    /** Reused render list combining static Y-sort items + per-frame actor items. Cleared and refilled each frame. */
    private final List<YSortItem> frameYSortBuffer = new ArrayList<>();
    /** Sort highest anchor Y first, lowest last — so southernmost (visually nearest) items draw on top. */
    private static final Comparator<YSortItem> Y_SORT_DESCENDING = new Comparator<YSortItem>() {
        @Override public int compare(YSortItem a, YSortItem b) {
            return Float.compare(b.anchorY(), a.anchorY());
        }
    };
    /** Last observed map version; tracking this is what triggers a renderer rebuild. */
    private int rendererMapVersion = -1;
    private final Texture playerIdleTexture = loadPixelTexture("characters/player/player_idle.png");
    private final Texture playerWalkTexture = loadPixelTexture("characters/player/player_walk.png");
    private final Texture playerCrouchTexture = loadPixelTexture("characters/player/player_crouch.png");
    private final Texture ownerIdleTexture = loadPixelTexture("characters/enemies/neighbour/enemies_idle.png");
    private final Texture ownerWalkTexture = loadPixelTexture("characters/enemies/neighbour/enemies_walk.png");
    private final Texture dogWalkTexture = loadPixelTexture("characters/enemies/dog/dog_walk.png");
    private final Texture dogSleepTexture = loadPixelTexture("characters/enemies/dog/dog_sleep.png");
    private final Texture meatTexture = loadPixelTexture("items/meat.png");
    private final Texture keyTexture = loadPixelTexture("items/key.png");
    private final List<Animation<TextureRegion>> playerIdle = createAnimations(playerIdleTexture);
    private final List<Animation<TextureRegion>> playerWalk = createAnimations(playerWalkTexture);
    private final List<Animation<TextureRegion>> playerCrouchWalk = createAnimations(playerCrouchTexture, FRAME_DURATION, CROUCH_WALK_STARTS, FRAME_COUNT);
    private final List<Animation<TextureRegion>> playerCrouchIdle = createAnimations(playerCrouchTexture, CROUCH_IDLE_FRAME_DURATION, CROUCH_IDLE_STARTS, FRAME_COUNT);
    private final List<Animation<TextureRegion>> ownerIdle = createAnimations(ownerIdleTexture);
    private final List<Animation<TextureRegion>> ownerWalk = createAnimations(ownerWalkTexture);
    private final List<Animation<TextureRegion>> dogWalk = createAnimations(dogWalkTexture);
    private final Animation<TextureRegion> dogSleep = createSleepAnimation(dogSleepTexture, DOG_SLEEP_FRAME_DURATION, DOG_SLEEP_FRAME_START);
    private float stateTime;

    public WorldRenderer(GameWorld world) {
        this.world = world;
        rebuildMapRenderer();
    }

    /**
     * Frame entry point. Draws floor map layers, then a single Y-sorted
     * pass containing wall/cap/overhead tiles, every furniture object, and
     * actors interleaved by foot Y. There is intentionally no
     * "always-on-top" overhead pass: every layer either sits under the
     * player (floor pass) or interleaves with the player by Y (Y-sort pass).
     */
    public void render(float deltaSeconds, OrthographicCamera camera) {
        stateTime += deltaSeconds;
        refreshIfMapChanged();

        mapRenderer.setView(camera);
        runPlan(floorPass);

        // One unified Y-sorted draw — same Batch for tiles and actors so order
        // is preserved. mapRenderer.getBatch() is the renderer's internal
        // SpriteBatch; using it for actors keeps the projection matrix that
        // mapRenderer.setView() configured, and avoids a redundant begin/end
        // on a separate batch.
        Batch sortBatch = mapRenderer.getBatch();
        sortBatch.begin();
        try {
            buildFrameYSortBuffer();
            Collections.sort(frameYSortBuffer, Y_SORT_DESCENDING);
            for (int i = 0, n = frameYSortBuffer.size(); i < n; i++) {
                frameYSortBuffer.get(i).draw(sortBatch, this);
            }
        } finally {
            sortBatch.end();
        }
    }

    public void renderPlayerOnly(float deltaSeconds, OrthographicCamera camera) {
        stateTime += deltaSeconds;

        batch.setProjectionMatrix(camera.combined);
        batch.begin();
        drawPlayer(world.getPlayer(), batch);
        batch.end();
    }

    public GameWorld getWorld() { return world; }

    @Override
    public void dispose() {
        if (mapRenderer != null) mapRenderer.dispose();
        batch.dispose();
        playerIdleTexture.dispose();
        playerWalkTexture.dispose();
        playerCrouchTexture.dispose();
        ownerIdleTexture.dispose();
        ownerWalkTexture.dispose();
        dogWalkTexture.dispose();
        dogSleepTexture.dispose();
        meatTexture.dispose();
        keyTexture.dispose();
    }

    private void refreshIfMapChanged() {
        if (rendererMapVersion != world.getMapVersion()) {
            rebuildMapRenderer();
        }
    }

    /** Run a cached pass plan, drawing each layer in its TMX-declaration order. */
    private void runPlan(List<LayerOp> plan) {
        for (int i = 0, n = plan.size(); i < n; i++) {
            plan.get(i).run(mapRenderer);
        }
    }

    /**
     * Build (or rebuild) the OrthogonalTiledMapRenderer plus the floor pass
     * plan and the static Y-sort item list for the world's currently-active
     * TiledMap. Called at construction and whenever the map version changes.
     *
     * <p>Classification per top-level layer:
     * <ul>
     *   <li>Collision-only group → skip</li>
     *   <li>Y-sort-named layer (wall/bathroom/overhead) or any object group with tile objects → Y-sort</li>
     *   <li>Otherwise → floor pass (always under actors)</li>
     * </ul>
     */
    private void rebuildMapRenderer() {
        if (mapRenderer != null) mapRenderer.dispose();
        TiledMap activeMap = world.getTiledMap();
        mapRenderer = new OrthogonalTiledMapRenderer(activeMap, WorldFactory.MAP_UNIT_SCALE);

        floorPass.clear();
        staticYSortItems.clear();
        MapLayers layers = activeMap.getLayers();
        for (int i = 0, n = layers.getCount(); i < n; i++) {
            MapLayer layer = layers.get(i);
            String name = layer.getName();
            if (isCollisionLayer(name) || isCollisionOnlyGroup(layer)) continue;

            if (matchesYSortLayer(name) || hasTileObjects(layer)) {
                addYSortItemsFromLayer(layer, i);
            } else {
                LayerOp op = classifyFloorLayer(layer, i);
                if (op != null) floorPass.add(op);
            }
        }
        rendererMapVersion = world.getMapVersion();
    }

    private static boolean isCollisionLayer(String name) {
        return name != null && name.equalsIgnoreCase(COLLISION_LAYER_NAME);
    }

    private static boolean matchesYSortLayer(String name) {
        if (name == null) return false;
        String lower = name.toLowerCase();
        for (String token : Y_SORT_LAYER_TOKENS) {
            if (lower.contains(token)) return true;
        }
        return false;
    }

    private static boolean hasTileObjects(MapLayer layer) {
        if (layer instanceof TiledMapTileLayer || layer instanceof MapGroupLayer) return false;
        for (MapObject obj : layer.getObjects()) {
            if (obj instanceof TiledMapTileMapObject) return true;
        }
        return false;
    }

    /**
     * Decompose one Y-sortable map layer into per-cell or per-object items
     * appended to {@link #staticYSortItems}. A tile layer turns into one
     * {@link YSortTileItem} per non-empty cell; an object group turns into
     * one {@link YSortObjectItem} per tile-placed object.
     *
     * <p>Wall-run anchor: for tile layers, every cell's anchor Y is the
     * bottom of its contiguous south-running stack <em>in the same layer</em>.
     * A single-tile wall is unchanged (its run is itself), but a 3-tile-tall
     * wall has all three cells anchored to the bottom cell's Y — the wall's
     * south face. Without this, the upper cells anchor too high and the
     * player draws on top of them whenever they walk just south of the wall
     * (foot Y below the bottom cell's anchor but above the upper cells').
     * Anchoring the whole run at the south face means the entire wall
     * occludes any player whose foot Y is at or above the wall's south edge.
     */
    private void addYSortItemsFromLayer(MapLayer layer, int topLevelIndex) {
        if (layer instanceof TiledMapTileLayer) {
            TiledMapTileLayer tileLayer = (TiledMapTileLayer) layer;
            float scale = WorldFactory.MAP_UNIT_SCALE;
            float tileW = tileLayer.getTileWidth() * scale;
            float tileH = tileLayer.getTileHeight() * scale;
            int height = tileLayer.getHeight();
            int width = tileLayer.getWidth();
            // Pre-compute per-(col, libGDX-row) bottom-of-run anchor: the
            // libGDX y of the southernmost cell in the same-column contiguous
            // run that this cell belongs to. One bottom-up sweep per column.
            float[] anchorPerCell = new float[width * height];
            for (int x = 0; x < width; x++) {
                float runBottomY = 0f;
                boolean inRun = false;
                for (int y = 0; y < height; y++) {
                    TiledMapTileLayer.Cell cell = tileLayer.getCell(x, y);
                    boolean filled = cell != null && cell.getTile() != null;
                    if (filled) {
                        if (!inRun) {
                            runBottomY = y * tileH;
                            inRun = true;
                        }
                        anchorPerCell[y * width + x] = runBottomY;
                    } else {
                        inRun = false;
                    }
                }
            }
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    TiledMapTileLayer.Cell cell = tileLayer.getCell(x, y);
                    if (cell == null || cell.getTile() == null) continue;
                    TextureRegion region = cell.getTile().getTextureRegion();
                    if (region == null) continue;
                    // Cell origin is the bottom-left of the tile-grid cell, but
                    // the tile's pixel region may be larger than tileW × tileH
                    // (windows are 16×48, wall decor pictures are 16×32 in this
                    // map). Tiled anchors such tiles at the cell's bottom and
                    // lets them extend UP — so we draw at region-sized w/h, not
                    // tile-sized. Hardcoding tileW/tileH would compress the
                    // sprite into a single cell and crop the rest off.
                    float worldX = x * tileW;
                    float worldY = y * tileH;
                    float drawW = region.getRegionWidth() * scale;
                    float drawH = region.getRegionHeight() * scale;
                    float anchorY = anchorPerCell[y * width + x];
                    staticYSortItems.add(new YSortTileItem(
                        region, worldX, worldY, drawW, drawH, anchorY,
                        cell.getFlipHorizontally(), cell.getFlipVertically(), cell.getRotation()));
                }
            }
            return;
        }
        // Object group: each tile-placed object becomes one item. We pre-pass
        // the group to detect "stacked" composites — multi-tile furniture
        // (lamps, dressers, chairs) and trees built from several adjacent
        // tile objects share a single anchor at the southernmost piece's
        // bottom. Without that, each piece anchors at its own bottom and the
        // player passes through them ONE PIECE AT A TIME as foot Y crosses
        // each piece's anchor, instead of past the whole composite at once.
        //
        // Detection: object B "supports" object A if B's top edge meets A's
        // bottom edge (within a 1 wu tolerance) and their X ranges overlap.
        // The transitive root of the supports chain is the run bottom.
        List<TileObjInfo> infos = new ArrayList<>();
        float scale = WorldFactory.MAP_UNIT_SCALE;
        for (MapObject obj : layer.getObjects()) {
            if (!(obj instanceof TiledMapTileMapObject)) continue;
            TiledMapTileMapObject tileObj = (TiledMapTileMapObject) obj;
            TextureRegion region = tileObj.getTile().getTextureRegion();
            if (region == null) continue;
            float x = tileObj.getX() * scale;
            float y = tileObj.getY() * scale;
            Object wProp = obj.getProperties().get("width");
            Object hProp = obj.getProperties().get("height");
            float w = (wProp instanceof Number ? ((Number) wProp).floatValue() : region.getRegionWidth()) * scale;
            float h = (hProp instanceof Number ? ((Number) hProp).floatValue() : region.getRegionHeight()) * scale;
            infos.add(new TileObjInfo(tileObj, region, x, y, w, h));
        }
        // Find each object's supporter (the one directly below it). N² scan;
        // object groups stay under a few hundred entries so this is cheap.
        final float STACK_TOLERANCE = 1f;
        for (TileObjInfo a : infos) {
            for (TileObjInfo b : infos) {
                if (b == a) continue;
                if (Math.abs(b.worldY + b.h - a.worldY) < STACK_TOLERANCE
                    && a.worldX < b.worldX + b.w
                    && a.worldX + a.w > b.worldX) {
                    a.supporter = b;
                    break;
                }
            }
        }
        for (TileObjInfo info : infos) {
            // Walk the support chain to its root with a hard cap so a cyclic
            // supports relation (shouldn't happen, but defensive) can't hang.
            TileObjInfo bottom = info;
            int safety = infos.size() + 1;
            while (bottom.supporter != null && safety-- > 0) bottom = bottom.supporter;
            staticYSortItems.add(new YSortObjectItem(
                info.region, info.worldX, info.worldY, info.w, info.h, bottom.worldY,
                info.obj.isFlipHorizontally(), info.obj.isFlipVertically()));
        }
    }

    /** Working entry for {@link #addYSortItemsFromLayer}'s composite-detection pass. */
    private static final class TileObjInfo {
        final TiledMapTileMapObject obj;
        final TextureRegion region;
        final float worldX;
        final float worldY;
        final float w;
        final float h;
        TileObjInfo supporter;
        TileObjInfo(TiledMapTileMapObject obj, TextureRegion region, float worldX, float worldY, float w, float h) {
            this.obj = obj; this.region = region;
            this.worldX = worldX; this.worldY = worldY;
            this.w = w; this.h = h;
        }
    }

    /** Build the floor-pass op for a non-Y-sort layer, or null if it has no visuals. */
    private LayerOp classifyFloorLayer(MapLayer layer, int topLevelIndex) {
        if (layer instanceof TiledMapTileLayer || layer instanceof MapGroupLayer) {
            return new TileLayerOp(topLevelIndex);
        }
        return new ObjectLayerOp(layer);
    }

    /**
     * Compose the full per-frame Y-sort buffer: every static map item plus
     * the dynamic actor items (meat placeholders, dog/homeowner, player).
     * The buffer is mutated in place and re-sorted each frame.
     */
    private void buildFrameYSortBuffer() {
        frameYSortBuffer.clear();
        frameYSortBuffer.addAll(staticYSortItems);

        // Meat placeholders (interior only). Anchor Y is the meat's own Y so
        // it sorts naturally with floor tiles and actors.
        if (world.isInInterior()) {
            for (MeatPickup pickup : world.getInteriorPickups()) {
                frameYSortBuffer.add(new YSortMeatItem(pickup.getX(), pickup.getY(), pickup.getMeat().isDrugged()));
            }
            for (DroppedMeat dropped : world.getInteriorDroppedMeat()) {
                frameYSortBuffer.add(new YSortMeatItem(dropped.getX(), dropped.getY(), dropped.isDrugged()));
            }
        }
        for (KeyPickup pickup : world.getKeyPickups()) {
            frameYSortBuffer.add(new YSortKeyItem(pickup.getX(), pickup.getY()));
        }

        // Actors. Anchor Y is the foot, not the sprite origin — that's what
        // makes a player at the south wall correctly sort over the wall cap.
        if (!world.isInInterior()) {
            if (world.getHomeOwner().isVisible()) {
                frameYSortBuffer.add(new YSortActorItem(world.getHomeOwner(), ActorKind.HOMEOWNER));
            }
        } else {
            if (world.hasActiveDog()) {
                frameYSortBuffer.add(new YSortActorItem(world.getDog(), ActorKind.DOG));
            }
        }
        frameYSortBuffer.add(new YSortActorItem(world.getPlayer(), ActorKind.PLAYER));
    }

    /**
     * True when every object in the group is a plain rectangle with no tile
     * GID — i.e. the layer is purely collision/trigger metadata and has no
     * visuals to render.
     */
    private static boolean isCollisionOnlyGroup(MapLayer layer) {
        if (layer instanceof TiledMapTileLayer || layer instanceof MapGroupLayer) return false;
        boolean any = false;
        for (MapObject obj : layer.getObjects()) {
            any = true;
            if (obj instanceof TiledMapTileMapObject) return false;
            // A tile-placed object in Tiled XML carries a `gid`. Plain rect
            // collisions don't.
            if (obj.getProperties().get("gid") != null) return false;
            if (!(obj instanceof RectangleMapObject)) return false;
        }
        return any;
    }

    /**
     * Draw every {@link TiledMapTileMapObject} in this group. libGDX's built-in
     * {@code renderObject} is a stub, so we draw the object's tile region
     * ourselves. Used only for non-Y-sort object layers (e.g. exterior
     * {@code Objects_bh}); Y-sortable object groups go through
     * {@link YSortObjectItem} instead.
     */
    private static void drawObjectGroup(OrthogonalTiledMapRenderer renderer, MapLayer layer) {
        Batch tileBatch = renderer.getBatch();
        tileBatch.begin();
        try {
            float scale = WorldFactory.MAP_UNIT_SCALE;
            for (MapObject obj : layer.getObjects()) {
                if (!(obj instanceof TiledMapTileMapObject)) continue;
                TiledMapTileMapObject tileObj = (TiledMapTileMapObject) obj;
                TextureRegion region = tileObj.getTile().getTextureRegion();
                if (region == null) continue;

                float x = tileObj.getX() * scale;
                float y = tileObj.getY() * scale;

                Object wProp = obj.getProperties().get("width");
                Object hProp = obj.getProperties().get("height");
                float w = (wProp instanceof Number ? ((Number) wProp).floatValue() : region.getRegionWidth()) * scale;
                float h = (hProp instanceof Number ? ((Number) hProp).floatValue() : region.getRegionHeight()) * scale;

                boolean flipH = tileObj.isFlipHorizontally();
                boolean flipV = tileObj.isFlipVertically();
                if (flipH || flipV) {
                    region.flip(flipH, flipV);
                    tileBatch.draw(region, x, y, w, h);
                    region.flip(flipH, flipV);
                } else {
                    tileBatch.draw(region, x, y, w, h);
                }
            }
        } finally {
            tileBatch.end();
        }
    }

    private void drawPlayer(Player player, Batch outputBatch) {
        if (player.isCrouching()) {
            if (player.isMoving()) {
                drawAnimation(player, playerCrouchWalk, outputBatch);
            } else {
                drawAnimation(player, playerCrouchIdle, outputBatch);
            }
            return;
        }

        drawActor(player, playerIdle, playerWalk, outputBatch);
    }

    private void drawDog(Dog dog, Batch outputBatch) {
        if (dog.isSleeping()) {
            TextureRegion frame = dogSleep.getKeyFrame(stateTime, true);
            outputBatch.draw(frame, dog.getX(), dog.getY(), DOG_DRAW_SIZE, DOG_DRAW_SIZE);
            return;
        }

        int spriteRow = dog.getFacingDirection().getSpriteRow();
        TextureRegion frame = dogWalk.get(spriteRow).getKeyFrame(stateTime, true);
        outputBatch.draw(frame, dog.getX(), dog.getY(), DOG_DRAW_SIZE, DOG_DRAW_SIZE);
    }

    private void drawActor(MovableEntity actor, List<Animation<TextureRegion>> idle, List<Animation<TextureRegion>> walk, Batch outputBatch) {
        int spriteRow = actor.getFacingDirection().getSpriteRow();
        Animation<TextureRegion> animation = actor.isMoving() ? walk.get(spriteRow) : idle.get(spriteRow);
        TextureRegion frame = animation.getKeyFrame(stateTime, true);
        outputBatch.draw(frame, actor.getX(), actor.getY(), DRAW_SIZE, DRAW_SIZE);
    }

    private void drawAnimation(MovableEntity actor, List<Animation<TextureRegion>> animations, Batch outputBatch) {
        int spriteRow = actor.getFacingDirection().getSpriteRow();
        TextureRegion frame = animations.get(spriteRow).getKeyFrame(stateTime, true);
        outputBatch.draw(frame, actor.getX(), actor.getY(), DRAW_SIZE, DRAW_SIZE);
    }

    private void drawMeatItem(Batch outputBatch, float x, float y, boolean drugged) {
        drawCenteredItem(outputBatch, meatTexture, x, y);
    }

    private void drawKeyItem(Batch outputBatch, float x, float y) {
        drawCenteredItem(outputBatch, keyTexture, x, y);
    }

    private static void drawCenteredItem(Batch outputBatch, Texture texture, float centerX, float centerY) {
        float width = texture.getWidth();
        float height = texture.getHeight();
        float scale = ITEM_DRAW_SIZE / Math.max(width, height);
        float drawW = width * scale;
        float drawH = height * scale;
        outputBatch.draw(texture, centerX - drawW / 2f, centerY - drawH / 2f, drawW, drawH);
    }

    private static Texture loadPixelTexture(String path) {
        Texture texture = new Texture(path);
        texture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        return texture;
    }

    private static List<Animation<TextureRegion>> createAnimations(Texture texture) {
        return createAnimations(texture, FRAME_DURATION, 0, TextureRegion.split(texture, FRAME_SIZE, FRAME_SIZE)[0].length);
    }

    /**
     * Build a single-row animation that skips a leading prefix of frames. Used
     * for the dog sleep sheet where the first {@code startFrame} columns are
     * a wake-up sequence rather than the looping idle-sleep cycle. The frame
     * size is inferred from the sheet's height (square frames).
     */
    private static Animation<TextureRegion> createSleepAnimation(Texture texture, float frameDuration, int startFrame) {
        int frameSize = texture.getHeight();
        TextureRegion[][] sheet = TextureRegion.split(texture, frameSize, frameSize);
        TextureRegion[] full = sheet[0];
        int len = Math.max(0, full.length - startFrame);
        TextureRegion[] frames = new TextureRegion[len];
        System.arraycopy(full, startFrame, frames, 0, len);
        return new Animation<>(frameDuration, frames);
    }

    private static List<Animation<TextureRegion>> createAnimations(Texture texture, float frameDuration, int startFrame, int endFrameExclusive) {
        TextureRegion[][] sheetFrames = TextureRegion.split(texture, FRAME_SIZE, FRAME_SIZE);
        List<Animation<TextureRegion>> animations = new ArrayList<>(DIRECTION_COUNT);

        for (int row = 0; row < DIRECTION_COUNT; row++) {
            TextureRegion[] frames = new TextureRegion[endFrameExclusive - startFrame];
            System.arraycopy(sheetFrames[row], startFrame, frames, 0, frames.length);
            animations.add(new Animation<>(frameDuration, frames));
        }

        return animations;
    }

    private static List<Animation<TextureRegion>> createAnimations(Texture texture, float frameDuration, int[] startFrames, int endFrameExclusive) {
        TextureRegion[][] sheetFrames = TextureRegion.split(texture, FRAME_SIZE, FRAME_SIZE);
        List<Animation<TextureRegion>> animations = new ArrayList<>(DIRECTION_COUNT);

        for (int row = 0; row < DIRECTION_COUNT; row++) {
            int startFrame = startFrames[row];
            TextureRegion[] frames = new TextureRegion[endFrameExclusive - startFrame];
            System.arraycopy(sheetFrames[row], startFrame, frames, 0, frames.length);
            animations.add(new Animation<>(frameDuration, frames));
        }

        return animations;
    }

    /** One render step in the floor-pass plan: a tile layer or an object group. */
    private interface LayerOp {
        void run(OrthogonalTiledMapRenderer renderer);
    }

    private static final class TileLayerOp implements LayerOp {
        private final int[] indices;
        TileLayerOp(int index) { this.indices = new int[] {index}; }
        @Override public void run(OrthogonalTiledMapRenderer renderer) { renderer.render(indices); }
    }

    private static final class ObjectLayerOp implements LayerOp {
        private final MapLayer layer;
        ObjectLayerOp(MapLayer layer) { this.layer = layer; }
        @Override public void run(OrthogonalTiledMapRenderer renderer) { drawObjectGroup(renderer, layer); }
    }

    /**
     * One renderable in the Y-sort pass. Implementations carry their own
     * anchor Y (used for the descending sort) and a {@link #draw} call that
     * pulls whatever state they need from the world.
     */
    private interface YSortItem {
        float anchorY();
        void draw(Batch batch, WorldRenderer renderer);
    }

    /**
     * One tile cell from a Y-sortable tile layer (wall_top, overhead, etc.).
     * Pre-resolved at map-load time so the per-frame cost is just sort+draw.
     * Anchor is the bottom of the cell in libGDX bottom-up world space.
     */
    private static final class YSortTileItem implements YSortItem {
        private final TextureRegion region;
        private final float worldX;
        private final float worldY;
        private final float w;
        private final float h;
        private final float anchorY;
        private final boolean flipH;
        private final boolean flipV;
        private final int rotation;
        YSortTileItem(TextureRegion region, float worldX, float worldY, float w, float h, float anchorY,
                      boolean flipH, boolean flipV, int rotation) {
            this.region = region;
            this.worldX = worldX;
            this.worldY = worldY;
            this.w = w;
            this.h = h;
            this.anchorY = anchorY;
            this.flipH = flipH;
            this.flipV = flipV;
            this.rotation = rotation;
        }
        @Override public float anchorY() { return anchorY; }
        @Override public void draw(Batch batch, WorldRenderer renderer) {
            // OrthogonalTiledMapRenderer handled TMX flip/rotation flags for us
            // before these layers moved into the Y-sort pass. Re-apply those
            // flags here so wall_top/overhead textures keep the exact same
            // orientation as the old renderer.
            if (flipH || flipV) region.flip(flipH, flipV);
            try {
                float degrees = rotationDegrees(rotation);
                if (degrees == 0f) {
                    batch.draw(region, worldX, worldY, w, h);
                } else {
                    batch.draw(region, worldX, worldY, w / 2f, h / 2f, w, h, 1f, 1f, degrees);
                }
            } finally {
                if (flipH || flipV) region.flip(flipH, flipV);
            }
        }
        private static float rotationDegrees(int rotation) {
            switch (rotation) {
                case TiledMapTileLayer.Cell.ROTATE_90:
                    return 90f;
                case TiledMapTileLayer.Cell.ROTATE_180:
                    return 180f;
                case TiledMapTileLayer.Cell.ROTATE_270:
                    return 270f;
                default:
                    return 0f;
            }
        }
    }

    /** One tile-placed object from a Y-sortable object group (object_overhead). */
    private static final class YSortObjectItem implements YSortItem {
        private final TextureRegion region;
        private final float worldX;
        private final float worldY;
        private final float w;
        private final float h;
        private final float anchorY;
        private final boolean flipH;
        private final boolean flipV;
        YSortObjectItem(TextureRegion region, float worldX, float worldY, float w, float h, float anchorY, boolean flipH, boolean flipV) {
            this.region = region;
            this.worldX = worldX;
            this.worldY = worldY;
            this.w = w;
            this.h = h;
            this.anchorY = anchorY;
            this.flipH = flipH;
            this.flipV = flipV;
        }
        @Override public float anchorY() { return anchorY; }
        @Override public void draw(Batch batch, WorldRenderer renderer) {
            if (flipH || flipV) {
                region.flip(flipH, flipV);
                batch.draw(region, worldX, worldY, w, h);
                region.flip(flipH, flipV);
            } else {
                batch.draw(region, worldX, worldY, w, h);
            }
        }
    }

    /** Player / dog / homeowner. Anchor is the foot (sprite Y + foot offset) so the actor sorts as a tall pawn does, not as a flat quad. */
    private static final class YSortActorItem implements YSortItem {
        private final MovableEntity actor;
        private final ActorKind kind;
        YSortActorItem(MovableEntity actor, ActorKind kind) {
            this.actor = actor;
            this.kind = kind;
        }
        @Override public float anchorY() {
            return actor.getY() + actor.getHitboxOffsetY();
        }
        @Override public void draw(Batch batch, WorldRenderer renderer) {
            switch (kind) {
                case PLAYER:
                    renderer.drawPlayer((Player) actor, batch);
                    break;
                case DOG:
                    renderer.drawDog((Dog) actor, batch);
                    break;
                case HOMEOWNER:
                    renderer.drawActor(actor, renderer.ownerIdle, renderer.ownerWalk, batch);
                    break;
            }
        }
    }

    /** Meat pickup or dropped meat. Anchor at meat Y so it sorts where it lies. */
    private static final class YSortMeatItem implements YSortItem {
        private final float x;
        private final float y;
        private final boolean drugged;
        YSortMeatItem(float x, float y, boolean drugged) {
            this.x = x;
            this.y = y;
            this.drugged = drugged;
        }
        @Override public float anchorY() { return y; }
        @Override public void draw(Batch batch, WorldRenderer renderer) {
            renderer.drawMeatItem(batch, x, y, drugged);
        }
    }

    /** Storage key pickup. Anchor at key Y so furniture/walls can occlude it naturally. */
    private static final class YSortKeyItem implements YSortItem {
        private final float x;
        private final float y;
        YSortKeyItem(float x, float y) {
            this.x = x;
            this.y = y;
        }
        @Override public float anchorY() { return y; }
        @Override public void draw(Batch batch, WorldRenderer renderer) {
            renderer.drawKeyItem(batch, x, y);
        }
    }

    private enum ActorKind { PLAYER, DOG, HOMEOWNER }
}
