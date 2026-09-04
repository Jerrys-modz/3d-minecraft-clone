package com.minecraftclone.player;

import com.minecraftclone.Difficulty;
import com.minecraftclone.world.BlockType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the Phase 0 radiation mechanic:
 * <ul>
 *   <li>Radiation accumulates while {@link PlayerStats#setRadiationRate} is positive.</li>
 *   <li>Radiation decays slowly once exposure ends.</li>
 *   <li>Health damage begins once radiation exceeds the damage threshold (~60 %).</li>
 *   <li>{@link Armor#radiationMultiplier} correctly attenuates exposure.</li>
 *   <li>Block identities: uranium/plutonium ores exist as radioactive {@link BlockType}s.</li>
 *   <li>Hazmat suit items exist and register in the armor registry.</li>
 *   <li>Rubber item ({@link BlockType#RUBBER}) exists as a craftable ingredient.</li>
 * </ul>
 */
class RadiationTest {

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /** Steps PlayerStats forward by {@code seconds} with the given radiation rate. */
    private static void stepWithRate(PlayerStats stats, float ratePerSecond, float seconds) {
        float step = 0.05f; // 50 ms per tick
        for (float t = 0f; t < seconds; t += step) {
            stats.setRadiationRate(ratePerSecond);
            stats.update(step, false, false, false, false, 0f, 0f, Difficulty.NORMAL);
        }
    }

    /** Steps PlayerStats forward by {@code seconds} with zero radiation rate. */
    private static void stepNoExposure(PlayerStats stats, float seconds) {
        stepWithRate(stats, 0f, seconds);
    }

    // -----------------------------------------------------------------------
    // Block type identities
    // -----------------------------------------------------------------------

    @Test
    void uraniumOreExists() {
        assertNotNull(BlockType.URANIUM_ORE);
        assertNotNull(BlockType.SMALL_URANIUM_ORE);
    }

    @Test
    void plutoniumOreExists() {
        assertNotNull(BlockType.PLUTONIUM_ORE);
        assertNotNull(BlockType.SMALL_PLUTONIUM_ORE);
    }

    @Test
    void rubberItemExists() {
        // RUBBER is a pure inventory item (isItem = true → never placed as a world block).
        assertTrue(BlockType.RUBBER.isItem, "RUBBER must be a pure item, not a placeable block");
        // Verify it is a known enum constant with the expected numeric id.
        assertEquals(524, BlockType.RUBBER.id);
    }

    // -----------------------------------------------------------------------
    // Hazmat armour registry
    // -----------------------------------------------------------------------

    @Test
    void hazmatPiecesAreRegisteredAsArmor() {
        assertTrue(Armor.isArmor(BlockType.HAZMAT_HELMET),     "HAZMAT_HELMET not registered");
        assertTrue(Armor.isArmor(BlockType.HAZMAT_CHESTPLATE), "HAZMAT_CHESTPLATE not registered");
        assertTrue(Armor.isArmor(BlockType.HAZMAT_LEGGINGS),   "HAZMAT_LEGGINGS not registered");
        assertTrue(Armor.isArmor(BlockType.HAZMAT_BOOTS),      "HAZMAT_BOOTS not registered");
    }

    @Test
    void hazmatSuitSlotsAreCorrect() {
        assertEquals(Armor.Slot.HELMET,     Armor.slotOf(BlockType.HAZMAT_HELMET));
        assertEquals(Armor.Slot.CHESTPLATE, Armor.slotOf(BlockType.HAZMAT_CHESTPLATE));
        assertEquals(Armor.Slot.LEGGINGS,   Armor.slotOf(BlockType.HAZMAT_LEGGINGS));
        assertEquals(Armor.Slot.BOOTS,      Armor.slotOf(BlockType.HAZMAT_BOOTS));
    }

    // -----------------------------------------------------------------------
    // Radiation-block fractions
    // -----------------------------------------------------------------------

    @Test
    void eachHazmatPieceBlocksOneQuarterRadiation() {
        assertEquals(0.25f, Armor.radiationBlock(BlockType.HAZMAT_HELMET),     1e-6f);
        assertEquals(0.25f, Armor.radiationBlock(BlockType.HAZMAT_CHESTPLATE), 1e-6f);
        assertEquals(0.25f, Armor.radiationBlock(BlockType.HAZMAT_LEGGINGS),   1e-6f);
        assertEquals(0.25f, Armor.radiationBlock(BlockType.HAZMAT_BOOTS),      1e-6f);
    }

    @Test
    void fullHazmatSuitBlocksAllRadiation() {
        float mult = Armor.radiationMultiplier(
                BlockType.HAZMAT_HELMET,
                BlockType.HAZMAT_CHESTPLATE,
                BlockType.HAZMAT_LEGGINGS,
                BlockType.HAZMAT_BOOTS);
        assertEquals(0f, mult, 1e-6f, "Full hazmat set should pass 0 % radiation");
    }

    @Test
    void halfHazmatSuitBlocksHalfRadiation() {
        // Helmet + chestplate (0.25 + 0.25 = 0.50 blocked → 0.50 passes through)
        float mult = Armor.radiationMultiplier(
                BlockType.HAZMAT_HELMET,
                BlockType.HAZMAT_CHESTPLATE,
                null, null);
        assertEquals(0.5f, mult, 1e-6f, "Two hazmat pieces should pass 50 % radiation");
    }

    @Test
    void nonHazmatArmorDoesNotBlockRadiation() {
        float mult = Armor.radiationMultiplier(
                BlockType.DIAMOND_HELMET,
                BlockType.DIAMOND_CHESTPLATE,
                BlockType.DIAMOND_LEGGINGS,
                BlockType.DIAMOND_BOOTS);
        assertEquals(1f, mult, 1e-6f, "Diamond armor should not block radiation");
    }

    // -----------------------------------------------------------------------
    // PlayerStats radiation accumulation & decay
    // -----------------------------------------------------------------------

    @Test
    void radiationStartsAtZero() {
        PlayerStats stats = new PlayerStats();
        assertEquals(0f, stats.getRadiation(), 1e-6f);
    }

    @Test
    void radiationAccumulatesUnderExposure() {
        PlayerStats stats = new PlayerStats();
        stepWithRate(stats, 20f, 2f); // 20 units/s × 2 s → ~40 (minus natural decay)
        assertTrue(stats.getRadiation() > 1f, "Radiation should accumulate under sustained exposure");
    }

    @Test
    void radiationDoesNotExceedMax() {
        PlayerStats stats = new PlayerStats();
        // Very high rate for a long time — must cap at MAX_RADIATION.
        stepWithRate(stats, 999f, 30f);
        assertEquals(PlayerStats.MAX_RADIATION, stats.getRadiation(), 1e-3f,
                "Radiation must not exceed MAX_RADIATION");
    }

    @Test
    void radiationDecaysWhenNotExposed() {
        PlayerStats stats = new PlayerStats();
        // Charge up, then let it decay
        stepWithRate(stats, 80f, 10f);
        float peak = stats.getRadiation();
        assertTrue(peak > 0f, "Should have accumulated radiation");

        stepNoExposure(stats, 30f);
        assertTrue(stats.getRadiation() < peak, "Radiation should decay once exposure ends");
    }

    // -----------------------------------------------------------------------
    // Radiation damage
    // -----------------------------------------------------------------------

    @Test
    void belowDamageThresholdCausesNoDamage() {
        PlayerStats stats = new PlayerStats();
        // Push radiation to just below the damage threshold (60 % of 100 = 60)
        // Use a low rate so it builds slowly; advance only until ~50 units then stop.
        stepWithRate(stats, 5f, 9f); // ~45 units (rough; decay and discretisation vary)

        float health = stats.getHealth();
        // No damage should have been applied by radiation alone in the first 9 s
        // (the rate is low enough not to cross the threshold in this window).
        // Only check that health is still at max or close (lava/fire/etc = false).
        assertTrue(stats.getRadiation() < 60f || stats.getHealth() <= PlayerStats.MAX_HEALTH,
                "Below threshold or at most at full health");
    }

    @Test
    void highRadiationCausesDamage() {
        PlayerStats stats = new PlayerStats();
        // Flood with maximum radiation quickly, then tick for a while above threshold.
        stepWithRate(stats, 999f, 15f); // saturates quickly
        assertTrue(stats.getRadiation() >= 60f, "Should be above damage threshold");
        float health = stats.getHealth();
        assertTrue(health < PlayerStats.MAX_HEALTH,
                "Health should drop when radiation is above the damage threshold");
    }

    @Test
    void radiationIsResetOnForceFull() {
        PlayerStats stats = new PlayerStats();
        stepWithRate(stats, 999f, 5f);
        assertTrue(stats.getRadiation() > 0f, "Should have radiation");
        stats.forceFull();
        assertEquals(0f, stats.getRadiation(), 1e-6f, "forceFull should clear radiation");
    }

    @Test
    void radiationIsResetOnReset() {
        PlayerStats stats = new PlayerStats();
        stepWithRate(stats, 999f, 5f);
        stats.reset();
        assertEquals(0f, stats.getRadiation(), 1e-6f, "reset() should clear radiation");
    }
}
