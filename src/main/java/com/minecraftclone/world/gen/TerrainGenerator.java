package com.minecraftclone.world.gen;

import com.minecraftclone.util.Noise;
import com.minecraftclone.world.BlockType;
import com.minecraftclone.world.Chunk;

import java.util.ArrayDeque;
import java.util.Random;

/**
 * Procedural terrain generator. A low-frequency "continental" noise carves large
 * ocean basins and landmasses; layered height noise adds rolling hills and
 * mountains. A river noise channel carves wide, shallow water channels whose banks
 * are pulled down to water level, so rivers sit level with the land instead of as
 * deep trenches. Climate is arranged in latitude bands (cold toward -Z, hot toward
 * +Z) with a temperature + moisture noise pair picking the per-column {@link Biome}
 * (ocean / beach / plains / forest / desert / savanna / taiga / snowy / tundra /
 * mountain), which drives the surface and subsurface blocks, tree type and density,
 * ground cover, and ocean-floor material. Water only forms where below-sea terrain
 * connects to a neighbour, so stray 1-2 block puddles never appear, and beaches are
 * decided by adjacency to water rather than height alone. 3D noise carves caves and
 * veins ore into remaining stone (with lava pooling in the deepest cave pockets).
 * Everything is deterministic from the world seed.
 */
public class TerrainGenerator {

    public static final int SEA_LEVEL = 42;
    private static final int BASE_HEIGHT = 44;
    private static final int SNOW_LINE = 60;
    private static final int MOUNTAIN_LINE = SEA_LEVEL + 16; // above this: mountain biome
    private static final int LAVA_LEVEL = 10;                // cave pockets at/below this fill with lava instead of air
    private static final int RIVER_ZONE = 6;                 // rivers only form in lowland within this of sea level, so they never become ravines

    /** The biomes a column can be assigned, from its temperature, moisture and height. */
    public enum Biome {
        OCEAN, FROZEN_OCEAN, BEACH, PLAINS, FOREST, DESERT, SAVANNA, BADLANDS, JUNGLE,
        TAIGA, SNOWY, TUNDRA, SWAMP, MUSHROOM_FIELD, MOUNTAIN
    }

    private final Noise heightNoise;
    private final Noise moistureNoise;
    private final Noise tempNoise;
    private final Noise caveNoise;
    private final Noise riverNoise;
    private final Noise oreNoise;
    private final long seed;

    public TerrainGenerator(long seed) {
        this.seed = seed;
        this.heightNoise = new Noise(seed);
        this.moistureNoise = new Noise(seed ^ 0x9E3779B97F4A7C15L);
        this.tempNoise = new Noise(seed ^ 0xC2B2AE3D27D4EB4FL);
        this.caveNoise = new Noise(seed ^ 0xD1B54A32D192ED03L);
        this.riverNoise = new Noise(seed ^ 0x27D4EB2F165667C5L);
        this.oreNoise = new Noise(seed ^ 0x6A09E667F3BCC909L);
    }

    /**
     * Picks the biome for a column from its temperature / moisture noise values and
     * its (pre-clamp) surface height. Height dominates first (ocean/beach/mountain),
     * then temperature splits cold / temperate / hot, with a {@link #TUNDRA} band
     * between frozen and temperate so snow never butts straight up against lush
     * plains, and moisture picks forest vs plains / desert vs savanna.
     */
    public static Biome biomeAt(double temperature, double moisture, int height) {
        if (height < SEA_LEVEL) return temperature < -0.25 ? Biome.FROZEN_OCEAN : Biome.OCEAN;
        if (height <= SEA_LEVEL + 1) return Biome.BEACH;
        if (height > MOUNTAIN_LINE) return Biome.MOUNTAIN;
        if (temperature < -0.28) return moisture > 0.05 ? Biome.TAIGA : Biome.SNOWY;
        if (temperature < -0.04) return Biome.TUNDRA;
        if (moisture > 0.42 && height <= SEA_LEVEL + 6) return Biome.SWAMP;
        if (temperature > 0.18) {
            if (moisture > 0.18) return Biome.JUNGLE;
            if (moisture < -0.12) return Biome.DESERT;
            if (moisture < 0.00) return Biome.BADLANDS;
            return Biome.SAVANNA;
        }
        return moisture > 0.10 ? Biome.FOREST : Biome.PLAINS;
    }

