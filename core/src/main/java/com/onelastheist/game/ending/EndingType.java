package com.onelastheist.game.ending;

/**
 * Outcome of a heist run. Drives which artwork the {@link com.onelastheist.game.screen.EndingScreen}
 * shows and which sting the {@link com.onelastheist.game.audio.AudioService} plays.
 */
public enum EndingType {
    /** Caught by the homeowner OR time over without enough money. */
    LOSE("end/lose.png"),
    /**
     * Reserved — secret/special ending art. Wired to a trigger later;
     * for now {@link EndingResolver} never returns this.
     */
    WIN1("end/win1.png"),
    /**
     * Standard escape: >= targetMoney collected, either by leaving the
     * fenced garden on the exterior or by surviving until time over
     * uncaught.
     */
    WIN2("end/win2.png");

    private final String texturePath;
    EndingType(String texturePath) { this.texturePath = texturePath; }
    public String getTexturePath() { return texturePath; }
}
