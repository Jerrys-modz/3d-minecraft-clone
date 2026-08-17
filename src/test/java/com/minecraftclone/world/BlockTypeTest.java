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
    void heatSourcesAreFireLampsTorchesAndLava() {
        assertTrue(BlockType.FIRE.isHeatSource());
        assertTrue(BlockType.TORCH.isHeatSource());
        assertTrue(BlockType.LAMP.isHeatSource());
        assertTrue(BlockType.LAVA.isHeatSource());
        assertTrue(BlockType.LAVA_SOURCE.isHeatSource());
        // A furnace only radiates heat while it's actively burning (checked live
        // in Player.hasHeatSource via World.isBlockActive); cold stone warms nothing.
        assertFalse(BlockType.FURNACE.isHeatSource());
        // Ordinary blocks radiate no heat - a plain sealed room stays cold.
        assertFalse(BlockType.PLANKS.isHeatSource());
        assertFalse(BlockType.STONE.isHeatSource());
        assertFalse(BlockType.WATER.isHeatSource());
        assertFalse(BlockType.WOOL.isHeatSource());
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

    @Test
    void stairsHaveSteppedCollisionBoxes() {
        // Facing +Z (orientation 0): raised step on the -Z half.
        com.minecraftclone.util.AABB[] boxes = BlockType.STONE_STAIRS.collisionBoxes(0, 0, 0, (byte) 0);
        assertEquals(2, boxes.length);
        // Low step: full cell, half height.
        assertEquals(0f, boxes[0].minY, 1e-6f);
        assertEquals(0.5f, boxes[0].maxY, 1e-6f);
        assertEquals(0f, boxes[0].minZ, 1e-6f);
        assertEquals(1f, boxes[0].maxZ, 1e-6f);
        // Raised step: back (-Z) half, half to full height.
        assertEquals(0.5f, boxes[1].minY, 1e-6f);
        assertEquals(1f, boxes[1].maxY, 1e-6f);
        assertEquals(0f, boxes[1].minZ, 1e-6f);
        assertEquals(0.5f, boxes[1].maxZ, 1e-6f);

        // Facing -Z (orientation 1): raised step on the +Z half.
        boxes = BlockType.STONE_STAIRS.collisionBoxes(0, 0, 0, (byte) 1);
        assertEquals(0.5f, boxes[1].minZ, 1e-6f);
        assertEquals(1f, boxes[1].maxZ, 1e-6f);
    }

    @Test
    void fenceCollisionIsTallEnoughToStopJumps() {
        // A fence occupies its cell as a 1.5-block-tall box - taller than the
        // ~1.4-block jump, so neither player nor mobs can hop over it.
        com.minecraftclone.util.AABB[] boxes = BlockType.WOODEN_FENCE.collisionBoxes(0, 0, 0, (byte) 0);
        assertEquals(1, boxes.length);
        assertEquals(1.5f, boxes[0].maxY, 1e-6f);
        assertTrue(boxes[0].maxY > 1.4f, "fence must be taller than the player's jump");
    }

    @Test
    void fullCubesHaveSingleCollisionBox() {
        com.minecraftclone.util.AABB[] boxes = BlockType.STONE.collisionBoxes(3, 4, 5, (byte) 0);
        assertEquals(1, boxes.length);
        assertEquals(3, boxes[0].minX, 1e-6f);
        assertEquals(4, boxes[0].minY, 1e-6f);
        assertEquals(4f + BlockType.STONE.collisionHeight, boxes[0].maxY, 1e-6f);
    }
}
