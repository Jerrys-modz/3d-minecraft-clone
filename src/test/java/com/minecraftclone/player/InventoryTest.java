package com.minecraftclone.player;

import com.minecraftclone.world.BlockType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class InventoryTest {

    @Test
    void splitsLargeStacksAcrossSlots() {
        Inventory inv = new Inventory();
        assertEquals(0, inv.add(BlockType.DIRT, 70));
        int sixtyFour = 0, six = 0;
        for (int i = 0; i < Inventory.SIZE; i++) {
            if (inv.typeOf(i) == BlockType.DIRT) {
                if (inv.countOf(i) == 64) sixtyFour++;
                if (inv.countOf(i) == 6) six++;
            }
        }
        assertEquals(1, sixtyFour);
        assertEquals(1, six);
        assertEquals(70, inv.getCount(BlockType.DIRT));
    }

    @Test
    void toolsAreUnstackableButBlocksStackTo64() {
        assertEquals(1, Inventory.maxStack(BlockType.IRON_PICKAXE));
        assertEquals(64, Inventory.maxStack(BlockType.DIRT));
    }

    @Test
    void addReturnsLeftoverWhenFull() {
        Inventory inv = new Inventory();
        int leftover = 0;
        for (int i = 0; i < Inventory.SIZE; i++) {
            leftover = inv.add(BlockType.STONE, 64);
        }
        assertEquals(0, leftover);
        // One more stack has nowhere to go.
        assertEquals(64, inv.add(BlockType.STONE, 64));
        assertTrue(inv.isFull());
    }

    @Test
    void removeTakesAcrossSlotsAndFailsWhenShort() {
        Inventory inv = new Inventory();
        inv.add(BlockType.SAND, 70);
        assertTrue(inv.remove(BlockType.SAND, 70));
        assertEquals(0, inv.getCount(BlockType.SAND));
        assertFalse(inv.remove(BlockType.SAND, 1));
    }

    @Test
    void clearEmptiesEverything() {
        Inventory inv = new Inventory();
        inv.add(BlockType.WOOD_LOG, 10);
        inv.setSlot(3, BlockType.APPLE, 5);
        inv.clear();
        assertTrue(inv.isEmpty());
    }
}
