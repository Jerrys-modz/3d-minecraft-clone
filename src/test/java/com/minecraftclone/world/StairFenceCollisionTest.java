package com.minecraftclone.world;

import com.minecraftclone.util.AABB;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the collision boxes that drive stairs/fence interaction: a player's
 * feet-box can step onto a stair's low step, a fence blocks the full jump
 * height, and a normal wall still blocks everything.
 */
class StairFenceCollisionTest {

    private static final class Stub implements BlockAccessor {
        final Map<Long, BlockType> blocks = new HashMap<>();
        final Map<Long, Byte> orientations = new HashMap<>();

        private static long k(int x, int y, int z) {
            return ((long) (x + 2048) << 40) | ((long) y << 32) | ((long) (z + 2048) << 20);
        }

        @Override
        public BlockType getBlock(int x, int y, int z) {
            return blocks.getOrDefault(k(x, y, z), BlockType.AIR);
        }

        @Override
        public byte getBlockOrientation(int x, int y, int z) {
            return orientations.getOrDefault(k(x, y, z), (byte) 0);
        }
    }

    private boolean collides(Stub s, AABB box) {
        int minX = (int) Math.floor(box.minX), maxX = (int) Math.floor(box.maxX - 1e-4f);
        int minY = (int) Math.floor(box.minY), maxY = (int) Math.floor(box.maxY - 1e-4f);
        int minZ = (int) Math.floor(box.minZ), maxZ = (int) Math.floor(box.maxZ - 1e-4f);
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    if (blockCollides(s, box, x, y, z)) return true;
                    // Tall blocks (a fence's 1.5-high box) extend up from the cell below.
                    if (blockCollides(s, box, x, y - 1, z)) return true;
                }
            }
        }
        return false;
    }

    private static boolean blockCollides(Stub s, AABB box, int x, int y, int z) {
        BlockType t = s.getBlock(x, y, z);
        if (!t.isCollidable()) return false;
        for (AABB b : t.collisionBoxes(x, y, z, s.getBlockOrientation(x, y, z))) {
            if (box.intersects(b)) return true;
        }
        return false;
    }

    @Test
    void playerCanStepOntoTheLowStairStep() {
        Stub s = new Stub();
        s.blocks.put(Stub.k(5, 0, 5), BlockType.STONE_STAIRS); // facing +Z
        // Feet box on the ground right in front of the low step.
        AABB feetAtGround = new AABB(4.2f, 0f, 4.2f, 4.8f, 0.6f, 4.8f);
        assertFalse(collides(s, feetAtGround), "ground in front of the stair is free");
        // The low step occupies the front (+Z) half at 0..0.5 - the player's box
        // reaching into it collides, so walking into it stops...
        AABB intoLowStep = new AABB(4.9f, 0f, 4.5f, 5.4f, 0.6f, 5.5f);
        assertTrue(collides(s, intoLowStep), "walking into the low step collides");
        // ...but a step-up (feet raised to 0.5) clears the low step and lets the
        // player stand on its front (+Z) half, away from the raised back half.
        AABB onLowStep = new AABB(4.9f, 0.5f, 5.7f, 5.4f, 1.1f, 6.0f);
        assertFalse(collides(s, onLowStep), "standing on the low step's front half clears the boxes");
        // The raised back half still blocks at low height.
        AABB throughBack = new AABB(4.9f, 0.5f, 4.5f, 5.4f, 1.1f, 5.4f);
        assertTrue(collides(s, throughBack), "the raised back step still collides at its height");
    }

    @Test
    void fenceBlocksTheFullJumpHeight() {
        Stub s = new Stub();
        s.blocks.put(Stub.k(5, 0, 5), BlockType.WOODEN_FENCE);
        // A player jumping: feet from 0 up to ~1.4. Any feet box up to 1.4 still
        // collides with the 1.5-tall fence, so you cannot jump over it.
        for (float feet = 0f; feet <= 1.4f; feet += 0.2f) {
            AABB box = new AABB(4.9f, feet, 4.9f, 5.4f, feet + 1.0f, 5.4f);
            assertTrue(collides(s, box), "feet at " + feet + " must collide with the fence");
        }
        // Only above the fence top is it clear.
        AABB aboveFence = new AABB(4.9f, 1.51f, 4.9f, 5.4f, 2.0f, 5.4f);
        assertFalse(collides(s, aboveFence), "above the fence is clear");
    }

    @Test
    void fullWallStillBlocksEverything() {
        Stub s = new Stub();
        s.blocks.put(Stub.k(5, 0, 5), BlockType.STONE);
        AABB low = new AABB(4.9f, 0f, 4.9f, 5.4f, 0.6f, 5.4f);
        assertTrue(collides(s, low), "a full wall blocks at ground level");
        AABB high = new AABB(4.9f, 0.5f, 4.9f, 5.4f, 1.1f, 5.4f);
        assertTrue(collides(s, high), "a full wall blocks at step height too");
    }
}
