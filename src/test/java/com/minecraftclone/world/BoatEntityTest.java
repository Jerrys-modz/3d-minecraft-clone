package com.minecraftclone.world;

import com.minecraftclone.util.AABB;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link BoatEntity} — physics model, AABB, mount state, and
 * water-surface tracking.
 *
 * <p>Tests that need block data supply a small inline {@link BlockAccessor}
 * stub; tests that only exercise mechanics independent of the world pass
 * {@code null} (the boat then falls under gravity, which is also tested).
 */
class BoatEntityTest {

    // ------------------------------------------------------------------
    // Initial state
    // ------------------------------------------------------------------

    @Test
    void newBoatIsNotMounted() {
        BoatEntity boat = new BoatEntity(10f, 65f, 10f);
        assertFalse(boat.isMounted());
    }

    @Test
    void setMountedRoundTrips() {
        BoatEntity boat = new BoatEntity(0f, 0f, 0f);
        boat.setMounted(true);
        assertTrue(boat.isMounted());
        boat.setMounted(false);
        assertFalse(boat.isMounted());
    }

    @Test
    void positionIsSetOnConstruction() {
        BoatEntity boat = new BoatEntity(3f, 64f, 7f);
        assertEquals(3f,  boat.getPosition().x, 1e-5f);
        assertEquals(64f, boat.getPosition().y, 1e-5f);
        assertEquals(7f,  boat.getPosition().z, 1e-5f);
    }

    // ------------------------------------------------------------------
    // AABB
    // ------------------------------------------------------------------

    @Test
    void aabbEncompassesHullExtents() {
        BoatEntity boat = new BoatEntity(0f, 10f, 0f);
        AABB box = boat.aabb();
        assertEquals(-BoatEntity.HALF_W, box.minX, 1e-5f);
        assertEquals( BoatEntity.HALF_W, box.maxX, 1e-5f);
        assertEquals(10f,                box.minY, 1e-5f);
        assertEquals(10f + BoatEntity.HEIGHT, box.maxY, 1e-5f);
    }

    @Test
    void aabbMovesWithBoatPosition() {
        BoatEntity boat = new BoatEntity(5f, 12f, 3f);
        AABB box = boat.aabb();
        assertEquals(5f - BoatEntity.HALF_W, box.minX, 1e-5f);
        assertEquals(5f + BoatEntity.HALF_W, box.maxX, 1e-5f);
        assertEquals(12f, box.minY, 1e-5f);
    }

    @Test
    void mountYOffsetIsPositive() {
        BoatEntity boat = new BoatEntity(0f, 0f, 0f);
        assertTrue(boat.mountYOffset() > 0f);
    }

    // ------------------------------------------------------------------
    // Gravity when no water is below
    // ------------------------------------------------------------------

    @Test
    void boatFallsWhenNoWaterBelow() {
        // Pass a stub that returns only AIR — boat should fall.
        BlockAccessor allAir = (x, y, z) -> BlockType.AIR;
        BoatEntity boat = new BoatEntity(0f, 10f, 0f);
        float startY = boat.getPosition().y;
        boat.tick(1.0f, allAir); // 1 second with no water
        assertTrue(boat.getPosition().y < startY,
                "Boat should fall when there is no water below it");
    }

    @Test
    void boatFloorClampsPreventsNegativeY() {
        // Even with very long free-fall the boat should not go below Y = 0.
        BlockAccessor allAir = (x, y, z) -> BlockType.AIR;
        BoatEntity boat = new BoatEntity(0f, 0.1f, 0f);
        for (int i = 0; i < 100; i++) {
            boat.tick(0.1f, allAir);
        }
        assertTrue(boat.getPosition().y >= 0f,
                "Boat Y should never go below 0 (bedrock floor)");
    }

    // ------------------------------------------------------------------
    // Floating on water
    // ------------------------------------------------------------------

