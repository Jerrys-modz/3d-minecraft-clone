package com.minecraftclone.player;

import com.minecraftclone.world.BlockType;
import com.minecraftclone.world.Mining;

import java.util.EnumMap;
import java.util.Map;

/**
 * Tracks remaining durability for tools and armor. The inventory only tracks
 * how many of a tool/armor type you own (a stack count, like any other item)
 * rather than per-instance state, so durability follows the same
 * simplification: one shared "uses left" counter per type, as if you always
 * swing whichever one is at the front of the stack. When it hits zero the item
 * is used up - the caller removes it (a tool from the inventory, a piece of
 * armor from its slot) - and the next one (if any are left) starts fresh at
 * full durability.
 */
public final class ToolDurability {

    private final Map<BlockType, Integer> remaining = new EnumMap<>(BlockType.class);

    /** How many uses an item starts with: a tool's max uses, an armor piece's, or 0 for anything else. */
    private int maxUsesOf(BlockType type) {
        Mining.ToolStats tool = Mining.toolStats(type);
        if (tool != null) return tool.maxUses();
        return Armor.maxUses(type);
    }

    /**
     * Registers one use of {@code tool} - call this only when it actually
     * finishes breaking a block, not on every swing. Returns true if this
     * use wore the tool out, in which case the caller must remove one from
     * the inventory.
     */
    public boolean use(BlockType tool) {
        return wear(tool, 1);
    }

    /**
     * Registers {@code cost} uses worth of wear on an equipped item - armor
     * pieces wear by {@link Armor#durabilityCost} per hit rather than one use
     * at a time. Returns true if the item wore out, in which case the caller
     * must unequip it.
     */
    public boolean wear(BlockType type, int cost) {
        int max = maxUsesOf(type);
        if (max <= 0 || cost <= 0) return false; // not a wearable item
        int current = remaining.getOrDefault(type, max) - cost;
        if (current <= 0) {
            remaining.remove(type); // next one pulled from the stack starts fresh
            return true;
        }
        remaining.put(type, current);
        return false;
    }

    /** Remaining uses left on {@code type}'s currently-active instance (full durability if it hasn't been used yet). */
    public int remaining(BlockType type) {
        int max = maxUsesOf(type);
        if (max <= 0) return 0;
        return remaining.getOrDefault(type, max);
    }

    /** Fraction (0..1) of durability left, for HUD wear indicators. 1 for non-wearables/untouched items. */
    public float fraction(BlockType type) {
        int max = maxUsesOf(type);
        if (max <= 0) return 1f;
        return remaining(type) / (float) max;
    }

    /** Clears all tracked wear, as if every item were freshly acquired - used on death, alongside the inventory wipe. */
    public void reset() {
        remaining.clear();
    }
}
