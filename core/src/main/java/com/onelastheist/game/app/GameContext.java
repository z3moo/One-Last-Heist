package com.onelastheist.game.app;

import com.badlogic.gdx.assets.AssetManager;
import com.onelastheist.game.OneLastHeistGame;
import com.onelastheist.game.config.BalanceConfig;
import com.onelastheist.game.config.ControlConfig;
import com.onelastheist.game.config.GameConfig;

/** Chua cac dich vu va cau hinh dung chung trong ung dung. */
public class GameContext {
    private final OneLastHeistGame game;
    private final AssetManager assets;
    private final GameConfig gameConfig;
    private final ControlConfig controlConfig;
    private final BalanceConfig balanceConfig;

    public GameContext(OneLastHeistGame game) {
        this.game = game;
        this.assets = new AssetManager();
        this.gameConfig = new GameConfig();
        this.controlConfig = new ControlConfig();
        this.balanceConfig = new BalanceConfig();
    }

    public OneLastHeistGame getGame() { return game; }
    public AssetManager getAssets() { return assets; }
    public GameConfig getGameConfig() { return gameConfig; }
    public ControlConfig getControlConfig() { return controlConfig; }
    public BalanceConfig getBalanceConfig() { return balanceConfig; }

    public void dispose() {
        assets.dispose();
    }
}
