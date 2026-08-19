package com.minecraftclone.player;

import com.minecraftclone.world.BlockType;

import java.util.*;

/**
 * Ore drop mappings for GTNH ores. When a GTNH ore block is broken,
 * it drops its corresponding crushed ore item (or for small ores, randomly
 * selects between crushed ore and impure pile).
 *
 * This allows for the multi-stage ore processing pipeline:
 * ore → crushed → dust → ingot (or impure → washed/centrifuged).
 */
public final class OreDrops {

    /** Maps ore to its primary drop (crushed ore). */
    private static final Map<BlockType, BlockType> PRIMARY_DROPS = new EnumMap<>(BlockType.class);

    /** Maps small ore to its secondary drop (impure pile). */
    private static final Map<BlockType, BlockType> SECONDARY_DROPS = new EnumMap<>(BlockType.class);

    /** Set of ore blocks that have variable drops (small ores). */
    private static final Set<BlockType> VARIABLE_DROP_ORES = new HashSet<>();

    static {
        // Early game ores (stone tier)
        registerOreDrop(BlockType.COPPER_ORE, BlockType.CRUSHED_COPPER, null);
        registerOreDrop(BlockType.TIN_ORE, BlockType.CRUSHED_TIN, null);
        registerOreDrop(BlockType.BAUXITE_ORE, BlockType.CRUSHED_BAUXITE, null);
        registerOreDrop(BlockType.ZINC_ORE, BlockType.CRUSHED_ZINC, null);
        registerOreDrop(BlockType.LEAD_ORE, BlockType.CRUSHED_LEAD, null);
        registerOreDrop(BlockType.SILVER_ORE, BlockType.CRUSHED_SILVER, null);

        // Mid-game ores (iron tier)
        registerOreDrop(BlockType.NICKEL_ORE, BlockType.CRUSHED_NICKEL, null);
        registerOreDrop(BlockType.COBALT_ORE, BlockType.CRUSHED_COBALT, null);
        registerOreDrop(BlockType.TUNGSTEN_ORE, BlockType.CRUSHED_TUNGSTEN, null);
        registerOreDrop(BlockType.MOLYBDENUM_ORE, BlockType.CRUSHED_MOLYBDENUM, null);
        registerOreDrop(BlockType.PLATINUM_ORE, BlockType.CRUSHED_PLATINUM, null);

        // Advanced ores (diamond tier)
        registerOreDrop(BlockType.CHROMIUM_ORE, BlockType.CRUSHED_CHROMIUM, null);
        registerOreDrop(BlockType.MANGANESE_ORE, BlockType.CRUSHED_MANGANESE, null);
        registerOreDrop(BlockType.VANADIUM_ORE, BlockType.CRUSHED_VANADIUM, null);
        registerOreDrop(BlockType.BERYLLIUM_ORE, BlockType.CRUSHED_BERYLLIUM, null);
        registerOreDrop(BlockType.TITANIUM_ORE, BlockType.CRUSHED_TITANIUM, null);

        // Late-game ores (endgame tier)
        registerOreDrop(BlockType.URANIUM_ORE, BlockType.CRUSHED_URANIUM, null);
        registerOreDrop(BlockType.THORIUM_ORE, BlockType.CRUSHED_THORIUM, null);
        registerOreDrop(BlockType.PLUTONIUM_ORE, BlockType.CRUSHED_PLUTONIUM, null);
        registerOreDrop(BlockType.IRIDIUM_ORE, BlockType.CRUSHED_IRIDIUM, null);

        // Small ore variants (mining indicators) - variable drops: crushed (70%) or impure (30%)
        // Early-game small ores
        registerOreDrop(BlockType.SMALL_COPPER_ORE, BlockType.CRUSHED_COPPER, BlockType.IMPURE_COPPER);
        registerOreDrop(BlockType.SMALL_TIN_ORE, BlockType.CRUSHED_TIN, BlockType.IMPURE_TIN);
        registerOreDrop(BlockType.SMALL_BAUXITE_ORE, BlockType.CRUSHED_BAUXITE, BlockType.IMPURE_BAUXITE);
        registerOreDrop(BlockType.SMALL_ZINC_ORE, BlockType.CRUSHED_ZINC, BlockType.IMPURE_ZINC);
        registerOreDrop(BlockType.SMALL_LEAD_ORE, BlockType.CRUSHED_LEAD, BlockType.IMPURE_LEAD);
        registerOreDrop(BlockType.SMALL_SILVER_ORE, BlockType.CRUSHED_SILVER, BlockType.IMPURE_SILVER);

        // Mid-game small ores
        registerOreDrop(BlockType.SMALL_NICKEL_ORE, BlockType.CRUSHED_NICKEL, BlockType.IMPURE_NICKEL);
        registerOreDrop(BlockType.SMALL_COBALT_ORE, BlockType.CRUSHED_COBALT, BlockType.IMPURE_COBALT);
        registerOreDrop(BlockType.SMALL_TUNGSTEN_ORE, BlockType.CRUSHED_TUNGSTEN, BlockType.IMPURE_TUNGSTEN);
        registerOreDrop(BlockType.SMALL_MOLYBDENUM_ORE, BlockType.CRUSHED_MOLYBDENUM, BlockType.IMPURE_MOLYBDENUM);
        registerOreDrop(BlockType.SMALL_PLATINUM_ORE, BlockType.CRUSHED_PLATINUM, BlockType.IMPURE_PLATINUM);

        // Advanced small ores (4 ores)
        registerOreDrop(BlockType.SMALL_CHROMIUM_ORE, BlockType.CRUSHED_CHROMIUM, BlockType.IMPURE_CHROMIUM);
        registerOreDrop(BlockType.SMALL_MANGANESE_ORE, BlockType.CRUSHED_MANGANESE, BlockType.IMPURE_MANGANESE);
        registerOreDrop(BlockType.SMALL_VANADIUM_ORE, BlockType.CRUSHED_VANADIUM, null); // Single drop only
        registerOreDrop(BlockType.SMALL_BERYLLIUM_ORE, BlockType.CRUSHED_BERYLLIUM, BlockType.IMPURE_BERYLLIUM);
        registerOreDrop(BlockType.SMALL_TITANIUM_ORE, BlockType.CRUSHED_TITANIUM, BlockType.IMPURE_TITANIUM);

        // Late-game small ores (3 ores)
        registerOreDrop(BlockType.SMALL_URANIUM_ORE, BlockType.CRUSHED_URANIUM, BlockType.IMPURE_URANIUM);
        registerOreDrop(BlockType.SMALL_THORIUM_ORE, BlockType.CRUSHED_THORIUM, null); // Single drop only
        registerOreDrop(BlockType.SMALL_PLUTONIUM_ORE, BlockType.CRUSHED_PLUTONIUM, BlockType.IMPURE_PLUTONIUM);
        registerOreDrop(BlockType.SMALL_IRIDIUM_ORE, BlockType.CRUSHED_IRIDIUM, BlockType.IMPURE_IRIDIUM);

        // =====================================================================
        // Extended GTNH ore pack — vein ore drops (primary: crushed ore)
        // =====================================================================
        // Iron chain
        registerOreDrop(BlockType.MAGNETITE_ORE,          BlockType.CRUSHED_MAGNETITE, null);
        registerOreDrop(BlockType.HEMATITE_ORE,           BlockType.CRUSHED_HEMATITE, null);
        registerOreDrop(BlockType.BROWN_LIMONITE_ORE,     BlockType.CRUSHED_BROWN_LIMONITE, null);
        registerOreDrop(BlockType.YELLOW_LIMONITE_ORE,    BlockType.CRUSHED_YELLOW_LIMONITE, null);
        registerOreDrop(BlockType.BANDED_IRON_ORE,        BlockType.CRUSHED_BANDED_IRON, null);
        registerOreDrop(BlockType.VANADIUM_MAGNETITE_ORE, BlockType.CRUSHED_VANADIUM_MAGNETITE, null);
        // Copper chain
        registerOreDrop(BlockType.CHALCOPYRITE_ORE,  BlockType.CRUSHED_CHALCOPYRITE, null);
        registerOreDrop(BlockType.TETRAHEDRITE_ORE,  BlockType.CRUSHED_TETRAHEDRITE, null);
        registerOreDrop(BlockType.MALACHITE_ORE,     BlockType.CRUSHED_MALACHITE, null);
        // Lead / Zinc
        registerOreDrop(BlockType.GALENA_ORE,        BlockType.CRUSHED_GALENA, null);
        registerOreDrop(BlockType.SPHALERITE_ORE,    BlockType.CRUSHED_SPHALERITE, null);
        // Nickel / Cobalt
        registerOreDrop(BlockType.GARNIERITE_ORE,    BlockType.CRUSHED_GARNIERITE, null);
        registerOreDrop(BlockType.PENTLANDITE_ORE,   BlockType.CRUSHED_PENTLANDITE, null);
        registerOreDrop(BlockType.COBALTITE_ORE,     BlockType.CRUSHED_COBALTITE, null);
        // Sulfur chain
        registerOreDrop(BlockType.PYRITE_ORE,        BlockType.CRUSHED_PYRITE, null);
        registerOreDrop(BlockType.ARSENOPYRITE_ORE,  BlockType.CRUSHED_ARSENOPYRITE, null);
        registerOreDrop(BlockType.SULFUR_ORE,        BlockType.CRUSHED_SULFUR, null);
        registerOreDrop(BlockType.CINNABAR_ORE,      BlockType.CRUSHED_CINNABAR, null);
        // Tin
        registerOreDrop(BlockType.CASSITERITE_ORE,   BlockType.CRUSHED_CASSITERITE, null);
        // Tungsten family
        registerOreDrop(BlockType.SCHEELITE_ORE,     BlockType.CRUSHED_SCHEELITE, null);
        registerOreDrop(BlockType.WOLFRAMITE_ORE,    BlockType.CRUSHED_WOLFRAMITE, null);
        registerOreDrop(BlockType.MOLYBDENITE_ORE,   BlockType.CRUSHED_MOLYBDENITE, null);
        registerOreDrop(BlockType.FERBERITE_ORE,     BlockType.CRUSHED_FERBERITE, null);
        // Chromium / Titanium
        registerOreDrop(BlockType.CHROMITE_ORE,      BlockType.CRUSHED_CHROMITE, null);
        registerOreDrop(BlockType.ILMENITE_ORE,      BlockType.CRUSHED_ILMENITE, null);
        registerOreDrop(BlockType.RUTILE_ORE,        BlockType.CRUSHED_RUTILE, null);
        // Uranium family
        registerOreDrop(BlockType.URANINITE_ORE,     BlockType.CRUSHED_URANINITE, null);
        registerOreDrop(BlockType.PITCHBLENDE_ORE,   BlockType.CRUSHED_PITCHBLENDE, null);
        // Rare earth
        registerOreDrop(BlockType.MONAZITE_ORE,      BlockType.CRUSHED_MONAZITE, null);
        registerOreDrop(BlockType.BASTNASITE_ORE,    BlockType.CRUSHED_BASTNASITE, null);
        // Vanadium / Manganese minerals
        registerOreDrop(BlockType.VANADINITE_ORE,    BlockType.CRUSHED_VANADINITE, null);
        registerOreDrop(BlockType.PYROLUSITE_ORE,    BlockType.CRUSHED_PYROLUSITE, null);
        // Carbon
        registerOreDrop(BlockType.GRAPHITE_ORE,      BlockType.CRUSHED_GRAPHITE, null);
        // Light metals
        registerOreDrop(BlockType.LITHIUM_ORE,       BlockType.CRUSHED_LITHIUM, null);
        // GTNH exotics
        registerOreDrop(BlockType.NAQUADAH_ORE,          BlockType.CRUSHED_NAQUADAH, null);
        registerOreDrop(BlockType.NAQUADAH_ENRICHED_ORE, BlockType.CRUSHED_NAQUADAH_ENRICHED, null);
        registerOreDrop(BlockType.TRINIUM_ORE,           BlockType.CRUSHED_TRINIUM, null);
        // Additional rare earths / PGM
        registerOreDrop(BlockType.NEODYMIUM_ORE,     BlockType.CRUSHED_NEODYMIUM, null);
        registerOreDrop(BlockType.CERIUM_ORE,        BlockType.CRUSHED_CERIUM, null);
        registerOreDrop(BlockType.OSMIUM_ORE,        BlockType.CRUSHED_OSMIUM, null);
        registerOreDrop(BlockType.PALLADIUM_ORE,     BlockType.CRUSHED_PALLADIUM, null);
        // Rock/mineral ores
        registerOreDrop(BlockType.CALCITE_ORE,       BlockType.CRUSHED_CALCITE, null);
        registerOreDrop(BlockType.OLIVINE_ORE,       BlockType.CRUSHED_OLIVINE, null);
        registerOreDrop(BlockType.TALC_ORE,          BlockType.CRUSHED_TALC, null);
        registerOreDrop(BlockType.BENTONITE_ORE,     BlockType.CRUSHED_BENTONITE, null);
        // Lapis components
        registerOreDrop(BlockType.SODALITE_ORE,      BlockType.CRUSHED_SODALITE, null);
        registerOreDrop(BlockType.LAZURITE_ORE,      BlockType.CRUSHED_LAZURITE, null);
        // Evaporites
        registerOreDrop(BlockType.SALT_ORE,          BlockType.CRUSHED_SALT, null);
        registerOreDrop(BlockType.ROCK_SALT_ORE,     BlockType.CRUSHED_ROCK_SALT, null);
        registerOreDrop(BlockType.SALTPETER_ORE,     BlockType.CRUSHED_SALTPETER, null);
        registerOreDrop(BlockType.BORAX_ORE,         BlockType.CRUSHED_BORAX, null);
        // Phosphates
        registerOreDrop(BlockType.APATITE_ORE,       BlockType.CRUSHED_APATITE, null);
        registerOreDrop(BlockType.PHOSPHATE_ORE,     BlockType.CRUSHED_PHOSPHATE, null);
        registerOreDrop(BlockType.PYROCHLORE_ORE,    BlockType.CRUSHED_PYROCHLORE, null);
        // Gemstones
        registerOreDrop(BlockType.LEPIDOLITE_ORE,    BlockType.CRUSHED_LEPIDOLITE, null);
        registerOreDrop(BlockType.RUBY_ORE,          BlockType.CRUSHED_RUBY, null);
        registerOreDrop(BlockType.SAPPHIRE_ORE,      BlockType.CRUSHED_SAPPHIRE, null);
        registerOreDrop(BlockType.GREEN_SAPPHIRE_ORE,BlockType.CRUSHED_GREEN_SAPPHIRE, null);
        registerOreDrop(BlockType.PYROPE_ORE,        BlockType.CRUSHED_PYROPE, null);
        registerOreDrop(BlockType.SPESSARTINE_ORE,   BlockType.CRUSHED_SPESSARTINE, null);

        // Small ore variants (variable drops: 70% crushed, 30% crushed again = single drop for now)
        // Iron-chain small ores
        registerOreDrop(BlockType.SMALL_MAGNETITE_ORE,          BlockType.CRUSHED_MAGNETITE, null);
        registerOreDrop(BlockType.SMALL_HEMATITE_ORE,           BlockType.CRUSHED_HEMATITE, null);
        registerOreDrop(BlockType.SMALL_BROWN_LIMONITE_ORE,     BlockType.CRUSHED_BROWN_LIMONITE, null);
        registerOreDrop(BlockType.SMALL_YELLOW_LIMONITE_ORE,    BlockType.CRUSHED_YELLOW_LIMONITE, null);
        registerOreDrop(BlockType.SMALL_BANDED_IRON_ORE,        BlockType.CRUSHED_BANDED_IRON, null);
        registerOreDrop(BlockType.SMALL_VANADIUM_MAGNETITE_ORE, BlockType.CRUSHED_VANADIUM_MAGNETITE, null);
        // Copper-chain small ores
        registerOreDrop(BlockType.SMALL_CHALCOPYRITE_ORE,  BlockType.CRUSHED_CHALCOPYRITE, null);
        registerOreDrop(BlockType.SMALL_TETRAHEDRITE_ORE,  BlockType.CRUSHED_TETRAHEDRITE, null);
        registerOreDrop(BlockType.SMALL_MALACHITE_ORE,     BlockType.CRUSHED_MALACHITE, null);
        // Lead / Zinc small ores
        registerOreDrop(BlockType.SMALL_GALENA_ORE,        BlockType.CRUSHED_GALENA, null);
        registerOreDrop(BlockType.SMALL_SPHALERITE_ORE,    BlockType.CRUSHED_SPHALERITE, null);
        // Nickel / Cobalt small ores
        registerOreDrop(BlockType.SMALL_GARNIERITE_ORE,    BlockType.CRUSHED_GARNIERITE, null);
        registerOreDrop(BlockType.SMALL_PENTLANDITE_ORE,   BlockType.CRUSHED_PENTLANDITE, null);
        registerOreDrop(BlockType.SMALL_COBALTITE_ORE,     BlockType.CRUSHED_COBALTITE, null);
        // Sulfur-chain small ores
        registerOreDrop(BlockType.SMALL_PYRITE_ORE,        BlockType.CRUSHED_PYRITE, null);
        registerOreDrop(BlockType.SMALL_ARSENOPYRITE_ORE,  BlockType.CRUSHED_ARSENOPYRITE, null);
        registerOreDrop(BlockType.SMALL_SULFUR_ORE,        BlockType.CRUSHED_SULFUR, null);
        registerOreDrop(BlockType.SMALL_CINNABAR_ORE,      BlockType.CRUSHED_CINNABAR, null);
        registerOreDrop(BlockType.SMALL_CASSITERITE_ORE,   BlockType.CRUSHED_CASSITERITE, null);
        // Tungsten/Chromium small ores
        registerOreDrop(BlockType.SMALL_SCHEELITE_ORE,     BlockType.CRUSHED_SCHEELITE, null);
        registerOreDrop(BlockType.SMALL_WOLFRAMITE_ORE,    BlockType.CRUSHED_WOLFRAMITE, null);
        registerOreDrop(BlockType.SMALL_MOLYBDENITE_ORE,   BlockType.CRUSHED_MOLYBDENITE, null);
        registerOreDrop(BlockType.SMALL_CHROMITE_ORE,      BlockType.CRUSHED_CHROMITE, null);
        registerOreDrop(BlockType.SMALL_ILMENITE_ORE,      BlockType.CRUSHED_ILMENITE, null);
        registerOreDrop(BlockType.SMALL_RUTILE_ORE,        BlockType.CRUSHED_RUTILE, null);
        // Uranium/Rare earth small ores
        registerOreDrop(BlockType.SMALL_URANINITE_ORE,     BlockType.CRUSHED_URANINITE, null);
        registerOreDrop(BlockType.SMALL_PITCHBLENDE_ORE,   BlockType.CRUSHED_PITCHBLENDE, null);
        registerOreDrop(BlockType.SMALL_MONAZITE_ORE,      BlockType.CRUSHED_MONAZITE, null);
        // GTNH exotic small ores
        registerOreDrop(BlockType.SMALL_NAQUADAH_ORE,      BlockType.CRUSHED_NAQUADAH, null);
        registerOreDrop(BlockType.SMALL_TRINIUM_ORE,       BlockType.CRUSHED_TRINIUM, null);
        // PGM/Gemstone small ores
        registerOreDrop(BlockType.SMALL_OSMIUM_ORE,        BlockType.CRUSHED_OSMIUM, null);
        registerOreDrop(BlockType.SMALL_PALLADIUM_ORE,     BlockType.CRUSHED_PALLADIUM, null);
        registerOreDrop(BlockType.SMALL_RUBY_ORE,          BlockType.CRUSHED_RUBY, null);
        // Note: SMALL_SAPPHIRE_ORE, SMALL_GREEN_SAPPHIRE_ORE, SMALL_SODALITE_ORE, SMALL_LAZURITE_ORE
        // not yet added to BlockType — only the vein forms exist.
        registerOreDrop(BlockType.SMALL_NEODYMIUM_ORE,     BlockType.CRUSHED_NEODYMIUM, null);
        registerOreDrop(BlockType.SMALL_CERIUM_ORE,        BlockType.CRUSHED_CERIUM, null);
        registerOreDrop(BlockType.SMALL_PYROLUSITE_ORE,    BlockType.CRUSHED_PYROLUSITE, null);
        registerOreDrop(BlockType.SMALL_GRAPHITE_ORE,      BlockType.CRUSHED_GRAPHITE, null);
        registerOreDrop(BlockType.SMALL_LITHIUM_ORE,       BlockType.CRUSHED_LITHIUM, null);
        registerOreDrop(BlockType.SMALL_VANADINITE_ORE,    BlockType.CRUSHED_VANADINITE, null);
        registerOreDrop(BlockType.SMALL_BASTNASITE_ORE,    BlockType.CRUSHED_BASTNASITE, null);
    }

    private OreDrops() {
    }

    /** Registers an ore block with primary and optional secondary (variable) drops. */
    private static void registerOreDrop(BlockType ore, BlockType primary, BlockType secondary) {
        PRIMARY_DROPS.put(ore, primary);
        if (secondary != null) {
            SECONDARY_DROPS.put(ore, secondary);
            VARIABLE_DROP_ORES.add(ore);
        }
    }

    /** Returns the item that should drop when this ore block is broken, or null if not a GTNH ore. */
    public static BlockType dropFor(BlockType ore) {
        return PRIMARY_DROPS.get(ore);
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
        return type != null && PRIMARY_DROPS.containsKey(type);
    }
}
