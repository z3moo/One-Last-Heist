package com.onelastheist.game.world;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.maps.tiled.TiledMap;
import com.badlogic.gdx.maps.tiled.TmxMapLoader;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.onelastheist.game.ai.Pathfinder;
import com.onelastheist.game.config.BalanceConfig;
import com.onelastheist.game.entity.npc.Dog;
import com.onelastheist.game.entity.npc.HomeOwner;
import com.onelastheist.game.entity.player.Player;
import com.onelastheist.game.environment.BodyPartPuzzle;
import com.onelastheist.game.environment.KeyPickup;
import com.onelastheist.game.environment.MeatPickup;
import com.onelastheist.game.environment.MoneyPickup;
import com.onelastheist.game.environment.Newspaper;
import com.onelastheist.game.environment.PianoPuzzle;
import com.onelastheist.game.item.ItemFactory;
import com.onelastheist.game.item.MoneyItem;

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

    /** Cash value for a coin pickup. */
    public static final int COIN_VALUE = 10;
    /** Cash value for a diamond pickup. */
    public static final int DIAMOND_VALUE = 20;
    /** Coins placed inside the main house. Bulk of the money lives indoors so the heist matters. */
    private static final int INTERIOR_COIN_COUNT = 18;
    /** Diamonds placed inside the main house. */
    private static final int INTERIOR_DIAMOND_COUNT = 9;
    /** Coins placed in the exterior neighbourhood — just a sprinkle to teach the player the pickup mechanic before they go inside. */
    private static final int EXTERIOR_COIN_COUNT = 2;
    /** Diamonds placed in the exterior neighbourhood. */
    private static final int EXTERIOR_DIAMOND_COUNT = 1;
    /**
     * Number of coins/diamonds reserved in the dog's spawn room. Placed
     * BEFORE the rest of the interior so they always land there even if
     * the random sampler runs short on attempts.
     */
    private static final int DOG_ROOM_COIN_COUNT = 1;
    private static final int DOG_ROOM_DIAMOND_COUNT = 2;
    /**
     * The dog's spawn room (world coords). Used as a hard-reserved area
     * for the dog-room money so the player has to risk waking the dog to
     * collect those pickups. Tuned around the dog spawn at (700, 1080).
     */
    private static final Rectangle DOG_ROOM_BOUNDS = new Rectangle(480f, 864f, 720f, 336f);
    /**
     * The fenced exterior garden, tightened to match the actual collision
     * perimeter:
     * <ul>
     *   <li>South fence sits around world Y ≈ 330 (libGDX bottom-up). Y
     *       below that is the road and is unreachable.</li>
     *   <li>West fence at world X ≈ 510. East fence at world X ≈ 2790.</li>
     *   <li>North fence at world Y ≈ 1850. Y above that is unreachable.</li>
     * </ul>
     * Earlier bounds (220, 100, 2440, 1700) included the road and patches
     * past the side fences — that's where coins were landing in
     * unreachable spots. Tighten to a rect whose every cell is inside the
     * playable garden.
     */
    public static final Rectangle EXTERIOR_GARDEN_BOUNDS = new Rectangle(560f, 380f, 2200f, 1450f);
    /** Probe size used when checking if a money tile is walkable — small so a coin can fit between tight furniture but big enough that it isn't lodged inside a sub-tile collision rect. */
    private static final float MONEY_PROBE_SIZE = 20f;
    /** How close (world units) a money pickup is allowed to spawn to the player's spawn point. ~3 tiles — far enough that the player can't sweep a free pile on entry. */
    private static final float MONEY_MIN_DISTANCE_FROM_SPAWN = 144f;
    /** Minimum spacing between two money pickups so they don't visually stack. */
    private static final float MONEY_MIN_SPACING = 96f;
    /** Per-pickup attempt budget. Bumped from 40 so a tightly-packed map still places every coin/diamond. */
    private static final int MONEY_ATTEMPTS_PER_PICKUP = 200;

    private final BalanceConfig balance;
    private final ItemFactory items = new ItemFactory();

    /** Exposed so the world layer can mint items at runtime (e.g. piano-puzzle reward). */
    public ItemFactory items() { return items; }

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
            new WorldClock(balance.mainMapTimeSeconds),
            new ObjectiveTracker(balance.targetMoney),
            exterior
        );
    }

    /**
     * Loads the exterior neighbourhood map plus its hand-tuned door list. The
     * exterior carries no dog or meat.
     *
     * <p>Collision is sourced exclusively from the hand-authored
     * {@code Collisions} objectgroup. Each house's ground-floor face is
     * already covered by a rect there (e.g. left-house rect at TMX rows
     * 8-9, right-house at rows 7-9) which is what physically blocks the
     * player. The houses' <em>roof</em> tiles in {@code House_demo} /
     * {@code House_demo2} extend several rows north of the ground footprint
     * because that's the standard top-down perspective trick — the artist
     * draws the roof up there so the player sprite can pass <em>behind</em>
     * the house. Rasterizing those tile layers as solid (an earlier attempt)
     * made the visible roof unwalkable from the sides and prevented the
     * player from reaching anywhere north of any house.
     *
     * <p>Visual occlusion (player behind the roof) is the renderer's job
     * via the {@code Overhead_Foreground} layer; collision is just the
     * objectgroup.
     */
    public MapBundle loadExteriorBundle() {
        TiledMap tiledMap = new TmxMapLoader().load(DEFAULT_MAP_PATH);
        CollisionMap collisionMap = new CollisionMap(
            tiledMap, MAP_UNIT_SCALE, COLLISION_OBJECT_LAYER, FALLBACK_SOLID_LAYERS);
        List<Door> doors = createExteriorDoors();
        registerDoorsAsSolids(collisionMap, doors);
        // Money is restricted to the fenced garden so coins don't land on
        // the road south of the house or in the unreachable patches beyond
        // the perimeter fence.
        List<MoneyPickup> money = generateMoneyPickups(
            collisionMap, EXTERIOR_GARDEN_BOUNDS, 520f, 280f,
            EXTERIOR_COIN_COUNT, EXTERIOR_DIAMOND_COUNT, "ext");
        return new MapBundle.Builder(tiledMap, collisionMap, doors, 520f, 280f)
            .moneyPickups(money)
            .build();
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

        // Dog: sleeping in the lower (south) room near the entry. Wander
        // bounds must cover the entire playable interior so the dog can roam
        // every room — entry hall, living room, dining room, AND the upper
        // bedrooms / bathroom. The TMX has floor cells in libGDX cols 9-76
        // (world X 432-3648) and libGDX rows 18-66 (world Y 864-3168), so the
        // rect is sized to contain that range with a small buffer. Earlier
        // bounds (480, 480, 2640, 2160) only reached world Y 2640, clipping
        // off most of the upper rooms which start at world Y 2352.
        float dogSpawnX = 700f;
        float dogSpawnY = 1080f;
        Rectangle wanderBounds = new Rectangle(432f, 864f, 3216f, 2304f);

        // Pre-placed meat. Two pieces — one in the kitchen-ish upper right,
        // one in a bedroom-ish far area. Both are drugged.
        List<MeatPickup> pickups = new ArrayList<>(Arrays.asList(
            new MeatPickup(items.druggedMeat("meat_kitchen"), 2160f, 1872f),
            new MeatPickup(items.druggedMeat("meat_pantry"), 1080f, 2208f)
        ));
        List<KeyPickup> keys = new ArrayList<KeyPickup>();
        // Storage-house key is no longer a floor pickup — it's the reward
        // for solving the piano puzzle. PlayScreen issues it on solve.
        // Piano puzzle. World coords match the piano sprite at TMX col 14,
        // libGDX row 34 — same cell the storage key used to sit on. Player
        // must stand adjacent and press E to open the keyboard overlay.
        List<PianoPuzzle> pianos = new ArrayList<PianoPuzzle>(Arrays.asList(
            new PianoPuzzle(696f, 1656f)
        ));
        // Readable newspaper. Tile-center for TMX (col 23, row 60 from top)
        // is world (1128, 936); nudged up and left by ~20 wu so the page
        // sits a bit further into the open floor in front of the cabinet
        // rather than dead-center on the tile under it. Sub-tile offsets
        // are fine — the renderer draws this in a top-most pass so the
        // exact anchor doesn't affect Y-sort.
        List<Newspaper> newspapers = new ArrayList<Newspaper>(Arrays.asList(
            new Newspaper(1108f, 956f)
        ));

        // Carpet bounds: cols 27-28, row 47 from top. Sprite math identical to
        // the prior comment block — see Git history if you want the derivation.
        // Money: place the dog-room reservation FIRST so those slots can never
        // be stolen by random sampling elsewhere. The remaining coins/diamonds
        // sweep the full playable interior, with min-spacing applied across
        // the dog-room placements so nothing overlaps.
        Rectangle interiorBounds = new Rectangle(432f, 864f,
            collisionMap.getWorldWidth() - 432f, collisionMap.getWorldHeight() - 864f);
        List<MoneyPickup> money = new ArrayList<>(INTERIOR_COIN_COUNT + INTERIOR_DIAMOND_COUNT);
        // Dog room: 1 coin + 2 diamonds (your call). Spawn-distance check
        // uses the player spawn so the reservation sits naturally far from
        // the entry; the dog room itself is always far from there.
        placeMoneyInBounds(collisionMap, DOG_ROOM_BOUNDS, 1272f, 1524f,
            DOG_ROOM_COIN_COUNT, DOG_ROOM_DIAMOND_COUNT, "int_dog", money);
        // Remainder of the house. Subtract what's already reserved.
        placeMoneyInBounds(collisionMap, interiorBounds, 1272f, 1524f,
            INTERIOR_COIN_COUNT - DOG_ROOM_COIN_COUNT,
            INTERIOR_DIAMOND_COUNT - DOG_ROOM_DIAMOND_COUNT,
            "int", money);
        return new MapBundle.Builder(tiledMap, collisionMap, doors, 1272f, 1524f)
            .dogSpawn(dogSpawnX, dogSpawnY)
            .dogWanderBounds(wanderBounds)
            .meatPickups(pickups)
            .keyPickups(keys)
            .moneyPickups(money)
            .newspapers(newspapers)
            .pianoPuzzles(pianos)
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
        // Body-part puzzle clues. Tile coords from the level designer
        // converted to world cell-centers: world_x = col*48+24,
        // world_y = (mapHeight - row - 1)*48 + 24 with mapHeight=40.
        // Correct answers: 1=A, 2=B, 3=C, 4=C.
        List<BodyPartPuzzle> bodyParts = new ArrayList<BodyPartPuzzle>(Arrays.asList(
            new BodyPartPuzzle(BodyPartPuzzle.Kind.LEG, 1, 'A',  744f,  600f),
            new BodyPartPuzzle(BodyPartPuzzle.Kind.ARM, 2, 'B', 1656f,  744f),
            new BodyPartPuzzle(BodyPartPuzzle.Kind.LEG, 3, 'C',  744f,  936f),
            new BodyPartPuzzle(BodyPartPuzzle.Kind.ARM, 4, 'C', 2136f, 1224f)
        ));
        return new MapBundle.Builder(tiledMap, collisionMap, doors, 1872f, 288f)
            .bodyPartPuzzles(bodyParts)
            .build();
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
    }

    /**
     * Scatter coin and diamond pickups across walkable tiles inside
     * {@code bounds}, appending to {@code placed} so a sequence of calls
     * can build up a single map's money list while honoring the
     * {@link #MONEY_MIN_SPACING} between every pickup (across calls). Each
     * candidate must:
     * <ul>
     *   <li>Pass {@link CollisionMap#rectCollides} for a small probe at the
     *       tile center, so it's reachable not stuck in a wall / piece of
     *       furniture.</li>
     *   <li>Sit at least {@link #MONEY_MIN_DISTANCE_FROM_SPAWN} from
     *       {@code spawnX/Y} so the player can't sweep a free pile from
     *       the entry tile.</li>
     *   <li>Sit at least {@link #MONEY_MIN_SPACING} from any previously
     *       placed pickup so they don't visually overlap.</li>
     * </ul>
     * If a candidate fails after a generous attempt budget the placement
     * is skipped — losing one or two coins is preferable to an infinite
     * loop on a tightly-packed map. The {@code idPrefix} is used to make
     * inventory ids unique across maps so a coin from the exterior and
     * one from the interior don't collide in the player's inventory.
     */
    private void placeMoneyInBounds(CollisionMap collisionMap, Rectangle bounds,
                                    float spawnX, float spawnY,
                                    int coinCount, int diamondCount,
                                    String idPrefix, List<MoneyPickup> placed) {
        // Reachability seeded from the player spawn. Filters out coins that
        // would otherwise land in isolated patches behind a fence, in a
        // sealed void, or under furniture the player can't access. Built
        // with a player-shaped pathfinder so the same probe size that
        // governs in-game movement governs which tiles are valid pickups.
        Pathfinder reachProbe = new Pathfinder(collisionMap, 42f, 12f, 60f, 36f);
        boolean[] reachable = reachProbe.computeReachable(spawnX, spawnY);

        float tileSize = collisionMap.getTileSize();
        int minTx = Math.max(0, (int) (bounds.x / tileSize));
        int maxTx = Math.min(collisionMap.getTileWidth() - 1,
            (int) ((bounds.x + bounds.width) / tileSize));
        int minTy = Math.max(0, (int) (bounds.y / tileSize));
        int maxTy = Math.min(collisionMap.getTileHeight() - 1,
            (int) ((bounds.y + bounds.height) / tileSize));
        if (maxTx < minTx || maxTy < minTy) return;

        int attemptBudget = (coinCount + diamondCount) * MONEY_ATTEMPTS_PER_PICKUP;
        int coinsLeft = coinCount;
        int diamondsLeft = diamondCount;
        int coinIdx = placed.size();
        int diamondIdx = placed.size();
        float minSpawnDistSq = MONEY_MIN_DISTANCE_FROM_SPAWN * MONEY_MIN_DISTANCE_FROM_SPAWN;
        float minSpacingSq = MONEY_MIN_SPACING * MONEY_MIN_SPACING;

        while ((coinsLeft > 0 || diamondsLeft > 0) && attemptBudget-- > 0) {
            int tx = MathUtils.random(minTx, maxTx);
            int ty = MathUtils.random(minTy, maxTy);
            float cx = tx * tileSize + tileSize / 2f;
            float cy = ty * tileSize + tileSize / 2f;
            // Walkability probe: small box at the tile center.
            if (collisionMap.rectCollides(cx - MONEY_PROBE_SIZE / 2f, cy - MONEY_PROBE_SIZE / 2f,
                MONEY_PROBE_SIZE, MONEY_PROBE_SIZE)) continue;
            // Reachability: the player can BFS-walk here from spawn.
            if (!reachable[ty * collisionMap.getTileWidth() + tx]) continue;
            float dxSpawn = cx - spawnX;
            float dySpawn = cy - spawnY;
            if (dxSpawn * dxSpawn + dySpawn * dySpawn < minSpawnDistSq) continue;
            // Spacing against earlier placements (this call + previous calls).
            boolean tooClose = false;
            for (int i = 0, n = placed.size(); i < n; i++) {
                MoneyPickup other = placed.get(i);
                float dx = cx - other.getX();
                float dy = cy - other.getY();
                if (dx * dx + dy * dy < minSpacingSq) { tooClose = true; break; }
            }
            if (tooClose) continue;

            MoneyPickup.Kind kind;
            MoneyItem money;
            if (coinsLeft > 0 && (diamondsLeft == 0 || MathUtils.randomBoolean(0.66f))) {
                kind = MoneyPickup.Kind.COIN;
                money = items.money(idPrefix + "_coin_" + coinIdx++, COIN_VALUE);
                coinsLeft--;
            } else {
                kind = MoneyPickup.Kind.DIAMOND;
                money = items.money(idPrefix + "_dia_" + diamondIdx++, DIAMOND_VALUE);
                diamondsLeft--;
            }
            // Random bob phase per pickup so a row of coins doesn't sync.
            float bobPhase = MathUtils.random(0f, MathUtils.PI2);
            placed.add(new MoneyPickup(money, kind, cx, cy, bobPhase));
        }
        // Diagnostic if the budget ran out before the spec was met. Earlier
        // versions returned silently, which made it look like the player
        // had grabbed the missing pickups when in fact they were never
        // placed (e.g. bounds disconnected from spawn by a fence change).
        if (coinsLeft > 0 || diamondsLeft > 0) {
            Gdx.app.error("WorldFactory",
                "money placement under-filled: prefix=" + idPrefix
                    + " coinsLeft=" + coinsLeft + " diamondsLeft=" + diamondsLeft
                    + " bounds=" + bounds);
        }
    }

    /**
     * One-shot helper: build a fresh list and run a single placement pass
     * inside {@code bounds}. Used for maps that don't need reserved sub-areas
     * (currently the exterior).
     */
    private List<MoneyPickup> generateMoneyPickups(CollisionMap collisionMap, Rectangle bounds,
                                                   float spawnX, float spawnY,
                                                   int coinCount, int diamondCount, String idPrefix) {
        List<MoneyPickup> placed = new ArrayList<>(coinCount + diamondCount);
        placeMoneyInBounds(collisionMap, bounds, spawnX, spawnY,
            coinCount, diamondCount, idPrefix, placed);
        return placed;
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

    /**
     * Exit strip at the south end of the storage interior, aligned with
     * the entry tile (TMX col 40, row 34). The door rect sits one tile
     * south of the spawn so the player's hitbox bottom touches its top —
     * same idiom as the main house's exit door. Walk south, press E to
     * return to the exterior.
     */
    private List<Door> createSideHouseDoors() {
        List<Door> doors = new ArrayList<Door>();
        doors.add(new Door(1872f, 192f, 144f, 48f, EXTERIOR_MAP_ID, "Leave Storage", false));
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
        /** Pre-placed coins/diamonds the player can pick up. Sized per map by {@link #generateMoneyPickups}. */
        public final List<MoneyPickup> moneyPickups;
        /** Stationary readable newspapers the player can interact with. Empty on maps without newspapers. */
        public final List<Newspaper> newspapers;
        /** Stationary interactive piano puzzles. The main interior has one; others are empty. */
        public final List<PianoPuzzle> pianoPuzzles;
        /** Body-part question pickups for the hidden-route puzzle. The side house has four; others are empty. */
        public final List<BodyPartPuzzle> bodyPartPuzzles;

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
            this.moneyPickups = b.moneyPickups == null
                ? Collections.<MoneyPickup>emptyList()
                : Collections.unmodifiableList(b.moneyPickups);
            this.newspapers = b.newspapers == null
                ? Collections.<Newspaper>emptyList()
                : Collections.unmodifiableList(b.newspapers);
            this.pianoPuzzles = b.pianoPuzzles == null
                ? Collections.<PianoPuzzle>emptyList()
                : Collections.unmodifiableList(b.pianoPuzzles);
            this.bodyPartPuzzles = b.bodyPartPuzzles == null
                ? Collections.<BodyPartPuzzle>emptyList()
                : Collections.unmodifiableList(b.bodyPartPuzzles);
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
            private List<MoneyPickup> moneyPickups;
            private List<Newspaper> newspapers;
            private List<PianoPuzzle> pianoPuzzles;
            private List<BodyPartPuzzle> bodyPartPuzzles;

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
            public Builder moneyPickups(List<MoneyPickup> p) { this.moneyPickups = p; return this; }
            public Builder newspapers(List<Newspaper> p) { this.newspapers = p; return this; }
            public Builder pianoPuzzles(List<PianoPuzzle> p) { this.pianoPuzzles = p; return this; }
            public Builder bodyPartPuzzles(List<BodyPartPuzzle> p) { this.bodyPartPuzzles = p; return this; }
            public MapBundle build() { return new MapBundle(this); }
        }
    }
}
