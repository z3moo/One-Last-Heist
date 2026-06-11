package com.onelastheist.game.environment;

import com.onelastheist.game.entity.base.Entity;
import com.onelastheist.game.item.Inventory;
import com.onelastheist.game.item.Meat;

/**
 * Pre-placed meat the player can pick up off the floor with F. Distinct from
 * {@link DroppedMeat}: pickups are inert as far as the dog is concerned (they
 * sit on counters or in cabinets, beyond the dog's notice), while a
 * {@link DroppedMeat} is what the player creates by dropping meat from the
 * inventory — that one the dog will sense and eat.
 *
 * <p>Splitting the two avoids a "dog beelines for the meat the player just
 * walked past" failure mode on entry.
 */
public class MeatPickup extends Entity {
    private final Meat meat;
    private boolean collected;

    public MeatPickup(Meat meat, float x, float y) {
        this.meat = meat;
        setPosition(x, y);
    }

    public Meat getMeat() { return meat; }
    public boolean isCollected() { return collected; }

    public void collectInto(Inventory inventory) {
        if (collected) return;
        inventory.add(meat);
        collected = true;
    }
}
