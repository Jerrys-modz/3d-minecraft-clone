package com.minecraftclone.player;

import com.minecraftclone.world.BlockType;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Ore drop mappings for GTNH ores. When a GTNH ore block is broken,
 * it drops its corresponding crushed ore item instead of the block itself.
 * This allows for the multi-stage ore processing pipeline (ore → crushed → dust → ingot).
 */
public final class OreDrops {

    private static final Map<BlockType, BlockType> PRIMARY_DROPS = new EnumMap<>(BlockType.class);
    private static final Map<BlockType, BlockType> SECONDARY_DROPS = new EnumMap<>(BlockType.class);
    private static final Set<BlockType> VARIABLE_DROP_ORES = new HashSet<>();
    private static final Map<BlockType, BlockType> DROPS = new EnumMap<>(BlockType.class);

    static {
        // Early game ores (stone tier)
        registerOreDrop(BlockType.COPPER_ORE, BlockType.CRUSHED_COPPER, BlockType.CRUSHED_COPPER);
        registerOreDrop(BlockType.TIN_ORE, BlockType.CRUSHED_TIN, BlockType.CRUSHED_TIN);
        registerOreDrop(BlockType.BAUXITE_ORE, BlockType.CRUSHED_BAUXITE, BlockType.CRUSHED_BAUXITE);
        registerOreDrop(BlockType.ZINC_ORE, BlockType.CRUSHED_ZINC, BlockType.CRUSHED_ZINC);
        registerOreDrop(BlockType.LEAD_ORE, BlockType.CRUSHED_LEAD, BlockType.CRUSHED_LEAD);
        registerOreDrop(BlockType.SILVER_ORE, BlockType.CRUSHED_SILVER, BlockType.CRUSHED_SILVER);

        // Mid-game ores (iron tier)
        registerOreDrop(BlockType.NICKEL_ORE, BlockType.CRUSHED_NICKEL, BlockType.CRUSHED_NICKEL);
        registerOreDrop(BlockType.COBALT_ORE, BlockType.CRUSHED_COBALT, BlockType.CRUSHED_COBALT);
        registerOreDrop(BlockType.TUNGSTEN_ORE, BlockType.CRUSHED_TUNGSTEN, BlockType.CRUSHED_TUNGSTEN);
        registerOreDrop(BlockType.MOLYBDENUM_ORE, BlockType.CRUSHED_MOLYBDENUM, BlockType.CRUSHED_MOLYBDENUM);
        registerOreDrop(BlockType.PLATINUM_ORE, BlockType.CRUSHED_PLATINUM, BlockType.CRUSHED_PLATINUM);

        // Advanced ores (diamond tier)
        registerOreDrop(BlockType.CHROMIUM_ORE, BlockType.CRUSHED_CHROMIUM, BlockType.CRUSHED_CHROMIUM);
        registerOreDrop(BlockType.MANGANESE_ORE, BlockType.CRUSHED_MANGANESE, BlockType.CRUSHED_MANGANESE);
        registerOreDrop(BlockType.VANADIUM_ORE, BlockType.CRUSHED_VANADIUM, BlockType.CRUSHED_VANADIUM);
        registerOreDrop(BlockType.BERYLLIUM_ORE, BlockType.CRUSHED_BERYLLIUM, BlockType.CRUSHED_BERYLLIUM);
        registerOreDrop(BlockType.TITANIUM_ORE, BlockType.CRUSHED_TITANIUM, BlockType.CRUSHED_TITANIUM);

        // Late-game ores (endgame tier)
        registerOreDrop(BlockType.URANIUM_ORE, BlockType.CRUSHED_URANIUM, BlockType.CRUSHED_URANIUM);
        registerOreDrop(BlockType.THORIUM_ORE, BlockType.CRUSHED_THORIUM, BlockType.CRUSHED_THORIUM);
        registerOreDrop(BlockType.PLUTONIUM_ORE, BlockType.CRUSHED_PLUTONIUM, BlockType.CRUSHED_PLUTONIUM);
        registerOreDrop(BlockType.IRIDIUM_ORE, BlockType.CRUSHED_IRIDIUM, BlockType.CRUSHED_IRIDIUM);

        // Small ore variants (mining indicators) - primary 70%, secondary 30%
        // Early-game small ores
        registerVariableDrop(BlockType.SMALL_COPPER_ORE, BlockType.CRUSHED_COPPER, BlockType.CRUSHED_COPPER);
        registerVariableDrop(BlockType.SMALL_TIN_ORE, BlockType.CRUSHED_TIN, BlockType.CRUSHED_TIN);
        registerVariableDrop(BlockType.SMALL_BAUXITE_ORE, BlockType.CRUSHED_BAUXITE, BlockType.CRUSHED_BAUXITE);
        registerVariableDrop(BlockType.SMALL_ZINC_ORE, BlockType.CRUSHED_ZINC, BlockType.CRUSHED_ZINC);
        registerVariableDrop(BlockType.SMALL_LEAD_ORE, BlockType.CRUSHED_LEAD, BlockType.CRUSHED_LEAD);
        registerVariableDrop(BlockType.SMALL_SILVER_ORE, BlockType.CRUSHED_SILVER, BlockType.CRUSHED_SILVER);

        // Mid-game small ores
        registerVariableDrop(BlockType.SMALL_NICKEL_ORE, BlockType.CRUSHED_NICKEL, BlockType.CRUSHED_NICKEL);
        registerVariableDrop(BlockType.SMALL_COBALT_ORE, BlockType.CRUSHED_COBALT, BlockType.CRUSHED_COBALT);
        registerVariableDrop(BlockType.SMALL_TUNGSTEN_ORE, BlockType.CRUSHED_TUNGSTEN, BlockType.CRUSHED_TUNGSTEN);
        registerVariableDrop(BlockType.SMALL_MOLYBDENUM_ORE, BlockType.CRUSHED_MOLYBDENUM, BlockType.CRUSHED_MOLYBDENUM);
        registerVariableDrop(BlockType.SMALL_PLATINUM_ORE, BlockType.CRUSHED_PLATINUM, BlockType.CRUSHED_PLATINUM);

        // Advanced small ores
        registerVariableDrop(BlockType.SMALL_CHROMIUM_ORE, BlockType.CRUSHED_CHROMIUM, BlockType.CRUSHED_CHROMIUM);
        registerVariableDrop(BlockType.SMALL_MANGANESE_ORE, BlockType.CRUSHED_MANGANESE, BlockType.CRUSHED_MANGANESE);
        registerVariableDrop(BlockType.SMALL_VANADIUM_ORE, BlockType.CRUSHED_VANADIUM, BlockType.CRUSHED_VANADIUM);
        registerVariableDrop(BlockType.SMALL_BERYLLIUM_ORE, BlockType.CRUSHED_BERYLLIUM, BlockType.CRUSHED_BERYLLIUM);
        registerVariableDrop(BlockType.SMALL_TITANIUM_ORE, BlockType.CRUSHED_TITANIUM, BlockType.CRUSHED_TITANIUM);

        // Late-game small ores
        registerVariableDrop(BlockType.SMALL_URANIUM_ORE, BlockType.CRUSHED_URANIUM, BlockType.CRUSHED_URANIUM);
        registerVariableDrop(BlockType.SMALL_THORIUM_ORE, BlockType.CRUSHED_THORIUM, BlockType.CRUSHED_THORIUM);
        registerVariableDrop(BlockType.SMALL_PLUTONIUM_ORE, BlockType.CRUSHED_PLUTONIUM, BlockType.CRUSHED_PLUTONIUM);
        registerVariableDrop(BlockType.SMALL_IRIDIUM_ORE, BlockType.CRUSHED_IRIDIUM, BlockType.CRUSHED_IRIDIUM);
    }

