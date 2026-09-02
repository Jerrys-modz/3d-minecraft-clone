package com.minecraftclone.player;

import com.minecraftclone.engine.gui.ContainerGui;
import com.minecraftclone.world.BlockType;
import com.minecraftclone.world.tinkers.PartBuilderGui;
import com.minecraftclone.world.tinkers.ToolPartType;
import com.minecraftclone.world.tinkers.TinkersRegistry;
import com.minecraftclone.world.tinkers.ToolStationGui;
import com.minecraftclone.player.AnvilGui;
import com.minecraftclone.player.ToolDurability;

/**
 * Drives the mouse interaction on any container screen, Minecraft-style. It
 * owns the <b>cursor stack</b> (the item currently "held" by the mouse) and
 * applies click/drag/shift-click actions against the active {@link ContainerGui}
 * - the player's {@link Inventory} plus, depending on which container is open,
 * the crafting grid and/or a placed furnace.
 * <p>
 * Slot numbering is defined by {@link ContainerGui}: {@code 0..35} are the
 * inventory slots (0-8 the hotbar), {@code 36..44} the 3x3 crafting grid,
 * {@code 45} the crafting result, and {@code 46..48} a furnace's slots. The
 * caller (Main) resolves which slot the mouse is over and forwards the events;
 * this class is pure logic so the whole interaction is testable without GL.
 * <p>
 * The cursor is stored as a full {@link ItemStack} so Tinkers' Construct parts
 * and assembled tools (which carry a per-item payload beyond their sentinel
 * {@link BlockType}) are never truncated when the player drags or clicks them.
 */
public class InventoryController {

    /** Slot id of the crafting result (one past the grid). */
    public static final int OUTPUT_SLOT = ContainerGui.OUTPUT_SLOT;

    private final Inventory inventory;
    private ContainerGui gui;

    /** The item stack currently "held" by the mouse cursor.  Never {@code null}. */
    private ItemStack cursor = ItemStack.EMPTY;

    /**
     * Optional durability tracker injected by Main so that anvil repair resets
     * a tool's wear counter.  May be {@code null}; the repair still succeeds
     * (the tool returns to the cursor), but durability is not reset until a
     * tracker is attached.
     */
    private ToolDurability toolDurability;

    // Click/drag state: a mouse press starts a session that resolves on release -
    // a single touched slot is a plain click, several touched slots is a drag that
    // spreads the cursor stack one item per slot.
    private boolean dragging;
    private int dragStart = -1;
    private boolean dragRight;
    private int dragDistinct;
    private boolean[] dragVisited = new boolean[ContainerGui.ARMOR_START + Inventory.ARMOR_SLOT_COUNT];

    /** Wraps the plain inventory screen (player inventory + crafting grid). */
    public InventoryController(Inventory inventory, CraftingGrid grid) {
        this(new ContainerGui(ContainerGui.Kind.INVENTORY, inventory, grid, null));
    }

    public InventoryController(ContainerGui gui) {
        this.inventory = gui.inventory();
        this.gui = gui;
    }

    /**
     * Injects the player's durability tracker so that anvil repair resets the
     * repaired tool's wear counter.  Call this once after construction; passing
     * {@code null} disables the durability-reset (useful in unit tests that
     * don't need full durability wiring).
     */
    public void setToolDurability(ToolDurability td) {
        this.toolDurability = td;
    }

    /** Rebinds the controller to a different open container (inventory/furnace/crafting table). */
    public void setGui(ContainerGui gui) {
        this.gui = gui;
        // Resize dragVisited to accommodate all slots in the new GUI
        if (dragVisited.length < gui.slotCount()) {
            dragVisited = new boolean[gui.slotCount()];
        }
    }

    public ContainerGui gui() {
        return gui;
    }

    /** The full cursor {@link ItemStack}, including any Tinkers' payload. */
    public ItemStack cursor() {
        return cursor;
    }

    /** Convenience: the {@link BlockType} of the cursor item (null if cursor is empty). */
    public BlockType cursorType() {
        return cursor.type();
    }

    /** Convenience: the count of the cursor item (0 if cursor is empty). */
    public int cursorCount() {
        return cursor.count();
    }

    public boolean hasCursorItem() {
        return !cursor.isEmpty();
    }

    public boolean isDragging() {
        return dragging;
    }

    /** The full {@link ItemStack} in a slot; output slot is separate and not reported here. */
    private ItemStack slotStack(int slotId) {
        return gui.stackOf(slotId);
    }

    /** A slot's type (null if empty); kept for callers that only need the type. */
    private BlockType slotType(int slotId) {
        return gui.typeOf(slotId);
    }

