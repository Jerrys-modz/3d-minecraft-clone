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
public class TerrainGenerator implements WorldGenerator {

    /** The default sea/water level in blocks; configurable via {@link WorldGenSettings}. */
    public static final int DEFAULT_SEA_LEVEL = 42;
    private static final int BASE_HEIGHT = 44;
    private static final int SNOW_LINE = 60;
    private static final int MOUNTAIN_OFFSET = 16; // sea level + this: mountain biome
    private static final int LAVA_LEVEL = 10;      // cave pockets at/below this fill with lava instead of air
    private static final int RIVER_ZONE = 6;       // rivers only form in lowland within this of sea level, so they never become ravines

    private static final int VILLAGE_GRID = 32;              // one candidate origin per 32×32 chunk region
    private static final int VILLAGE_SEP = 8;                // stay this many chunks from the next cell
    private static final int VILLAGE_RADIUS = 2;             // chunks away a village origin is still detected

    /** The biomes a column can be assigned, from its temperature, moisture and height. */
    public enum Biome {
        OCEAN, FROZEN_OCEAN, BEACH, PLAINS, FOREST, DESERT, SAVANNA, BADLANDS, JUNGLE,
        TAIGA, SNOWY, TUNDRA, SWAMP, MUSHROOM_FIELD, CHERRY_GROVE, FLOWER_MEADOW, MOUNTAIN,
        /** Stand-in biome reported for non-overworld dimensions (see NetherGenerator/EndGenerator). */
        NETHER, END
    }

    /** A small generated structure that can appear in a biome, placed at a flat surface cell. */
    public enum StructureType {
        NONE, DESERT_TEMPLE, IGLOO, CABIN, WITCH_HUT, STONE_RUIN
    }

    /** A building type used inside villages, chosen per plot. */
    enum BuildingType {
        HOUSE, TALL_HOUSE, BLACKSMITH, TOWER
    }

    /** Picks a village building type deterministically from the village position and plot index. */
    static BuildingType buildingTypeFor(int centerX, int centerZ, int plot) {
        int h = (int) ((centerX * 31L + centerZ * 17L + plot * 13L) & 0xFFFF);
        return switch (h % 5) {
            case 2 -> BuildingType.TALL_HOUSE;
            case 3 -> BuildingType.BLACKSMITH;
            case 4 -> BuildingType.TOWER;
            default -> BuildingType.HOUSE;
        };
    }

    /** The structure a biome tends to spawn, or {@link StructureType#NONE} if it spawns nothing. */
    public static StructureType structureFor(Biome b) {
        return switch (b) {
            case DESERT -> StructureType.DESERT_TEMPLE;
            case SNOWY, TUNDRA -> StructureType.IGLOO;
            case FOREST, PLAINS, TAIGA, CHERRY_GROVE, FLOWER_MEADOW -> StructureType.CABIN;
            case SWAMP -> StructureType.WITCH_HUT;
            case MOUNTAIN, BADLANDS -> StructureType.STONE_RUIN;
            default -> StructureType.NONE;
        };
    }

    /**
     * True if this chunk is the origin of a village. One candidate per
     * {@link #VILLAGE_GRID}×{@link #VILLAGE_GRID} region with a random offset,
     * so villages sit hundreds of blocks apart instead of every few chunks.
     */
    public static boolean isVillageChunk(long seed, int cx, int cz) {
        int gx = Math.floorDiv(cx, VILLAGE_GRID);
        int gz = Math.floorDiv(cz, VILLAGE_GRID);
        long h = seed ^ (gx * 0x9E3779B97F4A7C15L) ^ (gz * 0xBF58476D1CE4E5B9L);
        h ^= h >>> 33;
        h *= 0xFF51AFD7ED558CCDL;
        h ^= h >>> 33;
        int span = Math.max(1, VILLAGE_GRID - VILLAGE_SEP);
        int ox = gx * VILLAGE_GRID + (int) Math.floorMod(h, span);
        int oz = gz * VILLAGE_GRID + (int) Math.floorMod(h >>> 16, span);
        return cx == ox && cz == oz;
    }

    /** Biomes villages can appear in (flat, buildable lowlands). */
    public static boolean villageBiome(Biome b) {
        return b == Biome.PLAINS || b == Biome.DESERT || b == Biome.SAVANNA
                || b == Biome.TAIGA || b == Biome.TUNDRA || b == Biome.FLOWER_MEADOW
                || b == Biome.CHERRY_GROVE;
    }

    private final Noise heightNoise;
    private final Noise moistureNoise;
    private final Noise tempNoise;
    private final Noise caveNoise;
    private final Noise riverNoise;
    private final Noise oreNoise;
    private final GthnOreGenerator gthnOreGenerator;
    private final long seed;
    private final int seaLevel;
    private final boolean structures;
    private final boolean superflat;
    private final float terrainSize;
    /** Superflat world: the fixed height of the grass surface (a flat plain just above sea level). */
    private final int flatHeight;

    public TerrainGenerator(long seed, WorldGenSettings settings) {
        this.seed = seed;
        this.seaLevel = settings.getSeaLevel();
        this.structures = settings.hasStructures();
        this.superflat = settings.isSuperflat();
        this.terrainSize = settings.getTerrainSize();
        this.flatHeight = seaLevel + 4;
        this.heightNoise = new Noise(seed);
        this.moistureNoise = new Noise(seed ^ 0x9E3779B97F4A7C15L);
        this.tempNoise = new Noise(seed ^ 0xC2B2AE3D27D4EB4FL);
        this.caveNoise = new Noise(seed ^ 0xD1B54A32D192ED03L);
        this.riverNoise = new Noise(seed ^ 0x27D4EB2F165667C5L);
        this.oreNoise = new Noise(seed ^ 0x6A09E667F3BCC909L);
        this.gthnOreGenerator = new GthnOreGenerator(seed, seaLevel);
    }

    @Override
    public int seaLevel() {
        return seaLevel;
    }

    public int getSeaLevel() {
        return seaLevel;
    }

