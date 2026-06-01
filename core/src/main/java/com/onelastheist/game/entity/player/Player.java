package com.onelastheist.game.entity.player;

import com.onelastheist.game.entity.base.MovableEntity;
import com.onelastheist.game.item.Inventory;

public class Player extends MovableEntity {
    public static final float WALK_SPEED = 360f;
    public static final float CROUCH_SPEED = 220f;

    private final Inventory inventory = new Inventory();
    private PlayerState state = PlayerState.NORMAL;
    private boolean crouching;

    public Inventory getInventory() { return inventory; }
    public PlayerState getState() { return state; }
    public boolean isCrouching() { return crouching; }
    public boolean isHidden() { return state == PlayerState.HIDING; }
    public void setCrouching(boolean crouching) { this.crouching = crouching; }
    public void hide() { state = PlayerState.HIDING; }
    public void leaveHiding() { state = PlayerState.NORMAL; }
    public void catchPlayer() { state = PlayerState.CAUGHT; }
}
