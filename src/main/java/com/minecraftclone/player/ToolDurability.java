package com.minecraftclone.player;

import com.minecraftclone.world.BlockType;
import com.minecraftclone.world.Mining;

import java.util.EnumMap;
import java.util.Map;

/**
 * Tracks remaining durability for tools. The inventory only tracks how many
 * of a tool type you own (a stack count, like any other item) rather than
 * per-instance state, so durability follows the same simplification: one
 * shared "uses left" counter per tool type, as if you always swing whichever
 * one is at the front of the stack. When it hits zero that tool is used up -
 * the caller removes one from the inventory - and the next swing (if any are
 * left) starts a fresh one at full durability.
 */
public final class ToolDurability {

    private final Map<BlockType, Integer> remaining = new EnumMap<>(BlockType.class);

    /**
     * Registers one use of {@code tool} - call this only when it actually
     * finishes breaking a block, not on every swing. Returns true if this
     * use wore the tool out, in which case the caller must remove one from
     * the inventory.
     */
    public boolean use(BlockType tool) {
        Mining.ToolStats stats = Mining.toolStats(tool);
        if (stats == null) return false; // not a tool - nothing to wear out
        int current = remaining.getOrDefault(tool, stats.maxUses()) - 1;
        if (current <= 0) {
            remaining.remove(tool); // next one pulled from the stack starts fresh
            return true;
        }
        remaining.put(tool, current);
        return false;
    }

    /** Remaining uses left on {@code tool}'s currently-active instance (full durability if it hasn't been used yet). */
    public int remaining(BlockType tool) {
        Mining.ToolStats stats = Mining.toolStats(tool);
        if (stats == null) return 0;
        return remaining.getOrDefault(tool, stats.maxUses());
    }

    /** Fraction (0..1) of durability left, for HUD wear indicators. 1 for non-tools/untouched tools. */
    public float fraction(BlockType tool) {
        Mining.ToolStats stats = Mining.toolStats(tool);
        if (stats == null) return 1f;
        return remaining(tool) / (float) stats.maxUses();
    }

    /** Clears all tracked wear, as if every tool were freshly acquired - used on death, alongside the inventory wipe. */
    public void reset() {
        remaining.clear();
    }
}
