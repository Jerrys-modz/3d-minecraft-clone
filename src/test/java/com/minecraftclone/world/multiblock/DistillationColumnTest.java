package com.minecraftclone.world.multiblock;

import com.minecraftclone.player.Crafting;
import com.minecraftclone.world.BatteryBlockEntity;
import com.minecraftclone.world.BlockEntities;
import com.minecraftclone.world.BlockType;
import com.minecraftclone.world.DimensionType;
import com.minecraftclone.world.DistillationTowerEntity;
import com.minecraftclone.world.Fluid;
import com.minecraftclone.world.FluidTankEntity;
import com.minecraftclone.world.World;
import com.minecraftclone.world.gen.WorldGenSettings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** The multi-block distillation column: formation, height, and parallel batches. */
class DistillationColumnTest {

    @TempDir
    Path dir;

    private static final int X = 4, Y = 100, Z = 4;

    private World newWorld() {
        World w = new World(42, new WorldGenSettings(), null,
                dir.resolve("w" + System.nanoTime()), DimensionType.OVERWORLD, true);
        w.ensureChunk(0, 0);
        for (int x = 0; x < 12; x++)
            for (int y = 96; y < 114; y++)
                for (int z = 0; z < 12; z++) w.setBlock(x, y, z, BlockType.AIR);
        return w;
    }

    /** A 3x3 column with {@code storeys} of hollow core; controller on the south face of the first storey. */
    private static void build(World w, int storeys) {
        for (int y = 0; y < storeys + 2; y++)
            for (int x = 0; x < 3; x++)
                for (int z = 0; z < 3; z++) {
                    boolean core = x == 1 && z == 1 && y >= 1 && y <= storeys;
                    if (!core) w.setBlock(X + x, Y + y, Z + z, BlockType.DISTILLATION_CASING);
                }
        w.setBlock(X + 1, Y + 1, Z, BlockType.DISTILLATION_CONTROLLER);
    }

    private static DistillationColumnEntity controller(World w) {
        return w.blockEntityAt(X + 1, Y + 1, Z) instanceof DistillationColumnEntity c ? c : null;
    }

    @Test
    void formsAtAnyHeightAndCountsStoreys() {
        for (int storeys : new int[]{1, 3, DistillationColumnDefinition.MAX_STOREYS}) {
            World w = newWorld();
            build(w, storeys);
            DistillationColumnEntity c = controller(w);
            assertNotNull(c, storeys + " storeys should form");
            assertEquals(storeys, c.storeys());
        }
    }

    @Test
    void blockingTheCoreShortensOrBreaksTheColumn() {
        World w = newWorld();
        build(w, 3);
        // Plugging storey 2 leaves a valid 1-storey column below it.
        w.setBlock(X + 1, Y + 2, Z + 1, BlockType.DISTILLATION_CASING);
        assertEquals(1, controller(w).storeys());
        // Plugging the cell the controller faces leaves no core at all.
        w.setBlock(X + 1, Y + 1, Z + 1, BlockType.DISTILLATION_CASING);
        assertNull(controller(w));
    }

    @Test
    void eachStoreyRunsABatchPerCycle() {
        World w = newWorld();
        build(w, 3);
        int cx = X + 1, cy = Y + 1, cz = Z;
        // Power at the controller's open (south) face; fluids at the casing below it.
        w.setBlock(cx, cy, cz - 1, BlockType.BATTERY_BLOCK);
        BatteryBlockEntity b = w.getOrCreateBatteryBlock(cx, cy, cz - 1);
        b.setSlot(BatteryBlockEntity.SLOT_BATTERIES, BlockType.MV_BATTERY, 8);
        b.chargeEU(b.capacity());
        for (int dz = 1; dz <= 3; dz++) w.setBlock(cx, cy - 1, cz - dz, BlockType.FLUID_PIPE);
        FluidTankEntity oil = tank(w, cx - 1, cy - 1, cz - 1);
        oil.fill(Fluid.OIL, 10_000);
        FluidTankEntity d = tank(w, cx + 1, cy - 1, cz - 2);
        FluidTankEntity h = tank(w, cx - 1, cy - 1, cz - 3);

        assertNotNull(controller(w));
        for (float t = 0; t < DistillationTowerEntity.BATCH_SECONDS + 0.5f; t += 0.05f) w.tickBlockEntities(0.05f);

        assertEquals(3, controller(w).lastBatches(), "3 storeys -> 3 batches in one cycle");
        assertEquals(10_000 - 3 * DistillationTowerEntity.OIL_PER_BATCH, oil.amount());
        FluidTankEntity diesel = d.fluid() == Fluid.DIESEL ? d : h;
        assertEquals(3 * DistillationTowerEntity.DIESEL_PER_BATCH, diesel.amount());
    }

    private static FluidTankEntity tank(World w, int x, int y, int z) {
        w.setBlock(x, y, z, BlockType.STEEL_TANK);
        return w.getOrCreateSteelTank(x, y, z);
    }

    @Test
    void recipesAndRegistration() {
        assertTrue(BlockEntities.isRegistered(DistillationColumnEntity.TYPE));
        BlockType s = BlockType.STAINLESS_STEEL_INGOT, p = BlockType.STAINLESS_STEEL_PLATE, G = BlockType.GLASS;
        assertEquals(BlockType.DISTILLATION_CASING, Crafting.match3x3(new BlockType[]{s, p, s, p, G, p, s, p, s}).output());
        BlockType i = BlockType.INTEGRATED_CIRCUIT, M = BlockType.MV_CASING;
        assertEquals(BlockType.DISTILLATION_CONTROLLER, Crafting.match3x3(new BlockType[]{i, G, i, p, M, p, i, G, i}).output());
    }
}
