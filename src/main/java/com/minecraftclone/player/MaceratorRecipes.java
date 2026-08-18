package com.minecraftclone.player;

import com.minecraftclone.world.BlockType;

import java.util.EnumMap;
import java.util.Map;

/**
 * Macerator recipes: turn ore blocks into crushed ore. This is the recipe
 * registry for the Macerator machine (see {@code Macerator}).
 */
public final class MaceratorRecipes {

    private static final Map<BlockType, BlockType> RECIPES = new EnumMap<>(BlockType.class);

    static {
        // Early game ores
        recipe(BlockType.COPPER_ORE, BlockType.CRUSHED_COPPER);
        recipe(BlockType.TIN_ORE, BlockType.CRUSHED_TIN);
        recipe(BlockType.ZINC_ORE, BlockType.CRUSHED_ZINC);
        recipe(BlockType.LEAD_ORE, BlockType.CRUSHED_LEAD);
        recipe(BlockType.SILVER_ORE, BlockType.CRUSHED_SILVER);
        recipe(BlockType.BAUXITE_ORE, BlockType.CRUSHED_BAUXITE);

        // Mid game ores
        recipe(BlockType.GOLD_ORE, BlockType.CRUSHED_GOLD);
        recipe(BlockType.IRON_ORE, BlockType.CRUSHED_IRON);
        recipe(BlockType.NICKEL_ORE, BlockType.CRUSHED_NICKEL);
        recipe(BlockType.COBALT_ORE, BlockType.CRUSHED_COBALT);
        recipe(BlockType.TUNGSTEN_ORE, BlockType.CRUSHED_TUNGSTEN);

        // Late game ores
        recipe(BlockType.PLATINUM_ORE, BlockType.CRUSHED_PLATINUM);
        recipe(BlockType.IRIDIUM_ORE, BlockType.CRUSHED_IRIDIUM);
        recipe(BlockType.MANGANESE_ORE, BlockType.CRUSHED_MANGANESE);
        recipe(BlockType.DIAMOND_ORE, BlockType.CRUSHED_DIAMOND);

        // Vanilla ores
        recipe(BlockType.COAL_ORE, BlockType.COAL);
    }

    private MaceratorRecipes() {
    }

    /** Registers a macerator recipe: one ore block yields one crushed ore. */
    private static void recipe(BlockType ore, BlockType crushed) {
        RECIPES.put(ore, crushed);
    }

    /** True if {@code ore} can be macerated (has a known output). */
    public static boolean isSmeltable(BlockType ore) {
        return RECIPES.containsKey(ore);
    }

    /** The crushed ore produced from maceration, or null if it isn't smeltable. */
    public static BlockType outputFor(BlockType ore) {
        return RECIPES.get(ore);
    }
}
