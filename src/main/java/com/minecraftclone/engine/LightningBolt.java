package com.minecraftclone.engine;

import com.minecraftclone.util.FloatArray;

import java.util.Random;

/**
 * A short-lived jagged lightning bolt, generated on strike and rendered as
 * {@code GL_LINES} by {@code WeatherRenderer} (a few thin, bright, depth-tested
 * polylines that flare out and die over a fraction of a second - see
 * {@link #update}). The geometry is purely cosmetic: the strike's gameplay
 * effects (fire, mob damage) are applied by {@code World.strikeLightning}.
 */
public class LightningBolt {

    /** How long a bolt stays visible (the flash it belongs to lasts about as long). */
    private static final float BOLT_LIFE = 0.35f;
    /** How much the trunk's midpoint may wander sideways from its endpoints. */
    private static final float TRUNK_SPREAD = 3.5f;

    private final FloatArray segments = new FloatArray(512);
    private float life = BOLT_LIFE;

    /**
     * Builds a bolt from a point in the sky ({@code start}) down to the strike
     * target ({@code end}). The trunk's horizontal jitter tapers toward the
     * ground so the bolt always lands exactly on the strike point, plus a couple
     * of short forks off the trunk for the classic look.
     */
    public LightningBolt(float startX, float startY, float startZ,
                         float endX, float endY, float endZ, Random rnd) {
        int steps = 9;
        float[] px = new float[steps + 1];
        float[] py = new float[steps + 1];
        float[] pz = new float[steps + 1];
        for (int i = 0; i <= steps; i++) {
            float t = (float) i / steps;
            float falloff = 1f - t;
            px[i] = lerp(startX, endX, t) + (rnd.nextFloat() - 0.5f) * TRUNK_SPREAD * falloff;
            py[i] = lerp(startY, endY, t);
            pz[i] = lerp(startZ, endZ, t) + (rnd.nextFloat() - 0.5f) * TRUNK_SPREAD * falloff;
        }
        // Pin both ends to their exact targets so the bolt starts in the sky and
        // lands on the strike point.
        px[0] = startX;
        py[0] = startY;
        pz[0] = startZ;
        px[steps] = endX;
        py[steps] = endY;
        pz[steps] = endZ;
        addPath(px, py, pz);

        for (int fork = 0; fork < 2; fork++) {
            int at = 2 + rnd.nextInt(steps - 2);
            int len = 2 + rnd.nextInt(3);
            float bx = px[at], by = py[at], bz = pz[at];
            for (int j = 0; j < len; j++) {
                float startX = bx, startY = by, startZ = bz;
                bx += (rnd.nextFloat() - 0.5f) * 1.8f;
                by -= 2.2f;
                bz += (rnd.nextFloat() - 0.5f) * 1.8f;
                segments.add(startX);
                segments.add(startY);
                segments.add(startZ);
                segments.add(bx);
                segments.add(by);
                segments.add(bz);
            }
        }
    }

    private void addPath(float[] px, float[] py, float[] pz) {
        for (int i = 0; i < px.length - 1; i++) {
            segments.add(px[i]);
            segments.add(py[i]);
            segments.add(pz[i]);
            segments.add(px[i + 1]);
            segments.add(py[i + 1]);
            segments.add(pz[i + 1]);
        }
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    public void update(float dt) {
        life -= dt;
    }

    public boolean isAlive() {
        return life > 0f;
    }

    /** Writes the bolt's {@code GL_LINES} endpoints into {@code out}. */
    public void write(FloatArray out) {
        for (int i = 0; i < segments.size(); i++) {
            out.add(segments.get(i));
        }
    }
}
