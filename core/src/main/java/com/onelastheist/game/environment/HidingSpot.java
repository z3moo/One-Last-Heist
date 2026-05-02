package com.onelastheist.game.environment;

import com.onelastheist.game.entity.player.Player;
import com.onelastheist.game.interaction.Hideable;
import com.onelastheist.game.interaction.Interactable;

public class HidingSpot implements Hideable, Interactable {
    @Override public void interact(Player player) { hide(player); }
    @Override public void hide(Player player) { player.hide(); }
    @Override public void leave(Player player) { player.leaveHiding(); }
}
