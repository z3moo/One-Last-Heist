package com.onelastheist.game.environment;

import com.onelastheist.game.entity.player.Player;
import com.onelastheist.game.interaction.Interactable;

public class NoiseSource implements Interactable {
    private boolean noisy;
    @Override public void interact(Player player) { noisy = true; }
    public boolean isNoisy() { return noisy; }
}
