package com.minecraftclone.world;

import com.minecraftclone.engine.Climate;
import com.minecraftclone.engine.DayNightCycle;
import com.minecraftclone.engine.Calendar;
import com.minecraftclone.player.Armor;
import com.minecraftclone.player.PlayerStats;
import com.minecraftclone.world.gen.TerrainGenerator.Biome;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for the Temperature Effects system.
 *
 * <p>Covers the full pipeline:
 * <ol>
 *   <li>Climate temperature computation (biome × season × altitude × underground).</li>
 *   <li>The cold-factor formula that converts a temperature into a 0..1 exposure value.</li>
 *   <li>Armor warmth and its cold-multiplier effect.</li>
 *   <li>PlayerStats cold-hunger drain and freeze damage.</li>
 * </ol>
 *
 * <p>Shelf: shelter enclosure and space-heat are tested separately in
 * {@code EnclosureShelterTest}.
 */
class TemperatureEffectsTest {

    // ------------------------------------------------------------------
    // Cold-factor formula  (Climate.coldFactor)
    //   coldFactor = clamp(0, 1,  (2 - localTemp) / 22)
    //
    //   localTemp =  2°C  →  coldFactor = 0.0  (threshold: exactly warm enough)
    //   localTemp = -20°C →  coldFactor = 1.0  (maximum cold)
    //   localTemp = 20°C  →  coldFactor = 0.0  (clamped, positive temperatures are warm)
    // ------------------------------------------------------------------

    /** Temperatures at or above 2 °C must yield a cold factor of exactly zero. */
    @Test
    void warmTemperatureProducesNoColdFactor() {
        assertEquals(0f, Climate.coldFactor(20f),  0.001f, "20°C (plains summer) must be fully warm");
        assertEquals(0f, Climate.coldFactor(34f),  0.001f, "34°C (desert) must be fully warm");
        assertEquals(0f, Climate.coldFactor(2f),   0.001f, "2°C is the exact warm threshold");
    }

    /** Temperatures at or below −20 °C must clamp the cold factor to 1. */
    @Test
    void freezingTemperatureProducesMaxColdFactor() {
        assertEquals(1f, Climate.coldFactor(-20f), 0.001f, "-20°C must hit the cold cap");
        assertEquals(1f, Climate.coldFactor(-50f), 0.001f, "-50°C is clamped to 1");
    }

    /** Cold factor increases linearly between the 2 °C warm threshold and −20 °C maximum. */
    @Test
    void coldFactorScalesLinearlyBetweenThresholds() {
        // 2°C → 0, -20°C → 1. Midpoint -9°C → 0.5.
        assertEquals(0.5f, Climate.coldFactor(-9f), 0.001f, "-9°C should give 50% cold factor");
        // 2°C → 0, so -8°C is 10/22 ≈ 0.4545
        assertEquals((2f - (-8f)) / 22f, Climate.coldFactor(-8f), 0.001f);
    }

    // ------------------------------------------------------------------
    // Biome base temperatures vs the cold-factor formula
    // ------------------------------------------------------------------

    /** Desert and Savanna biomes are always hot enough to produce zero cold exposure. */
    @Test
    void hotBiomesProduceNoCold() {
        // Desert (34°C), Savanna (30°C), Badlands (32°C) — always fully warm.
        DayNightCycle cycle = new DayNightCycle();
        cycle.setTime(0.5f); // noon, so the nightly dip is minimised
        Climate climate = new Climate(new Calendar(), cycle);
        float desertTemp  = climate.temperatureFor(Biome.DESERT);
        float savannaTemp = climate.temperatureFor(Biome.SAVANNA);
        assertEquals(0f, Climate.coldFactor(desertTemp),  0.001f, "Desert is never cold");
        assertEquals(0f, Climate.coldFactor(savannaTemp), 0.001f, "Savanna is never cold");
    }

    /** Tundra and Snowy biomes register meaningful cold exposure even at noon. */
    @Test
    void coldBiomesProduceHighColdFactor() {
        DayNightCycle cycle = new DayNightCycle();
        cycle.setTime(0.5f); // noon
        Climate climate = new Climate(new Calendar(), cycle);
        float tundraTemp = climate.temperatureFor(Biome.TUNDRA);   // base -10°C
        float snowyTemp  = climate.temperatureFor(Biome.SNOWY);    // base  -4°C
        assertTrue(Climate.coldFactor(tundraTemp) > 0.3f,
                "Tundra should be meaningfully cold at noon: temp=" + tundraTemp);
        assertTrue(Climate.coldFactor(snowyTemp) > 0f,
                "Snowy biome should register some cold at noon: temp=" + snowyTemp);
    }

