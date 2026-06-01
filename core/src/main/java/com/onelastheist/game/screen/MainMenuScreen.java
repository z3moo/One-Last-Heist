package com.onelastheist.game.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.ImageButton;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener.ChangeEvent;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.onelastheist.game.app.ScreenNavigator;

/**
 * Title screen. Renders a full-screen background, four image buttons (Play /
 * Settings / Credits / Exit), and an optional credits popup drawn with
 * {@link ShapeRenderer} primitives.
 *
 * <p>Most of the file is layout/aesthetic: panel chrome, corner coins, rivets,
 * and a header plank are all hand-drawn shapes rather than pixel art so the
 * style scales cleanly with the virtual viewport.
 */
public class MainMenuScreen implements Screen {
    private static final float WORLD_WIDTH = 1920f;
    private static final float WORLD_HEIGHT = 1080f;
    private static final float BUTTON_WIDTH = 366f;
    private static final float BUTTON_HEIGHT = 99f;
    private static final float CREDITS_PANEL_WIDTH = 880f;
    private static final float CREDITS_PANEL_HEIGHT = 430f;
    private static final float CREDITS_PANEL_X = (WORLD_WIDTH - CREDITS_PANEL_WIDTH) / 2f;
    private static final float CREDITS_PANEL_Y = 330f;

    private final Array<Texture> textures = new Array<>();
    private final ScreenNavigator navigator;
    private final Vector3 touchPoint = new Vector3();

    private Viewport viewport;
    private SpriteBatch batch;
    private ShapeRenderer shapes;
    private Stage stage;
    private Table menuTable;
    private Texture background;
    private BitmapFont font;
    private GlyphLayout glyphLayout;
    private boolean showCredits;
    private boolean previousShowCredits;
    private boolean ignoreCreditsOutsideClick;
    private float statusTimer;
    private String statusMessage = "Play button clicked - add game screen here";

    public MainMenuScreen() {
        this(null);
    }

    public MainMenuScreen(ScreenNavigator navigator) {
        this.navigator = navigator;
    }

    @Override
    public void show() {
        viewport = new FitViewport(WORLD_WIDTH, WORLD_HEIGHT);
        batch = new SpriteBatch();
        shapes = new ShapeRenderer();
        stage = new Stage(viewport);
        Gdx.input.setInputProcessor(stage);

        background = loadTexture("start_screen/bg_main_menu.png");
        font = new BitmapFont();
        font.getData().setScale(2.2f);
        font.setUseIntegerPositions(false);
        glyphLayout = new GlyphLayout();

        addMenuButtons();
    }

