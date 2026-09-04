package com.minecraftclone.player;

import com.minecraftclone.world.BlockType;
import com.minecraftclone.world.Chunk;
import com.minecraftclone.world.World;

import java.util.Random;

/**
 * Farming system: crop growth, planting rules, and harvest drops.
 *
 * <p>Crops (wheat, potato, carrot) grow through multiple stages when planted
 * on {@link BlockType#FARMLAND}. Growth is driven by a Minecraft-faithful
 * <em>random tick</em> system: each nearby chunk column is divided into
 * 16-block-tall sections, and each section receives {@value #RANDOM_TICK_SPEED}
 * random block picks per simulated game-tick (20 TPS). When a picked block is
 * a crop that still has a next stage, it advances. Ticks are limited to chunks
 * within {@value #SIMULATION_CHUNK_RADIUS} of the player so hydration scans
 * cannot scale with render distance.
 *
 * <p>Sugar cane grows upward on random ticks while its column base sits on
 * dirt/grass/sand adjacent to water, up to {@value #SUGAR_CANE_MAX_HEIGHT}
 * blocks. It breaks and drops itself when that support is removed.
 *
 * <p>Farming interactions (hoe-on-dirt, seeds-on-farmland, bone-meal, canteen-at-water)
 * are wired in {@code Main}; this class answers "what grows where", "can I till
 * this", "what does bone meal do", and "what does breaking this crop produce?"
 */
public final class Farming {

    /**
     * Number of random block picks per chunk section (16×16×16) per game tick.
     * Vanilla Minecraft default is 3. Higher values make crops grow faster.
     */
    static final int RANDOM_TICK_SPEED = 3;

    /**
     * Loaded chunks further than this Chebyshev distance from the player are
     * skipped. Bounds farming cost independently of render distance.
     */
    static final int SIMULATION_CHUNK_RADIUS = 4;

    /**
     * Height of a chunk section in blocks. Matches Minecraft's 16-block sections.
     * Each loaded chunk column contains {@code Chunk.HEIGHT / SECTION_HEIGHT} sections.
     */
    private static final int SECTION_HEIGHT = 16;

    // -----------------------------------------------------------------------
    // Queries
    // -----------------------------------------------------------------------

    /** True for a planted crop world-block (any growth stage, any crop type). */
    public static boolean isCrop(BlockType type) {
        return type != null && type.isCrop();
    }

    /** True for item types that can be planted on FARMLAND (seeds, raw potato, carrot). */
    public static boolean isPlantable(BlockType type) {
        return type != null && type.isPlantable();
    }

    /**
     * True for soil a hoe can till into farmland: dirt, grass, swamp grass,
     * or mycelium. Farmland itself is already tilled.
     */
    public static boolean canTill(BlockType type) {
        return type == BlockType.DIRT
                || type == BlockType.GRASS
                || type == BlockType.SWAMP_GRASS
                || type == BlockType.MYCELIUM;
    }

    /**
     * The first-stage crop block that a plantable item becomes when placed on
     * FARMLAND, or {@code null} if the item isn't plantable.
     */
    public static BlockType plantedCrop(BlockType item) {
        if (item == null) return null;
        return switch (item) {
            case SEEDS  -> BlockType.WHEAT_STAGE_1;
            case POTATO -> BlockType.POTATO_CROP_1;
            case CARROT -> BlockType.CARROT_CROP_1;
            default     -> null;
        };
    }

    /**
     * The next growth stage for a crop, or {@code null} when fully grown.
     * Returning {@code null} means the crop is ripe and ready to harvest.
     */
    public static BlockType nextStage(BlockType crop) {
        if (crop == null) return null;
        return switch (crop) {
            case WHEAT_STAGE_1  -> BlockType.WHEAT_STAGE_2;
            case WHEAT_STAGE_2  -> BlockType.WHEAT_STAGE_3;
            case WHEAT_STAGE_3  -> BlockType.WHEAT_STAGE_4;
            case POTATO_CROP_1  -> BlockType.POTATO_CROP_2;
            case POTATO_CROP_2  -> BlockType.POTATO_CROP_3;
            case CARROT_CROP_1  -> BlockType.CARROT_CROP_2;
            case CARROT_CROP_2  -> BlockType.CARROT_CROP_3;
            default             -> null; // fully grown (WHEAT_STAGE_4, POTATO_CROP_3, CARROT_CROP_3)
        };
    }

    /** True if this crop is mature and dropping it gives food (not just seeds). */
    public static boolean isRipe(BlockType crop) {
        return crop == BlockType.WHEAT_STAGE_4
                || crop == BlockType.POTATO_CROP_3
                || crop == BlockType.CARROT_CROP_3;
    }

