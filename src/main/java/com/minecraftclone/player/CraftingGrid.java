package com.minecraftclone.player;

import com.minecraftclone.world.BlockType;

/**
 * The 3x3 crafting grid shown by the crafting screen. Items placed here are
 * pulled out of the player's inventory as they're placed; closing the grid
 * returns them, and crafting consumes them (converting them to the output).
 */
public class CraftingGrid {

    public static final int WIDTH = 3;
    public static final int HEIGHT = 3;
    public static final int SIZE = WIDTH * HEIGHT;

    private final BlockType[] cells = new BlockType[SIZE];

    public BlockType get(int index) {
        return cells[index];
    }

    public boolean isEmpty() {
        for (BlockType c : cells) {
            if (c != null) return false;
        }
        return true;
    }

    /** True if the cell at {@code index} already holds an item. */
    public boolean isOccupied(int index) {
        return cells[index] != null;
    }

    /**
     * Places one of {@code type} into an empty cell, consuming it from the
     * inventory. Returns false (no change) if the cell is occupied, the type is
     * null, or the player has none.
     */
    public boolean place(int index, Inventory inventory, BlockType type) {
        if (type == null || cells[index] != null) return false;
        if (!inventory.remove(type, 1)) return false;
        cells[index] = type;
        return true;
    }

    /** Returns the item in {@code index} to the inventory and empties the cell. */
    public void clear(int index, Inventory inventory) {
        if (cells[index] != null) {
            inventory.add(cells[index], 1);
            cells[index] = null;
        }
    }

    /** Returns every placed item to the inventory - used when closing the grid. */
    public void clearAll(Inventory inventory) {
        for (int i = 0; i < SIZE; i++) {
            clear(i, inventory);
        }
    }

    /** Empties the grid without returning items - used when a craft consumes them. */
    public void reset() {
        for (int i = 0; i < SIZE; i++) {
            cells[i] = null;
        }
    }

    /** A copy of the 3x3 cells (row-major) for recipe matching. */
    public BlockType[] snapshot() {
        return cells.clone();
    }
}
