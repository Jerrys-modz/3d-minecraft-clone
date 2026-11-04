package com.minecraftclone.world;

/**
 * Tiers of electric cable, GTNH-style: higher tiers carry more EU/t.
 * A network's effective throughput is set by its LOWEST-tier member,
 * just like steam pipes — one copper segment in an otherwise gold run
 * limits the whole line to copper rate.
 *
 * <p>Ordering is by ascending throughput (TIN first, GOLD last);
 * the weakest-link check compares {@code throughput} values rather than
 * ordinals so new tiers can be inserted freely in future.
 */
public enum CableTier {

    /** Starter tier: half the copper rate, lossiest. Crafted from tin ingots. */
    TIN(0.5f, Voltage.LV, 0.010f),

    /** Low-voltage tier: base EU/s rate. Crafted from copper ingots. */
    COPPER(1.0f, Voltage.LV, 0.005f),

    /**
     * Medium-voltage tier: 2× EU/s rate and the lowest loss, but MV — LV
     * machines on a gold network need an LV Transformer. Crafted from gold ingots.
     */
    GOLD(2.0f, Voltage.MV, 0.001f),

    /**
     * High-voltage tier: 4× EU/s rate and the lowest loss, but HV — MV machines
     * need an MV Transformer and LV machines an LV Transformer. Crafted from
     * aluminium ingots.
     */
    ALUMINIUM(4.0f, Voltage.HV, 0.0005f);

    /**
     * Voltage class a cable carries, in ascending order; a network runs at the
     * highest voltage of any cable in it.
     */
    public enum Voltage {
        LV, MV, HV;

        /** True if this voltage is above {@code rating}. */
        public boolean exceeds(Voltage rating) {
            return ordinal() > rating.ordinal();
        }
    }

    /** Fraction of base EU throughput this cable conducts (>0). */
    public final float throughput;

    /** Voltage class of this cable. */
    public final Voltage voltage;

    /** Fraction of energy lost per cable block travelled (see {@link EuNetwork}). */
    public final float lossPerBlock;

    CableTier(float throughput, Voltage voltage, float lossPerBlock) {
        this.throughput = throughput;
        this.voltage = voltage;
        this.lossPerBlock = lossPerBlock;
    }

    /** Returns the cable tier for a given block, or null if it is not a cable. */
    public static CableTier of(BlockType type) {
        if (type == BlockType.TIN_CABLE)    return TIN;
        if (type == BlockType.COPPER_CABLE) return COPPER;
        if (type == BlockType.GOLD_CABLE)   return GOLD;
        if (type == BlockType.ALUMINIUM_CABLE) return ALUMINIUM;
        return null;
    }

    /** True if the block is an electric cable of any tier. */
    public static boolean isCable(BlockType type) {
        return of(type) != null;
    }
}
