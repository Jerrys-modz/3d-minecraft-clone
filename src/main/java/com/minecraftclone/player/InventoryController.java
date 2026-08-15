package com.minecraftclone.player;

import com.minecraftclone.engine.gui.ContainerGui;
import com.minecraftclone.world.BlockType;

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
 */
public class InventoryController {

    /** Slot id of the crafting result (one past the grid). */
    public static final int OUTPUT_SLOT = ContainerGui.OUTPUT_SLOT;

    private final Inventory inventory;
    private ContainerGui gui;

    private BlockType cursorType;
    private int cursorCount;

    // Click/drag state: a mouse press starts a session that resolves on release -
    // a single touched slot is a plain click, several touched slots is a drag that
    // spreads the cursor stack one item per slot.
    private boolean dragging;
    private int dragStart = -1;
    private boolean dragRight;
    private int dragDistinct;
    private final boolean[] dragVisited = new boolean[ContainerGui.OUTPUT_SLOT + 1];

    /** Wraps the plain inventory screen (player inventory + crafting grid). */
    public InventoryController(Inventory inventory, CraftingGrid grid) {
        this(new ContainerGui(ContainerGui.Kind.INVENTORY, inventory, grid, null));
    }

    public InventoryController(ContainerGui gui) {
        this.inventory = gui.inventory();
        this.gui = gui;
    }

    /** Rebinds the controller to a different open container (inventory/furnace/crafting table). */
    public void setGui(ContainerGui gui) {
        this.gui = gui;
    }

    public ContainerGui gui() {
        return gui;
    }

    public BlockType cursorType() {
        return cursorType;
    }

    public int cursorCount() {
        return cursorCount;
    }

    public boolean hasCursorItem() {
        return cursorType != null && cursorCount > 0;
    }

    public boolean isDragging() {
        return dragging;
    }

    /** A slot's type (null if empty); the output slot is handled separately and never reported here. */
    private BlockType slotType(int slotId) {
        return gui.typeOf(slotId);
    }

    /** A slot's count (grid cells hold 0 or 1). */
    private int slotCount(int slotId) {
        return gui.countOf(slotId);
    }

    /** Writes a whole stack to a slot; grid cells ignore {@code count} and just record the type. */
    private void setSlot(int slotId, BlockType type, int count) {
        gui.setSlot(slotId, type, count);
    }

    private void clearCursor() {
        cursorType = null;
        cursorCount = 0;
    }

