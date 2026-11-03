package com.minecraftclone.world;

import com.minecraftclone.player.Crafting;
import com.minecraftclone.world.gen.WorldGenSettings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** The Chemical Reactor, sulfuric acid, acid leaching, and MV-rated machines. */
class ChemicalReactorTest {

    @TempDir
    Path dir;

    private static final int Y = 100;

    private World newWorld() {
        World world = new World(42, new WorldGenSettings(), null,
                dir.resolve("w" + System.nanoTime()), DimensionType.OVERWORLD, true);
        world.ensureChunk(0, 0);
        for (int x = 0; x <= 6; x++)
            for (int y = 97; y <= 103; y++)
                for (int z = 0; z < 12; z++) world.setBlock(x, y, z, BlockType.AIR);
        return world;
    }

    private static ChemicalReactorEntity poweredReactor() {
        ChemicalReactorEntity r = new ChemicalReactorEntity();
        BatteryBlockEntity b = new BatteryBlockEntity();
        b.setSlot(BatteryBlockEntity.SLOT_BATTERIES, BlockType.LV_BATTERY, 4);
        b.chargeEU(b.capacity());
        r.euSource = b;
        return r;
    }

    private static void run(ElectricMachineEntity m, float seconds) {
        for (float t = 0; t < seconds; t += 0.05f) m.tick(0.05f);
    }

    @Test
    void sulfurAndWaterMakeAcid() {
        ChemicalReactorEntity r = poweredReactor();
        FluidTankEntity water = new FluidTankEntity();
        water.fill(Fluid.WATER, 3000);
        FluidTankEntity acid = new FluidTankEntity();
        r.sourceOverride = water;
        r.destinationOverride = acid;
        r.setSlot(ElectricMachineEntity.SLOT_INPUT, BlockType.CRUSHED_SULFUR, 2);

        run(r, ChemicalReactorEntity.REACT_SECONDS * 2 + 1f);

        assertNull(r.typeOf(ElectricMachineEntity.SLOT_INPUT));
        assertNull(r.typeOf(ElectricMachineEntity.SLOT_OUTPUT), "acid goes to a tank, not a slot");
        assertEquals(1000, water.amount());
        assertEquals(Fluid.SULFURIC_ACID, acid.fluid());
        assertEquals(2000, acid.amount());
    }

    @Test
    void noWaterNoReaction() {
        ChemicalReactorEntity r = poweredReactor();
        r.sourceOverride = new FluidTankEntity();       // empty
        r.destinationOverride = new FluidTankEntity();
        r.setSlot(ElectricMachineEntity.SLOT_INPUT, BlockType.CRUSHED_SULFUR, 1);
        run(r, 10f);
        assertEquals(1, r.countOf(ElectricMachineEntity.SLOT_INPUT));
    }

    @Test
    void acidLeachingGivesPurifiedOreAndAGuaranteedByproduct() {
        ChemicalReactorEntity r = poweredReactor();
        FluidTankEntity acid = new FluidTankEntity();
        acid.fill(Fluid.SULFURIC_ACID, 500);              // 2 leaches
        r.sourceOverride = acid;
        r.setSlot(ElectricMachineEntity.SLOT_INPUT, BlockType.CRUSHED_COPPER, 3);

        run(r, ChemicalReactorEntity.REACT_SECONDS * 3 + 2f);

        assertEquals(BlockType.PURIFIED_COPPER, r.typeOf(ElectricMachineEntity.SLOT_OUTPUT));
        assertEquals(2, r.countOf(ElectricMachineEntity.SLOT_OUTPUT), "stops when the acid runs out");
        assertEquals(BlockType.TIN_DUST, r.typeOf(ElectricMachineEntity.SLOT_BYPRODUCT));
        assertEquals(2, r.countOf(ElectricMachineEntity.SLOT_BYPRODUCT), "a byproduct every leach");
        assertEquals(0, acid.amount());
        assertEquals(1, r.countOf(ElectricMachineEntity.SLOT_INPUT));
    }

