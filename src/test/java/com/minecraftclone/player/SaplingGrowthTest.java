package com.minecraftclone.player;

import com.minecraftclone.world.BlockType;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for sapling and new tree-type block helpers.
 *
 * <p>Tests that require a full {@code World} (cross-chunk tree placement) are
 * deferred to the integration-level headless run; the tests here focus on
 * {@link BlockType} predicate correctness and the 2×2 jungle-sapling detection
 * algorithm, which is extracted behind a {@link Farming.BlockGet} so it can be
 * exercised against an in-memory grid.
 */
class SaplingGrowthTest {

    // -----------------------------------------------------------------------
    // Minimal in-memory block grid
    // -----------------------------------------------------------------------

    private static final class Cells {
        final Map<Long, BlockType> m = new HashMap<>();

        static long key(int x, int y, int z) {
            // Coordinates are shifted to avoid sign-bit collisions.
            return ((long)(x + 65536) << 44) | ((long)(y + 65536) << 22) | (z + 65536);
        }

        BlockType get(int x, int y, int z) {
            return m.getOrDefault(key(x, y, z), BlockType.AIR);
        }

        void set(int x, int y, int z, BlockType t) {
            if (t == BlockType.AIR) m.remove(key(x, y, z));
            else m.put(key(x, y, z), t);
        }
    }

    // -----------------------------------------------------------------------
    // BlockType.isSapling()
    // -----------------------------------------------------------------------

    @Test
    void isSaplingTrueForAllFiveTypes() {
        assertTrue(BlockType.OAK_SAPLING.isSapling());
        assertTrue(BlockType.BIRCH_SAPLING.isSapling());
        assertTrue(BlockType.JUNGLE_SAPLING.isSapling());
        assertTrue(BlockType.PINE_SAPLING.isSapling());
        assertTrue(BlockType.CHERRY_SAPLING.isSapling());
    }

    @Test
    void isSaplingFalseForUnrelatedBlocks() {
        assertFalse(BlockType.DIRT.isSapling());
        assertFalse(BlockType.GRASS.isSapling());
        assertFalse(BlockType.LEAVES.isSapling());
        assertFalse(BlockType.WOOD_LOG.isSapling());
        assertFalse(BlockType.TALL_GRASS.isSapling());
        assertFalse(BlockType.AIR.isSapling());
        assertFalse(BlockType.STONE.isSapling());
    }

    // -----------------------------------------------------------------------
    // BlockType.isLog()
    // -----------------------------------------------------------------------

    @Test
    void isLogTrueForAllFiveLogTypes() {
        assertTrue(BlockType.WOOD_LOG.isLog());
        assertTrue(BlockType.BIRCH_LOG.isLog());
        assertTrue(BlockType.JUNGLE_LOG.isLog());
        assertTrue(BlockType.PINE_LOG.isLog());
        assertTrue(BlockType.CHERRY_LOG.isLog());
    }

    @Test
    void isLogFalseForPlanksLeafOrStone() {
        assertFalse(BlockType.PLANKS.isLog());
        assertFalse(BlockType.LEAVES.isLog());
        assertFalse(BlockType.STONE.isLog());
        assertFalse(BlockType.BIRCH_LEAVES.isLog());
    }

    // -----------------------------------------------------------------------
    // BlockType.isLeaves()
    // -----------------------------------------------------------------------

    @Test
    void isLeavesTrueForAllLeafVariants() {
        assertTrue(BlockType.LEAVES.isLeaves());
        assertTrue(BlockType.CHERRY_LEAVES.isLeaves());
        assertTrue(BlockType.BIRCH_LEAVES.isLeaves());
        assertTrue(BlockType.JUNGLE_LEAVES.isLeaves());
        assertTrue(BlockType.PINE_LEAVES.isLeaves());
    }

    @Test
    void isLeavesFalseForLogsAndOtherBlocks() {
        assertFalse(BlockType.WOOD_LOG.isLeaves());
        assertFalse(BlockType.BIRCH_LOG.isLeaves());
        assertFalse(BlockType.JUNGLE_LOG.isLeaves());
        assertFalse(BlockType.PINE_LOG.isLeaves());
        assertFalse(BlockType.DIRT.isLeaves());
        assertFalse(BlockType.AIR.isLeaves());
    }

    // -----------------------------------------------------------------------
    // 2×2 jungle-sapling detection
    // -----------------------------------------------------------------------

