package com.onelastheist.game.world;

import com.onelastheist.game.entity.player.Player;
import com.onelastheist.game.trap.AlarmSystem;

/** Mo hinh world luc chay. Luu trang thai game; viec ve duoc xu ly o noi khac. */
public class GameWorld {
    private final Player player;
    private final RoomGraph roomGraph;
    private final WorldClock clock;
    private final ObjectiveTracker objectives;
    private final AlarmSystem alarmSystem;

    public GameWorld(Player player, RoomGraph roomGraph, WorldClock clock, ObjectiveTracker objectives, AlarmSystem alarmSystem) {
        this.player = player;
        this.roomGraph = roomGraph;
        this.clock = clock;
        this.objectives = objectives;
        this.alarmSystem = alarmSystem;
    }

    public void update(float deltaSeconds) { clock.update(deltaSeconds); }
    public Player getPlayer() { return player; }
    public RoomGraph getRoomGraph() { return roomGraph; }
    public WorldClock getClock() { return clock; }
    public ObjectiveTracker getObjectives() { return objectives; }
    public AlarmSystem getAlarmSystem() { return alarmSystem; }
}
