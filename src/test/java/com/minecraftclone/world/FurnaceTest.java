package com.minecraftclone.world;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class FurnaceTest {

    @Test
    void oneCoalSmeltsTwelveItems() {
        Furnace f = new Furnace();
        f.setSlot(Furnace.SLOT_INPUT, BlockType.IRON_ORE, 12);
        f.setSlot(Furnace.SLOT_FUEL, BlockType.COAL, 1);
        f.tick(Furnace.COAL_BURN_TIME);
        assertEquals(BlockType.IRON_INGOT, f.typeOf(Furnace.SLOT_OUTPUT));
        assertEquals(12, f.countOf(Furnace.SLOT_OUTPUT));
        assertTrue(f.isEmpty(Furnace.SLOT_INPUT));
        assertTrue(f.isEmpty(Furnace.SLOT_FUEL));
        assertFalse(f.isBurning());
    }

    @Test
    void woodLogSmeltsTwoItems() {
        Furnace f = new Furnace();
        f.setSlot(Furnace.SLOT_INPUT, BlockType.IRON_ORE, 2);
        f.setSlot(Furnace.SLOT_FUEL, BlockType.WOOD_LOG, 1);
        f.tick(Furnace.fuelDuration(BlockType.WOOD_LOG));
        assertEquals(2, f.countOf(Furnace.SLOT_OUTPUT));
        assertTrue(f.isEmpty(Furnace.SLOT_FUEL));
        assertFalse(f.isBurning());
    }

    @Test
    void stickSmeltsOneItem() {
        Furnace f = new Furnace();
        f.setSlot(Furnace.SLOT_INPUT, BlockType.GOLD_ORE, 1);
        f.setSlot(Furnace.SLOT_FUEL, BlockType.STICK, 1);
        f.tick(Furnace.fuelDuration(BlockType.STICK));
        assertEquals(BlockType.GOLD_INGOT, f.typeOf(Furnace.SLOT_OUTPUT));
        assertEquals(1, f.countOf(Furnace.SLOT_OUTPUT));
        assertTrue(f.isEmpty(Furnace.SLOT_FUEL));
        assertFalse(f.isBurning());
    }

    @Test
    void fuelRegistryKnowsBurnDurations() {
        assertEquals(Furnace.COAL_BURN_TIME, Furnace.fuelDuration(BlockType.COAL), 0.001f);
        assertEquals(16f, Furnace.fuelDuration(BlockType.WOOD_LOG), 0.001f);
        assertEquals(16f, Furnace.fuelDuration(BlockType.PLANKS), 0.001f);
        assertEquals(Furnace.SMELT_TIME, Furnace.fuelDuration(BlockType.STICK), 0.001f);
        assertEquals(0f, Furnace.fuelDuration(BlockType.DIRT), 0.001f);
        assertTrue(Furnace.isFuel(BlockType.COAL));
        assertTrue(Furnace.isFuel(BlockType.PLANKS));
        assertFalse(Furnace.isFuel(BlockType.DIRT));
        assertFalse(Furnace.isFuel(null));
    }

    @Test
    void doesNothingWithoutFuel() {
        Furnace f = new Furnace();
        f.setSlot(Furnace.SLOT_INPUT, BlockType.GOLD_ORE, 1);
        f.tick(Furnace.SMELT_TIME);
        assertEquals(BlockType.GOLD_ORE, f.typeOf(Furnace.SLOT_INPUT));
        assertTrue(f.isEmpty(Furnace.SLOT_OUTPUT));
        assertFalse(f.isBurning());
        assertEquals(0f, f.progressFraction(), 0.001f);
    }

    @Test
    void runsOutOfFuelAfterTwelveItemsAndResumesWhenRefuelled() {
        Furnace f = new Furnace();
        f.setSlot(Furnace.SLOT_INPUT, BlockType.IRON_ORE, 13);
        f.setSlot(Furnace.SLOT_FUEL, BlockType.COAL, 1);
        f.tick(Furnace.COAL_BURN_TIME);
        assertEquals(BlockType.IRON_INGOT, f.typeOf(Furnace.SLOT_OUTPUT));
        assertEquals(12, f.countOf(Furnace.SLOT_OUTPUT));
        assertEquals(BlockType.IRON_ORE, f.typeOf(Furnace.SLOT_INPUT), "13th ore waits for more fuel");
        assertEquals(1, f.countOf(Furnace.SLOT_INPUT));
        assertFalse(f.isBurning());

        f.setSlot(Furnace.SLOT_FUEL, BlockType.COAL, 1);
        f.tick(Furnace.COAL_BURN_TIME);
        assertEquals(13, f.countOf(Furnace.SLOT_OUTPUT));
        assertTrue(f.isEmpty(Furnace.SLOT_INPUT));
    }

    @Test
    void fuelBurnsDownEvenWithNoInput() {
        Furnace f = new Furnace();
        f.setSlot(Furnace.SLOT_FUEL, BlockType.COAL, 1);
        f.tick(Furnace.COAL_BURN_TIME);
        assertTrue(f.isEmpty(Furnace.SLOT_FUEL));
        assertFalse(f.isBurning());
    }

    @Test
    void stopsWhenOutputIsFull() {
        Furnace f = new Furnace();
        f.setSlot(Furnace.SLOT_INPUT, BlockType.IRON_ORE, 1);
        f.setSlot(Furnace.SLOT_FUEL, BlockType.COAL, 1);
        f.setSlot(Furnace.SLOT_OUTPUT, BlockType.IRON_INGOT, 64);
        f.tick(Furnace.COAL_BURN_TIME);
        assertEquals(BlockType.IRON_ORE, f.typeOf(Furnace.SLOT_INPUT), "input untouched when output is full");
        assertEquals(64, f.countOf(Furnace.SLOT_OUTPUT));
    }

    @Test
    void progressAndFlameTrackTheBurn() {
        Furnace f = new Furnace();
        f.setSlot(Furnace.SLOT_INPUT, BlockType.GOLD_ORE, 1);
        f.setSlot(Furnace.SLOT_FUEL, BlockType.COAL, 1);
        f.tick(2f);
        assertTrue(f.isBurning());
        assertEquals(0.25f, f.progressFraction(), 0.001f);
        assertEquals((Furnace.COAL_BURN_TIME - 2f) / Furnace.COAL_BURN_TIME, f.burnFraction(), 0.001f);
    }

    @Test
    void flameFractionTracksTheCurrentFuelsDuration() {
        Furnace f = new Furnace();
        f.setSlot(Furnace.SLOT_INPUT, BlockType.IRON_ORE, 1);
        f.setSlot(Furnace.SLOT_FUEL, BlockType.STICK, 1);
        f.tick(4f);
        // Half a stick's burn is gone after 4 of its 8 seconds.
        assertEquals(0.5f, f.burnFraction(), 0.001f);
    }

    @Test
    void nonSmeltableInputNeverAdvances() {
        Furnace f = new Furnace();
        f.setSlot(Furnace.SLOT_INPUT, BlockType.DIRT, 1);
        f.setSlot(Furnace.SLOT_FUEL, BlockType.COAL, 1);
        f.tick(Furnace.SMELT_TIME);
        assertEquals(0f, f.progressFraction(), 0.001f);
        assertTrue(f.isEmpty(Furnace.SLOT_FUEL));
    }

    @Test
    void activeStateTracksBurningAndEmitsLight() {
        Furnace f = new Furnace();
        assertFalse(f.isActive(), "idle furnace is not active");
        assertEquals(0, f.activeLightLevel());
        f.setSlot(Furnace.SLOT_INPUT, BlockType.IRON_ORE, 1);
        f.setSlot(Furnace.SLOT_FUEL, BlockType.COAL, 1);
        f.tick(1f);
        assertTrue(f.isActive(), "burning furnace is active (glowing front, emits light)");
        assertEquals(13, f.activeLightLevel(), "lit furnace glows like Minecraft's (level 13)");
        // Burn all fuel: it goes inactive again.
        f.tick(Furnace.COAL_BURN_TIME);
        assertFalse(f.isActive());
    }

    @Test
    void serializationRoundTripPreservesSlotsAndProgress() throws Exception {
        Furnace f = new Furnace();
        f.setSlot(Furnace.SLOT_INPUT, BlockType.IRON_ORE, 3);
        f.setSlot(Furnace.SLOT_FUEL, BlockType.COAL, 2);
        f.setSlot(Furnace.SLOT_OUTPUT, BlockType.IRON_INGOT, 1);
        f.tick(2f); // consumes one coal and starts smelting

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        f.writeTo(new DataOutputStream(bos));
        Furnace g = new Furnace();
        g.readFrom(new DataInputStream(new ByteArrayInputStream(bos.toByteArray())));

        assertEquals(BlockType.IRON_ORE, g.typeOf(Furnace.SLOT_INPUT));
        assertEquals(3, g.countOf(Furnace.SLOT_INPUT));
        assertEquals(BlockType.COAL, g.typeOf(Furnace.SLOT_FUEL));
        assertEquals(1, g.countOf(Furnace.SLOT_FUEL), "tick(2) burned one coal");
        assertEquals(BlockType.IRON_INGOT, g.typeOf(Furnace.SLOT_OUTPUT));
        assertEquals(1, g.countOf(Furnace.SLOT_OUTPUT));
        assertEquals(f.burnFraction(), g.burnFraction(), 0.001f);
        assertEquals(f.progressFraction(), g.progressFraction(), 0.001f);
    }
}
