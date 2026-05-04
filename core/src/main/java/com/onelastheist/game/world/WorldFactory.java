package com.onelastheist.game.world;

import com.onelastheist.game.config.BalanceConfig;
import com.onelastheist.game.entity.npc.HomeOwner;
import com.onelastheist.game.entity.player.Player;
import com.onelastheist.game.trap.AlarmSystem;

/** Tao world tu cau hinh va du lieu map. */
public class WorldFactory {
    private final BalanceConfig balance;

    public WorldFactory(BalanceConfig balance) { this.balance = balance; }

    public GameWorld createDefaultWorld() {
        Player player = new Player();
        player.setPosition(520f, 280f);

        HomeOwner homeOwner = new HomeOwner();
        homeOwner.setPosition(690f, 280f);

        return new GameWorld(
            player,
            homeOwner,
            RoomGraph.createMainHouseGraph(),
            new WorldClock(balance.mainMapTimeSeconds),
            new ObjectiveTracker(balance.targetMoney),
            new AlarmSystem()
        );
    }
}
