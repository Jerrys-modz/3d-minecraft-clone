package com.minecraftclone.player;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DoubleTapDetectorTest {

    @Test
    void singlePressIsNotADoubleTap() {
        DoubleTapDetector d = new DoubleTapDetector(0.3f);
        assertFalse(d.tick(0.016f, true));
    }

    @Test
    void twoQuickPressesAreADoubleTap() {
        DoubleTapDetector d = new DoubleTapDetector(0.3f);
        assertFalse(d.tick(0.016f, true));  // 1st press
        assertFalse(d.tick(0.1f, false));   // still held/idle frames in between
        assertTrue(d.tick(0.05f, true));    // 2nd press, well within the window
    }

    @Test
    void tooSlowIsNotADoubleTap() {
        DoubleTapDetector d = new DoubleTapDetector(0.3f);
        assertFalse(d.tick(0.016f, true));  // 1st press
        assertFalse(d.tick(0.5f, false));   // gap exceeds the window before the 2nd press
        assertFalse(d.tick(0.016f, true));  // 2nd press - too late
    }

    @Test
    void thirdPressMeasuresFromTheSecondNotTheFirst() {
        DoubleTapDetector d = new DoubleTapDetector(0.3f);
        assertFalse(d.tick(0.016f, true));  // 1st press
        assertTrue(d.tick(0.29f, true));    // 2nd press, just inside the window -> a double-tap
        // A slow 3rd press should NOT register as another double-tap just because
        // the 1st press was long ago - it's measured from the 2nd press instead.
        assertFalse(d.tick(0.5f, false));
        assertFalse(d.tick(0.016f, true));
    }

    @Test
    void idleTicksWithNoPressNeverReportADoubleTap() {
        DoubleTapDetector d = new DoubleTapDetector(0.3f);
        for (int i = 0; i < 10; i++) {
            assertFalse(d.tick(0.016f, false));
        }
    }
}
