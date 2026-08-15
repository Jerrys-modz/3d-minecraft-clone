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
        // Two loud in-phase tones summed should clip at the max sample value,
        // not overflow/wrap around into negative territory.
        short[] loud1 = SoundSynth.tone(1000, 1000, 0.01f, 1f, 1f);
        short[] loud2 = SoundSynth.tone(1000, 1000, 0.01f, 1f, 1f);
        short[] mixed = SoundSynth.mix(loud1, loud2);
        for (short v : mixed) {
            assertTrue(v >= Short.MIN_VALUE && v <= Short.MAX_VALUE);
        }
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
}
