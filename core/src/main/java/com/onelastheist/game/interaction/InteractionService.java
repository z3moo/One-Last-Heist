package com.onelastheist.game.interaction;

import com.onelastheist.game.entity.player.Player;

/** Dieu phoi tuong tac cua nguoi choi voi cac vat the gan do. */
public class InteractionService {
    public void interact(Player player, Interactable interactable) {
        interactable.interact(player);
    }
}
