package com.onelastheist.game.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.onelastheist.game.app.GameContext;
import com.onelastheist.game.app.ScreenNavigator;
import com.onelastheist.game.audio.AudioService;
import com.onelastheist.game.audio.MusicId;
import com.onelastheist.game.audio.SfxId;

/**
 * Settings page. Same gold/plank chrome as the title screen credits popup
 * with two horizontal volume sliders (music, SFX) bound directly to
 * {@link AudioService}, and a Back button that returns to the main menu.
 *
 * <p>The sliders are drawn as a track + handle pair in {@link ShapeRenderer}
 * primitives — no scene2d skin needed, which keeps the visual identity
 * consistent with the rest of the menu (everything else here is also
 * hand-drawn shapes).
 *
 * <p>Drag-to-scrub: a click anywhere on a slider track snaps the handle
 * there; holding the mouse button continues to track the cursor while it
 * stays on or above the row. Clicking the SFX track also fires a short
 * preview tone so the player can hear the change without scrubbing
 * through silence.
 */
public class SettingsScreen implements Screen {
    private static final float WORLD_WIDTH = 1920f;
    private static final float WORLD_HEIGHT = 1080f;
    private static final float PANEL_WIDTH = 980f;
    private static final float PANEL_HEIGHT = 600f;
    private static final float PANEL_X = (WORLD_WIDTH - PANEL_WIDTH) / 2f;
    private static final float PANEL_Y = (WORLD_HEIGHT - PANEL_HEIGHT) / 2f;

    private static final float ROW_HEIGHT = 90f;
    private static final float ROW_LABEL_WIDTH = 230f;
    private static final float ROW_VALUE_WIDTH = 90f;
    private static final float ROW_GAP = 38f;

    private static final float SLIDER_HANDLE_RADIUS = 16f;
    private static final float SLIDER_TRACK_HEIGHT = 12f;

    private static final float BACK_BUTTON_WIDTH = 366f;
    private static final float BACK_BUTTON_HEIGHT = 99f;

    private final ScreenNavigator navigator;
    private final GameContext context;
    private final Vector3 touchPoint = new Vector3();
    private final Rectangle backButtonBounds = new Rectangle();
    private final Rectangle musicTrackBounds = new Rectangle();
    private final Rectangle sfxTrackBounds = new Rectangle();
    private final Rectangle musicRowBounds = new Rectangle();
    private final Rectangle sfxRowBounds = new Rectangle();

    private Viewport viewport;
    private SpriteBatch batch;
    private ShapeRenderer shapes;
    private BitmapFont font;
    private GlyphLayout glyphLayout;
    private Texture background;
    private Texture backNormal;
    private Texture backHover;
    private Texture backPressed;

    /** Which slider, if any, the user is currently scrubbing. */
    private enum Drag { NONE, MUSIC, SFX }
    private Drag dragging = Drag.NONE;
    /** Pending preview-tick: scheduled when SFX scrubbing changes; coalesced so we don't spam playSfx every frame. */
    private float sfxPreviewCooldown;

    public SettingsScreen(ScreenNavigator navigator, GameContext context) {
        this.navigator = navigator;
        this.context = context;
    }

    @Override
    public void show() {
        viewport = new FitViewport(WORLD_WIDTH, WORLD_HEIGHT);
        batch = new SpriteBatch();
        shapes = new ShapeRenderer();
        font = new BitmapFont();
        font.setUseIntegerPositions(false);
        glyphLayout = new GlyphLayout();

        background = loadTexture("start_screen/bg_main_menu.png");
        backNormal = loadTexture("start_screen/button_backtomenu/btn_backtm_normal.png");
        backHover = loadTexture("start_screen/button_backtomenu/btn_backtm_hover.png");
        backPressed = loadTexture("start_screen/button_backtomenu/btn_backtm_pressed.png");

        if (context != null) {
            context.getAudio().playMusic(MusicId.MENU);
        }

        Gdx.input.setInputProcessor(null);
        layoutControls();
    }

