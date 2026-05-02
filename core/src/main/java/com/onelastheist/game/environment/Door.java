package com.onelastheist.game.environment;

import com.onelastheist.game.entity.player.Player;
import com.onelastheist.game.interaction.Interactable;
import com.onelastheist.game.interaction.Lockable;
import com.onelastheist.game.item.Inventory;

public class Door implements Interactable, Lockable {
    private final Room targetRoom;
    private final String requiredKeyId;
    private boolean locked;

    public Door(Room targetRoom, String requiredKeyId) {
        this.targetRoom = targetRoom;
        this.requiredKeyId = requiredKeyId;
        this.locked = requiredKeyId != null && !requiredKeyId.isBlank();
    }

    @Override public void interact(Player player) { unlock(player.getInventory()); }
    @Override public boolean isLocked() { return locked; }
    @Override public boolean unlock(Inventory inventory) {
        if (!locked || inventory.containsItem(requiredKeyId)) locked = false;
        return !locked;
    }
    public Room getTargetRoom() { return targetRoom; }
}
