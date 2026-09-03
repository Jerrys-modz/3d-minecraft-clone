package com.minecraftclone.world;

import com.minecraftclone.util.AABB;
import org.joml.Vector3f;

/**
 * An oak boat: a rideable entity that floats on the surface of water.
 *
 * <h3>Lifecycle</h3>
 * <ol>
 *   <li><b>Place</b>: right-click any water block while holding an
 *       {@code OAK_BOAT} item. The boat spawns on the water surface;
 *       the item is consumed (except in creative).</li>
 *   <li><b>Board</b>: right-click the boat empty-handed within 3 blocks.</li>
 *   <li><b>Steer</b>: W/A/S/D while mounted. The boat follows the camera's
 *       horizontal yaw.</li>
 *   <li><b>Exit</b>: hold Shift to dismount; the boat stays in place.</li>
 *   <li><b>Retrieve</b>: left-click (break) the boat to pop it back to the
 *       player's inventory as an OAK_BOAT item.</li>
 * </ol>
 *
 * <p>Boats are <b>transient</b> — not saved — like dropped items and arrows.
 */
public class BoatEntity {

    /** Hull half-extents for collision and pick tests. */
    public static final float HALF_W = 0.7f;
    public static final float HEIGHT = 0.5f;

    /** Maximum forward/backward speed in blocks/second. */
    private static final float MAX_SPEED = 6f;
    /** Horizontal acceleration while a steering key is held (blocks/second²). */
    private static final float ACCEL = 12f;
    /** Drag factor — the fraction of velocity kept per second (exponential decay). */
    private static final float DRAG = 4f;
    /** Vertical spring constant: how aggressively the hull tracks the water surface. */
    private static final float FLOAT_SPRING = 14f;
    /** Gravity when there is no water below (boat fell off the edge), blocks/second². */
    private static final float GRAVITY = 20f;
    /**
     * How many blocks below the boat's current position the scanner looks for
     * water.  Keeps costs low while still catching a brief drop off the surface.
     */
    private static final int WATER_SCAN_DEPTH = 5;

    /** World position — bottom-centre of the hull. */
    public final Vector3f position = new Vector3f();

    private float vx, vz; // horizontal velocity, blocks/second
    private float vy;     // vertical velocity, blocks/second

    /** True while a player is riding this boat. */
    private boolean mounted;

    public BoatEntity(float x, float y, float z) {
        position.set(x, y, z);
    }

    // -----------------------------------------------------------------------
    // Accessors
    // -----------------------------------------------------------------------

    public Vector3f getPosition() { return position; }

    public boolean isMounted() { return mounted; }

    public void setMounted(boolean mounted) { this.mounted = mounted; }

    /**
     * Returns the AABB of the hull in world space — used by the right-click
     * proximity check and by future picking/rendering helpers.
     */
    public AABB aabb() {
        return new AABB(
                position.x - HALF_W, position.y, position.z - HALF_W,
                position.x + HALF_W, position.y + HEIGHT, position.z + HALF_W);
    }

    /**
     * Vertical offset from the hull bottom to where the player's feet rest
     * while seated.
     */
    public float mountYOffset() { return HEIGHT * 0.6f; }

    // -----------------------------------------------------------------------
    // Physics
    // -----------------------------------------------------------------------

    /**
     * Advances the boat by {@code dt} seconds.
     *
     * @param dt      frame time in seconds
     * @param fwd     W key held
     * @param back    S key held
     * @param left    A key held
     * @param right   D key held
     * @param frontX  X component of the camera's horizontal forward vector
     * @param frontZ  Z component of the camera's horizontal forward vector
     * @param blocks  block accessor used for the water-surface scan below the
     *                hull; a lightweight {@link BlockAccessor} stub is fine for
     *                unit tests
     */
    public void tick(float dt,
                     boolean fwd, boolean back, boolean left, boolean right,
                     float frontX, float frontZ,
                     BlockAccessor blocks) {

        // --- Steering ---
        // Normalise the forward direction in case the caller didn't.
        float flen = (float) Math.sqrt(frontX * frontX + frontZ * frontZ);
        if (flen < 1e-4f) { flen = 1f; }
        float fx = frontX / flen;
        float fz = frontZ / flen;

        // Right is the 90°-clockwise perpendicular of forward in the X-Z plane.
        float rx = -fz;
        float rz =  fx;

        float ax = 0f, az = 0f;
        if (fwd)   { ax += fx;  az += fz;  }
        if (back)  { ax -= fx;  az -= fz;  }
        if (right) { ax += rx;  az += rz;  }
        if (left)  { ax -= rx;  az -= rz;  }

        // Normalise diagonal input so it does not run faster than cardinal.
        float alen = (float) Math.sqrt(ax * ax + az * az);
        if (alen > 1f) { ax /= alen; az /= alen; }

        vx += ax * ACCEL * dt;
        vz += az * ACCEL * dt;

        // Clamp horizontal speed.
        float speed = (float) Math.sqrt(vx * vx + vz * vz);
        if (speed > MAX_SPEED) {
            vx = vx / speed * MAX_SPEED;
            vz = vz / speed * MAX_SPEED;
        }

        // Exponential drag.
        float drag = (float) Math.exp(-DRAG * dt);
        vx *= drag;
        vz *= drag;

        // --- Vertical: float to water surface or fall ---
        float targetY = waterSurfaceY(blocks);
        if (targetY >= 0f) {
            // Spring the hull toward the surface.
            vy = (targetY - position.y) * FLOAT_SPRING;
        } else {
            // No water below — gravity pulls the boat down.
            vy -= GRAVITY * dt;
        }

        // --- Integrate position, resolving each axis against block collision ---
        float newX = position.x + vx * dt;

        // Test X movement.
        AABB atX = new AABB(newX - HALF_W, position.y, position.z - HALF_W,
                            newX + HALF_W, position.y + HEIGHT, position.z + HALF_W);
        if (collidesHorizontally(blocks, atX)) {
            newX = position.x;
            vx   = 0f;
        }

        float newZ = position.z + vz * dt;
        // Test Z movement (using the possibly-adjusted X).
        AABB atZ = new AABB(newX - HALF_W, position.y, newZ - HALF_W,
                            newX + HALF_W, position.y + HEIGHT, newZ + HALF_W);
        if (collidesHorizontally(blocks, atZ)) {
            newZ = position.z;
            vz   = 0f;
        }

        float newY = position.y + vy * dt;
        // Test Y movement (using adjusted X and Z positions).
        AABB atY = new AABB(newX - HALF_W, newY, newZ - HALF_W,
                            newX + HALF_W, newY + HEIGHT, newZ + HALF_W);
        if (collidesHorizontally(blocks, atY)) {
            newY = position.y;
            vy   = 0f;
        }

        position.x = newX;
        position.y = newY;
        position.z = newZ;

        // Hard floor at Y = 0 (bedrock).
        if (position.y < 0f) { position.y = 0f; vy = 0f; }
    }

