package com.minecraftclone.world;

import com.minecraftclone.util.AABB;
import org.joml.Vector3f;

import java.util.List;
import java.util.Random;

/**
 * A passive animal wandering the surface - pigs, cows and sheep that idle and
 * stroll around on grass, making the world feel lived-in. Mobs are lightweight:
 * a position/velocity pair plus a tiny brain. Instead of blindly bumping into
 * the world, a mob picks a random walkable destination nearby, finds a path to
 * it with {@link Pathfinder} (2D A* over walkable columns, allowing single-block
 * steps and routing around walls and ledges), and follows the waypoints with
 * gravity and per-axis AABB-vs-voxel collision. If the terrain changes under it
 * or a path goes stale it re-routes. Mobs spawn on grass surfaces near the
 * player and despawn once they're far away. Transient - not saved, like dropped
 * items.
 */
public class Mob {

    /** Mob kinds, with their dimensions in blocks and walking speed. */
    public enum Type {
        PIG(0.9f, 0.9f, 1.6f),
        COW(1.0f, 1.0f, 1.4f),
        SHEEP(0.9f, 0.9f, 1.4f);

        public final float width;     // x/z footprint
        public final float height;    // full body height
        public final float walkSpeed; // blocks/second

        Type(float width, float height, float walkSpeed) {
            this.width = width;
            this.height = height;
            this.walkSpeed = walkSpeed;
        }
    }

    private static final float GRAVITY = 30f;
    private static final float TERMINAL_VELOCITY = -40f;
    private static final float WAYPOINT_REACH = 0.4f;  // feet within this of a waypoint center: advance
    private static final float STUCK_THRESHOLD = 0.5f; // blocked this long: give up and re-route
    private static final int MAX_PATH_NODES = 800;     // A* budget per route search

    public final Type type;
    /** Body center of the mob. */
    public final Vector3f position = new Vector3f();
    public final Vector3f velocity = new Vector3f();
    public float age;
    /** Facing direction in radians, 0 = +z; smooths toward the movement direction. */
    public float yaw;

    private boolean onGround;
    private boolean moving;
    private float wanderTimer;  // time until the mob picks a new destination
    private float stuckTimer;   // how long we've been wedged against something
    private List<int[]> path;   // waypoints {x, floorY, z}, from Pathfinder
    private int pathIndex;      // next waypoint to reach

    public Mob(Type type, float x, float y, float z) {
        this.type = type;
        this.position.set(x, y, z);
        this.wanderTimer = 0f; // decide on the first grounded tick
    }

    public boolean isMoving() {
        return moving;
    }

    /** Vertical bob for the renderer - scales with movement so walking looks alive. */
    public float bobOffset() {
        float amp = 0.04f * (moving ? 1f : 0f);
        return (float) Math.sin(age * 9f) * amp;
    }

