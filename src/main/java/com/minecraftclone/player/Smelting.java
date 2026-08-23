package com.minecraftclone.player;

import com.minecraftclone.world.BlockType;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Smelting recipes: turn raw ore into a refined ingot/gem in a furnace. This is
 * the recipe registry for placed furnaces (see {@code Furnace}), which smelt
 * over time rather than instantly - the old press-{@code C}-at-a-furnace
 * mechanic was replaced by the furnace GUI. Fuel (coal, wood, sticks - see
 * {@code Furnace#isFuel}) lives with the furnace itself; this class just
 * answers "what does this ore become?".
 */
public final class Smelting {

    private static final Map<BlockType, BlockType> OUTPUTS = new EnumMap<>(BlockType.class);

    static {
        // Add a smelting recipe with one line: ore -> refined output.
        smelt(BlockType.IRON_ORE, BlockType.IRON_INGOT);
        smelt(BlockType.GOLD_ORE, BlockType.GOLD_INGOT);
        smelt(BlockType.DIAMOND_ORE, BlockType.DIAMOND);
        smelt(BlockType.COAL_ORE, BlockType.COAL);
        smelt(BlockType.SAND, BlockType.GLASS);

        // GTNH ore chain: crushed ore → ingot (stage 2 of ore → crushed → ingot).
        // Vanilla-style ores with ingot counterparts.
        smelt(BlockType.CRUSHED_COPPER,    BlockType.COPPER_INGOT);
        smelt(BlockType.CRUSHED_TIN,       BlockType.TIN_INGOT);
        smelt(BlockType.CRUSHED_LEAD,      BlockType.LEAD_INGOT);
        smelt(BlockType.CRUSHED_SILVER,    BlockType.SILVER_INGOT);
        smelt(BlockType.CRUSHED_ZINC,      BlockType.ZINC_INGOT);
        smelt(BlockType.CRUSHED_NICKEL,    BlockType.NICKEL_INGOT);
        smelt(BlockType.CRUSHED_COBALT,    BlockType.COBALT_INGOT);
        smelt(BlockType.CRUSHED_TUNGSTEN,  BlockType.TUNGSTEN_INGOT);
        smelt(BlockType.CRUSHED_MOLYBDENUM,BlockType.MOLYBDENUM_INGOT);
        smelt(BlockType.CRUSHED_PLATINUM,  BlockType.PLATINUM_INGOT);
        smelt(BlockType.CRUSHED_CHROMIUM,  BlockType.CHROMIUM_INGOT);
        smelt(BlockType.CRUSHED_MANGANESE, BlockType.MANGANESE_INGOT);
        smelt(BlockType.CRUSHED_VANADIUM,  BlockType.VANADIUM_INGOT);
        smelt(BlockType.CRUSHED_BERYLLIUM, BlockType.BERYLLIUM_INGOT);
        smelt(BlockType.CRUSHED_TITANIUM,  BlockType.TITANIUM_INGOT);
        smelt(BlockType.CRUSHED_URANIUM,   BlockType.URANIUM_INGOT);
        smelt(BlockType.CRUSHED_THORIUM,   BlockType.THORIUM_INGOT);
        smelt(BlockType.CRUSHED_PLUTONIUM, BlockType.PLUTONIUM_INGOT);
        smelt(BlockType.CRUSHED_IRIDIUM,   BlockType.IRIDIUM_INGOT);
        smelt(BlockType.CRUSHED_BAUXITE,   BlockType.ALUMINUM_INGOT);

        // Extended GTNH ore pack — crushed ore → ingot (outputs limited to existing BlockType constants).
        // Iron-bearing minerals → IRON_INGOT
        smelt(BlockType.CRUSHED_MAGNETITE,          BlockType.IRON_INGOT);
        smelt(BlockType.CRUSHED_HEMATITE,           BlockType.IRON_INGOT);
        smelt(BlockType.CRUSHED_BROWN_LIMONITE,     BlockType.IRON_INGOT);
        smelt(BlockType.CRUSHED_YELLOW_LIMONITE,    BlockType.IRON_INGOT);
        smelt(BlockType.CRUSHED_BANDED_IRON,        BlockType.IRON_INGOT);
        smelt(BlockType.CRUSHED_VANADIUM_MAGNETITE, BlockType.VANADIUM_INGOT);
        smelt(BlockType.CRUSHED_PYRITE,             BlockType.IRON_INGOT);
        smelt(BlockType.CRUSHED_ARSENOPYRITE,       BlockType.IRON_INGOT);
        // Copper-bearing minerals → COPPER_INGOT
        smelt(BlockType.CRUSHED_CHALCOPYRITE,  BlockType.COPPER_INGOT);
        smelt(BlockType.CRUSHED_TETRAHEDRITE,  BlockType.COPPER_INGOT);
        smelt(BlockType.CRUSHED_MALACHITE,     BlockType.COPPER_INGOT);
        // Lead / Zinc
        smelt(BlockType.CRUSHED_GALENA,        BlockType.LEAD_INGOT);
        smelt(BlockType.CRUSHED_SPHALERITE,    BlockType.ZINC_INGOT);
        // Nickel / Cobalt
        smelt(BlockType.CRUSHED_GARNIERITE,    BlockType.NICKEL_INGOT);
        smelt(BlockType.CRUSHED_PENTLANDITE,   BlockType.NICKEL_INGOT);
        smelt(BlockType.CRUSHED_COBALTITE,     BlockType.COBALT_INGOT);
        // Tin / Tungsten / Chromium / Titanium
        smelt(BlockType.CRUSHED_CASSITERITE,   BlockType.TIN_INGOT);
        smelt(BlockType.CRUSHED_SCHEELITE,     BlockType.TUNGSTEN_INGOT);
        smelt(BlockType.CRUSHED_WOLFRAMITE,    BlockType.TUNGSTEN_INGOT);
        smelt(BlockType.CRUSHED_MOLYBDENITE,   BlockType.MOLYBDENUM_INGOT);
        smelt(BlockType.CRUSHED_FERBERITE,     BlockType.TUNGSTEN_INGOT);
        smelt(BlockType.CRUSHED_CHROMITE,      BlockType.CHROMIUM_INGOT);
        smelt(BlockType.CRUSHED_ILMENITE,      BlockType.TITANIUM_INGOT);
        smelt(BlockType.CRUSHED_RUTILE,        BlockType.TITANIUM_INGOT);
        // Uranium / Thorium
        smelt(BlockType.CRUSHED_URANINITE,     BlockType.URANIUM_INGOT);
        smelt(BlockType.CRUSHED_PITCHBLENDE,   BlockType.URANIUM_INGOT);
        // Vanadium / Manganese
        smelt(BlockType.CRUSHED_VANADINITE,    BlockType.VANADIUM_INGOT);
        smelt(BlockType.CRUSHED_PYROLUSITE,    BlockType.MANGANESE_INGOT);
        // Rare earth / PGM (mapped to the nearest existing precious metal)
        smelt(BlockType.CRUSHED_MONAZITE,          BlockType.PLATINUM_INGOT);
        smelt(BlockType.CRUSHED_BASTNASITE,         BlockType.PLATINUM_INGOT);
        smelt(BlockType.CRUSHED_NEODYMIUM,          BlockType.PLATINUM_INGOT);
        smelt(BlockType.CRUSHED_CERIUM,             BlockType.PLATINUM_INGOT);
        smelt(BlockType.CRUSHED_OSMIUM,             BlockType.IRIDIUM_INGOT);
        smelt(BlockType.CRUSHED_PALLADIUM,          BlockType.PLATINUM_INGOT);
        smelt(BlockType.CRUSHED_NAQUADAH,          BlockType.IRIDIUM_INGOT);
        smelt(BlockType.CRUSHED_NAQUADAH_ENRICHED, BlockType.IRIDIUM_INGOT);
        smelt(BlockType.CRUSHED_TRINIUM,           BlockType.IRIDIUM_INGOT);
        // Light metals / gemstone hosts (mapped to nearest existing ingot/gem)
        smelt(BlockType.CRUSHED_LEPIDOLITE,    BlockType.BERYLLIUM_INGOT);
        smelt(BlockType.CRUSHED_LITHIUM,       BlockType.BERYLLIUM_INGOT);
        smelt(BlockType.CRUSHED_CINNABAR,      BlockType.SILVER_INGOT); // mercury → silver (placeholder)
        // Gemstones
        smelt(BlockType.CRUSHED_RUBY,          BlockType.RUBY);
        smelt(BlockType.CRUSHED_SAPPHIRE,      BlockType.SAPPHIRE);
        smelt(BlockType.CRUSHED_GREEN_SAPPHIRE,BlockType.GREEN_SAPPHIRE);
        smelt(BlockType.CRUSHED_PYROPE,        BlockType.RUBY);        // garnet → ruby (closest gem)
        smelt(BlockType.CRUSHED_SPESSARTINE,   BlockType.RUBY);        // garnet → ruby
        // Non-metal minerals → COAL (gives back a fuel as a practical placeholder)
        smelt(BlockType.CRUSHED_SULFUR,        BlockType.COAL);
        smelt(BlockType.CRUSHED_GRAPHITE,      BlockType.COAL);
        smelt(BlockType.CRUSHED_CALCITE,       BlockType.COAL);
        smelt(BlockType.CRUSHED_OLIVINE,       BlockType.IRON_INGOT);   // magnesium-iron silicate → iron
        smelt(BlockType.CRUSHED_TALC,          BlockType.COAL);         // magnesium silicate, no metal
        smelt(BlockType.CRUSHED_BENTONITE,     BlockType.COAL);         // clay mineral, no metal
        smelt(BlockType.CRUSHED_SALT,          BlockType.COAL);         // NaCl, no smeltable metal
        smelt(BlockType.CRUSHED_ROCK_SALT,     BlockType.COAL);         // NaCl variant
        smelt(BlockType.CRUSHED_SALTPETER,     BlockType.COAL);         // KNO3, no metal
        smelt(BlockType.CRUSHED_BORAX,         BlockType.COAL);         // sodium borate, no metal
        smelt(BlockType.CRUSHED_APATITE,       BlockType.COAL);
        smelt(BlockType.CRUSHED_PHOSPHATE,     BlockType.COAL);
        smelt(BlockType.CRUSHED_PYROCHLORE,    BlockType.TUNGSTEN_INGOT); // niobium source → tungsten (placeholder)
        // Lapis minerals → SAPPHIRE (blue gem)
        smelt(BlockType.CRUSHED_SODALITE,      BlockType.SAPPHIRE);
        smelt(BlockType.CRUSHED_LAZURITE,      BlockType.SAPPHIRE);

        // Farming: raw potato baked into cooked potato.
        smelt(BlockType.POTATO, BlockType.POTATO_COOKED);
    }

    private Smelting() {
    }

    /** Registers a smelting recipe: one {@code ore} (plus fuel) yields one {@code output}. */
    private static void smelt(BlockType ore, BlockType output) {
        OUTPUTS.put(ore, output);
    }

    /** True if {@code ore} can be smelted (has a known output). */
    public static boolean isSmeltable(BlockType ore) {
        return OUTPUTS.containsKey(ore);
    }

    /** The block an ore smelts into, or null if it isn't smeltable. */
    public static BlockType outputFor(BlockType ore) {
        return OUTPUTS.get(ore);
    }

    /** Every furnace recipe as (input → output) pairs, in registration order. */
    public static List<SmeltingRecipe> allSmelting() {
        List<SmeltingRecipe> all = new ArrayList<>();
        for (Map.Entry<BlockType, BlockType> e : OUTPUTS.entrySet()) {
            all.add(new SmeltingRecipe(e.getKey(), e.getValue()));
        }
        return all;
    }

    /** One furnace recipe: an input item and what it smelts into. */
    public record SmeltingRecipe(BlockType input, BlockType output) {
    }
}
