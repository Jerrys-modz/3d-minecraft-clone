package com.minecraftclone.player;

import com.minecraftclone.world.BlockType;
import com.minecraftclone.world.Mining;

/**
 * The player's carried items, Minecraft-style: a fixed grid of {@link #SIZE}
 * slots, each holding a stack of one {@link BlockType} with a bounded count.
 * Slots {@code 0..HOTBAR_SIZE-1} are the hotbar; the remaining
 * {@code HOTBAR_SIZE..SIZE-1} are the main (3x9) inventory shown on the
 * inventory screen.
 * <p>
 * Unlike the old type-to-count map, items now live in a specific slot and
 * stack up to a limit ({@link #STACK_MAX} for blocks/food, 1 for tools, which
 * aren't stackable). {@link #add} tops up existing stacks before opening new
 * slots, and returns anything that didn't fit.
 */
public class Inventory implements StorageContainer {

    public static final int HOTBAR_SIZE = 9;
    public static final int ROWS = 3;
    public static final int MAIN_SIZE = HOTBAR_SIZE * ROWS;      // 27
    public static final int SIZE = HOTBAR_SIZE + MAIN_SIZE;      // 36

    /** Armor slots, Minecraft-style: helmet, chestplate, leggings, boots. */
    public static final int ARMOR_SLOT_HELMET = 0;
    public static final int ARMOR_SLOT_CHESTPLATE = 1;
    public static final int ARMOR_SLOT_LEGGINGS = 2;
    public static final int ARMOR_SLOT_BOOTS = 3;
    public static final int ARMOR_SLOT_COUNT = 4;

    /** Default stack limit: 64, like Minecraft. */
    public static final int STACK_MAX = 64;

    private final BlockType[] types = new BlockType[SIZE];
    private final int[] counts = new int[SIZE];
    /** Equipped armor, one piece per {@code ARMOR_SLOT_*} slot (never stacked). */
    private final BlockType[] armor = new BlockType[ARMOR_SLOT_COUNT];

    /** The type in an equipped armor slot (null if empty). */
    public BlockType armorType(int slot) {
        return armor[slot];
    }

    /** Equips {@code type} into an armor slot, replacing whatever was there. */
    public void setArmor(int slot, BlockType type) {
        armor[slot] = type;
    }

    /** Total defense points from the equipped armor, capped at the game's limit. */
    public int armorDefense() {
        return Armor.totalDefense(armor[ARMOR_SLOT_HELMET], armor[ARMOR_SLOT_CHESTPLATE],
                armor[ARMOR_SLOT_LEGGINGS], armor[ARMOR_SLOT_BOOTS]);
    }

    /** Clears every armor slot - the death penalty, alongside {@link #clear()}. */
    public void clearArmor() {
        for (int i = 0; i < ARMOR_SLOT_COUNT; i++) armor[i] = null;
    }

    @Override
    public int size() {
        return SIZE;
    }

    @Override
    public BlockType typeOf(int slot) {
        return types[slot];
    }

    @Override
    public int countOf(int slot) {
        return counts[slot];
    }

    @Override
    public boolean isEmpty(int slot) {
        return types[slot] == null;
    }

    /** True if there are no items at all (used to seed the creative inventory once). */
    public boolean isEmpty() {
        for (int i = 0; i < SIZE; i++) {
            if (types[i] != null) return false;
        }
        return true;
    }

    /** Maximum items a single stack of {@code type} can hold (1 for tools/armor, else {@link #STACK_MAX}). */
    public static int maxStack(BlockType type) {
        return (Mining.isTool(type) || Armor.isArmor(type)) ? 1 : STACK_MAX;
    }

    @Override
    public void setSlot(int slot, BlockType type, int count) {
        if (type == null || count <= 0) {
            types[slot] = null;
            counts[slot] = 0;
        } else {
            types[slot] = type;
            counts[slot] = count;
        }
    }

    public void clearSlot(int slot) {
        types[slot] = null;
        counts[slot] = 0;
    }

    @Override
    public int add(BlockType type, int amount) {
        if (type == null || amount <= 0) return amount;
        int max = maxStack(type);
        int remaining = amount;
        for (int i = 0; i < SIZE && remaining > 0; i++) {
            if (types[i] == type && counts[i] < max) {
                int space = max - counts[i];
                int take = Math.min(space, remaining);
                counts[i] += take;
                remaining -= take;
            }
        }
        for (int i = 0; i < SIZE && remaining > 0; i++) {
            if (types[i] == null) {
                int take = Math.min(max, remaining);
                types[i] = type;
                counts[i] = take;
                remaining -= take;
            }
        }
        return remaining;
    }

    @Override
    public int getCount(BlockType type) {
        if (type == null) return 0;
        int total = 0;
        for (int i = 0; i < SIZE; i++) {
            if (types[i] == type) total += counts[i];
        }
        return total;
    }

    /** Removes {@code amount} of {@code type} across whatever slots hold it; false if there isn't enough. */
    public boolean remove(BlockType type, int amount) {
        if (type == null || amount <= 0) return false;
        if (getCount(type) < amount) return false;
        int remaining = amount;
        for (int i = 0; i < SIZE && remaining > 0; i++) {
            if (types[i] == type) {
                int take = Math.min(counts[i], remaining);
                counts[i] -= take;
                remaining -= take;
                if (counts[i] == 0) types[i] = null;
            }
        }
        return true;
    }

    /** Empties every slot - the death penalty, applied on respawn. */
    public void clear() {
        for (int i = 0; i < SIZE; i++) {
            types[i] = null;
            counts[i] = 0;
        }
    }

    /** True if every slot is occupied (no room for a new stack). */
    public boolean isFull() {
        for (int i = 0; i < SIZE; i++) {
            if (types[i] == null) return false;
        }
        return true;
    }
}