    @Override
    public void render(float delta) {
        if (statusTimer > 0f) {
            statusTimer -= delta;
        }
        closeCreditsWhenClickingOutside();
        updateMenuTouchable();

        Gdx.gl.glClearColor(0f, 0f, 0f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        viewport.apply();
        batch.setProjectionMatrix(viewport.getCamera().combined);
        batch.begin();
        batch.setColor(Color.WHITE);
        batch.draw(background, 0f, 0f, WORLD_WIDTH, WORLD_HEIGHT);
        batch.end();

        stage.act(delta);
        stage.draw();

        drawOverlayMessages();
    }

    @Override
    public void resize(int width, int height) {
        if (width <= 0 || height <= 0) return;
        viewport.update(width, height, true);
    }

    @Override
    public void pause() {
        // Chua co xu ly khi tam dung.
    }

    @Override
    public void resume() {
        // Chua co xu ly khi tiep tuc.
    }

    @Override
    public void hide() {
        if (Gdx.input.getInputProcessor() == stage) {
            Gdx.input.setInputProcessor(null);
        }
    }

    @Override
    public void dispose() {
        if (stage != null) stage.dispose();
        if (batch != null) batch.dispose();
        if (shapes != null) shapes.dispose();
        if (font != null) font.dispose();
        for (Texture texture : textures) {
            texture.dispose();
        }
    }

    private void addMenuButtons() {
        ImageButton playButton = createButton(
            "start_screen/button_play/btn_play_normal.png",
            "start_screen/button_play/btn_play_hover.png",
            "start_screen/button_play/btn_play_pressed.png"
        );
        ImageButton creditsButton = createButton(
            "start_screen/button_credits/btn_credits_normal.png",
            "start_screen/button_credits/btn_credits_hover.png",
            "start_screen/button_credits/btn_credits_pressed.png"
        );
        ImageButton settingsButton = createButton(
            "start_screen/button_setting/btn_setting_normal.png",
            "start_screen/button_setting/btn_setting_hover.png",
            "start_screen/button_setting/btn_setting_pressed.png"
        );
        ImageButton exitButton = createButton(
            "start_screen/button_exit/btn_exit_normal.png",
            "start_screen/button_exit/btn_exit_hover.png",
            "start_screen/button_exit/btn_exit_pressed.png"
        );

        playButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                showCredits = false;
                if (navigator != null) {
                    navigator.showPlayScreen();
                } else {
                    statusMessage = "Play button clicked - add game screen here";
                    statusTimer = 1.5f;
                }
            }
        });
        settingsButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                showCredits = false;
                statusMessage = "Settings screen is not wired yet";
                statusTimer = 1.5f;
            }
        });
        creditsButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                showCredits = true;
                ignoreCreditsOutsideClick = true;
            }
        });
        exitButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                Gdx.app.exit();
            }
        });

        menuTable = new Table();
        menuTable.setFillParent(true);
        menuTable.bottom().padBottom(78f);
        menuTable.add(playButton).size(BUTTON_WIDTH, BUTTON_HEIGHT).padBottom(18f).row();
        menuTable.add(settingsButton).size(BUTTON_WIDTH, BUTTON_HEIGHT).padBottom(18f).row();
        menuTable.add(creditsButton).size(BUTTON_WIDTH, BUTTON_HEIGHT).padBottom(18f).row();
        menuTable.add(exitButton).size(BUTTON_WIDTH, BUTTON_HEIGHT);
        stage.addActor(menuTable);
    }

    private ImageButton createButton(String normalPath, String hoverPath, String pressedPath) {
        ImageButton.ImageButtonStyle style = new ImageButton.ImageButtonStyle();
        style.up = drawable(normalPath);
        style.over = drawable(hoverPath);
        style.down = drawable(pressedPath);
        return new ImageButton(style);
    }

    private TextureRegionDrawable drawable(String path) {
        return new TextureRegionDrawable(new TextureRegion(loadTexture(path)));
    }

    private Texture loadTexture(String path) {
        Texture texture = new Texture(Gdx.files.internal(path));
        texture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        textures.add(texture);
        return texture;
    }

    private void drawOverlayMessages() {
        if (!showCredits && statusTimer <= 0f) {
            return;
        }

        viewport.apply();

        if (showCredits) {
            drawCreditsPopup();
        } else {
            batch.setProjectionMatrix(viewport.getCamera().combined);
            batch.begin();
            drawCenteredText(statusMessage, 0f, 72f, WORLD_WIDTH, Color.WHITE, 2.2f);
            batch.end();
        }
    }

    private void drawCreditsPopup() {
        shapes.setProjectionMatrix(viewport.getCamera().combined);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(0f, 0f, 0f, 0.52f);
        shapes.rect(0f, 0f, WORLD_WIDTH, WORLD_HEIGHT);

        // Bong do mem.
        shapes.setColor(0f, 0f, 0f, 0.62f);
        shapes.rect(CREDITS_PANEL_X + 18f, CREDITS_PANEL_Y - 18f, CREDITS_PANEL_WIDTH, CREDITS_PANEL_HEIGHT);

        // Vien vang va nen bang toi ben trong.
        shapes.setColor(0.96f, 0.65f, 0.11f, 1f);
        shapes.rect(CREDITS_PANEL_X, CREDITS_PANEL_Y, CREDITS_PANEL_WIDTH, CREDITS_PANEL_HEIGHT);
        shapes.setColor(0.28f, 0.14f, 0.07f, 1f);
        shapes.rect(CREDITS_PANEL_X + 12f, CREDITS_PANEL_Y + 12f, CREDITS_PANEL_WIDTH - 24f, CREDITS_PANEL_HEIGHT - 24f);
        shapes.setColor(0.03f, 0.08f, 0.12f, 0.97f);
        shapes.rect(CREDITS_PANEL_X + 28f, CREDITS_PANEL_Y + 28f, CREDITS_PANEL_WIDTH - 56f, CREDITS_PANEL_HEIGHT - 56f);
        shapes.setColor(0.09f, 0.18f, 0.21f, 1f);
        shapes.rect(CREDITS_PANEL_X + 40f, CREDITS_PANEL_Y + CREDITS_PANEL_HEIGHT - 168f, CREDITS_PANEL_WIDTH - 80f, 5f);
        shapes.rect(CREDITS_PANEL_X + 40f, CREDITS_PANEL_Y + 82f, CREDITS_PANEL_WIDTH - 80f, 5f);

        drawHeaderPlank(CREDITS_PANEL_X + 105f, CREDITS_PANEL_Y + CREDITS_PANEL_HEIGHT - 125f, CREDITS_PANEL_WIDTH - 210f, 88f);
        drawCornerCoins(CREDITS_PANEL_X, CREDITS_PANEL_Y, CREDITS_PANEL_WIDTH, CREDITS_PANEL_HEIGHT);
        drawRivets(CREDITS_PANEL_X, CREDITS_PANEL_Y, CREDITS_PANEL_WIDTH, CREDITS_PANEL_HEIGHT);
        shapes.end();

        batch.setProjectionMatrix(viewport.getCamera().combined);
        batch.begin();
        batch.setColor(Color.WHITE);
        drawCenteredText("CREDITS", CREDITS_PANEL_X, CREDITS_PANEL_Y + CREDITS_PANEL_HEIGHT - 57f, CREDITS_PANEL_WIDTH, new Color(1f, 0.86f, 0.24f, 1f), 4.2f);
        drawCenteredText(
            "ONE LAST HEIST\n\nOOP Project - Group 30\nGame UI, programming, and design",
            CREDITS_PANEL_X + 72f,
            CREDITS_PANEL_Y + CREDITS_PANEL_HEIGHT - 180f,
            CREDITS_PANEL_WIDTH - 144f,
            new Color(0.92f, 0.95f, 0.88f, 1f),
            2.25f
        );
        batch.end();
    }

    private void closeCreditsWhenClickingOutside() {
        if (!showCredits || !Gdx.input.justTouched()) {
            return;
        }
        if (ignoreCreditsOutsideClick) {
            ignoreCreditsOutsideClick = false;
            return;
        }

        touchPoint.set(Gdx.input.getX(), Gdx.input.getY(), 0f);
        viewport.unproject(touchPoint);
        boolean clickedInsidePanel =
            touchPoint.x >= CREDITS_PANEL_X
                && touchPoint.x <= CREDITS_PANEL_X + CREDITS_PANEL_WIDTH
                && touchPoint.y >= CREDITS_PANEL_Y
                && touchPoint.y <= CREDITS_PANEL_Y + CREDITS_PANEL_HEIGHT;

        if (!clickedInsidePanel) {
            showCredits = false;
        }
    }

    private void updateMenuTouchable() {
        if (menuTable == null || previousShowCredits == showCredits) {
            return;
        }

        menuTable.setTouchable(showCredits ? Touchable.disabled : Touchable.enabled);
        previousShowCredits = showCredits;
    }

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
            float rivetX = x + 150f + i * 97f;
            shapes.circle(rivetX, y + height - 34f, 7f);
            shapes.circle(rivetX, y + 34f, 7f);
        }
    }

    private void drawCenteredText(String text, float x, float y, float width, Color color, float scale) {
        font.getData().setScale(scale);
        font.setColor(color);
        glyphLayout.setText(font, text, color, width, 1, true);
        font.draw(batch, glyphLayout, x, y);
    }
}
