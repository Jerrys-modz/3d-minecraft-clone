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
    void categoryVolumesAdjustClampAndPersistIndependently() throws IOException {
        int[] categoryRows = {
                Settings.MUSIC_VOLUME, Settings.AMBIENT_VOLUME, Settings.MOBS_VOLUME,
                Settings.MACHINES_VOLUME, Settings.PLAYER_VOLUME, Settings.UI_VOLUME
        };
        Path file = Files.createTempFile("mc-settings", ".txt");
        try {
            Settings s = new Settings();
            assertEquals(1f, s.getMusicVolume(), 1e-6f);
            assertEquals(1f, s.getAmbientVolume(), 1e-6f);
            assertEquals(1f, s.getMobsVolume(), 1e-6f);
            assertEquals(1f, s.getMachinesVolume(), 1e-6f);
            assertEquals(1f, s.getPlayerVolume(), 1e-6f);
            assertEquals(1f, s.getUiVolume(), 1e-6f);

            for (int row : categoryRows) {
                assertEquals(0f, Settings.minValue(row), 1e-6f);
                assertEquals(1f, Settings.maxValue(row), 1e-6f);
                s.adjust(row, -1);
                assertEquals(0.95f, s.fraction(row), 1e-6f); // min is 0 and max is 1, so fraction == value
                s.setFromFraction(row, -1f);
                assertEquals(0f, s.fraction(row), 1e-6f); // clamped at min
                s.setFromFraction(row, 2f);
                assertEquals(1f, s.fraction(row), 1e-6f); // clamped at max
            }

            s.setFromFraction(Settings.MUSIC_VOLUME, 0.4f);
            s.setFromFraction(Settings.AMBIENT_VOLUME, 0.6f);
            s.setFromFraction(Settings.MOBS_VOLUME, 0.8f);
            s.setFromFraction(Settings.MACHINES_VOLUME, 0.2f);
            s.setFromFraction(Settings.PLAYER_VOLUME, 0.3f);
            s.setFromFraction(Settings.UI_VOLUME, 0.7f);
            s.save(file);

            Settings loaded = Settings.load(file);
            assertEquals(0.4f, loaded.getMusicVolume(), 1e-6f);
            assertEquals(0.6f, loaded.getAmbientVolume(), 1e-6f);
            assertEquals(0.8f, loaded.getMobsVolume(), 1e-6f);
            assertEquals(0.2f, loaded.getMachinesVolume(), 1e-6f);
            assertEquals(0.3f, loaded.getPlayerVolume(), 1e-6f);
            assertEquals(0.7f, loaded.getUiVolume(), 1e-6f);
        } finally {
            Files.deleteIfExists(file);
        }
    }

    @Test
    void categoryVolumesAreIndependentOfEachOtherAndOfMaster() {
        Settings s = new Settings();
        s.setFromFraction(Settings.MOBS_VOLUME, 0.25f);
        assertEquals("25%", s.valueText(Settings.MOBS_VOLUME));
        // Only the Mobs slider moved - every other slider (including master) is untouched.
        assertEquals(1f, s.getMachinesVolume(), 1e-6f);
        assertEquals(1f, s.getMusicVolume(), 1e-6f);
        assertEquals(1f, s.getAmbientVolume(), 1e-6f);
        assertEquals(1f, s.getPlayerVolume(), 1e-6f);
        assertEquals(1f, s.getUiVolume(), 1e-6f);
        assertEquals(1f, s.getSoundVolume(), 1e-6f);
    }

    @Test
    void categoryVolumesRejectNonFiniteFileValues() throws IOException {
        Path file = Files.createTempFile("mc-settings", ".txt");
        try {
            Files.writeString(file, "music_volume=NaN\nmobs_volume=Infinity\nui_volume=-Infinity\n");
            Settings loaded = Settings.load(file);
            assertEquals(1f, loaded.getMusicVolume(), 1e-6f);
            assertEquals(1f, loaded.getMobsVolume(), 1e-6f);
            assertEquals(1f, loaded.getUiVolume(), 1e-6f);
        } finally {
            Files.deleteIfExists(file);
        }
    }

    @Test
    void audioTabHasOneRowPerVolumeSlider() {
        assertEquals(7, Settings.tabRowCount(Settings.TAB_AUDIO)); // master + 6 categories
    }

    @Test
    void controllerTabHasNoSettingsRowsOfItsOwn() {
        // Same shape as Controls: it shows GamepadBindings' own list instead of
        // any Settings row, so it contributes nothing to the row-count/tab math.
        assertEquals(0, Settings.tabRowCount(Settings.TAB_CONTROLLER));
    }

    @Test
    void gamepadBindingsPersistThroughSaveAndLoad() throws IOException {
        Path file = Files.createTempFile("mc-settings", ".txt");
        try {
            Settings s = new Settings();
            s.getGamepadBinds().set(0, org.lwjgl.glfw.GLFW.GLFW_GAMEPAD_BUTTON_Y); // JUMP -> Y
            s.save(file);

            Settings loaded = Settings.load(file);
            assertEquals(org.lwjgl.glfw.GLFW.GLFW_GAMEPAD_BUTTON_Y,
                    loaded.getGamepadBinds().buttonFor(com.minecraftclone.engine.KeyBindings.JUMP));
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

    @Test
    void darkGuiToggleFlipsAndPersists() throws IOException {
        Settings s = new Settings();
        assertFalse(s.isDarkGui()); // light GUI by default
        assertTrue(Settings.isToggle(Settings.DARK_GUI));
        assertEquals("OFF", s.valueText(Settings.DARK_GUI));
        s.adjust(Settings.DARK_GUI, 1); // flip on
        assertTrue(s.isDarkGui());
        assertEquals("ON", s.valueText(Settings.DARK_GUI));
        s.adjust(Settings.DARK_GUI, -1); // flip back off
        assertFalse(s.isDarkGui());

        Path file = Files.createTempFile("mc-settings", ".txt");
        try {
            s.adjust(Settings.DARK_GUI, 1); // on
            s.save(file);
            Settings loaded = Settings.load(file);
            assertTrue(loaded.isDarkGui());
        } finally {
            Files.deleteIfExists(file);
        }
    }

    @Test
    void soundVolumeIgnoresNaNAndInfinities() throws IOException {
        Path file = Files.createTempFile("mc-settings", ".txt");
        try {
            Files.writeString(file, "sound_volume=NaN\n");
            Settings loaded = Settings.load(file);
            assertEquals(1f, loaded.getSoundVolume(), 1e-6f); // default unchanged

            Files.writeString(file, "sound_volume=Infinity\n");
            loaded = Settings.load(file);
            assertEquals(1f, loaded.getSoundVolume(), 1e-6f); // default unchanged

            Files.writeString(file, "sound_volume=-Infinity\n");
            loaded = Settings.load(file);
            assertEquals(1f, loaded.getSoundVolume(), 1e-6f); // default unchanged
        } finally {
            Files.deleteIfExists(file);
        }
    }

    @Test
    void gameModeIsNotPersistedInGlobalSettings() throws IOException {
        Path file = Files.createTempFile("mc-settings", ".txt");
        try {
            Settings s = new Settings();
            s.setGameMode(GameMode.CREATIVE);
            assertEquals(GameMode.CREATIVE, s.getGameMode());
            s.save(file);
            String body = Files.readString(file);
            assertFalse(body.contains("game_mode="), "game mode belongs on the world, not settings.txt");
            assertFalse(body.contains("worldgen_seed="), "world seeds must not leak into global settings");

            Settings loaded = Settings.load(file);
            assertEquals(GameMode.SURVIVAL, loaded.getGameMode());
        } finally {
            Files.deleteIfExists(file);
        }
    }

    @Test
    void legacyGlobalGameModeLineIsIgnored() throws IOException {
        Path file = Files.createTempFile("mc-settings", ".txt");
        try {
            Files.writeString(file, "game_mode=2\nrender_distance=8\n");
            Settings loaded = Settings.load(file);
            assertEquals(GameMode.SURVIVAL, loaded.getGameMode());
            assertEquals(8, loaded.getRenderDistance());
        } finally {
            Files.deleteIfExists(file);
        }
    }

    @Test
    void titleScreenGameplayTabHidesGameMode() {
        assertTrue(Settings.isWorldSetting(Settings.GAME_MODE));
        assertTrue(Settings.isWorldSetting(Settings.DIFFICULTY));
        assertEquals(Settings.GAME_MODE, Settings.rowInTab(Settings.TAB_GAMEPLAY, 0, true));
        assertEquals(Settings.DIFFICULTY, Settings.rowInTab(Settings.TAB_GAMEPLAY, 1, true));
        assertEquals(Settings.SENSITIVITY, Settings.rowInTab(Settings.TAB_GAMEPLAY, 0, false));
        assertEquals(Settings.tabRowCount(Settings.TAB_GAMEPLAY, true) - 2,
                Settings.tabRowCount(Settings.TAB_GAMEPLAY, false));
        for (int local = 0; local < Settings.tabRowCount(Settings.TAB_GAMEPLAY, false); local++) {
            assertFalse(Settings.isWorldSetting(Settings.rowInTab(Settings.TAB_GAMEPLAY, local, false)));
        }
    }

    @Test
    void difficultyDefaultsToNormalAndIsNotPersistedGlobally() throws IOException {
        Path file = Files.createTempFile("mc-settings", ".txt");
        try {
            Settings s = new Settings();
            assertEquals(Difficulty.NORMAL, s.getDifficulty());
            s.setDifficulty(Difficulty.HARD);
            s.save(file);
            String body = Files.readString(file);
            assertFalse(body.contains("difficulty="), "difficulty belongs on the world, not settings.txt");

            Settings loaded = Settings.load(file);
            assertEquals(Difficulty.NORMAL, loaded.getDifficulty());
        } finally {
            Files.deleteIfExists(file);
        }
    }

    @Test
    void difficultyCyclesWrapAround() {
        Settings s = new Settings();
        assertTrue(Settings.isCycle(Settings.DIFFICULTY));
        assertTrue(Settings.isCycle(Settings.GAME_MODE));
        s.adjust(Settings.DIFFICULTY, +1);
        assertEquals(Difficulty.HARD, s.getDifficulty());
        s.adjust(Settings.DIFFICULTY, +1);
        assertEquals(Difficulty.PEACEFUL, s.getDifficulty());
        s.adjust(Settings.DIFFICULTY, -1);
        assertEquals(Difficulty.HARD, s.getDifficulty());
    }

    @Test
    void tabLabelsMatchMinecraftOptions() {
        assertEquals("Video", Settings.tabLabel(Settings.TAB_GRAPHICS));
        assertEquals("Gameplay", Settings.tabLabel(Settings.TAB_GAMEPLAY));
        assertEquals("Sound", Settings.tabLabel(Settings.TAB_AUDIO));
        assertEquals("Controls", Settings.tabLabel(Settings.TAB_CONTROLS));
        assertEquals("Controller", Settings.tabLabel(Settings.TAB_CONTROLLER));
    }

    @Test
    void miniMapLayoutPersistsAndResets() throws IOException {
        Path file = Files.createTempFile("mc-settings", ".txt");
        try {
            Settings s = new Settings();
            assertEquals(0.48f, s.getMiniMapSizeY(), 1e-6f);
            assertTrue(Float.isNaN(s.getMiniMapNdcX()), "default parks in the top-right");
            assertTrue(Float.isNaN(s.getMiniMapNdcY()));

            s.setMiniMapLayout(0.62f, 0.25f, -0.10f);
            s.save(file);
            String body = Files.readString(file);
            assertTrue(body.contains("mini_map_size="));
            assertTrue(body.contains("mini_map_x="));
            assertTrue(body.contains("mini_map_y="));

            Settings loaded = Settings.load(file);
            assertEquals(0.62f, loaded.getMiniMapSizeY(), 1e-5f);
            assertEquals(0.25f, loaded.getMiniMapNdcX(), 1e-5f);
            assertEquals(-0.10f, loaded.getMiniMapNdcY(), 1e-5f);

            loaded.resetMiniMapLayout();
            loaded.save(file);
            String resetBody = Files.readString(file);
            assertTrue(resetBody.contains("mini_map_size=0.48"));
            assertFalse(resetBody.contains("mini_map_x="), "NaN position is the default, not written");
            assertFalse(resetBody.contains("mini_map_y="));

            Settings reset = Settings.load(file);
            assertEquals(0.48f, reset.getMiniMapSizeY(), 1e-6f);
            assertTrue(Float.isNaN(reset.getMiniMapNdcX()));
            assertTrue(Float.isNaN(reset.getMiniMapNdcY()));
        } finally {
            Files.deleteIfExists(file);
        }
    }
}
