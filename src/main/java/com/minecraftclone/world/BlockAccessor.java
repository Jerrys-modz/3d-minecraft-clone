package com.minecraftclone.world;

/** Read access to blocks by absolute world coordinates, used by chunk mesh building for neighbor lookups. */
public interface BlockAccessor {
    BlockType getBlock(int worldX, int worldY, int worldZ);
}
