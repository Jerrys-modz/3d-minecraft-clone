package com.minecraftclone.world;

import com.minecraftclone.player.Crafting;
import com.minecraftclone.world.gen.WorldGenSettings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** MV gold cable, the LV Transformer, overvoltage explosions, and per-block cable loss. */
class VoltageAndLossTest {

    @TempDir
    Path dir;

    /** A world with a clear row of air at y=100 along +z, so terrain can't join the wiring. */
    private World newWorld() {
        World world = new World(42, new WorldGenSettings(), null,
                dir.resolve("w" + System.nanoTime()), DimensionType.OVERWORLD, true);
        world.ensureChunk(0, 0);
        for (int x = -3; x <= 3; x++)
            for (int y = 97; y <= 103; y++)
                for (int z = -3; z < 16; z++) world.setBlock(x, y, z, BlockType.AIR);
        return world;
    }

    private static final int Y = 100;

    /** Lays {@code n} cables at z=1..n and a charged battery at z=n+1; returns the battery. */
    private static BatteryBlockEntity line(World w, BlockType cable, int n) {
        for (int z = 1; z <= n; z++) w.setBlock(0, Y, z, cable);
        w.setBlock(0, Y, n + 1, BlockType.BATTERY_BLOCK);
        BatteryBlockEntity b = w.getOrCreateBatteryBlock(0, Y, n + 1);
        b.chargeEU(BatteryBlockEntity.MAX_EU);
        return b;
    }

    // ------------------------------------------------------------------
    // Voltage
    // ------------------------------------------------------------------

    @Test
    void goldIsMvCopperAndTinAreLv() {
        assertEquals(CableTier.Voltage.MV, CableTier.GOLD.voltage);
        assertEquals(CableTier.Voltage.LV, CableTier.COPPER.voltage);
        assertEquals(CableTier.Voltage.LV, CableTier.TIN.voltage);
    }

    @Test
    void lvMachineOnGoldExplodes() {
        World w = newWorld();
        line(w, BlockType.GOLD_CABLE, 2);
        w.setBlock(0, Y, 0, BlockType.ELECTRIC_MACERATOR);
        ElectricMaceratorEntity m = (ElectricMaceratorEntity) w.blockEntityAt(0, Y, 0);
        m.setSlot(ElectricMachineEntity.SLOT_INPUT, BlockType.IRON_ORE, 4);

        w.tickBlockEntities(0.05f);

        assertNotEquals(BlockType.ELECTRIC_MACERATOR, w.getBlock(0, Y, 0), "the macerator blew up");
        assertNull(w.blockEntityAt(0, Y, 0));
        assertEquals(1, w.takeOverloadBlasts().size());
        assertTrue(w.takeOverloadBlasts().isEmpty(), "blasts are handed out once");
    }

    @Test
    void transformerProtectsTheMachine() {
        World w = newWorld();
        w.setBlock(0, Y, 1, BlockType.LV_TRANSFORMER);
        for (int z = 2; z <= 3; z++) w.setBlock(0, Y, z, BlockType.GOLD_CABLE);
        w.setBlock(0, Y, 4, BlockType.BATTERY_BLOCK);
        w.getOrCreateBatteryBlock(0, Y, 4).chargeEU(BatteryBlockEntity.MAX_EU);
        w.setBlock(0, Y, 0, BlockType.ELECTRIC_MACERATOR);
        ElectricMaceratorEntity m = (ElectricMaceratorEntity) w.blockEntityAt(0, Y, 0);
        m.setSlot(ElectricMachineEntity.SLOT_INPUT, BlockType.IRON_ORE, 1);

        for (int i = 0; i < 200; i++) w.tickBlockEntities(0.05f);

        assertEquals(BlockType.ELECTRIC_MACERATOR, w.getBlock(0, Y, 0));
        assertFalse(m.isOvervolted());
        assertEquals(2, m.countOf(ElectricMachineEntity.SLOT_OUTPUT));
        assertTrue(w.takeOverloadBlasts().isEmpty());
    }

    @Test
    void transformerBridgesGoldLineIntoCopperNetwork() {
        // generator side: battery - gold - transformer - copper - machine
        World w = newWorld();
        w.setBlock(0, Y, 1, BlockType.COPPER_CABLE);
        w.setBlock(0, Y, 2, BlockType.LV_TRANSFORMER);
        w.setBlock(0, Y, 3, BlockType.GOLD_CABLE);
        w.setBlock(0, Y, 4, BlockType.BATTERY_BLOCK);
        BatteryBlockEntity b = w.getOrCreateBatteryBlock(0, Y, 4);
        b.chargeEU(100f);
        EuNetwork.Feed feed = EuNetwork.resolve(w, 0, Y, 0);
        assertSame(b, feed.source());
        assertFalse(feed.overvoltage());
        assertEquals(CableTier.COPPER.throughput, feed.throughput(), "copper is the weakest cable on the path");
        assertEquals(2, feed.cableBlocks());
    }

