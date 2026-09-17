package com.minecraftclone.world;

import com.minecraftclone.player.AnvilGui;
import com.minecraftclone.player.Crafting;
import com.minecraftclone.player.Smelting;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BronzeToolsTest {

    @Test
    void bronzePickIsFasterThanStoneSlowerThanIronOnOre() {
        float stone = Mining.breakTimeSeconds(BlockType.COPPER_ORE, BlockType.STONE_PICKAXE);
        float bronze = Mining.breakTimeSeconds(BlockType.COPPER_ORE, BlockType.BRONZE_PICKAXE);
        float iron = Mining.breakTimeSeconds(BlockType.COPPER_ORE, BlockType.IRON_PICKAXE);
        assertTrue(bronze < stone, "bronze faster than stone");
        assertTrue(iron < bronze, "iron still faster than bronze");
        assertEquals(stone / 1.5f, bronze, 0.0001f);
    }

    @Test
    void bronzePickMinesIronOreButNotGold() {
        assertTrue(Mining.canBreak(BlockType.IRON_ORE, BlockType.BRONZE_PICKAXE));
        assertFalse(Mining.canBreak(BlockType.GOLD_ORE, BlockType.BRONZE_PICKAXE),
                "bronze harvest is stone-level — gold still needs iron");
        assertTrue(Mining.canBreak(BlockType.GOLD_ORE, BlockType.IRON_PICKAXE));
    }

    @Test
    void bronzePickaxeRecipe() {
        BlockType[] grid = new BlockType[9];
        grid[0] = BlockType.BRONZE_INGOT; grid[1] = BlockType.BRONZE_INGOT; grid[2] = BlockType.BRONZE_INGOT;
        grid[3] = BlockType.BRONZE_INGOT; grid[4] = BlockType.STICK;        grid[5] = BlockType.BRONZE_INGOT;
        grid[7] = BlockType.STICK;
        Crafting.Recipe r = Crafting.match3x3(grid);
        assertNotNull(r);
        assertEquals(BlockType.BRONZE_PICKAXE, r.output());
    }

    @Test
    void bronzeSwordRecipe() {
        BlockType[] grid = new BlockType[9];
        grid[1] = BlockType.BRONZE_INGOT;
        grid[4] = BlockType.BRONZE_INGOT;
        grid[7] = BlockType.STICK;
        Crafting.Recipe r = Crafting.match3x3(grid);
        assertNotNull(r);
        assertEquals(BlockType.BRONZE_SWORD, r.output());
    }

    @Test
    void bronzeHammerRecipeIsDistinctFromAxe() {
        BlockType[] grid = new BlockType[9];
        grid[0] = BlockType.BRONZE_INGOT; grid[1] = BlockType.BRONZE_INGOT;
        grid[3] = BlockType.BRONZE_INGOT; grid[4] = BlockType.STICK;
        grid[7] = BlockType.STICK;
        Crafting.Recipe r = Crafting.match3x3(grid);
        assertNotNull(r);
        assertEquals(BlockType.BRONZE_HAMMER, r.output());
    }

    @Test
    void bronzeAxeRecipe() {
        BlockType[] grid = new BlockType[9];
        grid[0] = BlockType.BRONZE_INGOT; grid[1] = BlockType.BRONZE_INGOT;
        grid[3] = BlockType.BRONZE_INGOT; grid[4] = BlockType.STICK; grid[5] = BlockType.BRONZE_INGOT;
        grid[7] = BlockType.STICK;
        Crafting.Recipe r = Crafting.match3x3(grid);
        assertNotNull(r);
        assertEquals(BlockType.BRONZE_AXE, r.output());
    }

    @Test
    void mortarRecipe() {
        BlockType[] grid = new BlockType[9];
        grid[0] = BlockType.CLAY_BALL; grid[2] = BlockType.CLAY_BALL;
        grid[3] = BlockType.CLAY_BALL; grid[4] = BlockType.STICK; grid[5] = BlockType.CLAY_BALL;
        grid[7] = BlockType.CLAY_BALL;
        Crafting.Recipe r = Crafting.match3x3(grid);
        assertNotNull(r);
        assertEquals(BlockType.MORTAR, r.output());
    }

    @Test
    void brassDustCraftsAndSmelts() {
        BlockType[] grid = new BlockType[4];
        grid[0] = BlockType.COPPER_DUST;
        grid[1] = BlockType.ZINC_DUST;
        Crafting.Recipe r = Crafting.match(grid);
        assertNotNull(r);
        assertEquals(BlockType.BRASS_DUST, r.output());
        assertEquals(BlockType.BRASS_INGOT, Smelting.outputFor(BlockType.BRASS_DUST));
    }

    @Test
    void fireBrickSmeltsFromClay() {
        assertEquals(BlockType.FIRE_BRICK, Smelting.outputFor(BlockType.CLAY_BALL));
        assertEquals(BlockType.FIRE_BRICK, Smelting.outputFor(BlockType.CLAY));
    }

    @Test
    void pbfCraftsFromFireBricks() {
        BlockType[] grid = new BlockType[9];
        for (int i = 0; i < 9; i++) if (i != 4) grid[i] = BlockType.FIRE_BRICK;
        Crafting.Recipe r = Crafting.match3x3(grid);
        assertNotNull(r);
        assertEquals(BlockType.PRIMITIVE_BLAST_FURNACE, r.output());
    }

    @Test
    void washerCraftsFromBronzeAndGlass() {
        BlockType[] grid = new BlockType[9];
        for (int i = 0; i < 9; i++) grid[i] = BlockType.BRONZE_INGOT;
        grid[4] = BlockType.GLASS;
        Crafting.Recipe r = Crafting.match3x3(grid);
        assertNotNull(r);
        assertEquals(BlockType.STEAM_ORE_WASHER, r.output());
    }

    @Test
    void bronzeToolsRepairWithBronzeIngots() {
        assertEquals(BlockType.BRONZE_INGOT, AnvilGui.repairMaterialOf(BlockType.BRONZE_PICKAXE));
        assertEquals(BlockType.CLAY_BALL, AnvilGui.repairMaterialOf(BlockType.MORTAR));
        assertEquals(BlockType.IRON_INGOT, AnvilGui.repairMaterialOf(BlockType.IRON_PICKAXE));
    }

    @Test
    void bronzeHoeTillsLikeOtherHoes() {
        assertTrue(BlockType.BRONZE_HOE.isHoe());
        assertTrue(Mining.isTool(BlockType.BRONZE_HOE));
        assertTrue(Mining.isTool(BlockType.MORTAR));
    }

    @Test
    void bronzeThreeToOneYieldsFourDust() {
        BlockType[] grid = new BlockType[9];
        grid[0] = BlockType.COPPER_DUST;
        grid[1] = BlockType.COPPER_DUST;
        grid[2] = BlockType.COPPER_DUST;
        grid[3] = BlockType.TIN_DUST;
        Crafting.Recipe r = Crafting.match3x3(grid);
        assertNotNull(r);
        assertEquals(BlockType.BRONZE_DUST, r.output());
        assertEquals(4, r.outputAmount());
    }

    @Test
    void steelDustCraftsFromWroughtIronAndCoalDust() {
        BlockType[] grid = new BlockType[4];
        grid[0] = BlockType.WROUGHT_IRON_INGOT;
        grid[1] = BlockType.COAL_DUST;
        Crafting.Recipe r = Crafting.match(grid);
        assertNotNull(r);
        assertEquals(BlockType.STEEL_DUST, r.output());
    }

    @Test
    void tableNoLongerMakesSteelIngots() {
        BlockType[] grid = new BlockType[9];
        grid[0] = BlockType.IRON_INGOT; grid[1] = BlockType.COAL; grid[2] = BlockType.IRON_INGOT;
        grid[4] = BlockType.COAL;
        grid[6] = BlockType.IRON_INGOT; grid[7] = BlockType.COAL; grid[8] = BlockType.IRON_INGOT;
        Crafting.Recipe r = Crafting.match3x3(grid);
        assertTrue(r == null || r.output() != BlockType.STEEL_INGOT,
                "placeholder table steel is gone — use the PBF");
    }

    @Test
    void fileRecipeAndSteelCasing() {
        BlockType[] fileGrid = new BlockType[4];
        fileGrid[0] = BlockType.IRON_INGOT;
        fileGrid[2] = BlockType.STICK;
        Crafting.Recipe file = Crafting.match(fileGrid);
        assertNotNull(file);
        assertEquals(BlockType.FILE, file.output());

        BlockType[] casing = new BlockType[9];
        for (int i = 0; i < 9; i++) if (i != 4) casing[i] = BlockType.STEEL_PLATE;
        Crafting.Recipe c = Crafting.match3x3(casing);
        assertNotNull(c);
        assertEquals(BlockType.STEEL_CASING, c.output());
    }

    @Test
    void fileRepairsWithIron() {
        assertEquals(BlockType.IRON_INGOT, AnvilGui.repairMaterialOf(BlockType.FILE));
        assertTrue(Mining.isTool(BlockType.FILE));
    }

    @Test
    void coalDustBurnsLikeCoal() {
        assertTrue(Furnace.isFuel(BlockType.COAL_DUST));
        assertEquals(Furnace.fuelDuration(BlockType.COAL), Furnace.fuelDuration(BlockType.COAL_DUST));
    }
}
