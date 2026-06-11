package com.onelastheist.game.environment;

import com.onelastheist.game.entity.base.Entity;
import com.onelastheist.game.item.Inventory;
import com.onelastheist.game.item.MoneyItem;

/**
 * A coin or diamond lying on the floor, picked up with F. Holds a money
 * {@link Item} the player adds to their inventory plus a per-pickup bob
 * phase used by the renderer to give every coin a slightly different
 * up-and-down animation start.
 *
 * <p>The bob is purely visual — the entity's world position never moves,
 * so collision and pickup-distance checks remain stable.
 */
public class MoneyPickup extends Entity {
    /** Whether this pickup represents a coin or a diamond — drives sprite + value. */
    public enum Kind { COIN, DIAMOND }

    private final MoneyItem money;
    private final Kind kind;
    /** Phase offset (radians) so a row of coins doesn't bob in lockstep. */
    private final float bobPhase;
    private boolean collected;

    public MoneyPickup(MoneyItem money, Kind kind, float x, float y, float bobPhase) {
        this.money = money;
        this.kind = kind;
        this.bobPhase = bobPhase;
        setPosition(x, y);
    }

    public MoneyItem getMoney() { return money; }
    public Kind getKind() { return kind; }
    public float getBobPhase() { return bobPhase; }
    public int getValue() { return money.getValue(); }
    public boolean isCollected() { return collected; }

    public void collectInto(Inventory inventory) {
        if (collected) return;
        inventory.add(money);
        collected = true;
    }
}
