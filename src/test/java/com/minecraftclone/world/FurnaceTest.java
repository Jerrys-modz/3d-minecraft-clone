package com.minecraftclone.world;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FurnaceTest {

    @Test
    void oneCoalSmeltsOneOre() {
        Furnace f = new Furnace();
        f.setSlot(Furnace.SLOT_INPUT, BlockType.IRON_ORE, 1);
        f.setSlot(Furnace.SLOT_FUEL, BlockType.COAL_ORE, 1);
        f.tick(Furnace.SMELT_TIME);
        assertEquals(BlockType.IRON_INGOT, f.typeOf(Furnace.SLOT_OUTPUT));
        assertEquals(1, f.countOf(Furnace.SLOT_OUTPUT));
        assertTrue(f.isEmpty(Furnace.SLOT_INPUT));
        assertTrue(f.isEmpty(Furnace.SLOT_FUEL));
        assertFalse(f.isBurning());
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
    void runsOutOfFuelAfterOneItemAndResumesWhenRefuelled() {
        Furnace f = new Furnace();
        f.setSlot(Furnace.SLOT_INPUT, BlockType.IRON_ORE, 2);
        f.setSlot(Furnace.SLOT_FUEL, BlockType.COAL_ORE, 1);
        f.tick(Furnace.SMELT_TIME);
        assertEquals(BlockType.IRON_INGOT, f.typeOf(Furnace.SLOT_OUTPUT));
        assertEquals(1, f.countOf(Furnace.SLOT_OUTPUT));
        assertEquals(BlockType.IRON_ORE, f.typeOf(Furnace.SLOT_INPUT), "second ore waits for more fuel");
        assertFalse(f.isBurning());

        f.setSlot(Furnace.SLOT_FUEL, BlockType.COAL_ORE, 1);
        f.tick(Furnace.SMELT_TIME);
        assertEquals(2, f.countOf(Furnace.SLOT_OUTPUT));
        assertTrue(f.isEmpty(Furnace.SLOT_INPUT));
    }

    @Test
    void fuelBurnsDownEvenWithNoInput() {
        Furnace f = new Furnace();
        f.setSlot(Furnace.SLOT_FUEL, BlockType.COAL_ORE, 1);
        f.tick(Furnace.SMELT_TIME);
        assertTrue(f.isEmpty(Furnace.SLOT_FUEL));
        assertFalse(f.isBurning());
    }

    @Test
    void stopsWhenOutputIsFull() {
        Furnace f = new Furnace();
        f.setSlot(Furnace.SLOT_INPUT, BlockType.IRON_ORE, 1);
        f.setSlot(Furnace.SLOT_FUEL, BlockType.COAL_ORE, 1);
        f.setSlot(Furnace.SLOT_OUTPUT, BlockType.IRON_INGOT, 64);
        f.tick(Furnace.SMELT_TIME);
        assertEquals(BlockType.IRON_ORE, f.typeOf(Furnace.SLOT_INPUT), "input untouched when output is full");
        assertEquals(64, f.countOf(Furnace.SLOT_OUTPUT));
    }

    @Test
    void progressAndFlameTrackTheBurn() {
        Furnace f = new Furnace();
        f.setSlot(Furnace.SLOT_INPUT, BlockType.GOLD_ORE, 1);
        f.setSlot(Furnace.SLOT_FUEL, BlockType.COAL_ORE, 1);
        f.tick(2f);
        assertTrue(f.isBurning());
        assertEquals(0.25f, f.progressFraction(), 0.001f);
        assertEquals(0.75f, f.burnFraction(), 0.001f);
    }

    @Test
    void nonSmeltableInputNeverAdvances() {
        Furnace f = new Furnace();
        f.setSlot(Furnace.SLOT_INPUT, BlockType.DIRT, 1);
        f.setSlot(Furnace.SLOT_FUEL, BlockType.COAL_ORE, 1);
        f.tick(Furnace.SMELT_TIME);
        assertEquals(0f, f.progressFraction(), 0.001f);
        assertTrue(f.isEmpty(Furnace.SLOT_FUEL));
    }
}
