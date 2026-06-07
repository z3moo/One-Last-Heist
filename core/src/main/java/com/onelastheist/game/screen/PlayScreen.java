package com.onelastheist.game.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
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
import com.onelastheist.game.ai.HomeOwnerBrain;
import com.onelastheist.game.audio.MusicId;
import com.onelastheist.game.audio.SfxId;
import com.onelastheist.game.ending.EndingResolver;
import com.onelastheist.game.ending.EndingType;
import com.onelastheist.game.entity.npc.NpcState;
import com.onelastheist.game.entity.player.PlayerController;
import com.onelastheist.game.environment.Newspaper;
import com.onelastheist.game.environment.PianoPuzzle;
import com.onelastheist.game.item.Item;
import com.onelastheist.game.item.ItemType;
import com.onelastheist.game.render.WorldRenderer;
import com.onelastheist.game.world.Door;
import com.onelastheist.game.world.GameWorld;
import com.onelastheist.game.world.WorldFactory;

import java.util.ArrayList;
import java.util.List;

import static com.onelastheist.game.world.WorldFactory.EXTERIOR_MAP_ID;
import static com.onelastheist.game.world.WorldFactory.MAIN_HOUSE_INTERIOR_ID;
import static com.onelastheist.game.world.WorldFactory.SIDE_HOUSE_INTERIOR_ID;

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

    // Operation manual layout. Five cards in a row, identical internal geometry
    // so key shapes and key labels share anchors and always line up. Card width
    // is sized so all five fit on a 1920-wu authoring viewport with a small gap.
    private static final int CARD_COUNT = 5;
    private static final float CARD_WIDTH = 340f;
    private static final float CARD_HEIGHT = 340f;
    private static final float CARD_GAP = 25f;
    private static final float CARD_BOTTOM = 560f;
    private static final float CARDS_LEFT =
        (WORLD_WIDTH - (CARD_COUNT * CARD_WIDTH + (CARD_COUNT - 1) * CARD_GAP)) / 2f;
    private static final float CARD_HEADER_HEIGHT = 70f;
    /** Bottom area of each card holds the description. Taller than before so the bigger description font has breathing room. */
    private static final float CARD_FOOTER_HEIGHT = 108f;
    /** Per-key icon size. Slightly smaller than the original 70wu — "smaller, just a bit". */
    private static final float KEY_SIZE = 60f;
    private static final float KEY_GAP = 8f;
    private static final float KEY_AREA_CENTER_Y =
        CARD_BOTTOM + (CARD_FOOTER_HEIGHT + CARD_HEIGHT - CARD_HEADER_HEIGHT) / 2f;
    /** Text scale for card description ("F: pick up", etc.). Bumped from 0.95 so functions read clearly at a glance. */
    private static final float CARD_DESCRIPTION_SCALE = 1.15f;

    // Door interaction prompt.
    private static final float DOOR_INTERACT_RADIUS = 40f;
    private static final float PROMPT_PULSE_PERIOD = 1.4f;
    private static final float PROMPT_BOB_PERIOD = 1.8f;
    private static final float PROMPT_BOB_AMPLITUDE = 6f;
    private static final float PROMPT_WIDTH = 360f;
    private static final float PROMPT_HEIGHT = 78f;
    private static final float PROMPT_VERTICAL_OFFSET = 188f;
    private static final float LOCKED_FLASH_DURATION = 1.1f;

    // Hotbar layout. Drawn at the bottom-center of the screen, fixed eight
    // slots — same height/width regardless of how many items the player carries
    // so the bar feels stable. Filled left-to-right; the selected slot is
    // highlighted gold and the item name is shown floating above the bar
    // briefly each time the selection changes.
    private static final int HOTBAR_SLOT_COUNT = 5;
    private static final float HOTBAR_SLOT_SIZE = 36f;
    private static final float HOTBAR_SLOT_GAP = 5f;
    private static final float HOTBAR_PADDING = 6f;
    private static final float HOTBAR_BOTTOM_MARGIN = 14f;
    private static final float HOTBAR_ITEM_DRAW = 28f;
    private static final float HOTBAR_LABEL_OFFSET = 10f;
    private static final float HOTBAR_LABEL_HEIGHT = 22f;
    /** How long (seconds) the floating item-name label stays visible after the player changes the selection. */
    private static final float HOTBAR_LABEL_DURATION = 2.2f;
    /** Tail of the label-fade window (seconds) where alpha ramps from 1 to 0. */
    private static final float HOTBAR_LABEL_FADE = 0.45f;

    // Top-of-screen HUD: a clock badge on the left (countdown) and a coin-
    // counter badge on the right (running total). Drawn in zoom-1 screen
    // space exactly like the pause overlay so the badges stay the same
    // visual size at any camera zoom.
    private static final float HUD_BADGE_WIDTH = 240f;
    private static final float HUD_BADGE_HEIGHT = 70f;
    private static final float HUD_TOP_PADDING = 28f;
    private static final float HUD_SIDE_PADDING = 42f;
    /** Text scale inside the HUD badges. The value field is the rectangular interior, not the icon. */
    private static final float HUD_TEXT_SCALE = 1.6f;
    /** How wide (fraction of badge width) is reserved on the left for the icon — clock has an orange tab, coin frame has a coin to the left. Text starts after this. */
    private static final float HUD_TEXT_LEFT_FRACTION = 0.30f;

    private final GameContext context;
    private final ScreenNavigator navigator;
    private final Vector3 touchPoint = new Vector3();
    private final Rectangle resumeBounds = new Rectangle();
    private final Rectangle restartBounds = new Rectangle();
    private final Rectangle menuBounds = new Rectangle();
    /** Click target for the symmetric BACK TO MENU banner on the operation manual page. */
    private final Rectangle tutorialBackBounds = new Rectangle();
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
    /** Newspaper currently in interact range, or null. Refreshed every frame next to {@link #activeDoor}. */
    private Newspaper activeNewspaper;
    /** True while the player is reading the broadside (E toggles). All input except ESC pause is suppressed. */
    private boolean newspaperOpen;
    /** Piano puzzle currently in interact range, or null. Drives the floating prompt. */
    private PianoPuzzle nearbyPiano;
    /**
     * Piano whose keyboard overlay is currently open, or null. Captured on
     * open and held until close so walking out of the interact range
     * doesn't NPE the input handler / overlay renderer.
     */
    private PianoPuzzle openPiano;
    /** True while the keyboard overlay is open. C/D/E/F/G/A/B play notes; Q closes. */
    private boolean pianoOpen;
    /** Body-part puzzle currently in interact range, or null. */
    private com.onelastheist.game.environment.BodyPartPuzzle nearbyBodyPart;
    /** Body-part puzzle whose question overlay is open, captured at open. */
    private com.onelastheist.game.environment.BodyPartPuzzle openBodyPart;
    /** True while the body-part question overlay is open. A/B/C/D answer; Q closes. */
    private boolean bodyPartOpen;
    /** Resolved ending if {@link PlayPhase#FADING_TO_END} is active. Drives which screen we navigate to once the fade completes. */
    private EndingType pendingEnding;
    /** Resolver instance is stateless; held as a field to avoid per-frame allocation. */
    private final EndingResolver endingResolver = new EndingResolver();
    private boolean paused;
    /** Selected hotbar slot index. Bounded to [0, HOTBAR_SLOT_COUNT). */
    private int hotbarSelection;
    /** Seconds remaining the floating item-name label is visible. Reset on each scroll/selection change. */
    private float hotbarLabelTimer;
    /** Reused buffer of player inventory items relevant to the hotbar. Refreshed every frame. */
    private final List<Item> hotbarItems = new ArrayList<>();
    private Texture hotbarMeatTexture;
    private Texture hotbarKeyTexture;
    private Texture hudClockTexture;
    private Texture hudCoinTexture;
    /** Full-page broadside drawn when the player has an interior newspaper open. */
    private Texture newspaperOpenTexture;
    /** Body-part question images, index 1..4. The puzzle's questionIndex selects which one to show. */
    private Texture[] questionTextures;

    // Audio state. We compare this-frame state to last-frame state so we can
    // fire one-shot SFX exactly on the transition (e.g. dog bite is "bite
    // flash went from inactive to active") instead of every frame the
    // condition holds. Loops are toggled per-frame; the AudioService
    // de-dupes redundant on/off calls.
    /** True while the gameplay theme is the active music. */
    private boolean gameplayMusicStarted;
    /** True last frame — use to detect 0→nonzero transitions for the bite SFX. */
    private boolean prevBiteFlashActive;
    /** True last frame — used to fire CarArrive once when the homeowner activates. */
    private boolean prevHomeOwnerActive;
    /** Dog's state last frame — used to fire the dog bark on the detect transition. */
    private NpcState prevDogState;

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
        // The hotbar shares item art with the world renderer, but the renderer's
        // textures are private to it. Loading our own copies keeps the hotbar
        // self-contained and means we don't depend on render-internal assets.
        hotbarMeatTexture = loadTexture("items/meat.png");
        hotbarKeyTexture = loadTexture("items/key.png");
        hudClockTexture = loadTexture("ui/hud/clock.png");
        hudCoinTexture = loadTexture("ui/hud/coin_counter.png");
        newspaperOpenTexture = loadTexture("items/opened.png");
        // Question images for the body-part puzzle. Index 0 is unused so
        // the puzzle's 1-based questionIndex maps directly to slot N.
        questionTextures = new Texture[5];
        for (int i = 1; i <= 4; i++) {
            questionTextures[i] = loadTexture("questions/" + i + ".png");
        }
        // Mouse wheel changes the selected hotbar slot. Set as the screen's
        // input processor — the rest of input goes through Gdx.input polling
        // which doesn't depend on the registered processor, so this doesn't
        // disturb keyboard / click handling.
        Gdx.input.setInputProcessor(new InputAdapter() {
            @Override
            public boolean scrolled(float amountX, float amountY) {
                // Block hotbar scroll while any overlay or lock is active —
                // newspaper/piano/pause/caught/fade-to-end. Without this the
                // wheel still cycled the hotbar selection behind the
                // 0.85-alpha overlays and the floating item-name label
                // bled through.
                if (phase != PlayPhase.GAME || paused
                    || newspaperOpen || pianoOpen || bodyPartOpen
                    || world.getPlayer().isCaught()) return false;
                // amountY is positive when the wheel turns DOWN (toward user).
                // Match a typical hotbar: wheel-down advances forward through
                // the slots, wheel-up goes back.
                int delta = amountY > 0f ? 1 : -1;
                hotbarSelection = Math.floorMod(hotbarSelection + delta, HOTBAR_SLOT_COUNT);
                hotbarLabelTimer = HOTBAR_LABEL_DURATION;
                return true;
            }
        });
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
        updateAudio();

        Gdx.gl.glClearColor(0.03f, 0.04f, 0.06f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        if (phase == PlayPhase.GAME || phase == PlayPhase.FADING_TO_END) {
            renderer.render(delta, (OrthographicCamera) viewport.getCamera());
            if (phase == PlayPhase.GAME) {
                if (activeDoor != null && !newspaperOpen && !pianoOpen && !bodyPartOpen) {
                    drawDoorPrompt((OrthographicCamera) viewport.getCamera(), activeDoor);
                }
                if (activeNewspaper != null && !newspaperOpen && !pianoOpen && !bodyPartOpen) {
                    drawNewspaperPrompt((OrthographicCamera) viewport.getCamera(), activeNewspaper);
                }
                if (nearbyPiano != null && !pianoOpen && !newspaperOpen && !bodyPartOpen) {
                    drawPianoPrompt((OrthographicCamera) viewport.getCamera(), nearbyPiano);
                }
                if (nearbyBodyPart != null && !pianoOpen && !newspaperOpen && !bodyPartOpen) {
                    drawBodyPartPrompt((OrthographicCamera) viewport.getCamera(), nearbyBodyPart);
                }
                drawHotbar((OrthographicCamera) viewport.getCamera());
                drawHud((OrthographicCamera) viewport.getCamera());
                if (world.isBiteFlashActive()) {
                    drawBiteFlash((OrthographicCamera) viewport.getCamera());
                }
                if (newspaperOpen) {
                    drawNewspaperOverlay((OrthographicCamera) viewport.getCamera());
                }
                if (pianoOpen && openPiano != null) {
                    drawPianoOverlay((OrthographicCamera) viewport.getCamera(), openPiano);
                }
                if (bodyPartOpen && openBodyPart != null) {
                    drawBodyPartOverlay((OrthographicCamera) viewport.getCamera(), openBodyPart);
                }
            } else {
                // FADING_TO_END: dim the world to black so the EndingScreen
                // can fade back in from black for a continuous transition.
                drawFadeOverlay((OrthographicCamera) viewport.getCamera());
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
    public void hide() {
        // Stop audio only — DO NOT dispose() here. Game.setScreen calls
        // hide() synchronously from inside the outgoing screen's render
        // tick (we navigate from updateActivePhase → showEndingScreen),
        // so disposing the world / renderer here would invalidate the
        // TiledMap mid-frame and the still-pending render block would
        // segfault in BufferUtils.copyJni from TileLayerOp.run.
        // Resources free in dispose() on app shutdown — pre-existing
        // leak across screens is acceptable; the crash isn't.
        if (context != null) {
            context.getAudio().stopAllLoops();
            context.getAudio().stopMusic();
        }
    }

    @Override
    public void dispose() {
        // Stop the gameplay theme + any active SFX loops so the audio doesn't
        // leak into the next screen. The AudioService itself is owned by
        // GameContext and survives the screen swap; only this screen's
        // claim on it ends here.
        if (context != null) {
            context.getAudio().stopAllLoops();
            context.getAudio().stopMusic();
        }
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
        if (hotbarMeatTexture != null) hotbarMeatTexture.dispose();
        if (hotbarKeyTexture != null) hotbarKeyTexture.dispose();
        if (hudClockTexture != null) hudClockTexture.dispose();
        if (hudCoinTexture != null) hudCoinTexture.dispose();
        if (newspaperOpenTexture != null) newspaperOpenTexture.dispose();
        if (questionTextures != null) {
            for (Texture t : questionTextures) if (t != null) t.dispose();
        }
        if (tutorialFont != null) tutorialFont.dispose();
    }

    private void updateActivePhase(float delta) {
        if (phase == PlayPhase.TUTORIAL) {
            if (handleTutorialActions()) return;
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

        if (phase == PlayPhase.FADING_TO_END) {
            // World still ticks during the fade so the homeowner walking
            // up to the player keeps animating instead of freezing into a
            // static frame. Player input is gated below — the world tick
            // is what matters for the visual.
            world.update(delta);
            fadeTimer += delta;
            if (fadeTimer >= FADE_DURATION) {
                navigator.showEndingScreen(pendingEnding);
            }
            return;
        }

        // Once caught, the world freezes input so the player can't keep
        // walking, opening doors, or grabbing loot. The actual game-over
        // screen is wired up in a later step; for now this lock makes the
        // catch read as a real consequence instead of a soft tap.
        // Reading the newspaper applies a similar lock: the player stops
        // walking and the broadside fills the screen until they press E
        // again to close it. The piano overlay does the same — full focus
        // on the keyboard until Q closes it.
        boolean caught = world.getPlayer().isCaught();
        boolean reading = newspaperOpen;
        if (!caught && !reading && !pianoOpen && !bodyPartOpen) {
            playerController.update(world.getPlayer(), delta, world.getCollisionMap());
        }
        world.update(delta);
        // Ending triggers — caught, time over, escape with enough money,
        // or all four body-part puzzles solved (hidden WIN1 path). Once
        // one fires we flip to FADING_TO_END and stop processing
        // gameplay input for the rest of this frame.
        if (checkEndingTriggers()) return;
        promptTimer += delta;
        if (lockedFlashTimer > 0f) lockedFlashTimer -= delta;
        if (hotbarLabelTimer > 0f) hotbarLabelTimer -= delta;
        activeDoor = world.findActiveDoor(DOOR_INTERACT_RADIUS);
        // Newspapers don't have a footprint, so this is independent of the
        // door check. They live in different rooms anyway.
        activeNewspaper = world.findActiveNewspaper();
        nearbyPiano = world.findActivePiano();
        nearbyBodyPart = world.findActiveBodyPart();
        // Tick whichever piano is currently relevant — the open one if any
        // (keeps the just-solved flash counting down past walking out of
        // range), otherwise the nearby one for prompt animation.
        PianoPuzzle pianoToTick = openPiano != null ? openPiano : nearbyPiano;
        if (pianoToTick != null) pianoToTick.update(delta);
        com.onelastheist.game.environment.BodyPartPuzzle bodyToTick =
            openBodyPart != null ? openBodyPart : nearbyBodyPart;
        if (bodyToTick != null) bodyToTick.update(delta);
        if (!caught) {
            if (bodyPartOpen) {
                handleBodyPartInput();
            } else if (pianoOpen) {
                handlePianoInput();
            } else if (reading) {
                // While reading: only E (close) is meaningful. Movement,
                // pickups, drops, and door interaction are all suppressed
                // so the player can't walk through walls behind the page.
                if (Gdx.input.isKeyJustPressed(context.getControlConfig().interact)) {
                    newspaperOpen = false;
                }
            } else if (activeNewspaper != null
                && Gdx.input.isKeyJustPressed(context.getControlConfig().interact)) {
                // E near a newspaper opens the broadside. Eat the keypress
                // so the same E doesn't immediately also trigger an
                // adjacent door (newspapers and doors won't normally
                // overlap in range, but be defensive).
                newspaperOpen = true;
            } else if (nearbyPiano != null
                && Gdx.input.isKeyJustPressed(context.getControlConfig().interact)) {
                // E near the piano opens the keyboard overlay. Capture the
                // piano reference here — the player can wander out of
                // interact range while the overlay is up; we keep using
                // openPiano until they press Q.
                openPiano = nearbyPiano;
                pianoOpen = true;
            } else if (nearbyBodyPart != null
                && Gdx.input.isKeyJustPressed(context.getControlConfig().interact)) {
                // E near a body-part clue opens the question overlay. Same
                // capture-on-open rule as the piano so leaving interact
                // range mid-question doesn't NPE the input handler.
                openBodyPart = nearbyBodyPart;
                bodyPartOpen = true;
            } else if (activeDoor != null
                && Gdx.input.isKeyJustPressed(context.getControlConfig().interact)) {
                triggerDoor(activeDoor);
            }
            // F: pick up whichever item the player is standing on. Money first
            // because it's the most common pickup; key second because it's
            // unique; meat last so a stray meat tile under the player can't
            // pre-empt collecting nearby money.
            if (!reading && !pianoOpen && !bodyPartOpen
                && Gdx.input.isKeyJustPressed(context.getControlConfig().collect)) {
                if (world.tryPickUpMoney()) {
                    Gdx.app.log("PlayScreen", "Picked up money");
                    context.getAudio().playSfx(SfxId.COLLECT_ITEMS);
                } else if (world.tryPickUpKey()) {
                    Gdx.app.log("PlayScreen", "Picked up key");
                    hotbarLabelTimer = HOTBAR_LABEL_DURATION;
                    context.getAudio().playSfx(SfxId.COLLECT_ITEMS);
                } else if (world.tryPickUpMeat()) {
                    Gdx.app.log("PlayScreen", "Picked up meat");
                    hotbarLabelTimer = HOTBAR_LABEL_DURATION;
                    context.getAudio().playSfx(SfxId.COLLECT_ITEMS);
                }
            }
            // G: drop a piece of meat at the player's feet. Same approach as F.
            if (!reading && !pianoOpen && !bodyPartOpen
                && Gdx.input.isKeyJustPressed(context.getControlConfig().dropMeat)) {
                if (world.tryDropMeat()) {
                    Gdx.app.log("PlayScreen", "Dropped meat");
                    hotbarLabelTimer = HOTBAR_LABEL_DURATION;
                }
            }
        }
    }

    /**
     * Inspect the world after this frame's tick and decide whether an
     * ending should fire. Returns true if a trigger fired, in which case
     * the caller should bail out of the rest of {@link #updateActivePhase}.
     *
     * <p>Triggers (first match wins):
     * <ul>
     *   <li>Caught by the homeowner → LOSE</li>
     *   <li>Time over &amp; money &lt; target → LOSE</li>
     *   <li>Time over uncaught with target met → WIN2</li>
     *   <li>On exterior, outside the fenced garden, target met, has been
     *       inside the house at least once → WIN2 (escape)</li>
     * </ul>
     *
     * <p>Once {@link #pendingEnding} is set we flip to {@link PlayPhase#FADING_TO_END}
     * and the world tick keeps running — but the player input branch
     * exits early so the player can't move during the fade.
     */
    private boolean checkEndingTriggers() {
        if (phase != PlayPhase.GAME) return false;
        EndingType resolved = null;
        if (world.getPlayer().isCaught()) {
            resolved = EndingType.LOSE;
        } else if (world.getClock().isTimeOver()) {
            resolved = world.getObjectives().hasEnoughMoney() ? EndingType.WIN2 : EndingType.LOSE;
        } else if (world.areAllBodyPartsSolved()) {
            // Hidden route: all four body-part questions answered correctly
            // before the clock runs out. Beats the regular escape-with-cash
            // win and is checked first so the player gets WIN1 even if they
            // happen to also be carrying enough money outside the garden.
            resolved = EndingType.WIN1;
        } else if (world.getObjectives().hasEnoughMoney()
            && world.hasPlayerEnteredHouse()
            && world.isPlayerOutsideGarden()) {
            resolved = EndingType.WIN2;
        }
        if (resolved == null) return false;
        pendingEnding = resolved;
        phase = PlayPhase.FADING_TO_END;
        fadeTimer = 0f;
        // Drop any open overlays so the fade-out is purely the world dim.
        newspaperOpen = false;
        pianoOpen = false;
        openPiano = null;
        bodyPartOpen = false;
        openBodyPart = null;
        // Stop SFX loops AND the gameplay music immediately. Music keeps
        // playing across screen swaps because LibGDX only calls hide() on
        // setScreen, not dispose(); without an explicit stop the InHouse
        // theme overlaps the win/lose sting on the EndingScreen.
        if (context != null) {
            context.getAudio().stopAllLoops();
            context.getAudio().stopMusic();
        }
        return true;
    }

    private void triggerDoor(Door door) {
        String targetMapId = door.getTargetMapId();
        if (door.isLocked()) {
            if (SIDE_HOUSE_INTERIOR_ID.equals(targetMapId) && world.hasSideHouseKey()) {
                door.unlock();
            } else {
                // Cua khoa: nhay flash do trong vai giay, khong dieu huong.
                lockedFlashTimer = LOCKED_FLASH_DURATION;
                Gdx.app.log("PlayScreen", "Door locked: " + targetMapId);
                return;
            }
        }
        // Map swaps in place — same screen, same WorldRenderer instance, just a
        // different active TiledMap and door list. Clearing activeDoor avoids
        // drawing a stale prompt for one frame at the new player position.
        // Also drop any newspaper-read state — the only newspaper is in the
        // main house, so a swap means we shouldn't be reading anymore.
        if (MAIN_HOUSE_INTERIOR_ID.equals(targetMapId)) {
            world.enterInterior();
            activeDoor = null;
            activeNewspaper = null;
            newspaperOpen = false;
            nearbyPiano = null;
            openPiano = null;
            pianoOpen = false;
            nearbyBodyPart = null;
            openBodyPart = null;
            bodyPartOpen = false;
            lockedFlashTimer = 0f;
            return;
        }
        if (SIDE_HOUSE_INTERIOR_ID.equals(targetMapId)) {
            world.enterSideHouse();
            activeDoor = null;
            activeNewspaper = null;
            newspaperOpen = false;
            nearbyPiano = null;
            openPiano = null;
            pianoOpen = false;
            nearbyBodyPart = null;
            openBodyPart = null;
            bodyPartOpen = false;
            lockedFlashTimer = 0f;
            return;
        }
        if (EXTERIOR_MAP_ID.equals(targetMapId)) {
            world.returnToExterior();
            activeDoor = null;
            activeNewspaper = null;
            newspaperOpen = false;
            nearbyPiano = null;
            openPiano = null;
            pianoOpen = false;
            nearbyBodyPart = null;
            openBodyPart = null;
            bodyPartOpen = false;
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

    /**
     * Tutorial-phase click + key handler. Catches the BACK TO MENU banner
     * (mirror of WALK RIGHT TO BEGIN) and the ESC shortcut to bail back to
     * the title screen without going through pause. Returns {@code true}
     * when navigation was triggered so the caller can short-circuit the
     * rest of the tutorial frame.
     */
    private boolean handleTutorialActions() {
        // ESC during the manual screen pops back to main menu directly.
        // The pause overlay is meaningful during gameplay only.
        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
            if (context != null) context.getAudio().playSfx(SfxId.CLICK_OK);
            navigator.showMainMenu();
            return true;
        }
        if (!Gdx.input.justTouched()) return false;
        // Tutorial uses MENU_CAMERA_ZOOM (1.0) so a straight unproject
        // through the live viewport maps to the same coords used by
        // tutorialBackBounds.
        touchPoint.set(Gdx.input.getX(), Gdx.input.getY(), 0f);
        viewport.unproject(touchPoint);
        if (tutorialBackBounds.contains(touchPoint.x, touchPoint.y)) {
            if (context != null) context.getAudio().playSfx(SfxId.CLICK_OK);
            navigator.showMainMenu();
            return true;
        }
        return false;
    }

    private void handlePauseInput() {
        // Don't let pause stall the ending fade — the fade timer only
        // advances inside updateActivePhase, and the pause overlay would
        // block updateActivePhase indefinitely. Catch- and time-over have
        // already happened by this point; there's nothing meaningful to
        // pause for during the dim-out.
        if (phase == PlayPhase.FADING_TO_END) return;
        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
            paused = !paused;
        }
    }

    private void handlePausedActions() {
        if (!Gdx.input.justTouched()) {
            return;
        }

        // Click bounds (resume/restart/menu) are laid out using the
        // zoom-1.0 menu framing inside updateOverlayLayout. During GAME
        // phase the live camera is still at GAME_CAMERA_ZOOM (0.5) from
        // the prior frame, so unprojecting through it would map a click
        // on the visible MENU button to the wrong world coords — and
        // the click would land on RESTART instead, which is what was
        // sending the player back to the operation manual when they
        // expected to return to the main menu.
        OrthographicCamera camera = (OrthographicCamera) viewport.getCamera();
        float savedZoom = camera.zoom;
        camera.zoom = MENU_CAMERA_ZOOM;
        camera.update();
        touchPoint.set(Gdx.input.getX(), Gdx.input.getY(), 0f);
        viewport.unproject(touchPoint);
        camera.zoom = savedZoom;
        camera.update();

        if (restartBounds.contains(touchPoint.x, touchPoint.y)) {
            context.getAudio().playSfx(SfxId.CLICK_OK);
            navigator.showPlayScreen();
            return;
        }

        if (resumeBounds.contains(touchPoint.x, touchPoint.y)) {
            context.getAudio().playSfx(SfxId.CLICK_OK);
            paused = false;
            return;
        }

        if (menuBounds.contains(touchPoint.x, touchPoint.y)) {
            context.getAudio().playSfx(SfxId.CLICK_OK);
            navigator.showMainMenu();
        }
    }

    private void drawPauseOverlay() {
        viewport.apply();
        OrthographicCamera camera = (OrthographicCamera) viewport.getCamera();
        // Temporarily reset zoom to 1.0 so the pause panel appears the same
        // visual size as it does on the menu / tutorial screens. Without this,
        // the 0.5 game zoom makes the panel fill twice as much screen area.
        float savedZoom = camera.zoom;
        camera.zoom = MENU_CAMERA_ZOOM;
        camera.update();

        float left = getCameraLeft(camera);
        float bottom = getCameraBottom(camera);
        shapes.setProjectionMatrix(camera.combined);
        overlayBatch.setProjectionMatrix(camera.combined);

        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(0f, 0f, 0f, 0.58f);
        shapes.rect(left, bottom, WORLD_WIDTH, WORLD_HEIGHT);

        float panelX = left + PANEL_X;
        float panelY = bottom + PANEL_Y;
        drawPausePanel(panelX, panelY);

        shapes.end();

        overlayBatch.begin();
        drawPauseButton(resumeBounds, resumeNormalTexture, resumeHoverTexture, resumePressedTexture);
        drawPauseButton(restartBounds, restartNormalTexture, restartHoverTexture, restartPressedTexture);
        drawPauseButton(menuBounds, menuNormalTexture, menuHoverTexture, menuPressedTexture);
        overlayBatch.end();

        // Restore game zoom.
        camera.zoom = savedZoom;
        camera.update();
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

    /**
     * One-frame input handler for the piano overlay. C/D/E/F/G/A/B play
     * their notes (and feed the puzzle); Q closes the overlay. E here would
     * collide with the global "interact" key, so we deliberately remap close
     * to Q while inside the piano. On a SOLVED result the storage key is
     * granted via {@link GameWorld#awardStorageKey()} and the overlay closes
     * automatically after the brief solved-flash window.
     */
    private void handlePianoInput() {
        if (Gdx.input.isKeyJustPressed(Input.Keys.Q)) {
            pianoOpen = false;
            openPiano = null;
            return;
        }
        if (openPiano == null) {
            // Defensive — should never happen now that openPiano is set on
            // open and only cleared on close, but if it did we'd just shut
            // the overlay rather than NPE.
            pianoOpen = false;
            return;
        }
        char note = 0;
        SfxId sfx = null;
        if (Gdx.input.isKeyJustPressed(Input.Keys.C)) { note = 'C'; sfx = SfxId.NOTE_C; }
        else if (Gdx.input.isKeyJustPressed(Input.Keys.D)) { note = 'D'; sfx = SfxId.NOTE_D; }
        else if (Gdx.input.isKeyJustPressed(Input.Keys.E)) { note = 'E'; sfx = SfxId.NOTE_E; }
        else if (Gdx.input.isKeyJustPressed(Input.Keys.F)) { note = 'F'; sfx = SfxId.NOTE_F; }
        else if (Gdx.input.isKeyJustPressed(Input.Keys.G)) { note = 'G'; sfx = SfxId.NOTE_G; }
        else if (Gdx.input.isKeyJustPressed(Input.Keys.A)) { note = 'A'; sfx = SfxId.NOTE_A; }
        else if (Gdx.input.isKeyJustPressed(Input.Keys.B)) { note = 'B'; sfx = SfxId.NOTE_B; }
        if (note == 0) return;
        context.getAudio().playSfx(sfx);
        PianoPuzzle.Result r = openPiano.pressNote(note);
        if (r == PianoPuzzle.Result.SOLVED) {
            world.awardStorageKey();
            hotbarLabelTimer = HOTBAR_LABEL_DURATION;
            context.getAudio().playSfx(SfxId.COLLECT_ITEMS);
        }
    }

    /**
     * Floating "E to Play" panel hovering over the piano. Mirrors the
     * newspaper / door prompt visuals so the prompt language stays
     * consistent for the player.
     */
    private void drawPianoPrompt(OrthographicCamera camera, PianoPuzzle piano) {
        float pulsePhase = (promptTimer / PROMPT_PULSE_PERIOD) * MathUtils.PI2;
        float pulse = 0.5f + 0.5f * (float) Math.sin(pulsePhase);
        float alpha = 0.65f + 0.35f * pulse;

        float bobPhase = (promptTimer / PROMPT_BOB_PERIOD) * MathUtils.PI2;
        float bob = (float) Math.sin(bobPhase) * PROMPT_BOB_AMPLITUDE;

        float centerX = piano.getX();
        float baseY = piano.getY() + PROMPT_VERTICAL_OFFSET + bob;
        float panelX = centerX - PROMPT_WIDTH / 2f;
        float panelY = baseY;

        // If the puzzle is already solved, tint the prompt green so the
        // player knows revisits are cosmetic-only and the key is already in
        // their inventory. Pre-solve uses the standard amber.
        boolean solved = piano.isSolved();
        Color borderTint = solved ? new Color(0.40f, 0.96f, 0.56f, 1f) : new Color(0.96f, 0.65f, 0.13f, 1f);
        Color haloTint = solved ? new Color(0.40f, 0.96f, 0.56f, 1f) : new Color(0.96f, 0.62f, 0.13f, 1f);
        Color labelColor = solved ? new Color(0.62f, 1f, 0.78f, 1f) : new Color(1f, 0.85f, 0.45f, 1f);

        shapes.setProjectionMatrix(camera.combined);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapes.begin(ShapeRenderer.ShapeType.Filled);

        float haloAlpha = 0.18f + 0.32f * pulse;
        shapes.setColor(haloTint.r, haloTint.g, haloTint.b, haloAlpha);
        float haloPad = 18f;
        shapes.rect(panelX - haloPad, panelY - haloPad,
            PROMPT_WIDTH + haloPad * 2f, PROMPT_HEIGHT + haloPad * 2f);
        shapes.setColor(0f, 0f, 0f, 0.55f * alpha);
        shapes.rect(panelX + 8f, panelY - 8f, PROMPT_WIDTH, PROMPT_HEIGHT);
        shapes.setColor(borderTint.r, borderTint.g, borderTint.b, alpha);
        shapes.rect(panelX, panelY, PROMPT_WIDTH, PROMPT_HEIGHT);
        shapes.setColor(0.07f, 0.075f, 0.09f, alpha);
        shapes.rect(panelX + 4f, panelY + 4f, PROMPT_WIDTH - 8f, PROMPT_HEIGHT - 8f);

        float arrowAlpha = 0.65f + 0.35f * pulse;
        shapes.setColor(borderTint.r, borderTint.g, borderTint.b, arrowAlpha);
        float arrowCx = panelX + PROMPT_WIDTH / 2f;
        float arrowTipY = panelY - 14f;
        shapes.triangle(arrowCx - 12f, panelY + 2f, arrowCx + 12f, panelY + 2f, arrowCx, arrowTipY);

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

        overlayBatch.setProjectionMatrix(camera.combined);
        overlayBatch.begin();
        Color keyTextColor = new Color(0.10f, 0.10f, 0.12f, alpha);
        labelColor.a = alpha;
        drawTextInRect("E", capX, capY, capW, capH, 1.2f, keyTextColor);
        float labelX = capX + capW + 16f;
        float labelW = panelX + PROMPT_WIDTH - labelX - 16f;
        drawTextInRect(solved ? "PLAYED" : "PLAY", labelX, panelY, labelW, PROMPT_HEIGHT, 1.0f, labelColor);
        overlayBatch.end();
    }

    /**
     * Full-screen piano keyboard. Seven white-key panes labeled C/D/E/F/G/A/B,
     * each with a flashing animation when its key is pressed (driven by
     * {@code stateTime}). The current input buffer renders as a row of dots
     * above the keyboard so the player sees their progress without
     * memorizing what they've typed.
     */
    private void drawPianoOverlay(OrthographicCamera camera, PianoPuzzle piano) {
        float visibleWidth = camera.viewportWidth * camera.zoom;
        float visibleHeight = camera.viewportHeight * camera.zoom;
        float left = getCameraLeft(camera);
        float bottom = getCameraBottom(camera);

        // Dim backdrop. Greener if the puzzle just accepted the sequence;
        // fades out via the puzzle's internal timer. There's no "wrong"
        // flash anymore — matching is substring, so a non-match just means
        // "keep playing" and shouldn't punish the player visually.
        float just = piano.isJustSolved() ? 0.6f * (piano.getJustSolvedTimer() / 1.6f) : 0f;
        shapes.setProjectionMatrix(camera.combined);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(0.08f, 0.08f + 0.20f * just, 0.10f, 0.85f);
        shapes.rect(left, bottom, visibleWidth, visibleHeight);
        // (No rolling-progress display — the player just plays freely;
        // the key drops the moment the solution sequence appears in their
        // input history. Hiding the dots removes the misleading
        // "5-window" implication and keeps focus on the keyboard.)

        // Keyboard. Seven white-key rectangles, equal width, gap between
        // them. Sized against the visible viewport so it fits at zoom 0.5.
        char[] notes = {'C','D','E','F','G','A','B'};
        int[] keycodes = {Input.Keys.C, Input.Keys.D, Input.Keys.E, Input.Keys.F, Input.Keys.G, Input.Keys.A, Input.Keys.B};
        int n = notes.length;
        float kbW = visibleWidth * 0.78f;
        float kbH = visibleHeight * 0.42f;
        float kbX = left + (visibleWidth - kbW) / 2f;
        float kbY = bottom + visibleHeight * 0.10f;
        float gap = 6f;
        float keyW = (kbW - gap * (n - 1)) / n;

        // Frame.
        shapes.setColor(0.05f, 0.05f, 0.07f, 1f);
        shapes.rect(kbX - 12f, kbY - 12f, kbW + 24f, kbH + 24f);
        shapes.setColor(0.20f, 0.13f, 0.07f, 1f);
        shapes.rect(kbX - 6f, kbY - 6f, kbW + 12f, kbH + 12f);

        for (int i = 0; i < n; i++) {
            float kx = kbX + i * (keyW + gap);
            boolean down = Gdx.input.isKeyPressed(keycodes[i]);
            // Bottom-up shading: subtle gradient via two stacked rects so a
            // pressed key reads as "depressed" (no top highlight).
            float r = down ? 0.78f : 0.96f;
            float g = down ? 0.78f : 0.96f;
            float b = down ? 0.82f : 0.99f;
            shapes.setColor(r, g, b, 1f);
            shapes.rect(kx, kbY, keyW, kbH);
            // Top sheen — only when not pressed.
            if (!down) {
                shapes.setColor(1f, 1f, 1f, 0.55f);
                shapes.rect(kx + 3f, kbY + kbH - 12f, keyW - 6f, 6f);
            }
            // Bottom shadow strip for grounding.
            shapes.setColor(0f, 0f, 0f, 0.35f);
            shapes.rect(kx, kbY, keyW, 5f);
        }
        shapes.end();
        Gdx.gl.glDisable(GL20.GL_BLEND);

        // Letter labels on each key, plus the title and close hint.
        overlayBatch.setProjectionMatrix(camera.combined);
        overlayBatch.begin();
        Color label = new Color(0.18f, 0.18f, 0.22f, 1f);
        Color labelDown = new Color(0.96f, 0.65f, 0.13f, 1f);
        for (int i = 0; i < n; i++) {
            float kx = kbX + i * (keyW + gap);
            boolean down = Gdx.input.isKeyPressed(keycodes[i]);
            drawTextInRect(String.valueOf(notes[i]),
                kx, kbY + 18f, keyW, 30f,
                1.4f, down ? labelDown : label);
        }
        Color title = new Color(1f, 0.92f, 0.62f, 0.95f);
        drawTextInRect(piano.isSolved() ? "Sequence accepted" : "Play the sequence",
            left, bottom + visibleHeight * 0.84f, visibleWidth, 26f,
            1.1f, title);
        Color hint = new Color(1f, 0.85f, 0.45f, 0.95f);
        drawTextInRect("Q to close",
            left, bottom + 18f, visibleWidth, 20f, 0.95f, hint);
        overlayBatch.end();
    }

    /**
     * One-frame input handler for the body-part question overlay. A/B/C/D
     * answer the active question; Q closes. Wrong answers fire a 30-second
     * time penalty (reusing the bite-flash overlay machinery so the
     * floating "-30s" text appears on the player). Already-solved puzzles
     * just flash green again and don't penalise.
     */
    private void handleBodyPartInput() {
        if (Gdx.input.isKeyJustPressed(Input.Keys.Q)) {
            bodyPartOpen = false;
            openBodyPart = null;
            return;
        }
        if (openBodyPart == null) {
            bodyPartOpen = false;
            return;
        }
        char letter = 0;
        if (Gdx.input.isKeyJustPressed(Input.Keys.A)) letter = 'A';
        else if (Gdx.input.isKeyJustPressed(Input.Keys.B)) letter = 'B';
        else if (Gdx.input.isKeyJustPressed(Input.Keys.C)) letter = 'C';
        else if (Gdx.input.isKeyJustPressed(Input.Keys.D)) letter = 'D';
        if (letter == 0) return;
        com.onelastheist.game.environment.BodyPartPuzzle.Result r = openBodyPart.answer(letter);
        switch (r) {
            case CORRECT:
                context.getAudio().playSfx(SfxId.COLLECT_ITEMS);
                hotbarLabelTimer = HOTBAR_LABEL_DURATION;
                break;
            case WRONG:
                // -30s penalty + bite-flash overlay (the "-30s" floating text
                // already wired into the renderer reads this state).
                world.applyTimePenalty(world.getBitePenaltySeconds());
                context.getAudio().playSfx(SfxId.DOG);
                break;
            case ALREADY_SOLVED:
            default:
                break;
        }
    }

    /**
     * Floating "E to Read" panel hovering over a body-part clue. Mirrors
     * {@link #drawNewspaperPrompt} but tints green when the puzzle is
     * already solved so the player can see at a glance which clues they
     * still need to answer.
     */
    private void drawBodyPartPrompt(OrthographicCamera camera,
                                    com.onelastheist.game.environment.BodyPartPuzzle puzzle) {
        float pulsePhase = (promptTimer / PROMPT_PULSE_PERIOD) * MathUtils.PI2;
        float pulse = 0.5f + 0.5f * (float) Math.sin(pulsePhase);
        float alpha = 0.65f + 0.35f * pulse;

        float bobPhase = (promptTimer / PROMPT_BOB_PERIOD) * MathUtils.PI2;
        float bob = (float) Math.sin(bobPhase) * PROMPT_BOB_AMPLITUDE;

        float centerX = puzzle.getX();
        float baseY = puzzle.getY() + PROMPT_VERTICAL_OFFSET + bob;
        float panelX = centerX - PROMPT_WIDTH / 2f;
        float panelY = baseY;

        boolean solved = puzzle.isSolved();
        Color borderTint = solved ? new Color(0.40f, 0.96f, 0.56f, 1f) : new Color(0.96f, 0.65f, 0.13f, 1f);
        Color haloTint = solved ? new Color(0.40f, 0.96f, 0.56f, 1f) : new Color(0.96f, 0.62f, 0.13f, 1f);
        Color labelColor = solved ? new Color(0.62f, 1f, 0.78f, 1f) : new Color(1f, 0.85f, 0.45f, 1f);

        shapes.setProjectionMatrix(camera.combined);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapes.begin(ShapeRenderer.ShapeType.Filled);

        float haloAlpha = 0.18f + 0.32f * pulse;
        shapes.setColor(haloTint.r, haloTint.g, haloTint.b, haloAlpha);
        float haloPad = 18f;
        shapes.rect(panelX - haloPad, panelY - haloPad,
            PROMPT_WIDTH + haloPad * 2f, PROMPT_HEIGHT + haloPad * 2f);
        shapes.setColor(0f, 0f, 0f, 0.55f * alpha);
        shapes.rect(panelX + 8f, panelY - 8f, PROMPT_WIDTH, PROMPT_HEIGHT);
        shapes.setColor(borderTint.r, borderTint.g, borderTint.b, alpha);
        shapes.rect(panelX, panelY, PROMPT_WIDTH, PROMPT_HEIGHT);
        shapes.setColor(0.07f, 0.075f, 0.09f, alpha);
        shapes.rect(panelX + 4f, panelY + 4f, PROMPT_WIDTH - 8f, PROMPT_HEIGHT - 8f);

        float arrowAlpha = 0.65f + 0.35f * pulse;
        shapes.setColor(borderTint.r, borderTint.g, borderTint.b, arrowAlpha);
        float arrowCx = panelX + PROMPT_WIDTH / 2f;
        float arrowTipY = panelY - 14f;
        shapes.triangle(arrowCx - 12f, panelY + 2f, arrowCx + 12f, panelY + 2f, arrowCx, arrowTipY);

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

        overlayBatch.setProjectionMatrix(camera.combined);
        overlayBatch.begin();
        Color keyTextColor = new Color(0.10f, 0.10f, 0.12f, alpha);
        labelColor.a = alpha;
        drawTextInRect("E", capX, capY, capW, capH, 1.2f, keyTextColor);
        float labelX = capX + capW + 16f;
        float labelW = panelX + PROMPT_WIDTH - labelX - 16f;
        drawTextInRect(solved ? "ANSWERED" : "EXAMINE", labelX, panelY, labelW, PROMPT_HEIGHT, 1.0f, labelColor);
        overlayBatch.end();
    }

    /**
     * Fullscreen body-part question overlay. Shows the question image
     * fit-clamped to the visible viewport, plus the recent-answer flash
     * (green for correct, red for wrong) and a Q hint. The image itself
     * carries the question text and the four answer letters; we only
     * draw the chrome around it and the answer feedback.
     */
    private void drawBodyPartOverlay(OrthographicCamera camera,
                                     com.onelastheist.game.environment.BodyPartPuzzle puzzle) {
        float visibleWidth = camera.viewportWidth * camera.zoom;
        float visibleHeight = camera.viewportHeight * camera.zoom;
        float left = getCameraLeft(camera);
        float bottom = getCameraBottom(camera);

        // Backdrop tinted by the most recent answer's flash. Green for
        // correct, red for wrong; both fade out via the puzzle's timer.
        float flash = puzzle.isFlashing() ? puzzle.getFlashStrength() : 0f;
        boolean correct = puzzle.isFlashCorrect();
        float r = 0.07f + (correct ? 0f : 0.40f * flash);
        float g = 0.07f + (correct ? 0.30f * flash : 0f);
        float b = 0.10f;
        shapes.setProjectionMatrix(camera.combined);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(r, g, b, 0.85f);
        shapes.rect(left, bottom, visibleWidth, visibleHeight);
        shapes.end();
        Gdx.gl.glDisable(GL20.GL_BLEND);

        int idx = puzzle.getQuestionIndex();
        Texture art = (questionTextures != null && idx >= 1 && idx < questionTextures.length)
            ? questionTextures[idx] : null;
        if (art != null) {
            float maxH = visibleHeight * 0.82f;
            float maxW = visibleWidth * 0.70f;
            float scale = Math.min(maxH / art.getHeight(), maxW / art.getWidth());
            float drawW = art.getWidth() * scale;
            float drawH = art.getHeight() * scale;
            float drawX = left + (visibleWidth - drawW) / 2f;
            float drawY = bottom + (visibleHeight - drawH) / 2f;
            overlayBatch.setProjectionMatrix(camera.combined);
            overlayBatch.begin();
            overlayBatch.setColor(Color.WHITE);
            overlayBatch.draw(art, drawX, drawY, drawW, drawH);
            overlayBatch.end();
        }

        // Hint strip at the bottom. Different copy for already-answered
        // puzzles so a player revisiting a solved clue doesn't think they
        // still need to act on it.
        overlayBatch.setProjectionMatrix(camera.combined);
        overlayBatch.begin();
        Color hint = new Color(1f, 0.85f, 0.45f, 0.95f);
        String text = puzzle.isSolved()
            ? "Already answered.  Q to close"
            : "Press A / B / C / D to answer.  Q to close";
        drawTextInRect(text, left, bottom + 18f, visibleWidth, 20f, 0.95f, hint);
        overlayBatch.end();
    }

    /**
     * Floating "E to Read" panel hovering over a newspaper. Same idiom as
     * {@link #drawDoorPrompt(OrthographicCamera, Door)} — pulse + bob driven
     * by {@code promptTimer}, keycap on the left, label on the right — minus
     * the locked-flash branch since newspapers can't be locked. Anchored to
     * the newspaper's world position rather than a door rect.
     */
    private void drawNewspaperPrompt(OrthographicCamera camera, Newspaper paper) {
        float pulsePhase = (promptTimer / PROMPT_PULSE_PERIOD) * MathUtils.PI2;
        float pulse = 0.5f + 0.5f * (float) Math.sin(pulsePhase);
        float alpha = 0.65f + 0.35f * pulse;

        float bobPhase = (promptTimer / PROMPT_BOB_PERIOD) * MathUtils.PI2;
        float bob = (float) Math.sin(bobPhase) * PROMPT_BOB_AMPLITUDE;

        float centerX = paper.getX();
        float baseY = paper.getY() + PROMPT_VERTICAL_OFFSET + bob;
        float panelX = centerX - PROMPT_WIDTH / 2f;
        float panelY = baseY;

        Color borderTint = new Color(0.96f, 0.65f, 0.13f, 1f);
        Color haloTint = new Color(0.96f, 0.62f, 0.13f, 1f);
        Color labelColor = new Color(1f, 0.85f, 0.45f, 1f);

        shapes.setProjectionMatrix(camera.combined);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapes.begin(ShapeRenderer.ShapeType.Filled);

        float haloAlpha = 0.18f + 0.32f * pulse;
        shapes.setColor(haloTint.r, haloTint.g, haloTint.b, haloAlpha);
        float haloPad = 18f;
        shapes.rect(panelX - haloPad, panelY - haloPad,
            PROMPT_WIDTH + haloPad * 2f, PROMPT_HEIGHT + haloPad * 2f);

        shapes.setColor(0f, 0f, 0f, 0.55f * alpha);
        shapes.rect(panelX + 8f, panelY - 8f, PROMPT_WIDTH, PROMPT_HEIGHT);

        shapes.setColor(borderTint.r, borderTint.g, borderTint.b, alpha);
        shapes.rect(panelX, panelY, PROMPT_WIDTH, PROMPT_HEIGHT);

        shapes.setColor(0.07f, 0.075f, 0.09f, alpha);
        shapes.rect(panelX + 4f, panelY + 4f, PROMPT_WIDTH - 8f, PROMPT_HEIGHT - 8f);

        float arrowAlpha = 0.65f + 0.35f * pulse;
        shapes.setColor(borderTint.r, borderTint.g, borderTint.b, arrowAlpha);
        float arrowCx = panelX + PROMPT_WIDTH / 2f;
        float arrowTipY = panelY - 14f;
        shapes.triangle(arrowCx - 12f, panelY + 2f, arrowCx + 12f, panelY + 2f, arrowCx, arrowTipY);

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

        overlayBatch.setProjectionMatrix(camera.combined);
        overlayBatch.begin();
        Color keyTextColor = new Color(0.10f, 0.10f, 0.12f, alpha);
        labelColor.a = alpha;
        drawTextInRect("E", capX, capY, capW, capH, 1.2f, keyTextColor);
        float labelX = capX + capW + 16f;
        float labelW = panelX + PROMPT_WIDTH - labelX - 16f;
        drawTextInRect("READ", labelX, panelY, labelW, PROMPT_HEIGHT, 1.0f, labelColor);
        overlayBatch.end();
    }

    /**
     * Full-screen broadside overlay shown while {@link #newspaperOpen} is true.
     * Dim the world behind, draw the page centered with letterbox bars, and
     * a small "Press E to close" footnote so the player isn't stuck.
     *
     * <p>Sizes everything against the camera's visible region
     * ({@code viewportWidth * zoom}) rather than {@link #WORLD_WIDTH} —
     * gameplay runs at a zoom of 0.5 so the visible width is 960 wu, half
     * the design size. Earlier I sized the page off WORLD_WIDTH and the
     * dim rect off WORLD_WIDTH too, which extended both well past the
     * actual viewport and made the page render anchored to a corner.
     */
    private void drawNewspaperOverlay(OrthographicCamera camera) {
        float visibleWidth = camera.viewportWidth * camera.zoom;
        float visibleHeight = camera.viewportHeight * camera.zoom;
        float left = getCameraLeft(camera);
        float bottom = getCameraBottom(camera);

        // Dim backdrop covering the visible viewport (not the design size).
        shapes.setProjectionMatrix(camera.combined);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(0f, 0f, 0f, 0.78f);
        shapes.rect(left, bottom, visibleWidth, visibleHeight);
        shapes.end();
        Gdx.gl.glDisable(GL20.GL_BLEND);

        // Centered page. Aspect ratio matches the source asset (3:4 for the
        // existing opened.png). Fit-clamp to 86% of visible height OR 55% of
        // visible width — whichever scales smaller wins, so the page never
        // bleeds past the screen edge regardless of source aspect.
        float pageW = newspaperOpenTexture.getWidth();
        float pageH = newspaperOpenTexture.getHeight();
        float maxH = visibleHeight * 0.86f;
        float maxW = visibleWidth * 0.55f;
        float scale = Math.min(maxH / pageH, maxW / pageW);
        float drawW = pageW * scale;
        float drawH = pageH * scale;
        float drawX = left + (visibleWidth - drawW) / 2f;
        float drawY = bottom + (visibleHeight - drawH) / 2f;

        overlayBatch.setProjectionMatrix(camera.combined);
        overlayBatch.begin();
        overlayBatch.setColor(Color.WHITE);
        overlayBatch.draw(newspaperOpenTexture, drawX, drawY, drawW, drawH);
        overlayBatch.end();

        // Small hint at the bottom — same prompt-yellow as the door prompt
        // so the player keeps the visual association. Anchored to the
        // visible viewport's bottom, not the design size.
        overlayBatch.setProjectionMatrix(camera.combined);
        overlayBatch.begin();
        Color hint = new Color(1f, 0.85f, 0.45f, 0.95f);
        drawTextInRect("Press E to close", left, bottom + 18f, visibleWidth, 20f, 1.0f, hint);
        overlayBatch.end();
    }

    /**
     * Bottom-of-screen hotbar. Fixed eight slots — the player's KEY and
     * CONSUMABLE items spill into them in inventory order. The selected slot
     * gets a gold highlight; the selected item's name floats in a small panel
     * above the bar with a tail pointing down at it. Scroll-wheel handling
     * lives in {@link #show()}'s InputAdapter.
     *
     * <p>Renders in world-space using the same projection as the rest of the
     * UI overlays (door prompt, pause panel) so we don't need a second camera
     * or viewport. Coordinates are anchored to the camera's bottom-center;
     * with the gameplay zoom of 0.5 the visible width is 960 wu, so the bar
     * comes out around 656 wu wide and stays roughly centred on screen.
     */
    private void drawHotbar(OrthographicCamera camera) {
        refreshHotbarItems();

        float visibleWidth = camera.viewportWidth * camera.zoom;
        float left = getCameraLeft(camera);
        float bottom = getCameraBottom(camera);

        float slotsRun = HOTBAR_SLOT_COUNT * HOTBAR_SLOT_SIZE + (HOTBAR_SLOT_COUNT - 1) * HOTBAR_SLOT_GAP;
        float barWidth = slotsRun + HOTBAR_PADDING * 2f;
        float barHeight = HOTBAR_SLOT_SIZE + HOTBAR_PADDING * 2f;
        float barX = left + (visibleWidth - barWidth) / 2f;
        float barY = bottom + HOTBAR_BOTTOM_MARGIN;

        shapes.setProjectionMatrix(camera.combined);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapes.begin(ShapeRenderer.ShapeType.Filled);

        // Drop shadow under the bar.
        shapes.setColor(0f, 0f, 0f, 0.55f);
        shapes.rect(barX + 6f, barY - 6f, barWidth, barHeight);
        // Outer gold border + dark inner.
        shapes.setColor(0.95f, 0.62f, 0.13f, 1f);
        shapes.rect(barX, barY, barWidth, barHeight);
        shapes.setColor(0.07f, 0.075f, 0.09f, 0.95f);
        shapes.rect(barX + 4f, barY + 4f, barWidth - 8f, barHeight - 8f);

        // Per-slot frames + selection highlight.
        for (int i = 0; i < HOTBAR_SLOT_COUNT; i++) {
            float slotX = barX + HOTBAR_PADDING + i * (HOTBAR_SLOT_SIZE + HOTBAR_SLOT_GAP);
            float slotY = barY + HOTBAR_PADDING;
            shapes.setColor(0.13f, 0.14f, 0.17f, 1f);
            shapes.rect(slotX, slotY, HOTBAR_SLOT_SIZE, HOTBAR_SLOT_SIZE);
            if (i == hotbarSelection) {
                // 3-wu border around the selected slot. Drawn as four thin
                // rects rather than a Line shape so the call stays inside the
                // Filled batch and we don't pay an extra begin/end.
                shapes.setColor(0.96f, 0.72f, 0.20f, 1f);
                float t = 3f;
                shapes.rect(slotX - t, slotY - t, HOTBAR_SLOT_SIZE + t * 2f, t);
                shapes.rect(slotX - t, slotY + HOTBAR_SLOT_SIZE, HOTBAR_SLOT_SIZE + t * 2f, t);
                shapes.rect(slotX - t, slotY, t, HOTBAR_SLOT_SIZE);
                shapes.rect(slotX + HOTBAR_SLOT_SIZE, slotY, t, HOTBAR_SLOT_SIZE);
            }
        }
        shapes.end();

        // Item icons.
        overlayBatch.setProjectionMatrix(camera.combined);
        overlayBatch.begin();
        overlayBatch.setColor(Color.WHITE);
        int filledCount = Math.min(HOTBAR_SLOT_COUNT, hotbarItems.size());
        for (int i = 0; i < filledCount; i++) {
            Texture texture = textureForItem(hotbarItems.get(i));
            if (texture == null) continue;
            float slotX = barX + HOTBAR_PADDING + i * (HOTBAR_SLOT_SIZE + HOTBAR_SLOT_GAP);
            float slotY = barY + HOTBAR_PADDING;
            float pad = (HOTBAR_SLOT_SIZE - HOTBAR_ITEM_DRAW) / 2f;
            overlayBatch.draw(texture, slotX + pad, slotY + pad, HOTBAR_ITEM_DRAW, HOTBAR_ITEM_DRAW);
        }
        overlayBatch.end();

        // Floating item-name label above the selected slot. Only visible for
        // a short window after the player changes the selection or picks up /
        // drops an item — long enough to read, short enough to stay out of
        // the way during play. Alpha fades over the last HOTBAR_LABEL_FADE
        // seconds of the window.
        if (hotbarLabelTimer > 0f && hotbarSelection < hotbarItems.size()) {
            float labelAlpha = MathUtils.clamp(hotbarLabelTimer / HOTBAR_LABEL_FADE, 0f, 1f);
            Item selected = hotbarItems.get(hotbarSelection);
            String label = selected.getName();
            float slotX = barX + HOTBAR_PADDING + hotbarSelection * (HOTBAR_SLOT_SIZE + HOTBAR_SLOT_GAP);
            float labelCenterX = slotX + HOTBAR_SLOT_SIZE / 2f;
            float labelY = barY + barHeight + HOTBAR_LABEL_OFFSET;

            tutorialFont.getData().setScale(0.7f);
            tutorialLayout.setText(tutorialFont, label);
            float labelWidth = tutorialLayout.width + 22f;
            float labelHeight = HOTBAR_LABEL_HEIGHT;
            float labelX = labelCenterX - labelWidth / 2f;

            shapes.begin(ShapeRenderer.ShapeType.Filled);
            shapes.setColor(0f, 0f, 0f, 0.55f * labelAlpha);
            shapes.rect(labelX + 3f, labelY - 3f, labelWidth, labelHeight);
            shapes.setColor(0.96f, 0.65f, 0.13f, labelAlpha);
            shapes.rect(labelX, labelY, labelWidth, labelHeight);
            shapes.setColor(0.07f, 0.075f, 0.09f, 0.95f * labelAlpha);
            shapes.rect(labelX + 2f, labelY + 2f, labelWidth - 4f, labelHeight - 4f);
            // Triangle tail pointing at the selected slot.
            shapes.setColor(0.96f, 0.65f, 0.13f, labelAlpha);
            shapes.triangle(labelCenterX - 6f, labelY + 2f,
                labelCenterX + 6f, labelY + 2f,
                labelCenterX, labelY - 7f);
            shapes.end();

            overlayBatch.begin();
            drawTextInRect(label, labelX, labelY, labelWidth, labelHeight,
                0.7f, new Color(1f, 0.85f, 0.45f, labelAlpha));
            overlayBatch.end();
        }
    }

    /**
     * Refresh the hotbar's view of the player inventory. Only KEY and
     * CONSUMABLE (= meat) items are shown — money/weapons/evidence belong on
     * a stats HUD, not a usable-item bar.
     */
    private void refreshHotbarItems() {
        hotbarItems.clear();
        for (Item item : world.getPlayer().getInventory().getItems()) {
            ItemType type = item.getType();
            if (type == ItemType.KEY || type == ItemType.CONSUMABLE) {
                hotbarItems.add(item);
            }
        }
    }

    private Texture textureForItem(Item item) {
        switch (item.getType()) {
            case KEY:
                return hotbarKeyTexture;
            case CONSUMABLE:
                return hotbarMeatTexture;
            default:
                return null;
        }
    }

    /**
     * Top-of-screen HUD: time-remaining badge in the top-left, money-total
     * badge in the top-right. Both render at zoom 1.0 (same pattern as
     * {@link #drawPauseOverlay}) so badge size stays constant regardless of
     * camera zoom. Time formats as MM:SS; money is the running total from
     * {@link com.onelastheist.game.world.ObjectiveTracker#getCollectedMoney}.
     */
    private void drawHud(OrthographicCamera camera) {
        float savedZoom = camera.zoom;
        camera.zoom = MENU_CAMERA_ZOOM;
        camera.update();

        float left = getCameraLeft(camera);
        float bottom = getCameraBottom(camera);
        float topY = bottom + WORLD_HEIGHT - HUD_TOP_PADDING - HUD_BADGE_HEIGHT;

        overlayBatch.setProjectionMatrix(camera.combined);
        overlayBatch.begin();
        overlayBatch.setColor(Color.WHITE);
        // Clock badge — top-left.
        float clockX = left + HUD_SIDE_PADDING;
        overlayBatch.draw(hudClockTexture, clockX, topY, HUD_BADGE_WIDTH, HUD_BADGE_HEIGHT);
        // Money badge — top-right. Mirror across the screen.
        float moneyX = left + WORLD_WIDTH - HUD_SIDE_PADDING - HUD_BADGE_WIDTH;
        overlayBatch.draw(hudCoinTexture, moneyX, topY, HUD_BADGE_WIDTH, HUD_BADGE_HEIGHT);

        // Text.
        Color valueColor = new Color(0.10f, 0.10f, 0.12f, 1f);
        float textOriginX = HUD_BADGE_WIDTH * HUD_TEXT_LEFT_FRACTION;
        float textWidth = HUD_BADGE_WIDTH - textOriginX - 12f;
        drawTextInRect(formatTime(world.getClock().getRemainingSeconds()),
            clockX + textOriginX, topY, textWidth, HUD_BADGE_HEIGHT, HUD_TEXT_SCALE, valueColor);
        drawTextInRect(Integer.toString(world.getObjectives().getCollectedMoney()),
            moneyX + textOriginX, topY, textWidth, HUD_BADGE_HEIGHT, HUD_TEXT_SCALE, valueColor);
        overlayBatch.end();

        camera.zoom = savedZoom;
        camera.update();
    }

    /**
     * White flicker over the player + a "-30 seconds" floating notification
     * during the post-bite window. Both fade together as
     * {@link GameWorld#getBiteFlashStrength()} drops to zero. Rendered in
     * world-space (no zoom override) so the flash sticks to the player as
     * they move.
     */
    private void drawBiteFlash(OrthographicCamera camera) {
        float strength = world.getBiteFlashStrength();
        if (strength <= 0f) return;

        float playerCenterX = world.getPlayer().getX() + ACTOR_CENTER_OFFSET;
        float playerBottomY = world.getPlayer().getY() + 28f;
        // Flicker: pulse the alpha at 18 Hz so it reads as a strobe rather
        // than a smooth fade. Multiply by the overall fade for a tail-out.
        float flicker = (float) Math.sin(promptTimer * 38f);
        float flickerAlpha = strength * (0.55f + 0.35f * flicker);
        if (flickerAlpha < 0f) flickerAlpha = 0f;

        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapes.setProjectionMatrix(camera.combined);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(1f, 1f, 1f, flickerAlpha);
        // Tight ellipse over the player sprite — wide enough to cover the
        // player but not so wide it bleeds onto the dog.
        shapes.ellipse(playerCenterX - 60f, playerBottomY, 120f, 130f);
        shapes.end();

        // "-30 seconds" floating text above the player. Drawn in world-space
        // so it bobs with the camera; the strength is the alpha so it fades
        // out cleanly with the flash.
        overlayBatch.setProjectionMatrix(camera.combined);
        overlayBatch.begin();
        String label = "-" + (int) world.getBitePenaltySeconds() + "s";
        Color textColor = new Color(1f, 0.32f, 0.32f, strength);
        // Float text upward over the lifespan of the flash so it looks like
        // damage popping off the player. Convert remaining strength back to
        // elapsed time: bigger lift the longer the effect has been on screen.
        float lift = (1f - strength) * 60f;
        float textY = world.getPlayer().getY() + 240f + lift;
        tutorialFont.getData().setScale(1.4f);
        tutorialLayout.setText(tutorialFont, label);
        float textX = playerCenterX - tutorialLayout.width / 2f;
        tutorialFont.setColor(textColor);
        tutorialFont.draw(overlayBatch, label, textX, textY);
        overlayBatch.end();
    }

    private static String formatTime(float seconds) {
        int total = (int) Math.ceil(seconds);
        if (total < 0) total = 0;
        int minutes = total / 60;
        int secs = total % 60;
        return String.format("%d:%02d", minutes, secs);
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
        drawObjectivesPanelShape();
        drawTutorialBannerShape(true);
        drawTutorialBannerShape(false);
        shapes.end();

        renderer.renderPlayerOnly(delta, camera);

        overlayBatch.setProjectionMatrix(camera.combined);
        overlayBatch.begin();
        drawTutorialHeading();
        for (int i = 0; i < CARD_COUNT; i++) drawCardText(i);
        drawObjectivesPanelText();
        drawTutorialBannerText(true);
        drawTutorialBannerText(false);
        overlayBatch.end();

        if (phase == PlayPhase.FADING_TO_GAME) {
            drawFadeOverlay(camera);
        }
    }

    private void drawTutorialBackdrop() {
        shapes.setColor(0.04f, 0.045f, 0.055f, 1f);
        shapes.rect(0f, 0f, WORLD_WIDTH, WORLD_HEIGHT);

        // Single thin gold rule under the heading. The bottom rule used to
        // live at y=410 but now lands inside the OBJECTIVES panel, so it's
        // gone — the panel itself supplies the lower visual anchor.
        shapes.setColor(0.96f, 0.62f, 0.13f, 0.32f);
        shapes.rect(0f, WORLD_HEIGHT - 160f, WORLD_WIDTH, 2f);
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
        drawTextInRect(cardDescription(index), x + 16f, y + 14f, CARD_WIDTH - 32f, CARD_FOOTER_HEIGHT - 28f,
            CARD_DESCRIPTION_SCALE, descColor);

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
        // Legacy shim — banners are now drawn by drawTutorialBannerShape.
    }

    private void drawContinueHintText() {
        // Legacy shim — see drawTutorialBannerText.
    }

    // ---- Operation manual: symmetric BACK / WALK RIGHT banners + OBJECTIVES panel.
    // Both banners share width/height/Y so the layout reads as balanced about
    // the center axis of the screen.

    private static final float TUTORIAL_BANNER_WIDTH = 540f;
    private static final float TUTORIAL_BANNER_HEIGHT = 80f;
    private static final float TUTORIAL_BANNER_Y = 50f;
    private static final float TUTORIAL_BANNER_SIDE_PADDING = 90f;

    private static final float OBJECTIVES_PANEL_WIDTH = 1280f;
    private static final float OBJECTIVES_PANEL_HEIGHT = 340f;
    private static final float OBJECTIVES_PANEL_X =
        (WORLD_WIDTH - OBJECTIVES_PANEL_WIDTH) / 2f;
    private static final float OBJECTIVES_PANEL_Y = 175f;

    /**
     * Draw one of the two bottom banners. {@code leftSide=true} is the
     * BACK TO MENU banner on the left, {@code leftSide=false} is the
     * WALK RIGHT TO BEGIN banner on the right. Same shape data so they
     * read as a matched pair flanking the OBJECTIVES panel.
     */
    private void drawTutorialBannerShape(boolean leftSide) {
        float bannerX = leftSide
            ? TUTORIAL_BANNER_SIDE_PADDING
            : WORLD_WIDTH - TUTORIAL_BANNER_WIDTH - TUTORIAL_BANNER_SIDE_PADDING;
        float bannerY = TUTORIAL_BANNER_Y;

        shapes.setColor(0f, 0f, 0f, 0.55f);
        shapes.rect(bannerX + 8f, bannerY - 8f, TUTORIAL_BANNER_WIDTH, TUTORIAL_BANNER_HEIGHT);
        shapes.setColor(0.95f, 0.62f, 0.13f, 1f);
        shapes.rect(bannerX, bannerY, TUTORIAL_BANNER_WIDTH, TUTORIAL_BANNER_HEIGHT);
        shapes.setColor(0.07f, 0.075f, 0.09f, 1f);
        shapes.rect(bannerX + 4f, bannerY + 4f,
            TUTORIAL_BANNER_WIDTH - 8f, TUTORIAL_BANNER_HEIGHT - 8f);

        // Mirrored chevrons: the right banner points right (forward), the
        // left banner points left (back to menu).
        shapes.setColor(0.96f, 0.72f, 0.28f, 1f);
        float chevY = bannerY + TUTORIAL_BANNER_HEIGHT / 2f;
        if (leftSide) {
            for (int i = 0; i < 2; i++) {
                float ox = bannerX + 76f - i * 22f;
                shapes.triangle(ox + 16f, chevY + 16f, ox + 16f, chevY - 16f, ox - 4f, chevY);
            }
        } else {
            for (int i = 0; i < 2; i++) {
                float ox = bannerX + TUTORIAL_BANNER_WIDTH - 76f + i * 22f;
                shapes.triangle(ox - 16f, chevY + 16f, ox - 16f, chevY - 16f, ox + 4f, chevY);
            }
        }
    }

    private void drawTutorialBannerText(boolean leftSide) {
        Color gold = new Color(1f, 0.8f, 0.3f, 1f);
        float bannerX = leftSide
            ? TUTORIAL_BANNER_SIDE_PADDING
            : WORLD_WIDTH - TUTORIAL_BANNER_WIDTH - TUTORIAL_BANNER_SIDE_PADDING;
        float bannerY = TUTORIAL_BANNER_Y;
        // Text sits between the chevrons. Reserve ~110wu on the chevron
        // side so the text never collides with the arrows.
        float textX = leftSide ? bannerX + 110f : bannerX;
        float textWidth = TUTORIAL_BANNER_WIDTH - 110f;
        String label = leftSide ? "BACK TO MENU" : "WALK RIGHT TO BEGIN";
        drawTextInRect(label, textX, bannerY, textWidth, TUTORIAL_BANNER_HEIGHT, 1.15f, gold);
    }

    /**
     * Heist-style ledger panel listing the four objectives. Same chrome as
     * the credits / pause panels (gold border, brown plank, deep navy
     * interior) so the manual page reads as one composition.
     */
    private void drawObjectivesPanelShape() {
        float x = OBJECTIVES_PANEL_X;
        float y = OBJECTIVES_PANEL_Y;
        float w = OBJECTIVES_PANEL_WIDTH;
        float h = OBJECTIVES_PANEL_HEIGHT;

        shapes.setColor(0f, 0f, 0f, 0.62f);
        shapes.rect(x + 14f, y - 14f, w, h);
        shapes.setColor(0.96f, 0.65f, 0.11f, 1f);
        shapes.rect(x, y, w, h);
        shapes.setColor(0.28f, 0.14f, 0.07f, 1f);
        shapes.rect(x + 12f, y + 12f, w - 24f, h - 24f);
        shapes.setColor(0.03f, 0.08f, 0.12f, 0.97f);
        shapes.rect(x + 28f, y + 28f, w - 56f, h - 56f);

        // Header strip and gold dividers above each objective row for a
        // ledger feel.
        shapes.setColor(0.95f, 0.62f, 0.13f, 1f);
        shapes.rect(x + 28f, y + h - 80f, w - 56f, 4f);
        shapes.rect(x + 28f, y + 36f, w - 56f, 4f);

        // Coin pip at the inner corners of the ledger keeps the heist motif.
        float coinPad = 50f;
        float[][] coins = {
            {x + coinPad, y + h - coinPad},
            {x + w - coinPad, y + h - coinPad},
            {x + coinPad, y + coinPad},
            {x + w - coinPad, y + coinPad}
        };
        for (float[] c : coins) {
            shapes.setColor(0.66f, 0.43f, 0.02f, 1f);
            shapes.circle(c[0] + 4f, c[1] - 4f, 18f);
            shapes.setColor(1f, 0.81f, 0.02f, 1f);
            shapes.circle(c[0], c[1], 18f);
            shapes.setColor(1f, 0.94f, 0.27f, 1f);
            shapes.circle(c[0] - 5f, c[1] + 5f, 6f);
        }

        // Bullet glyphs on the left of each row. ShapeRenderer primitives
        // are used in place of emoji because the BitmapFont default doesn't
        // include them; the glyph hints at the objective without taking
        // extra vertical space.
        float[] rowYs = objectiveRowCenters();
        float bulletX = x + 86f;
        for (int i = 0; i < rowYs.length; i++) {
            float cy = rowYs[i];
            switch (i) {
                case 0: drawBulletCoin(bulletX, cy); break;       // money
                case 1: drawBulletHouse(bulletX, cy); break;      // rooms
                case 2: drawBulletPaw(bulletX, cy); break;        // dog
                case 3: drawBulletDiamond(bulletX, cy); break;    // hidden valuables
                default: drawBulletDot(bulletX, cy); break;
            }
        }
    }

    private void drawObjectivesPanelText() {
        Color titleGold = new Color(1f, 0.86f, 0.24f, 1f);
        Color body = new Color(0.92f, 0.95f, 0.88f, 1f);

        float x = OBJECTIVES_PANEL_X;
        float y = OBJECTIVES_PANEL_Y;
        float w = OBJECTIVES_PANEL_WIDTH;
        float h = OBJECTIVES_PANEL_HEIGHT;

        // Header.
        drawTextInRect("THIEF'S OBJECTIVE",
            x + 28f, y + h - 76f, w - 56f, 40f, 1.7f, titleGold);

        String[] lines = {
            "Collect 360$ before time expires.",
            "Investigate rooms and interact with objects.",
            "Avoid being caught by the guard dog.",
            "Some valuables are hidden in unexpected places."
        };
        float[] rowYs = objectiveRowCenters();
        float rowHeight = (rowYs[0] - rowYs[1]) - 4f;
        for (int i = 0; i < lines.length; i++) {
            // Indent past the bullet glyph so all four lines line up.
            drawTextInRect(lines[i],
                x + 130f, rowYs[i] - rowHeight / 2f,
                w - 160f, rowHeight, 1.15f, body);
        }
    }

    /**
     * Vertical center of each objective row. Row strip lives between the
     * two gold dividers; we space four rows evenly inside it so the panel
     * reads as a clean ledger no matter how tall the panel becomes.
     */
    private float[] objectiveRowCenters() {
        float y = OBJECTIVES_PANEL_Y;
        float h = OBJECTIVES_PANEL_HEIGHT;
        float topDivider = y + h - 80f;
        float bottomDivider = y + 36f;
        float rowSpan = topDivider - bottomDivider;
        float[] centers = new float[4];
        for (int i = 0; i < 4; i++) {
            // Bias the centers slightly upward so descenders don't kiss the
            // divider below — looks tidier than a pure even split.
            centers[i] = topDivider - rowSpan * (i + 0.5f) / 4f;
        }
        return centers;
    }

    // -- Tiny bullet glyphs (ShapeRenderer; BitmapFont doesn't ship emoji).

    private void drawBulletCoin(float cx, float cy) {
        shapes.setColor(0.66f, 0.43f, 0.02f, 1f);
        shapes.circle(cx + 2f, cy - 2f, 14f);
        shapes.setColor(1f, 0.81f, 0.02f, 1f);
        shapes.circle(cx, cy, 14f);
        shapes.setColor(1f, 0.94f, 0.27f, 1f);
        shapes.circle(cx - 4f, cy + 4f, 4f);
    }

    private void drawBulletHouse(float cx, float cy) {
        // Square body + triangle roof, golden silhouette.
        shapes.setColor(0.96f, 0.65f, 0.11f, 1f);
        shapes.rect(cx - 12f, cy - 12f, 24f, 18f);
        shapes.triangle(cx - 14f, cy + 6f, cx + 14f, cy + 6f, cx, cy + 18f);
        shapes.setColor(0.07f, 0.075f, 0.09f, 1f);
        shapes.rect(cx - 4f, cy - 12f, 8f, 12f);
    }

    private void drawBulletPaw(float cx, float cy) {
        // Pad + four toes — readable as "dog" at small scale.
        shapes.setColor(0.96f, 0.65f, 0.11f, 1f);
        shapes.circle(cx, cy - 4f, 9f);
        shapes.circle(cx - 10f, cy + 4f, 4f);
        shapes.circle(cx - 4f, cy + 9f, 4f);
        shapes.circle(cx + 4f, cy + 9f, 4f);
        shapes.circle(cx + 10f, cy + 4f, 4f);
    }

    private void drawBulletDiamond(float cx, float cy) {
        shapes.setColor(0.4f, 0.85f, 1.0f, 1f);
        // Diamond outline as four triangles around center.
        shapes.triangle(cx, cy + 14f, cx - 12f, cy, cx + 12f, cy);
        shapes.triangle(cx, cy - 14f, cx - 12f, cy, cx + 12f, cy);
        shapes.setColor(0.85f, 0.96f, 1.0f, 1f);
        shapes.triangle(cx - 4f, cy + 8f, cx + 4f, cy + 8f, cx, cy + 14f);
    }

    private void drawBulletDot(float cx, float cy) {
        shapes.setColor(0.96f, 0.65f, 0.11f, 1f);
        shapes.circle(cx, cy, 6f);
    }

    private String cardTitle(int index) {
        switch (index) {
            case 0: return "MOVE";
            case 1: return "STEALTH";
            case 2: return "ACTIONS";
            case 3: return "SCROLL";
            case 4: return "PAUSE";
            default: return "";
        }
    }

    private String cardDescription(int index) {
        switch (index) {
            case 0: return "Walk in 8 directions";
            case 1: return "Crouch to walk quietly";
            case 2: return "Pick up · Interact · Drop";
            case 3: return "Cycle inventory items";
            case 4: return "Open the menu";
            default: return "";
        }
    }

    private String[][] cardKeys(int index) {
        switch (index) {
            case 0: return new String[][] { {"", "W", ""}, {"A", "S", "D"} };
            case 1: return new String[][] { {"CTRL"} };
            case 2: return new String[][] { {"F", "E", "G"} };
            case 3: return new String[][] { {"WHEEL"} };
            case 4: return new String[][] { {"ESC"} };
            default: return new String[0][];
        }
    }

    private float keyWidth(String key) {
        if (key.isEmpty()) return KEY_SIZE;
        if (key.length() >= 5) return 150f;
        if (key.length() >= 4) return 130f;
        if (key.length() >= 2) return 96f;
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
        // Anchor the fade rect to the visible viewport, not (0,0)-(WORLD).
        // The gameplay camera runs at zoom 0.5 and is centered on the
        // player, so a world-space rect at the design size only covers
        // ~half the screen — the visual we saw during FADING_TO_END.
        // getCameraLeft/Bottom give us the bottom-left of what's actually
        // on screen; viewportWidth * zoom is the visible extent.
        float visibleWidth = camera.viewportWidth * camera.zoom;
        float visibleHeight = camera.viewportHeight * camera.zoom;
        shapes.rect(getCameraLeft(camera), getCameraBottom(camera), visibleWidth, visibleHeight);
        shapes.end();
    }

    private void updateOverlayLayout() {
        OrthographicCamera camera = (OrthographicCamera) viewport.getCamera();
        // Button bounds use zoom 1.0 positioning to match drawPauseOverlay,
        // which temporarily resets zoom before rendering the panel.
        float overlayLeft = camera.position.x - camera.viewportWidth * MENU_CAMERA_ZOOM / 2f;
        float overlayBottom = camera.position.y - camera.viewportHeight * MENU_CAMERA_ZOOM / 2f;
        float buttonX = overlayLeft + PANEL_X + (PANEL_WIDTH - BUTTON_WIDTH) / 2f;
        float menuY = overlayBottom + PANEL_Y + 50f;
        menuBounds.set(buttonX, menuY, BUTTON_WIDTH, BUTTON_HEIGHT);
        restartBounds.set(buttonX, menuY + BUTTON_HEIGHT + BUTTON_GAP, BUTTON_WIDTH, BUTTON_HEIGHT);
        resumeBounds.set(buttonX, restartBounds.y + BUTTON_HEIGHT + BUTTON_GAP, BUTTON_WIDTH, BUTTON_HEIGHT);

        // Operation manual BACK TO MENU banner — matches the geometry in
        // drawTutorialBannerShape(true). Tutorial camera is at zoom 1.0
        // centered on (WORLD_WIDTH/2, WORLD_HEIGHT/2), so world-space and
        // viewport-space coords coincide here.
        tutorialBackBounds.set(
            TUTORIAL_BANNER_SIDE_PADDING,
            TUTORIAL_BANNER_Y,
            TUTORIAL_BANNER_WIDTH,
            TUTORIAL_BANNER_HEIGHT
        );
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

    /**
     * Per-frame audio mixer driver. Translates current world state into
     * one-shot SFX (edge-triggered against the previous frame's state) and
     * looped SFX (level-triggered every frame). Must be called every frame
     * regardless of pause / phase so loops drop cleanly when gameplay halts.
     *
     * <ul>
     *   <li>Music: gameplay theme starts on first GAME-phase frame, swaps
     *       back to MENU-style silence on pause (we just stop loops; the
     *       music is whatever the menu started).</li>
     *   <li>Player footsteps: looped while {@link com.onelastheist.game.entity.player.Player#isMakingNoise()}.</li>
     *   <li>Homeowner footsteps: looped while his brain is APPROACHING.</li>
     *   <li>CarArrive: fired once when the homeowner brain comes online.</li>
     *   <li>Dog SFX: fired on the bite-flash 0→nonzero transition.</li>
     * </ul>
     */
    private void updateAudio() {
        com.onelastheist.game.audio.AudioService audio = context.getAudio();

        // Music: switch to the gameplay theme the first time we render the
        // GAME phase, regardless of pause state. Pausing keeps the music
        // looping under the pause overlay; the menu theme would have to be
        // re-started by the menu screen on its own.
        if (phase == PlayPhase.GAME && !gameplayMusicStarted) {
            audio.playMusic(MusicId.IN_HOUSE);
            gameplayMusicStarted = true;
        }

        // While paused, suppress all footstep loops so silence under the
        // pause overlay isn't undermined by a perpetually-walking thief.
        if (paused || phase != PlayPhase.GAME) {
            audio.setLoop(SfxId.FOOTSTEPS_THIEF, false);
            audio.setLoop(SfxId.FOOTSTEPS_HOMEOWNER, false);
            return;
        }

        // Thief footsteps: loop whenever the player would broadcast noise
        // to AI hearing. Crouching/standing-still both turn this off
        // automatically since isMakingNoise() returns false in those cases.
        // Suppressed during caught/reading/piano so a stuck "moving" flag
        // (controller hasn't ticked since the lock started) doesn't keep
        // the footstep loop running during overlays.
        boolean playerLoud = !world.getPlayer().isCaught()
            && !newspaperOpen
            && !pianoOpen
            && !bodyPartOpen
            && world.getPlayer().isMakingNoise();
        audio.setLoop(SfxId.FOOTSTEPS_THIEF, playerLoud);

        // Homeowner audio. Brain may be null until the clock crosses the
        // arrival threshold. CarArrive fires once on the null→non-null
        // transition. Footsteps loop while the brain is APPROACHING (he's
        // walking up the road); they stop when he steps inside and HUNTING
        // takes over — interior steps are a deliberate omission so the
        // player can't audio-cue his exact position once he's hunting.
        HomeOwnerBrain brain = world.getHomeOwnerBrain();
        boolean homeOwnerActive = brain != null;
        if (homeOwnerActive && !prevHomeOwnerActive) {
            audio.playSfx(SfxId.CAR_ARRIVE);
        }
        prevHomeOwnerActive = homeOwnerActive;
        boolean approaching = homeOwnerActive
            && brain.getPhase() == HomeOwnerBrain.Phase.APPROACHING;
        audio.setLoop(SfxId.FOOTSTEPS_HOMEOWNER, approaching);

        // Dog SFX: bark on detect (state enters INVESTIGATING_NOISE) AND on
        // the bite itself. The bite path uses the bite-flash 0→active edge
        // so we get the audio cue at the exact frame the world applies the
        // penalty, even if the dog state has already moved past
        // INVESTIGATING_NOISE by then. Detection uses the dog's NpcState
        // edge — only fires when the state was something else last frame
        // and is INVESTIGATING_NOISE this frame, so re-acquiring the player
        // mid-chase doesn't spam the bark.
        NpcState dogState = world.getDog().getState();
        if (world.hasActiveDog()
            && dogState == NpcState.INVESTIGATING_NOISE
            && prevDogState != NpcState.INVESTIGATING_NOISE) {
            audio.playSfx(SfxId.DOG);
        }
        prevDogState = dogState;

        boolean biteFlashActive = world.isBiteFlashActive();
        if (biteFlashActive && !prevBiteFlashActive) {
            audio.playSfx(SfxId.DOG);
        }
        prevBiteFlashActive = biteFlashActive;
    }

    private void updateCamera() {
        OrthographicCamera camera = (OrthographicCamera) viewport.getCamera();
        // Pre-game phases (tutorial, fade-in) use the menu camera framing.
        // FADING_TO_END deliberately keeps the gameplay camera so the
        // dim-to-black covers the player's last known position rather
        // than snapping to a recentered/zoomed-out view.
        if (phase != PlayPhase.GAME && phase != PlayPhase.FADING_TO_END) {
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
        GAME,
        /**
         * Played the win/lose triggered, currently fading the world to
         * black before navigating to the ending screen. Movement and
         * world ticks are frozen during this phase.
         */
        FADING_TO_END
    }
}
