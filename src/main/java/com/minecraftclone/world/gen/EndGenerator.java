package com.minecraftclone.world.gen;

import com.minecraftclone.util.Noise;
import com.minecraftclone.world.BlockType;
import com.minecraftclone.world.Chunk;

import java.util.Random;

/**
 * Generates the End: a lone floating island of end stone adrift in a void,
 * ringed by a few tall obsidian pillars and scattered smaller floating
 * islets, all high above an empty nothingness (falling off the edge drops
 * you into the void below the world). Everything else is air. Deterministic
 * from the seed.
 */
public class EndGenerator implements WorldGenerator {

    private static final int ISLAND_Y = 60;        // vertical center of the main island
    private static final double ISLAND_RADIUS = 34.0; // rough radius of the main island
    private static final int PILLAR_COUNT = 4;

    private final Noise heightNoise;
    private final Noise pillarNoise;
    private final Noise isletNoise;
    private final long seed;

    public EndGenerator(long seed) {
        this.seed = seed;
        this.heightNoise = new Noise(seed ^ 0x3F4A5B6C7D8E9F0AL);
        this.pillarNoise = new Noise(seed ^ 0x6A0E3B1D5C9F2E7AL);
        this.isletNoise = new Noise(seed ^ 0x9C1F2E3D4B5A6978L);
    }

    @Override
    public int seaLevel() {
        return 0; // the void: no ground unless a chunk generated it
    }

    @Override
    public TerrainGenerator.Biome biomeAtWorld(int wx, int wz) {
        return TerrainGenerator.Biome.END;
    }

    @Override
    public void generate(Chunk chunk) {
        int originX = chunk.getOriginX();
        int originZ = chunk.getOriginZ();
        Random rnd = new Random(seed ^ ((long) chunk.getPos().x() * 341873128712L) ^ ((long) chunk.getPos().z() * 132897987541L));

        // The main island is centered on the world origin. For each column that
        // falls within it, carve an end-stone dome with an uneven rim.
        for (int x = 0; x < Chunk.SIZE; x++) {
            for (int z = 0; z < Chunk.SIZE; z++) {
                int wx = originX + x;
                int wz = originZ + z;
                double dist = Math.sqrt((double) wx * wx + (double) wz * wz);
                double rim = heightNoise.fbm2(wx * 0.03, wz * 0.03, 2, 0.5, 2.0);
                double radius = ISLAND_RADIUS + rim * 8;
                if (dist <= radius) {
                    // A dome: thicker near the center, thinning to a rim at the edge.
                    int maxY = ISLAND_Y + (int) Math.round((radius - dist) * 0.4) + (int) (rim * 2);
                    int minY = ISLAND_Y - 6 - (int) Math.round((radius - dist) * 0.6);
                    for (int y = minY; y <= maxY; y++) {
                        chunk.setLocal(x, y, z, BlockType.END_STONE);
                    }
                }
            }
        }

        // Obsidian pillars: a few tall columns poking up from the island surface,
        // placed at random angles around the island's edge.
        for (int p = 0; p < PILLAR_COUNT; p++) {
            double angle = pillarNoise.noise2(p * 7.13, 3.7) * Math.PI * 2;
            double pdist = ISLAND_RADIUS * (0.7 + pillarNoise.noise2(p * 3.3, 9.1) * 0.3);
            int px = (int) Math.round(Math.cos(angle) * pdist);
            int pz = (int) Math.round(Math.sin(angle) * pdist);
            int pHeight = 20 + (int) (pillarNoise.noise2(p * 11.7, 5.2) * 14);
            int baseY = islandSurfaceAt(chunk, px, pz);
            if (baseY > 0) {
                for (int y = baseY + 1; y <= baseY + pHeight; y++) {
                    chunk.setLocal(px - originX, y, pz - originZ, BlockType.OBSIDIAN);
                }
            }
        }

        // Scattered floating islets of end stone drifting around the main island.
        int islets = 4 + rnd.nextInt(4);
        for (int i = 0; i < islets; i++) {
            double angle = rnd.nextDouble() * Math.PI * 2;
            double dist = ISLAND_RADIUS + 12 + rnd.nextDouble() * 40;
            int ix = (int) Math.round(Math.cos(angle) * dist);
            int iz = (int) Math.round(Math.sin(angle) * dist);
            int iy = ISLAND_Y + 8 + rnd.nextInt(20);
            int ir = 2 + rnd.nextInt(3);
            for (int dx = -ir; dx <= ir; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -ir; dz <= ir; dz++) {
                        if (dx * dx + dz * dz <= ir * ir) {
                            chunk.setLocal(ix - originX + dx, iy + dy, iz - originZ + dz, BlockType.END_STONE);
                        }
                    }
                }
            }
        }

        chunk.markGenerated();
        chunk.markDirty();
    }

    /** Highest end-stone block in this chunk's column, or 0 if the column is void. */
    private int islandSurfaceAt(Chunk chunk, int wx, int wz) {
        int lx = wx - chunk.getOriginX();
        int lz = wz - chunk.getOriginZ();
        for (int y = Chunk.HEIGHT - 1; y >= 0; y--) {
            if (chunk.getLocal(lx, y, lz) == BlockType.END_STONE) {
                return y;
            }
        }
        return 0;
    }
}