    @Test
    void acceptsOnlyRecipeInputs() {
        ChemicalReactorEntity r = new ChemicalReactorEntity();
        assertTrue(r.acceptsInput(BlockType.CRUSHED_SULFUR));
        assertTrue(r.acceptsInput(BlockType.CRUSHED_TIN));
        assertFalse(r.acceptsInput(BlockType.DIRT));
        assertFalse(r.acceptsInput(BlockType.PURIFIED_TIN));
    }

    @Test
    void acidReactionThroughRealTanksAndPipes() {
        World w = newWorld();
        // reactor (0) - pipe (1) - water tank (2); acid tank adjacent on +x.
        w.setBlock(0, Y, 1, BlockType.FLUID_PIPE);
        w.setBlock(0, Y, 2, BlockType.STEEL_TANK);
        w.getOrCreateSteelTank(0, Y, 2).fill(Fluid.WATER, 1000);
        w.setBlock(1, Y, 0, BlockType.STEEL_TANK);
        FluidTankEntity acid = w.getOrCreateSteelTank(1, Y, 0);
        w.setBlock(0, Y + 1, 0, BlockType.BATTERY_BLOCK);
        w.getOrCreateBatteryBlock(0, Y + 1, 0).chargeEU(BatteryBlockEntity.MAX_EU);
        w.setBlock(0, Y, 0, BlockType.CHEMICAL_REACTOR);
        ChemicalReactorEntity r = (ChemicalReactorEntity) w.blockEntityAt(0, Y, 0);
        r.setSlot(ElectricMachineEntity.SLOT_INPUT, BlockType.CRUSHED_SULFUR, 1);

        for (int i = 0; i < 400; i++) w.tickBlockEntities(0.05f);

        assertEquals(1000, acid.amount());
        assertEquals(Fluid.SULFURIC_ACID, acid.fluid());
        assertNull(w.getOrCreateSteelTank(0, Y, 2).fluid(), "the water was used up");
    }

    @Test
    void reactorIsMvRatedAndSurvivesGoldCable() {
        World w = newWorld();
        w.setBlock(0, Y, 1, BlockType.GOLD_CABLE);
        w.setBlock(0, Y, 2, BlockType.BATTERY_BLOCK);
        w.getOrCreateBatteryBlock(0, Y, 2).chargeEU(BatteryBlockEntity.MAX_EU);
        w.setBlock(1, Y, 0, BlockType.STEEL_TANK);
        w.getOrCreateSteelTank(1, Y, 0).fill(Fluid.SULFURIC_ACID, 1000);
        w.setBlock(0, Y, 0, BlockType.CHEMICAL_REACTOR);
        ChemicalReactorEntity r = (ChemicalReactorEntity) w.blockEntityAt(0, Y, 0);
        r.setSlot(ElectricMachineEntity.SLOT_INPUT, BlockType.CRUSHED_COPPER, 1);

        for (int i = 0; i < 400; i++) w.tickBlockEntities(0.05f);

        assertEquals(BlockType.CHEMICAL_REACTOR, w.getBlock(0, Y, 0), "MV machines don't explode on gold");
        assertTrue(w.takeOverloadBlasts().isEmpty());
        assertEquals(1, r.countOf(ElectricMachineEntity.SLOT_OUTPUT));
    }

    @Test
    void recipeAndRegistration() {
        assertTrue(BlockEntities.isRegistered(ChemicalReactorEntity.TYPE));
        BlockType S = BlockType.STAINLESS_STEEL_PLATE, G = BlockType.GLASS, V = BlockType.VACUUM_TUBE,
                M = BlockType.MV_CASING;
        var r = Crafting.match3x3(new BlockType[]{S, G, S, V, M, V, S, S, S});
        assertNotNull(r);
        assertEquals(BlockType.CHEMICAL_REACTOR, r.output());
        assertEquals(BlockType.SULFURIC_ACID_CELL, Fluid.cellFor(Fluid.SULFURIC_ACID));
    }
}
