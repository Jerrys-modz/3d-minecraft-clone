package com.minecraftclone.world;

import com.minecraftclone.player.Crafting;
import com.minecraftclone.world.gen.OilDeposits;
import com.minecraftclone.world.gen.WorldGenSettings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** Oil deposits, the Oil Pump, the Distillation Tower and fluid-fuelled generators. */
class OilTest {

    @TempDir
    Path dir;

    private static final int Y = 100;

    private World newWorld() {
        World world = new World(42, new WorldGenSettings(), null,
                dir.resolve("w" + System.nanoTime()), DimensionType.OVERWORLD, true);
        world.ensureChunk(0, 0);
        for (int x = 0; x <= 8; x++)
            for (int y = 1; y <= 103; y++)
                for (int z = 0; z < 12; z++) world.setBlock(x, y, z, y == Y - 1 ? BlockType.STONE : BlockType.AIR);
        return world;
    }

    private static BatteryBlockEntity battery(World w, int x, int y, int z) {
        w.setBlock(x, y, z, BlockType.BATTERY_BLOCK);
        BatteryBlockEntity b = w.getOrCreateBatteryBlock(x, y, z);
        b.setSlot(BatteryBlockEntity.SLOT_BATTERIES, BlockType.MV_BATTERY, 4);
        b.chargeEU(b.capacity());
        return b;
    }

    private static FluidTankEntity tank(World w, int x, int y, int z) {
        w.setBlock(x, y, z, BlockType.STEEL_TANK);
        return w.getOrCreateSteelTank(x, y, z);
    }

    // ------------------------------------------------------------------
    // Deposits
    // ------------------------------------------------------------------

    @Test
    void depositsAreDeterministicDeepAndUncommon() {
        OilDeposits a = new OilDeposits(42), b = new OilDeposits(42);
        int chunksWithOil = 0;
        for (int cx = 0; cx < 60; cx++) {
            for (int cz = 0; cz < 60; cz++) {
                boolean any = false;
                for (int y = 0; y < 128 && !any; y++) {
                    int wx = cx * 16 + 8, wz = cz * 16 + 8;
                    for (int dx = -4; dx <= 4 && !any; dx++) {
                        if (a.oilAt(wx + dx, y, wz)) {
                            any = true;
                            assertTrue(y >= OilDeposits.MIN_Y - 3 && y <= OilDeposits.MAX_Y + 3, "y=" + y);
                        }
                        assertEquals(a.oilAt(wx + dx, y, wz), b.oilAt(wx + dx, y, wz));
                    }
                }
                if (any) chunksWithOil++;
            }
        }
        double share = chunksWithOil / 3600.0;
        assertTrue(share > 0.10 && share < 0.25, "share=" + share);
    }

    @Test
    void generatedTerrainContainsOilDeposits() {
        World w = new World(42, new WorldGenSettings(), null, dir.resolve("gen"), DimensionType.OVERWORLD, true);
        OilDeposits oil = new OilDeposits(42);
        int found = 0;
        for (int cx = 0; cx < 12 && found == 0; cx++) {
            for (int cz = 0; cz < 12 && found == 0; cz++) {
                w.ensureChunk(cx, cz);
                for (int y = 5; y <= 33; y++)
                    for (int x = 0; x < 16; x++)
                        for (int z = 0; z < 16; z++)
                            if (w.getBlock(cx * 16 + x, y, cz * 16 + z) == BlockType.OIL_DEPOSIT) found++;
            }
        }
        assertTrue(found > 0, "some chunk in a 12x12 area should have oil");
    }

    // ------------------------------------------------------------------
    // Pump
    // ------------------------------------------------------------------

    @Test
    void pumpDrainsDepositsIntoATankAndLeavesStone() {
        World w = newWorld();
        w.setBlock(4, 20, 4, BlockType.OIL_DEPOSIT);
        w.setBlock(5, 20, 4, BlockType.OIL_DEPOSIT);
        w.setBlock(4, Y, 4, BlockType.OIL_PUMP);
        battery(w, 3, Y, 4);
        FluidTankEntity out = tank(w, 5, Y, 4);
        OilPumpEntity pump = (OilPumpEntity) w.blockEntityAt(4, Y, 4);

        float perDeposit = OilPumpEntity.CYCLE_SECONDS * OilPumpEntity.MB_PER_DEPOSIT / OilPumpEntity.MB_PER_CYCLE;
        for (float t = 0; t < perDeposit + 1f; t += 0.05f) w.tickBlockEntities(0.05f);

        assertEquals(BlockType.STONE, w.getBlock(4, 20, 4), "the drained deposit turns to stone");
        assertEquals(BlockType.OIL_DEPOSIT, w.getBlock(5, 20, 4));
        assertEquals(Fluid.OIL, out.fluid());
        assertTrue(out.amount() >= OilPumpEntity.MB_PER_DEPOSIT, "amount=" + out.amount());

        for (float t = 0; t < perDeposit + 6f; t += 0.05f) w.tickBlockEntities(0.05f);
        assertEquals(BlockType.STONE, w.getBlock(5, 20, 4));
        assertEquals(2 * OilPumpEntity.MB_PER_DEPOSIT, out.amount());
        for (int i = 0; i < 200; i++) w.tickBlockEntities(0.05f);
        assertFalse(pump.hasDeposit());
        assertEquals(2 * OilPumpEntity.MB_PER_DEPOSIT, out.amount(), "nothing left to pump");
    }

