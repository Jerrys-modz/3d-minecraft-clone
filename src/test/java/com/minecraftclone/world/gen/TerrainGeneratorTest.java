package com.minecraftclone.world.gen;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TerrainGeneratorTest {

    @Test
    void heightDominatesTheMapping() {
        assertEquals(TerrainGenerator.Biome.OCEAN, TerrainGenerator.biomeAt(0, 0, TerrainGenerator.SEA_LEVEL - 1));
        assertEquals(TerrainGenerator.Biome.BEACH, TerrainGenerator.biomeAt(0, 0, TerrainGenerator.SEA_LEVEL + 1));
        assertEquals(TerrainGenerator.Biome.MOUNTAIN, TerrainGenerator.biomeAt(0, 0, 100));
    }

    @Test
    void temperatureSplitsColdAndHot() {
        assertEquals(TerrainGenerator.Biome.SNOWY, TerrainGenerator.biomeAt(-0.5, 0, 50));
        assertEquals(TerrainGenerator.Biome.TAIGA, TerrainGenerator.biomeAt(-0.5, 0.5, 50));
        assertEquals(TerrainGenerator.Biome.TUNDRA, TerrainGenerator.biomeAt(-0.2, 0, 50));
        assertEquals(TerrainGenerator.Biome.DESERT, TerrainGenerator.biomeAt(0.5, -0.5, 50));
        assertEquals(TerrainGenerator.Biome.SAVANNA, TerrainGenerator.biomeAt(0.5, 0.5, 50));
    }

    @Test
    void moistureSplitsTemperateLowlands() {
        assertEquals(TerrainGenerator.Biome.FOREST, TerrainGenerator.biomeAt(0, 0.5, 50));
        assertEquals(TerrainGenerator.Biome.PLAINS, TerrainGenerator.biomeAt(0, -0.1, 50));
    }
}
