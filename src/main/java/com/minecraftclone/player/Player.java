package com.minecraftclone.player;

import com.minecraftclone.GameMode;
import com.minecraftclone.engine.Camera;
import com.minecraftclone.engine.Input;
import com.minecraftclone.util.AABB;
import com.minecraftclone.world.BlockType;
import com.minecraftclone.world.World;
import org.joml.Vector3f;

import static org.lwjgl.glfw.GLFW.*;

/**
 * First-person player controller: walking/flying physics, AABB-vs-voxel
 * collision resolved one axis at a time, mouse look, survival stats
 * (health/hunger/stamina), and a couple of quality-of-life extras (jump,
 * sprint, fly toggle).
 */
public class Player {

    private static final float WIDTH = 0.6f;
    private static final float HEIGHT = 1.8f;
    private static final float EYE_HEIGHT = 1.62f;

    private static final float WALK_SPEED = 4.3f;
    private static final float SPRINT_SPEED = 6.6f;
    private static final float FLY_SPEED = 12.0f;
    private static final float JUMP_VELOCITY = 8.2f;
    private static final float GRAVITY = 24.0f;
    private static final float TERMINAL_VELOCITY = -50f;

    private static final float DEFAULT_MOUSE_SENSITIVITY = 0.12f;

    /** Feet position: bottom-center of the player's bounding box. */
    private final Vector3f position = new Vector3f();
    private final Vector3f velocity = new Vector3f();
    private final Vector3f eyePosition = new Vector3f();
    private final Camera camera = new Camera();
    private final Inventory inventory = new Inventory();
    private final PlayerStats stats = new PlayerStats();
    private final ToolDurability durability = new ToolDurability();

    private float mouseSensitivity = DEFAULT_MOUSE_SENSITIVITY;
    private GameMode gameMode = GameMode.SURVIVAL;

    private boolean onGround = false;
    private boolean flying = false;
    private float lastFallImpactSpeed = 0f;

    public void spawn(World world, float x, float z) {
        int surfaceY = world.getSurfaceHeight((int) Math.floor(x), (int) Math.floor(z));
        position.set(x, surfaceY + 2, z);
        velocity.set(0, 0, 0);
        camera.setPosition(x, position.y + EYE_HEIGHT, z);
    }

    /** Full respawn: stats and position reset, as if starting over (used after death). */
    public void respawn(World world, float x, float z) {
        spawn(world, x, z);
        stats.reset();
    }

    public Camera getCamera() {
        return camera;
    }

    public Inventory getInventory() {
        return inventory;
    }

    public PlayerStats getStats() {
        return stats;
    }

    public ToolDurability getDurability() {
        return durability;
    }

    public Vector3f getPosition() {
        return position;
    }

    /** Eye (camera) position: feet position plus the eye height. Returns a reused vector - copy before retaining. */
    public Vector3f getEyePosition() {
        return eyePosition.set(position.x, position.y + EYE_HEIGHT, position.z);
    }

    public boolean isFlying() {
        return flying;
    }

    /** Overrides mouse-look sensitivity (from the settings menu). */
    public void setMouseSensitivity(float value) {
        this.mouseSensitivity = value;
    }

    /** Sets the current game mode (creative/spectator are invulnerable, spectator is no-clip). */
    public void setGameMode(GameMode mode) {
        this.gameMode = mode;
        if (mode.isSpectator()) {
            flying = true;
        }
    }

    public GameMode getGameMode() {
        return gameMode;
    }

    /** Attempts to eat one unit of {@code food} from the inventory. Returns false if it's not food or there's none held. */
    public boolean eat(BlockType food) {
        if (!food.isEdible()) return false;
        if (!inventory.remove(food, 1)) return false;
        stats.eat(food.foodValue);
        return true;
    }

    public void update(float dt, Input input, World world) {
        updateLook(input);
        if (gameMode.isSpectator()) {
            flying = true; // always in no-clip flight
        } else {
            updateFlyToggle(input);
        }
        boolean sprintingAndMoving = updateMovement(dt, input, world);
        camera.setPosition(position.x, position.y + EYE_HEIGHT, position.z);

        if (gameMode.isInvulnerable()) {
            stats.forceFull();
        } else {
            boolean inLava = overlapsAny(world, aabbAt(position), BlockType::isLava);
            boolean submerged = world.getBlock(
                    (int) Math.floor(position.x), (int) Math.floor(position.y + EYE_HEIGHT), (int) Math.floor(position.z))
                    .isWater();
            stats.update(dt, inLava, submerged, sprintingAndMoving, lastFallImpactSpeed);
        }
        lastFallImpactSpeed = 0f;
    }

    private void updateLook(Input input) {
        float dx = (float) input.getDeltaX();
        float dy = (float) input.getDeltaY();
        camera.addRotation(dx * mouseSensitivity, -dy * mouseSensitivity);
        input.resetMouseDelta();
    }

    private void updateFlyToggle(Input input) {
        if (input.isKeyJustPressed(GLFW_KEY_F)) {
            flying = !flying;
            velocity.y = 0;
        }
    }

