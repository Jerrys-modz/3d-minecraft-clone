package com.minecraftclone.engine;

/** Tracks delta time and FPS. */
public class Timer {

    private double lastLoopTime;
    private float fpsAccumulator;
    private int fpsCount;
    private int lastFps;

    public void init() {
        lastLoopTime = time();
    }

    public double time() {
        return System.nanoTime() / 1_000_000_000.0;
    }

    /** Elapsed seconds since the previous call to this method. */
    public float getDeltaTime() {
        double now = time();
        float delta = (float) (now - lastLoopTime);
        lastLoopTime = now;
        return Math.max(0f, Math.min(delta, 0.25f)); // clamp to avoid huge jumps (e.g. breakpoints)
    }

    public void updateFps(float delta) {
        fpsAccumulator += delta;
        fpsCount++;
        if (fpsAccumulator >= 1.0f) {
            lastFps = fpsCount;
            fpsCount = 0;
            fpsAccumulator = 0;
        }
    }

    public int getFps() {
        return lastFps;
    }
}
