package com.minecraftclone.world.tinkers;

import com.minecraftclone.player.ItemStack;
import com.minecraftclone.world.BlockType;

/**
 * State machine for the Part Builder GUI.
 *
 * <p>The player places a material ingot into the material slot, selects one of
 * the available {@link ToolPartType} shapes from the shape list, and triggers
 * {@link #craft()} to receive an unassembled tool-part {@link ItemStack}.  One
 * material item is consumed per craft regardless of part shape.
 *
 * <p>This class is a <em>pure model</em> — it holds slot state and shape
 * selection, validates input, and produces output.  All rendering is handled
 * separately by the engine's GUI renderer.
 *
 * <h3>Accepted materials</h3>
 * Only {@link BlockType}s registered in {@link TinkersRegistry} are accepted
 * as material input; the slot silently rejects anything else.  Because
 * {@code TinkersRegistry} is populated at runtime (and can be extended by
 * future ore phases with one line), no changes here are needed when a new
 * material is added.
 */
public final class PartBuilderGui {

    private ItemStack materialSlot = ItemStack.EMPTY;
    private ToolPartType selectedShape = null;

    // -----------------------------------------------------------------------
    // Accessors
    // -----------------------------------------------------------------------

    /** The material currently in the input slot (may be {@link ItemStack#EMPTY}). */
    public ItemStack materialSlot() { return materialSlot; }

    /** The shape the player has selected; {@code null} if none chosen yet. */
    public ToolPartType selectedShape() { return selectedShape; }

    /** The {@link BlockType} of the material in the slot, or {@code null} if empty. */
    public BlockType materialType() {
        return materialSlot.isEmpty() ? null : materialSlot.type();
    }

    /** How many material items are in the slot (0 if empty). */
    public int materialCount() {
        return materialSlot.count();
    }

    // -----------------------------------------------------------------------
    // Mutators
    // -----------------------------------------------------------------------

    /**
     * Puts {@code stack} into the material slot.  Only stacks whose
     * {@link BlockType} is registered in {@link TinkersRegistry} are accepted;
     * pass {@link ItemStack#EMPTY} or {@code null} to clear the slot.
     * Unregistered materials are silently ignored.
     */
    public void setMaterial(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            materialSlot = ItemStack.EMPTY;
        } else if (TinkersRegistry.isMaterial(stack.type())) {
            materialSlot = stack;
        }
        // Silently reject unregistered materials; callers may pre-check isMaterial().
    }

    /**
     * Sets the selected shape directly (used for deserialization / programmatic
     * control).  Pass {@code null} to clear the selection.
     */
    public void setSelectedShape(ToolPartType shape) {
        this.selectedShape = shape;
    }

    /**
     * Toggles the selected shape: selects {@code shape} if a different one (or
     * none) is selected; deselects it if it is already the current selection.
     * This mirrors typical GUI button behaviour.
     */
    public void toggleShape(ToolPartType shape) {
        this.selectedShape = (shape == selectedShape) ? null : shape;
    }

    // -----------------------------------------------------------------------
    // Crafting logic
    // -----------------------------------------------------------------------

    /**
     * Returns {@code true} when a craft is possible: the material slot holds a
     * registered Tinkers material and a shape has been selected.
     */
    public boolean canCraft() {
        return selectedShape != null
                && !materialSlot.isEmpty()
                && TinkersRegistry.isMaterial(materialSlot.type());
    }

    /**
     * The part that would result from crafting with the current state, or
     * {@link ItemStack#EMPTY} when {@link #canCraft()} is {@code false}.
     */
    public ItemStack currentOutput() {
        if (!canCraft()) return ItemStack.EMPTY;
        return ItemStack.tinkersPart(new TinkersItem.Part(selectedShape, materialSlot.type()));
    }

    /**
     * Consumes one material from the input slot and returns the crafted part.
     *
     * <p>When {@link #canCraft()} is {@code false}, returns
     * {@link ItemStack#EMPTY} without modifying the slot.
     *
     * @return the crafted part ItemStack (count = 1), or EMPTY if not craftable.
     */
    public ItemStack craft() {
        if (!canCraft()) return ItemStack.EMPTY;
        ItemStack result = currentOutput();
        materialSlot = materialSlot.withCount(materialSlot.count() - 1);
        return result;
    }

    /** Clears the material slot and shape selection (e.g., on GUI close). */
    public void reset() {
        materialSlot  = ItemStack.EMPTY;
        selectedShape = null;
    }
}