    /** A slot's count (grid cells hold 0 or 1). */
    private int slotCount(int slotId) {
        return gui.countOf(slotId);
    }

    /** Writes a full stack to a slot, preserving Tinkers payloads for player slots. */
    private void setStack(int slotId, ItemStack stack) {
        gui.setStack(slotId, stack);
    }

    /** Writes a whole stack to a slot; grid cells ignore {@code count} and just record the type. */
    private void setSlot(int slotId, BlockType type, int count) {
        gui.setSlot(slotId, type, count);
    }

    private void clearCursor() {
        cursor = ItemStack.EMPTY;
    }

    /**
     * True when the two stacks can be merged (same type, both vanilla, not
     * Tinkers items).  Tinkers parts and tools are always count=1 and unique,
     * so they are never mergeable — a click on a slot holding a different
     * Tinkers item always resolves as a swap.
     */
    private static boolean isSameType(ItemStack a, ItemStack b) {
        if (a.isEmpty() || b.isEmpty()) return false;
        if (a.isTinkers() || b.isTinkers()) return false; // Tinkers items are unique, never merge
        return a.type() == b.type();
    }

    /**
     * Handles one mouse click on {@code slotId}. {@code right} selects the
     * single-item (right-click) behaviour; {@code shift} does a quick transfer
     * instead (shift-click). A left click on the output slot crafts into the
     * cursor.
     */
    public void click(int slotId, boolean right, boolean shift) {
        if (slotId < 0 || slotId >= gui.slotCount()) return;

        // Tinkers GUI: shape-button click - select/deselect the shape.
        if (gui.isPbShapeSlot(slotId)) {
            int shapeIdx = slotId - ContainerGui.PB_SHAPE_SLOT_0;
            gui.partBuilderGui().toggleShape(ToolPartType.values()[shapeIdx]);
            return;
        }
        // Tinkers GUI: material slot only accepts registered materials.
        if (gui.isPbMaterialSlot(slotId)) {
            if (shift) { quickMove(slotId); return; }
            clickPartBuilderMaterial(right);
            return;
        }
        // Tinkers GUI: output clicks trigger craft/assemble rather than pick-up.
        if (gui.isPbOutputSlot(slotId)) {
            if (shift) craftPartBuilderToInventory(); else craftPartBuilder();
            return;
        }
        if (gui.isTsOutputSlot(slotId)) {
            if (shift) assembleToolStationToInventory(); else assembleToolStation();
            return;
        }
        // Anvil: clicking the output slot triggers a repair (moves repaired tool to cursor).
        if (gui.isAnvilOutputSlot(slotId)) {
            if (shift) repairAnvilToInventory(); else repairAnvil();
            return;
        }
        // Tinkers GUI: Tool Station input slots only accept Tinkers parts.
        if (gui.isTsInputSlot(slotId)) {
            if (shift) { quickMove(slotId); return; }
            clickToolStationSlot(slotId, right);
            return;
        }

        if (shift) {
            quickMove(slotId);
            return;
        }
        if (gui.isOutputSlot(slotId)) {
            craft();
            return;
        }
        if (gui.isArmorSlot(slotId)) {
            clickArmorSlot(slotId, right);
            return;
        }

        ItemStack slot = slotStack(slotId);
        BlockType st = slot.type();
        int sc = slot.count();
        boolean isGrid = gui.isGridSlot(slotId);

        // Clicking an armor piece held in the main inventory with an empty
        // cursor auto-equips it into its matching armor slot (Minecraft
        // behaviour: the piece jumps straight from the bag to the slot).
        if (!hasCursorItem() && st != null && Armor.isArmor(st) && !isGrid) {
            Armor.Slot armorSlot = Armor.slotOf(st);
            if (armorSlot != null && gui.isArmorSlot(armorSlotIdFor(armorSlot))) {
                int armorId = armorSlotIdFor(armorSlot);
                if (gui.typeOf(armorId) == null) {
                    gui.setSlot(slotId, null, 0);
                    gui.setSlot(armorId, st, 1);
                    return;
                }
            }
        }

        if (!hasCursorItem()) {
            if (slot.isEmpty()) return;                    // empty click on empty slot
            int take = right ? (sc + 1) / 2 : sc;          // right-click lifts half the stack
            if (isGrid) take = 1;
            cursor = slot.withCount(take);                  // preserves TinkersItem payload
            if (take < sc) {
                setStack(slotId, slot.withCount(sc - take));
            } else {
                setSlot(slotId, null, 0);
            }
        } else if (slot.isEmpty()) {
            // Place into the empty slot: one item (right) or the whole stack (left);
            // a grid cell only ever accepts a single item.
            if (cursor.isTinkers() && !gui.isPlayerSlot(slotId)) return;
            int place = isGrid ? 1 : (right ? 1 : cursor.count());
            setStack(slotId, cursor.withCount(place));
            cursor = cursor.withCount(cursor.count() - place);
            if (cursor.isEmpty()) clearCursor();
        } else if (isSameType(slot, cursor)) {
            if (isGrid) return;                            // grid cells are single-item and already full
            int space = Inventory.maxStack(st) - sc;
            if (space > 0) {
                int add = right ? 1 : Math.min(space, cursor.count());
                setSlot(slotId, st, sc + add);
                cursor = cursor.withCount(cursor.count() - add);
                if (cursor.isEmpty()) clearCursor();
            }
        } else {
            // Different type: only a left-click swaps; right-click does nothing
            // (right-click means "one item at a time", never a swap - so the
            // crafting grid can't be scrambled by a mis-click either).
            if (right) return;
            if (cursor.isTinkers() && !gui.isPlayerSlot(slotId)) return;
            setStack(slotId, cursor.withCount(isGrid ? 1 : cursor.count()));
            cursor = isGrid ? slot.withCount(1) : slot;    // preserves TinkersItem payload
        }
    }

