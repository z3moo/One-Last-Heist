package com.onelastheist.game;

import com.badlogic.gdx.Game;
import com.onelastheist.game.app.GameContext;
import com.onelastheist.game.app.ScreenNavigator;

/** Lop goc cua ung dung, duoc cac launcher nen tang su dung chung. */
public class OneLastHeistGame extends Game {
    private GameContext context;
    private ScreenNavigator navigator;

    @Override
    public void create() {
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
