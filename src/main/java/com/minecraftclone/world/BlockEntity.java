package com.minecraftclone.world;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/**
 * A block entity: a placed block that carries persistent state beyond its id -
 * a furnace's slots and smelting progress today, and (in time) chests, machines,
 * signs and anything else that needs to remember things. State is kept in memory
 * per block position by {@link World} and persisted with its chunk via
 * {@link ChunkStorage}.
 * <p>
 * Adding a new block entity is three steps:
 * <ol>
 *   <li>Implement this interface (each type has a stable string {@link #type()}).</li>
 *   <li>Register it with a factory in {@link BlockEntities} so saved data can be
 *       rebuilt on load.</li>
 *   <li>Give {@link #blockType()} the block it lives in, and override {@link #tick(float)}
 *       if it does something over time.</li>
 * </ol>
 */
public interface BlockEntity {

    /** Stable, unique type name persisted with the entity (see {@link BlockEntities}). */
    String type();

    /** The block this entity lives in - used to prune entities whose block has been removed. */
    BlockType blockType();

    /** Serializes the entity's state - everything needed to resume exactly where it left off. */
    void writeTo(DataOutput out) throws IOException;

    /** Restores the state written by {@link #writeTo}; the caller pairs these 1:1. */
    void readFrom(DataInput in) throws IOException;

    /** Advances the entity's behaviour by {@code dt} seconds of world time; passive entities leave it as a no-op. */
    default void tick(float dt) {
    }

    /**
     * Whether this block entity is currently "active" - e.g. a furnace that's
     * burning, which switches its block's front face to the glowing variant
     * (see {@link BlockAccessor#isBlockActive}). Defaults to false; machines
     * override it based on their own state.
     */
    default boolean isActive() {
        return false;
    }

    /**
     * How much light an active entity emits (0 = none), Minecraft-style 0-15.
     * A burning furnace glows like a torch (13); future machines can use any
     * level. Only consulted when {@link #isActive()} is true - see
     * {@code World.collectNearbyLights}.
     */
    default int activeLightLevel() {
        return 0;
    }
}
