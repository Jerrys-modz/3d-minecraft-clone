package com.minecraftclone.player;

import com.minecraftclone.world.BlockType;

import java.util.EnumMap;
import java.util.Map;

/**
 * Ore drop mappings for GTNH ores. When a GTNH ore block is broken,
 * it drops its corresponding crushed ore item instead of the block itself.
 * This allows for the multi-stage ore processing pipeline (ore → crushed → dust → ingot).
 */
public final class OreDrops {

    private static final Map<BlockType, BlockType> DROPS = new EnumMap<>(BlockType.class);

    static {
        // Early game ores (stone tier)
        registerOreDrop(BlockType.COPPER_ORE, BlockType.CRUSHED_COPPER);
        registerOreDrop(BlockType.TIN_ORE, BlockType.CRUSHED_TIN);
        registerOreDrop(BlockType.BAUXITE_ORE, BlockType.CRUSHED_BAUXITE);
        registerOreDrop(BlockType.ZINC_ORE, BlockType.CRUSHED_ZINC);
        registerOreDrop(BlockType.LEAD_ORE, BlockType.CRUSHED_LEAD);
        registerOreDrop(BlockType.SILVER_ORE, BlockType.CRUSHED_SILVER);

        // Mid-game ores (iron tier)
        registerOreDrop(BlockType.NICKEL_ORE, BlockType.CRUSHED_NICKEL);
        registerOreDrop(BlockType.COBALT_ORE, BlockType.CRUSHED_COBALT);
        registerOreDrop(BlockType.TUNGSTEN_ORE, BlockType.CRUSHED_TUNGSTEN);
        registerOreDrop(BlockType.MOLYBDENUM_ORE, BlockType.CRUSHED_MOLYBDENUM);
        registerOreDrop(BlockType.PLATINUM_ORE, BlockType.CRUSHED_PLATINUM);

        // Advanced ores (diamond tier)
        registerOreDrop(BlockType.CHROMIUM_ORE, BlockType.CRUSHED_CHROMIUM);
        registerOreDrop(BlockType.MANGANESE_ORE, BlockType.CRUSHED_MANGANESE);
        registerOreDrop(BlockType.VANADIUM_ORE, BlockType.CRUSHED_VANADIUM);
        registerOreDrop(BlockType.BERYLLIUM_ORE, BlockType.CRUSHED_BERYLLIUM);
        registerOreDrop(BlockType.TITANIUM_ORE, BlockType.CRUSHED_TITANIUM);

        // Late-game ores (endgame tier)
        registerOreDrop(BlockType.URANIUM_ORE, BlockType.CRUSHED_URANIUM);
        registerOreDrop(BlockType.THORIUM_ORE, BlockType.CRUSHED_THORIUM);
        registerOreDrop(BlockType.PLUTONIUM_ORE, BlockType.CRUSHED_PLUTONIUM);
        registerOreDrop(BlockType.IRIDIUM_ORE, BlockType.CRUSHED_IRIDIUM);
    }

    private OreDrops() {
    }

    /** Registers an ore block to drop a specific crushed ore item. */
    private static void registerOreDrop(BlockType ore, BlockType crushedOre) {
        DROPS.put(ore, crushedOre);
    }

    /** Returns the item that should drop when this ore block is broken, or null if not a GTNH ore. */
    public static BlockType dropFor(BlockType ore) {
        return DROPS.get(ore);
    }

    /** True if this block is a GTNH ore that has a registered drop. */
    public static boolean isGthnOre(BlockType type) {
        return type != null && DROPS.containsKey(type);
    }
}
