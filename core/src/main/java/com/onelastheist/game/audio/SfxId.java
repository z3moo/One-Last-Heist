package com.onelastheist.game.audio;

/**
 * Catalog of one-shot and short-loop sound effects. Names match the WAV
 * files under {@code assets/sounds/} (post-toLowerCase) so {@link AudioService}
 * can map enum → asset path mechanically.
 */
public enum SfxId {
    /** UI button click — menu, pause overlay, ending screen. */
    CLICK_OK("Click_OK.wav"),
    /** Player picks up a money item, a key, or meat. */
    COLLECT_ITEMS("Collect_Items.wav"),
    /** Dog enters investigation state, or the dog has just bitten the player. */
    DOG("Dog.wav"),
    /** Plays once when the homeowner brain activates (before he walks home). */
    CAR_ARRIVE("CarArrive.wav"),
    /** Looped while the homeowner is in APPROACHING phase. Stops on entry. */
    FOOTSTEPS_HOMEOWNER("Footsteps_HomeOwner.wav"),
    /** Looped while the player is moving (any direction, walk or crouch). */
    FOOTSTEPS_THIEF("Footsteps_Thief.wav"),
    /** Available for the catch screen; not auto-wired. */
    LOSE("Lose.wav"),
    /** Available for the win screen; not auto-wired. */
    WIN("Win.wav"),
    /** Optional ambient suspense sting; not auto-wired. */
    SUSPENSE("Suspense.wav"),

    /** Piano puzzle — single C/D/E/F/G/A/B notes. Files live under sounds/piano/. */
    NOTE_C("piano/Note_C.wav"),
    NOTE_D("piano/Note_D.wav"),
    NOTE_E("piano/Note_E.wav"),
    NOTE_F("piano/Note_F.wav"),
    NOTE_G("piano/Note_G.wav"),
    NOTE_A("piano/Note_A.wav"),
    NOTE_B("piano/Note_B.wav");

    private final String fileName;
    SfxId(String fileName) { this.fileName = fileName; }
    public String getFileName() { return fileName; }
}
