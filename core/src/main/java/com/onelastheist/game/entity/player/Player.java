package com.onelastheist.game.entity.player;

import com.onelastheist.game.entity.base.MovableEntity;
import com.onelastheist.game.item.Inventory;

public class Player extends MovableEntity {
    private final Inventory inventory = new Inventory();
    private PlayerState state = PlayerState.NORMAL;

    public Inventory getInventory() { return inventory; }
    public PlayerState getState() { return state; }
    public boolean isHidden() { return state == PlayerState.HIDING; }
    public void hide() { state = PlayerState.HIDING; }
    public void leaveHiding() { state = PlayerState.NORMAL; }
    public void catchPlayer() { state = PlayerState.CAUGHT; }
}
