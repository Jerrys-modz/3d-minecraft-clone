package com.minecraftclone.engine.audio;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SoundSynthTest {

    @Test
    void toneLengthMatchesDurationAtTheSampleRate() {
        short[] s = SoundSynth.tone(440, 440, 0.5f, 1f, 1f);
        assertEquals((int) (0.5f * SoundSynth.SAMPLE_RATE), s.length);
    }

    @Test
    void noiseLengthMatchesDurationAtTheSampleRate() {
        short[] s = SoundSynth.noise(1L, 0.25f, 1f, 1f, 1f);
        assertEquals((int) (0.25f * SoundSynth.SAMPLE_RATE), s.length);
    }

    @Test
    void toneEnvelopeDecaysFromStartAmpToEndAmp() {
        // A fixed-frequency tone at a quarter-cycle-aligned duration samples
        // near its peak early and late, so comparing magnitude near the start
        // vs. the end is a reliable check of the amplitude ramp actually
        // being applied (not just of where the sine happens to be).
        short[] s = SoundSynth.tone(1000, 1000, 1f, 1f, 0f);
        int early = Math.abs(s[SoundSynth.SAMPLE_RATE / 4000]); // ~0.25ms in - near a peak
        int late = Math.abs(s[s.length - SoundSynth.SAMPLE_RATE / 4000 - 1]);
        assertTrue(early > late, "amplitude should decay toward the end: early=" + early + " late=" + late);
    }

    @Test
    void noiseIsDeterministicForTheSameSeed() {
        short[] a = SoundSynth.noise(42L, 0.05f, 1f, 1f, 0.5f);
        short[] b = SoundSynth.noise(42L, 0.05f, 1f, 1f, 0.5f);
        assertArrayEquals(a, b);
    }

    @Test
    void noiseDiffersForDifferentSeeds() {
        short[] a = SoundSynth.noise(1L, 0.05f, 1f, 1f, 0.5f);
        short[] b = SoundSynth.noise(2L, 0.05f, 1f, 1f, 0.5f);
        boolean anyDifferent = false;
        for (int i = 0; i < a.length; i++) {
            if (a[i] != b[i]) {
                anyDifferent = true;
                break;
            }
        }
        assertTrue(anyDifferent, "different seeds should produce different noise");
    }

    @Test
    void silenceIsAllZerosAtTheRequestedLength() {
        short[] s = SoundSynth.silence(0.1f);
        assertEquals((int) (0.1f * SoundSynth.SAMPLE_RATE), s.length);
        for (short v : s) {
            assertEquals(0, v);
        }
    }

    @Test
    void concatPreservesOrderAndTotalLength() {
        short[] a = SoundSynth.tone(200, 200, 0.01f, 1f, 1f);
        short[] b = SoundSynth.tone(400, 400, 0.01f, 1f, 1f);
        short[] combined = SoundSynth.concat(a, b);
        assertEquals(a.length + b.length, combined.length);
        assertArrayEquals(a, java.util.Arrays.copyOfRange(combined, 0, a.length));
        assertArrayEquals(b, java.util.Arrays.copyOfRange(combined, a.length, combined.length));
    }

    @Test
    void mixAddsLayersAndClampsToTheValidSampleRange() {
        // Two loud in-phase tones summed should saturate at Short.MAX_VALUE,
        // not overflow/wrap around into negative territory.
        short[] loud1 = SoundSynth.tone(1000, 1000, 0.01f, 1f, 1f);
        short[] loud2 = SoundSynth.tone(1000, 1000, 0.01f, 1f, 1f);
        short[] mixedMax = SoundSynth.mix(loud1, loud2);

        short maxFound = Short.MIN_VALUE;
        for (short v : mixedMax) {
            if (v > maxFound) maxFound = v;
        }
        assertEquals(Short.MAX_VALUE, maxFound, "mixing should saturate at Short.MAX_VALUE");

        // Create inverted tones to produce negative saturation.
        short[] inverted1 = SoundSynth.scale(loud1, -1f);
        short[] inverted2 = SoundSynth.scale(loud2, -1f);
        short[] mixedMin = SoundSynth.mix(inverted1, inverted2);

        short minFound = Short.MAX_VALUE;
        for (short v : mixedMin) {
            if (v < minFound) minFound = v;
        }
        assertEquals(Short.MIN_VALUE, minFound, "mixing should saturate at Short.MIN_VALUE");
    }

    @Test
    void bubblesLengthMatchesDurationAtTheSampleRate() {
        short[] s = SoundSynth.bubbles(7L, 0.3f, 5, 0.5f);
        assertEquals((int) (0.3f * SoundSynth.SAMPLE_RATE), s.length);
    }

    @Test
    void bubblesIsDeterministicForTheSameSeed() {
        short[] a = SoundSynth.bubbles(7L, 0.3f, 5, 0.5f);
        short[] b = SoundSynth.bubbles(7L, 0.3f, 5, 0.5f);
        assertArrayEquals(a, b);
    }

    @Test
    void bubblesDiffersForDifferentSeeds() {
        short[] a = SoundSynth.bubbles(1L, 0.3f, 5, 0.5f);
        short[] b = SoundSynth.bubbles(2L, 0.3f, 5, 0.5f);
        assertTrue(!java.util.Arrays.equals(a, b), "different seeds should produce different bubble timing/pitch");
    }

    @Test
    void bubblesIsMostlySilentBetweenTheChirps() {
        // Only a handful of short (~0.03-0.08s) chirps scattered across the
        // window - most samples should be exactly the silence they're mixed
        // over, not a continuous tone/wash like tone()/noise() produce.
        short[] s = SoundSynth.bubbles(3L, 0.5f, 4, 0.8f);
        int silentCount = 0;
        for (short v : s) {
            if (v == 0) silentCount++;
        }
        assertTrue(silentCount > s.length / 2, "expected most of the buffer to be silent between chirps");
    }

    @Test
    void scaleMultipliesAmplitudeProportionally() {
        short[] s = SoundSynth.tone(1000, 1000, 0.01f, 1f, 1f);
        short[] half = SoundSynth.scale(s, 0.5f);
        assertEquals(s.length, half.length);
        for (int i = 0; i < s.length; i++) {
            assertEquals(Math.round(s[i] * 0.5f), half[i], 1);
        }
    }

    @Test
    void bubblesLengthMatchesRequestedDurationEvenWhenShorterThanSingleChirp() {
        // Regression test: when the requested duration is shorter than 0.03s
        // (the minimum chirp duration), the bubbles method should still
        // return a buffer matching the requested duration, not overflow.
        short[] s = SoundSynth.bubbles(42L, 0.02f, 3, 0.5f);
        assertEquals((int) (0.02f * SoundSynth.SAMPLE_RATE), s.length,
                "bubbles should return exactly the requested duration even when shorter than a chirp");
    }
}