    /** The armor-slot id for a given {@link Armor.Slot} on the player's inventory screen. */
    private int armorSlotIdFor(Armor.Slot slot) {
        return ContainerGui.ARMOR_START + slot.ordinal();
    }

    /**
     * Clicking an armor slot only ever exchanges <em>matching</em> armor: an
     * empty cursor picks up the piece there, a cursor holding armor for this
     * slot places it (swapping if the slot is occupied), and a cursor holding
     * anything else does nothing - you can't jam a helmet into the boots slot.
     */
    private void clickArmorSlot(int slotId, boolean right) {
        Armor.Slot wanted = Armor.Slot.values()[slotId - ContainerGui.ARMOR_START];
        BlockType in = slotType(slotId);
        if (!hasCursorItem()) {
            if (in != null) {
                cursor = ItemStack.of(in, 1);
                setSlot(slotId, null, 0);
            }
            return;
        }
        if (Armor.slotOf(cursor.type()) != wanted) return;   // wrong piece for this slot
        if (in != null) {
            if (in == cursor.type()) return;              // already wearing it
            setSlot(slotId, cursor.type(), 1);            // swap: old piece goes to cursor
            cursor = ItemStack.of(in, 1);
        } else {
            setSlot(slotId, cursor.type(), 1);
            clearCursor();
        }
    }

    /**
     * Mouse press: starts a click/drag session without touching the inventory yet.
     * On release, a single touched slot resolves as a normal {@link #click}, while
     * several touched slots resolve as a drag that spreads the cursor stack one
     * item per slot.
     */
    public void beginDrag(int slotId, boolean right) {
        if (slotId < 0 || slotId >= gui.slotCount()) return;
        dragging = true;
        dragStart = slotId;
        dragRight = right;
        dragDistinct = 1;
        // Ensure dragVisited is large enough for all GUI slots
        if (dragVisited.length < gui.slotCount()) {
            dragVisited = new boolean[gui.slotCount()];
        } else {
            for (int i = 0; i < dragVisited.length; i++) dragVisited[i] = false;
        }
        dragVisited[slotId] = true;
    }

    /** Mouse move over a slot while the button is held: record it for the drag resolution. */
    public void continueDrag(int slotId) {
        if (!dragging || slotId < 0 || slotId >= gui.slotCount() || gui.isOutputSlot(slotId)) return;
        if (dragVisited[slotId]) return;
        dragVisited[slotId] = true;
        dragDistinct++;
    }

