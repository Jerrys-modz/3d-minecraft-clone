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
    private static final float CLOUD_DRIFT_RATE = 0.05f; // noise-units per second

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

    /** Continuous cloud-drift phase (noise units), always advancing so the sky keeps changing. */
    private float cloudPhase = 0f;

    /** Whole day/night cycles completed; drives the {@link Calendar} and seasons. */
    private int daysElapsed = 0;

    /**
     * Fraction of the cycle that is daylight. {@link Calendar} feeds the
     * season's value here so sunrise/sunset shift with the time of year - long
     * days in summer, short days in winter. Default 0.5 (a balanced day).
     */
    private float daylightFraction = 0.5f;

    public void update(float dt) {
        // Count every whole cycle completed (so a long frame delta can span
        // several days) and keep only the fractional remainder as the time of day.
        float nextTime = time + dt / DAY_LENGTH_SECONDS;
        int completedDays = (int) Math.floor(nextTime);
        time = nextTime - completedDays;
        daysElapsed += completedDays;
        cloudPhase += dt * CLOUD_DRIFT_RATE;
    }

    public void setTime(float t) {
        time = ((t % 1f) + 1f) % 1f;
    }

    public float getTime() {
        return time;
    }

    /** Whole day/night cycles completed since the world started (see {@link Calendar}). */
    public int getDayIndex() {
        return daysElapsed;
    }

    /** Resets the day counter - call when a new world starts so its calendar begins at day one. */
    public void resetDays() {
        daysElapsed = 0;
    }

    /** Sets the daylight fraction of the cycle (seasonal) - drives sunrise/sunset. */
    public void setDaylightFraction(float fraction) {
        daylightFraction = Math.max(0.05f, Math.min(0.95f, fraction));
    }

    /** Time of day (0..1) when the sun rises. */
    public float getSunriseTime() {
        return 0.5f - daylightFraction / 2f;
    }

    /** Time of day (0..1) when the sun sets. */
    public float getSunsetTime() {
        return 0.5f + daylightFraction / 2f;
    }

    /** Monotonic cloud-drift phase for the sky shader; see {@link #update}. */
    public float getCloudPhase() {
        return cloudPhase;
    }

    /** Overrides the cloud-drift phase (used by the headless autotest). */
    public void setCloudPhase(float phase) {
        cloudPhase = phase;
    }

    /** 0 at night, 1 at noon, smoothly rising through the seasonal daylight window. */
    public float getDaylightFactor() {
        float sunrise = getSunriseTime();
        float sunset = getSunsetTime();
        float t = (time - sunrise) / (sunset - sunrise);
        if (t <= 0f || t >= 1f) return 0f;
        return (float) ((1 - Math.cos(2 * Math.PI * t)) / 2);
    }

    public float getAmbientBrightness() {
        float daylight = getDaylightFactor();
        return NIGHT_MIN_BRIGHTNESS + (1f - NIGHT_MIN_BRIGHTNESS) * daylight;
    }

    /**
     * World-space direction toward the sun: it rises in the +X direction at the
     * seasonal sunrise, is straight up at noon, and sets at the seasonal sunset.
     * A separate night arc carries it below the horizon back to the next
     * sunrise, so the direction stays continuous across the midnight wrap.
     * Returns a reused vector.
     */
    public Vector3f getSunDirection() {
        float sunrise = getSunriseTime();
        float sunset = getSunsetTime();
        float angle;
        if (time >= sunrise && time <= sunset) {
            // Daylight arc: horizon (east) at sunrise, overhead at noon, horizon (west) at sunset.
            float daylightProgress = (time - sunrise) / daylightFraction;
            angle = (float) (Math.PI * daylightProgress);
        } else {
            // Night arc below the horizon, continuous across the midnight boundary.
            float nightLength = 1f - daylightFraction;
            float nightProgress = time >= sunset
                    ? (time - sunset) / nightLength
                    : (time + 1f - sunset) / nightLength;
            angle = (float) (Math.PI * (1f + nightProgress));
        }
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
