package com.minecraftclone.world.multiblock;

import com.minecraftclone.player.ItemStack;
import com.minecraftclone.world.BlockType;
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
        for (float t = 0; t < SmelteryEntity.MELT_SECONDS; t += 1f) e.tick(1f);
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
        // Fuel persists too: 123 poured minus 8 burned.
        assertEquals(115f, loaded.lavaFuel(), 0.5f);
    }
}
