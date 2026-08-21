package com.minecraftclone.player;

import com.minecraftclone.world.BlockType;
import org.joml.Vector3i;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Hold-to-break progress and the in-progress hit pulses that drive the
 * punch sound / arm swing. GPU-free.
 */
class MiningControllerTest {

    @Test
    void firstTickWhileMiningFiresAHit() {
        MiningController m = new MiningController();
        Vector3i p = new Vector3i(1, 2, 3);
        float f = m.update(p, BlockType.STONE, (BlockType) null, true, 0.05f);
        assertTrue(f > 0f && f < 1f);
        assertTrue(m.pollHit(), "the first punch lands as soon as you start mining");
        assertFalse(m.pollHit(), "pollHit is consumed so the same pulse does not replay");
    }

    @Test
    void hitsRepeatOnTheIntervalNotEveryFrame() {
        MiningController m = new MiningController();
        Vector3i p = new Vector3i(0, 0, 0);
        m.update(p, BlockType.STONE, (BlockType) null, true, 0.05f);
        assertTrue(m.pollHit());
        m.update(p, BlockType.STONE, (BlockType) null, true, 0.05f);
        assertFalse(m.pollHit(), "still inside the first interval");
        m.update(p, BlockType.STONE, (BlockType) null, true, MiningController.HIT_INTERVAL_SECONDS);
        assertTrue(m.pollHit(), "crossing the interval fires the next punch");
    }

    @Test
    void releasingClearsHits() {
        MiningController m = new MiningController();
        m.update(new Vector3i(0, 0, 0), BlockType.DIRT, (BlockType) null, true, 0.05f);
        m.update(null, null, (BlockType) null, false, 0.05f);
        assertFalse(m.pollHit());
    }

    @Test
    void finishingTheBreakDoesNotAlsoHit() {
        MiningController m = new MiningController();
        Vector3i p = new Vector3i(0, 0, 0);
        float f = 0f;
        for (int i = 0; i < 40; i++) {
            f = m.update(p, BlockType.DIRT, (BlockType) null, true, 0.2f);
            if (f >= 1f) break;
        }
        assertTrue(f >= 1f);
        assertFalse(m.pollHit(), "the break sound covers the last frame");
    }
}
