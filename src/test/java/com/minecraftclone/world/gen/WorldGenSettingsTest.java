package com.minecraftclone.world.gen;

import com.minecraftclone.GameMode;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldGenSettingsTest {

    @Test
    void blankSeedResolvesToAFreshRandomLong() {
        WorldGenSettings a = new WorldGenSettings();
        WorldGenSettings b = new WorldGenSettings();
        assertTrue(a.isSeedBlank());
        assertEquals("(random)", a.valueText(WorldGenSettings.ROW_SEED));
        long seedA = a.resolveSeed();
        long seedB = b.resolveSeed();
        assertNotEquals(seedA, seedB, "two blank seeds should not reuse the same long");
    }

    @Test
    void rollFreshSeedFillsAVisibleNumberAndDoesNotReuseTheLastOne() {
        WorldGenSettings first = new WorldGenSettings();
        first.rollFreshSeed();
        assertFalse(first.isSeedBlank());
        long firstSeed = first.resolveSeed();
        assertEquals(Long.toString(firstSeed), first.getSeedText());

        WorldGenSettings second = new WorldGenSettings();
        second.rollFreshSeed();
        assertNotEquals(first.getSeedText(), second.getSeedText());
        assertEquals(second.resolveSeed(), Long.parseLong(second.getSeedText()));
    }

    @Test
    void numericAndTextSeedsParseMinecraftStyle() {
        WorldGenSettings numeric = new WorldGenSettings();
        numeric.setSeedText("12345");
        assertEquals(12345L, numeric.resolveSeed());

        WorldGenSettings text = new WorldGenSettings();
        text.setSeedText("hello");
        assertEquals("hello".hashCode(), text.resolveSeed());
    }

    @Test
    void gameModeDefaultsToSurvivalAndCycles() {
        WorldGenSettings g = new WorldGenSettings();
        assertEquals(GameMode.SURVIVAL, g.getGameMode());
        assertEquals("Survival", g.valueText(WorldGenSettings.ROW_GAME_MODE));
        g.adjust(WorldGenSettings.ROW_GAME_MODE, +1);
        assertEquals(GameMode.CREATIVE, g.getGameMode());
        assertEquals("Creative", g.valueText(WorldGenSettings.ROW_GAME_MODE));
        g.adjust(WorldGenSettings.ROW_GAME_MODE, -1);
        assertEquals(GameMode.SURVIVAL, g.getGameMode());
        g.setGameMode(GameMode.SPECTATOR);
        assertEquals(GameMode.SPECTATOR, g.getGameMode());
    }

    @Test
    void gameModeAndSeedRoundTripThroughWorldTxtLines() {
        WorldGenSettings original = new WorldGenSettings();
        original.setName("My World");
        original.rollFreshSeed();
        original.setGameMode(GameMode.CREATIVE);
        original.adjust(WorldGenSettings.ROW_WORLD_TYPE, +1);

        List<String> lines = new ArrayList<>();
        original.saveLines(lines);
        assertTrue(lines.stream().anyMatch(l -> l.startsWith("worldgen_game_mode=" + GameMode.CREATIVE.ordinal())));
        assertTrue(lines.stream().anyMatch(l -> l.startsWith("worldgen_seed=" + original.getSeedText())));

        WorldGenSettings loaded = new WorldGenSettings();
        for (String line : lines) {
            int eq = line.indexOf('=');
            loaded.loadEntry(line.substring(0, eq), line.substring(eq + 1));
        }
        assertEquals("My World", loaded.getName());
        assertEquals(original.getSeedText(), loaded.getSeedText());
        assertEquals(GameMode.CREATIVE, loaded.getGameMode());
        assertEquals(WorldGenSettings.WorldType.SUPERFLAT, loaded.getWorldType());
    }

    @Test
    void missingGameModeKeyStaysSurvival() {
        WorldGenSettings g = new WorldGenSettings();
        g.loadEntry("worldgen_seed", "99");
        assertEquals(GameMode.SURVIVAL, g.getGameMode());
        assertEquals(99L, g.resolveSeed());
    }
}
