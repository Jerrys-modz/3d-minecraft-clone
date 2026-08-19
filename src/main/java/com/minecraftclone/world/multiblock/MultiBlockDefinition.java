package com.minecraftclone.world.multiblock;

import com.minecraftclone.world.BlockType;
import com.minecraftclone.world.World;

/**
 * Describes a family of multi-block structures: what blocks are valid shell
 * material, what the minimum and maximum interior dimensions are, and what to
 * do when the structure forms or breaks.
 *
 * <p>To add a new multi-block type:
 * <ol>
 *   <li>Implement this interface.</li>
 *   <li>Register it with {@link MultiBlockManager#register} before the first
 *       game tick.</li>
 *   <li>Optionally implement a matching {@link MultiBlockEntity} for per-tick
 *       behaviour (smelting, heating, etc.) and return it from
 *       {@link #createEntity}.</li>
 * </ol>
 *
 * <p>The {@link MultiBlockManager} calls {@link #tryForm} whenever a block
 * near a controller changes and calls {@link #onDeform} when any block inside
 * a formed instance is removed.
 */
public interface MultiBlockDefinition {

    /** Unique stable identifier for this definition (used in entity type strings). */
    String id();

    /**
     * True if placing/breaking a block of this type should trigger a nearby
     * formation scan. Typically covers both the controller block and all valid
     * wall/floor/ceiling blocks.
     */
    boolean isRelevantBlock(BlockType type);

    /**
     * True for any block that can legally form the shell (walls, floor, ceiling)
     * of the structure, including the controller.
     */
    boolean isShellBlock(BlockType type);

    /**
     * True if this block type is the specific controller block for this definition.
     * Used to identify valid controller positions during formation scans.
     * Defaults to checking if the block is both relevant and a shell block.
     */
    default boolean isControllerBlock(BlockType type) {
        return isRelevantBlock(type) && isShellBlock(type);
    }

    /**
     * True for any block that is allowed inside the hollow interior.
     * Usually {@code AIR}, fluid, or small decorations.
     */
    boolean isInteriorBlock(BlockType type);

    /** Minimum allowed interior width (X-axis or shorter horizontal axis). */
    int minInteriorW();

    /** Maximum allowed interior width (X-axis or shorter horizontal axis). */
    int maxInteriorW();

    /** Minimum allowed interior height. */
    int minInteriorH();

    /** Maximum allowed interior height. */
    int maxInteriorH();

    /** Minimum allowed interior depth (Z-axis or longer horizontal axis). */
    int minInteriorD();

    /** Maximum allowed interior depth (Z-axis or longer horizontal axis). */
    int maxInteriorD();

    /**
     * Maximum number of interior cells. Formation BFS aborts early if this is
     * exceeded, preventing runaway scans in open terrain.
     * Default: {@code maxInteriorW * maxInteriorH * maxInteriorD}.
     */
    default int maxInteriorVolume() {
        return maxInteriorW() * maxInteriorH() * maxInteriorD();
    }

    /**
     * How far from a changed block to search for existing controller blocks.
     * Defaults to {@code max(maxInteriorW, maxInteriorH, maxInteriorD) + 1}.
     */
    default int scanRadius() {
        return Math.max(maxInteriorW(), Math.max(maxInteriorH(), maxInteriorD())) + 1;
    }

    /**
     * Try to form this multi-block structure with its controller at
     * {@code (cx, cy, cz)}.  Returns a fully populated {@link MultiBlockInstance}
     * on success, or {@code null} if the structure is not currently valid.
     *
     * <p>The default implementation uses a BFS interior flood-fill — see
     * {@link PatternValidator#findHollowBox}.  Override when a structure has a
     * different topology (e.g. a T-shape, a stack of rings, etc.).
     */
    default MultiBlockInstance tryForm(World world, int cx, int cy, int cz) {
        return PatternValidator.findHollowBox(world, cx, cy, cz, this);
    }

    /**
     * Called when a formerly valid instance breaks (a block inside changed).
     * Should undo whatever {@link #onForm} did (e.g. revert the controller to
     * its inactive state) and remove any registered {@link MultiBlockEntity}.
     *
     * @param instance the now-invalid instance
     * @param world    the world (use to place/remove blocks and block entities)
     */
    void onDeform(MultiBlockInstance instance, World world);

    /**
     * Called immediately after the instance is registered as formed.
     * Default is a no-op; override to transform the controller block, spawn
     * particles, etc.
     *
     * @param instance the newly formed instance
     * @param world    the world
     */
    default void onForm(MultiBlockInstance instance, World world) {}

    /**
     * Create and register the {@link MultiBlockEntity} for this structure's
     * controller position.  Called by the manager just after the instance is
     * registered.  Return {@code null} if no block entity is needed.
     */
    default MultiBlockEntity createEntity(MultiBlockInstance instance) {
        return null;
    }
}
