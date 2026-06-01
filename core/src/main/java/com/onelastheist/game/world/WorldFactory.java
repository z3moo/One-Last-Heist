package com.onelastheist.game.world;

import com.badlogic.gdx.maps.tiled.TiledMap;
import com.badlogic.gdx.maps.tiled.TmxMapLoader;
import com.onelastheist.game.config.BalanceConfig;
import com.onelastheist.game.entity.npc.Dog;
import com.onelastheist.game.entity.npc.HomeOwner;
import com.onelastheist.game.entity.player.Player;
import com.onelastheist.game.trap.AlarmSystem;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds a fresh {@link GameWorld} from configuration plus on-disk map data.
 * Every value PlayScreen needs to start a run is constructed here, including
 * the player/NPC entities, the loaded Tiled map, the derived collision rectangles,
 * and the registered door interactions.
 *
 * <p>Map coordinates are world units (Tiled pixels x {@link #MAP_UNIT_SCALE}).
 * The map is 60x40 tiles; each tile is 16 source px → 48 world units.
 */
public class WorldFactory {
    public static final String DEFAULT_MAP_PATH = "maps/Exterior_Neighbour_upgrade.tmx";
    /** Multiplier from Tiled source pixels to world units. Keeps sprites legible at 1080p. */
    public static final float MAP_UNIT_SCALE = 3f;
    /** Identifier for the not-yet-imported main house interior map. */
    public static final String MAIN_HOUSE_INTERIOR_ID = "interior_main_house";
    /** Identifier for the smaller, currently-locked house. */
    public static final String SIDE_HOUSE_INTERIOR_ID = "interior_side_house";
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

    private final BalanceConfig balance;

    public WorldFactory(BalanceConfig balance) { this.balance = balance; }

    /**
     * Build a default game world. Spawns the player at a fixed location, places
     * the homeowner and dog (initially hidden — they appear later via
     * {@code setVisible(true)}), loads the TMX map, derives collisions, and
     * registers door interactions as additional solids so the player has to
     * use E rather than walk through walls.
     */
    public GameWorld createDefaultWorld() {
        Player player = new Player();
        player.setPosition(520f, 280f);
        player.setSpeed(Player.WALK_SPEED);

        HomeOwner homeOwner = new HomeOwner();
        homeOwner.setPosition(690f, 280f);
        homeOwner.setSpeed(Player.WALK_SPEED);
        homeOwner.setVisible(false);

        Dog dog = new Dog();
        dog.setPosition(860f, 280f);
        dog.setSpeed(Player.WALK_SPEED);
        dog.setVisible(false);

        TiledMap tiledMap = new TmxMapLoader().load(DEFAULT_MAP_PATH);
        CollisionMap collisionMap = new CollisionMap(tiledMap, MAP_UNIT_SCALE, COLLISION_OBJECT_LAYER, FALLBACK_SOLID_LAYERS);

        List<Door> doors = createDoors();
        for (Door door : doors) {
            // Door rect doubles as a wall: the player cannot pass without pressing E.
            // PlayScreen's interaction handler is what actually transitions to the interior.
            collisionMap.addSolid(door.getBounds().x, door.getBounds().y, door.getBounds().width, door.getBounds().height);
        }

        return new GameWorld(
            player,
            homeOwner,
            dog,
            RoomGraph.createMainHouseGraph(),
            new WorldClock(balance.mainMapTimeSeconds),
            new ObjectiveTracker(balance.targetMoney),
            new AlarmSystem(),
            tiledMap,
            collisionMap,
            doors
        );
    }

    /**
     * Hardcoded door list for the exterior map. Coordinates are in world units
     * after {@link #MAP_UNIT_SCALE} (1 tile = 48 units). LibGDX uses bottom-up
     * Y, so a door visible at "row N from top" lives at
     * {@code y = (mapHeight - rowFromTop - 1) * tileSize}.
     */
    private List<Door> createDoors() {
        List<Door> doors = new ArrayList<Door>();
        // Big house (House_demo): front door at cols 38-39, row 15 from top. Unlocked.
        doors.add(new Door(1824f, 1152f, 96f, 48f, MAIN_HOUSE_INTERIOR_ID, "Enter House", false));
        // Small house (House2): front door at cols 28-29, row 8 from top. Locked for now.
        doors.add(new Door(1344f, 1488f, 96f, 48f, SIDE_HOUSE_INTERIOR_ID, "Locked", true));
        return doors;
    }
}
