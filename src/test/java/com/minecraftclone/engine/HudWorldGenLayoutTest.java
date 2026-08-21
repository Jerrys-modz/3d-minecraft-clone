package com.minecraftclone.engine;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Create New World lays out a label and a value on each row. A freshly rolled
 * seed is a full Java long (up to 20 characters) and used to run straight
 * through the "Seed" label on a narrow panel.
 */
class HudWorldGenLayoutTest {

    private static final float SIZE = 0.034f;
    private static final float EPS = 1e-5f;

    /** Matches {@code TextRenderer.measure}: each glyph is size plus 8% gap. */
    private static float measure(int chars, float size) {
        if (chars <= 0) return 0f;
        float gap = size * 0.08f;
        return chars * (size + gap) - gap;
    }

    @Test
    void shortValuesKeepTheDefaultSize() {
        float label = measure(4, SIZE);  // "Seed"
        float value = measure(8, SIZE);  // "(random)"
        assertEquals(SIZE, Hud.fitWorldGenValueSize(label, value, SIZE, Hud.worldGenInnerWidth()), EPS);
    }

    @Test
    void aFullLongSeedFitsBesideTheSeedLabel() {
        float label = measure(4, SIZE);   // "Seed"
        float value = measure(20, SIZE);  // "-9223372036854775808"
        float inner = Hud.worldGenInnerWidth();
        float size = Hud.fitWorldGenValueSize(label, value, SIZE, inner);
        float used = measure(20, size);
        assertTrue(label + 0.05f + used <= inner + EPS,
                "seed value must not run into the label: label=" + label + " value=" + used + " inner=" + inner);
        assertTrue(size > SIZE * 0.5f, "seed should stay readable, not shrink to a speckle");
    }

    @Test
    void anOverlongTypedValueShrinksInsteadOfOverlapping() {
        float label = measure(15, SIZE); // "Weeks per month"
        float value = measure(40, SIZE);
        float inner = Hud.worldGenInnerWidth();
        float size = Hud.fitWorldGenValueSize(label, value, SIZE, inner);
        assertTrue(size < SIZE);
        assertTrue(label + 0.05f + measure(40, size) <= inner + EPS);
    }
}
