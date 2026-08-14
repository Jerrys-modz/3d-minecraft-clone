package com.minecraftclone.world;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PathfinderTest {

    private static final int FLOOR = 1; // standing on ground with its top at y=1

    /** A tiny in-memory world so pathfinding can be tested without GL. */
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

    private static StubWorld flatGround() {
        StubWorld w = new StubWorld();
        for (int x = -20; x <= 20; x++) {
            for (int z = -20; z <= 20; z++) {
                w.set(x, 0, z, BlockType.GRASS);
            }
        }
        return w;
    }

    @Test
    void findsStraightLinePathOnFlatGround() {
        StubWorld w = flatGround();
        List<int[]> path = Pathfinder.findPath(w, 0, 0, 5, 0, FLOOR, 500);
        assertNotNull(path);
        assertEquals(6, path.size());
        assertArrayEquals(new int[]{0, 1, 0}, path.get(0));
        assertArrayEquals(new int[]{5, 1, 0}, path.get(path.size() - 1));
        for (int[] wp : path) {
            assertEquals(1, wp[1], "stays on the flat floor");
        }
    }

    @Test
    void routesAroundAWall() {
        StubWorld w = flatGround();
        // A north-south stone wall at x=2 (z from -5..5, two blocks tall) blocks
        // the direct east-west line at floor 1.
        for (int z = -5; z <= 5; z++) {
            w.set(2, 1, z, BlockType.STONE);
            w.set(2, 2, z, BlockType.STONE);
        }
        List<int[]> path = Pathfinder.findPath(w, 0, 0, 4, 0, FLOOR, 2000);
        assertNotNull(path, "a route around the wall exists");
        assertEquals(4, path.get(path.size() - 1)[0], "ends at the goal");
        boolean wentAround = false;
        for (int[] wp : path) {
            assertFalse(wp[0] == 2 && Math.abs(wp[2]) <= 5, "never paths through the wall itself");
            if (Math.abs(wp[2]) > 5) wentAround = true;
        }
        assertTrue(wentAround, "path detours around the wall's end, not through it");
    }

    @Test
    void returnsNullForUnreachableGoal() {
        StubWorld w = new StubWorld();
        // Ground only on two isolated cells: nothing connects them.
        w.set(0, 0, 0, BlockType.GRASS);
        w.set(5, 0, 0, BlockType.GRASS);
        assertNull(Pathfinder.findPath(w, 0, 0, 5, 0, FLOOR, 500));
    }

    @Test
    void refusesToCrossACliff() {
        StubWorld w = flatGround();
        // Knife a 1-column-wide gap through the ground at x=0 across the whole
        // world: floors on either side are 1, but the gap column can't be stood
        // on anywhere, so no route exists.
        for (int z = -20; z <= 20; z++) {
            w.set(0, 0, z, BlockType.AIR);
        }
        assertNull(Pathfinder.findPath(w, -4, 0, 4, 0, FLOOR, 500));
    }

    @Test
    void climbsOneBlockStepsButNotTwo() {
        StubWorld w = flatGround();
        // A platform one block higher over x=3..5 (stone at y=1, top at y=2).
        for (int x = 3; x <= 5; x++) {
            for (int z = -2; z <= 2; z++) {
                w.set(x, 1, z, BlockType.STONE);
            }
        }
        List<int[]> path = Pathfinder.findPath(w, 0, 0, 4, 0, FLOOR, 500);
        assertNotNull(path, "one-block step is climbable");
        boolean steppedUp = false;
        for (int[] wp : path) {
            if (wp[0] == 3 && wp[1] == 2) steppedUp = true;
        }
        assertTrue(steppedUp, "path climbs onto the platform");

        // Now stack it two blocks high (y=1 and y=2, top at y=3) - a two-block
        // step is a wall to a walking mob.
        for (int x = 8; x <= 10; x++) {
            for (int z = -2; z <= 2; z++) {
                w.set(x, 1, z, BlockType.STONE);
                w.set(x, 2, z, BlockType.STONE);
            }
        }
        assertEquals(Pathfinder.NO_FLOOR, Pathfinder.floorAt(w, 9, 0, FLOOR), "two-block platform is not standable");
        assertNull(Pathfinder.findPath(w, 0, 0, 9, 0, FLOOR, 500), "two-block step blocks the path");
    }

    @Test
    void floorAtDetectsStandability() {
        StubWorld w = flatGround();
        assertEquals(1, Pathfinder.floorAt(w, 3, 0, 1), "flat ground floor");
        // A two-block-tall obstacle in the column: the mob can't stand there at all
        // (floorAt scans within one block, so the top at +2 is out of reach).
        w.set(3, 1, 0, BlockType.STONE);
        w.set(3, 2, 0, BlockType.STONE);
        assertEquals(Pathfinder.NO_FLOOR, Pathfinder.floorAt(w, 3, 0, 1), "blocked column is not standable");
    }
}
