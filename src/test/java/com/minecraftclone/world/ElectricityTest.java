package com.minecraftclone.world;

import com.minecraftclone.player.Crafting;
import com.minecraftclone.player.ItemStack;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the Electric Age:
 * {@link CoalGeneratorEntity}, {@link BatteryBlockEntity},
 * {@link ElectricFurnaceEntity}, {@link CableTier},
 * and the matching crafting recipes.
 */
class ElectricityTest {

    private static final float DT = 0.05f; // 20 ticks per second

    // ------------------------------------------------------------------
    // BlockType registration
    // ------------------------------------------------------------------

    @Test
    void coalGeneratorBlockTypeExists() {
        assertNotNull(BlockType.COAL_GENERATOR);
    }

    @Test
    void copperCableBlockTypeExists() {
        assertNotNull(BlockType.COPPER_CABLE);
    }

    @Test
    void goldCableBlockTypeExists() {
        assertNotNull(BlockType.GOLD_CABLE);
    }

    @Test
    void electricFurnaceBlockTypeExists() {
        assertNotNull(BlockType.ELECTRIC_FURNACE);
    }

    @Test
    void batteryBlockBlockTypeExists() {
        assertNotNull(BlockType.BATTERY_BLOCK);
    }

    // ------------------------------------------------------------------
    // BlockEntity registry
    // ------------------------------------------------------------------

    @Test
    void coalGeneratorIsRegistered() {
        assertTrue(BlockEntities.isRegistered(CoalGeneratorEntity.TYPE));
    }

    @Test
    void electricFurnaceIsRegistered() {
        assertTrue(BlockEntities.isRegistered(ElectricFurnaceEntity.TYPE));
    }

    @Test
    void batteryBlockIsRegistered() {
        assertTrue(BlockEntities.isRegistered(BatteryBlockEntity.TYPE));
    }

    // ------------------------------------------------------------------
    // CableTier
    // ------------------------------------------------------------------

    @Test
    void copperCableHasLowerThroughputThanGold() {
        assertTrue(CableTier.COPPER.throughput < CableTier.GOLD.throughput,
                "Copper throughput must be lower than gold");
    }

    @Test
    void cableTierOfCopperCable() {
        assertEquals(CableTier.COPPER, CableTier.of(BlockType.COPPER_CABLE));
    }

    @Test
    void cableTierOfGoldCable() {
        assertEquals(CableTier.GOLD, CableTier.of(BlockType.GOLD_CABLE));
    }

    @Test
    void cableTierOfNonCableIsNull() {
        assertNull(CableTier.of(BlockType.STONE));
    }

    @Test
    void isCableTrueForCables() {
        assertTrue(CableTier.isCable(BlockType.COPPER_CABLE));
        assertTrue(CableTier.isCable(BlockType.GOLD_CABLE));
    }

    @Test
    void isCableFalseForNonCable() {
        assertFalse(CableTier.isCable(BlockType.IRON_ORE));
    }

    // ------------------------------------------------------------------
    // CoalGeneratorEntity — fuel consumption & EU production
    // ------------------------------------------------------------------

    @Test
    void newGeneratorHasNoEU() {
        CoalGeneratorEntity gen = new CoalGeneratorEntity();
        assertEquals(0f, gen.euStored(), 0.01f);
    }

    @Test
    void generatorDoesNotBurnWithNoFuel() {
        CoalGeneratorEntity gen = new CoalGeneratorEntity();
        gen.tick(DT);
        assertFalse(gen.isBurning());
        assertEquals(0f, gen.euStored(), 0.01f);
    }

    @Test
    void generatorStartsBurningAfterCoalInserted() {
        CoalGeneratorEntity gen = new CoalGeneratorEntity();
        assertTrue(gen.canInsert(0, BlockType.COAL));
        gen.insert(0, BlockType.COAL, 1);
        gen.tick(DT);
        assertTrue(gen.isBurning(), "Generator should start burning after coal is inserted");
    }

