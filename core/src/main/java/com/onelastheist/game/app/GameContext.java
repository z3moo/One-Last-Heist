package com.onelastheist.game.app;

import com.badlogic.gdx.assets.AssetManager;
import com.onelastheist.game.OneLastHeistGame;
import com.onelastheist.game.config.BalanceConfig;
import com.onelastheist.game.config.ControlConfig;
import com.onelastheist.game.config.GameConfig;

/**
 * Process-wide service locator. Holds objects that must outlive any single screen:
 * the LibGDX {@link AssetManager}, key-binding config, gameplay tuning, and a
 * back-reference to the {@link OneLastHeistGame} entry point.
 *
 * <p>Screens read from this rather than constructing their own copies — that way
 * a settings change (e.g. rebinding a key) takes effect immediately on every screen.
 */
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

    /** Releases native handles owned by the AssetManager. Called from {@link OneLastHeistGame#dispose()}. */
    public void dispose() {
        assets.dispose();
    }
}
