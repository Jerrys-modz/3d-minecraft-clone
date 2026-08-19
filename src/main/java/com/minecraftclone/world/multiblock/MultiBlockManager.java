package com.minecraftclone.world.multiblock;

import com.minecraftclone.world.BlockEntity;
import com.minecraftclone.world.BlockType;
import com.minecraftclone.world.World;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * World-level registry and coordinator for all multi-block structures.
 *
 * <p>One {@code MultiBlockManager} lives inside {@link World}.  All
 * {@link MultiBlockDefinition}s are registered here at startup via
 * {@link #register}.  Each frame, {@link #onBlockChanged} is called by
 * {@code World.setBlock} to detect formations and deformations.
 *
 * <h3>Design: O(1) deform, bounded scan on form</h3>
 * <ul>
 *   <li>Two maps key instances: one by controller position and one mapping
 *       every block position that is inside any instance's bounding box to
 *       that instance.  The second map makes deformation detection O(1).</li>
 *   <li>Formation scans are bounded: for each registered definition whose
 *       "relevant block" test matches the changed block, we search within
 *       {@code definition.scanRadius()} for controller blocks and attempt
 *       {@link MultiBlockDefinition#tryForm} there.  The BFS inside
 *       {@link PatternValidator} already has a hard interior-volume cap.</li>
 * </ul>
 *
 * <p>Re-entrance guard: {@code setBlock} → {@code onBlockChanged} →
 * {@code def.onForm} may call {@code setBlock} again (to change the
 * controller to its active state).  A simple boolean flag prevents the
 * nested call from triggering a second formation scan.
 */
public final class MultiBlockManager {

    private final List<MultiBlockDefinition> definitions = new ArrayList<>();

    /**
     * Formed instances keyed by their controller position (packed long,
     * same encoding as World.blockKey).
     */
    private final Map<Long, MultiBlockInstance> byController = new HashMap<>();

    /**
     * Spatial index: maps every block position inside a formed instance's
     * bounding box to that instance, for O(1) "does this change break a
     * structure?" lookup.
     *
     * <p>Populated lazily when an instance is registered and cleared when
     * the instance deforms.  Instances with large bounding boxes populate
     * many entries — the maximum supported size (9×9×9) produces at most 729
     * entries, which is fine for a HashMap.
     */
    private final Map<Long, MultiBlockInstance> byPosition = new HashMap<>();

    /** Re-entrance guard: true while processing an onBlockChanged event. */
    private boolean processing = false;

    // -----------------------------------------------------------------------
    // Registration
    // -----------------------------------------------------------------------

    /**
     * Register a multi-block definition.  Must be called before the first
     * game tick (typically in the same static block or constructor that sets
     * up the World).
     */
    public void register(MultiBlockDefinition def) {
        definitions.add(def);
    }

    // -----------------------------------------------------------------------
    // Queries
    // -----------------------------------------------------------------------

    /**
     * The formed instance whose controller is at {@code (x, y, z)}, or
     * {@code null}.
     */
    public MultiBlockInstance instanceAt(int x, int y, int z) {
        return byController.get(blockKey(x, y, z));
    }

    /**
     * The formed instance whose bounding box contains {@code (x, y, z)}, or
     * {@code null}.  Returns the instance even if {@code (x, y, z)} is on the
     * shell (not only the interior).
     */
    public MultiBlockInstance instanceContaining(int x, int y, int z) {
        return byPosition.get(blockKey(x, y, z));
    }

    // -----------------------------------------------------------------------
    // Lifecycle
    // -----------------------------------------------------------------------

    /**
     * Called by {@code World.setBlock} whenever a block changes.  Handles
     * both deforming any existing structure that contains this position and
     * attempting to form new structures near this position.
     *
     * <p>Must not be called while already in progress (re-entrance guard).
     */
    public void onBlockChanged(World world, int x, int y, int z) {
        if (processing) return; // prevent re-entrance from onForm/onDeform setBlock calls
        processing = true;
        try {
            handleDeform(world, x, y, z);
            handleForm(world, x, y, z);
        } finally {
            processing = false;
        }
    }

    /**
     * Called by {@code World} when a chunk at {@code (chunkX, chunkZ)} is
     * unloaded.  Removes all formed multi-block instances whose bounding
     * boxes intersect the unloaded chunk, clearing them from both the
     * {@code byController} and {@code byPosition} maps.
     *
     * <p>This ensures that when chunks reload, the multiblock can form fresh
     * instances without stale entity references or inconsistent state.
     *
     * @param world  the world (used to call onDeform callbacks)
     * @param chunkX chunk X coordinate
     * @param chunkZ chunk Z coordinate
     */
    public void onChunkUnload(World world, int chunkX, int chunkZ) {
        // Chunk world-block bounds (16 blocks per chunk)
        int minX = chunkX * 16;
        int maxX = minX + 15;
        int minZ = chunkZ * 16;
        int maxZ = minZ + 15;

        // Collect instances to remove (avoid concurrent modification)
        List<MultiBlockInstance> toRemove = new ArrayList<>();
        for (MultiBlockInstance inst : byController.values()) {
            // Check if instance's bounding box intersects this chunk
            if (inst.maxX >= minX && inst.minX <= maxX
             && inst.maxZ >= minZ && inst.minZ <= maxZ) {
                toRemove.add(inst);
            }
        }

        // Deform each intersecting instance
        for (MultiBlockInstance inst : toRemove) {
            MultiBlockDefinition def = findDefinition(inst.definitionId);
            if (def != null) {
                def.onDeform(inst, world);
            }
            byController.remove(blockKey(inst.controllerX, inst.controllerY, inst.controllerZ));
            unindexInstance(inst);
        }
    }

    // -----------------------------------------------------------------------
    // Private: deformation
    // -----------------------------------------------------------------------

    /**
     * If (x,y,z) is inside a known instance, deform that instance.
     * Changing a shell block, an interior block, or the controller itself
     * all break the structure.
     */
    private void handleDeform(World world, int x, int y, int z) {
        MultiBlockInstance instance = byPosition.get(blockKey(x, y, z));
        if (instance == null) return;

        // Find the definition to call onDeform
        MultiBlockDefinition def = findDefinition(instance.definitionId);
        if (def != null) {
            def.onDeform(instance, world);
        }

        // Remove from byController
        byController.remove(blockKey(instance.controllerX, instance.controllerY, instance.controllerZ));

        // Remove all position entries for this instance
        unindexInstance(instance);
    }

    // -----------------------------------------------------------------------
    // Private: formation
    // -----------------------------------------------------------------------

    /**
     * Search for controller blocks of each registered definition within
     * {@code scanRadius} of the changed position and try to form there.
     */
    private void handleForm(World world, int x, int y, int z) {
        BlockType changedBlock = world.getBlock(x, y, z);
        if (changedBlock == null) return;

        defLoop: for (MultiBlockDefinition def : definitions) {
            if (!def.isRelevantBlock(changedBlock)) continue;

            int radius = def.scanRadius();
            // Scan the neighborhood for controller blocks of this definition
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dy = -radius; dy <= radius; dy++) {
                    for (int dz = -radius; dz <= radius; dz++) {
                        int cx = x + dx, cy = y + dy, cz = z + dz;
                        // Skip positions that already have a formed instance here
                        if (byController.containsKey(blockKey(cx, cy, cz))) continue;
                        BlockType b = world.getBlock(cx, cy, cz);
                        // Only try formation when this position holds a controller block
                        if (b == null || !isControllerOf(def, b)) continue;

                        MultiBlockInstance instance = def.tryForm(world, cx, cy, cz);
                        if (instance != null) {
                            registerInstance(instance, world, def);
                            continue defLoop; // One formation per definition per change event is enough
                        }
                    }
                }
            }
        }
    }

    /**
     * Returns true if {@code type} is a controller block for {@code def}.
     * Uses the definition's {@link MultiBlockDefinition#isControllerBlock} predicate.
     */
    private static boolean isControllerOf(MultiBlockDefinition def, BlockType type) {
        return def.isControllerBlock(type);
    }

    // -----------------------------------------------------------------------
    // Private: registration and indexing
    // -----------------------------------------------------------------------

    private void registerInstance(MultiBlockInstance instance, World world, MultiBlockDefinition def) {
        long ctrlKey = blockKey(instance.controllerX, instance.controllerY, instance.controllerZ);
        byController.put(ctrlKey, instance);
        indexInstance(instance);

        // Give the definition a chance to transform the controller block, etc.
        def.onForm(instance, world);

        // Create and register the block entity at the controller position
        MultiBlockEntity entity = def.createEntity(instance);
        if (entity != null) {
            world.registerMultiBlockEntity(instance.controllerX, instance.controllerY, instance.controllerZ, entity);
        }
    }

    /** Populate byPosition for every block in the instance's bounding box. */
    private void indexInstance(MultiBlockInstance instance) {
        for (int x = instance.minX; x <= instance.maxX; x++) {
            for (int y = instance.minY; y <= instance.maxY; y++) {
                for (int z = instance.minZ; z <= instance.maxZ; z++) {
                    byPosition.put(blockKey(x, y, z), instance);
                }
            }
        }
    }

    /** Remove all byPosition entries belonging to the given instance. */
    private void unindexInstance(MultiBlockInstance instance) {
        for (int x = instance.minX; x <= instance.maxX; x++) {
            for (int y = instance.minY; y <= instance.maxY; y++) {
                for (int z = instance.minZ; z <= instance.maxZ; z++) {
                    long key = blockKey(x, y, z);
                    if (byPosition.get(key) == instance) {
                        byPosition.remove(key);
                    }
                }
            }
        }
    }

    private MultiBlockDefinition findDefinition(String id) {
        for (MultiBlockDefinition def : definitions) {
            if (def.id().equals(id)) return def;
        }
        return null;
    }

    // -----------------------------------------------------------------------
    // Key encoding (matches World.blockKey)
    // -----------------------------------------------------------------------

    private static long blockKey(int x, int y, int z) {
        return ((long) x & 0x1FFFFFL) | (((long) y & 0x1FFFFFL) << 21) | (((long) z & 0x1FFFFFL) << 42);
    }
}