    /** Mouse release: resolve the session as a click (one slot) or a drag (several). */
    public void endDrag(int releaseSlot) {
        if (!dragging) return;
        // Output slots craft on press-release. The result sits right next to the
        // armor column / grid, so a tiny mouse slip would otherwise turn the
        // click into a multi-slot drag and silently skip craft().
        if (gui.isOutputSlot(dragStart) || gui.isPbOutputSlot(dragStart) || gui.isTsOutputSlot(dragStart)) {
            click(dragStart, dragRight, false);
            dragging = false;
            dragStart = -1;
            dragDistinct = 0;
            dragRight = false;
            return;
        }
        if (releaseSlot >= 0 && releaseSlot < gui.slotCount() && !gui.isOutputSlot(releaseSlot) && !dragVisited[releaseSlot]) {
            dragVisited[releaseSlot] = true;
            dragDistinct++;
        }
        // A single, non-stackable item (a tool, armor piece, or Tinkers item) can't
        // be "spread" one-per-slot across several touched slots the way a stack can -
        // there's only one of it.  Minecraft just resolves that drag as a normal click
        // on wherever the mouse released.  The item can already be on the cursor, or
        // still sitting in dragStart (a press-and-drag starting on a filled slot).
        ItemStack dragStack = hasCursorItem() ? cursor
                : (gui.isOutputSlot(dragStart) ? ItemStack.EMPTY : slotStack(dragStart));
        BlockType dragType = dragStack.type();
        if (dragDistinct <= 1) {
            click(dragStart, dragRight, false);
        } else if (dragType != null && Inventory.maxStack(dragType) <= 1) {
            if (!hasCursorItem()) {
                // Lift it directly rather than via click(dragStart, ...): clicking a
                // bagged armor piece with an empty cursor auto-equips it into its
                // matching armor slot (see click()'s auto-equip shortcut), which would
                // ignore wherever this drag actually released.
                cursor = dragStack;                          // preserves TinkersItem payload
                setSlot(dragStart, null, 0);
            }
            click(releaseSlot, dragRight, false);
        } else {
            resolveDrag();
        }
        dragging = false;
        dragStart = -1;
        dragDistinct = 0;
        dragRight = false;
    }

    /**
     * Distributes a drag: if the cursor is empty it first lifts the stack from the
     * pressed slot, then spreads one item into each touched slot (skipping slots
     * holding a different item, like Minecraft's drag).
     */
    private void resolveDrag() {
        boolean liftedFromDragStart = false;
        if (!hasCursorItem() && !gui.isOutputSlot(dragStart) && slotType(dragStart) != null) {
            click(dragStart, false, false);
            liftedFromDragStart = true;
        }
        if (!hasCursorItem()) return;

        // Check if we're dragging to crafting grid cells - if so, don't place items back in source
        boolean dragToCraftingGrid = false;
        for (int i = 0; i < dragVisited.length; i++) {
            if (dragVisited[i] && gui.isGridSlot(i)) {
                dragToCraftingGrid = true;
                break;
            }
        }

        for (int i = 0; i < dragVisited.length; i++) {
            // Skip the output slot and any unvisited slots
            if (!gui.isOutputSlot(i) && dragVisited[i]) {
                // When dragging to crafting grid, skip dragStart only if it was the pickup source
                if (dragToCraftingGrid && i == dragStart && liftedFromDragStart) {
                    continue;
                }
                depositOne(i);
            }
        }
    }

    /** Places one cursor item into {@code slotId} (or merges it onto a same-type stack). */
    private void depositOne(int slotId) {
        if (cursor.isEmpty()) return;
        if (cursor.isTinkers() && !gui.isPlayerSlot(slotId)) return;
        BlockType curType = cursor.type();
        int curCount = cursor.count();
        BlockType st = slotType(slotId);
        if (st == null) {
            // Armor slots: only accept matching armor type into an empty slot.
            if (gui.isArmorSlot(slotId)) {
                Armor.Slot wantedSlot = Armor.Slot.values()[slotId - ContainerGui.ARMOR_START];
                if (Armor.slotOf(curType) != wantedSlot) {
                    return; // Wrong armor type for this slot, skip.
                }
            }
            setStack(slotId, cursor.withCount(1));          // preserves TinkersItem payload
            cursor = cursor.withCount(curCount - 1);
        } else if (st == curType && !cursor.isTinkers() && (gui.isPlayerSlot(slotId) || gui.isContainerSlot(slotId))) {
            int max = Inventory.maxStack(st);
            if (slotCount(slotId) < max) {
                setSlot(slotId, st, slotCount(slotId) + 1);
                cursor = cursor.withCount(curCount - 1);
            }
        }
        // Different type: skip the slot (like Minecraft, which doesn't overwrite on drag).
        if (cursor.isEmpty()) clearCursor();
    }