    /**
     * Picks the biome for a column from its temperature / moisture noise values and
     * its (pre-clamp) surface height. Height dominates first (ocean/beach/mountain),
     * then temperature splits cold / temperate / hot, with a {@link #TUNDRA} band
     * between frozen and temperate so snow never butts straight up against lush
     * plains, and moisture picks forest vs plains / desert vs savanna.
     */
    public Biome biomeAt(double temperature, double moisture, int height) {
        if (height < seaLevel) return temperature < -0.25 ? Biome.FROZEN_OCEAN : Biome.OCEAN;
        if (height <= seaLevel + 1) return Biome.BEACH;
        if (height > seaLevel + MOUNTAIN_OFFSET) return Biome.MOUNTAIN;
        if (temperature < -0.28) return moisture > 0.05 ? Biome.TAIGA : Biome.SNOWY;
        if (temperature < -0.04) return Biome.TUNDRA;
        if (moisture > 0.42 && height <= seaLevel + 6) return Biome.SWAMP;
        if (temperature > 0.18) {
            if (moisture > 0.18) return Biome.JUNGLE;
            if (moisture < -0.12) return Biome.DESERT;
            if (moisture < 0.00) return Biome.BADLANDS;
            return Biome.SAVANNA;
        }
        return moisture > 0.10 ? Biome.FOREST : Biome.PLAINS;
    }

    /**
     * The climate biome for a column, plus the rare patchy biome gates (mushroom
     * fields, cherry groves and flower meadows) seeded by separate low-frequency
     * noises so they appear as scattered pockets rather than whole regions.
     */
    private Biome biomeForClimate(double temperature, double moisture, int height, int wx, int wz) {
        Biome b = biomeAt(temperature, moisture, height);
        double patch = heightNoise.fbm2(wx * 0.008 + 77, wz * 0.008 + 77, 2, 0.5, 2.0);
        if ((b == Biome.FOREST || b == Biome.PLAINS) && moisture > 0.15 && moisture < 0.35 && patch > 0.12) {
            return Biome.CHERRY_GROVE;
        }
        double patch2 = heightNoise.fbm2(wx * 0.008 + 456, wz * 0.008 + 456, 2, 0.5, 2.0);
        if (b == Biome.PLAINS && moisture < 0.12 && patch2 > 0.10) {
            return Biome.FLOWER_MEADOW;
        }
        if ((b == Biome.FOREST || b == Biome.PLAINS) && moisture > 0.30 && patch > 0.22) {
            return Biome.MUSHROOM_FIELD;
        }
        return b;
    }

    private static boolean sandyBiome(Biome b) {
        return b == Biome.BEACH || b == Biome.DESERT || b == Biome.OCEAN || b == Biome.FROZEN_OCEAN;
    }

    /** True if any of a column's in-chunk orthogonal neighbors sits below sea level. */
    private boolean neighborBelowSea(int[][] heights, int x, int z) {
        return (x > 0 && heights[x - 1][z] < seaLevel)
                || (x < heights.length - 1 && heights[x + 1][z] < seaLevel)
                || (z > 0 && heights[x][z - 1] < seaLevel)
                || (z < heights.length - 1 && heights[x][z + 1] < seaLevel);
    }

    /**
     * The biome used for filling a column: oceans by height (frozen in the cold),
     * beaches only where the land actually touches water, and inland lowlands near
     * the sea fall through to the climate biomes instead of becoming sand.
     */
    private Biome biomeForColumn(int[][] heights, int x, int z, double temperature, double moisture,
                                 int height, int wx, int wz) {
        if (height < seaLevel) return biomeAt(temperature, moisture, height);
        if (height <= seaLevel + 1 && neighborBelowSea(heights, x, z)) return Biome.BEACH;
        Biome b = biomeForClimate(temperature, moisture, height, wx, wz);
        return b == Biome.BEACH ? Biome.PLAINS : b;
    }

