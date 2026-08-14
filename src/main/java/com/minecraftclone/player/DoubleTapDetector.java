package com.minecraftclone.player;

/**
 * Tracks whether the most recent press of a key landed within a short window
 * of the previous one - a "double-tap". Pure timing logic with no GLFW/Input
 * dependency (unlike {@link Player}, which needs a live window for key
 * events), so it can be unit tested directly.
 */
public class DoubleTapDetector {

    private final float window;
    private float gap = Float.MAX_VALUE;

    public DoubleTapDetector(float window) {
        this.window = window;
    }

    /**
     * Advances the clock by {@code dt} and, if {@code justPressed} is true (the
     * key transitioned from up to down this frame), checks whether the gap
     * since the previous press was within the double-tap window. Always resets
     * the gap on a press, so consecutive taps are measured from the most
     * recent one, not the first.
     */
    public boolean tick(float dt, boolean justPressed) {
        gap += dt;
        if (!justPressed) return false;
        boolean doubleTap = gap <= window;
        gap = 0f;
        return doubleTap;
    }
}
