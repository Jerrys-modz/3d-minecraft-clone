package com.minecraftclone.player;

import com.minecraftclone.world.BlockType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Shaped crafting: recipes are 3x3 grid patterns (like Minecraft's crafting
 * table) rather than a flat list of ingredients. The player arranges items in
 * the in-game crafting grid (press `E`), and the shape determines the output.
 * <p>
 * Each recipe is stored in its minimal (top-left) bounding-box form, and a
 * player's grid is normalized the same way before matching, so a recipe can
 * sit anywhere in the 3x3 grid and still match.
 */
public final class Crafting {

    /** A shaped recipe: a 3x3 row-major pattern (null = empty) and its output. */
    public record ShapedRecipe(BlockType[] pattern, BlockType output, int outputAmount) {
    }

    private static final List<ShapedRecipe> RECIPES = new ArrayList<>();

    // Single-character codes for readable 3x3 pattern strings.
    private static final Map<Character, BlockType> CHARS = new HashMap<>();

    static {
        CHARS.put('.', null);
        CHARS.put('W', BlockType.WOOD_LOG);
        CHARS.put('P', BlockType.PLANKS);
        CHARS.put('S', BlockType.STICK);
        CHARS.put('C', BlockType.COAL_ORE);
        CHARS.put('G', BlockType.GLASS);
        CHARS.put('T', BlockType.TORCH);
        CHARS.put('K', BlockType.STONE);
        CHARS.put('N', BlockType.SAND);
        CHARS.put('I', BlockType.IRON_INGOT);
        CHARS.put('D', BlockType.DIAMOND);

        // Basic blocks & materials.
        register(p("W..", "...", "..."), BlockType.PLANKS, 4);        // log -> planks
        register(p("P..", "P..", "..."), BlockType.STICK, 4);         // 2 planks -> sticks
        register(p("NN.", "...", "..."), BlockType.GLASS, 1);         // 2 sand -> glass
        register(p("KKK", "K.K", "KKK"), BlockType.FURNACE, 1);       // stone ring -> furnace
        register(p(".C.", ".S.", "..."), BlockType.TORCH, 4);         // coal over stick -> torches
        register(p(".G.", ".T.", "..."), BlockType.LAMP, 1);          // glass over torch -> lamp
        register(p("KKK", "...", "..."), BlockType.STONE_SLAB, 6);    // 3 stone -> slabs
        register(p("PPP", "...", "..."), BlockType.PLANKS_SLAB, 6);   // 3 planks -> slabs

        // Tools: pickaxe (3 mat + 2 stick), axe (3 mat + 2 stick), sword (2 mat + 1 stick).
        registerTools('P', BlockType.WOOD_PICKAXE, BlockType.WOOD_AXE, BlockType.WOOD_SWORD);
        registerTools('K', BlockType.STONE_PICKAXE, BlockType.STONE_AXE, BlockType.STONE_SWORD);
        registerTools('I', BlockType.IRON_PICKAXE, BlockType.IRON_AXE, BlockType.IRON_SWORD);
        registerTools('D', BlockType.DIAMOND_PICKAXE, BlockType.DIAMOND_AXE, BlockType.DIAMOND_SWORD);
    }

    private Crafting() {
    }

    private static void registerTools(char m, BlockType pickaxe, BlockType axe, BlockType sword) {
        register(p("" + m + m + m, ".S.", ".S."), pickaxe, 1);
        register(p("" + m + m + ".", "" + m + "S.", ".S."), axe, 1);
        register(p("." + m + ".", "." + m + ".", ".S."), sword, 1);
    }

    /** Builds a 3x3 pattern from three 3-character strings (' . ' = empty). */
    private static BlockType[] p(String r0, String r1, String r2) {
        BlockType[] cells = new BlockType[9];
        String[] rows = {r0, r1, r2};
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 3; c++) {
                cells[r * 3 + c] = CHARS.get(rows[r].charAt(c));
            }
        }
        return cells;
    }

    private static void register(BlockType[] pattern, BlockType output, int outputAmount) {
        RECIPES.add(new ShapedRecipe(normalize(pattern), output, outputAmount));
    }

    /** Shifts a 3x3 grid so its non-empty cells are in the top-left (its minimal bounding box). */
    private static BlockType[] normalize(BlockType[] cells) {
        int minR = 3, minC = 3, maxR = -1, maxC = -1;
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 3; c++) {
                if (cells[r * 3 + c] != null) {
                    minR = Math.min(minR, r);
                    maxR = Math.max(maxR, r);
                    minC = Math.min(minC, c);
                    maxC = Math.max(maxC, c);
                }
            }
        }
        if (minR > maxR) return new BlockType[9]; // empty grid
        BlockType[] out = new BlockType[9];
        for (int r = minR; r <= maxR; r++) {
            for (int c = minC; c <= maxC; c++) {
                out[(r - minR) * 3 + (c - minC)] = cells[r * 3 + c];
            }
        }
        return out;
    }

    /**
     * Finds the shaped recipe matching the given 3x3 grid (row-major, null =
     * empty), or null if no recipe matches. The grid is normalized to its
     * bounding box first, so recipes match regardless of where they're placed.
     */
    public static ShapedRecipe match(BlockType[] grid) {
        BlockType[] norm = normalize(grid);
        for (ShapedRecipe recipe : RECIPES) {
            if (Arrays.equals(norm, recipe.pattern())) {
                return recipe;
            }
        }
        return null;
    }
}