    @Test
    void pumpIgnoresOilOutsideItsRadius() {
        World w = newWorld();
        w.setBlock(4 + OilPumpEntity.RADIUS + 1, 20, 4, BlockType.OIL_DEPOSIT);
        w.setBlock(4, Y, 4, BlockType.OIL_PUMP);
        w.tickBlockEntities(0.05f);
        assertFalse(((OilPumpEntity) w.blockEntityAt(4, Y, 4)).hasDeposit());
    }

    @Test
    void pumpWithoutATankDoesNothing() {
        World w = newWorld();
        w.setBlock(4, 20, 4, BlockType.OIL_DEPOSIT);
        w.setBlock(4, Y, 4, BlockType.OIL_PUMP);
        BatteryBlockEntity b = battery(w, 3, Y, 4);
        float before = b.euStored();
        for (int i = 0; i < 200; i++) w.tickBlockEntities(0.05f);
        assertEquals(before, b.euStored(), 0.01f, "no tank, no EU spent");
        assertEquals(BlockType.OIL_DEPOSIT, w.getBlock(4, 20, 4));
    }

    // ------------------------------------------------------------------
    // Distillation
    // ------------------------------------------------------------------

    @Test
    void towerSplitsOilIntoDieselAndHeavyFuel() {
        World w = newWorld();
        w.setBlock(4, Y, 4, BlockType.DISTILLATION_TOWER);
        battery(w, 4, Y + 1, 4);
        FluidTankEntity oil = tank(w, 3, Y, 4);
        oil.fill(Fluid.OIL, 2000);
        FluidTankEntity a = tank(w, 5, Y, 4);       // both outputs start empty -
        FluidTankEntity b = tank(w, 4, Y, 5);       // they must not pick the same tank
        for (float t = 0; t < DistillationTowerEntity.BATCH_SECONDS * 2 + 1f; t += 0.05f) w.tickBlockEntities(0.05f);

        assertEquals(0, oil.amount());
        FluidTankEntity diesel = a.fluid() == Fluid.DIESEL ? a : b;
        FluidTankEntity heavy = diesel == a ? b : a;
        assertEquals(Fluid.DIESEL, diesel.fluid());
        assertEquals(2 * DistillationTowerEntity.DIESEL_PER_BATCH, diesel.amount());
        assertEquals(Fluid.HEAVY_FUEL, heavy.fluid());
        assertEquals(2 * DistillationTowerEntity.HEAVY_PER_BATCH, heavy.amount());
    }

    @Test
    void towerWaitsForASecondOutputTank() {
        World w = newWorld();
        w.setBlock(4, Y, 4, BlockType.DISTILLATION_TOWER);
        battery(w, 4, Y + 1, 4);
        tank(w, 3, Y, 4).fill(Fluid.OIL, 1000);
        FluidTankEntity only = tank(w, 5, Y, 4);
        for (int i = 0; i < 400; i++) w.tickBlockEntities(0.05f);
        assertNull(only.fluid());
    }

    // ------------------------------------------------------------------
    // Fuel
    // ------------------------------------------------------------------

    @Test
    void refiningIsWorthIt() {
        float crude = 10 * Fluid.OIL.burnSecondsPer100mB();
        float refined = 6 * Fluid.DIESEL.burnSecondsPer100mB() + 3 * Fluid.HEAVY_FUEL.burnSecondsPer100mB();
        assertTrue(refined > 2 * crude, crude + " vs " + refined);
        assertEquals(0f, Fluid.WATER.burnSecondsPer100mB());
    }

    @Test
    void generatorPrefersDieselOverLava() {
        World w = newWorld();
        w.setBlock(4, Y, 4, BlockType.COAL_GENERATOR);
        FluidTankEntity lava = tank(w, 3, Y, 4);
        lava.fill(Fluid.LAVA, 1000);
        FluidTankEntity diesel = tank(w, 5, Y, 4);
        diesel.fill(Fluid.DIESEL, 1000);
        w.tickBlockEntities(0.05f);
        assertEquals(1000, lava.amount());
        assertEquals(900, diesel.amount());
    }

    @Test
    void bronzePipesCarryOil() {
        assertTrue(FluidPipeTier.BRONZE.carries(Fluid.OIL));
        assertTrue(FluidPipeTier.BRONZE.carries(Fluid.DIESEL));
        assertFalse(FluidPipeTier.BRONZE.carries(Fluid.LAVA));
    }

    @Test
    void recipesAndCells() {
        BlockType S = BlockType.STAINLESS_STEEL_PLATE, I = BlockType.INTEGRATED_CIRCUIT, Q = BlockType.STEEL_INGOT,
                M = BlockType.MV_CASING, G = BlockType.GLASS, N = null;
        assertEquals(BlockType.OIL_PUMP, Crafting.match3x3(new BlockType[]{S, I, S, Q, M, Q, Q, N, Q}).output());
        assertEquals(BlockType.DISTILLATION_TOWER, Crafting.match3x3(new BlockType[]{S, G, S, I, M, I, S, G, S}).output());
        assertEquals(BlockType.DIESEL_CELL, Fluid.cellFor(Fluid.DIESEL));
        assertEquals(Fluid.OIL, Fluid.inCell(BlockType.OIL_CELL));
        assertTrue(BlockEntities.isRegistered(OilPumpEntity.TYPE));
        assertTrue(BlockEntities.isRegistered(DistillationTowerEntity.TYPE));
    }
}
