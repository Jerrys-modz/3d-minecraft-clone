package com.minecraftclone.world.gen;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TerrainGeneratorTest {

    @Test
    void heightDominatesTheMapping() {
        assertEquals(TerrainGenerator.Biome.OCEAN, TerrainGenerator.biomeAt(0, 0, TerrainGenerator.SEA_LEVEL - 1));
        assertEquals(TerrainGenerator.Biome.FROZEN_OCEAN, TerrainGenerator.biomeAt(-0.5, 0, TerrainGenerator.SEA_LEVEL - 1));
        assertEquals(TerrainGenerator.Biome.BEACH, TerrainGenerator.biomeAt(0, 0, TerrainGenerator.SEA_LEVEL + 1));
        assertEquals(TerrainGenerator.Biome.MOUNTAIN, TerrainGenerator.biomeAt(0, 0, 100));
    }

    @Test
    void temperatureSplitsColdAndHot() {
        assertEquals(TerrainGenerator.Biome.SNOWY, TerrainGenerator.biomeAt(-0.5, 0, 50));
        assertEquals(TerrainGenerator.Biome.TAIGA, TerrainGenerator.biomeAt(-0.5, 0.5, 50));
        assertEquals(TerrainGenerator.Biome.TUNDRA, TerrainGenerator.biomeAt(-0.2, 0, 50));
        assertEquals(TerrainGenerator.Biome.DESERT, TerrainGenerator.biomeAt(0.5, -0.5, 50));
        assertEquals(TerrainGenerator.Biome.BADLANDS, TerrainGenerator.biomeAt(0.5, -0.05, 50));
        assertEquals(TerrainGenerator.Biome.SAVANNA, TerrainGenerator.biomeAt(0.5, 0.05, 50));
        assertEquals(TerrainGenerator.Biome.JUNGLE, TerrainGenerator.biomeAt(0.5, 0.5, 50));
    }

    @Test
    void moistureSplitsTemperateLowlands() {
        assertEquals(TerrainGenerator.Biome.FOREST, TerrainGenerator.biomeAt(0, 0.5, 50));
        assertEquals(TerrainGenerator.Biome.PLAINS, TerrainGenerator.biomeAt(0, -0.1, 50));
        assertEquals(TerrainGenerator.Biome.SWAMP, TerrainGenerator.biomeAt(0, 0.5, TerrainGenerator.SEA_LEVEL + 3));
    }

    @Test
    void structuresMapToTheirBiomes() {
        assertEquals(TerrainGenerator.StructureType.DESERT_TEMPLE, TerrainGenerator.structureFor(TerrainGenerator.Biome.DESERT));
        assertEquals(TerrainGenerator.StructureType.IGLOO, TerrainGenerator.structureFor(TerrainGenerator.Biome.SNOWY));
        assertEquals(TerrainGenerator.StructureType.CABIN, TerrainGenerator.structureFor(TerrainGenerator.Biome.PLAINS));
        assertEquals(TerrainGenerator.StructureType.WITCH_HUT, TerrainGenerator.structureFor(TerrainGenerator.Biome.SWAMP));
        assertEquals(TerrainGenerator.StructureType.STONE_RUIN, TerrainGenerator.structureFor(TerrainGenerator.Biome.MOUNTAIN));
        assertEquals(TerrainGenerator.StructureType.NONE, TerrainGenerator.structureFor(TerrainGenerator.Biome.OCEAN));
    }

    @Test
    void villagesAreSparseAndDeterministic() {
        int count = 0;
        for (int cx = 0; cx < 1000; cx++) {
            if (TerrainGenerator.isVillageChunk(12345L, cx, 7)) count++;
        }
        assertTrue(count > 0, "some chunks should be village origins");
        assertTrue(count < 100, "villages should be rare");
        assertEquals(TerrainGenerator.isVillageChunk(12345L, 12, 7),
                TerrainGenerator.isVillageChunk(12345L, 12, 7));
    }

    @Test
    void villagesOnlySpawnInBuildableBiomes() {
        assertTrue(TerrainGenerator.villageBiome(TerrainGenerator.Biome.PLAINS));
        assertTrue(TerrainGenerator.villageBiome(TerrainGenerator.Biome.DESERT));
        assertFalse(TerrainGenerator.villageBiome(TerrainGenerator.Biome.OCEAN));
        assertFalse(TerrainGenerator.villageBiome(TerrainGenerator.Biome.MOUNTAIN));
    }
}