    /**
     * Shift-click: quick-move a stack. Container slots and grid cells move to
     * the inventory; a player inventory slot moves into the open container when
     * it belongs there (smeltable ore/fuel into a furnace, anything into a
     * chest or an empty crafting-table cell), otherwise it hops between hotbar
     * and main inventory.
     */
    private void quickMove(int slotId) {
        // Tinkers: Part Builder output shift-click.
        if (gui.isPbOutputSlot(slotId)) {
            craftPartBuilderToInventory();
            return;
        }
        // Tinkers: Part Builder material slot shift-click returns to inventory.
        if (gui.isPbMaterialSlot(slotId)) {
            ItemStack mat = gui.partBuilderGui().materialSlot();
            if (!mat.isEmpty()) {
                int leftover = inventory.add(mat.type(), mat.count());
                gui.partBuilderGui().setMaterial(leftover > 0 ? mat.withCount(leftover) : ItemStack.EMPTY);
            }
            return;
        }
        // Tinkers: Tool Station output shift-click.
        if (gui.isTsOutputSlot(slotId)) {
            assembleToolStationToInventory();
            return;
        }
        // Tinkers: Tool Station input slot shift-click returns part to inventory.
        if (gui.isTsInputSlot(slotId)) {
            ItemStack part = gui.toolStationGui().slot(slotId - ContainerGui.TS_SLOT_0);
            if (!part.isEmpty()) {
                ItemStack leftover = inventory.addStack(part);
                if (leftover.isEmpty()) {
                    gui.toolStationGui().setSlot(slotId - ContainerGui.TS_SLOT_0, ItemStack.EMPTY);
                }
            }
            return;
        }
        // Anvil: shift-click on output triggers repair + deposit directly into inventory.
        if (gui.isAnvilOutputSlot(slotId)) {
            repairAnvilToInventory();
            return;
        }
        // Anvil: shift-click on a writable input slot returns its item to inventory.
        if (gui.isAnvilInputSlot(slotId)) {
            BlockType t = gui.typeOf(slotId);
            int cnt = gui.countOf(slotId);
            if (t != null) {
                int leftover = inventory.add(t, cnt);
                gui.setSlot(slotId, leftover > 0 ? t : null, leftover);
            }
            return;
        }

        if (gui.isOutputSlot(slotId)) {
            // Craft repeatedly into the inventory while the grid keeps matching.
            Crafting.Recipe recipe = gui.currentRecipe();
            while (recipe != null) {
                if (inventory.add(recipe.output(), recipe.outputAmount()) != 0) break;
                gui.grid().reset();
                recipe = gui.currentRecipe();
            }
            return;
        }
        if (gui.isGridSlot(slotId)) {
            BlockType t = gui.grid().get(slotId - ContainerGui.GRID_START);
            if (t != null && inventory.add(t, 1) == 0) gui.grid().set(slotId - ContainerGui.GRID_START, null);
            return;
        }
        if (gui.isArmorSlot(slotId)) {
            // Shift-click an armor slot: unequip it back into the inventory.
            BlockType t = slotType(slotId);
            if (t != null && inventory.add(t, 1) == 0) setSlot(slotId, null, 0);
            return;
        }
        if (gui.isContainerSlot(slotId)) {
            int cs = slotId - ContainerGui.CONTAINER_START;
            BlockType t = gui.container().typeOf(cs);
            int count = gui.container().countOf(cs);
            if (t == null) return;
            int leftover = inventory.add(t, count);
            gui.container().setSlot(cs, leftover > 0 ? t : null, leftover);
            return;
        }

        BlockType t = inventory.typeOf(slotId);
        if (t == null) return;
        int count = inventory.countOf(slotId);
        int original = count;

        // Shift-click an armor piece in the bag: auto-equip it into its slot
        // if that slot is free (Minecraft behaviour).
        if (Armor.isArmor(t)) {
            Armor.Slot slot = Armor.slotOf(t);
            int armorId = armorSlotIdFor(slot);
            if (gui.isArmorSlot(armorId) && gui.typeOf(armorId) == null) {
                inventory.setSlot(slotId, null, 0);
                gui.setSlot(armorId, t, 1);
                return;
            }
        }

        // Tinkers Part Builder: shift-click a material from inventory → material slot.
        if (gui.kind() == ContainerGui.Kind.PART_BUILDER
                && gui.partBuilderGui() != null
                && com.minecraftclone.world.tinkers.TinkersRegistry.isMaterial(t)) {
            if (gui.partBuilderGui().materialSlot().isEmpty()) {
                int moved = Math.min(count, Inventory.maxStack(t));
                gui.partBuilderGui().setMaterial(ItemStack.of(t, moved));
                inventory.setSlot(slotId, count - moved == 0 ? null : t, count - moved);
            }
            return;
        }

        // Tinkers Tool Station: shift-click a Tinkers part from inventory into
        // the slot that matches its role (head → 0, rod → 1, extras → 2–4).
        // Dumping everything into the first empty slot used to put a rod in
        // Head and a pick head in Rod, so the station never assembled.
        if (gui.kind() == ContainerGui.Kind.TOOL_STATION && gui.toolStationGui() != null) {
            ItemStack stack = inventory.stackOf(slotId);
            if (stack.isTinkersPart()) {
                ToolStationGui ts = gui.toolStationGui();
                int dest = ts.preferredEmptySlot(stack);
                if (dest >= 0) {
                    ts.setSlot(dest, stack.withCount(1));
                    inventory.setSlot(slotId, null, 0);
                }
            }
            return;
        }

        ItemStack src = inventory.stackOf(slotId);
        if (src.isTinkers()) {
            // Unique Tinkers items cannot go through BlockType-only destinations
            // (chest, furnace, grid). Hop between hotbar and main via addStack.
            inventory.setStack(slotId, ItemStack.EMPTY);
            int from = slotId < Inventory.HOTBAR_SIZE ? Inventory.HOTBAR_SIZE : 0;
            int to = slotId < Inventory.HOTBAR_SIZE ? Inventory.SIZE : Inventory.HOTBAR_SIZE;
            ItemStack leftover = addStackInRange(src, from, to);
            if (!leftover.isEmpty()) inventory.setStack(slotId, leftover);
            return;
        }

        // Prefer the open container: ore/fuel into a furnace, anything into a
        // chest, items into empty crafting-grid cells.
        if (gui.kind() == ContainerGui.Kind.FURNACE) {
            int target = -1;
            if (Smelting.isSmeltable(t)) {
                target = ContainerGui.CONTAINER_START + com.minecraftclone.world.Furnace.SLOT_INPUT;
            } else if (com.minecraftclone.world.Furnace.isFuel(t)) {
                target = ContainerGui.CONTAINER_START + com.minecraftclone.world.Furnace.SLOT_FUEL;
            }
            if (target >= 0) {
                int moved = moveToFurnaceSlot(target, t, count);
                count -= moved;
                if (count == 0) {
                    inventory.setSlot(slotId, null, 0);
                    return;
                }
            }
        } else if (gui.kind() == ContainerGui.Kind.CHEST) {
            // A chest accepts anything; fill its stacks first, then empty slots.
            int moved = count - gui.container().add(t, count);
            count -= moved;
            if (count == 0) {
                inventory.setSlot(slotId, null, 0);
                return;
            }
        } else if (gui.kind() == ContainerGui.Kind.CRAFTING_TABLE ||
                   gui.kind() == ContainerGui.Kind.ADVANCED_CRAFTING_TABLE ||
                   (gui.kind() == ContainerGui.Kind.INVENTORY && slotId >= Inventory.HOTBAR_SIZE)) {
            // For a dedicated crafting table (3x3 or 5x5), or when shift-clicking from main inventory to the crafting grid,
            // place items into empty crafting-grid cells.
            for (int i = 0; i < gui.gridSize() && count > 0; i++) {
                if (gui.grid().get(i) == null) {
                    gui.grid().set(i, t);
                    count--;
                }
            }
            // Update the slot with remaining items and return (don't redistribute to other inventory slots)
            if (count < original) {
                inventory.setSlot(slotId, count == 0 ? null : t, count);
            }
            return;
        }

        // Anything left hops between hotbar and main inventory.
        int from = slotId < Inventory.HOTBAR_SIZE ? Inventory.HOTBAR_SIZE : 0;
        int to = slotId < Inventory.HOTBAR_SIZE ? Inventory.SIZE : Inventory.HOTBAR_SIZE;
        int max = Inventory.maxStack(t);
        int remaining = count;
        for (int i = from; i < to && remaining > 0; i++) {
            if (inventory.typeOf(i) == t && inventory.countOf(i) < max) {
                int add = Math.min(max - inventory.countOf(i), remaining);
                inventory.setSlot(i, t, inventory.countOf(i) + add);
                remaining -= add;
            }
        }
        for (int i = from; i < to && remaining > 0; i++) {
            if (inventory.typeOf(i) == null) {
                int add = Math.min(max, remaining);
                inventory.setSlot(i, t, add);
                remaining -= add;
            }
        }
        if (remaining != original) {
            inventory.setSlot(slotId, remaining == 0 ? null : t, remaining);
        }
    }

