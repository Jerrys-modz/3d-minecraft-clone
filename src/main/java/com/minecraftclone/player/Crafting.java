package com.minecraftclone.player;

import com.minecraftclone.world.BlockType;

import java.util.EnumMap;
import java.util.Map;

/**
 * A small, fixed recipe table: each recipe converts some amount of one or
 * two raw materials into some amount of a refined block. There's no
 * crafting-grid UI - press 'C' with the desired output selected in the
 * hotbar, and if you have enough of its input(s), it's crafted directly
 * into your inventory.
 */
public final class Crafting {

    /** {@code input2} is null for single-ingredient recipes; {@code amount2} is meaningless when it is. */
    public record Recipe(BlockType input1, int amount1, BlockType input2, int amount2, BlockType output, int outputAmount) {
    }

    private static final Map<BlockType, Recipe> BY_OUTPUT = new EnumMap<>(BlockType.class);

    static {
        register(new Recipe(BlockType.WOOD_LOG, 1, null, 0, BlockType.PLANKS, 4));
        register(new Recipe(BlockType.SAND, 2, null, 0, BlockType.GLASS, 1));
        register(new Recipe(BlockType.PLANKS, 2, null, 0, BlockType.STICK, 4));
        register(new Recipe(BlockType.STONE, 3, null, 0, BlockType.STONE_SLAB, 6));
        register(new Recipe(BlockType.PLANKS, 3, null, 0, BlockType.PLANKS_SLAB, 6));
        register(new Recipe(BlockType.STICK, 1, BlockType.COAL_ORE, 1, BlockType.TORCH, 4));

        register(new Recipe(BlockType.PLANKS, 3, BlockType.STICK, 2, BlockType.WOOD_PICKAXE, 1));
        register(new Recipe(BlockType.STONE, 3, BlockType.STICK, 2, BlockType.STONE_PICKAXE, 1));
        register(new Recipe(BlockType.IRON_ORE, 3, BlockType.STICK, 2, BlockType.IRON_PICKAXE, 1));
        register(new Recipe(BlockType.DIAMOND_ORE, 3, BlockType.STICK, 2, BlockType.DIAMOND_PICKAXE, 1));

        register(new Recipe(BlockType.PLANKS, 3, BlockType.STICK, 2, BlockType.WOOD_AXE, 1));
        register(new Recipe(BlockType.STONE, 3, BlockType.STICK, 2, BlockType.STONE_AXE, 1));
        register(new Recipe(BlockType.IRON_ORE, 3, BlockType.STICK, 2, BlockType.IRON_AXE, 1));
        register(new Recipe(BlockType.DIAMOND_ORE, 3, BlockType.STICK, 2, BlockType.DIAMOND_AXE, 1));

        // Swords are lighter than pickaxes/axes: 2 material + 1 stick instead of 3 + 2.
        register(new Recipe(BlockType.PLANKS, 2, BlockType.STICK, 1, BlockType.WOOD_SWORD, 1));
        register(new Recipe(BlockType.STONE, 2, BlockType.STICK, 1, BlockType.STONE_SWORD, 1));
        register(new Recipe(BlockType.IRON_ORE, 2, BlockType.STICK, 1, BlockType.IRON_SWORD, 1));
        register(new Recipe(BlockType.DIAMOND_ORE, 2, BlockType.STICK, 1, BlockType.DIAMOND_SWORD, 1));
    }

    private static void register(Recipe recipe) {
        BY_OUTPUT.put(recipe.output(), recipe);
    }

    private Crafting() {
    }

    public static Recipe recipeFor(BlockType output) {
        return BY_OUTPUT.get(output);
    }

    /** Attempts to craft one batch of {@code output}'s recipe from {@code inventory}. Returns false if there's no recipe or not enough input. */
    public static boolean craft(Inventory inventory, BlockType output) {
        Recipe recipe = BY_OUTPUT.get(output);
        if (recipe == null) return false;

        boolean hasSecond = recipe.input2() != null;
        // Check both requirements before removing anything, so a recipe never
        // partially consumes ingredient 1 and then fails on ingredient 2.
        if (inventory.getCount(recipe.input1()) < recipe.amount1()) return false;
        if (hasSecond && inventory.getCount(recipe.input2()) < recipe.amount2()) return false;

        inventory.remove(recipe.input1(), recipe.amount1());
        if (hasSecond) {
            inventory.remove(recipe.input2(), recipe.amount2());
        }
        inventory.add(recipe.output(), recipe.outputAmount());
        return true;
    }
}
