package com.onelastheist.game.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
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
import com.onelastheist.game.entity.player.PlayerController;
import com.onelastheist.game.render.WorldRenderer;
import com.onelastheist.game.world.Door;
import com.onelastheist.game.world.GameWorld;
import com.onelastheist.game.world.WorldFactory;

import static com.onelastheist.game.world.WorldFactory.EXTERIOR_MAP_ID;
import static com.onelastheist.game.world.WorldFactory.MAIN_HOUSE_INTERIOR_ID;

/**
 * Gameplay screen. Owns the LibGDX lifecycle for an active heist run and
 * delegates simulation to the domain layer ({@link GameWorld}, {@link PlayerController}).
 *
 * <p>Three-phase state machine:
 * <ol>
 *   <li>{@code TUTORIAL} — the OPERATION MANUAL screen with four key-cards.
 *       Player can walk around freely; reaching the right edge starts the
 *       fade transition into the world.</li>
 *   <li>{@code FADING_TO_GAME} — short black fade while the world warms up.</li>
 *   <li>{@code GAME} — the real heist. Camera follows the player, collisions
 *       are active, door prompts appear when in range.</li>
 * </ol>
 *
 * <p>Pause overlay (ESC) freezes the simulation and shows three buttons. Door
 * prompts are an animated panel above any door the player is in range of:
 * gold + slow pulse for unlocked doors, red + fast shake/flash for locked ones
 * after the player presses E.
 */
public class PlayScreen extends ScreenAdapter {
    private static final float WORLD_WIDTH = 1920f;
    private static final float WORLD_HEIGHT = 1080f;
    private static final float ACTOR_DRAW_SIZE = 144f;
    private static final float ACTOR_CENTER_OFFSET = ACTOR_DRAW_SIZE / 2f;
    /**
     * Camera zoom while in {@link PlayPhase#GAME}. The {@link FitViewport} is
     * authored at 1920x1080 wu and the interior map is 4080x3840 wu, so a
     * zoom of 1.0 frames nearly half the house at once and reduces the
     * stealth game to "see everything from the doorway." 0.5 cuts the
     * visible region in half on each axis (showing ~20×11 tiles), which
     * forces the player to actually explore.
     */
    private static final float GAME_CAMERA_ZOOM = 0.5f;
    /** Zoom for non-GAME phases (tutorial / pause splash). 1.0 keeps the legacy framing. */
    private static final float MENU_CAMERA_ZOOM = 1.0f;
    private static final float MAP_START_X = 520f;
    private static final float MAP_START_Y = 280f;
    private static final float TUTORIAL_START_X = 280f;
    private static final float TUTORIAL_START_Y = 420f;
    private static final float TUTORIAL_MIN_X = 90f;
    private static final float TUTORIAL_MAX_X = WORLD_WIDTH - ACTOR_DRAW_SIZE - 90f;
    private static final float TUTORIAL_MIN_Y = 140f;
    private static final float TUTORIAL_MAX_Y = WORLD_HEIGHT - ACTOR_DRAW_SIZE - 180f;
    private static final float START_TRIGGER_X = WORLD_WIDTH - 260f;
    private static final float FADE_DURATION = 1.15f;
    private static final float PANEL_WIDTH = 620f;
    private static final float PANEL_HEIGHT = 450f;
    private static final float BUTTON_WIDTH = 366f;
    private static final float BUTTON_HEIGHT = 99f;
    private static final float BUTTON_GAP = 18f;
    private static final float PANEL_X = (WORLD_WIDTH - PANEL_WIDTH) / 2f;
    private static final float PANEL_Y = (WORLD_HEIGHT - PANEL_HEIGHT) / 2f;

    // Operation manual layout. Four cards in a row, identical internal geometry so
    // key shapes and key labels share anchors and always line up.
    private static final int CARD_COUNT = 4;
    private static final float CARD_WIDTH = 380f;
    private static final float CARD_HEIGHT = 340f;
    private static final float CARD_GAP = 36f;
    private static final float CARD_BOTTOM = 560f;
    private static final float CARDS_LEFT =
        (WORLD_WIDTH - (CARD_COUNT * CARD_WIDTH + (CARD_COUNT - 1) * CARD_GAP)) / 2f;
    private static final float CARD_HEADER_HEIGHT = 70f;
    private static final float CARD_FOOTER_HEIGHT = 90f;
    private static final float KEY_SIZE = 70f;
    private static final float KEY_GAP = 8f;
    private static final float KEY_AREA_CENTER_Y =
        CARD_BOTTOM + (CARD_FOOTER_HEIGHT + CARD_HEIGHT - CARD_HEADER_HEIGHT) / 2f;

