package com.minecraftclone.world.pipes;

import com.minecraftclone.world.SteamPipeTier;

import java.util.Set;

/**
 * An immutable snapshot of one connected pipe network: the transport type,
 * every pipe cell belonging to it (packed block keys, matching
 * {@code World.blockKey}), and the weakest pipe tier in the run - the whole
 * network conducts at that tier's throughput.
 */
public final class PipeNetwork {

    public final PipeType type;
    /** Packed keys of every pipe cell in this connected run. */
    public final Set<Long> cells;
    /** Weakest tier among the cells (null for non-tiered transports). */
    public final SteamPipeTier minTier;

    PipeNetwork(PipeType type, Set<Long> cells, SteamPipeTier minTier) {
        this.type = type;
        this.cells = cells;
        this.minTier = minTier;
    }

    /** True if the packed key belongs to this network. */
    public boolean contains(long packedKey) {
        return cells.contains(packedKey);
    }
}
