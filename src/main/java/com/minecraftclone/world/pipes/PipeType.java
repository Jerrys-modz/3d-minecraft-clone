package com.minecraftclone.world.pipes;

import com.minecraftclone.world.BlockType;
import com.minecraftclone.world.CableTier;
import com.minecraftclone.world.SteamPipeTier;

/**
 * The kind of resource a pipe conducts. Each type has its own pipe blocks
 * (potentially across several material tiers) and its own exchange rules; the
 * network topology discovery in {@link PipeNetworkManager} is shared across
 * all of them.
 */
public enum PipeType {

    /** Steam from boilers to steam machines. Live now, in wood + bronze tiers. */
    STEAM(BlockType.STEAM_PIPE_BRONZE) {
        @Override
        public boolean matches(BlockType block) {
            return SteamPipeTier.isSteamPipe(block);
        }
    },

    /** Fluids (water, lava, molten metals) - planned. */
    FLUID(null),

    /** Items between inventories and machines - planned. */
    ITEM(null),

    /** Energy (EU) between generators and machines — copper and gold cable tiers. */
    ENERGY(BlockType.COPPER_CABLE) {
        @Override
        public boolean matches(BlockType block) {
            return CableTier.isCable(block);
        }
    },

    /** Gases - planned. */
    GAS(null);

    /** The representative pipe block for this type, or null until implemented. */
    public final BlockType pipeBlock;

    PipeType(BlockType pipeBlock) {
        this.pipeBlock = pipeBlock;
    }

    /** True if this block is a pipe of this transport type (any tier). */
    public boolean matches(BlockType block) {
        return block != null && block == pipeBlock;
    }

    /** True if this transport type has a placeable pipe block yet. */
    public boolean isImplemented() {
        return pipeBlock != null;
    }
}
