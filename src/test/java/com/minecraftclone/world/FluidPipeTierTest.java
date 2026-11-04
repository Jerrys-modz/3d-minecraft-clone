package com.minecraftclone.world;

import com.minecraftclone.player.Crafting;
import com.minecraftclone.world.gen.WorldGenSettings;
import com.minecraftclone.world.pipes.PipeType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** Bronze/steel/stainless fluid pipes and lava-fired Coal Generators. */
class FluidPipeTierTest {

    @TempDir
    Path dir;

    private static final int Y = 100;

    private World newWorld() {
        World world = new World(42, new WorldGenSettings(), null,
                dir.resolve("w" + System.nanoTime()), DimensionType.OVERWORLD, true);
        world.ensureChunk(0, 0);
        for (int x = 0; x <= 4; x++)
            for (int y = 97; y <= 103; y++)
                for (int z = 0; z < 10; z++) world.setBlock(x, y, z, BlockType.AIR);
        return world;
    }

    /** Pipes at z=1..n of the given kinds, then a tank holding {@code mB} of {@code f}. */
    private static FluidTankEntity line(World w, Fluid f, int mB, BlockType... pipes) {
        for (int i = 0; i < pipes.length; i++) w.setBlock(0, Y, 1 + i, pipes[i]);
        int tz = 1 + pipes.length;
        w.setBlock(0, Y, tz, BlockType.STEEL_TANK);
        FluidTankEntity t = w.getOrCreateSteelTank(0, Y, tz);
        t.fill(f, mB);
        return t;
    }

    @Test
    void tiersCarryWhatTheirMaterialSurvives() {
        assertTrue(FluidPipeTier.BRONZE.carries(Fluid.WATER));
        assertFalse(FluidPipeTier.BRONZE.carries(Fluid.LAVA));
        assertTrue(FluidPipeTier.STEEL.carries(Fluid.LAVA));
        assertFalse(FluidPipeTier.STEEL.carries(Fluid.SULFURIC_ACID));
        assertTrue(FluidPipeTier.STAINLESS.carries(Fluid.SULFURIC_ACID));
        assertEquals(FluidPipeTier.STAINLESS, FluidPipeTier.minimumFor(Fluid.SULFURIC_ACID));
        for (FluidPipeTier t : FluidPipeTier.values()) assertTrue(PipeType.FLUID.matches(t.block));
    }

    @Test
    void lavaNeedsSteelAndAcidNeedsStainless() {
        World w = newWorld();
        line(w, Fluid.LAVA, 1000, BlockType.FLUID_PIPE, BlockType.FLUID_PIPE);
        assertNull(FluidNetwork.findTankWith(w, 0, Y, 0, Fluid.LAVA, 100), "bronze can't take the heat");

        World w2 = newWorld();
        FluidTankEntity lava = line(w2, Fluid.LAVA, 1000, BlockType.FLUID_PIPE_STEEL, BlockType.FLUID_PIPE_STEEL);
        assertSame(lava, FluidNetwork.findTankWith(w2, 0, Y, 0, Fluid.LAVA, 100));

        World w3 = newWorld();
        line(w3, Fluid.SULFURIC_ACID, 1000, BlockType.FLUID_PIPE_STEEL);
        assertNull(FluidNetwork.findTankWith(w3, 0, Y, 0, Fluid.SULFURIC_ACID, 100), "acid corrodes steel");

        World w4 = newWorld();
        FluidTankEntity acid = line(w4, Fluid.SULFURIC_ACID, 1000, BlockType.FLUID_PIPE_STAINLESS);
        assertSame(acid, FluidNetwork.findTankWith(w4, 0, Y, 0, Fluid.SULFURIC_ACID, 100));
    }

    @Test
    void oneWeakSegmentLimitsTheWholeLine() {
        World w = newWorld();
        line(w, Fluid.LAVA, 1000, BlockType.FLUID_PIPE_STEEL, BlockType.FLUID_PIPE, BlockType.FLUID_PIPE_STEEL);
        assertNull(FluidNetwork.findTankWith(w, 0, Y, 0, Fluid.LAVA, 100));
    }

    @Test
    void adjacentTanksNeedNoPipe() {
        World w = newWorld();
        w.setBlock(0, Y, 1, BlockType.STEEL_TANK);
        FluidTankEntity t = w.getOrCreateSteelTank(0, Y, 1);
        t.fill(Fluid.SULFURIC_ACID, 500);
        assertSame(t, FluidNetwork.findTankWith(w, 0, Y, 0, Fluid.SULFURIC_ACID, 100));
    }

    @Test
    void generatorBurnsPipedLavaWhenItHasNoSolidFuel() {
        World w = newWorld();
        FluidTankEntity lava = line(w, Fluid.LAVA, 1000, BlockType.FLUID_PIPE_STEEL);
        w.setBlock(0, Y, 0, BlockType.COAL_GENERATOR);
        CoalGeneratorEntity g = (CoalGeneratorEntity) w.blockEntityAt(0, Y, 0);

        w.tickBlockEntities(1f);

        assertTrue(g.isBurning());
        assertEquals(1000 - CoalGeneratorEntity.LAVA_MB_PER_BURN, lava.amount());
        assertTrue(g.euStored() > 0f);
    }

    @Test
    void solidFuelComesFirst() {
        World w = newWorld();
        FluidTankEntity lava = line(w, Fluid.LAVA, 1000, BlockType.FLUID_PIPE_STEEL);
        w.setBlock(0, Y, 0, BlockType.COAL_GENERATOR);
        CoalGeneratorEntity g = (CoalGeneratorEntity) w.blockEntityAt(0, Y, 0);
        g.insert(CoalGeneratorEntity.SLOT_FUEL, BlockType.COAL, 1);
        w.tickBlockEntities(1f);
        assertEquals(1000, lava.amount(), "coal burns before lava is touched");
    }

    @Test
    void recipes() {
        BlockType G = BlockType.GLASS, N = null;
        assertEquals(BlockType.FLUID_PIPE_STEEL, Crafting.match3x3(new BlockType[]{
                N, N, N, BlockType.STEEL_INGOT, G, BlockType.STEEL_INGOT, N, N, N}).output());
        assertEquals(BlockType.FLUID_PIPE_STAINLESS, Crafting.match3x3(new BlockType[]{
                N, N, N, BlockType.STAINLESS_STEEL_INGOT, G, BlockType.STAINLESS_STEEL_INGOT, N, N, N}).output());
    }
}
