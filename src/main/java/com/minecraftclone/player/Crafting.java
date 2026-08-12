package com.minecraftclone.player;

import com.minecraftclone.world.BlockType;

import java.util.EnumMap;
import java.util.Map;

/**
 * A small, fixed recipe table: each recipe converts some amount of one raw
 * material into some amount of a refined block. There's no crafting-grid UI
 * - press 'C' with the desired output selected in the hotbar, and if you
 * have enough of its input, it's crafted directly into your inventory.
 */
public final class Crafting {

    public record Recipe(BlockType input, int inputAmount, BlockType output, int outputAmount) {
    }

    private static final Map<BlockType, Recipe> BY_OUTPUT = new EnumMap<>(BlockType.class);

    static {
        register(new Recipe(BlockType.WOOD_LOG, 1, BlockType.PLANKS, 4));
        register(new Recipe(BlockType.SAND, 2, BlockType.GLASS, 1));
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
        if (!inventory.remove(recipe.input(), recipe.inputAmount())) return false;
        inventory.add(recipe.output(), recipe.outputAmount());
        return true;
    }
}
