package com.onelastheist.game.quest;

public interface Objective {
    String getName();
    ObjectiveStatus getStatus();
    void update();
}
