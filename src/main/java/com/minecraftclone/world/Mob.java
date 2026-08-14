package com.minecraftclone.world;

import com.minecraftclone.util.AABB;
import org.joml.Vector3f;

import java.util.Random;

/**
 * A passive animal wandering the surface - pigs, cows and sheep that idle and
 * stroll around on grass, making the world feel lived-in. Mobs are lightweight:
 * a position/velocity pair plus a tiny wander brain (gravity, AABB-vs-voxel
 * collision resolved per axis, and a "don't walk off cliffs or into walls"
 * check when choosing where to go). They spawn on grass surfaces near the
 * player and despawn once they're far away. Transient - not saved, like
 * dropped items.
 */
public class Mob {

    /** Mob kinds, with their dimensions in blocks and walking speed. */
    public enum Type {
        PIG(0.9f, 0.7f, 1.6f),
        COW(1.0f, 0.9f, 1.4f),
        SHEEP(0.9f, 0.8f, 1.4f);

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

    public final Type type;
    /** Body center of the mob. */
    public final Vector3f position = new Vector3f();
    public final Vector3f velocity = new Vector3f();
    public float age;

    private boolean onGround;
    private boolean moving;
    private float wanderTimer; // time until the next wander decision
    private float moveX, moveZ; // current wander heading (0 or +/-1)

    public Mob(Type type, float x, float y, float z) {
        this.type = type;
        this.position.set(x, y, z);
        this.wanderTimer = 0f; // decide on the first tick
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
     * Advances the mob one tick: gravity, a periodic wander decision, and
     * movement resolved against solid blocks one axis at a time. Pure logic
     * (only {@link BlockAccessor} is used) so it can be tested without GL.
     */
    public void update(float dt, BlockAccessor world, Random rnd) {
        age += dt;

        wanderTimer -= dt;
        if (onGround && wanderTimer <= 0f) {
            chooseWander(world, rnd);
        }

        // Cliff/wall guard mid-stride too: the goal was picked while the ground
        // ahead was solid, but if it runs out before the wander timer does (walking
        // off a ledge), stop and re-decide instead of stepping into thin air.
        if (onGround && moving && !canWalkAhead(world, (int) moveX, (int) moveZ)) {
            moving = false;
            moveX = 0f;
            moveZ = 0f;
            wanderTimer = Math.min(wanderTimer, 0.4f);
        }

        velocity.y -= GRAVITY * dt;
        if (velocity.y < TERMINAL_VELOCITY) {
            velocity.y = TERMINAL_VELOCITY;
        }

        moveAndCollide(world, moveX * type.walkSpeed * dt, velocity.y * dt, moveZ * type.walkSpeed * dt);
    }

    /** Picks a new wander goal: stand still, or walk one of the four cardinal directions. */
    private void chooseWander(BlockAccessor world, Random rnd) {
        moving = false;
        moveX = 0f;
        moveZ = 0f;
        if (rnd.nextFloat() < 0.3f) {
            // Pause and look around for a moment.
            wanderTimer = 1f + rnd.nextFloat() * 2.5f;
            return;
        }
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        int[] d = dirs[rnd.nextInt(dirs.length)];
        if (canWalkAhead(world, d[0], d[1])) {
            moveX = d[0];
            moveZ = d[1];
            moving = true;
            wanderTimer = 1f + rnd.nextFloat() * 2.5f;
        } else {
            // Cliff or wall that way - re-decide shortly.
            wanderTimer = 0.4f;
        }
    }

    /**
     * True if a step in direction (sx, sz) is safe: the ground at the destination
     * is solid (no walking off a cliff) and there's headroom (no walking into a
     * wall). This keeps mobs on the surface without a collision detector.
     */
    private boolean canWalkAhead(BlockAccessor world, int sx, int sz) {
        float look = 0.6f + type.width * 0.4f;
        int bx = (int) Math.floor(position.x + sx * look);
        int bz = (int) Math.floor(position.z + sz * look);
        int feetY = (int) Math.floor(position.y - type.height / 2f);
        return world.getBlock(bx, feetY - 1, bz).isCollidable()
                && !world.getBlock(bx, feetY, bz).isCollidable()
                && !world.getBlock(bx, feetY + 1, bz).isCollidable();
    }

    /** Moves by (dx, dy, dz), resolving collisions one axis at a time. */
    private void moveAndCollide(BlockAccessor world, float dx, float dy, float dz) {
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

        // Wedged against a wall (didn't actually move) - re-decide where to go soon.
        if ((dx != 0f || dz != 0f) && blocked) {
            wanderTimer = Math.min(wanderTimer, 0.3f);
        }
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