    // ------------------------------------------------------------------
    // Altitude lapse: mountain peak is colder than its foothills
    // ------------------------------------------------------------------

    /** A mountain summit experiences lower temperature and higher cold exposure than its base. */
    @Test
    void mountainPeakColderThanBase() {
        DayNightCycle cycle = new DayNightCycle();
        cycle.setTime(0.5f);
        Climate climate = new Climate(new Calendar(), cycle);
        // Sea-level reference is 42, lapse is 8 blocks / -1°C.
        float seaLevel = climate.temperatureFor(Biome.MOUNTAIN, 42f, 42f);
        float summit   = climate.temperatureFor(Biome.MOUNTAIN, 100f, 100f);
        assertTrue(summit < seaLevel, "Summit must be colder than the base");
        assertTrue(Climate.coldFactor(summit) >= Climate.coldFactor(seaLevel),
                "Summit cold factor must be at least as high as base");
    }

    // ------------------------------------------------------------------
    // Underground stability: caves hover near the biome's annual mean
    // ------------------------------------------------------------------

    /** In mid-winter a cave remains warmer than the frozen surface above it. */
    @Test
    void caveIsWarmerThanSurfaceInWinter() {
        // Mid-winter, noon -- mirrors ClimateTest.cavesAreWarmerThanAFrozenSurfaceInWinter.
        Calendar calendar = new Calendar();
        int daysPerSeason = calendar.getDaysPerSeason();
        calendar.update(3 * daysPerSeason + 10); // mid-winter
        DayNightCycle cycle = new DayNightCycle();
        cycle.setTime(0.5f); // noon, so the nightly dip is minimised
        Climate winter = new Climate(calendar, cycle);

        float surface = winter.temperatureFor(Biome.SNOWY, 42f, 42f); // at ground level
        float cave    = winter.temperatureFor(Biome.SNOWY, 30f, 42f); // 12 blocks underground

        assertTrue(surface < 0f,
                "A snowy surface in mid-winter should be below freezing: " + surface);
        assertTrue(cave > surface,
                "Cave must be warmer than surface (" + surface + "C) in winter");
        assertTrue(Climate.coldFactor(cave) < Climate.coldFactor(surface),
                "Cave cold factor must be lower than surface cold factor in winter");
    }

    // ------------------------------------------------------------------
    // Armor warmth and cold multiplier
    // ------------------------------------------------------------------

    /** A player wearing no armor receives the full cold exposure (multiplier = 1). */
    @Test
    void noArmorLeavesFullColdExposure() {
        assertEquals(1f, Armor.coldMultiplier(0f), 1e-6f,
                "No armor = 100% of the cold reaches you");
    }

    /** A full set of bear-hide armor reaches the warmth cap and blocks all cold. */
    @Test
    void fullBearArmorEliminatesCold() {
        float warmth = Armor.totalWarmth(
                BlockType.BEAR_HELMET,
                BlockType.BEAR_CHESTPLATE,
                BlockType.BEAR_LEGGINGS,
                BlockType.BEAR_BOOTS);
        assertTrue(warmth >= Armor.WARMTH_CAP, "Full bear set meets the warmth cap");
        assertEquals(0f, Armor.coldMultiplier(warmth), 1e-6f,
                "Full bear set: 0% cold reaches the player");
    }

    /** A full fur set cuts cold exposure to below 25% without going negative. */
    @Test
    void fullFurSetCutsExposureToFraction() {
        float warmth = Armor.totalWarmth(
                BlockType.FUR_HELMET,
                BlockType.FUR_CHESTPLATE,
                BlockType.FUR_LEGGINGS,
                BlockType.FUR_BOOTS);
        float mul = Armor.coldMultiplier(warmth);
        assertTrue(mul < 0.25f, "Full fur set cuts cold exposure to under 25%: " + mul);
        assertTrue(mul >= 0f,   "Cold multiplier must never go negative");
    }

    /** Iron armor provides negligible insulation; the cold multiplier stays close to 1. */
    @Test
    void ironArmorProvidesLittleWarmth() {
        float warmth = Armor.totalWarmth(
                BlockType.IRON_HELMET,
                BlockType.IRON_CHESTPLATE,
                BlockType.IRON_LEGGINGS,
                BlockType.IRON_BOOTS);
        // Iron total warmth: 0.15 + 0.10 + 0.05 = ~0.35 — barely cuts the cold.
        assertTrue(Armor.coldMultiplier(warmth) > 0.85f,
                "Iron armor is a poor insulator: cold multiplier should be close to 1");
    }

