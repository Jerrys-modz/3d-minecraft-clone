package com.minecraftclone.player;

import com.minecraftclone.engine.gui.ContainerGui;
import com.minecraftclone.world.BlockType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class InventoryControllerTest {

    private static int firstEmptySlot(Inventory inv) {
        for (int i = 0; i < Inventory.SIZE; i++) {
            if (inv.isEmpty(i)) return i;
        }
        return -1;
    }

    @Test
    void leftClickLiftsAndPlacesWholeStacks() {
        Inventory inv = new Inventory();
        inv.setSlot(5, BlockType.DIRT, 64);
        InventoryController c = new InventoryController(inv, new CraftingGrid());
        c.click(5, false, false);
        assertTrue(inv.isEmpty(5));
        assertEquals(64, c.cursorCount());
        c.click(9, false, false);
        assertEquals(64, inv.countOf(9));
        assertFalse(c.hasCursorItem());
    }

    @Test
    void rightClickLiftsHalfAndPlacesOne() {
        Inventory inv = new Inventory();
        inv.setSlot(9, BlockType.DIRT, 32);
        InventoryController c = new InventoryController(inv, new CraftingGrid());
        c.click(9, true, false);
        assertEquals(16, c.cursorCount());
        assertEquals(16, inv.countOf(9));
        c.click(10, true, false);
        assertEquals(1, inv.countOf(10));
        assertEquals(15, c.cursorCount());
    }

    @Test
    void shiftClickQuickMovesBetweenHotbarAndInventory() {
        Inventory inv = new Inventory();
        InventoryController c = new InventoryController(inv, new CraftingGrid());
        inv.setSlot(0, BlockType.STONE, 40);
        c.click(0, false, true);
        assertTrue(inv.isEmpty(0));
        assertEquals(40, inv.getCount(BlockType.STONE));
    }

    @Test
    void craftsFromTheGridIntoTheCursor() {
        Inventory inv = new Inventory();
        CraftingGrid grid = new CraftingGrid();
        // 2x2 grid: stick recipe is P. / P. (cells 0, 2)
        grid.set(0, BlockType.PLANKS);
        grid.set(2, BlockType.PLANKS);
        InventoryController c = new InventoryController(inv, grid);
        assertEquals(BlockType.STICK, Crafting.match(grid.snapshot()).output());
        c.click(InventoryController.OUTPUT_SLOT, false, false);
        assertEquals(BlockType.STICK, c.cursorType());
        assertEquals(4, c.cursorCount());
        assertTrue(grid.isEmpty());
    }

    @Test
    void craftRefusesWhenCursorHoldsAnIncompatibleItem() {
        Inventory inv = new Inventory();
        CraftingGrid grid = new CraftingGrid();
        grid.set(0, BlockType.PLANKS);
        grid.set(3, BlockType.PLANKS);
        InventoryController c = new InventoryController(inv, grid);
        inv.setSlot(5, BlockType.DIRT, 5);
        c.click(5, false, false);
        c.click(InventoryController.OUTPUT_SLOT, false, false);
        assertEquals(BlockType.DIRT, c.cursorType());
        assertFalse(grid.isEmpty());
    }

    @Test
    void dragSpreadsOneItemPerSlot() {
        Inventory inv = new Inventory();
        InventoryController c = new InventoryController(inv, new CraftingGrid());
        inv.setSlot(10, BlockType.SAND, 3);
        c.beginDrag(10, false);
        c.continueDrag(11);
        c.continueDrag(12);
        c.continueDrag(13);
        c.endDrag(13);
        assertEquals(1, inv.countOf(10));
        assertEquals(1, inv.countOf(11));
        assertEquals(1, inv.countOf(12));
        assertEquals(0, inv.countOf(13), "only 3 sand to spread");
        assertFalse(c.hasCursorItem());
    }

    @Test
    void clickOnPressAndReleaseSameSlotIsStillAClick() {
        Inventory inv = new Inventory();
        InventoryController c = new InventoryController(inv, new CraftingGrid());
        inv.setSlot(5, BlockType.DIRT, 64);
        c.beginDrag(5, false);
        c.endDrag(5);
        assertTrue(inv.isEmpty(5));
        assertEquals(64, c.cursorCount());
    }

    @Test
    void rightClickNeverSwapsDifferentTypes() {
        Inventory inv = new Inventory();
        InventoryController c = new InventoryController(inv, new CraftingGrid());
        inv.setSlot(0, BlockType.DIRT, 5);
        c.pickCreativeItem(BlockType.STONE, false);
        c.click(0, true, false);
        assertEquals(BlockType.DIRT, inv.typeOf(0), "right-click leaves a different-type slot alone");
        assertEquals(5, inv.countOf(0));
        assertEquals(BlockType.STONE, c.cursorType());
    }

    @Test
    void closingReturnsCursorAndGridItems() {
        Inventory inv = new Inventory();
        CraftingGrid grid = new CraftingGrid();
        grid.set(1, BlockType.DIRT);
        InventoryController c = new InventoryController(inv, grid);
        inv.setSlot(15, BlockType.GRASS, 2);
        c.click(15, false, false);
        c.returnCursorToInventory();
        c.returnGridToInventory();
        assertEquals(2, inv.getCount(BlockType.GRASS));
        assertEquals(1, inv.getCount(BlockType.DIRT));
        assertFalse(c.hasCursorItem());
        assertTrue(grid.isEmpty());
    }

    @Test
    void creativeClickGivesFullStackOnCursor() {
        Inventory inv = new Inventory();
        InventoryController c = new InventoryController(inv, new CraftingGrid());
        c.pickCreativeItem(BlockType.DIRT, false);
        assertEquals(BlockType.DIRT, c.cursorType());
        assertEquals(64, c.cursorCount());
        c.pickCreativeItem(BlockType.DIAMOND_PICKAXE, false);
        assertEquals(1, c.cursorCount(), "tools are single-stack");
        assertEquals(64, inv.getCount(BlockType.DIRT), "replaced cursor item returns to inventory");
    }

    @Test
    void creativeShiftClickMovesStackIntoHotbar() {
        Inventory inv = new Inventory();
        InventoryController c = new InventoryController(inv, new CraftingGrid());
        c.pickCreativeItem(BlockType.STONE, true);
        assertEquals(BlockType.STONE, inv.typeOf(0));
        assertEquals(64, inv.countOf(0));
        assertFalse(c.hasCursorItem());
    }

    @Test
    void destroyClearsTheCursor() {
        Inventory inv = new Inventory();
        InventoryController c = new InventoryController(inv, new CraftingGrid());
        c.pickCreativeItem(BlockType.APPLE, false);
        assertTrue(c.hasCursorItem());
        c.destroyCursor();
        assertFalse(c.hasCursorItem());
    }

    @Test
    void firstEmptySlotHelperWorks() {
        Inventory inv = new Inventory();
        assertEquals(0, firstEmptySlot(inv));
    }

    @Test
    void clickEquipsArmorIntoMatchingSlot() {
        Inventory inv = new Inventory();
        inv.setSlot(10, BlockType.IRON_HELMET, 1);
        InventoryController c = new InventoryController(inv, new CraftingGrid());
        // Clicking an armor piece with an empty cursor auto-equips it.
        c.click(10, false, false);
        assertTrue(inv.isEmpty(10));
        assertEquals(BlockType.IRON_HELMET, inv.armorType(Inventory.ARMOR_SLOT_HELMET));
        assertFalse(c.hasCursorItem());
    }

    @Test
    void clickDoesNotEquipWhenSlotIsOccupied() {
        Inventory inv = new Inventory();
        inv.setSlot(10, BlockType.IRON_HELMET, 1);
        inv.setArmor(Inventory.ARMOR_SLOT_HELMET, BlockType.WOOD_HELMET);
        InventoryController c = new InventoryController(inv, new CraftingGrid());
        c.click(10, false, false);
        assertTrue(inv.isEmpty(10), "occupied armor slot means a normal pickup instead");
        assertEquals(BlockType.IRON_HELMET, c.cursorType());
        assertEquals(BlockType.WOOD_HELMET, inv.armorType(Inventory.ARMOR_SLOT_HELMET));
    }

    @Test
    void clickArmorSlotPicksItUpAndSwaps() {
        Inventory inv = new Inventory();
        inv.setArmor(Inventory.ARMOR_SLOT_CHESTPLATE, BlockType.IRON_CHESTPLATE);
        InventoryController c = new InventoryController(inv, new CraftingGrid());
        int slot = ContainerGui.ARMOR_START + Inventory.ARMOR_SLOT_CHESTPLATE;
        c.click(slot, false, false);
        assertEquals(BlockType.IRON_CHESTPLATE, c.cursorType());
        assertNull(inv.armorType(Inventory.ARMOR_SLOT_CHESTPLATE));
    }

    @Test
    void clickRejectsMismatchedArmorOnSlot() {
        Inventory inv = new Inventory();
        InventoryController c = new InventoryController(inv, new CraftingGrid());
        int helmetSlot = ContainerGui.ARMOR_START + Inventory.ARMOR_SLOT_HELMET;
        c.pickCreativeItem(BlockType.IRON_BOOTS, false);
        c.click(helmetSlot, false, false);
        assertEquals(BlockType.IRON_BOOTS, c.cursorType(), "boots can't go in the helmet slot");
        assertNull(inv.armorType(Inventory.ARMOR_SLOT_HELMET));
    }

    @Test
    void shiftClickEquipsAndUnequipsArmor() {
        Inventory inv = new Inventory();
        inv.setSlot(20, BlockType.DIAMOND_LEGGINGS, 1);
        InventoryController c = new InventoryController(inv, new CraftingGrid());
        // Shift-click the piece in the bag: auto-equip.
        c.click(20, false, true);
        assertTrue(inv.isEmpty(20));
        assertEquals(BlockType.DIAMOND_LEGGINGS, inv.armorType(Inventory.ARMOR_SLOT_LEGGINGS));
        // Shift-click the armor slot: unequip back to the inventory.
        int slot = ContainerGui.ARMOR_START + Inventory.ARMOR_SLOT_LEGGINGS;
        c.click(slot, false, true);
        assertNull(inv.armorType(Inventory.ARMOR_SLOT_LEGGINGS));
        assertEquals(1, inv.getCount(BlockType.DIAMOND_LEGGINGS));
    }

    @Test
    void armorSlotIsCountedInTheGui() {
        Inventory inv = new Inventory();
        ContainerGui gui = new ContainerGui(ContainerGui.Kind.INVENTORY, inv, new CraftingGrid(), null);
        assertEquals(Inventory.SIZE + CraftingGrid.SIZE + 1 + Inventory.ARMOR_SLOT_COUNT, gui.slotCount());
        assertTrue(gui.isArmorSlot(ContainerGui.ARMOR_START));
        assertTrue(gui.isArmorSlot(ContainerGui.ARMOR_START + Inventory.ARMOR_SLOT_COUNT - 1));
        assertFalse(gui.isArmorSlot(ContainerGui.OUTPUT_SLOT));
    }

    @Test
    void dragRejectsMismatchedArmorOnSlot() {
        Inventory inv = new Inventory();
        InventoryController c = new InventoryController(inv, new CraftingGrid());
        int helmetSlot = ContainerGui.ARMOR_START + Inventory.ARMOR_SLOT_HELMET;
        c.pickCreativeItem(BlockType.DIAMOND_CHESTPLATE, false);
        c.beginDrag(10, false);
        c.continueDrag(helmetSlot);
        c.endDrag(helmetSlot);
        assertEquals(BlockType.DIAMOND_CHESTPLATE, c.cursorType(), "chestplate can't be dragged into helmet slot");
        assertNull(inv.armorType(Inventory.ARMOR_SLOT_HELMET));
    }

    @Test
    void dragStartingOnABaggedArmorPieceRejectsAMismatchedSlot() {
        // Regression: a drag that *starts* on an inventory slot holding armor (not
        // already on the cursor) must not resolve via a plain click(dragStart, ...) -
        // that would trigger the bag's auto-equip shortcut and jump the piece into its
        // own matching slot, ignoring wherever the drag actually released.
        Inventory inv = new Inventory();
        inv.setSlot(10, BlockType.DIAMOND_CHESTPLATE, 1);
        InventoryController c = new InventoryController(inv, new CraftingGrid());
        int helmetSlot = ContainerGui.ARMOR_START + Inventory.ARMOR_SLOT_HELMET;
        c.beginDrag(10, false);
        c.continueDrag(helmetSlot);
        c.endDrag(helmetSlot);
        assertEquals(BlockType.DIAMOND_CHESTPLATE, c.cursorType(), "still on the cursor, not auto-equipped");
        assertEquals(1, c.cursorCount(), "exactly the one piece - not duplicated");
        assertTrue(inv.isEmpty(10), "lifted out of its source slot, not left behind too");
        assertNull(inv.armorType(Inventory.ARMOR_SLOT_HELMET));
        assertNull(inv.armorType(Inventory.ARMOR_SLOT_CHESTPLATE), "never auto-equipped into its own slot either");
    }

    @Test
    void dragStartingOnABaggedArmorPieceEquipsItsMatchingSlot() {
        // The same starting gesture, but released on the *correct* slot: should equip.
        Inventory inv = new Inventory();
        inv.setSlot(10, BlockType.DIAMOND_CHESTPLATE, 1);
        InventoryController c = new InventoryController(inv, new CraftingGrid());
        int chestplateSlot = ContainerGui.ARMOR_START + Inventory.ARMOR_SLOT_CHESTPLATE;
        c.beginDrag(10, false);
        c.continueDrag(chestplateSlot);
        c.endDrag(chestplateSlot);
        assertFalse(c.hasCursorItem());
        assertEquals(BlockType.DIAMOND_CHESTPLATE, inv.armorType(Inventory.ARMOR_SLOT_CHESTPLATE));
        assertTrue(inv.isEmpty(10));
    }

    @Test
    void shiftClickPlacesStackIntoCraftingGrid() {
        Inventory inv = new Inventory();
        CraftingGrid grid = new CraftingGrid();
        InventoryController c = new InventoryController(inv, grid);  // Creates a ContainerGui internally
        inv.setSlot(10, BlockType.PLANKS, 5);

        // Shift-click should place items into the crafting grid
        c.click(10, false, true);

        // Verify: 5 items from inventory should be placed in 5 grid cells
        assertEquals(BlockType.PLANKS, grid.get(0), "first grid cell should have 1 plank");
        assertEquals(BlockType.PLANKS, grid.get(1), "second grid cell should have 1 plank");
        assertEquals(BlockType.PLANKS, grid.get(2), "third grid cell should have 1 plank");
        assertEquals(BlockType.PLANKS, grid.get(3), "fourth grid cell should have 1 plank");
        assertEquals(BlockType.PLANKS, grid.get(4), "fifth grid cell should have 1 plank");
        assertTrue(inv.isEmpty(10), "inventory slot should be empty");
    }

    @Test
    void dragPlacesOneItemPerCraftingGridCell() {
        Inventory inv = new Inventory();
        CraftingGrid grid = new CraftingGrid();
        InventoryController c = new InventoryController(inv, grid);
        inv.setSlot(10, BlockType.DIRT, 5);

        // Drag from inventory slot with 5 dirt to 5 different grid cells
        c.beginDrag(10, false);
        c.continueDrag(ContainerGui.GRID_START + 0);  // Grid cell 0
        c.continueDrag(ContainerGui.GRID_START + 1);  // Grid cell 1
        c.continueDrag(ContainerGui.GRID_START + 2);  // Grid cell 2
        c.continueDrag(ContainerGui.GRID_START + 3);  // Grid cell 3
        c.endDrag(ContainerGui.GRID_START + 4);       // Grid cell 4

        // Debug: check how many cells actually have items
        int cellsWithItems = 0;
        for (int i = 0; i < 9; i++) {
            if (grid.get(i) != null) cellsWithItems++;
        }

        // Should place 1 dirt in each of the 5 cells
        assertEquals(BlockType.DIRT, grid.get(0), "grid cell 0 should have dirt");
        assertEquals(BlockType.DIRT, grid.get(1), "grid cell 1 should have dirt");
        assertEquals(BlockType.DIRT, grid.get(2), "grid cell 2 should have dirt");
        assertEquals(BlockType.DIRT, grid.get(3), "grid cell 3 should have dirt");
        assertEquals(BlockType.DIRT, grid.get(4), "grid cell 4 should have dirt (only " + cellsWithItems + " cells filled)");
        assertFalse(c.hasCursorItem(), "cursor should be empty after placing all 5 items");
    }

    @Test
    void dragWithPopulatedCursorToCraftingGridPlacesInAllVisitedCells() {
        Inventory inv = new Inventory();
        CraftingGrid grid = new CraftingGrid();
        InventoryController c = new InventoryController(inv, grid);
        // Pre-populate cursor with planks (not lifted from dragStart)
        inv.setSlot(10, BlockType.PLANKS, 5);
        c.click(10, false, false); // Lift the stack onto the cursor
        assertTrue(c.hasCursorItem(), "cursor should hold planks");
        assertEquals(5, c.cursorCount(), "cursor should have 5 planks");

        // Start drag on a crafting grid cell (dragStart is a destination, not source)
        c.beginDrag(ContainerGui.GRID_START + 0, false);
        c.continueDrag(ContainerGui.GRID_START + 1);
        c.continueDrag(ContainerGui.GRID_START + 2);
        c.continueDrag(ContainerGui.GRID_START + 3);
        c.endDrag(ContainerGui.GRID_START + 4);

        // All 5 visited cells should receive 1 plank each, including dragStart (cell 0)
        assertEquals(BlockType.PLANKS, grid.get(0), "grid cell 0 (dragStart) should have plank");
        assertEquals(BlockType.PLANKS, grid.get(1), "grid cell 1 should have plank");
        assertEquals(BlockType.PLANKS, grid.get(2), "grid cell 2 should have plank");
        assertEquals(BlockType.PLANKS, grid.get(3), "grid cell 3 should have plank");
        assertEquals(BlockType.PLANKS, grid.get(4), "grid cell 4 should have plank");
        assertFalse(c.hasCursorItem(), "cursor should be empty after placing all 5 items");
    }
}
