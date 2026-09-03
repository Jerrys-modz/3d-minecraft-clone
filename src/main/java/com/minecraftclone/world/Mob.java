package com.minecraftclone.world;

import com.minecraftclone.util.AABB;
import org.joml.Vector3f;

import java.util.List;
import java.util.Random;

/**
 * A mob wandering the surface - passive animals (pigs, cows, sheep) that idle
 * and stroll around, plus hostile monsters (zombies, skeletons) that spawn at
 * night and hunt the player. Mobs are lightweight: a position/velocity pair
 * plus a tiny brain. Instead of blindly bumping into the world, a mob picks
 * walkable destinations and finds a path to them with {@link Pathfinder} (2D A*
 * over walkable columns, allowing single-block steps and routing around walls
 * and ledges), then follows the waypoints with gravity and per-axis
 * AABB-vs-voxel collision. Passives wander the surface; hostiles chase the
 * player when they notice them and attack on contact (zombies melee, skeletons
 * shoot). Mobs spawn on grass surfaces near the player (hostiles only at
 * night), despawn once they're far away (hostiles also melt away at dawn), and
 * can be killed for drops. Transient - not saved, like dropped items.
 */
public class Mob {

    /** Mob kinds, with their dimensions, walking speed, hit points, and combat role. */
    public enum Type {
        PIG(0.9f, 0.9f, 1.6f, 10f, false, 0f, false),
        COW(1.0f, 1.0f, 1.4f, 10f, false, 0f, false),
        SHEEP(0.9f, 0.9f, 1.4f, 10f, false, 0f, false),
        ZOMBIE(0.7f, 1.8f, 2.0f, 20f, true, 4f, true),
        SKELETON(0.6f, 1.8f, 2.2f, 20f, true, 3f, true),
        // Wild predators - hostile like monsters, but biome-tied and rarer: a
        // wolf stalks forests, a polar bear owns the snowy wastes. Both drop the
        // pelts that the higher fur-armor tiers are made from, and being wild
        // animals (not undead), they stay out by day.
        WOLF(0.8f, 0.8f, 1.4f, 14f, true, 3f, false),
        POLAR_BEAR(1.4f, 1.2f, 1.4f, 30f, true, 6f, false),
        // Phase 0 hostile mobs:
        // Spider: wide and fast, active both day and night (unlike undead it
        // doesn't burn at dawn). Wall-climbing is flagged here but implemented
        // only as a speed boost toward the player; true surface-clinging is a
        // future physics extension.
        SPIDER(1.4f, 0.9f, 2.8f, 16f, true, 3f, false),
        // Creeper: slow-moving hostile that deals high contact damage via the
        // standard melee path (attackDamage=10).  In vanilla Tinkers' Construct
        // a Creeper explodes on close contact; explosion support is a planned
        // future feature.  dawnDespawns=true so it fades at sunrise rather than
        // lingering in daylight.
        CREEPER(0.6f, 1.8f, 1.6f, 20f, true, 10f, true),
        // Passive rideable: a horse that wanders plains and can be saddled and
        // steered at high speed.  Not hostile, doesn't despawn at dawn.
        HORSE(1.2f, 1.6f, 4.5f, 15f, false, 0f, false);

        public final float width;     // x/z footprint
        public final float height;    // full body height
        public final float walkSpeed; // blocks/second
        public final float maxHealth;
        public final boolean hostile;
        public final float attackDamage;
        /** True if this hostile melts away at dawn (undead); wild animals don't. */
        public final boolean dawnDespawns;

        Type(float width, float height, float walkSpeed, float maxHealth, boolean hostile, float attackDamage, boolean dawnDespawns) {
            this.width = width;
            this.height = height;
            this.walkSpeed = walkSpeed;
            this.maxHealth = maxHealth;
            this.hostile = hostile;
            this.attackDamage = attackDamage;
            this.dawnDespawns = dawnDespawns;
        }
    }

    private static final float GRAVITY = 30f;
    private static final float TERMINAL_VELOCITY = -40f;
    private static final float WAYPOINT_REACH = 0.4f;  // feet within this of a waypoint center: advance
    private static final float STUCK_THRESHOLD = 0.5f; // blocked this long: give up and re-route
    private static final int MAX_PATH_NODES = 800;     // A* budget per route search
    private static final float PANIC_TIME = 4f;        // how long a hit passive runs away for
    private static final float PANIC_SPEED_MULT = 1.6f; // flee faster than it walks
    /** Horizontal speed (blocks/second) applied while a player is riding this mob. */
    private static final float RIDE_SPEED = 10f;