    // Door interaction prompt.
    private static final float DOOR_INTERACT_RADIUS = 40f;
    private static final float PROMPT_PULSE_PERIOD = 1.4f;
    private static final float PROMPT_BOB_PERIOD = 1.8f;
    private static final float PROMPT_BOB_AMPLITUDE = 6f;
    private static final float PROMPT_WIDTH = 360f;
    private static final float PROMPT_HEIGHT = 78f;
    private static final float PROMPT_VERTICAL_OFFSET = 188f;
    private static final float LOCKED_FLASH_DURATION = 1.1f;

    private final GameContext context;
    private final ScreenNavigator navigator;
    private final Vector3 touchPoint = new Vector3();
    private final Rectangle resumeBounds = new Rectangle();
    private final Rectangle restartBounds = new Rectangle();
    private final Rectangle menuBounds = new Rectangle();
    private Texture resumeNormalTexture;
    private Texture resumeHoverTexture;
    private Texture resumePressedTexture;
    private Texture restartNormalTexture;
    private Texture restartHoverTexture;
    private Texture restartPressedTexture;
    private Texture menuNormalTexture;
    private Texture menuHoverTexture;
    private Texture menuPressedTexture;
    private GameWorld world;
    private WorldRenderer renderer;
    private PlayerController playerController;
    private Viewport viewport;
    private ShapeRenderer shapes;
    private SpriteBatch overlayBatch;
    private BitmapFont tutorialFont;
    private GlyphLayout tutorialLayout;
    private PlayPhase phase = PlayPhase.TUTORIAL;
    private float fadeTimer;
    private float promptTimer;
    private float lockedFlashTimer;
    private Door activeDoor;
    private boolean paused;

    public PlayScreen(GameContext context, ScreenNavigator navigator) {
        this.context = context;
        this.navigator = navigator;
    }

    @Override
    public void show() {
        world = new WorldFactory(context.getBalanceConfig()).createDefaultWorld();
        world.getPlayer().setPosition(TUTORIAL_START_X, TUTORIAL_START_Y);
        playerController = new PlayerController(context.getControlConfig());
        renderer = new WorldRenderer(world);
        viewport = new FitViewport(WORLD_WIDTH, WORLD_HEIGHT);
        shapes = new ShapeRenderer();
        overlayBatch = new SpriteBatch();
        tutorialFont = new BitmapFont();
        tutorialLayout = new GlyphLayout();
        tutorialFont.getRegion().getTexture().setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        resumeNormalTexture = loadTexture("start_screen/button_resume/btn_resume_normal.png");
        resumeHoverTexture = loadTexture("start_screen/button_resume/btn_resume_hover.png");
        resumePressedTexture = loadTexture("start_screen/button_resume/btn_resume_pressed.png");
        restartNormalTexture = loadTexture("start_screen/button_restart/btn_restart_normal.png");
        restartHoverTexture = loadTexture("start_screen/button_restart/btn_restart_hover.png");
        restartPressedTexture = loadTexture("start_screen/button_restart/btn_restart_pressed.png");
        menuNormalTexture = loadTexture("start_screen/button_backtomenu/btn_backtm_normal.png");
        menuHoverTexture = loadTexture("start_screen/button_backtomenu/btn_backtm_hover.png");
        menuPressedTexture = loadTexture("start_screen/button_backtomenu/btn_backtm_pressed.png");
        updateOverlayLayout();
    }

