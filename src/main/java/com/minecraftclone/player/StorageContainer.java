package com.minecraftclone.player;

import com.minecraftclone.world.BlockType;

/**
 * A container of item slots - the shared contract behind the player's
 * {@link Inventory}, a placed {@link com.minecraftclone.world.Furnace}, a
 * {@link com.minecraftclone.world.Chest}, and any future machine or storage
 * network (AE2/Refined-Storage-style systems will build on this same slot API).
 * <p>
 * The idea: once something can {@link #typeOf}/{@link #countOf}/{@link
 * #setSlot} a fixed slot space and {@link #add} items to it, every screen that
 * deals in slots (the container GUI, the mouse controller, quick-move logic)
 * works against it without knowing what the container actually is. That's what
 * lets a chest drop in as "just another storage" and lets a future item
 * network expose a virtual, arbitrarily-large container behind the same
 * interface.
 */
public interface StorageContainer {

    /** Number of slots. */
    int size();

    /** The type held in a slot, or null if empty. */
    BlockType typeOf(int slot);

    /** The count in a slot (0 if empty). */
    int countOf(int slot);

    /** Replaces a slot's stack; a null type or non-positive count clears it. */
    void setSlot(int slot, BlockType type, int count);

    /**
     * Adds {@code amount} of {@code type}, topping up existing stacks first and
     * then filling empty slots. Returns the amount that couldn't fit (0 = all
     * placed).
     */
    int add(BlockType type, int amount);

    /** Total count of {@code type} across all slots. */
    int getCount(BlockType type);

    /** True if a slot is empty. */
    default boolean isEmpty(int slot) {
        return typeOf(slot) == null;
    }

    /** Maximum items a single stack of {@code type} can hold. */
    static int maxStack(BlockType type) {
        return Inventory.maxStack(type);
    }
}
