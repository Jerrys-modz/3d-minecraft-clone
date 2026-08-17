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

    /**
     * Regression test for a constructor bug: the plain full-cube delegate used
     * to pass its own sideTile (atlas tile index) into the foodValue slot
     * instead of 0, so isEdible() (foodValue > 0) came back true for nearly
     * every ordinary block. In survival, right-clicking one of these then ran
     * Main's eat-branch instead of the place-branch - the item vanished from
     * the hotbar via Player.eat() and no block ever appeared in the world.
     */
    @Test
    void ordinaryCubeBlocksAreNotEdible() {
        assertFalse(BlockType.DIRT.isEdible());
        assertFalse(BlockType.STONE.isEdible());
        assertFalse(BlockType.PLANKS.isEdible());
        assertFalse(BlockType.GRASS.isEdible());
        assertFalse(BlockType.SAND.isEdible());
        assertEquals(0, BlockType.DIRT.foodValue);
        assertEquals(0, BlockType.STONE.foodValue);
        // Real food still works - foraged items keep their nonzero value.
        assertTrue(BlockType.APPLE.isEdible());
    }

    /**
     * Regression test: isPassThrough() used to list WATER and WATER_FLOW but
     * not WATER_SOURCE (or LAVA/LAVA_SOURCE, only LAVA_FLOW) - despite all
     * three water variants (and all three lava variants) being rendered
     * identically (same tile, same non-solid/translucent flags, same
     * Chunk#emitFluid mesh path). Breaking a block next to natural ocean
     * water promotes the boundary cell from WATER to WATER_SOURCE (see
     * World#promoteIfStaticFluid) - extremely common near any player-touched
     * shoreline - so swimming through one of those cells made Raycaster.cast
     * treat the camera's own eye position as "embedded in solid" every
     * frame, aiming the block-outline highlight at the camera itself: a
     * glitchy wireframe-in-the-water "x-ray" look.
     */
    @Test
    void everyFluidVariantIsPassThroughLikeItsSiblings() {
        assertTrue(BlockType.WATER.isPassThrough());
        assertTrue(BlockType.WATER_SOURCE.isPassThrough());
        assertTrue(BlockType.WATER_FLOW.isPassThrough());
        assertTrue(BlockType.LAVA.isPassThrough());
        assertTrue(BlockType.LAVA_SOURCE.isPassThrough());
        assertTrue(BlockType.LAVA_FLOW.isPassThrough());
        assertTrue(BlockType.AIR.isPassThrough());
        assertFalse(BlockType.STONE.isPassThrough());
    }
}
