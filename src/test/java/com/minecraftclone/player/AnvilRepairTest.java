package com.minecraftclone.player;

import com.minecraftclone.engine.gui.ContainerGui;
import com.minecraftclone.world.BlockType;
import com.minecraftclone.world.Mining;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the Anvil repair station: {@link AnvilGui},
 * {@link ContainerGui} slot routing, {@link InventoryController} click logic,
 * and the {@link ToolDurability#resetType} integration.
 */
class AnvilRepairTest {

    private Inventory inventory;
    private ToolDurability durability;

    @BeforeEach
    void setup() {
        inventory  = new Inventory();
        durability = new ToolDurability();
    }

    // -----------------------------------------------------------------------
    // AnvilGui unit tests
    // -----------------------------------------------------------------------

    @Test
    void repairMaterialOfWoodTool() {
        assertEquals(BlockType.PLANKS,    AnvilGui.repairMaterialOf(BlockType.WOOD_PICKAXE));
        assertEquals(BlockType.PLANKS,    AnvilGui.repairMaterialOf(BlockType.WOOD_AXE));
        assertEquals(BlockType.PLANKS,    AnvilGui.repairMaterialOf(BlockType.WOOD_SWORD));
    }

    @Test
    void repairMaterialOfStoneTool() {
        assertEquals(BlockType.STONE,     AnvilGui.repairMaterialOf(BlockType.STONE_PICKAXE));
    }

    @Test
    void repairMaterialOfIronTool() {
        assertEquals(BlockType.IRON_INGOT, AnvilGui.repairMaterialOf(BlockType.IRON_PICKAXE));
    }

    @Test
    void repairMaterialOfDiamondTool() {
        assertEquals(BlockType.DIAMOND,   AnvilGui.repairMaterialOf(BlockType.DIAMOND_PICKAXE));
    }

    @Test
    void repairMaterialOfNonToolIsNull() {
        assertNull(AnvilGui.repairMaterialOf(BlockType.STONE));
        assertNull(AnvilGui.repairMaterialOf(BlockType.DIRT));
        assertNull(AnvilGui.repairMaterialOf(null));
    }

    @Test
    void canRepairIsFalseWhenNoTool() {
        AnvilGui anvil = new AnvilGui();
        anvil.setMaterial(BlockType.IRON_INGOT, 5);
        assertFalse(anvil.canRepair(), "material alone should not enable repair");
    }

    @Test
    void canRepairIsFalseWhenNoMaterial() {
        AnvilGui anvil = new AnvilGui();
        anvil.setTool(BlockType.IRON_PICKAXE, 1);
        assertFalse(anvil.canRepair(), "tool without material should not enable repair");
    }

    @Test
    void canRepairIsFalseForWrongMaterial() {
        AnvilGui anvil = new AnvilGui();
        anvil.setTool(BlockType.IRON_PICKAXE, 1);
        anvil.setMaterial(BlockType.PLANKS, 1);   // wrong tier
        assertFalse(anvil.canRepair());
    }

    @Test
    void canRepairIsTrueForMatchingPair() {
        AnvilGui anvil = new AnvilGui();
        anvil.setTool(BlockType.IRON_PICKAXE, 1);
        anvil.setMaterial(BlockType.IRON_INGOT, 1);
        assertTrue(anvil.canRepair());
    }

    @Test
    void consumeReturnsTool() {
        AnvilGui anvil = new AnvilGui();
        anvil.setTool(BlockType.IRON_PICKAXE, 1);
        anvil.setMaterial(BlockType.IRON_INGOT, 3);
        BlockType result = anvil.consume();
        assertEquals(BlockType.IRON_PICKAXE, result);
    }

    @Test
    void consumeClearsToolSlot() {
        AnvilGui anvil = new AnvilGui();
        anvil.setTool(BlockType.IRON_PICKAXE, 1);
        anvil.setMaterial(BlockType.IRON_INGOT, 1);
        anvil.consume();
        assertNull(anvil.toolType());
        assertEquals(0, anvil.toolCount());
    }

    @Test
    void consumeDecrementsMaterialByOne() {
        AnvilGui anvil = new AnvilGui();
        anvil.setTool(BlockType.IRON_PICKAXE, 1);
        anvil.setMaterial(BlockType.IRON_INGOT, 3);
        anvil.consume();
        assertEquals(BlockType.IRON_INGOT, anvil.materialType());
        assertEquals(2, anvil.materialCount());
    }

    @Test
    void consumeClearsMaterialWhenLastOne() {
        AnvilGui anvil = new AnvilGui();
        anvil.setTool(BlockType.IRON_PICKAXE, 1);
        anvil.setMaterial(BlockType.IRON_INGOT, 1);
        anvil.consume();
        assertNull(anvil.materialType());
        assertEquals(0, anvil.materialCount());
    }

    @Test
    void consumeReturnsNullWhenCannotRepair() {
        AnvilGui anvil = new AnvilGui();
        // tool slot empty
        anvil.setMaterial(BlockType.IRON_INGOT, 1);
        assertNull(anvil.consume());
    }

    @Test
    void outputTypeIsNullWhenCannotRepair() {
        AnvilGui anvil = new AnvilGui();
        assertNull(anvil.outputType());
        anvil.setTool(BlockType.IRON_PICKAXE, 1);
        assertNull(anvil.outputType(), "no material yet → still null");
    }

    @Test
    void outputTypeMatchesToolWhenReady() {
        AnvilGui anvil = new AnvilGui();
        anvil.setTool(BlockType.IRON_PICKAXE, 1);
        anvil.setMaterial(BlockType.IRON_INGOT, 2);
        assertEquals(BlockType.IRON_PICKAXE, anvil.outputType());
    }

    // -----------------------------------------------------------------------
    // ToolDurability.resetType
    // -----------------------------------------------------------------------

    @Test
    void resetTypeClearsWear() {
        durability.wear(BlockType.IRON_PICKAXE, 100);
        int worn = durability.remaining(BlockType.IRON_PICKAXE);
        int max  = Mining.toolStats(BlockType.IRON_PICKAXE).maxUses();
        assertTrue(worn < max, "should have worn some durability");

        durability.resetType(BlockType.IRON_PICKAXE);
        assertEquals(max, durability.remaining(BlockType.IRON_PICKAXE), "after reset should be back to max");
    }

    @Test
    void resetTypeOnFreshToolIsNoOp() {
        // Should not throw even if the tool has never been used.
        assertDoesNotThrow(() -> durability.resetType(BlockType.IRON_PICKAXE));
        int max = Mining.toolStats(BlockType.IRON_PICKAXE).maxUses();
        assertEquals(max, durability.remaining(BlockType.IRON_PICKAXE));
    }

    // -----------------------------------------------------------------------
    // ContainerGui routing
    // -----------------------------------------------------------------------

    @Test
    void containerGuiAnvilSlotsRouteCorrectly() {
        AnvilGui anvil = new AnvilGui();
        ContainerGui gui = ContainerGui.forAnvil(inventory, anvil);

        // Tool slot
        gui.setSlot(ContainerGui.ANVIL_TOOL_SLOT, BlockType.IRON_PICKAXE, 1);
        assertEquals(BlockType.IRON_PICKAXE, gui.typeOf(ContainerGui.ANVIL_TOOL_SLOT));
        assertEquals(1, gui.countOf(ContainerGui.ANVIL_TOOL_SLOT));

        // Material slot
        gui.setSlot(ContainerGui.ANVIL_MATERIAL_SLOT, BlockType.IRON_INGOT, 5);
        assertEquals(BlockType.IRON_INGOT, gui.typeOf(ContainerGui.ANVIL_MATERIAL_SLOT));
        assertEquals(5, gui.countOf(ContainerGui.ANVIL_MATERIAL_SLOT));

        // Output slot reflects canRepair()
        assertTrue(anvil.canRepair());
        assertEquals(BlockType.IRON_PICKAXE, gui.typeOf(ContainerGui.ANVIL_OUTPUT_SLOT));
        assertEquals(1, gui.countOf(ContainerGui.ANVIL_OUTPUT_SLOT));
    }

    @Test
    void containerGuiAnvilSlotCount() {
        AnvilGui anvil = new AnvilGui();
        ContainerGui gui = ContainerGui.forAnvil(inventory, anvil);
        // 36 inventory + 3 anvil slots
        assertEquals(39, gui.slotCount());
    }

    // -----------------------------------------------------------------------
    // InventoryController click → repairAnvil
    // -----------------------------------------------------------------------

    @Test
    void clickOutputSlotRepairsToolAndMovesCursorWithoutDurability() {
        AnvilGui anvil = new AnvilGui();
        anvil.setTool(BlockType.IRON_PICKAXE, 1);
        anvil.setMaterial(BlockType.IRON_INGOT, 2);
        ContainerGui gui = ContainerGui.forAnvil(inventory, anvil);
        InventoryController ctrl = new InventoryController(gui);
        // No toolDurability wired — should still work.

        ctrl.click(ContainerGui.ANVIL_OUTPUT_SLOT, false, false);

        assertEquals(BlockType.IRON_PICKAXE, ctrl.cursorType());
        assertEquals(1, ctrl.cursorCount());
        assertNull(anvil.toolType(),          "tool slot cleared after repair");
        assertEquals(1, anvil.materialCount(), "one material consumed");
    }

    @Test
    void clickOutputSlotResetsToolDurabilityWhenInjected() {
        AnvilGui anvil = new AnvilGui();
        anvil.setTool(BlockType.IRON_PICKAXE, 1);
        anvil.setMaterial(BlockType.IRON_INGOT, 1);
        ContainerGui gui = ContainerGui.forAnvil(inventory, anvil);
        InventoryController ctrl = new InventoryController(gui);
        ctrl.setToolDurability(durability);

        // Pre-wear the pickaxe
        durability.wear(BlockType.IRON_PICKAXE, 50);
        int max = Mining.toolStats(BlockType.IRON_PICKAXE).maxUses();
        assertTrue(durability.remaining(BlockType.IRON_PICKAXE) < max);

        ctrl.click(ContainerGui.ANVIL_OUTPUT_SLOT, false, false);

        // Durability restored to max
        assertEquals(max, durability.remaining(BlockType.IRON_PICKAXE));
        // Repaired tool is on cursor
        assertEquals(BlockType.IRON_PICKAXE, ctrl.cursorType());
    }

    @Test
    void clickOutputSlotDoesNothingWhenCannotRepair() {
        AnvilGui anvil = new AnvilGui(); // empty
        ContainerGui gui = ContainerGui.forAnvil(inventory, anvil);
        InventoryController ctrl = new InventoryController(gui);

        ctrl.click(ContainerGui.ANVIL_OUTPUT_SLOT, false, false);
        assertTrue(ctrl.cursor().isEmpty(), "cursor should stay empty");
    }

    @Test
    void shiftClickOutputSlotDepositsIntoInventory() {
        AnvilGui anvil = new AnvilGui();
        anvil.setTool(BlockType.IRON_PICKAXE, 1);
        anvil.setMaterial(BlockType.IRON_INGOT, 1);
        ContainerGui gui = ContainerGui.forAnvil(inventory, anvil);
        InventoryController ctrl = new InventoryController(gui);
        ctrl.setToolDurability(durability);

        durability.wear(BlockType.IRON_PICKAXE, 100);

        ctrl.click(ContainerGui.ANVIL_OUTPUT_SLOT, false, true); // shift-click

        assertTrue(ctrl.cursor().isEmpty(), "cursor unchanged on shift-click");
        assertEquals(BlockType.IRON_PICKAXE, inventory.typeOf(0),
                "repaired tool deposited to inventory");
        assertEquals(Mining.toolStats(BlockType.IRON_PICKAXE).maxUses(),
                durability.remaining(BlockType.IRON_PICKAXE),
                "durability restored on shift-click repair");
    }

    @Test
    void shiftClickToolSlotReturnsToolToInventory() {
        AnvilGui anvil = new AnvilGui();
        anvil.setTool(BlockType.IRON_PICKAXE, 1);
        ContainerGui gui = ContainerGui.forAnvil(inventory, anvil);
        InventoryController ctrl = new InventoryController(gui);

        ctrl.click(ContainerGui.ANVIL_TOOL_SLOT, false, true); // shift-click

        assertNull(anvil.toolType(), "tool slot cleared");
        assertEquals(BlockType.IRON_PICKAXE, inventory.typeOf(0), "tool moved to inventory");
    }

    @Test
    void shiftClickMaterialSlotReturnsMaterialToInventory() {
        AnvilGui anvil = new AnvilGui();
        anvil.setMaterial(BlockType.IRON_INGOT, 3);
        ContainerGui gui = ContainerGui.forAnvil(inventory, anvil);
        InventoryController ctrl = new InventoryController(gui);

        ctrl.click(ContainerGui.ANVIL_MATERIAL_SLOT, false, true); // shift-click

        assertNull(anvil.materialType(), "material slot cleared");
        assertEquals(BlockType.IRON_INGOT, inventory.typeOf(0), "material moved to inventory");
        assertEquals(3, inventory.countOf(0));
    }

    @Test
    void normalClickPlacesToolIntoToolSlot() {
        inventory.setSlot(0, BlockType.IRON_PICKAXE, 1);
        AnvilGui anvil = new AnvilGui();
        ContainerGui gui = ContainerGui.forAnvil(inventory, anvil);
        InventoryController ctrl = new InventoryController(gui);

        ctrl.click(0, false, false); // pick up pickaxe
        ctrl.click(ContainerGui.ANVIL_TOOL_SLOT, false, false); // drop into tool slot

        assertEquals(BlockType.IRON_PICKAXE, anvil.toolType());
        assertTrue(ctrl.cursor().isEmpty());
    }

    @Test
    void woodToolRepairsMaterialIsPlanks() {
        AnvilGui anvil = new AnvilGui();
        anvil.setTool(BlockType.WOOD_AXE, 1);
        anvil.setMaterial(BlockType.PLANKS, 2);
        assertTrue(anvil.canRepair());

        ContainerGui gui = ContainerGui.forAnvil(inventory, anvil);
        InventoryController ctrl = new InventoryController(gui);
        ctrl.click(ContainerGui.ANVIL_OUTPUT_SLOT, false, false);

        assertEquals(BlockType.WOOD_AXE, ctrl.cursorType());
        assertEquals(1, anvil.materialCount());
    }

    // -----------------------------------------------------------------------
    // Regression: surplus tools and full-inventory output loss (CodeRabbit #82)
    // -----------------------------------------------------------------------

    /**
     * If two tools are stacked in the tool slot, canRepair() must be false.
     * Allowing the repair would clear the whole slot (both tools) but only
     * return one, silently deleting the extra.
     */
    @Test
    void canRepairIsFalseWhenToolCountExceedsOne() {
        AnvilGui anvil = new AnvilGui();
        anvil.setTool(BlockType.IRON_PICKAXE, 2);   // two pickaxes — not supported
        anvil.setMaterial(BlockType.IRON_INGOT, 1);
        assertFalse(anvil.canRepair(),
                "repair should be blocked when tool slot holds more than one item");
    }

    /**
     * Exactly one tool in the tool slot must still repair normally (the
     * toolCount == 1 guard should not break the happy path).
     */
    @Test
    void canRepairIsTrueWhenExactlyOneTool() {
        AnvilGui anvil = new AnvilGui();
        anvil.setTool(BlockType.IRON_PICKAXE, 1);
        anvil.setMaterial(BlockType.IRON_INGOT, 1);
        assertTrue(anvil.canRepair());
    }

    /**
     * Left-click on the output must not consume inputs when the cursor is
     * already held and the inventory is completely full — the repaired tool
     * would have nowhere to land and would be lost.
     */
    @Test
    void repairAnvilDoesNotConsumeWhenCursorOccupiedAndInventoryFull() {
        // Fill inventory
        for (int i = 0; i < Inventory.SIZE; i++) {
            inventory.setSlot(i, BlockType.STONE, 64);
        }
        assertTrue(inventory.isFull());

        AnvilGui anvil = new AnvilGui();
        anvil.setTool(BlockType.IRON_PICKAXE, 1);
        anvil.setMaterial(BlockType.IRON_INGOT, 3);
        ContainerGui gui = ContainerGui.forAnvil(inventory, anvil);
        InventoryController ctrl = new InventoryController(gui);
        ctrl.setToolDurability(durability);

        // Give cursor an item so it's occupied
        inventory.setSlot(0, BlockType.IRON_INGOT, 1);
        ContainerGui invGui = ContainerGui.forAnvil(inventory, anvil); // share same inventory
        InventoryController ctrl2 = new InventoryController(invGui);
        ctrl2.click(0, false, false); // pick up ingot to cursor
        // Re-fill slot 0 so inventory stays full
        inventory.setSlot(0, BlockType.STONE, 64);

        // Attempt repair — should be a no-op
        ctrl2.click(ContainerGui.ANVIL_OUTPUT_SLOT, false, false);

        // Inputs must be unchanged
        assertEquals(BlockType.IRON_PICKAXE, anvil.toolType(),   "tool slot must not be consumed");
        assertEquals(3,                      anvil.materialCount(), "material count must be unchanged");
    }

    /**
     * Shift-click on the output must not consume inputs when the inventory is
     * completely full — there is nowhere for the repaired tool to land.
     */
    @Test
    void repairAnvilToInventoryDoesNotConsumeWhenInventoryFull() {
        // Fill entire inventory
        for (int i = 0; i < Inventory.SIZE; i++) {
            inventory.setSlot(i, BlockType.STONE, 64);
        }
        assertTrue(inventory.isFull());

        AnvilGui anvil = new AnvilGui();
        anvil.setTool(BlockType.IRON_PICKAXE, 1);
        anvil.setMaterial(BlockType.IRON_INGOT, 2);
        ContainerGui gui = ContainerGui.forAnvil(inventory, anvil);
        InventoryController ctrl = new InventoryController(gui);
        ctrl.setToolDurability(durability);

        ctrl.click(ContainerGui.ANVIL_OUTPUT_SLOT, false, true); // shift-click

        // Inputs must be unchanged
        assertEquals(BlockType.IRON_PICKAXE, anvil.toolType(),   "tool slot must not be consumed");
        assertEquals(2,                      anvil.materialCount(), "material count must be unchanged");
    }
}