    // Swimming/drowning: a mob submerged in water floats and paddles instead of
    // sinking under full gravity, and drowns if it stays fully underwater too
    // long - the same buoyancy the player gets, so mobs can cross (and perish
    // in) water instead of just sinking.
    private static final float WATER_GRAVITY = 5f;      // buoyancy: gentle sink, not free-fall
    private static final float WATER_SINK_SPEED = -0.8f;// terminal sink while paddling
    private static final float SWIM_RISE_ACCEL = 6f;     // paddle acceleration while the head is under water
    private static final float SWIM_SURFACE_SPEED = 1.6f; // rise speed cap while paddling up
    private static final float DROWN_GRACE_SECONDS = 15f;  // how long a mob can hold its breath
    private static final float DROWN_DAMAGE_PER_SECOND = 5f;

    private static final float HOSTILE_DETECT_RANGE = 20f; // notice the player within this many blocks
    private static final float HOSTILE_MELEE_REACH = 1.4f; // adjacent enough to land a melee hit
    private static final float SKELETON_MIN_SHOOT = 3f;    // skeletons back off rather than shoot at point-blank
    private static final float SKELETON_MAX_SHOOT = 14f;
    private static final float MELEE_COOLDOWN = 1f;        // seconds between hits
    private static final float SHOOT_COOLDOWN = 1.5f;      // seconds between arrows

    public final Type type;
    /** Body center of the mob. */
    public final Vector3f position = new Vector3f();
    public final Vector3f velocity = new Vector3f();
    public float age;
    /** Facing direction in radians, 0 = +z; smooths toward the movement direction. */
    public float yaw;
    /** Server-assigned id (0 in single player) - lets a client address a remote mob for attacks. */
    public int id;

    /** Server-reported target pose for remote mobs (interpolated toward on the client). */
    public final Vector3f target = new Vector3f();
    public float targetYaw;

    private float health;
    private boolean onGround;
    private boolean moving;
    private float wanderTimer;  // time until the mob picks a new destination
    private float stuckTimer;   // how long we've been wedged against something
    private List<int[]> path;   // waypoints {x, floorY, z}, from Pathfinder
    private int pathIndex;      // next waypoint to reach
    private float panicTimer;   // > 0: hurt, running away (see fleeX/fleeZ)
    private float fleeX, fleeZ; // unit direction away from the last damage source
    private float attackCooldown;
    private float repathTimer;  // hostiles re-route to the moving player on this interval
    private float meleeRequest; // damage this mob wants to deal the player this frame (0 = none)
    private boolean shootRequest; // true: fire a projectile at the player this frame (skeletons)
    private float submergedTime; // how long the mob's body has been fully under water (drowning)
    private boolean swimming;    // this frame: in water and not standing on the bottom
    private boolean saddled;     // a player has placed a saddle on this mob (horses only)
    private boolean mounted;     // a player is currently riding this mob

    public Mob(Type type, float x, float y, float z) {
        this.type = type;
        this.health = type.maxHealth;
        this.position.set(x, y, z);
        this.wanderTimer = 0f; // decide on the first grounded tick
    }

    public boolean isMoving() {
        return moving;
    }

    public float getHealth() {
        return health;
    }

    public float getMaxHealth() {
        return type.maxHealth;
    }

    public boolean isDead() {
        return health <= 0f;
    }

    /** This mob's collision box, in world coordinates - used for hit-testing the player's attacks. */
    public AABB getAABB() {
        return box();
    }

    public boolean isHostile() {
        return type.hostile;
    }

    public float getAttackDamage() {
        return type.attackDamage;
    }