    /**
     * Handles one mouse click on {@code slotId}. {@code right} selects the
     * single-item (right-click) behaviour; {@code shift} does a quick transfer
     * instead (shift-click). A left click on the output slot crafts into the
     * cursor.
     */
    public void click(int slotId, boolean right, boolean shift) {
        if (slotId < 0 || slotId >= gui.slotCount()) return;
        if (shift) {
            quickMove(slotId);
            return;
        }
        if (gui.isOutputSlot(slotId)) {
            craft();
            return;
        }

        BlockType st = slotType(slotId);
        int sc = slotCount(slotId);
        boolean isGrid = gui.isGridSlot(slotId);

        if (!hasCursorItem()) {
            if (st == null) return;                       // empty click on empty slot
            int take = right ? (sc + 1) / 2 : sc;         // right-click lifts half the stack
            if (isGrid) take = 1;
            cursorType = st;
            cursorCount = take;
            if (take < sc) {
                setSlot(slotId, st, sc - take);
            } else {
                setSlot(slotId, null, 0);
            }
        } else if (st == null) {
            // Place into the empty slot: one item (right) or the whole stack (left);
            // a grid cell only ever accepts a single item.
            int place = isGrid ? 1 : (right ? 1 : cursorCount);
            setSlot(slotId, cursorType, place);
            cursorCount -= place;
            if (cursorCount <= 0) clearCursor();
        } else if (st == cursorType) {
            if (isGrid) return;                           // grid cells are single-item and already full
            int space = Inventory.maxStack(st) - sc;
            if (space > 0) {
                int add = right ? 1 : Math.min(space, cursorCount);
                setSlot(slotId, st, sc + add);
                cursorCount -= add;
                if (cursorCount <= 0) clearCursor();
            }
        } else {
            // Different type: only a left-click swaps; right-click does nothing
            // (right-click means "one item at a time", never a swap - so the
            // crafting grid can't be scrambled by a mis-click either).
            if (right) return;
            setSlot(slotId, cursorType, isGrid ? 1 : cursorCount);
            cursorType = st;
            cursorCount = isGrid ? 1 : sc;
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
        for (int i = 0; i < dragVisited.length; i++) dragVisited[i] = false;
        dragVisited[slotId] = true;
    }

    /** Mouse move over a slot while the button is held: record it for the drag resolution. */
    public void continueDrag(int slotId) {
        if (!dragging || slotId < 0 || gui.isOutputSlot(slotId)) return;
        if (dragVisited[slotId]) return;
        dragVisited[slotId] = true;
        dragDistinct++;
    }

    /** Mouse release: resolve the session as a click (one slot) or a drag (several). */
    public void endDrag(int releaseSlot) {
        if (!dragging) return;
        if (releaseSlot >= 0 && !gui.isOutputSlot(releaseSlot) && !dragVisited[releaseSlot]) {
            dragVisited[releaseSlot] = true;
            dragDistinct++;
        }
        if (dragDistinct <= 1) {
            click(dragStart, dragRight, false);
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
        if (!hasCursorItem() && !gui.isOutputSlot(dragStart) && slotType(dragStart) != null) {
            click(dragStart, false, false);
        }
        if (!hasCursorItem()) return;
        for (int i = 0; i < dragVisited.length; i++) {
            if (!gui.isOutputSlot(i) && dragVisited[i]) {
                depositOne(i);
            }
        }
    }

    /** Places one cursor item into {@code slotId} (or merges it onto a same-type stack). */
    private void depositOne(int slotId) {
        BlockType st = slotType(slotId);
        if (st == null) {
            setSlot(slotId, cursorType, 1);
            cursorCount--;
        } else if (st == cursorType && (gui.isPlayerSlot(slotId) || gui.isFurnaceSlot(slotId))) {
            int max = Inventory.maxStack(st);
            if (slotCount(slotId) < max) {
                setSlot(slotId, st, slotCount(slotId) + 1);
                cursorCount--;
            }
        }
        // Different type: skip the slot (like Minecraft, which doesn't overwrite on drag).
        if (cursorCount <= 0) clearCursor();
    }

    /**
     * Shift-click: quick-move a stack. Furnace slots and grid cells move to the
     * inventory; a player inventory slot moves into the open container when it
     * belongs there (smeltable ore/fuel into a furnace, anything into an empty
     * crafting-table cell), otherwise it hops between hotbar and main inventory.
     */
    private void quickMove(int slotId) {
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
        if (gui.isFurnaceSlot(slotId)) {
            int fs = slotId - ContainerGui.CONTAINER_START;
            BlockType t = gui.furnace().typeOf(fs);
            int count = gui.furnace().countOf(fs);
            if (t == null) return;
            int leftover = inventory.add(t, count);
            gui.furnace().setSlot(fs, leftover > 0 ? t : null, leftover);
            return;
        }

        BlockType t = inventory.typeOf(slotId);
        if (t == null) return;
        int count = inventory.countOf(slotId);
        int original = count;

        // Prefer the open container: ore/fuel into a furnace, items into empty
        // crafting-table cells.
        if (gui.kind() == ContainerGui.Kind.FURNACE) {
            int target = -1;
            if (Smelting.isSmeltable(t)) {
                target = ContainerGui.CONTAINER_START + com.minecraftclone.world.Furnace.SLOT_INPUT;
            } else if (t == Smelting.FUEL) {
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
        } else if (gui.kind() == ContainerGui.Kind.CRAFTING_TABLE) {
            for (int i = 0; i < CraftingGrid.SIZE && count > 0; i++) {
                if (gui.grid().get(i) == null) {
                    gui.grid().set(i, t);
                    count--;
                }
            }
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

    /** Crafts the current grid match into the cursor (if there's room), consuming the ingredients. */
    private void craft() {
        Crafting.Recipe recipe = gui.currentRecipe();
        if (recipe == null) return;
        BlockType out = recipe.output();
        int amount = recipe.outputAmount();
        if (hasCursorItem()) {
            if (cursorType != out) return;
            if (cursorCount + amount > Inventory.maxStack(out)) return;
            cursorCount += amount;
        } else {
            cursorType = out;
            cursorCount = amount;
        }
        gui.grid().reset();
    }

    /** Returns any items still on the cursor to the inventory - called when the screen closes. */
    public void returnCursorToInventory() {
        if (hasCursorItem()) {
            inventory.add(cursorType, cursorCount);
            clearCursor();
        }
    }

    /** Returns grid contents to the inventory (keeping any that don't fit) - called when the screen closes. */
    public void returnGridToInventory() {
        if (!gui.hasGrid()) return;
        for (int i = 0; i < CraftingGrid.SIZE; i++) {
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
        if (hasCursorItem() && cursorType != type) {
            inventory.add(cursorType, cursorCount);
        }
        cursorType = type;
        cursorCount = Inventory.maxStack(type);
    }

    /** Creative mode: drops whatever the cursor is holding (the "destroy item" slot). */
    public void destroyCursor() {
        clearCursor();
    }
}
