package com.onelastheist.game.render;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.maps.MapLayer;
import com.badlogic.gdx.maps.tiled.renderers.OrthogonalTiledMapRenderer;
import com.badlogic.gdx.utils.Disposable;
import com.onelastheist.game.entity.base.MovableEntity;
import com.onelastheist.game.entity.npc.Dog;
import com.onelastheist.game.entity.player.Player;
import com.onelastheist.game.world.GameWorld;
import com.onelastheist.game.world.WorldFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Renders {@link GameWorld} state every frame. Owns no gameplay logic — it only
 * reads the world's positions/states and draws them. Two-pass tile rendering
 * separates ground/object layers (drawn before the actors) from the
 * {@code Overhead_Foreground} layer (drawn after the actors) so canopies and
 * roof tops occlude characters that walk under them.
 *
 * <p>Animation handling: every walk/idle sheet is split into 8 directional rows
 * of {@link #FRAME_SIZE}px frames; {@link #createAnimations} converts each row
 * into a LibGDX {@link Animation}. The crouch sheet starts its useful frames
 * at column 3 (frames 0-2 are skipped via {@link #CROUCH_WALK_STARTS}).
 */
public class WorldRenderer implements Disposable {
    /** Tiled layer name that should be drawn after actors so it occludes them. */
    private static final String OVERHEAD_LAYER_NAME = "Overhead_Foreground";
    private static final int FRAME_SIZE = 48;
    private static final int DIRECTION_COUNT = 8;
    private static final int FRAME_COUNT = 5;
    /** World-space draw size for human-sized actors (player, homeowner). */
    private static final float DRAW_SIZE = 144f;
    /** Slightly smaller than humans so the dog reads visually as a pet. */
    private static final float DOG_DRAW_SIZE = 120f;
    private static final float FRAME_DURATION = 0.14f;
    private static final float CROUCH_IDLE_FRAME_DURATION = 0.22f;
    private static final float DOG_SLEEP_FRAME_DURATION = 0.32f;
    /** Column index where each row's crouch-walk loop actually starts. */
    private static final int[] CROUCH_WALK_STARTS = {3, 3, 3, 3, 3, 3, 3, 3};
    private static final int[] CROUCH_IDLE_STARTS = {3, 3, 3, 3, 3, 3, 3, 3};

    private final GameWorld world;
    private final SpriteBatch batch = new SpriteBatch();
    private final OrthogonalTiledMapRenderer mapRenderer;
    /** All tile layers except the overhead one — drawn before actors. */
    private final int[] groundLayerIndices;
    /** Just the overhead layer — drawn after actors so trees/roofs occlude them. */
    private final int[] overheadLayerIndices;
    private final Texture playerIdleTexture = loadPixelTexture("characters/player/player_idle.png");
    private final Texture playerWalkTexture = loadPixelTexture("characters/player/player_walk.png");
    private final Texture playerCrouchTexture = loadPixelTexture("characters/player/player_crouch.png");
    private final Texture ownerIdleTexture = loadPixelTexture("characters/enemies/neighbour/enemies_idle.png");
    private final Texture ownerWalkTexture = loadPixelTexture("characters/enemies/neighbour/enemies_walk.png");
    private final Texture dogWalkTexture = loadPixelTexture("characters/enemies/dog/dog_walk.png");
    private final Texture dogSleepTexture = loadPixelTexture("characters/enemies/dog/dog_sleep.png");
    private final List<Animation<TextureRegion>> playerIdle = createAnimations(playerIdleTexture);
    private final List<Animation<TextureRegion>> playerWalk = createAnimations(playerWalkTexture);
    private final List<Animation<TextureRegion>> playerCrouchWalk = createAnimations(playerCrouchTexture, FRAME_DURATION, CROUCH_WALK_STARTS, FRAME_COUNT);
    private final List<Animation<TextureRegion>> playerCrouchIdle = createAnimations(playerCrouchTexture, CROUCH_IDLE_FRAME_DURATION, CROUCH_IDLE_STARTS, FRAME_COUNT);
    private final List<Animation<TextureRegion>> ownerIdle = createAnimations(ownerIdleTexture);
    private final List<Animation<TextureRegion>> ownerWalk = createAnimations(ownerWalkTexture);
    private final List<Animation<TextureRegion>> dogWalk = createAnimations(dogWalkTexture);
    private final Animation<TextureRegion> dogSleep = createSingleRowAnimation(dogSleepTexture, DOG_SLEEP_FRAME_DURATION);
    private float stateTime;

    public WorldRenderer(GameWorld world) {
        this.world = world;
        this.mapRenderer = new OrthogonalTiledMapRenderer(world.getTiledMap(), WorldFactory.MAP_UNIT_SCALE);

        int layerCount = world.getTiledMap().getLayers().getCount();
        int overheadIndex = -1;
        for (int i = 0; i < layerCount; i++) {
            MapLayer layer = world.getTiledMap().getLayers().get(i);
            if (OVERHEAD_LAYER_NAME.equals(layer.getName())) {
                overheadIndex = i;
                break;
            }
        }
        if (overheadIndex >= 0) {
            this.overheadLayerIndices = new int[] {overheadIndex};
            this.groundLayerIndices = new int[layerCount - 1];
            int dst = 0;
            for (int i = 0; i < layerCount; i++) {
                if (i != overheadIndex) groundLayerIndices[dst++] = i;
            }
        } else {
            this.overheadLayerIndices = new int[0];
            this.groundLayerIndices = new int[layerCount];
            for (int i = 0; i < layerCount; i++) groundLayerIndices[i] = i;
        }
    }

    public void render(float deltaSeconds, OrthographicCamera camera) {
        stateTime += deltaSeconds;

        mapRenderer.setView(camera);
        if (groundLayerIndices.length > 0) mapRenderer.render(groundLayerIndices);

        batch.setProjectionMatrix(camera.combined);
        batch.begin();
        if (world.getHomeOwner().isVisible()) drawActor(world.getHomeOwner(), ownerIdle, ownerWalk);
        if (world.getDog().isVisible()) drawDog(world.getDog());
        drawPlayer(world.getPlayer());
        batch.end();

        if (overheadLayerIndices.length > 0) mapRenderer.render(overheadLayerIndices);
    }

    public void renderPlayerOnly(float deltaSeconds, OrthographicCamera camera) {
        stateTime += deltaSeconds;

        batch.setProjectionMatrix(camera.combined);
        batch.begin();
        drawPlayer(world.getPlayer());
        batch.end();
    }

    public GameWorld getWorld() { return world; }

    @Override
    public void dispose() {
        mapRenderer.dispose();
        batch.dispose();
        playerIdleTexture.dispose();
        playerWalkTexture.dispose();
        playerCrouchTexture.dispose();
        ownerIdleTexture.dispose();
        ownerWalkTexture.dispose();
        dogWalkTexture.dispose();
        dogSleepTexture.dispose();
    }

    private void drawPlayer(Player player) {
        if (player.isCrouching()) {
            if (player.isMoving()) {
                drawAnimation(player, playerCrouchWalk);
            } else {
                drawAnimation(player, playerCrouchIdle);
            }
            return;
        }

        drawActor(player, playerIdle, playerWalk);
    }

    private void drawDog(Dog dog) {
        if (dog.isSleeping()) {
            TextureRegion frame = dogSleep.getKeyFrame(stateTime, true);
            batch.draw(frame, dog.getX(), dog.getY(), DOG_DRAW_SIZE, DOG_DRAW_SIZE);
            return;
        }

        int spriteRow = dog.getFacingDirection().getSpriteRow();
        TextureRegion frame = dogWalk.get(spriteRow).getKeyFrame(stateTime, true);
        batch.draw(frame, dog.getX(), dog.getY(), DOG_DRAW_SIZE, DOG_DRAW_SIZE);
    }

    private void drawActor(MovableEntity actor, List<Animation<TextureRegion>> idle, List<Animation<TextureRegion>> walk) {
        int spriteRow = actor.getFacingDirection().getSpriteRow();
        Animation<TextureRegion> animation = actor.isMoving() ? walk.get(spriteRow) : idle.get(spriteRow);
        TextureRegion frame = animation.getKeyFrame(stateTime, true);
        batch.draw(frame, actor.getX(), actor.getY(), DRAW_SIZE, DRAW_SIZE);
    }

    private void drawAnimation(MovableEntity actor, List<Animation<TextureRegion>> animations) {
        int spriteRow = actor.getFacingDirection().getSpriteRow();
        TextureRegion frame = animations.get(spriteRow).getKeyFrame(stateTime, true);
        batch.draw(frame, actor.getX(), actor.getY(), DRAW_SIZE, DRAW_SIZE);
    }

    private static Texture loadPixelTexture(String path) {
        Texture texture = new Texture(path);
        texture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        return texture;
    }

    private static List<Animation<TextureRegion>> createAnimations(Texture texture) {
        return createAnimations(texture, FRAME_DURATION, 0, TextureRegion.split(texture, FRAME_SIZE, FRAME_SIZE)[0].length);
    }

    private static Animation<TextureRegion> createSingleRowAnimation(Texture texture, float frameDuration) {
        int frameHeight = texture.getHeight();
        int frameWidth = frameHeight;
        TextureRegion[][] sheetFrames = TextureRegion.split(texture, frameWidth, frameHeight);
        TextureRegion[] frames = sheetFrames[0];
        return new Animation<>(frameDuration, frames);
    }

    private static List<Animation<TextureRegion>> createAnimations(Texture texture, float frameDuration, int startFrame, int endFrameExclusive) {
        TextureRegion[][] sheetFrames = TextureRegion.split(texture, FRAME_SIZE, FRAME_SIZE);
        List<Animation<TextureRegion>> animations = new ArrayList<>(DIRECTION_COUNT);

        for (int row = 0; row < DIRECTION_COUNT; row++) {
            TextureRegion[] frames = new TextureRegion[endFrameExclusive - startFrame];
            System.arraycopy(sheetFrames[row], startFrame, frames, 0, frames.length);
            animations.add(new Animation<>(frameDuration, frames));
        }

        return animations;
    }

    private static List<Animation<TextureRegion>> createAnimations(Texture texture, float frameDuration, int[] startFrames, int endFrameExclusive) {
        TextureRegion[][] sheetFrames = TextureRegion.split(texture, FRAME_SIZE, FRAME_SIZE);
        List<Animation<TextureRegion>> animations = new ArrayList<>(DIRECTION_COUNT);

        for (int row = 0; row < DIRECTION_COUNT; row++) {
            int startFrame = startFrames[row];
            TextureRegion[] frames = new TextureRegion[endFrameExclusive - startFrame];
            System.arraycopy(sheetFrames[row], startFrame, frames, 0, frames.length);
            animations.add(new Animation<>(frameDuration, frames));
        }

        return animations;
    }
}
