package com.onelastheist.game.item;

public class WeaponItem extends Item {
    private final int power;

    public WeaponItem(String id, String name, int power) {
        super(id, name, ItemType.WEAPON);
        this.power = power;
    }

    public int getPower() { return power; }
}
