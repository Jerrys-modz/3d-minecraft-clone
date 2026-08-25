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
 *   <li><b>Falling resets distance</b>: any drop - not just a source's own
 *       column - brings a cell's distance back to 0, so water flowing down a
 *       hillside or staircase can keep descending indefinitely instead of
 *       drying up once the 7-block spread limit is used up.</li>
 *   <li><b>Downhill preference</b>: a resting cell with at least one
 *       immediate drop beside it only flows toward that drop, not evenly in
 *       all 4 directions - otherwise every row of a slope would also spend
 *       its (distance-reset) 7-block sideways allowance, fanning a narrow
 *       stream out into a flooded wedge on the way down. Flat, enclosed
 *       ground with no drop anywhere still spreads evenly, same as before.</li>
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
     *       reached from, same keys as {@code fill} (0 = falling - a
     *       source's own column, or any water resuming a fall after a
     *       drop).</li>
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

    /** Packs signed world coordinates without a finite world-size limit. */
    public static long key(int x, int y, int z) {
        // Three signed 21-bit coordinates cover the complete supported block
        // range while preserving sign during unpacking. Unlike the old offset
        // packing, out-of-range coordinates cannot alias one another.
        return ((long) (x & 0x1FFFFF))
                | ((long) (y & 0x1FFFFF) << 21)
                | ((long) (z & 0x1FFFFF) << 42);
    }

    private static int unpackSigned(long value) {
        int coordinate = (int) (value & 0x1FFFFF);
        return (coordinate << 11) >> 11;
    }

    public static int keyX(long k) {
        return unpackSigned(k);
    }

    public static int keyY(long k) {
        return unpackSigned(k >>> 21);
    }

    public static int keyZ(long k) {
        return unpackSigned(k >>> 42);
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
                // doesn't spread sideways, which keeps waterfalls narrow. Falling always
                // resets distance to 0, not just for a source's own column: water that
                // spread out to its 7-block limit and then reaches a drop is falling
                // again, and a fall is unrestricted the same way a source's is. Without
                // this a hillside staircase would carry the accumulated distance down
                // through every step and dry up a few drops later, even though it's
                // still headed downhill the whole way - flowing water should be able to
                // keep descending a slope indefinitely, the same as Minecraft's own.
                spread(world, n.x, n.y - 1, n.z, 0, n.flowType, queue, visited, fill, levels, flowLevels, reachedFlow);
            } else {
                // Keep traversing down through existing fluid so a column stays "reached".
                if (below == n.flowType || below == sourceOf(n.flowType)) {
                    spread(world, n.x, n.y - 1, n.z, 0, n.flowType, queue, visited, fill, levels, flowLevels, reachedFlow);
                }
                // Spread sideways only from cells resting on a solid surface. A cell
                // whose below is more of the same fluid is part of a vertical column
                // - letting it spread sideways would make a waterfall balloon into a
                // fat blob on its second tick.
                if (!below.isFluid() && n.dist + 1 <= maxDistance(n.flowType)) {
                    // If any neighbor is an immediate drop (open, with open air below
                    // it too), flow only toward those - a real downhill run, like a
                    // staircase or a sloped hillside, keeps a stream narrow because
                    // every direction that isn't the way down is normal solid ground
                    // right beside it, not another opening to also pour into.
                    // Distance resets on every fall (see above), so without this a
                    // wide-open slope would let every single row spend a fresh
                    // 7-block sideways allowance before continuing down, fanning a
                    // narrow stream out into a flooded wedge shape by the bottom.
                    // Only kicks in when at least one direction actually leads
                    // somewhere lower - on flat, enclosed ground (no drop anywhere)
                    // it's still an ordinary even spread in all 4 directions.
                    boolean dropEast = leadsToDrop(world, n.x + 1, n.y, n.z, n.flowType);
                    boolean dropWest = leadsToDrop(world, n.x - 1, n.y, n.z, n.flowType);
                    boolean dropSouth = leadsToDrop(world, n.x, n.y, n.z + 1, n.flowType);
                    boolean dropNorth = leadsToDrop(world, n.x, n.y, n.z - 1, n.flowType);
                    boolean anyDrop = dropEast || dropWest || dropSouth || dropNorth;
                    if (!anyDrop || dropEast) {
                        spread(world, n.x + 1, n.y, n.z, n.dist + 1, n.flowType, queue, visited, fill, levels, flowLevels, reachedFlow);
                    }
                    if (!anyDrop || dropWest) {
                        spread(world, n.x - 1, n.y, n.z, n.dist + 1, n.flowType, queue, visited, fill, levels, flowLevels, reachedFlow);
                    }
                    if (!anyDrop || dropSouth) {
                        spread(world, n.x, n.y, n.z + 1, n.dist + 1, n.flowType, queue, visited, fill, levels, flowLevels, reachedFlow);
                    }
                    if (!anyDrop || dropNorth) {
                        spread(world, n.x, n.y, n.z - 1, n.dist + 1, n.flowType, queue, visited, fill, levels, flowLevels, reachedFlow);
                    }
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

    /**
     * True if a neighbor cell is itself passable <em>and</em> has open air
     * (or more of the same fall) below it too - a genuine one-block drop
     * right there, not just passable ground at the same height. Used to
     * make sideways spread prefer any direction that keeps descending over
     * ones that don't.
     * <p>
     * "Passable" here means the same thing {@link #spread} does: open air,
     * a cross decoration, or already more of this same flow/its source -
     * not just {@code AIR}. A direction that's a genuine drop doesn't stop
     * being one the moment gradual spread (or an earlier step in this same
     * flood) actually places flow there; checking only for {@code AIR}
     * meant a downhill neighbor counted as "a drop" for exactly one tick,
     * then looked identical to solid ground once it was wet - silently
     * falling back to the unrestricted, spreads-everywhere case from then on.
     */
    private static boolean leadsToDrop(BlockAccessor world, int x, int y, int z, BlockType flowType) {
        BlockType here = world.getBlock(x, y, z);
        boolean passable = here == BlockType.AIR || here.cross || here == flowType || here == sourceOf(flowType);
        if (!passable) return false;
        BlockType beneath = world.getBlock(x, y - 1, z);
        return beneath == BlockType.AIR || beneath.cross || beneath.isFluid();
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
