package com.minecraftclone.engine.gui;

import com.minecraftclone.player.AdvancedCraftingTableGrid;
import com.minecraftclone.player.Crafting;
import com.minecraftclone.player.CraftingGrid;
import com.minecraftclone.player.CraftingTableGrid;
import com.minecraftclone.player.Grid;
import com.minecraftclone.player.Inventory;
import com.minecraftclone.player.ItemStack;
import com.minecraftclone.player.StorageContainer;
import com.minecraftclone.world.BlockType;
import com.minecraftclone.world.Furnace;
import com.minecraftclone.world.tinkers.PartBuilderGui;
import com.minecraftclone.world.tinkers.ToolPartType;
import com.minecraftclone.world.tinkers.ToolStationGui;

/**
 * The model behind every full-screen container GUI: the plain inventory screen,
 * a placed furnace, a placed crafting table, a placed chest, a Part Builder, or
 * a Tool Station. A gui is a fixed slot space built from the player's inventory
 * plus, depending on {@link Kind}, additional specialized slots.
 * <p>
 * Slot numbering is one contiguous space so the mouse/controller logic never
 * has to branch on where an item lives. Which ranges exist depends on the kind:
 * <pre>
 *   0 .. 35                player inventory (0-8 hotbar, 9-35 main)
 *   36 .. 44               crafting grid cells   (INVENTORY / CRAFTING_TABLE)
 *   45                     crafting output slot  (INVENTORY / CRAFTING_TABLE)
 *   36 .. 38               furnace slots         (FURNACE: input, fuel, output)
 *   36 .. 62               chest slots           (CHEST: 27 slots)
 *   36                     Part Builder material slot
 *   37                     Part Builder output slot
 *   38 .. 45               Part Builder shape-selection buttons (8 shapes)
 *   36 .. 40               Tool Station input slots (head, rod, 3 extras)
 *   41                     Tool Station output slot
 * </pre>
 */
public class ContainerGui {

    /** The flavour of container being shown. */
    public enum Kind {
        INVENTORY("Inventory"),
        FURNACE("Furnace"),
        CRAFTING_TABLE("Crafting Table"),
        ADVANCED_CRAFTING_TABLE("Advanced Crafting Table"),
        CHEST("Chest"),
        PART_BUILDER("Part Builder"),
        TOOL_STATION("Tool Station");

        private final String title;

        Kind(String title) {
            this.title = title;
        }
    }

    /** First slot id of the crafting grid (directly after the player slots). */
    public static final int GRID_START = Inventory.SIZE;
    /** Slot id of the crafting result (2x2 grid: 36 + 4 = 40). Note: for 3x3 it would be 45. */
    public static final int OUTPUT_SLOT = GRID_START + CraftingGrid.SIZE;
    /**
     * First slot id of the armor column (helmet/chestplate/leggings/boots),
     * just past the crafting output. Only the player's own inventory screen
     * (2x2 grid) shows armor slots - placed containers (furnace/chest) don't.
     */
    public static final int ARMOR_START = 41;  // GRID_START (36) + CraftingGrid.SIZE (4) + 1
    /** Number of armor slots on the player's inventory screen. */
    public static final int ARMOR_SLOT_COUNT = Inventory.ARMOR_SLOT_COUNT;
    /**
     * First slot id of a placed container's slots (furnace/chest). A container
     * has no crafting grid, so it reuses the grid's slot numbers: {@code 36..}.
     */
    public static final int CONTAINER_START = Inventory.SIZE;
    /** Number of slots a furnace contributes. */
    public static final int FURNACE_SLOT_COUNT = Furnace.SLOT_COUNT;
    /** Number of slots a chest contributes. */
    public static final int CHEST_SLOT_COUNT = com.minecraftclone.world.Chest.SLOT_COUNT;

    // -----------------------------------------------------------------------
    // Part Builder slot IDs  (PART_BUILDER kind only)
    // -----------------------------------------------------------------------

    /** Material input slot for the Part Builder. */
    public static final int PB_MATERIAL_SLOT = GRID_START;          // 36
    /** Output slot for the Part Builder (read-only; shows crafted part). */
    public static final int PB_OUTPUT_SLOT   = GRID_START + 1;      // 37
    /** First shape-selection button slot for the Part Builder (8 shapes, 38..45). */
    public static final int PB_SHAPE_SLOT_0  = GRID_START + 2;      // 38
    /** Number of shape-selection button slots (one per {@link ToolPartType}). */
    public static final int PB_SHAPE_COUNT   = ToolPartType.values().length; // 8

    // -----------------------------------------------------------------------
    // Tool Station slot IDs  (TOOL_STATION kind only)
    // -----------------------------------------------------------------------

