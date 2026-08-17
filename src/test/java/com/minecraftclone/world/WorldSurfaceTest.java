package com.minecraftclone.world;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The pure block-transformation rules behind seasonal surface changes: which
 * water cells freeze over and which ice thaws back (see {@link World#frozenForm}
 * and {@link World#thawedForm}). The temperature gating and column probing that
 * drive them live in {@link World#updateSeasonalSurfaces}, which needs a live
 * OpenGL world and isn't unit-testable headlessly.
 */
class WorldSurfaceTest {

    @Test
    void exposedStillWaterFreezesToIce() {
        assertEquals(BlockType.ICE, World.frozenForm(BlockType.WATER), "terrain lakes freeze");
        assertEquals(BlockType.ICE, World.frozenForm(BlockType.WATER_SOURCE), "water sources freeze");
        // Flowing water, lava and non-water are all left alone.
        assertEquals(BlockType.WATER_FLOW, World.frozenForm(BlockType.WATER_FLOW), "flowing water doesn't freeze");
        assertEquals(BlockType.LAVA, World.frozenForm(BlockType.LAVA), "lava doesn't freeze");
        assertEquals(BlockType.STONE, World.frozenForm(BlockType.STONE));
    }

    @Test
    void surfaceIceThawsToWater() {
        assertEquals(BlockType.WATER, World.thawedForm(BlockType.ICE), "surface ice melts back to water");
        // Packed ice (igloo blocks) and everything else is unaffected.
        assertEquals(BlockType.PACKED_ICE, World.thawedForm(BlockType.PACKED_ICE));
        assertEquals(BlockType.SNOW, World.thawedForm(BlockType.SNOW));
        assertEquals(BlockType.STONE, World.thawedForm(BlockType.STONE));
    }

    @Test
    void lightningFireSpreadsOnlyToFlammableBlocks() {
        assertTrue(World.isFlammable(BlockType.WOOD_LOG));
        assertTrue(World.isFlammable(BlockType.PLANKS));
        assertTrue(World.isFlammable(BlockType.LEAVES));
        assertTrue(World.isFlammable(BlockType.CHERRY_LEAVES));
        // Stone, dirt, sand, snow and ice don't burn.
        assertFalse(World.isFlammable(BlockType.STONE));
        assertFalse(World.isFlammable(BlockType.DIRT));
        assertFalse(World.isFlammable(BlockType.SAND));
        assertFalse(World.isFlammable(BlockType.SNOW));
        assertFalse(World.isFlammable(BlockType.ICE));
    }
}
