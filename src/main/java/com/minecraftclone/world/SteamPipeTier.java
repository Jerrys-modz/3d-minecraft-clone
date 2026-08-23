package com.minecraftclone.world;

/**
 * Tiers of steam pipe, GTNH-style: each tier is cheaper to build but
 * throttles the steam flowing through it. A network's effective rate is set
 * by its LOWEST tier member - one wooden segment downgrades a whole bronze
 * run.
 */
public enum SteamPipeTier {

    /** Starter tier: half-rate steam flow. Crafted from planks. */
    WOOD(0.5f),

    /** Standard Steam Age tier: full rate. Crafted from bronze ingots. */
    BRONZE(1f);

    /** Fraction of normal steam throughput this tier conducts (0..1]. */
    public final float throughput;

    SteamPipeTier(float throughput) {
        this.throughput = throughput;
    }

    /** The tier of a pipe block, or null for non-pipes. */
    public static SteamPipeTier of(BlockType type) {
        if (type == BlockType.STEAM_PIPE_WOOD) return WOOD;
        if (type == BlockType.STEAM_PIPE_BRONZE) return BRONZE;
        return null;
    }

    /** True if the block is a steam pipe of any tier. */
    public static boolean isSteamPipe(BlockType type) {
        return of(type) != null;
    }
}