    @Test
    void unpoweredGoldNetworkIsHarmless() {
        World w = newWorld();
        w.setBlock(0, Y, 1, BlockType.GOLD_CABLE);
        w.setBlock(0, Y, 2, BlockType.BATTERY_BLOCK);   // empty battery
        w.setBlock(0, Y, 0, BlockType.ELECTRIC_FURNACE);
        ElectricFurnaceEntity f = (ElectricFurnaceEntity) w.blockEntityAt(0, Y, 0);
        f.setSlot(ElectricFurnaceEntity.SLOT_INPUT, BlockType.IRON_ORE, 1);
        w.tickBlockEntities(0.05f);
        assertEquals(BlockType.ELECTRIC_FURNACE, w.getBlock(0, Y, 0));
    }

    @Test
    void explosionsOffMeansTheMachineJustIdles() {
        World w = newWorld();
        w.setMachineExplosions(false);
        line(w, BlockType.GOLD_CABLE, 1);
        w.setBlock(0, Y, 0, BlockType.ELECTRIC_FURNACE);
        ElectricFurnaceEntity f = (ElectricFurnaceEntity) w.blockEntityAt(0, Y, 0);
        f.setSlot(ElectricFurnaceEntity.SLOT_INPUT, BlockType.IRON_ORE, 1);
        for (int i = 0; i < 100; i++) w.tickBlockEntities(0.05f);
        assertEquals(BlockType.ELECTRIC_FURNACE, w.getBlock(0, Y, 0));
        assertTrue(f.isOvervolted());
        assertNull(f.typeOf(ElectricFurnaceEntity.SLOT_OUTPUT), "no smelting on MV without a transformer");
    }

    @Test
    void transformerRecipe() {
        BlockType A = BlockType.GOLD_INGOT, I = BlockType.IRON_INGOT, X = BlockType.COPPER_INGOT;
        var r = Crafting.match3x3(new BlockType[]{A, A, A, I, I, I, X, X, X});
        assertNotNull(r);
        assertEquals(BlockType.LV_TRANSFORMER, r.output());
        assertFalse(CableTier.isCable(BlockType.LV_TRANSFORMER), "a transformer splits networks");
    }

    // ------------------------------------------------------------------
    // Distance loss
    // ------------------------------------------------------------------

    @Test
    void lossGrowsWithCableLength() {
        World w = newWorld();
        line(w, BlockType.COPPER_CABLE, 10);
        EuNetwork.Feed feed = EuNetwork.resolve(w, 0, Y, 0);
        assertEquals(10, feed.cableBlocks());
        assertEquals(1f - 10 * CableTier.COPPER.lossPerBlock, feed.efficiency(), 1e-4f);
    }

    @Test
    void tinLosesMoreThanCopperAndGoldLeast() {
        assertTrue(CableTier.TIN.lossPerBlock > CableTier.COPPER.lossPerBlock);
        assertTrue(CableTier.COPPER.lossPerBlock > CableTier.GOLD.lossPerBlock);
    }

    @Test
    void directAdjacencyIsLossless() {
        World w = newWorld();
        w.setBlock(0, Y, 1, BlockType.BATTERY_BLOCK);
        w.getOrCreateBatteryBlock(0, Y, 1).chargeEU(100f);
        EuNetwork.Feed feed = EuNetwork.resolve(w, 0, Y, 0);
        assertEquals(1f, feed.efficiency());
        assertEquals(0, feed.cableBlocks());
    }

    @Test
    void lossSlowsMachineButChargesFullEu() {
        World w = newWorld();
        BatteryBlockEntity b = line(w, BlockType.TIN_CABLE, 10);   // 10% loss
        w.setBlock(0, Y, 0, BlockType.ELECTRIC_COMPRESSOR);
        ElectricCompressorEntity c = (ElectricCompressorEntity) w.blockEntityAt(0, Y, 0);
        c.setSlot(ElectricMachineEntity.SLOT_INPUT, BlockType.IRON_INGOT, 1);
        c.tick(1f);
        float perSecond = ElectricCompressorEntity.EU_PER_OPERATION / ElectricCompressorEntity.PRESS_SECONDS;
        float drawn = perSecond * CableTier.TIN.throughput;
        assertEquals(BatteryBlockEntity.MAX_EU - drawn, b.euStored(), 0.01f);
        float expectedProgress = drawn * 0.9f / perSecond / ElectricCompressorEntity.PRESS_SECONDS;
        assertEquals(expectedProgress, c.progressFraction(), 1e-4f);
    }

    @Test
    void sourcePicksTheShorterOfTwoPaths() {
        World w = newWorld();
        // Short branch: 1 cable to a battery at z=2. Long branch: a loop round to a battery further away.
        w.setBlock(0, Y, 1, BlockType.COPPER_CABLE);
        w.setBlock(0, Y, 2, BlockType.BATTERY_BLOCK);
        BatteryBlockEntity near = w.getOrCreateBatteryBlock(0, Y, 2);
        near.chargeEU(100f);
        for (int z = 1; z <= 6; z++) w.setBlock(1, Y, z, BlockType.COPPER_CABLE);
        w.setBlock(1, Y, 7, BlockType.BATTERY_BLOCK);
        w.getOrCreateBatteryBlock(1, Y, 7).chargeEU(100f);
        assertSame(near, EuNetwork.findSource(w, 0, Y, 0));
    }
}
