package com.minecraftclone.player;

import com.minecraftclone.world.BlockType;
import com.minecraftclone.world.Chest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JoinedStorageTest {

    @Test
    void presentsTwoStoragesAsOneContiguousSpace() {
        Chest a = new Chest();
        Chest b = new Chest();
        a.setSlot(0, BlockType.DIRT, 3);
        b.setSlot(0, BlockType.STONE, 4);

        JoinedStorage joined = new JoinedStorage(a, b);
        assertEquals(Chest.SLOT_COUNT + Chest.SLOT_COUNT, joined.size());
        assertEquals(BlockType.DIRT, joined.typeOf(0));
        assertEquals(3, joined.countOf(0));
        // The second half starts right after the first.
        assertEquals(BlockType.STONE, joined.typeOf(Chest.SLOT_COUNT));
        assertEquals(4, joined.countOf(Chest.SLOT_COUNT));
        assertNull(joined.typeOf(2), "slot 2 in the second half is empty");
    }

    @Test
    void setSlotRoutesToTheRightHalf() {
        Chest a = new Chest();
        Chest b = new Chest();
        JoinedStorage joined = new JoinedStorage(a, b);
        joined.setSlot(5, BlockType.APPLE, 8);
        joined.setSlot(Chest.SLOT_COUNT + 5, BlockType.BERRIES, 2);
        assertEquals(BlockType.APPLE, a.typeOf(5));
        assertEquals(8, a.countOf(5));
        assertEquals(BlockType.BERRIES, b.typeOf(5));
        assertEquals(2, b.countOf(5));
    }

    @Test
    void addFillsFirstThenSecond() {
        Chest a = new Chest();
        Chest b = new Chest();
        JoinedStorage joined = new JoinedStorage(a, b);
        // Small amount: lives entirely in the first half.
        assertEquals(0, joined.add(BlockType.DIRT, 10));
        assertEquals(10, a.getCount(BlockType.DIRT));
        assertEquals(0, b.getCount(BlockType.DIRT));

        // Overflow past the first half's capacity (27 slots x 64).
        int leftover = joined.add(BlockType.DIRT, Chest.SLOT_COUNT * 64 + 5);
        assertEquals(0, leftover);
        assertEquals(Chest.SLOT_COUNT * 64, a.getCount(BlockType.DIRT), "first half fills before the second");
        assertEquals(15, b.getCount(BlockType.DIRT), "second half takes the overflow");
        assertEquals(Chest.SLOT_COUNT * 64 + 15, joined.getCount(BlockType.DIRT));
    }

    @Test
    void getCountSumsAcrossBothHalves() {
        Chest a = new Chest();
        Chest b = new Chest();
        a.add(BlockType.STICK, 5);
        b.add(BlockType.STICK, 7);
        assertEquals(12, new JoinedStorage(a, b).getCount(BlockType.STICK));
    }
}
