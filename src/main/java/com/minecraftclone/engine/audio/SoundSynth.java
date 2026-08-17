package com.minecraftclone.engine.audio;

import java.util.Random;

/**
 * Tiny waveform synthesizer: builds short mono 16-bit PCM buffers from a
 * handful of primitives (a frequency-sweeping tone, a filtered noise burst)
 * that {@link Sounds} combines into every sound effect in the game.
 * <p>
 * Following the project's "fully self-contained, nothing downloaded at
 * runtime" rule (see the block texture atlas, item icons and HUD font, all
 * generated the same way) - sound effects are synthesized once at startup
 * rather than shipped as recorded/sampled audio files, so there are no
 * licensing questions and no binary assets to keep in the repo. The result
 * is closer to a retro/8-bit blip-and-thud palette than a realistic sample
 * library, which - deliberately - suits a low-poly voxel game's aesthetic
 * rather than clashing with it.
 */
public final class SoundSynth {

    public static final int SAMPLE_RATE = 44100;

    private SoundSynth() {
    }

    /**
     * A sine tone sweeping linearly from {@code freqStartHz} to {@code freqEndHz}
     * over {@code durationSeconds}, with its amplitude ramped linearly from
     * {@code startAmp} to {@code endAmp} (0..1) across the same span - a
     * decaying (or rising) envelope so notes don't start/stop with an audible
     * click. Used for anything tonal: jumps, pops, dings, hurt/death cries.
     */
    public static short[] tone(float freqStartHz, float freqEndHz, float durationSeconds, float startAmp, float endAmp) {
        int n = Math.max(1, (int) (durationSeconds * SAMPLE_RATE));
        short[] samples = new short[n];
        double phase = 0;
        for (int i = 0; i < n; i++) {
            float t = (float) i / n;
            float freq = lerp(freqStartHz, freqEndHz, t);
            float amp = lerp(startAmp, endAmp, t);
            phase += 2 * Math.PI * freq / SAMPLE_RATE;
            samples[i] = toSample(Math.sin(phase) * amp);
        }
        return samples;
    }

    /**
     * White noise shaped by a one-pole low-pass filter (higher {@code brightness},
     * 0..1, passes more high frequency content - a "crack" instead of a "thud")
     * and a linear amplitude ramp, same as {@link #tone}. Used for anything
     * percussive/textural: block break/place/footsteps, splashes, chews.
     * {@code seed} makes it deterministic (and lets two calls with different
     * seeds sound like distinct "hits" of the same material instead of being
     * identical).
     */
    public static short[] noise(long seed, float durationSeconds, float startAmp, float endAmp, float brightness) {
        int n = Math.max(1, (int) (durationSeconds * SAMPLE_RATE));
        short[] samples = new short[n];
        Random rnd = new Random(seed);
        float filtered = 0f;
        float a = Math.max(0.02f, Math.min(1f, brightness));
        for (int i = 0; i < n; i++) {
            float t = (float) i / n;
            float amp = lerp(startAmp, endAmp, t);
            float white = rnd.nextFloat() * 2f - 1f;
            filtered += a * (white - filtered);
            samples[i] = toSample(filtered * amp);
        }
        return samples;
    }

    /**
     * A cluster of short, randomly-pitched, randomly-timed upward tone chirps
     * mixed together within a {@code durationSeconds} window - each chirp
     * reads as a single bubble breaking the surface (real bubbles rise in
     * pitch as they near the surface and pop). Neither {@link #tone} nor
     * {@link #noise} alone reads as specifically liquid - a tone sweep is
     * just a whistle and filtered noise is just a generic impact/wash - but
     * a handful of these overlapping "bloops" gives a splash or swim-stroke
     * sound the gurgle that's actually distinctive to water. {@code seed}
     * makes the whole cluster deterministic; {@code count} controls how
     * busy/dense it sounds and {@code amp} scales every chirp's peak volume.
     */
    public static short[] bubbles(long seed, float durationSeconds, int count, float amp) {
        Random rnd = new Random(seed);
        short[] out = silence(durationSeconds);
        for (int i = 0; i < count; i++) {
            float chirpDur = 0.03f + rnd.nextFloat() * 0.05f;
            float startFreq = 250f + rnd.nextFloat() * 500f;
            // Rising pitch: a bubble nearing the surface, not a random blip.
            float endFreq = startFreq + 300f + rnd.nextFloat() * 700f;
            // Clamp startT so the chirp fits entirely within the requested duration.
            float maxStartT = Math.max(0f, durationSeconds - chirpDur);
            float startT = rnd.nextFloat() * Math.max(0.0001f, maxStartT);
            float chirpAmp = amp * (0.6f + rnd.nextFloat() * 0.4f);
            short[] chirp = tone(startFreq, endFreq, chirpDur, chirpAmp, 0f);
            // Build the layer (silence + chirp), but truncate it to fit within out.length.
            short[] layer = concat(silence(startT), chirp);
            if (layer.length > out.length) {
                layer = java.util.Arrays.copyOf(layer, out.length);
            }
            out = mix(out, layer);
        }
        return out;
    }

    /** Silence, for spacing out notes in a {@link #concat}enated sound. */
    public static short[] silence(float durationSeconds) {
        return new short[Math.max(0, (int) (durationSeconds * SAMPLE_RATE))];
    }

    /** Uniformly scales a buffer's volume by {@code factor} (clamped to the valid sample range). */
    public static short[] scale(short[] samples, float factor) {
        short[] out = new short[samples.length];
        for (int i = 0; i < samples.length; i++) {
            out[i] = toSample(samples[i] * factor / Short.MAX_VALUE);
        }
        return out;
    }

    /** Sequential concatenation - one sound after another (a multi-note chime, a couple of chews). */
    public static short[] concat(short[]... parts) {
        int total = 0;
        for (short[] p : parts) total += p.length;
        short[] out = new short[total];
        int pos = 0;
        for (short[] p : parts) {
            System.arraycopy(p, 0, out, pos, p.length);
            pos += p.length;
        }
        return out;
    }

    /**
     * Additive mix of same-length-or-shorter layers (e.g. a tone plus a noise
     * texture) - shorter buffers just stop contributing early rather than
     * padding with silence. Clipped to the valid sample range.
     */
    public static short[] mix(short[]... layers) {
        int max = 0;
        for (short[] l : layers) max = Math.max(max, l.length);
        short[] out = new short[max];
        for (int i = 0; i < max; i++) {
            int sum = 0;
            for (short[] l : layers) {
                if (i < l.length) sum += l[i];
            }
            out[i] = clip(sum);
        }
        return out;
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private static short toSample(double value) {
        return clip((int) Math.round(value * Short.MAX_VALUE));
    }

    private static short clip(int value) {
        return (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, value));
    }
}
