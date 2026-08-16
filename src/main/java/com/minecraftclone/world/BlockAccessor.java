package com.minecraftclone.world;

/** Read access to blocks by absolute world coordinates, used by chunk mesh building for neighbor lookups. */
public interface BlockAccessor {
    BlockType getBlock(int worldX, int worldY, int worldZ);

    /**
     * The fluid flow "level" at this position: 0 for a cell right next to its
     * source, increasing up to {@link FluidSim#WATER_FLOW_DISTANCE}/{@link
     * FluidSim#LAVA_FLOW_DISTANCE} the farther it's spread - used to grade the
     * rendered surface height so flowing fluid visibly thins out with
     * distance, the way it does in Minecraft. Meaningless (always 0) for
     * anything that isn't a tracked flow block. Defaulted so existing
     * BlockAccessor implementations (like tests) don't need to know about it.
     */
    default int getFluidLevel(int worldX, int worldY, int worldZ) {
        return 0;
    }

    /**
     * A decoration layered <em>inside</em> this cell alongside its primary
     * block - e.g. seaweed growing inside a water cell, the way Minecraft's
     * waterlogged plants sit inside a fluid block rather than replacing it.
     * {@link BlockType#AIR} means no overlay. Defaulted so existing
     * BlockAccessor implementations (like tests) don't need to know about it.
     */
    default BlockType getOverlay(int worldX, int worldY, int worldZ) {
        return BlockType.AIR;
    }

    /**
     * Whether the block at this position is currently "active" - e.g. a
     * furnace that's burning, which switches its front face to the glowing
     * variant. Defaulted to false so existing BlockAccessor implementations
     * (like tests) don't need to know about it.
     */
    default boolean isBlockActive(int worldX, int worldY, int worldZ) {
        return false;
    }
}
