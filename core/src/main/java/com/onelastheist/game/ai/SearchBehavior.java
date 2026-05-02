package com.onelastheist.game.ai;

import com.onelastheist.game.entity.player.Player;

public class SearchBehavior {
    public boolean catches(Player player) { return !player.isHidden(); }
}
