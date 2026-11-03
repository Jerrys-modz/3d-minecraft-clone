package com.minecraftclone.world;

import com.minecraftclone.player.Crafting;
import com.minecraftclone.world.gen.WorldGenSettings;
import com.minecraftclone.world.pipes.PipeType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** Steel tanks, tin cells, fluid pipes, and washers drawing tank water. */
class FluidSystemTest {

    @TempDir
    Path dir;

    private static final int Y = 100;

    private World newWorld() {
        World world = new World(42, new WorldGenSettings(), null,
                dir.resolve("w" + System.nanoTime()), DimensionType.OVERWORLD, true);
        world.ensureChunk(0, 0);
        for (int x = -3; x <= 3; x++)
            for (int y = 97; y <= 103; y++)
                for (int z = -3; z < 12; z++) world.setBlock(x, y, z, BlockType.AIR);
        return world;
    }

    // ------------------------------------------------------------------
    // Tank
    // ------------------------------------------------------------------

    @Test
    void tankHoldsOneFluidUpToCapacity() {
        FluidTankEntity t = new FluidTankEntity();
        assertNull(t.fluid());
        assertEquals(1000, t.fill(Fluid.WATER, 1000));
        assertEquals(0, t.fill(Fluid.LAVA, 1000), "fluids don't mix");
        assertEquals(FluidTankEntity.CAPACITY_MB - 1000, t.fill(Fluid.WATER, 1_000_000));
        assertEquals(FluidTankEntity.CAPACITY_MB, t.amount());
        assertFalse(t.canFill(Fluid.WATER, 1));
    }

    @Test
    void drainingToZeroForgetsTheFluid() {
        FluidTankEntity t = new FluidTankEntity();
        t.fill(Fluid.LAVA, 500);
        assertEquals(500, t.drain(Fluid.LAVA, 1000));
        assertNull(t.fluid());
        assertEquals(1000, t.fill(Fluid.WATER, 1000), "an emptied tank takes a new fluid");
    }

    @Test
    void tankSaveRoundTrip() throws Exception {
        FluidTankEntity t = new FluidTankEntity();
        t.fill(Fluid.LAVA, 4200);
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        t.writeTo(new DataOutputStream(buf));
        FluidTankEntity loaded = new FluidTankEntity();
        loaded.readFrom(new DataInputStream(new ByteArrayInputStream(buf.toByteArray())));
        assertEquals(Fluid.LAVA, loaded.fluid());
        assertEquals(4200, loaded.amount());
        assertTrue(BlockEntities.isRegistered(FluidTankEntity.TYPE));
    }

    @Test
    void placingATankCreatesItsEntity() {
        World w = newWorld();
        w.setBlock(0, Y, 0, BlockType.STEEL_TANK);
        assertInstanceOf(FluidTankEntity.class, w.blockEntityAt(0, Y, 0));
    }

    // ------------------------------------------------------------------
    // Cells
    // ------------------------------------------------------------------

    @Test
    void cellsMapToFluids() {
        assertEquals(BlockType.WATER_CELL, Fluid.cellFor(Fluid.WATER));
        assertEquals(Fluid.LAVA, Fluid.inCell(BlockType.LAVA_CELL));
        assertNull(Fluid.inCell(BlockType.TIN_CELL));
        assertTrue(Fluid.isCell(BlockType.TIN_CELL));
        assertEquals(Fluid.WATER, Fluid.ofBlock(BlockType.WATER_SOURCE));
        assertEquals(Fluid.LAVA, Fluid.ofBlock(BlockType.LAVA_SOURCE));
        assertNull(Fluid.ofBlock(BlockType.STONE));
    }

    @Test
    void recipes() {
        BlockType P = BlockType.STEEL_PLATE, G = BlockType.GLASS, E = BlockType.BRONZE_INGOT,
                T = BlockType.TIN_PLATE, N = null;
        var tank = Crafting.match3x3(new BlockType[]{P, P, P, P, G, P, P, P, P});
        var pipe = Crafting.match3x3(new BlockType[]{N, N, N, E, G, E, N, N, N});
        var cell = Crafting.match3x3(new BlockType[]{N, T, N, T, G, T, N, T, N});
        assertEquals(BlockType.STEEL_TANK, tank.output());
        assertEquals(BlockType.FLUID_PIPE, pipe.output());
        assertEquals(4, pipe.outputAmount());
        assertEquals(BlockType.TIN_CELL, cell.output());
        assertEquals(2, cell.outputAmount());
    }

