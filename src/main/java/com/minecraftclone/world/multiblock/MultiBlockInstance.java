package com.minecraftclone.world.multiblock;

import java.util.HashMap;
import java.util.Map;

/**
 * A successfully formed multi-block structure.
 *
 * <p>Stores the identity of the definition that formed it, the position of its
 * controller block, and the full axis-aligned bounding box of the structure
 * (including shell walls). Also carries a generic key→value data bag so
 * individual definitions can stash per-instance state (e.g. liquid type and
 * amount for a smeltery) without subclassing.
 *
 * <p>Instances are created by {@link MultiBlockManager} when a formation scan
 * succeeds and are discarded when any block inside the bounding box changes.
 */
public final class MultiBlockInstance {

    /** Which definition produced this instance. */
    public final String definitionId;

    /** World position of the controller block. */
    public final int controllerX, controllerY, controllerZ;

    /**
     * Bounding box of the full structure (inclusive, including shell walls).
     * Interior cells span {@code (minX+1, minY+1, minZ+1)} to
     * {@code (maxX-1, maxY-1, maxZ-1)}.
     */
    public final int minX, minY, minZ, maxX, maxY, maxZ;

    /** Generic per-instance data bag — definitions may store arbitrary state here. */
    private final Map<String, Object> data = new HashMap<>();

    public MultiBlockInstance(String definitionId,
                              int controllerX, int controllerY, int controllerZ,
                              int minX, int minY, int minZ,
                              int maxX, int maxY, int maxZ) {
        this.definitionId = definitionId;
        this.controllerX = controllerX;
        this.controllerY = controllerY;
        this.controllerZ = controllerZ;
        this.minX = minX;  this.minY = minY;  this.minZ = minZ;
        this.maxX = maxX;  this.maxY = maxY;  this.maxZ = maxZ;
    }

    /**
     * True if {@code (x, y, z)} falls inside (or on the boundary of) this
     * instance's bounding box.
     */
    public boolean contains(int x, int y, int z) {
        return x >= minX && x <= maxX
            && y >= minY && y <= maxY
            && z >= minZ && z <= maxZ;
    }

    /**
     * True if {@code (x, y, z)} is strictly inside the hollow interior
     * (not part of the shell wall).
     */
    public boolean isInterior(int x, int y, int z) {
        return x > minX && x < maxX
            && y > minY && y < maxY
            && z > minZ && z < maxZ;
    }

    // -----------------------------------------------------------------------
    // Generic data bag
    // -----------------------------------------------------------------------

    /** Store an arbitrary value under {@code key}. */
    public void set(String key, Object value) { data.put(key, value); }

    /** Retrieve a value, or {@code null} if not present. */
    public Object get(String key) { return data.get(key); }

    /** Retrieve an int value with a default. */
    public int getInt(String key, int defaultValue) {
        Object v = data.get(key);
        return v instanceof Integer i ? i : defaultValue;
    }

    /** Retrieve a float value with a default. */
    public float getFloat(String key, float defaultValue) {
        Object v = data.get(key);
        return v instanceof Float f ? f : defaultValue;
    }

    @Override
    public String toString() {
        return String.format("MultiBlockInstance[%s @ (%d,%d,%d) bounds=(%d,%d,%d)-(%d,%d,%d)]",
                definitionId, controllerX, controllerY, controllerZ,
                minX, minY, minZ, maxX, maxY, maxZ);
    }
}
