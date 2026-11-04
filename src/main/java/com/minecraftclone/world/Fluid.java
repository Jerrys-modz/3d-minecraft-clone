package com.minecraftclone.world;

/**
 * A fluid that can be stored in tanks and cells and moved through fluid pipes.
 * Amounts are in millibuckets (mB): one bucket / one cell = 1000 mB.
 */
public enum Fluid {
    WATER("Water"),
    LAVA("Lava"),
    /** H2SO4 — made in the Chemical Reactor from sulfur + water; used for acid ore leaching. */
    SULFURIC_ACID("Sulfuric Acid"),
    /** Crude oil — pumped from underground deposits by the Oil Pump. */
    OIL("Crude Oil"),
    /** Diesel — the light fraction from the Distillation Tower; the best generator fuel. */
    DIESEL("Diesel"),
    /** Heavy fuel — the heavy fraction from the Distillation Tower. */
    HEAVY_FUEL("Heavy Fuel"),
    /** HCl — electrolysed from salt brine; the chloride route to titanium. Stainless pipe only. */
    HYDROCHLORIC_ACID("Hydrochloric Acid");

    public final String displayName;

    Fluid(String displayName) {
        this.displayName = displayName;
    }

    /** One cell / one bucket. */
    public static final int CELL_MB = 1000;

    /** The filled tin cell for {@code fluid}. */
    public static BlockType cellFor(Fluid fluid) {
        return switch (fluid) {
            case WATER -> BlockType.WATER_CELL;
            case LAVA -> BlockType.LAVA_CELL;
            case SULFURIC_ACID -> BlockType.SULFURIC_ACID_CELL;
            case OIL -> BlockType.OIL_CELL;
            case DIESEL -> BlockType.DIESEL_CELL;
            case HEAVY_FUEL -> BlockType.HEAVY_FUEL_CELL;
            case HYDROCHLORIC_ACID -> BlockType.HYDROCHLORIC_ACID_CELL;
        };
    }

    /** The fluid a filled cell holds, or null for the empty cell / non-cells. */
    public static Fluid inCell(BlockType item) {
        if (item == BlockType.WATER_CELL) return WATER;
        if (item == BlockType.LAVA_CELL) return LAVA;
        if (item == BlockType.SULFURIC_ACID_CELL) return SULFURIC_ACID;
        if (item == BlockType.OIL_CELL) return OIL;
        if (item == BlockType.DIESEL_CELL) return DIESEL;
        if (item == BlockType.HEAVY_FUEL_CELL) return HEAVY_FUEL;
        if (item == BlockType.HYDROCHLORIC_ACID_CELL) return HYDROCHLORIC_ACID;
        return null;
    }

    /** True for the empty or any filled tin cell. */
    public static boolean isCell(BlockType item) {
        return item == BlockType.TIN_CELL || inCell(item) != null;
    }

    /**
     * Seconds of Coal Generator burn per 100 mB of this fluid, or 0 if it
     * isn't a fuel. Refining pays: 1000 mB of crude is 400 s, but its 600 mB
     * diesel + 300 mB heavy fuel are 960 s + 180 s.
     */
    public float burnSecondsPer100mB() {
        return switch (this) {
            case DIESEL -> 160f;
            case LAVA -> 80f;
            case HEAVY_FUEL -> 60f;
            case OIL -> 40f;
            default -> 0f;
        };
    }

    /** The fluid a world block can be scooped as, or null. */
    public static Fluid ofBlock(BlockType block) {
        if (block == null) return null;
        if (block.isWater()) return WATER;
        if (block.isLava()) return LAVA;
        return null;
    }
}
