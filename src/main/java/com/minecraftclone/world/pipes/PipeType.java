package com.minecraftclone.world.pipes;

import com.minecraftclone.world.BlockType;

/**
 * The kind of resource a pipe conducts. Each type has its own pipe block and
 * its own exchange rules; the network topology discovery in
 * {@link PipeNetworkManager} is shared across all of them.
 */
public enum PipeType {

    /** Steam from boilers to steam machines. Live now. */
    STEAM(BlockType.STEAM_PIPE),

    /** Fluids (water, lava, molten metals) - planned. */
    FLUID(null),

    /** Items between inventories and machines - planned. */
    ITEM(null),

    /** Energy (EU) between generators and machines - planned. */
    ENERGY(null),

    /** Gases - planned. */
    GAS(null);

    /** The placeable block this type flows through, or null until implemented. */
    public final BlockType pipeBlock;

    PipeType(BlockType pipeBlock) {
        this.pipeBlock = pipeBlock;
    }

    /** True if this transport type has a placeable pipe block yet. */
    public boolean isImplemented() {
        return pipeBlock != null;
    }
}
