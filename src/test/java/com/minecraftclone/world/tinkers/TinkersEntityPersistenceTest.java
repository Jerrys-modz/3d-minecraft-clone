package com.minecraftclone.world.tinkers;

import com.minecraftclone.player.ItemStack;
import com.minecraftclone.world.BlockType;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Round-trip persistence tests for {@link PartBuilderEntity} and
 * {@link ToolStationEntity}: write to bytes, read back, verify state matches.
 */
class TinkersEntityPersistenceTest {

    // -----------------------------------------------------------------------
    // PartBuilderEntity
    // -----------------------------------------------------------------------

    @Test
    void partBuilderRoundTripWithMaterialAndShape() throws Exception {
        PartBuilderEntity entity = new PartBuilderEntity();
        entity.gui().setMaterial(ItemStack.of(BlockType.IRON_INGOT, 3));
        entity.gui().setSelectedShape(ToolPartType.PICK_HEAD);

        byte[] bytes = write(entity);

        PartBuilderEntity loaded = new PartBuilderEntity();
        read(loaded, bytes);

        assertEquals(ToolPartType.PICK_HEAD, loaded.gui().selectedShape());
        assertEquals(BlockType.IRON_INGOT,   loaded.gui().materialType());
        assertEquals(3,                      loaded.gui().materialCount());
    }

    @Test
    void partBuilderRoundTripEmptyState() throws Exception {
        PartBuilderEntity entity = new PartBuilderEntity();
        // Nothing set

        byte[] bytes = write(entity);

        PartBuilderEntity loaded = new PartBuilderEntity();
        read(loaded, bytes);

        assertNull(loaded.gui().selectedShape());
        assertTrue(loaded.gui().materialSlot().isEmpty());
    }

    @Test
    void partBuilderRoundTripShapeOnlyNoMaterial() throws Exception {
        PartBuilderEntity entity = new PartBuilderEntity();
        entity.gui().setSelectedShape(ToolPartType.SWORD_BLADE);

        byte[] bytes = write(entity);

        PartBuilderEntity loaded = new PartBuilderEntity();
        read(loaded, bytes);

        assertEquals(ToolPartType.SWORD_BLADE, loaded.gui().selectedShape());
        assertTrue(loaded.gui().materialSlot().isEmpty());
    }

    @Test
    void partBuilderEmptySaveClearsPopulatedSlot() throws Exception {
        PartBuilderEntity populated = new PartBuilderEntity();
        populated.gui().setMaterial(ItemStack.of(BlockType.IRON_INGOT, 5));
        populated.gui().setSelectedShape(ToolPartType.AXE_HEAD);
        assertFalse(populated.gui().materialSlot().isEmpty());

        PartBuilderEntity empty = new PartBuilderEntity();
        byte[] bytes = write(empty);
        read(populated, bytes);

        assertTrue(populated.gui().materialSlot().isEmpty(),
                "loading an empty save must not keep a leftover material stack");
        assertNull(populated.gui().selectedShape());
    }

    // -----------------------------------------------------------------------
    // ToolStationEntity
    // -----------------------------------------------------------------------

    @Test
    void toolStationRoundTripHeadAndRod() throws Exception {
        ToolStationEntity entity = new ToolStationEntity();
        entity.gui().setSlot(0, ItemStack.tinkersPart(new TinkersItem.Part(ToolPartType.AXE_HEAD, BlockType.DIAMOND)));
        entity.gui().setSlot(1, ItemStack.tinkersPart(new TinkersItem.Part(ToolPartType.TOOL_ROD, BlockType.PLANKS)));

        byte[] bytes = write(entity);

        ToolStationEntity loaded = new ToolStationEntity();
        read(loaded, bytes);

        TinkersItem.Part head = loaded.gui().headPart();
        assertNotNull(head);
        assertEquals(ToolPartType.AXE_HEAD, head.shape);
        assertEquals(BlockType.DIAMOND,     head.material);

        TinkersItem.Part rod = loaded.gui().rodPart();
        assertNotNull(rod);
        assertEquals(ToolPartType.TOOL_ROD, rod.shape);
        assertEquals(BlockType.PLANKS,      rod.material);
    }

    @Test
    void toolStationRoundTripWithBinding() throws Exception {
        ToolStationEntity entity = new ToolStationEntity();
        entity.gui().setSlot(0, ItemStack.tinkersPart(new TinkersItem.Part(ToolPartType.PICK_HEAD,  BlockType.IRON_INGOT)));
        entity.gui().setSlot(1, ItemStack.tinkersPart(new TinkersItem.Part(ToolPartType.TOOL_ROD,   BlockType.PLANKS)));
        entity.gui().setSlot(2, ItemStack.tinkersPart(new TinkersItem.Part(ToolPartType.BINDING,    BlockType.STONE)));

        byte[] bytes = write(entity);

        ToolStationEntity loaded = new ToolStationEntity();
        read(loaded, bytes);

        assertTrue(loaded.gui().canAssemble());
        TinkersItem.Part binding = loaded.gui().slot(2).tinkersPart();
        assertNotNull(binding);
        assertEquals(ToolPartType.BINDING, binding.shape);
        assertEquals(BlockType.STONE,      binding.material);
    }

    @Test
    void toolStationRoundTripEmptyState() throws Exception {
        ToolStationEntity entity = new ToolStationEntity();

        byte[] bytes = write(entity);

        ToolStationEntity loaded = new ToolStationEntity();
        read(loaded, bytes);

        assertFalse(loaded.gui().canAssemble());
        for (int i = 0; i < ToolStationGui.INPUT_SLOTS; i++) {
            assertTrue(loaded.gui().slot(i).isEmpty(), "Slot " + i + " should be empty");
        }
    }

    @Test
    void toolStationTrailingUnknownKindStopsWithoutCorruptingLoadedSlots() throws Exception {
        // version=1, slotCount=7 (> INPUT_SLOTS), five empty current slots, then
        // one empty trailing slot and an unknown kind. The reader must return
        // rather than treating leftover payload bytes as later kind bytes.
        byte[] bytes = new byte[] {
                1, 7,
                0, 0, 0, 0, 0,   // five current slots: KIND_EMPTY
                0,               // trailing KIND_EMPTY
                0x7f, (byte) 0xAA, (byte) 0xBB
        };
        ToolStationEntity loaded = new ToolStationEntity();
        loaded.gui().setSlot(0, ItemStack.tinkersPart(
                new TinkersItem.Part(ToolPartType.PICK_HEAD, BlockType.IRON_INGOT)));
        read(loaded, bytes);
        for (int i = 0; i < ToolStationGui.INPUT_SLOTS; i++) {
            assertTrue(loaded.gui().slot(i).isEmpty(), "Slot " + i + " should stay empty");
        }
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private static byte[] write(com.minecraftclone.world.BlockEntity entity) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        entity.writeTo(new DataOutputStream(baos));
        return baos.toByteArray();
    }

    private static void read(com.minecraftclone.world.BlockEntity entity, byte[] bytes) throws Exception {
        entity.readFrom(new DataInputStream(new ByteArrayInputStream(bytes)));
    }
}
