package com.onelastheist.game.entity.player;

import com.badlogic.gdx.Gdx;
import com.onelastheist.game.config.ControlConfig;
import com.onelastheist.game.world.CollisionMap;

/** Chuyen input thanh y dinh cua nguoi choi. Phan doc input LibGDX se duoc them o day. */
public class PlayerController {
    private final ControlConfig controls;

    public PlayerController(ControlConfig controls) { this.controls = controls; }

    public void update(Player player, float deltaSeconds) {
        update(player, deltaSeconds, null);
    }

    public void update(Player player, float deltaSeconds, CollisionMap collisionMap) {
        float dx = 0f;
        float dy = 0f;
        boolean crouching = Gdx.input.isKeyPressed(controls.crouch);

        if (Gdx.input.isKeyPressed(controls.moveLeft)) dx -= 1f;
        if (Gdx.input.isKeyPressed(controls.moveRight)) dx += 1f;
        if (Gdx.input.isKeyPressed(controls.moveDown)) dy -= 1f;
        if (Gdx.input.isKeyPressed(controls.moveUp)) dy += 1f;

        player.setCrouching(crouching);
        player.setSpeed(crouching ? Player.CROUCH_SPEED : Player.WALK_SPEED);
        if (collisionMap == null) {
            player.move(dx, dy, deltaSeconds);
        } else {
            player.tryMove(dx, dy, deltaSeconds, collisionMap);
        }
    }

    public ControlConfig getControls() { return controls; }
}
