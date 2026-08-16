package com.minecraftclone.engine;

import com.minecraftclone.util.FloatArray;

import java.util.Random;

/**
 * Simulates rain and snow falling around the player's eye position. The
 * simulation is pure logic (no GL), so it is unit-testable: particles spawn in
 * a box around the camera, fall/drift with time, expire below the view, and
 * are recycled through a fixed pool. Rendering reads the positions back
 * through {@link #writeRain} / {@link #writeSnow} into vertex buffers for the
 * line shader (see {@code WeatherRenderer}).
 * <p>
 * When the weather is clear (or switches type) the other pool is emptied, and
 * particle counts scale with the weather's strength.
 */
public class WeatherParticles {

    /** Hard caps on the particle pools, so memory is bounded regardless of weather. */
    public static final int MAX_RAIN = 500;
    public static final int MAX_SNOW = 300;

    private static final float RAIN_SPEED = 24f;
    private static final float RAIN_LENGTH = 0.35f;
    private static final float BOX_RADIUS = 14f;  // horizontal half-extent of the spawn box
    private static final float SPAWN_TOP = 10f;   // how far above the eye to spawn
    private static final float KILL_BELOW = 5f;   // expire below the eye
    private static final float SNOW_SPEED = 2.2f;
    private static final float SNOW_SWAY = 0.8f;
    private static final float SNOW_LIFE = 7f;

    private final Random rnd;

    private final float[] rainX = new float[MAX_RAIN];
    private final float[] rainY = new float[MAX_RAIN];
    private final float[] rainZ = new float[MAX_RAIN];
    private final float[] rainSpeed = new float[MAX_RAIN];
    private int rainCount;

    private final float[] snowX = new float[MAX_SNOW];
    private final float[] snowY = new float[MAX_SNOW];
    private final float[] snowZ = new float[MAX_SNOW];
    private final float[] snowSpeed = new float[MAX_SNOW];
    private final float[] snowPhase = new float[MAX_SNOW];
    private final float[] snowAge = new float[MAX_SNOW];
    private int snowCount;

    private float time;

    public WeatherParticles() {
        this(new Random());
    }

    public WeatherParticles(Random rnd) {
        this.rnd = rnd;
    }

    public int getRainCount() {
        return rainCount;
    }

    public int getSnowCount() {
        return snowCount;
    }

    /**
     * Advances the particles for the given weather by {@code dt} seconds, centered
     * on the eye position. {@code strength} (0..1) scales how many particles are
     * kept in the air.
     */
    public void update(float dt, float eyeX, float eyeY, float eyeZ, Weather weather, float strength) {
        time += dt;
        if (weather == Weather.RAIN) {
            updateRain(dt, eyeX, eyeY, eyeZ, (int) (strength * MAX_RAIN));
            snowCount = 0;
        } else if (weather == Weather.SNOW) {
            updateSnow(dt, eyeX, eyeY, eyeZ, (int) (strength * MAX_SNOW));
            rainCount = 0;
        } else {
            rainCount = 0;
            snowCount = 0;
        }
    }

    private void updateRain(float dt, float eyeX, float eyeY, float eyeZ, int target) {
        int w = 0;
        for (int i = 0; i < rainCount; i++) {
            rainY[i] -= rainSpeed[i] * dt;
            if (rainY[i] >= eyeY - KILL_BELOW) {
                rainX[w] = rainX[i];
                rainY[w] = rainY[i];
                rainZ[w] = rainZ[i];
                rainSpeed[w] = rainSpeed[i];
                w++;
            }
        }
        rainCount = w;
        int capped = Math.min(target, MAX_RAIN);
        while (rainCount < capped) {
            rainX[rainCount] = eyeX + (rnd.nextFloat() * 2f - 1f) * BOX_RADIUS;
            rainY[rainCount] = eyeY + SPAWN_TOP * (0.6f + rnd.nextFloat() * 0.6f);
            rainZ[rainCount] = eyeZ + (rnd.nextFloat() * 2f - 1f) * BOX_RADIUS;
            rainSpeed[rainCount] = RAIN_SPEED * (0.85f + rnd.nextFloat() * 0.3f);
            rainCount++;
        }
    }

