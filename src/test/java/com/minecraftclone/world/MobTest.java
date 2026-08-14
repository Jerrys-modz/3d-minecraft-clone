package com.minecraftclone.world;

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
}