    /** Diamond armor has zero warmth and provides no cold protection whatsoever. */
    @Test
    void diamondArmorProvidesNoWarmth() {
        float warmth = Armor.totalWarmth(
                BlockType.DIAMOND_HELMET,
                BlockType.DIAMOND_CHESTPLATE,
                BlockType.DIAMOND_LEGGINGS,
                BlockType.DIAMOND_BOOTS);
        assertEquals(0f, warmth, 1e-6f, "Diamond armor has zero warmth");
        assertEquals(1f, Armor.coldMultiplier(warmth), 1e-6f,
                "Diamond armor gives no cold protection");
    }

    // ------------------------------------------------------------------
    // PlayerStats cold-damage pipeline
    // ------------------------------------------------------------------

    private static final float DT = 1f; // 1 second steps for legible arithmetic

    /** Without any cold exposure, health must not decrease from cold damage alone. */
    @Test
    void noColdnessNoColdDamage() {
        PlayerStats stats = new PlayerStats();
        float healthBefore = stats.getHealth();
        // Many seconds with no cold
        for (int i = 0; i < 60; i++) {
            stats.update(DT, false, false, false, false, 0f, 0f);
        }
        // Health should not drop from cold (it may drop from starvation eventually,
        // but the first ~5 minutes drain hunger only, so 60 s is still safe).
        assertTrue(stats.getHealth() >= healthBefore - 1f,
                "No coldness: health should not drop from cold damage");
    }

    /** Full cold exposure drains hunger faster than the passive rate alone. */
    @Test
    void fullColdExposureDrainsHungerFasterThanPassive() {
        PlayerStats warmStats = new PlayerStats();
        PlayerStats coldStats = new PlayerStats();

        for (int i = 0; i < 30; i++) {
            warmStats.update(DT, false, false, false, false, 0f, 0f);
            coldStats.update(DT, false, false, false, false, 0f, 1f);
        }

        assertTrue(coldStats.getHunger() < warmStats.getHunger(),
                "Full cold exposure must drain hunger faster than no cold");
    }

    /** Once hunger is exhausted by cold, freeze damage reduces health below the maximum. */
    @Test
    void coldExposureFreezesOnceStarved() {
        PlayerStats stats = new PlayerStats();
        // At full cold exposure + passive drain, hunger depletes in ~240 s.
        // Run 260 s: hunger reaches 0 around step 240, then ~20 s of cold/starvation
        // damage accumulates without the player being dead yet.
        // Stop early if the player dies so we don't keep updating a dead entity.
        for (int i = 0; i < 260; i++) {
            if (stats.isDead()) break;
            stats.update(DT, false, false, false, false, 0f, 1f);
        }
        assertEquals(0f, stats.getHunger(), 0.01f,
                "The cold must drain hunger to zero");
        assertTrue(stats.getHealth() < PlayerStats.MAX_HEALTH,
                "Freeze damage must have reduced health once hunger ran out");
    }

    /** Half cold exposure drains hunger more slowly than full cold exposure over the same duration. */
    @Test
    void partialColdExposureDrainsSlower() {
        PlayerStats halfCold = new PlayerStats();
        PlayerStats fullCold = new PlayerStats();

        for (int i = 0; i < 60; i++) {
            halfCold.update(DT, false, false, false, false, 0f, 0.5f);
            fullCold.update(DT, false, false, false, false, 0f, 1.0f);
        }

        assertTrue(halfCold.getHunger() > fullCold.getHunger(),
                "Half cold exposure drains hunger more slowly than full exposure");
    }

    /** Returning to warmth (coldness = 0) immediately stops the accelerated hunger drain. */
    @Test
    void warmingUpStopsColdDamage() {
        PlayerStats stats = new PlayerStats();
        // Run cold for a while (but not long enough to starve).
        for (int i = 0; i < 30; i++) {
            stats.update(DT, false, false, false, false, 0f, 1f);
        }
        float hungerAfterCold = stats.getHunger();
        // Now warm up (coldness = 0).
        for (int i = 0; i < 30; i++) {
            stats.update(DT, false, false, false, false, 0f, 0f);
        }
        float hungerAfterWarmUp = stats.getHunger();
        // Warming up doesn't restore hunger, but it must stop the accelerated drain.
        // Passive drain at ~0.056/s × 30 = ~1.67. Cold adds ~10/s, so cold drain in
        // the second 30 s would be ~300 hunger — obviously it won't drain MORE than
        // the first 30 s (which was cold + passive).
        float coldDrain   = PlayerStats.MAX_HUNGER - hungerAfterCold;
        float warmDrain   = hungerAfterCold - hungerAfterWarmUp;
        assertTrue(warmDrain < coldDrain,
                "Hunger drain while warm (" + warmDrain + ") must be less than while cold (" + coldDrain + ")");
    }
}
