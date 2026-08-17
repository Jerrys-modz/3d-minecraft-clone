package com.minecraftclone.player;

import com.minecraftclone.GameMode;
import com.minecraftclone.engine.Camera;
import com.minecraftclone.engine.Input;
import com.minecraftclone.engine.KeyBindings;
import com.minecraftclone.util.AABB;
import com.minecraftclone.world.BlockAccessor;
import com.minecraftclone.world.BlockType;
import com.minecraftclone.world.World;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;

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
    /** Blocks above the eye that count as shelter from the cold (a roof overhead). */
    private static final int COLD_ROOF_CHECK = 8;
    /** Horizontal radius (blocks) in which a burning fire warms you against the cold. */
    private static final int COLD_FIRE_RADIUS = 3;
    /** Horizontal radius (blocks) in which a heat source heats the sealed space around you. */
    private static final int COLD_HEAT_RADIUS = 6;
    /** Seconds of staying sealed to fully heat the space around you. */
    private static final float SPACE_WARM_UP_SECONDS = 25f;
    /** Seconds of being breached/outside for the space's stored heat to fully leak away. */
    private static final float SPACE_COOL_SECONDS = 45f;
    /** Coarse grid cell (in blocks) that stores space heat, so a whole small house is one cell. */
    private static final int SPACE_HEAT_CELL = 8;
    /** Most heat cells to remember before evicting the coldest (bounds memory across a long playthrough). */
    private static final int SPACE_HEAT_CELL_LIMIT = 128;
    /**
     * Stored heat per coarse cell (key = column cell), so a house keeps its
     * warmth after you step out: the value at the player's current cell rises
     * while the space is sealed and leaks while breached, and the others persist
     * (and slowly age) rather than resetting when the player moves away.
     */
    private final Map<Long, Float> spaceHeat = new HashMap<>();

    private static long spaceHeatCell(int x, int z) {
        return ((long) (x >> 3) << 32) | (z >> 3 & 0xFFFFFFFFL);
    }

    /** How warm the enclosed space around the player is right now (0..1) - drives the cold exposure and F3 readout. */
    public float getSpaceWarmth() {
        return spaceHeat.getOrDefault(spaceHeatCell((int) Math.floor(position.x), (int) Math.floor(position.z)), 0f);
    }

    private static final float WALK_SPEED = 4.3f;
    private static final float SPRINT_SPEED = 6.6f;
    private static final float FLY_SPEED = 12.0f;
    private static final float FLY_SPRINT_SPEED = 20.0f;
    private static final float JUMP_VELOCITY = 8.2f;
    private static final float GRAVITY = 24.0f;
    private static final float TERMINAL_VELOCITY = -50f;

    // Swimming: water is buoyant rather than a floor, so it gets its own,
    // much gentler vertical model instead of gravity/jump-impulse. A held
    // direction (jump = up, fly-down's key = down) swims at SWIM_SPEED;
    // released, the player sinks slowly under WATER_GRAVITY rather than
    // free-falling - which also happens to cancel most fall damage on its
    // own, since diving in decelerates you to WATER_SINK_SPEED well before
    // you'd reach a real lakebed, instead of hitting it at terminal velocity.
    private static final float SWIM_SPEED = 3.6f;
    private static final float SWIM_SPRINT_SPEED = 5.4f;
    private static final float WATER_GRAVITY = 5.0f;
    private static final float WATER_SINK_SPEED = -1.3f;

    private static final float DEFAULT_MOUSE_SENSITIVITY = 0.12f;
    /** Max gap between two W presses for it to count as a double-tap (sprint, or fly-toggle in creative). */
    private static final float DOUBLE_TAP_WINDOW = 0.3f;

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
    private KeyBindings keyBinds = new KeyBindings();
    private boolean invertMouseY = false;
    private boolean viewBobbing = true;
    private float bobPhase = 0f;

    public void setKeyBinds(KeyBindings keyBinds) {
        this.keyBinds = keyBinds;
    }

    private boolean onGround = false;
    private boolean flying = false;
    private boolean movingOnGround = false; // moving horizontally while grounded (drives view bob)
    private boolean swimmingAndMoving = false; // moving horizontally while swimming (drives the stroke sound)
    private float lastFallImpactSpeed = 0f;
    private final DoubleTapDetector wTapDetector = new DoubleTapDetector(DOUBLE_TAP_WINDOW);
    private boolean sprintLatched = false; // sprint started by a double-tap, held until W is released

    // Transient one-frame flags recomputed fresh every update() - not "consumed",
    // just true exactly on the frame the event happened, false otherwise. Exist
    // purely so the caller (Main) can trigger a sound effect without duplicating
    // any of this class's physics/collision logic to detect the same moments.
    private boolean justJumped = false;
    private boolean justLanded = false;
    private boolean wasOnGround = true; // starts true: spawning in doesn't count as "landing"
    private boolean landingArmed = false; // landing detection is disarmed until the first real ground contact
    // Head-underwater state, recomputed every update() (also drives the breath/
    // drowning mechanic in PlayerStats) - exposed read-only so Main can trigger
    // a splash sound on the frame it changes, without recomputing the same
    // world lookup a second time itself.
    private boolean submerged = false;
    // Any part of the body's hitbox overlapping a water cell, recomputed
    // every updateMovement() call - distinct from submerged (eye point only)
    // and broader than swimming (which also requires not standing on
    // anything). True the instant your feet touch the surface, e.g. jumping
    // into a shallow pool that never reaches your eyes - see Main's splash
    // sound, which used to only trigger off submerged and so stayed silent
    // for exactly that case.
    private boolean inWater = false;
    // Body-in-water-and-not-standing-on-anything state, recomputed every
    // updateMovement() call - distinct from submerged (which only checks the
    // eye/camera point): this drives swim physics and is true well before the
    // head goes under, e.g. wading chest-deep. Exposed so Main can play a
    // stroke sound while actively swimming.
    private boolean swimming = false;
    private static final float LANDING_SOUND_MIN_SPEED = 4f; // ignore trivial dips, only a real fall

    public void spawn(World world, float x, float z) {
        int surfaceY = world.getSurfaceHeight((int) Math.floor(x), (int) Math.floor(z));
        position.set(x, surfaceY + 2, z);
        velocity.set(0, 0, 0);
        camera.setPosition(x, position.y + EYE_HEIGHT, z);
        landingArmed = false; // disarm landing detection until the first real ground contact
    }

    /** Full respawn: stats and position reset, as if starting over (used after death). */
    public void respawn(World world, float x, float z) {
        spawn(world, x, z);
        stats.reset();
    }

    /**
     * Teleports the player to the given absolute position, zeroing all velocity.
     * Unlike spawn/respawn this doesn't probe the world for a surface - the
     * caller (dimension teleporting) has already picked a safe landing spot.
     */
    public void teleportTo(float x, float y, float z) {
        position.set(x, y, z);
        velocity.set(0, 0, 0);
        camera.setPosition(x, y + EYE_HEIGHT, z);
        onGround = false;
        lastFallImpactSpeed = 0f;
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

    /** Enables/disables the subtle camera bob while walking on the ground (settings menu). */
    public void setViewBobbing(boolean enabled) {
        this.viewBobbing = enabled;
        if (!enabled) bobPhase = 0f;
    }

    /** Enables/disables inverted vertical mouse look (settings menu). */
    public void setInvertMouseY(boolean invert) {
        this.invertMouseY = invert;
    }

    /** The walk-bob phase (radians) - drives the held-item sway in first person. */
    public float getBobPhase() {
        return bobPhase;
    }

    /** Sets the current game mode (creative/spectator are invulnerable, spectator is no-clip). */
    public void setGameMode(GameMode mode) {
        this.gameMode = mode;
        if (mode.isSpectator()) {
            flying = true;
        } else if (!mode.isCreative()) {
            // Only creative (and spectator) can fly - leaving creative drops you out of flight.
            flying = false;
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

    /**
     * Applies {@code amount} damage to the player, mitigated by armor and accumulated
     * for armor wear. Use this for external damage sources (mobs, lightning, etc.) so
     * they route through the same armor-mitigation and armor-wear path as environmental
     * hazards - and so they respect the invulnerability check, which a direct
     * {@code getStats().damage(...)} call would silently skip.
     * <p>
     * Refreshes the armor multiplier from the current inventory first: callers may run
     * before or after {@link #update}, so this can't rely on that frame's {@code update}
     * having already refreshed it.
     */
    public void takeDamage(float amount) {
        if (gameMode.isInvulnerable()) return;
        stats.setArmorMultiplier(Armor.damageMultiplier(inventory.armorDefense()));
        stats.damage(amount);
    }

    /**
     * Wears equipped armor by whatever damage has accumulated this frame and clears the
     * accumulator. Called once per frame from {@code Main}, after every damage source for
     * the frame (environmental hazards inside {@link #update}, plus mob/lightning hits via
     * {@link #takeDamage} which can happen before or after it) has had a chance to run -
     * consuming the accumulator from inside {@code update} itself would miss damage from
     * calls made later in the same frame, deferring their wear to the next frame instead
     * (and, worse, applying it to whatever armor happens to be equipped by then).
     */
    public void finalizeDamage() {
        wearArmor(stats.frameDamageAccumulator());
        stats.clearFrameDamage();
    }

    /**
     * @param coldFactor 0..1 how cold the current weather is (snow/blizzard); the
     *                    player's shelter and nearby fires cut it down to the
     *                    effective coldness that drains hunger/freezes (see PlayerStats).
     */
    public void update(float dt, Input input, World world, float coldFactor) {
        updateLook(input);
        if (gameMode.isSpectator()) {
            flying = true; // always in no-clip flight
        } else if (gameMode.isCreative()) {
            updateFlyToggle(input); // F toggles flight in creative only
        }
        updateDoubleTapW(input, dt);
        boolean sprintingAndMoving = updateMovement(dt, input, world);
        updateBobbing(dt, sprintingAndMoving);

        // A "just landed" thump: onGround flipping on this frame specifically
        // (not "is currently resting on the ground", which is true every frame
        // afterward too) with enough fall speed that it wasn't just a trivial
        // step-down. Checked here, before lastFallImpactSpeed resets below.
        // Landing detection is gated: disarmed at spawn, armed on first ground
        // contact - checked with the *previous* frame's armed state (not
        // updated in place first) specifically so the very landing that arms
        // it - the initial touchdown after spawning in mid-air - doesn't also
        // count as a "just landed" thump on that same frame.
        justLanded = computeJustLanded(landingArmed, onGround, wasOnGround, lastFallImpactSpeed);
        if (onGround) landingArmed = true;
        wasOnGround = onGround;

        submerged = world.getBlock(
                (int) Math.floor(position.x), (int) Math.floor(position.y + EYE_HEIGHT), (int) Math.floor(position.z))
                .isWater();

        if (gameMode.isInvulnerable()) {
            stats.forceFull();
        } else {
            stats.setArmorMultiplier(Armor.damageMultiplier(inventory.armorDefense()));
            boolean inLava = overlapsAny(world, aabbAt(position), BlockType::isLava);
            boolean inFire = overlapsAny(world, aabbAt(position), b -> b == BlockType.FIRE);
            // Cold exposure: how cold the local temperature is, cut down by how
            // sheltered the space around you is and how warm your armor is. A
            // sealed space (walls all around, a roof, solid ground) only warms
            // up if it contains a HEAT SOURCE - a fire, torch, lamp, burning
            // furnace or lava - and the HOUSE keeps that warmth: the value is
            // stored per 8x8 cell, so it cools only while you're standing in it
            // and the space is breached (a door opening or a wall block breaking
            // lets the cold in - a closed door counts as a wall, an open one
            // doesn't) or its fire goes out. A nearby fire warms you almost
            // completely either way.
            float coldness = coldFactor;
            boolean heating = isEnclosed(world) && hasHeatSource(world);
            long cell = spaceHeatCell((int) Math.floor(position.x), (int) Math.floor(position.z));
            float spaceWarmth = updateSpaceHeat(spaceHeat, cell, heating, dt);
            ageStoredHeat(dt);
            if (heating) {
                // Sealed and heated: the space holds heat, so the cold barely reaches you.
                coldness *= 1f - 0.9f * spaceWarmth;
            } else {
                // Not fully sealed: a roof alone still breaks the wind a little.
                if (hasRoofAbove(world, (int) Math.floor(position.x), (int) Math.floor(position.z),
                        (int) Math.floor(position.y + EYE_HEIGHT))) {
                    coldness *= 0.5f;
                }
            }
            if (fireNearby(world)) coldness *= 0.15f;
            coldness *= Armor.coldMultiplier(inventory.totalArmorWarmth());
            stats.update(dt, inLava, inFire, submerged, sprintingAndMoving, lastFallImpactSpeed, coldness);
        }
        lastFallImpactSpeed = 0f;
    }

    /**
     * True if the space around the player is fully enclosed: a solid block in
     * every cardinal direction at body height, a roof overhead, and solid ground
     * underfoot. A closed door/trapdoor is solid and seals a room; the moment it
     * opens - or a wall/roof block breaks - the space is open to the elements
     * again.
     */
    private boolean isEnclosed(World world) {
        return isEnclosedAt(world, position.x, position.y, position.z);
    }

    /**
     * The pure enclosure test for the space around a position, split out so the
     * rules (walls on all sides, a roof overhead, solid ground) are unit-testable
     * headlessly against a fake {@link BlockAccessor}.
     */
    static boolean isEnclosedAt(BlockAccessor world, float x, float y, float z) {
        int ix = (int) Math.floor(x);
        int iz = (int) Math.floor(z);
        int waist = (int) Math.floor(y + 0.6f);
        int head = (int) Math.floor(y + 1.2f);
        return wallsOnAllSides(world, ix, iz, waist, head)
                && hasRoofAbove(world, ix, iz, head)
                && solidBelow(world, ix, iz, y);
    }

    private static boolean wallsOnAllSides(BlockAccessor world, int x, int z, int waist, int head) {
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] d : dirs) {
            int nx = x + d[0], nz = z + d[1];
            boolean wall = world.getBlock(nx, waist, nz).solid || world.getBlock(nx, head, nz).solid;
            if (!wall) return false;
        }
        return true;
    }

    private static boolean solidBelow(BlockAccessor world, int x, int z, float y) {
        int by = (int) Math.floor(y - 0.6f);
        return by >= 0 && by < com.minecraftclone.world.Chunk.HEIGHT && world.getBlock(x, by, z).solid;
    }

    /** True if a solid block sits within {@link #COLD_ROOF_CHECK} blocks overhead - basic shelter. */
    private static boolean hasRoofAbove(BlockAccessor world, int x, int z, int y) {
        for (int i = 1; i <= COLD_ROOF_CHECK; i++) {
            if (y + i >= 0 && y + i < com.minecraftclone.world.Chunk.HEIGHT) {
                BlockType b = world.getBlock(x, y + i, z);
                if (b.solid) return true;
            }
        }
        return false;
    }

    /**
     * Steps a cell's stored warmth: it rises toward 1 while the space is sealed
     * AND being heated (a fire/torch/lamp/furnace/lava inside), and falls toward
     * 0 once it's breached or its heat source goes out.
     */
    static float stepSpaceWarmth(float current, boolean heating, float dt) {
        if (heating) {
            return Math.min(1f, current + dt / SPACE_WARM_UP_SECONDS);
        }
        return Math.max(0f, current - dt / SPACE_COOL_SECONDS);
    }

    /**
     * Advances one heat cell's stored warmth and writes it back into {@code heat}
     * (removing it once it cools to zero). Split out so the "house holds its
     * temperature" behavior - a cell keeps its value while you're elsewhere - is
     * unit-testable headlessly.
     */
    static float updateSpaceHeat(Map<Long, Float> heat, long cell, boolean heating, float dt) {
        float value = stepSpaceWarmth(heat.getOrDefault(cell, 0f), heating, dt);
        if (value <= 0f) {
            heat.remove(cell);
        } else {
            heat.put(cell, value);
        }
        return value;
    }

    /**
     * Slowly ages every remembered heat cell so an abandoned house eventually
     * cools (over ~5 minutes), and evicts the coldest cell once too many have
     * been remembered so memory stays bounded across a long playthrough.
     */
    private void ageStoredHeat(float dt) {
        if (spaceHeat.size() < SPACE_HEAT_CELL_LIMIT) {
            for (Map.Entry<Long, Float> e : spaceHeat.entrySet()) {
                e.setValue(Math.max(0f, e.getValue() - dt / 300f));
            }
        } else {
            long coldestKey = -1;
            float coldest = Float.MAX_VALUE;
            for (Map.Entry<Long, Float> e : spaceHeat.entrySet()) {
                if (e.getValue() < coldest) {
                    coldest = e.getValue();
                    coldestKey = e.getKey();
                }
            }
            if (coldestKey != -1) spaceHeat.remove(coldestKey);
        }
    }

    /**
     * Wears the equipped armor by the damage just taken: each piece loses
     * durability proportional to the hit (see {@link Armor#durabilityCost}) and
     * is unequipped once worn out, exactly like a tool breaking.
     */
    public void wearArmor(float damage) {
        if (damage <= 0f) return;
        int cost = Armor.durabilityCost(damage);
        for (int slot = 0; slot < Inventory.ARMOR_SLOT_COUNT; slot++) {
            BlockType piece = inventory.armorType(slot);
            if (piece != null && durability.wear(piece, cost)) {
                inventory.setArmor(slot, null);
            }
        }
    }

    /**
     * True if a burning fire is within {@link #COLD_FIRE_RADIUS} blocks - huddling close warms you.
     */
    private boolean fireNearby(World world) {
        int cx = (int) Math.floor(position.x);
        int cy = (int) Math.floor(position.y);
        int cz = (int) Math.floor(position.z);
        for (int dx = -COLD_FIRE_RADIUS; dx <= COLD_FIRE_RADIUS; dx++) {
            for (int dz = -COLD_FIRE_RADIUS; dz <= COLD_FIRE_RADIUS; dz++) {
                for (int dy = -1; dy <= 2; dy++) {
                    if (world.getBlock(cx + dx, cy + dy, cz + dz) == BlockType.FIRE) return true;
                }
            }
        }
        return false;
    }

    /**
     * True if a heat source (fire, torch, lamp, an actively-burning furnace, or
     * lava) is within {@link #COLD_HEAT_RADIUS} blocks - what makes a sealed
     * house actually heat up. A plain sealed room with nothing burning in it
     * stays cold inside; an unlit furnace is just cold stone.
     */
    private boolean hasHeatSource(World world) {
        int cx = (int) Math.floor(position.x);
        int cy = (int) Math.floor(position.y);
        int cz = (int) Math.floor(position.z);
        for (int dx = -COLD_HEAT_RADIUS; dx <= COLD_HEAT_RADIUS; dx++) {
            for (int dz = -COLD_HEAT_RADIUS; dz <= COLD_HEAT_RADIUS; dz++) {
                for (int dy = -2; dy <= 3; dy++) {
                    int bx = cx + dx, by = cy + dy, bz = cz + dz;
                    BlockType block = world.getBlock(bx, by, bz);
                    if (block.isHeatSource()) return true;
                    if (block == BlockType.FURNACE && world.isBlockActive(bx, by, bz)) return true;
                }
            }
        }
        return false;
    }

    /** True on exactly the frame the player left the ground under their own jump (not falling off a ledge). */
    public boolean hasJustJumped() {
        return justJumped;
    }

    /** True on exactly the frame the player lands hard enough for it to be worth a sound (not a trivial step-down). */
    public boolean hasJustLanded() {
        return justLanded;
    }

    /** Whether the player's head (eye position) is currently inside a water block. */
    public boolean isSubmerged() {
        return submerged;
    }

    /** Whether the player is currently swimming: in water and not standing on anything (see swim physics in updateMovement). */
    public boolean isSwimming() {
        return swimming;
    }

    /** Whether any part of the player's hitbox is touching water - true well before (or even without) the eyes going under; see {@link #isSubmerged()}. */
    public boolean isInWater() {
        return inWater;
    }

    /** Whether the player is currently walking/sprinting on solid ground (not flying, not airborne) - drives footstep sounds. */
    public boolean isMovingOnGround() {
        return movingOnGround;
    }

    /** Whether the player is currently swimming *and* moving horizontally - drives the stroke sound. */
    public boolean isSwimmingAndMoving() {
        return swimmingAndMoving;
    }

    /** The block directly under the player's feet - what a footstep sound should sound like. */
    public BlockType blockUnderfoot(World world) {
        return world.getBlock((int) Math.floor(position.x), (int) Math.floor(position.y) - 1, (int) Math.floor(position.z));
    }

    private void updateLook(Input input) {
        float dx = (float) input.getDeltaX();
        float dy = (float) input.getDeltaY();
        float pitchDir = invertMouseY ? 1f : -1f;
        camera.addRotation(dx * mouseSensitivity, pitchDir * dy * mouseSensitivity);
        input.resetMouseDelta();
    }

    private void updateFlyToggle(Input input) {
        if (input.isKeyJustPressed(keyBinds.get(KeyBindings.FLY_TOGGLE))) {
            flying = !flying;
            velocity.y = 0;
        }
    }

    /** What a completed double-tap of W should do, decided by {@link #decideDoubleTapWAction}. */
    enum WTapAction {NONE, START_FLYING, SPRINT}

    /**
     * Pure decision for what a double-tap of W means: in creative, while not
     * already flying, it takes off (same as {@code F}); while already
     * flying, it instead latches on a faster flying speed (the flight
     * equivalent of ground sprint); on the ground (any mode) it latches
     * ground sprint on - Minecraft-style, without needing to hold anything.
     * <p>
     * Deliberately never turns flight itself off - {@code alreadyFlying}
     * always yields {@code SPRINT}, never a fly-toggle. A double-tap that
     * could switch flight off too meant any stray double-tap while just
     * trying to move forward mid-flight - easy to do by accident - dropped
     * you straight out of the sky. {@code F} is still the deliberate way to
     * land. No GLFW/Input dependency, so this is directly unit testable.
     */
    static WTapAction decideDoubleTapWAction(boolean doubleTapped, boolean alreadyFlying, boolean creative) {
        if (!doubleTapped) return WTapAction.NONE;
        if (alreadyFlying) return WTapAction.SPRINT;
        return creative ? WTapAction.START_FLYING : WTapAction.SPRINT;
    }

    /**
     * Pure decision for whether this frame's ground contact is a real "just
     * landed" thump worth a sound - true only once landing detection is
     * {@code armed} (see {@link #landingArmed}), {@code onGround} just
     * flipped true this frame ({@code wasOnGround} is still false, last
     * frame's value), and the fall was hard enough to matter.
     * <p>
     * Takes {@code armed} as a plain boolean (the caller's *current* value,
     * from before it arms for next frame) rather than re-deriving it here,
     * specifically so the touchdown that arms it - landing for the very
     * first time after spawning in mid-air - doesn't also satisfy this same
     * check on that same frame: arming and checking against the same
     * already-updated flag would let that first landing slip through as a
     * "just landed" event too, which is exactly the spurious spawn-landing
     * sound this gate exists to suppress. No World/GL dependency, so this is
     * directly unit testable.
     */
    static boolean computeJustLanded(boolean armed, boolean onGround, boolean wasOnGround, float fallImpactSpeed) {
        return armed && onGround && !wasOnGround && fallImpactSpeed >= LANDING_SOUND_MIN_SPEED;
    }

    /**
     * Whether the player should use swim physics this frame: their body overlaps
     * water and they aren't resting on anything solid underneath it (that's just
     * wading, handled by normal ground movement) - and flying/no-clip always wins,
     * exactly like it already overrides gravity. No World/GL dependency, so this
     * is directly unit testable.
     */
    static boolean computeSwimming(boolean flying, boolean spectator, boolean onGround, boolean inWater) {
        return !flying && !spectator && !onGround && inWater;
    }

    /**
     * One frame of swim vertical physics: a held stroke (up or down) moves at a
     * flat {@link #SWIM_SPEED} in that direction; released, buoyancy takes over -
     * {@code currentVy} decays toward {@link #WATER_SINK_SPEED} under
     * {@link #WATER_GRAVITY} rather than free-falling under full gravity, which
     * is also what keeps diving into water from dealing fall damage: by the time
     * a dive reaches a real lakebed its vertical speed has already been capped
     * here, well below what it entered the water at. No World/GL dependency, so
     * this is directly unit testable.
     */
    static float swimVerticalVelocity(boolean strokeUp, boolean strokeDown, float currentVy, float dt) {
        if (strokeUp) return SWIM_SPEED;
        if (strokeDown) return -SWIM_SPEED;
        return Math.max(currentVy - WATER_GRAVITY * dt, WATER_SINK_SPEED);
    }

    /**
     * The fall-impact speed to record for a landing: the raw one, unless the
     * player is also touching water right where they're landing, in which case
     * it's capped to the same safe speed buoyancy would have slowed them to.
     * Covers landings {@link #swimVerticalVelocity} alone can't: a fast fall can
     * cross a shallow puddle and hit the solid floor beneath it within a single
     * physics step, never getting a full frame of buoyancy first (updateMovement's
     * swimming check runs against the position *before* that same step). No
     * World/GL dependency, so this is directly unit testable.
     */
    static float landingImpactSpeed(float rawImpactSpeed, boolean landingInWater) {
        return landingInWater ? Math.min(rawImpactSpeed, -WATER_SINK_SPEED) : rawImpactSpeed;
    }

    private void updateDoubleTapW(Input input, float dt) {
        boolean doubleTapped = wTapDetector.tick(dt, input.isKeyJustPressed(keyBinds.get(KeyBindings.FORWARD)));
        switch (decideDoubleTapWAction(doubleTapped, flying, gameMode.isCreative())) {
            case START_FLYING -> {
                flying = true;
                velocity.y = 0;
            }
            case SPRINT -> sprintLatched = true;
            case NONE -> {}
        }
        if (!input.isKeyDown(keyBinds.get(KeyBindings.FORWARD))) {
            sprintLatched = false;
        }
    }

    /** Returns true if the player is sprinting and actually moving this frame (for stamina/hunger drain). */
    private boolean updateMovement(float dt, Input input, World world) {
        justJumped = false;
        Vector3f front = camera.getFrontFlat();
        Vector3f right = new Vector3f(-front.z, 0, front.x); // matches Camera.getRight()'s front-cross-up convention

        float moveX = 0, moveZ = 0;
        if (input.isKeyDown(keyBinds.get(KeyBindings.FORWARD))) { moveX += front.x; moveZ += front.z; }
        if (input.isKeyDown(keyBinds.get(KeyBindings.BACK))) { moveX -= front.x; moveZ -= front.z; }
        if (input.isKeyDown(keyBinds.get(KeyBindings.RIGHT))) { moveX += right.x; moveZ += right.z; }
        if (input.isKeyDown(keyBinds.get(KeyBindings.LEFT))) { moveX -= right.x; moveZ -= right.z; }

        boolean moving = (moveX * moveX + moveZ * moveZ) > 0.0001f;
        float len = (float) Math.sqrt(moveX * moveX + moveZ * moveZ);
        if (moving) {
            moveX /= len;
            moveZ /= len;
        }

        // Swimming: in water and not standing on anything solid - a diver mid-water
        // column, not someone wading in the shallows with their feet on the bottom
        // (that's onGround, and just walks slower through the water like normal
        // ground movement). Flying overrides it entirely, same as it overrides gravity.
        inWater = overlapsAny(world, aabbAt(position), BlockType::isWater);
        swimming = computeSwimming(flying, gameMode.isSpectator(), onGround, inWater);

        boolean sprinting = (input.isKeyDown(keyBinds.get(KeyBindings.SPRINT)) || sprintLatched) && stats.canSprint();
        float speed;
        if (flying) {
            speed = sprinting ? FLY_SPRINT_SPEED : FLY_SPEED;
        } else if (swimming) {
            speed = sprinting ? SWIM_SPRINT_SPEED : SWIM_SPEED;
        } else {
            speed = sprinting ? SPRINT_SPEED : WALK_SPEED;
        }

        velocity.x = moveX * speed;
        velocity.z = moveZ * speed;

        if (flying) {
            float vy = 0;
            if (input.isKeyDown(keyBinds.get(KeyBindings.JUMP))) vy += speed;
            if (input.isKeyDown(keyBinds.get(KeyBindings.FLY_DOWN))) vy -= speed;
            velocity.y = vy;
        } else if (swimming) {
            boolean strokeUp = input.isKeyDown(keyBinds.get(KeyBindings.JUMP));
            boolean strokeDown = input.isKeyDown(keyBinds.get(KeyBindings.FLY_DOWN));
            velocity.y = swimVerticalVelocity(strokeUp, strokeDown, velocity.y, dt);
        } else {
            velocity.y -= GRAVITY * dt;
            velocity.y = Math.max(velocity.y, TERMINAL_VELOCITY);
            if (onGround && input.isKeyDown(keyBinds.get(KeyBindings.JUMP))) {
                velocity.y = JUMP_VELOCITY;
                onGround = false;
                justJumped = true;
            }
        }

        moveAndCollide(world, velocity.x * dt, velocity.y * dt, velocity.z * dt);
        // Refresh against the post-move position/onGround: moveAndCollide can
        // surface the player onto solid ground (or carry them out of the water
        // entirely) this same frame, and the pre-move swimming computed above is
        // stale by then - isSwimming()/isSwimmingAndMoving() (debug overlay,
        // stroke sound) would otherwise report swimming for one extra frame.
        inWater = overlapsAny(world, aabbAt(position), BlockType::isWater);
        swimming = computeSwimming(flying, gameMode.isSpectator(), onGround, inWater);
        movingOnGround = onGround && moving && !flying;
        swimmingAndMoving = swimming && moving;
        return sprinting && moving;
    }

    /** Advances the subtle walk-bob phase when the player is moving on the ground, and applies it to the camera. */
    private void updateBobbing(float dt, boolean sprintingAndMoving) {
        if (viewBobbing && movingOnGround) {
            bobPhase += dt * (sprintingAndMoving ? 14f : 10f);
        } else {
            bobPhase = 0f;
        }
        float bobY = (float) Math.sin(bobPhase) * 0.035f;
        float bobX = (float) Math.cos(bobPhase) * 0.02f;
        camera.setPosition(position.x + bobX, position.y + EYE_HEIGHT + bobY, position.z);
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
    private static boolean blockCollides(World world, AABB box, int x, int y, int z) {
        BlockType type = world.getBlock(x, y, z);
        if (!type.isCollidable()) return false;
        for (AABB blockBox : type.collisionBoxes(x, y, z, world.getBlockOrientation(x, y, z))) {
            if (box.intersects(blockBox)) return true;
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

        // Whether we're grounded going into this move - gates auto-step-up so
        // the player climbs stairs by walking, not by mid-air flailing.
        boolean grounded = onGround;
        onGround = false;

        // Y axis
        AABB box = aabbAt(position);
        AABB movedY = box.offset(0, dy, 0);
        if (collidesAt(world, movedY)) {
            if (dy < 0) {
                onGround = true;
                // Landing in water this same frame - possible even if updateMovement's
                // swimming check (based on the position before this move) said the
                // player wasn't swimming yet, since a fast fall can cross a shallow
                // puddle and hit the solid floor beneath it within a single physics
                // step, never getting a full frame of buoyancy to slow it down first.
                // Cap the recorded impact the same way buoyancy would have.
                boolean landingInWater = overlapsAny(world, movedY, BlockType::isWater);
                lastFallImpactSpeed = Math.max(lastFallImpactSpeed, landingImpactSpeed(-velocity.y, landingInWater));
            }
            velocity.y = 0;
            dy = 0;
        } else {
            position.y += dy;
        }

        // X axis - if blocked, try stepping up onto a stair's low step instead.
        box = aabbAt(position);
        AABB movedX = box.offset(dx, 0, 0);
        if (collidesAt(world, movedX)) {
            if (!stepUp(world, dx, 0, grounded)) {
                velocity.x = 0;
            }
        } else {
            position.x += dx;
        }

        // Z axis
        box = aabbAt(position);
        AABB movedZ = box.offset(0, 0, dz);
        if (collidesAt(world, movedZ)) {
            if (!stepUp(world, 0, dz, grounded)) {
                velocity.z = 0;
            }
        } else {
            position.z += dz;
        }

        if (position.y < -32) {
            // Fell out of the world (e.g. into an unloaded chunk) - respawn upward.
            velocity.set(0, 0, 0);
            position.y = 96;
        }
    }

    /**
     * Minecraft-style auto-step-up: when a horizontal move is blocked, tries
     * climbing a half-block step (a stair's low step, or a slab) instead of
     * stopping. Only attempted while on the ground, and only if there's room to
     * stand on the raised position with the horizontal move completing - so a
     * full block still stops you, but stairs and slabs are walkable without
     * jumping. Returns true if the move completed via the step.
     */
    private boolean stepUp(World world, float dx, float dz, boolean grounded) {
        if (!grounded) return false;
        float step = 0.5f;
        AABB raised = aabbAt(position).offset(0, step, 0);
        if (collidesAt(world, raised)) return false;                    // no headroom above
        AABB raisedMoved = raised.offset(dx, 0, dz);
        if (collidesAt(world, raisedMoved)) return false;               // still blocked up there
        position.y += step;
        position.x += dx;
        position.z += dz;
        onGround = true;
        return true;
    }
}
