package com.onelastheist.game.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.onelastheist.game.app.GameContext;
import com.onelastheist.game.app.ScreenNavigator;
import com.onelastheist.game.audio.SfxId;
import com.onelastheist.game.ending.EndingType;

/**
 * End-of-run screen. Displays the artwork that matches the {@link EndingType}
 * and fades it in from full black, mirroring the fade-to-black that
 * {@link PlayScreen} applies before transitioning. Click anywhere (or press
 * any key) to return to the main menu.
 *
 * <p>The fade is a single black ShapeRenderer rect drawn over the artwork
 * with alpha lerping from 1 → 0 over {@link #FADE_IN_SECONDS}. We start
 * input-blind during the fade-in so a held click on the way in doesn't
 * skip the screen before the player can see it.
 */
public class EndingScreen extends ScreenAdapter {
    private static final float WORLD_WIDTH = 1920f;
    private static final float WORLD_HEIGHT = 1080f;
    /** Length of the black-to-clear fade. Match PlayScreen's fade-out for a continuous transition. */
    private static final float FADE_IN_SECONDS = 1.5f;
    /** Grace period after the fade where input is ignored, so a held click doesn't immediately back out. */
    private static final float INPUT_GUARD_SECONDS = 0.4f;

    private final ScreenNavigator navigator;
    private final GameContext context;
    private final EndingType endingType;

    private Viewport viewport;
    private SpriteBatch batch;
    private ShapeRenderer shapes;
    private Texture artwork;
    private float fadeTimer;
    private boolean stingPlayed;

    public EndingScreen(ScreenNavigator navigator, GameContext context, EndingType endingType) {
        this.navigator = navigator;
        this.context = context;
        this.endingType = endingType;
    }

    @Override
    public void show() {
        viewport = new FitViewport(WORLD_WIDTH, WORLD_HEIGHT);
        batch = new SpriteBatch();
        shapes = new ShapeRenderer();
        artwork = new Texture(Gdx.files.internal(endingType.getTexturePath()));
        artwork.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        fadeTimer = 0f;
    }

    @Override
    public void render(float delta) {
        fadeTimer += delta;

        // Play the win/lose sting once the screen is actually visible. Doing
        // it here rather than in show() means the sting hits in sync with
        // the artwork appearing, not while we're still fully black.
        if (!stingPlayed && fadeTimer > 0.05f && context != null) {
            SfxId sting = endingType == EndingType.LOSE ? SfxId.LOSE : SfxId.WIN;
            context.getAudio().playSfx(sting);
            stingPlayed = true;
        }

        Gdx.gl.glClearColor(0f, 0f, 0f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        viewport.apply();
        batch.setProjectionMatrix(viewport.getCamera().combined);
        batch.begin();
        batch.setColor(Color.WHITE);
        // Stretch the art across the full virtual viewport. Source images
        // are pre-rendered scenes intended to fill the screen, so any aspect
        // mismatch is handled by FitViewport's letterboxing.
        batch.draw(artwork, 0f, 0f, WORLD_WIDTH, WORLD_HEIGHT);
        batch.end();

        // Black fade-in overlay. Linear alpha is the right shape here —
        // matches the fade-out PlayScreen runs and reads as a single
        // continuous transition rather than two separate fades.
        float fade = 1f - Math.min(1f, fadeTimer / FADE_IN_SECONDS);
        if (fade > 0f) {
            Gdx.gl.glEnable(GL20.GL_BLEND);
            Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
            shapes.setProjectionMatrix(viewport.getCamera().combined);
            shapes.begin(ShapeRenderer.ShapeType.Filled);
            shapes.setColor(0f, 0f, 0f, fade);
            shapes.rect(0f, 0f, WORLD_WIDTH, WORLD_HEIGHT);
            shapes.end();
            Gdx.gl.glDisable(GL20.GL_BLEND);
        }

        // After the fade-in plus a short grace, any click or key returns
        // to the main menu. The grace stops a still-held mouse button from
        // the gameplay click before the fade started.
        if (fadeTimer >= FADE_IN_SECONDS + INPUT_GUARD_SECONDS) {
            if (Gdx.input.justTouched() || Gdx.input.isKeyJustPressed(Input.Keys.ANY_KEY)) {
                if (context != null) context.getAudio().playSfx(SfxId.CLICK_OK);
                navigator.showMainMenu();
            }
        }
    }

    @Override
    public void resize(int width, int height) {
        if (width <= 0 || height <= 0) return;
        viewport.update(width, height, true);
    }

    @Override
    public void hide() {
        // Audio only. Same reason as PlayScreen.hide — Game.setScreen
        // calls hide() inside the outgoing screen's render frame; doing
        // anything destructive here would race the still-pending draw.
        if (context != null) context.getAudio().stopAllLoops();
    }

    @Override
    public void dispose() {
        if (artwork != null) { artwork.dispose(); artwork = null; }
        if (batch != null) { batch.dispose(); batch = null; }
        if (shapes != null) { shapes.dispose(); shapes = null; }
    }

    public ScreenNavigator getNavigator() { return navigator; }
    public EndingType getEndingType() { return endingType; }
}
