package com.minecraftclone.player;

import com.minecraftclone.world.BlockType;

/**
 * Drives the mouse interaction on the inventory screen, Minecraft-style. It
 * owns the <b>cursor stack</b> (the item currently "held" by the mouse) and
 * applies click/drag/shift-click actions against the player's {@link Inventory}
 * and {@link CraftingGrid}.
 * <p>
 * Slot numbering: {@code 0..Inventory.SIZE-1} are the inventory slots (0-8 the
 * hotbar), {@code Inventory.SIZE..Inventory.SIZE+CraftingGrid.SIZE-1} are the
 * 3x3 crafting grid, and {@link #OUTPUT_SLOT} is the crafting result. The
 * caller (Main) resolves which slot the mouse is over and forwards the events;
 * this class is pure logic so the whole interaction is testable without GL.
 */
public class InventoryController {

    /** Slot id of the crafting result (one past the grid). */
    public static final int OUTPUT_SLOT = Inventory.SIZE + CraftingGrid.SIZE;

    private final Inventory inventory;
    private final CraftingGrid grid;

    private BlockType cursorType;
    private int cursorCount;

    private boolean dragging;
    private final boolean[] dragVisited = new boolean[OUTPUT_SLOT + 1];

    public InventoryController(Inventory inventory, CraftingGrid grid) {
        this.inventory = inventory;
        this.grid = grid;
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
        if (slotId >= Inventory.SIZE) return grid.get(slotId - Inventory.SIZE);
        return inventory.typeOf(slotId);
    }

    /** A slot's count (grid cells hold 0 or 1). */
    private int slotCount(int slotId) {
        if (slotId >= Inventory.SIZE) return grid.get(slotId - Inventory.SIZE) == null ? 0 : 1;
        return inventory.countOf(slotId);
    }

    /** Writes a whole stack to a slot; grid cells ignore {@code count} and just record the type. */
    private void setSlot(int slotId, BlockType type, int count) {
        if (slotId >= Inventory.SIZE) {
            grid.set(slotId - Inventory.SIZE, type);
        } else {
            inventory.setSlot(slotId, type, count);
        }
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
        if (slotId < 0 || slotId > OUTPUT_SLOT) return;
        if (shift) {
            quickMove(slotId);
            return;
        }
        if (slotId == OUTPUT_SLOT) {
            craft();
            return;
        }

        BlockType st = slotType(slotId);
        int sc = slotCount(slotId);
        boolean isGrid = slotId >= Inventory.SIZE;

        if (!hasCursorItem()) {
            if (st == null) return;                       // empty click on empty slot
            int take = right ? (sc + 1) / 2 : sc;         // right-click lifts half the stack
            if (isGrid) take = 1;
            cursorType = st;
            cursorCount = take;
            if (take < sc) {
                inventory.setSlot(slotId, st, sc - take);
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
                inventory.setSlot(slotId, st, sc + add);
                cursorCount -= add;
                if (cursorCount <= 0) clearCursor();
            }
        } else {
            // Different type: swap the two stacks.
            setSlot(slotId, cursorType, isGrid ? 1 : cursorCount);
            cursorType = st;
            cursorCount = isGrid ? 1 : sc;
        }
    }

    /**
     * Begins a left-button drag: performs a normal left-click on the pressed
     * slot (picking up its stack), then subsequent {@link #continueDrag} calls
     * spread one item per newly-entered slot.
     */
    public void beginDrag(int slotId) {
        if (slotId < 0 || slotId > OUTPUT_SLOT) return;
        dragging = true;
        for (int i = 0; i < dragVisited.length; i++) dragVisited[i] = false;
        if (slotId != OUTPUT_SLOT) {
            click(slotId, false, false);
            dragVisited[slotId] = true;
        }
    }

    /** Spreads one cursor item into {@code slotId} if the drag hasn't already touched it. */
    public void continueDrag(int slotId) {
        if (!dragging || slotId < 0 || slotId == OUTPUT_SLOT) return;
        if (dragVisited[slotId]) return;
        dragVisited[slotId] = true;
        if (!hasCursorItem()) return;

        BlockType st = slotType(slotId);
        if (st == null) {
            setSlot(slotId, cursorType, 1);
            cursorCount--;
        } else if (st == cursorType && slotId < Inventory.SIZE) {
            int max = Inventory.maxStack(st);
            if (inventory.countOf(slotId) < max) {
                inventory.setSlot(slotId, st, inventory.countOf(slotId) + 1);
                cursorCount--;
            }
        }
        // Different type: skip the slot (like Minecraft, which doesn't overwrite on drag).
        if (cursorCount <= 0) clearCursor();
    }

    public void endDrag() {
        dragging = false;
    }

    /** Shift-click: quick-move a stack between hotbar and inventory, or a grid cell back to the inventory. */
    private void quickMove(int slotId) {
        if (slotId == OUTPUT_SLOT) {
            Crafting.Recipe recipe = Crafting.match(grid.snapshot());
            if (recipe != null && inventory.add(recipe.output(), recipe.outputAmount()) == 0) {
                grid.reset();
            }
            return;
        }
        if (slotId >= Inventory.SIZE) {
            BlockType t = grid.get(slotId - Inventory.SIZE);
            if (t != null && inventory.add(t, 1) == 0) grid.set(slotId - Inventory.SIZE, null);
            return;
        }

        BlockType t = inventory.typeOf(slotId);
        if (t == null) return;
        int count = inventory.countOf(slotId);
        int from = slotId < Inventory.HOTBAR_SIZE ? Inventory.HOTBAR_SIZE : 0;
        int to = slotId < Inventory.HOTBAR_SIZE ? Inventory.SIZE : Inventory.HOTBAR_SIZE;

        int remaining = count;
        int max = Inventory.maxStack(t);
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
        if (remaining != count) {
            inventory.setSlot(slotId, remaining == 0 ? null : t, remaining);
        }
    }

    /** Crafts the current grid match into the cursor (if there's room), consuming the ingredients. */
    private void craft() {
        Crafting.Recipe recipe = Crafting.match(grid.snapshot());
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
        grid.reset();
    }

    /** Returns any items still on the cursor to the inventory - called when the inventory screen closes. */
    public void returnCursorToInventory() {
        if (hasCursorItem()) {
            inventory.add(cursorType, cursorCount);
            clearCursor();
        }
    }

    /** Returns grid contents to the inventory (keeping any that don't fit) - called when the screen closes. */
    public void returnGridToInventory() {
        for (int i = 0; i < CraftingGrid.SIZE; i++) {
            BlockType t = grid.get(i);
            if (t != null && inventory.add(t, 1) == 0) {
                grid.set(i, null);
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
