package com.minecraftclone.player;

import com.minecraftclone.world.BlockType;

import java.util.EnumMap;
import java.util.Map;

/**
 * Smelting recipes: turn raw ore into a refined ingot/gem using coal as fuel.
 * This is the recipe registry for placed furnaces (see {@code Furnace}), which
 * smelt over time rather than instantly - the old press-{@code C}-at-a-furnace
 * mechanic was replaced by the furnace GUI. Kept as a static registry, like
 * {@link Crafting}, so a furnace just asks "what does this ore become?".
 */
public final class Smelting {

    /** Fuel for a furnace: coal, mined from coal ore (which drops coal). */
    public static final BlockType FUEL = BlockType.COAL;

    private static final Map<BlockType, BlockType> OUTPUTS = new EnumMap<>(BlockType.class);

    static {
        // Add a smelting recipe with one line: ore -> refined output.
        smelt(BlockType.IRON_ORE, BlockType.IRON_INGOT);
        smelt(BlockType.GOLD_ORE, BlockType.GOLD_INGOT);
        smelt(BlockType.DIAMOND_ORE, BlockType.DIAMOND);
        smelt(BlockType.COAL_ORE, BlockType.COAL);
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
