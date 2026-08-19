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
 * <em>random tick</em> system: every loaded chunk column is divided into
 * 16-block-tall sections, and each section receives {@value #RANDOM_TICK_SPEED}
 * random block picks per simulated game-tick (20 TPS). When a picked block is
 * a crop that still has a next stage, it advances. This matches vanilla
 * Minecraft's behaviour and means crops grow anywhere within the loaded world
 * (typically ~6-chunk radius = ~96 blocks), not just near the player.
 *
 * <p>Sugar cane does not grow automatically yet - it is placeable as a
 * decoration block. Future work: add upward growth logic here.
 *
 * <p>Farming interactions (hoe-on-dirt, seeds-on-farmland, canteen-at-water)
 * are wired in {@code Main}; this class only answers "what grows where" and
 * "what does breaking this crop produce?"
 */
public final class Farming {

    /**
     * Number of random block picks per chunk section (16×16×16) per game tick.
     * Vanilla Minecraft default is 3. Higher values make crops grow faster.
     */
    static final int RANDOM_TICK_SPEED = 3;

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
        for (int dz = -4; dz <= 4; dz++) {
            for (int dx = -4; dx <= 4; dx++) {
                for (int dy = 0; dy <= 1; dy++) {
                    BlockType b = world.getBlock(wx + dx, wy + dy, wz + dz);
                    if (b != null && b.isFluid() && !b.equals(BlockType.LAVA)
                            && !b.equals(BlockType.LAVA_SOURCE)
                            && !b.equals(BlockType.LAVA_FLOW)) {
                        return true;
                    }
                }
            }
        }
        return false;
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
            if (b != null && b.isFluid()
                    && b != BlockType.LAVA && b != BlockType.LAVA_SOURCE && b != BlockType.LAVA_FLOW) {
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
        // Stacking on another cane is always allowed as long as the column base is valid.
        if (below == BlockType.SUGAR_CANE) {
            // Walk down to the soil block.
            int baseY = wy - 1;
            while (baseY > 0 && world.getBlock(wx, baseY - 1, wz) == BlockType.SUGAR_CANE) baseY--;
            return isAdjacentToWater(world, wx, baseY - 1, wz);
        }
        // Soil block: must be dirt/grass/sand AND adjacent to water.
        boolean validSoil = below == BlockType.DIRT || below == BlockType.GRASS || below == BlockType.SAND;
        return validSoil && isAdjacentToWater(world, wx, wy - 1, wz);
    }

    /**
     * Returns the Y of the topmost SUGAR_CANE block in a column starting at (wx, wy, wz).
     * If the block at wy is not sugar cane, returns wy - 1 (signals "empty").
     */
    private static int sugarCaneTop(World world, int wx, int wy, int wz) {
        int y = wy;
        while (y + 1 < Chunk.HEIGHT && world.getBlock(wx, y + 1, wz) == BlockType.SUGAR_CANE) y++;
        return y;
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
     * <p>Range: all loaded chunks — typically the full render-distance radius
     * (default 6 chunks = 96 blocks in each direction), far larger than the
     * previous 8-block hardcoded radius.
     *
     * @param world the world to query and mutate
     * @param dt    frame delta-time in seconds
     * @param rnd   shared random (not freshly seeded — deliberate noise)
     */
    public static void tickCrops(World world, float dt, Random rnd) {
        // Convert wall-clock dt to simulated game-ticks (20 TPS = 0.05s per tick).
        // At typical 60 fps, dt ≈ 0.0167 → ~0.33 ticks. We handle the fractional
        // part probabilistically so the long-run rate is always correct.
        float ticksF = dt * 20f;
        int wholeTicks = (int) ticksF;
        float fracTick = ticksF - wholeTicks;

        int sectionsPerColumn = Chunk.HEIGHT / SECTION_HEIGHT;

        for (Chunk chunk : world.getLoadedChunks()) {
            int originX = chunk.getOriginX();
            int originZ = chunk.getOriginZ();

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
                        int topY = sugarCaneTop(world, wx, wy, wz);
                        for (int dropY = wy; dropY <= topY; dropY++) {
                            world.setBlock(wx, dropY, wz, BlockType.AIR);
                            world.spawnItem(wx, dropY, wz, BlockType.SUGAR_CANE, 1, rnd);
                        }
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
     * @deprecated Use {@link #tickCrops(World, float, Random)} instead.
     *             This player-radius variant is kept only to avoid breaking any
     *             direct call sites during the transition; it now delegates to
     *             the chunk-based implementation and ignores the position args.
     */
    @Deprecated
    public static void tickCropsNear(World world, int px, int py, int pz,
                                     float dt, Random rnd) {
        tickCrops(world, dt, rnd);
    }

    private Farming() {}
}
