package com.minecraftclone.world.gen;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
        int inFirstCell = 0;
        for (int cx = 0; cx < 32; cx++) {
            for (int cz = 0; cz < 32; cz++) {
                if (TerrainGenerator.isVillageChunk(12345L, cx, cz)) inFirstCell++;
            }
        }
        assertEquals(1, inFirstCell, "exactly one origin per 32×32 chunk region");

        int count = 0;
        for (int cx = 0; cx < 256; cx++) {
            for (int cz = 0; cz < 256; cz++) {
                if (TerrainGenerator.isVillageChunk(12345L, cx, cz)) count++;
            }
        }
        assertEquals(64, count, "8×8 regions in a 256×256 chunk square");
        assertEquals(TerrainGenerator.isVillageChunk(12345L, 12, 7),
                TerrainGenerator.isVillageChunk(12345L, 12, 7));
        // Old 1-in-36-chunks density would light up ~1800 origins in this square.
        assertTrue(count < 100, "villages should be rare");
    }

    @Test
    void villagesOnlySpawnInBuildableBiomes() {
        assertTrue(TerrainGenerator.villageBiome(TerrainGenerator.Biome.PLAINS));
        assertTrue(TerrainGenerator.villageBiome(TerrainGenerator.Biome.DESERT));
        assertFalse(TerrainGenerator.villageBiome(TerrainGenerator.Biome.OCEAN));
        assertFalse(TerrainGenerator.villageBiome(TerrainGenerator.Biome.MOUNTAIN));
    }

    @Test
    void villagesMixBuildingTypes() {
        java.util.EnumSet<TerrainGenerator.BuildingType> seen = java.util.EnumSet.noneOf(TerrainGenerator.BuildingType.class);
        for (int i = 0; i < 60; i++) {
            seen.add(TerrainGenerator.buildingTypeFor(100, 200, i));
        }
        assertEquals(4, seen.size(), "over many plots all building types should appear");
    }
}
