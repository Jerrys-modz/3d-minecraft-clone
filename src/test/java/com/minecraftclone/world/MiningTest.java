package com.minecraftclone.world;

import com.minecraftclone.player.ItemStack;
import com.minecraftclone.world.tinkers.TinkersItem;
import com.minecraftclone.world.tinkers.ToolPartType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MiningTest {

    @Test
    void shovelIsEffectiveOnSoftGround() {
        float hand = Mining.breakTimeSeconds(BlockType.DIRT, null);
        float shovel = Mining.breakTimeSeconds(BlockType.DIRT, BlockType.IRON_SHOVEL);
        assertTrue(shovel < hand, "shovel digs dirt faster than bare hands");
        assertEquals(0.5f / 8f, shovel, 0.0001f); // 0.5s / 2^3 (iron tier)
    }

    @Test
    void hoeIsNotAShovelAndDoesNotSpeedDirt() {
        float hand = Mining.breakTimeSeconds(BlockType.DIRT, null);
        assertEquals(hand, Mining.breakTimeSeconds(BlockType.DIRT, BlockType.WOOD_HOE), 0.0001f);
        assertEquals(hand, Mining.breakTimeSeconds(BlockType.DIRT, BlockType.DIAMOND_HOE), 0.0001f);
        assertEquals(Mining.breakTimeSeconds(BlockType.GRASS, null),
                Mining.breakTimeSeconds(BlockType.GRASS, BlockType.IRON_HOE), 0.0001f);
        assertTrue(Mining.isTool(BlockType.WOOD_HOE), "hoes still wear as tools");
        assertEquals(Mining.ToolKind.NONE, Mining.toolStats(BlockType.DIAMOND_HOE).kind());
        float shovel = Mining.breakTimeSeconds(BlockType.DIRT, BlockType.DIAMOND_SHOVEL);
        assertTrue(shovel < hand, "a real shovel is still faster than a hoe");
    }

    @Test
    void hammerIsEffectiveOnStoneAndFurnace() {
        float hand = Mining.breakTimeSeconds(BlockType.STONE, null);
        float hammer = Mining.breakTimeSeconds(BlockType.STONE, BlockType.DIAMOND_HAMMER);
        assertTrue(hammer < hand, "hammer breaks stone faster than bare hands");
        assertEquals(2.5f / 16f, hammer, 0.0001f);
        assertTrue(Mining.breakTimeSeconds(BlockType.FURNACE, BlockType.IRON_HAMMER)
                < Mining.breakTimeSeconds(BlockType.FURNACE, null));
    }

    @Test
    void broadaxeIsTheFastestWoodCutter() {
        float broad = Mining.breakTimeSeconds(BlockType.WOOD_LOG, BlockType.DIAMOND_BROADAXE);
        assertEquals(1.5f / 32f, broad, 0.0001f, "diamond broadaxe cuts at 2^(4+1) speed");
        assertTrue(broad < Mining.breakTimeSeconds(BlockType.WOOD_LOG, BlockType.DIAMOND_PICKAXE),
                "broadaxe out-cuts a same-tier pickaxe on wood");
    }

    @Test
    void axeIsEffectiveOnLeaves() {
        float axe = Mining.breakTimeSeconds(BlockType.LEAVES, BlockType.STONE_AXE);
        float sword = Mining.breakTimeSeconds(BlockType.LEAVES, BlockType.STONE_SWORD);
        float hand = Mining.breakTimeSeconds(BlockType.LEAVES, null);
        assertTrue(axe < hand, "axe is faster than bare hands through leaves");
        assertTrue(axe <= sword, "axe is at least as fast as a sword through leaves");
    }

    @Test
    void onlyPickaxesBreakOres() {
        assertTrue(Mining.canBreak(BlockType.IRON_ORE, BlockType.STONE_PICKAXE));
        assertFalse(Mining.canBreak(BlockType.IRON_ORE, BlockType.STONE_SHOVEL));
        assertFalse(Mining.canBreak(BlockType.IRON_ORE, BlockType.STONE_HAMMER));
        assertFalse(Mining.canBreak(BlockType.IRON_ORE, BlockType.STONE_BROADAXE));
        assertFalse(Mining.canBreak(BlockType.IRON_ORE, BlockType.STONE_SWORD));
        assertFalse(Mining.canBreak(BlockType.IRON_ORE, null));
    }

    @Test
    void harvestHintSaysWhetherYouCanMineIt() {
        ItemStack empty = ItemStack.EMPTY;
        ItemStack stonePick = ItemStack.of(BlockType.STONE_PICKAXE, 1);
        ItemStack ironPick = ItemStack.of(BlockType.IRON_PICKAXE, 1);

        assertNull(Mining.harvestHint(BlockType.AIR, empty, false, true));
        assertEquals("Can mine", Mining.harvestHint(BlockType.DIRT, empty, false, true));
        assertEquals("Need Stone Pickaxe", Mining.harvestHint(BlockType.IRON_ORE, empty, false, true));
        assertEquals("Can mine", Mining.harvestHint(BlockType.IRON_ORE, stonePick, false, true));
        assertEquals("Need Iron Pickaxe", Mining.harvestHint(BlockType.DIAMOND_ORE, stonePick, false, true));
        assertEquals("Can mine", Mining.harvestHint(BlockType.DIAMOND_ORE, ironPick, false, true));
        assertEquals("Unbreakable", Mining.harvestHint(BlockType.BEDROCK, ironPick, true, true));
        assertEquals("Can mine", Mining.harvestHint(BlockType.IRON_ORE, empty, true, true),
                "creative mines without the right tool");
        assertEquals("Can't mine", Mining.harvestHint(BlockType.DIRT, empty, false, false),
                "adventure/spectator cannot break");
        assertTrue(Mining.harvestHintPositive("Can mine"));
        assertFalse(Mining.harvestHintPositive("Need Stone Pickaxe"));
        assertFalse(Mining.harvestHintPositive("Unbreakable"));
    }

    @Test
    void creativeRemovesOresWithoutTheRightTool() {
        ItemStack empty = ItemStack.EMPTY;
        assertTrue(Mining.canRemove(BlockType.IRON_ORE, empty, true),
                "creative punches out ores that survival would refuse");
        assertTrue(Mining.canRemove(BlockType.DIAMOND_ORE, empty, true));
        assertTrue(Mining.canRemove(BlockType.STONE, empty, true));
        assertTrue(Mining.canRemove(BlockType.DIRT, empty, true));
        assertFalse(Mining.canRemove(BlockType.IRON_ORE, empty, false),
                "survival still needs a pick for iron ore");
        assertFalse(Mining.canRemove(BlockType.BEDROCK, empty, true),
                "bedrock stays unbreakable even in creative");
        assertFalse(Mining.canRemove(BlockType.AIR, empty, true));
        assertTrue(Mining.canRemove(BlockType.IRON_ORE, ItemStack.of(BlockType.STONE_PICKAXE, 1), false));
    }

    @Test
    void newToolsAreRegisteredAtEveryTier() {
        BlockType[] kinds = {
                BlockType.WOOD_SHOVEL, BlockType.STONE_SHOVEL, BlockType.IRON_SHOVEL, BlockType.DIAMOND_SHOVEL,
                BlockType.WOOD_HAMMER, BlockType.STONE_HAMMER, BlockType.IRON_HAMMER, BlockType.DIAMOND_HAMMER,
                BlockType.WOOD_BROADAXE, BlockType.STONE_BROADAXE, BlockType.IRON_BROADAXE, BlockType.DIAMOND_BROADAXE,
        };
        for (BlockType t : kinds) {
            assertTrue(Mining.isTool(t), t + " is a tool");
            assertNotNull(Mining.toolStats(t), t + " has stats");
        }
    }

    @Test
    void hammerIsTheAreaMiningTool() {
        assertTrue(Mining.isHammer(BlockType.WOOD_HAMMER));
        assertTrue(Mining.isHammer(BlockType.DIAMOND_HAMMER));
        assertFalse(Mining.isHammer(BlockType.WOOD_PICKAXE));
        assertFalse(Mining.isHammer(BlockType.WOOD_SHOVEL));
        assertFalse(Mining.isHammer((BlockType) null));
    }

    @Test
    void stairsMineLikeTheirMaterial() {
        // Stone stairs break with a hammer at stone's speed; planks/fence with a broadaxe.
        assertTrue(Mining.breakTimeSeconds(BlockType.STONE_STAIRS, BlockType.IRON_HAMMER)
                < Mining.breakTimeSeconds(BlockType.STONE_STAIRS, null));
        assertEquals(Mining.breakTimeSeconds(BlockType.STONE, BlockType.IRON_HAMMER),
                Mining.breakTimeSeconds(BlockType.STONE_STAIRS, BlockType.IRON_HAMMER), 0.0001f);
        assertTrue(Mining.breakTimeSeconds(BlockType.PLANKS_STAIRS, BlockType.IRON_BROADAXE)
                < Mining.breakTimeSeconds(BlockType.PLANKS_STAIRS, null));
        assertTrue(Mining.breakTimeSeconds(BlockType.WOODEN_FENCE, BlockType.IRON_BROADAXE)
                < Mining.breakTimeSeconds(BlockType.WOODEN_FENCE, null));
    }

    private static ItemStack tinkersTool(Mining.ToolKind kind, BlockType head) {
        return ItemStack.tinkersTool(new TinkersItem.Tool(kind, List.of(
                new TinkersItem.ToolLayer(ToolPartType.PICK_HEAD, head),
                new TinkersItem.ToolLayer(ToolPartType.TOOL_ROD, BlockType.PLANKS)
        )));
    }

    @Test
    void tinkersPickCanMineIronOreAtHeadSpeed() {
        ItemStack pick = tinkersTool(Mining.ToolKind.PICKAXE, BlockType.IRON_INGOT);
        assertTrue(Mining.canBreakItem(BlockType.IRON_ORE, pick),
                "iron-head Tinkers pick must be able to mine iron ore");
        assertFalse(Mining.canBreak(BlockType.IRON_ORE, BlockType.TINKERS_TOOL),
                "the TINKERS_TOOL sentinel itself has no tier and must not mine ores");
        // IRON_ORE hardness 4.0, iron-head miningSpeed 3.0 → 4/3 seconds
        assertEquals(4.0f / 3.0f, Mining.breakTimeItem(BlockType.IRON_ORE, pick), 0.0001f);
        assertTrue(Mining.breakTimeItem(BlockType.DIRT, pick)
                > Mining.breakTimeSeconds(BlockType.DIRT, BlockType.IRON_SHOVEL),
                "a pick is not effective on dirt");
    }

    @Test
    void tinkersSwordHitsHarderThanAPunchAndCountsAsASword() {
        ItemStack sword = ItemStack.tinkersTool(new TinkersItem.Tool(Mining.ToolKind.SWORD, List.of(
                new TinkersItem.ToolLayer(ToolPartType.SWORD_BLADE, BlockType.IRON_INGOT),
                new TinkersItem.ToolLayer(ToolPartType.TOOL_ROD, BlockType.PLANKS)
        )));
        assertTrue(Mining.isSword(sword));
        assertEquals(6f, Mining.attackDamage(sword), 0.0001f);
        assertEquals(1f, Mining.attackDamage(ItemStack.of(BlockType.TINKERS_TOOL, 1)), 0.0001f,
                "payload-less sentinel must not deal sword damage");
    }

    @Test
    void tinkersHammerIsTheAreaMiningTool() {
        ItemStack hammer = tinkersTool(Mining.ToolKind.HAMMER, BlockType.IRON_INGOT);
        assertTrue(Mining.isHammer(hammer));
        assertFalse(Mining.isHammer(tinkersTool(Mining.ToolKind.PICKAXE, BlockType.IRON_INGOT)));
        assertFalse(Mining.isHammer(ItemStack.EMPTY));
    }

    @Test
    void toolStatsForReadsTinkersPayload() {
        ItemStack pick = tinkersTool(Mining.ToolKind.PICKAXE, BlockType.IRON_INGOT);
        Mining.ToolStats stats = Mining.toolStatsFor(pick);
        assertNotNull(stats);
        assertEquals(Mining.ToolKind.PICKAXE, stats.kind());
        assertEquals(Mining.TIER_IRON, stats.tier());
        assertNull(Mining.toolStats(BlockType.TINKERS_TOOL),
                "static TOOLS table has no TINKERS_TOOL entry (Hud must not NPE on it)");
        assertTrue(Mining.isTool(BlockType.TINKERS_TOOL));
    }
}