    /**
     * The primary item dropped when this crop is broken.
     * Mature crops drop food; immature wheat drops seeds; immature
     * potato/carrot drops nothing useful (caller should discard).
     */
    public static BlockType harvestDrop(BlockType crop) {
        if (crop == null) return null;
        return switch (crop) {
            case WHEAT_STAGE_4 -> BlockType.WHEAT;
            case POTATO_CROP_3 -> BlockType.POTATO;
            case CARROT_CROP_3 -> BlockType.CARROT;
            case SUGAR_CANE -> BlockType.SUGAR_CANE;
            // Immature: at least return seeds for wheat, null for others
            case WHEAT_STAGE_1, WHEAT_STAGE_2, WHEAT_STAGE_3 -> BlockType.SEEDS;
            default            -> null; // immature potato/carrot → no drop
        };
    }

    /**
     * True if breaking this ripe crop also drops bonus seeds (wheat only).
     * Main uses this to spawn an extra 1-3 SEEDS alongside the WHEAT drop.
     */
    public static boolean alsoDropsSeeds(BlockType crop) {
        return crop == BlockType.WHEAT_STAGE_4;
    }

    /** Maximum height a natural sugar cane column can reach (vanilla: 3). */
    private static final int SUGAR_CANE_MAX_HEIGHT = 3;

    // -----------------------------------------------------------------------
    // Hydration
    // -----------------------------------------------------------------------

    /**
     * Vanilla rule: farmland is hydrated when any water block (source, flow, or
     * the legacy WATER constant) exists within 4 blocks horizontally at the same
     * Y or one block above. This is checked on every random tick and drives the
     * FARMLAND ↔ FARMLAND_WET state transition.
     */
    public static boolean isNearWater(World world, int wx, int wy, int wz) {
        return isNearWater(world::getBlock, wx, wy, wz);
    }

