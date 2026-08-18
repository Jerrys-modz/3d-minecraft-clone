package com.minecraftclone.player;

import com.minecraftclone.world.BlockType;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Minecraft-style crafting for the player's 2x2 inventory grid where either the shape of the
 * ingredients (shaped recipes) or just their combination (shapeless recipes)
 * determines the output.
 * <p>
 * Adding a recipe is a one-liner - see the two registration helpers:
 * <pre>
 *   shaped("PP", "PP", BlockType.PLANKS_SLAB, 3);   // 2x2 pattern, '.' = empty
 *   shapeless(BlockType.GLASS, 1, BlockType.SAND, BlockType.SAND); // any arrangement
 * </pre>
 * Shaped recipes match like Minecraft's: the pattern may sit anywhere in the
 * grid, and it's also matched under horizontal mirroring. Shapeless recipes match on the multiset of
 * ingredients, ignoring arrangement.
 *
 * Note: This 2x2 grid is limited to simple recipes. Complex recipes requiring more space
 * would need a 3x3 crafting table (not yet implemented).
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
        CHARS.put('C', BlockType.COAL);
        CHARS.put('G', BlockType.GLASS);
        CHARS.put('T', BlockType.TORCH);
        CHARS.put('K', BlockType.STONE);
        CHARS.put('N', BlockType.SAND);
        CHARS.put('I', BlockType.IRON_INGOT);
        CHARS.put('D', BlockType.DIAMOND);
        CHARS.put('O', BlockType.OBSIDIAN);
        CHARS.put('L', BlockType.GLOWSTONE);
        CHARS.put('U', BlockType.WOOL);
        CHARS.put('V', BlockType.WOLF_PELT);
        CHARS.put('B', BlockType.BEAR_HIDE);

        // --- Shaped recipes: two 2-character rows ('.' = empty). ---
        // Simple 2x2 recipes for the player inventory crafting grid
        shaped("W.", "..", BlockType.PLANKS, 4);                  // log -> planks
        shaped("PP", "PP", BlockType.CRAFTING_TABLE, 1);          // 4 planks -> crafting table
        shaped("P.", "P.", BlockType.STICK, 4);                   // 2 planks vertical -> sticks
        shaped("C.", "S.", BlockType.TORCH, 4);                   // coal + stick -> torches
        shaped("PS", "PS", BlockType.DOOR, 1);                    // 4 planks + 2 sticks -> door
        shaped("PP", "..", BlockType.PLANKS_SLAB, 2);             // 2 planks -> 2 slabs
        shaped("KK", "..", BlockType.STONE_SLAB, 2);              // 2 stone -> 2 slabs
        shaped("W.", "W.", BlockType.WOOD_PICKAXE, 1);            // simple wooden pickaxe
        shaped("K.", "K.", BlockType.STONE_PICKAXE, 1);           // simple stone pickaxe
        shaped("I.", "I.", BlockType.IRON_PICKAXE, 1);            // simple iron pickaxe
        shaped("D.", "D.", BlockType.DIAMOND_PICKAXE, 1);         // simple diamond pickaxe

        // Simple 2x2 swords and tools
        shaped(".W", ".W", BlockType.WOOD_SWORD, 1);              // wooden sword
        shaped(".K", ".K", BlockType.STONE_SWORD, 1);             // stone sword
        shaped(".I", ".I", BlockType.IRON_SWORD, 1);              // iron sword
        shaped(".D", ".D", BlockType.DIAMOND_SWORD, 1);           // diamond sword

        // --- Shapeless recipes: any arrangement of the given ingredients. ---
        shapeless(BlockType.GLASS, 1, BlockType.SAND, BlockType.SAND);
        shapeless(BlockType.OBSIDIAN, 1, BlockType.LAVA_SOURCE, BlockType.WATER_SOURCE); // quench lava -> obsidian
    }

    private Crafting() {
    }

    /** Registers a shaped recipe from two 2-character rows ('.' = empty). */
    private static void shaped(String r0, String r1, BlockType output, int amount) {
        RECIPES.add(new ShapedRecipe(cells(r0, r1), output, amount));
    }

    /** Registers a shapeless recipe that matches any arrangement of {@code ingredients}. */
    private static void shapeless(BlockType output, int amount, BlockType... ingredients) {
        RECIPES.add(new ShapelessRecipe(ingredients, output, amount));
    }


    private static BlockType[] cells(String r0, String r1) {
        BlockType[] out = new BlockType[4];
        String[] rows = {r0, r1};
        for (int r = 0; r < 2; r++) {
            for (int c = 0; c < 2; c++) {
                out[r * 2 + c] = CHARS.get(rows[r].charAt(c));
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

    /** Shaped match: pattern may sit anywhere in the 2x2 grid, and the grid is also mirrored horizontally. */
    private static boolean matchesShaped(BlockType[] grid, BlockType[] pattern) {
        int maxR = 0, maxC = 0;
        for (int r = 0; r < 2; r++) {
            for (int c = 0; c < 2; c++) {
                if (pattern[r * 2 + c] != null) {
                    maxR = Math.max(maxR, r);
                    maxC = Math.max(maxC, c);
                }
            }
        }
        // Mirror the grid (not the pattern) so the pattern's bounding box stays valid.
        BlockType[] mirroredGrid = mirrorHorizontal(grid);
        for (int dr = 0; dr + maxR < 2; dr++) {
            for (int dc = 0; dc + maxC < 2; dc++) {
                if (fits(grid, pattern, dr, dc) || fits(mirroredGrid, pattern, dr, dc)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** True if the pattern, placed with its top-left at (dr, dc), exactly covers the grid's items. */
    private static boolean fits(BlockType[] grid, BlockType[] pattern, int dr, int dc) {
        for (int r = 0; r < 2; r++) {
            for (int c = 0; c < 2; c++) {
                int pr = r - dr, pc = c - dc;
                BlockType pat = (pr >= 0 && pr < 2 && pc >= 0 && pc < 2) ? pattern[pr * 2 + pc] : null;
                BlockType cell = grid[r * 2 + c];
                if (pat == null ? cell != null : pat != cell) {
                    return false;
                }
            }
        }
        return true;
    }

    private static BlockType[] mirrorHorizontal(BlockType[] pattern) {
        BlockType[] out = new BlockType[4];
        for (int r = 0; r < 2; r++) {
            for (int c = 0; c < 2; c++) {
                out[r * 2 + c] = pattern[r * 2 + (1 - c)];
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
