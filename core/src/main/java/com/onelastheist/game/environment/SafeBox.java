package com.onelastheist.game.environment;

import com.onelastheist.game.entity.player.Player;
import com.onelastheist.game.interaction.Interactable;
import com.onelastheist.game.interaction.Lockable;
import com.onelastheist.game.item.Inventory;

public class SafeBox implements Interactable, Lockable {
    private final String requiredKeyId;
    private boolean locked = true;

    public SafeBox(String requiredKeyId) { this.requiredKeyId = requiredKeyId; }
    @Override public void interact(Player player) { unlock(player.getInventory()); }
    @Override public boolean isLocked() { return locked; }
    @Override public boolean unlock(Inventory inventory) {
        if (inventory.containsItem(requiredKeyId)) locked = false;
        return !locked;
    }
}
