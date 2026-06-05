package com.onelastheist.game.item;

public class ItemFactory {
    public MoneyItem money(String id, int value) { return new MoneyItem(id, "Money", value); }
    public KeyItem key(String id, String name) { return new KeyItem(id, name); }
    public WeaponItem weapon(String id, String name, int power) { return new WeaponItem(id, name, power); }
    public EvidenceItem evidence(String id, String name) { return new EvidenceItem(id, name); }
    /** Drugged meat — eating it puts the dog out for a long sleep. */
    public Meat druggedMeat(String id) { return new Meat(id, "Meat (sleeping pills)", true); }
    /** Plain meat — distracts the dog momentarily but doesn't drug. */
    public Meat plainMeat(String id) { return new Meat(id, "Meat", false); }
}
