package com.minecraftclone.world;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BarrelTest {

    @Test
    void barrelIsA27SlotStorageWithItsOwnType() {
        Barrel b = new Barrel();
        assertEquals(Barrel.TYPE, b.type());
        assertEquals(BlockType.BARREL, b.blockType());
        assertEquals(Chest.SLOT_COUNT, b.size());
        b.add(BlockType.APPLE, 5);
        assertEquals(5, b.getCount(BlockType.APPLE));
        assertTrue(!b.isEmpty(0));
    }

    @Test
    void barrelDoesNotMergeIntoSingles() {
        // A barrel is a plain single-block stash: even side by side it stays 27 slots.
        Barrel a = new Barrel();
        Barrel b = new Barrel();
        a.add(BlockType.DIRT, 3);
        b.add(BlockType.STONE, 4);
        assertEquals(3, a.getCount(BlockType.DIRT));
        assertEquals(4, b.getCount(BlockType.STONE));
    }
}