    @Override
    public void render(float delta) {
        sfxPreviewCooldown = Math.max(0f, sfxPreviewCooldown - delta);

        handleInput();

        Gdx.gl.glClearColor(0f, 0f, 0f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        viewport.apply();
        batch.setProjectionMatrix(viewport.getCamera().combined);
        batch.begin();
        batch.setColor(Color.WHITE);
        batch.draw(background, 0f, 0f, WORLD_WIDTH, WORLD_HEIGHT);
        batch.end();

        drawPanel();
        drawSliders();
        drawBackButton();

        // Press ESC to back out — the manual mirror of the Back button.
        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
            playClickSfx();
            navigator.showMainMenu();
        }
    }

    @Override
    public void resize(int width, int height) {
        if (width <= 0 || height <= 0) return;
        viewport.update(width, height, true);
        layoutControls();
    }

    @Override
    public void pause() {}

    @Override
    public void resume() {}

    @Override
    public void hide() {
        if (Gdx.input.getInputProcessor() != null) {
            Gdx.input.setInputProcessor(null);
        }
    }

    @Override
    public void dispose() {
        if (batch != null) batch.dispose();
        if (shapes != null) shapes.dispose();
        if (font != null) font.dispose();
        if (background != null) background.dispose();
        if (backNormal != null) backNormal.dispose();
        if (backHover != null) backHover.dispose();
        if (backPressed != null) backPressed.dispose();
    }

    // -- Layout -------------------------------------------------------------

    /**
     * Compute slider/back-button rectangles. Called on show + every resize so
     * the FitViewport's letterboxing doesn't leave the click targets where
     * they used to be.
     */
    private void layoutControls() {
        float titleSpace = 130f;
        float rowsTop = PANEL_Y + PANEL_HEIGHT - titleSpace - 30f;
        // Two rows stacked. Row Y is the bottom of the row strip.
        float musicRowY = rowsTop - ROW_HEIGHT;
        float sfxRowY = musicRowY - ROW_GAP - ROW_HEIGHT;

        float rowLeft = PANEL_X + 70f;
        float rowRight = PANEL_X + PANEL_WIDTH - 70f;
        float trackLeft = rowLeft + ROW_LABEL_WIDTH + 30f;
        float trackRight = rowRight - ROW_VALUE_WIDTH - 16f;
        float trackY = musicRowY + ROW_HEIGHT / 2f - SLIDER_TRACK_HEIGHT / 2f;

        musicRowBounds.set(rowLeft, musicRowY, rowRight - rowLeft, ROW_HEIGHT);
        sfxRowBounds.set(rowLeft, sfxRowY, rowRight - rowLeft, ROW_HEIGHT);
        musicTrackBounds.set(trackLeft, trackY, trackRight - trackLeft, SLIDER_TRACK_HEIGHT);
        // Same X span as music track, but down on the SFX row.
        sfxTrackBounds.set(trackLeft, sfxRowY + ROW_HEIGHT / 2f - SLIDER_TRACK_HEIGHT / 2f,
            trackRight - trackLeft, SLIDER_TRACK_HEIGHT);

        backButtonBounds.set(
            PANEL_X + (PANEL_WIDTH - BACK_BUTTON_WIDTH) / 2f,
            PANEL_Y + 40f,
            BACK_BUTTON_WIDTH,
            BACK_BUTTON_HEIGHT
        );
    }

    // -- Input --------------------------------------------------------------

