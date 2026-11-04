package com.minecraftclone.world;

import com.minecraftclone.player.Crafting;
import com.minecraftclone.world.gen.WorldGenSettings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** HV aluminium cable, the MV Transformer, and voltage ratings across LV and MV machines. */
class HighVoltageTest {

    @TempDir
    Path dir;

    private static final int Y = 100;

    private World newWorld() {
        World w = new World(42, new WorldGenSettings(), null,
                dir.resolve("w" + System.nanoTime()), DimensionType.OVERWORLD, true);
        w.ensureChunk(0, 0);
        for (int x = 0; x < 10; x++)
            for (int y = 97; y < 104; y++)
                for (int z = 0; z < 12; z++) w.setBlock(x, y, z, BlockType.AIR);
        return w;
    }

    /** Lays blocks at z = 1.. along x=0 and ends with a charged battery. */
    private static void line(World w, BlockType... blocks) {
        for (int i = 0; i < blocks.length; i++) w.setBlock(0, Y, 1 + i, blocks[i]);
        int bz = 1 + blocks.length;
        w.setBlock(0, Y, bz, BlockType.BATTERY_BLOCK);
        BatteryBlockEntity b = w.getOrCreateBatteryBlock(0, Y, bz);
        b.setSlot(BatteryBlockEntity.SLOT_BATTERIES, BlockType.MV_BATTERY, 4);
        b.chargeEU(b.capacity());
    }

    @Test
    void aluminiumIsHvAndFastest() {
        assertEquals(CableTier.Voltage.HV, CableTier.ALUMINIUM.voltage);
        assertTrue(CableTier.ALUMINIUM.throughput > CableTier.GOLD.throughput);
        assertTrue(CableTier.ALUMINIUM.lossPerBlock < CableTier.GOLD.lossPerBlock);
        assertTrue(CableTier.Voltage.HV.exceeds(CableTier.Voltage.MV));
        assertFalse(CableTier.Voltage.MV.exceeds(CableTier.Voltage.MV));
    }

    @Test
    void feedReportsTheDeliveredVoltage() {
        World w = newWorld();
        line(w, BlockType.ALUMINIUM_CABLE);
        assertEquals(CableTier.Voltage.HV, EuNetwork.resolve(w, 0, Y, 0).voltage());

        World w2 = newWorld();
        line(w2, BlockType.MV_TRANSFORMER, BlockType.ALUMINIUM_CABLE);
        assertEquals(CableTier.Voltage.MV, EuNetwork.resolve(w2, 0, Y, 0).voltage());

        World w3 = newWorld();
        line(w3, BlockType.LV_TRANSFORMER, BlockType.ALUMINIUM_CABLE);
        assertEquals(CableTier.Voltage.LV, EuNetwork.resolve(w3, 0, Y, 0).voltage());
    }

    @Test
    void theTransformerNearestTheMachineDecides() {
        // machine - LV transformer - gold - MV transformer - aluminium - battery: arrives at LV.
        World w = newWorld();
        line(w, BlockType.LV_TRANSFORMER, BlockType.GOLD_CABLE, BlockType.MV_TRANSFORMER, BlockType.ALUMINIUM_CABLE);
        assertEquals(CableTier.Voltage.LV, EuNetwork.resolve(w, 0, Y, 0).voltage());
        // machine - MV transformer - aluminium: arrives at MV.
        World w2 = newWorld();
        line(w2, BlockType.MV_TRANSFORMER, BlockType.ALUMINIUM_CABLE);
        assertEquals(CableTier.Voltage.MV, EuNetwork.resolve(w2, 0, Y, 0).voltage());
    }

    @Test
    void mvMachineOnHvExplodes() {
        World w = newWorld();
        line(w, BlockType.ALUMINIUM_CABLE);
        w.setBlock(1, Y, 0, BlockType.STEEL_TANK);
        w.getOrCreateSteelTank(1, Y, 0).fill(Fluid.SULFURIC_ACID, 1000);
        w.setBlock(0, Y, 0, BlockType.CHEMICAL_REACTOR);
        ((ChemicalReactorEntity) w.blockEntityAt(0, Y, 0)).setSlot(ElectricMachineEntity.SLOT_INPUT,
                BlockType.CRUSHED_COPPER, 1);
        w.tickBlockEntities(0.05f);
        assertNotEquals(BlockType.CHEMICAL_REACTOR, w.getBlock(0, Y, 0));
        assertEquals(1, w.takeOverloadBlasts().size());
    }