    /** First input slot for the Tool Station (head; rod is +1; extras are +2..+4). */
    public static final int TS_SLOT_0   = GRID_START;                          // 36
    /** Output slot for the Tool Station (read-only; shows assembled tool). */
    public static final int TS_OUTPUT_SLOT = GRID_START + ToolStationGui.INPUT_SLOTS; // 41

    private final Kind kind;
    private final Inventory inventory;
    private final CraftingGrid playerGrid;
    private final CraftingTableGrid tableGrid;
    private final AdvancedCraftingTableGrid advancedGrid;
    private final StorageContainer container;
    private final PartBuilderGui partBuilderGui;
    private final ToolStationGui toolStationGui;

    /**
     * @param kind       which container this gui shows
     * @param inventory  the player's inventory (shared, always present)
     * @param grid       the shared 2x2 crafting grid (used for INVENTORY/CRAFTING_TABLE)
     * @param container  the placed block's container (FURNACE/CHEST kinds only;
     *                   may be null otherwise)
     */
    public ContainerGui(Kind kind, Inventory inventory, CraftingGrid grid, StorageContainer container) {
        this.kind = kind;
        this.inventory = inventory;
        this.playerGrid = grid;
        this.tableGrid = new CraftingTableGrid();
        this.advancedGrid = new AdvancedCraftingTableGrid();
        this.container = (kind == Kind.FURNACE || kind == Kind.CHEST) ? container : null;
        this.partBuilderGui = null;
        this.toolStationGui = null;
    }

    /** Private constructor used by the static Tinkers factory methods. */
    private ContainerGui(Kind kind, Inventory inventory, PartBuilderGui pbGui, ToolStationGui tsGui) {
        this.kind = kind;
        this.inventory = inventory;
        this.playerGrid = null;
        this.tableGrid  = null;
        this.advancedGrid = null;
        this.container = null;
        this.partBuilderGui = pbGui;
        this.toolStationGui = tsGui;
    }

    /** Opens a Part Builder screen backed by the given gui model. */
    public static ContainerGui forPartBuilder(Inventory inventory, PartBuilderGui pbGui) {
        return new ContainerGui(Kind.PART_BUILDER, inventory, pbGui, null);
    }

    /** Opens a Tool Station screen backed by the given gui model. */
    public static ContainerGui forToolStation(Inventory inventory, ToolStationGui tsGui) {
        return new ContainerGui(Kind.TOOL_STATION, inventory, null, tsGui);
    }

    public Kind kind() {
        return kind;
    }

    public String title() {
        return kind.title;
    }

    public boolean hasGrid() {
        return kind == Kind.INVENTORY || kind == Kind.CRAFTING_TABLE || kind == Kind.ADVANCED_CRAFTING_TABLE;
    }

    public boolean hasContainer() {
        return kind == Kind.FURNACE || kind == Kind.CHEST;
    }

    public Inventory inventory() {
        return inventory;
    }

    /**
     * Returns the crafting grid. Uses the player grid (2x2) for INVENTORY,
     * the table grid (3x3) for CRAFTING_TABLE, and the advanced grid (5x5) for ADVANCED_CRAFTING_TABLE.
     */
    public Grid grid() {
        return switch (kind) {
            case CRAFTING_TABLE -> tableGrid;
            case ADVANCED_CRAFTING_TABLE -> advancedGrid;
            default -> playerGrid;
        };
    }

    /** Returns the crafting grid size (4 for 2x2, 9 for 3x3), determined from the grid's snapshot. */
    public int gridSize() {
        return grid().snapshot().length;
    }

    /** The placed block's container (furnace or chest); null when no container is open. */
    public StorageContainer container() {
        return container;
    }

    /** The placed furnace, if this is a furnace gui (for the flame/arrow rendering); null otherwise. */
    public Furnace furnace() {
        return container instanceof Furnace furnace ? furnace : null;
    }

    /** The Part Builder gui model; non-null only for {@link Kind#PART_BUILDER}. */
    public PartBuilderGui partBuilderGui() {
        return partBuilderGui;
    }

    /** The Tool Station gui model; non-null only for {@link Kind#TOOL_STATION}. */
    public ToolStationGui toolStationGui() {
        return toolStationGui;
    }

    /** Total number of interactive slots in the gui. */
    public int slotCount() {
        int count = Inventory.SIZE;
        if (hasGrid()) count += gridSize() + 1;
        if (hasArmor()) count += ARMOR_SLOT_COUNT;
        if (hasContainer()) count += container.size();
        if (kind == Kind.PART_BUILDER) count += 2 + PB_SHAPE_COUNT;  // material + output + 8 shape buttons
        if (kind == Kind.TOOL_STATION) count += ToolStationGui.INPUT_SLOTS + 1; // 5 inputs + output
        return count;
    }

