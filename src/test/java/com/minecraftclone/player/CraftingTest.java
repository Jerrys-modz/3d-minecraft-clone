package com.minecraftclone.player;

import com.minecraftclone.world.BlockType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CraftingTest {

    @Test
    void shapedLogToPlanks() {
        BlockType[] grid = new BlockType[9];
        grid[0] = BlockType.WOOD_LOG;
        Crafting.Recipe r = Crafting.match(grid);
        assertNotNull(r);
        assertEquals(BlockType.PLANKS, r.output());
        assertEquals(4, r.outputAmount());
    }

    @Test
    void shapedTwoPlanksToSticks() {
        BlockType[] grid = new BlockType[9];
        grid[0] = BlockType.PLANKS;
        grid[3] = BlockType.PLANKS;
        Crafting.Recipe r = Crafting.match(grid);
        assertNotNull(r);
        assertEquals(BlockType.STICK, r.output());
        assertEquals(4, r.outputAmount());
    }

    @Test
    void toolRecipeMatchesWithMirroring() {
        // Axe: material-material-empty / material-stick-empty / empty-stick-empty.
        BlockType[] normal = new BlockType[9];
        normal[0] = BlockType.IRON_INGOT;
        normal[1] = BlockType.IRON_INGOT;
        normal[3] = BlockType.IRON_INGOT;
        normal[4] = BlockType.STICK;
        normal[7] = BlockType.STICK;
        Crafting.Recipe r = Crafting.match(normal);
        assertNotNull(r);
        assertEquals(BlockType.IRON_AXE, r.output());

        // Mirror (handle on the other side) must also match.
        BlockType[] mirrored = new BlockType[9];
        mirrored[1] = BlockType.IRON_INGOT;
        mirrored[2] = BlockType.IRON_INGOT;
        mirrored[4] = BlockType.STICK;
        mirrored[5] = BlockType.IRON_INGOT;
        mirrored[7] = BlockType.STICK;
        Crafting.Recipe rm = Crafting.match(mirrored);
        assertNotNull(rm);
        assertEquals(BlockType.IRON_AXE, rm.output());
    }

    @Test
    void shapelessGlassFromSand() {
        BlockType[] grid = new BlockType[9];
        grid[2] = BlockType.SAND;
        grid[5] = BlockType.SAND;
        Crafting.Recipe r = Crafting.match(grid);
        assertNotNull(r);
        assertEquals(BlockType.GLASS, r.output());
    }

    @Test
    void wrongShapeDoesNotMatch() {
        BlockType[] grid = new BlockType[9];
        grid[0] = BlockType.STONE;   // one stone is not the stone-ring furnace recipe
        assertNull(Crafting.match(grid));
    }

    @Test
    void doorAndTrapdoorCraftFromPlanks() {
        BlockType[] door = new BlockType[9];
        door[0] = BlockType.PLANKS;
        door[1] = BlockType.PLANKS;
        door[3] = BlockType.PLANKS;
        door[4] = BlockType.PLANKS;
        door[6] = BlockType.PLANKS;
        door[7] = BlockType.PLANKS;
        Crafting.Recipe rd = Crafting.match(door);
        assertNotNull(rd);
        assertEquals(BlockType.DOOR, rd.output());

        BlockType[] trap = new BlockType[9];
        trap[0] = BlockType.PLANKS;
        trap[1] = BlockType.PLANKS;
        trap[2] = BlockType.PLANKS;
        trap[3] = BlockType.PLANKS;
        trap[4] = BlockType.PLANKS;
        trap[5] = BlockType.PLANKS;
        Crafting.Recipe rt = Crafting.match(trap);
        assertNotNull(rt);
        assertEquals(BlockType.TRAPDOOR, rt.output());
        assertEquals(2, rt.outputAmount());
    }

    @Test
    void armorCraftsFromMaterialShapes() {
        // Iron chestplate: M.M / MMM / MMM.
        BlockType[] chest = new BlockType[9];
        chest[0] = BlockType.IRON_INGOT;
        chest[2] = BlockType.IRON_INGOT;
        chest[3] = BlockType.IRON_INGOT;
        chest[4] = BlockType.IRON_INGOT;
        chest[5] = BlockType.IRON_INGOT;
        chest[6] = BlockType.IRON_INGOT;
        chest[7] = BlockType.IRON_INGOT;
        chest[8] = BlockType.IRON_INGOT;
        Crafting.Recipe rc = Crafting.match(chest);
        assertNotNull(rc);
        assertEquals(BlockType.IRON_CHESTPLATE, rc.output());

        // Diamond helmet: MMM / M.M / ...
        BlockType[] helmet = new BlockType[9];
        helmet[0] = BlockType.DIAMOND;
        helmet[1] = BlockType.DIAMOND;
        helmet[2] = BlockType.DIAMOND;
        helmet[3] = BlockType.DIAMOND;
        helmet[5] = BlockType.DIAMOND;
        Crafting.Recipe rh = Crafting.match(helmet);
        assertNotNull(rh);
        assertEquals(BlockType.DIAMOND_HELMET, rh.output());

        // Wood leggings: MMM / M.M / M.M.
        BlockType[] legs = new BlockType[9];
        legs[0] = BlockType.PLANKS;
        legs[1] = BlockType.PLANKS;
        legs[2] = BlockType.PLANKS;
        legs[3] = BlockType.PLANKS;
        legs[5] = BlockType.PLANKS;
        legs[6] = BlockType.PLANKS;
        legs[8] = BlockType.PLANKS;
        Crafting.Recipe rl = Crafting.match(legs);
        assertNotNull(rl);
        assertEquals(BlockType.WOOD_LEGGINGS, rl.output());

        // Stone boots: M.M / M.M / ...
        BlockType[] boots = new BlockType[9];
        boots[0] = BlockType.STONE;
        boots[2] = BlockType.STONE;
        boots[3] = BlockType.STONE;
        boots[5] = BlockType.STONE;
        Crafting.Recipe rb = Crafting.match(boots);
        assertNotNull(rb);
        assertEquals(BlockType.STONE_BOOTS, rb.output());
    }
}