    /** Places a unique Tinkers stack into the first empty slot in {@code [from, to)}. */
    private ItemStack addStackInRange(ItemStack stack, int from, int to) {
        for (int i = from; i < to; i++) {
            if (inventory.isEmpty(i)) {
                inventory.setStack(i, stack);
                return ItemStack.EMPTY;
            }
        }
        return stack;
    }

    /** Moves up to {@code count} of {@code t} into a furnace slot, topping up a same-type stack; returns items moved. */
    private int moveToFurnaceSlot(int slotId, BlockType t, int count) {
        com.minecraftclone.world.Furnace f = gui.furnace();
        int fs = slotId - ContainerGui.CONTAINER_START;
        int max = Inventory.maxStack(t);
        int current = f.countOf(fs);
        if (f.typeOf(fs) != null && f.typeOf(fs) != t) return 0;
        int space = max - current;
        int add = Math.min(space, count);
        f.setSlot(fs, t, current + add);
        return add;
    }

    // -----------------------------------------------------------------------
    // Tinkers GUI craft / assemble helpers
    // -----------------------------------------------------------------------

    /**
     * Part Builder material slot: only registered Tinkers materials are accepted.
     * Invalid items stay on the cursor instead of being swallowed by setMaterial().
     */
    private void clickPartBuilderMaterial(boolean right) {
        PartBuilderGui pb = gui.partBuilderGui();
        if (pb == null) return;
        ItemStack slotItem = pb.materialSlot();
        if (!hasCursorItem()) {
            if (slotItem.isEmpty()) return;
            int take = right ? (slotItem.count() + 1) / 2 : slotItem.count();
            cursor = slotItem.withCount(take);
            int leftover = slotItem.count() - take;
            pb.setMaterial(leftover <= 0 ? ItemStack.EMPTY : slotItem.withCount(leftover));
            return;
        }
        if (cursor.isTinkers() || !TinkersRegistry.isMaterial(cursor.type())) {
            return;
        }
        if (slotItem.isEmpty()) {
            int place = right ? 1 : cursor.count();
            pb.setMaterial(cursor.withCount(place));
            cursor = cursor.withCount(cursor.count() - place);
            return;
        }
        if (slotItem.type() == cursor.type()) {
            int space = Inventory.maxStack(cursor.type()) - slotItem.count();
            if (space <= 0) return;
            int add = right ? 1 : Math.min(space, cursor.count());
            pb.setMaterial(slotItem.withCount(slotItem.count() + add));
            cursor = cursor.withCount(cursor.count() - add);
            return;
        }
        if (right) return;
        ItemStack prev = slotItem;
        pb.setMaterial(cursor);
        cursor = prev;
    }