    /** True only on the player's own inventory screen - the one screen that shows the armor column. */
    public boolean hasArmor() {
        return kind == Kind.INVENTORY;
    }

    public boolean isPlayerSlot(int slotId) {
        return slotId >= 0 && slotId < Inventory.SIZE;
    }

    public boolean isGridSlot(int slotId) {
        return hasGrid() && slotId >= GRID_START && slotId < GRID_START + gridSize();
    }

    public boolean isOutputSlot(int slotId) {
        return hasGrid() && slotId == GRID_START + gridSize();
    }

    /** True if {@code slotId} is one of the four armor slots (helmet/chestplate/leggings/boots). */
    public boolean isArmorSlot(int slotId) {
        return hasArmor() && slotId >= ARMOR_START && slotId < ARMOR_START + ARMOR_SLOT_COUNT;
    }

    /** True if {@code slotId} is one of the placed container's slots (furnace or chest). */
    public boolean isContainerSlot(int slotId) {
        return hasContainer() && slotId >= CONTAINER_START && slotId < CONTAINER_START + container.size();
    }

    /** True if {@code slotId} is a furnace slot (subset of {@link #isContainerSlot}). */
    public boolean isFurnaceSlot(int slotId) {
        return kind == Kind.FURNACE && slotId >= CONTAINER_START && slotId < CONTAINER_START + FURNACE_SLOT_COUNT;
    }

    // -----------------------------------------------------------------------
    // Part Builder slot predicates
    // -----------------------------------------------------------------------

    /** True if {@code slotId} is the Part Builder's material input slot. */
    public boolean isPbMaterialSlot(int slotId) {
        return kind == Kind.PART_BUILDER && slotId == PB_MATERIAL_SLOT;
    }

    /** True if {@code slotId} is the Part Builder's crafted-part output slot. */
    public boolean isPbOutputSlot(int slotId) {
        return kind == Kind.PART_BUILDER && slotId == PB_OUTPUT_SLOT;
    }

    /** True if {@code slotId} is one of the Part Builder's 8 shape-selection button slots. */
    public boolean isPbShapeSlot(int slotId) {
        return kind == Kind.PART_BUILDER
                && slotId >= PB_SHAPE_SLOT_0
                && slotId < PB_SHAPE_SLOT_0 + PB_SHAPE_COUNT;
    }

    // -----------------------------------------------------------------------
    // Tool Station slot predicates
    // -----------------------------------------------------------------------

    /** True if {@code slotId} is one of the Tool Station's 5 part input slots. */
    public boolean isTsInputSlot(int slotId) {
        return kind == Kind.TOOL_STATION
                && slotId >= TS_SLOT_0
                && slotId < TS_SLOT_0 + ToolStationGui.INPUT_SLOTS;
    }

    /** True if {@code slotId} is the Tool Station's assembled-tool output slot. */
    public boolean isTsOutputSlot(int slotId) {
        return kind == Kind.TOOL_STATION && slotId == TS_OUTPUT_SLOT;
    }

    /** The type held in a slot (null if empty); the output slot derives from the recipe. */
    public BlockType typeOf(int slotId) {
        if (isPlayerSlot(slotId)) return inventory.typeOf(slotId);
        if (isGridSlot(slotId)) return grid().get(slotId - GRID_START);
        if (isArmorSlot(slotId)) return inventory.armorType(slotId - ARMOR_START);
        if (isContainerSlot(slotId)) return container.typeOf(slotId - CONTAINER_START);
        if (isPbMaterialSlot(slotId)) return partBuilderGui.materialType();
        if (isPbOutputSlot(slotId)) {
            ItemStack out = partBuilderGui.currentOutput();
            return out.isEmpty() ? null : out.type();
        }
        if (isPbShapeSlot(slotId)) return null; // shape buttons don't hold items
        if (isTsInputSlot(slotId)) {
            ItemStack s = toolStationGui.slot(slotId - TS_SLOT_0);
            return s.isEmpty() ? null : s.type();
        }
        if (isTsOutputSlot(slotId)) {
            ItemStack out = toolStationGui.currentOutput();
            return out.isEmpty() ? null : out.type();
        }
        return null;
    }

