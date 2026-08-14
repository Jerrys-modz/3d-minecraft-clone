package com.minecraftclone.world;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Covers {@link BlockType#promotedFluidSource()}, the decision behind
 * World#setBlock's fix for "breaking a block next to natural water/lava
 * doesn't make it flow": static (world-gen) WATER/LAVA is deliberately left
 * out of FluidSim's tracked set for performance (an entire ocean shouldn't be
 * rescanned every tick), so the boundary block that ends up next to a fresh
 * opening needs to be promoted to a real, tracked source there instead.
 */
class FluidPromotionTest {

    @Test
    void staticWaterPromotesToWaterSource() {
        assertEquals(BlockType.WATER_SOURCE, BlockType.WATER.promotedFluidSource());
    }

    @Test
    void staticLavaPromotesToLavaSource() {
        assertEquals(BlockType.LAVA_SOURCE, BlockType.LAVA.promotedFluidSource());
    }

    @Test
    void alreadyTrackedFluidIsNotRepromoted() {
        // Already-tracked blocks have nothing to promote to - re-triggering setBlock
        // on them (e.g. a flow spreading next to another flow) must be a no-op, not
        // an infinite/self-referential promotion.
        assertNull(BlockType.WATER_SOURCE.promotedFluidSource());
        assertNull(BlockType.WATER_FLOW.promotedFluidSource());
        assertNull(BlockType.LAVA_SOURCE.promotedFluidSource());
        assertNull(BlockType.LAVA_FLOW.promotedFluidSource());
    }

    @Test
    void nonFluidBlocksHaveNoPromotion() {
        assertNull(BlockType.STONE.promotedFluidSource());
        assertNull(BlockType.AIR.promotedFluidSource());
        assertNull(BlockType.TORCH.promotedFluidSource());
    }
}
