package com.onelastheist.game.entity.player;

import com.onelastheist.game.entity.base.MovableEntity;
import com.onelastheist.game.item.Inventory;

/**
 * The player character. Holds an inventory, a high-level {@link PlayerState} (NORMAL
 * / HIDING / CAUGHT / FINAL_ENCOUNTER), and a crouch flag that swaps the active
 * speed and animation set.
 *
 * <p>Speed constants are referenced from {@link PlayerController} when the crouch
 * key state changes. Bumping these affects every screen since {@link MovableEntity}
 * uses the per-frame speed value during {@code tryMove}.
 */
public class Player extends MovableEntity {
    /** World units per second when walking upright. */
    public static final float WALK_SPEED = 360f;
    /** World units per second when crouched — slower, intended for stealth. */
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

    /**
     * True when the player is generating audible footsteps this frame: walking
     * upright. Crouch silences movement, and a stationary player makes no
     * noise at all. Sensed by NPC AI (e.g. {@link com.onelastheist.game.ai.DogBrain})
     * to decide whether to investigate.
     */
    public boolean isMakingNoise() {
        return isMoving() && !crouching;
    }
}
