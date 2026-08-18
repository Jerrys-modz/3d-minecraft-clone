package com.minecraftclone.player;

import com.minecraftclone.world.BlockType;

/**
 * The 2x2 crafting grid on the player inventory screen. Each cell holds at most one
 * item (single items only, never stacked, like Minecraft's crafting grid), so
 * a cell is simply a {@link BlockType} or null. Items are moved in/out of the
 * cells by the cursor stack in {@link InventoryController}; crafting consumes
 * the ingredients (see {@link #reset}) and hands the result back to the
 * cursor.
 *
 * Note: The 2x2 player inventory grid only supports simple 2x2 recipes.
 * Complex recipes that require 3x3 grids are only available at a crafting table.
 */
public class CraftingGrid {

    public static final int WIDTH = 2;
    public static final int HEIGHT = 2;
    public static final int SIZE = WIDTH * HEIGHT;

    private final BlockType[] cells = new BlockType[SIZE];

    public BlockType get(int index) {
        return cells[index];
    }

    /** Puts one item in the cell (null clears it). Cells hold single items, so a count is implicit. */
    public void set(int index, BlockType type) {
        cells[index] = type;
    }

    public boolean isOccupied(int index) {
        return cells[index] != null;
    }

    public boolean isEmpty() {
        for (BlockType c : cells) {
            if (c != null) return false;
        }
        return true;
    }

    /** Empties every cell without returning items - used when a craft consumes the ingredients. */
    public void reset() {
        for (int i = 0; i < SIZE; i++) {
            cells[i] = null;
        }
    }

    /** A copy of the 2x2 cells (row-major, null = empty) for recipe matching. */
    public BlockType[] snapshot() {
        return cells.clone();
    }
}