    /**
     * Convenience overload: tick a non-steered (drifting) boat.  Passes zero
     * for all steering flags so the boat just floats in place.
     *
     * @param blocks block accessor for the water-surface scan
     */
    public void tick(float dt, BlockAccessor blocks) {
        tick(dt, false, false, false, false, 0f, 1f, blocks);
    }

    // -----------------------------------------------------------------------
    // Internal helpers
    // -----------------------------------------------------------------------

    /**
     * Scans downward from near the hull bottom to find the topmost water block,
     * and returns the Y coordinate where the hull should float (i.e. one block
     * above that water block's top face).
     *
     * <p>Returns {@code -1f} if no water is found within {@link #WATER_SCAN_DEPTH}
     * blocks, if a solid non-fluid block is encountered first, or if
     * {@code blocks} is {@code null}.
     *
     * @param blocks block accessor for the column scan
     */
    private float waterSurfaceY(BlockAccessor blocks) {
        if (blocks == null) return -1f;
        // Use the boat's centre column.
        int bx = (int) Math.floor(position.x);
        int bz = (int) Math.floor(position.z);
        // Start the scan one block above the current hull position so the boat
        // can re-enter water after a small bounce.
        int startY = Math.min((int) Math.ceil(position.y) + 1, Chunk.HEIGHT - 1);
        int endY   = Math.max(0, startY - WATER_SCAN_DEPTH);

        for (int by = startY; by >= endY; by--) {
            BlockType b = blocks.getBlock(bx, by, bz);
            if (b.isWater()) {
                // Hull bottom floats at the top face of this water block.
                // Lava is intentionally excluded: oak boats cannot float on lava.
                return by + 1.0f;
            }
            if (b != BlockType.AIR) {
                // Hit a solid block before water — no surface to float on.
                return -1f;
            }
        }
        return -1f; // scan exhausted without finding water
    }

    /**
     * Returns {@code true} if {@code box} overlaps any collidable block reachable
     * via {@code blocks}.  Returns {@code false} when {@code blocks} is
     * {@code null} (e.g. in unit tests that only exercise vertical physics).
     *
     * <p>Mirrors the logic in {@code Mob.collidesAt} so boats and mobs share the
     * same solid-block contract.
     */
    private static boolean collidesHorizontally(BlockAccessor blocks, AABB box) {
        if (blocks == null) return false;
        int minX = (int) Math.floor(box.minX);
        int maxX = (int) Math.floor(box.maxX - 1e-4f);
        int minY = (int) Math.floor(box.minY);
        int maxY = (int) Math.floor(box.maxY - 1e-4f);
        int minZ = (int) Math.floor(box.minZ);
        int maxZ = (int) Math.floor(box.maxZ - 1e-4f);

        for (int bx = minX; bx <= maxX; bx++) {
            for (int by = minY; by <= maxY; by++) {
                for (int bz = minZ; bz <= maxZ; bz++) {
                    BlockType t = blocks.getBlock(bx, by, bz);
                    if (!t.isCollidable()) continue;
                    for (AABB blockBox : t.collisionBoxes(bx, by, bz,
                            blocks.getBlockOrientation(bx, by, bz))) {
                        if (box.intersects(blockBox)) return true;
                    }
                }
            }
        }
        return false;
    }
}
