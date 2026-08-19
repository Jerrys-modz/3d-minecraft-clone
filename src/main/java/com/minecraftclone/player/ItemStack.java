package com.minecraftclone.player;

import com.minecraftclone.world.BlockType;
import com.minecraftclone.world.tinkers.TinkersItem;

/**
 * An inventory slot's contents: a {@link BlockType} (or Tinkers sentinel) plus
 * a count, optionally carrying a {@link TinkersItem} payload.
 *
 * <h3>Three states</h3>
 * <ul>
 *   <li><b>Empty</b> — {@link #isEmpty()}, {@link #EMPTY}.</li>
 *   <li><b>Vanilla item</b> — {@code type()} is a normal {@link BlockType},
 *       {@code tinkersItem()} is {@code null}.</li>
 *   <li><b>Tinkers item</b> — {@code type()} is {@code BlockType.TINKERS_PART}
 *       or {@code BlockType.TINKERS_TOOL}; {@code tinkersItem()} carries the
 *       per-item data (shape + material for parts, layers + durability for
 *       tools).</li>
 * </ul>
 *
 * <p>Use {@link Inventory#stackOf(int)} instead of {@link Inventory#typeOf(int)}
 * when you need to distinguish Tinkers items from vanilla ones.  All existing
 * code that only checks {@code typeOf()} keeps working — it sees the sentinel.
 */
public final class ItemStack {

    /** Canonical empty singleton — use {@link #isEmpty()} to check. */
    public static final ItemStack EMPTY = new ItemStack(null, null, 0);

    private final BlockType    type;
    private final TinkersItem  tinkersItem;
    private final int          count;

    private ItemStack(BlockType type, TinkersItem tinkersItem, int count) {
        this.type        = type;
        this.tinkersItem = tinkersItem;
        this.count       = count;
    }

    // -----------------------------------------------------------------------
    // Factories
    // -----------------------------------------------------------------------

    /** Vanilla item stack.  Returns {@link #EMPTY} for null type or non-positive count. */
    public static ItemStack of(BlockType type, int count) {
        if (type == null || count <= 0) return EMPTY;
        return new ItemStack(type, null, count);
    }

    /** Tinkers part stack (count always 1 — parts are not stackable). */
    public static ItemStack tinkersPart(TinkersItem.Part part) {
        if (part == null) return EMPTY;
        return new ItemStack(BlockType.TINKERS_PART, part, 1);
    }

    /** Tinkers assembled tool stack (count always 1). */
    public static ItemStack tinkersTool(TinkersItem.Tool tool) {
        if (tool == null) return EMPTY;
        return new ItemStack(BlockType.TINKERS_TOOL, tool, 1);
    }

    // -----------------------------------------------------------------------
    // Accessors
    // -----------------------------------------------------------------------

    public boolean isEmpty()       { return type == null; }

    /**
     * The item's {@link BlockType}.  For Tinkers items this is the sentinel
     * ({@code TINKERS_PART} or {@code TINKERS_TOOL}); for empty slots it is
     * {@code null}.
     */
    public BlockType type()        { return type; }

    /**
     * The Tinkers' Construct payload, or {@code null} for vanilla items and
     * empty slots.
     */
    public TinkersItem tinkersItem()     { return tinkersItem; }
    public TinkersItem.Part tinkersPart(){ return tinkersItem instanceof TinkersItem.Part p ? p : null; }
    public TinkersItem.Tool tinkersTool(){ return tinkersItem instanceof TinkersItem.Tool t ? t : null; }

    public int count()             { return count; }
    public boolean isTinkers()     { return tinkersItem != null; }
    public boolean isTinkersPart() { return tinkersItem instanceof TinkersItem.Part; }
    public boolean isTinkersTool() { return tinkersItem instanceof TinkersItem.Tool; }

    // -----------------------------------------------------------------------
    // Derived views
    // -----------------------------------------------------------------------

    /** Copy with a different count; returns {@link #EMPTY} if {@code newCount <= 0}. */
    public ItemStack withCount(int newCount) {
        if (newCount <= 0) return EMPTY;
        if (newCount == count) return this;
        return new ItemStack(type, tinkersItem, newCount);
    }

    @Override
    public String toString() {
        if (isEmpty()) return "ItemStack[EMPTY]";
        if (isTinkers()) return "ItemStack[" + tinkersItem + " ×" + count + "]";
        return "ItemStack[" + type + " ×" + count + "]";
    }
}
