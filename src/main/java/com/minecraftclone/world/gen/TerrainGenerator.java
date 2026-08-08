package com.minecraftclone.world.gen;

import com.minecraftclone.util.Noise;
import com.minecraftclone.world.BlockType;
import com.minecraftclone.world.Chunk;

import java.util.Random;

/**
 * Procedural terrain generator: a heightmap built from layered noise decides
 * ground level per column, a second noise channel picks a rough "biome"
 * (desert / plains / mountains), and a splash of trees/cacti dresses up the
 * surface. Everything is deterministic from the world seed.
 */
public class TerrainGenerator {

    public static final int SEA_LEVEL = 34;
    private static final int BASE_HEIGHT = 40;
    private static final int SNOW_LINE = 78;

    private final Noise heightNoise;
    private final Noise biomeNoise;
    private final Noise caveNoise;
    private final long seed;

    public TerrainGenerator(long seed) {
        this.seed = seed;
        this.heightNoise = new Noise(seed);
        this.biomeNoise = new Noise(seed ^ 0x9E3779B97F4A7C15L);
        this.caveNoise = new Noise(seed ^ 0xC2B2AE3D27D4EB4FL);
    }

    public void generate(Chunk chunk) {
        int originX = chunk.getOriginX();
        int originZ = chunk.getOriginZ();
        Random treeRandom = new Random(seed ^ ((long) chunk.getPos().x() * 341873128712L) ^ ((long) chunk.getPos().z() * 132897987541L));

        int[][] heights = new int[Chunk.SIZE][Chunk.SIZE];

        for (int x = 0; x < Chunk.SIZE; x++) {
            for (int z = 0; z < Chunk.SIZE; z++) {
                int wx = originX + x;
                int wz = originZ + z;

                double h = heightNoise.fbm2(wx * 0.01, wz * 0.01, 5, 0.5, 2.0);
                double mountains = heightNoise.fbm2(wx * 0.004, wz * 0.004, 3, 0.5, 2.0);
                double moisture = biomeNoise.fbm2(wx * 0.006 + 500, wz * 0.006 + 500, 3, 0.5, 2.0);

                int height = BASE_HEIGHT + (int) Math.round(h * 14 + Math.max(0, mountains) * 40);
                height = Math.max(2, Math.min(Chunk.HEIGHT - 10, height));
                heights[x][z] = height;

                boolean desert = moisture < -0.25 && height <= SEA_LEVEL + 6;

                for (int y = 0; y <= height; y++) {
                    BlockType type;
                    if (y == 0) {
                        type = BlockType.BEDROCK;
                    } else if (y < height - 4) {
                        type = caveAt(wx, y, wz) ? BlockType.AIR : BlockType.STONE;
                    } else if (y < height) {
                        type = desert ? BlockType.SAND : (height > SNOW_LINE ? BlockType.STONE : BlockType.DIRT);
                        if (!desert && y < height && caveAt(wx, y, wz) && y < height - 1) {
                            type = BlockType.AIR;
                        }
                    } else { // y == height, the surface block
                        if (desert) {
                            type = BlockType.SAND;
                        } else if (height <= SEA_LEVEL + 1) {
                            type = BlockType.SAND;
                        } else if (height > SNOW_LINE) {
                            type = BlockType.SNOW;
                        } else {
                            type = BlockType.GRASS;
                        }
                    }
                    if (type != BlockType.AIR) {
                        chunk.setLocal(x, y, z, type);
                    }
                }

                // Fill water up to sea level for low terrain.
                if (height < SEA_LEVEL) {
                    for (int y = height + 1; y <= SEA_LEVEL; y++) {
                        chunk.setLocal(x, y, z, BlockType.WATER);
                    }
                }
            }
        }

        // Vegetation: keep trees fully inside the chunk (margin 2) so canopies
        // never spill into a not-yet-generated neighboring chunk.
        for (int x = 2; x < Chunk.SIZE - 2; x++) {
            for (int z = 2; z < Chunk.SIZE - 2; z++) {
                int height = heights[x][z];
                if (height < SEA_LEVEL || height > SNOW_LINE) continue;

                int wx = originX + x;
                int wz = originZ + z;
                double moisture = biomeNoise.fbm2(wx * 0.006 + 500, wz * 0.006 + 500, 3, 0.5, 2.0);
                boolean desert = moisture < -0.25 && height <= SEA_LEVEL + 6;

                BlockType surface = chunk.getLocal(x, height, z);
                if (desert && surface == BlockType.SAND) {
                    if (treeRandom.nextInt(180) == 0) {
                        placeCactus(chunk, x, height + 1, z, treeRandom);
                    }
                } else if (surface == BlockType.GRASS) {
                    if (treeRandom.nextInt(90) == 0) {
                        placeTree(chunk, x, height + 1, z, treeRandom);
                    }
                }
            }
        }

        chunk.markGenerated();
        chunk.markDirty();
    }

    private boolean caveAt(int wx, int y, int wz) {
        if (y < 4 || y > 60) return false;
        double n = caveNoise.fbm2(wx * 0.06 + y * 0.06, wz * 0.06 - y * 0.04, 3, 0.5, 2.0);
        return n > 0.55;
    }

    private void placeTree(Chunk chunk, int x, int y, int z, Random rnd) {
        int trunkHeight = 4 + rnd.nextInt(2);
        for (int i = 0; i < trunkHeight; i++) {
            chunk.setLocal(x, y + i, z, BlockType.WOOD_LOG);
        }
        int canopyBase = y + trunkHeight - 2;
        for (int cy = 0; cy <= 2; cy++) {
            int radius = (cy == 2) ? 1 : 2;
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (dx == 0 && dz == 0 && cy < 2) continue; // trunk already there below top
                    if (Math.abs(dx) == radius && Math.abs(dz) == radius && radius == 2) continue; // round corners
                    BlockType existing = chunk.getLocal(x + dx, canopyBase + cy, z + dz);
                    if (existing == BlockType.AIR) {
                        chunk.setLocal(x + dx, canopyBase + cy, z + dz, BlockType.LEAVES);
                    }
                }
            }
        }
        chunk.setLocal(x, y + trunkHeight, z, BlockType.LEAVES);
    }

    private void placeCactus(Chunk chunk, int x, int y, int z, Random rnd) {
        int h = 1 + rnd.nextInt(3);
        for (int i = 0; i < h; i++) {
            chunk.setLocal(x, y + i, z, BlockType.CACTUS);
        }
    }
}
