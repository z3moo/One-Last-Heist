package com.onelastheist.game.ending;

import com.onelastheist.game.world.GameWorld;

/**
 * Maps the gameplay state at the moment of an ending trigger to one of the
 * three concrete {@link EndingType} buckets. Pure read-only inspection of
 * the world — no mutation, idempotent.
 *
 * <p>Decision tree:
 * <ol>
 *   <li>Player caught → {@link EndingType#LOSE}</li>
 *   <li>Time over AND money &lt; target → {@link EndingType#LOSE}</li>
 *   <li>Otherwise (time over uncaught with target met, or escape with target met)
 *       → {@link EndingType#WIN2}</li>
 * </ol>
 *
 * <p>{@link EndingType#WIN1} is reserved for a special trigger that hasn't
 * been wired yet — caller is responsible for choosing it directly when
 * that path comes online.
 */
public class EndingResolver {
    public EndingType resolve(GameWorld world) {
        if (world.getPlayer().isCaught()) return EndingType.LOSE;
        if (world.getClock().isTimeOver() && !world.getObjectives().hasEnoughMoney()) {
            return EndingType.LOSE;
        }
        return EndingType.WIN2;
    }
}
