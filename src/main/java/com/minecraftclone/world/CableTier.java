package com.minecraftclone.world;

/**
 * Tiers of electric cable, GTNH-style: higher tiers carry more EU/t.
 * A network's effective throughput is set by its LOWEST-tier member,
 * just like steam pipes — one copper segment in an otherwise gold run
 * limits the whole line to copper rate.
 *
 * <p>Ordering is by ascending throughput (COPPER first, GOLD last);
 * the weakest-link check compares {@code throughput} values rather than
 * ordinals so new tiers can be inserted freely in future.
 */
public enum CableTier {

    /** Low-voltage tier: base EU/s rate. Crafted from copper ingots. */
    COPPER(1.0f),

    /** Medium-voltage tier: 2× EU/s rate. Crafted from gold ingots. */
    GOLD(2.0f);

    /** Fraction of base EU throughput this cable conducts (>0). */
    public final float throughput;

    CableTier(float throughput) {
        this.throughput = throughput;
    }

    /** Returns the cable tier for a given block, or null if it is not a cable. */
    public static CableTier of(BlockType type) {
        if (type == BlockType.COPPER_CABLE) return COPPER;
        if (type == BlockType.GOLD_CABLE)   return GOLD;
        return null;
    }

    /** True if the block is an electric cable of any tier. */
    public static boolean isCable(BlockType type) {
        return of(type) != null;
    }
}