    @Override
    public void render(float delta) {
        handlePauseInput();
        if (!paused) {
            updateActivePhase(delta);
        } else {
            handlePausedActions();
        }

        updateCamera();
        updateOverlayLayout();

        Gdx.gl.glClearColor(0.03f, 0.04f, 0.06f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        if (phase == PlayPhase.GAME) {
            renderer.render(delta, (OrthographicCamera) viewport.getCamera());
            if (activeDoor != null) {
                drawDoorPrompt((OrthographicCamera) viewport.getCamera(), activeDoor);
            }
        } else {
            drawTutorialScreen(delta);
        }
        if (paused) {
            drawPauseOverlay();
        }
    }

    @Override
    public void resize(int width, int height) {
        if (width <= 0 || height <= 0) return;
        viewport.update(width, height, true);
        updateOverlayLayout();
    }

    public ScreenNavigator getNavigator() { return navigator; }

    @Override
    public void dispose() {
        if (renderer != null) renderer.dispose();
        if (world != null) world.dispose();
        if (shapes != null) shapes.dispose();
        if (overlayBatch != null) overlayBatch.dispose();
        if (resumeNormalTexture != null) resumeNormalTexture.dispose();
        if (resumeHoverTexture != null) resumeHoverTexture.dispose();
        if (resumePressedTexture != null) resumePressedTexture.dispose();
        if (restartNormalTexture != null) restartNormalTexture.dispose();
        if (restartHoverTexture != null) restartHoverTexture.dispose();
        if (restartPressedTexture != null) restartPressedTexture.dispose();
        if (menuNormalTexture != null) menuNormalTexture.dispose();
        if (menuHoverTexture != null) menuHoverTexture.dispose();
        if (menuPressedTexture != null) menuPressedTexture.dispose();
        if (tutorialFont != null) tutorialFont.dispose();
    }

    private void updateActivePhase(float delta) {
        if (phase == PlayPhase.TUTORIAL) {
            playerController.update(world.getPlayer(), delta);
            clampTutorialPlayer();
            if (world.getPlayer().getX() + ACTOR_CENTER_OFFSET >= START_TRIGGER_X) {
                phase = PlayPhase.FADING_TO_GAME;
                fadeTimer = 0f;
            }
            return;
        }

        if (phase == PlayPhase.FADING_TO_GAME) {
            fadeTimer += delta;
            if (fadeTimer >= FADE_DURATION) {
                phase = PlayPhase.GAME;
                world.getPlayer().setPosition(MAP_START_X, MAP_START_Y);
            }
            return;
        }

        playerController.update(world.getPlayer(), delta, world.getCollisionMap());
        world.update(delta);
        promptTimer += delta;
        if (lockedFlashTimer > 0f) lockedFlashTimer -= delta;
        activeDoor = world.findActiveDoor(DOOR_INTERACT_RADIUS);
        if (activeDoor != null && Gdx.input.isKeyJustPressed(context.getControlConfig().interact)) {
            triggerDoor(activeDoor);
        }
        // F: pick up the meat the player is standing on. Cheap to call every
        // frame the key is just-pressed; world.tryPickUpMeat() is a no-op when
        // there's nothing under the player or we're outdoors.
        if (Gdx.input.isKeyJustPressed(context.getControlConfig().collect)) {
            if (world.tryPickUpMeat()) {
                Gdx.app.log("PlayScreen", "Picked up meat");
            }
        }
        // G: drop a piece of meat at the player's feet. Same approach as F.
        if (Gdx.input.isKeyJustPressed(context.getControlConfig().dropMeat)) {
            if (world.tryDropMeat()) {
                Gdx.app.log("PlayScreen", "Dropped meat");
            }
        }
    }

    private void triggerDoor(Door door) {
        if (door.isLocked()) {
            // Cua khoa: nhay flash do trong vai giay, khong dieu huong.
            lockedFlashTimer = LOCKED_FLASH_DURATION;
            Gdx.app.log("PlayScreen", "Door locked: " + door.getTargetMapId());
            return;
        }
        // Map swaps in place — same screen, same WorldRenderer instance, just a
        // different active TiledMap and door list. Clearing activeDoor avoids
        // drawing a stale prompt for one frame at the new player position.
        String targetMapId = door.getTargetMapId();
        if (MAIN_HOUSE_INTERIOR_ID.equals(targetMapId)) {
            world.enterInterior();
            activeDoor = null;
            lockedFlashTimer = 0f;
            return;
        }
        if (EXTERIOR_MAP_ID.equals(targetMapId)) {
            world.returnToExterior();
            activeDoor = null;
            lockedFlashTimer = 0f;
            return;
        }
        Gdx.app.log("PlayScreen", "Door interact -> " + targetMapId);
    }

    private void clampTutorialPlayer() {
        float x = MathUtils.clamp(world.getPlayer().getX(), TUTORIAL_MIN_X, TUTORIAL_MAX_X);
        float y = MathUtils.clamp(world.getPlayer().getY(), TUTORIAL_MIN_Y, TUTORIAL_MAX_Y);
        world.getPlayer().setPosition(x, y);
    }

    private void handlePauseInput() {
        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
            paused = !paused;
        }
    }

    private void handlePausedActions() {
        if (!Gdx.input.justTouched()) {
            return;
        }

        touchPoint.set(Gdx.input.getX(), Gdx.input.getY(), 0f);
        viewport.unproject(touchPoint);

        if (restartBounds.contains(touchPoint.x, touchPoint.y)) {
            navigator.showPlayScreen();
            return;
        }

        if (resumeBounds.contains(touchPoint.x, touchPoint.y)) {
            paused = false;
            return;
        }

        if (menuBounds.contains(touchPoint.x, touchPoint.y)) {
            navigator.showMainMenu();
        }
    }

