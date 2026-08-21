package com.minecraftclone.world;

import com.minecraftclone.world.gen.TerrainGenerator;
import com.minecraftclone.world.gen.WorldGenSettings;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChunkMeshQueueTest {

    @Test
    void ungeneratedChunksStayOutOfTheMeshQueue() {
        Chunk fresh = new Chunk(new ChunkPos(0, 0));
        assertFalse(fresh.isGenerated());
        assertFalse(fresh.needsMesh());
        assertFalse(fresh.needsFirstMesh());
    }

    @Test
    void generatedChunkJumpsTheQueueUntilItsFirstUpload() {
        Chunk c = new Chunk(new ChunkPos(1, 2));
        c.markGenerated();
        assertTrue(c.needsFirstMesh());
        assertTrue(c.needsMesh());
    }

    @Test
    void firstMeshBeatsACloserRemesh() {
        assertTrue(World.compareMeshQueue(true, 100, false, 0) < 0,
                "a new chunk at the rim must mesh before re-stitching the chunk under your feet");
        assertTrue(World.compareMeshQueue(false, 0, true, 100) > 0);
        assertTrue(World.compareMeshQueue(true, 1, true, 4) < 0,
                "two new chunks still go nearest-first");
    }

    @Test
    void generateRaisesTheMeshScanHeight() {
        Chunk c = new Chunk(new ChunkPos(2, 3));
        new TerrainGenerator(1L, new WorldGenSettings()).generate(c);
        assertTrue(c.isGenerated());
        assertTrue(c.getHighestNonAirY() > 0,
                "if the scan bound stays -1, rebuildMesh uploads an empty mesh and the chunk is invisible");
        assertTrue(c.needsFirstMesh());
    }

    @Test
    void setRawBlocksRaisesTheMeshScanHeight() {
        Chunk c = new Chunk(new ChunkPos(0, 0));
        short[] data = new short[Chunk.SIZE * Chunk.HEIGHT * Chunk.SIZE];
        int y = 70;
        data[(y * Chunk.SIZE) * Chunk.SIZE] = BlockType.STONE.id;
        c.setRawBlocks(data);
        assertEquals(y, c.getHighestNonAirY());
    }
}
