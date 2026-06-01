package com.onelastheist.game.world;

import com.badlogic.gdx.maps.tiled.TiledMap;
import com.badlogic.gdx.utils.Disposable;
import com.onelastheist.game.entity.npc.Dog;
import com.onelastheist.game.entity.npc.HomeOwner;
import com.onelastheist.game.entity.player.Player;
import com.onelastheist.game.trap.AlarmSystem;

import java.util.Collections;
import java.util.List;

/** Mo hinh world luc chay. Luu trang thai game; viec ve duoc xu ly o noi khac. */
public class GameWorld implements Disposable {
    private final Player player;
    private final HomeOwner homeOwner;
    private final Dog dog;
    private final RoomGraph roomGraph;
    private final WorldClock clock;
    private final ObjectiveTracker objectives;
    private final AlarmSystem alarmSystem;
    private final TiledMap tiledMap;
    private final CollisionMap collisionMap;
    private final List<Door> doors;

    public GameWorld(Player player, HomeOwner homeOwner, Dog dog, RoomGraph roomGraph, WorldClock clock,
                     ObjectiveTracker objectives, AlarmSystem alarmSystem, TiledMap tiledMap,
                     CollisionMap collisionMap, List<Door> doors) {
        this.player = player;
        this.homeOwner = homeOwner;
        this.dog = dog;
        this.roomGraph = roomGraph;
        this.clock = clock;
        this.objectives = objectives;
        this.alarmSystem = alarmSystem;
        this.tiledMap = tiledMap;
        this.collisionMap = collisionMap;
        this.doors = doors == null ? Collections.<Door>emptyList() : doors;
    }

    public void update(float deltaSeconds) {
        clock.update(deltaSeconds);
        dog.update(deltaSeconds);
    }

    /**
     * Cua dau tien nguoi choi dung trong tam interact, hoac null neu khong co.
     * Tinh ban kinh dua tren hitbox cua player de tranh kich hoat tu xa.
     */
    public Door findActiveDoor(float interactRadius) {
        if (doors.isEmpty()) return null;
        float px = player.getX() + player.getHitboxOffsetX();
        float py = player.getY() + player.getHitboxOffsetY();
        float pw = player.getHitboxWidth();
        float ph = player.getHitboxHeight();
        for (Door door : doors) {
            if (door.playerInRange(px, py, pw, ph, interactRadius)) return door;
        }
        return null;
    }

    public Player getPlayer() { return player; }
    public HomeOwner getHomeOwner() { return homeOwner; }
    public Dog getDog() { return dog; }
    public RoomGraph getRoomGraph() { return roomGraph; }
    public WorldClock getClock() { return clock; }
    public ObjectiveTracker getObjectives() { return objectives; }
    public AlarmSystem getAlarmSystem() { return alarmSystem; }
    public TiledMap getTiledMap() { return tiledMap; }
    public CollisionMap getCollisionMap() { return collisionMap; }
    public List<Door> getDoors() { return doors; }

    @Override
    public void dispose() {
        if (tiledMap != null) tiledMap.dispose();
    }
}