    @Test
    void boatFloatsToWaterSurface() {
        // One water block at y=63; boat starts at y=62 (below the surface).
        // The accessor returns WATER_SOURCE at y=63, AIR everywhere else.
        BlockAccessor stub = (x, y, z) -> (y == 63) ? BlockType.WATER_SOURCE : BlockType.AIR;
        BoatEntity boat = new BoatEntity(0f, 62f, 0f);

        // Run many small ticks; boat should converge to y = 64 (top of block 63).
        for (int i = 0; i < 200; i++) {
            boat.tick(0.05f, stub);
        }
        assertEquals(64f, boat.getPosition().y, 0.2f,
                "Boat should float at the top of the water block");
    }

    @Test
    void boatDropsToWaterSurfaceFromAbove() {
        // Water at y=63; boat starts at y=70 (above the water).
        BlockAccessor stub = (x, y, z) -> (y == 63) ? BlockType.WATER_SOURCE : BlockType.AIR;
        BoatEntity boat = new BoatEntity(0f, 70f, 0f);

        for (int i = 0; i < 200; i++) {
            boat.tick(0.05f, stub);
        }
        assertEquals(64f, boat.getPosition().y, 0.5f,
                "Boat dropped from above should settle on the water surface");
    }

    @Test
    void boatDoesNotFloatOnLava() {
        BlockAccessor lava = (x, y, z) -> (y == 63) ? BlockType.LAVA_SOURCE : BlockType.AIR;
        BoatEntity boat = new BoatEntity(0f, 64f, 0f);

        boat.tick(0.05f, lava);

        assertTrue(boat.getPosition().y < 64f, "Lava must not act as a flotation surface");
    }

    // ------------------------------------------------------------------
    // Block collision
    // ------------------------------------------------------------------

    @Test
    void boatStopsAtWallAlongXWithoutBlockingZ() {
        BlockAccessor blocks = (x, y, z) -> {
            if (x == 1 && y == 64) return BlockType.STONE;
            return y == 63 ? BlockType.WATER_SOURCE : BlockType.AIR;
        };
        BoatEntity boat = new BoatEntity(0f, 64f, 0f);

        for (int i = 0; i < 80; i++) {
            boat.tick(0.05f, true, false, false, true, 1f, 0f, blocks);
        }

        assertTrue(boat.aabb().maxX <= 1.0001f, "Boat must not enter the wall on X");
        assertTrue(boat.getPosition().z > 0.5f, "Unblocked Z movement should be preserved");
    }

    @Test
    void boatStopsAtWallAlongZ() {
        BlockAccessor blocks = (x, y, z) -> {
            if (z == 1 && y == 64) return BlockType.STONE;
            return y == 63 ? BlockType.WATER_SOURCE : BlockType.AIR;
        };
        BoatEntity boat = new BoatEntity(0f, 64f, 0f);

        for (int i = 0; i < 80; i++) {
            boat.tick(0.05f, true, false, false, false, 0f, 1f, blocks);
        }

        assertTrue(boat.aabb().maxZ <= 1.0001f, "Boat must not enter the wall on Z");
    }

    @Test
    void boatStopsOnSolidGroundAlongY() {
        BlockAccessor ground = (x, y, z) -> y == 0 ? BlockType.STONE : BlockType.AIR;
        BoatEntity boat = new BoatEntity(0f, 1f, 0f);

        for (int i = 0; i < 20; i++) boat.tick(0.05f, ground);

        assertEquals(1f, boat.getPosition().y, 1e-5f,
                "Boat hull should stop at the ground's top surface");
    }

    // ------------------------------------------------------------------
    // Steering mechanics
    // ------------------------------------------------------------------

