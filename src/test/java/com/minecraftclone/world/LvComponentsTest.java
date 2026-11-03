package com.minecraftclone.world;

import com.minecraftclone.player.Crafting;
import com.minecraftclone.world.gen.WorldGenSettings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** Tin cable, the vacuum tube circuit, and the LV Compressor. */
class LvComponentsTest {

    @TempDir
    Path dir;

    private World newWorld() {
        World world = new World(42, new WorldGenSettings(), null,
                dir.resolve("w" + System.nanoTime()), DimensionType.OVERWORLD, true);
        world.ensureChunk(0, 0);
        return world;
    }

    // ------------------------------------------------------------------
    // Tin cable
    // ------------------------------------------------------------------

    @Test
    void tinCableIsTheWeakestTier() {
        assertEquals(CableTier.TIN, CableTier.of(BlockType.TIN_CABLE));
        assertTrue(CableTier.isCable(BlockType.TIN_CABLE));
        assertTrue(CableTier.TIN.throughput < CableTier.COPPER.throughput);
    }

    @Test
    void tinCableRecipeYieldsSix() {
        BlockType T = BlockType.TIN_INGOT;
        var r = Crafting.match3x3(new BlockType[]{null, T, null, null, T, null, null, T, null});
        assertNotNull(r);
        assertEquals(BlockType.TIN_CABLE, r.output());
        assertEquals(6, r.outputAmount());
    }

    @Test
    void oneTinSegmentThrottlesACopperRun() {
        World world = newWorld();
        world.setBlock(0, 64, 1, BlockType.COPPER_CABLE);
        world.setBlock(0, 64, 2, BlockType.TIN_CABLE);
        world.setBlock(0, 64, 3, BlockType.COPPER_CABLE);
        assertEquals(CableTier.TIN.throughput, EuNetwork.drawRate(world, 0, 64, 0));
    }

    @Test
    void machineDrawsPowerThroughTinCable() {
        World world = newWorld();
        world.setBlock(0, 64, 2, BlockType.BATTERY_BLOCK);
        BatteryBlockEntity bat = world.getOrCreateBatteryBlock(0, 64, 2);
        bat.chargeEU(1000f);
        world.setBlock(0, 64, 1, BlockType.TIN_CABLE);
        world.setBlock(0, 64, 0, BlockType.ELECTRIC_COMPRESSOR);
        ElectricCompressorEntity c = (ElectricCompressorEntity) world.blockEntityAt(0, 64, 0);
        c.setSlot(ElectricMachineEntity.SLOT_INPUT, BlockType.IRON_INGOT, 1);
        c.tick(1f);
        float perSecond = ElectricCompressorEntity.EU_PER_OPERATION / ElectricCompressorEntity.PRESS_SECONDS;
        assertEquals(perSecond * CableTier.TIN.throughput, 1000f - bat.euStored(), 0.01f);
    }

    // ------------------------------------------------------------------
    // Vacuum tube
    // ------------------------------------------------------------------

    @Test
    void vacuumTubeRecipeYieldsTwo() {
        BlockType W = BlockType.COPPER_CABLE, G = BlockType.GLASS;
        var r = Crafting.match3x3(new BlockType[]{null, null, null, W, G, W, null, null, null});
        assertNotNull(r);
        assertEquals(BlockType.VACUUM_TUBE, r.output());
        assertEquals(2, r.outputAmount());
        assertTrue(BlockType.VACUUM_TUBE.isItem);
    }

    @Test
    void lvProcessorsNeedAVacuumTube() {
        BlockType X = BlockType.COPPER_INGOT, D = BlockType.DIAMOND, C = BlockType.STEEL_CASING;
        var withoutTube = Crafting.match3x3(new BlockType[]{X, D, X, X, C, X, X, X, X});
        assertTrue(withoutTube == null || withoutTube.output() != BlockType.ELECTRIC_MACERATOR,
                "the old tube-less macerator recipe must be gone");
    }

    // ------------------------------------------------------------------
    // Compressor
    // ------------------------------------------------------------------

    @Test
    void compressorPressesIngotsIntoPlates() {
        assertEquals(BlockType.STEEL_PLATE, ElectricCompressorEntity.outputFor(BlockType.STEEL_INGOT));
        assertEquals(BlockType.COPPER_PLATE, ElectricCompressorEntity.outputFor(BlockType.COPPER_INGOT));
        assertNull(ElectricCompressorEntity.outputFor(BlockType.DIRT));
        assertNull(ElectricCompressorEntity.outputFor(BlockType.STEEL_PLATE));

        ElectricCompressorEntity c = new ElectricCompressorEntity();
        BatteryBlockEntity bat = new BatteryBlockEntity();
        bat.chargeEU(BatteryBlockEntity.MAX_EU);
        c.euSource = bat;
        c.setSlot(ElectricMachineEntity.SLOT_INPUT, BlockType.STEEL_INGOT, 4);
        for (int i = 0; i < 400; i++) c.tick(0.05f);
        assertEquals(BlockType.STEEL_PLATE, c.typeOf(ElectricMachineEntity.SLOT_OUTPUT));
        assertEquals(4, c.countOf(ElectricMachineEntity.SLOT_OUTPUT));
        assertNull(c.typeOf(ElectricMachineEntity.SLOT_BYPRODUCT));
    }

    @Test
    void compressorIsRegisteredCraftableAndPlaceable() {
        assertTrue(BlockEntities.isRegistered(ElectricCompressorEntity.TYPE));
        BlockType X = BlockType.COPPER_INGOT, P = BlockType.STEEL_PLATE, C = BlockType.STEEL_CASING,
                V = BlockType.VACUUM_TUBE;
        var r = Crafting.match3x3(new BlockType[]{X, P, X, X, C, X, X, V, X});
        assertNotNull(r);
        assertEquals(BlockType.ELECTRIC_COMPRESSOR, r.output());

        World world = newWorld();
        world.setBlock(0, 70, 0, BlockType.ELECTRIC_COMPRESSOR);
        assertInstanceOf(ElectricCompressorEntity.class, world.blockEntityAt(0, 70, 0));
    }
}
