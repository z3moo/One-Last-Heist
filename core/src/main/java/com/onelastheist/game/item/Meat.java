package com.onelastheist.game.item;

/**
 * A piece of meat the player can drop to lure the guard dog. Default {@link #drugged}
 * is true — i.e. the meat is laced with sleeping pills, which is the only kind of
 * meat the player can find in the house. The {@code drugged=false} constructor
 * exists for flavour text / future plain-meat pickups that don't put the dog out.
 */
public class Meat extends Item {
    private final boolean drugged;

    public Meat(String id, String name, boolean drugged) {
        super(id, name, ItemType.CONSUMABLE);
        this.drugged = drugged;
    }

    public boolean isDrugged() { return drugged; }
}
