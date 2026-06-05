package com.onelastheist.game.environment;

import com.onelastheist.game.entity.base.Entity;
import com.onelastheist.game.item.Meat;

/**
 * A piece of meat lying on the floor. Spawned either by world generation (the
 * pre-placed meats in the house) or when the player drops one from their
 * inventory. The dog senses these via {@link com.onelastheist.game.ai.DogBrain}
 * — if the meat is {@link #isDrugged()} and the dog reaches {@link #getX()} /
 * {@link #getY()}, the dog enters its drugged sleep state.
 *
 * <p>Marked {@link #consumed} once eaten so the brain ignores it on subsequent
 * frames; the world purges consumed entries each tick.
 */
public class DroppedMeat extends Entity {
    private final Meat meat;
    private boolean consumed;

    public DroppedMeat(Meat meat, float x, float y) {
        this.meat = meat;
        setPosition(x, y);
    }

    public Meat getMeat() { return meat; }
    public boolean isDrugged() { return meat.isDrugged(); }
    public boolean isConsumed() { return consumed; }
    public void consume() { consumed = true; }
}
