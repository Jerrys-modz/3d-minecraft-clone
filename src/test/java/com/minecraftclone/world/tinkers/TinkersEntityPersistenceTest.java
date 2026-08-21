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
    void partBuilderEmptyLoadClearsExistingMaterial() throws Exception {
        PartBuilderEntity empty = new PartBuilderEntity();
        byte[] bytes = write(empty);

        PartBuilderEntity loaded = new PartBuilderEntity();
        loaded.gui().setMaterial(ItemStack.of(BlockType.IRON_INGOT, 3));
        read(loaded, bytes);

        assertTrue(loaded.gui().materialSlot().isEmpty());
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
    void toolStationStopsAtUnknownTrailingSlotKind() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeByte(1); // format version
        out.writeByte(ToolStationGui.INPUT_SLOTS + 2);
        for (int i = 0; i < ToolStationGui.INPUT_SLOTS; i++) out.writeByte(0);
        out.writeByte(99); // unknown first trailing slot; no safely-skippable payload follows

        ToolStationEntity loaded = new ToolStationEntity();
        assertDoesNotThrow(() -> read(loaded, bytes.toByteArray()));
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
