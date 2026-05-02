package com.onelastheist.game.item;

public class ItemFactory {
    public MoneyItem money(String id, int value) { return new MoneyItem(id, "Money", value); }
    public KeyItem key(String id, String name) { return new KeyItem(id, name); }
    public WeaponItem weapon(String id, String name, int power) { return new WeaponItem(id, name, power); }
    public EvidenceItem evidence(String id, String name) { return new EvidenceItem(id, name); }
}
