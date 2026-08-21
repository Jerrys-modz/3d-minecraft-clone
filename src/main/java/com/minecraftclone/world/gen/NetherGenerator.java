package com.minecraftclone.world.gen;

import com.minecraftclone.util.Noise;
import com.minecraftclone.world.BlockType;
import com.minecraftclone.world.Chunk;

import java.util.Random;

/**
 * Generates the Nether: a low, cavernous hellscape of netherrack sealed top
 * and bottom by bedrock (so you can't tunnel out the top or fall out the
 * bottom), with lava lakes pooling in the lowlands, soul-sand patches and
 * glowstone clusters scattered over the surfaces, and lava-flooded caves. No
 * water, no sky - just constant dim red light. Deterministic from the seed.
 */
public class NetherGenerator implements WorldGenerator {

    private static final int GROUND_LEVEL = 42;         // baseline surface height
    private static final int LAVA_LEVEL = 34;           // terrain at/below this is flooded with lava
    private static final int CEILING_START = 220;       // netherrack filler above this
    private static final int BEDROCK_TOP = 250;         // bedrock sealing the top

    private final Noise groundNoise;
    private final Noise caveNoise;
    private final Noise patchNoise;
    private final long seed;

    public NetherGenerator(long seed) {
        this.seed = seed;
        this.groundNoise = new Noise(seed ^ 0x29A17A1FB91D97F1L);
        this.caveNoise = new Noise(seed ^ 0x7C4EA6C1F9C2A3B7L);
        this.patchNoise = new Noise(seed ^ 0x9D8F5A2B3C4D5E6FL);
    }

    @Override
    public int seaLevel() {
        return LAVA_LEVEL;
    }

    @Override
    public int terrainHeight(int wx, int wz) {
        return LAVA_LEVEL;
    }

    @Override
    public TerrainGenerator.Biome biomeAtWorld(int wx, int wz) {
        return TerrainGenerator.Biome.NETHER;
    }

    @Override
    public void generate(Chunk chunk) {
        int originX = chunk.getOriginX();
        int originZ = chunk.getOriginZ();
        Random rnd = new Random(seed ^ ((long) chunk.getPos().x() * 341873128712L) ^ ((long) chunk.getPos().z() * 132897987541L));

        int[][] surface = new int[Chunk.SIZE][Chunk.SIZE];
        for (int x = 0; x < Chunk.SIZE; x++) {
            for (int z = 0; z < Chunk.SIZE; z++) {
                int wx = originX + x;
                int wz = originZ + z;
                int h = GROUND_LEVEL + (int) Math.round(groundNoise.fbm2(wx * 0.02, wz * 0.02, 4, 0.5, 2.0) * 14);
                surface[x][z] = h;
            }
        }

        for (int x = 0; x < Chunk.SIZE; x++) {
            for (int z = 0; z < Chunk.SIZE; z++) {
                int wx = originX + x;
                int wz = originZ + z;
                int h = surface[x][z];

                for (int y = 0; y <= h; y++) {
                    BlockType type;
                    if (y == 0) {
                        type = BlockType.BEDROCK;
                    } else if (caveAt(wx, y, wz)) {
                        type = (y <= LAVA_LEVEL) ? BlockType.LAVA : BlockType.AIR;
                    } else if (y == h) {
                        // Surface cap: soul-sand in scattered patches, else netherrack.
                        type = (patchNoise.fbm2(wx * 0.05, wz * 0.05, 3, 0.5, 2.0) > 0.55) ? BlockType.SOUL_SAND : BlockType.NETHERRACK;
                    } else {
                        type = BlockType.NETHERRACK;
                    }
                    if (type != BlockType.AIR) {
                        chunk.setLocal(x, y, z, type);
                    }
                }

                // Lava lakes flood the lowlands up to a fixed level, the way oceans
                // fill the overworld's basins.
                if (h < LAVA_LEVEL) {
                    for (int y = h + 1; y <= LAVA_LEVEL; y++) {
                        chunk.setLocal(x, y, z, BlockType.LAVA);
                    }
                }

                // The bedrock roof and netherrack filler above it seal the ceiling.
                for (int y = Math.max(h + 1, CEILING_START); y < BEDROCK_TOP; y++) {
                    chunk.setLocal(x, y, z, BlockType.NETHERRACK);
                }
                for (int y = BEDROCK_TOP; y < Chunk.HEIGHT; y++) {
                    chunk.setLocal(x, y, z, BlockType.BEDROCK);
                }
            }
        }

        // Glowstone clusters: pockets of glowstone hung from the ceiling and the
        // occasional nodule on the ground, so the nether has dim self-light.
        for (int i = 0; i < 3; i++) {
            int cx = 2 + rnd.nextInt(Chunk.SIZE - 4);
            int cz = 2 + rnd.nextInt(Chunk.SIZE - 4);
            int cy = CEILING_START - 1 - rnd.nextInt(6);
            placeGlowstoneCluster(chunk, cx, cy, cz, rnd);
        }
        for (int i = 0; i < 2; i++) {
            int cx = 2 + rnd.nextInt(Chunk.SIZE - 4);
            int cz = 2 + rnd.nextInt(Chunk.SIZE - 4);
            int cy = surface[cx][cz] - 1;
            placeGlowstoneCluster(chunk, cx, cy, cz, rnd);
        }

        chunk.markGenerated();
        chunk.markDirty();
    }

    private void placeGlowstoneCluster(Chunk chunk, int cx, int cy, int cz, Random rnd) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (rnd.nextInt(3) == 0) continue;
                    chunk.setLocal(cx + dx, cy + dy, cz + dz, BlockType.GLOWSTONE);
                }
            }
        }
    }

    private boolean caveAt(int wx, int y, int wz) {
        if (y < 3) return false;
        double n = caveNoise.fbm3(wx * 0.045, y * 0.07, wz * 0.045, 3, 0.5, 2.0);
        return n > 0.28;
    }
}
