package com.minecraftclone.world;

import com.minecraftclone.engine.gui.ContainerGui;
import com.minecraftclone.player.Crafting;
import com.minecraftclone.player.InventoryController;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;

import static org.junit.jupiter.api.Assertions.*;

/** LV battery items in the Battery Block, and the LV Extractor. */
class BatteryAndExtractorTest {

    // ------------------------------------------------------------------
    // Battery Block capacity
    // ------------------------------------------------------------------

    @Test
    void batteriesRaiseCapacity() {
        BatteryBlockEntity b = new BatteryBlockEntity();
        assertEquals(BatteryBlockEntity.MAX_EU, b.capacity());
        b.setSlot(BatteryBlockEntity.SLOT_BATTERIES, BlockType.LV_BATTERY, 3);
        assertEquals(BatteryBlockEntity.MAX_EU + 3 * BatteryBlockEntity.EU_PER_BATTERY, b.capacity());
        float absorbed = b.chargeEU(1_000_000f);
        assertEquals(b.capacity(), absorbed, 0.01f);
    }

    @Test
    void capacityCapsAtMaxBatteries() {
        BatteryBlockEntity b = new BatteryBlockEntity();
        b.setSlot(BatteryBlockEntity.SLOT_BATTERIES, BlockType.LV_BATTERY, 64);
        assertEquals(BatteryBlockEntity.MAX_BATTERIES, b.batteryCount());
        assertEquals(10, b.add(BlockType.LV_BATTERY, 10), "add() refuses past the cap");
    }

    @Test
    void otherItemsAddNoCapacity() {
        BatteryBlockEntity b = new BatteryBlockEntity();
        assertEquals(5, b.add(BlockType.IRON_INGOT, 5));
        b.setSlot(BatteryBlockEntity.SLOT_BATTERIES, BlockType.IRON_INGOT, 5); // GUI can place anything
        assertEquals(BatteryBlockEntity.MAX_EU, b.capacity());
    }

    @Test
    void removingBatteriesClampsCharge() {
        BatteryBlockEntity b = new BatteryBlockEntity();
        b.setSlot(BatteryBlockEntity.SLOT_BATTERIES, BlockType.LV_BATTERY, 2);
        b.chargeEU(b.capacity());
        b.setSlot(BatteryBlockEntity.SLOT_BATTERIES, null, 0);
        assertEquals(BatteryBlockEntity.MAX_EU, b.euStored(), 0.01f);
    }

    @Test
    void saveRoundTripAndOldFormat() throws Exception {
        BatteryBlockEntity b = new BatteryBlockEntity();
        b.setSlot(BatteryBlockEntity.SLOT_BATTERIES, BlockType.LV_BATTERY, 4);
        b.chargeEU(20_000f);
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        b.writeTo(new DataOutputStream(buf));
        BatteryBlockEntity loaded = new BatteryBlockEntity();
        loaded.readFrom(new DataInputStream(new ByteArrayInputStream(buf.toByteArray())));
        assertEquals(4, loaded.batteryCount());
        assertEquals(20_000f, loaded.euStored(), 0.01f);

        // A save from before the battery slot existed: just the EU float.
        ByteArrayOutputStream old = new ByteArrayOutputStream();
        new DataOutputStream(old).writeFloat(1234f);
        BatteryBlockEntity legacy = new BatteryBlockEntity();
        legacy.readFrom(new DataInputStream(new ByteArrayInputStream(old.toByteArray())));
        assertEquals(1234f, legacy.euStored(), 0.01f);
        assertEquals(0, legacy.batteryCount());
    }

    @Test
    void largerBatteryFeedsMachinesLonger() {
        BatteryBlockEntity b = new BatteryBlockEntity();
        b.setSlot(BatteryBlockEntity.SLOT_BATTERIES, BlockType.LV_BATTERY, 1);
        b.chargeEU(b.capacity());
        assertEquals(1f, b.progressFraction(), 1e-4f);
        assertEquals(b.capacity(), EuNetwork.capacityOf(b));
    }

    @Test
    void batteryRecipeAndShiftClick() {
        BlockType W = BlockType.COPPER_CABLE, P = BlockType.TIN_PLATE, S = BlockType.CRUSHED_SULFUR;
        var r = Crafting.match3x3(new BlockType[]{null, W, null, P, S, P, P, S, P});
        assertNotNull(r);
        assertEquals(BlockType.LV_BATTERY, r.output());

        BatteryBlockEntity b = new BatteryBlockEntity();
        assertEquals(ContainerGui.CONTAINER_START + BatteryBlockEntity.SLOT_BATTERIES,
                InventoryController.quickMoveFurnaceTarget(b, BlockType.LV_BATTERY));
        assertEquals(-1, InventoryController.quickMoveFurnaceTarget(b, BlockType.COAL));
    }

    // ------------------------------------------------------------------
    // Extractor
    // ------------------------------------------------------------------

    @Test
    void extractorTurnsJungleWoodIntoRubber() {
        assertEquals(BlockType.RUBBER, ElectricExtractorEntity.outputFor(BlockType.JUNGLE_LOG));
        assertEquals(BlockType.RUBBER, ElectricExtractorEntity.outputFor(BlockType.JUNGLE_SAPLING));
        assertNull(ElectricExtractorEntity.outputFor(BlockType.WOOD_LOG));

        ElectricExtractorEntity e = new ElectricExtractorEntity();
        BatteryBlockEntity bat = new BatteryBlockEntity();
        bat.chargeEU(BatteryBlockEntity.MAX_EU);
        e.euSource = bat;
        e.setSlot(ElectricMachineEntity.SLOT_INPUT, BlockType.JUNGLE_LOG, 3);
        for (int i = 0; i < 400; i++) e.tick(0.05f);
        assertEquals(BlockType.RUBBER, e.typeOf(ElectricMachineEntity.SLOT_OUTPUT));
        assertEquals(3, e.countOf(ElectricMachineEntity.SLOT_OUTPUT));
    }

    @Test
    void extractorIsRegisteredAndCraftable() {
        assertTrue(BlockEntities.isRegistered(ElectricExtractorEntity.TYPE));
        BlockType X = BlockType.COPPER_INGOT, P = BlockType.PLANKS, C = BlockType.STEEL_CASING,
                V = BlockType.VACUUM_TUBE;
        var r = Crafting.match3x3(new BlockType[]{X, P, X, X, C, X, X, V, X});
        assertNotNull(r);
        assertEquals(BlockType.ELECTRIC_EXTRACTOR, r.output());
    }
}