    /**
     * Per-frame input. Drives slider scrub state, fires the back button on
     * release, and lets ESC mirror the back button. Drag state is sticky:
     * once a track is grabbed, the cursor can move anywhere (even off the
     * panel) until the button is released.
     */
    private void handleInput() {
        if (viewport == null) return;
        Vector3 pointer = pointerWorld();

        if (dragging != Drag.NONE) {
            if (!Gdx.input.isTouched()) {
                dragging = Drag.NONE;
            } else {
                Rectangle track = dragging == Drag.MUSIC ? musicTrackBounds : sfxTrackBounds;
                applySliderScrub(track, pointer.x, dragging);
                return;
            }
        }

        if (!Gdx.input.justTouched()) return;

        if (musicTrackBounds.contains(pointer.x, pointer.y)
            || musicRowBounds.contains(pointer.x, pointer.y)) {
            dragging = Drag.MUSIC;
            applySliderScrub(musicTrackBounds, pointer.x, Drag.MUSIC);
            return;
        }
        if (sfxTrackBounds.contains(pointer.x, pointer.y)
            || sfxRowBounds.contains(pointer.x, pointer.y)) {
            dragging = Drag.SFX;
            applySliderScrub(sfxTrackBounds, pointer.x, Drag.SFX);
            return;
        }
        if (backButtonBounds.contains(pointer.x, pointer.y)) {
            playClickSfx();
            navigator.showMainMenu();
        }
    }

    private void applySliderScrub(Rectangle track, float pointerX, Drag which) {
        float t = (pointerX - track.x) / track.width;
        t = MathUtils.clamp(t, 0f, 1f);
        if (context == null) return;
        AudioService audio = context.getAudio();
        if (which == Drag.MUSIC) {
            audio.setMusicVolume(t);
        } else {
            audio.setSfxVolume(t);
            // Preview the new SFX volume with a click tick; rate-limited so
            // dragging doesn't fire 60 ticks/sec.
            if (sfxPreviewCooldown <= 0f) {
                audio.playSfx(SfxId.CLICK_OK);
                sfxPreviewCooldown = 0.10f;
            }
        }
    }

    private Vector3 pointerWorld() {
        touchPoint.set(Gdx.input.getX(), Gdx.input.getY(), 0f);
        viewport.unproject(touchPoint);
        return touchPoint;
    }

    // -- Drawing ------------------------------------------------------------

    private void drawPanel() {
        shapes.setProjectionMatrix(viewport.getCamera().combined);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(0f, 0f, 0f, 0.55f);
        shapes.rect(0f, 0f, WORLD_WIDTH, WORLD_HEIGHT);

        // Drop shadow.
        shapes.setColor(0f, 0f, 0f, 0.62f);
        shapes.rect(PANEL_X + 18f, PANEL_Y - 18f, PANEL_WIDTH, PANEL_HEIGHT);

        // Gold border, brown plank, deep navy interior — same palette as
        // the credits popup so the two screens read as one design.
        shapes.setColor(0.96f, 0.65f, 0.11f, 1f);
        shapes.rect(PANEL_X, PANEL_Y, PANEL_WIDTH, PANEL_HEIGHT);
        shapes.setColor(0.28f, 0.14f, 0.07f, 1f);
        shapes.rect(PANEL_X + 12f, PANEL_Y + 12f, PANEL_WIDTH - 24f, PANEL_HEIGHT - 24f);
        shapes.setColor(0.03f, 0.08f, 0.12f, 0.97f);
        shapes.rect(PANEL_X + 28f, PANEL_Y + 28f, PANEL_WIDTH - 56f, PANEL_HEIGHT - 56f);

        // Header divider plank.
        drawHeaderPlank(PANEL_X + 130f, PANEL_Y + PANEL_HEIGHT - 135f, PANEL_WIDTH - 260f, 88f);
        drawCornerCoins(PANEL_X, PANEL_Y, PANEL_WIDTH, PANEL_HEIGHT);
        drawRivets(PANEL_X, PANEL_Y, PANEL_WIDTH, PANEL_HEIGHT);
        shapes.end();

        batch.setProjectionMatrix(viewport.getCamera().combined);
        batch.begin();
        Color titleGold = new Color(1f, 0.86f, 0.24f, 1f);
        drawCenteredText("SETTINGS",
            PANEL_X, PANEL_Y + PANEL_HEIGHT - 60f, PANEL_WIDTH, titleGold, 4.0f);
        batch.end();
    }

