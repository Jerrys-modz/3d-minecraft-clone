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

    // Sky colors for the procedural skybox (see SkyRenderer / sky.frag).
    private static final Vector3f DAY_ZENITH = new Vector3f(0.30f, 0.56f, 0.93f);
    private static final Vector3f DAY_HORIZON = new Vector3f(0.62f, 0.80f, 0.95f);
    private static final Vector3f NIGHT_ZENITH = new Vector3f(0.05f, 0.07f, 0.16f);
    private static final Vector3f NIGHT_HORIZON = new Vector3f(0.03f, 0.05f, 0.10f);
    private static final Vector3f SUN_COLOR = new Vector3f(1.0f, 0.95f, 0.80f);
    private static final Vector3f MOON_COLOR = new Vector3f(0.90f, 0.95f, 1.0f);

    /** Reused output vectors so per-frame lookups don't allocate. */
    private final Vector3f sunDir = new Vector3f();
    private final Vector3f horizonColor = new Vector3f();
    private final Vector3f zenithColor = new Vector3f();
    private final Vector3f nightZenithColor = new Vector3f();
    private final Vector3f sunColorOut = new Vector3f();
    private final Vector3f moonColorOut = new Vector3f();

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

    /**
     * World-space direction toward the sun: it orbits in the X-Z plane, at the
     * horizon on sunrise/sunset and straight up at noon. Returns a reused vector.
     */
    public Vector3f getSunDirection() {
        float angle = (float) (2 * Math.PI * (time - 0.25));
        return sunDir.set((float) Math.cos(angle), (float) Math.sin(angle), 0f);
    }

    /** Sky color near the horizon (also used for distance fog). Returns a reused vector. */
    public Vector3f getHorizonColor() {
        float t = getDaylightFactor();
        return horizonColor.set(
                NIGHT_HORIZON.x + (DAY_HORIZON.x - NIGHT_HORIZON.x) * t,
                NIGHT_HORIZON.y + (DAY_HORIZON.y - NIGHT_HORIZON.y) * t,
                NIGHT_HORIZON.z + (DAY_HORIZON.z - NIGHT_HORIZON.z) * t);
    }

    public Vector3f getZenithColor() {
        float t = getDaylightFactor();
        return zenithColor.set(
                NIGHT_ZENITH.x + (DAY_ZENITH.x - NIGHT_ZENITH.x) * t,
                NIGHT_ZENITH.y + (DAY_ZENITH.y - NIGHT_ZENITH.y) * t,
                NIGHT_ZENITH.z + (DAY_ZENITH.z - NIGHT_ZENITH.z) * t);
    }

    public Vector3f getNightZenithColor() {
        return nightZenithColor.set(NIGHT_ZENITH);
    }

    public Vector3f getSunColor() {
        return sunColorOut.set(SUN_COLOR);
    }

    public Vector3f getMoonColor() {
        return moonColorOut.set(MOON_COLOR);
    }

    public boolean isNight() {
        return getDaylightFactor() < 0.35f;
    }
}
