package com.onelastheist.game.entity.player;

import com.badlogic.gdx.Gdx;
import com.onelastheist.game.config.ControlConfig;
import com.onelastheist.game.world.CollisionMap;

/**
 * Translates raw keyboard polling into player actions each frame. Owns no state
 * of its own beyond the bound {@link ControlConfig} — all the mutable state
 * lives on {@link Player}, which makes hot-swapping the controller trivial.
 *
 * <p>Two overloads of {@code update}: one for screens without a world (tutorial)
 * and one with a {@link CollisionMap} for the actual gameplay map.
 */
public class PlayerController {
    private final ControlConfig controls;

    public PlayerController(ControlConfig controls) { this.controls = controls; }

    /** Tutorial / no-collision overload. */
    public void update(Player player, float deltaSeconds) {
        update(player, deltaSeconds, null);
    }

    /**
     * Full update: reads movement keys, sets crouch flag and active speed,
     * then dispatches either a free move or a collision-aware move depending
     * on whether a {@link CollisionMap} was provided.
     */
    public void update(Player player, float deltaSeconds, CollisionMap collisionMap) {
        float dx = 0f;
        float dy = 0f;
        boolean crouching = Gdx.input.isKeyPressed(controls.crouch);

        if (Gdx.input.isKeyPressed(controls.moveLeft)) dx -= 1f;
        if (Gdx.input.isKeyPressed(controls.moveRight)) dx += 1f;
        if (Gdx.input.isKeyPressed(controls.moveDown)) dy -= 1f;
        if (Gdx.input.isKeyPressed(controls.moveUp)) dy += 1f;

        player.setCrouching(crouching);
        // Speed is set every frame because crouch can toggle on/off mid-step.
        player.setSpeed(crouching ? Player.CROUCH_SPEED : Player.WALK_SPEED);
        if (collisionMap == null) {
            player.move(dx, dy, deltaSeconds);
        } else {
            player.tryMove(dx, dy, deltaSeconds, collisionMap);
        }
    }

    public ControlConfig getControls() { return controls; }
}
