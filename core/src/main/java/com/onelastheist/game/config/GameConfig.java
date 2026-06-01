package com.onelastheist.game.config;

/**
 * Global display constants. The virtual resolution is what every screen uses for
 * its {@link com.badlogic.gdx.utils.viewport.FitViewport}, so layout coordinates
 * stay stable even when the window is resized — LibGDX letterboxes to fit.
 */
public class GameConfig {
    public static final int VIRTUAL_WIDTH = 1920;
    public static final int VIRTUAL_HEIGHT = 1080;
    public static final String TITLE = "One Last Heist";
}
