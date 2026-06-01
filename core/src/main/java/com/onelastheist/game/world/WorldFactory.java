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

/** Tao world tu cau hinh va du lieu map. */
public class WorldFactory {
    public static final String DEFAULT_MAP_PATH = "maps/Exterior_Neighbour_upgrade.tmx";
    public static final float MAP_UNIT_SCALE = 3f;
    public static final String MAIN_HOUSE_INTERIOR_ID = "interior_main_house";
    public static final String SIDE_HOUSE_INTERIOR_ID = "interior_side_house";
    private static final String COLLISION_OBJECT_LAYER = "Collisions";
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
            // Cua chinh la solid de chan nguoi choi xuyen tuong; chi qua khi nhan E (xu ly o PlayScreen).
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
     * Cua dinh san trong map ngoai troi. Toa do o trong don vi the gioi (sau MAP_UNIT_SCALE = 3),
     * 1 tile = 48 don vi. Y theo libGDX (bottom-up): y = (mapHeight - rowFromTop - 1) * tileSize.
     */
    private List<Door> createDoors() {
        List<Door> doors = new ArrayList<Door>();
        // Big house (House_demo): cua truoc o cot 38-39, hang 15 tu tren xuong. Mo khoa => vao map noi that chinh.
        doors.add(new Door(1824f, 1152f, 96f, 48f, MAIN_HOUSE_INTERIOR_ID, "Enter House", false));
        // Small house (House2): cua truoc o cot 28-29, hang 8 tu tren xuong. Khoa => chi hien thong bao.
        doors.add(new Door(1344f, 1488f, 96f, 48f, SIDE_HOUSE_INTERIOR_ID, "Locked", true));
        return doors;
    }
}
