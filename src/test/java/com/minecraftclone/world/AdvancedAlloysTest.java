package com.minecraftclone.world;

import com.minecraftclone.engine.gui.ContainerGui;
import com.minecraftclone.player.Crafting;
import com.minecraftclone.player.InventoryController;
import com.minecraftclone.player.Metalworking;
import com.minecraftclone.player.Smelting;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Phase 3 alloys: invar, electrum, stainless steel, and the MV casing. */
class AdvancedAlloysTest {

    private static final BlockType N = null;

    @Test
    void invarMixesTwoIronOneNickel() {
        var r = Crafting.match3x3(new BlockType[]{
                BlockType.IRON_DUST, BlockType.NICKEL_DUST, N,
                BlockType.IRON_DUST, N, N, N, N, N});
        assertNotNull(r);
        assertEquals(BlockType.INVAR_DUST, r.output());
        assertEquals(3, r.outputAmount());
        assertEquals(BlockType.INVAR_INGOT, Smelting.outputFor(BlockType.INVAR_DUST));
    }

    @Test
    void electrumMixesGoldAndSilver() {
        var r = Crafting.match2x2(new BlockType[]{BlockType.SILVER_DUST, BlockType.GOLD_DUST, N, N});
        assertNotNull(r);
        assertEquals(BlockType.ELECTRUM_DUST, r.output());
        assertEquals(2, r.outputAmount());
        assertEquals(BlockType.ELECTRUM_INGOT, Smelting.outputFor(BlockType.ELECTRUM_DUST));
    }

    @Test
    void stainlessNeedsAllFourMetalsAndThePbf() {
        BlockType I = BlockType.IRON_DUST;
        var r = Crafting.match3x3(new BlockType[]{
                I, I, I, I, BlockType.CHROMIUM_DUST, I, I, BlockType.MANGANESE_DUST, BlockType.NICKEL_DUST});
        assertNotNull(r);
        assertEquals(BlockType.STAINLESS_STEEL_DUST, r.output());
        assertEquals(9, r.outputAmount());
        assertNull(Smelting.outputFor(BlockType.STAINLESS_STEEL_DUST), "a plain furnace can't reach the heat");
        assertEquals(BlockType.STAINLESS_STEEL_INGOT,
                PrimitiveBlastFurnaceEntity.outputFor(BlockType.STAINLESS_STEEL_DUST));
    }

    @Test
    void alloysFormPlatesAndRods() {
        for (BlockType ingot : new BlockType[]{
                BlockType.INVAR_INGOT, BlockType.ELECTRUM_INGOT, BlockType.STAINLESS_STEEL_INGOT}) {
            assertNotNull(Metalworking.plateFor(ingot), ingot.name());
            assertNotNull(Metalworking.rodFor(ingot), ingot.name());
            assertEquals(Metalworking.plateFor(ingot), ElectricCompressorEntity.outputFor(ingot));
        }
    }

    @Test
    void mvCasingFromStainlessPlates() {
        BlockType S = BlockType.STAINLESS_STEEL_PLATE;
        var r = Crafting.match3x3(new BlockType[]{S, S, S, S, N, S, S, S, S});
        assertNotNull(r);
        assertEquals(BlockType.MV_CASING, r.output());
    }

    @Test
    void shiftClickFeedsThePbfItsOwnRecipes() {
        PrimitiveBlastFurnaceEntity pbf = new PrimitiveBlastFurnaceEntity();
        int input = ContainerGui.CONTAINER_START + PrimitiveBlastFurnaceEntity.SLOT_INPUT;
        assertEquals(input, InventoryController.quickMoveFurnaceTarget(pbf, BlockType.STAINLESS_STEEL_DUST));
        assertEquals(input, InventoryController.quickMoveFurnaceTarget(pbf, BlockType.STEEL_DUST));
        assertEquals(ContainerGui.CONTAINER_START + PrimitiveBlastFurnaceEntity.SLOT_FUEL,
                InventoryController.quickMoveFurnaceTarget(pbf, BlockType.COAL));
    }
}
