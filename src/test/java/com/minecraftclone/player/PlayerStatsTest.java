package com.minecraftclone.player;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The cold-exposure survival rules (see {@link PlayerStats#update}): freezing
 * weather burns through hunger first, then health once you've run out of food.
 * The exposure itself (shelter, nearby fires) is computed in {@link Player},
 * which needs a live world and isn't unit-testable headlessly.
 */
class PlayerStatsTest {

    @Test
    void coldExposureDrainsHungerThenFreezes() {
        PlayerStats stats = new PlayerStats();
        // Full exposure: the cold drains hunger at ~0.42/s on top of the passive
        // drain, so ~240 one-second steps empty the bar with barely any freeze time.
        for (int i = 0; i < 245; i++) {
            stats.update(1f, false, false, false, false, 0f, 1f);
        }
        assertEquals(1f, stats.getColdness(), 0.001f, "the effective exposure is stored");
        assertEquals(0f, stats.getHunger(), 0.001f, "the cold burns through hunger");
        assertTrue(stats.getHealth() > 60f, "a few seconds of freezing isn't lethal: " + stats.getHealth());

        float health = stats.getHealth();
        stats.update(1f, false, false, false, false, 0f, 1f);
        // One cold freeze tick (2) plus the existing starvation tick (2), since
        // being cold AND out of food is worse than either alone.
        assertEquals(health - 4f, stats.getHealth(), 0.01f, "once starving, the cold freezes you");
    }

    @Test
    void noColdMeansNoFreeze() {
        PlayerStats stats = new PlayerStats();
        float health = stats.getHealth();
        stats.update(10f, false, false, false, false, 0f, 0f);
        assertEquals(health, stats.getHealth(), 0.001f, "warm weather never hurts you");
        assertEquals(0f, stats.getColdness());
        assertTrue(stats.getHunger() < PlayerStats.MAX_HUNGER, "the normal passive drain still applies");
    }

    @Test
    void breathStartsFullAndCountsDownOnlyWhileSubmerged() {
        PlayerStats stats = new PlayerStats();
        assertEquals(PlayerStats.MAX_BREATH, stats.getBreath(), 0.001f, "full breath on dry land");

        stats.update(1f, false, false, true, false, 0f, 0f); // 1s submerged
        assertEquals(PlayerStats.MAX_BREATH - 1f, stats.getBreath(), 0.001f);

        stats.update(2f, false, false, false, false, 0f, 0f); // surfaced - resets, doesn't just stop draining
        assertEquals(PlayerStats.MAX_BREATH, stats.getBreath(), 0.001f, "resurfacing refills breath immediately");
    }

    @Test
    void breathNeverGoesNegativeAndDrowningStartsOnlyOnceItRunsOut() {
        PlayerStats stats = new PlayerStats();
        float health = stats.getHealth();

        // Still within the grace period: no damage yet, breath clamped at 0 (not negative).
        stats.update(PlayerStats.MAX_BREATH - 0.5f, false, false, true, false, 0f, 0f);
        assertTrue(stats.getBreath() > 0f, "still holding breath: " + stats.getBreath());
        assertEquals(health, stats.getHealth(), 0.001f, "no drowning damage yet");

        // Push well past the grace period in one go.
        stats.update(5f, false, false, true, false, 0f, 0f);
        assertEquals(0f, stats.getBreath(), 0.001f, "breath is clamped at zero, never negative");
        assertTrue(stats.getHealth() < health, "drowning damage kicks in once breath runs out: " + stats.getHealth());
    }

    @Test
    void damageAccumulatesForArmorWearAndClearsOnDemand() {
        PlayerStats stats = new PlayerStats();
        stats.damage(10f);
        stats.damage(6f);
        assertEquals(16f, stats.frameDamageAccumulator(), 0.001f, "multiple hits in a frame all accumulate");
        stats.clearFrameDamage();
        assertEquals(0f, stats.frameDamageAccumulator(), 0.001f);
    }

    @Test
    void armorMultiplierScalesDamageDealtAndAccumulated() {
        PlayerStats stats = new PlayerStats();
        stats.setArmorMultiplier(0.5f); // half damage gets through
        stats.damage(10f);
        assertEquals(PlayerStats.MAX_HEALTH - 5f, stats.getHealth(), 0.001f);
        assertEquals(5f, stats.frameDamageAccumulator(), 0.001f, "the accumulator tracks post-mitigation damage");
    }

    @Test
    void resetAndForceFullClearArmorTransientState() {
        // Regression: a stray hit's accumulated damage (and a non-default armor
        // multiplier) must not linger across a reset/game-mode switch and later
        // get wrongly applied to whatever armor is equipped by then.
        PlayerStats reset = new PlayerStats();
        reset.setArmorMultiplier(0.3f);
        reset.damage(10f);
        reset.reset();
        assertEquals(0f, reset.frameDamageAccumulator(), 0.001f);
        reset.damage(10f);
        assertEquals(PlayerStats.MAX_HEALTH - 10f, reset.getHealth(), 0.001f, "multiplier reset to 1 (full damage)");

        PlayerStats forceFull = new PlayerStats();
        forceFull.setArmorMultiplier(0.3f);
        forceFull.damage(10f);
        forceFull.forceFull();
        assertEquals(0f, forceFull.frameDamageAccumulator(), 0.001f);
        forceFull.damage(10f);
        assertEquals(PlayerStats.MAX_HEALTH - 10f, forceFull.getHealth(), 0.001f, "multiplier reset to 1 (full damage)");
    }
}