    private void drawPauseOverlay() {
        viewport.apply();
        OrthographicCamera camera = (OrthographicCamera) viewport.getCamera();
        float left = getCameraLeft(camera);
        float bottom = getCameraBottom(camera);
        shapes.setProjectionMatrix(camera.combined);
        overlayBatch.setProjectionMatrix(camera.combined);

        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(0f, 0f, 0f, 0.58f);
        shapes.rect(left, bottom, WORLD_WIDTH, WORLD_HEIGHT);

        drawPausePanel(left + PANEL_X, bottom + PANEL_Y);

        shapes.end();

        overlayBatch.begin();
        drawPauseButton(resumeBounds, resumeNormalTexture, resumeHoverTexture, resumePressedTexture);
        drawPauseButton(restartBounds, restartNormalTexture, restartHoverTexture, restartPressedTexture);
        drawPauseButton(menuBounds, menuNormalTexture, menuHoverTexture, menuPressedTexture);
        overlayBatch.end();
    }

    private void drawPauseButton(Rectangle bounds, Texture normalTexture, Texture hoverTexture, Texture pressedTexture) {
        Texture texture = normalTexture;
        if (isPressed(bounds)) {
            texture = pressedTexture;
        } else if (isHovered(bounds)) {
            texture = hoverTexture;
        }

        overlayBatch.setColor(Color.WHITE);
        overlayBatch.draw(texture, bounds.x, bounds.y, bounds.width, bounds.height);
    }

    /**
     * Ve panel "Press E to Interact" lo lung tren cua khi nguoi choi vao tam interact.
     * Animation: alpha pulse + bob len xuong; ca hai dung sin theo promptTimer de loop muot.
     * Cua khoa ve mau do; khi nhan E luc khoa, lockedFlashTimer lam halo va shake them manh.
     */
    private void drawDoorPrompt(OrthographicCamera camera, Door door) {
        boolean locked = door.isLocked();
        boolean flashing = locked && lockedFlashTimer > 0f;
        float flashStrength = flashing ? Math.max(0f, lockedFlashTimer / LOCKED_FLASH_DURATION) : 0f;

        // Pulse: nhanh gap doi khi flash de phan biet ro voi prompt thuong.
        float pulsePeriod = flashing ? PROMPT_PULSE_PERIOD * 0.5f : PROMPT_PULSE_PERIOD;
        float pulsePhase = (promptTimer / pulsePeriod) * MathUtils.PI2;
        float pulse = 0.5f + 0.5f * (float) Math.sin(pulsePhase);
        float alpha = 0.65f + 0.35f * pulse;

        float bobPhase = (promptTimer / PROMPT_BOB_PERIOD) * MathUtils.PI2;
        float bob = (float) Math.sin(bobPhase) * PROMPT_BOB_AMPLITUDE;

        // Shake nho khi flash, suy giam dan theo flashStrength.
        float shake = flashing ? (float) Math.sin(promptTimer * 38f) * 6f * flashStrength : 0f;

        float centerX = door.getCenterX() + shake;
        float baseY = door.getBounds().y + door.getBounds().height + PROMPT_VERTICAL_OFFSET + bob;
        float panelX = centerX - PROMPT_WIDTH / 2f;
        float panelY = baseY;

        // Bang mau theo trang thai cua.
        Color borderTint = locked
            ? lerpColor(0.85f, 0.18f, 0.16f, 1.0f, 0.42f, 0.18f, flashStrength)
            : new Color(0.96f, 0.65f, 0.13f, 1f);
        Color haloTint = locked
            ? lerpColor(0.92f, 0.20f, 0.16f, 1.0f, 0.42f, 0.18f, flashStrength)
            : new Color(0.96f, 0.62f, 0.13f, 1f);
        Color labelColor = locked
            ? lerpColor(1f, 0.42f, 0.42f, 1f, 0.85f, 0.45f, flashStrength)
            : new Color(1f, 0.85f, 0.45f, 1f);

        shapes.setProjectionMatrix(camera.combined);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapes.begin(ShapeRenderer.ShapeType.Filled);

        // Halo phia sau panel — manh hon khi flash.
        float haloAlpha = (0.18f + 0.32f * pulse) + flashStrength * 0.35f;
        shapes.setColor(haloTint.r, haloTint.g, haloTint.b, haloAlpha);
        float haloPad = 18f + flashStrength * 14f;
        shapes.rect(panelX - haloPad, panelY - haloPad, PROMPT_WIDTH + haloPad * 2f, PROMPT_HEIGHT + haloPad * 2f);

        // Bong duoi panel.
        shapes.setColor(0f, 0f, 0f, 0.55f * alpha);
        shapes.rect(panelX + 8f, panelY - 8f, PROMPT_WIDTH, PROMPT_HEIGHT);

        // Vien.
        shapes.setColor(borderTint.r, borderTint.g, borderTint.b, alpha);
        shapes.rect(panelX, panelY, PROMPT_WIDTH, PROMPT_HEIGHT);

        // Nen toi.
        shapes.setColor(0.07f, 0.075f, 0.09f, alpha);
        shapes.rect(panelX + 4f, panelY + 4f, PROMPT_WIDTH - 8f, PROMPT_HEIGHT - 8f);

        // Mui ten chi xuong cua o canh duoi panel.
        float arrowAlpha = 0.65f + 0.35f * pulse;
        shapes.setColor(borderTint.r, borderTint.g, borderTint.b, arrowAlpha);
        float arrowCx = panelX + PROMPT_WIDTH / 2f;
        float arrowTipY = panelY - 14f;
        shapes.triangle(arrowCx - 12f, panelY + 2f, arrowCx + 12f, panelY + 2f, arrowCx, arrowTipY);

        // Keycap nho cho phim E.
        float capW = 50f;
        float capH = 50f;
        float capX = panelX + 22f;
        float capY = panelY + (PROMPT_HEIGHT - capH) / 2f;
        shapes.setColor(0f, 0f, 0f, 0.45f * alpha);
        shapes.rect(capX + 3f, capY - 4f, capW, capH);
        shapes.setColor(0.86f, 0.86f, 0.90f, alpha);
        shapes.rect(capX, capY, capW, capH);
        shapes.setColor(0.97f, 0.97f, 1.0f, alpha);
        shapes.rect(capX + 5f, capY + 7f, capW - 10f, capH - 12f);
        shapes.setColor(0.55f, 0.55f, 0.60f, alpha);
        shapes.rect(capX + 4f, capY + 4f, capW - 8f, 5f);

        shapes.end();

        // Text: phim E va nhan label cua cua.
        overlayBatch.setProjectionMatrix(camera.combined);
        overlayBatch.begin();
        Color keyTextColor = new Color(0.10f, 0.10f, 0.12f, alpha);
        labelColor.a = alpha;
        drawTextInRect("E", capX, capY, capW, capH, 1.2f, keyTextColor);

        float labelX = capX + capW + 16f;
        float labelW = panelX + PROMPT_WIDTH - labelX - 16f;
        String labelText = locked ? "LOCKED" : door.getLabel();
        drawTextInRect(labelText, labelX, panelY, labelW, PROMPT_HEIGHT, 1.0f, labelColor);
        overlayBatch.end();
    }

