package com.minecraftclone.world.gen;

import com.minecraftclone.world.BlockType;
import com.minecraftclone.world.Chunk;
import com.minecraftclone.world.ChunkPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NetherGeneratorTest {

    private static Chunk generate(long seed, int cx, int cz) {
        Chunk chunk = new Chunk(new ChunkPos(cx, cz));
        new NetherGenerator(seed).generate(chunk);
        return chunk;
    }

    @Test
    void isDeterministicFromSeed() {
        for (int cx = -2; cx <= 2; cx++) {
            for (int cz = -2; cz <= 2; cz++) {
                Chunk a = generate(12345L, cx, cz);
                Chunk b = generate(12345L, cx, cz);
                assertEquals(a.getRawBlocks().length, b.getRawBlocks().length);
                for (int i = 0; i < a.getRawBlocks().length; i++) {
                    assertEquals(a.getRawBlocks()[i], b.getRawBlocks()[i], "chunk " + cx + "," + cz + " byte " + i);
                }
            }
        }
    }

    @Test
    void hasBedrockFloorAndCeiling() {
        Chunk chunk = generate(42L, 0, 0);
        for (int x = 0; x < Chunk.SIZE; x++) {
            for (int z = 0; z < Chunk.SIZE; z++) {
                assertEquals(BlockType.BEDROCK, chunk.getLocal(x, 0, z), "floor");
                // The roof is sealed from the BEDROCK_TOP layer up (250..255);
                // below that is the netherrack filler that hides the void.
                for (int y = Chunk.HEIGHT - 3; y < Chunk.HEIGHT; y++) {
                    assertEquals(BlockType.BEDROCK, chunk.getLocal(x, y, z), "ceiling at y=" + y);
                }
            }
        }
    }

    @Test
    void isMadeOfNetherrackAndLavaNotOverworldTerrain() {
        Chunk chunk = generate(7L, 1, 1);
        boolean foundNetherrack = false;
        for (int x = 0; x < Chunk.SIZE; x++) {
            for (int z = 0; z < Chunk.SIZE; z++) {
                // Check only the nether cavern space, not the ceiling (which is bedrock/netherrack filler from CEILING_START=220 up)
                for (int y = 1; y < 220; y++) {
                    BlockType t = chunk.getLocal(x, y, z);
                    if (t == BlockType.NETHERRACK || t == BlockType.SOUL_SAND || t == BlockType.LAVA
                            || t == BlockType.GLOWSTONE || t == BlockType.AIR) {
                        if (t == BlockType.NETHERRACK) foundNetherrack = true;
                    } else {
                        assertTrue(false, "unexpected block " + t + " at " + x + "," + y + "," + z);
                    }
                }
            }
        }
        assertTrue(foundNetherrack, "chunk should contain netherrack");
    }

    @Test
    void reportsNetherBiome() {
        NetherGenerator gen = new NetherGenerator(1L);
        assertEquals(TerrainGenerator.Biome.NETHER, gen.biomeAtWorld(0, 0));
    }
}
