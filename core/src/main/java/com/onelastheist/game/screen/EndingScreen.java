package com.onelastheist.game.screen;

import com.badlogic.gdx.ScreenAdapter;
import com.onelastheist.game.app.ScreenNavigator;
import com.onelastheist.game.ending.EndingType;

/** Hien thi ket qua ket thuc va tom tat. */
public class EndingScreen extends ScreenAdapter {
    private final ScreenNavigator navigator;
    private final EndingType endingType;

    public EndingScreen(ScreenNavigator navigator, EndingType endingType) {
        this.navigator = navigator;
        this.endingType = endingType;
    }

    public ScreenNavigator getNavigator() { return navigator; }
    public EndingType getEndingType() { return endingType; }
}