    /** Returns true if the player is sprinting and actually moving this frame (for stamina/hunger drain). */
    private boolean updateMovement(float dt, Input input, World world) {
        Vector3f front = camera.getFrontFlat();
        Vector3f right = new Vector3f(-front.z, 0, front.x); // matches Camera.getRight()'s front-cross-up convention

        float moveX = 0, moveZ = 0;
        if (input.isKeyDown(GLFW_KEY_W)) { moveX += front.x; moveZ += front.z; }
        if (input.isKeyDown(GLFW_KEY_S)) { moveX -= front.x; moveZ -= front.z; }
        if (input.isKeyDown(GLFW_KEY_D)) { moveX += right.x; moveZ += right.z; }
        if (input.isKeyDown(GLFW_KEY_A)) { moveX -= right.x; moveZ -= right.z; }

        boolean moving = (moveX * moveX + moveZ * moveZ) > 0.0001f;
        float len = (float) Math.sqrt(moveX * moveX + moveZ * moveZ);
        if (moving) {
            moveX /= len;
            moveZ /= len;
        }

        boolean sprinting = input.isKeyDown(GLFW_KEY_LEFT_CONTROL) && stats.canSprint();
        float speed = flying ? FLY_SPEED : (sprinting ? SPRINT_SPEED : WALK_SPEED);

        velocity.x = moveX * speed;
        velocity.z = moveZ * speed;

        if (flying) {
            float vy = 0;
            if (input.isKeyDown(GLFW_KEY_SPACE)) vy += speed;
            if (input.isKeyDown(GLFW_KEY_LEFT_SHIFT)) vy -= speed;
            velocity.y = vy;
        } else {
            velocity.y -= GRAVITY * dt;
            velocity.y = Math.max(velocity.y, TERMINAL_VELOCITY);
            if (onGround && input.isKeyDown(GLFW_KEY_SPACE)) {
                velocity.y = JUMP_VELOCITY;
                onGround = false;
            }
        }

        moveAndCollide(world, velocity.x * dt, velocity.y * dt, velocity.z * dt);
        return sprinting && moving;
    }

    private AABB aabbAt(Vector3f pos) {
        float hw = WIDTH / 2f;
        return new AABB(pos.x - hw, pos.y, pos.z - hw, pos.x + hw, pos.y + HEIGHT, pos.z + hw);
    }

    private boolean collidesAt(World world, AABB box) {
        int minX = (int) Math.floor(box.minX);
        int maxX = (int) Math.floor(box.maxX - 1e-4f);
        int minY = (int) Math.floor(box.minY);
        int maxY = (int) Math.floor(box.maxY - 1e-4f);
        int minZ = (int) Math.floor(box.minZ);
        int maxZ = (int) Math.floor(box.maxZ - 1e-4f);

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockType type = world.getBlock(x, y, z);
                    if (type.isCollidable()) {
                        AABB blockBox = new AABB(x, y, z, x + 1, y + type.collisionHeight, z + 1);
                        if (box.intersects(blockBox)) return true;
                    }
                }
            }
        }
        return false;
    }

    /** True if any block matching {@code predicate} overlaps the box - used for lava/water hazard checks. */
    private boolean overlapsAny(World world, AABB box, java.util.function.Predicate<BlockType> predicate) {
        int minX = (int) Math.floor(box.minX);
        int maxX = (int) Math.floor(box.maxX - 1e-4f);
        int minY = (int) Math.floor(box.minY);
        int maxY = (int) Math.floor(box.maxY - 1e-4f);
        int minZ = (int) Math.floor(box.minZ);
        int maxZ = (int) Math.floor(box.maxZ - 1e-4f);

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

    /** Moves the player by (dx, dy, dz), resolving collisions one axis at a time. */
    private void moveAndCollide(World world, float dx, float dy, float dz) {
        // Spectator: no-clip, move freely through everything.
        if (gameMode.isSpectator()) {
            position.x += dx;
            position.y += dy;
            position.z += dz;
            onGround = false;
            return;
        }

        onGround = false;

        // Y axis
        AABB box = aabbAt(position);
        AABB movedY = box.offset(0, dy, 0);
        if (collidesAt(world, movedY)) {
            if (dy < 0) {
                onGround = true;
                lastFallImpactSpeed = Math.max(lastFallImpactSpeed, -velocity.y);
            }
            velocity.y = 0;
            dy = 0;
        } else {
            position.y += dy;
        }

        // X axis
        box = aabbAt(position);
        AABB movedX = box.offset(dx, 0, 0);
        if (collidesAt(world, movedX)) {
            velocity.x = 0;
        } else {
            position.x += dx;
        }

        // Z axis
        box = aabbAt(position);
        AABB movedZ = box.offset(0, 0, dz);
        if (collidesAt(world, movedZ)) {
            velocity.z = 0;
        } else {
            position.z += dz;
        }

        if (position.y < -32) {
            // Fell out of the world (e.g. into an unloaded chunk) - respawn upward.
            velocity.set(0, 0, 0);
            position.y = 96;
        }
    }
}