    private void drawSliders() {
        drawSlider(musicTrackBounds, musicRowBounds, "MUSIC",
            context == null ? 0.30f : context.getAudio().getMusicVolume());
        drawSlider(sfxTrackBounds, sfxRowBounds, "SOUND FX",
            context == null ? 0.30f : context.getAudio().getSfxVolume());
    }

    private void drawSlider(Rectangle track, Rectangle row, String label, float value) {
        float pct = MathUtils.clamp(value, 0f, 1f);
        float handleX = track.x + track.width * pct;
        float handleY = track.y + track.height / 2f;

        shapes.setProjectionMatrix(viewport.getCamera().combined);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapes.begin(ShapeRenderer.ShapeType.Filled);

        // Inset row backdrop — a slim plank inside the panel for each row
        // so the eye groups label/track/value as one control.
        shapes.setColor(0f, 0f, 0f, 0.32f);
        shapes.rect(row.x - 8f, row.y - 8f, row.width + 16f, row.height + 16f);
        shapes.setColor(0.07f, 0.13f, 0.16f, 1f);
        shapes.rect(row.x, row.y, row.width, row.height);
        shapes.setColor(0.96f, 0.62f, 0.13f, 1f);
        shapes.rect(row.x, row.y + row.height - 3f, row.width, 3f);
        shapes.rect(row.x, row.y, row.width, 3f);

        // Track. Filled portion uses the gold accent so the value reads
        // at a glance even without looking at the percentage label.
        shapes.setColor(0.18f, 0.20f, 0.24f, 1f);
        shapes.rect(track.x, track.y, track.width, track.height);
        shapes.setColor(0.96f, 0.65f, 0.11f, 1f);
        shapes.rect(track.x, track.y, track.width * pct, track.height);
        shapes.setColor(0.55f, 0.32f, 0.06f, 1f);
        shapes.rect(track.x, track.y + track.height - 2f, track.width, 2f);

        // Tick marks at every 25% so the slider feels measured.
        shapes.setColor(0.96f, 0.85f, 0.40f, 0.65f);
        for (int i = 0; i <= 4; i++) {
            float tx = track.x + track.width * (i / 4f);
            shapes.rect(tx - 1f, track.y - 6f, 2f, 6f);
        }

        // Handle: a coin-style stack to keep the heist motif consistent
        // with the corner coins on the panel.
        shapes.setColor(0f, 0f, 0f, 0.55f);
        shapes.circle(handleX + 3f, handleY - 3f, SLIDER_HANDLE_RADIUS);
        shapes.setColor(0.66f, 0.43f, 0.02f, 1f);
        shapes.circle(handleX, handleY, SLIDER_HANDLE_RADIUS);
        shapes.setColor(1f, 0.81f, 0.02f, 1f);
        shapes.circle(handleX, handleY, SLIDER_HANDLE_RADIUS - 3f);
        shapes.setColor(1f, 0.94f, 0.27f, 1f);
        shapes.circle(handleX - 4f, handleY + 4f, 5f);
        shapes.end();

        batch.setProjectionMatrix(viewport.getCamera().combined);
        batch.begin();
        Color labelColor = new Color(1f, 0.86f, 0.24f, 1f);
        Color valueColor = new Color(0.92f, 0.95f, 0.88f, 1f);
        drawTextLeft(label, row.x + 18f, row.y + row.height / 2f, 2.0f, labelColor);
        drawTextRight(Math.round(pct * 100f) + "%",
            row.x + row.width - 18f, row.y + row.height / 2f, 2.0f, valueColor);
        batch.end();
    }

    private void drawBackButton() {
        Texture tex = backNormal;
        Vector3 pointer = pointerWorld();
        boolean hover = backButtonBounds.contains(pointer.x, pointer.y);
        if (hover && Gdx.input.isTouched()) tex = backPressed;
        else if (hover) tex = backHover;

        batch.setProjectionMatrix(viewport.getCamera().combined);
        batch.begin();
        batch.setColor(Color.WHITE);
        batch.draw(tex, backButtonBounds.x, backButtonBounds.y,
            backButtonBounds.width, backButtonBounds.height);
        batch.end();
    }