    private static Color lerpColor(float r1, float g1, float b1, float r2, float g2, float b2, float t) {
        return new Color(
            r2 + (r1 - r2) * t,
            g2 + (g1 - g2) * t,
            b2 + (b1 - b2) * t,
            1f
        );
    }

    private void drawTutorialScreen(float delta) {
        viewport.apply();
        OrthographicCamera camera = (OrthographicCamera) viewport.getCamera();

        shapes.setProjectionMatrix(camera.combined);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        drawTutorialBackdrop();
        for (int i = 0; i < CARD_COUNT; i++) drawCardShape(i);
        drawContinueHintShape();
        shapes.end();

        renderer.renderPlayerOnly(delta, camera);

        overlayBatch.setProjectionMatrix(camera.combined);
        overlayBatch.begin();
        drawTutorialHeading();
        for (int i = 0; i < CARD_COUNT; i++) drawCardText(i);
        drawContinueHintText();
        overlayBatch.end();

        if (phase == PlayPhase.FADING_TO_GAME) {
            drawFadeOverlay(camera);
        }
    }

    private void drawTutorialBackdrop() {
        shapes.setColor(0.04f, 0.045f, 0.055f, 1f);
        shapes.rect(0f, 0f, WORLD_WIDTH, WORLD_HEIGHT);

        // Mep vang mong de phan vung tieu de va hint.
        shapes.setColor(0.96f, 0.62f, 0.13f, 0.32f);
        shapes.rect(0f, WORLD_HEIGHT - 160f, WORLD_WIDTH, 2f);
        shapes.rect(0f, 410f, WORLD_WIDTH, 2f);
    }

