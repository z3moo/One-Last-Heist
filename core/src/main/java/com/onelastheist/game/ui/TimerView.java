package com.onelastheist.game.ui;

import com.onelastheist.game.world.WorldClock;

public class TimerView {
    private final WorldClock clock;
    public TimerView(WorldClock clock) { this.clock = clock; }
    public String getText() { return Integer.toString((int) clock.getRemainingSeconds()); }
}