    // -- Chrome (mirrors MainMenuScreen) ------------------------------------

    private void drawHeaderPlank(float x, float y, float width, float height) {
        shapes.setColor(0.34f, 0.16f, 0.07f, 1f);
        shapes.rect(x + 9f, y - 9f, width, height);
        shapes.triangle(x + 9f, y - 9f, x + 9f, y + height - 9f, x - 38f, y + height / 2f - 9f);
        shapes.triangle(x + width + 9f, y - 9f, x + width + 9f, y + height - 9f, x + width + 47f, y + height / 2f - 9f);

        shapes.setColor(0.66f, 0.33f, 0.13f, 1f);
        shapes.rect(x, y, width, height);
        shapes.triangle(x, y, x, y + height, x - 48f, y + height / 2f);
        shapes.triangle(x + width, y, x + width, y + height, x + width + 48f, y + height / 2f);

        shapes.setColor(0.80f, 0.45f, 0.20f, 1f);
        shapes.rect(x + 20f, y + height - 18f, width - 40f, 7f);
        shapes.setColor(0.39f, 0.18f, 0.08f, 1f);
        shapes.rect(x + 18f, y + 14f, width - 36f, 7f);
        for (int i = 0; i < 6; i++) {
            float stripeY = y + 25f + i * 10f;
            shapes.setColor(i % 2 == 0 ? 0.48f : 0.57f, 0.25f, 0.11f, 1f);
            shapes.rect(x + 28f, stripeY, width - 56f, 3f);
        }
    }

    private void drawCornerCoins(float x, float y, float width, float height) {
        float[][] coins = {
            {x + 55f, y + height - 70f}, {x + width - 55f, y + height - 70f},
            {x + 55f, y + 62f}, {x + width - 55f, y + 62f}
        };
        for (float[] coin : coins) {
            shapes.setColor(0.66f, 0.43f, 0.02f, 1f);
            shapes.circle(coin[0] + 5f, coin[1] - 6f, 31f);
            shapes.setColor(1f, 0.81f, 0.02f, 1f);
            shapes.circle(coin[0], coin[1], 31f);
            shapes.setColor(1f, 0.94f, 0.27f, 1f);
            shapes.circle(coin[0] - 8f, coin[1] + 8f, 10f);
        }
    }

    private void drawRivets(float x, float y, float width, float height) {
        shapes.setColor(0.95f, 0.62f, 0.13f, 1f);
        for (int i = 0; i < 7; i++) {
            float rivetX = x + 150f + i * 110f;
            shapes.circle(rivetX, y + height - 34f, 7f);
            shapes.circle(rivetX, y + 34f, 7f);
        }
    }

    // -- Text helpers -------------------------------------------------------

    private void drawCenteredText(String text, float x, float y, float width, Color color, float scale) {
        font.getData().setScale(scale);
        font.setColor(color);
        glyphLayout.setText(font, text, color, width, 1, true);
        font.draw(batch, glyphLayout, x, y);
    }

    private void drawTextLeft(String text, float x, float centerY, float scale, Color color) {
        font.getData().setScale(scale);
        font.setColor(color);
        glyphLayout.setText(font, text);
        font.draw(batch, text, x, centerY + glyphLayout.height / 2f);
    }

    private void drawTextRight(String text, float rightX, float centerY, float scale, Color color) {
        font.getData().setScale(scale);
        font.setColor(color);
        glyphLayout.setText(font, text);
        font.draw(batch, text, rightX - glyphLayout.width, centerY + glyphLayout.height / 2f);
    }

    private Texture loadTexture(String path) {
        Texture t = new Texture(Gdx.files.internal(path));
        t.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        return t;
    }

    private void playClickSfx() {
        if (context != null) context.getAudio().playSfx(SfxId.CLICK_OK);
    }
}