    /** The item this mob drops when killed. */
    public BlockType dropType() {
        return switch (type) {
            case PIG -> BlockType.RAW_PORKCHOP;
            case COW -> BlockType.RAW_BEEF;
            case SHEEP -> BlockType.MUTTON;
            case ZOMBIE -> BlockType.ROTTEN_FLESH;
            case SKELETON -> BlockType.BONES;
            case WOLF -> BlockType.WOLF_PELT;
            case POLAR_BEAR -> BlockType.BEAR_HIDE;
            case SPIDER -> BlockType.COAL;        // spiders drop string in vanilla; coal is the closest available item
            case CREEPER -> BlockType.COAL;       // creepers drop gunpowder; coal is the placeholder
            case HORSE -> BlockType.RAW_BEEF;     // placeholder; horses drop leather in vanilla
        };
    }

    // -----------------------------------------------------------------------
    // Horse-riding API
    // -----------------------------------------------------------------------

    /** True if a saddle has been placed on this mob (horses only). */
    public boolean isSaddled() { return saddled; }

    /** Places or removes the saddle on this mob. */
    public void setSaddled(boolean s) { saddled = s; }

    /** True while a player is riding this mob. */
    public boolean isMounted() { return mounted; }

    /** Marks this mob as ridden (true) or free (false). */
    public void setMounted(boolean m) { mounted = m; }

    /**
     * Y offset from this mob's body-centre {@link #position} to where the
     * rider's feet rest.  Only meaningful for rideable mobs (currently
     * {@link Type#HORSE}).
     */
    public float mountYOffset() { return type.height * 0.4f; }

