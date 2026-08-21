package com.minecraftclone.engine.graphics;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Stage mapping is pure (no GL) so the overlay's 10 crack tiles line up
 * with hold-to-break progress.
 */
class BreakOverlayRendererTest {

    @Test
    void lookingAtABlockHasNoStage() {
        assertEquals(-1, BreakOverlayRenderer.stageIndex(0f));
        assertEquals(-1, BreakOverlayRenderer.stageIndex(-1f));
    }

    @Test
    void stagesClimbFromZeroToNine() {
        assertEquals(0, BreakOverlayRenderer.stageIndex(0.01f));
        assertEquals(0, BreakOverlayRenderer.stageIndex(0.09f));
        assertEquals(4, BreakOverlayRenderer.stageIndex(0.45f));
        assertEquals(9, BreakOverlayRenderer.stageIndex(0.99f));
        assertEquals(9, BreakOverlayRenderer.stageIndex(1f));
        assertEquals(9, BreakOverlayRenderer.stageIndex(2f));
    }
}
