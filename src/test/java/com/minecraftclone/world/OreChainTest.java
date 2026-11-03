package com.minecraftclone.world;

import com.minecraftclone.player.Crafting;
import com.minecraftclone.player.Mortar;
import com.minecraftclone.player.Smelting;
import com.minecraftclone.world.gen.WorldGenSettings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The LV ore chain: crushed → {@link ElectricOreWasherEntity} → purified →
 * {@link ThermalCentrifugeEntity} → 3× dust, plus the purified-ore item rules.
 */
class OreChainTest {

    @TempDir
    Path dir;

    private static void run(ElectricMachineEntity m, float seconds) {
        for (float t = 0; t < seconds; t += 0.05f) m.tick(0.05f);
    }

    private static BatteryBlockEntity battery() {
        BatteryBlockEntity b = new BatteryBlockEntity();
        b.chargeEU(BatteryBlockEntity.MAX_EU);
        return b;
    }

    // ------------------------------------------------------------------
    // Purified ore items
    // ------------------------------------------------------------------

    @Test
    void purifiedOreMirrorsCrushedOre() {
        assertTrue(PurifiedOre.isPurified(BlockType.PURIFIED_TIN));
        assertEquals(BlockType.CRUSHED_TIN, PurifiedOre.crushedOf(BlockType.PURIFIED_TIN));
        assertEquals(Smelting.outputFor(BlockType.CRUSHED_TIN), Smelting.outputFor(BlockType.PURIFIED_TIN));
        assertEquals(BlockType.TIN_DUST, Mortar.dustFor(BlockType.PURIFIED_TIN));
        assertEquals(BlockType.ALUMINUM_INGOT, Smelting.outputFor(BlockType.PURIFIED_BAUXITE));
    }

    @Test
    void everyPurifiedOreHasADustAndASmelt() {
        for (BlockType t : BlockType.values()) {
            if (!PurifiedOre.isPurified(t)) continue;
            assertNotNull(PurifiedOre.crushedOf(t), t.name());
            assertNotNull(ThermalCentrifugeEntity.outputFor(t), t + " must spin into dust");
            assertNotNull(Smelting.outputFor(t), t + " must smelt");
        }
    }

    // ------------------------------------------------------------------
    // Ore Washer
    // ------------------------------------------------------------------

    @Test
    void washerTurnsCrushedAndImpureIntoPurified() {
        assertEquals(BlockType.PURIFIED_COPPER, ElectricOreWasherEntity.outputFor(BlockType.CRUSHED_COPPER));
        assertEquals(BlockType.PURIFIED_COPPER, ElectricOreWasherEntity.outputFor(BlockType.IMPURE_COPPER));
        assertNull(ElectricOreWasherEntity.outputFor(BlockType.COPPER_ORE));
        assertNull(ElectricOreWasherEntity.outputFor(BlockType.PURIFIED_COPPER));
    }

    @Test
    void washerEmitsByproductOnEveryFourthWash() {
        ElectricOreWasherEntity w = new ElectricOreWasherEntity();
        w.euSource = battery();
        w.waterOverride = true;
        w.setSlot(ElectricMachineEntity.SLOT_INPUT, BlockType.CRUSHED_COPPER, 8);
        run(w, ElectricOreWasherEntity.WASH_SECONDS * 8 + 1f);
        assertEquals(BlockType.PURIFIED_COPPER, w.typeOf(ElectricMachineEntity.SLOT_OUTPUT));
        assertEquals(8, w.countOf(ElectricMachineEntity.SLOT_OUTPUT));
        assertEquals(BlockType.TIN_DUST, w.typeOf(ElectricMachineEntity.SLOT_BYPRODUCT));
        assertEquals(2, w.countOf(ElectricMachineEntity.SLOT_BYPRODUCT));
    }

    @Test
    void washerNeedsWater() {
        ElectricOreWasherEntity w = new ElectricOreWasherEntity();
        BatteryBlockEntity b = battery();
        w.euSource = b;
        w.waterOverride = false;
        w.setSlot(ElectricMachineEntity.SLOT_INPUT, BlockType.CRUSHED_COPPER, 1);
        run(w, 10f);
        assertNull(w.typeOf(ElectricMachineEntity.SLOT_OUTPUT));
        assertEquals(BatteryBlockEntity.MAX_EU, b.euStored(), 0.001f, "no EU spent without water");
    }

    @Test
    void washerChecksRealWaterAndIgnoresIce() {
        World world = new World(42, new WorldGenSettings(), null,
                dir.resolve("w" + System.nanoTime()), DimensionType.OVERWORLD, true);
        world.ensureChunk(0, 0);
        world.setBlock(0, 100, 0, BlockType.ELECTRIC_ORE_WASHER);
        ElectricOreWasherEntity w = world.getOrCreateElectricOreWasher(0, 100, 0);
        // Clear the neighbourhood so world-gen terrain can't supply water.
        int[][] faces = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
        for (int[] d : faces) world.setBlock(d[0], 100 + d[1], d[2], BlockType.AIR);
        world.setBlock(1, 100, 0, BlockType.ICE);
        assertFalse(w.environmentReady());
        world.setBlock(1, 100, 0, BlockType.WATER_SOURCE);
        assertTrue(w.environmentReady());
    }

    // ------------------------------------------------------------------
    // Thermal Centrifuge
    // ------------------------------------------------------------------

