package com.onelastheist.game.ending;

import com.onelastheist.game.entity.player.Player;
import com.onelastheist.game.world.GameWorld;

public class EndingResolver {
    public EndingType resolve(GameWorld world) {
        Player player = world.getPlayer();
        if (player.getState().name().equals("CAUGHT") || world.getClock().isTimeOver()) return EndingType.CAUGHT_OR_TIME_OVER;
        if (world.getObjectives().getEvidenceCount() >= 3 && player.getInventory().hasWeapon()) return EndingType.TRUE_ENDING;
        if (world.getObjectives().isHiddenRouteEntered()) return EndingType.HIDDEN_ROUTE_FAIL;
        return EndingType.NORMAL_ESCAPE;
    }
}