    /**
     * The climate biome for a column, plus the rare mushroom-field gate (extra-wet
     * temperate patches seeded by a separate low-frequency noise).
     */
    private Biome biomeForClimate(double temperature, double moisture, int height, int wx, int wz) {
        Biome b = biomeAt(temperature, moisture, height);
        if ((b == Biome.FOREST || b == Biome.PLAINS)
                && moisture > 0.30
                && heightNoise.fbm2(wx * 0.008 + 77, wz * 0.008 + 77, 2, 0.5, 2.0) > 0.12) {
            return Biome.MUSHROOM_FIELD;
        }
        return b;
    }

    private static boolean sandyBiome(Biome b) {
        return b == Biome.BEACH || b == Biome.DESERT || b == Biome.OCEAN || b == Biome.FROZEN_OCEAN;
    }

    /** True if any of a column's in-chunk orthogonal neighbors sits below sea level. */
    private static boolean neighborBelowSea(int[][] heights, int x, int z) {
        return (x > 0 && heights[x - 1][z] < SEA_LEVEL)
                || (x < heights.length - 1 && heights[x + 1][z] < SEA_LEVEL)
                || (z > 0 && heights[x][z - 1] < SEA_LEVEL)
                || (z < heights.length - 1 && heights[x][z + 1] < SEA_LEVEL);
    }

    /**
     * The biome used for filling a column: oceans by height (frozen in the cold),
     * beaches only where the land actually touches water, and inland lowlands near
     * the sea fall through to the climate biomes instead of becoming sand.
     */
    private Biome biomeForColumn(int[][] heights, int x, int z, double temperature, double moisture,
                                 int height, int wx, int wz) {
        if (height < SEA_LEVEL) return biomeAt(temperature, moisture, height);
        if (height <= SEA_LEVEL + 1 && neighborBelowSea(heights, x, z)) return Biome.BEACH;
        Biome b = biomeForClimate(temperature, moisture, height, wx, wz);
        return b == Biome.BEACH ? Biome.PLAINS : b;
    }

    /**
     * The finalized terrain height for a world column: continental + hill noise,
     * the shallow river carve, clamping, and ocean deepening (rivers stay shallow).
     * Matches what {@link #generate} fills, so biome queries agree with the world.
     */
    public int terrainHeight(int wx, int wz) {
        double h = heightNoise.fbm2(wx * 0.01, wz * 0.01, 5, 0.5, 2.0);
        // Real mountains: a low-frequency range, sharpened into tall peaks by the
        // power so only the core of a range reaches high altitude.
        double mountains = heightNoise.fbm2(wx * 0.004, wz * 0.004, 2, 0.5, 2.0);
        double continent = heightNoise.fbm2(wx * 0.0035, wz * 0.0035, 2, 0.5, 2.0);
        int height = BASE_HEIGHT + (int) Math.round(
                continent * 16 + h * 18 + Math.pow(Math.max(0, mountains - 0.02), 1.6) * 420);
        // Rivers only cut through lowland near sea level, so their water sits level
        // with the land instead of carving deep ravines through high terrain.
        boolean river = isRiver(wx, wz) && height <= SEA_LEVEL + RIVER_ZONE;
        if (river) {
            height = Math.min(height, SEA_LEVEL - 1); // shallow riverbed
        }
        height = Math.max(2, Math.min(Chunk.HEIGHT - 10, height));
        if (height < SEA_LEVEL && !river) {
            double depth = heightNoise.fbm2(wx * 0.008 + 91, wz * 0.008 + 91, 3, 0.5, 2.0);
            height = Math.max(2, height - (int) (depth * 5));
        }
        return height;
    }