    @Test
    void generatorProducesEUWhileBurning() {
        CoalGeneratorEntity gen = new CoalGeneratorEntity();
        gen.insert(0, BlockType.COAL, 1);
        // Run for 1 second.
        for (int i = 0; i < 20; i++) gen.tick(DT);
        assertTrue(gen.euStored() > 0f, "Generator must produce EU while burning coal");
    }

    @Test
    void generatorEUDoesNotExceedMax() {
        CoalGeneratorEntity gen = new CoalGeneratorEntity();
        gen.insert(0, BlockType.COAL, 64);
        // Run long enough to fill the buffer.
        for (int i = 0; i < 2000; i++) gen.tick(DT);
        assertTrue(gen.euStored() <= CoalGeneratorEntity.MAX_EU + 0.01f,
                "Generator EU must not exceed MAX_EU");
    }

    @Test
    void generatorDrainEUReducesStorage() {
        CoalGeneratorEntity gen = new CoalGeneratorEntity();
        gen.insert(0, BlockType.COAL, 1);
        for (int i = 0; i < 20; i++) gen.tick(DT);
        float before = gen.euStored();
        float drawn  = gen.drainEU(50f);
        assertTrue(drawn > 0f && drawn <= 50f);
        assertEquals(before - drawn, gen.euStored(), 0.01f);
    }

    @Test
    void generatorCannotInsertNonFuel() {
        CoalGeneratorEntity gen = new CoalGeneratorEntity();
        assertFalse(gen.canInsert(0, BlockType.STONE),
                "Stone is not a valid fuel");
    }

    @Test
    void generatorCannotExtractFuel() {
        CoalGeneratorEntity gen = new CoalGeneratorEntity();
        gen.insert(0, BlockType.COAL, 1);
        assertFalse(gen.canExtract(0),
                "Players should not be able to pull fuel out of a running generator");
    }

    // ------------------------------------------------------------------
    // BatteryBlockEntity — charge / drain
    // ------------------------------------------------------------------

    @Test
    void newBatteryIsEmpty() {
        BatteryBlockEntity bat = new BatteryBlockEntity();
        assertEquals(0f, bat.euStored(), 0.01f);
    }

    @Test
    void batteryChargeEUIncreases() {
        BatteryBlockEntity bat = new BatteryBlockEntity();
        float taken = bat.chargeEU(1000f);
        assertEquals(1000f, taken, 0.01f);
        assertEquals(1000f, bat.euStored(), 0.01f);
    }

    @Test
    void batteryChargeDoesNotExceedMax() {
        BatteryBlockEntity bat = new BatteryBlockEntity();
        bat.chargeEU(BatteryBlockEntity.MAX_EU + 9999f);
        assertTrue(bat.euStored() <= BatteryBlockEntity.MAX_EU + 0.01f,
                "Battery must not exceed MAX_EU");
    }

    @Test
    void batteryDrainEUDecreases() {
        BatteryBlockEntity bat = new BatteryBlockEntity();
        bat.chargeEU(2000f);
        float drawn = bat.drainEU(500f);
        assertEquals(500f, drawn, 0.01f);
        assertEquals(1500f, bat.euStored(), 0.01f);
    }

    @Test
    void batteryDrainCannotGoNegative() {
        BatteryBlockEntity bat = new BatteryBlockEntity();
        bat.chargeEU(100f);
        float drawn = bat.drainEU(9999f);
        assertEquals(100f, drawn, 0.01f);
        assertEquals(0f, bat.euStored(), 0.01f);
    }

    @Test
    void batteryProgressFractionMatchesFill() {
        BatteryBlockEntity bat = new BatteryBlockEntity();
        bat.chargeEU(BatteryBlockEntity.MAX_EU / 2f);
        assertEquals(0.5f, bat.progressFraction(), 0.01f);
    }

    // ------------------------------------------------------------------
    // ElectricFurnaceEntity — slot layout & smelting logic
    // ------------------------------------------------------------------

    @Test
    void electricFurnaceHasThreeSlots() {
        assertEquals(3, ElectricFurnaceEntity.SLOT_COUNT);
    }

