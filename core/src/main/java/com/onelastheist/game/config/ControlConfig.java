package com.onelastheist.game.config;

import com.badlogic.gdx.Input;

/**
 * Keyboard bindings for player input. Read by {@link com.onelastheist.game.entity.player.PlayerController}
 * each frame and by {@link com.onelastheist.game.screen.PlayScreen} for one-shot actions
 * (interact / pause). Fields are public-final so screens can reference them without
 * boilerplate getters; future settings UI can swap this whole instance in {@code GameContext}.
 */
public class ControlConfig {
    public final int moveUp = Input.Keys.W;
    public final int moveDown = Input.Keys.S;
    public final int moveLeft = Input.Keys.A;
    public final int moveRight = Input.Keys.D;
    public final int crouch = Input.Keys.CONTROL_LEFT;
    public final int interact = Input.Keys.E;
    public final int collect = Input.Keys.F;
}
