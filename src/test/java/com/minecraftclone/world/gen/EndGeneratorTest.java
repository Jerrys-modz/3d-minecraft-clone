package com.minecraftclone.world.gen;

import com.minecraftclone.world.BlockType;
import com.minecraftclone.world.Chunk;
import com.minecraftclone.world.ChunkPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EndGeneratorTest {

    private static Chunk generate(long seed, int cx, int cz) {
        Chunk chunk = new Chunk(new ChunkPos(cx, cz));
        new EndGenerator(seed).generate(chunk);
        return chunk;
    }

    @Test
    void isDeterministicFromSeed() {
        for (int cx = -2; cx <= 2; cx++) {
            for (int cz = -2; cz <= 2; cz++) {
                Chunk a = generate(314L, cx, cz);
                Chunk b = generate(314L, cx, cz);
                assertEquals(a.getRawBlocks().length, b.getRawBlocks().length);
                for (int i = 0; i < a.getRawBlocks().length; i++) {
                    assertEquals(a.getRawBlocks()[i], b.getRawBlocks()[i], "chunk " + cx + "," + cz + " byte " + i);
                }
            }
        }
    }

    @Test
    void hasAnIslandAtTheOrigin() {
        // The main island is centered on the world origin; the chunk (0,0) should
        // contain a good amount of end stone somewhere above the void.
        Chunk chunk = generate(1L, 0, 0);
        int endStone = 0;
        for (int x = 0; x < Chunk.SIZE; x++) {
            for (int z = 0; z < Chunk.SIZE; z++) {
                for (int y = 0; y < Chunk.HEIGHT; y++) {
                    if (chunk.getLocal(x, y, z) == BlockType.END_STONE) endStone++;
                }
            }
        }
        assertTrue(endStone > 500, "origin chunk should be packed with end stone, found " + endStone);
    }

    @Test
    void farAwayIsEmptyVoid() {
        Chunk chunk = generate(1L, 200, 200);
        for (int x = 0; x < Chunk.SIZE; x++) {
            for (int z = 0; z < Chunk.SIZE; z++) {
                for (int y = 0; y < Chunk.HEIGHT; y++) {
                    assertEquals(BlockType.AIR, chunk.getLocal(x, y, z), "void chunk should be empty");
                }
            }
        }
    }

    @Test
    void reportsEndBiome() {
        EndGenerator gen = new EndGenerator(1L);
        assertEquals(TerrainGenerator.Biome.END, gen.biomeAtWorld(0, 0));
    }
}
