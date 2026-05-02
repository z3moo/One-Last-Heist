package com.onelastheist.game.app;

import com.onelastheist.game.OneLastHeistGame;
import com.onelastheist.game.ending.EndingType;
import com.onelastheist.game.screen.EndingScreen;
import com.onelastheist.game.screen.MainMenuScreen;
import com.onelastheist.game.screen.PauseScreen;
import com.onelastheist.game.screen.PlayScreen;

/** Noi quan ly tap trung cac thao tac chuyen man hinh. */
public class ScreenNavigator {
    private final OneLastHeistGame game;
    private final GameContext context;

    public ScreenNavigator(OneLastHeistGame game, GameContext context) {
        this.game = game;
        this.context = context;
    }

    public void showMainMenu() { game.setScreen(new MainMenuScreen(this)); }
    public void showPlayScreen() { game.setScreen(new PlayScreen(context, this)); }
    public void showPauseScreen() { game.setScreen(new PauseScreen(this)); }
    public void showEndingScreen(EndingType endingType) { game.setScreen(new EndingScreen(this, endingType)); }
}
