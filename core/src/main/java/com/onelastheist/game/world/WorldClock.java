package com.onelastheist.game.world;

/** Dong ho cua map chinh. Tuyen bi mat co the tam dung bang setRunning(false). */
public class WorldClock {
    private float remainingSeconds;
    private boolean running = true;

    public WorldClock(float remainingSeconds) { this.remainingSeconds = remainingSeconds; }

    public void update(float deltaSeconds) {
        if (running && remainingSeconds > 0f) remainingSeconds = Math.max(0f, remainingSeconds - deltaSeconds);
    }

    public float getRemainingSeconds() { return remainingSeconds; }
    public boolean isTimeOver() { return remainingSeconds <= 0f; }
    public void setRunning(boolean running) { this.running = running; }
}
