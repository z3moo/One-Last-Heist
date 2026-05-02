package com.onelastheist.game.ai;

import com.onelastheist.game.entity.player.Player;

public class DetectionService {
    public boolean canDetect(Player player) { return !player.isHidden(); }
}
