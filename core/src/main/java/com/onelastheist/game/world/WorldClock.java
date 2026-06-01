package com.onelastheist.game.world;

/**
 * Countdown timer for the main heist. Ticks down on every {@link #update(float)}
 * call while {@code running} is true; reaching zero is the trigger for the
 * "homeowner returns" / time-over fail flow.
 *
 * <p>Pause-able via {@link #setRunning(boolean)} so the hidden route (which has
 * no time pressure) can freeze the clock while the player is inside it.
 */
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
