package com.minecraftclone.world.tinkers;

import com.minecraftclone.player.ItemStack;
import com.minecraftclone.world.BlockType;
import com.minecraftclone.world.Mining.ToolKind;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link ToolStationGui} — the pure-model state machine that
 * drives Tool Station assembly.
 */
class ToolStationGuiTest {

    // -----------------------------------------------------------------------
    // Test helpers
    // -----------------------------------------------------------------------

    private static ItemStack part(ToolPartType shape, BlockType material) {
        return ItemStack.tinkersPart(new TinkersItem.Part(shape, material));
    }

    private static ItemStack ironHead(ToolPartType shape) {
        return part(shape, BlockType.IRON_INGOT);
    }

    private static ItemStack woodRod() {
        return part(ToolPartType.TOOL_ROD, BlockType.PLANKS);
    }

    private static ItemStack stoneBinding() {
        return part(ToolPartType.BINDING, BlockType.STONE);
    }

    // -----------------------------------------------------------------------
    // Validation guards
    // -----------------------------------------------------------------------

    @Test
    void cannotAssembleWithEmptyStation() {
        ToolStationGui gui = new ToolStationGui();
        assertFalse(gui.canAssemble());
    }

    @Test
    void cannotAssembleWithoutHead() {
        ToolStationGui gui = new ToolStationGui();
        gui.setSlot(1, woodRod());
        assertFalse(gui.canAssemble(), "Rod without head cannot assemble");
    }

    @Test
    void cannotAssembleWithoutRod() {
        ToolStationGui gui = new ToolStationGui();
        gui.setSlot(0, ironHead(ToolPartType.PICK_HEAD));
        assertFalse(gui.canAssemble(), "Head without rod cannot assemble");
    }

    @Test
    void headSlotMustContainAHead() {
        ToolStationGui gui = new ToolStationGui();
        // Placing a TOOL_ROD in the head slot should not count as a valid head
        gui.setSlot(0, woodRod());
        gui.setSlot(1, woodRod());
        assertFalse(gui.canAssemble(), "Rod in head slot must not satisfy head requirement");
    }

    @Test
    void rodSlotMustContainARod() {
        ToolStationGui gui = new ToolStationGui();
        gui.setSlot(0, ironHead(ToolPartType.PICK_HEAD));
        // Placing a head part in the rod slot should not count as a valid rod
        gui.setSlot(1, ironHead(ToolPartType.PICK_HEAD));
        assertFalse(gui.canAssemble(), "Head in rod slot must not satisfy rod requirement");
    }

    // -----------------------------------------------------------------------
    // Basic assembly — one head + one rod
    // -----------------------------------------------------------------------

    @Test
    void assemblesPickaxeFromIronHeadAndWoodRod() {
        ToolStationGui gui = new ToolStationGui();
        gui.setSlot(0, ironHead(ToolPartType.PICK_HEAD));
        gui.setSlot(1, woodRod());
        assertTrue(gui.canAssemble());

        ItemStack output = gui.currentOutput();
        assertTrue(output.isTinkersTool(), "Output must be a Tinkers tool");
        TinkersItem.Tool tool = output.tinkersTool();
        assertNotNull(tool);
        assertEquals(ToolKind.PICKAXE, tool.kind);
        assertEquals(BlockType.IRON_INGOT, tool.headMaterial());
    }

    @Test
    void assemblesAxeFromIronHeadAndWoodRod() {
        ToolStationGui gui = new ToolStationGui();
        gui.setSlot(0, ironHead(ToolPartType.AXE_HEAD));
        gui.setSlot(1, woodRod());
        ItemStack output = gui.assemble();
        assertTrue(output.isTinkersTool());
        assertEquals(ToolKind.AXE, output.tinkersTool().kind);
    }

    @Test
    void assemblesSwordFromBladeAndRod() {
        ToolStationGui gui = new ToolStationGui();
        gui.setSlot(0, ironHead(ToolPartType.SWORD_BLADE));
        gui.setSlot(1, woodRod());
        ItemStack output = gui.assemble();
        assertTrue(output.isTinkersTool());
        assertEquals(ToolKind.SWORD, output.tinkersTool().kind);
    }

    @Test
    void assemblesShovelFromShovelHeadAndRod() {
        ToolStationGui gui = new ToolStationGui();
        gui.setSlot(0, ironHead(ToolPartType.SHOVEL_HEAD));
        gui.setSlot(1, woodRod());
        ItemStack output = gui.assemble();
        assertTrue(output.isTinkersTool());
        assertEquals(ToolKind.SHOVEL, output.tinkersTool().kind);
    }

    // -----------------------------------------------------------------------
    // Tough Rod as alternative rod
    // -----------------------------------------------------------------------

    @Test
    void toughRodIsValidInRodSlot() {
        ToolStationGui gui = new ToolStationGui();
        gui.setSlot(0, ironHead(ToolPartType.PICK_HEAD));
        gui.setSlot(1, part(ToolPartType.TOUGH_ROD, BlockType.IRON_INGOT));
        assertTrue(gui.canAssemble(), "TOUGH_ROD must satisfy the rod-slot requirement");
    }

