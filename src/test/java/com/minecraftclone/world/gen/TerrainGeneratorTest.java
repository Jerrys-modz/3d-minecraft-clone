package com.minecraftclone.world.gen;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TerrainGeneratorTest {

    private static TerrainGenerator generator() {
        return new TerrainGenerator(0L, new WorldGenSettings());
    }

    @Test
    void heightDominatesTheMapping() {
        TerrainGenerator g = generator();
        int sea = g.getSeaLevel();
        assertEquals(TerrainGenerator.Biome.OCEAN, g.biomeAt(0, 0, sea - 1));
        assertEquals(TerrainGenerator.Biome.FROZEN_OCEAN, g.biomeAt(-0.5, 0, sea - 1));
        assertEquals(TerrainGenerator.Biome.BEACH, g.biomeAt(0, 0, sea + 1));
        assertEquals(TerrainGenerator.Biome.MOUNTAIN, g.biomeAt(0, 0, 100));
    }

    @Test
    void temperatureSplitsColdAndHot() {
        TerrainGenerator g = generator();
        assertEquals(TerrainGenerator.Biome.SNOWY, g.biomeAt(-0.5, 0, 50));
        assertEquals(TerrainGenerator.Biome.TAIGA, g.biomeAt(-0.5, 0.5, 50));
        assertEquals(TerrainGenerator.Biome.TUNDRA, g.biomeAt(-0.2, 0, 50));
        assertEquals(TerrainGenerator.Biome.DESERT, g.biomeAt(0.5, -0.5, 50));
        assertEquals(TerrainGenerator.Biome.BADLANDS, g.biomeAt(0.5, -0.05, 50));
        assertEquals(TerrainGenerator.Biome.SAVANNA, g.biomeAt(0.5, 0.05, 50));
        assertEquals(TerrainGenerator.Biome.JUNGLE, g.biomeAt(0.5, 0.5, 50));
    }

    @Test
    void moistureSplitsTemperateLowlands() {
        TerrainGenerator g = generator();
        assertEquals(TerrainGenerator.Biome.FOREST, g.biomeAt(0, 0.5, 50));
        assertEquals(TerrainGenerator.Biome.PLAINS, g.biomeAt(0, -0.1, 50));
        assertEquals(TerrainGenerator.Biome.SWAMP, g.biomeAt(0, 0.5, g.getSeaLevel() + 3));
    }

    @Test
    void superflatTerrainIsFlatAndAboveSeaLevel() {
        WorldGenSettings flat = new WorldGenSettings();
        flat.adjust(WorldGenSettings.ROW_WORLD_TYPE, 1); // superflat
        TerrainGenerator g = new TerrainGenerator(1L, flat);
        assertEquals(g.getSeaLevel() + 4, g.terrainHeight(3, 7));
        assertEquals(g.getSeaLevel() + 4, g.terrainHeight(-500, 900));
        assertTrue(g.getSeaLevel() + 4 > g.getSeaLevel());
    }

    @Test
    void seaLevelIsConfigurable() {
        WorldGenSettings high = new WorldGenSettings();
        high.adjust(WorldGenSettings.ROW_SEA_LEVEL, 1); // Normal -> High
        assertEquals(50, new TerrainGenerator(2L, high).getSeaLevel());
    }
}
