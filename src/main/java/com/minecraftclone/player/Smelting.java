package com.minecraftclone.player;

import com.minecraftclone.world.BlockType;

import java.util.EnumMap;
import java.util.Map;

/**
 * Smelting rules: turn raw ore into a refined ingot/gem using coal as fuel,
 * performed by pressing the craft key while aiming at a placed furnace (see
 * {@code Main}). Kept as a static registry, like {@link Crafting}, rather than
 * a per-furnace inventory - the furnace itself is stateless, so smelting is a
 * simple one-input/one-output conversion gated on fuel.
 */
public final class Smelting {

    /** Fuel consumed per smelt. The game has no separate coal item, so coal ore is the fuel. */
    public static final BlockType FUEL = BlockType.COAL_ORE;
    public static final int FUEL_PER_SMELT = 1;

    private static final Map<BlockType, BlockType> OUTPUTS = new EnumMap<>(BlockType.class);

    static {
        OUTPUTS.put(BlockType.IRON_ORE, BlockType.IRON_INGOT);
        OUTPUTS.put(BlockType.GOLD_ORE, BlockType.GOLD_INGOT);
        OUTPUTS.put(BlockType.DIAMOND_ORE, BlockType.DIAMOND);
    }

    private Smelting() {
    }

    /** True if {@code ore} can be smelted (has a known output). */
    public static boolean isSmeltable(BlockType ore) {
        return OUTPUTS.containsKey(ore);
    }

    /** The block an ore smelts into, or null if it isn't smeltable. */
    public static BlockType outputFor(BlockType ore) {
        return OUTPUTS.get(ore);
    }

    /**
     * Smelts one {@code ore} into its refined form, consuming one fuel. Returns
     * the output (and adds it to the inventory) on success, or null if there's
     * no recipe, no ore, or no fuel - in which case nothing is consumed.
     */
    public static BlockType smelt(Inventory inventory, BlockType ore) {
        BlockType output = OUTPUTS.get(ore);
        if (output == null) return null;
        if (inventory.getCount(ore) < 1) return null;
        if (inventory.getCount(FUEL) < FUEL_PER_SMELT) return null;
        inventory.remove(ore, 1);
        inventory.remove(FUEL, FUEL_PER_SMELT);
        inventory.add(output, 1);
        return output;
    }
}
