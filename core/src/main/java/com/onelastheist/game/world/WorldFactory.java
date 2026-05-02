package com.onelastheist.game.world;

import com.onelastheist.game.config.BalanceConfig;
import com.onelastheist.game.entity.player.Player;
import com.onelastheist.game.trap.AlarmSystem;

/** Tao world tu cau hinh va du lieu map. */
public class WorldFactory {
    private final BalanceConfig balance;

    public WorldFactory(BalanceConfig balance) { this.balance = balance; }

    public GameWorld createDefaultWorld() {
        return new GameWorld(
            new Player(),
            RoomGraph.createMainHouseGraph(),
            new WorldClock(balance.mainMapTimeSeconds),
            new ObjectiveTracker(balance.targetMoney),
            new AlarmSystem()
        );
    }
}
