package com.minecraftclone.player;

import com.minecraftclone.world.BlockType;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Minecraft-style crafting: a 3x3 grid where either the shape of the
 * ingredients (shaped recipes) or just their combination (shapeless recipes)
 * determines the output.
 * <p>
 * Adding a recipe is a one-liner - see the two registration helpers:
 * <pre>
 *   shaped("III", ".S.", ".S.", BlockType.IRON_PICKAXE, 1);   // 3x3 pattern, '.' = empty
 *   shapeless(BlockType.GLASS, 1, BlockType.SAND, BlockType.SAND); // any arrangement
 * </pre>
 * Shaped recipes match like Minecraft's: the pattern may sit anywhere in the
 * grid, and it's also matched under horizontal mirroring (so an axe works with
 * its handle on either side). Shapeless recipes match on the multiset of
 * ingredients, ignoring arrangement.
 */
public final class Crafting {

    /** A craftable recipe that can be matched against the player's 3x3 grid. */
    public interface Recipe {
        BlockType output();

        int outputAmount();

        boolean matches(BlockType[] grid);
    }

    public record ShapedRecipe(BlockType[] pattern, BlockType output, int outputAmount) implements Recipe {
        @Override
        public boolean matches(BlockType[] grid) {
            return matchesShaped(grid, pattern);
        }
    }

    public record ShapelessRecipe(BlockType[] ingredients, BlockType output, int outputAmount) implements Recipe {
        @Override
        public boolean matches(BlockType[] grid) {
            return matchesShapeless(grid, ingredients);
        }
    }

    private static final List<Recipe> RECIPES = new ArrayList<>();

    // Single-character codes used by the shaped() pattern strings. Add a code
    // here (and a new BlockType) to use it in a pattern.
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

        // --- Shaped recipes: three 3-character rows ('.' = empty). ---
        shaped("W..", "...", "...", BlockType.PLANKS, 4);        // log -> planks
        shaped("P..", "P..", "...", BlockType.STICK, 4);         // 2 planks -> sticks
        shaped("KKK", "K.K", "KKK", BlockType.FURNACE, 1);       // stone ring -> furnace
        shaped(".C.", ".S.", "...", BlockType.TORCH, 4);         // coal over stick -> torches
        shaped(".G.", ".T.", "...", BlockType.LAMP, 1);          // glass over torch -> lamp
        shaped("KKK", "...", "...", BlockType.STONE_SLAB, 6);    // 3 stone -> slabs
        shaped("PPP", "...", "...", BlockType.PLANKS_SLAB, 6);   // 3 planks -> slabs
        shaped("PP.", "PP.", "PP.", BlockType.DOOR, 1);          // 6 planks -> door
        shaped("PPP", "PPP", "...", BlockType.TRAPDOOR, 2);      // 6 planks -> 2 trapdoors

        // Tools (mirrored matching lets an axe be built either way round).
        tools('P', BlockType.WOOD_PICKAXE, BlockType.WOOD_AXE, BlockType.WOOD_SWORD);
        tools('K', BlockType.STONE_PICKAXE, BlockType.STONE_AXE, BlockType.STONE_SWORD);
        tools('I', BlockType.IRON_PICKAXE, BlockType.IRON_AXE, BlockType.IRON_SWORD);
        tools('D', BlockType.DIAMOND_PICKAXE, BlockType.DIAMOND_AXE, BlockType.DIAMOND_SWORD);

        // --- Shapeless recipes: any arrangement of the given ingredients. ---
        shapeless(BlockType.GLASS, 1, BlockType.SAND, BlockType.SAND);
    }

    private Crafting() {
    }

    /** Registers a shaped recipe from three 3-character rows ('.' = empty). */
    private static void shaped(String r0, String r1, String r2, BlockType output, int amount) {
        RECIPES.add(new ShapedRecipe(cells(r0, r1, r2), output, amount));
    }

    /** Registers a shapeless recipe that matches any arrangement of {@code ingredients}. */
    private static void shapeless(BlockType output, int amount, BlockType... ingredients) {
        RECIPES.add(new ShapelessRecipe(ingredients, output, amount));
    }

    /** Registers the three tool recipes for one material character (pickaxe/axe/sword). */
    private static void tools(char m, BlockType pickaxe, BlockType axe, BlockType sword) {
        shaped("" + m + m + m, ".S.", ".S.", pickaxe, 1);
        shaped("" + m + m + ".", "" + m + "S.", ".S.", axe, 1);
        shaped("." + m + ".", "." + m + ".", ".S.", sword, 1);
    }

    private static BlockType[] cells(String r0, String r1, String r2) {
        BlockType[] out = new BlockType[9];
        String[] rows = {r0, r1, r2};
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 3; c++) {
                out[r * 3 + c] = CHARS.get(rows[r].charAt(c));
            }
        }
        return out;
    }

    /** Finds the recipe matching the given 3x3 grid (row-major, null = empty), or null. */
    public static Recipe match(BlockType[] grid) {
        for (Recipe recipe : RECIPES) {
            if (recipe.matches(grid)) {
                return recipe;
            }
        }
        return null;
    }

    /** Shaped match: pattern may be placed anywhere in the grid, and the grid is also mirrored horizontally. */
    private static boolean matchesShaped(BlockType[] grid, BlockType[] pattern) {
        int maxR = 0, maxC = 0;
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 3; c++) {
                if (pattern[r * 3 + c] != null) {
                    maxR = Math.max(maxR, r);
                    maxC = Math.max(maxC, c);
                }
            }
        }
        // Mirror the grid (not the pattern) so the pattern's bounding box stays valid.
        BlockType[] mirroredGrid = mirrorHorizontal(grid);
        for (int dr = 0; dr + maxR < 3; dr++) {
            for (int dc = 0; dc + maxC < 3; dc++) {
                if (fits(grid, pattern, dr, dc) || fits(mirroredGrid, pattern, dr, dc)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** True if the pattern, placed with its top-left at (dr, dc), exactly covers the grid's items. */
    private static boolean fits(BlockType[] grid, BlockType[] pattern, int dr, int dc) {
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 3; c++) {
                int pr = r - dr, pc = c - dc;
                BlockType pat = (pr >= 0 && pc >= 0) ? pattern[pr * 3 + pc] : null;
                BlockType cell = grid[r * 3 + c];
                if (pat == null ? cell != null : pat != cell) {
                    return false;
                }
            }
        }
        return true;
    }

    private static BlockType[] mirrorHorizontal(BlockType[] pattern) {
        BlockType[] out = new BlockType[9];
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 3; c++) {
                out[r * 3 + c] = pattern[r * 3 + (2 - c)];
            }
        }
        return out;
    }

    /** Shapeless match: the grid and ingredients hold the same multiset of block types. */
    private static boolean matchesShapeless(BlockType[] grid, BlockType[] ingredients) {
        Map<BlockType, Integer> counts = new EnumMap<>(BlockType.class);
        for (BlockType b : grid) {
            if (b != null) counts.merge(b, 1, Integer::sum);
        }
        for (BlockType b : ingredients) {
            Integer remaining = counts.get(b);
            if (remaining == null || remaining == 0) {
                return false;
            }
            if (remaining == 1) {
                counts.remove(b);
            } else {
                counts.put(b, remaining - 1);
            }
        }
        return counts.isEmpty();
    }
}