    static boolean isNearWater(BlockGet get, int wx, int wy, int wz) {
        for (int dz = -4; dz <= 4; dz++) {
            for (int dx = -4; dx <= 4; dx++) {
                for (int dy = 0; dy <= 1; dy++) {
                    BlockType b = get.get(wx + dx, wy + dy, wz + dz);
                    if (b != null && b.isWater()) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Till the soil at {@code (wx, wy, wz)} into farmland (wet if water is
     * nearby). Returns false if the block isn't tillable or a solid block
     * is sitting on top of it (vanilla: you can't till under a floor).
     */
    public static boolean tillAt(World world, int wx, int wy, int wz) {
        return tillAt(world::getBlock, world::setBlock, wx, wy, wz);
    }

    static boolean tillAt(BlockGet get, BlockSet set, int wx, int wy, int wz) {
        if (!canTill(get.get(wx, wy, wz))) return false;
        BlockType above = get.get(wx, wy + 1, wz);
        if (above != null && above.solid) return false;
        boolean wet = isNearWater(get, wx, wy, wz);
        set.set(wx, wy, wz, wet ? BlockType.FARMLAND_WET : BlockType.FARMLAND);
        return true;
    }

    // -----------------------------------------------------------------------
    // Sugar cane helpers
    // -----------------------------------------------------------------------

    /**
     * True if water (non-lava fluid) is directly adjacent (N/S/E/W, same Y)
     * to the given block position. Used for sugar cane base validation.
     */
    public static boolean isAdjacentToWater(World world, int wx, int wy, int wz) {
        int[][] dirs = {{1,0},{-1,0},{0,1},{0,-1}};
        for (int[] d : dirs) {
            BlockType b = world.getBlock(wx + d[0], wy, wz + d[1]);
            if (b != null && b.isWater()) {
                return true;
            }
        }
        return false;
    }

    /**
     * True if sugar cane can be placed/survive at {@code (wx, wy, wz)}.
     * The block below must be DIRT, GRASS, or SAND (or another SUGAR_CANE),
     * and the soil at the bottom of the column must be adjacent to water.
     */
    public static boolean canSugarCaneStand(World world, int wx, int wy, int wz) {
        BlockType below = world.getBlock(wx, wy - 1, wz);
        if (below == null) return false;
        // Stacking on another cane is allowed only if the column base has valid soil and water.
        if (below == BlockType.SUGAR_CANE) {
            // Walk down to the bottom cane block.
            int baseY = wy - 1;
            while (baseY > 0 && world.getBlock(wx, baseY - 1, wz) == BlockType.SUGAR_CANE) baseY--;
            // Check the soil block beneath the bottom cane.
            BlockType soil = world.getBlock(wx, baseY - 1, wz);
            if (soil == null) return false;
            boolean validSoil = soil == BlockType.DIRT || soil == BlockType.GRASS || soil == BlockType.SAND;
            return validSoil && isAdjacentToWater(world, wx, baseY - 1, wz);
        }
        // Soil block: must be dirt/grass/sand AND adjacent to water.
        boolean validSoil = below == BlockType.DIRT || below == BlockType.GRASS || below == BlockType.SAND;
        return validSoil && isAdjacentToWater(world, wx, wy - 1, wz);
    }

    /**
     * Returns the Y of the topmost SUGAR_CANE block in a column starting at (wx, wy, wz).
     * If the block at wy is not sugar cane, returns wy - 1 (signals "empty").
     */
    static int sugarCaneTop(World world, int wx, int wy, int wz) {
        int y = wy;
        while (y + 1 < Chunk.HEIGHT && world.getBlock(wx, y + 1, wz) == BlockType.SUGAR_CANE) y++;
        return y;
    }

    /**
     * Breaks every sugar-cane block from {@code (wx, wy, wz)} up to the top of
     * the column, dropping one {@link BlockType#SUGAR_CANE} per cell. The cell
     * at {@code wy} is included. No-op if that cell is not sugar cane.
     */
    public static void collapseSugarCaneFrom(World world, int wx, int wy, int wz, java.util.Random rnd) {
        if (world.getBlock(wx, wy, wz) != BlockType.SUGAR_CANE) return;
        int topY = sugarCaneTop(world, wx, wy, wz);
        for (int dropY = wy; dropY <= topY; dropY++) {
            world.setBlock(wx, dropY, wz, BlockType.AIR);
            world.spawnItem(wx, dropY, wz, BlockType.SUGAR_CANE, 1, rnd);
        }
    }

    /**
     * Returns the height of the sugar cane column that contains (wx, wy, wz).
     */
    private static int sugarCaneHeight(World world, int wx, int wy, int wz) {
        // Walk to bottom of column.
        int botY = wy;
        while (botY > 0 && world.getBlock(wx, botY - 1, wz) == BlockType.SUGAR_CANE) botY--;
        int topY = sugarCaneTop(world, wx, wy, wz);
        return topY - botY + 1;
    }

    // -----------------------------------------------------------------------
    // Growth tick — Minecraft-style random tick system
    // -----------------------------------------------------------------------

    /**
     * Called each frame from the main game loop. Advances crops in all loaded
     * chunks using the same random-tick algorithm as vanilla Minecraft:
     *
     * <ol>
     *   <li>Convert frame delta-time to simulated game-ticks (20 TPS).</li>
     *   <li>For every loaded chunk, iterate its 16-block-tall <em>sections</em>.</li>
     *   <li>Each section receives {@value #RANDOM_TICK_SPEED} × ticksThisFrame
     *       random block picks; fractional ticks are handled probabilistically.</li>
     *   <li>If the randomly selected block is a crop with a next stage, it grows.</li>
     *   <li>If a crop's farmland base was removed, revert the crop to air.</li>
     * </ol>
     *
     * <p>Range: chunks within {@value #SIMULATION_CHUNK_RADIUS} of the player,
     * independent of render distance so hydration scans cannot scale with the
     * view radius.
     *
     * @param world   the world to query and mutate
     * @param dt      frame delta-time in seconds
     * @param rnd     shared random (not freshly seeded — deliberate noise)
     * @param playerX player world X (used to bound the simulation radius)
     * @param playerZ player world Z
     */
    public static void tickCrops(World world, float dt, Random rnd, float playerX, float playerZ) {
        // Convert wall-clock dt to simulated game-ticks (20 TPS = 0.05s per tick).
        // At typical 60 fps, dt ≈ 0.0167 → ~0.33 ticks. We handle the fractional
        // part probabilistically so the long-run rate is always correct.
        float ticksF = dt * 20f;
        int wholeTicks = (int) ticksF;
        float fracTick = ticksF - wholeTicks;

        int sectionsPerColumn = Chunk.HEIGHT / SECTION_HEIGHT;
        int playerChunkX = Math.floorDiv((int) Math.floor(playerX), Chunk.SIZE);
        int playerChunkZ = Math.floorDiv((int) Math.floor(playerZ), Chunk.SIZE);

        for (Chunk chunk : world.getLoadedChunks()) {
            int originX = chunk.getOriginX();
            int originZ = chunk.getOriginZ();
            int chunkX = Math.floorDiv(originX, Chunk.SIZE);
            int chunkZ = Math.floorDiv(originZ, Chunk.SIZE);
            if (Math.abs(chunkX - playerChunkX) > SIMULATION_CHUNK_RADIUS
                    || Math.abs(chunkZ - playerChunkZ) > SIMULATION_CHUNK_RADIUS) {
                continue;
            }

            // Number of random picks this frame for this chunk column.
            // Each section gets RANDOM_TICK_SPEED picks per whole tick, plus a
            // probabilistic extra pick for the fractional tick remainder.
            int picks = RANDOM_TICK_SPEED * sectionsPerColumn * wholeTicks;
            if (rnd.nextFloat() < fracTick) {
                // Fractional tick: give all sections one extra pick with probability = fracTick.
                picks += RANDOM_TICK_SPEED * sectionsPerColumn;
            }

            for (int p = 0; p < picks; p++) {
                // Pick a random block within the entire chunk column (all sections combined).
                int lx = rnd.nextInt(Chunk.SIZE);
                int ly = rnd.nextInt(Chunk.HEIGHT);
                int lz = rnd.nextInt(Chunk.SIZE);

                int wx = originX + lx;
                int wy = ly;
                int wz = originZ + lz;

                BlockType b = world.getBlock(wx, wy, wz);
                if (b == null) continue;

                // --- Farmland hydration tick ---
                if (b == BlockType.FARMLAND || b == BlockType.FARMLAND_WET) {
                    boolean wet = isNearWater(world, wx, wy, wz);
                    if (wet && b == BlockType.FARMLAND) {
                        world.setBlock(wx, wy, wz, BlockType.FARMLAND_WET);
                    } else if (!wet && b == BlockType.FARMLAND_WET) {
                        world.setBlock(wx, wy, wz, BlockType.FARMLAND);
                    }
                    continue;
                }

                if (!isCrop(b)) continue;

                // --- Sugar cane: separate growth path ---
                if (b == BlockType.SUGAR_CANE) {
                    if (!canSugarCaneStand(world, wx, wy, wz)) {
                        // Water removed from base — break this cane and everything above it.
                        collapseSugarCaneFrom(world, wx, wy, wz, rnd);
                    } else {
                        // Only the top-most cane block can grow upward (like vanilla).
                        if (world.getBlock(wx, wy + 1, wz) == BlockType.SUGAR_CANE) continue;
                        // Grow upward if below the max height.
                        if (sugarCaneHeight(world, wx, wy, wz) < SUGAR_CANE_MAX_HEIGHT
                                && world.getBlock(wx, wy + 1, wz) == BlockType.AIR) {
                            world.setBlock(wx, wy + 1, wz, BlockType.SUGAR_CANE);
                        }
                    }
                    continue;
                }

                // --- Normal crops: need farmland base ---
                BlockType base = world.getBlock(wx, wy - 1, wz);
                if (base == null || !base.isFarmland()) {
                    // Farmland was broken — clear the orphaned crop.
                    world.setBlock(wx, wy, wz, BlockType.AIR);
                    continue;
                }

                // Crops only grow on HYDRATED farmland (FARMLAND_WET), just like vanilla.
                if (base != BlockType.FARMLAND_WET) continue;

                BlockType next = nextStage(b);
                if (next != null) {
                    world.setBlock(wx, wy, wz, next);
                }
            }
        }
    }

    /**
     * @deprecated Use {@link #tickCrops(World, float, Random, float, float)} instead.
     *             This player-radius variant is kept only to avoid breaking any
     *             direct call sites during the transition; it now delegates to
     *             the chunk-based implementation.
     */
    @Deprecated
    public static void tickCropsNear(World world, int px, int py, int pz,
                                     float dt, Random rnd) {
        tickCrops(world, dt, rnd, px, pz);
    }

    // -----------------------------------------------------------------------
    // Bone meal
    // -----------------------------------------------------------------------

    /**
     * Apply bone meal at {@code (wx, wy, wz)}. Returns true if something grew
     * (the caller should consume one bone meal). Crops jump 1–2 stages;
     * grass / swamp grass / mycelium sprout tall grass and the odd flower
     * in a small neighbourhood. No-op on fully grown crops, sugar cane, or
     * anything that isn't soil or a crop.
     */
    public static boolean applyBonemeal(World world, int wx, int wy, int wz, Random rnd) {
        BlockType b = world.getBlock(wx, wy, wz);
        // Saplings need the full World so the tree can cross chunk boundaries.
        if (b != null && b.isSapling()) {
            return applyBonemealSapling(world, wx, wy, wz, b, rnd);
        }
        return applyBonemeal(world::getBlock, world::setBlock, wx, wy, wz, rnd);
    }

    static boolean applyBonemeal(BlockGet get, BlockSet set, int wx, int wy, int wz, Random rnd) {
        BlockType b = get.get(wx, wy, wz);
        if (b == null) return false;

        // Clicking a plant on grass still fertilizes the grass underneath.
        if (b == BlockType.TALL_GRASS || b == BlockType.FLOWER_RED || b == BlockType.FLOWER_YELLOW) {
            wy = wy - 1;
            b = get.get(wx, wy, wz);
            if (b == null) return false;
        }

        if (b.isFarmland()) {
            BlockType crop = get.get(wx, wy + 1, wz);
            return growCrop(get, set, wx, wy + 1, wz, crop, rnd);
        }
        if (isCrop(b) && b != BlockType.SUGAR_CANE) {
            return growCrop(get, set, wx, wy, wz, b, rnd);
        }
        if (b == BlockType.GRASS || b == BlockType.SWAMP_GRASS || b == BlockType.MYCELIUM) {
            return sproutOnGrass(get, set, wx, wy, wz, rnd);
        }
        // Bone meal on saplings is handled by the World-aware overload; this
        // BlockGet/BlockSet variant cannot do cross-chunk tree placement.
        return false;
    }

    /** Advance {@code crop} by 1 or 2 stages. Returns false if it was already ripe. */
    static boolean growCrop(BlockGet get, BlockSet set, int wx, int wy, int wz,
                            BlockType crop, Random rnd) {
        if (crop == null || !isCrop(crop) || crop == BlockType.SUGAR_CANE) return false;
        int steps = 1 + rnd.nextInt(2);
        BlockType cur = crop;
        boolean grew = false;
        for (int i = 0; i < steps; i++) {
            BlockType next = nextStage(cur);
            if (next == null) break;
            cur = next;
            grew = true;
        }
        if (grew) set.set(wx, wy, wz, cur);
        return grew;
    }

    /**
     * Scatter tall grass (and the occasional flower) on air above nearby
     * grass-like blocks. The clicked cell always sprouts if the space above
     * is empty; neighbours are probabilistic.
     */
    static boolean sproutOnGrass(BlockGet get, BlockSet set, int wx, int wy, int wz, Random rnd) {
        int spawned = 0;
        for (int dz = -2; dz <= 2; dz++) {
            for (int dx = -2; dx <= 2; dx++) {
                if (Math.abs(dx) == 2 && Math.abs(dz) == 2) continue; // skip far corners
                int x = wx + dx;
                int z = wz + dz;
                BlockType ground = get.get(x, wy, z);
                if (ground != BlockType.GRASS && ground != BlockType.SWAMP_GRASS
                        && ground != BlockType.MYCELIUM) {
                    continue;
                }
                BlockType above = get.get(x, wy + 1, z);
                if (above != null && above != BlockType.AIR) continue;
                float chance = (dx == 0 && dz == 0) ? 1f : 0.4f;
                if (rnd.nextFloat() > chance) continue;
                set.set(x, wy + 1, z, randomSprout(rnd));
                spawned++;
            }
        }
        return spawned > 0;
    }

    static BlockType randomSprout(Random rnd) {
        float r = rnd.nextFloat();
        if (r < 0.08f) return BlockType.FLOWER_RED;
        if (r < 0.16f) return BlockType.FLOWER_YELLOW;
        return BlockType.TALL_GRASS;
    }

    // -----------------------------------------------------------------------
    // Sapling growth tick — same random-tick algorithm as crops
    // -----------------------------------------------------------------------

    /**
     * Sapling growth chance per random tick (1-in-N).  Vanilla oak saplings
     * grow ~1/7 ticks that hit them; we use the same rate for all types.
     */
    private static final int SAPLING_GROW_CHANCE = 7;

    /**
     * Called each frame alongside {@link #tickCrops}. Attempts to grow any
     * sapling blocks selected by the random-tick in loaded chunks near the
     * player.
     *
     * <p>Jungle saplings check all four possible 2×2 corner configurations:
     * if every position in a 2×2 square contains {@code JUNGLE_SAPLING} (all
     * at the same Y), the group grows a large 2-block-wide jungle tree; a
     * lone jungle sapling grows a smaller 1-block-wide tree.
     *
     * @param world   world to query and mutate
     * @param dt      frame delta-time in seconds
     * @param rnd     shared random
     * @param playerX player world X coordinate (bounds simulation radius)
     * @param playerZ player world Z coordinate
     */
    public static void tickSaplings(World world, float dt, Random rnd,
                                    float playerX, float playerZ) {
        float ticksF = dt * 20f;
        int wholeTicks = (int) ticksF;
        float fracTick = ticksF - wholeTicks;

        int sectionsPerColumn = Chunk.HEIGHT / SECTION_HEIGHT;
        int playerChunkX = Math.floorDiv((int) Math.floor(playerX), Chunk.SIZE);
        int playerChunkZ = Math.floorDiv((int) Math.floor(playerZ), Chunk.SIZE);

        for (Chunk chunk : world.getLoadedChunks()) {
            int originX = chunk.getOriginX();
            int originZ = chunk.getOriginZ();
            int chunkX = Math.floorDiv(originX, Chunk.SIZE);
            int chunkZ = Math.floorDiv(originZ, Chunk.SIZE);
            if (Math.abs(chunkX - playerChunkX) > SIMULATION_CHUNK_RADIUS
                    || Math.abs(chunkZ - playerChunkZ) > SIMULATION_CHUNK_RADIUS) {
                continue;
            }

            int picks = RANDOM_TICK_SPEED * sectionsPerColumn * wholeTicks;
            if (rnd.nextFloat() < fracTick) {
                picks += RANDOM_TICK_SPEED * sectionsPerColumn;
            }

            for (int p = 0; p < picks; p++) {
                int lx = rnd.nextInt(Chunk.SIZE);
                int ly = rnd.nextInt(Chunk.HEIGHT);
                int lz = rnd.nextInt(Chunk.SIZE);

                int wx = originX + lx;
                int wy = ly;
                int wz = originZ + lz;

                BlockType b = world.getBlock(wx, wy, wz);
                if (b == null || !b.isSapling()) continue;

                // Each tick the sapling gets only a 1-in-SAPLING_GROW_CHANCE
                // of actually growing, matching vanilla oak growth rates.
                if (rnd.nextInt(SAPLING_GROW_CHANCE) != 0) continue;

                growSapling(world, wx, wy, wz, b, rnd);
            }
        }
    }

    /**
     * Attempt to grow the sapling at {@code (wx, wy, wz)}.
     * Does nothing if the block below is not DIRT or GRASS, or if there
     * is not enough clear space above for the tree.
     */
    static void growSapling(World world, int wx, int wy, int wz,
                            BlockType sapling, Random rnd) {
        // Must be planted on dirt or grass.
        BlockType below = world.getBlock(wx, wy - 1, wz);
        if (below != BlockType.DIRT && below != BlockType.GRASS) return;

        switch (sapling) {
            case OAK_SAPLING    -> growOakTree(world, wx, wy, wz, rnd);
            case BIRCH_SAPLING  -> growBirchTree(world, wx, wy, wz, rnd);
            case JUNGLE_SAPLING -> growJungleSapling(world, wx, wy, wz, rnd);
            case PINE_SAPLING   -> growPineTree(world, wx, wy, wz, rnd);
            case CHERRY_SAPLING -> growCherryTree(world, wx, wy, wz, rnd);
            default             -> { /* not a sapling */ }
        }
    }

    /**
     * Jungle sapling: if a complete 2×2 of JUNGLE_SAPLING exists (all at the
     * same Y, any of the four corner configurations containing this sapling),
     * grows a large multi-trunk jungle tree rooted at the SW corner.
     * Otherwise grows a single-trunk jungle tree here.
     */
    private static void growJungleSapling(World world, int wx, int wy, int wz, Random rnd) {
        int[] sw = find2x2JungleSWCorner(world::getBlock, wx, wy, wz);
        if (sw != null) {
            // Select the actual trunkH first so we validate the exact height that
            // growLargeJungleTree will use — prevents a tall tree (trunkH up to 30)
            // from overwriting occupied cells above a shorter MIN_TRUNK_H preflight.
            final int trunkH = 20 + rnd.nextInt(11); // mirrors growLargeJungleTree range
            final int CANOPY_BUFFER = 3;
            boolean clear = true;
            outer:
            for (int tx = 0; tx < 2; tx++) {
                for (int tz = 0; tz < 2; tz++) {
                    if (!hasClearance(world, sw[0] + tx, wy, sw[1] + tz,
                            trunkH + CANOPY_BUFFER)) {
                        clear = false;
                        break outer;
                    }
                }
            }
            if (!clear) return; // not enough headroom; saplings stay in place
            // Remove all four saplings, then grow the large tree from the SW corner.
            int[][] corners = {{0, 0}, {1, 0}, {0, 1}, {1, 1}};
            for (int[] c : corners) {
                world.setBlock(sw[0] + c[0], wy, sw[1] + c[1], BlockType.AIR);
            }
            growLargeJungleTree(world, sw[0], wy, sw[1], trunkH);
        } else {
            // No complete 2×2 — grow a small 1×1 jungle tree.
            growSmallJungleTree(world, wx, wy, wz, rnd);
        }
    }

    /**
     * Returns the {@code [swX, swZ]} of the SW corner of a complete 2×2
     * JUNGLE_SAPLING square at height {@code wy} that contains {@code (wx, wz)},
     * or {@code null} if no such square exists.
     *
     * <p>Package-private so unit tests can verify the detection logic without
     * a full World instance.
     */
    static int[] find2x2JungleSWCorner(BlockGet get, int wx, int wy, int wz) {
        int[][] offsets = {{0, 0}, {1, 0}, {0, 1}, {1, 1}};
        for (int[] myCorner : offsets) {
            int swx = wx - myCorner[0];
            int swz = wz - myCorner[1];
            boolean allSaplings = true;
            for (int[] c : offsets) {
                if (get.get(swx + c[0], wy, swz + c[1]) != BlockType.JUNGLE_SAPLING) {
                    allSaplings = false;
                    break;
                }
            }
            if (allSaplings) return new int[]{swx, swz};
        }
        return null;
    }

    // -----------------------------------------------------------------------
    // Tree-growing helpers (use world.setBlock so they cross chunk boundaries)
    // -----------------------------------------------------------------------

    /** Grow a standard oak tree at {@code (x, y, z)} (the sapling position). */
    static void growOakTree(World world, int x, int y, int z, Random rnd) {
        int trunkH = 4 + rnd.nextInt(3);
        if (!hasClearance(world, x, y, z, trunkH + 2)) return;
        world.setBlock(x, y, z, BlockType.AIR); // remove sapling
        for (int i = 0; i < trunkH; i++) {
            world.setBlock(x, y + i, z, BlockType.WOOD_LOG);
        }
        int canopyBase = y + trunkH - 2;
        for (int cy = 0; cy <= 2; cy++) {
            int radius = (cy == 2) ? 1 : 2;
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (dx == 0 && dz == 0 && cy < 2) continue;
                    if (Math.abs(dx) == 2 && Math.abs(dz) == 2) continue;
                    setLeafIfAir(world, x + dx, canopyBase + cy, z + dz, BlockType.LEAVES);
                }
            }
        }
        world.setBlock(x, y + trunkH, z, BlockType.LEAVES);
    }

    /** Grow a birch tree (slimmer, slightly taller than oak). */
    static void growBirchTree(World world, int x, int y, int z, Random rnd) {
        int trunkH = 5 + rnd.nextInt(3);
        if (!hasClearance(world, x, y, z, trunkH + 2)) return;
        world.setBlock(x, y, z, BlockType.AIR);
        for (int i = 0; i < trunkH; i++) {
            world.setBlock(x, y + i, z, BlockType.BIRCH_LOG);
        }
        int canopyBase = y + trunkH - 2;
        for (int cy = 0; cy <= 2; cy++) {
            int radius = (cy == 2) ? 1 : 2;
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (dx == 0 && dz == 0 && cy < 2) continue;
                    if (Math.abs(dx) == radius && Math.abs(dz) == radius && radius == 2) continue;
                    setLeafIfAir(world, x + dx, canopyBase + cy, z + dz, BlockType.BIRCH_LEAVES);
                }
            }
        }
        world.setBlock(x, y + trunkH, z, BlockType.BIRCH_LEAVES);
    }

    /**
     * Grow a cherry tree (short, wide pink blossom canopy).
     * Trunk uses {@code CHERRY_LOG}; canopy uses {@code CHERRY_LEAVES}.
     */
    static void growCherryTree(World world, int x, int y, int z, Random rnd) {
        int trunkH = 4 + rnd.nextInt(2);
        if (!hasClearance(world, x, y, z, trunkH + 2)) return;
        world.setBlock(x, y, z, BlockType.AIR); // remove sapling
        for (int i = 0; i < trunkH; i++) {
            world.setBlock(x, y + i, z, BlockType.CHERRY_LOG);
        }
        int canopyBase = y + trunkH - 2;
        for (int cy = 0; cy <= 2; cy++) {
            int radius = (cy == 2) ? 1 : 2;
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (dx == 0 && dz == 0 && cy < 2) continue;
                    if (Math.abs(dx) == radius && Math.abs(dz) == radius && radius == 2) continue;
                    setLeafIfAir(world, x + dx, canopyBase + cy, z + dz, BlockType.CHERRY_LEAVES);
                }
            }
        }
        world.setBlock(x, y + trunkH, z, BlockType.CHERRY_LEAVES);
    }

    /** Grow a pine/spruce tree (tall, conical). */
    static void growPineTree(World world, int x, int y, int z, Random rnd) {
        int trunkH = 8 + rnd.nextInt(5);
        if (!hasClearance(world, x, y, z, trunkH + 1)) return;
        world.setBlock(x, y, z, BlockType.AIR);
        for (int i = 0; i < trunkH; i++) {
            world.setBlock(x, y + i, z, BlockType.PINE_LOG);
        }
        // Conical canopy: wider at the bottom, narrowing to a point at top.
        for (int tier = 0; tier < 5; tier++) {
            int layerY = y + trunkH - 4 + tier;
            int radius = Math.max(0, 3 - tier);
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.abs(dx) == radius && Math.abs(dz) == radius && radius > 1) continue;
                    setLeafIfAir(world, x + dx, layerY, z + dz, BlockType.PINE_LEAVES);
                }
            }
        }
        world.setBlock(x, y + trunkH, z, BlockType.PINE_LEAVES);
    }

    /** Grow a small 1-trunk jungle tree. */
    static void growSmallJungleTree(World world, int x, int y, int z, Random rnd) {
        int trunkH = 8 + rnd.nextInt(5);
        if (!hasClearance(world, x, y, z, trunkH + 2)) return;
        world.setBlock(x, y, z, BlockType.AIR);
        for (int i = 0; i < trunkH; i++) {
            world.setBlock(x, y + i, z, BlockType.JUNGLE_LOG);
        }
        int canopyBase = y + trunkH - 2;
        for (int cy = 0; cy <= 2; cy++) {
            int radius = (cy == 2) ? 1 : 3;
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (dx == 0 && dz == 0 && cy < 2) continue;
                    if (Math.abs(dx) == radius && Math.abs(dz) == radius) continue;
                    setLeafIfAir(world, x + dx, canopyBase + cy, z + dz, BlockType.JUNGLE_LEAVES);
                }
            }
        }
        world.setBlock(x, y + trunkH, z, BlockType.JUNGLE_LEAVES);
    }

    /**
     * Grow a large 2×2-trunk jungle tree. {@code (bx, bz)} is the SW corner
     * of the 2×2 base; {@code y} is the first clear block above the ground.
     * {@code trunkH} must be pre-selected by the caller so the clearance
     * preflight in {@link #growJungleSapling} can validate the exact height.
     */
    static void growLargeJungleTree(World world, int bx, int y, int bz, int trunkH) {
        // Lay 2×2 trunk columns.
        for (int tx = 0; tx < 2; tx++) {
            for (int tz = 0; tz < 2; tz++) {
                for (int i = 0; i < trunkH; i++) {
                    world.setBlock(bx + tx, y + i, bz + tz, BlockType.JUNGLE_LOG);
                }
            }
        }
        // Wide spherical canopy near the top.
        // canopyRadius is fixed (no longer random) so the preflight height matches
        // exactly what we write here. 6 is the midpoint of the original 5–7 range.
        int topY = y + trunkH;
        int canopyRadius = 6;
        for (int dy = -canopyRadius / 2; dy <= canopyRadius / 2; dy++) {
            int sliceR = (int) Math.sqrt(canopyRadius * canopyRadius
                    - (double) dy * dy * 4); // flatten vertically
            sliceR = Math.min(sliceR, canopyRadius);
            for (int dx = -sliceR; dx <= sliceR + 1; dx++) {
                for (int dz = -sliceR; dz <= sliceR + 1; dz++) {
                    double dist = Math.sqrt(
                            (dx - 0.5) * (dx - 0.5) + (dz - 0.5) * (dz - 0.5));
                    if (dist <= sliceR + 0.5) {
                        setLeafIfAir(world, bx + dx, topY + dy, bz + dz,
                                BlockType.JUNGLE_LEAVES);
                    }
                }
            }
        }
        // Cap above canopy.
        for (int dx = 0; dx < 2; dx++) {
            for (int dz = 0; dz < 2; dz++) {
                world.setBlock(bx + dx, topY + 1, bz + dz, BlockType.JUNGLE_LEAVES);
            }
        }
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    /**
     * True if there are at least {@code needed} consecutive AIR blocks directly
     * above {@code (x, y, z)}, meaning growth won't clip into solid blocks.
     */
    private static boolean hasClearance(World world, int x, int y, int z, int needed) {
        if (y + needed >= Chunk.HEIGHT) return false; // would write beyond world top
        for (int i = 1; i <= needed; i++) {
            BlockType b = world.getBlock(x, y + i, z);
            if (b != null && b != BlockType.AIR && !b.isLeaves()) return false;
        }
        return true;
    }

    /** Place {@code leafType} only if the target cell is AIR (don't overwrite logs). */
    private static void setLeafIfAir(World world, int x, int y, int z, BlockType leafType) {
        BlockType existing = world.getBlock(x, y, z);
        if (existing == BlockType.AIR || existing == null) {
            world.setBlock(x, y, z, leafType);
        }
    }

    // -----------------------------------------------------------------------
    // Bone meal — sapling fast-grow
    // -----------------------------------------------------------------------

    /**
     * Extended bone-meal handler that also covers saplings and the new tree
     * leaf types. Called from the existing {@link #applyBonemeal} path via
     * the sapling check inside that method.
     */
    static boolean applyBonemealSapling(World world, int wx, int wy, int wz,
                                        BlockType sapling, Random rnd) {
        growSapling(world, wx, wy, wz, sapling, rnd);
        // If the sapling was replaced with a log or air, growth happened.
        BlockType after = world.getBlock(wx, wy, wz);
        return after != sapling;
    }

    @FunctionalInterface
    interface BlockGet {
        BlockType get(int x, int y, int z);
    }

    @FunctionalInterface
    interface BlockSet {
        void set(int x, int y, int z, BlockType type);
    }

    private Farming() {}
}
