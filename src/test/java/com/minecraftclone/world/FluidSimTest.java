package com.minecraftclone.world;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class FluidSimTest {

    /** A tiny in-memory world so the pure-logic fluid sim can be tested without GL. */
    private static final class StubWorld implements BlockAccessor {
        private final Map<Long, BlockType> blocks = new HashMap<>();
        private final Map<Long, Integer> levels = new HashMap<>();

        void set(int x, int y, int z, BlockType t) {
            set(x, y, z, t, 0);
        }

        void set(int x, int y, int z, BlockType t, int level) {
            blocks.put(FluidSim.key(x, y, z), t);
            levels.put(FluidSim.key(x, y, z), level);
        }

        @Override
        public BlockType getBlock(int x, int y, int z) {
            return blocks.getOrDefault(FluidSim.key(x, y, z), BlockType.AIR);
        }

        @Override
        public int getFluidLevel(int x, int y, int z) {
            return levels.getOrDefault(FluidSim.key(x, y, z), 0);
        }
    }

    private static StubWorld flatGround() {
        StubWorld w = new StubWorld();
        for (int x = -20; x <= 20; x++) {
            for (int z = -20; z <= 20; z++) {
                w.set(x, 0, z, BlockType.STONE);
            }
        }
        return w;
    }

    /** Number of cells in a Manhattan-diamond of radius {@code r}, excluding its center. */
    private static int diamondCells(int r) {
        return 2 * r * (r + 1);
    }

    /** One fluid tick, mirroring World.updateFluids: applies fills, refreshed flow levels, and removals. */
    private static void tick(StubWorld w) {
        List<FluidSim.FluidBlock> sources = new ArrayList<>();
        List<FluidSim.FluidBlock> flows = new ArrayList<>();
        for (Map.Entry<Long, BlockType> e : w.blocks.entrySet()) {
            int x = FluidSim.keyX(e.getKey()), y = FluidSim.keyY(e.getKey()), z = FluidSim.keyZ(e.getKey());
            if (e.getValue().isFluidSource()) {
                sources.add(new FluidSim.FluidBlock(x, y, z, e.getValue()));
            } else if (e.getValue().isFluidFlow()) {
                flows.add(new FluidSim.FluidBlock(x, y, z, e.getValue()));
            }
        }
        FluidSim.Result r = FluidSim.compute(w, sources, flows);
        for (Map.Entry<Long, BlockType> e : r.fill().entrySet()) {
            long k = e.getKey();
            w.set(FluidSim.keyX(k), FluidSim.keyY(k), FluidSim.keyZ(k), e.getValue(), r.levels().getOrDefault(k, 0));
        }
        for (Map.Entry<Long, Integer> e : r.flowLevels().entrySet()) {
            long k = e.getKey();
            int x = FluidSim.keyX(k), y = FluidSim.keyY(k), z = FluidSim.keyZ(k);
            BlockType t = w.getBlock(x, y, z);
            if (t.isFluidFlow()) {
                w.set(x, y, z, t, e.getValue());
            }
        }
        for (long k : r.remove()) {
            w.set(FluidSim.keyX(k), FluidSim.keyY(k), FluidSim.keyZ(k), BlockType.AIR);
        }
    }

    private static int countFlows(StubWorld w) {
        int n = 0;
        for (BlockType t : w.blocks.values()) {
            if (t.isFluidFlow()) n++;
        }
        return n;
    }

    @Test
    void waterSpreadsGraduallyOneRingPerTick() {
        StubWorld w = flatGround();
        w.set(0, 1, 0, BlockType.WATER_SOURCE);
        // Rings grow by 4 cells each tick (the four axial neighbors of the previous ring).
        int expected = 0;
        for (int tick = 1; tick <= FluidSim.WATER_FLOW_DISTANCE; tick++) {
            tick(w);
            expected += 4 * tick;
            assertEquals(expected, countFlows(w), "flows after tick " + tick);
        }
        // Nothing more spreads after reaching full extent.
        tick(w);
        assertEquals(diamondCells(FluidSim.WATER_FLOW_DISTANCE), countFlows(w));
    }

    @Test
    void waterSpreadsExactlySevenBlocksOnGround() {
        StubWorld w = flatGround();
        w.set(0, 1, 0, BlockType.WATER_SOURCE);
        for (int i = 0; i < FluidSim.WATER_FLOW_DISTANCE; i++) {
            tick(w);
        }
        assertEquals(diamondCells(FluidSim.WATER_FLOW_DISTANCE), countFlows(w));
        assertEquals(BlockType.WATER_FLOW, w.getBlock(7, 1, 0));
        assertEquals(BlockType.AIR, w.getBlock(8, 1, 0), "nothing past the flow distance");
        assertEquals(BlockType.WATER_FLOW, w.getBlock(-3, 1, 2));
    }

    @Test
    void levelGrowsByOneWithEachStepFromTheSource() {
        StubWorld w = flatGround();
        w.set(0, 1, 0, BlockType.WATER_SOURCE);
        for (int i = 0; i < FluidSim.WATER_FLOW_DISTANCE; i++) {
            tick(w);
        }
        for (int d = 1; d <= FluidSim.WATER_FLOW_DISTANCE; d++) {
            assertEquals(d, w.getFluidLevel(d, 1, 0), "level at distance " + d);
        }
    }

    @Test
    void lavaSpreadsOnlyThreeBlocks() {
        StubWorld w = flatGround();
        w.set(0, 1, 0, BlockType.LAVA_SOURCE);
        for (int i = 0; i < FluidSim.LAVA_FLOW_DISTANCE; i++) {
            tick(w);
        }
        assertEquals(diamondCells(FluidSim.LAVA_FLOW_DISTANCE), countFlows(w));
        assertEquals(BlockType.AIR, w.getBlock(4, 1, 0));
    }

    @Test
    void sourcePoursDownUntilItHitsGround() {
        StubWorld w = flatGround();
        w.set(0, 3, 0, BlockType.WATER_SOURCE);
        tick(w);
        // The falling column drops at once (falling costs no distance), but the
        // pool only starts the tick after the landing cell is in place.
        assertTrue(w.getBlock(0, 2, 0).isFluidFlow());
        assertTrue(w.getBlock(0, 1, 0).isFluidFlow());
        assertEquals(0, w.getFluidLevel(0, 2, 0));
        assertEquals(0, w.getFluidLevel(0, 1, 0));
        assertEquals(BlockType.AIR, w.getBlock(1, 1, 0), "pool ring needs the landing cell settled first");
        tick(w);
        assertTrue(w.getBlock(1, 1, 0).isFluidFlow(), "pool ring appears once the landing cell is settled");
    }

    @Test
    void waterfallColumnStaysNarrow() {
        StubWorld w = flatGround();
        w.set(0, 10, 0, BlockType.WATER_SOURCE);
        for (int i = 0; i < 5; i++) {
            tick(w);
        }
        // A source column falls straight down and never spreads sideways - a cell
        // in a vertical column keeps falling instead of fattening the waterfall.
        for (int y = 2; y <= 9; y++) {
            assertEquals(BlockType.WATER_FLOW, w.getBlock(0, y, 0), "column cell at y=" + y);
            assertEquals(BlockType.AIR, w.getBlock(1, y, 0), "no sideways spread at y=" + y);
            assertEquals(BlockType.AIR, w.getBlock(-1, y, 0), "no sideways spread at y=" + y);
        }
    }

    @Test
    void waterFlowingDownhillIsNotCappedByTheFlowDistance() {
        // key() packs y directly with no offset (unlike x/z), so it only
        // round-trips non-negative y - keep the whole staircase within the
        // normal, always-positive world-height range.
        StubWorld w = new StubWorld();
        int baseY = 50;
        // A one-block-wide staircase corridor, 15 steps long - each tread one
        // lower than the last, walled in on both sides so the only ways out
        // of each landing are onward (downhill) or back the way it came
        // (blocked by the previous, higher tread). Without the side walls
        // every landing would also see open space - and open air below it -
        // off to the sides, "leading to a drop" there too (see
        // leadsToDrop/downhill preference) and falling forever into it.
        // A source sits on the first tread; water should ride the stairs all
        // the way down, well past the 7-block horizontal spread limit,
        // because every drop between treads resets its distance the same way
        // falling under the source does.
        int steps = 15;
        for (int x = 0; x <= steps; x++) {
            w.set(x, baseY - x, 0, BlockType.STONE);
            for (int y = baseY - steps; y <= baseY + 2; y++) {
                w.set(x, y, 1, BlockType.STONE);
                w.set(x, y, -1, BlockType.STONE);
            }
        }
        w.set(0, baseY + 1, 0, BlockType.WATER_SOURCE);
        for (int i = 0; i < steps * 2; i++) {
            tick(w);
        }
        for (int x = 1; x <= steps; x++) {
            assertEquals(BlockType.WATER_FLOW, w.getBlock(x, baseY - x + 1, 0), "tread " + x + " should be wet");
            // Each landing is a fresh fall, not a graded, nearly-dry-out puddle.
            assertEquals(0, w.getFluidLevel(x, baseY - x + 1, 0), "tread " + x + " just fell, so its level resets to 0");
        }
    }

    @Test
    void downhillFlowPrefersTheDropOverSpreadingSideways() {
        StubWorld w = flatGround();
        // A wide, uniform ramp: every row (increasing x) is one block lower
        // than the last, but each row is flat and open across its full width
        // (z), same as a real sloped hillside - nothing forces the flow
        // sideways except its own spread rules.
        int rows = 5;
        for (int x = 0; x <= rows; x++) {
            for (int z = -4; z <= 4; z++) {
                w.set(x, 10 - x, z, BlockType.STONE);
            }
        }
        w.set(0, 11, 0, BlockType.WATER_SOURCE);
        for (int i = 0; i < 20; i++) {
            tick(w);
        }
        // The source's own row may pool a little around it (nothing there is
        // a drop yet), but once the flow reaches the ramp itself it should
        // ride straight down it rather than also ballooning across the row's
        // width - each row's far sides (z=+-3 or wider) should stay dry.
        for (int x = 1; x <= rows; x++) {
            assertEquals(BlockType.AIR, w.getBlock(x, 10 - x + 1, 3), "row " + x + " shouldn't flood sideways");
            assertEquals(BlockType.AIR, w.getBlock(x, 10 - x + 1, -3), "row " + x + " shouldn't flood sideways");
        }
        // But it does still ride the slope the whole way down.
        assertEquals(BlockType.WATER_FLOW, w.getBlock(rows, 10 - rows + 1, 0), "last row should still be wet");
    }

    @Test
    void unreachableFlowDriesUpButReachableFlowSurvives() {
        StubWorld w = flatGround();
        w.set(1, 1, 0, BlockType.WATER_FLOW);   // reachable from the source
        w.set(50, 5, 50, BlockType.WATER_FLOW); // far away -> must dry up
        List<FluidSim.FluidBlock> sources = List.of(new FluidSim.FluidBlock(0, 1, 0, BlockType.WATER_SOURCE));
        List<FluidSim.FluidBlock> flows = List.of(
                new FluidSim.FluidBlock(1, 1, 0, BlockType.WATER_FLOW),
                new FluidSim.FluidBlock(50, 5, 50, BlockType.WATER_FLOW));
        FluidSim.Result r = FluidSim.compute(w, sources, flows);
        assertTrue(r.remove().contains(FluidSim.key(50, 5, 50)));
        assertFalse(r.remove().contains(FluidSim.key(1, 1, 0)));
    }

    @Test
    void existingFlowLevelRefreshesWhenTopologyChanges() {
        StubWorld w = flatGround();
        w.set(0, 1, 0, BlockType.WATER_SOURCE);
        for (int i = 0; i < FluidSim.WATER_FLOW_DISTANCE; i++) {
            tick(w);
        }
        assertEquals(7, w.getFluidLevel(7, 1, 0));
        // Place a second source at the far edge: cells beside it are now one step
        // away from a source, so their rendered level must refresh from ~7 to 1 -
        // otherwise the surface right next to the new source stays paper-thin.
        w.set(6, 1, 0, BlockType.WATER_SOURCE);
        tick(w);
        assertEquals(1, w.getFluidLevel(5, 1, 0), "cell next to the new source");
        assertEquals(1, w.getFluidLevel(7, 1, 0), "cell next to the new source");
    }

    @Test
    void removingTheSourceDriesTheWholePool() {
        StubWorld w = flatGround();
        w.set(0, 1, 0, BlockType.WATER_SOURCE);
        for (int i = 0; i < FluidSim.WATER_FLOW_DISTANCE; i++) {
            tick(w);
        }
        assertEquals(diamondCells(FluidSim.WATER_FLOW_DISTANCE), countFlows(w));
        w.set(0, 1, 0, BlockType.AIR); // break the source
        tick(w);
        assertEquals(0, countFlows(w), "no source, no flow");
    }
}
