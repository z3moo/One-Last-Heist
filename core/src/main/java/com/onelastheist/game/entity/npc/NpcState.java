package com.onelastheist.game.entity.npc;

/**
 * Lifecycle states an NPC can occupy. Drives both the AI tick (what behavior
 * runs this frame) and the renderer (which animation set to draw).
 *
 * <p>Dog-specific states:
 * <ul>
 *   <li>{@link #WANDERING} — walking around picking random directions.</li>
 *   <li>{@link #INVESTIGATING_NOISE} — heard the player and is closing on them.</li>
 *   <li>{@link #INVESTIGATING_MEAT} — sees a meat pickup and is closing on it.</li>
 *   <li>{@link #DRUGGED} — ate sleeping-pill meat; deep sleep, cannot wake until timer expires.</li>
 * </ul>
 */
public enum NpcState {
    IDLE,
    INVESTIGATING,
    INVESTIGATING_NOISE,
    INVESTIGATING_MEAT,
    SEARCHING,
    SLEEPING,
    WANDERING,
    RETURNING_HOME,
    DRUGGED,
    ALERTED
}
