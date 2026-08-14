package com.minecraftclone.player;

import com.minecraftclone.world.BlockType;

import java.util.EnumMap;
import java.util.Map;

/**
 * The player's held blocks: a simple count per {@link BlockType}, no stack
 * limits. Breaking a block adds one to its count; placing a block spends one.
 * Not persisted across sessions - a fresh session starts empty, matching a
 * survival-style "gather before you can build" loop.
 */
public class Inventory {

    private final Map<BlockType, Integer> counts = new EnumMap<>(BlockType.class);

    public int getCount(BlockType type) {
        return counts.getOrDefault(type, 0);
    }

    public void add(BlockType type, int amount) {
        if (amount <= 0) return;
        counts.merge(type, amount, Integer::sum);
    }

    /** Attempts to spend {@code amount} of {@code type}; returns false (no change) if there isn't enough. */
    public boolean remove(BlockType type, int amount) {
        int have = getCount(type);
        if (have < amount) return false;
        int remaining = have - amount;
        if (remaining == 0) {
            counts.remove(type);
        } else {
            counts.put(type, remaining);
        }
        return true;
    }

    /** Empties the inventory entirely - used as the death penalty on respawn. */
    public void clear() {
        counts.clear();
    }

    /** A copy of the current contents (type -> count), safe to iterate while the inventory changes. */
    public Map<BlockType, Integer> snapshot() {
        return new EnumMap<>(counts);
    }
}
