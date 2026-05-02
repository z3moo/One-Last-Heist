package com.onelastheist.game.item;

public class InventorySlot {
    private Item item;

    public boolean isEmpty() { return item == null; }
    public Item getItem() { return item; }
    public void setItem(Item item) { this.item = item; }
    public Item removeItem() {
        Item removed = item;
        item = null;
        return removed;
    }
}
