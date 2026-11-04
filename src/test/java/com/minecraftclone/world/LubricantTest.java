package com.minecraftclone.world;

import com.minecraftclone.world.gen.WorldGenSettings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** Lubricant from distillation, and lubricated machines running faster. */
class LubricantTest {

    @TempDir
    Path dir;

    private static final int Y = 100;

    private World newWorld() {
        World w = new World(42, new WorldGenSettings(), null,
                dir.resolve("w" + System.nanoTime()), DimensionType.OVERWORLD, true);
        w.ensureChunk(0, 0);
        for (int x = 0; x < 10; x++)
            for (int y = 97; y < 104; y++)
                for (int z = 0; z < 10; z++) w.setBlock(x, y, z, BlockType.AIR);
        return w;
    }

    private static void battery(World w, int x, int y, int z) {
        w.setBlock(x, y, z, BlockType.BATTERY_BLOCK);
        BatteryBlockEntity b = w.getOrCreateBatteryBlock(x, y, z);
        b.setSlot(BatteryBlockEntity.SLOT_BATTERIES, BlockType.MV_BATTERY, 4);
        b.chargeEU(b.capacity());
    }

    private static FluidTankEntity tank(World w, int x, int y, int z) {
        w.setBlock(x, y, z, BlockType.STEEL_TANK);
        return w.getOrCreateSteelTank(x, y, z);
    }

    @Test
    void towerFillsALubricantTankWhenThereIsOne() {
        World w = newWorld();
        w.setBlock(4, Y, 4, BlockType.DISTILLATION_TOWER);
        battery(w, 4, Y + 1, 4);
        tank(w, 3, Y, 4).fill(Fluid.OIL, 1000);
        FluidTankEntity a = tank(w, 5, Y, 4), b = tank(w, 4, Y, 5), c = tank(w, 4, Y, 3);
        for (float t = 0; t < DistillationTowerEntity.BATCH_SECONDS + 1f; t += 0.05f) w.tickBlockEntities(0.05f);
        int lube = 0;
        for (FluidTankEntity t : new FluidTankEntity[]{a, b, c}) {
            if (t.fluid() == Fluid.LUBRICANT) lube = t.amount();
        }
        assertEquals(DistillationTowerEntity.LUBRICANT_PER_BATCH, lube);
    }

    @Test
    void lubricantIsOptional() {
        World w = newWorld();
        w.setBlock(4, Y, 4, BlockType.DISTILLATION_TOWER);
        battery(w, 4, Y + 1, 4);
        FluidTankEntity oil = tank(w, 3, Y, 4);
        oil.fill(Fluid.OIL, 1000);
        tank(w, 5, Y, 4);
        tank(w, 4, Y, 5);
        for (float t = 0; t < DistillationTowerEntity.BATCH_SECONDS + 1f; t += 0.05f) w.tickBlockEntities(0.05f);
        assertEquals(0, oil.amount(), "runs with just two output tanks; lubricant is vented");
    }

    @Test
    void lubricatedMachineRunsFasterAndUsesLubricant() {
        World w = newWorld();
        // Two identical macerators; only the first has a lubricant tank beside it.
        w.setBlock(1, Y, 1, BlockType.ELECTRIC_MACERATOR);
        w.setBlock(1, Y, 6, BlockType.ELECTRIC_MACERATOR);
        battery(w, 0, Y, 1);
        battery(w, 0, Y, 6);
        FluidTankEntity lube = tank(w, 2, Y, 1);
        lube.fill(Fluid.LUBRICANT, 1000);
        ElectricMaceratorEntity fast = (ElectricMaceratorEntity) w.blockEntityAt(1, Y, 1);
        ElectricMaceratorEntity slow = (ElectricMaceratorEntity) w.blockEntityAt(1, Y, 6);
        fast.setSlot(ElectricMachineEntity.SLOT_INPUT, BlockType.IRON_ORE, 64);
        slow.setSlot(ElectricMachineEntity.SLOT_INPUT, BlockType.IRON_ORE, 64);

        for (float t = 0; t < ElectricMaceratorEntity.GRIND_SECONDS * 8; t += 0.05f) w.tickBlockEntities(0.05f);

        int fastDone = 64 - fast.countOf(ElectricMachineEntity.SLOT_INPUT);
        int slowDone = 64 - slow.countOf(ElectricMachineEntity.SLOT_INPUT);
        assertTrue(fast.isLubricated());
        assertFalse(slow.isLubricated());
        assertTrue(fastDone >= slowDone + 1, "fast=" + fastDone + " slow=" + slowDone);
        assertEquals(1000 - fastDone * ElectricMachineEntity.LUBRICANT_PER_OPERATION, lube.amount());
    }

    @Test
    void lubricantFlowsThroughBronzeAndHasACell() {
        assertTrue(FluidPipeTier.BRONZE.carries(Fluid.LUBRICANT));
        assertEquals(BlockType.LUBRICANT_CELL, Fluid.cellFor(Fluid.LUBRICANT));
    }
}
