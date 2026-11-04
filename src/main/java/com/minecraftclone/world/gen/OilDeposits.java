package com.minecraftclone.world.gen;

/**
 * Deterministic underground oil pockets: about one chunk in
 * {@value #CHUNK_ODDS} holds a flattened ellipsoid of oil-bearing rock
 * between y={@value #MIN_Y} and y={@value #MAX_Y}, kept inside its own chunk.
 * Uses its own hash stream, so adding oil doesn't move any ore vein.
 */
public final class OilDeposits {

    public static final int CHUNK_ODDS = 6;
    public static final int MIN_Y = 8;
    public static final int MAX_Y = 30;

    private final long seed;

    public OilDeposits(long seed) {
        this.seed = seed ^ 0x0115EEDL;
    }

    /** True if {@code (wx, y, wz)} lies inside its chunk's oil pocket. */
    public boolean oilAt(int wx, int y, int wz) {
        if (y < MIN_Y - 3 || y > MAX_Y + 3) return false;
        int cx = Math.floorDiv(wx, 16), cz = Math.floorDiv(wz, 16);
        long h = hash(cx, cz);
        if (Long.remainderUnsigned(h, CHUNK_ODDS) != 0) return false;
        int centerX = cx * 16 + 6 + (int) ((h >>> 8) & 3);              // 6..9
        int centerZ = cz * 16 + 6 + (int) ((h >>> 10) & 3);
        int centerY = MIN_Y + (int) Long.remainderUnsigned(h >>> 12, MAX_Y - MIN_Y + 1);
        double rh = 3 + ((h >>> 20) & 1);                               // 3..4 wide
        double rv = 2 + ((h >>> 21) & 1);                               // 2..3 tall
        double dx = (wx - centerX) / rh, dy = (y - centerY) / rv, dz = (wz - centerZ) / rh;
        return dx * dx + dy * dy + dz * dz <= 1.0;
    }

    private long hash(int cx, int cz) {
        long h = seed;
        h ^= (long) cx * 0x9E3779B97F4A7C15L;
        h ^= (long) cz * 0xC2B2AE3D27D4EB4FL;
        h ^= (h >>> 31);
        h *= 0xBF58476D1CE4E5B9L;
        h ^= (h >>> 29);
        h *= 0x94D049BB133111EBL;
        h ^= (h >>> 32);
        return h;
    }
}
