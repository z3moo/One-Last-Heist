package com.onelastheist.game.config;

/**
 * Gameplay tuning knobs. Designers/test players adjust values here without touching
 * mechanics code. Consumed by {@link com.onelastheist.game.world.WorldFactory},
 * {@link com.onelastheist.game.world.ObjectiveTracker}, and trap-related systems.
 */
public class BalanceConfig {
    /** Cash needed before the player is allowed to escape via a normal-ending exit. */
    public final int targetMoney = 300;
    /** Total time on the main map before the homeowner returns and time-over triggers. */
    public final float mainMapTimeSeconds = 20f * 60f;
    /** How long a tripped trap shows its preview marker before going live. */
    public final float trapPreviewSeconds = 1.5f;
}