    /**
     * The biome at an arbitrary world column, using the same rules the chunk
     * generator applies (isolated dips stay dry, neighbor-aware beaches included) -
     * for the F3 debug overlay and any other world-coordinate biome queries.
     */
    public Biome biomeAtWorld(int wx, int wz) {
        double temperature = temperatureAt(wx, wz);
        double moisture = moistureAt(wx, wz);
        int height = terrainHeight(wx, wz);
        if (height < SEA_LEVEL && !neighborBelowSeaWorld(wx, wz)) {
            height = SEA_LEVEL; // an isolated dip isn't a puddle
        }
        if (height < SEA_LEVEL) return biomeAt(temperature, moisture, height);
        if (height <= SEA_LEVEL + 1 && neighborBelowSeaWorld(wx, wz)) return Biome.BEACH;
        Biome b = biomeForClimate(temperature, moisture, height, wx, wz);
        return b == Biome.BEACH ? Biome.PLAINS : b;
    }

    /** True if any orthogonal neighbor of a world column sits below sea level. */
    private boolean neighborBelowSeaWorld(int wx, int wz) {
        return terrainHeight(wx - 1, wz) < SEA_LEVEL
                || terrainHeight(wx + 1, wz) < SEA_LEVEL
                || terrainHeight(wx, wz - 1) < SEA_LEVEL
                || terrainHeight(wx, wz + 1) < SEA_LEVEL;
    }

    public void generate(Chunk chunk) {
        int originX = chunk.getOriginX();
        int originZ = chunk.getOriginZ();
        Random featureRandom = new Random(seed ^ ((long) chunk.getPos().x() * 341873128712L) ^ ((long) chunk.getPos().z() * 132897987541L));

        int[][] heights = new int[Chunk.SIZE][Chunk.SIZE];
        boolean[][] rivers = new boolean[Chunk.SIZE][Chunk.SIZE];
        double[][] temperature = new double[Chunk.SIZE][Chunk.SIZE];
        double[][] moisture = new double[Chunk.SIZE][Chunk.SIZE];
        Biome[][] biomes = new Biome[Chunk.SIZE][Chunk.SIZE];

        // Pass 1: surface height + climate per column (needed up-front so beaches can
        // look at their neighbors before any blocks are placed).
        for (int x = 0; x < Chunk.SIZE; x++) {
            for (int z = 0; z < Chunk.SIZE; z++) {
                int wx = originX + x;
                int wz = originZ + z;

                double h = heightNoise.fbm2(wx * 0.01, wz * 0.01, 5, 0.5, 2.0);
                // Low-frequency mountain ranges, sharpened into tall peaks by a power.
                double mountains = heightNoise.fbm2(wx * 0.004, wz * 0.004, 2, 0.5, 2.0);
                // Low-frequency "continental" term: large ocean basins and landmasses.
                double continent = heightNoise.fbm2(wx * 0.0035, wz * 0.0035, 2, 0.5, 2.0);
                temperature[x][z] = temperatureAt(wx, wz);
                moisture[x][z] = moistureAt(wx, wz);

                int height = BASE_HEIGHT + (int) Math.round(
                        continent * 16 + h * 18 + Math.pow(Math.max(0, mountains - 0.02), 1.6) * 420);
                // Rivers only cut through lowland near sea level (see RIVER_ZONE).
                boolean river = isRiver(wx, wz) && height <= SEA_LEVEL + RIVER_ZONE;
                rivers[x][z] = river;
                if (river) {
                    height = Math.min(height, SEA_LEVEL - 1); // shallow riverbed
                }
                height = Math.max(2, Math.min(Chunk.HEIGHT - 10, height));

                // Deepen oceans with a slow depth noise so open water is more than a
                // puddle; rivers stay shallow so their water sits level with the land.
                if (height < SEA_LEVEL && !river) {
                    double depth = heightNoise.fbm2(wx * 0.008 + 91, wz * 0.008 + 91, 3, 0.5, 2.0);
                    height = Math.max(2, height - (int) (depth * 5));
                }
                heights[x][z] = height;
            }
        }

        // River valleys: pull land within a few blocks of a river down in gentle
        // rings toward water level, so a river sits in a shallow valley rather than
        // between steep ravine walls.
        int[][] riverDist = new int[Chunk.SIZE][Chunk.SIZE];
        ArrayDeque<int[]> queue = new ArrayDeque<>();
        for (int x = 0; x < Chunk.SIZE; x++) {
            for (int z = 0; z < Chunk.SIZE; z++) {
                riverDist[x][z] = rivers[x][z] ? 0 : Integer.MAX_VALUE;
                if (rivers[x][z]) queue.add(new int[]{x, z});
            }
        }
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!queue.isEmpty()) {
            int[] p = queue.poll();
            for (int[] d : dirs) {
                int nx = p[0] + d[0], nz = p[1] + d[1];
                if (nx >= 0 && nx < Chunk.SIZE && nz >= 0 && nz < Chunk.SIZE
                        && riverDist[nx][nz] > riverDist[p[0]][p[1]] + 1) {
                    riverDist[nx][nz] = riverDist[p[0]][p[1]] + 1;
                    queue.add(new int[]{nx, nz});
                }
            }
        }
        for (int x = 0; x < Chunk.SIZE; x++) {
            for (int z = 0; z < Chunk.SIZE; z++) {
                int d = riverDist[x][z];
                if (d >= 1 && d <= 4) {
                    int target = SEA_LEVEL + d + 1; // 1 away: +2 above water, up to 5 away: +5
                    if (heights[x][z] > target) {
                        heights[x][z] = target;
                    }
                }
            }
        }

