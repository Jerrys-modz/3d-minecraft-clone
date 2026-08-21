package com.minecraftclone;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DifficultyTest {

    @Test
    void peacefulHasNoHostilesAndNoHungerDrain() {
        assertFalse(Difficulty.PEACEFUL.allowsHostileMobs());
        assertEquals(0f, Difficulty.PEACEFUL.mobDamageMultiplier(), 1e-6f);
        assertEquals(0f, Difficulty.PEACEFUL.hungerDrainMultiplier(), 1e-6f);
        assertEquals(0, Difficulty.PEACEFUL.maxHostiles());
        assertTrue(Difficulty.PEACEFUL.healthRegenMultiplier() > 1f);
    }

    @Test
    void easyIsSofterThanNormalAndHardIsHarsher() {
        assertTrue(Difficulty.EASY.allowsHostileMobs());
        assertEquals(0.5f, Difficulty.EASY.mobDamageMultiplier(), 1e-6f);
        assertEquals(1f, Difficulty.NORMAL.mobDamageMultiplier(), 1e-6f);
        assertEquals(1.5f, Difficulty.HARD.mobDamageMultiplier(), 1e-6f);
        assertTrue(Difficulty.EASY.maxHostiles() < Difficulty.NORMAL.maxHostiles());
        assertTrue(Difficulty.HARD.maxHostiles() > Difficulty.NORMAL.maxHostiles());
        assertTrue(Difficulty.HARD.hostileSpawnOdds() < Difficulty.NORMAL.hostileSpawnOdds());
        assertEquals(50f, Difficulty.EASY.starvationHealthFloor(), 1e-6f);
        assertEquals(5f, Difficulty.NORMAL.starvationHealthFloor(), 1e-6f);
        assertEquals(0f, Difficulty.HARD.starvationHealthFloor(), 1e-6f);
    }

    @Test
    void displayNamesAreTitleCase() {
        assertEquals("Peaceful", Difficulty.PEACEFUL.toString());
        assertEquals("Easy", Difficulty.EASY.displayName());
        assertEquals("Normal", Difficulty.NORMAL.displayName());
        assertEquals("Hard", Difficulty.HARD.displayName());
    }
}