    @Test
    void mvTransformerProtectsMvMachines() {
        World w = newWorld();
        line(w, BlockType.MV_TRANSFORMER, BlockType.ALUMINIUM_CABLE, BlockType.ALUMINIUM_CABLE);
        w.setBlock(1, Y, 0, BlockType.STEEL_TANK);
        w.getOrCreateSteelTank(1, Y, 0).fill(Fluid.SULFURIC_ACID, 1000);
        w.setBlock(0, Y, 0, BlockType.CHEMICAL_REACTOR);
        ChemicalReactorEntity r = (ChemicalReactorEntity) w.blockEntityAt(0, Y, 0);
        r.setSlot(ElectricMachineEntity.SLOT_INPUT, BlockType.CRUSHED_COPPER, 1);
        for (int i = 0; i < 400; i++) w.tickBlockEntities(0.05f);
        assertEquals(BlockType.CHEMICAL_REACTOR, w.getBlock(0, Y, 0));
        assertEquals(1, r.countOf(ElectricMachineEntity.SLOT_OUTPUT));
        assertTrue(w.takeOverloadBlasts().isEmpty());
    }

    @Test
    void lvMachineBehindAnMvTransformerStillBlows() {
        World w = newWorld();
        line(w, BlockType.MV_TRANSFORMER, BlockType.ALUMINIUM_CABLE);
        w.setBlock(0, Y, 0, BlockType.ELECTRIC_MACERATOR);
        ((ElectricMaceratorEntity) w.blockEntityAt(0, Y, 0)).setSlot(ElectricMachineEntity.SLOT_INPUT,
                BlockType.IRON_ORE, 1);
        w.tickBlockEntities(0.05f);
        assertNotEquals(BlockType.ELECTRIC_MACERATOR, w.getBlock(0, Y, 0), "MV is still too much for LV");
    }

    @Test
    void mvUpgradedMachineAcceptsMvButNotHv() {
        World w = newWorld();
        line(w, BlockType.MV_TRANSFORMER, BlockType.ALUMINIUM_CABLE);
        w.setBlock(0, Y, 0, BlockType.ELECTRIC_MACERATOR);
        ElectricMaceratorEntity m = (ElectricMaceratorEntity) w.blockEntityAt(0, Y, 0);
        m.installMvUpgrade();
        m.setSlot(ElectricMachineEntity.SLOT_INPUT, BlockType.IRON_ORE, 1);
        for (int i = 0; i < 200; i++) w.tickBlockEntities(0.05f);
        assertEquals(2, m.countOf(ElectricMachineEntity.SLOT_OUTPUT));

        World w2 = newWorld();
        line(w2, BlockType.ALUMINIUM_CABLE);
        w2.setBlock(0, Y, 0, BlockType.ELECTRIC_MACERATOR);
        ElectricMaceratorEntity m2 = (ElectricMaceratorEntity) w2.blockEntityAt(0, Y, 0);
        m2.installMvUpgrade();
        m2.setSlot(ElectricMachineEntity.SLOT_INPUT, BlockType.IRON_ORE, 1);
        w2.tickBlockEntities(0.05f);
        assertNotEquals(BlockType.ELECTRIC_MACERATOR, w2.getBlock(0, Y, 0));
    }

    @Test
    void recipes() {
        BlockType a = BlockType.ALUMINUM_INGOT, I = BlockType.IRON_INGOT, A = BlockType.GOLD_INGOT, N = null;
        assertEquals(BlockType.MV_TRANSFORMER, Crafting.match3x3(new BlockType[]{a, a, a, I, I, I, A, A, A}).output());
        var cable = Crafting.match3x3(new BlockType[]{N, a, N, N, a, N, N, a, N});
        assertEquals(BlockType.ALUMINIUM_CABLE, cable.output());
        assertEquals(6, cable.outputAmount());
        assertEquals(CableTier.Voltage.MV, EuNetwork.transformerOutput(BlockType.MV_TRANSFORMER));
        assertFalse(CableTier.isCable(BlockType.MV_TRANSFORMER));
    }
}
