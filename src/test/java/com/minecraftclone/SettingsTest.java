package com.minecraftclone;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SettingsTest {

    @Test
    void setFromFractionRoundsDiscreteGameModeRow() {
        Settings s = new Settings();
        s.setFromFraction(Settings.GAME_MODE, 0.9f);
        assertEquals(GameMode.SPECTATOR, s.getGameMode());
        s.setFromFraction(Settings.GAME_MODE, 0.0f);
        assertEquals(GameMode.SURVIVAL, s.getGameMode());
    }

    @Test
    void setFromFractionClampsToRange() {
        Settings s = new Settings();
        s.setFromFraction(Settings.RENDER_DISTANCE, 2f); // beyond max
        assertEquals(12, s.getRenderDistance());
        assertTrue(s.fraction(Settings.RENDER_DISTANCE) <= 1f);
        s.setFromFraction(Settings.RENDER_DISTANCE, -1f); // below min
        assertEquals(3, s.getRenderDistance());
        assertTrue(s.fraction(Settings.RENDER_DISTANCE) >= 0f);
    }
}
