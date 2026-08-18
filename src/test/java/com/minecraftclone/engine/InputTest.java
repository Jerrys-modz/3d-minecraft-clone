package com.minecraftclone.engine;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Input's gamepad polling needs a live GLFW window/controller and so can't run
 * headless, but the two small math helpers it uses to interpret raw stick/trigger
 * axes are pure and package-private specifically so they can be checked here
 * without one - see {@link Input#updateGamepad}.
 */
class InputTest {

    @Test
    void deadzoneZeroesOutValuesInsideIt() {
        assertEquals(0f, Input.applyDeadzone(0.1f, 0.22f));
        assertEquals(0f, Input.applyDeadzone(-0.2f, 0.22f));
        assertEquals(0f, Input.applyDeadzone(0f, 0.22f));
    }

    @Test
    void deadzonePassesThroughValuesOutsideIt() {
        assertEquals(0.5f, Input.applyDeadzone(0.5f, 0.22f));
        assertEquals(-0.9f, Input.applyDeadzone(-0.9f, 0.22f));
    }

    @Test
    void deadzoneBoundaryPassesThrough() {
        // A value exactly at the threshold is *not* inside it (< not <=) - the
        // deadzone only exists to swallow noise strictly below the threshold, so
        // the boundary value itself should still register as real input.
        assertEquals(0.22f, Input.applyDeadzone(0.22f, 0.22f));
    }

    @Test
    void triggerReleasedMapsToZero() {
        // GLFW/SDL convention: an untouched trigger axis reads -1.
        assertEquals(0f, Input.normalizeTrigger(-1f), 1e-6f);
    }

    @Test
    void triggerFullyPressedMapsToOne() {
        assertEquals(1f, Input.normalizeTrigger(1f), 1e-6f);
    }

    @Test
    void triggerHalfwayMapsToOneHalf() {
        assertEquals(0.5f, Input.normalizeTrigger(0f), 1e-6f);
    }

    @Test
    void triggerClampsOutOfRangeInput() {
        // Some drivers report a hair outside -1..1 - shouldn't produce a
        // negative or >1 "pressed amount".
        assertEquals(0f, Input.normalizeTrigger(-1.2f), 1e-6f);
        assertEquals(1f, Input.normalizeTrigger(1.2f), 1e-6f);
    }
}
