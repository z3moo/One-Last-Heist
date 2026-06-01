package com.onelastheist.game;

import com.badlogic.gdx.Game;
import com.onelastheist.game.app.GameContext;
import com.onelastheist.game.app.ScreenNavigator;

/**
 * Application root. Every platform launcher (currently just the LWJGL3 desktop launcher)
 * instantiates this class. LibGDX calls {@link #create()} once after the GL context is
 * ready, then drives screens via the active {@link com.badlogic.gdx.Screen}.
 *
 * <p>Wires up the long-lived services ({@link GameContext}) and the screen router
 * ({@link ScreenNavigator}), then opens the main menu. Disposes the context on
 * shutdown so asset handles are released cleanly.
 */
public class OneLastHeistGame extends Game {
    private GameContext context;
    private ScreenNavigator navigator;

    @Override
    public void create() {
        // Order matters: context owns the AssetManager that screens may need on first show.
        context = new GameContext(this);
        navigator = new ScreenNavigator(this, context);
        navigator.showMainMenu();
    }

    public GameContext getContext() {
        return context;
    }

    public ScreenNavigator getNavigator() {
        return navigator;
    }

    @Override
    public void dispose() {
        super.dispose();
        if (context != null) {
            context.dispose();
        }
    }
}
