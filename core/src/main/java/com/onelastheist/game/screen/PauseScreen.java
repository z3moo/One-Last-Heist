package com.onelastheist.game.screen;

import com.badlogic.gdx.ScreenAdapter;
import com.onelastheist.game.app.ScreenNavigator;

/** Man hinh tam dung tam thoi. */
public class PauseScreen extends ScreenAdapter {
    private final ScreenNavigator navigator;
    public PauseScreen(ScreenNavigator navigator) { this.navigator = navigator; }
    public ScreenNavigator getNavigator() { return navigator; }
}
