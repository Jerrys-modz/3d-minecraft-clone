package com.minecraftclone.engine.gui;

import com.minecraftclone.engine.audio.SoundEvent;
import com.minecraftclone.player.CraftingGrid;
import com.minecraftclone.player.Grid;
import com.minecraftclone.player.Inventory;
import com.minecraftclone.player.InventoryController;
import com.minecraftclone.player.JoinedStorage;
import com.minecraftclone.world.BlockType;
import com.minecraftclone.world.Chest;
import com.minecraftclone.world.Furnace;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ContainerGuiTest {

    private static final int INPUT = ContainerGui.CONTAINER_START + Furnace.SLOT_INPUT;
    private static final int FUEL = ContainerGui.CONTAINER_START + Furnace.SLOT_FUEL;
    private static final int OUTPUT = ContainerGui.CONTAINER_START + Furnace.SLOT_OUTPUT;

    private InventoryController furnaceController(Inventory inv, Furnace f) {
        ContainerGui gui = new ContainerGui(ContainerGui.Kind.FURNACE, inv, new CraftingGrid(), f);
        return new InventoryController(gui);
    }

    @Test
    void furnaceGuiRoutesClicksToTheFurnace() {
        Inventory inv = new Inventory();
        Furnace f = new Furnace();
        InventoryController c = furnaceController(inv, f);
        inv.setSlot(0, BlockType.IRON_ORE, 5);
        c.click(0, false, false);
        c.click(INPUT, false, false);
        assertEquals(BlockType.IRON_ORE, f.typeOf(Furnace.SLOT_INPUT));
        assertEquals(5, f.countOf(Furnace.SLOT_INPUT));
        assertTrue(inv.isEmpty(0));

        c.click(INPUT, false, false);
        c.click(0, false, false);
        assertEquals(BlockType.IRON_ORE, inv.typeOf(0));
        assertEquals(5, inv.countOf(0));
        assertTrue(f.isEmpty(Furnace.SLOT_INPUT));
    }

    @Test
    void shiftClickSendsOreToInputAndCoalToFuel() {
        Inventory inv = new Inventory();
        Furnace f = new Furnace();
        InventoryController c = furnaceController(inv, f);
        inv.setSlot(0, BlockType.GOLD_ORE, 3);
        inv.setSlot(1, BlockType.COAL, 2);
        c.click(0, false, true);
        assertEquals(BlockType.GOLD_ORE, f.typeOf(Furnace.SLOT_INPUT));
        assertEquals(3, f.countOf(Furnace.SLOT_INPUT));
        c.click(1, false, true);
        assertEquals(BlockType.COAL, f.typeOf(Furnace.SLOT_FUEL));
        assertEquals(2, f.countOf(Furnace.SLOT_FUEL));
        assertTrue(inv.isEmpty(0));
        assertTrue(inv.isEmpty(1));
    }

    @Test
    void shiftClickSendsAnyFuelToFuelSlot() {
        Inventory inv = new Inventory();
        Furnace f = new Furnace();
        InventoryController c = furnaceController(inv, f);
        inv.setSlot(0, BlockType.WOOD_LOG, 3);
        c.click(0, false, true);
        assertEquals(BlockType.WOOD_LOG, f.typeOf(Furnace.SLOT_FUEL), "logs are a fuel");
        assertEquals(3, f.countOf(Furnace.SLOT_FUEL));
        assertTrue(inv.isEmpty(0));

        Inventory inv2 = new Inventory();
        Furnace f2 = new Furnace();
        InventoryController c2 = furnaceController(inv2, f2);
        inv2.setSlot(0, BlockType.STICK, 5);
        c2.click(0, false, true);
        assertEquals(BlockType.STICK, f2.typeOf(Furnace.SLOT_FUEL), "sticks are a fuel");
        assertEquals(5, f2.countOf(Furnace.SLOT_FUEL));
        assertTrue(inv2.isEmpty(0));
    }

    @Test
    void shiftClickOnUnrelatedItemStaysInInventory() {
        Inventory inv = new Inventory();
        Furnace f = new Furnace();
        InventoryController c = furnaceController(inv, f);
        inv.setSlot(0, BlockType.DIRT, 10);
        c.click(0, false, true);
        assertEquals(10, inv.getCount(BlockType.DIRT));
        assertTrue(f.isEmpty(Furnace.SLOT_INPUT));
        assertTrue(f.isEmpty(Furnace.SLOT_FUEL));
    }

    @Test
    void shiftClickMovesFurnaceOutputToInventory() {
        Inventory inv = new Inventory();
        Furnace f = new Furnace();
        f.setSlot(Furnace.SLOT_OUTPUT, BlockType.IRON_INGOT, 4);
        InventoryController c = furnaceController(inv, f);
        c.click(OUTPUT, false, true);
        assertTrue(f.isEmpty(Furnace.SLOT_OUTPUT));
        assertEquals(4, inv.getCount(BlockType.IRON_INGOT));
    }

    @Test
    void shiftClickFurnaceSlotMovesStackToInventory() {
        Inventory inv = new Inventory();
        Furnace f = new Furnace();
        f.setSlot(Furnace.SLOT_FUEL, BlockType.COAL, 7);
        InventoryController c = furnaceController(inv, f);
        c.click(FUEL, false, true);
        assertTrue(f.isEmpty(Furnace.SLOT_FUEL));
        assertEquals(7, inv.getCount(BlockType.COAL));
    }

    @Test
    void craftingTableGuiCraftsThroughTheGrid() {
        Inventory inv = new Inventory();
        CraftingGrid grid = new CraftingGrid();
        ContainerGui gui = new ContainerGui(ContainerGui.Kind.CRAFTING_TABLE, inv, grid, null);
        InventoryController c = new InventoryController(gui);
        // 3x3 table grid: test with a simple recipe that works in 3x3
        // Use stone stairs pattern (K.. / KK. / KKK)
        Grid tableGrid = gui.grid();
        tableGrid.set(0, BlockType.STONE);
        tableGrid.set(3, BlockType.STONE);
        tableGrid.set(4, BlockType.STONE);
        tableGrid.set(6, BlockType.STONE);
        tableGrid.set(7, BlockType.STONE);
        tableGrid.set(8, BlockType.STONE);
        assertEquals(BlockType.STONE_STAIRS, gui.currentRecipe().output());
        c.click(ContainerGui.GRID_START + 9, false, false);  // output slot for 3x3 is at GRID_START + 9
        assertEquals(BlockType.STONE_STAIRS, c.cursorType());
        assertEquals(4, c.cursorCount());
        assertTrue(tableGrid.isEmpty());
    }

    @Test
    void shiftClickFillsEmptyCraftingTableCellsFromInventory() {
        Inventory inv = new Inventory();
        CraftingGrid grid = new CraftingGrid();
        ContainerGui gui = new ContainerGui(ContainerGui.Kind.CRAFTING_TABLE, inv, grid, null);
        InventoryController c = new InventoryController(gui);
        inv.setSlot(0, BlockType.PLANKS, 10);
        c.click(0, false, true);
        // 3x3 crafting table grid has 9 cells, so 9 planks fit
        Grid tableGrid = gui.grid();
        for (int i = 0; i < 9; i++) {
            assertEquals(BlockType.PLANKS, tableGrid.get(i), "cell " + i + " filled");
        }
        assertEquals(1, inv.getCount(BlockType.PLANKS), "10th plank stays in inventory (grid full)");
        assertFalse(inv.isEmpty(0));
    }

    @Test
    void furnaceGuiHasNoGridAndItsOwnSlotRange() {
        Inventory inv = new Inventory();
        ContainerGui gui = new ContainerGui(ContainerGui.Kind.FURNACE, inv, new CraftingGrid(), new Furnace());
        assertFalse(gui.hasGrid());
        assertEquals(Inventory.SIZE + Furnace.SLOT_COUNT, gui.slotCount());
        assertTrue(gui.isFurnaceSlot(ContainerGui.CONTAINER_START));
        assertTrue(gui.isFurnaceSlot(ContainerGui.CONTAINER_START + 2));
        assertFalse(gui.isPlayerSlot(ContainerGui.CONTAINER_START));
        assertNull(gui.currentRecipe());
    }

    @Test
    void switchingGuiKeepsCursorState() {
        Inventory inv = new Inventory();
        Furnace f = new Furnace();
        InventoryController c = new InventoryController(new ContainerGui(ContainerGui.Kind.INVENTORY, inv, new CraftingGrid(), null));
        inv.setSlot(0, BlockType.SAND, 8);
        c.click(0, false, false);
        c.setGui(new ContainerGui(ContainerGui.Kind.FURNACE, inv, new CraftingGrid(), f));
        assertEquals(BlockType.SAND, c.cursorType());
        assertEquals(8, c.cursorCount());
        c.click(INPUT, false, false);
        assertEquals(BlockType.SAND, f.typeOf(Furnace.SLOT_INPUT));
        assertTrue(inv.isEmpty(0));
    }

    @Test
    void chestGuiRoutesClicksToTheChest() {
        Inventory inv = new Inventory();
        Chest chest = new Chest();
        ContainerGui gui = new ContainerGui(ContainerGui.Kind.CHEST, inv, new CraftingGrid(), chest);
        InventoryController c = new InventoryController(gui);

        assertEquals(Inventory.SIZE + Chest.SLOT_COUNT, gui.slotCount());
        assertTrue(gui.isContainerSlot(ContainerGui.CONTAINER_START));
        assertTrue(gui.isContainerSlot(ContainerGui.CONTAINER_START + Chest.SLOT_COUNT - 1));
        assertFalse(gui.isPlayerSlot(ContainerGui.CONTAINER_START));
        assertNull(gui.currentRecipe(), "chest has no crafting grid");

        inv.setSlot(0, BlockType.DIAMOND, 3);
        c.click(0, false, false);
        c.click(ContainerGui.CONTAINER_START + 5, false, false);
        assertEquals(BlockType.DIAMOND, chest.typeOf(5));
        assertEquals(3, chest.countOf(5));
        assertTrue(inv.isEmpty(0));
    }

    @Test
    void nonGridContainersReportNoGridDimensionsOrOutput() {
        ContainerGui gui = new ContainerGui(ContainerGui.Kind.CHEST,
                new Inventory(), new CraftingGrid(), new Chest());
        assertEquals(0, gui.gridWidth());
        assertEquals(-1, gui.outputSlotId());
    }

    @Test
    void chestScreenUsesLidSoundsInsteadOfTheUiBeep() {
        Inventory inv = new Inventory();
        ContainerGui chest = new ContainerGui(ContainerGui.Kind.CHEST, inv, new CraftingGrid(), new Chest());
        ContainerGui barrel = new ContainerGui(ContainerGui.Kind.CHEST, inv, new CraftingGrid(),
                new com.minecraftclone.world.Barrel());
        ContainerGui inventory = new ContainerGui(ContainerGui.Kind.INVENTORY, inv, new CraftingGrid(), null);
        ContainerGui furnace = new ContainerGui(ContainerGui.Kind.FURNACE, inv, new CraftingGrid(), new Furnace());

        assertEquals(SoundEvent.CHEST_OPEN, chest.openSound());
        assertEquals(SoundEvent.CHEST_CLOSE, chest.closeSound());
        assertEquals(SoundEvent.CHEST_OPEN, barrel.openSound(), "barrels reuse the chest screen");
        assertEquals(SoundEvent.UI_OPEN, inventory.openSound());
        assertEquals(SoundEvent.UI_CLOSE, inventory.closeSound());
        assertEquals(SoundEvent.UI_OPEN, furnace.openSound());
        assertEquals(SoundEvent.UI_CLOSE, furnace.closeSound());
    }

    @Test
    void shiftClickSendsAnythingIntoAChest() {
        Inventory inv = new Inventory();
        Chest chest = new Chest();
        ContainerGui gui = new ContainerGui(ContainerGui.Kind.CHEST, inv, new CraftingGrid(), chest);
        InventoryController c = new InventoryController(gui);

        inv.setSlot(0, BlockType.DIRT, 10);
        inv.setSlot(1, BlockType.IRON_INGOT, 4);
        c.click(0, false, true);
        c.click(1, false, true);
        assertEquals(10, chest.getCount(BlockType.DIRT));
        assertEquals(4, chest.getCount(BlockType.IRON_INGOT));
        assertTrue(inv.isEmpty(0));
        assertTrue(inv.isEmpty(1));
    }

    @Test
    void shiftClickMovesChestStackToInventory() {
        Inventory inv = new Inventory();
        Chest chest = new Chest();
        chest.setSlot(2, BlockType.APPLE, 7);
        ContainerGui gui = new ContainerGui(ContainerGui.Kind.CHEST, inv, new CraftingGrid(), chest);
        InventoryController c = new InventoryController(gui);
        c.click(ContainerGui.CONTAINER_START + 2, false, true);
        assertEquals(7, inv.getCount(BlockType.APPLE));
        assertTrue(chest.isEmpty(2));
    }

    @Test
    void doubleChestGuiPresents54SlotsAcrossBothHalves() {
        Inventory inv = new Inventory();
        Chest west = new Chest();
        Chest east = new Chest();
        JoinedStorage doubleChest = new JoinedStorage(west, east);
        ContainerGui gui = new ContainerGui(ContainerGui.Kind.CHEST, inv, new CraftingGrid(), doubleChest);
        InventoryController c = new InventoryController(gui);

        assertEquals(Inventory.SIZE + 54, gui.slotCount());
        assertTrue(gui.isContainerSlot(ContainerGui.CONTAINER_START + 53));
        assertFalse(gui.isContainerSlot(ContainerGui.CONTAINER_START + 54));

        // Slot 30 lands in the east half.
        inv.setSlot(0, BlockType.SAND, 3);
        c.click(0, false, false);
        c.click(ContainerGui.CONTAINER_START + 30, false, false);
        assertEquals(BlockType.SAND, east.typeOf(30 - Chest.SLOT_COUNT));
        assertEquals(3, east.countOf(30 - Chest.SLOT_COUNT));
        assertTrue(inv.isEmpty(0));

        // Shift-click from the inventory fills the double chest west-first.
        inv.setSlot(1, BlockType.DIRT, 4);
        InventoryController c2 = new InventoryController(gui);
        c2.click(1, false, true);
        assertEquals(4, west.getCount(BlockType.DIRT));
        assertTrue(inv.isEmpty(1));
    }

    @Test
    void quadChestGuiPresents108SlotsAcrossFourChests() {
        Inventory inv = new Inventory();
        Chest nw = new Chest(), ne = new Chest(), sw = new Chest(), se = new Chest();
        // A 2x2 chest composes four 27-slot chests, row-major (NW, NE, SW, SE).
        JoinedStorage quad = new JoinedStorage(
                new JoinedStorage(nw, ne),
                new JoinedStorage(sw, se));
        ContainerGui gui = new ContainerGui(ContainerGui.Kind.CHEST, inv, new CraftingGrid(), quad);
        InventoryController c = new InventoryController(gui);

        assertEquals(Inventory.SIZE + 108, gui.slotCount());
        assertTrue(gui.isContainerSlot(ContainerGui.CONTAINER_START + 107));
        assertFalse(gui.isContainerSlot(ContainerGui.CONTAINER_START + 108));

        // Slot 80 lands in the SE chest (row-major: NW 0-26, NE 27-53, SW 54-80... wait
        // 54-80 is 27 slots so SW=54..80, SE=81..107). Use 100 -> SE.
        inv.setSlot(0, BlockType.SAND, 3);
        c.click(0, false, false);
        c.click(ContainerGui.CONTAINER_START + 100, false, false);
        assertEquals(BlockType.SAND, se.typeOf(100 - 3 * Chest.SLOT_COUNT));
        assertEquals(3, se.countOf(100 - 3 * Chest.SLOT_COUNT));
        assertTrue(inv.isEmpty(0));
        assertTrue(nw.isEmpty(0) && ne.isEmpty(0) && sw.isEmpty(0), "only SE received the item");

        // Shift-click fills the quad west-first, north row before south.
        inv.setSlot(2, BlockType.APPLE, 9);
        c.click(2, false, true);
        assertEquals(9, nw.getCount(BlockType.APPLE), "NW fills first");
        assertTrue(ne.isEmpty(0) && sw.isEmpty(0) && se.isEmpty(0));
        assertTrue(inv.isEmpty(2));
    }
}