    // -----------------------------------------------------------------------
    // Tool properties
    // -----------------------------------------------------------------------

    @Test
    void toolDurabilityBasedOnHeadMaterial() {
        ToolStationGui gui = new ToolStationGui();
        gui.setSlot(0, ironHead(ToolPartType.PICK_HEAD));
        gui.setSlot(1, woodRod());
        TinkersItem.Tool tool = gui.currentOutput().tinkersTool();
        // Iron head base durability is 500; rod may add a small bonus.
        assertTrue(tool.maxDurability >= 500,
                "Iron pickaxe should have at least 500 durability");
    }

    @Test
    void toolMiningTierMatchesHeadMaterial() {
        ToolStationGui gui = new ToolStationGui();
        gui.setSlot(0, ironHead(ToolPartType.PICK_HEAD));
        gui.setSlot(1, woodRod());
        TinkersItem.Tool tool = gui.currentOutput().tinkersTool();
        // Iron tier = 3 (matches TinkersRegistry.statsFor(IRON_INGOT).miningTier())
        assertEquals(3, tool.miningTier());
    }

    @Test
    void toolMiningSpeedMatchesHeadMaterial() {
        ToolStationGui gui = new ToolStationGui();
        gui.setSlot(0, ironHead(ToolPartType.PICK_HEAD));
        gui.setSlot(1, woodRod());
        TinkersItem.Tool tool = gui.currentOutput().tinkersTool();
        assertEquals(3.0f, tool.miningSpeed(), 0.001f);
    }

    // -----------------------------------------------------------------------
    // Extra slots (binding etc.)
    // -----------------------------------------------------------------------

    @Test
    void bindingInExtraSlotIncludedAsLayer() {
        ToolStationGui gui = new ToolStationGui();
        gui.setSlot(0, ironHead(ToolPartType.PICK_HEAD));
        gui.setSlot(1, woodRod());
        gui.setSlot(2, stoneBinding());
        assertTrue(gui.canAssemble());

        TinkersItem.Tool tool = gui.assemble().tinkersTool();
        assertEquals(3, tool.layers.size(), "Head + rod + binding = 3 layers");
        assertEquals(ToolPartType.BINDING, tool.layers.get(2).shape());
    }

    @Test
    void multipleExtraSlotsAllIncluded() {
        ToolStationGui gui = new ToolStationGui();
        gui.setSlot(0, ironHead(ToolPartType.PICK_HEAD));
        gui.setSlot(1, woodRod());
        gui.setSlot(2, stoneBinding());
        gui.setSlot(3, stoneBinding());
        TinkersItem.Tool tool = gui.assemble().tinkersTool();
        assertEquals(4, tool.layers.size());
    }

    // -----------------------------------------------------------------------
    // Assembly consumption
    // -----------------------------------------------------------------------

    @Test
    void assembleConsumesAllInputs() {
        ToolStationGui gui = new ToolStationGui();
        gui.setSlot(0, ironHead(ToolPartType.PICK_HEAD));
        gui.setSlot(1, woodRod());
        gui.assemble();
        assertTrue(gui.slot(0).isEmpty(), "Head slot must be empty after assembly");
        assertTrue(gui.slot(1).isEmpty(), "Rod slot must be empty after assembly");
    }

    @Test
    void assembleWhenNotPossibleReturnsEmpty() {
        ToolStationGui gui = new ToolStationGui();
        ItemStack result = gui.assemble();
        assertTrue(result.isEmpty(), "assemble() must return EMPTY when canAssemble() is false");
    }

    @Test
    void currentOutputDoesNotConsume() {
        ToolStationGui gui = new ToolStationGui();
        gui.setSlot(0, ironHead(ToolPartType.PICK_HEAD));
        gui.setSlot(1, woodRod());
        gui.currentOutput();
        gui.currentOutput(); // second call should return same result
        assertFalse(gui.slot(0).isEmpty(), "currentOutput() must not consume inputs");
    }

    // -----------------------------------------------------------------------
    // clearAll
    // -----------------------------------------------------------------------

    @Test
    void clearAllEmptiesAllSlots() {
        ToolStationGui gui = new ToolStationGui();
        gui.setSlot(0, ironHead(ToolPartType.PICK_HEAD));
        gui.setSlot(1, woodRod());
        gui.setSlot(2, stoneBinding());
        gui.clearAll();
        for (int i = 0; i < ToolStationGui.INPUT_SLOTS; i++) {
            assertTrue(gui.slot(i).isEmpty(), "Slot " + i + " should be empty after clearAll()");
        }
    }

    // -----------------------------------------------------------------------
    // Reject non-Tinkers items
    // -----------------------------------------------------------------------

    @Test
    void vanillaItemStackRejectedInInputSlot() {
        ToolStationGui gui = new ToolStationGui();
        gui.setSlot(0, ItemStack.of(BlockType.IRON_INGOT, 1));
        assertTrue(gui.slot(0).isEmpty(), "Plain vanilla ItemStack must not be accepted");
    }
}
