package com.minecraftclone.player;

import com.minecraftclone.world.BlockType;
import com.minecraftclone.world.Mining;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MetalworkingTest {

    @Test
    void ingotsMapToMatchingPlateAndRod() {
        assertEquals(BlockType.COPPER_PLATE, Metalworking.plateFor(BlockType.COPPER_INGOT));
        assertEquals(BlockType.COPPER_ROD, Metalworking.rodFor(BlockType.COPPER_INGOT));
        assertEquals(BlockType.STEEL_PLATE, Metalworking.plateFor(BlockType.STEEL_INGOT));
        assertEquals(BlockType.STEEL_ROD, Metalworking.rodFor(BlockType.STEEL_INGOT));
        assertEquals(BlockType.WROUGHT_IRON_PLATE, Metalworking.plateFor(BlockType.WROUGHT_IRON_INGOT));
        assertEquals(BlockType.BRONZE_PLATE, Metalworking.plateFor(BlockType.BRONZE_INGOT));
        assertEquals(BlockType.BRASS_PLATE, Metalworking.plateFor(BlockType.BRASS_INGOT));
        assertEquals(BlockType.IRON_PLATE, Metalworking.plateFor(BlockType.IRON_INGOT));
        assertEquals(BlockType.GOLD_PLATE, Metalworking.plateFor(BlockType.GOLD_INGOT));
        assertEquals(BlockType.TIN_PLATE, Metalworking.plateFor(BlockType.TIN_INGOT));
    }

    @Test
    void metalsWithoutPlateItemsAreSkipped() {
        assertNull(Metalworking.plateFor(BlockType.ALUMINUM_INGOT), "no ALUMINUM_PLATE item");
        assertNull(Metalworking.plateFor(BlockType.COPPER_ORE));
        assertNull(Metalworking.plateFor(BlockType.COPPER_PLATE));
        assertNull(Metalworking.plateFor(null));
        assertFalse(Metalworking.isPlateable(BlockType.DIRT));
        assertFalse(Metalworking.isDrawable(BlockType.STICK));
    }

    @Test
    void hammerConsumesIngotProducesPlateAndWears() {
        Inventory inv = new Inventory();
        inv.setSlot(0, BlockType.IRON_HAMMER, 1);
        inv.setSlot(1, BlockType.IRON_INGOT, 3);
        ToolDurability dur = new ToolDurability();
        boolean[] broke = new boolean[1];

        BlockType plate = Metalworking.hammerPlate(inv, 1, dur, false, broke);
        assertEquals(BlockType.IRON_PLATE, plate);
        assertEquals(2, inv.getCount(BlockType.IRON_INGOT));
        assertEquals(1, inv.getCount(BlockType.IRON_PLATE));
        assertEquals(1, inv.getCount(BlockType.IRON_HAMMER));
        assertFalse(broke[0]);
        assertTrue(dur.remaining(BlockType.IRON_HAMMER) < Mining.toolStats(BlockType.IRON_HAMMER).maxUses());
    }

    @Test
    void fileConsumesIngotProducesRodAndWears() {
        Inventory inv = new Inventory();
        inv.setSlot(0, BlockType.FILE, 1);
        inv.setSlot(1, BlockType.BRONZE_INGOT, 2);
        ToolDurability dur = new ToolDurability();

        BlockType rod = Metalworking.fileRod(inv, 1, dur, false, null);
        assertEquals(BlockType.BRONZE_ROD, rod);
        assertEquals(1, inv.getCount(BlockType.BRONZE_INGOT));
        assertEquals(1, inv.getCount(BlockType.BRONZE_ROD));
        assertTrue(dur.remaining(BlockType.FILE) < Mining.toolStats(BlockType.FILE).maxUses());
    }

    @Test
    void hammerSelectedPicksFirstIngot() {
        Inventory inv = new Inventory();
        inv.setSlot(0, BlockType.STONE_HAMMER, 1);
        inv.setSlot(3, BlockType.GOLD_INGOT, 1);
        BlockType plate = Metalworking.hammerPlate(inv, 0, new ToolDurability(), false, null);
        assertEquals(BlockType.GOLD_PLATE, plate);
        assertEquals(0, inv.getCount(BlockType.GOLD_INGOT));
        assertEquals(1, inv.getCount(BlockType.GOLD_PLATE));
    }

    @Test
    void withoutToolDoesNothing() {
        Inventory inv = new Inventory();
        inv.setSlot(0, BlockType.IRON_INGOT, 4);
        assertNull(Metalworking.hammerPlate(inv, 0, new ToolDurability(), false, null));
        assertNull(Metalworking.fileRod(inv, 0, new ToolDurability(), false, null));
        assertEquals(4, inv.getCount(BlockType.IRON_INGOT));
    }

    @Test
    void fileBreaksAfterMaxUses() {
        Inventory inv = new Inventory();
        inv.setSlot(0, BlockType.FILE, 1);
        inv.setSlot(1, BlockType.COPPER_INGOT, 64);
        inv.setSlot(2, BlockType.COPPER_INGOT, 64);
        ToolDurability dur = new ToolDurability();
        int uses = Mining.toolStats(BlockType.FILE).maxUses();
        boolean[] broke = new boolean[1];
        for (int i = 0; i < uses - 1; i++) {
            assertNotNull(Metalworking.fileRod(inv, 1, dur, false, broke));
            assertFalse(broke[0]);
        }
        assertNotNull(Metalworking.fileRod(inv, 1, dur, false, broke));
        assertTrue(broke[0]);
        assertEquals(0, inv.getCount(BlockType.FILE));
    }

    @Test
    void creativeDoesNotConsumeOrWear() {
        Inventory inv = new Inventory();
        inv.setSlot(0, BlockType.FILE, 1);
        inv.setSlot(1, BlockType.STEEL_INGOT, 1);
        ToolDurability dur = new ToolDurability();
        assertEquals(BlockType.STEEL_ROD, Metalworking.fileRod(inv, 1, dur, true, null));
        assertEquals(1, inv.getCount(BlockType.STEEL_INGOT));
        assertEquals(1, inv.getCount(BlockType.STEEL_ROD));
        assertEquals(1, inv.getCount(BlockType.FILE));
        assertEquals(Mining.toolStats(BlockType.FILE).maxUses(), dur.remaining(BlockType.FILE));
    }

    @Test
    void fullBagAbortsWithoutConsuming() {
        Inventory inv = new Inventory();
        inv.setSlot(0, BlockType.IRON_HAMMER, 1);
        inv.setSlot(1, BlockType.IRON_INGOT, 2);
        for (int i = 2; i < inv.size(); i++) {
            inv.setSlot(i, BlockType.DIRT, Inventory.maxStack(BlockType.DIRT));
        }
        assertNull(Metalworking.hammerPlate(inv, 1, new ToolDurability(), false, null));
        assertEquals(2, inv.getCount(BlockType.IRON_INGOT));
        assertEquals(0, inv.getCount(BlockType.IRON_PLATE));
    }

    @Test
    void bronzeHammerPlatesSteel() {
        Inventory inv = new Inventory();
        inv.setSlot(0, BlockType.BRONZE_HAMMER, 1);
        inv.setSlot(1, BlockType.STEEL_INGOT, 1);
        assertEquals(BlockType.STEEL_PLATE, Metalworking.hammerPlate(inv, 1, new ToolDurability(), false, null));
    }
}
