package com.onelastheist.game.ui;

import com.onelastheist.game.item.Inventory;

public class InventoryView {
    private final Inventory inventory;
    public InventoryView(Inventory inventory) { this.inventory = inventory; }
    public Inventory getInventory() { return inventory; }
}
