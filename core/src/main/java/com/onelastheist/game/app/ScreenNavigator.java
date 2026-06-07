package com.onelastheist.game.app;

import com.onelastheist.game.OneLastHeistGame;
import com.onelastheist.game.ending.EndingType;
import com.onelastheist.game.screen.EndingScreen;
import com.onelastheist.game.screen.MainMenuScreen;
import com.onelastheist.game.screen.PauseScreen;
import com.onelastheist.game.screen.PlayScreen;
import com.onelastheist.game.screen.SettingsScreen;

/**
 * Centralized screen routing. Every {@code show*} method constructs the target
 * screen and hands it to {@link com.badlogic.gdx.Game#setScreen(com.badlogic.gdx.Screen)}.
 * Putting all transitions here keeps screens decoupled — a screen never instantiates
 * its successor directly, it just calls {@code navigator.showSomething()}.
 */
public class ScreenNavigator {
    private final OneLastHeistGame game;
    private final GameContext context;

    public ScreenNavigator(OneLastHeistGame game, GameContext context) {
        this.game = game;
        this.context = context;
    }

    public void showMainMenu() { game.setScreen(new MainMenuScreen(this, context)); }
    public void showPlayScreen() { game.setScreen(new PlayScreen(context, this)); }
    public void showPauseScreen() { game.setScreen(new PauseScreen(this)); }
    public void showSettings() { game.setScreen(new SettingsScreen(this, context)); }
    public void showEndingScreen(EndingType endingType) {
        game.setScreen(new EndingScreen(this, context, endingType));
    }
}
