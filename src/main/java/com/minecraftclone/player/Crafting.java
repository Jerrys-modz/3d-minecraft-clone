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
            // Determine grid dimensions from array size
            int gridSize = grid.length;
            int width = (gridSize == 4) ? 2 : (gridSize == 9) ? 3 : 0;
            int height = width;
            if (width == 0) return false;
            // Pattern must match grid size
            if (pattern.length != gridSize) return false;
            return matchesShaped(grid, pattern, width, height);
        }
    }

    public record ShapelessRecipe(BlockType[] ingredients, BlockType output, int outputAmount) implements Recipe {
        @Override
        public boolean matches(BlockType[] grid) {
            return matchesShapeless(grid, ingredients);
        }
    }

    private static final List<Recipe> RECIPES_2x2 = new ArrayList<>();
    private static final List<Recipe> RECIPES_3x3 = new ArrayList<>();

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
        shaped2x2("W.", "..", BlockType.PLANKS, 4);                  // log -> planks
        shaped2x2("PP", "PP", BlockType.CRAFTING_TABLE, 1);          // 4 planks -> crafting table
        shaped2x2("P.", "P.", BlockType.STICK, 4);                   // 2 planks vertical -> sticks
        shaped2x2("C.", "S.", BlockType.TORCH, 4);                   // coal + stick -> torches
        shaped2x2("PS", "PS", BlockType.DOOR, 1);                    // 4 planks + 2 sticks -> door
        shaped2x2("PP", "..", BlockType.PLANKS_SLAB, 2);             // 2 planks -> 2 slabs
        shaped2x2("KK", "..", BlockType.STONE_SLAB, 2);              // 2 stone -> 2 slabs
        shaped2x2("W.", "W.", BlockType.WOOD_PICKAXE, 1);            // simple wooden pickaxe
        shaped2x2("K.", "K.", BlockType.STONE_PICKAXE, 1);           // simple stone pickaxe
        shaped2x2("I.", "I.", BlockType.IRON_PICKAXE, 1);            // simple iron pickaxe
        shaped2x2("D.", "D.", BlockType.DIAMOND_PICKAXE, 1);         // simple diamond pickaxe

        // 2x2 swords (distinct from pickaxes with mirrored vertical orientation)
        shaped2x2(".W", ".W", BlockType.WOOD_SWORD, 1);              // wooden sword
        shaped2x2(".K", ".K", BlockType.STONE_SWORD, 1);             // stone sword
        shaped2x2(".I", ".I", BlockType.IRON_SWORD, 1);              // iron sword
        shaped2x2(".D", ".D", BlockType.DIAMOND_SWORD, 1);           // diamond sword

        // 3x3 recipes from crafting table
        // Stairs: a 6-material "K.. / KK. / KKK" wedge -> 4 stairs.
        shaped3x3("K..", "KK.", "KKK", BlockType.STONE_STAIRS, 4);
        shaped3x3("P..", "PP.", "PPP", BlockType.PLANKS_STAIRS, 4);
        // Fence: 4 planks + 2 sticks (PSP / PSP / ...) -> 3 fence posts.
        shaped3x3("PSP", "PSP", "...", BlockType.WOODEN_FENCE, 3);
        // Bed: 3 wool on top, 2 planks below -> 1 bed
        shaped3x3("UUU", "PPP", "...", BlockType.BED, 1);

        // Dimension portals: an obsidian ring frames a swirling portal. Obsidian
        // itself is made by quenching a lava source with a water source (see
        // shapeless below), so the Nether is reachable from raw overworld finds.
        shaped3x3("OOO", "O.O", "OOO", BlockType.NETHER_PORTAL, 1); // obsidian ring -> nether portal
        shaped3x3("OLO", "L.L", "OLO", BlockType.END_PORTAL, 1);    // obsidian + glowstone ring -> end portal

        // --- Shapeless recipes: any arrangement of the given ingredients. ---
        shapeless2x2(BlockType.GLASS, 1, BlockType.SAND, BlockType.SAND);
        shapeless2x2(BlockType.OBSIDIAN, 1, BlockType.LAVA_SOURCE, BlockType.WATER_SOURCE); // quench lava -> obsidian

        // --- 3x3 Crafting Table Recipes ---
        // Complex armor recipes
        shaped3x3("UUU", "U.U", "...", BlockType.FUR_HELMET, 1);                         // 5 wool -> helmet
        shaped3x3("VVV", "V.V", "VVV", BlockType.WOLF_CHESTPLATE, 1);                    // 8 wolf pelt -> chestplate
        shaped3x3("BBB", "B.B", "BBB", BlockType.BEAR_CHESTPLATE, 1);                    // 8 bear hide -> chestplate
        shaped3x3("III", "I.I", "III", BlockType.IRON_CHESTPLATE, 1);                    // 8 iron -> chestplate
        shaped3x3("DDD", "D.D", "...", BlockType.DIAMOND_HELMET, 1);                     // 5 diamond -> helmet
        shaped3x3("PPP", "P.P", "P.P", BlockType.WOOD_LEGGINGS, 1);                       // 7 planks -> leggings
        shaped3x3("KKK", "K.K", "K.K", BlockType.STONE_BOOTS, 1);                        // 6 stone -> boots

        // Tool recipes with mirroring
        shaped3x3("II.", "IS.", ".S.", BlockType.IRON_AXE, 1);                           // iron axe

        // Stairs (wedge pattern: K.. / KK. / KKK)
        shaped3x3("K..", "KK.", "KKK", BlockType.STONE_STAIRS, 4);                       // 6 stone -> 4 stairs
        shaped3x3("P..", "PP.", "PPP", BlockType.PLANKS_STAIRS, 4);                      // 6 planks -> 4 stairs

        // Door and trapdoor
        shaped3x3("PP.", "PP.", "PP.", BlockType.DOOR, 1);                               // 6 planks -> door
        shaped3x3("PPP", "PPP", "...", BlockType.TRAPDOOR, 2);                           // 6 planks -> 2 trapdoors

        // Fence
        shaped3x3("PSP", "PSP", "...", BlockType.WOODEN_FENCE, 3);                       // 4 planks + 2 sticks -> 3 fence
    }

    private Crafting() {
    }

    /** Registers a shaped 2x2 recipe from two 2-character rows ('.' = empty). */
    private static void shaped2x2(String r0, String r1, BlockType output, int amount) {
        RECIPES_2x2.add(new ShapedRecipe(cells2x2(r0, r1), output, amount));
    }

    /** Registers a shaped 3x3 recipe from three 3-character rows ('.' = empty). */
    private static void shaped3x3(String r0, String r1, String r2, BlockType output, int amount) {
        RECIPES_3x3.add(new ShapedRecipe(cells3x3(r0, r1, r2), output, amount));
    }

    /** Registers a shapeless 2x2 recipe that matches any arrangement of {@code ingredients}. */
    private static void shapeless2x2(BlockType output, int amount, BlockType... ingredients) {
        RECIPES_2x2.add(new ShapelessRecipe(ingredients, output, amount));
    }

    /** Registers a shapeless 3x3 recipe that matches any arrangement of {@code ingredients}. */
    private static void shapeless3x3(BlockType output, int amount, BlockType... ingredients) {
        RECIPES_3x3.add(new ShapelessRecipe(ingredients, output, amount));
    }


    private static BlockType[] cells2x2(String r0, String r1) {
        BlockType[] out = new BlockType[4];
        String[] rows = {r0, r1};
        for (int r = 0; r < 2; r++) {
            for (int c = 0; c < 2; c++) {
                out[r * 2 + c] = CHARS.get(rows[r].charAt(c));
            }
        }
        return out;
    }

    private static BlockType[] cells3x3(String r0, String r1, String r2) {
        BlockType[] out = new BlockType[9];
        String[] rows = {r0, r1, r2};
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 3; c++) {
                out[r * 3 + c] = CHARS.get(rows[r].charAt(c));
            }
        }
        return out;
    }

    /** Finds the recipe matching the given 2x2 grid (row-major, null = empty), or null. */
    public static Recipe match2x2(BlockType[] grid) {
        for (Recipe recipe : RECIPES_2x2) {
            if (recipe.matches(grid)) {
                return recipe;
            }
        }
        return null;
    }

    /** Finds the recipe matching the given 3x3 grid (row-major, null = empty), or null. */
    public static Recipe match3x3(BlockType[] grid) {
        for (Recipe recipe : RECIPES_3x3) {
            if (recipe.matches(grid)) {
                return recipe;
            }
        }
        return null;
    }

    /** Finds the recipe matching the given grid (row-major, null = empty), or null. Defaults to 2x2. */
    public static Recipe match(BlockType[] grid) {
        return match2x2(grid);
    }

    /** Shaped match: pattern may sit anywhere in the grid, and the grid is also mirrored horizontally. */
    private static boolean matchesShaped(BlockType[] grid, BlockType[] pattern, int width, int height) {
        int maxR = 0, maxC = 0;
        for (int r = 0; r < height; r++) {
            for (int c = 0; c < width; c++) {
                if (pattern[r * width + c] != null) {
                    maxR = Math.max(maxR, r);
                    maxC = Math.max(maxC, c);
                }
            }
        }
        // Mirror the grid (not the pattern) so the pattern's bounding box stays valid.
        BlockType[] mirroredGrid = mirrorHorizontal(grid, width, height);
        for (int dr = 0; dr + maxR < height; dr++) {
            for (int dc = 0; dc + maxC < width; dc++) {
                if (fits(grid, pattern, dr, dc, width, height) || fits(mirroredGrid, pattern, dr, dc, width, height)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** True if the pattern, placed with its top-left at (dr, dc), exactly covers the grid's items. */
    private static boolean fits(BlockType[] grid, BlockType[] pattern, int dr, int dc, int width, int height) {
        for (int r = 0; r < height; r++) {
            for (int c = 0; c < width; c++) {
                int pr = r - dr, pc = c - dc;
                BlockType pat = (pr >= 0 && pr < height && pc >= 0 && pc < width) ? pattern[pr * width + pc] : null;
                BlockType cell = grid[r * width + c];
                if (pat == null ? cell != null : pat != cell) {
                    return false;
                }
            }
        }
        return true;
    }

    private static BlockType[] mirrorHorizontal(BlockType[] pattern, int width, int height) {
        BlockType[] out = new BlockType[width * height];
        for (int r = 0; r < height; r++) {
            for (int c = 0; c < width; c++) {
                out[r * width + c] = pattern[r * width + (width - 1 - c)];
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
