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

    @Test
    void furnaceIsDirectionalWithDistinctFrontTile() {
        assertTrue(BlockType.FURNACE.isDirectional());
        // The furnace's front (mouth) tile differs from its plain stone sides.
        assertTrue(BlockType.FURNACE.frontTile != BlockType.FURNACE.sideTile);
        assertEquals(BlockType.FURNACE.sideTile, BlockType.STONE.topTile, "sides use plain stone");
        // Ordinary cubes aren't directional.
        assertFalse(BlockType.STONE.isDirectional());
        assertFalse(BlockType.DIRT.isDirectional());
    }

    @Test
    void canHoldSnowOnOpaqueCubesButNotTransparentOrCross() {
        // Ground, stone, wood, sand and snow itself all take a snow coat.
        assertTrue(BlockType.GRASS.canHoldSnow());
        assertTrue(BlockType.DIRT.canHoldSnow());
        assertTrue(BlockType.STONE.canHoldSnow());
        assertTrue(BlockType.WOOD_LOG.canHoldSnow());
        assertTrue(BlockType.SAND.canHoldSnow());
        assertTrue(BlockType.SNOW.canHoldSnow(), "snow piles on snow (deep drifts)");
        // See-through, cross-shaped plants and fluids don't hold snow.
        assertFalse(BlockType.GLASS.canHoldSnow());
        assertFalse(BlockType.ICE.canHoldSnow());
        assertFalse(BlockType.LEAVES.canHoldSnow());
        assertFalse(BlockType.TALL_GRASS.canHoldSnow());
        assertFalse(BlockType.TORCH.canHoldSnow());
        assertFalse(BlockType.WATER.canHoldSnow());
        // Slabs hold snow too - it caps them flush (see World.tryAddSnow / Chunk.emitSnowySlab).
        assertTrue(BlockType.STONE_SLAB.canHoldSnow());
        assertTrue(BlockType.PLANKS_SLAB.canHoldSnow());
        assertTrue(BlockType.SNOWY_STONE_SLAB.isSnowCappedSlab());
        assertTrue(BlockType.SNOWY_PLANKS_SLAB.isSnowCappedSlab());
        assertFalse(BlockType.STONE_SLAB.isSnowCappedSlab());
    }

    @Test
    void fireIsABrightNonSolidCross() {
        assertTrue(BlockType.FIRE.cross, "fire renders as a crossed-plane flame");
        assertFalse(BlockType.FIRE.solid, "fire never collides");
        assertEquals(14, BlockType.FIRE.lightLevel, "a lightning-lit flame glows brightly");
    }

    @Test
    void stairsArePartialCubesWithFullCellCollision() {
        assertTrue(BlockType.STONE_STAIRS.isStair());
        assertTrue(BlockType.PLANKS_STAIRS.isStair());
        assertFalse(BlockType.STONE.isStair());
        assertFalse(BlockType.WOODEN_FENCE.isStair());
        // Stairs are solid (collide) and occupy the full cell.
        assertTrue(BlockType.STONE_STAIRS.solid);
        assertEquals(1f, BlockType.STONE_STAIRS.collisionHeight, 1e-6f);
        // They reuse their material's tiles.
        assertEquals(BlockType.STONE.topTile, BlockType.STONE_STAIRS.topTile);
        assertEquals(BlockType.PLANKS.topTile, BlockType.PLANKS_STAIRS.topTile);
    }

    @Test
    void fenceIsAPartialCubeThatAutoConnects() {
        assertTrue(BlockType.WOODEN_FENCE.isFence());
        assertFalse(BlockType.PLANKS.isFence());
        assertFalse(BlockType.STONE_STAIRS.isFence());
        assertTrue(BlockType.WOODEN_FENCE.solid);
        assertTrue(BlockType.WOODEN_FENCE.isPartialCube());
    }

    @Test
    void stairsAndFenceHoldSnowLikeOtherSolids() {
        assertTrue(BlockType.STONE_STAIRS.canHoldSnow());
        assertTrue(BlockType.PLANKS_STAIRS.canHoldSnow());
        assertTrue(BlockType.WOODEN_FENCE.canHoldSnow());
    }
}
