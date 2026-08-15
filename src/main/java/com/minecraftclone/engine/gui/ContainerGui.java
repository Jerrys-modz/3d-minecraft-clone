package com.minecraftclone.engine.gui;

import com.minecraftclone.player.Crafting;
import com.minecraftclone.player.CraftingGrid;
import com.minecraftclone.player.Inventory;
import com.minecraftclone.world.BlockType;
import com.minecraftclone.world.Furnace;

/**
 * The model behind every full-screen container GUI: the plain inventory screen,
 * a placed furnace, or a placed crafting table. A gui is a fixed slot space
 * built from the player's inventory plus, depending on {@link Kind}, a 3x3
 * crafting grid with its output slot and/or a placed block's container (a
 * furnace).
 * <p>
 * Slot numbering is one contiguous space so the mouse/controller logic never
 * has to branch on where an item lives. Which ranges exist depends on the kind
 * (a furnace has no crafting grid, so its slots take the grid's numbers):
 * <pre>
 *   0 .. 35                player inventory (0-8 hotbar, 9-35 main)
 *   36 .. 44               crafting grid cells   (INVENTORY / CRAFTING_TABLE)
 *   45                     crafting output slot  (INVENTORY / CRAFTING_TABLE)
 *   36 .. 38               furnace slots         (FURNACE: input, fuel, output)
 * </pre>
 * The crafting output is derived (it holds the current recipe's result) rather
 * than stored, exactly like Minecraft - {@link #currentRecipe} reports what
 * would come out of the grid right now.
 * <p>
 * This is the extension point for future GUIs: a new {@link Kind} adds its
 * container's slots to the space and the renderer/interaction code pick them
 * up automatically via {@link #typeOf}/{@link #setSlot}.
 */
public class ContainerGui {

    /** The flavour of container being shown. */
    public enum Kind {
        INVENTORY("Inventory"),
        FURNACE("Furnace"),
        CRAFTING_TABLE("Crafting Table");

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
     * First slot id of a placed container's slots (furnace). A furnace has no
     * crafting grid, so it reuses the grid's slot numbers: {@code 36..38}.
     */
    public static final int CONTAINER_START = Inventory.SIZE;
    /** Number of slots a furnace contributes. */
    public static final int FURNACE_SLOT_COUNT = Furnace.SLOT_COUNT;

    private final Kind kind;
    private final Inventory inventory;
    private final CraftingGrid grid;
    private final Furnace furnace;

    /**
     * @param kind     which container this gui shows
     * @param inventory the player's inventory (shared, always present)
     * @param grid     the shared crafting grid (used when the kind has one)
     * @param furnace  the placed furnace (FURNACE kind only; may be null otherwise)
     */
    public ContainerGui(Kind kind, Inventory inventory, CraftingGrid grid, Furnace furnace) {
        this.kind = kind;
        this.inventory = inventory;
        this.grid = grid;
        this.furnace = kind == Kind.FURNACE ? furnace : null;
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

    public Inventory inventory() {
        return inventory;
    }

    public CraftingGrid grid() {
        return grid;
    }

    public Furnace furnace() {
        return furnace;
    }

    /** Total number of interactive slots in the gui. */
    public int slotCount() {
        int count = Inventory.SIZE;
        if (hasGrid()) count += CraftingGrid.SIZE + 1;
        if (kind == Kind.FURNACE) count += FURNACE_SLOT_COUNT;
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

    public boolean isFurnaceSlot(int slotId) {
        return kind == Kind.FURNACE && slotId >= CONTAINER_START && slotId < CONTAINER_START + FURNACE_SLOT_COUNT;
    }

    /** The type held in a slot (null if empty); the output slot derives from the recipe. */
    public BlockType typeOf(int slotId) {
        if (isPlayerSlot(slotId)) return inventory.typeOf(slotId);
        if (isGridSlot(slotId)) return grid.get(slotId - GRID_START);
        if (isFurnaceSlot(slotId)) return furnace.typeOf(slotId - CONTAINER_START);
        return null;
    }

    /** The count in a slot (grid cells hold 0 or 1). */
    public int countOf(int slotId) {
        if (isPlayerSlot(slotId)) return inventory.countOf(slotId);
        if (isGridSlot(slotId)) return grid.get(slotId - GRID_START) == null ? 0 : 1;
        if (isFurnaceSlot(slotId)) return furnace.countOf(slotId - CONTAINER_START);
        return 0;
    }

    /** Writes a whole stack to a slot; grid cells ignore {@code count} and just record the type. */
    public void setSlot(int slotId, BlockType type, int count) {
        if (isPlayerSlot(slotId)) {
            inventory.setSlot(slotId, type, count);
        } else if (isGridSlot(slotId)) {
            grid.set(slotId - GRID_START, type);
        } else if (isFurnaceSlot(slotId)) {
            furnace.setSlot(slotId - CONTAINER_START, type, count);
        }
    }

    /** The recipe the current grid contents produce, or null. */
    public Crafting.Recipe currentRecipe() {
        return hasGrid() ? Crafting.match(grid.snapshot()) : null;
    }
}
