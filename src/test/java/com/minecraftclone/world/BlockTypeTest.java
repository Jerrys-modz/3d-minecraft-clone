package com.minecraftclone.world;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlockTypeTest {

    @Test
    void displayNamesAreHumanized() {
        assertEquals("Diamond Pickaxe", BlockType.DIAMOND_PICKAXE.displayName());
        assertEquals("Wood Log", BlockType.WOOD_LOG.displayName());
        assertEquals("Stone Slab", BlockType.STONE_SLAB.displayName());
        assertEquals("Water Source", BlockType.WATER_SOURCE.displayName());
        assertEquals("Tall Grass", BlockType.TALL_GRASS.displayName());
    }

    @Test
    void glassAndIceAreTranslucent() {
        assertTrue(BlockType.GLASS.isTranslucent());
        assertTrue(BlockType.ICE.isTranslucent());
        assertFalse(BlockType.STONE.isTranslucent());
    }
}
