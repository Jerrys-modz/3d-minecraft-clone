package com.minecraftclone.player;

import com.minecraftclone.world.BlockType;

import java.util.EnumMap;
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
}