    @Test
    void forwardKeyAcceleratesBoatInCameraDirection() {
        // No water: gravity pulls, but we check horizontal displacement.
        BlockAccessor stub = (x, y, z) -> (y == 63) ? BlockType.WATER_SOURCE : BlockType.AIR;
        BoatEntity boat = new BoatEntity(0f, 64f, 0f);

        // Let it settle first.
        for (int i = 0; i < 50; i++) boat.tick(0.05f, stub);

        float startX = boat.getPosition().x;
        float startZ = boat.getPosition().z;

        // Face toward +X (frontX=1, frontZ=0) and press W.
        for (int i = 0; i < 20; i++) {
            boat.tick(0.05f, true, false, false, false, 1f, 0f, stub);
        }

        float dx = boat.getPosition().x - startX;
        float dz = boat.getPosition().z - startZ;
        assertTrue(dx > 0.5f, "Boat should have moved significantly in +X: actual dx=" + dx);
        assertTrue(Math.abs(dz) < Math.abs(dx),
                "Z drift should be small compared to X displacement");
    }

    @Test
    void noSteeringKeyLeavesBoatStationary() {
        // Water stub so the boat stays on the surface.
        BlockAccessor stub = (x, y, z) -> (y == 63) ? BlockType.WATER_SOURCE : BlockType.AIR;
        BoatEntity boat = new BoatEntity(5f, 64f, 5f);

        // Settle.
        for (int i = 0; i < 50; i++) boat.tick(0.05f, stub);

        float startX = boat.getPosition().x;
        float startZ = boat.getPosition().z;

        // Tick for a full second with no steering keys.
        for (int i = 0; i < 20; i++) {
            boat.tick(0.05f, false, false, false, false, 1f, 0f, stub);
        }

        assertEquals(startX, boat.getPosition().x, 0.05f,
                "Stationary boat should not drift in X");
        assertEquals(startZ, boat.getPosition().z, 0.05f,
                "Stationary boat should not drift in Z");
    }

    @Test
    void dragSlowsDownBoatAfterReleasingKey() {
        BlockAccessor stub = (x, y, z) -> (y == 63) ? BlockType.WATER_SOURCE : BlockType.AIR;
        BoatEntity boat = new BoatEntity(0f, 64f, 0f);

        // Accelerate for 1 second.
        for (int i = 0; i < 20; i++) {
            boat.tick(0.05f, true, false, false, false, 1f, 0f, stub);
        }
        float speedAfterAccel = Math.abs(boat.getPosition().x);

        // Now coast for 2 seconds with no input.
        float posAfterAccel = boat.getPosition().x;
        for (int i = 0; i < 40; i++) {
            boat.tick(0.05f, false, false, false, false, 1f, 0f, stub);
        }
        float speedAfterCoast = Math.abs(boat.getPosition().x - posAfterAccel) / 2f;

        // The boat should have decelerated (average speed while coasting < final speed after accelerating).
        assertTrue(speedAfterCoast < speedAfterAccel,
                "Boat should lose speed when no key is held (drag)");
    }

    // ------------------------------------------------------------------
    // Crafting recipe sanity (OAK_BOAT exists as a registered BlockType)
    // ------------------------------------------------------------------

    @Test
    void oakBoatBlockTypeExists() {
        // Verifies the enum constant was added without error.
        BlockType boat = BlockType.OAK_BOAT;
        assertNotNull(boat);
        assertTrue(boat.isItem, "OAK_BOAT should be an item-only type (no placed tile)");
    }

    @Test
    void oakBoatRecipeYieldsOneBoat() {
        // The crafting recipe P.P / PPP / ... should produce exactly 1 OAK_BOAT.
        // Grid is 3x3; P = PLANKS, . = null.
        BlockType P = BlockType.PLANKS;
        BlockType[] grid = {
            P, null, P,
            P,    P, P,
            null, null, null
        };
        var result = com.minecraftclone.player.Crafting.match3x3(grid);
        assertNotNull(result, "Crafting P.P/PPP/... should yield a result");
        assertEquals(BlockType.OAK_BOAT, result.output());
        assertEquals(1, result.outputAmount());
    }
}