    @Test
    void electricFurnaceCanInsertSmeltableIntoInputSlot() {
        ElectricFurnaceEntity ef = new ElectricFurnaceEntity();
        assertTrue(ef.canInsert(ElectricFurnaceEntity.SLOT_INPUT, BlockType.IRON_ORE));
    }

    @Test
    void electricFurnaceCannotInsertIntoOutputSlot() {
        ElectricFurnaceEntity ef = new ElectricFurnaceEntity();
        assertFalse(ef.canInsert(ElectricFurnaceEntity.SLOT_OUTPUT, BlockType.IRON_ORE));
    }

    @Test
    void electricFurnaceCannotInsertNonSmeltable() {
        ElectricFurnaceEntity ef = new ElectricFurnaceEntity();
        assertFalse(ef.canInsert(ElectricFurnaceEntity.SLOT_INPUT, BlockType.DIRT));
    }

    @Test
    void electricFurnaceNoSmeltWithoutPower() {
        ElectricFurnaceEntity ef = new ElectricFurnaceEntity();
        ef.insert(ElectricFurnaceEntity.SLOT_INPUT, BlockType.IRON_ORE, 1);
        // Tick without an EU source attached.
        for (int i = 0; i < 200; i++) ef.tick(DT);
        assertNull(ef.typeOf(ElectricFurnaceEntity.SLOT_OUTPUT),
                "Electric furnace must not smelt without EU");
    }

    @Test
    void electricFurnaceSmeltsWithDirectGenerator() {
        // Wire up: generator feeds electric furnace directly (no cables).
        CoalGeneratorEntity gen = new CoalGeneratorEntity();
        gen.insert(0, BlockType.COAL, 64);
        // Prime the generator.
        for (int i = 0; i < 40; i++) gen.tick(DT);

        ElectricFurnaceEntity ef = new ElectricFurnaceEntity();
        // Directly inject the EU source so we don't need a real World.
        ef.euSource = gen;
        ef.insert(ElectricFurnaceEntity.SLOT_INPUT, BlockType.IRON_ORE, 1);

        // Run for 2× smelt time + margin; tick both so the generator keeps refilling.
        float runtime = ElectricFurnaceEntity.SMELT_SECONDS * 2f + 1f;
        int frames = (int)(runtime / DT) + 1;
        for (int i = 0; i < frames; i++) { gen.tick(DT); ef.tick(DT); }

        assertNotNull(ef.typeOf(ElectricFurnaceEntity.SLOT_OUTPUT),
                "Electric furnace must produce output when powered by a generator");
    }

    @Test
    void electricFurnaceSmeltsFasterThanPlainFurnace() {
        // Electric furnace should smelt in SMELT_SECONDS, plain furnace in SMELT_TIME.
        assertTrue(ElectricFurnaceEntity.SMELT_SECONDS < Furnace.SMELT_TIME,
                "Electric furnace smelt time must be shorter than plain furnace smelt time");
    }

    @Test
    void electricFurnaceSmeltsWithBattery() {
        // Battery-powered smelt — no generator.
        BatteryBlockEntity bat = new BatteryBlockEntity();
        bat.chargeEU(ElectricFurnaceEntity.EU_PER_SMELT * 3f); // plenty of charge

        ElectricFurnaceEntity ef = new ElectricFurnaceEntity();
        ef.euSource = bat;
        ef.insert(ElectricFurnaceEntity.SLOT_INPUT, BlockType.IRON_ORE, 1);

        float runtime = ElectricFurnaceEntity.SMELT_SECONDS * 2f + 0.5f;
        int frames = (int)(runtime / DT) + 1;
        for (int i = 0; i < frames; i++) ef.tick(DT);

        assertNotNull(ef.typeOf(ElectricFurnaceEntity.SLOT_OUTPUT),
                "Electric furnace must smelt when powered by a battery");
    }

    @Test
    void electricFurnaceDrainsEUFromGenerator() {
        CoalGeneratorEntity gen = new CoalGeneratorEntity();
        gen.insert(0, BlockType.COAL, 64);
        for (int i = 0; i < 40; i++) gen.tick(DT);
        float euBefore = gen.euStored();

        ElectricFurnaceEntity ef = new ElectricFurnaceEntity();
        ef.euSource = gen;
        ef.insert(ElectricFurnaceEntity.SLOT_INPUT, BlockType.IRON_ORE, 1);

        // Tick the furnace (but not the generator) so the buffer only shrinks.
        for (int i = 0; i < 10; i++) ef.tick(DT);

        assertTrue(gen.euStored() < euBefore,
                "Electric furnace must drain EU from the generator during operation");
    }

