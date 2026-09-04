package com.minecraftclone.world;

import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the creeper-explosion / TNT / gunpowder feature.
 *
 * <p>Covered scenarios:
 * <ul>
 *   <li>{@link BlockType#GUNPOWDER} and {@link BlockType#TNT} are defined.</li>
 *   <li>Creepers drop {@link BlockType#GUNPOWDER} when killed by a player.</li>
 *   <li>Creeper fuse constants ({@link Mob#CREEPER_FUSE_RANGE},
 *       {@link Mob#CREEPER_FUSE_TIME}) are within sensible ranges.</li>
 *   <li>{@link Explosion} radius/damage constants are sane.</li>
 *   <li>{@link Explosion#damageAt} returns max damage at the centre,
 *       falls off with distance, and is zero beyond 1.5× radius.</li>
 *   <li>A creeper sets {@code wantsToExplode()} only after the fuse timer
 *       elapses, and aborts the fuse when the player backs away.</li>
 *   <li>{@link Mob#isFuseLit()} and {@link Mob#getFuseProgress()} track
 *       the fuse state correctly.</li>
 * </ul>
 */
class TntTest {

    private static final float DT = 1f / 30f;

    // -----------------------------------------------------------------------
    // Minimal stub world for mob tests
    // -----------------------------------------------------------------------

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

    private static StubWorld flatGround(int size) {
        StubWorld w = new StubWorld();
        for (int x = -size; x <= size; x++) {
            for (int z = -size; z <= size; z++) {
                w.set(x, 0, z, BlockType.GRASS);
            }
        }
        return w;
    }

    // -----------------------------------------------------------------------
    // BlockType presence
    // -----------------------------------------------------------------------

    @Test
    void gunpowderBlockTypeDefined() {
        assertNotNull(BlockType.GUNPOWDER);
    }

    @Test
    void tntBlockTypeDefined() {
        assertNotNull(BlockType.TNT);
    }

    // -----------------------------------------------------------------------
    // Creeper drop
    // -----------------------------------------------------------------------

    @Test
    void creeperDropsGunpowder() {
        Mob creeper = new Mob(Mob.Type.CREEPER, 0f, 1f + Mob.Type.CREEPER.height / 2f, 0f);
        assertEquals(BlockType.GUNPOWDER, creeper.dropType(),
                "Creepers should drop GUNPOWDER when killed");
    }

    // -----------------------------------------------------------------------
    // Creeper fuse constants
    // -----------------------------------------------------------------------

    @Test
    void creeperFuseRangeIsPositive() {
        assertTrue(Mob.CREEPER_FUSE_RANGE > 0f, "CREEPER_FUSE_RANGE must be > 0");
    }

    @Test
    void creeperFuseTimeIsPositive() {
        assertTrue(Mob.CREEPER_FUSE_TIME > 0f, "CREEPER_FUSE_TIME must be > 0");
    }

    @Test
    void creeperFuseTimeIsReasonable() {
        // Vanilla is 1.5 s; we allow 0.5–5 s.
        assertTrue(Mob.CREEPER_FUSE_TIME >= 0.5f && Mob.CREEPER_FUSE_TIME <= 5f,
                "CREEPER_FUSE_TIME should be between 0.5 and 5 seconds");
    }

    // -----------------------------------------------------------------------
    // Explosion constants
    // -----------------------------------------------------------------------

    @Test
    void creeperRadiusIsPositive() {
        assertTrue(Explosion.CREEPER_RADIUS > 0f);
    }

    @Test
    void tntRadiusIsPositive() {
        assertTrue(Explosion.TNT_RADIUS > 0f);
    }

    @Test
    void tntRadiusAtLeastCreeperRadius() {
        assertTrue(Explosion.TNT_RADIUS >= Explosion.CREEPER_RADIUS,
                "TNT should be at least as powerful as a creeper");
    }

    @Test
    void creeperCenterDamageIsPositive() {
        assertTrue(Explosion.CREEPER_CENTER_DAMAGE > 0f);
    }

    @Test
    void tntCenterDamageAtLeastCreeperCenterDamage() {
        assertTrue(Explosion.TNT_CENTER_DAMAGE >= Explosion.CREEPER_CENTER_DAMAGE,
                "TNT center damage should be >= creeper center damage");
    }

    // -----------------------------------------------------------------------
    // Explosion.damageAt
    // -----------------------------------------------------------------------

    @Test
    void damageAtCenterIsMaxDamage() {
        float radius = 3f, max = 9f;
        float dmg = Explosion.damageAt(0f, 0f, 0f, radius, max, 0f, 0f, 0f);
        assertEquals(max, dmg, 0.001f, "Damage at centre must equal maxDamage");
    }

    @Test
    void damageDecreasesWithDistance() {
        float radius = 3f, max = 9f;
        float close = Explosion.damageAt(0f, 0f, 0f, radius, max, 0.5f, 0f, 0f);
        float far   = Explosion.damageAt(0f, 0f, 0f, radius, max, 2.0f, 0f, 0f);
        assertTrue(close > far, "Damage should decrease with distance");
    }

    @Test
    void damageIsZeroBeyondKillZone() {
        float radius = 3f, max = 9f;
        // kill zone = 1.5 × radius = 4.5 → target at 5 units takes no damage
        float dmg = Explosion.damageAt(0f, 0f, 0f, radius, max, 5f, 0f, 0f);
        assertEquals(0f, dmg, 0.001f, "No damage outside kill zone");
    }

    @Test
    void damageIsPositiveJustInsideKillZone() {
        float radius = 3f, max = 9f;
        float dmg = Explosion.damageAt(0f, 0f, 0f, radius, max, 4.4f, 0f, 0f);
        assertTrue(dmg > 0f, "Should deal damage just inside the kill zone");
    }

    // -----------------------------------------------------------------------
    // Creeper fuse state machine
    // -----------------------------------------------------------------------

    /** A freshly constructed creeper must not request an explosion. */
    @Test
    void freshCreeperDoesNotWantToExplode() {
        Mob creeper = new Mob(Mob.Type.CREEPER, 0f, 1f + Mob.Type.CREEPER.height / 2f, 0f);
        assertFalse(creeper.wantsToExplode(), "Unticked creeper must not want to explode");
    }

    /** A creeper ticked with the player far away must not ignite. */
    @Test
    void creeperFarAwayDoesNotIgnite() {
        StubWorld w = flatGround(20);
        Mob creeper = new Mob(Mob.Type.CREEPER, 0f, 1f + Mob.Type.CREEPER.height / 2f, 0f);
        Random rnd = new Random(3);
        // Player at (100, 0.9, 100) — far beyond fuse range
        Vector3f player = new Vector3f(100f, 0.9f, 100f);
        for (int i = 0; i < 60; i++) {
            creeper.update(DT, w, rnd, player);
        }
        assertFalse(creeper.wantsToExplode(), "Creeper outside fuse range should not explode");
        assertFalse(creeper.isFuseLit(), "Fuse should not be burning when player is far away");
    }

    /**
     * After CREEPER_FUSE_TIME seconds inside fuse range the creeper
     * sets {@code wantsToExplode()}.
     */
    @Test
    void creeperExplodesAfterFuseTime() {
        StubWorld w = flatGround(20);
        Mob creeper = new Mob(Mob.Type.CREEPER, 0f, 1f + Mob.Type.CREEPER.height / 2f, 0f);
        Random rnd = new Random(4);
        // Player 1 block away (inside CREEPER_FUSE_RANGE = 2.5)
        Vector3f player = new Vector3f(1f, 0.9f, 0f);
        float totalTime = Mob.CREEPER_FUSE_TIME + 0.5f; // a little over
        int steps = (int) Math.ceil(totalTime / DT);
        boolean exploded = false;
        for (int i = 0; i < steps; i++) {
            creeper.update(DT, w, rnd, player);
            if (creeper.wantsToExplode()) {
                exploded = true;
                break;
            }
        }
        assertTrue(exploded, "Creeper should want to explode after fuse elapses");
    }

    /** Backing the player away mid-fuse resets it; no explosion follows. */
    @Test
    void creeperFuseAbortsWhenPlayerBacksAway() {
        StubWorld w = flatGround(20);
        Mob creeper = new Mob(Mob.Type.CREEPER, 0f, 1f + Mob.Type.CREEPER.height / 2f, 0f);
        Random rnd = new Random(5);
        float fuseHalf = Mob.CREEPER_FUSE_TIME / 2f;
        int steps = (int) Math.ceil(fuseHalf / DT);

        // Ignite fuse for half the fuse time
        Vector3f close = new Vector3f(1f, 0.9f, 0f);
        for (int i = 0; i < steps; i++) {
            creeper.update(DT, w, rnd, close);
        }
        assertTrue(creeper.isFuseLit(), "Fuse should be burning after half time inside range");

        // Player backs away beyond fuse range
        Vector3f far = new Vector3f(100f, 0.9f, 0f);
        for (int i = 0; i < steps; i++) {
            creeper.update(DT, w, rnd, far);
        }
        assertFalse(creeper.isFuseLit(), "Fuse should be extinguished when player backs away");
        assertFalse(creeper.wantsToExplode(), "Creeper should not explode after fuse abort");
    }

    /** getFuseProgress() increases over time while inside range. */
    @Test
    void fuseProgressIncreasesOverTime() {
        StubWorld w = flatGround(20);
        Mob creeper = new Mob(Mob.Type.CREEPER, 0f, 1f + Mob.Type.CREEPER.height / 2f, 0f);
        Random rnd = new Random(6);
        Vector3f player = new Vector3f(1f, 0.9f, 0f);

        // Tick inside range for ~20% of fuse time
        int steps20 = (int) Math.ceil(Mob.CREEPER_FUSE_TIME * 0.2f / DT);
        for (int i = 0; i < steps20; i++) {
            creeper.update(DT, w, rnd, player);
            if (creeper.wantsToExplode()) break; // shouldn't happen at 20%
        }
        float progress20 = creeper.getFuseProgress();

        // Tick inside range for another ~40% of fuse time
        int steps40 = (int) Math.ceil(Mob.CREEPER_FUSE_TIME * 0.4f / DT);
        for (int i = 0; i < steps40; i++) {
            creeper.update(DT, w, rnd, player);
            if (creeper.wantsToExplode()) break;
        }
        float progress60 = creeper.getFuseProgress();

        assertTrue(progress60 > progress20,
                "Fuse progress at ~60% should exceed progress at ~20%");
    }

    /** getFuseProgress() returns 0 for non-creeper mob types. */
    @Test
    void fuseProgressIsZeroForNonCreeper() {
        Mob zombie = new Mob(Mob.Type.ZOMBIE, 0f, 1f + Mob.Type.ZOMBIE.height / 2f, 0f);
        assertEquals(0f, zombie.getFuseProgress(), 0.001f,
                "Non-creeper mob should always have zero fuse progress");
    }
}
