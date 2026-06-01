package com.onelastheist.game.world;

import com.badlogic.gdx.maps.tiled.TiledMap;
import com.badlogic.gdx.utils.Disposable;
import com.onelastheist.game.entity.npc.Dog;
import com.onelastheist.game.entity.npc.HomeOwner;
import com.onelastheist.game.entity.player.Player;
import com.onelastheist.game.trap.AlarmSystem;

import java.util.Collections;
import java.util.List;

/**
 * Runtime model of the active heist. Bundles every long-lived gameplay object
 * — player, NPCs, rooms, clock, objectives, alarm system, the loaded TiledMap,
 * its derived collision data, and the door definitions — behind a single handle
 * that screens and renderers can consume.
 *
 * <p>Construction is delegated to {@link WorldFactory}; once built, screens
 * call {@link #update(float)} every frame and read the rest as needed. Rendering
 * is intentionally kept out of this class — see {@link com.onelastheist.game.render.WorldRenderer}.
 */
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

    /** Per-frame tick. Advances the world clock and any NPCs that need a heartbeat. */
    public void update(float deltaSeconds) {
        clock.update(deltaSeconds);
        dog.update(deltaSeconds);
    }

    /**
     * Returns the first door whose bounds, expanded by {@code interactRadius},
     * overlap the player's hitbox — or {@code null} if the player is not near
     * any door. Used by {@link com.onelastheist.game.screen.PlayScreen} to decide
     * whether to draw the interact prompt and respond to the E key.
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

    /** Releases the loaded TiledMap. Other fields are POJOs that the GC handles. */
    @Override
    public void dispose() {
        if (tiledMap != null) tiledMap.dispose();
    }
}
