package com.minecraftclone.world;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Surface sampling and v1/v2 map.dat compatibility — GPU-free.
 */
class MapDataTest {

    @TempDir
    Path tmp;

    @Test
    void exploreSamplesTheGroundUnderPlants() {
        MapData data = new MapData();
        data.exploreChunk(0, 0, (x, y, z) -> {
            if (y > 42) return BlockType.AIR;
            if (y == 42) return BlockType.TALL_GRASS;
            if (y == 41) return BlockType.GRASS;
            return BlockType.DIRT;
        });
        assertTrue(data.isChunkExplored(0, 0));
        assertTrue(data.hasSurface(0, 0));
        assertEquals(BlockType.GRASS, data.getSurfaceBlock(3, 5),
                "cross plants should not paint the map");
        assertEquals(41, data.getSurfaceY(3, 5));
    }

    @Test
    void v1ExploredChunksGainSurfaceOnRevisit() {
        MapData data = new MapData();
        // Simulate a v1 save: chunk is explored, veins recorded, no surface.
        Path file = tmp.resolve("v1-no-surface.dat");
        data.exploreChunk(0, 0, (x, y, z) -> {
            if (y == 10) return BlockType.SAND;
            if (y == 20 && x == 4 && z == 4) return BlockType.IRON_ORE;
            return BlockType.AIR;
        });
        data.saveTo(file);

        MapData loaded = new MapData();
        loaded.loadFrom(file);
        assertTrue(loaded.hasSurface(0, 0));

        // Fresh v1 file (no surface section): load then re-explore.
        MapData v1 = new MapData();
        try {
            writeV1(file, 0, 0, 4, 12, 4, "IRON_ORE");
        } catch (IOException e) {
            fail(e);
        }
        v1.loadFrom(file);
        assertTrue(v1.isChunkExplored(0, 0));
        assertFalse(v1.hasSurface(0, 0));
        v1.exploreChunk(0, 0, (x, y, z) -> y == 12 ? BlockType.SNOW : BlockType.AIR);
        assertEquals(BlockType.SNOW, v1.getSurfaceBlock(1, 1),
                "re-entering a v1 chunk should fill in terrain");
        assertEquals(1, v1.getVeinsInChunk(0, 0).size(), "v1 veins must not be rescanned away");
    }

    private static void writeV1(Path file, int cx, int cz, int x, int y, int z, String ore)
            throws IOException {
        long key = MapData.encodeChunkKey(cx, cz);
        try (DataOutputStream out = new DataOutputStream(
                new BufferedOutputStream(Files.newOutputStream(file)))) {
            out.writeInt(1);
            out.writeInt(1);
            out.writeLong(key);
            out.writeInt(1);
            out.writeLong(key);
            out.writeInt(1);
            out.writeInt(x);
            out.writeInt(y);
            out.writeInt(z);
            out.writeUTF(ore);
        }
    }

    @Test
    void exploringAChunkDoesNotRevealOres() {
        MapData data = new MapData();
        data.exploreChunk(0, 0, (x, y, z) -> {
            if (y == 20 && x == 4 && z == 4) return BlockType.COPPER_ORE;
            if (y == 40) return BlockType.GRASS;
            if (y < 40) return BlockType.DIRT;
            return BlockType.AIR;
        });
        assertTrue(data.isChunkExplored(0, 0));
        assertTrue(data.hasSurface(0, 0));
        assertTrue(data.getVeinsInChunk(0, 0).isEmpty(),
                "walking a chunk must not dump GTNH mix waypoints onto the map");
    }

    @Test
    void lookingAtOreDiscoversTheMix() {
        MapData data = new MapData();
        assertTrue(data.discoverOre(4, 20, 4, BlockType.COPPER_ORE));
        assertEquals(1, data.getVeinsInChunk(0, 0).size());
        assertEquals(BlockType.COPPER_ORE, data.getVeinsInChunk(0, 0).get(0).oreType);
        assertFalse(data.discoverOre(5, 21, 5, BlockType.COPPER_ORE),
                "same ore type in the same chunk is already prospected");
        assertTrue(data.discoverOre(8, 18, 8, BlockType.TIN_ORE),
                "a mix secondary is its own record so clustering can name the mix");
    }

    @Test
    void smallOreIsAProspectingSignalForTheMix() {
        assertEquals(BlockType.COPPER_ORE, MapData.prospectableOre(BlockType.SMALL_COPPER_ORE));
        assertEquals(BlockType.COPPER_ORE, MapData.prospectableOre(BlockType.COPPER_ORE));
        assertNull(MapData.prospectableOre(BlockType.STONE));
        MapData data = new MapData();
        assertTrue(data.discoverOre(3, 50, 3, BlockType.SMALL_TIN_ORE));
        assertEquals(BlockType.TIN_ORE, data.getVeinsInChunk(0, 0).get(0).oreType);
    }

