package com.minecraftclone.player;

import com.minecraftclone.world.BlockType;
import com.minecraftclone.world.Mining;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MortarTest {

    @Test
    void copperOreGrindsToCopperDust() {
        assertEquals(BlockType.COPPER_DUST, Mortar.dustFor(BlockType.COPPER_ORE));
        assertEquals(BlockType.COPPER_DUST, Mortar.dustFor(BlockType.CRUSHED_COPPER));
        assertEquals(BlockType.COPPER_DUST, Mortar.dustFor(BlockType.IMPURE_COPPER));
        assertTrue(Mortar.isGrindable(BlockType.COPPER_ORE));
        assertFalse(Mortar.isGrindable(BlockType.COPPER_DUST), "already dust");
    }

    @Test
    void vanillaOresGrindToDust() {
        assertEquals(BlockType.IRON_DUST, Mortar.dustFor(BlockType.IRON_ORE));
        assertEquals(BlockType.GOLD_DUST, Mortar.dustFor(BlockType.GOLD_ORE));
        assertEquals(BlockType.COAL, Mortar.dustFor(BlockType.COAL_ORE));
        assertEquals(BlockType.COAL_DUST, Mortar.dustFor(BlockType.COAL));
        assertTrue(Mortar.isGrindable(BlockType.COAL));
        assertFalse(Mortar.isGrindable(BlockType.COAL_DUST));
    }

    @Test
    void crushedMagnetiteGrindsToIronDust() {
        assertEquals(BlockType.IRON_DUST, Mortar.dustFor(BlockType.CRUSHED_MAGNETITE));
        assertEquals(BlockType.COPPER_DUST, Mortar.dustFor(BlockType.CRUSHED_CHALCOPYRITE));
    }

    @Test
    void dirtIsNotGrindable() {
        assertNull(Mortar.dustFor(BlockType.DIRT));
        assertFalse(Mortar.isGrindable(BlockType.DIRT));
        assertFalse(Mortar.isGrindable(null));
    }

    @Test
    void grindConsumesOreProducesDustAndWearsMortar() {
        Inventory inv = new Inventory();
        inv.setSlot(0, BlockType.MORTAR, 1);
        inv.setSlot(1, BlockType.COPPER_ORE, 3);
        ToolDurability dur = new ToolDurability();
        boolean[] broke = new boolean[1];

        BlockType dust = Mortar.grind(inv, 1, dur, false, broke);
        assertEquals(BlockType.COPPER_DUST, dust);
        assertEquals(2, inv.getCount(BlockType.COPPER_ORE));
        assertEquals(1, inv.getCount(BlockType.COPPER_DUST));
        assertEquals(1, inv.getCount(BlockType.MORTAR));
        assertFalse(broke[0]);
        assertTrue(dur.remaining(BlockType.MORTAR) < Mining.toolStats(BlockType.MORTAR).maxUses());
    }

    @Test
    void grindWithMortarSelectedPicksFirstOre() {
        Inventory inv = new Inventory();
        inv.setSlot(0, BlockType.MORTAR, 1);
        inv.setSlot(3, BlockType.TIN_ORE, 1);
        BlockType dust = Mortar.grind(inv, 0, new ToolDurability(), false, null);
        assertEquals(BlockType.TIN_DUST, dust);
        assertEquals(0, inv.getCount(BlockType.TIN_ORE));
        assertEquals(1, inv.getCount(BlockType.TIN_DUST));
    }

    @Test
    void grindWithoutMortarDoesNothing() {
        Inventory inv = new Inventory();
        inv.setSlot(0, BlockType.COPPER_ORE, 4);
        assertNull(Mortar.grind(inv, 0, new ToolDurability(), false, null));
        assertEquals(4, inv.getCount(BlockType.COPPER_ORE));
    }

    @Test
    void mortarBreaksAfterMaxUses() {
        Inventory inv = new Inventory();
        inv.setSlot(0, BlockType.MORTAR, 1);
        inv.setSlot(1, BlockType.COPPER_ORE, 64);
        ToolDurability dur = new ToolDurability();
        int uses = Mining.toolStats(BlockType.MORTAR).maxUses();
        boolean broke = false;
        for (int i = 0; i < uses; i++) {
            boolean[] flag = new boolean[1];
            assertNotNull(Mortar.grind(inv, 1, dur, false, flag));
            if (flag[0]) {
                broke = true;
                break;
            }
        }
        assertTrue(broke, "mortar should break on the last use");
        assertEquals(0, inv.getCount(BlockType.MORTAR));
    }

    @Test
    void creativeGrindDoesNotConsume() {
        Inventory inv = new Inventory();
        inv.setSlot(0, BlockType.MORTAR, 1);
        inv.setSlot(1, BlockType.COPPER_ORE, 1);
        BlockType dust = Mortar.grind(inv, 1, new ToolDurability(), true, null);
        assertEquals(BlockType.COPPER_DUST, dust);
        assertEquals(1, inv.getCount(BlockType.COPPER_ORE), "creative keeps the ore");
        assertEquals(1, inv.getCount(BlockType.MORTAR));
        assertEquals(1, inv.getCount(BlockType.COPPER_DUST));
    }
}