    private void updateSnow(float dt, float eyeX, float eyeY, float eyeZ, int target) {
        int w = 0;
        for (int i = 0; i < snowCount; i++) {
            snowAge[i] += dt;
            snowPhase[i] += dt * 2f;
            snowY[i] -= snowSpeed[i] * dt;
            snowX[i] += (float) Math.sin(snowPhase[i]) * SNOW_SWAY * dt;
            snowZ[i] += (float) Math.cos(snowPhase[i] * 1.3f) * SNOW_SWAY * dt;
            if (snowY[i] >= eyeY - KILL_BELOW && snowAge[i] < SNOW_LIFE) {
                snowX[w] = snowX[i];
                snowY[w] = snowY[i];
                snowZ[w] = snowZ[i];
                snowSpeed[w] = snowSpeed[i];
                snowPhase[w] = snowPhase[i];
                snowAge[w] = snowAge[i];
                w++;
            }
        }
        snowCount = w;
        int capped = Math.min(target, MAX_SNOW);
        while (snowCount < capped) {
            snowX[snowCount] = eyeX + (rnd.nextFloat() * 2f - 1f) * BOX_RADIUS;
            snowY[snowCount] = eyeY + SPAWN_TOP * (0.6f + rnd.nextFloat() * 0.6f);
            snowZ[snowCount] = eyeZ + (rnd.nextFloat() * 2f - 1f) * BOX_RADIUS;
            snowSpeed[snowCount] = SNOW_SPEED * (0.7f + rnd.nextFloat() * 0.6f);
            snowPhase[snowCount] = rnd.nextFloat() * 6.28f;
            snowAge[snowCount] = 0f;
            snowCount++;
        }
    }

    /**
     * Writes rain as {@code GL_LINES} endpoints (two vertices per drop) into
     * {@code out}; returns the number of drops drawn. Rain is world-vertical, so
     * it looks right from any viewing angle without billboarding.
     */
    public int writeRain(FloatArray out) {
        for (int i = 0; i < rainCount; i++) {
            out.add(rainX[i]);
            out.add(rainY[i]);
            out.add(rainZ[i]);
            out.add(rainX[i]);
            out.add(rainY[i] - RAIN_LENGTH);
            out.add(rainZ[i]);
        }
        return rainCount;
    }

    /**
     * Writes snow as {@code GL_TRIANGLES}: each flake is two small crossed planes
     * (facing +Z and +X), so a drifting speck is visible from any angle. Returns
     * the number of flakes drawn.
     */
    public int writeSnow(FloatArray out) {
        float half = 0.06f;
        for (int i = 0; i < snowCount; i++) {
            float x = snowX[i], y = snowY[i], z = snowZ[i];
            // Facing +Z.
            addQuad(out, x - half, y - half, z, x + half, y - half, z, x + half, y + half, z, x - half, y + half, z);
            // Facing +X.
            addQuad(out, x, y - half, z - half, x, y - half, z + half, x, y + half, z + half, x, y + half, z - half);
        }
        return snowCount;
    }

    private static void addQuad(FloatArray out, float x0, float y0, float z0,
                                float x1, float y1, float z1, float x2, float y2, float z2,
                                float x3, float y3, float z3) {
        out.add(x0);
        out.add(y0);
        out.add(z0);
        out.add(x1);
        out.add(y1);
        out.add(z1);
        out.add(x2);
        out.add(y2);
        out.add(z2);
        out.add(x0);
        out.add(y0);
        out.add(z0);
        out.add(x2);
        out.add(y2);
        out.add(z2);
        out.add(x3);
        out.add(y3);
        out.add(z3);
    }
}
