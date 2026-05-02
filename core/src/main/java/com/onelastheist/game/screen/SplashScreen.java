package com.onelastheist.game.screen;

import com.badlogic.gdx.ScreenAdapter;
import com.onelastheist.game.app.ScreenNavigator;

/** Man hinh tai/splash tuy chon truoc menu. */
public class SplashScreen extends ScreenAdapter {
    private final ScreenNavigator navigator;

    public SplashScreen(ScreenNavigator navigator) { this.navigator = navigator; }

    @Override
    public void show() { navigator.showMainMenu(); }
}
