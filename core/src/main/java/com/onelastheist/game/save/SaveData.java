package com.onelastheist.game.save;

import com.onelastheist.game.ending.EndingType;

public class SaveData {
    private int money;
    private EndingType lastEnding;

    public int getMoney() { return money; }
    public void setMoney(int money) { this.money = money; }
    public EndingType getLastEnding() { return lastEnding; }
    public void setLastEnding(EndingType lastEnding) { this.lastEnding = lastEnding; }
}