        // Pass 2: place blocks, using the biome (with neighbor-aware beaches).
        for (int x = 0; x < Chunk.SIZE; x++) {
            for (int z = 0; z < Chunk.SIZE; z++) {
                int height = heights[x][z];
                // A tiny isolated dip below sea would otherwise become a stray 1-2
                // block puddle; flatten it to sea level so water only forms real
                // bodies that connect to a neighbor.
                if (height < SEA_LEVEL && !neighborBelowSea(heights, x, z)) {
                    height = SEA_LEVEL;
                    heights[x][z] = height;
                }
                Biome biome = biomeForColumn(heights, x, z, temperature[x][z], moisture[x][z],
                        height, originX + x, originZ + z);
                biomes[x][z] = biome;

                boolean sandy = sandyBiome(biome);
                BlockType surface = surfaceFor(biome, height);
                BlockType subsurface = subsurfaceFor(biome, height);

                for (int y = 0; y <= height; y++) {
                    BlockType type;
                    if (y == 0) {
                        type = BlockType.BEDROCK;
                    } else if (y < height - 4) {
                        if (caveAt(originX + x, y, originZ + z)) {
                            type = (y <= LAVA_LEVEL) ? BlockType.LAVA : BlockType.AIR;
                        } else {
                            type = oreAt(originX + x, y, originZ + z);
                        }
                    } else if (y < height) {
                        type = subsurface;
                        // Let caves poke through the topsoil (but not sand, which would float).
                        if (!sandy && caveAt(originX + x, y, originZ + z) && y < height - 1) {
                            type = BlockType.AIR;
                        }
                    } else {
                        type = surface;
                    }
                    if (type != BlockType.AIR) {
                        chunk.setLocal(x, y, z, type);
                    }
                }

                // Fill water up to sea level for low terrain (oceans, rivers, lakes).
                // Frozen oceans cap the surface with ice so you can walk on them.
                if (height < SEA_LEVEL) {
                    boolean frozen = biome == Biome.FROZEN_OCEAN;
                    for (int y = height + 1; y <= SEA_LEVEL; y++) {
                        chunk.setLocal(x, y, z, frozen && y == SEA_LEVEL ? BlockType.ICE : BlockType.WATER);
                    }
                }
            }
        }

