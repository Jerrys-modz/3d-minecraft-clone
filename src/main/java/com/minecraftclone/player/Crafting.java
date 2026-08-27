package com.minecraftclone.player;

import com.minecraftclone.world.BlockType;

import java.util.ArrayList;
import java.util.Collections;
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
    private static final List<Recipe> RECIPES_5x5 = new ArrayList<>();

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
        CHARS.put('Y', BlockType.CLAY_BALL);    // Y = claY ball
        CHARS.put('H', BlockType.WHEAT);        // H = wHeat
        // Phase 0.5: Tinkers' Construct ingredient shortcuts
        CHARS.put('Z', BlockType.SEARED_BRICK);       // Z = seared/sintered (kiln)
        // Steam Age: bronze machinery
        CHARS.put('F', BlockType.FURNACE);            // F = Furnace block
        CHARS.put('E', BlockType.BRONZE_INGOT);       // E = bronzE ingot
        CHARS.put('Q', BlockType.STEEL_INGOT);        // Q = Steel ingot

        // --- Shaped recipes: two 2-character rows ('.' = empty). ---
        // Simple 2x2 recipes for the player inventory crafting grid
        shaped2x2("W.", "..", BlockType.PLANKS, 4);                  // log -> planks
        shaped2x2("PP", "PP", BlockType.CRAFTING_TABLE, 1);          // 4 planks -> crafting table
        shaped2x2("P.", "P.", BlockType.STICK, 4);                   // 2 planks vertical -> sticks
        shaped2x2("C.", "S.", BlockType.TORCH, 4);                   // coal + stick -> torches

        // Register basic 2x2 recipes for 3x3 grids (so they work in crafting tables too)
        shaped3x3("W..", "...", "...", BlockType.PLANKS, 4);         // log -> planks
        shaped3x3("P..", "P..", "...", BlockType.STICK, 4);          // 2 planks vertical -> sticks
        shaped3x3("C..", "S..", "...", BlockType.TORCH, 4);          // coal + stick -> torches

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
        // Note: Glass is made in the furnace (smelting sand); obsidian forms in the world (lava + water)

        // --- 3x3 Crafting Table Recipes ---
        // Slabs: 3 blocks in a row
        shaped3x3("PPP", "...", "...", BlockType.PLANKS_SLAB, 6);                       // 3 planks -> 6 slabs
        shaped3x3("KKK", "...", "...", BlockType.STONE_SLAB, 6);                        // 3 stone -> 6 slabs

        // Doors: 2x3 arrangement (2 wide, 3 tall)
        shaped3x3("PP.", "PP.", "PP.", BlockType.DOOR, 1);                             // 6 planks -> 1 door

        // Tools (mirrored matching lets an axe be built either way round).
        tools('P', BlockType.WOOD_PICKAXE, BlockType.WOOD_AXE, BlockType.WOOD_SWORD,
                BlockType.WOOD_SHOVEL, BlockType.WOOD_HAMMER, BlockType.WOOD_BROADAXE);
        tools('K', BlockType.STONE_PICKAXE, BlockType.STONE_AXE, BlockType.STONE_SWORD,
                BlockType.STONE_SHOVEL, BlockType.STONE_HAMMER, BlockType.STONE_BROADAXE);
        tools('I', BlockType.IRON_PICKAXE, BlockType.IRON_AXE, BlockType.IRON_SWORD,
                BlockType.IRON_SHOVEL, BlockType.IRON_HAMMER, BlockType.IRON_BROADAXE);
        tools('D', BlockType.DIAMOND_PICKAXE, BlockType.DIAMOND_AXE, BlockType.DIAMOND_SWORD,
                BlockType.DIAMOND_SHOVEL, BlockType.DIAMOND_HAMMER, BlockType.DIAMOND_BROADAXE);

        // Armor: helmet (5), chestplate (8), leggings (7), boots (4) of each material.
        armor('P', BlockType.WOOD_HELMET, BlockType.WOOD_CHESTPLATE, BlockType.WOOD_LEGGINGS, BlockType.WOOD_BOOTS);
        armor('K', BlockType.STONE_HELMET, BlockType.STONE_CHESTPLATE, BlockType.STONE_LEGGINGS, BlockType.STONE_BOOTS);
        armor('I', BlockType.IRON_HELMET, BlockType.IRON_CHESTPLATE, BlockType.IRON_LEGGINGS, BlockType.IRON_BOOTS);
        armor('D', BlockType.DIAMOND_HELMET, BlockType.DIAMOND_CHESTPLATE, BlockType.DIAMOND_LEGGINGS, BlockType.DIAMOND_BOOTS);
        armor('U', BlockType.FUR_HELMET, BlockType.FUR_CHESTPLATE, BlockType.FUR_LEGGINGS, BlockType.FUR_BOOTS);
        armor('V', BlockType.WOLF_HELMET, BlockType.WOLF_CHESTPLATE, BlockType.WOLF_LEGGINGS, BlockType.WOLF_BOOTS);
        armor('B', BlockType.BEAR_HELMET, BlockType.BEAR_CHESTPLATE, BlockType.BEAR_LEGGINGS, BlockType.BEAR_BOOTS);

        // Stairs and doors are registered earlier (see "3x3 recipes from
        // crafting table"); trapdoor is new here.
        shaped3x3("PPP", "PPP", "...", BlockType.TRAPDOOR, 2);                           // 6 planks -> 2 trapdoors

        // --- Farming recipes ---
        // Hoes: two material on top, column of two sticks below (vanilla, mirrors for left-hand).
        shaped3x3("PP.", ".S.", ".S.", BlockType.WOOD_HOE, 1);
        shaped3x3("KK.", ".S.", ".S.", BlockType.STONE_HOE, 1);
        shaped3x3("II.", ".S.", ".S.", BlockType.IRON_HOE, 1);
        shaped3x3("DD.", ".S.", ".S.", BlockType.DIAMOND_HOE, 1);

        // Bone meal: crush one bone into 3 piles (inventory 2x2 or crafting table).
        shapeless2x2(BlockType.BONE_MEAL, 3, BlockType.BONES);
        shapeless3x3(BlockType.BONE_MEAL, 3, BlockType.BONES);

        // Clay canteen: 4 clay balls in a 2x2 (inventory grid). Crafting-table
        // recipe uses a distinct 3x3 vessel shape so it does not collide with
        // the seared-brick bootstrap (YY. / YY. / ...).
        shaped2x2("YY", "YY", BlockType.CLAY_CANTEEN, 1);  // 4 clay balls -> 1 canteen
        shaped3x3("Y.Y", "YYY", "Y.Y", BlockType.CLAY_CANTEEN, 1); // 7 clay balls -> 1 canteen

        // Bread: 3 wheat in a row (crafting-table recipe; a 2x2 grid cannot hold 3 in a row)
        shaped3x3("HHH", "...", "...", BlockType.BREAD, 1);

        // ---------------------------------------------------------------
        // Phase 0.5: Tinkers' Construct crafting station recipes
        // ---------------------------------------------------------------
        // Character key used in this section:
        //   'Z' = SEARED_BRICK (mnemonic: "Zapped in a kiln")

        // Seared Brick: fire clay balls in-world (Furnace: clay ball → seared brick).
        // Also allow crafting 2 seared bricks from 4 clay balls as an early-game bootstrap.
        // Use a 3x3 pattern to avoid conflict with the clay-canteen 2x2 recipe
        shaped3x3("YY.", "YY.", "...", BlockType.SEARED_BRICK, 2);         // 4 clay balls → 2 seared bricks

        // Smeltery Controller: 8 iron ingots in a ring around the centre (empty inside)
        shaped3x3("III", "I.I", "III", BlockType.SMELTERY_CONTROLLER, 1);

        // Seared Glass: seared brick + glass (2×1). Duplicate for 3x3 so it
        // works at a crafting table (same translation/mirroring as other 2x2 copies).
        shaped2x2("ZG", "..", BlockType.SEARED_GLASS, 2);          // seared brick + glass → 2 seared glass
        shaped3x3("ZG.", "...", "...", BlockType.SEARED_GLASS, 2);

        // Seared Tank: seared brick frame with glass inside (holds liquid)
        shaped3x3("ZGZ", "G.G", "ZGZ", BlockType.SEARED_TANK, 1); // frame + glass interior

        // Smeltery Drain: seared brick with iron at centre (the pour hole)
        shaped3x3("ZZZ", "ZIZ", "ZZZ", BlockType.SMELTERY_DRAIN, 2);

        // Casting Table: seared brick top + plank legs
        shaped3x3("ZZZ", ".P.", ".P.", BlockType.CASTING_TABLE, 1);

        // Casting Basin: U-shape of seared bricks (open top)
        shaped3x3("Z.Z", "Z.Z", "ZZZ", BlockType.CASTING_BASIN, 1);

        // Part Builder: planks workbench topped with seared brick
        shaped3x3("ZZZ", "PPP", "...", BlockType.PART_BUILDER, 1);

        // Tool Station: iron ingots on top, planks frame body
        shaped3x3("III", "PZP", "PPP", BlockType.TOOL_STATION, 1);

        // Iron bucket: three ingots in a V (transports lava for the smeltery)
        shaped3x3("I.I", ".I.", "...", BlockType.IRON_BUCKET, 1);

        // --- Steam Age: bronze alloy + machines ---
        // Bronze blend: 1 copper + 1 tin (dust or ingot forms), smelts to bronze.
        shapeless2x2(BlockType.BRONZE_DUST, 1, BlockType.COPPER_DUST, BlockType.TIN_DUST);
        shapeless3x3(BlockType.BRONZE_DUST, 1, BlockType.COPPER_DUST, BlockType.TIN_DUST);
        shapeless3x3(BlockType.BRONZE_DUST, 2, BlockType.COPPER_INGOT, BlockType.TIN_INGOT);

        // Steam Boiler: bronze ring, hollow center. Loads solid fuel,
        // builds steam while burning; adjacent steam machines draw from it.
        shaped3x3("EEE", "E.E", "EEE", BlockType.STEAM_BOILER, 1);

        // Steam Furnace: bronze shell around a furnace; runs off boiler steam
        // instead of fuel in its own slot.
        shaped3x3("EEE", "EFE", "EEE", BlockType.STEAM_FURNACE, 1);

        // Steam Macerator: bronze shell around a diamond grinding element.
        shaped3x3("EEE", "EDI", "EEE", BlockType.STEAM_MACERATOR, 1);

        // Steam Pipe: bronze column - connects machines to a boiler's steam.
        shaped3x3(".E.", ".E.", ".E.", BlockType.STEAM_PIPE_BRONZE, 6);

        // Wooden Steam Pipe: cheap starter tier, throttles flow to half rate.
        shaped3x3(".P.", ".P.", ".P.", BlockType.STEAM_PIPE_WOOD, 8);

        // Iron Steam Pipe: high-pressure tier, 1.5x throughput.
        shaped3x3(".I.", ".I.", ".I.", BlockType.STEAM_PIPE_IRON, 6);

        // Steel Ingot: 4 iron + 2 coal compressed under extreme heat.
        // Placeholder until a Blast Furnace machine exists.
        shaped3x3("ICI", ".C.", "ICI", BlockType.STEEL_INGOT, 1);

        // Steel Steam Pipe: top Steam Age tier, 2x throughput.
        shaped3x3(".Q.", ".Q.", ".Q.", BlockType.STEAM_PIPE_STEEL, 6);

        // Note: Tinkers' tool parts are shaped at the Part Builder and assembled at
        // the Tool Station — there are no crafting-table recipes for them.

        // --- 5x5 Advanced Crafting Table Recipes ---
        // These recipes require the full 5x5 grid and produce advanced items
        // Large storage: 5x5 chest/vault (25 planks -> 1 large chest)
        shaped5x5("PPPPP", "P...P", "P...P", "P...P", "PPPPP", BlockType.CHEST, 2);   // 20 planks -> 2 chests

        // Advanced beacon/tower: glowstone tower with diamond cap (10 glowstone + 1 diamond)
        shaped5x5("..G..", ".GGG.", "GGGGG", ".GGG.", "..D..", BlockType.LAMP, 3);    // glowstone tower -> 3 lamps
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

    /** Registers a shaped 5x5 recipe from five 5-character rows ('.' = empty). */
    private static void shaped5x5(String r0, String r1, String r2, String r3, String r4, BlockType output, int amount) {
        RECIPES_5x5.add(new ShapedRecipe(cells5x5(r0, r1, r2, r3, r4), output, amount));
    }

    /** Registers a shapeless 5x5 recipe that matches any arrangement of {@code ingredients}. */
    private static void shapeless5x5(BlockType output, int amount, BlockType... ingredients) {
        RECIPES_5x5.add(new ShapelessRecipe(ingredients, output, amount));
    }

    /** Registers 3x3 tool recipes (pickaxe, axe, sword, shovel, hammer, broadaxe) for a material. */
    private static void tools(char material, BlockType pickaxe, BlockType axe, BlockType sword,
                              BlockType shovel, BlockType hammer, BlockType broadaxe) {
        // Pickaxe: XXX / XSX / .S. (top 3 same, middle stick in center, stick below)
        shaped3x3(String.valueOf(material) + String.valueOf(material) + String.valueOf(material),
                  String.valueOf(material) + "S" + String.valueOf(material),
                  ".S.", pickaxe, 1);
        // Axe: XX. / XSX / .S. (left 2 top, stick in middle+center, stick below)
        shaped3x3(String.valueOf(material) + String.valueOf(material) + ".",
                  String.valueOf(material) + "S" + String.valueOf(material),
                  ".S.", axe, 1);
        // Sword: X / X / S (vertical line, 1 wide)
        shaped3x3(".X.", ".X.", ".S.", sword, 1);
        // Shovel: X / S / S (material on top, 2 sticks below)
        shaped3x3(".X.", ".S.", ".S.", shovel, 1);
        // Hammer: XX. / XSX / .S. (like pickaxe but wider)
        shaped3x3(String.valueOf(material) + String.valueOf(material) + ".",
                  String.valueOf(material) + "S" + String.valueOf(material),
                  ".S.", hammer, 1);
        // Broadaxe: .XX / XSX / .S. (right side heavy for axe, stick in middle, stick below)
        shaped3x3("." + String.valueOf(material) + String.valueOf(material),
                  String.valueOf(material) + "S" + String.valueOf(material),
                  ".S.", broadaxe, 1);
    }

    /** Registers 3x3 armor recipes (helmet, chestplate, leggings, boots) for a material. */
    private static void armor(char material, BlockType helmet, BlockType chestplate,
                              BlockType leggings, BlockType boots) {
        String m = String.valueOf(material);
        // Helmet: XXX / X.X / ... (5 pieces, hollow top)
        shaped3x3(m + m + m, m + "." + m, "...", helmet, 1);
        // Chestplate: X.X / XXX / XXX (8 pieces)
        shaped3x3(m + "." + m, m + m + m, m + m + m, chestplate, 1);
        // Leggings: XXX / X.X / X.X (7 pieces)
        shaped3x3(m + m + m, m + "." + m, m + "." + m, leggings, 1);
        // Boots: X.X / X.X / ... (4 pieces)
        shaped3x3(m + "." + m, m + "." + m, "...", boots, 1);
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

    private static BlockType[] cells5x5(String r0, String r1, String r2, String r3, String r4) {
        BlockType[] out = new BlockType[25];
        String[] rows = {r0, r1, r2, r3, r4};
        for (int r = 0; r < 5; r++) {
            for (int c = 0; c < 5; c++) {
                out[r * 5 + c] = CHARS.get(rows[r].charAt(c));
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

    /** Finds the recipe matching the given 5x5 grid (row-major, null = empty), or null. */
    public static Recipe match5x5(BlockType[] grid) {
        for (Recipe recipe : RECIPES_5x5) {
            if (recipe.matches(grid)) {
                return recipe;
            }
        }
        return null;
    }

    /**
     * Every registered recipe across all grid sizes, in registration order
     * (2x2 first, then 3x3, then 5x5) - the recipe book's index.
     */
    public static List<Recipe> allRecipes() {
        List<Recipe> all = new ArrayList<>(RECIPES_2x2.size() + RECIPES_3x3.size() + RECIPES_5x5.size());
        all.addAll(RECIPES_2x2);
        all.addAll(RECIPES_3x3);
        all.addAll(RECIPES_5x5);
        return all;
    }

    /** The grid size a recipe was registered for: 2, 3 or 5. */
    public static int gridSizeOf(Recipe recipe) {
        if (RECIPES_2x2.contains(recipe)) return 2;
        if (RECIPES_5x5.contains(recipe)) return 5;
        return 3;
    }

    /**
     * Every recipe that produces {@code output} across all grid sizes (the
     * recipe book's detail view). May be empty.
     */
    public static List<Recipe> recipesFor(BlockType output) {
        List<Recipe> matches = new ArrayList<>();
        for (Recipe recipe : allRecipes()) {
            if (recipe.output() == output) matches.add(recipe);
        }
        return Collections.unmodifiableList(matches);
    }

    /**
     * Finds the recipe matching the given 2x2 grid (row-major, null = empty), or null.
     * Enforces that the grid is exactly 4 cells (2x2).
     *
     * @param grid a 4-element array representing a 2x2 crafting grid
     * @return the matching recipe, or null if no recipe matches
     * @throws IllegalArgumentException if grid.length != 4
     */
    public static Recipe match(BlockType[] grid) {        if (grid == null || grid.length != 4) {
            throw new IllegalArgumentException("Grid must be a 2x2 grid (4 cells), got " + (grid == null ? "null" : grid.length));
        }
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
