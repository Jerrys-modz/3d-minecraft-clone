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
    void persistsSlotsAndProgress() throws Exception {
        SmelteryEntity e = formed();
        e.insert(BlockType.IRON_ORE, 7);
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
    }
}
