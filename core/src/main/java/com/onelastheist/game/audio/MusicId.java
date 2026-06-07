package com.onelastheist.game.audio;

/**
 * Catalog of looping music tracks. {@link AudioService} cross-fades between
 * these via {@link AudioService#playMusic(MusicId)}.
 */
public enum MusicId {
    /** Main menu, splash, ending screens. */
    MENU("MenuTheme.wav"),
    /** Gameplay theme — covers both the garden (exterior) and the house interior. */
    IN_HOUSE("InHouse.wav");

    private final String fileName;
    MusicId(String fileName) { this.fileName = fileName; }
    public String getFileName() { return fileName; }
}
