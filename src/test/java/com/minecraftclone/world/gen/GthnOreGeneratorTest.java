package com.minecraftclone.world.gen;

import com.minecraftclone.world.BlockType;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * GTNH veins must sit on the 3-chunk lattice ({@code |cx|%3==1, |cz|%3==1})
 * with exactly one mix per ore chunk. The previous generator hashed a centre
 * eight chunks *outside* its 3×3 cell, so veins never actually spawned.
 */
class GthnOreGeneratorTest {

    private static GthnOreGenerator gen() {
        return new GthnOreGenerator(12345L, 40);
    }

    @Test
    void oreChunksFollowTheGtnhLattice() {
        assertEquals(3, GthnOreGenerator.VEIN_SPACING);
        assertTrue(GthnOreGenerator.isOreChunk(1, 1));
        assertTrue(GthnOreGenerator.isOreChunk(4, 1));
        assertTrue(GthnOreGenerator.isOreChunk(1, 4));
        assertTrue(GthnOreGenerator.isOreChunk(-1, -1));
        assertTrue(GthnOreGenerator.isOreChunk(-1, 1));
        assertTrue(GthnOreGenerator.isOreChunk(7, 4));

        assertFalse(GthnOreGenerator.isOreChunk(0, 0), "origin is not an ore chunk");
        assertFalse(GthnOreGenerator.isOreChunk(1, 0));
        assertFalse(GthnOreGenerator.isOreChunk(0, 1));
        assertFalse(GthnOreGenerator.isOreChunk(2, 1));
        assertFalse(GthnOreGenerator.isOreChunk(1, 2));
        assertFalse(GthnOreGenerator.isOreChunk(3, 3));
        assertFalse(GthnOreGenerator.isOreChunk(2, 2));
    }

    @Test
    void latticeIsOreThenTwoEmptyAlongAnAxis() {
        // Away from 0: chunks 1, 4, 7, 10 are ore (z fixed at 1).
        int ores = 0;
        int prevOre = Integer.MIN_VALUE;
        for (int cx = 1; cx <= 20; cx++) {
            boolean ore = GthnOreGenerator.isOreChunk(cx, 1);
            if (ore) {
                ores++;
                if (prevOre != Integer.MIN_VALUE) {
                    assertEquals(3, cx - prevOre, "ore chunks must be 3 apart, got " + cx + " after " + prevOre);
                }
                prevOre = cx;
            }
        }
        assertEquals(7, ores, "cx 1..20 at cz=1 should be 1,4,7,10,13,16,19");
    }

    @Test
    void everyOreChunkGetsAMixAndADenseVein() {
        GthnOreGenerator g = gen();
        int chunksWithVein = 0;
        for (int cx = -7; cx <= 7; cx++) {
            for (int cz = -7; cz <= 7; cz++) {
                if (!GthnOreGenerator.isOreChunk(cx, cz)) {
                    assertNull(g.mixForOreChunk(cx, cz));
                    continue;
                }
                GthnOreGenerator.MixInfo mix = g.mixForOreChunk(cx, cz);
                assertNotNull(mix, "ore chunk (" + cx + "," + cz + ") must pick a mix");
                int veinBlocks = countVeinOres(g, cx, cz);
                assertTrue(veinBlocks >= 200,
                        "ore chunk (" + cx + "," + cz + ") mix=" + mix.name
                                + " should be a dense cuboid, got " + veinBlocks);
                chunksWithVein++;
            }
        }
        assertTrue(chunksWithVein >= 16, "expected many ore chunks in ±7, got " + chunksWithVein);
    }

    @Test
    void aVeinIsOneMixNotAPileOfEveryOre() {
        GthnOreGenerator g = gen();
        int cx = 1, cz = 1;
        GthnOreGenerator.MixInfo mix = g.mixForOreChunk(cx, cz);
        assertNotNull(mix);
        Set<BlockType> allowed = new HashSet<>();
        for (BlockType t : mix.composition) allowed.add(t);

        Set<BlockType> seen = new HashSet<>();
        int originX = cx * 16;
        int originZ = cz * 16;
        for (int x = originX; x < originX + 16; x++) {
            for (int z = originZ; z < originZ + 16; z++) {
                for (int y = 1; y < 96; y++) {
                    BlockType b = g.oreAt(x, y, z);
                    if (b == BlockType.STONE) continue;
                    if (b.name().startsWith("SMALL_")) continue;
                    seen.add(b);
                    assertTrue(allowed.contains(b),
                            b + " is not part of " + mix.name + " " + mix.compositionLabel());
                }
            }
        }
        assertFalse(seen.isEmpty(), "vein must place at least one full-size ore");
    }

    @Test
    void nonOreChunksAreNotFullOfVeinOre() {
        GthnOreGenerator g = gen();
        // Chunk (3,3) is two steps from (1,1) and from (4,4); too far for a 16–31 cuboid.
        assertFalse(GthnOreGenerator.isOreChunk(3, 3));
        int veinBlocks = countVeinOres(g, 3, 3);
        assertTrue(veinBlocks < 30,
                "non-ore chunk (3,3) should not host a vein, got " + veinBlocks);
        assertTrue(countVeinOres(g, 1, 1) > veinBlocks * 10,
                "the neighbouring ore chunk must dwarf the empty one");
    }

    @Test
    void mixForOreChunkIsDeterministic() {
        GthnOreGenerator a = new GthnOreGenerator(99L, 40);
        GthnOreGenerator b = new GthnOreGenerator(99L, 40);
        GthnOreGenerator c = new GthnOreGenerator(100L, 40);
        assertEquals(a.mixForOreChunk(1, 1).name, b.mixForOreChunk(1, 1).name);
        // Different seed is allowed to collide but usually won't; just lock same-seed.
        assertNotNull(c.mixForOreChunk(1, 1));
    }

    private static int countVeinOres(GthnOreGenerator g, int cx, int cz) {
        int n = 0;
        int originX = cx * 16;
        int originZ = cz * 16;
        for (int x = originX; x < originX + 16; x++) {
            for (int z = originZ; z < originZ + 16; z++) {
                for (int y = 1; y < 96; y++) {
                    BlockType b = g.oreAt(x, y, z);
                    if (b != BlockType.STONE && !b.name().startsWith("SMALL_")
                            && GthnOreGenerator.mixInfo(b) != null) {
                        n++;
                    }
                }
            }
        }
        return n;
    }
}
