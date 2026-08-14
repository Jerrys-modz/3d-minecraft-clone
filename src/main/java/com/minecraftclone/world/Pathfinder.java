package com.minecraftclone.world;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * 2D A* pathfinding for mobs, over columns of the voxel world at a mob's
 * walking layer. A "node" is a column (x, z) paired with the floor height the
 * mob would stand on there. Moving to a neighbor column is allowed when the
 * mob can stand there and its floor is at most one block above or below the
 * current one - a single step up is fine (mobs climb one-block steps), a
 * cliff, wall or step of two is not. That's what lets mobs route around walls
 * and ledges instead of just bumping into them.
 * <p>
 * Pure logic over {@link BlockAccessor} (the same interface the mob AI uses),
 * so it's fully unit-testable without a GL context.
 */
public final class Pathfinder {

    /** Sentinel returned by {@link #floorAt} for a column the mob can't stand on. */
    public static final int NO_FLOOR = Integer.MIN_VALUE;

    private static final int[] DX = {1, -1, 0, 0};
    private static final int[] DZ = {0, 0, 1, -1};

    private static final long KEY_OFFSET = 1 << 20; // keep packed keys positive for small coords

    private Pathfinder() {
    }

    private static long key(int x, int z) {
        return ((long) (x + KEY_OFFSET) << 32) | (z + KEY_OFFSET);
    }

    /**
     * The floor height (the y the mob's feet rest on) of the column at (x, z),
     * looking within one block of {@code nearFloor} so a one-block step up or
     * down is found. Returns {@link #NO_FLOOR} if the mob can't stand there.
     */
    public static int floorAt(BlockAccessor world, int x, int z, int nearFloor) {
        for (int fy = nearFloor + 1; fy >= nearFloor - 1; fy--) {
            if (canStand(world, x, fy, z)) return fy;
        }
        return NO_FLOOR;
    }

    /** True if a mob with its feet at {@code feetY} in column (x, z) has solid ground and an open body cell. */
    private static boolean canStand(BlockAccessor world, int x, int feetY, int z) {
        return world.getBlock(x, feetY - 1, z).isCollidable()
                && !world.getBlock(x, feetY, z).isCollidable();
    }

    /**
     * A* from column (sx, sz) - with the mob standing at {@code floor} - to
     * column (gx, gz), bounded to {@code maxNodes} expansions so a path to an
     * unreachable goal fails fast instead of scanning the whole map. Returns a
     * list of waypoints {@code {x, floorY, z}}, the first being the start column
     * and the last the goal, or null if no route exists within the budget.
     */
    public static List<int[]> findPath(BlockAccessor world, int sx, int sz, int gx, int gz, int floor, int maxNodes) {
        Map<Long, Node> all = new HashMap<>();
        PriorityQueue<Node> open = new PriorityQueue<>(Comparator.comparingDouble(n -> n.f));
        Node start = new Node(sx, sz, floor, 0f, heuristic(sx, sz, gx, gz), null);
        all.put(key(sx, sz), start);
        open.add(start);

        int expanded = 0;
        while (!open.isEmpty() && expanded < maxNodes) {
            Node cur = open.poll();
            if (cur.closed) continue;
            cur.closed = true;
            expanded++;
            if (cur.x == gx && cur.z == gz) {
                return reconstruct(cur);
            }
            for (int d = 0; d < 4; d++) {
                int nx = cur.x + DX[d];
                int nz = cur.z + DZ[d];
                int nf = floorAt(world, nx, nz, cur.floor);
                if (nf == NO_FLOOR || Math.abs(nf - cur.floor) > 1) continue;
                // Cheap moves over steps up/down, so the path prefers flat ground.
                float g = cur.g + 1f + (nf == cur.floor ? 0f : 0.5f);
                Node next = new Node(nx, nz, nf, g, g + heuristic(nx, nz, gx, gz), cur);
                Node existing = all.get(key(nx, nz));
                if (existing == null || g < existing.g) {
                    all.put(key(nx, nz), next);
                    open.add(next);
                }
            }
        }
        return null;
    }

    private static int heuristic(int x, int z, int gx, int gz) {
        return Math.abs(x - gx) + Math.abs(z - gz);
    }

    private static List<int[]> reconstruct(Node end) {
        List<int[]> path = new ArrayList<>();
        for (Node n = end; n != null; n = n.parent) {
            path.add(0, new int[]{n.x, n.floor, n.z});
        }
        return path;
    }

    private static final class Node {
        final int x, z, floor;
        final float g, f;
        final Node parent;
        boolean closed;

        Node(int x, int z, int floor, float g, float f, Node parent) {
            this.x = x;
            this.z = z;
            this.floor = floor;
            this.g = g;
            this.f = f;
            this.parent = parent;
        }
    }
}
