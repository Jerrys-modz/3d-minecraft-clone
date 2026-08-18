package com.minecraftclone.player;

import com.minecraftclone.world.BlockType;

/**
 * Common interface for crafting grids (both 2x2 player inventory and 3x3 crafting table).
 */
public interface Grid {
    BlockType get(int index);
    void set(int index, BlockType type);
    boolean isOccupied(int index);
    boolean isEmpty();
    void reset();
    BlockType[] snapshot();
}
