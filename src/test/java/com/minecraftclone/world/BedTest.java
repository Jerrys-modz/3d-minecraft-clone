package com.minecraftclone.world;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BedTest {

    private static final class Stub implements BlockAccessor, Bed.BlockSetter {
        final Map<Long, BlockType> blocks = new HashMap<>();
        final Map<Long, Byte> orients = new HashMap<>();

        private static long k(int x, int y, int z) {
            return ((long) (x + 2048) << 40) | ((long) y << 32) | ((long) (z + 2048) << 20);
        }

        @Override
        public BlockType getBlock(int x, int y, int z) {
            return blocks.getOrDefault(k(x, y, z), BlockType.AIR);
        }

        @Override
        public byte getBlockOrientation(int x, int y, int z) {
            return orients.getOrDefault(k(x, y, z), (byte) 0);
        }

        @Override
        public void set(int x, int y, int z, BlockType t) {
            blocks.put(k(x, y, z), t);
        }

        @Override
        public void setOrientation(int x, int y, int z, byte orientation) {
            orients.put(k(x, y, z), orientation);
        }
    }

    @Test
    void footPosResolvesHeadAndFootToTheSameBlock() {
        Stub s = new Stub();
        Bed.place(s, 10, 5, 10, (byte) 0, false);
        int[] fromFoot = Bed.footPos(s, 10, 5, 10);
        int[] fromHead = Bed.footPos(s, 10, 5, 9); // ori 0 places head at z-1
        assertArrayEquals(new int[]{10, 5, 10}, fromFoot);
        assertArrayEquals(fromFoot, fromHead);
        assertTrue(Bed.isBed(s.getBlock(10, 5, 10)));
        assertTrue(Bed.isHead(s.getBlock(10, 5, 9)));
    }

    @Test
    void occupiedBedStillResolvesToTheFoot() {
        Stub s = new Stub();
        Bed.place(s, 4, 2, 7, (byte) 2, true);
        assertEquals(BlockType.BED_OCCUPIED, s.getBlock(4, 2, 7));
        int[] foot = Bed.footPos(s, 4, 2, 7);
        int[] other = Bed.getOtherHalf(s, 4, 2, 7, (byte) 2);
        int[] fromHead = Bed.footPos(s, other[0], other[1], other[2]);
        assertArrayEquals(foot, fromHead);
        assertArrayEquals(new int[]{4, 2, 7}, foot);
    }
}
