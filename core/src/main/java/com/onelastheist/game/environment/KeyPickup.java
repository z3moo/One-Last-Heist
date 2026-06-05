package com.onelastheist.game.environment;

import com.onelastheist.game.entity.base.Entity;
import com.onelastheist.game.interaction.Collectible;
import com.onelastheist.game.item.Inventory;
import com.onelastheist.game.item.KeyItem;

/**
 * A world key pickup collected with F. The key stays in the player's inventory
 * and is checked by locked doors instead of being consumed on pickup.
 */
public class KeyPickup extends Entity implements Collectible {
    private final KeyItem key;
    private boolean collected;

    public KeyPickup(KeyItem key, float x, float y) {
        this.key = key;
        setPosition(x, y);
    }

    public KeyItem getKey() { return key; }
    public boolean isCollected() { return collected; }

    @Override
    public void collectInto(Inventory inventory) {
        if (collected) return;
        inventory.add(key);
        collected = true;
    }
}
