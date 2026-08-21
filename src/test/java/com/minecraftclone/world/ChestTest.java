package com.minecraftclone.world;

import com.minecraftclone.player.ItemStack;
import com.minecraftclone.world.Mining.ToolKind;
import com.minecraftclone.world.tinkers.TinkersItem;
import com.minecraftclone.world.tinkers.ToolPartType;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChestTest {

    @Test
    void chestStartsEmptyWithTwentySevenSlots() {
        Chest c = new Chest();
        assertEquals(Chest.SLOT_COUNT, c.size());
        assertEquals(27, c.size());
        for (int i = 0; i < c.size(); i++) {
            assertTrue(c.isEmpty(i));
        }
        assertTrue(c.getCount(BlockType.DIRT) == 0);
    }

    @Test
    void addTopsUpStacksThenFillsEmptySlots() {
        Chest c = new Chest();
        c.add(BlockType.DIRT, 70);
        // 64 in one slot, 6 in the next.
        assertEquals(64, c.countOf(0));
        assertEquals(6, c.countOf(1));
        assertEquals(70, c.getCount(BlockType.DIRT));

        // Topping up merges into the existing stack first.
        int leftover = c.add(BlockType.DIRT, 60);
        assertEquals(0, leftover);
        assertEquals(64, c.countOf(1), "remaining dirt tops up slot 1 to 64");
        assertEquals(70 + 60, c.getCount(BlockType.DIRT));
    }

    @Test
    void addReturnsLeftoverWhenFull() {
        Chest c = new Chest();
        int leftover = 0;
        for (int i = 0; i < 27; i++) {
            leftover = c.add(BlockType.STONE, 64);
        }
        assertEquals(0, leftover, "27 stacks of 64 exactly fill the chest");
        assertEquals(3, c.add(BlockType.STONE, 3), "no room for more - all 3 left over");
    }

    @Test
    void setSlotClearsOnNullOrNonPositiveCount() {
        Chest c = new Chest();
        c.setSlot(5, BlockType.STONE, 10);
        assertEquals(BlockType.STONE, c.typeOf(5));
        assertEquals(10, c.countOf(5));
        c.setSlot(5, null, 0);
        assertTrue(c.isEmpty(5));
        c.setSlot(5, BlockType.STONE, 0);
        assertTrue(c.isEmpty(5));
        assertNull(c.typeOf(-1), "out of range reads as empty");
        assertNull(c.typeOf(27), "out of range reads as empty");
        c.setSlot(30, BlockType.STONE, 5);
        assertTrue(c.isEmpty(30), "out of range writes are ignored");
    }

    @Test
    void serializationRoundTripPreservesContents() throws Exception {
        Chest c = new Chest();
        c.setSlot(0, BlockType.DIAMOND, 3);
        c.setSlot(10, BlockType.WOOD_LOG, 64);
        c.setSlot(26, BlockType.APPLE, 8);

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        c.writeTo(new DataOutputStream(bos));
        Chest loaded = new Chest();
        loaded.readFrom(new DataInputStream(new ByteArrayInputStream(bos.toByteArray())));

        assertEquals(BlockType.DIAMOND, loaded.typeOf(0));
        assertEquals(3, loaded.countOf(0));
        assertEquals(BlockType.WOOD_LOG, loaded.typeOf(10));
        assertEquals(64, loaded.countOf(10));
        assertEquals(BlockType.APPLE, loaded.typeOf(26));
        assertEquals(8, loaded.countOf(26));
        for (int i = 0; i < Chest.SLOT_COUNT; i++) {
            if (i != 0 && i != 10 && i != 26) {
                assertTrue(loaded.isEmpty(i), "slot " + i + " stays empty");
            }
        }
    }

    @Test
    void serializationRoundTripPreservesTinkersPayload() throws Exception {
        Chest chest = new Chest();
        chest.setStack(4, ItemStack.tinkersPart(
                new TinkersItem.Part(ToolPartType.PICK_HEAD, BlockType.IRON_INGOT)));

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        chest.writeTo(new DataOutputStream(bytes));
        Chest loaded = new Chest();
        loaded.readFrom(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())));

        assertTrue(loaded.stackOf(4).isTinkersPart());
        assertEquals(ToolPartType.PICK_HEAD, loaded.stackOf(4).tinkersPart().shape);
        assertEquals(BlockType.IRON_INGOT, loaded.stackOf(4).tinkersPart().material);
    }

    @Test
    void serializationRoundTripPreservesTinkersToolWear() throws Exception {
        TinkersItem.Tool tool = new TinkersItem.Tool(ToolKind.PICKAXE, List.of(
                new TinkersItem.ToolLayer(ToolPartType.PICK_HEAD, BlockType.IRON_INGOT),
                new TinkersItem.ToolLayer(ToolPartType.TOOL_ROD, BlockType.PLANKS)));
        tool.use();
        Chest chest = new Chest();
        chest.setStack(7, ItemStack.tinkersTool(tool));

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        chest.writeTo(new DataOutputStream(bytes));
        Chest loaded = new Chest();
        loaded.readFrom(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())));

        assertTrue(loaded.stackOf(7).isTinkersTool());
        assertEquals(tool.remaining(), loaded.stackOf(7).tinkersTool().remaining());
        assertEquals(tool.layers, loaded.stackOf(7).tinkersTool().layers);
    }

    @Test
    void emptyAndFilledStatesReported() {
        Chest c = new Chest();
        assertTrue(c.getCount(BlockType.STONE) == 0);
        c.add(BlockType.STONE, 1);
        assertEquals(1, c.getCount(BlockType.STONE));
        assertFalse(c.isEmpty(0));
    }
}
