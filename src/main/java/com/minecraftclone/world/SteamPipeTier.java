package com.minecraftclone.world;

/**
 * Tiers of steam pipe, GTNH-style: higher tiers cost more but conduct steam
 * faster. A network's effective rate is set by its LOWEST tier member - one
 * wooden segment downgrades a whole bronze run.
 *
 * <p>Ordering is by ascending throughput (WOOD first, IRON last); the
 * weakest-link check in {@code PipeNetworkManager} compares throughput
 * values rather than ordinals, so new tiers can be inserted at any position.
 */
public enum SteamPipeTier {

    /** Starter tier: half-rate steam flow. Crafted from planks. */
    WOOD(0.5f),

    /** Standard Steam Age tier: full rate. Crafted from bronze ingots. */
    BRONZE(1f),

    /** High-pressure tier: 1.5x rate. Crafted from iron ingots. */
    IRON(1.5f),

    /** Top Steam Age tier: 2x rate. Crafted from steel ingots. */
    STEEL(2f);

    /** Fraction of normal steam throughput this tier conducts (>0). */
    public final float throughput;

    SteamPipeTier(float throughput) {
        this.throughput = throughput;
    }

    /** The tier of a pipe block, or null for non-pipes. */
    public static SteamPipeTier of(BlockType type) {
        if (type == BlockType.STEAM_PIPE_WOOD) return WOOD;
        if (type == BlockType.STEAM_PIPE_BRONZE) return BRONZE;
        if (type == BlockType.STEAM_PIPE_IRON) return IRON;
        if (type == BlockType.STEAM_PIPE_STEEL) return STEEL;
        return null;
    }

    /** True if the block is a steam pipe of any tier. */
    public static boolean isSteamPipe(BlockType type) {
        return of(type) != null;
    }
}
