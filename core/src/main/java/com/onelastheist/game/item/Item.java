package com.onelastheist.game.item;

public abstract class Item {
    private final String id;
    private final String name;
    private final ItemType type;

    protected Item(String id, String name, ItemType type) {
        this.id = id;
        this.name = name;
        this.type = type;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public ItemType getType() { return type; }
}