    /**
     * Advances the mob one tick: gravity, a periodic "pick a new destination"
     * decision, path following (steering toward the next waypoint), and movement
     * resolved against solid blocks one axis at a time. Pure logic (only
     * {@link BlockAccessor} is used) so it can be tested without GL.
     */
    public void update(float dt, BlockAccessor world, Random rnd) {
        age += dt;
        wanderTimer -= dt;

        // When the mob has nothing to walk toward (arrived, or no goal yet), decide
        // where to go next - but only once it's settled on the ground.
        if (onGround && (path == null || pathIndex >= path.size()) && wanderTimer <= 0f) {
            pickNewGoal(world, rnd);
        }

        followPath();

        velocity.y -= GRAVITY * dt;
        if (velocity.y < TERMINAL_VELOCITY) {
            velocity.y = TERMINAL_VELOCITY;
        }

        boolean blocked = moveAndCollide(world, velocity.x * dt, velocity.y * dt, velocity.z * dt);
        if (blocked) {
            // Wedged against something the path didn't expect (e.g. terrain changed) -
            // give up after a moment and pick a new goal.
            stuckTimer += dt;
            if (stuckTimer > STUCK_THRESHOLD) {
                stuckTimer = 0f;
                path = null;
                if (wanderTimer > 0.4f) {
                    wanderTimer = 0.4f;
                }
            }
        } else {
            stuckTimer = 0f;
        }

        // Face the way we're going (smooth turn, so it doesn't snap around).
        float hSpeed = (float) Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z);
        if (hSpeed > 0.1f) {
            float target = (float) Math.atan2(velocity.x, velocity.z);
            yaw = turnToward(yaw, target, 12f * dt);
        }
    }

    /** Rotates {@code current} toward {@code target} by at most {@code maxDelta}, wrapping around -pi..pi. */
    private static float turnToward(float current, float target, float maxDelta) {
        float diff = target - current;
        while (diff > Math.PI) diff -= 2f * (float) Math.PI;
        while (diff < -Math.PI) diff += 2f * (float) Math.PI;
        float step = Math.max(-maxDelta, Math.min(maxDelta, diff));
        return current + step;
    }

    /** Picks a random walkable destination and paths to it; sometimes just idles. */
    private void pickNewGoal(BlockAccessor world, Random rnd) {
        path = null;
        if (rnd.nextFloat() < 0.25f) {
            // Pause and look around for a moment.
            wanderTimer = 1.5f + rnd.nextFloat() * 2.5f;
            return;
        }
        int bx = (int) Math.floor(position.x);
        int bz = (int) Math.floor(position.z);
        int floor = (int) Math.floor(position.y - type.height / 2f);
        int radius = 6 + rnd.nextInt(8);
        for (int attempt = 0; attempt < 8; attempt++) {
            int gx = bx + rnd.nextInt(radius * 2 + 1) - radius;
            int gz = bz + rnd.nextInt(radius * 2 + 1) - radius;
            if (gx == bx && gz == bz) continue;
            if (Pathfinder.floorAt(world, gx, gz, floor) == Pathfinder.NO_FLOOR) continue;
            List<int[]> waypoints = Pathfinder.findPath(world, bx, bz, gx, gz, floor, MAX_PATH_NODES);
            if (waypoints != null && waypoints.size() > 1) {
                path = waypoints;
                pathIndex = 1; // already standing on the start column
                wanderTimer = 4f + rnd.nextFloat() * 4f;
                return;
            }
        }
        // Nothing reachable nearby - pause briefly, then try somewhere else.
        wanderTimer = 1f + rnd.nextFloat() * 2f;
    }

    /** Steers toward the next waypoint, advancing along the path as waypoints are reached. */
    private void followPath() {
        moving = false;
        velocity.x = 0f;
        velocity.z = 0f;
        if (path == null || pathIndex >= path.size()) return;

        int[] wp = path.get(pathIndex);

        // Re-validate the waypoint's floor against where the mob actually is -
        // terrain edits can invalidate a path mid-walk (a floor caved in, a wall
        // appeared). Difference of one is a normal step up/down; more means the
        // path is stale, so abandon it and let the next decision re-route.
        int feetY = (int) Math.floor(position.y - type.height / 2f);
        if (Math.abs(wp[1] - feetY) > 1) {
            path = null;
            return;
        }

        float tx = wp[0] + 0.5f;
        float tz = wp[2] + 0.5f;
        float dx = tx - position.x;
        float dz = tz - position.z;
        float dist = (float) Math.sqrt(dx * dx + dz * dz);
        if (dist < WAYPOINT_REACH) {
            pathIndex++;
            if (pathIndex >= path.size()) {
                path = null;
            }
            return;
        }
        moving = true;
        velocity.x = dx / dist * type.walkSpeed;
        velocity.z = dz / dist * type.walkSpeed;
    }

    /** Moves by (dx, dy, dz), resolving collisions one axis at a time. */
    private boolean moveAndCollide(BlockAccessor world, float dx, float dy, float dz) {
        onGround = false;
        boolean blocked = false;

        // Y axis.
        if (collidesAt(world, box().offset(0, dy, 0))) {
            if (dy < 0) onGround = true;
            velocity.y = 0f;
        } else {
            position.y += dy;
        }

        // X axis - try to step up a single block instead of wedging against it.
        if (collidesAt(world, box().offset(dx, 0, 0))) {
            if (!collidesAt(world, box().offset(dx, 1f, 0))) {
                position.x += dx;
                position.y += 1f;
            } else {
                blocked = true;
            }
        } else {
            position.x += dx;
        }

        // Z axis - same step-up behavior.
        if (collidesAt(world, box().offset(0, 0, dz))) {
            if (!collidesAt(world, box().offset(0, 1f, dz))) {
                position.z += dz;
                position.y += 1f;
            } else {
                blocked = true;
            }
        } else {
            position.z += dz;
        }

        return blocked;
    }

    private boolean collidesAt(BlockAccessor world, AABB box) {
        int minX = (int) Math.floor(box.minX);
        int maxX = (int) Math.floor(box.maxX - 1e-4f);
        int minY = (int) Math.floor(box.minY);
        int maxY = (int) Math.floor(box.maxY - 1e-4f);
        int minZ = (int) Math.floor(box.minZ);
        int maxZ = (int) Math.floor(box.maxZ - 1e-4f);

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockType t = world.getBlock(x, y, z);
                    if (t.isCollidable()) {
                        AABB blockBox = new AABB(x, y, z, x + 1, y + t.collisionHeight, z + 1);
                        if (box.intersects(blockBox)) return true;
                    }
                }
            }
        }
        return false;
    }

    private AABB box() {
        float hw = type.width / 2f;
        float y0 = position.y - type.height / 2f;
        float y1 = position.y + type.height / 2f;
        return new AABB(position.x - hw, y0, position.z - hw, position.x + hw, y1, position.z + hw);
    }
}
