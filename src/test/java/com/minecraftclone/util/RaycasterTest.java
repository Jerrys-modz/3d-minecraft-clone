package com.minecraftclone.util;

import com.minecraftclone.world.BlockAccessor;
import com.minecraftclone.world.BlockType;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class RaycasterTest {

    /** A sparse voxel grid for tests - unset cells default to AIR. */
    private static class FakeWorld implements BlockAccessor {
        private final Map<Long, BlockType> blocks = new HashMap<>();

        void set(int x, int y, int z, BlockType type) {
            blocks.put(key(x, y, z), type);
        }

        @Override
        public BlockType getBlock(int x, int y, int z) {
            return blocks.getOrDefault(key(x, y, z), BlockType.AIR);
        }

        private static long key(int x, int y, int z) {
            return ((long) x & 0x1FFFFFL) | (((long) y & 0x1FFFFFL) << 21) | (((long) z & 0x1FFFFFL) << 42);
        }
    }

    @Test
    void raycastFromInsideWaterSourceReachesASolidBlockAhead() {
        // Regression: WATER_SOURCE used to be excluded from isPassThrough(),
        // so standing inside one (extremely common near any mined ocean
        // shoreline - see World#promoteIfStaticFluid) made the raycast treat
        // the player's own eye position as "embedded in solid" instead of
        // actually looking through the water to what's ahead.
        FakeWorld world = new FakeWorld();
        for (int x = -5; x <= 5; x++) {
            for (int y = -5; y <= 5; y++) {
                for (int z = 0; z <= 10; z++) {
                    world.set(x, y, z, BlockType.WATER_SOURCE);
                }
            }
        }
        world.set(0, 0, 5, BlockType.STONE);

        Raycaster.Hit hit = Raycaster.cast(world, new Vector3f(0, 0, 0), new Vector3f(0, 0, 1), 20f);

        assertNotNull(hit);
        assertEquals(0, hit.blockPos.x);
        assertEquals(0, hit.blockPos.y);
        assertEquals(5, hit.blockPos.z, "reaches the actual stone block, not a degenerate hit at the origin");
    }

    @Test
    void raycastFromInsideSolidGroundStillReportsTheDegenerateOriginHit() {
        // Unaffected control case: genuinely embedded in solid rock (not a
        // fluid) should still hit immediately at the origin.
        FakeWorld world = new FakeWorld();
        world.set(0, 0, 0, BlockType.STONE);

        Raycaster.Hit hit = Raycaster.cast(world, new Vector3f(0.5f, 0.5f, 0.5f), new Vector3f(0, 0, 1), 20f);

        assertNotNull(hit);
        assertEquals(0, hit.blockPos.x);
        assertEquals(0, hit.blockPos.y);
        assertEquals(0, hit.blockPos.z);
    }

    @Test
    void raycastThroughOpenAirFindsNothingWithinRange() {
        FakeWorld world = new FakeWorld();
        Raycaster.Hit hit = Raycaster.cast(world, new Vector3f(0, 0, 0), new Vector3f(0, 0, 1), 5f);
        assertNull(hit);
    }
}
