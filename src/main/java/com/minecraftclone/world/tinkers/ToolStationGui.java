package com.minecraftclone.world.tinkers;

import com.minecraftclone.player.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * State machine for the Tool Station GUI.
 *
 * <p>The player drops assembled tool parts into input slots; when a valid
 * combination is present the output slot displays the assembled
 * {@link TinkersItem.Tool}.  Triggering {@link #assemble()} consumes all
 * inputs and returns the tool {@link ItemStack}.
 *
 * <h3>Slot layout</h3>
 * <pre>
 *   Slot 0  — head part  (any {@link ToolPartType#isHead()} shape)
 *   Slot 1  — rod part   (any {@link ToolPartType#isRod()} shape, e.g. TOOL_ROD / TOUGH_ROD)
 *   Slot 2  — optional extra (BINDING, LARGE_PLATE, …)
 *   Slot 3  — optional extra
 *   Slot 4  — optional extra
 * </pre>
 * A tool can be assembled with only a head (slot 0) and a rod (slot 1).
 * Extra parts in slots 2-4 add additional {@link TinkersItem.ToolLayer layers}
 * to the tool, which may contribute bonus durability or future modifier slots.
 *
 * <p>This class is a <em>pure model</em>; all rendering is handled separately.
 */
public final class ToolStationGui {

    /** Total input slots (head + rod + 3 optional extras). */
    public static final int INPUT_SLOTS = 5;

    private final ItemStack[] inputs = new ItemStack[INPUT_SLOTS];

    public ToolStationGui() {
        for (int i = 0; i < INPUT_SLOTS; i++) inputs[i] = ItemStack.EMPTY;
    }

    // -----------------------------------------------------------------------
    // Accessors
    // -----------------------------------------------------------------------

    /**
     * Returns the part in {@code slot}, or {@link ItemStack#EMPTY} if the slot
     * is unoccupied.
     */
    public ItemStack slot(int slot) {
        if (slot < 0 || slot >= INPUT_SLOTS) return ItemStack.EMPTY;
        return inputs[slot];
    }

    // -----------------------------------------------------------------------
    // Mutators
    // -----------------------------------------------------------------------

    /**
     * Places {@code stack} into {@code slot}.  Only {@link ItemStack}s that
     * carry a {@link TinkersItem.Part} are accepted; pass {@link ItemStack#EMPTY}
     * or {@code null} to clear a slot.
     *
     * <p>Non-Tinkers stacks are silently ignored (callers may pre-check
     * {@link ItemStack#isTinkersPart()}).
     */
    public void setSlot(int slot, ItemStack stack) {
        if (slot < 0 || slot >= INPUT_SLOTS) return;
        if (stack == null || stack.isEmpty()) {
            inputs[slot] = ItemStack.EMPTY;
        } else if (stack.isTinkersPart()) {
            inputs[slot] = stack;
        }
    }

    // -----------------------------------------------------------------------
    // Internal helpers
    // -----------------------------------------------------------------------

    /**
     * Returns the head part from slot 0, or {@code null} if the slot is empty
     * or holds a non-head shape (e.g. a rod or binding placed by mistake).
     */
    public TinkersItem.Part headPart() {
        ItemStack s = inputs[0];
        if (!s.isTinkersPart()) return null;
        TinkersItem.Part p = s.tinkersPart();
        return (p != null && p.shape.isHead()) ? p : null;
    }

    /**
     * Returns the rod part from slot 1, or {@code null} if the slot is empty
     * or holds a non-rod shape.
     */
    public TinkersItem.Part rodPart() {
        ItemStack s = inputs[1];
        if (!s.isTinkersPart()) return null;
        TinkersItem.Part p = s.tinkersPart();
        return (p != null && p.shape.isRod()) ? p : null;
    }

    // -----------------------------------------------------------------------
    // Assembly logic
    // -----------------------------------------------------------------------

    /**
     * Returns {@code true} when a valid assembly is possible: slot 0 holds a
     * head part and slot 1 holds a rod part.
     */
    public boolean canAssemble() {
        return headPart() != null && rodPart() != null;
    }

    /**
     * The tool that would result from assembling the current parts, or
     * {@link ItemStack#EMPTY} when {@link #canAssemble()} is {@code false}.
     */
    public ItemStack currentOutput() {
        if (!canAssemble()) return ItemStack.EMPTY;
        return buildTool();
    }

    /**
     * Consumes all input parts and returns the assembled tool.
     *
     * <p>When {@link #canAssemble()} is {@code false}, returns
     * {@link ItemStack#EMPTY} without modifying the slots.
     *
     * @return the assembled {@link TinkersItem.Tool} ItemStack, or EMPTY.
     */
    public ItemStack assemble() {
        if (!canAssemble()) return ItemStack.EMPTY;
        ItemStack result = buildTool();
        clearAll();
        return result;
    }

    /** Empties all input slots (e.g., after assembly or on GUI close). */
    public void clearAll() {
        for (int i = 0; i < INPUT_SLOTS; i++) inputs[i] = ItemStack.EMPTY;
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    private ItemStack buildTool() {
        TinkersItem.Part head = headPart();
        TinkersItem.Part rod  = rodPart();

        List<TinkersItem.ToolLayer> layers = new ArrayList<>();
        layers.add(new TinkersItem.ToolLayer(head.shape, head.material));
        layers.add(new TinkersItem.ToolLayer(rod.shape,  rod.material));

        // Optional extra parts in slots 2-4 (BINDING, LARGE_PLATE, etc.)
        for (int i = 2; i < INPUT_SLOTS; i++) {
            ItemStack s = inputs[i];
            if (s.isTinkersPart()) {
                TinkersItem.Part p = s.tinkersPart();
                if (p != null) {
                    layers.add(new TinkersItem.ToolLayer(p.shape, p.material));
                }
            }
        }

        TinkersItem.Tool tool = new TinkersItem.Tool(head.shape.assembledKind, layers);
        return ItemStack.tinkersTool(tool);
    }
}
