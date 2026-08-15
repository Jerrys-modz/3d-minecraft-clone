package com.minecraftclone.engine.gui;

import com.minecraftclone.player.CraftingGrid;
import com.minecraftclone.player.Inventory;
import com.minecraftclone.player.InventoryController;
import com.minecraftclone.world.BlockType;
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
        grid.set(0, BlockType.PLANKS);
        grid.set(3, BlockType.PLANKS);
        assertEquals(BlockType.STICK, gui.currentRecipe().output());
        c.click(ContainerGui.OUTPUT_SLOT, false, false);
        assertEquals(BlockType.STICK, c.cursorType());
        assertEquals(4, c.cursorCount());
        assertTrue(grid.isEmpty());
    }

    @Test
    void shiftClickFillsEmptyCraftingTableCellsFromInventory() {
        Inventory inv = new Inventory();
        CraftingGrid grid = new CraftingGrid();
        ContainerGui gui = new ContainerGui(ContainerGui.Kind.CRAFTING_TABLE, inv, grid, null);
        InventoryController c = new InventoryController(gui);
        inv.setSlot(0, BlockType.PLANKS, 5);
        c.click(0, false, true);
        for (int i = 0; i < 5; i++) {
            assertEquals(BlockType.PLANKS, grid.get(i), "cell " + i + " filled");
        }
        assertEquals(0, inv.getCount(BlockType.PLANKS), "stack moved entirely into the grid");
        assertTrue(inv.isEmpty(0));
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
}
