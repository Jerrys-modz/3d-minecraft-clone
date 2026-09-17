package com.minecraftclone.world;

import com.minecraftclone.player.Mortar;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class SteamOreWasherTest {

    @Test
    void crushedCopperWashesToCopperDust() {
        assertEquals(BlockType.COPPER_DUST, SteamOreWasherEntity.primaryOutput(BlockType.CRUSHED_COPPER));
        assertEquals(BlockType.COPPER_DUST, SteamOreWasherEntity.primaryOutput(BlockType.IMPURE_COPPER));
        assertEquals(BlockType.COPPER_DUST, SteamOreWasherEntity.primaryOutput(BlockType.COPPER_ORE));
        assertNull(SteamOreWasherEntity.primaryOutput(BlockType.COPPER_DUST));
        assertNull(SteamOreWasherEntity.primaryOutput(BlockType.DIRT));
    }

    @Test
    void copperByproductIsTin() {
        assertEquals(BlockType.TIN_DUST, SteamOreWasherEntity.byproductOf(BlockType.COPPER_DUST));
        assertEquals(BlockType.COPPER_DUST, SteamOreWasherEntity.byproductOf(BlockType.TIN_DUST));
        assertNull(SteamOreWasherEntity.byproductOf(BlockType.DIRT));
    }

    @Test
    void washerNeedsSteamAndWater() {
        SteamBoilerEntity boiler = new SteamBoilerEntity();
        SteamOreWasherEntity w = new SteamOreWasherEntity();
        w.boiler = boiler;
        w.waterOverride = true;
        w.setSlot(SteamOreWasherEntity.SLOT_INPUT, BlockType.CRUSHED_COPPER, 2);

        for (int i = 0; i < 40; i++) w.tick(0.25f);
        assertEquals(0, w.countOf(SteamOreWasherEntity.SLOT_OUTPUT), "no steam → no wash");

        boiler.addFuel(BlockType.COAL, 8);
        for (int i = 0; i < 80; i++) {
            boiler.tick(0.25f);
            w.tick(0.25f);
        }
        assertEquals(BlockType.COPPER_DUST, w.typeOf(SteamOreWasherEntity.SLOT_OUTPUT));
        assertEquals(2, w.countOf(SteamOreWasherEntity.SLOT_OUTPUT));
        assertFalse(w.canWash());
    }

    @Test
    void dryWasherDoesNotProgress() {
        SteamBoilerEntity boiler = new SteamBoilerEntity();
        boiler.addFuel(BlockType.COAL, 8);
        SteamOreWasherEntity w = new SteamOreWasherEntity();
        w.boiler = boiler;
        w.waterOverride = false;
        w.setSlot(SteamOreWasherEntity.SLOT_INPUT, BlockType.CRUSHED_TIN, 1);
        for (int i = 0; i < 80; i++) {
            boiler.tick(0.25f);
            w.tick(0.25f);
        }
        assertEquals(0, w.countOf(SteamOreWasherEntity.SLOT_OUTPUT));
    }

    @Test
    void persistenceRoundTrip() throws Exception {
        SteamOreWasherEntity w = new SteamOreWasherEntity();
        w.setSlot(SteamOreWasherEntity.SLOT_INPUT, BlockType.CRUSHED_COPPER, 5);
        w.setSlot(SteamOreWasherEntity.SLOT_OUTPUT, BlockType.COPPER_DUST, 2);
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        w.writeTo(new DataOutputStream(buf));
        SteamOreWasherEntity loaded = new SteamOreWasherEntity();
        loaded.readFrom(new DataInputStream(new ByteArrayInputStream(buf.toByteArray())));
        assertEquals(BlockType.CRUSHED_COPPER, loaded.typeOf(SteamOreWasherEntity.SLOT_INPUT));
        assertEquals(5, loaded.countOf(SteamOreWasherEntity.SLOT_INPUT));
        assertEquals(BlockType.COPPER_DUST, loaded.typeOf(SteamOreWasherEntity.SLOT_OUTPUT));
        assertEquals(2, loaded.countOf(SteamOreWasherEntity.SLOT_OUTPUT));
    }

    @Test
    void mortarAndWasherAgreeOnDust() {
        assertEquals(Mortar.dustFor(BlockType.CRUSHED_TIN),
                SteamOreWasherEntity.primaryOutput(BlockType.CRUSHED_TIN));
    }
}
