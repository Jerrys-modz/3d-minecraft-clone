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
}
