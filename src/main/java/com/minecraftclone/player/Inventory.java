package com.minecraftclone.player;

import com.minecraftclone.world.BlockType;
import com.minecraftclone.world.Mining;
import java.util.Arrays;

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

    private final ItemStack[] slots = new ItemStack[SIZE];
    /** Equipped armor, one piece per {@code ARMOR_SLOT_*} slot (never stacked). */
    private final BlockType[] armor = new BlockType[ARMOR_SLOT_COUNT];

    /** The type in an equipped armor slot (null if empty). */
    public BlockType armorType(int slot) {
        return armor[slot];
    }

    /** Equips {@code type} into an armor slot, replacing whatever was there. */
    public void setArmor(int slot, BlockType type) {
        if (slot < 0 || slot >= ARMOR_SLOT_COUNT) {
            throw new IllegalArgumentException("Invalid armor slot: " + slot);
        }
        if (type != null) {
            Armor.Slot expectedSlot = Armor.Slot.values()[slot];
            Armor.Slot actualSlot = Armor.slotOf(type);
            if (actualSlot != expectedSlot) {
                throw new IllegalArgumentException(
                    "Armor type " + type + " (slot " + actualSlot + ") does not match slot " + expectedSlot);
            }
        }
        armor[slot] = type;
    }

    /** Total defense points from the equipped armor, capped at the game's limit. */
    public int armorDefense() {
        return Armor.totalDefense(armor[ARMOR_SLOT_HELMET], armor[ARMOR_SLOT_CHESTPLATE],
                armor[ARMOR_SLOT_LEGGINGS], armor[ARMOR_SLOT_BOOTS]);
    }

    /** Total warmth from the equipped armor (how well it insulates against the cold), capped. */
    public float totalArmorWarmth() {
        return Armor.totalWarmth(armor[ARMOR_SLOT_HELMET], armor[ARMOR_SLOT_CHESTPLATE],
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

    /**
     * Returns the full {@link ItemStack} in {@code slot}, or {@link ItemStack#EMPTY}
     * if the slot is unoccupied.  Prefer this over {@link #typeOf} when you need to
     * distinguish Tinkers items from vanilla ones.
     */
    public ItemStack stackOf(int slot) {
        ItemStack s = slots[slot];
        return s != null ? s : ItemStack.EMPTY;
    }

    /** Sets {@code slot} to {@code stack}; pass {@link ItemStack#EMPTY} or {@code null} to clear. */
    public void setStack(int slot, ItemStack stack) {
        slots[slot] = (stack == null || stack.isEmpty()) ? null : stack;
    }

    @Override
    public BlockType typeOf(int slot) {
        ItemStack s = slots[slot];
        return s != null ? s.type() : null;
    }

    @Override
    public int countOf(int slot) {
        ItemStack s = slots[slot];
        return s != null ? s.count() : 0;
    }

    @Override
    public boolean isEmpty(int slot) {
        ItemStack s = slots[slot];
        return s == null || s.isEmpty();
    }

    /** True if there are no items at all (used to seed the creative inventory once). */
    public boolean isEmpty() {
        for (int i = 0; i < SIZE; i++) {
            if (slots[i] != null && !slots[i].isEmpty()) return false;
        }
        return true;
    }

    /**
     * Maximum items a single stack of {@code type} can hold.
     * Tools, armor, and Tinkers items (parts and assembled tools) all have a
     * max stack of 1 because each is a unique item.  Everything else is 64.
     */
    public static int maxStack(BlockType type) {
        if (type == null) return 1;
        if (type.isTinkersToolPart() || type.isTinkersTool()) return 1;
        return (Mining.isTool(type) || Armor.isArmor(type)) ? 1 : STACK_MAX;
    }

    @Override
    public void setSlot(int slot, BlockType type, int count) {
        slots[slot] = (type == null || count <= 0) ? null : ItemStack.of(type, count);
    }

    public void clearSlot(int slot) {
        slots[slot] = null;
    }

    /**
     * Adds a full {@link ItemStack} (including any {@link com.minecraftclone.world.tinkers.TinkersItem}
     * payload) to the first available inventory slot.  Unlike {@link #add(BlockType, int)}, this
     * method preserves the Tinkers payload and never merges with an existing stack (Tinkers items
     * are always count=1 and unique).  Returns the stack unchanged if no space is available.
     *
     * @param stack the stack to place (must not be empty)
     * @return {@link ItemStack#EMPTY} on success, {@code stack} if the inventory is full
     */
    public ItemStack addStack(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return ItemStack.EMPTY;
        // Vanilla items: delegate to the existing merge+fill logic.
        if (!stack.isTinkers()) {
            int leftover = add(stack.type(), stack.count());
            return leftover <= 0 ? ItemStack.EMPTY : stack.withCount(leftover);
        }
        // Tinkers items: find the first empty slot (they never merge with other stacks).
        for (int i = 0; i < SIZE; i++) {
            if (slots[i] == null) {
                slots[i] = stack;
                return ItemStack.EMPTY;
            }
        }
        return stack; // inventory full
    }

    @Override
    public int add(BlockType type, int amount) {
        if (type == null || amount <= 0) return amount;
        if (type.isTinkersToolPart() || type.isTinkersTool()) return amount;
        int max = maxStack(type);
        int remaining = amount;
        // Top up existing stacks of the same type.
        for (int i = 0; i < SIZE && remaining > 0; i++) {
            ItemStack s = slots[i];
            if (s != null && s.type() == type && s.count() < max) {
                int space = max - s.count();
                int take = Math.min(space, remaining);
                slots[i] = s.withCount(s.count() + take);
                remaining -= take;
            }
        }
        // Open new slots for what didn't fit.
        for (int i = 0; i < SIZE && remaining > 0; i++) {
            if (slots[i] == null) {
                int take = Math.min(max, remaining);
                slots[i] = ItemStack.of(type, take);
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
            ItemStack s = slots[i];
            if (s != null && s.type() == type) total += s.count();
        }
        return total;
    }

    /** Removes {@code amount} of {@code type} across whatever slots hold it; false if there isn't enough. */
    public boolean remove(BlockType type, int amount) {
        if (type == null || amount <= 0) return false;
        if (getCount(type) < amount) return false;
        int remaining = amount;
        for (int i = 0; i < SIZE && remaining > 0; i++) {
            ItemStack s = slots[i];
            if (s != null && s.type() == type) {
                int take = Math.min(s.count(), remaining);
                slots[i] = s.withCount(s.count() - take); // withCount returns EMPTY if 0
                if (slots[i].isEmpty()) slots[i] = null;
                remaining -= take;
            }
        }
        return true;
    }

    /** Empties every slot - the death penalty, applied on respawn. */
    public void clear() {
        Arrays.fill(slots, null);
    }

    /** True if every slot is occupied (no room for a new stack). */
    public boolean isFull() {
        for (int i = 0; i < SIZE; i++) {
            if (slots[i] == null) return false;
        }
        return true;
    }
}
