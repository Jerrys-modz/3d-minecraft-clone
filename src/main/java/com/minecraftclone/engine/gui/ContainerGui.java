package com.minecraftclone.engine.gui;

import com.minecraftclone.player.Crafting;
import com.minecraftclone.player.CraftingGrid;
import com.minecraftclone.player.Inventory;
import com.minecraftclone.player.StorageContainer;
import com.minecraftclone.world.BlockType;
import com.minecraftclone.world.Furnace;

/**
 * The model behind every full-screen container GUI: the plain inventory screen,
 * a placed furnace, a placed crafting table, or a placed chest. A gui is a
 * fixed slot space built from the player's inventory plus, depending on {@link
 * Kind}, a 3x3 crafting grid with its output slot and/or a placed block's
 * container.
 * <p>
 * Slot numbering is one contiguous space so the mouse/controller logic never
 * has to branch on where an item lives. Which ranges exist depends on the kind
 * (a furnace/chest has no crafting grid, so its slots take the grid's numbers):
 * <pre>
 *   0 .. 35                player inventory (0-8 hotbar, 9-35 main)
 *   36 .. 44               crafting grid cells   (INVENTORY / CRAFTING_TABLE)
 *   45                     crafting output slot  (INVENTORY / CRAFTING_TABLE)
 *   36 .. 38               furnace slots         (FURNACE: input, fuel, output)
 *   36 .. 62               chest slots           (CHEST: 27 slots)
 * </pre>
 * The crafting output is derived (it holds the current recipe's result) rather
 * than stored, exactly like Minecraft - {@link #currentRecipe} reports what
 * would come out of the grid right now.
 * <p>
 * The placed container is a {@link StorageContainer}, so any future block that
 * implements that interface (a new machine, a barrel, an AE2-style network
 * terminal exposing virtual storage) plugs into the GUI, mouse interaction and
 * quick-move logic with no further code here.
 */
public class ContainerGui {

    /** The flavour of container being shown. */
    public enum Kind {
        INVENTORY("Inventory"),
        FURNACE("Furnace"),
        CRAFTING_TABLE("Crafting Table"),
        CHEST("Chest");

        private final String title;

        Kind(String title) {
            this.title = title;
        }
    }

    /** First slot id of the crafting grid (directly after the player slots). */
    public static final int GRID_START = Inventory.SIZE;
    /** Slot id of the crafting result. */
    public static final int OUTPUT_SLOT = GRID_START + CraftingGrid.SIZE;
    /**
     * First slot id of a placed container's slots (furnace/chest). A container
     * has no crafting grid, so it reuses the grid's slot numbers: {@code 36..}.
     */
    public static final int CONTAINER_START = Inventory.SIZE;
    /** Number of slots a furnace contributes. */
    public static final int FURNACE_SLOT_COUNT = Furnace.SLOT_COUNT;
    /** Number of slots a chest contributes. */
    public static final int CHEST_SLOT_COUNT = com.minecraftclone.world.Chest.SLOT_COUNT;

    private final Kind kind;
    private final Inventory inventory;
    private final CraftingGrid grid;
    private final StorageContainer container;

    /**
     * @param kind       which container this gui shows
     * @param inventory  the player's inventory (shared, always present)
     * @param grid       the shared crafting grid (used when the kind has one)
     * @param container  the placed block's container (FURNACE/CHEST kinds only;
     *                   may be null otherwise)
     */
    public ContainerGui(Kind kind, Inventory inventory, CraftingGrid grid, StorageContainer container) {
        this.kind = kind;
        this.inventory = inventory;
        this.grid = grid;
        this.container = (kind == Kind.FURNACE || kind == Kind.CHEST) ? container : null;
    }

    public Kind kind() {
        return kind;
    }

    public String title() {
        return kind.title;
    }

    public boolean hasGrid() {
        return kind == Kind.INVENTORY || kind == Kind.CRAFTING_TABLE;
    }

    public boolean hasContainer() {
        return kind == Kind.FURNACE || kind == Kind.CHEST;
    }

    public Inventory inventory() {
        return inventory;
    }

    public CraftingGrid grid() {
        return grid;
    }

    /** The placed block's container (furnace or chest); null when no container is open. */
    public StorageContainer container() {
        return container;
    }

    /** The placed furnace, if this is a furnace gui (for the flame/arrow rendering); null otherwise. */
    public Furnace furnace() {
        return container instanceof Furnace furnace ? furnace : null;
    }

    /** Total number of interactive slots in the gui. */
    public int slotCount() {
        int count = Inventory.SIZE;
        if (hasGrid()) count += CraftingGrid.SIZE + 1;
        if (hasContainer()) count += container.size();
        return count;
    }

    public boolean isPlayerSlot(int slotId) {
        return slotId >= 0 && slotId < Inventory.SIZE;
    }

    public boolean isGridSlot(int slotId) {
        return hasGrid() && slotId >= GRID_START && slotId < OUTPUT_SLOT;
    }

    public boolean isOutputSlot(int slotId) {
        return hasGrid() && slotId == OUTPUT_SLOT;
    }

    /** True if {@code slotId} is one of the placed container's slots (furnace or chest). */
    public boolean isContainerSlot(int slotId) {
        return hasContainer() && slotId >= CONTAINER_START && slotId < CONTAINER_START + container.size();
    }

    /** True if {@code slotId} is a furnace slot (subset of {@link #isContainerSlot}). */
    public boolean isFurnaceSlot(int slotId) {
        return kind == Kind.FURNACE && slotId >= CONTAINER_START && slotId < CONTAINER_START + FURNACE_SLOT_COUNT;
    }

    /** The type held in a slot (null if empty); the output slot derives from the recipe. */
    public BlockType typeOf(int slotId) {
        if (isPlayerSlot(slotId)) return inventory.typeOf(slotId);
        if (isGridSlot(slotId)) return grid.get(slotId - GRID_START);
        if (isContainerSlot(slotId)) return container.typeOf(slotId - CONTAINER_START);
        return null;
    }

    /** The count in a slot (grid cells hold 0 or 1). */
    public int countOf(int slotId) {
        if (isPlayerSlot(slotId)) return inventory.countOf(slotId);
        if (isGridSlot(slotId)) return grid.get(slotId - GRID_START) == null ? 0 : 1;
        if (isContainerSlot(slotId)) return container.countOf(slotId - CONTAINER_START);
        return 0;
    }

    /** Writes a whole stack to a slot; grid cells ignore {@code count} and just record the type. */
    public void setSlot(int slotId, BlockType type, int count) {
        if (isPlayerSlot(slotId)) {
            inventory.setSlot(slotId, type, count);
        } else if (isGridSlot(slotId)) {
            grid.set(slotId - GRID_START, type);
        } else if (isContainerSlot(slotId)) {
            container.setSlot(slotId - CONTAINER_START, type, count);
        }
    }

    /** The recipe the current grid contents produce, or null. */
    public Crafting.Recipe currentRecipe() {
        return hasGrid() ? Crafting.match(grid.snapshot()) : null;
    }
}