    @Test
    void jungleDetectionFindsCornerWhenAllFourPresent() {
        // Saplings at (0,5,0), (1,5,0), (0,5,1), (1,5,1).
        Cells w = new Cells();
        w.set(0, 5, 0, BlockType.JUNGLE_SAPLING);
        w.set(1, 5, 0, BlockType.JUNGLE_SAPLING);
        w.set(0, 5, 1, BlockType.JUNGLE_SAPLING);
        w.set(1, 5, 1, BlockType.JUNGLE_SAPLING);

        // Any of the four cells should find the SW corner (0, 1=z→0, …) = [0, 0].
        int[] sw = Farming.find2x2JungleSWCorner(w::get, 0, 5, 0);
        assertNotNull(sw, "should detect 2×2 from NW corner");
        assertArrayEquals(new int[]{0, 0}, sw);

        sw = Farming.find2x2JungleSWCorner(w::get, 1, 5, 1);
        assertNotNull(sw, "should detect 2×2 from SE corner");
        assertArrayEquals(new int[]{0, 0}, sw);

        sw = Farming.find2x2JungleSWCorner(w::get, 1, 5, 0);
        assertNotNull(sw, "should detect 2×2 from NE corner");
        assertArrayEquals(new int[]{0, 0}, sw);

        sw = Farming.find2x2JungleSWCorner(w::get, 0, 5, 1);
        assertNotNull(sw, "should detect 2×2 from SW corner");
        assertArrayEquals(new int[]{0, 0}, sw);
    }

    @Test
    void jungleDetectionReturnsNullForSingleSapling() {
        Cells w = new Cells();
        w.set(0, 5, 0, BlockType.JUNGLE_SAPLING);
        assertNull(Farming.find2x2JungleSWCorner(w::get, 0, 5, 0),
                "lone sapling must not trigger 2×2 growth");
    }

    @Test
    void jungleDetectionReturnsNullForThreeSaplings() {
        Cells w = new Cells();
        w.set(0, 5, 0, BlockType.JUNGLE_SAPLING);
        w.set(1, 5, 0, BlockType.JUNGLE_SAPLING);
        w.set(0, 5, 1, BlockType.JUNGLE_SAPLING);
        // Missing (1,5,1).
        assertNull(Farming.find2x2JungleSWCorner(w::get, 0, 5, 0),
                "incomplete 2×2 must not trigger large-tree growth");
    }

    @Test
    void jungleDetectionIgnoresWrongBlockType() {
        Cells w = new Cells();
        w.set(0, 5, 0, BlockType.JUNGLE_SAPLING);
        w.set(1, 5, 0, BlockType.JUNGLE_SAPLING);
        w.set(0, 5, 1, BlockType.JUNGLE_SAPLING);
        w.set(1, 5, 1, BlockType.OAK_SAPLING); // wrong type
        assertNull(Farming.find2x2JungleSWCorner(w::get, 0, 5, 0),
                "mixed sapling types must not count as a 2×2");
    }

    @Test
    void jungleDetectionFindsOffsetCorner() {
        // Saplings placed at (10,20,30) through (11,20,31).
        Cells w = new Cells();
        w.set(10, 20, 30, BlockType.JUNGLE_SAPLING);
        w.set(11, 20, 30, BlockType.JUNGLE_SAPLING);
        w.set(10, 20, 31, BlockType.JUNGLE_SAPLING);
        w.set(11, 20, 31, BlockType.JUNGLE_SAPLING);

        int[] sw = Farming.find2x2JungleSWCorner(w::get, 11, 20, 31);
        assertNotNull(sw);
        assertArrayEquals(new int[]{10, 30}, sw);
    }

    // -----------------------------------------------------------------------
    // BlockType unique IDs and cross-sapling uniqueness
    // -----------------------------------------------------------------------

    @Test
    void saplingBlockIdsAreDistinct() {
        int[] ids = {
            BlockType.OAK_SAPLING.id,
            BlockType.BIRCH_SAPLING.id,
            BlockType.JUNGLE_SAPLING.id,
            BlockType.PINE_SAPLING.id,
            BlockType.CHERRY_SAPLING.id,
        };
        for (int i = 0; i < ids.length; i++) {
            for (int j = i + 1; j < ids.length; j++) {
                assertNotEquals(ids[i], ids[j],
                        "sapling IDs must all be unique");
            }
        }
    }

    @Test
    void newLogBlockIdsAreDistinctFromEachOther() {
        int[] ids = {
            BlockType.WOOD_LOG.id,
            BlockType.BIRCH_LOG.id,
            BlockType.JUNGLE_LOG.id,
            BlockType.PINE_LOG.id,
            BlockType.CHERRY_LOG.id,
        };
        for (int i = 0; i < ids.length; i++) {
            for (int j = i + 1; j < ids.length; j++) {
                assertNotEquals(ids[i], ids[j], "log IDs must all be unique");
            }
        }
    }
}