    /** The count in a slot (grid cells hold 0 or 1). */
    public int countOf(int slotId) {
        if (isPlayerSlot(slotId)) return inventory.countOf(slotId);
        if (isGridSlot(slotId)) return grid().get(slotId - GRID_START) == null ? 0 : 1;
        if (isArmorSlot(slotId)) return inventory.armorType(slotId - ARMOR_START) == null ? 0 : 1;
        if (isContainerSlot(slotId)) return container.countOf(slotId - CONTAINER_START);
        if (isPbMaterialSlot(slotId)) return partBuilderGui.materialCount();
        if (isPbOutputSlot(slotId)) return partBuilderGui.currentOutput().isEmpty() ? 0 : 1;
        if (isPbShapeSlot(slotId)) return 0;
        if (isTsInputSlot(slotId)) {
            ItemStack s = toolStationGui.slot(slotId - TS_SLOT_0);
            return s.isEmpty() ? 0 : 1;
        }
        if (isTsOutputSlot(slotId)) return toolStationGui.currentOutput().isEmpty() ? 0 : 1;
        return 0;
    }

    /**
     * The full {@link ItemStack} at {@code slotId}, including any Tinkers' Construct payload.
     * Prefer this over {@link #typeOf}/{@link #countOf} when you need to distinguish Tinkers
     * items from vanilla ones (e.g. in drag/click logic).
     *
     * @return the stack in the slot, or {@link ItemStack#EMPTY}
     */
    public ItemStack stackOf(int slotId) {
        if (isPlayerSlot(slotId)) return inventory.stackOf(slotId);
        if (isGridSlot(slotId)) {
            BlockType t = grid().get(slotId - GRID_START);
            return t == null ? ItemStack.EMPTY : ItemStack.of(t, 1);
        }
        if (isArmorSlot(slotId)) {
            BlockType t = inventory.armorType(slotId - ARMOR_START);
            return t == null ? ItemStack.EMPTY : ItemStack.of(t, 1);
        }
        if (isContainerSlot(slotId)) {
            int cs = slotId - CONTAINER_START;
            BlockType t = container.typeOf(cs);
            int cnt = container.countOf(cs);
            return (t == null || cnt <= 0) ? ItemStack.EMPTY : ItemStack.of(t, cnt);
        }
        if (isPbMaterialSlot(slotId)) return partBuilderGui.materialSlot();
        if (isPbOutputSlot(slotId))   return partBuilderGui.currentOutput();
        if (isPbShapeSlot(slotId))    return ItemStack.EMPTY;
        if (isTsInputSlot(slotId))    return toolStationGui.slot(slotId - TS_SLOT_0);
        if (isTsOutputSlot(slotId))   return toolStationGui.currentOutput();
        return ItemStack.EMPTY;
    }

    /**
     * Writes a full {@link ItemStack} to {@code slotId}, preserving any Tinkers' payload.
     * Shape-button and output slots are read-only and silently ignored.
     *
     * @param stack the stack to write; pass {@link ItemStack#EMPTY} to clear
     */
    public void setStack(int slotId, ItemStack stack) {
        if (stack == null) stack = ItemStack.EMPTY;
        if (isPlayerSlot(slotId)) {
            inventory.setStack(slotId, stack);
        } else if (isPbMaterialSlot(slotId)) {
            partBuilderGui.setMaterial(stack);
        } else if (isTsInputSlot(slotId)) {
            toolStationGui.setSlot(slotId - TS_SLOT_0, stack);
        } else {
            // Non-player slots (grid, armor, container) only support vanilla types.
            setSlot(slotId, stack.isEmpty() ? null : stack.type(), stack.count());
        }
    }

    /** Writes a whole stack to a slot; grid cells ignore {@code count} and just record the type. */
    public void setSlot(int slotId, BlockType type, int count) {
        if (isPlayerSlot(slotId)) {
            inventory.setSlot(slotId, type, count);
        } else if (isGridSlot(slotId)) {
            grid().set(slotId - GRID_START, type);
        } else if (isArmorSlot(slotId)) {
            inventory.setArmor(slotId - ARMOR_START, type);
        } else if (isContainerSlot(slotId)) {
            container.setSlot(slotId - CONTAINER_START, type, count);
        } else if (isPbMaterialSlot(slotId)) {
            partBuilderGui.setMaterial(type == null ? ItemStack.EMPTY : ItemStack.of(type, count));
        } else if (isTsInputSlot(slotId)) {
            toolStationGui.setSlot(slotId - TS_SLOT_0, ItemStack.EMPTY); // type-only path clears the slot
        }
        // PB output, PB shape buttons, TS output — read-only, silently ignored
    }

    /** The recipe the current grid contents produce, or null. */
    public Crafting.Recipe currentRecipe() {
        if (!hasGrid()) return null;
        BlockType[] snapshot = grid().snapshot();
        // Determine matching method based on grid size
        return switch (snapshot.length) {
            case 9 -> Crafting.match3x3(snapshot);
            case 25 -> Crafting.match5x5(snapshot);
            default -> Crafting.match2x2(snapshot);
        };
    }
}
