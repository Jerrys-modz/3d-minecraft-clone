package com.minecraftclone.player;

import com.minecraftclone.world.BlockType;
import com.minecraftclone.world.Mining;
import com.minecraftclone.world.tinkers.TinkersItem;
import com.minecraftclone.world.tinkers.ToolPartType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ItemStackTest {

    @Test
    void ofRejectsTinkersSentinels() {
        assertTrue(ItemStack.of(BlockType.TINKERS_PART, 1).isEmpty());
        assertTrue(ItemStack.of(BlockType.TINKERS_TOOL, 1).isEmpty());
        assertTrue(ItemStack.of(BlockType.DIRT, 8).type() == BlockType.DIRT);
    }

    @Test
    void withCountKeepsTinkersAtCountOne() {
        ItemStack part = ItemStack.tinkersPart(
                new TinkersItem.Part(ToolPartType.PICK_HEAD, BlockType.IRON_INGOT));
        assertSame(part, part.withCount(5));
        assertEquals(1, part.withCount(5).count());
        assertTrue(part.withCount(0).isEmpty());

        ItemStack tool = ItemStack.tinkersTool(new TinkersItem.Tool(Mining.ToolKind.SWORD, List.of(
                new TinkersItem.ToolLayer(ToolPartType.SWORD_BLADE, BlockType.IRON_INGOT),
                new TinkersItem.ToolLayer(ToolPartType.TOOL_ROD, BlockType.PLANKS))));
        assertSame(tool, tool.withCount(2));
        assertEquals(1, tool.count());
    }
}
