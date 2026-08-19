package com.minecraftclone.player;

import com.minecraftclone.world.BlockType;
import com.minecraftclone.world.Chunk;
import com.minecraftclone.world.World;

import java.util.Random;

/**
 * Farming system: crop growth, planting rules, and harvest drops.
 *
 * <p>Crops (wheat, potato, carrot) grow through multiple stages when planted
 * on {@link BlockType#FARMLAND}. Each game frame there is a small random chance
 * each crop in a radius around the player advances one stage. A mature crop
 * (final stage) is harvested by breaking it; it then drops the corresponding
 * food item (and extra seeds for wheat).
 *
 * <p>Sugar cane does not grow automatically yet - it is placeable as a
 * decoration block. Future work: add growth-tick logic to grow upward.
 *
 * <p>Farming interactions (hoe-on-dirt, seeds-on-farmland, canteen-at-water)
 * are wired in {@code Main}; this class only answers "what grows where" and
 * "what does breaking this crop produce?"
 */
public final class Farming {

    /**
     * Probability per game second that a crop in range advances one growth stage.
     * Average ~90 seconds per stage → ~6 minutes for wheat (3 stages), ~5 min for potato/carrot (2).
     */
    private static final float GROW_PROBABILITY_PER_SECOND = 1f / 90f;

    /** Block radius around the player to tick crops each frame. */
    private static final int TICK_RADIUS = 8;

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

    // -----------------------------------------------------------------------
    // Growth tick
    // -----------------------------------------------------------------------

    /**
     * Called each frame from the main game loop. Randomly advances crops
     * within {@link #TICK_RADIUS} blocks of the player's position. Only
     * the cells between {@code py - 4} and {@code py + 4} are scanned so
     * the loop stays cheap even when the player is at very high altitude.
     *
     * @param world  the world to query and mutate
     * @param px     player X (block coords)
     * @param py     player Y (block coords)
     * @param pz     player Z (block coords)
     * @param dt     frame delta-time in seconds
     * @param rnd    shared random (not seeded per tick - deliberately noisy)
     */
    public static void tickCropsNear(World world, int px, int py, int pz,
                                     float dt, Random rnd) {
        int yMin = Math.max(1, py - 4);
        int yMax = Math.min(Chunk.HEIGHT - 2, py + 4);
        float growChance = GROW_PROBABILITY_PER_SECOND * dt;

        for (int x = px - TICK_RADIUS; x <= px + TICK_RADIUS; x++) {
            for (int z = pz - TICK_RADIUS; z <= pz + TICK_RADIUS; z++) {
                for (int y = yMin; y <= yMax; y++) {
                    BlockType b = world.getBlock(x, y, z);
                    if (b == null || !isCrop(b)) continue;
                    // Require farmland directly below (sugar cane just sits on any block for now)
                    if (b != BlockType.SUGAR_CANE
                            && world.getBlock(x, y - 1, z) != BlockType.FARMLAND) {
                        // Farmland was replaced or broken - revert crop to air
                        world.setBlock(x, y, z, BlockType.AIR);
                        continue;
                    }
                    BlockType next = nextStage(b);
                    if (next != null && rnd.nextFloat() < growChance) {
                        world.setBlock(x, y, z, next);
                    }
                }
            }
        }
    }

    private Farming() {}
}
