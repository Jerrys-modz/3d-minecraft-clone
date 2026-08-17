package com.minecraftclone.world;

import org.junit.jupiter.api.Test;

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
        assertFalse(Mining.isHammer(null));
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
}