    private void drawCardShape(int index) {
        float x = CARDS_LEFT + index * (CARD_WIDTH + CARD_GAP);
        float y = CARD_BOTTOM;

        shapes.setColor(0f, 0f, 0f, 0.55f);
        shapes.rect(x + 8f, y - 8f, CARD_WIDTH, CARD_HEIGHT);

        shapes.setColor(0.95f, 0.62f, 0.13f, 1f);
        shapes.rect(x, y, CARD_WIDTH, CARD_HEIGHT);

        shapes.setColor(0.07f, 0.075f, 0.09f, 1f);
        shapes.rect(x + 4f, y + 4f, CARD_WIDTH - 8f, CARD_HEIGHT - 8f);

        float headerY = y + CARD_HEIGHT - CARD_HEADER_HEIGHT;
        shapes.setColor(0.95f, 0.62f, 0.13f, 1f);
        shapes.rect(x + 4f, headerY, CARD_WIDTH - 8f, CARD_HEADER_HEIGHT - 4f);
        shapes.setColor(0.7f, 0.42f, 0.08f, 1f);
        shapes.rect(x + 4f, headerY, CARD_WIDTH - 8f, 5f);

        // Mep ngan giua key area va description.
        shapes.setColor(0.18f, 0.20f, 0.24f, 1f);
        shapes.rect(x + 28f, y + CARD_FOOTER_HEIGHT - 4f, CARD_WIDTH - 56f, 2f);

        drawKeyClusterShape(x + CARD_WIDTH / 2f, KEY_AREA_CENTER_Y, cardKeys(index));
    }

    private void drawKeyClusterShape(float centerX, float centerY, String[][] rows) {
        float totalH = rows.length * KEY_SIZE + (rows.length - 1) * KEY_GAP;
        float bottomY = centerY - totalH / 2f;
        for (int row = 0; row < rows.length; row++) {
            String[] keys = rows[row];
            float totalW = 0f;
            for (String key : keys) totalW += keyWidth(key);
            totalW += (keys.length - 1) * KEY_GAP;
            float drawX = centerX - totalW / 2f;
            float drawY = bottomY + (rows.length - 1 - row) * (KEY_SIZE + KEY_GAP);
            for (String key : keys) {
                float w = keyWidth(key);
                if (!key.isEmpty()) drawKeycap(drawX, drawY, w, KEY_SIZE);
                drawX += w + KEY_GAP;
            }
        }
    }

    private void drawKeycap(float x, float y, float width, float height) {
        shapes.setColor(0f, 0f, 0f, 0.45f);
        shapes.rect(x + 4f, y - 5f, width, height);
        shapes.setColor(0.86f, 0.86f, 0.90f, 1f);
        shapes.rect(x, y, width, height);
        shapes.setColor(0.97f, 0.97f, 1.0f, 1f);
        shapes.rect(x + 6f, y + 8f, width - 12f, height - 14f);
        shapes.setColor(0.55f, 0.55f, 0.60f, 1f);
        shapes.rect(x + 4f, y + 4f, width - 8f, 6f);
    }

    private void drawTutorialHeading() {
        Color gold = new Color(1f, 0.8f, 0.3f, 1f);
        drawText("OPERATION MANUAL", WORLD_WIDTH / 2f, WORLD_HEIGHT - 70f, 2.4f, Color.WHITE, true);
        drawText("Learn the keys, then walk right when you are ready.",
            WORLD_WIDTH / 2f, WORLD_HEIGHT - 120f, 1.05f, gold, true);
    }

    private void drawCardText(int index) {
        float x = CARDS_LEFT + index * (CARD_WIDTH + CARD_GAP);
        float y = CARD_BOTTOM;
        float headerY = y + CARD_HEIGHT - CARD_HEADER_HEIGHT;

        Color titleColor = new Color(0.12f, 0.08f, 0.04f, 1f);
        Color descColor = new Color(0.85f, 0.88f, 0.92f, 1f);
        Color keyColor = new Color(0.10f, 0.10f, 0.12f, 1f);

        drawTextInRect(cardTitle(index), x, headerY, CARD_WIDTH, CARD_HEADER_HEIGHT - 4f, 1.55f, titleColor);
        drawTextInRect(cardDescription(index), x + 16f, y + 14f, CARD_WIDTH - 32f, CARD_FOOTER_HEIGHT - 28f, 0.95f, descColor);

        // Key labels — recompute the same anchors as drawKeyClusterShape so text always lines up.
        String[][] rows = cardKeys(index);
        float totalH = rows.length * KEY_SIZE + (rows.length - 1) * KEY_GAP;
        float bottomY = KEY_AREA_CENTER_Y - totalH / 2f;
        float centerX = x + CARD_WIDTH / 2f;
        for (int row = 0; row < rows.length; row++) {
            String[] keys = rows[row];
            float totalW = 0f;
            for (String key : keys) totalW += keyWidth(key);
            totalW += (keys.length - 1) * KEY_GAP;
            float drawX = centerX - totalW / 2f;
            float drawY = bottomY + (rows.length - 1 - row) * (KEY_SIZE + KEY_GAP);
            for (String key : keys) {
                float w = keyWidth(key);
                if (!key.isEmpty()) {
                    float scale = key.length() >= 2 ? 1.0f : 1.55f;
                    drawTextInRect(key, drawX, drawY, w, KEY_SIZE, scale, keyColor);
                }
                drawX += w + KEY_GAP;
            }
        }
    }

