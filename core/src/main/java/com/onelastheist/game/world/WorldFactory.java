package com.onelastheist.game.world;

import com.badlogic.gdx.maps.tiled.TiledMap;
import com.badlogic.gdx.maps.tiled.TmxMapLoader;
import com.badlogic.gdx.math.Rectangle;
import com.onelastheist.game.config.BalanceConfig;
import com.onelastheist.game.entity.npc.Dog;
import com.onelastheist.game.entity.npc.HomeOwner;
import com.onelastheist.game.entity.player.Player;
import com.onelastheist.game.environment.KeyPickup;
import com.onelastheist.game.environment.MeatPickup;
import com.onelastheist.game.item.ItemFactory;
import com.onelastheist.game.trap.AlarmSystem;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Builds a fresh {@link GameWorld} from configuration plus on-disk map data.
 * Every value PlayScreen needs to start a run is constructed here, including
 * the player/NPC entities, the loaded Tiled map, the derived collision rectangles,
 * the registered door interactions, and (for the interior) the dog's spawn,
 * wander region, and pre-placed meat pickups.
 *
 * <p>Map coordinates are world units (Tiled pixels x {@link #MAP_UNIT_SCALE}).
 * The exterior map is 60x40 tiles; each tile is 16 source px → 48 world units.
 * The interior map is 85x80 tiles at the same scale.
 */
public class WorldFactory {
    public static final String DEFAULT_MAP_PATH = "maps/Exterior_Neighbour_upgrade.tmx";
    /** Interior map for the main house, loaded lazily on first door enter. */
    public static final String INTERIOR_MAP_PATH = "maps/Interior_Neighbour_upgrade.tmx";
    /** Interior map for the locked small storage house outside. */
    public static final String SIDE_HOUSE_MAP_PATH = "maps/Storage_Neighbour.tmx";
    /** Inventory id for the key that unlocks the small storage house. */
    public static final String SIDE_HOUSE_KEY_ID = "side_house_key";
    /** Multiplier from Tiled source pixels to world units. Keeps sprites legible at 1080p. */
    public static final float MAP_UNIT_SCALE = 3f;
    /** Identifier for the main house interior map. */
    public static final String MAIN_HOUSE_INTERIOR_ID = "interior_main_house";
    /** Identifier for the smaller, currently-locked house. */
    public static final String SIDE_HOUSE_INTERIOR_ID = "interior_side_house";
    /** Identifier used by the interior's exit door to return to the exterior. */
    public static final String EXTERIOR_MAP_ID = "exterior_main";
    /** Tiled object layer that hosts the hand-tuned solid rectangles. */
    private static final String COLLISION_OBJECT_LAYER = "Collisions";
    /** Tile layers that fall back to whole-tile blocking if no Collisions group is present. */
    private static final String[] FALLBACK_SOLID_LAYERS = {
        "Fence",
        "House_demo",
        "House_demo2",
        "House2",
        "Chill_time",
        "Object1",
        "Objects2",
        "farm1",
        "farm2",
        "farm3",
        "farm4",
        "farm5",
        "farm6",
        "Grass_objects"
    };
    /**
     * Interior tile layers that are <em>always</em> rasterized into collision,
     * on top of the {@code collisions} objectgroup.
     *
     * <p>Only {@code walls} is included. The {@code overhead} layer was
     * removed from this list because the artist used overhead tiles to draw
     * the north face of the upper rooms <em>including over the doorway
     * cells</em> — rasterizing them generated invisible collision exactly
     * where the doorway should be open and sealed the upper rooms behind a
     * phantom wall. The hand-authored rectangles in the {@code collisions}
     * objectgroup remain authoritative for actual blocking, and the
     * {@link com.onelastheist.game.render.WorldRenderer} Y-sort handles the
     * walks-under-wall_top occlusion that overhead-as-collision was
     * previously protecting against.
     *
     * <p>Other tile-only wall layers ({@code wall_top}, {@code wall_top2},
     * {@code overhead_decor}) were always purely visual and remain so.
     */
    private static final String[] INTERIOR_ALWAYS_SOLID_LAYERS = {
        "walls"
    };

    private final BalanceConfig balance;
    private final ItemFactory items = new ItemFactory();

    public WorldFactory(BalanceConfig balance) { this.balance = balance; }

    /**
     * Build a default game world. Spawns the player at a fixed location, places
     * the homeowner and dog (initially hidden — the dog only becomes visible on
     * first interior entry, the homeowner appears later in the game), loads the
     * exterior TMX, derives collisions, and registers door interactions as
     * additional solids so the player has to use E rather than walk through walls.
     */
    public GameWorld createDefaultWorld() {
        Player player = new Player();
        player.setSpeed(Player.WALK_SPEED);

        HomeOwner homeOwner = new HomeOwner();
        homeOwner.setPosition(690f, 280f);
        homeOwner.setSpeed(Player.WALK_SPEED);
        homeOwner.setVisible(false);

        Dog dog = new Dog();
        // Park the dog off-stage until its real interior position is set on
        // first entry; visible flag is also flipped there.
        dog.setPosition(0f, 0f);
        dog.setVisible(false);

        MapBundle exterior = loadExteriorBundle();
        player.setPosition(exterior.spawnX, exterior.spawnY);

        return new GameWorld(
            this,
            balance,
            player,
            homeOwner,
            dog,
            RoomGraph.createMainHouseGraph(),
            new WorldClock(balance.mainMapTimeSeconds),
            new ObjectiveTracker(balance.targetMoney),
            new AlarmSystem(),
            exterior
        );
    }

    /**
     * Loads the exterior neighbourhood map plus its hand-tuned door list. The
     * exterior carries no dog or meat.
     *
     * <p>Collision is sourced exclusively from the hand-authored
     * {@code Collisions} objectgroup. The {@link #FALLBACK_SOLID_LAYERS} list
     * is only consulted when no objectgroup exists — the exterior has one,
     * so those layers stay decorative. Rasterizing them as a "safety net"
     * was too aggressive: the layers include Grass_objects and farm1-6 which
     * carry walkable grass/path tiles, and rasterizing them blocked legitimate
     * paths everywhere.
     */
    public MapBundle loadExteriorBundle() {
        TiledMap tiledMap = new TmxMapLoader().load(DEFAULT_MAP_PATH);
        CollisionMap collisionMap = new CollisionMap(
            tiledMap, MAP_UNIT_SCALE, COLLISION_OBJECT_LAYER, FALLBACK_SOLID_LAYERS);
        List<Door> doors = createExteriorDoors();
        registerDoorsAsSolids(collisionMap, doors);
        return new MapBundle.Builder(tiledMap, collisionMap, doors, 520f, 280f).build();
    }

    /**
     * Loads the main-house interior. Player spawns on the small entry rug at
     * TMX row 47 cols 27-28. The dog spawns asleep in the lower (south) room
     * and wanders within {@link #INTERIOR_DOG_BOUNDS} when awake. Two pre-placed
     * meat pickups sit in distant rooms so the player has to scout the house.
     *
     * <p>Interior doors are NOT registered as solids — putting a solid rect
     * over the entry tile would trap the player on top of their own spawn.
     */
    public MapBundle loadInteriorBundle() {
        TiledMap tiledMap = new TmxMapLoader().load(INTERIOR_MAP_PATH);
        // Interior collision objectgroup is named "collisions" lowercase; matched
        // case-insensitively by CollisionMap. Wall tile layers are also rasterized
        // as additional collision so missing rects in the objectgroup don't leave
        // walls passable.
        CollisionMap collisionMap = new CollisionMap(
            tiledMap, MAP_UNIT_SCALE, COLLISION_OBJECT_LAYER, FALLBACK_SOLID_LAYERS, INTERIOR_ALWAYS_SOLID_LAYERS);
        List<Door> doors = createInteriorDoors();
        // Register the exit door as a solid wall. Without this, the player can
        // walk south straight through the door tile and out of the map. The
        // door is in the south wall, so the player must press E from a few
        // tiles away to transition — same UX as the exterior front door.
        registerDoorsAsSolids(collisionMap, doors);
        // Plug TMX data gaps where the artist drew a wall in the wall_top
        // layer (visual) but never put a matching cell in `walls` or a rect
        // in `collisions`, leaving phase-through-the-wall holes. Coordinates
        // are world-space (post-MAP_UNIT_SCALE), audited from the TMX.
        patchInteriorCollisionGaps(collisionMap);

        // Dog: sleeping in the lower (south) room near the entry. Wander bounds
        // span the entire visible interior — DogBrain rejects targets inside
        // walls, so a generous outer rect is fine.
        float dogSpawnX = 700f;
        float dogSpawnY = 1080f;
        Rectangle wanderBounds = new Rectangle(480f, 480f, 2640f, 2160f);

        // Pre-placed meat. Two pieces — one in the kitchen-ish upper right,
        // one in a bedroom-ish far area. Both are drugged.
        List<MeatPickup> pickups = new ArrayList<>(Arrays.asList(
            new MeatPickup(items.druggedMeat("meat_kitchen"), 2160f, 1872f),
            new MeatPickup(items.druggedMeat("meat_pantry"), 1080f, 2208f)
        ));
        List<KeyPickup> keys = new ArrayList<KeyPickup>(Arrays.asList(
            // Tucked behind the piano at TMX col 14, row 45 (libGDX y 34) —
            // tile-center world coords. The piano in `objects` covers the cell
            // visually; the key only becomes obvious when the player approaches
            // and notices the prompt range.
            new KeyPickup(items.key(SIDE_HOUSE_KEY_ID, "Storage Key"), 696f, 1656f)
        ));

        // Carpet bounds: cols 27-28, row 47 from top. Sprite math identical to
        // the prior comment block — see Git history if you want the derivation.
        return new MapBundle.Builder(tiledMap, collisionMap, doors, 1272f, 1524f)
            .dogSpawn(dogSpawnX, dogSpawnY)
            .dogWanderBounds(wanderBounds)
            .meatPickups(pickups)
            .keyPickups(keys)
            .build();
    }

    /** Loads the small locked storage-house interior. It has its own authored collisions and no dog. */
    public MapBundle loadSideHouseBundle() {
        TiledMap tiledMap = new TmxMapLoader().load(SIDE_HOUSE_MAP_PATH);
        // Always rasterize the walls tile layer in addition to the objectgroup —
        // same rationale as the main interior. The hand-authored rectangles
        // catch most of the wall, but a missing rect would let the player walk
        // through it; the always-solid pass closes those gaps.
        CollisionMap collisionMap = new CollisionMap(
            tiledMap, MAP_UNIT_SCALE, COLLISION_OBJECT_LAYER, FALLBACK_SOLID_LAYERS, INTERIOR_ALWAYS_SOLID_LAYERS);
        List<Door> doors = createSideHouseDoors();
        registerDoorsAsSolids(collisionMap, doors);
        patchSideHouseCollisionGaps(collisionMap);
        return new MapBundle.Builder(tiledMap, collisionMap, doors, 1440f, 576f).build();
    }

    private static void registerDoorsAsSolids(CollisionMap collisionMap, List<Door> doors) {
        for (Door door : doors) {
            // Door rect doubles as a wall: the player cannot pass without pressing E.
            // PlayScreen's interaction handler is what actually transitions screens.
            collisionMap.addSolid(door.getBounds().x, door.getBounds().y, door.getBounds().width, door.getBounds().height);
        }
    }

    /**
     * Fill the audited collision holes in the main-house TMX. Each rect closes
     * a wall the artist drew in <em>wall_top</em> only — visible to the
     * player but neither rasterized via the {@code walls} layer nor present
     * in the {@code collisions} objectgroup. Without these the player walks
     * straight through the visible wall into void.
     *
     * <p>Patches are deliberately conservative — only added for the two
     * audited gaps (east bathroom wall, south bathroom edge) that are far
     * enough from the entry room to be unambiguous wall-not-doorway. Earlier
     * patches in the entry hallway and west perimeter were removed after
     * they turned out to overlap doorways the dog needs to traverse.
     *
     * <p>All values are world coordinates (post-{@link #MAP_UNIT_SCALE}).
     */
    private static void patchInteriorCollisionGaps(CollisionMap collisionMap) {
        // East wall, col 65 rows 43-52 (bathroom east edge). wall_top draws
        // a visible wall at cols 63-66 but neither walls nor collisions cover
        // it, so the player walks east through the wall.
        collisionMap.addSolid(3072f, 1296f, 48f, 480f);
        // South edge of bathroom, row 53 cols 44-64. floor_base ends at row
        // 52, void below — but no south blocking, so the player drops
        // through the floor walking south.
        collisionMap.addSolid(2100f, 1248f, 1020f, 48f);
    }

    /**
     * Fill audited collision holes in the storage-house TMX. The east-wall
     * objectgroup rects have a 36-unit vertical gap that the player's hitbox
     * can squeeze through; the south edge has long stretches with neither
     * walls cells nor collision rects, letting the player walk into void.
     *
     * <p>World coordinates (post-{@link #MAP_UNIT_SCALE}). The map's world
     * height is 1920 (40 tiles × 48), so south-edge rects sit at low Y.
     */
    private static void patchSideHouseCollisionGaps(CollisionMap collisionMap) {
        // East-wall gap between objectgroup rects (audit found a 36-unit
        // sliver between Y=1062 and Y=1098 at X≈2245-2320). Cover with
        // overlapping margin so float drift can't slip through.
        collisionMap.addSolid(2245f, 1050f, 80f, 60f);
        // South edge gaps (player can walk south off the floor into void).
        // Add a thin south wall spanning the full map width as a backstop;
        // the boundary check at Y=0 catches anything past this.
        collisionMap.addSolid(0f, 0f, 2880f, 48f);
    }

    /**
     * Hardcoded door list for the exterior map. Coordinates are in world units
     * after {@link #MAP_UNIT_SCALE} (1 tile = 48 units). LibGDX uses bottom-up
     * Y, so a door visible at "row N from top" lives at
     * {@code y = (mapHeight - rowFromTop - 1) * tileSize}.
     */
    private List<Door> createExteriorDoors() {
        List<Door> doors = new ArrayList<Door>();
        // Big house (House_demo): front door at cols 38-39, row 15 from top. Unlocked.
        doors.add(new Door(1824f, 1152f, 96f, 48f, MAIN_HOUSE_INTERIOR_ID, "Enter House", false));
        // Small house (House2): front door at cols 28-29, row 8 from top. Requires the storage key from inside the main house.
        doors.add(new Door(1344f, 1488f, 96f, 48f, SIDE_HOUSE_INTERIOR_ID, "Enter Storage", true));
        return doors;
    }

    /**
     * Single exit door: a thin trigger strip just south of the row-47 entry
     * carpet, spanning cols 27-29. Walking south off the carpet brings the
     * player into range; pressing E returns to the exterior.
     */
    private List<Door> createInteriorDoors() {
        List<Door> doors = new ArrayList<Door>();
        doors.add(new Door(1296f, 1488f, 144f, 48f, EXTERIOR_MAP_ID, "Leave House", false));
        return doors;
    }

    /** Exit strip at the south/front of the storage-house interior. */
    private List<Door> createSideHouseDoors() {
        List<Door> doors = new ArrayList<Door>();
        doors.add(new Door(1344f, 432f, 144f, 48f, EXTERIOR_MAP_ID, "Leave Storage", false));
        return doors;
    }

    /**
     * Bundle of everything one screen-worth of map needs. Built via
     * {@link Builder} because the exterior carries fewer fields than the
     * interior — a builder keeps the call sites declarative and means
     * exterior callers don't pass dummy meat lists or wander rects.
     */
    public static final class MapBundle {
        public final TiledMap tiledMap;
        public final CollisionMap collisionMap;
        public final List<Door> doors;
        public final float spawnX;
        public final float spawnY;
        /** Where the dog appears on first entry. Defaults to (0,0) for maps without a dog. */
        public final float dogSpawnX;
        public final float dogSpawnY;
        /** Rect within which the dog picks random wander targets. May be null on maps without a dog. */
        public final Rectangle dogWanderBounds;
        /** Pre-placed meat the player can pick up. Empty on maps without a dog. */
        public final List<MeatPickup> meatPickups;
        /** Pre-placed keys the player can pick up. Empty on maps without keys. */
        public final List<KeyPickup> keyPickups;

        private MapBundle(Builder b) {
            this.tiledMap = b.tiledMap;
            this.collisionMap = b.collisionMap;
            this.doors = b.doors == null ? Collections.<Door>emptyList() : b.doors;
            this.spawnX = b.spawnX;
            this.spawnY = b.spawnY;
            this.dogSpawnX = b.dogSpawnX;
            this.dogSpawnY = b.dogSpawnY;
            this.dogWanderBounds = b.dogWanderBounds;
            this.meatPickups = b.meatPickups == null
                ? Collections.<MeatPickup>emptyList()
                : Collections.unmodifiableList(b.meatPickups);
            this.keyPickups = b.keyPickups == null
                ? Collections.<KeyPickup>emptyList()
                : Collections.unmodifiableList(b.keyPickups);
        }

        public static final class Builder {
            private final TiledMap tiledMap;
            private final CollisionMap collisionMap;
            private final List<Door> doors;
            private final float spawnX;
            private final float spawnY;
            private float dogSpawnX;
            private float dogSpawnY;
            private Rectangle dogWanderBounds;
            private List<MeatPickup> meatPickups;
            private List<KeyPickup> keyPickups;

            public Builder(TiledMap tiledMap, CollisionMap collisionMap, List<Door> doors, float spawnX, float spawnY) {
                this.tiledMap = tiledMap;
                this.collisionMap = collisionMap;
                this.doors = doors;
                this.spawnX = spawnX;
                this.spawnY = spawnY;
            }

            public Builder dogSpawn(float x, float y) { this.dogSpawnX = x; this.dogSpawnY = y; return this; }
            public Builder dogWanderBounds(Rectangle r) { this.dogWanderBounds = r; return this; }
            public Builder meatPickups(List<MeatPickup> p) { this.meatPickups = p; return this; }
            public Builder keyPickups(List<KeyPickup> p) { this.keyPickups = p; return this; }
            public MapBundle build() { return new MapBundle(this); }
        }
    }
}