    /**
     * The finalized terrain height for a world column: continental + hill noise,
     * the shallow river carve, clamping, and ocean deepening (rivers stay shallow).
     * Matches what {@link #generate} fills, so biome queries agree with the world.
     */
    public int terrainHeight(int wx, int wz) {
        if (superflat) {
            return flatHeight;
        }
        double h = heightNoise.fbm2(wx * 0.01, wz * 0.01, 5, 0.5, 2.0);
        // Real mountains: a low-frequency range, sharpened into tall peaks by the
        // power so only the core of a range reaches high altitude.
        double mountains = heightNoise.fbm2(wx * 0.004, wz * 0.004, 2, 0.5, 2.0);
        double continent = heightNoise.fbm2(wx * 0.0035, wz * 0.0035, 2, 0.5, 2.0);
        int height = BASE_HEIGHT + (int) Math.round(terrainSize * (
                continent * 16 + h * 18 + Math.pow(Math.max(0, mountains - 0.02), 1.6) * 420));
        // Rivers only cut through lowland near sea level, so their water sits level
        // with the land instead of carving deep ravines through high terrain.
        boolean river = isRiver(wx, wz) && height <= seaLevel + RIVER_ZONE;
        if (river) {
            height = Math.min(height, seaLevel - 1); // shallow riverbed
        }
        height = Math.max(2, Math.min(Chunk.HEIGHT - 10, height));
        if (height < seaLevel && !river) {
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
    @Override
    public Biome biomeAtWorld(int wx, int wz) {
        double temperature = temperatureAt(wx, wz);
        double moisture = moistureAt(wx, wz);
        int height = terrainHeight(wx, wz);
        if (height < seaLevel && !neighborBelowSeaWorld(wx, wz)) {
            height = seaLevel; // an isolated dip isn't a puddle
        }
        if (height < seaLevel) return biomeAt(temperature, moisture, height);
        if (height <= seaLevel + 1 && neighborBelowSeaWorld(wx, wz)) return Biome.BEACH;
        Biome b = biomeForClimate(temperature, moisture, height, wx, wz);
        return b == Biome.BEACH ? Biome.PLAINS : b;
    }

    /** True if any orthogonal neighbor of a world column sits below sea level. */
    private boolean neighborBelowSeaWorld(int wx, int wz) {
        return terrainHeight(wx - 1, wz) < seaLevel
                || terrainHeight(wx + 1, wz) < seaLevel
                || terrainHeight(wx, wz - 1) < seaLevel
                || terrainHeight(wx, wz + 1) < seaLevel;
    }

    public void generate(Chunk chunk) {
        if (superflat) {
            generateSuperflat(chunk);
            return;
        }
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

                int height = BASE_HEIGHT + (int) Math.round(terrainSize * (
                        continent * 16 + h * 18 + Math.pow(Math.max(0, mountains - 0.02), 1.6) * 420));
                // Rivers only cut through lowland near sea level (see RIVER_ZONE).
                boolean river = isRiver(wx, wz) && height <= seaLevel + RIVER_ZONE;
                rivers[x][z] = river;
                if (river) {
                    height = Math.min(height, seaLevel - 1); // shallow riverbed
                }
                height = Math.max(2, Math.min(Chunk.HEIGHT - 10, height));

                // Deepen oceans with a slow depth noise so open water is more than a
                // puddle; rivers stay shallow so their water sits level with the land.
                if (height < seaLevel && !river) {
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
                    int target = seaLevel + d + 1; // 1 away: +2 above water, up to 5 away: +5
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
                if (height < seaLevel && !neighborBelowSea(heights, x, z)) {
                    height = seaLevel;
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

                // Clay deposits: replace the top 1-2 blocks of riverbed / shallow lake
                // floor with CLAY, matching vanilla Minecraft's clay generation.
                // Rivers are very shallow (height == seaLevel-1) and some shallow ocean
                // spots qualify too.  Depth range: up to 3 blocks below sea level.
                if (height < seaLevel && height >= seaLevel - 3
                        && (rivers[x][z] || biome == Biome.SWAMP)
                        && featureRandom.nextInt(3) != 0) {
                    chunk.setLocal(x, height, z, BlockType.CLAY);
                    if (height > 0) chunk.setLocal(x, height - 1, z, BlockType.CLAY);
                }

                // Fill water up to sea level for low terrain (oceans, rivers, lakes).
                // Frozen oceans cap the surface with ice; swamps get the odd lily pad.
                if (height < seaLevel) {
                    boolean frozen = biome == Biome.FROZEN_OCEAN;
                    for (int y = height + 1; y <= seaLevel; y++) {
                        BlockType fill = frozen && y == seaLevel ? BlockType.ICE : BlockType.WATER;
                        if (y == seaLevel && biome == Biome.SWAMP && featureRandom.nextInt(6) == 0) {
                            fill = BlockType.LILY_PAD;
                        }
                        chunk.setLocal(x, y, z, fill);
                    }
                    // Seaweed on shallow, warm ocean floors - grows *inside* the water
                    // cell (an overlay, like Minecraft's waterlogged seagrass) rather
                    // than replacing it, so the water is still there around it.
                    if (biome == Biome.OCEAN && height >= seaLevel - 8 && featureRandom.nextInt(4) == 0) {
                        for (int i = 1; i <= 2; i++) {
                            if (chunk.getLocal(x, height + i, z) == BlockType.WATER) {
                                chunk.setOverlay(x, height + i, z, BlockType.SEAWEED);
                            }
                        }
                    }
                }
            }
        }

        // Surface dressing: trees / cacti / boulders / ground cover per biome. Kept
        // fully inside the chunk (margin 2) so tree canopies never spill into a
        // not-yet-generated neighboring chunk. Skipped entirely when structures
        // are disabled (WorldGenSettings).
        if (structures) {
            for (int x = 2; x < Chunk.SIZE - 2; x++) {
            for (int z = 2; z < Chunk.SIZE - 2; z++) {
                int height = heights[x][z];
                if (height < seaLevel) continue; // ocean floor gets no dressing

                Biome biome = biomes[x][z];
                BlockType surface = chunk.getLocal(x, height, z);

                switch (biome) {
                    case DESERT -> {
                        if (surface == BlockType.SAND) {
                            if (featureRandom.nextInt(160) == 0) {
                                placeCactus(chunk, x, height + 1, z, featureRandom);
                            } else if (featureRandom.nextInt(90) == 0) {
                                placeDeadBush(chunk, x, height + 1, z);
                            }
                        }
                    }
                    case TAIGA -> {
                        if (surface == BlockType.SNOW || surface == BlockType.GRASS) {
                            if (featureRandom.nextInt(25) == 0) {
                                placePineTree(chunk, x, height + 1, z, featureRandom);
                            } else if (featureRandom.nextInt(40) == 0) {
                                placeMushroom(chunk, x, height + 1, z, featureRandom);
                            }
                        }
                    }
                    case MOUNTAIN -> {
                        if (surface == BlockType.STONE) {
                            if (featureRandom.nextInt(45) == 0) {
                                placePineTree(chunk, x, height + 1, z, featureRandom);
                            } else if (featureRandom.nextInt(30) == 0) {
                                placeBoulder(chunk, x, height + 1, z, featureRandom);
                            }
                        }
                    }
                    case FOREST -> {
                        if (surface == BlockType.GRASS) {
                            if (featureRandom.nextInt(18) == 0) {
                                // ~30% birch, rest oak — gives forest a mixed look.
                                if (featureRandom.nextInt(3) == 0) {
                                    placeBirchTree(chunk, x, height + 1, z, featureRandom);
                                } else {
                                    placeTree(chunk, x, height + 1, z, featureRandom);
                                }
                            } else if (featureRandom.nextInt(60) == 0) {
                                placeFallenLog(chunk, x, height + 1, z, featureRandom);
                            } else if (featureRandom.nextInt(80) == 0) {
                                placeMushroom(chunk, x, height + 1, z, featureRandom);
                            } else {
                                placeGroundCover(chunk, x, height + 1, z, featureRandom, true);
                            }
                        }
                    }
                    case SAVANNA -> {
                        if (surface == BlockType.GRASS) {
                            if (featureRandom.nextInt(70) == 0) {
                                placeTree(chunk, x, height + 1, z, featureRandom);
                            } else if (featureRandom.nextInt(90) == 0) {
                                placeDeadBush(chunk, x, height + 1, z);
                            } else if (featureRandom.nextInt(40) == 0) {
                                chunk.setLocal(x, height + 1, z, BlockType.TALL_GRASS);
                            }
                        }
                    }
                    case SWAMP -> {
                        if (surface == BlockType.SWAMP_GRASS) {
                            if (featureRandom.nextInt(30) == 0) {
                                placeDeadTree(chunk, x, height + 1, z, featureRandom);
                            } else if (featureRandom.nextInt(50) == 0) {
                                placeMushroom(chunk, x, height + 1, z, featureRandom);
                            }
                        }
                    }
                    case JUNGLE -> {
                        if (surface == BlockType.GRASS) {
                            if (featureRandom.nextInt(14) == 0) {
                                placeJungleTree(chunk, x, height + 1, z, featureRandom);
                            } else if (featureRandom.nextInt(30) == 0) {
                                placeBamboo(chunk, x, height + 1, z, featureRandom);
                            } else if (featureRandom.nextInt(60) == 0) {
                                placeFallenLog(chunk, x, height + 1, z, featureRandom);
                            }
                        }
                    }
                    case CHERRY_GROVE -> {
                        if (surface == BlockType.GRASS && featureRandom.nextInt(20) == 0) {
                            placeCherryTree(chunk, x, height + 1, z, featureRandom);
                        }
                    }
                    case FLOWER_MEADOW -> {
                        if (surface == BlockType.GRASS) {
                            if (featureRandom.nextInt(160) == 0) {
                                placeTree(chunk, x, height + 1, z, featureRandom);
                            } else {
                                placeFlowerCover(chunk, x, height + 1, z, featureRandom);
                            }
                        }
                    }
                    case SNOWY -> {
                        if (surface == BlockType.SNOW && featureRandom.nextInt(60) == 0) {
                            placeIceSpike(chunk, x, height + 1, z, featureRandom);
                        }
                    }
                    case BADLANDS -> {
                        if (surface == BlockType.RED_CLAY) {
                            if (featureRandom.nextInt(60) == 0) {
                                placeDeadBush(chunk, x, height + 1, z);
                            } else if (featureRandom.nextInt(110) == 0) {
                                placeCactus(chunk, x, height + 1, z, featureRandom);
                            }
                        }
                    }
                    case MUSHROOM_FIELD -> {
                        if (surface == BlockType.MYCELIUM && featureRandom.nextInt(30) == 0) {
                            placeMushroom(chunk, x, height + 1, z, featureRandom);
                        }
                    }
                    case TUNDRA -> {
                        if (surface == BlockType.GRASS) {
                            if (featureRandom.nextInt(90) == 0) {
                                placeDeadBush(chunk, x, height + 1, z);
                            } else if (featureRandom.nextInt(120) == 0) {
                                placeBoulder(chunk, x, height + 1, z, featureRandom);
                            }
                        }
                    }
                    case PLAINS -> {
                        if (surface == BlockType.GRASS) {
                            if (featureRandom.nextInt(85) == 0) {
                                placeTree(chunk, x, height + 1, z, featureRandom);
                            } else if (featureRandom.nextInt(130) == 0) {
                                placeBoulder(chunk, x, height + 1, z, featureRandom);
                            } else if (featureRandom.nextInt(160) == 0) {
                                placePumpkin(chunk, x, height + 1, z);
                            } else if (featureRandom.nextInt(120) == 0) {
                                placeMushroom(chunk, x, height + 1, z, featureRandom);
                            } else {
                                placeGrassCover(chunk, x, height + 1, z, featureRandom);
                            }
                        }
                    }
                    default -> { /* beach / ocean / frozen ocean: nothing grows */ }
                }
            }
        }
        }

        // Structures: rare, biome-gated, built at a flat surface cell (kept well
        // inside the chunk so a structure never spills into a neighbor).
        if (featureRandom.nextInt(12) == 0) {
            placeStructure(chunk, featureRandom, heights, biomes);
        }

        // Villages: deterministic multi-chunk clusters - each chunk builds the
        // portion of every nearby village that falls inside it.
        if (structures) {
            placeVillages(chunk, originX, originZ);
        }

        chunk.markGenerated();
        chunk.markDirty();
    }

    /**
     * Superflat world: a featureless grass plain. Bedrock at the floor, a few
     * stone/dirt layers, a grass surface just above sea level, and no water,
     * caves, ores, trees or decoration.
     */
    private void generateSuperflat(Chunk chunk) {
        int originX = chunk.getOriginX();
        int originZ = chunk.getOriginZ();
        Random featureRandom = new Random(seed ^ ((long) chunk.getPos().x() * 341873128712L) ^ ((long) chunk.getPos().z() * 132897987541L));
        for (int x = 0; x < Chunk.SIZE; x++) {
            for (int z = 0; z < Chunk.SIZE; z++) {
                for (int y = 0; y <= flatHeight; y++) {
                    BlockType type;
                    if (y == 0) {
                        type = BlockType.BEDROCK;
                    } else if (y < flatHeight - 3) {
                        type = BlockType.STONE;
                    } else if (y < flatHeight) {
                        type = BlockType.DIRT;
                    } else {
                        type = BlockType.GRASS;
                    }
                    chunk.setLocal(x, y, z, type);
                }
            }
        }
        // The occasional tall grass tuft so the plain isn't totally sterile.
        if (structures) {
            for (int i = 0; i < 8; i++) {
                int x = featureRandom.nextInt(Chunk.SIZE - 4) + 2;
                int z = featureRandom.nextInt(Chunk.SIZE - 4) + 2;
                chunk.setLocal(x, flatHeight + 1, z, BlockType.TALL_GRASS);
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
    private BlockType surfaceFor(Biome b, int height) {
        return switch (b) {
            case BEACH, DESERT -> BlockType.SAND;
            case SNOWY, TAIGA -> BlockType.SNOW;
            case MOUNTAIN -> height > SNOW_LINE ? BlockType.SNOW : BlockType.STONE;
            case SWAMP -> BlockType.SWAMP_GRASS;
            case BADLANDS -> BlockType.RED_CLAY;
            case MUSHROOM_FIELD -> BlockType.MYCELIUM;
            case OCEAN, FROZEN_OCEAN -> height < seaLevel - 5 ? BlockType.GRAVEL : BlockType.SAND;
            default -> BlockType.GRASS;
        };
    }

    /** The block just under the surface. */
    private BlockType subsurfaceFor(Biome b, int height) {
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

    // caveAt/oreAt run for every underground block in every generated chunk -
    // by far the hottest path in world-gen (profiling: ~74% of generate()'s
    // time, mostly because oreAt independently samples up to 4 separate 3D
    // noise fields per block). fbm3's cost is roughly linear in octave count,
    // so these use a single octave instead of the usual 3 - still real 3D
    // Perlin noise (not a flat step function), just without the extra fine-
    // detail layers a threshold-gated blob feature barely benefits from at
    // block resolution. A single octave has a wider value distribution than
    // a 3-octave sum, though, so the thresholds below aren't the old ones -
    // they're recalibrated (empirically, by sampling millions of points) to
    // trigger at close to the same rate the original 3-octave thresholds did,
    // so caves/veins stay about as common as before.
    private boolean caveAt(int wx, int y, int wz) {
        if (y < 4 || y > 60) return false;
        double n = caveNoise.fbm3(wx * 0.045, y * 0.07, wz * 0.045, 1, 0.5, 2.0);
        return n > 0.40; // recalibrated for 1 octave - see caveAt/oreAt's shared comment above
    }

    /**
     * Ore generation: GTNH ores take priority over vanilla ores, providing a rich
     * stratified ore system with both small indicator ores and large vein clusters.
     * Falls back to vanilla ores (coal, iron, gold, diamond) and then stone.
     */
    private BlockType oreAt(int wx, int y, int wz) {
        // Try GTNH ore generation first (more diverse ore types)
        BlockType gthnOre = gthnOreGenerator.oreAt(wx, y, wz);
        if (gthnOre != BlockType.STONE) {
            return gthnOre;
        }

        // Vanilla ore generation as fallback
        if (y >= 5 && y <= 16 && oreNoise.fbm3(wx * 0.11 + 1000, y * 0.11, wz * 0.11 + 1000, 1, 0.5, 2.0) > 0.786) {
            return BlockType.DIAMOND_ORE;
        }
        if (y >= 5 && y <= 32 && oreNoise.fbm3(wx * 0.10 + 2000, y * 0.10, wz * 0.10 + 2000, 1, 0.5, 2.0) > 0.692) {
            return BlockType.GOLD_ORE;
        }
        if (y >= 5 && y <= 64 && oreNoise.fbm3(wx * 0.10 + 3000, y * 0.10, wz * 0.10 + 3000, 1, 0.5, 2.0) > 0.594) {
            return BlockType.IRON_ORE;
        }
        if (y >= 5 && y <= 100 && oreNoise.fbm3(wx * 0.09 + 4000, y * 0.09, wz * 0.09 + 4000, 1, 0.5, 2.0) > 0.460) {
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

    /** A thick carpet of tall grass for the plains - a proper grassland, with the odd flower or berry bush. */
    private void placeGrassCover(Chunk chunk, int x, int y, int z, Random rnd) {
        int roll = rnd.nextInt(5);
        if (roll < 3) {
            chunk.setLocal(x, y, z, BlockType.TALL_GRASS);
        } else if (roll == 3) {
            chunk.setLocal(x, y, z, rnd.nextBoolean() ? BlockType.FLOWER_RED : BlockType.FLOWER_YELLOW);
        } else if (rnd.nextInt(40) == 0) {
            chunk.setLocal(x, y, z, BlockType.BERRY_BUSH);
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

    /** A fallen log lying flat on the ground - a couple of wood blocks laid end to end. */
    private void placeFallenLog(Chunk chunk, int x, int y, int z, Random rnd) {
        int len = 2 + rnd.nextInt(2);
        boolean alongX = rnd.nextBoolean();
        for (int i = 0; i < len; i++) {
            int px = alongX ? x + i : x;
            int pz = alongX ? z : z + i;
            if (chunk.getLocal(px, y, pz) == BlockType.AIR) {
                chunk.setLocal(px, y, pz, BlockType.WOOD_LOG);
            }
        }
    }

    /** A pumpkin on the grass - rare plains/forest flavor. */
    private void placePumpkin(Chunk chunk, int x, int y, int z) {
        chunk.setLocal(x, y, z, BlockType.PUMPKIN);
    }

    /** Half-width (in blocks) of a structure's footprint, for flatness checks. */
    private static int footprint(StructureType s) {
        return switch (s) {
            case DESERT_TEMPLE, IGLOO, CABIN -> 3;
            case WITCH_HUT, STONE_RUIN -> 2;
            default -> 0;
        };
    }

    /** True if the ground around (x, z) is flat enough (within 2 blocks) to build on. */
    private static boolean flatArea(int[][] heights, int x, int z, int fp) {
        for (int dx = -fp; dx <= fp; dx++) {
            for (int dz = -fp; dz <= fp; dz++) {
                int nx = x + dx, nz = z + dz;
                if (nx < 0 || nx >= Chunk.SIZE || nz < 0 || nz >= Chunk.SIZE) return false;
                if (Math.abs(heights[nx][nz] - heights[x][z]) > 2) return false;
            }
        }
        return true;
    }

    /** Picks a biome-appropriate structure on a flat spot and builds it. */
    private void placeStructure(Chunk chunk, Random rnd, int[][] heights, Biome[][] biomes) {
        for (int t = 0; t < 10; t++) {
            int x = 4 + rnd.nextInt(Chunk.SIZE - 8);
            int z = 4 + rnd.nextInt(Chunk.SIZE - 8);
            StructureType s = structureFor(biomes[x][z]);
            if (s == StructureType.NONE) continue;
            int height = heights[x][z];
            if (height < seaLevel || height >= Chunk.HEIGHT - 12) continue;
            if (!flatArea(heights, x, z, footprint(s))) continue;
            buildStructure(chunk, s, x, height + 1, z, rnd);
            return;
        }
    }

    private void buildStructure(Chunk chunk, StructureType s, int x, int y, int z, Random rnd) {
        switch (s) {
            case DESERT_TEMPLE -> buildDesertTemple(chunk, x, y, z);
            case IGLOO -> buildIgloo(chunk, x, y, z);
            case CABIN -> buildCabin(chunk, x, y, z, rnd);
            case WITCH_HUT -> buildWitchHut(chunk, x, y, z);
            case STONE_RUIN -> buildStoneRuin(chunk, x, y, z, rnd);
            default -> { }
        }
    }

    /** A hollow stepped sand pyramid with a front entrance and a stone treasure inside. */
    private void buildDesertTemple(Chunk chunk, int x, int y, int z) {
        int[][] layers = {{7, 3}, {5, 2}, {3, 1}, {1, 0}}; // {size, hollow interior half}
        for (int layer = 0; layer < layers.length; layer++) {
            int size = layers[layer][0];
            int half = size / 2;
            int hollow = layers[layer][1];
            for (int dx = -half; dx <= half; dx++) {
                for (int dz = -half; dz <= half; dz++) {
                    if (layer == 0 && dx == half && dz == 0) continue;      // front entrance
                    if (Math.abs(dx) < hollow && Math.abs(dz) < hollow) continue; // hollow core
                    chunk.setLocal(x + dx, y + layer, z + dz, BlockType.SAND);
                }
            }
        }
        chunk.setLocal(x, y + 1, z, BlockType.STONE); // hidden treasure
    }

    /** A small hollow snow dome with a door, for snowy plains and tundra. */
    private void buildIgloo(Chunk chunk, int x, int y, int z) {
        int[][] rings = {{3, 0}, {2, 1}, {1, 2}}; // {radius, height above base}
        for (int[] ring : rings) {
            int rr = ring[0], ly = y + ring[1];
            for (int dx = -rr; dx <= rr; dx++) {
                for (int dz = -rr; dz <= rr; dz++) {
                    if (dx * dx + dz * dz > (rr + 0.4f) * (rr + 0.4f)) continue; // round it
                    if (ring[1] == 0 && dx == rr && dz == 0) continue; // entrance
                    if (Math.abs(dx) < rr && Math.abs(dz) < rr) continue; // hollow inside
                    chunk.setLocal(x + dx, ly, z + dz, BlockType.SNOW);
                }
            }
        }
        chunk.setLocal(x, y + 3, z, BlockType.SNOW); // cap
    }

    /** A small ruined log-and-plank cabin with a flat roof and a doorway. */
    private void buildCabin(Chunk chunk, int x, int y, int z, Random rnd) {
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (Math.abs(dx) != 2 && Math.abs(dz) != 1) continue; // walls only
                for (int h = 0; h < 2; h++) {
                    if (rnd.nextInt(4) == 0) continue; // ruined gap
                    chunk.setLocal(x + dx, y + h, z + dz, BlockType.PLANKS);
                }
            }
        }
        chunk.setLocal(x - 2, y, z, BlockType.AIR);     // doorway
        chunk.setLocal(x - 2, y + 1, z, BlockType.AIR);
        for (int dx = -1; dx <= 1; dx++) {
            chunk.setLocal(x + dx, y + 2, z - 1, BlockType.PLANKS); // roof remnant
        }
        chunk.setLocal(x, y + 2, z, BlockType.PLANKS);
    }

    /** A plank hut on log stilts with a flat roof and a front door, for the swamp. */
    private void buildWitchHut(Chunk chunk, int x, int y, int z) {
        for (int dx = -1; dx <= 1; dx++) {
            chunk.setLocal(x + dx, y - 2, z, BlockType.WOOD_LOG);
            chunk.setLocal(x + dx, y - 1, z, BlockType.WOOD_LOG);
        }
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                chunk.setLocal(x + dx, y, z + dz, BlockType.PLANKS); // floor
            }
        }
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                boolean wall = Math.abs(dx) == 1 || Math.abs(dz) == 1;
                if (wall && !(dx == 0 && dz == 1)) {
                    chunk.setLocal(x + dx, y + 1, z + dz, BlockType.PLANKS);
                }
            }
        }
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                chunk.setLocal(x + dx, y + 2, z + dz, BlockType.PLANKS); // roof
            }
        }
    }

    /** A few cracked stone pillars with a lintel, for mountain and badlands slopes. */
    private void buildStoneRuin(Chunk chunk, int x, int y, int z, Random rnd) {
        int[][] pillars = {{-1, -1}, {1, -1}, {1, 1}, {-1, 1}};
        for (int[] p : pillars) {
            int h = 1 + rnd.nextInt(3);
            for (int i = 0; i < h; i++) {
                chunk.setLocal(x + p[0], y + i, z + p[1], BlockType.STONE);
            }
        }
        chunk.setLocal(x - 1, y + 2, z - 1, BlockType.STONE);
        chunk.setLocal(x, y + 2, z - 1, BlockType.STONE);
        chunk.setLocal(x + 1, y + 2, z - 1, BlockType.STONE);
    }

    /** Writes a block at a world position, silently ignoring anything outside this chunk. */
    private void setWorldBlock(Chunk chunk, int originX, int originZ, int wx, int wy, int wz, BlockType t) {
        int lx = wx - originX, lz = wz - originZ;
        if (lx < 0 || lx >= Chunk.SIZE || lz < 0 || lz >= Chunk.SIZE) return;
        if (wy < 0 || wy >= Chunk.HEIGHT) return;
        chunk.setLocal(lx, wy, lz, t);
    }

    /** Records a block's facing (0:+Z, 1:-Z, 2:+X, 3:-X) at a world position, ignoring anything outside this chunk. */
    private void setWorldOrientation(Chunk chunk, int originX, int originZ, int wx, int wy, int wz, byte orientation) {
        int lx = wx - originX, lz = wz - originZ;
        if (lx < 0 || lx >= Chunk.SIZE || lz < 0 || lz >= Chunk.SIZE) return;
        if (wy < 0 || wy >= Chunk.HEIGHT) return;
        chunk.setOrientation(lx, wy, lz, orientation);
    }

    /**
     * Builds every nearby village's portion of this chunk. Village origins are a
     * deterministic hash of the chunk coords, so all the chunks a village spans
     * agree on the layout and each flatten/build only their own slice.
     */
    private void placeVillages(Chunk chunk, int originX, int originZ) {
        int cx = originX >> 4, cz = originZ >> 4;
        for (int dx = -VILLAGE_RADIUS; dx <= VILLAGE_RADIUS; dx++) {
            for (int dz = -VILLAGE_RADIUS; dz <= VILLAGE_RADIUS; dz++) {
                int vx = cx + dx, vz = cz + dz;
                if (isVillageChunk(seed, vx, vz)) {
                    buildVillage(chunk, originX, originZ, vx * Chunk.SIZE + 8, vz * Chunk.SIZE + 8);
                }
            }
        }
    }

    /** Flattens pads under buildings and paths, then places houses and a well. */
    private void buildVillage(Chunk chunk, int originX, int originZ, int centerX, int centerZ) {
        Biome b = biomeAtWorld(centerX, centerZ);
        if (!villageBiome(b)) return;
        int floor = terrainHeight(centerX, centerZ);
        if (floor < seaLevel || floor > seaLevel + 12) return; // not on buildable lowland
        // Skip slopes: a giant shaved plaza on a hill looks worse than no village.
        int slope = 0;
        int[][] probes = {{8, 0}, {-8, 0}, {0, 8}, {0, -8}, {8, 7}, {-8, -7}};
        for (int[] p : probes) {
            slope = Math.max(slope, Math.abs(terrainHeight(centerX + p[0], centerZ + p[1]) - floor));
        }
        if (slope > 3) return;

        boolean desert = b == Biome.DESERT || b == Biome.SAVANNA;
        BlockType ground = desert ? BlockType.SAND : BlockType.GRASS;
        BlockType path = desert ? BlockType.SAND : BlockType.GRAVEL;

        int[][] houses = {{8, -7}, {-8, -7}, {8, 7}, {-8, 7}, {0, 11}};
        for (int i = 0; i < houses.length; i++) {
            int hx = centerX + houses[i][0];
            int hz = centerZ + houses[i][1];
            placePath(chunk, originX, originZ, centerX, centerZ, hx, hz, floor, path);
            BuildingType type = buildingTypeFor(centerX, centerZ, i);
            int[] size = buildingSize(type);
            flattenPad(chunk, originX, originZ, hx, hz, size[0] + 1, size[1] + 1, floor, ground);
            buildVillageHouse(chunk, originX, originZ, hx, hz, floor + 1, desert, type);
        }

        flattenPad(chunk, originX, originZ, centerX, centerZ, 2, 2, floor, ground);
        placeWell(chunk, originX, originZ, centerX, floor, centerZ);
    }

    private static int[] buildingSize(BuildingType t) {
        return switch (t) {
            case TALL_HOUSE -> new int[]{3, 2};
            case BLACKSMITH -> new int[]{2, 1};
            case TOWER -> new int[]{1, 1};
            default -> new int[]{3, 2};
        };
    }

    /** Clears plants/trees and levels a small pad so a building sits in the grass, not on a shaved square. */
    private void flattenPad(Chunk chunk, int originX, int originZ, int cx, int cz,
                            int halfX, int halfZ, int floor, BlockType surface) {
        for (int wx = cx - halfX; wx <= cx + halfX; wx++) {
            for (int wz = cz - halfZ; wz <= cz + halfZ; wz++) {
                int lx = wx - originX, lz = wz - originZ;
                if (lx < 0 || lx >= Chunk.SIZE || lz < 0 || lz >= Chunk.SIZE) continue;
                for (int y = floor + 1; y <= floor + 12 && y < Chunk.HEIGHT; y++) {
                    if (chunk.getLocal(lx, y, lz) != BlockType.AIR) {
                        chunk.setLocal(lx, y, lz, BlockType.AIR);
                    }
                }
                chunk.setLocal(lx, floor, lz, surface);
                for (int y = floor - 1; y >= floor - 3 && y > 0; y--) {
                    if (chunk.getLocal(lx, y, lz) == BlockType.AIR) {
                        chunk.setLocal(lx, y, lz, BlockType.DIRT);
                    }
                }
            }
        }
    }

    /** Chebyshev dirt/gravel path from the well to a house. */
    private void placePath(Chunk chunk, int originX, int originZ,
                           int x0, int z0, int x1, int z1, int floor, BlockType path) {
        int x = x0, z = z0;
        while (true) {
            if (!(x == x0 && z == z0)) {
                flattenPad(chunk, originX, originZ, x, z, 0, 0, floor, path);
            }
            if (x == x1 && z == z1) break;
            if (x != x1) x += Integer.signum(x1 - x);
            if (z != z1) z += Integer.signum(z1 - z);
        }
    }

    /** A one-block-deep stone well with fence posts and a small plank roof. */
    private void placeWell(Chunk chunk, int originX, int originZ, int cx, int floor, int cz) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) {
                    setWorldBlock(chunk, originX, originZ, cx, floor, cz, BlockType.AIR);
                    setWorldBlock(chunk, originX, originZ, cx, floor - 1, cz, BlockType.WATER_SOURCE);
                    setWorldBlock(chunk, originX, originZ, cx, floor - 2, cz, BlockType.STONE);
                } else {
                    setWorldBlock(chunk, originX, originZ, cx + dx, floor, cz + dz, BlockType.STONE);
                }
            }
        }
        int[][] posts = {{1, 1}, {1, -1}, {-1, 1}, {-1, -1}};
        for (int[] p : posts) {
            setWorldBlock(chunk, originX, originZ, cx + p[0], floor + 1, cz + p[1], BlockType.WOODEN_FENCE);
        }
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                setWorldBlock(chunk, originX, originZ, cx + dx, floor + 2, cz + dz, BlockType.PLANKS_SLAB);
            }
        }
        setWorldBlock(chunk, originX, originZ, cx, floor + 1, cz + 1, BlockType.TORCH);
    }

    /** True if the wall cell (dx, dz) of a halfX-by-halfZ footprint is a window. */
    private static boolean isWindow(int dx, int dz, int halfX, int halfZ) {
        if (Math.abs(dz) == halfZ && halfX >= 2 && (dx == halfX - 1 || dx == -(halfX - 1))) return true;
        if (Math.abs(dx) == halfX && halfZ >= 2 && (dz == halfZ - 1 || dz == -(halfZ - 1))) return true;
        return false;
    }

    /** Builds a village building of the given type, all clipped to this chunk. */
    private void buildVillageHouse(Chunk chunk, int originX, int originZ, int wx, int wz, int baseY,
                                   boolean desert, BuildingType t) {
        int[] size = buildingSize(t);
        int halfX = size[0], halfZ = size[1];
        int wallH = switch (t) {
            case TALL_HOUSE -> 3;
            case TOWER -> 4;
            default -> 2;
        };
        boolean stoneWall = t == BuildingType.BLACKSMITH || t == BuildingType.TOWER || desert;
        BlockType wall = stoneWall ? BlockType.STONE : BlockType.PLANKS;
        BlockType corner = desert ? BlockType.STONE : BlockType.WOOD_LOG;
        BlockType stair = stoneWall ? BlockType.STONE_STAIRS : BlockType.PLANKS_STAIRS;
        BlockType fill = stoneWall ? BlockType.STONE : BlockType.PLANKS;

        for (int h = 0; h < wallH; h++) {
            for (int dx = -halfX; dx <= halfX; dx++) {
                for (int dz = -halfZ; dz <= halfZ; dz++) {
                    if (Math.abs(dx) != halfX && Math.abs(dz) != halfZ) continue; // interior
                    if (dx == 0 && dz == halfZ && h < 2) continue; // door opening
                    if (h == 1 && isWindow(dx, dz, halfX, halfZ)) {
                        setWorldBlock(chunk, originX, originZ, wx + dx, baseY + h, wz + dz, BlockType.GLASS);
                        continue;
                    }
                    boolean isCorner = Math.abs(dx) == halfX && Math.abs(dz) == halfZ;
                    setWorldBlock(chunk, originX, originZ, wx + dx, baseY + h, wz + dz,
                            isCorner ? corner : wall);
                }
            }
        }

        if (t != BuildingType.TOWER) {
            setWorldBlock(chunk, originX, originZ, wx, baseY, wz + halfZ, BlockType.DOOR);
            setWorldBlock(chunk, originX, originZ, wx, baseY + 1, wz + halfZ, BlockType.DOOR);
        }

        if (t == BuildingType.BLACKSMITH) {
            setWorldBlock(chunk, originX, originZ, wx + 1, baseY, wz, BlockType.FURNACE);
            setWorldOrientation(chunk, originX, originZ, wx + 1, baseY, wz, (byte) 3);
        }

        if (t == BuildingType.TOWER) {
            for (int dx = -halfX; dx <= halfX; dx++) {
                for (int dz = -halfZ; dz <= halfZ; dz++) {
                    setWorldBlock(chunk, originX, originZ, wx + dx, baseY + wallH, wz + dz, BlockType.STONE_SLAB);
                    if (Math.abs(dx) == halfX || Math.abs(dz) == halfZ) {
                        setWorldBlock(chunk, originX, originZ, wx + dx, baseY + wallH + 1, wz + dz, BlockType.WOODEN_FENCE);
                    }
                }
            }
        } else {
            placeGableRoof(chunk, originX, originZ, wx, wz, baseY + wallH, halfX, halfZ, stair, fill);
        }
    }

    /**
     * Gable roof, ridge along X, slopes north/south. Overhangs one block so the
     * house isn't a flat lid sitting on a flat pad.
     */
    private void placeGableRoof(Chunk chunk, int originX, int originZ,
                                int wx, int wz, int roofY, int halfX, int halfZ,
                                BlockType stair, BlockType fill) {
        for (int layer = 0; layer <= halfZ; layer++) {
            int span = halfZ - layer + 1;
            int y = roofY + layer;
            for (int dx = -halfX - 1; dx <= halfX + 1; dx++) {
                setWorldBlock(chunk, originX, originZ, wx + dx, y, wz + span, stair);
                setWorldOrientation(chunk, originX, originZ, wx + dx, y, wz + span, (byte) 0);
                setWorldBlock(chunk, originX, originZ, wx + dx, y, wz - span, stair);
                setWorldOrientation(chunk, originX, originZ, wx + dx, y, wz - span, (byte) 1);
                for (int dz = -span + 1; dz <= span - 1; dz++) {
                    setWorldBlock(chunk, originX, originZ, wx + dx, y, wz + dz, fill);
                }
            }
        }
    }

    /** A tall jungle tree: a longer trunk and a bigger, lusher canopy than the oak. */
    private void placeJungleTree(Chunk chunk, int x, int y, int z, Random rnd) {
        int trunkHeight = 7 + rnd.nextInt(3);
        for (int i = 0; i < trunkHeight; i++) {
            chunk.setLocal(x, y + i, z, BlockType.JUNGLE_LOG);
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
                        chunk.setLocal(x + dx, canopyBase + cy, z + dz, BlockType.JUNGLE_LEAVES);
                    }
                }
            }
        }
        chunk.setLocal(x, y + trunkHeight, z, BlockType.JUNGLE_LEAVES);

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

    /** A cherry tree: a cherry-wood trunk crowned with pink blossom leaves. */
    private void placeCherryTree(Chunk chunk, int x, int y, int z, Random rnd) {
        int trunkHeight = 4 + rnd.nextInt(2);
        for (int i = 0; i < trunkHeight; i++) {
            chunk.setLocal(x, y + i, z, BlockType.CHERRY_LOG);
        }
        int canopyBase = y + trunkHeight - 2;
        for (int cy = 0; cy <= 2; cy++) {
            int radius = (cy == 2) ? 1 : 2;
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (dx == 0 && dz == 0 && cy < 2) continue;
                    if (Math.abs(dx) == radius && Math.abs(dz) == radius && radius == 2) continue;
                    BlockType existing = chunk.getLocal(x + dx, canopyBase + cy, z + dz);
                    if (existing == BlockType.AIR) {
                        chunk.setLocal(x + dx, canopyBase + cy, z + dz, BlockType.CHERRY_LEAVES);
                    }
                }
            }
        }
        chunk.setLocal(x, y + trunkHeight, z, BlockType.CHERRY_LEAVES);
    }

    /** A small patch of a few bamboo stalks. */
    private void placeBamboo(Chunk chunk, int x, int y, int z, Random rnd) {
        int stalks = 1 + rnd.nextInt(3);
        for (int s = 0; s < stalks; s++) {
            int bx = x + (s == 0 ? 0 : rnd.nextInt(3) - 1);
            int bz = z + (s == 0 ? 0 : rnd.nextInt(3) - 1);
            int h = 3 + rnd.nextInt(5);
            for (int i = 0; i < h; i++) {
                if (chunk.getLocal(bx, y + i, bz) == BlockType.AIR) {
                    chunk.setLocal(bx, y + i, bz, BlockType.BAMBOO);
                }
            }
        }
    }

    /** A tall pillar of packed ice jutting up from snowy ground. */
    private void placeIceSpike(Chunk chunk, int x, int y, int z, Random rnd) {
        int h = 2 + rnd.nextInt(4);
        for (int i = 0; i < h; i++) {
            chunk.setLocal(x, y + i, z, BlockType.PACKED_ICE);
        }
    }

    /** A dense carpet of flowers and tall grass for the flower meadow. */
    private void placeFlowerCover(Chunk chunk, int x, int y, int z, Random rnd) {
        if (rnd.nextInt(3) == 0) {
            chunk.setLocal(x, y, z, rnd.nextBoolean() ? BlockType.FLOWER_RED : BlockType.FLOWER_YELLOW);
        } else if (rnd.nextInt(4) == 0) {
            chunk.setLocal(x, y, z, BlockType.TALL_GRASS);
        } else if (rnd.nextInt(60) == 0) {
            chunk.setLocal(x, y, z, BlockType.BERRY_BUSH);
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

    /** A narrower, conical evergreen: shrinking leaf rings stacked up a taller trunk. Used in taiga and mountains. */
    private void placePineTree(Chunk chunk, int x, int y, int z, Random rnd) {
        int trunkHeight = 6 + rnd.nextInt(3);
        for (int i = 0; i < trunkHeight; i++) {
            chunk.setLocal(x, y + i, z, BlockType.PINE_LOG);
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
                        chunk.setLocal(x + dx, cy, z + dz, BlockType.LEAVES); // pine reuses oak leaf tile
                    }
                }
            }
        }
        chunk.setLocal(x, y + trunkHeight, z, BlockType.LEAVES);
    }

    /** Birch tree: slimmer oak shape with BIRCH_LOG trunk and BIRCH_LEAVES canopy. */
    private void placeBirchTree(Chunk chunk, int x, int y, int z, Random rnd) {
        int trunkHeight = 5 + rnd.nextInt(3); // slightly taller than oak
        for (int i = 0; i < trunkHeight; i++) {
            chunk.setLocal(x, y + i, z, BlockType.BIRCH_LOG);
        }
        int canopyBase = y + trunkHeight - 2;
        for (int cy = 0; cy <= 2; cy++) {
            int radius = (cy == 2) ? 1 : 2;
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (dx == 0 && dz == 0 && cy < 2) continue;
                    if (Math.abs(dx) == radius && Math.abs(dz) == radius && radius == 2) continue;
                    BlockType existing = chunk.getLocal(x + dx, canopyBase + cy, z + dz);
                    if (existing == BlockType.AIR) {
                        chunk.setLocal(x + dx, canopyBase + cy, z + dz, BlockType.BIRCH_LEAVES);
                    }
                }
            }
        }
        chunk.setLocal(x, y + trunkHeight, z, BlockType.BIRCH_LEAVES);
    }

    private void placeCactus(Chunk chunk, int x, int y, int z, Random rnd) {
        int h = 1 + rnd.nextInt(3);
        for (int i = 0; i < h; i++) {
            chunk.setLocal(x, y + i, z, BlockType.CACTUS);
        }
    }
}