    private void drawContinueHintShape() {
        float bannerW = 540f;
        float bannerH = 70f;
        float bannerX = WORLD_WIDTH - bannerW - 90f;
        float bannerY = 320f;

        shapes.setColor(0f, 0f, 0f, 0.55f);
        shapes.rect(bannerX + 8f, bannerY - 8f, bannerW, bannerH);
        shapes.setColor(0.95f, 0.62f, 0.13f, 1f);
        shapes.rect(bannerX, bannerY, bannerW, bannerH);
        shapes.setColor(0.07f, 0.075f, 0.09f, 1f);
        shapes.rect(bannerX + 4f, bannerY + 4f, bannerW - 8f, bannerH - 8f);

        // Hai mui ten chevron ben phai.
        shapes.setColor(0.96f, 0.72f, 0.28f, 1f);
        float chevY = bannerY + bannerH / 2f;
        for (int i = 0; i < 2; i++) {
            float ox = bannerX + bannerW - 76f + i * 22f;
            shapes.triangle(ox - 16f, chevY + 16f, ox - 16f, chevY - 16f, ox + 4f, chevY);
        }
    }

    private void drawContinueHintText() {
        Color gold = new Color(1f, 0.8f, 0.3f, 1f);
        float bannerW = 540f;
        float bannerH = 70f;
        float bannerX = WORLD_WIDTH - bannerW - 90f;
        float bannerY = 320f;
        drawTextInRect("WALK RIGHT TO BEGIN", bannerX, bannerY, bannerW - 110f, bannerH, 1.15f, gold);
    }

    private String cardTitle(int index) {
        switch (index) {
            case 0: return "MOVE";
            case 1: return "ACTIONS";
            case 2: return "STEALTH";
            case 3: return "PAUSE";
            default: return "";
        }
    }

    private String cardDescription(int index) {
        switch (index) {
            case 0: return "Walk in 8 directions";
            case 1: return "F: pick up    E: interact    G: drop item";
            case 2: return "Crouch and walk quietly";
            case 3: return "Open the menu";
            default: return "";
        }
    }

    private String[][] cardKeys(int index) {
        switch (index) {
            case 0: return new String[][] { {"", "W", ""}, {"A", "S", "D"} };
            case 1: return new String[][] { {"F", "E", "G"} };
            case 2: return new String[][] { {"CTRL"} };
            case 3: return new String[][] { {"ESC"} };
            default: return new String[0][];
        }
    }

    private float keyWidth(String key) {
        if (key.isEmpty()) return KEY_SIZE;
        if (key.length() >= 4) return 150f;
        if (key.length() >= 2) return 108f;
        return KEY_SIZE;
    }

    private void drawTextInRect(String text, float rectX, float rectY, float rectW, float rectH, float scale, Color color) {
        tutorialFont.getData().setScale(scale);
        tutorialFont.setColor(color);
        tutorialLayout.setText(tutorialFont, text);
        float drawX = rectX + (rectW - tutorialLayout.width) / 2f;
        float drawY = rectY + (rectH + tutorialLayout.height) / 2f;
        tutorialFont.draw(overlayBatch, text, drawX, drawY);
    }

    private void drawText(String text, float x, float y, float scale, Color color, boolean centered) {
        tutorialFont.getData().setScale(scale);
        tutorialLayout.setText(tutorialFont, text);
        float drawX = centered ? x - tutorialLayout.width / 2f : x;
        tutorialFont.setColor(color);
        tutorialFont.draw(overlayBatch, text, drawX, y);
    }

    private void drawFadeOverlay(OrthographicCamera camera) {
        float alpha = MathUtils.clamp(fadeTimer / FADE_DURATION, 0f, 1f);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapes.setProjectionMatrix(camera.combined);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(0f, 0f, 0f, alpha);
        shapes.rect(0f, 0f, WORLD_WIDTH, WORLD_HEIGHT);
        shapes.end();
    }

