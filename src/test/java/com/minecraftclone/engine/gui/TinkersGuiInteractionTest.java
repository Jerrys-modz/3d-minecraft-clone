package com.minecraftclone.engine.gui;

import com.minecraftclone.player.CraftingGrid;
import com.minecraftclone.player.Inventory;
import com.minecraftclone.player.InventoryController;
import com.minecraftclone.player.ItemStack;
import com.minecraftclone.world.BlockType;
import com.minecraftclone.world.tinkers.PartBuilderGui;
import com.minecraftclone.world.tinkers.TinkersItem;
import com.minecraftclone.world.tinkers.ToolPartType;
import com.minecraftclone.world.tinkers.ToolStationGui;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies that the InventoryController correctly routes clicks through
 * {@link ContainerGui#PART_BUILDER} and {@link ContainerGui#TOOL_STATION} guis.
 */
class TinkersGuiInteractionTest {

    private Inventory inventory;

    @BeforeEach
    void setup() {
        inventory = new Inventory();
    }

    // -----------------------------------------------------------------------
    // Part Builder tests
    // -----------------------------------------------------------------------

    @Test
    void partBuilderMaterialSlotAcceptsVanillaMaterial() {
        PartBuilderGui pb = new PartBuilderGui();
        ContainerGui gui = ContainerGui.forPartBuilder(inventory, pb);
        InventoryController ctrl = new InventoryController(gui);

        // Put iron ingot in cursor, click material slot.
        inventory.setSlot(0, BlockType.IRON_INGOT, 4);
        ctrl.click(0, false, false); // pick up from inventory
        assertEquals(BlockType.IRON_INGOT, ctrl.cursorType());
        assertEquals(4, ctrl.cursorCount());

        ctrl.click(ContainerGui.PB_MATERIAL_SLOT, false, false); // place in material slot
        assertEquals(4, pb.materialCount());
        assertEquals(BlockType.IRON_INGOT, pb.materialType());
        assertTrue(ctrl.cursor().isEmpty());
    }

    @Test
    void partBuilderShapeButtonTogglesSelection() {
        PartBuilderGui pb = new PartBuilderGui();
        ContainerGui gui = ContainerGui.forPartBuilder(inventory, pb);
        InventoryController ctrl = new InventoryController(gui);

        assertNull(pb.selectedShape());

        // Click shape button for PICK_HEAD (index 0 = slot PB_SHAPE_SLOT_0).
        ctrl.click(ContainerGui.PB_SHAPE_SLOT_0, false, false);
        assertEquals(ToolPartType.PICK_HEAD, pb.selectedShape());

        // Click same button again → deselects.
        ctrl.click(ContainerGui.PB_SHAPE_SLOT_0, false, false);
        assertNull(pb.selectedShape());

        // Click AXE_HEAD button (index 1).
        ctrl.click(ContainerGui.PB_SHAPE_SLOT_0 + 1, false, false);
        assertEquals(ToolPartType.AXE_HEAD, pb.selectedShape());
    }

    @Test
    void partBuilderOutputSlotCraftsPartIntoCursor() {
        PartBuilderGui pb = new PartBuilderGui();
        pb.setMaterial(ItemStack.of(BlockType.IRON_INGOT, 3));
        pb.setSelectedShape(ToolPartType.PICK_HEAD);
        ContainerGui gui = ContainerGui.forPartBuilder(inventory, pb);
        InventoryController ctrl = new InventoryController(gui);

        assertTrue(pb.canCraft());
        ctrl.click(ContainerGui.PB_OUTPUT_SLOT, false, false);

        // Cursor now holds the crafted part.
        assertFalse(ctrl.cursor().isEmpty());
        assertTrue(ctrl.cursor().isTinkersPart());
        TinkersItem.Part part = ctrl.cursor().tinkersPart();
        assertNotNull(part);
        assertEquals(ToolPartType.PICK_HEAD, part.shape);
        assertEquals(BlockType.IRON_INGOT, part.material);

        // One material consumed.
        assertEquals(2, pb.materialCount());
    }

    @Test
    void partBuilderShiftClickOutputCraftsToInventory() {
        PartBuilderGui pb = new PartBuilderGui();
        pb.setMaterial(ItemStack.of(BlockType.DIAMOND, 2));
        pb.setSelectedShape(ToolPartType.AXE_HEAD);
        ContainerGui gui = ContainerGui.forPartBuilder(inventory, pb);
        InventoryController ctrl = new InventoryController(gui);

        ctrl.click(ContainerGui.PB_OUTPUT_SLOT, false, true); // shift-click

        // Cursor stays empty; part lands in inventory.
        assertTrue(ctrl.cursor().isEmpty());
        boolean found = false;
        for (int i = 0; i < Inventory.SIZE; i++) {
            if (inventory.stackOf(i).isTinkersPart()) {
                found = true;
                TinkersItem.Part p = inventory.stackOf(i).tinkersPart();
                assertNotNull(p);
                assertEquals(ToolPartType.AXE_HEAD, p.shape);
                assertEquals(BlockType.DIAMOND, p.material);
                break;
            }
        }
        assertTrue(found, "Crafted part should have landed in the inventory");
        assertEquals(1, pb.materialCount()); // 1 consumed
    }

    @Test
    void partBuilderShiftClickMaterialReturnsMaterialToInventory() {
        PartBuilderGui pb = new PartBuilderGui();
        pb.setMaterial(ItemStack.of(BlockType.GOLD_INGOT, 5));
        ContainerGui gui = ContainerGui.forPartBuilder(inventory, pb);
        InventoryController ctrl = new InventoryController(gui);

        ctrl.click(ContainerGui.PB_MATERIAL_SLOT, false, true); // shift-click material

        assertEquals(0, pb.materialCount());
        assertEquals(5, inventory.getCount(BlockType.GOLD_INGOT));
    }

    // -----------------------------------------------------------------------
    // Tool Station tests
    // -----------------------------------------------------------------------

    @Test
    void toolStationInputSlotsAcceptParts() {
        ToolStationGui ts = new ToolStationGui();
        ContainerGui gui = ContainerGui.forToolStation(inventory, ts);
        InventoryController ctrl = new InventoryController(gui);

        // Put a Tinkers part in cursor.
        ItemStack headPart = ItemStack.tinkersPart(new TinkersItem.Part(ToolPartType.PICK_HEAD, BlockType.IRON_INGOT));
        // Simulate cursor having the part by putting it in inventory first then clicking.
        inventory.addStack(headPart);
        int headSlot = -1;
        for (int i = 0; i < Inventory.SIZE; i++) {
            if (inventory.stackOf(i).isTinkersPart()) { headSlot = i; break; }
        }
        assertTrue(headSlot >= 0);
        ctrl.click(headSlot, false, false); // pick up from inventory
        assertTrue(ctrl.cursor().isTinkersPart());

        // Place into Tool Station input slot 0.
        ctrl.click(ContainerGui.TS_SLOT_0, false, false);
        assertTrue(ts.slot(0).isTinkersPart());
        TinkersItem.Part placed = ts.slot(0).tinkersPart();
        assertNotNull(placed);
        assertEquals(ToolPartType.PICK_HEAD, placed.shape);
    }

    @Test
    void toolStationAssemblesToolOnOutputClick() {
        ToolStationGui ts = new ToolStationGui();
        ts.setSlot(0, ItemStack.tinkersPart(new TinkersItem.Part(ToolPartType.PICK_HEAD, BlockType.IRON_INGOT)));
        ts.setSlot(1, ItemStack.tinkersPart(new TinkersItem.Part(ToolPartType.TOOL_ROD, BlockType.PLANKS)));
        ContainerGui gui = ContainerGui.forToolStation(inventory, ts);
        InventoryController ctrl = new InventoryController(gui);

        assertTrue(ts.canAssemble());
        ctrl.click(ContainerGui.TS_OUTPUT_SLOT, false, false);

        assertFalse(ctrl.cursor().isEmpty());
        assertTrue(ctrl.cursor().isTinkersTool());
        TinkersItem.Tool tool = ctrl.cursor().tinkersTool();
        assertNotNull(tool);
        assertEquals(com.minecraftclone.world.Mining.ToolKind.PICKAXE, tool.kind);
    }

    @Test
    void toolStationShiftClickOutputAssemblesToInventory() {
        ToolStationGui ts = new ToolStationGui();
        ts.setSlot(0, ItemStack.tinkersPart(new TinkersItem.Part(ToolPartType.SWORD_BLADE, BlockType.DIAMOND)));
        ts.setSlot(1, ItemStack.tinkersPart(new TinkersItem.Part(ToolPartType.TOOL_ROD, BlockType.PLANKS)));
        ContainerGui gui = ContainerGui.forToolStation(inventory, ts);
        InventoryController ctrl = new InventoryController(gui);

        ctrl.click(ContainerGui.TS_OUTPUT_SLOT, false, true); // shift-click

        assertTrue(ctrl.cursor().isEmpty());
        boolean found = false;
        for (int i = 0; i < Inventory.SIZE; i++) {
            if (inventory.stackOf(i).isTinkersTool()) {
                found = true;
                break;
            }
        }
        assertTrue(found, "Assembled tool should land in inventory");
    }

    @Test
    void toolStationShiftClickInputReturnsPartToInventory() {
        ToolStationGui ts = new ToolStationGui();
        ItemStack rod = ItemStack.tinkersPart(new TinkersItem.Part(ToolPartType.TOOL_ROD, BlockType.PLANKS));
        ts.setSlot(1, rod);
        ContainerGui gui = ContainerGui.forToolStation(inventory, ts);
        InventoryController ctrl = new InventoryController(gui);

        ctrl.click(ContainerGui.TS_SLOT_0 + 1, false, true); // shift-click slot 1

        assertTrue(ts.slot(1).isEmpty());
        boolean found = false;
        for (int i = 0; i < Inventory.SIZE; i++) {
            if (inventory.stackOf(i).isTinkersPart()) { found = true; break; }
        }
        assertTrue(found, "Part should have returned to inventory");
    }

    @Test
    void inventoryShiftClickTinkersMaterialSendsToPartBuilder() {
        PartBuilderGui pb = new PartBuilderGui();
        ContainerGui gui = ContainerGui.forPartBuilder(inventory, pb);
        InventoryController ctrl = new InventoryController(gui);

        inventory.setSlot(0, BlockType.STONE, 16);
        ctrl.click(0, false, true); // shift-click stone from slot 0

        assertEquals(16, pb.materialCount());
        assertEquals(BlockType.STONE, pb.materialType());
    }

    @Test
    void inventoryShiftClickTinkersPartSendsToToolStation() {
        ToolStationGui ts = new ToolStationGui();
        ContainerGui gui = ContainerGui.forToolStation(inventory, ts);
        InventoryController ctrl = new InventoryController(gui);

        ItemStack part = ItemStack.tinkersPart(new TinkersItem.Part(ToolPartType.PICK_HEAD, BlockType.DIAMOND));
        inventory.addStack(part);
        int slot = -1;
        for (int i = 0; i < Inventory.SIZE; i++) {
            if (inventory.stackOf(i).isTinkersPart()) { slot = i; break; }
        }
        assertTrue(slot >= 0);

        ctrl.click(slot, false, true); // shift-click → Tool Station
        assertTrue(inventory.stackOf(slot).isEmpty(), "Inventory slot should be emptied");
        assertTrue(ts.slot(0).isTinkersPart(), "Part should be in tool station slot 0");
    }

    @Test
    void containerGuiSlotCountsAreCorrect() {
        ContainerGui pbGui = ContainerGui.forPartBuilder(inventory,  new PartBuilderGui());
        // 36 player + 1 material + 1 output + 8 shape buttons = 46
        assertEquals(Inventory.SIZE + 2 + ContainerGui.PB_SHAPE_COUNT, pbGui.slotCount());

        ContainerGui tsGui = ContainerGui.forToolStation(inventory, new ToolStationGui());
        // 36 player + 5 inputs + 1 output = 42
        assertEquals(Inventory.SIZE + ToolStationGui.INPUT_SLOTS + 1, tsGui.slotCount());
    }
}