    // ------------------------------------------------------------------
    // Pipes and washers
    // ------------------------------------------------------------------

    @Test
    void fluidPipeIsTheFluidPipeType() {
        assertTrue(PipeType.FLUID.isImplemented());
        assertTrue(PipeType.FLUID.matches(BlockType.FLUID_PIPE));
        assertFalse(PipeType.ENERGY.matches(BlockType.FLUID_PIPE));
    }

    @Test
    void tankFoundThroughPipes() {
        World w = newWorld();
        for (int z = 1; z <= 3; z++) w.setBlock(0, Y, z, BlockType.FLUID_PIPE);
        w.setBlock(0, Y, 4, BlockType.STEEL_TANK);
        FluidTankEntity t = w.getOrCreateSteelTank(0, Y, 4);
        assertNull(FluidNetwork.findTankWith(w, 0, Y, 0, Fluid.WATER, 100));
        t.fill(Fluid.WATER, 1000);
        assertSame(t, FluidNetwork.findTankWith(w, 0, Y, 0, Fluid.WATER, 100));
        assertNull(FluidNetwork.findTankWith(w, 0, Y, 0, Fluid.LAVA, 100));
    }

    @Test
    void lvWasherRunsOnPipedTankWater() {
        World w = newWorld();
        w.setBlock(0, Y, 1, BlockType.FLUID_PIPE);
        w.setBlock(0, Y, 2, BlockType.STEEL_TANK);
        FluidTankEntity tank = w.getOrCreateSteelTank(0, Y, 2);
        tank.fill(Fluid.WATER, 250);                          // enough for 2 washes
        w.setBlock(1, Y, 0, BlockType.BATTERY_BLOCK);
        w.getOrCreateBatteryBlock(1, Y, 0).chargeEU(BatteryBlockEntity.MAX_EU);
        w.setBlock(0, Y, 0, BlockType.ELECTRIC_ORE_WASHER);
        ElectricOreWasherEntity wsh = (ElectricOreWasherEntity) w.blockEntityAt(0, Y, 0);
        wsh.setSlot(ElectricMachineEntity.SLOT_INPUT, BlockType.CRUSHED_COPPER, 5);

        for (int i = 0; i < 600; i++) w.tickBlockEntities(0.05f);

        assertEquals(2, wsh.countOf(ElectricMachineEntity.SLOT_OUTPUT), "stops when the tank runs dry");
        assertEquals(50, tank.amount());
        assertEquals(3, wsh.countOf(ElectricMachineEntity.SLOT_INPUT));
    }

    @Test
    void steamWasherDrawsTankWater() {
        World w = newWorld();
        w.setBlock(0, Y, 1, BlockType.STEEL_TANK);
        FluidTankEntity tank = w.getOrCreateSteelTank(0, Y, 1);
        tank.fill(Fluid.WATER, 1000);
        w.setBlock(0, Y, 0, BlockType.STEAM_ORE_WASHER);
        SteamOreWasherEntity wsh = w.getOrCreateSteamOreWasher(0, Y, 0);
        w.setBlock(1, Y, 0, BlockType.STEAM_BOILER);
        SteamBoilerEntity boiler = w.getOrCreateSteamBoiler(1, Y, 0);
        boiler.addFuel(BlockType.COAL, 16);
        wsh.setSlot(SteamOreWasherEntity.SLOT_INPUT, BlockType.CRUSHED_COPPER, 2);

        for (int i = 0; i < 1200; i++) w.tickBlockEntities(0.05f);

        assertEquals(BlockType.COPPER_DUST, wsh.typeOf(SteamOreWasherEntity.SLOT_OUTPUT));
        assertEquals(2, wsh.countOf(SteamOreWasherEntity.SLOT_OUTPUT));
        assertEquals(1000 - 2 * SteamOreWasherEntity.WATER_PER_WASH, tank.amount());
    }
}