    /**
     * Tool Station input slot click: only accepts Tinkers parts (or the vanilla
     * pick-up/place logic when the cursor is empty).
     */
    private void clickToolStationSlot(int slotId, boolean right) {
        ItemStack slotItem = slotStack(slotId);
        if (!hasCursorItem()) {
            // Pick up whatever is in the slot.
            if (!slotItem.isEmpty()) {
                cursor = slotItem;
                setStack(slotId, ItemStack.EMPTY);
            }
        } else {
            // Place cursor into slot — only the matching part role is accepted
            // (head in 0, rod in 1, extras in 2–4). Rejecting here keeps the
            // cursor intact; setSlot would otherwise silently ignore the drop.
            if (!cursor.isTinkersPart()) return;
            int tsSlot = slotId - ContainerGui.TS_SLOT_0;
            ToolStationGui ts = gui.toolStationGui();
            if (ts == null || !ts.accepts(tsSlot, cursor)) return;
            if (slotItem.isEmpty()) {
                setStack(slotId, cursor.withCount(1));
                cursor = cursor.withCount(cursor.count() - 1);
                if (cursor.isEmpty()) clearCursor();
            } else {
                ItemStack prev = slotItem;
                setStack(slotId, cursor.withCount(1));
                cursor = prev;
            }
        }
    }

    /** Crafts one part from the Part Builder into the cursor; the material is consumed. */
    private void craftPartBuilder() {
        PartBuilderGui pb = gui.partBuilderGui();
        if (pb == null || !pb.canCraft()) return;
        // Refuse if the cursor is occupied — otherwise craft() consumes the
        // material and addStack may drop the unique part on the floor of a full bag.
        if (!cursor.isEmpty()) return;
        cursor = pb.craft();
    }

    /** Shift-click on Part Builder output: craft and deposit directly into inventory. */
    private void craftPartBuilderToInventory() {
        PartBuilderGui pb = gui.partBuilderGui();
        if (pb == null || !pb.canCraft()) return;
        if (inventory.isFull()) return;
        ItemStack result = pb.craft();
        if (!result.isEmpty()) inventory.addStack(result);
    }