    private OreDrops() {
    }

    /** Registers an ore block to drop a specific crushed ore item. */
    private static void registerOreDrop(BlockType ore, BlockType primary, BlockType secondary) {
        PRIMARY_DROPS.put(ore, primary);
        SECONDARY_DROPS.put(ore, secondary);
        DROPS.put(ore, primary);
    }

    /** Registers a small ore that can drop either primary or secondary drop. */
    private static void registerVariableDrop(BlockType ore, BlockType primary, BlockType secondary) {
        registerOreDrop(ore, primary, secondary);
        VARIABLE_DROP_ORES.add(ore);
    }

    /** Returns the item that should drop when this ore block is broken, or null if not a GTNH ore. */
    public static BlockType dropFor(BlockType ore) {
        return DROPS.get(ore);
    }

    /**
     * Returns the item that should drop when this ore block is broken, with random selection
     * for ores that have variable drops (small ores: 70% primary, 30% secondary).
     */
    public static BlockType dropForWithVariance(BlockType ore, Random rng) {
        if (!PRIMARY_DROPS.containsKey(ore)) {
            return null;
        }

        // Small ores have variable drops: 70% primary, 30% secondary
        if (VARIABLE_DROP_ORES.contains(ore) && rng.nextDouble() < 0.3) {
            return SECONDARY_DROPS.get(ore);
        }

        return PRIMARY_DROPS.get(ore);
    }

    /** True if this block is a GTNH ore that has a registered drop. */
    public static boolean isGthnOre(BlockType type) {
        return type != null && DROPS.containsKey(type);
    }
}
