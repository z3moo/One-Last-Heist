package com.onelastheist.game.item;

public class ItemFactory {
    public MoneyItem money(String id, int value) { return new MoneyItem(id, "Money", value); }
    public KeyItem key(String id, String name) { return new KeyItem(id, name); }
    /** Drugged meat — eating it puts the dog out for a long sleep. */
    public Meat druggedMeat(String id) { return new Meat(id, "Meat (sleeping pills)", true); }
}
