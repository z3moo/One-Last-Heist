package com.onelastheist.game.environment;

import com.onelastheist.game.entity.base.Entity;

/**
 * Stationary, interactive piano. Pressing E in range opens a keyboard
 * overlay where the player plays C/D/E/F/G/A/B notes. Successfully
 * playing the {@link #SOLUTION} sequence at any point in their input
 * history unlocks the storage-house key.
 *
 * <p><b>Matching is "contains", not "equals".</b> The buffer accumulates
 * every note pressed (capped at {@link #BUFFER_CAP} so an infinite session
 * doesn't grow memory), and the puzzle solves the moment {@code SOLUTION}
 * appears as a substring of the full buffer. The player can fumble through
 * as many wrong notes as they want; the key drops the instant the correct
 * sequence emerges from the noise.
 *
 * <p>Solved state is sticky — re-opening a solved piano shows the keyboard
 * but doesn't re-trigger the reward.
 */
public class PianoPuzzle extends Entity {
    /** Required note sequence. Each char is one of C/D/E/F/G/A/B. */
    public static final String SOLUTION = "CBFFD";
    /** Width of the rolling-display row shown above the keyboard. */
    public static final int DISPLAY_LENGTH = 5;
    /**
     * Hard cap on the unbounded input buffer. Past this we drop chars from
     * the front, keeping the trailing window long enough that any
     * still-in-progress copy of {@link #SOLUTION} survives the trim.
     * Bounds memory in case a player decides to type for an hour.
     */
    private static final int BUFFER_CAP = 256;
    /** Interact radius (world units). Same idiom as Newspaper — generous so the prompt fires reliably. */
    public static final float INTERACT_RADIUS = 80f;

    private final StringBuilder buffer = new StringBuilder();
    private boolean solved;
    /** Set non-zero by {@link #pressNote} when the sequence completes correctly. Counts down in PlayScreen. */
    private float justSolvedTimer;

    public PianoPuzzle(float x, float y) {
        setPosition(x, y);
    }

    /**
     * True if the player's hitbox-center sits within {@link #INTERACT_RADIUS}
     * of the piano's center. Mirrors {@link Newspaper#playerInRange}.
     */
    public boolean playerInRange(float playerCenterX, float playerCenterY) {
        float dx = playerCenterX - getX();
        float dy = playerCenterY - getY();
        return dx * dx + dy * dy <= INTERACT_RADIUS * INTERACT_RADIUS;
    }

    /**
     * Record one note press. Returns {@link Result#SOLVED} on the first
     * frame the puzzle just solved (so the caller can issue the reward
     * exactly once), {@link Result#ALREADY_SOLVED} on every subsequent
     * press, or {@link Result#PROGRESS} otherwise. There is intentionally
     * no WRONG result — the matching is substring, so a non-match just
     * means "keep playing".
     */
    public Result pressNote(char note) {
        if (solved) return Result.ALREADY_SOLVED;
        buffer.append(note);
        if (buffer.length() > BUFFER_CAP) {
            // Trim from the front but keep the last (BUFFER_CAP - SOLUTION + 1)
            // chars — that's the largest window that can still complete a
            // copy of SOLUTION ending at the most recent press.
            int keep = BUFFER_CAP - SOLUTION.length() + 1;
            buffer.delete(0, buffer.length() - keep);
        }
        // toString().contains is fine here: BUFFER_CAP keeps the search
        // bounded, and we only call this on a key-press boundary.
        if (buffer.indexOf(SOLUTION) >= 0) {
            solved = true;
            justSolvedTimer = 1.6f;
            return Result.SOLVED;
        }
        return Result.PROGRESS;
    }

    /** Tick down the just-solved flash timer. PlayScreen calls this each frame. */
    public void update(float delta) {
        if (justSolvedTimer > 0f) justSolvedTimer = Math.max(0f, justSolvedTimer - delta);
    }

    public boolean isSolved() { return solved; }

    /**
     * Returns the trailing slice of the input buffer for the rolling-row
     * display. Length is min(buffer.length, {@link #DISPLAY_LENGTH}).
     */
    public String getDisplayBuffer() {
        int n = buffer.length();
        if (n <= DISPLAY_LENGTH) return buffer.toString();
        return buffer.substring(n - DISPLAY_LENGTH);
    }

    public boolean isJustSolved() { return justSolvedTimer > 0f; }
    public float getJustSolvedTimer() { return justSolvedTimer; }

    /** Outcome of {@link #pressNote(char)}. Caller acts on SOLVED to issue the reward. */
    public enum Result {
        /** Buffer grew, no match yet. The default response after most presses. */
        PROGRESS,
        /** First frame of solved — caller awards the key here, exactly once. */
        SOLVED,
        /** Already solved on a prior press; press is ignored for reward purposes (note still plays). */
        ALREADY_SOLVED;
    }
}
