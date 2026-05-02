package com.onelastheist.game.interaction;

import com.onelastheist.game.item.Inventory;

public interface Lockable {
    boolean isLocked();
    boolean unlock(Inventory inventory);
}
