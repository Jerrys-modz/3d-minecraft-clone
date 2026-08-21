package com.minecraftclone.world;

import com.minecraftclone.engine.graphics.TextureAtlas;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
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
        assertFalse(BlockType.WATER.isTranslucent(), "fluids use emitFluid, not the glass cube path");
        assertFalse(BlockType.WATER_FLOW.isTranslucent());
        assertFalse(BlockType.LAVA.isTranslucent());
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
    void oakAndCherryAreBothLeaves() {
        assertTrue(BlockType.LEAVES.isLeaves());
        assertTrue(BlockType.CHERRY_LEAVES.isLeaves());
        assertFalse(BlockType.WOOD_LOG.isLeaves());
        assertFalse(BlockType.TALL_GRASS.isLeaves());
    }

    @Test
    void chestIsDirectionalWithDistinctLidFrontAndSides() {
        assertTrue(BlockType.CHEST.isDirectional());
        assertTrue(BlockType.CHEST.frontTile != BlockType.CHEST.sideTile);
        assertTrue(BlockType.CHEST.topTile != BlockType.CHEST.sideTile);
        assertTrue(BlockType.CHEST.bottomTile != BlockType.CHEST.topTile);
        assertEquals(TextureAtlas.CHEST_TILE, BlockType.CHEST.frontTile);
        assertFalse(BlockType.BARREL.isDirectional(), "barrels stay a single undirected stash");
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
    void farmlandSidesAreDirtNotGrassFringe() {
        // Atlas tile 2 is the grass side (dirt + green fringe). Tile 3 is dirt.
        assertEquals(BlockType.DIRT.sideTile, BlockType.FARMLAND.sideTile);
        assertEquals(BlockType.DIRT.bottomTile, BlockType.FARMLAND.bottomTile);
        assertEquals(BlockType.DIRT.sideTile, BlockType.FARMLAND_WET.sideTile);
        assertEquals(BlockType.DIRT.bottomTile, BlockType.FARMLAND_WET.bottomTile);
        assertEquals(3, BlockType.FARMLAND.sideTile);
        assertNotEquals(BlockType.GRASS.sideTile, BlockType.FARMLAND.sideTile,
                "farmland must not reuse the grass-fringe side tile");
        assertNotEquals(BlockType.FARMLAND.topTile, BlockType.FARMLAND.sideTile);
    }

    @Test
    void boneMealIsAnInventoryItem() {
        assertTrue(BlockType.BONE_MEAL.isItem);
        assertTrue(BlockType.BONE_MEAL.isBoneMeal());
        assertFalse(BlockType.BONES.isBoneMeal());
        assertEquals("Bone Meal", BlockType.BONE_MEAL.displayName());
        assertEquals(455, BlockType.BONE_MEAL.id);
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

    @Test
    void fluidSideUvsScaleWithFaceHeight() {
        float[][] uvs = {{0f, 1f}, {1f, 1f}, {1f, 0f}, {0f, 0f}};
        float[][] full = Chunk.fluidSideUvs(uvs, 10f, 11f, 11f);
        assertEquals(0f, full[2][1], 0.001f, "full-height face uses the full tile");
        assertEquals(0f, full[3][1], 0.001f);
        float[][] shallow = Chunk.fluidSideUvs(uvs, 10f, 10.25f, 10.25f);
        assertEquals(0.75f, shallow[2][1], 0.001f,
                "quarter-height stream should use a quarter of the tile, not squash it");
        assertEquals(1f, shallow[0][1], 0.001f);
    }

    @Test
    void waterfallLandingAlwaysGetsATopFace() {
        // A puddle the fall just landed on still needs a top, otherwise the
        // pool holes through to dirt.
        assertTrue(Chunk.shouldEmitFluidTop(true, false, 10.5f, 10f));
        assertTrue(Chunk.shouldEmitFluidTop(true, false, 10f + 0.86f, 10f),
                "fresh landing at FLOW_TOP_NEAR still needs a surface");
        // Mid-column at full height: no extra slab floating in the shaft.
        assertFalse(Chunk.shouldEmitFluidTop(true, true, 11f, 10f));
        // Surface puddle (nothing above) with lowered corners.
        assertTrue(Chunk.shouldEmitFluidTop(false, false, 10.4f, 10f));
        // A water source / ocean cell at 0.9 with more water on top must NOT
        // emit a top — that's the sheet you saw while swimming.
        assertFalse(Chunk.shouldEmitFluidTop(true, true, 10.9f, 10f));
        assertFalse(Chunk.shouldEmitFluidTop(true, false, 10.9f, 10f));
    }

    @Test
    void onlyRealWaterfallsAreFullHeightColumns() {
        assertTrue(Chunk.isContinuingFall(BlockType.AIR, BlockType.AIR),
                "2+ blocks of air is a waterfall");
        assertTrue(Chunk.isContinuingFall(BlockType.WATER_FLOW, BlockType.WATER_FLOW),
                "stacked falling water is a column");
        assertTrue(Chunk.isContinuingFall(BlockType.WATER_FLOW, BlockType.AIR));
        assertFalse(Chunk.isContinuingFall(BlockType.WATER_FLOW, BlockType.GRASS),
                "sitting on a landing puddle over dirt is a stream step, not a cube");
        assertFalse(Chunk.isContinuingFall(BlockType.AIR, BlockType.STONE),
                "1-block drop onto solid isn't a hanging cube");
        assertFalse(Chunk.isDropThrough(BlockType.GRASS));
        assertTrue(Chunk.isDropThrough(BlockType.WATER_FLOW));
    }
}
