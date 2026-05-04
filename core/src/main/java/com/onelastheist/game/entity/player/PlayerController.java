package com.onelastheist.game.entity.player;

import com.badlogic.gdx.Gdx;
import com.onelastheist.game.config.ControlConfig;

/** Chuyen input thanh y dinh cua nguoi choi. Phan doc input LibGDX se duoc them o day. */
public class PlayerController {
    private final ControlConfig controls;

    public PlayerController(ControlConfig controls) { this.controls = controls; }

    public void update(Player player, float deltaSeconds) {
        float dx = 0f;
        float dy = 0f;

        if (Gdx.input.isKeyPressed(controls.moveLeft)) dx -= 1f;
        if (Gdx.input.isKeyPressed(controls.moveRight)) dx += 1f;
        if (Gdx.input.isKeyPressed(controls.moveDown)) dy -= 1f;
        if (Gdx.input.isKeyPressed(controls.moveUp)) dy += 1f;

        player.move(dx, dy, deltaSeconds);
    }

    public ControlConfig getControls() { return controls; }
}
