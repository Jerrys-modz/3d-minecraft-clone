package com.minecraftclone.world;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Computes where flowing water/lava should be, Minecraft-style: a fluid
 * **source** pours straight down until it hits something, then spreads
 * horizontally for a bounded distance (water 7, lava 3), thinning out as it
 * goes. The whole flow field is recomputed from sources each tick, so removing
 * a source makes its flow dry up.
 * <p>
 * This is pure logic (only {@link BlockAccessor#getBlock} is used) so it can be
 * tested without a GL context. The result is a set of cells to fill with flow
 * and a set of existing flow cells to remove.
 */
public final class FluidSim {

    public static final int WATER_FLOW_DISTANCE = 7;
    public static final int LAVA_FLOW_DISTANCE = 3;

    /** A fluid block's world position and type. */
    public record FluidBlock(int x, int y, int z, BlockType type) {
    }

    /** The result of one pass: cells to fill (pos -> flow type) and cells to dry up. */
    public record Result(Map<Long, BlockType> fill, Set<Long> remove) {
    }

    // Coordinates are bounded by the loaded-chunk radius (fluids only flow within
    // a few blocks of a placed source), so a 12-bit x/z field (+ 2048 offset) and
    // an 8-bit y field are more than enough - and don't overflow their masks.
    private static final int OFF = 1 << 11; // 2048

    /** Packs a world coordinate into a long key (for the fill/remove sets). */
    public static long key(int x, int y, int z) {
        return ((long) (x + OFF) << 40) | ((long) y << 32) | ((long) (z + OFF) << 20);
    }

    public static int keyX(long k) {
        return (int) ((k >>> 40) & 0xFFF) - OFF;
    }

    public static int keyY(long k) {
        return (int) ((k >>> 32) & 0xFF);
    }

    public static int keyZ(long k) {
        return (int) ((k >>> 20) & 0xFFF) - OFF;
    }

    private static final class Node {
        final int x, y, z, dist;
        final BlockType flowType;

        Node(int x, int y, int z, int dist, BlockType flowType) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.dist = dist;
            this.flowType = flowType;
        }
    }

    private FluidSim() {
    }

    /**
     * Runs one flood-fill from {@code sources} and returns what to change:
     * {@link Result#fill} maps air/cross cells (that should become fluid) to
     * their flow type, and {@link Result#remove} lists existing flow cells that
     * are no longer reachable from any source (so they dry up).
     */
    public static Result compute(BlockAccessor world, List<FluidBlock> sources, List<FluidBlock> flows) {
        Map<Long, BlockType> fill = new HashMap<>();
        Set<Long> reachedFlow = new HashSet<>();
        Set<Long> visited = new HashSet<>();
        ArrayDeque<Node> queue = new ArrayDeque<>();

        for (FluidBlock s : sources) {
            if (visited.add(key(s.x, s.y, s.z))) {
                queue.add(new Node(s.x, s.y, s.z, 0, flowTypeOf(s.type)));
            }
        }

        while (!queue.isEmpty()) {
            Node n = queue.poll();
            BlockType below = world.getBlock(n.x, n.y - 1, n.z);

            if (below == BlockType.AIR || below.cross) {
                // Air (or a cross decoration): fall straight down - a cell in free-fall
                // doesn't spread sideways, which keeps waterfalls narrow.
                spread(world, n.x, n.y - 1, n.z, n.dist, n.flowType, queue, visited, fill, reachedFlow);
            } else {
                // Supported - below is solid OR more of the same fluid: spread
                // horizontally (so a pool's surface fills a freshly-broken block, and
                // a source resting on water still spreads), and keep traversing down
                // through existing fluid so a column stays "reached".
                if (below == n.flowType || below == sourceOf(n.flowType)) {
                    spread(world, n.x, n.y - 1, n.z, n.dist, n.flowType, queue, visited, fill, reachedFlow);
                }
                if (n.dist + 1 <= maxDistance(n.flowType)) {
                    spread(world, n.x + 1, n.y, n.z, n.dist + 1, n.flowType, queue, visited, fill, reachedFlow);
                    spread(world, n.x - 1, n.y, n.z, n.dist + 1, n.flowType, queue, visited, fill, reachedFlow);
                    spread(world, n.x, n.y, n.z + 1, n.dist + 1, n.flowType, queue, visited, fill, reachedFlow);
                    spread(world, n.x, n.y, n.z - 1, n.dist + 1, n.flowType, queue, visited, fill, reachedFlow);
                }
            }
        }

        Set<Long> remove = new HashSet<>();
        for (FluidBlock f : flows) {
            long k = key(f.x, f.y, f.z);
            if (!reachedFlow.contains(k)) {
                remove.add(k);
            }
        }
        return new Result(fill, remove);
    }

    /** Expands the flood into one neighbor cell, filling air/cross or passing through the same fluid. */
    private static void spread(BlockAccessor world, int x, int y, int z, int dist, BlockType flowType,
                               ArrayDeque<Node> queue, Set<Long> visited, Map<Long, BlockType> fill, Set<Long> reachedFlow) {
        long k = key(x, y, z);
        if (!visited.add(k)) return;
        BlockType b = world.getBlock(x, y, z);
        if (b == BlockType.AIR || b.cross) {
            fill.put(k, flowType);
            queue.add(new Node(x, y, z, dist, flowType));
        } else if (b == flowType || b == sourceOf(flowType)) {
            if (b == flowType) {
                reachedFlow.add(k);
            }
            queue.add(new Node(x, y, z, dist, flowType));
        }
        // else: solid, other fluid, static water/lava -> barrier.
    }

    private static BlockType flowTypeOf(BlockType source) {
        return source == BlockType.WATER_SOURCE ? BlockType.WATER_FLOW : BlockType.LAVA_FLOW;
    }

    private static BlockType sourceOf(BlockType flow) {
        return flow == BlockType.WATER_FLOW ? BlockType.WATER_SOURCE : BlockType.LAVA_SOURCE;
    }

    private static int maxDistance(BlockType flowType) {
        return flowType == BlockType.WATER_FLOW ? WATER_FLOW_DISTANCE : LAVA_FLOW_DISTANCE;
    }
}
