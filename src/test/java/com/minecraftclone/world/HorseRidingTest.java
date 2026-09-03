package com.minecraftclone.world;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for horse/animal riding:
 * {@link Mob.Type#HORSE}, the {@code SADDLE} item, saddle/mount state,
 * {@link Mob#rideTick}, and the matching crafting recipe.
 */
class HorseRidingTest {

    private static final float DT = 1f / 30f;

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /** Minimal BlockAccessor backed by an in-memory map. */
    private static final class StubWorld implements BlockAccessor {
        private final Map<Long, BlockType> blocks = new HashMap<>();

        void set(int x, int y, int z, BlockType t) {
            blocks.put(FluidSim.key(x, y, z), t);
        }

        @Override
        public BlockType getBlock(int x, int y, int z) {
            return blocks.getOrDefault(FluidSim.key(x, y, z), BlockType.AIR);
        }
    }

    /** Flat grass surface large enough that the horse never runs off the edge. */
    private static StubWorld flatGround(int radius) {
        StubWorld w = new StubWorld();
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                w.set(x, 0, z, BlockType.GRASS);
            }
        }
        return w;
    }

    /** Spawn a horse resting on the ground (body-centre at ground + height/2). */
    private static Mob horseOnGround() {
        float groundTop = 1f; // GRASS top at y = 1
        float cy = groundTop + Mob.Type.HORSE.height / 2f;
        return new Mob(Mob.Type.HORSE, 0f, cy, 0f);
    }

    // ------------------------------------------------------------------
    // SADDLE item type
    // ------------------------------------------------------------------

    @Test
    void saddleBlockTypeExists() {
        assertNotNull(BlockType.SADDLE, "SADDLE must be registered in BlockType");
    }

    @Test
    void saddleIsAnItem() {
        assertTrue(BlockType.SADDLE.isItem, "SADDLE should be an item-only type (not a placed tile)");
    }

    // ------------------------------------------------------------------
    // HORSE mob type
    // ------------------------------------------------------------------

    @Test
    void horseMobTypeExists() {
        Mob.Type horse = Mob.Type.HORSE;
        assertNotNull(horse, "HORSE must be registered in Mob.Type");
    }

    @Test
    void horseIsNotHostile() {
        assertFalse(Mob.Type.HORSE.hostile, "HORSE must not be hostile");
    }

    @Test
    void horseHasPositiveDimensions() {
        assertTrue(Mob.Type.HORSE.width > 0f,  "HORSE width must be positive");
        assertTrue(Mob.Type.HORSE.height > 0f, "HORSE height must be positive");
    }

    @Test
    void horseHasPositiveMaxHealth() {
        assertTrue(Mob.Type.HORSE.maxHealth > 0f, "HORSE max-health must be positive");
    }

    // ------------------------------------------------------------------
    // Saddle / mount state transitions
    // ------------------------------------------------------------------

    @Test
    void newHorseIsNeitherSaddledNorMounted() {
        Mob horse = new Mob(Mob.Type.HORSE, 0f, 0f, 0f);
        assertFalse(horse.isSaddled(), "fresh horse must not be saddled");
        assertFalse(horse.isMounted(), "fresh horse must not be mounted");
    }

    @Test
    void setSaddledRoundTrips() {
        Mob horse = new Mob(Mob.Type.HORSE, 0f, 0f, 0f);
        horse.setSaddled(true);
        assertTrue(horse.isSaddled());
        horse.setSaddled(false);
        assertFalse(horse.isSaddled());
    }

    @Test
    void setMountedRoundTrips() {
        Mob horse = new Mob(Mob.Type.HORSE, 0f, 0f, 0f);
        horse.setMounted(true);
        assertTrue(horse.isMounted());
        horse.setMounted(false);
        assertFalse(horse.isMounted());
    }

    @Test
    void mountYOffsetIsPositive() {
        Mob horse = new Mob(Mob.Type.HORSE, 0f, 0f, 0f);
        assertTrue(horse.mountYOffset() > 0f,
                "mountYOffset must place the rider above the horse's body centre");
    }

    @Test
    void mountYOffsetIsLessThanFullHeight() {
        Mob horse = new Mob(Mob.Type.HORSE, 0f, 0f, 0f);
        assertTrue(horse.mountYOffset() < Mob.Type.HORSE.height,
                "mountYOffset must not exceed the horse's full body height");
    }

    // ------------------------------------------------------------------
    // rideTick – movement
    // ------------------------------------------------------------------

    @Test
    void ridingForwardMovesFarther() {
        StubWorld w = flatGround(50);
        Mob horse = horseOnGround();

        // Let gravity settle the horse onto the ground first.
        Random rnd = new Random(1);
        for (int i = 0; i < 60; i++) horse.update(DT, w, rnd);

        float startX = horse.position.x;

        // Press W, face +X direction (frontX=1, frontZ=0).
        for (int i = 0; i < 60; i++) {
            horse.rideTick(DT, true, false, false, false, 1f, 0f, w);
        }

        assertTrue(horse.position.x > startX + 1f,
                "Horse should move in +X when W is pressed facing +X, dx="
                        + (horse.position.x - startX));
    }

    @Test
    void ridingBackwardMovesOppositeDirection() {
        StubWorld w = flatGround(50);
        Mob horse = horseOnGround();

        Random rnd = new Random(2);
        for (int i = 0; i < 60; i++) horse.update(DT, w, rnd);

        float startX = horse.position.x;

        // Press S, face +X direction.
        for (int i = 0; i < 60; i++) {
            horse.rideTick(DT, false, true, false, false, 1f, 0f, w);
        }

        assertTrue(horse.position.x < startX - 1f,
                "Horse should reverse when S is pressed facing +X, dx="
                        + (horse.position.x - startX));
    }

    @Test
    void ridingRightStrafesInCorrectDirection() {
        StubWorld w = flatGround(50);
        Mob horse = horseOnGround();

        Random rnd = new Random(3);
        for (int i = 0; i < 60; i++) horse.update(DT, w, rnd);

        float startZ = horse.position.z;

        // Press D (right), facing +X (frontX=1, frontZ=0).
        // Right vector is rx = -frontZ = 0, rz = frontX = 1  →  strafe moves in +Z.
        for (int i = 0; i < 60; i++) {
            horse.rideTick(DT, false, false, false, true, 1f, 0f, w);
        }

        assertTrue(horse.position.z > startZ + 1f,
                "Strafing right with front=(1,0) should move in +Z, dz="
                        + (horse.position.z - startZ));
    }

    @Test
    void noKeyLeavesHorseStationary() {
        StubWorld w = flatGround(20);
        Mob horse = horseOnGround();

        // Settle first.
        Random rnd = new Random(4);
        for (int i = 0; i < 60; i++) horse.update(DT, w, rnd);

        float startX = horse.position.x;
        float startZ = horse.position.z;

        // One second of rideTick with no keys pressed.
        for (int i = 0; i < 30; i++) {
            horse.rideTick(DT, false, false, false, false, 1f, 0f, w);
        }

        assertEquals(startX, horse.position.x, 0.15f,
                "Horse should not drift in X with no keys pressed");
        assertEquals(startZ, horse.position.z, 0.15f,
                "Horse should not drift in Z with no keys pressed");
    }

    // ------------------------------------------------------------------
    // rideTick – vertical physics
    // ------------------------------------------------------------------

    @Test
    void horseDoesNotSinkBelowGroundWhileRidden() {
        // Large enough ground so the horse never walks off the edge.
        // RIDE_SPEED=10 m/s × 1 s of riding (30 frames) = 10 units of travel.
        StubWorld w = flatGround(50);
        Mob horse = horseOnGround();

        // Settle first.
        Random rnd = new Random(5);
        for (int i = 0; i < 60; i++) horse.update(DT, w, rnd);

        float groundY = horse.position.y;

        // Ride for 1 second (30 frames × DT=1/30) so the horse travels 10 units —
        // well within the 50-block radius ground.
        for (int i = 0; i < 30; i++) {
            horse.rideTick(DT, true, false, false, false, 1f, 0f, w);
        }

        assertTrue(horse.position.y >= groundY - 0.3f,
                "Horse should not sink below the ground while being ridden: y=" + horse.position.y);
    }

    @Test
    void airborneHorseFallsUnderGravityWhenRidden() {
        // No ground under the horse — it should fall.
        BlockAccessor allAir = (x, y, z) -> BlockType.AIR;
        Mob horse = new Mob(Mob.Type.HORSE, 0f, 10f, 0f);
        float startY = horse.position.y;

        // rideTick with no forward key for 1 second.
        for (int i = 0; i < 30; i++) {
            horse.rideTick(DT, false, false, false, false, 1f, 0f, allAir);
        }

        assertTrue(horse.position.y < startY,
                "Airborne horse should fall under gravity even while ridden: y=" + horse.position.y);
    }

    // ------------------------------------------------------------------
    // SADDLE crafting recipe
    // ------------------------------------------------------------------

    @Test
    void saddleRecipeYieldsOneSaddle() {
        // U.U
        // UUU   (U = WOOL, I = IRON_INGOT)
        // .I.
        BlockType U = BlockType.WOOL;
        BlockType I = BlockType.IRON_INGOT;
        BlockType[] grid = {
            U, null, U,
            U, U,    U,
            null, I, null
        };
        var result = com.minecraftclone.player.Crafting.match3x3(grid);
        assertNotNull(result, "U.U / UUU / .I. should yield a SADDLE recipe result");
        assertEquals(BlockType.SADDLE, result.output(), "Recipe output must be SADDLE");
        assertEquals(1, result.outputAmount(), "Recipe should yield exactly 1 saddle");
    }

    // ------------------------------------------------------------------
    // Water buoyancy while ridden (rideTick must honour swimming physics)
    // ------------------------------------------------------------------

    @Test
    void riddenHorseDoesNotSinkThroughWater() {
        // A pool of water (y=1..4) over a stone floor (y=0).
        StubWorld w = new StubWorld();
        for (int x = -10; x <= 10; x++) {
            for (int z = -10; z <= 10; z++) {
                w.set(x, 0, z, BlockType.STONE);
                for (int y = 1; y <= 4; y++) {
                    w.set(x, y, z, BlockType.WATER);
                }
            }
        }
        // Drop the horse into the middle of the pool.
        Mob horse = new Mob(Mob.Type.HORSE, 0f, 2.5f, 0f);

        // Ride for 3 seconds with no directional input — buoyancy should keep it
        // well above the stone floor (feet above y=1).
        for (int i = 0; i < 90; i++) {
            horse.rideTick(DT, false, false, false, false, 1f, 0f, w);
        }

        float feet = horse.position.y - Mob.Type.HORSE.height / 2f;
        assertTrue(feet > 1.5f,
                "Ridden horse should float in water, not sink to the floor: feet=" + feet);
    }
}
