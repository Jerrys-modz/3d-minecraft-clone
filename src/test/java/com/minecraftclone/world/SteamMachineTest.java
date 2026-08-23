package com.minecraftclone.world;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the Steam Age machines: the boiler's fuel-to-steam burn and
 * the steam furnace's steam-drawn smelting, plus persistence round-trips.
 * The furnace is wired directly to a boiler instance so no world or adjacency
 * scan is needed.
 */
class SteamMachineTest {

    @Test
    void burningFuelBuildsSteam() {
        SteamBoilerEntity b = new SteamBoilerEntity();
        assertEquals(2, b.addFuel(BlockType.COAL, 2));
        // One coal burns for COAL_BURN_TIME seconds; tick well past it.
        for (int i = 0; i < 200; i++) b.tick(1f);
        assertTrue(b.steamSeconds() > 0f, "burning coal should generate steam");
    }

    @Test
    void nonFuelIsRejected() {
        SteamBoilerEntity b = new SteamBoilerEntity();
        assertEquals(0, b.addFuel(BlockType.DIRT, 10));
        assertEquals(0, b.addFuel(BlockType.STONE, 5));
    }

    @Test
    void idleBoilerKeepsSteamForever() {
        SteamBoilerEntity b = new SteamBoilerEntity();
        b.addFuel(BlockType.COAL, 1);
        for (int i = 0; i < 100; i++) b.tick(1f);
        float before = b.steamSeconds();
        // Long idle stretch with nothing drawing: steam must not decay.
        for (int i = 0; i < 50; i++) b.tick(1f);
        assertEquals(before, b.steamSeconds(), 0.01f);
    }

    @Test
    void addFuelRejectsWrongTypeInSlot() {
        SteamBoilerEntity b = new SteamBoilerEntity();
        assertTrue(b.addFuel(BlockType.COAL, 4) > 0);
        assertEquals(0, b.addFuel(BlockType.WOOD_LOG, 2)); // different type: refused
        assertTrue(b.addFuel(BlockType.COAL, 2) > 0);      // same type tops up
    }

    @Test
    void steamFurnaceSmeltsWhileSteamIsDrawn() {
        SteamBoilerEntity boiler = new SteamBoilerEntity();
        SteamFurnaceEntity sf = new SteamFurnaceEntity();
        sf.attach(1, 64, 1, null);
        sf.boiler = boiler;

        sf.setSlot(SteamFurnaceEntity.SLOT_INPUT, BlockType.IRON_ORE, 3);
        boiler.addFuel(BlockType.COAL, 12);

        // Tick both long enough to smelt all three items.
        for (int i = 0; i < 120; i++) {
            boiler.tick(0.25f);
            sf.tick(0.25f);
        }
        assertEquals(BlockType.IRON_INGOT, sf.typeOf(SteamFurnaceEntity.SLOT_OUTPUT));
        assertEquals(3, sf.countOf(SteamFurnaceEntity.SLOT_OUTPUT));
        assertFalse(sf.canSmelt());
    }

    @Test
    void dryBoilerPausesProgress() {
        SteamBoilerEntity boiler = new SteamBoilerEntity(); // no fuel = no steam
        SteamFurnaceEntity sf = new SteamFurnaceEntity();
        sf.attach(1, 64, 1, null);
        sf.boiler = boiler;
        sf.setSlot(SteamFurnaceEntity.SLOT_INPUT, BlockType.IRON_ORE, 1);

        for (int i = 0; i < 40; i++) sf.tick(0.25f);
        assertEquals(0, sf.countOf(SteamFurnaceEntity.SLOT_OUTPUT));
    }

    @Test
    void boilerPersistsFuelAndSteam() throws Exception {
        SteamBoilerEntity b = new SteamBoilerEntity();
        b.addFuel(BlockType.COAL, 9);
        for (int i = 0; i < 20; i++) b.tick(1f);

        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        b.writeTo(new DataOutputStream(buf));
        SteamBoilerEntity loaded = new SteamBoilerEntity();
        loaded.readFrom(new DataInputStream(new ByteArrayInputStream(buf.toByteArray())));

        assertEquals(BlockType.COAL, loaded.typeOf(SteamBoilerEntity.SLOT_FUEL));
        assertEquals(8, loaded.countOf(SteamBoilerEntity.SLOT_FUEL));
        assertEquals(b.steamSeconds(), loaded.steamSeconds(), 0.01f);
    }

    @Test
    void steamFurnacePersistsSlotsAndProgress() throws Exception {
        SteamFurnaceEntity sf = new SteamFurnaceEntity();
        sf.attach(1, 64, 1, null);
        sf.setSlot(SteamFurnaceEntity.SLOT_INPUT, BlockType.IRON_ORE, 2);

        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        sf.writeTo(new DataOutputStream(buf));
        SteamFurnaceEntity loaded = new SteamFurnaceEntity();
        loaded.readFrom(new DataInputStream(new ByteArrayInputStream(buf.toByteArray())));

        assertEquals(BlockType.IRON_ORE, loaded.typeOf(SteamFurnaceEntity.SLOT_INPUT));
        assertEquals(2, loaded.countOf(SteamFurnaceEntity.SLOT_INPUT));
        assertNull(loaded.typeOf(SteamFurnaceEntity.SLOT_OUTPUT));
    }
}
