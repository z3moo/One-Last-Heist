package com.onelastheist.game.entity.player;

/**
 * High-level player FSM state. {@code NORMAL} is the default; {@code HIDING}
 * pauses detection from NPCs; {@code CAUGHT} flips the run to a fail ending;
 * {@code FINAL_ENCOUNTER} is reserved for the climax sequence.
 */
public enum PlayerState {
    NORMAL,
    HIDING,
    CAUGHT,
    FINAL_ENCOUNTER
}
