package com.onelastheist.game.render;

import com.badlogic.gdx.utils.Disposable;
import com.onelastheist.game.world.GameWorld;

/** Ve GameWorld. Khong dat luat gameplay trong lop nay. */
public class WorldRenderer implements Disposable {
    private final GameWorld world;

    public WorldRenderer(GameWorld world) { this.world = world; }
    public void render() {
        // Tam thoi de trong cho den khi them map/sprite.
    }
    public GameWorld getWorld() { return world; }
    @Override public void dispose() {}
}