    @Test
    void fullSizeOresAreRecordedAsVeins() {
        MapData data = new MapData();
        data.exploreChunk(0, 0, (x, y, z) -> {
            if (y == 20 && x == 4 && z == 4) return BlockType.COPPER_ORE;
            if (y == 20 && x == 5 && z == 4) return BlockType.SMALL_COPPER_ORE;
            if (y == 40) return BlockType.GRASS;
            if (y < 40) return BlockType.DIRT;
            return BlockType.AIR;
        });
        assertTrue(data.getVeinsInChunk(0, 0).isEmpty());
        data.discoverOre(4, 20, 4, BlockType.COPPER_ORE);
        assertEquals(1, data.getVeinsInChunk(0, 0).size());
        assertEquals(BlockType.COPPER_ORE, data.getVeinsInChunk(0, 0).get(0).oreType);
        assertTrue(MapData.isFullSizeOre(BlockType.COPPER_ORE));
        assertFalse(MapData.isFullSizeOre(BlockType.SMALL_COPPER_ORE));
        assertTrue(MapData.isFullSizeOre(BlockType.COAL_ORE));
    }

    @Test
    void v2SaveRoundTripsSurfaceAndVeins() {
        MapData data = new MapData();
        data.exploreChunk(1, -2, (x, y, z) -> {
            if (y == 18 && x == 20 && z == -30) return BlockType.MAGNETITE_ORE;
            if (y == 44) return BlockType.SAND;
            if (y < 44) return BlockType.SAND;
            return BlockType.AIR;
        });
        data.discoverOre(20, 18, -30, BlockType.MAGNETITE_ORE);
        Path file = tmp.resolve("map.dat");
        data.saveTo(file);
        assertTrue(Files.exists(file));

        MapData loaded = new MapData();
        loaded.loadFrom(file);
        assertTrue(loaded.isChunkExplored(1, -2));
        assertTrue(loaded.hasSurface(1, -2));
        assertEquals(BlockType.SAND, loaded.getSurfaceBlock(20, -30));
        assertEquals(1, loaded.getVeinsInChunk(1, -2).size());
        assertEquals(BlockType.MAGNETITE_ORE, loaded.getVeinsInChunk(1, -2).get(0).oreType);
    }

    @Test
    void v1SavesStillLoadWithoutSurface() throws IOException {
        Path file = tmp.resolve("old-map.dat");
        long key = MapData.encodeChunkKey(2, 3);
        try (DataOutputStream out = new DataOutputStream(
                new BufferedOutputStream(Files.newOutputStream(file)))) {
            out.writeInt(1); // v1
            out.writeInt(1);
            out.writeLong(key);
            out.writeInt(1);
            out.writeLong(key);
            out.writeInt(1);
            out.writeInt(32);
            out.writeInt(12);
            out.writeInt(48);
            out.writeUTF("IRON_ORE");
        }

        MapData loaded = new MapData();
        loaded.loadFrom(file);
        assertTrue(loaded.isChunkExplored(2, 3));
        assertFalse(loaded.hasSurface(2, 3), "v1 had no terrain sample");
        assertNull(loaded.getSurfaceBlock(32, 48));
        assertEquals(1, loaded.getVeinsInChunk(2, 3).size());
        assertEquals(BlockType.IRON_ORE, loaded.getVeinsInChunk(2, 3).get(0).oreType);
    }

    @Test
    void exploreGeneratedChunkReadsTheChunkDirectly() {
        Chunk chunk = new Chunk(new ChunkPos(2, -1));
        for (int z = 0; z < Chunk.SIZE; z++) {
            for (int x = 0; x < Chunk.SIZE; x++) {
                chunk.setLocal(x, 40, z, BlockType.GRASS);
                chunk.setLocal(x, 39, z, BlockType.DIRT);
            }
        }
        chunk.setLocal(4, 22, 4, BlockType.COPPER_ORE);
        chunk.setLocal(4, 42, 4, BlockType.TALL_GRASS);
        chunk.markGenerated();

        MapData data = new MapData();
        data.exploreGeneratedChunk(chunk);
        assertTrue(data.hasSurface(2, -1));
        assertEquals(BlockType.GRASS, data.getSurfaceBlock(2 * 16 + 3, -1 * 16 + 5));
        assertEquals(BlockType.GRASS, data.getSurfaceBlock(2 * 16 + 4, -1 * 16 + 4),
                "tall grass on grass should still show grass");
        assertTrue(data.getVeinsInChunk(2, -1).isEmpty(),
                "generated-chunk mapping is terrain only");
        data.discoverOre(2 * 16 + 4, 22, -1 * 16 + 4, BlockType.COPPER_ORE);
        assertEquals(1, data.getVeinsInChunk(2, -1).size());
        assertEquals(BlockType.COPPER_ORE, data.getVeinsInChunk(2, -1).get(0).oreType);
    }

    @Test
    void ungeneratedChunksAreNotMapped() {
        Chunk chunk = new Chunk(new ChunkPos(0, 0));
        chunk.setLocal(0, 10, 0, BlockType.STONE);
        MapData data = new MapData();
        data.exploreGeneratedChunk(chunk);
        assertFalse(data.hasSurface(0, 0));
        assertFalse(data.isChunkExplored(0, 0));
    }
}