    private void updateOverlayLayout() {
        OrthographicCamera camera = (OrthographicCamera) viewport.getCamera();
        float left = getCameraLeft(camera);
        float bottom = getCameraBottom(camera);
        float buttonX = PANEL_X + (PANEL_WIDTH - BUTTON_WIDTH) / 2f;
        float menuY = PANEL_Y + 50f;
        menuBounds.set(left + buttonX, bottom + menuY, BUTTON_WIDTH, BUTTON_HEIGHT);
        restartBounds.set(left + buttonX, bottom + menuY + BUTTON_HEIGHT + BUTTON_GAP, BUTTON_WIDTH, BUTTON_HEIGHT);
        resumeBounds.set(left + buttonX, restartBounds.y + BUTTON_HEIGHT + BUTTON_GAP, BUTTON_WIDTH, BUTTON_HEIGHT);
    }

    private void drawPausePanel(float panelX, float panelY) {
        shapes.setColor(0f, 0f, 0f, 0.62f);
        shapes.rect(panelX + 18f, panelY - 18f, PANEL_WIDTH, PANEL_HEIGHT);

        shapes.setColor(0.96f, 0.65f, 0.11f, 1f);
        shapes.rect(panelX, panelY, PANEL_WIDTH, PANEL_HEIGHT);
        shapes.setColor(0.28f, 0.14f, 0.07f, 1f);
        shapes.rect(panelX + 12f, panelY + 12f, PANEL_WIDTH - 24f, PANEL_HEIGHT - 24f);
        shapes.setColor(0.03f, 0.08f, 0.12f, 0.97f);
        shapes.rect(panelX + 28f, panelY + 28f, PANEL_WIDTH - 56f, PANEL_HEIGHT - 56f);

        drawCornerCoins(panelX, panelY, PANEL_WIDTH, PANEL_HEIGHT);
        drawRivets(panelX, panelY, PANEL_WIDTH, PANEL_HEIGHT);
    }

    private void updateCamera() {
        OrthographicCamera camera = (OrthographicCamera) viewport.getCamera();
        if (phase != PlayPhase.GAME) {
            camera.zoom = MENU_CAMERA_ZOOM;
            camera.position.set(WORLD_WIDTH / 2f, WORLD_HEIGHT / 2f, 0f);
            camera.update();
            return;
        }

        // Zoom is applied to the visible region, so the camera-clamp half-extents
        // and the world-edge clamp both have to honor it. With zoom 0.5 the
        // camera shows a 960×540 wu slice — that's the half-extents below.
        camera.zoom = GAME_CAMERA_ZOOM;
        float halfWidth = camera.viewportWidth * camera.zoom / 2f;
        float halfHeight = camera.viewportHeight * camera.zoom / 2f;
        float targetX = world.getPlayer().getX() + ACTOR_CENTER_OFFSET;
        float targetY = world.getPlayer().getY() + ACTOR_CENTER_OFFSET;

        // Read map size from the active collision map so the camera clamp adapts
        // when the player swaps between exterior (60x40 tiles) and interior (85x80).
        float mapWidth = world.getCollisionMap().getWorldWidth();
        float mapHeight = world.getCollisionMap().getWorldHeight();
        camera.position.set(
            MathUtils.clamp(targetX, halfWidth, mapWidth - halfWidth),
            MathUtils.clamp(targetY, halfHeight, mapHeight - halfHeight),
            0f
        );
        camera.update();
    }

    private float getCameraLeft(OrthographicCamera camera) {
        return camera.position.x - camera.viewportWidth * camera.zoom / 2f;
    }

    private float getCameraBottom(OrthographicCamera camera) {
        return camera.position.y - camera.viewportHeight * camera.zoom / 2f;
    }

    private Texture loadTexture(String path) {
        Texture texture = new Texture(Gdx.files.internal(path));
        texture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        return texture;
    }

    private boolean isPressed(Rectangle bounds) {
        if (!Gdx.input.isTouched()) {
            return false;
        }

        Vector3 pointer = getPointerWorldPosition();
        return bounds.contains(pointer.x, pointer.y);
    }

    private boolean isHovered(Rectangle bounds) {
        Vector3 pointer = getPointerWorldPosition();
        return bounds.contains(pointer.x, pointer.y);
    }

    private Vector3 getPointerWorldPosition() {
        touchPoint.set(Gdx.input.getX(), Gdx.input.getY(), 0f);
        viewport.unproject(touchPoint);
        return touchPoint;
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
        for (int i = 0; i < 4; i++) {
            float rivetX = x + 112f + i * 112f;
            shapes.circle(rivetX, y + height - 34f, 7f);
            shapes.circle(rivetX, y + 34f, 7f);
        }
    }

    private enum PlayPhase {
        TUTORIAL,
        FADING_TO_GAME,
        GAME
    }
}
