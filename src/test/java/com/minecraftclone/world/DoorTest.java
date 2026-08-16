package com.minecraftclone.world;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DoorTest {

    /** A tiny in-memory world for the door logic. */
    private static final class Stub implements BlockAccessor, Door.BlockSetter {
        final Map<Long, BlockType> blocks = new HashMap<>();

        private static long k(int x, int y, int z) {
            return ((long) (x + 2048) << 40) | ((long) y << 32) | ((long) (z + 2048) << 20);
        }

        @Override
        public BlockType getBlock(int x, int y, int z) {
            return blocks.getOrDefault(k(x, y, z), BlockType.AIR);
        }

        @Override
        public void set(int x, int y, int z, BlockType t) {
            blocks.put(k(x, y, z), t);
        }
    }

    @Test
    void toggleOpensAndClosesBothHalves() {
        Stub s = new Stub();
        s.set(10, 5, 10, BlockType.DOOR);
        s.set(10, 6, 10, BlockType.DOOR);
        Door.toggle(s, s, 10, 5, 10); // click the bottom half
        assertEquals(BlockType.DOOR_OPEN, s.getBlock(10, 5, 10));
        assertEquals(BlockType.DOOR_OPEN, s.getBlock(10, 6, 10));
        Door.toggle(s, s, 10, 6, 10); // click the top half
        assertEquals(BlockType.DOOR, s.getBlock(10, 5, 10));
        assertEquals(BlockType.DOOR, s.getBlock(10, 6, 10));
    }

    @Test
    void breakingOneHalfRemovesBoth() {
        Stub s = new Stub();
        s.set(10, 5, 10, BlockType.DOOR_OPEN);
        s.set(10, 6, 10, BlockType.DOOR_OPEN);
        Door.breakDoor(s, s, 10, 6, 10);
        assertEquals(BlockType.AIR, s.getBlock(10, 5, 10));
        assertEquals(BlockType.AIR, s.getBlock(10, 6, 10));
    }

    @Test
    void recognizesDoorBlocks() {
        assertTrue(BlockType.DOOR.isDoor());
        assertTrue(BlockType.DOOR_OPEN.isDoor());
        assertTrue(!BlockType.PLANKS.isDoor());
    }

    @Test
    void trapdoorTogglesSingleBlock() {
        Stub s = new Stub();
        s.set(10, 5, 10, BlockType.TRAPDOOR);
        Door.toggleSingle(s, s, 10, 5, 10);
        assertEquals(BlockType.TRAPDOOR_OPEN, s.getBlock(10, 5, 10));
        Door.toggleSingle(s, s, 10, 5, 10);
        assertEquals(BlockType.TRAPDOOR, s.getBlock(10, 5, 10));
        assertTrue(BlockType.TRAPDOOR.isTrapdoor());
    }
}
