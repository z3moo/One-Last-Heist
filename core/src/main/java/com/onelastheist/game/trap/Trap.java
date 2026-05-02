package com.onelastheist.game.trap;

import com.onelastheist.game.interaction.Triggerable;

public abstract class Trap implements Triggerable {
    private final TrapTrigger triggerType;
    private boolean revealed;
    private boolean triggered;

    protected Trap(TrapTrigger triggerType) { this.triggerType = triggerType; }
    @Override public void trigger() { triggered = true; }
    @Override public boolean isTriggered() { return triggered; }
    public TrapTrigger getTriggerType() { return triggerType; }
    public boolean isRevealed() { return revealed; }
    public void reveal() { revealed = true; }
    public void hide() { revealed = false; }
}
