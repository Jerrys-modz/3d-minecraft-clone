package com.minecraftclone.world.gen;

import com.minecraftclone.util.Noise;
import com.minecraftclone.world.BlockType;
import com.minecraftclone.world.Chunk;

import java.util.Random;

/**
 * Procedural terrain generator: a heightmap built from layered noise decides
 * ground level per column, a river noise channel carves winding water
 * channels into it, a second noise channel picks a rough "biome" (desert /
 * plains / forest / mountains), 3D noise carves caves and veins ore into
 * remaining stone (with lava pooling in the deepest cave pockets), and a
 * splash of trees/grass/flowers/cacti dresses up the surface. Everything is
 * deterministic from the world seed.
 */
public class TerrainGenerator {

    public static final int SEA_LEVEL = 34;
    private static final int BASE_HEIGHT = 40;
    private static final int SNOW_LINE = 78;
    private static final int MOUNTAIN_LINE = SEA_LEVEL + 25; // above this: pine forest instead of oak
    private static final int LAVA_LEVEL = 10;                // cave pockets at/below this fill with lava instead of air

    private final Noise heightNoise;
    private final Noise biomeNoise;
    private final Noise caveNoise;
    private final Noise riverNoise;
    private final Noise oreNoise;
    private final long seed;

    public TerrainGenerator(long seed) {
        this.seed = seed;
        this.heightNoise = new Noise(seed);
        this.biomeNoise = new Noise(seed ^ 0x9E3779B97F4A7C15L);
        this.caveNoise = new Noise(seed ^ 0xC2B2AE3D27D4EB4FL);
        this.riverNoise = new Noise(seed ^ 0xD1B54A32D192ED03L);
        this.oreNoise = new Noise(seed ^ 0x27D4EB2F165667C5L);
    }