    /**
     * Tick used <em>in place of</em> {@link #update} while a player rides this
     * mob.  Applies rider-directed horizontal movement, gravity, and block
     * collision — the normal AI pathfinder is fully bypassed.
     *
     * @param dt      frame time in seconds
     * @param fwd     W key held
     * @param back    S key held
     * @param left    A key held
     * @param right   D key held
     * @param frontX  X component of the camera's horizontal forward vector
     * @param frontZ  Z component of the camera's horizontal forward vector
     * @param world   block accessor for collision and ground detection
     */
    public void rideTick(float dt,
                         boolean fwd, boolean back, boolean left, boolean right,
                         float frontX, float frontZ,
                         BlockAccessor world) {
        // Normalise the camera-forward horizontal vector.
        float flen = (float) Math.sqrt(frontX * frontX + frontZ * frontZ);
        if (flen < 1e-4f) flen = 1f;
        float fx = frontX / flen;
        float fz = frontZ / flen;

        // Right is the 90°-clockwise perpendicular in the XZ-plane.
        float rx = -fz, rz = fx;

        float ax = 0f, az = 0f;
        if (fwd)   { ax += fx; az += fz; }
        if (back)  { ax -= fx; az -= fz; }
        if (right) { ax += rx; az += rz; }
        if (left)  { ax -= rx; az -= rz; }

        // Normalise diagonal input so it doesn't run faster than cardinal.
        float alen = (float) Math.sqrt(ax * ax + az * az);
        if (alen > 1f) { ax /= alen; az /= alen; }

        velocity.x = ax * RIDE_SPEED;
        velocity.z = az * RIDE_SPEED;

        // Gravity — same as the normal update path (no swimming check for
        // simplicity; a ridden horse that enters water just keeps moving).
        if (onGround) {
            velocity.y = Math.max(0f, velocity.y);
        } else {
            velocity.y -= GRAVITY * dt;
            if (velocity.y < TERMINAL_VELOCITY) velocity.y = TERMINAL_VELOCITY;
        }

        moveAndCollide(world, velocity.x * dt, velocity.y * dt, velocity.z * dt);

        // Smooth the facing yaw toward the direction of travel.
        float hSpeed = (float) Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z);
        if (hSpeed > 0.05f) {
            float target = (float) Math.atan2(velocity.x, velocity.z);
            yaw = turnToward(yaw, target, 16f * dt);
        }
    }

    /** Damage the player should take from this mob this frame (0 = none), for hostiles' melee hits. */
    public float getMeleeRequest() {
        return meleeRequest;
    }

    /** True if this mob wants to fire a projectile at the player this frame (skeletons). */
    public boolean wantsToShoot() {
        return shootRequest;
    }

    /**
     * Applies {@code amount} damage from a source at (sourceX, sourceZ): knocks the
     * mob back, panics a passive (it flees for a few seconds - hostiles keep
     * coming), and kills it at zero health. Returns true if the hit killed it.
     */
    public boolean damage(float amount, float sourceX, float sourceZ) {
        health -= amount;
        float dx = position.x - sourceX;
        float dz = position.z - sourceZ;
        float len = (float) Math.sqrt(dx * dx + dz * dz);
        if (len > 1e-4f) {
            fleeX = dx / len;
            fleeZ = dz / len;
        } else {
            fleeX = 0f;
            fleeZ = 1f;
        }
        velocity.x += fleeX * 3f;   // small knockback
        velocity.z += fleeZ * 3f;
        if (!type.hostile) {
            panicTimer = PANIC_TIME;
        }
        return isDead();
    }

    /**
     * Applies drowning damage without the knockback/panic of {@link #damage} - a
     * submerged mob just loses health (and dies at zero) rather than thrashing
     * as if it were being hit. Returns true if it drowned.
     */
    public boolean drown(float amount) {
        health -= amount;
        return isDead();
    }

    /**
     * The t at which the ray (origin, dir) first enters {@code box}, clamped to
     * {@code maxDist}, or -1 if it misses. Slab method; the origin being inside
     * the box counts as a hit at t=0. Used to aim attacks at mobs.
     */
    public static float rayIntersects(Vector3f origin, Vector3f dir, float maxDist, AABB box) {
        float tmin = 0f, tmax = maxDist;
        tmin = Math.max(tmin, slabEntry(origin.x, dir.x, box.minX, box.maxX));
        tmax = Math.min(tmax, slabExit(origin.x, dir.x, box.minX, box.maxX));
        if (tmin > tmax) return -1f;
        tmin = Math.max(tmin, slabEntry(origin.y, dir.y, box.minY, box.maxY));
        tmax = Math.min(tmax, slabExit(origin.y, dir.y, box.minY, box.maxY));
        if (tmin > tmax) return -1f;
        tmin = Math.max(tmin, slabEntry(origin.z, dir.z, box.minZ, box.maxZ));
        tmax = Math.min(tmax, slabExit(origin.z, dir.z, box.minZ, box.maxZ));
        if (tmin > tmax) return -1f;
        return tmin;
    }

    private static float slabEntry(float origin, float dir, float min, float max) {
        if (Math.abs(dir) < 1e-6f) {
            return origin >= min && origin <= max ? Float.NEGATIVE_INFINITY : Float.POSITIVE_INFINITY;
        }
        return Math.min((min - origin) / dir, (max - origin) / dir);
    }

    private static float slabExit(float origin, float dir, float min, float max) {
        if (Math.abs(dir) < 1e-6f) {
            return origin >= min && origin <= max ? Float.POSITIVE_INFINITY : Float.NEGATIVE_INFINITY;
        }
        return Math.max((min - origin) / dir, (max - origin) / dir);
    }

    /** Vertical bob for the renderer - scales with movement so walking looks alive. */
    public float bobOffset() {
        float amp = 0.04f * (moving ? 1f : 0f);
        return (float) Math.sin(age * 9f) * amp;
    }

    /**
     * Smooths the client-side pose toward the server-reported target (multiplayer
     * remote mobs). Lerps position and yaw (wrapping around -pi..pi) so ~10 Hz
     * state packets render as fluid movement instead of teleporting. Also
     * advances the animation clock and flags movement, so remote mobs walk with
     * the same bob/leg-swing as locally simulated ones instead of gliding.
     */
    public void tickRemote(float dt) {
        float t = Math.min(1f, dt * 12f);
        float prevX = position.x, prevY = position.y, prevZ = position.z;
        position.lerp(target, t);
        float diff = targetYaw - yaw;
        while (diff > Math.PI) diff -= 2f * (float) Math.PI;
        while (diff < -Math.PI) diff += 2f * (float) Math.PI;
        yaw += diff * t;
        age += dt;
        float dx = position.x - prevX, dy = position.y - prevY, dz = position.z - prevZ;
        moving = dx * dx + dy * dy + dz * dz > 1e-8f;
    }

    /**
     * Advances the mob one tick: gravity, a periodic "pick a new destination"
     * decision, path following (steering toward the next waypoint), and movement
     * resolved against solid blocks one axis at a time. Pure logic (only
     * {@link BlockAccessor} is used) so it can be tested without GL.
     */
    public void update(float dt, BlockAccessor world, Random rnd) {
        update(dt, world, rnd, null);
    }

    /**
     * Like {@link #update(float, BlockAccessor, Random)} but with the player's
     * position, which hostile mobs chase (passives ignore it). Hostiles also
     * request melee hits and shots here - the caller (World) applies them to the
     * player.
     */
    public void update(float dt, BlockAccessor world, Random rnd, Vector3f playerPos) {
        age += dt;
        wanderTimer -= dt;

        if (type.hostile) {
            updateHostile(dt, world, rnd, playerPos);
        } else if (panicTimer > 0f) {
            panicTimer -= dt;
            // Hurt: bolt away from the last damage source, ignoring the pathfinder -
            // a panicking animal runs (and may stumble off a ledge) rather than
            // route neatly around things.
            path = null;
            moving = true;
            velocity.x = fleeX * type.walkSpeed * PANIC_SPEED_MULT;
            velocity.z = fleeZ * type.walkSpeed * PANIC_SPEED_MULT;
        } else {
            // When the mob has nothing to walk toward (arrived, or no goal yet), decide
            // where to go next - but only once it's settled on the ground.
            if (onGround && (path == null || pathIndex >= path.size()) && wanderTimer <= 0f) {
                pickNewGoal(world, rnd);
            }
            followPath();
        }

        // Swimming: a mob whose body overlaps water and isn't standing on the
        // bottom floats and paddles (buoyancy) rather than sinking under full
        // gravity, so it can cross water and surface to breathe. Flying hostiles
        // and creatures on the bottom just walk through the water as before.
        swimming = !onGround && overlapsWater(world, box());

        if (swimming) {
            if (fullySubmerged(world, box())) {
                // Head under water: accelerate up toward the surface, capped at a
                // fixed paddle speed - accelerating into the cap (rather than
                // snapping straight to it) means a mob that crosses back out of
                // "fully submerged" mid-stroke loses its rise speed gradually
                // instead of the sign flipping instantly, which is what made
                // every mob visibly judder at the surface (was reported as
                // "mobs walk on water bouncing").
                velocity.y = Math.min(velocity.y + SWIM_RISE_ACCEL * dt, SWIM_SURFACE_SPEED);
            } else {
                // Already at/near the surface: gentle buoyant sink capped at a
                // slow terminal speed, not the full paddle rate - unconditionally
                // forcing SWIM_SURFACE_SPEED here (regardless of depth) used to
                // relaunch every mob clean out of the water the instant any part
                // of it touched the surface, so it never settled.
                velocity.y = Math.max(velocity.y - WATER_GRAVITY * dt, WATER_SINK_SPEED);
            }
        } else {
            velocity.y -= GRAVITY * dt;
            if (velocity.y < TERMINAL_VELOCITY) {
                velocity.y = TERMINAL_VELOCITY;
            }
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

        // Drowning: a mob that stays fully underwater (its whole body submerged)
        // runs out of breath after a grace period and takes damage until it
        // surfaces or dies. A surfaced swimmer (head out of water) doesn't
        // accumulate breath - the mob surfaced to breathe.
        boolean fullySubmerged = fullySubmerged(world, box());
        if (fullySubmerged) {
            float submergedBefore = submergedTime;
            submergedTime += dt;
            // Damage only the portion of this dt that's actually past the grace
            // period - the update straddling the boundary (submergedBefore below
            // it, submergedTime past it) would otherwise get charged for the
            // *entire* dt, not just the fraction of it spent drowning.
            float drowningSeconds = submergedTime - Math.max(submergedBefore, DROWN_GRACE_SECONDS);
            if (drowningSeconds > 0f) {
                drown(DROWN_DAMAGE_PER_SECOND * drowningSeconds);
            }
        } else {
            submergedTime = 0f;
        }

        // Face the way we're going (smooth turn, so it doesn't snap around).
        float hSpeed = (float) Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z);
        if (hSpeed > 0.1f) {
            float target = (float) Math.atan2(velocity.x, velocity.z);
            yaw = turnToward(yaw, target, 12f * dt);
        }
    }

    /** True if {@code box} overlaps any water block. */
    private static boolean overlapsWater(BlockAccessor world, AABB box) {
        return overlaps(world, box, BlockType::isWater);
    }

    /** True if {@code box} is entirely inside water (fully submerged - head too). */
    private static boolean fullySubmerged(BlockAccessor world, AABB box) {
        int minX = (int) Math.floor(box.minX), maxX = (int) Math.floor(box.maxX - 1e-4f);
        int minY = (int) Math.floor(box.minY), maxY = (int) Math.floor(box.maxY - 1e-4f);
        int minZ = (int) Math.floor(box.minZ), maxZ = (int) Math.floor(box.maxZ - 1e-4f);
        // The whole body is underwater if every cell it spans is water (the head
        // cell at maxY is water, so the creature can't breathe).
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    if (!world.getBlock(x, y, z).isWater()) return false;
                }
            }
        }
        return true;
    }

    /** True if any block matching {@code predicate} overlaps {@code box}. */
    private static boolean overlaps(BlockAccessor world, AABB box, java.util.function.Predicate<BlockType> predicate) {
        int minX = (int) Math.floor(box.minX), maxX = (int) Math.floor(box.maxX - 1e-4f);
        int minY = (int) Math.floor(box.minY), maxY = (int) Math.floor(box.maxY - 1e-4f);
        int minZ = (int) Math.floor(box.minZ), maxZ = (int) Math.floor(box.maxZ - 1e-4f);
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    if (predicate.test(world.getBlock(x, y, z))) {
                        AABB blockBox = new AABB(x, y, z, x + 1, y + 1, z + 1);
                        if (box.intersects(blockBox)) return true;
                    }
                }
            }
        }
        return false;
    }

    /** Hostile AI: notice and chase the player, attacking on contact (or at range for skeletons). */
    private void updateHostile(float dt, BlockAccessor world, Random rnd, Vector3f playerPos) {
        attackCooldown -= dt;
        repathTimer -= dt;
        meleeRequest = 0f;
        shootRequest = false;

        boolean hasTarget = playerPos != null
                && Math.hypot(playerPos.x - position.x, playerPos.z - position.z) <= HOSTILE_DETECT_RANGE;
        if (!hasTarget) {
            // No player to hunt - idle around like the passives do.
            if (onGround && (path == null || pathIndex >= path.size()) && wanderTimer <= 0f) {
                pickNewGoal(world, rnd);
            }
            followPath();
            return;
        }

        // Chase: re-route to the player's column periodically (they're moving).
        int bx = (int) Math.floor(position.x);
        int bz = (int) Math.floor(position.z);
        int px = (int) Math.floor(playerPos.x);
        int pz = (int) Math.floor(playerPos.z);
        if (path == null || pathIndex >= path.size() || repathTimer <= 0f) {
            repathTimer = 1f;
            int floor = (int) Math.floor(position.y - type.height / 2f);
            List<int[]> waypoints = Pathfinder.findPath(world, bx, bz, px, pz, floor, MAX_PATH_NODES);
            if (waypoints != null && waypoints.size() > 1) {
                path = waypoints;
                pathIndex = 1;
            }
        }
        followPath();

        // Attack: melee once in reach; skeletons prefer shooting from mid range.
        float dist = (float) Math.hypot(playerPos.x - position.x, playerPos.z - position.z);
        if (dist <= HOSTILE_MELEE_REACH && attackCooldown <= 0f) {
            meleeRequest = type.attackDamage;
            attackCooldown = MELEE_COOLDOWN;
        } else if (type == Type.SKELETON && dist >= SKELETON_MIN_SHOOT && dist <= SKELETON_MAX_SHOOT
                && attackCooldown <= 0f) {
            shootRequest = true;
            attackCooldown = SHOOT_COOLDOWN;
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
                    if (blockCollides(world, box, x, y, z)) return true;
                    // A tall block (a fence's 1.5-high box) extends up into this
                    // cell from the one below - check the block beneath too.
                    if (blockCollides(world, box, x, y - 1, z)) return true;
                }
            }
        }
        return false;
    }

    /** True if the collidable block at ({@code x},{@code y},{@code z}) overlaps {@code box}. */
    private static boolean blockCollides(BlockAccessor world, AABB box, int x, int y, int z) {
        BlockType t = world.getBlock(x, y, z);
        if (!t.isCollidable()) return false;
        for (AABB blockBox : t.collisionBoxes(x, y, z, world.getBlockOrientation(x, y, z))) {
            if (box.intersects(blockBox)) return true;
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
