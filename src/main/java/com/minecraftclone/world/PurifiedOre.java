package com.minecraftclone.world;

/**
 * Lookups for the {@code PURIFIED_*} ore items produced by the LV Ore Washer.
 * Each one mirrors a {@code CRUSHED_*} item of the same metal: it smelts to
 * the same ingot, grinds to the same dust, and its icon is the crushed-ore
 * icon with a washed sheen.
 */
public final class PurifiedOre {

    private PurifiedOre() {
    }

    private static final String PREFIX = "PURIFIED_";

    /** True for any {@code PURIFIED_*} item. */
    public static boolean isPurified(BlockType type) {
        return type != null && type.name().startsWith(PREFIX);
    }

    /** {@code PURIFIED_<metal>}, or null when that metal has no purified form. */
    public static BlockType forMetal(String metal) {
        return byName(PREFIX + metal);
    }

    /** The crushed ore a purified ore was washed from, or null. */
    public static BlockType crushedOf(BlockType purified) {
        return isPurified(purified) ? byName("CRUSHED_" + purified.name().substring(PREFIX.length())) : null;
    }

    private static BlockType byName(String name) {
        try {
            return BlockType.valueOf(name);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