    @Test
    void electricFurnaceExtractOutput() {
        BatteryBlockEntity bat = new BatteryBlockEntity();
        bat.chargeEU(ElectricFurnaceEntity.EU_PER_SMELT * 5f);

        ElectricFurnaceEntity ef = new ElectricFurnaceEntity();
        ef.euSource = bat;
        ef.insert(ElectricFurnaceEntity.SLOT_INPUT, BlockType.IRON_ORE, 2);

        float runtime = ElectricFurnaceEntity.SMELT_SECONDS * 3f;
        for (int i = 0; i < (int)(runtime / DT) + 1; i++) ef.tick(DT);

        assertTrue(ef.canExtract(ElectricFurnaceEntity.SLOT_OUTPUT));
        ItemStack out = ef.extract(ElectricFurnaceEntity.SLOT_OUTPUT, 64);
        assertNotNull(out);
        assertTrue(out.count() >= 1, "Should extract at least 1 smelted item");
    }

    // ------------------------------------------------------------------
    // Crafting recipes
    // ------------------------------------------------------------------

    @Test
    void coalGeneratorRecipeYieldsOne() {
        BlockType I = BlockType.IRON_INGOT;
        BlockType F = BlockType.FURNACE;
        BlockType[] grid = {
            I, F, I,
            I, F, I,
            I, F, I
        };
        var result = Crafting.match3x3(grid);
        assertNotNull(result, "IFI/IFI/IFI should yield a COAL_GENERATOR recipe");
        assertEquals(BlockType.COAL_GENERATOR, result.output());
        assertEquals(1, result.outputAmount());
    }

    @Test
    void copperCableRecipeYieldsSix() {
        BlockType X = BlockType.COPPER_INGOT;
        BlockType[] grid = {
            null, X, null,
            null, X, null,
            null, X, null
        };
        var result = Crafting.match3x3(grid);
        assertNotNull(result, ".X./.X./.X. should yield a COPPER_CABLE recipe");
        assertEquals(BlockType.COPPER_CABLE, result.output());
        assertEquals(6, result.outputAmount());
    }

    @Test
    void goldCableRecipeYieldsSix() {
        BlockType A = BlockType.GOLD_INGOT;
        BlockType[] grid = {
            null, A, null,
            null, A, null,
            null, A, null
        };
        var result = Crafting.match3x3(grid);
        assertNotNull(result, ".A./.A./.A. should yield a GOLD_CABLE recipe");
        assertEquals(BlockType.GOLD_CABLE, result.output());
        assertEquals(6, result.outputAmount());
    }

    @Test
    void electricFurnaceRecipeYieldsOne() {
        BlockType I = BlockType.IRON_INGOT;
        BlockType X = BlockType.COPPER_INGOT;
        BlockType F = BlockType.FURNACE;
        BlockType[] grid = {
            I, X, I,
            X, F, X,
            I, X, I
        };
        var result = Crafting.match3x3(grid);
        assertNotNull(result, "IXI/XFX/IXI should yield an ELECTRIC_FURNACE recipe");
        assertEquals(BlockType.ELECTRIC_FURNACE, result.output());
        assertEquals(1, result.outputAmount());
    }

    @Test
    void batteryBlockRecipeYieldsOne() {
        BlockType X = BlockType.COPPER_INGOT;
        BlockType I = BlockType.IRON_INGOT;
        BlockType[] grid = {
            X, I, X,
            I, X, I,
            X, I, X
        };
        var result = Crafting.match3x3(grid);
        assertNotNull(result, "XIX/IXI/XIX should yield a BATTERY_BLOCK recipe");
        assertEquals(BlockType.BATTERY_BLOCK, result.output());
        assertEquals(1, result.outputAmount());
    }
}
