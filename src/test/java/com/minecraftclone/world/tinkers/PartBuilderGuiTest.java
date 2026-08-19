package com.minecraftclone.world.tinkers;

import com.minecraftclone.player.ItemStack;
import com.minecraftclone.world.BlockType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link PartBuilderGui} — the pure-model state machine that
 * drives Part Builder crafting.
 */
class PartBuilderGuiTest {

    // -----------------------------------------------------------------------
    // Validation guards
    // -----------------------------------------------------------------------

    @Test
    void cannotCraftWithoutMaterial() {
        PartBuilderGui gui = new PartBuilderGui();
        gui.setSelectedShape(ToolPartType.PICK_HEAD);
        assertFalse(gui.canCraft(), "Need a material in the slot");
    }

    @Test
    void cannotCraftWithoutShape() {
        PartBuilderGui gui = new PartBuilderGui();
        gui.setMaterial(ItemStack.of(BlockType.IRON_INGOT, 1));
        assertFalse(gui.canCraft(), "Need a shape selected");
    }

    @Test
    void cannotCraftWithUnregisteredMaterial() {
        PartBuilderGui gui = new PartBuilderGui();
        gui.setMaterial(ItemStack.of(BlockType.DIRT, 64));
        gui.setSelectedShape(ToolPartType.PICK_HEAD);
        // DIRT isn't registered in TinkersRegistry, so the slot should be empty
        assertTrue(gui.materialSlot().isEmpty(), "Unregistered material should be rejected");
        assertFalse(gui.canCraft());
    }

    // -----------------------------------------------------------------------
    // Happy-path crafting
    // -----------------------------------------------------------------------

    @Test
    void craftIronPickHead() {
        PartBuilderGui gui = new PartBuilderGui();
        gui.setMaterial(ItemStack.of(BlockType.IRON_INGOT, 3));
        gui.setSelectedShape(ToolPartType.PICK_HEAD);
        assertTrue(gui.canCraft());

        ItemStack output = gui.craft();
        assertTrue(output.isTinkersPart(), "Output must be a Tinkers part");
        TinkersItem.Part part = output.tinkersPart();
        assertNotNull(part);
        assertEquals(ToolPartType.PICK_HEAD, part.shape);
        assertEquals(BlockType.IRON_INGOT,   part.material);
    }

    @Test
    void craftConsumesSingleMaterialItem() {
        PartBuilderGui gui = new PartBuilderGui();
        gui.setMaterial(ItemStack.of(BlockType.IRON_INGOT, 3));
        gui.setSelectedShape(ToolPartType.PICK_HEAD);
        gui.craft();
        assertEquals(2, gui.materialCount(), "Exactly one material should be consumed");
    }

    @Test
    void craftClearsMaterialWhenLastConsumed() {
        PartBuilderGui gui = new PartBuilderGui();
        gui.setMaterial(ItemStack.of(BlockType.STONE, 1));
        gui.setSelectedShape(ToolPartType.SHOVEL_HEAD);
        gui.craft();
        assertTrue(gui.materialSlot().isEmpty(), "Slot must be empty after last item consumed");
        assertFalse(gui.canCraft(), "Should not be craftable with empty slot");
    }

    @Test
    void craftDoesNotClearShapeSelection() {
        // Shape stays selected across multiple crafts (material is the consumable)
        PartBuilderGui gui = new PartBuilderGui();
        gui.setMaterial(ItemStack.of(BlockType.DIAMOND, 2));
        gui.setSelectedShape(ToolPartType.SWORD_BLADE);
        gui.craft();
        assertEquals(ToolPartType.SWORD_BLADE, gui.selectedShape(),
                "Shape selection persists after craft");
    }

    @Test
    void craftWhenNotPossibleReturnsEmpty() {
        PartBuilderGui gui = new PartBuilderGui();
        // No material, no shape
        ItemStack result = gui.craft();
        assertTrue(result.isEmpty(), "craft() must return EMPTY when canCraft() is false");
    }

    // -----------------------------------------------------------------------
    // Part output for every head shape
    // -----------------------------------------------------------------------

    @Test
    void craftProducesCorrectShapeForAllHeads() {
        for (ToolPartType shape : ToolPartType.values()) {
            PartBuilderGui gui = new PartBuilderGui();
            gui.setMaterial(ItemStack.of(BlockType.IRON_INGOT, 1));
            gui.setSelectedShape(shape);
            assertTrue(gui.canCraft(), shape + " should be craftable");
            TinkersItem.Part part = gui.craft().tinkersPart();
            assertNotNull(part, shape + ": part must not be null");
            assertEquals(shape, part.shape, shape + ": output shape mismatch");
        }
    }

    // -----------------------------------------------------------------------
    // All registered materials accepted
    // -----------------------------------------------------------------------

    @Test
    void allRegisteredMaterialsAreAccepted() {
        for (BlockType mat : TinkersRegistry.materials()) {
            // Use a fresh gui per material so a previously-accepted slot cannot
            // mask a rejection of the current material (setMaterial rejects
            // unregistered types but leaves the slot unchanged if it is already set).
            PartBuilderGui gui = new PartBuilderGui();
            gui.setMaterial(ItemStack.of(mat, 1));
            assertFalse(gui.materialSlot().isEmpty(),
                    "Expected " + mat.name() + " to be accepted as a Tinkers material");
        }
    }

    // -----------------------------------------------------------------------
    // toggleShape
    // -----------------------------------------------------------------------

    @Test
    void toggleShapeSelectsUnselected() {
        PartBuilderGui gui = new PartBuilderGui();
        gui.toggleShape(ToolPartType.AXE_HEAD);
        assertEquals(ToolPartType.AXE_HEAD, gui.selectedShape());
    }

    @Test
    void toggleShapeDeselectsAlreadySelected() {
        PartBuilderGui gui = new PartBuilderGui();
        gui.toggleShape(ToolPartType.AXE_HEAD);
        gui.toggleShape(ToolPartType.AXE_HEAD);
        assertNull(gui.selectedShape(), "Toggle on same shape should deselect");
    }

    @Test
    void toggleShapeSwitchesFromOneToAnother() {
        PartBuilderGui gui = new PartBuilderGui();
        gui.toggleShape(ToolPartType.PICK_HEAD);
        gui.toggleShape(ToolPartType.AXE_HEAD);
        assertEquals(ToolPartType.AXE_HEAD, gui.selectedShape());
    }

    // -----------------------------------------------------------------------
    // reset
    // -----------------------------------------------------------------------

    @Test
    void resetClearsBothSlotAndShape() {
        PartBuilderGui gui = new PartBuilderGui();
        gui.setMaterial(ItemStack.of(BlockType.DIAMOND, 5));
        gui.setSelectedShape(ToolPartType.PICK_HEAD);
        gui.reset();
        assertTrue(gui.materialSlot().isEmpty());
        assertNull(gui.selectedShape());
    }
}
