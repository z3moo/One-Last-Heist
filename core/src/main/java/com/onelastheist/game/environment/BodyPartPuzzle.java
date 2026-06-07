package com.onelastheist.game.environment;

import com.onelastheist.game.entity.base.Entity;

/**
 * Stationary body-part clue scattered in the storage room. Pressing E in
 * range opens a fullscreen overlay showing the body part as the question;
 * the player presses A, B, C, or D to answer. A correct answer marks the
 * puzzle solved (sticky); a wrong answer deducts 30 seconds via the
 * game's time-penalty hook. Solving all four body-part puzzles in the
 * heist time triggers the hidden WIN1 ending.
 *
 * <p>Same interaction shape as {@link Newspaper} and {@link PianoPuzzle}
 * but with built-in answer state per instance instead of a shared rolling
 * buffer.
 */
public class BodyPartPuzzle extends Entity {
    /** Texture variant. The renderer picks the matching sprite. */
    public enum Kind { ARM, LEG }

    /** Result of a single answer press. Caller acts on these. */
    public enum Result {
        /** Right answer; first time only — caller may unlock next-step state. */
        CORRECT,
        /** Wrong answer — caller should fire the time penalty. */
        WRONG,
        /** Already solved on a prior press; ignore for scoring. */
        ALREADY_SOLVED
    }

    /** How close the player must stand for the prompt + interaction to fire. */
    public static final float INTERACT_RADIUS = 80f;
    /** How long the green/red flash sits on the overlay after an answer. */
    private static final float FLASH_SECONDS = 1.4f;

    private final Kind kind;
    /** 1-based ordinal so the overlay can show "Question N of 4" if we ever want to. */
    private final int questionIndex;
    /** Correct letter, one of 'A'/'B'/'C'/'D'. */
    private final char correctAnswer;

    private boolean solved;
    /** Counts down each frame; nonzero means draw a recent-answer flash. */
    private float flashTimer;
    /** Whether the most recent answer was correct (drives flash colour). */
    private boolean flashCorrect;
    /** The letter the player most recently pressed, or 0 if none yet. */
    private char lastAnswer;

    public BodyPartPuzzle(Kind kind, int questionIndex, char correctAnswer, float x, float y) {
        this.kind = kind;
        this.questionIndex = questionIndex;
        this.correctAnswer = correctAnswer;
        setPosition(x, y);
    }

    /**
     * True if the player's hitbox-center sits within {@link #INTERACT_RADIUS}
     * of this puzzle. Mirrors {@link Newspaper#playerInRange}.
     */
    public boolean playerInRange(float playerCenterX, float playerCenterY) {
        float dx = playerCenterX - getX();
        float dy = playerCenterY - getY();
        return dx * dx + dy * dy <= INTERACT_RADIUS * INTERACT_RADIUS;
    }

    /**
     * Record one answer press. Sticky on first correct answer (subsequent
     * presses return ALREADY_SOLVED so the time penalty doesn't fire on a
     * revisit). Both flash timer and last-answer letter update so the
     * overlay can render a coloured ring around the chosen key.
     */
    public Result answer(char letter) {
        lastAnswer = letter;
        if (solved) {
            flashTimer = FLASH_SECONDS;
            flashCorrect = true;
            return Result.ALREADY_SOLVED;
        }
        boolean correct = letter == correctAnswer;
        flashTimer = FLASH_SECONDS;
        flashCorrect = correct;
        if (correct) {
            solved = true;
            return Result.CORRECT;
        }
        return Result.WRONG;
    }

    /** Tick down the flash timer. PlayScreen calls this each frame the puzzle is open. */
    public void update(float delta) {
        if (flashTimer > 0f) flashTimer = Math.max(0f, flashTimer - delta);
    }

    public Kind getKind() { return kind; }
    public int getQuestionIndex() { return questionIndex; }
    public boolean isSolved() { return solved; }
    public boolean isFlashing() { return flashTimer > 0f; }
    public boolean isFlashCorrect() { return flashCorrect; }
    /** Normalized [0, 1] flash strength, 1 right after the press, 0 when expired. */
    public float getFlashStrength() {
        if (FLASH_SECONDS <= 0f) return 0f;
        return Math.max(0f, flashTimer / FLASH_SECONDS);
    }
    public char getLastAnswer() { return lastAnswer; }
}
