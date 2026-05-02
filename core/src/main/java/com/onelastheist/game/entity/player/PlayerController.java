package com.onelastheist.game.entity.player;

import com.onelastheist.game.config.ControlConfig;

/** Chuyen input thanh y dinh cua nguoi choi. Phan doc input LibGDX se duoc them o day. */
public class PlayerController {
    private final ControlConfig controls;

    public PlayerController(ControlConfig controls) { this.controls = controls; }
    public ControlConfig getControls() { return controls; }
}