    @Test
    void centrifugeSpinsPurifiedIntoTripleDust() {
        ThermalCentrifugeEntity c = new ThermalCentrifugeEntity();
        c.euSource = battery();
        c.setSlot(ElectricMachineEntity.SLOT_INPUT, BlockType.PURIFIED_COPPER, 2);
        run(c, ThermalCentrifugeEntity.SPIN_SECONDS * 2 + 1f);
        assertEquals(BlockType.COPPER_DUST, c.typeOf(ElectricMachineEntity.SLOT_OUTPUT));
        assertEquals(6, c.countOf(ElectricMachineEntity.SLOT_OUTPUT));
        assertEquals(BlockType.TIN_DUST, c.typeOf(ElectricMachineEntity.SLOT_BYPRODUCT));
        assertEquals(1, c.countOf(ElectricMachineEntity.SLOT_BYPRODUCT));
    }

    @Test
    void centrifugeOnlyAcceptsPurifiedOre() {
        ThermalCentrifugeEntity c = new ThermalCentrifugeEntity();
        assertFalse(c.acceptsInput(BlockType.CRUSHED_COPPER));
        assertFalse(c.acceptsInput(BlockType.COPPER_ORE));
        assertTrue(c.acceptsInput(BlockType.PURIFIED_COPPER));
    }

    @Test
    void fullChainBeatsTheMacerator() {
        // One crushed ore: macerator alone gives 2 dust; washer + centrifuge gives 3.
        assertEquals(2, ElectricMaceratorEntity.yieldFor(BlockType.CRUSHED_COPPER));
        BlockType purified = ElectricOreWasherEntity.outputFor(BlockType.CRUSHED_COPPER);
        assertEquals(BlockType.COPPER_DUST, ThermalCentrifugeEntity.outputFor(purified));
        assertEquals(3, ThermalCentrifugeEntity.YIELD);
    }

    // ------------------------------------------------------------------
    // Persistence, registration, recipes
    // ------------------------------------------------------------------

    @Test
    void byproductCreditSurvivesSaveLoad() throws Exception {
        ElectricOreWasherEntity w = new ElectricOreWasherEntity();
        w.euSource = battery();
        w.waterOverride = true;
        w.setSlot(ElectricMachineEntity.SLOT_INPUT, BlockType.CRUSHED_COPPER, 3);
        run(w, ElectricOreWasherEntity.WASH_SECONDS * 3 + 1f);   // credit now 0.75, no byproduct yet
        assertNull(w.typeOf(ElectricMachineEntity.SLOT_BYPRODUCT));

        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        w.writeTo(new DataOutputStream(buf));
        ElectricOreWasherEntity loaded = new ElectricOreWasherEntity();
        loaded.readFrom(new DataInputStream(new ByteArrayInputStream(buf.toByteArray())));
        loaded.euSource = battery();
        loaded.waterOverride = true;
        loaded.setSlot(ElectricMachineEntity.SLOT_INPUT, BlockType.CRUSHED_COPPER, 1);
        run(loaded, ElectricOreWasherEntity.WASH_SECONDS + 1f);
        assertEquals(1, loaded.countOf(ElectricMachineEntity.SLOT_BYPRODUCT), "4th wash emits the byproduct");
    }

    @Test
    void readsVersionOneMaceratorSaves() throws Exception {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(buf);
        out.writeByte(1);
        out.writeShort(BlockType.IRON_ORE.id); out.writeInt(5);
        out.writeShort(0); out.writeInt(0);
        out.writeShort(BlockType.IRON_DUST.id); out.writeInt(4);
        out.writeFloat(1.5f);
        ElectricMaceratorEntity m = new ElectricMaceratorEntity();
        m.readFrom(new DataInputStream(new ByteArrayInputStream(buf.toByteArray())));
        assertEquals(5, m.countOf(ElectricMachineEntity.SLOT_INPUT));
        assertEquals(4, m.countOf(ElectricMachineEntity.SLOT_OUTPUT));
    }

    @Test
    void machinesAreRegisteredAndCraftable() {
        assertTrue(BlockEntities.isRegistered(ElectricOreWasherEntity.TYPE));
        assertTrue(BlockEntities.isRegistered(ThermalCentrifugeEntity.TYPE));
        BlockType X = BlockType.COPPER_INGOT, C = BlockType.STEEL_CASING, V = BlockType.VACUUM_TUBE;
        var washer = Crafting.match3x3(new BlockType[]{X, BlockType.GLASS, X, X, C, X, X, V, X});
        var centrifuge = Crafting.match3x3(new BlockType[]{X, BlockType.FURNACE, X, X, C, X, X, V, X});
        assertNotNull(washer);
        assertNotNull(centrifuge);
        assertEquals(BlockType.ELECTRIC_ORE_WASHER, washer.output());
        assertEquals(BlockType.THERMAL_CENTRIFUGE, centrifuge.output());
    }

    @Test
    void placingProcessorsCreatesEntities() {
        World world = new World(42, new WorldGenSettings(), null,
                dir.resolve("p" + System.nanoTime()), DimensionType.OVERWORLD, true);
        world.ensureChunk(0, 0);
        world.setBlock(0, 70, 0, BlockType.ELECTRIC_ORE_WASHER);
        world.setBlock(2, 70, 0, BlockType.THERMAL_CENTRIFUGE);
        assertInstanceOf(ElectricOreWasherEntity.class, world.blockEntityAt(0, 70, 0));
        assertInstanceOf(ThermalCentrifugeEntity.class, world.blockEntityAt(2, 70, 0));
    }
}
