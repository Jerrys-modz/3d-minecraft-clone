package com.minecraftclone;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

    @Test
    void cloudAmountAdjustsAndClampsWithNamedValues() {
        Settings s = new Settings();
        assertEquals(2, s.getCloudAmount());
        assertEquals("Normal", s.valueText(Settings.CLOUDS));
        s.adjust(Settings.CLOUDS, -1);
        assertEquals(1, s.getCloudAmount());
        assertEquals("Light", s.valueText(Settings.CLOUDS));
        s.adjust(Settings.CLOUDS, -1);
        assertEquals(0, s.getCloudAmount());
        assertEquals("Off", s.valueText(Settings.CLOUDS));
        s.adjust(Settings.CLOUDS, -1); // clamps at min
        assertEquals(0, s.getCloudAmount());
        s.adjust(Settings.CLOUDS, 1);
        s.adjust(Settings.CLOUDS, 1);
        s.adjust(Settings.CLOUDS, 1);
        assertEquals(3, s.getCloudAmount());
        assertEquals("Heavy", s.valueText(Settings.CLOUDS));
        s.adjust(Settings.CLOUDS, 1); // clamps at max
        assertEquals(3, s.getCloudAmount());
    }

    @Test
    void cloudSpeedAdjustsAndClampsWithNamedValues() {
        Settings s = new Settings();
        assertEquals(1, s.getCloudSpeed());
        assertEquals("Normal", s.valueText(Settings.CLOUD_SPEED));
        s.adjust(Settings.CLOUD_SPEED, -1);
        assertEquals(0, s.getCloudSpeed());
        assertEquals("Slow", s.valueText(Settings.CLOUD_SPEED));
        s.adjust(Settings.CLOUD_SPEED, 1);
        s.adjust(Settings.CLOUD_SPEED, 1);
        assertEquals(2, s.getCloudSpeed());
        assertEquals("Fast", s.valueText(Settings.CLOUD_SPEED));
        s.adjust(Settings.CLOUD_SPEED, 1); // clamps at max
        assertEquals(2, s.getCloudSpeed());
    }

    @Test
    void starToggleFlips() {
        Settings s = new Settings();
        assertTrue(s.isStars());
        s.adjust(Settings.STARS, 1);
        assertFalse(s.isStars());
        s.adjust(Settings.STARS, -1);
        assertTrue(s.isStars());
    }

    @Test
    void skyboxSettingsPersist() throws IOException {
        Path file = Files.createTempFile("mc-settings", ".txt");
        try {
            Settings s = new Settings();
            s.adjust(Settings.CLOUDS, -1);   // light
            s.adjust(Settings.CLOUD_SPEED, 1); // fast
            s.adjust(Settings.STARS, 1);     // off
            s.save(file);
            Settings loaded = Settings.load(file);
            assertEquals(1, loaded.getCloudAmount());
            assertEquals(2, loaded.getCloudSpeed());
            assertFalse(loaded.isStars());
        } finally {
            Files.deleteIfExists(file);
        }
    }
}
