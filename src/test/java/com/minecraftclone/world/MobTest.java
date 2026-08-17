package com.minecraftclone.world;

import com.minecraftclone.util.AABB;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class MobTest {

    private static final float DT = 1f / 30f;

    /** A tiny in-memory world so the pure-logic mob AI can be tested without GL. */
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

    @Test
    void mobFallsToTheGroundAndStaysThere() {
        StubWorld w = flatGround(20);
        Mob m = new Mob(Mob.Type.PIG, 0.5f, 20f, 0.5f);
        Random rnd = new Random(1);
        for (int i = 0; i < 180; i++) {
            m.update(DT, w, rnd);
        }
        float expectedFeet = 1f; // ground block top is y=1
        assertEquals(expectedFeet + m.type.height / 2f, m.position.y, 0.3f, "mob should rest on the ground");
    }

    @Test
    void mobDoesNotWalkOffAPillar() {
        // A 3x3 pillar of stone with open air all around: the mob must never step off.
        StubWorld w = new StubWorld();
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                w.set(x, 0, z, BlockType.STONE);
                w.set(x, 1, z, BlockType.STONE);
            }
        }
        Mob m = new Mob(Mob.Type.COW, 0f, 2f + Mob.Type.COW.height / 2f, 0f);
        Random rnd = new Random(7);
        for (int i = 0; i < 900; i++) {
            m.update(DT, w, rnd);
        }
        assertTrue(Math.abs(m.position.x) <= 1.5f, "strayed off the pillar on X: " + m.position.x);
        assertTrue(Math.abs(m.position.z) <= 1.5f, "strayed off the pillar on Z: " + m.position.z);
        float ground = 2f; // pillar top at y=2
        assertTrue(m.position.y >= ground + m.type.height / 2f - 0.3f,
                "fell off the pillar: y=" + m.position.y);
    }

    @Test
    void mobStaysInsideAWalledPen() {
        StubWorld w = new StubWorld();
        int s = 5; // floor from -s..s
        for (int x = -s; x <= s; x++) {
            for (int z = -s; z <= s; z++) {
                w.set(x, 0, z, BlockType.GRASS);
            }
        }
        // Walls around the pen at |x| == s+1 or |z| == s+1, height 2.
        for (int i = -s - 1; i <= s + 1; i++) {
            for (int y = 1; y <= 2; y++) {
                w.set(i, y, s + 1, BlockType.STONE);
                w.set(i, y, -s - 1, BlockType.STONE);
                w.set(s + 1, y, i, BlockType.STONE);
                w.set(-s - 1, y, i, BlockType.STONE);
            }
        }
        Mob m = new Mob(Mob.Type.SHEEP, 0f, 1f + Mob.Type.SHEEP.height / 2f, 0f);
        Random rnd = new Random(11);
        for (int i = 0; i < 900; i++) {
            m.update(DT, w, rnd);
        }
        assertTrue(Math.abs(m.position.x) <= s + 0.5f, "escaped the pen on X: " + m.position.x);
        assertTrue(Math.abs(m.position.z) <= s + 0.5f, "escaped the pen on Z: " + m.position.z);
    }

    @Test
    void mobActuallyWandersOnOpenGround() {
        StubWorld w = flatGround(40);
        Mob m = new Mob(Mob.Type.SHEEP, 0.5f, 1f + Mob.Type.SHEEP.height / 2f, 0.5f);
        float startX = m.position.x;
        float startZ = m.position.z;
        Random rnd = new Random(3);
        for (int i = 0; i < 1200; i++) {
            m.update(DT, w, rnd);
        }
        float dx = m.position.x - startX;
        float dz = m.position.z - startZ;
        assertTrue(Math.hypot(dx, dz) > 1.0f,
                "mob barely moved over 40s: dx=" + dx + " dz=" + dz);
    }

    @Test
    void mobGravityOnlyAppliesWhenAirborne() {
        StubWorld w = flatGround(20);
        Mob m = new Mob(Mob.Type.COW, 0.5f, 1f + Mob.Type.COW.height / 2f, 0.5f);
        float groundY = m.position.y;
        Random rnd = new Random(5);
        for (int i = 0; i < 300; i++) {
            m.update(DT, w, rnd);
        }
        assertEquals(groundY, m.position.y, 0.2f, "grounded mob should not sink or float");
    }

    @Test
    void mobNavigatesAlongACorridorWithoutLeavingIt() {
        // A long corridor along X (floor grass at y=0, stone walls at z=±3):
        // the pathfinding should route the mob along it, never through the walls.
        StubWorld w = new StubWorld();
        for (int x = -14; x <= 14; x++) {
            for (int z = -2; z <= 2; z++) {
                w.set(x, 0, z, BlockType.GRASS);
            }
            for (int y = 1; y <= 2; y++) {
                w.set(x, y, -3, BlockType.STONE);
                w.set(x, y, 3, BlockType.STONE);
            }
        }
        Mob m = new Mob(Mob.Type.SHEEP, 0f, 1f + Mob.Type.SHEEP.height / 2f, 0f);
        float startX = m.position.x;
        Random rnd = new Random(21);
        for (int i = 0; i < 900; i++) {
            m.update(DT, w, rnd);
        }
        assertTrue(Math.abs(m.position.z) <= 2.4f, "left the corridor: z=" + m.position.z);
        assertTrue(Math.abs(m.position.x - startX) > 0.5f,
                "should wander along the corridor: dx=" + (m.position.x - startX));
    }

    @Test
    void mobStartsAtFullHealthAndDamageReducesIt() {
        Mob m = new Mob(Mob.Type.PIG, 0f, 0f, 0f);
        assertEquals(m.getMaxHealth(), m.getHealth(), 0.001f);
        m.damage(3f, 0f, 0f);
        assertEquals(m.getMaxHealth() - 3f, m.getHealth(), 0.001f);
        assertFalse(m.isDead());
    }

    @Test
    void mobDiesWhenItsHealthRunsOut() {
        Mob m = new Mob(Mob.Type.COW, 0f, 0f, 0f);
        assertTrue(m.damage(m.getMaxHealth(), 0f, 0f), "a full-health hit kills it");
        assertTrue(m.isDead());
        assertEquals(0f, m.getHealth(), 0.001f);
    }

    @Test
    void hurtMobFleesAwayFromTheDamageSource() {
        StubWorld w = flatGround(20);
        Mob m = new Mob(Mob.Type.SHEEP, 0f, 1f + Mob.Type.SHEEP.height / 2f, 0f);
        float startX = m.position.x;
        // Hit from -x, so the mob should bolt toward +x.
        m.damage(1f, -4f, 0f);
        Random rnd = new Random(9);
        for (int i = 0; i < 45; i++) {
            m.update(DT, w, rnd);
        }
        assertTrue(m.position.x > startX + 0.5f,
                "panic should run away from the attacker: dx=" + (m.position.x - startX));
    }

    @Test
    void rayIntersectsAabbHitsOnlyWhenAimed() {
        AABB box = new AABB(0, 0, 0, 1, 1, 1);
        // Straight on from +x: enters the box at t = 4 (x=1).
        float t = Mob.rayIntersects(new Vector3f(5f, 0.5f, 0.5f), new Vector3f(-1f, 0f, 0f), 10f, box);
        assertEquals(4f, t, 1e-3f);
        // Out of reach.
        assertEquals(-1f, Mob.rayIntersects(new Vector3f(5f, 0.5f, 0.5f), new Vector3f(-1f, 0f, 0f), 3f, box), 0f);
        // Misses vertically.
        assertEquals(-1f, Mob.rayIntersects(new Vector3f(5f, 5f, 0.5f), new Vector3f(-1f, 0f, 0f), 10f, box), 0f);
        // Diagonal hit.
        assertTrue(Mob.rayIntersects(new Vector3f(3f, 3f, 3f), new Vector3f(-1f, -1f, -1f), 10f, box) > 0f,
                "diagonal ray should enter the box");
        // Ray starting inside the box counts as a hit at t=0.
        assertEquals(0f, Mob.rayIntersects(new Vector3f(0.5f, 0.5f, 0.5f), new Vector3f(0f, 1f, 0f), 10f, box), 1e-3f);
    }

    @Test
    void hostileChasesThePlayer() {
        StubWorld w = flatGround(20);
        Mob zombie = new Mob(Mob.Type.ZOMBIE, 0f, 1f + Mob.Type.ZOMBIE.height / 2f, 0f);
        float startX = zombie.position.x;
        Random rnd = new Random(13);
        Vector3f player = new Vector3f(5f, 0.9f, 0f);
        for (int i = 0; i < 60; i++) {
            zombie.update(DT, w, rnd, player);
        }
        assertTrue(zombie.position.x > startX + 1f,
                "zombie should close in on the player: dx=" + (zombie.position.x - startX));
    }

    @Test
    void hostileRequestsMeleeWhenAdjacent() {
        StubWorld w = flatGround(10);
        Mob zombie = new Mob(Mob.Type.ZOMBIE, 0f, 1f + Mob.Type.ZOMBIE.height / 2f, 0f);
        Random rnd = new Random(4);
        zombie.update(DT, w, rnd, new Vector3f(1f, 0.9f, 0f)); // adjacent
        assertEquals(zombie.getAttackDamage(), zombie.getMeleeRequest(), 0.001f);
    }

    @Test
    void skeletonRequestsShotsFromRange() {
        StubWorld w = flatGround(10);
        Mob skeleton = new Mob(Mob.Type.SKELETON, 0f, 1f + Mob.Type.SKELETON.height / 2f, 0f);
        Random rnd = new Random(6);
        skeleton.update(DT, w, rnd, new Vector3f(6f, 0.9f, 0f)); // in shoot range
        assertTrue(skeleton.wantsToShoot(), "skeleton should shoot from mid range");
    }

    @Test
    void hostileNeverAttacksWithANullTarget() {
        // World passes null instead of the real player position when the player is
        // invulnerable (creative/spectator - see World#updateMobs), so a hostile
        // should never register an attack even standing right on top of where the
        // player would be (regression: "mobs still target you in creative mode").
        StubWorld w = flatGround(10);
        Mob zombie = new Mob(Mob.Type.ZOMBIE, 1f, 1f + Mob.Type.ZOMBIE.height / 2f, 0f);
        Random rnd = new Random(4);
        zombie.update(DT, w, rnd, null); // would be adjacent to (1,0.9,0) if targeted
        assertEquals(0f, zombie.getMeleeRequest(), 0.001f, "no target means no attack request");

        Mob skeleton = new Mob(Mob.Type.SKELETON, 6f, 1f + Mob.Type.SKELETON.height / 2f, 0f);
        skeleton.update(DT, w, rnd, null); // would be in shoot range of (6,0.9,0) if targeted
        assertFalse(skeleton.wantsToShoot(), "no target means no shot request either");
    }

    @Test
    void hostileKeepsChasingAfterBeingHit() {
        StubWorld w = flatGround(20);
        Mob zombie = new Mob(Mob.Type.ZOMBIE, 0f, 1f + Mob.Type.ZOMBIE.height / 2f, 0f);
        zombie.damage(2f, 3f, 0f); // hit from the player's side - must not flee away
        float startX = zombie.position.x;
        Random rnd = new Random(17);
        Vector3f player = new Vector3f(5f, 0.9f, 0f);
        for (int i = 0; i < 60; i++) {
            zombie.update(DT, w, rnd, player);
        }
        assertTrue(zombie.position.x > startX + 0.5f,
                "a hurt hostile keeps coming, not fleeing: dx=" + (zombie.position.x - startX));
    }

    /** A pool of water at ground level (y=1..4 of water over a solid floor at y=0). */
    private static StubWorld waterPool(int size) {
        StubWorld w = new StubWorld();
        for (int x = -size; x <= size; x++) {
            for (int z = -size; z <= size; z++) {
                w.set(x, 0, z, BlockType.STONE);
                for (int y = 1; y <= 4; y++) {
                    w.set(x, y, z, BlockType.WATER);
                }
            }
        }
        return w;
    }

    @Test
    void mobSwimsInsteadOfSinkingToTheBottom() {
        StubWorld w = waterPool(20);
        // Drop a pig mid-water: it should float near the surface, not rest on
        // the pool bed (y=1). Its feet should rise well above the bed.
        Mob pig = new Mob(Mob.Type.PIG, 0f, 2.5f, 0f);
        Random rnd = new Random(2);
        for (int i = 0; i < 300; i++) {
            pig.update(DT, w, rnd);
        }
        float feet = pig.position.y - pig.type.height / 2f;
        assertTrue(feet > 1.5f, "a swimming mob should float up, not rest on the bed: feet=" + feet);
    }

    @Test
    void mobDoesNotBounceOutOfTheWaterOnceAtTheSurface() {
        // Regression: the swim velocity used to be clamped to at least
        // SWIM_SURFACE_SPEED unconditionally, regardless of depth - since that's
        // faster than the buoyancy decay ever settles to, it won every frame a
        // mob was even partly in water, snapping its vertical velocity straight
        // from the full paddle-up speed to a hard sink and back within a single
        // frame right at the surface - a visible judder, reported as "mobs walk
        // on water bouncing". Once floating, per-frame velocity swings should be
        // smooth (bounded acceleration), not an instant sign-flipping snap.
        StubWorld w = waterPool(20);
        Mob pig = new Mob(Mob.Type.PIG, 0f, 2.5f, 0f);
        Random rnd = new Random(2);
        for (int i = 0; i < 300; i++) {
            pig.update(DT, w, rnd); // let it rise and settle at the surface first
        }
        float maxJump = 0f;
        float lastVy = pig.velocity.y;
        for (int i = 0; i < 300; i++) {
            pig.update(DT, w, rnd);
            maxJump = Math.max(maxJump, Math.abs(pig.velocity.y - lastVy));
            lastVy = pig.velocity.y;
        }
        assertTrue(maxJump < 1f,
                "a floating mob's vertical velocity shouldn't snap between frames: maxJump=" + maxJump);
    }

    @Test
    void mobDrownsAfterStayingFullySubmerged() {
        // A pool with a solid ceiling just under the water surface, so a mob in
        // it can't swim up to breathe - it stays fully submerged and drowns.
        StubWorld w = waterPool(20);
        for (int x = -20; x <= 20; x++) {
            for (int z = -20; z <= 20; z++) {
                w.set(x, 3, z, BlockType.STONE); // ceiling just above the water floor
            }
        }
        Mob cow = new Mob(Mob.Type.COW, 0f, 2.0f, 0f);
        float startHealth = cow.getHealth();
        Random rnd = new Random(8);
        for (int i = 0; i < 1200; i++) {
            cow.update(DT, w, rnd);
        }
        assertTrue(cow.isDead(), "a mob fully underwater for a long time should drown");
        assertTrue(cow.getHealth() < startHealth, "drowning should have dealt damage");
    }

    @Test
    void drownedMobTakesDamageWithoutPanicKnockback() {
        Mob pig = new Mob(Mob.Type.PIG, 0f, 0f, 0f);
        float x = pig.position.x;
        pig.drown(3f);
        assertEquals(pig.getMaxHealth() - 3f, pig.getHealth(), 0.001f);
        // No knockback from drowning - the mob doesn't jump away.
        assertEquals(x, pig.position.x, 0.001f);
    }
}
