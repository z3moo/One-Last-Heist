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

    // ---- Dog AI tuning ----
    /** World units / second when the dog patrols. Slower than {@link com.onelastheist.game.entity.player.Player#WALK_SPEED}=360 so a calm sweep reads as patrol, not pursuit. */
    public final float dogWanderSpeed = 220f;
    /** World units / second when the dog has heard or smelled the player and is closing on a target. Slightly above the player's walk speed so an upright player gets caught. */
    public final float dogChaseSpeed = 420f;
    /** Lower bound (seconds) the dog stays in the WANDERING state before lying down. */
    public final float dogWanderMinSeconds = 10f;
    /** Upper bound (seconds) the dog stays in the WANDERING state before lying down. */
    public final float dogWanderMaxSeconds = 15f;
    /** Lower bound (seconds) of a normal nap before the dog wakes and wanders again. */
    public final float dogSleepMinSeconds = 10f;
    /** Upper bound (seconds) of a normal nap before the dog wakes and wanders again. */
    public final float dogSleepMaxSeconds = 15f;
    /** Lower bound (seconds) of meat-induced deep sleep — cannot be interrupted. */
    public final float dogDruggedMinSeconds = 45f;
    /** Upper bound (seconds) of meat-induced deep sleep — cannot be interrupted. */
    public final float dogDruggedMaxSeconds = 60f;
    /** Distance (world units) at which the dog notices a noisy player. ~10 tiles. */
    public final float dogHearingRange = 480f;
    /** Distance (world units) at which the dog smells dropped meat. ~10 tiles. */
    public final float dogMeatSenseRange = 480f;
    /** Distance (world units) at which the dog reaches meat and eats it. */
    public final float dogMeatEatRange = 48f;
    /** Distance (world units) at which the dog can bite a chased player. ~1 tile of contact. */
    public final float dogBiteRange = 56f;
    /** Seconds of silence after which the dog gives up an investigation and resumes wandering. */
    public final float dogNoiseLostSeconds = 2f;
    /** Lower bound (seconds) of the wander stretch the dog drifts through after losing a noise / finishing an investigation, before lying down again. */
    public final float dogPostInvestigateWanderMinSeconds = 5f;
    /** Upper bound (seconds) of the same. */
    public final float dogPostInvestigateWanderMaxSeconds = 10f;

    // ---- Player audibility ----
    /** Walking-without-crouch noise radius (world units). Inside this circle a dog hears the player. */
    public final float playerWalkNoiseRadius = 480f;
}

