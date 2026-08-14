package com.minecraftclone.world;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class FluidSimTest {

    /** A tiny in-memory world so the pure-logic fluid sim can be tested without GL. */
    private static final class StubWorld implements BlockAccessor {
        private final Map<Long, BlockType> blocks = new HashMap<>();

        void set(int x, int y, int z, BlockType t) {
            blocks.put(FluidSim.key(x, y, z), t);
        }

        @Override
        public BlockType getBlock(int x, int y, int z) {
            return blocks.getOrDefault(FluidSim.key(x, y, z), BlockType.AIR);
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

    @Test
    void waterSpreadsExactlySevenBlocksOnGround() {
        StubWorld w = flatGround();
        List<FluidSim.FluidBlock> sources = List.of(new FluidSim.FluidBlock(0, 1, 0, BlockType.WATER_SOURCE));
        FluidSim.Result r = FluidSim.compute(w, sources, List.of());
        assertEquals(diamondCells(FluidSim.WATER_FLOW_DISTANCE), r.fill().size());
        assertEquals(BlockType.WATER_FLOW, r.fill().get(FluidSim.key(7, 1, 0)));
        assertFalse(r.fill().containsKey(FluidSim.key(8, 1, 0)), "nothing past the flow distance");
        assertEquals(BlockType.WATER_FLOW, r.fill().get(FluidSim.key(-3, 1, 2)));
    }

    @Test
    void lavaSpreadsOnlyThreeBlocks() {
        StubWorld w = flatGround();
        List<FluidSim.FluidBlock> sources = List.of(new FluidSim.FluidBlock(0, 1, 0, BlockType.LAVA_SOURCE));
        FluidSim.Result r = FluidSim.compute(w, sources, List.of());
        assertEquals(diamondCells(FluidSim.LAVA_FLOW_DISTANCE), r.fill().size());
        assertFalse(r.fill().containsKey(FluidSim.key(4, 1, 0)));
    }

    @Test
    void sourcePoursDownUntilItHitsGround() {
        StubWorld w = flatGround();
        List<FluidSim.FluidBlock> sources = List.of(new FluidSim.FluidBlock(0, 3, 0, BlockType.WATER_SOURCE));
        FluidSim.Result r = FluidSim.compute(w, sources, List.of());
        // The two air cells in the column below the source, plus the flat spread.
        assertEquals(2 + diamondCells(FluidSim.WATER_FLOW_DISTANCE), r.fill().size());
        assertTrue(r.fill().containsKey(FluidSim.key(0, 2, 0)));
        assertTrue(r.fill().containsKey(FluidSim.key(0, 1, 0)));
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
}
