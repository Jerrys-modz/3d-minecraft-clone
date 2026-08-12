package com.minecraftclone.engine;

import org.joml.Vector3f;

/**
 * Tracks time of day and derives the sky/fog color and a global ambient
 * brightness multiplier from it. "Night" is approximated by dimming
 * everything and shifting the sky towards dark blue - a single scene-wide
 * floor, not real light propagation (see the chunk shader's per-face fixed
 * shading). Torches punch a local hole in that floor instead: see {@link
 * com.minecraftclone.world.Chunk}'s block-light baking, which keeps
 * torch-lit areas bright independently of this class's day/night dimming.
 */
public class DayNightCycle {

    private static final float DAY_LENGTH_SECONDS = 600f; // one full day/night cycle
    private static final float NIGHT_MIN_BRIGHTNESS = 0.28f;

    private static final Vector3f DAY_SKY = new Vector3f(0.53f, 0.81f, 0.92f);
    private static final Vector3f NIGHT_SKY = new Vector3f(0.02f, 0.03f, 0.10f);

    /** 0 = midnight, 0.25 = sunrise, 0.5 = noon, 0.75 = sunset. Starts mid-morning. */
    private float time = 0.3f;

    public void update(float dt) {
        time = (time + dt / DAY_LENGTH_SECONDS) % 1f;
    }

    public void setTime(float t) {
        time = ((t % 1f) + 1f) % 1f;
    }

    public float getTime() {
        return time;
    }

    /** 0 at midnight, 1 at noon, smoothly interpolated in between. */
    public float getDaylightFactor() {
        return (float) ((Math.cos(2 * Math.PI * (time - 0.5)) + 1.0) / 2.0);
    }

    public float getAmbientBrightness() {
        float daylight = getDaylightFactor();
        return NIGHT_MIN_BRIGHTNESS + (1f - NIGHT_MIN_BRIGHTNESS) * daylight;
    }

    public Vector3f getSkyColor() {
        float t = getDaylightFactor();
        return new Vector3f(
                NIGHT_SKY.x + (DAY_SKY.x - NIGHT_SKY.x) * t,
                NIGHT_SKY.y + (DAY_SKY.y - NIGHT_SKY.y) * t,
                NIGHT_SKY.z + (DAY_SKY.z - NIGHT_SKY.z) * t);
    }

    public boolean isNight() {
        return getDaylightFactor() < 0.35f;
    }
}
