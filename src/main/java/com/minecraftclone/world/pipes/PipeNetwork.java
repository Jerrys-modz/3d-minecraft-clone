package com.minecraftclone.world.pipes;

import java.util.Set;

/**
 * An immutable snapshot of one connected pipe network: the transport type and
 * every pipe cell belonging to it (packed block keys, matching
 * {@code World.blockKey}). Consumers scan the network's perimeter for their
 * own endpoints (boilers, tanks, machine ports).
 */
public final class PipeNetwork {

    public final PipeType type;
    /** Packed keys of every pipe cell in this connected run. */
    public final Set<Long> cells;

    PipeNetwork(PipeType type, Set<Long> cells) {
        this.type = type;
        this.cells = cells;
    }

    /** True if the packed key belongs to this network. */
    public boolean contains(long packedKey) {
        return cells.contains(packedKey);
    }
}
