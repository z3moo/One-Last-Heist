package com.onelastheist.game.entity.npc;

import com.onelastheist.game.entity.base.MovableEntity;

/**
 * The homeowner NPC ("the neighbour"). Returns to the house in the final
 * minutes of the heist; once inside, hunts the player without distraction
 * until time runs out or the player is caught.
 *
 * <p>Lives on either the exterior or the interior map at any moment —
 * {@link #isOnInterior()} flips when {@link com.onelastheist.game.ai.HomeOwnerBrain}
 * finishes the approach phase and the homeowner steps inside. The renderer
 * uses this flag to decide which map should display him.
 */
public class HomeOwner extends MovableEntity {
    private NpcState state = NpcState.IDLE;
    /** True once the homeowner has reached the front door and entered the house. */
    private boolean onInterior;

    public NpcState getState() { return state; }
    public void setState(NpcState state) { this.state = state; }
    public boolean isOnInterior() { return onInterior; }
    public void setOnInterior(boolean onInterior) { this.onInterior = onInterior; }
}
