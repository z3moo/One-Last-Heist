package com.onelastheist.game.ending;

import com.onelastheist.game.item.Inventory;

public class FinalEncounterService {
    public boolean survives(Inventory inventory) { return inventory.hasWeapon(); }
}
