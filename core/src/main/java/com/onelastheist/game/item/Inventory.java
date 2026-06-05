package com.onelastheist.game.item;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Inventory {
    private final List<Item> items = new ArrayList<>();

    public void add(Item item) { items.add(item); }
    public boolean containsItem(String itemId) { return items.stream().anyMatch(item -> item.getId().equals(itemId)); }
    public boolean hasWeapon() { return items.stream().anyMatch(item -> item.getType() == ItemType.WEAPON); }
    public List<Item> getItems() { return Collections.unmodifiableList(items); }

    /**
     * Remove the first occurrence of the given item by reference. Returns true
     * if anything was actually removed. Used when the player drops an item
     * from the inventory back into the world (e.g. {@link Meat}); reference
     * equality is intentional so dropping one stack of meat doesn't blow away
     * a different stack with the same id.
     */
    public boolean removeFirstMatching(Item item) {
        for (int i = 0, n = items.size(); i < n; i++) {
            if (items.get(i) == item) {
                items.remove(i);
                return true;
            }
        }
        return false;
    }
}