        // Surface dressing: trees / cacti / boulders / ground cover per biome. Kept
        // fully inside the chunk (margin 2) so tree canopies never spill into a
        // not-yet-generated neighboring chunk.
        for (int x = 2; x < Chunk.SIZE - 2; x++) {
            for (int z = 2; z < Chunk.SIZE - 2; z++) {
                int height = heights[x][z];
                if (height < SEA_LEVEL) continue; // ocean floor gets no dressing

                Biome biome = biomes[x][z];
                BlockType surface = chunk.getLocal(x, height, z);

                switch (biome) {
                    case DESERT -> {
                        if (surface == BlockType.SAND && featureRandom.nextInt(180) == 0) {
                            placeCactus(chunk, x, height + 1, z, featureRandom);
                        }
                    }
                    case TAIGA -> {
                        if ((surface == BlockType.SNOW || surface == BlockType.GRASS) && featureRandom.nextInt(28) == 0) {
                            placePineTree(chunk, x, height + 1, z, featureRandom);
                        }
                    }
                    case MOUNTAIN -> {
                        if (surface == BlockType.STONE && featureRandom.nextInt(45) == 0) {
                            placePineTree(chunk, x, height + 1, z, featureRandom);
                        }
                    }
                    case FOREST -> {
                        if (surface == BlockType.GRASS) {
                            if (featureRandom.nextInt(18) == 0) {
                                placeTree(chunk, x, height + 1, z, featureRandom);
                            } else {
                                placeGroundCover(chunk, x, height + 1, z, featureRandom, true);
                            }
                        }
                    }
                    case SAVANNA -> {
                        if (surface == BlockType.GRASS && featureRandom.nextInt(70) == 0) {
                            placeTree(chunk, x, height + 1, z, featureRandom);
                        }
                    }
                    case SWAMP -> {
                        if (surface == BlockType.SWAMP_GRASS && featureRandom.nextInt(40) == 0) {
                            placeDeadTree(chunk, x, height + 1, z, featureRandom);
                        }
                    }
                    case JUNGLE -> {
                        if (surface == BlockType.GRASS && featureRandom.nextInt(14) == 0) {
                            placeJungleTree(chunk, x, height + 1, z, featureRandom);
                        }
                    }
                    case BADLANDS -> {
                        if (surface == BlockType.RED_CLAY) {
                            if (featureRandom.nextInt(70) == 0) {
                                placeDeadBush(chunk, x, height + 1, z);
                            } else if (featureRandom.nextInt(120) == 0) {
                                placeCactus(chunk, x, height + 1, z, featureRandom);
                            }
                        }
                    }
                    case MUSHROOM_FIELD -> {
                        if (surface == BlockType.MYCELIUM && featureRandom.nextInt(30) == 0) {
                            placeMushroom(chunk, x, height + 1, z, featureRandom);
                        }
                    }
                    case PLAINS -> {
                        if (surface == BlockType.GRASS) {
                            if (featureRandom.nextInt(85) == 0) {
                                placeTree(chunk, x, height + 1, z, featureRandom);
                            } else if (featureRandom.nextInt(140) == 0) {
                                placeBoulder(chunk, x, height + 1, z, featureRandom);
                            } else {
                                placeGroundCover(chunk, x, height + 1, z, featureRandom, false);
                            }
                        }
                    }
                    default -> { /* beach / snowy: nothing grows */ }
                }
            }
        }

