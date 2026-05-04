package com.onelastheist.game.render;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.Disposable;
import com.onelastheist.game.entity.base.MovableEntity;
import com.onelastheist.game.world.GameWorld;

import java.util.ArrayList;
import java.util.List;

/** Ve GameWorld. Khong dat luat gameplay trong lop nay. */
public class WorldRenderer implements Disposable {
    private static final int FRAME_SIZE = 48;
    private static final int DIRECTION_COUNT = 8;
    private static final float DRAW_SIZE = 144f;
    private static final float FRAME_DURATION = 0.14f;

    private final GameWorld world;
    private final SpriteBatch batch = new SpriteBatch();
    private final Texture playerIdleTexture = loadPixelTexture("characters/player/player_idle.png");
    private final Texture playerWalkTexture = loadPixelTexture("characters/player/player_walk.png");
    private final Texture ownerIdleTexture = loadPixelTexture("characters/enemies/enemies_idle.png");
    private final Texture ownerWalkTexture = loadPixelTexture("characters/enemies/enemies_walk.png");
    private final List<Animation<TextureRegion>> playerIdle = createAnimations(playerIdleTexture);
    private final List<Animation<TextureRegion>> playerWalk = createAnimations(playerWalkTexture);
    private final List<Animation<TextureRegion>> ownerIdle = createAnimations(ownerIdleTexture);
    private final List<Animation<TextureRegion>> ownerWalk = createAnimations(ownerWalkTexture);
    private float stateTime;

    public WorldRenderer(GameWorld world) { this.world = world; }

    public void render(float deltaSeconds) {
        stateTime += deltaSeconds;

        batch.begin();
        drawActor(world.getHomeOwner(), ownerIdle, ownerWalk);
        drawActor(world.getPlayer(), playerIdle, playerWalk);
        batch.end();
    }

    public GameWorld getWorld() { return world; }

    @Override
    public void dispose() {
        batch.dispose();
        playerIdleTexture.dispose();
        playerWalkTexture.dispose();
        ownerIdleTexture.dispose();
        ownerWalkTexture.dispose();
    }

    private void drawActor(MovableEntity actor, List<Animation<TextureRegion>> idle, List<Animation<TextureRegion>> walk) {
        int spriteRow = actor.getFacingDirection().getSpriteRow();
        Animation<TextureRegion> animation = actor.isMoving() ? walk.get(spriteRow) : idle.get(spriteRow);
        TextureRegion frame = animation.getKeyFrame(stateTime, true);
        batch.draw(frame, actor.getX(), actor.getY(), DRAW_SIZE, DRAW_SIZE);
    }

    private static Texture loadPixelTexture(String path) {
        Texture texture = new Texture(path);
        texture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        return texture;
    }

    private static List<Animation<TextureRegion>> createAnimations(Texture texture) {
        TextureRegion[][] sheetFrames = TextureRegion.split(texture, FRAME_SIZE, FRAME_SIZE);
        List<Animation<TextureRegion>> animations = new ArrayList<>(DIRECTION_COUNT);

        for (int row = 0; row < DIRECTION_COUNT; row++) {
            animations.add(new Animation<>(FRAME_DURATION, sheetFrames[row]));
        }

        return animations;
    }
}
