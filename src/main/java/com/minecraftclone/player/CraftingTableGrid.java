package com.minecraftclone.player;

import com.minecraftclone.world.BlockType;

/**
 * The 3x3 crafting grid of a placed crafting table block. Each cell holds at most one
 * item (single items only, never stacked, like Minecraft's crafting grid), so
 * a cell is simply a {@link BlockType} or null. Items are moved in/out of the
 * cells by the cursor stack in {@link InventoryController}; crafting consumes
 * the ingredients (see {@link #reset}) and hands the result back to the
 * cursor.
 *
 * Note: The 3x3 crafting table grid supports complex recipes for armor, tools,
 * stairs, doors, and other multi-component items.
 */
public class CraftingTableGrid implements Grid {

    public static final int WIDTH = 3;
    public static final int HEIGHT = 3;
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

    /** A copy of the 3x3 cells (row-major, null = empty) for recipe matching. */
    public BlockType[] snapshot() {
        return cells.clone();
    }
}
