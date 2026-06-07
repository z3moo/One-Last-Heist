package com.onelastheist.game.config;

/**
 * Gameplay tuning knobs. Designers/test players adjust values here without touching
 * mechanics code. Consumed by {@link com.onelastheist.game.world.WorldFactory},
 * {@link com.onelastheist.game.world.ObjectiveTracker}, and trap-related systems.
 */
public class BalanceConfig {
    /** Cash needed before the player is allowed to escape via a normal-ending exit. */
    public final int targetMoney = 360;
    /** Total time on the main map before the homeowner returns and time-over triggers. 10 minutes. */
    public final float mainMapTimeSeconds = 10f * 60f;
    /** How long a tripped trap shows its preview marker before going live. */
    public final float trapPreviewSeconds = 1.5f;
    /** Seconds deducted from the world clock each time the dog bites the player. */
    public final float bitePenaltySeconds = 30f;
    /** Duration (seconds) of the white-flash + "-30s" notification after a bite. */
    public final float biteFlashSeconds = 1.2f;

    // ---- Dog AI tuning ----
    /** World units / second when the dog patrols. Slower than {@link com.onelastheist.game.entity.player.Player#WALK_SPEED}=360 so a calm sweep reads as patrol, not pursuit. */
    public final float dogWanderSpeed = 220f;
    /** World units / second when the dog has heard or smelled the player and is closing on a target. Just above the player's 360 walk speed so a careless player gets caught, but a player who pivots/crouches has room to escape. */
    public final float dogChaseSpeed = 380f;
    /** Lower bound (seconds) the dog stays in the WANDERING state before lying down. */
    public final float dogWanderMinSeconds = 20f;
    /** Upper bound (seconds) the dog stays in the WANDERING state before lying down. */
    public final float dogWanderMaxSeconds = 20f;
    /** Lower bound (seconds) of a normal nap before the dog wakes and wanders again. */
    public final float dogSleepMinSeconds = 15f;
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

    // ---- Homeowner ("the neighbour") AI tuning ----
    /**
     * Clock value (seconds remaining) at which the homeowner arrives.
     * The heist is {@link #mainMapTimeSeconds} long, so 180s here means
     * "with 3 minutes left on the clock". The arrival also requires that
     * the player has at least entered the interior once — a player who
     * never went inside doesn't trigger him.
     */
    public final float homeOwnerArrivalSecondsRemaining = 3f * 60f;
    /** World units / second when the homeowner is searching the house. */
    public final float homeOwnerWanderSpeed = 240f;
    /** World units / second when the homeowner has spotted/heard the player. Slightly above the player's 360 walk so a careless player gets caught. */
    public final float homeOwnerChaseSpeed = 380f;
    /** Vision range (world units). 5 tiles. */
    public final float homeOwnerVisionRange = 240f;
    /**
     * Vision cone full-angle (degrees). 60 degrees = 30 to either side of
     * facing — a normal person's foveal vision plus near-peripheral, not
     * a 180-degree all-seeing arc.
     */
    public final float homeOwnerVisionAngleDegrees = 60f;
    /** Hearing range (world units). Same magnitude as the dog so behaviors feel symmetric. */
    public final float homeOwnerHearingRange = 480f;
    /** Distance (world units) at which the homeowner can grab a chased player. ~1 tile of contact. */
    public final float homeOwnerCatchRange = 56f;
    /** Seconds of silence after which the homeowner gives up the active chase and returns to searching. */
    public final float homeOwnerNoiseLostSeconds = 3f;
}

