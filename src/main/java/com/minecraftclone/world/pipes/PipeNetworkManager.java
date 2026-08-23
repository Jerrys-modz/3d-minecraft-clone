package com.minecraftclone.world.pipes;

import com.minecraftclone.world.BlockType;
import com.minecraftclone.world.World;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Discovers and caches connected pipe networks per world, shared by every
 * transport type. A network is the flood-filled set of pipe cells connected
 * to a given position; consumers scan its perimeter for their endpoints
 * (boilers, tanks, machine ports), so this class stays transport-agnostic.
 *
 * <p>Caching: networks are memoized by any member cell's packed key. Any
 * block change touching a pipe invalidates that transport type's whole cache
 * (cheap: rebuilds happen lazily on the next query). Consumers additionally
 * throttle their own queries, so a rebuild lands at most a couple of times
 * per second no matter how many machines share one network.
 */
public final class PipeNetworkManager {

    /** Max pipe cells one network may contain - runaway loops can't stall ticks. */
    public static final int MAX_NETWORK_CELLS = 1024;

    private final World world;
    /** type -> (any-member packed key -> network snapshot). */
    private final Map<PipeType, Map<Long, PipeNetwork>> cache = new HashMap<>();

    public PipeNetworkManager(World world) {
        this.world = world;
    }

    /**
     * The connected {@code type} pipe network containing (or touching)
     * {@code (x,y,z)}, or null when that position has no pipe of this type
     * adjacent to it. The returned snapshot includes only cells actually
     * filled with pipes.
     */
    public PipeNetwork networkAt(PipeType type, int x, int y, int z) {
        if (!type.isImplemented()) return null;
        BlockType pipeBlock = type.pipeBlock;

        // Fast path: position itself is a pipe and its network is cached.
        long here = key(x, y, z);
        Map<Long, PipeNetwork> byCell = cache.computeIfAbsent(type, t -> new HashMap<>());
        PipeNetwork cached = byCell.get(here);

        // Flood fill seeded by the queried cell itself (when it's a pipe)
        // plus its six neighbours.
        Set<Long> visited = new HashSet<>();
        Set<Long> cells = new HashSet<>();
        ArrayDeque<long[]> queue = new ArrayDeque<>();
        if (world.getBlock(x, y, z) == pipeBlock) {
            visited.add(here);
            cells.add(here);
            queue.add(new long[]{x, y, z});
        }
        for (int[] d : FACES) {
            long key = key(x + d[0], y + d[1], z + d[2]);
            if (!visited.add(key)) continue;
            if (world.getBlock(x + d[0], y + d[1], z + d[2]) == pipeBlock) {
                cells.add(key);
                queue.add(new long[]{x + d[0], y + d[1], z + d[2]});
            }
        }
        while (!queue.isEmpty() && cells.size() <= MAX_NETWORK_CELLS) {
            long[] cell = queue.poll();
            for (int[] d : FACES) {
                int nx = (int) cell[0] + d[0], ny = (int) cell[1] + d[1], nz = (int) cell[2] + d[2];
                long key = key(nx, ny, nz);
                if (!visited.add(key)) continue;
                if (world.getBlock(nx, ny, nz) == pipeBlock) {
                    cells.add(key);
                    queue.add(new long[]{nx, ny, nz});
                }
            }
        }

        // No connected pipes at all: nothing to cache or return.
        if (cells.isEmpty()) return null;

        PipeNetwork network = new PipeNetwork(type, cells);
        for (long key : cells) byCell.put(key, network);
        return network;
    }

    /** Drops cached networks after a block change so stale runs aren't reused. */
    public void onBlockChanged(int x, int y, int z) {
        cache.clear();
    }

    // ------------------------------------------------------------------

    private static final int[][] FACES = {
            {1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1},
    };

    private static long key(int x, int y, int z) {
        return ((long) x & 0x1FFFFFL) | (((long) y & 0x1FFFFFL) << 21) | (((long) z & 0x1FFFFFL) << 42);
    }
}
