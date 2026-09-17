package com.minecraftclone.world;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class PrimitiveBlastFurnaceTest {

    @Test
    void ironChainBecomesWroughtIron() {
        assertEquals(BlockType.WROUGHT_IRON_INGOT, PrimitiveBlastFurnaceEntity.outputFor(BlockType.IRON_ORE));
        assertEquals(BlockType.WROUGHT_IRON_INGOT, PrimitiveBlastFurnaceEntity.outputFor(BlockType.IRON_INGOT));
        assertEquals(BlockType.WROUGHT_IRON_INGOT, PrimitiveBlastFurnaceEntity.outputFor(BlockType.IRON_DUST));
        assertEquals(BlockType.WROUGHT_IRON_INGOT, PrimitiveBlastFurnaceEntity.outputFor(BlockType.CRUSHED_MAGNETITE));
        assertNull(PrimitiveBlastFurnaceEntity.outputFor(BlockType.GOLD_ORE));
        assertNull(PrimitiveBlastFurnaceEntity.outputFor(BlockType.COPPER_INGOT));
    }

    @Test
    void smeltsIronIngotWithCoal() {
        PrimitiveBlastFurnaceEntity pbf = new PrimitiveBlastFurnaceEntity();
        pbf.setSlot(PrimitiveBlastFurnaceEntity.SLOT_INPUT, BlockType.IRON_INGOT, 2);
        pbf.setSlot(PrimitiveBlastFurnaceEntity.SLOT_FUEL, BlockType.COAL, 4);
        float dt = PrimitiveBlastFurnaceEntity.SMELT_TIME;
        for (int i = 0; i < 8; i++) pbf.tick(dt / 2f);
        assertEquals(BlockType.WROUGHT_IRON_INGOT, pbf.typeOf(PrimitiveBlastFurnaceEntity.SLOT_OUTPUT));
        assertEquals(2, pbf.countOf(PrimitiveBlastFurnaceEntity.SLOT_OUTPUT));
        assertEquals(0, pbf.countOf(PrimitiveBlastFurnaceEntity.SLOT_INPUT));
    }

    @Test
    void refusesNonIronInput() {
        PrimitiveBlastFurnaceEntity pbf = new PrimitiveBlastFurnaceEntity();
        assertEquals(3, pbf.add(BlockType.GOLD_ORE, 3), "gold is leftover");
        assertFalse(pbf.canSmelt());
        assertEquals(0, pbf.countOf(PrimitiveBlastFurnaceEntity.SLOT_INPUT));
    }

    @Test
    void coalGoesToFuelSlot() {
        PrimitiveBlastFurnaceEntity pbf = new PrimitiveBlastFurnaceEntity();
        assertEquals(0, pbf.add(BlockType.COAL, 4));
        assertEquals(BlockType.COAL, pbf.typeOf(PrimitiveBlastFurnaceEntity.SLOT_FUEL));
        assertEquals(4, pbf.countOf(PrimitiveBlastFurnaceEntity.SLOT_FUEL));
    }

    @Test
    void noFuelMeansNoProgress() {
        PrimitiveBlastFurnaceEntity pbf = new PrimitiveBlastFurnaceEntity();
        pbf.setSlot(PrimitiveBlastFurnaceEntity.SLOT_INPUT, BlockType.IRON_INGOT, 1);
        for (int i = 0; i < 40; i++) pbf.tick(1f);
        assertEquals(0, pbf.countOf(PrimitiveBlastFurnaceEntity.SLOT_OUTPUT));
    }

    @Test
    void persistenceRoundTrip() throws Exception {
        PrimitiveBlastFurnaceEntity pbf = new PrimitiveBlastFurnaceEntity();
        pbf.setSlot(PrimitiveBlastFurnaceEntity.SLOT_INPUT, BlockType.IRON_ORE, 3);
        pbf.setSlot(PrimitiveBlastFurnaceEntity.SLOT_FUEL, BlockType.COAL, 2);
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        pbf.writeTo(new DataOutputStream(buf));
        PrimitiveBlastFurnaceEntity loaded = new PrimitiveBlastFurnaceEntity();
        loaded.readFrom(new DataInputStream(new ByteArrayInputStream(buf.toByteArray())));
        assertEquals(BlockType.IRON_ORE, loaded.typeOf(PrimitiveBlastFurnaceEntity.SLOT_INPUT));
        assertEquals(3, loaded.countOf(PrimitiveBlastFurnaceEntity.SLOT_INPUT));
        assertEquals(BlockType.COAL, loaded.typeOf(PrimitiveBlastFurnaceEntity.SLOT_FUEL));
    }

    @Test
    void steelDustBecomesSteelIngot() {
        assertEquals(BlockType.STEEL_INGOT, PrimitiveBlastFurnaceEntity.outputFor(BlockType.STEEL_DUST));
        assertNull(com.minecraftclone.player.Smelting.outputFor(BlockType.STEEL_DUST),
                "a regular furnace is not hot enough for steel");
        PrimitiveBlastFurnaceEntity pbf = new PrimitiveBlastFurnaceEntity();
        pbf.setSlot(PrimitiveBlastFurnaceEntity.SLOT_INPUT, BlockType.STEEL_DUST, 1);
        pbf.setSlot(PrimitiveBlastFurnaceEntity.SLOT_FUEL, BlockType.COAL, 2);
        float dt = PrimitiveBlastFurnaceEntity.SMELT_TIME;
        for (int i = 0; i < 4; i++) pbf.tick(dt / 2f);
        assertEquals(BlockType.STEEL_INGOT, pbf.typeOf(PrimitiveBlastFurnaceEntity.SLOT_OUTPUT));
        assertEquals(1, pbf.countOf(PrimitiveBlastFurnaceEntity.SLOT_OUTPUT));
        assertEquals(0, pbf.countOf(PrimitiveBlastFurnaceEntity.SLOT_INPUT));
    }

    @Test
    void wroughtIronIsNotADirectPbfInput() {
        assertNull(PrimitiveBlastFurnaceEntity.outputFor(BlockType.WROUGHT_IRON_INGOT),
                "mix wrought iron with coal dust first");
    }
}