    /** Assembles the current Tool Station parts into the cursor. */
    private void assembleToolStation() {
        ToolStationGui ts = gui.toolStationGui();
        if (ts == null || !ts.canAssemble()) return;
        if (!cursor.isEmpty()) return;
        cursor = ts.assemble();
    }

    /** Shift-click on Tool Station output: assemble and deposit directly into inventory. */
    private void assembleToolStationToInventory() {
        ToolStationGui ts = gui.toolStationGui();
        if (ts == null || !ts.canAssemble()) return;
        if (inventory.isFull()) return;
        ItemStack result = ts.assemble();
        if (!result.isEmpty()) inventory.addStack(result);
    }

    /**
     * Left-click on the Anvil output: consume 1 repair material, reset the
     * tool's durability via {@link ToolDurability#resetType}, and hand the
     * repaired tool to the cursor (or push it to the inventory if the cursor is
     * already occupied).
     */
    private void repairAnvil() {
        AnvilGui anvil = gui.anvilGui();
        if (anvil == null || !anvil.canRepair()) return;
        // Pre-check: the repaired tool is a single-item stack that needs a slot to
        // land in.  If the cursor is already held and the inventory is full there is
        // nowhere to put the result, so leave everything unchanged rather than
        // consuming the inputs and losing the output.
        if (!cursor.isEmpty() && inventory.isFull()) return;
        BlockType toolType = anvil.consume();
        if (toolType == null) return;
        if (toolDurability != null) toolDurability.resetType(toolType);
        ItemStack repaired = ItemStack.of(toolType, 1);
        if (cursor.isEmpty()) {
            cursor = repaired;
        } else {
            inventory.addStack(repaired);
        }
    }

    /**
     * Shift-click on the Anvil output: repair and deposit the tool directly
     * into the inventory without touching the cursor.
     */
    private void repairAnvilToInventory() {
        AnvilGui anvil = gui.anvilGui();
        if (anvil == null || !anvil.canRepair()) return;
        // Pre-check: avoid consuming inputs when the inventory has no room for the
        // repaired tool, which would cause the output to be silently discarded.
        if (inventory.isFull()) return;
        BlockType toolType = anvil.consume();
        if (toolType == null) return;
        if (toolDurability != null) toolDurability.resetType(toolType);
        inventory.addStack(ItemStack.of(toolType, 1));
    }

    /** Crafts the current grid match into the cursor (if there's room), consuming the ingredients. */
    private void craft() {
        Crafting.Recipe recipe = gui.currentRecipe();
        if (recipe == null) return;
        BlockType out = recipe.output();
        int amount = recipe.outputAmount();
        if (hasCursorItem()) {
            if (cursor.type() != out) return;
            if (cursor.count() + amount > Inventory.maxStack(out)) return;
            cursor = cursor.withCount(cursor.count() + amount);
        } else {
            cursor = ItemStack.of(out, amount);
        }
        gui.grid().reset();
    }

    /** Returns any items still on the cursor to the inventory - called when the screen closes. */
    public void returnCursorToInventory() {
        if (hasCursorItem()) {
            if (cursor.isTinkers()) {
                cursor = inventory.addStack(cursor);
                return;
            } else {
                inventory.add(cursor.type(), cursor.count());
            }
            clearCursor();
        }
    }

    /** Returns grid contents to the inventory (keeping any that don't fit) - called when the screen closes. */
    public void returnGridToInventory() {
        if (!gui.hasGrid()) return;
        for (int i = 0; i < gui.gridSize(); i++) {
            BlockType t = gui.grid().get(i);
            if (t != null && inventory.add(t, 1) == 0) {
                gui.grid().set(i, null);
            }
        }
    }

    /**
     * Creative mode: clicking a catalog item puts a full stack on the cursor
     * (returning any incompatible cursor item to the inventory first); shift-
     * clicking instead moves it straight into the first available slot (the
     * hotbar, since its slots come first).
     */
    public void pickCreativeItem(BlockType type, boolean shift) {
        if (type == null) return;
        if (shift) {
            inventory.add(type, Inventory.maxStack(type));
            return;
        }
        if (hasCursorItem() && (cursor.isTinkers() || cursor.type() != type)) {
            if (cursor.isTinkers()) {
                // Keep the unique item on the cursor when the bag is full.
                if (!inventory.addStack(cursor).isEmpty()) return;
            } else {
                inventory.add(cursor.type(), cursor.count());
            }
        }
        cursor = ItemStack.of(type, Inventory.maxStack(type));
    }

    /** Creative mode: drops whatever the cursor is holding (the "destroy item" slot). */
    public void destroyCursor() {
        clearCursor();
    }
}
