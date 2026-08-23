package com.minecraftclone.world.multiblock;

import com.minecraftclone.player.ItemStack;
import com.minecraftclone.world.BlockType;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the smeltery controller's brain: input rules, melting while
 * hot (paused while cold), ore double-yield, output aggregation, and save
 * round-trips. These drive {@link SmelteryEntity#advance(float, boolean)}
 * directly, so no multi-block structure has to be formed.
 */
class SmelteryEntityTest {

    /** A formed entity: advance() refuses to run unless this flag is set. */
    private static SmelteryEntity formed() {
        SmelteryEntity e = new SmelteryEntity();
        e.reform(null);
        return e;
    }

    /** Melts in whole-second steps so per-call boundaries are exercised too. */
    private static void meltFor(SmelteryEntity e, float seconds) {
        for (float t = 0; t < seconds; t += 1f) {
            e.advance(1f, true);
        }
    }

    @Test
    void acceptsOnlySmeltables() {
        SmelteryEntity e = formed();
        assertTrue(e.accepts(BlockType.IRON_ORE));
        assertTrue(e.accepts(BlockType.CRUSHED_COPPER));
        assertFalse(e.accepts(BlockType.STONE));
        assertFalse(e.accepts(BlockType.DIRT));
    }

    @Test
    void meltsOreIntoDoubleIngotsWhileHot() {
        SmelteryEntity e = formed();
        assertEquals(2, e.insert(BlockType.IRON_ORE, 2));
        meltFor(e, SmelteryEntity.MELT_SECONDS);
        assertTrue(e.hasOutput());
        assertEquals(BlockType.IRON_INGOT, e.outputType());
        assertEquals(2, e.countOf(SmelteryEntity.SLOT_OUTPUT));

        ItemStack taken = e.takeOutput();
        assertEquals(BlockType.IRON_INGOT, taken.type());
        assertEquals(2, taken.count());
        assertFalse(e.hasOutput());
        assertNull(e.outputType());
    }

    @Test
    void dustYieldsSingleIngot() {
        SmelteryEntity e = formed();
        e.insert(BlockType.CRUSHED_COPPER, 1);
        meltFor(e, SmelteryEntity.MELT_SECONDS);
        assertEquals(1, e.countOf(SmelteryEntity.SLOT_OUTPUT));
    }

    @Test
    void coldPausesProgressWithoutLosingIt() {
        SmelteryEntity e = formed();
        e.insert(BlockType.GOLD_ORE, 1);
        // Almost a full melt while hot...
        for (float t = 0; t < SmelteryEntity.MELT_SECONDS - 2f; t += 1f) {
            e.advance(1f, true);
        }
        // ...then cold: progress freezes and nothing is produced.
        for (int i = 0; i < 30; i++) e.advance(1f, false);
        assertFalse(e.hasOutput());
        // Hot again: the remaining two seconds finish the item.
        e.advance(2f, true);
        assertTrue(e.hasOutput());
        assertEquals(BlockType.GOLD_INGOT, e.outputType());
    }

    @Test
    void differentOutputsWaitTheirTurn() {
        SmelteryEntity e = formed();
        e.insert(BlockType.IRON_ORE, 1);
        meltFor(e, SmelteryEntity.MELT_SECONDS);
        assertTrue(e.hasOutput());
        // Now queue gold while iron sits in the output: it must NOT overwrite.
        e.insert(BlockType.GOLD_ORE, 1);
        meltFor(e, SmelteryEntity.MELT_SECONDS * 3);
        assertEquals(BlockType.IRON_INGOT, e.outputType());
        assertEquals(2, e.countOf(SmelteryEntity.SLOT_OUTPUT)); // unchanged
    }

    @Test
    void unformedOrEmptyDoesNothing() {
        SmelteryEntity cold = new SmelteryEntity(); // never reformed -> !formed
        cold.insert(BlockType.IRON_ORE, 1);         // insert refused while unformed
        assertEquals(0, cold.pendingCount());
        assertFalse(cold.advance(SmelteryEntity.MELT_SECONDS, true));

        SmelteryEntity idle = formed();
        assertFalse(idle.advance(SmelteryEntity.MELT_SECONDS, true)); // nothing to melt
    }

    @Test
    void fuelBurnsOnlyWhileMelting() {
        SmelteryEntity e = formed();
        e.insert(BlockType.IRON_ORE, 2);
        e.addFuel(SmelteryEntity.MELT_SECONDS * 2f); // exactly enough for two items
        assertTrue(e.isHot());
        e.tick(SmelteryEntity.MELT_SECONDS); // one tick = one melted item
        assertTrue(e.hasOutput());
        assertEquals(SmelteryEntity.MELT_SECONDS, e.lavaFuel(), 0.01f);

        e.tick(SmelteryEntity.MELT_SECONDS); // second item drains the rest
        assertEquals(4, e.countOf(SmelteryEntity.SLOT_OUTPUT));
        assertEquals(0f, e.lavaFuel(), 0.01f);
        assertFalse(e.isHot()); // dry: queue waits

        // Refill finishes nothing extra - the input slot is already empty.
        e.addFuel(SmelteryEntity.LAVA_SECONDS);
        e.tick(SmelteryEntity.MELT_SECONDS);
        assertEquals(4, e.countOf(SmelteryEntity.SLOT_OUTPUT));
    }

    @Test
    void nearDryTankCannotFinishAnItem() {
        SmelteryEntity e = formed();
        e.insert(BlockType.GOLD_ORE, 1);
        // Only half the needed heat: a long tick must burn just that half and
        // NOT complete the melt (previously dt was spent whole).
        e.addFuel(SmelteryEntity.MELT_SECONDS / 2f);
        e.tick(SmelteryEntity.MELT_SECONDS);
        assertFalse(e.hasOutput());
        assertEquals(0f, e.lavaFuel(), 0.001f);
        // The partial progress is kept and finishes with fresh fuel.
        e.addFuel(SmelteryEntity.MELT_SECONDS);
        e.tick(SmelteryEntity.MELT_SECONDS / 2f + 0.5f);
        assertTrue(e.hasOutput());
    }

    @Test
    void noFuelBurnedWhileOutputIsBlocked() {
        SmelteryEntity e = formed();
        e.insert(BlockType.IRON_ORE, 1);
        meltFor(e, SmelteryEntity.MELT_SECONDS); // iron ingots sit in output
        e.addFuel(50f); // advance() doesn't burn fuel; fill for the blocked test
        float before = e.lavaFuel();
        // Gold queued while iron occupies the output: blocked, so ticking must
        // not consume fuel.
        e.insert(BlockType.GOLD_ORE, 1);
        e.tick(SmelteryEntity.MELT_SECONDS);
        assertEquals(before, e.lavaFuel(), 0.01f);
        assertEquals(BlockType.IRON_INGOT, e.outputType()); // untouched
    }

    @Test
    void idleSmelteryKeepsItsFuel() {
        SmelteryEntity idle = formed();
        idle.addFuel(50f);
        idle.tick(10f); // nothing queued - no burn
        assertEquals(50f, idle.lavaFuel(), 0.01f);
    }

    @Test
    void addFuelClampsAtCapacity() {
        SmelteryEntity e = formed();
        float accepted = e.addFuel(SmelteryEntity.MAX_FUEL * 3f);
        assertEquals(SmelteryEntity.MAX_FUEL, accepted, 0.01f);
        assertEquals(SmelteryEntity.MAX_FUEL, e.lavaFuel(), 0.01f);
        assertEquals(0f, e.addFuel(1f), 0.001f); // full: refuses more
    }

    @Test
    void persistsSlotsAndProgress() throws Exception {
        SmelteryEntity e = formed();
        e.insert(BlockType.IRON_ORE, 7);
        e.addFuel(123f);
        for (float t = 0; t < SmelteryEntity.MELT_SECONDS; t += 1f) e.advance(1f, true);
        assertTrue(e.hasOutput());

        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        e.writeTo(new DataOutputStream(buf));
        SmelteryEntity loaded = new SmelteryEntity();
        loaded.readFrom(new DataInputStream(new ByteArrayInputStream(buf.toByteArray())));

        // Slots survive (formed resets; reform restores simulation).
        assertEquals(BlockType.IRON_ORE, loaded.typeOf(SmelteryEntity.SLOT_INPUT));
        assertEquals(6, loaded.countOf(SmelteryEntity.SLOT_INPUT));
        assertEquals(BlockType.IRON_INGOT, loaded.typeOf(SmelteryEntity.SLOT_OUTPUT));
        assertEquals(2, loaded.countOf(SmelteryEntity.SLOT_OUTPUT));
        // Fuel persists too - advance() is the test path and doesn't burn, so
        // the full poured amount comes back.
        assertEquals(123f, loaded.lavaFuel(), 0.5f);
    }

    @Test
    void legacyOrCorruptSaveResetsCleanly() throws Exception {
        // A v1 payload round-trips...
        SmelteryEntity e = formed();
        e.insert(BlockType.GOLD_ORE, 3);
        e.addFuel(40f);
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        e.writeTo(new DataOutputStream(buf));
        SmelteryEntity loaded = new SmelteryEntity();
        loaded.readFrom(new DataInputStream(new ByteArrayInputStream(buf.toByteArray())));
        assertEquals(3, loaded.countOf(SmelteryEntity.SLOT_INPUT));
        assertEquals(40f, loaded.lavaFuel(), 0.01f);

        // ...while a legacy placeholder payload (just the super's formed flag
        // + a heat float) hits EOF and resets to a clean, empty state.
        ByteArrayOutputStream legacy = new ByteArrayOutputStream();
        DataOutputStream lout = new DataOutputStream(legacy);
        lout.writeBoolean(false); // MultiBlockEntity super: formed flag
        lout.writeFloat(42f);     // legacy heatAccum
        SmelteryEntity old = new SmelteryEntity();
        old.reform(null);
        assertDoesNotThrow((org.junit.jupiter.api.function.Executable) () ->
                old.readFrom(new DataInputStream(new ByteArrayInputStream(legacy.toByteArray()))));
        assertFalse(old.hasOutput());
        assertEquals(0, old.pendingCount());
        assertEquals(0f, old.lavaFuel(), 0.001f);
    }
}
