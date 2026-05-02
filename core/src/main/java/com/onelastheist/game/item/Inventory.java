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
}