        chunk.markGenerated();
        chunk.markDirty();
    }

    private double moistureAt(int wx, int wz) {
        return moistureNoise.fbm2(wx * 0.008 + 500, wz * 0.008 + 500, 3, 0.5, 2.0) * 1.6;
    }

    private double temperatureAt(int wx, int wz) {
        // Climate runs in latitude bands: colder toward -Z (north), hotter toward
        // +Z (south), with a little noise for local variety. So snow forms a
        // northern region rather than random patches plopped next to plains/oceans.
        double latitude = wz * 0.0012;
        double noise = tempNoise.fbm2(wx * 0.006 + 900, wz * 0.006 + 900, 3, 0.5, 2.0) * 0.55;
        return latitude + noise;
    }

    /** The top surface block for a biome (oceans get their sea-floor material). */
    private static BlockType surfaceFor(Biome b, int height) {
        return switch (b) {
            case BEACH, DESERT -> BlockType.SAND;
            case SNOWY, TAIGA -> BlockType.SNOW;
            case MOUNTAIN -> height > SNOW_LINE ? BlockType.SNOW : BlockType.STONE;
            case SWAMP -> BlockType.SWAMP_GRASS;
            case BADLANDS -> BlockType.RED_CLAY;
            case MUSHROOM_FIELD -> BlockType.MYCELIUM;
            case OCEAN, FROZEN_OCEAN -> height < SEA_LEVEL - 5 ? BlockType.GRAVEL : BlockType.SAND;
            default -> BlockType.GRASS;
        };
    }

    /** The block just under the surface. */
    private static BlockType subsurfaceFor(Biome b, int height) {
        return switch (b) {
            case BEACH, DESERT, OCEAN, FROZEN_OCEAN -> BlockType.SAND;
            case BADLANDS -> BlockType.RED_CLAY;
            case MOUNTAIN -> BlockType.STONE;
            case SNOWY, TAIGA -> height > SNOW_LINE ? BlockType.STONE : BlockType.DIRT;
            default -> BlockType.DIRT;
        };
    }

    /** Winding rivers: the near-zero contour of a low-frequency noise field, thickened into a wide shallow channel. */
    private boolean isRiver(int wx, int wz) {
        double r = riverNoise.fbm2(wx * 0.004, wz * 0.004, 2, 0.5, 2.0);
        return Math.abs(r) < 0.03;
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

    /** A small grey boulder (a couple of stone blocks) on the surface - rare plains decoration. */
    private void placeBoulder(Chunk chunk, int x, int y, int z, Random rnd) {
        chunk.setLocal(x, y, z, BlockType.STONE);
        if (rnd.nextBoolean()) {
            chunk.setLocal(x + (rnd.nextBoolean() ? 1 : -1), y, z, BlockType.STONE);
        }
    }

    /** A bare dead trunk sticking out of the swamp - just a couple of wood blocks, no canopy. */
    private void placeDeadTree(Chunk chunk, int x, int y, int z, Random rnd) {
        int h = 2 + rnd.nextInt(2);
        for (int i = 0; i < h; i++) {
            chunk.setLocal(x, y + i, z, BlockType.WOOD_LOG);
        }
    }

    /** A tall jungle tree: a longer trunk and a bigger, lusher canopy than the oak. */
    private void placeJungleTree(Chunk chunk, int x, int y, int z, Random rnd) {
        int trunkHeight = 7 + rnd.nextInt(3);
        for (int i = 0; i < trunkHeight; i++) {
            chunk.setLocal(x, y + i, z, BlockType.WOOD_LOG);
        }
        int canopyBase = y + trunkHeight - 3;
        for (int cy = 0; cy <= 3; cy++) {
            int radius = (cy == 3) ? 1 : 3;
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (dx == 0 && dz == 0 && cy < 3) continue;
                    if (Math.abs(dx) == radius && Math.abs(dz) == radius && radius == 3) continue;
                    BlockType existing = chunk.getLocal(x + dx, canopyBase + cy, z + dz);
                    if (existing == BlockType.AIR) {
                        chunk.setLocal(x + dx, canopyBase + cy, z + dz, BlockType.LEAVES);
                    }
                }
            }
        }
        chunk.setLocal(x, y + trunkHeight, z, BlockType.LEAVES);

        // Vines hanging from the canopy fringe down into the air below.
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                if (dx == 0 && dz == 0) continue;
                if (Math.abs(dx) != 3 && Math.abs(dz) != 3) continue; // fringe only
                if (rnd.nextInt(3) != 0) continue;
                int vx = x + dx, vz = z + dz;
                int len = 1 + rnd.nextInt(3);
                for (int i = 1; i <= len; i++) {
                    int vy = canopyBase - i;
                    if (chunk.getLocal(vx, vy, vz) == BlockType.AIR) {
                        chunk.setLocal(vx, vy, vz, BlockType.VINE);
                    } else {
                        break;
                    }
                }
            }
        }
    }

    /** A dried bush on the badlands surface. */
    private void placeDeadBush(Chunk chunk, int x, int y, int z) {
        chunk.setLocal(x, y, z, BlockType.DEAD_BUSH);
    }

    /** A red or brown mushroom on the mycelium floor. */
    private void placeMushroom(Chunk chunk, int x, int y, int z, Random rnd) {
        chunk.setLocal(x, y, z, rnd.nextBoolean() ? BlockType.MUSHROOM_RED : BlockType.MUSHROOM_BROWN);
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

    /** A narrower, conical evergreen: shrinking leaf rings stacked up a taller trunk. Used in taiga and mountains. */
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