    public void generate(Chunk chunk) {
        int originX = chunk.getOriginX();
        int originZ = chunk.getOriginZ();
        Random featureRandom = new Random(seed ^ ((long) chunk.getPos().x() * 341873128712L) ^ ((long) chunk.getPos().z() * 132897987541L));

        int[][] heights = new int[Chunk.SIZE][Chunk.SIZE];

        for (int x = 0; x < Chunk.SIZE; x++) {
            for (int z = 0; z < Chunk.SIZE; z++) {
                int wx = originX + x;
                int wz = originZ + z;

                double h = heightNoise.fbm2(wx * 0.01, wz * 0.01, 5, 0.5, 2.0);
                double mountains = heightNoise.fbm2(wx * 0.004, wz * 0.004, 3, 0.5, 2.0);
                double moisture = moistureAt(wx, wz);

                int height = BASE_HEIGHT + (int) Math.round(h * 14 + Math.max(0, mountains) * 40);
                height = Math.max(2, Math.min(Chunk.HEIGHT - 10, height));
                if (isRiver(wx, wz)) {
                    height = Math.min(height, SEA_LEVEL - 2);
                }
                heights[x][z] = height;

                boolean desert = moisture < -0.25 && height <= SEA_LEVEL + 6;

                for (int y = 0; y <= height; y++) {
                    BlockType type;
                    if (y == 0) {
                        type = BlockType.BEDROCK;
                    } else if (y < height - 4) {
                        if (caveAt(wx, y, wz)) {
                            type = (y <= LAVA_LEVEL) ? BlockType.LAVA : BlockType.AIR;
                        } else {
                            type = oreAt(wx, y, wz);
                        }
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

                // Fill water up to sea level for low terrain (including river channels).
                if (height < SEA_LEVEL) {
                    for (int y = height + 1; y <= SEA_LEVEL; y++) {
                        chunk.setLocal(x, y, z, BlockType.WATER);
                    }
                }
            }
        }

        // Surface dressing: keep everything fully inside the chunk (margin 2) so
        // tree canopies never spill into a not-yet-generated neighboring chunk.
        for (int x = 2; x < Chunk.SIZE - 2; x++) {
            for (int z = 2; z < Chunk.SIZE - 2; z++) {
                int height = heights[x][z];
                if (height < SEA_LEVEL || height > SNOW_LINE) continue;

                int wx = originX + x;
                int wz = originZ + z;
                double moisture = moistureAt(wx, wz);
                boolean desert = moisture < -0.25 && height <= SEA_LEVEL + 6;
                boolean forest = moisture > 0.25;

                BlockType surface = chunk.getLocal(x, height, z);
                if (desert && surface == BlockType.SAND) {
                    if (featureRandom.nextInt(180) == 0) {
                        placeCactus(chunk, x, height + 1, z, featureRandom);
                    }
                    continue;
                }
                if (surface != BlockType.GRASS) continue;

                if (height > MOUNTAIN_LINE) {
                    if (featureRandom.nextInt(60) == 0) {
                        placePineTree(chunk, x, height + 1, z, featureRandom);
                    }
                } else if (forest) {
                    if (featureRandom.nextInt(20) == 0) {
                        placeTree(chunk, x, height + 1, z, featureRandom);
                    } else {
                        placeGroundCover(chunk, x, height + 1, z, featureRandom, true);
                    }
                } else {
                    if (featureRandom.nextInt(90) == 0) {
                        placeTree(chunk, x, height + 1, z, featureRandom);
                    } else {
                        placeGroundCover(chunk, x, height + 1, z, featureRandom, false);
                    }
                }
            }
        }

        chunk.markGenerated();
        chunk.markDirty();
    }

    private double moistureAt(int wx, int wz) {
        return biomeNoise.fbm2(wx * 0.006 + 500, wz * 0.006 + 500, 3, 0.5, 2.0);
    }

    /** Winding rivers: the near-zero contour of a low-frequency noise field, thickened by a small threshold band. */
    private boolean isRiver(int wx, int wz) {
        double r = riverNoise.fbm2(wx * 0.004, wz * 0.004, 2, 0.5, 2.0);
        return Math.abs(r) < 0.02;
    }

    private boolean caveAt(int wx, int y, int wz) {
        if (y < 4 || y > 60) return false;
        double n = caveNoise.fbm3(wx * 0.045, y * 0.07, wz * 0.045, 3, 0.5, 2.0);
        return n > 0.26; // ~top 9% of the observed noise distribution (see calibration notes on oreAt)
    }

    /**
     * Ore veins: independent 3D noise fields per ore type (decorrelated via large
     * coordinate offsets), each gated to a depth range and rarity threshold -
     * rarer/deeper ores are checked first so an unlikely overlap resolves in
     * favor of the rarer one. Falls back to plain stone.
     */
    // fbm3(octaves=3, persistence=0.5) empirically lands in roughly [-0.71, 0.68], not [-1, 1],
    // so these thresholds are picked from the actual observed distribution (percentiles), not
    // guessed against the theoretical range - e.g. 0.30 is around the 97th percentile.
    private BlockType oreAt(int wx, int y, int wz) {
        if (y >= 5 && y <= 16 && oreNoise.fbm3(wx * 0.11 + 1000, y * 0.11, wz * 0.11 + 1000, 3, 0.5, 2.0) > 0.55) {
            return BlockType.DIAMOND_ORE;
        }
        if (y >= 5 && y <= 32 && oreNoise.fbm3(wx * 0.10 + 2000, y * 0.10, wz * 0.10 + 2000, 3, 0.5, 2.0) > 0.48) {
            return BlockType.GOLD_ORE;
        }
        if (y >= 5 && y <= 64 && oreNoise.fbm3(wx * 0.10 + 3000, y * 0.10, wz * 0.10 + 3000, 3, 0.5, 2.0) > 0.40) {
            return BlockType.IRON_ORE;
        }
        if (y >= 5 && y <= 100 && oreNoise.fbm3(wx * 0.09 + 4000, y * 0.09, wz * 0.09 + 4000, 3, 0.5, 2.0) > 0.30) {
            return BlockType.COAL_ORE;
        }
        return BlockType.STONE;
    }

    /** Tall grass tufts, the occasional flower, and rarer berry bushes (a foraged food source), scattered on plains/forest grass. */
    private void placeGroundCover(Chunk chunk, int x, int y, int z, Random rnd, boolean dense) {
        int bushChance = dense ? 70 : 140;
        int grassChance = dense ? 5 : 10;
        int flowerChance = dense ? 24 : 45;
        if (rnd.nextInt(bushChance) == 0) {
            chunk.setLocal(x, y, z, BlockType.BERRY_BUSH);
        } else if (rnd.nextInt(flowerChance) == 0) {
            chunk.setLocal(x, y, z, rnd.nextBoolean() ? BlockType.FLOWER_RED : BlockType.FLOWER_YELLOW);
        } else if (rnd.nextInt(grassChance) == 0) {
            chunk.setLocal(x, y, z, BlockType.TALL_GRASS);
        }
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

    /** A narrower, conical evergreen: shrinking leaf rings stacked up a taller trunk. Used at higher, colder elevations. */
    private void placePineTree(Chunk chunk, int x, int y, int z, Random rnd) {
        int trunkHeight = 6 + rnd.nextInt(3);
        for (int i = 0; i < trunkHeight; i++) {
            chunk.setLocal(x, y + i, z, BlockType.WOOD_LOG);
        }
        int layers = 4;
        int canopyBase = y + trunkHeight - layers;
        for (int layer = 0; layer < layers; layer++) {
            int cy = canopyBase + layer;
            int radius = Math.max(1, 2 - layer / 2);
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (dx == 0 && dz == 0) continue; // trunk column
                    if (Math.abs(dx) == radius && Math.abs(dz) == radius) continue; // round corners
                    BlockType existing = chunk.getLocal(x + dx, cy, z + dz);
                    if (existing == BlockType.AIR) {
                        chunk.setLocal(x + dx, cy, z + dz, BlockType.LEAVES);
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
