package com.onelastheist.game.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.GL20;
import com.onelastheist.game.app.GameContext;
import com.onelastheist.game.app.ScreenNavigator;
import com.onelastheist.game.entity.player.PlayerController;
import com.onelastheist.game.render.WorldRenderer;
import com.onelastheist.game.world.GameWorld;
import com.onelastheist.game.world.WorldFactory;

/** Man hinh gameplay: quan ly vong doi LibGDX va uy quyen luat cho cac lop domain/world. */
public class PlayScreen extends ScreenAdapter {
    private final GameContext context;
    private final ScreenNavigator navigator;
    private GameWorld world;
    private WorldRenderer renderer;
    private PlayerController playerController;

    public PlayScreen(GameContext context, ScreenNavigator navigator) {
        this.context = context;
        this.navigator = navigator;
    }

    @Override
    public void show() {
        world = new WorldFactory(context.getBalanceConfig()).createDefaultWorld();
        playerController = new PlayerController(context.getControlConfig());
        renderer = new WorldRenderer(world);
    }

    @Override
    public void render(float delta) {
        playerController.update(world.getPlayer(), delta);
        world.update(delta);
        Gdx.gl.glClearColor(0.03f, 0.04f, 0.06f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        renderer.render(delta);
    }

    public ScreenNavigator getNavigator() { return navigator; }

    @Override
    public void dispose() {
        if (renderer != null) renderer.dispose();
    }
}
