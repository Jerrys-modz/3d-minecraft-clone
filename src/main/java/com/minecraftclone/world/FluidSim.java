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
 * Two rules keep it feeling like Minecraft:
 * <ul>
 *   <li><b>Gradual spread</b>: a flow cell only appears once the cell it pours
 *       out of already holds fluid, so a pool creeps outward one ring per tick
 *       instead of materializing at full extent instantly. Falling cells
 *       (distance 0) are the exception - a waterfall column drops at once.</li>
 *   <li><b>Narrow waterfalls</b>: only a cell resting on a solid surface
 *       spreads sideways. A cell in a vertical column (fluid directly below
 *       it) just keeps falling, so a waterfall stays a narrow ribbon instead
 *       of ballooning into a fat blob on its second tick.</li>
 * </ul>
 * <p>
 * This is pure logic (only {@link BlockAccessor} is used) so it can be tested
 * without a GL context. The result lists the cells to fill this tick, each
 * existing flow's fresh distance (levels change as the topology changes), and
 * the existing flow cells to dry up.
 */
public final class FluidSim {

    public static final int WATER_FLOW_DISTANCE = 7;
    public static final int LAVA_FLOW_DISTANCE = 3;

    /** A fluid block's world position and type. */
    public record FluidBlock(int x, int y, int z, BlockType type) {
    }

    /**
     * The result of one pass:
     * <ul>
     *   <li>{@code fill} - cells to fill this tick (pos -> flow type).</li>
     *   <li>{@code levels} - each fill's distance from the source it was
     *       reached from, same keys as {@code fill} (0 = a falling column
     *       directly under a source).</li>
     *   <li>{@code flowLevels} - every existing flow that was reached this
     *       tick, mapped to its fresh distance (0..maxDistance). Cells can
     *       move closer to a source when the topology changes, so the stored
     *       level of an already-flowing cell must be refreshed too, not just
     *       newly filled ones.</li>
     *   <li>{@code remove} - existing flow cells no longer reachable from any
     *       source (they dry up).</li>
     * </ul>
     */
    public record Result(Map<Long, BlockType> fill, Map<Long, Integer> levels,
                         Map<Long, Integer> flowLevels, Set<Long> remove) {
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
     * Runs one flood-fill from {@code sources} and returns what to change this
     * tick. The flood itself covers the whole reachable field (so every cell's
     * distance and every still-connected flow is known), but {@link
     * Result#fill} only lists cells that may appear <em>now</em>: anything
     * resting on already-existing fluid, which makes pools creep outward one
     * ring per tick instead of snapping to full extent. {@link Result#remove}
     * lists existing flow cells that are no longer reachable from any source.
     */
    public static Result compute(BlockAccessor world, List<FluidBlock> sources, List<FluidBlock> flows) {
        Map<Long, BlockType> fill = new HashMap<>();
        Map<Long, Integer> levels = new HashMap<>();
        Map<Long, Integer> flowLevels = new HashMap<>();
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
                spread(world, n.x, n.y - 1, n.z, n.dist, n.flowType, queue, visited, fill, levels, flowLevels, reachedFlow);
            } else {
                // Keep traversing down through existing fluid so a column stays "reached".
                if (below == n.flowType || below == sourceOf(n.flowType)) {
                    spread(world, n.x, n.y - 1, n.z, n.dist, n.flowType, queue, visited, fill, levels, flowLevels, reachedFlow);
                }
                // Spread sideways only from cells resting on a solid surface. A cell
                // whose below is more of the same fluid is part of a vertical column
                // - letting it spread sideways would make a waterfall balloon into a
                // fat blob on its second tick.
                if (!below.isFluid() && n.dist + 1 <= maxDistance(n.flowType)) {
                    spread(world, n.x + 1, n.y, n.z, n.dist + 1, n.flowType, queue, visited, fill, levels, flowLevels, reachedFlow);
                    spread(world, n.x - 1, n.y, n.z, n.dist + 1, n.flowType, queue, visited, fill, levels, flowLevels, reachedFlow);
                    spread(world, n.x, n.y, n.z + 1, n.dist + 1, n.flowType, queue, visited, fill, levels, flowLevels, reachedFlow);
                    spread(world, n.x, n.y, n.z - 1, n.dist + 1, n.flowType, queue, visited, fill, levels, flowLevels, reachedFlow);
                }
            }
        }

        // Gradual spread: only cells that touch existing fluid (a source, or a
        // flow that poured in a previous tick) may fill this tick - water creeps
        // one ring outward per tick instead of appearing everywhere at once.
        // Falling cells (distance 0, a source's waterfall column) appear at once.
        Map<Long, BlockType> fillNow = new HashMap<>();
        Map<Long, Integer> levelsNow = new HashMap<>();
        for (Map.Entry<Long, BlockType> e : fill.entrySet()) {
            long k = e.getKey();
            int d = levels.get(k);
            if (d == 0 || touchesFluidWithin(world, keyX(k), keyY(k), keyZ(k), d)) {
                fillNow.put(k, e.getValue());
                levelsNow.put(k, d);
            }
        }

        Set<Long> remove = new HashSet<>();
        for (FluidBlock f : flows) {
            long k = key(f.x, f.y, f.z);
            if (!reachedFlow.contains(k)) {
                remove.add(k);
            }
        }
        return new Result(fillNow, levelsNow, flowLevels, remove);
    }

    /** True if any orthogonal neighbor holds tracked fluid within {@code dist} of its source. */
    private static boolean touchesFluidWithin(BlockAccessor world, int x, int y, int z, int dist) {
        return isFluidWithin(world, x + 1, y, z, dist)
                || isFluidWithin(world, x - 1, y, z, dist)
                || isFluidWithin(world, x, y + 1, z, dist)
                || isFluidWithin(world, x, y - 1, z, dist)
                || isFluidWithin(world, x, y, z + 1, dist)
                || isFluidWithin(world, x, y, z - 1, dist);
    }

    /** True if the cell holds a tracked source/flow whose stored level is within {@code dist}. */
    private static boolean isFluidWithin(BlockAccessor world, int x, int y, int z, int dist) {
        BlockType t = world.getBlock(x, y, z);
        return t.isFlowingFluid() && world.getFluidLevel(x, y, z) <= dist;
    }

    /** Expands the flood into one neighbor cell, filling air/cross or passing through the same fluid. */
    private static void spread(BlockAccessor world, int x, int y, int z, int dist, BlockType flowType,
                               ArrayDeque<Node> queue, Set<Long> visited, Map<Long, BlockType> fill,
                               Map<Long, Integer> levels, Map<Long, Integer> flowLevels, Set<Long> reachedFlow) {
        long k = key(x, y, z);
        if (!visited.add(k)) return;
        BlockType b = world.getBlock(x, y, z);
        if (b == BlockType.AIR || b.cross) {
            fill.put(k, flowType);
            levels.put(k, dist);
            queue.add(new Node(x, y, z, dist, flowType));
        } else if (b == flowType || b == sourceOf(flowType)) {
            if (b == flowType) {
                reachedFlow.add(k);
                // Refresh this existing flow's distance from its source - the topology
                // may have changed since it was first filled (e.g. a new source placed
                // right next to it), so its rendered surface height must follow.
                flowLevels.put(k, dist);
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
