package com.onelastheist.game.item;

public class MoneyItem extends Item {
    private final int value;

    public MoneyItem(String id, String name, int value) {
        super(id, name, ItemType.MONEY);
        this.value = value;
    }

    public int getValue() { return value; }
}
