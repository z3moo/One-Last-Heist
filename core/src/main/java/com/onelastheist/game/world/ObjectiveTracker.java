package com.onelastheist.game.world;

/** Theo doi tien do loot va tuyen bi mat. */
public class ObjectiveTracker {
    private final int targetMoney;
    private int collectedMoney;
    private int evidenceCount;
    private boolean hiddenRouteEntered;

    public ObjectiveTracker(int targetMoney) { this.targetMoney = targetMoney; }

    public void addMoney(int amount) { collectedMoney += Math.max(0, amount); }
    public void addEvidence() { evidenceCount++; }
    public void enterHiddenRoute() { hiddenRouteEntered = true; }
    public boolean hasEnoughMoney() { return collectedMoney >= targetMoney; }
    public int getCollectedMoney() { return collectedMoney; }
    public int getEvidenceCount() { return evidenceCount; }
    public boolean isHiddenRouteEntered() { return hiddenRouteEntered; }
}
