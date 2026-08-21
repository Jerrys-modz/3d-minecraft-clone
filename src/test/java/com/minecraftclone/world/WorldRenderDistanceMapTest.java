package com.minecraftclone.world;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The map fills the same Chebyshev square chunk streaming uses, not a
 * hardcoded 2-chunk ring.
 */
class WorldRenderDistanceMapTest {

    @Test
    void renderDistanceSixCoversAThirteenByThirteenSquare() {
        assertRing(0, 0, 6, 13 * 13);
    }

    @Test
    void renderDistanceThreeCoversASevenBySevenSquare() {
        assertRing(4, -2, 3, 7 * 7);
    }

    @Test
    void everyVisitedChunkIsInsideTheSquare() {
        int pcx = 10, pcz = -3, rd = 4;
        World.visitRenderDistanceRing(pcx, pcz, rd, (cx, cz) -> {
            int chebyshev = Math.max(Math.abs(cx - pcx), Math.abs(cz - pcz));
            assertTrue(chebyshev <= rd, cx + "," + cz + " outside rd " + rd);
            return true;
        });
    }

    private static void assertRing(int pcx, int pcz, int rd, int expected) {
        Set<Long> seen = new HashSet<>();
        int visited = World.visitRenderDistanceRing(pcx, pcz, rd, (cx, cz) -> {
            assertTrue(seen.add(MapData.encodeChunkKey(cx, cz)),
                    "duplicate " + cx + "," + cz);
            return true;
        });
        assertEquals(expected, visited);
        assertEquals(expected, seen.size());
    }
}
