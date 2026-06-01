package com.onelastheist.game.world;

/**
 * Run-scoped progress for the heist's two parallel objectives:
 * <ul>
 *   <li>Loot enough money to clear the {@code targetMoney} threshold so a normal
 *       escape ending becomes available.</li>
 *   <li>Find evidence and enter the hidden route to unlock the true ending.</li>
 * </ul>
 *
 * <p>Pure data accumulator — the ending resolver inspects this state at run end
 * and decides which {@link com.onelastheist.game.ending.EndingType} fires.
 */
public class ObjectiveTracker {
    private final int targetMoney;
    private int collectedMoney;
    private int evidenceCount;
    private boolean hiddenRouteEntered;

    public ObjectiveTracker(int targetMoney) { this.targetMoney = targetMoney; }

    /** Adds positive money only — guards against accidental negative deltas. */
    public void addMoney(int amount) { collectedMoney += Math.max(0, amount); }
    public void addEvidence() { evidenceCount++; }
    public void enterHiddenRoute() { hiddenRouteEntered = true; }
    public boolean hasEnoughMoney() { return collectedMoney >= targetMoney; }
    public int getCollectedMoney() { return collectedMoney; }
    public int getEvidenceCount() { return evidenceCount; }
    public boolean isHiddenRouteEntered() { return hiddenRouteEntered; }
}
