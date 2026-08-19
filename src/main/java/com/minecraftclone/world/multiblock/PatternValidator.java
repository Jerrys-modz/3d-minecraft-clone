package com.minecraftclone.world.multiblock;

import com.minecraftclone.world.BlockType;
import com.minecraftclone.world.World;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;

/**
 * Static utilities for validating multi-block structure shapes.
 *
 * <p>The primary method {@link #findHollowBox} implements the standard
 * "hollow box" topology used by most Tinkers'-style multi-blocks: a
 * rectangular prism whose outer shell consists of valid structural blocks and
 * whose interior is hollow (air / fluid / items).  It is used as the default
 * implementation of {@link MultiBlockDefinition#tryForm}.
 *
 * <p>Other topologies (T-shape, concentric rings, etc.) can be added here as
 * additional static helpers and referenced from custom definition
 * implementations.
 */
public final class PatternValidator {

    private static final int[][] FACES = {
        {1,0,0},{-1,0,0},{0,1,0},{0,-1,0},{0,0,1},{0,0,-1}
    };

    /**
     * Try to form a hollow-box multi-block whose controller is at
     * {@code (cx, cy, cz)}.
     *
     * <p>Algorithm:
     * <ol>
     *   <li>For each of the 6 face directions: treat that face as the
     *       inward-facing side of the controller and BFS the adjacent
     *       interior region.</li>
     *   <li>Abort the BFS if it finds a block that is neither a valid interior
     *       block nor a valid shell block (meaning the "box" has a hole or the
     *       interior contains an illegal block), or if the interior exceeds the
     *       definition's {@code maxInteriorVolume()}.</li>
     *   <li>Compute the interior bounding box from the BFS result and expand by
     *       one to get the shell bounding box.</li>
     *   <li>Validate every position on the shell (outer surface of the expanded
     *       box) is a valid shell block.</li>
     *   <li>Validate the interior size is within the definition's allowed
     *       range.</li>
     *   <li>Return the {@link MultiBlockInstance} on success, {@code null} on
     *       failure.</li>
     * </ol>
     *
     * @param world the world to query
     * @param cx    controller X
     * @param cy    controller Y
     * @param cz    controller Z
     * @param def   the definition whose rules to apply
     * @return a formed instance, or {@code null} if the structure is invalid
     */
    public static MultiBlockInstance findHollowBox(World world, int cx, int cy, int cz,
                                                   MultiBlockDefinition def) {
        for (int[] inDir : FACES) {
            int startX = cx + inDir[0];
            int startY = cy + inDir[1];
            int startZ = cz + inDir[2];
            BlockType startBlock = world.getBlock(startX, startY, startZ);
            if (startBlock == null || !def.isInteriorBlock(startBlock)) continue;

            MultiBlockInstance result = bfsInterior(world, def, cx, cy, cz, startX, startY, startZ);
            if (result != null) return result;
        }
        return null;
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    private static MultiBlockInstance bfsInterior(World world, MultiBlockDefinition def,
                                                   int cx, int cy, int cz,
                                                   int startX, int startY, int startZ) {
        Set<Long> visited = new HashSet<>();
        Deque<int[]> queue = new ArrayDeque<>();
        queue.add(new int[]{startX, startY, startZ});

        int minX = startX, maxX = startX;
        int minY = startY, maxY = startY;
        int minZ = startZ, maxZ = startZ;
        int maxVolume = def.maxInteriorVolume();

        while (!queue.isEmpty()) {
            int[] pos = queue.poll();
            int px = pos[0], py = pos[1], pz = pos[2];
            long key = interiorKey(px, py, pz);
            if (!visited.add(key)) continue;

            if (visited.size() > maxVolume) return null; // Interior too large

            minX = Math.min(minX, px); maxX = Math.max(maxX, px);
            minY = Math.min(minY, py); maxY = Math.max(maxY, py);
            minZ = Math.min(minZ, pz); maxZ = Math.max(maxZ, pz);

            for (int[] d : FACES) {
                int qx = px + d[0], qy = py + d[1], qz = pz + d[2];
                BlockType qb = world.getBlock(qx, qy, qz);
                if (qb == null) return null; // Unloaded chunk — abort
                if (def.isInteriorBlock(qb)) {
                    if (!visited.contains(interiorKey(qx, qy, qz))) {
                        queue.add(new int[]{qx, qy, qz});
                    }
                } else if (!def.isShellBlock(qb)) {
                    return null; // Invalid block in the boundary — not a valid box
                }
                // If it's a shell block, BFS stops here (it's the wall)
            }
        }

        // Shell bounding box (interior + 1 in each direction)
        int shellMinX = minX - 1, shellMinY = minY - 1, shellMinZ = minZ - 1;
        int shellMaxX = maxX + 1, shellMaxY = maxY + 1, shellMaxZ = maxZ + 1;

        // Interior dimensions
        int interiorW = maxX - minX + 1;
        int interiorH = maxY - minY + 1;
        int interiorD = maxZ - minZ + 1;

        if (interiorW < def.minInteriorW() || interiorW > def.maxInteriorW()) return null;
        if (interiorH < def.minInteriorH() || interiorH > def.maxInteriorH()) return null;
        if (interiorD < def.minInteriorD() || interiorD > def.maxInteriorD()) return null;

        // Controller must be on the shell, not in the interior
        if (!isOnShell(cx, cy, cz, shellMinX, shellMinY, shellMinZ, shellMaxX, shellMaxY, shellMaxZ))
            return null;

        // Validate every shell position
        for (int x = shellMinX; x <= shellMaxX; x++) {
            for (int y = shellMinY; y <= shellMaxY; y++) {
                for (int z = shellMinZ; z <= shellMaxZ; z++) {
                    // Skip interior cells
                    if (x > shellMinX && x < shellMaxX
                     && y > shellMinY && y < shellMaxY
                     && z > shellMinZ && z < shellMaxZ) continue;
                    BlockType b = world.getBlock(x, y, z);
                    if (b == null || !def.isShellBlock(b)) return null;
                }
            }
        }

        return new MultiBlockInstance(def.id(), cx, cy, cz,
                shellMinX, shellMinY, shellMinZ, shellMaxX, shellMaxY, shellMaxZ);
    }

    /**
     * True if (x,y,z) is on the surface of the shell box (not in interior,
     * not outside).
     */
    private static boolean isOnShell(int x, int y, int z,
                                     int sMinX, int sMinY, int sMinZ,
                                     int sMaxX, int sMaxY, int sMaxZ) {
        if (x < sMinX || x > sMaxX || y < sMinY || y > sMaxY || z < sMinZ || z > sMaxZ)
            return false; // Outside the box entirely
        boolean onFaceX = (x == sMinX || x == sMaxX);
        boolean onFaceY = (y == sMinY || y == sMaxY);
        boolean onFaceZ = (z == sMinZ || z == sMaxZ);
        return onFaceX || onFaceY || onFaceZ;
    }

    /**
     * Packs interior cell coordinates into a long key.  Uses 21 bits per axis,
     * matching {@code World}'s block-key encoding.
     */
    private static long interiorKey(int x, int y, int z) {
        return ((long)(x & 0x1FFFFF)) | (((long)(y & 0x1FFFFF)) << 21) | (((long)(z & 0x1FFFFF)) << 42);
    }

    private PatternValidator() {}
}
