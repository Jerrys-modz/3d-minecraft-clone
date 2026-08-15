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

    @Test
    void brightnessAdjustsAndClamps() {
        Settings s = new Settings();
        assertEquals(1f, s.getBrightness(), 1e-6f);
        assertEquals("100%", s.valueText(Settings.BRIGHTNESS));
        s.adjust(Settings.BRIGHTNESS, -1);
        assertEquals(0.95f, s.getBrightness(), 1e-6f);
        s.setFromFraction(Settings.BRIGHTNESS, 0f);
        assertEquals(0.5f, s.getBrightness(), 1e-6f); // clamped at min
        s.setFromFraction(Settings.BRIGHTNESS, 2f);
        assertEquals(1.5f, s.getBrightness(), 1e-6f); // clamped at max
    }

    @Test
    void invertMouseYAndViewBobbingToggleAndPersist() throws IOException {
        Path file = Files.createTempFile("mc-settings", ".txt");
        try {
            Settings s = new Settings();
            assertFalse(s.isInvertMouseY());
            assertTrue(s.isViewBobbing()); // on by default
            s.adjust(Settings.INVERT_MOUSE_Y, 1); // flip on
            s.adjust(Settings.VIEW_BOBBING, 1);   // flip off
            assertTrue(s.isInvertMouseY());
            assertFalse(s.isViewBobbing());
            s.save(file);
            Settings loaded = Settings.load(file);
            assertTrue(loaded.isInvertMouseY());
            assertFalse(loaded.isViewBobbing());
        } finally {
            Files.deleteIfExists(file);
        }
    }

    @Test
    void soundVolumeAdjustsClampsAndPersists() throws IOException {
        Path file = Files.createTempFile("mc-settings", ".txt");
        try {
            Settings s = new Settings();
            assertEquals(1f, s.getSoundVolume(), 1e-6f); // full volume by default
            assertEquals("100%", s.valueText(Settings.SOUND_VOLUME));
            s.adjust(Settings.SOUND_VOLUME, -1);
            assertEquals(0.95f, s.getSoundVolume(), 1e-6f);
            s.setFromFraction(Settings.SOUND_VOLUME, -1f);
            assertEquals(0f, s.getSoundVolume(), 1e-6f); // clamped at min - silent, not negative
            s.setFromFraction(Settings.SOUND_VOLUME, 2f);
            assertEquals(1f, s.getSoundVolume(), 1e-6f); // clamped at max
            s.setFromFraction(Settings.SOUND_VOLUME, 0.5f);
            s.save(file);
            Settings loaded = Settings.load(file);
            assertEquals(0.5f, loaded.getSoundVolume(), 1e-6f);
        } finally {
            Files.deleteIfExists(file);
        }
    }

    @Test
    void tabsCoverAllRowsWithoutOverlap() {
        int seen = 0;
        for (int tab = 0; tab < Settings.TAB_COUNT; tab++) {
            assertTrue(Settings.tabRowCount(tab) >= 0);
            for (int local = 0; local < Settings.tabRowCount(tab); local++) {
                int row = Settings.rowInTab(tab, local);
                assertTrue(row >= 0 && row < Settings.ROW_COUNT, "row " + row + " out of range");
                seen++;
            }
        }
        assertEquals(Settings.ROW_COUNT, seen, "every settings row should live on exactly one tab");
    }
}
