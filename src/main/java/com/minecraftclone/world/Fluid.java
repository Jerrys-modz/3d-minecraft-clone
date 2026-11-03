package com.minecraftclone.world;

/**
 * A fluid that can be stored in tanks and cells and moved through fluid pipes.
 * Amounts are in millibuckets (mB): one bucket / one cell = 1000 mB.
 */
public enum Fluid {
    WATER("Water"),
    LAVA("Lava"),
    /** H2SO4 — made in the Chemical Reactor from sulfur + water; used for acid ore leaching. */
    SULFURIC_ACID("Sulfuric Acid");

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
        };
    }

    /** The fluid a filled cell holds, or null for the empty cell / non-cells. */
    public static Fluid inCell(BlockType item) {
        if (item == BlockType.WATER_CELL) return WATER;
        if (item == BlockType.LAVA_CELL) return LAVA;
        if (item == BlockType.SULFURIC_ACID_CELL) return SULFURIC_ACID;
        return null;
    }

    /** True for the empty or any filled tin cell. */
    public static boolean isCell(BlockType item) {
        return item == BlockType.TIN_CELL || inCell(item) != null;
    }

    /** The fluid a world block can be scooped as, or null. */
    public static Fluid ofBlock(BlockType block) {
        if (block == null) return null;
        if (block.isWater()) return WATER;
        if (block.isLava()) return LAVA;
        return null;
    }
}
