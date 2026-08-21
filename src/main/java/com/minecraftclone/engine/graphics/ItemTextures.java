package com.minecraftclone.engine.graphics;

import com.minecraftclone.world.BlockType;
import com.minecraftclone.world.tinkers.TinkersItem;

import java.awt.image.BufferedImage;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.GL_TEXTURE0;
import static org.lwjgl.opengl.GL13.glActiveTexture;

/**
 * Generates each inventory-only item's ({@link BlockType#isItem}) 16x16
 * texture procedurally at startup, exactly like the block atlas - so the game
 * stays fully self-contained with no image assets to ship. Items aren't drawn
 * by chunk meshing, so they don't belong in the shared block atlas; each gets
 * its own small GL texture, painted by a per-item generator below.
 */
public class ItemTextures {

    private static final int SIZE = 16;

    private final Map<BlockType, Integer> textureIds = new EnumMap<>(BlockType.class);
    /** Per-item textures for Tinkers parts and tools, keyed by visual fingerprint. */
    private final Map<String, Integer> tinkersTextureIds = new HashMap<>();

    /** Generates (and uploads to the GPU) the texture for every item-type {@link BlockType}. */
    public void generate() {
        Random rnd = new Random(2024);
        for (BlockType type : BlockType.values()) {
            if (!type.isItem) continue;
            textureIds.put(type, GLTexture.upload(paint(type, rnd)));
        }
    }

    /** Paints the 16x16 texture for an item. */
    private static BufferedImage paint(BlockType type, Random rnd) {
        return switch (type) {
            case STICK -> paintStick();
            case APPLE -> paintApple();
            case BERRIES -> paintBerries();

            case WOOD_PICKAXE -> paintPickaxe(0xA9814F);
            case STONE_PICKAXE -> paintPickaxe(0x9E9E9E);
            case IRON_PICKAXE -> paintPickaxe(0xE8E8E8);
            case DIAMOND_PICKAXE -> paintPickaxe(0x5FE0E0);

            case WOOD_AXE -> paintAxe(0xA9814F);
            case STONE_AXE -> paintAxe(0x9E9E9E);
            case IRON_AXE -> paintAxe(0xE8E8E8);
            case DIAMOND_AXE -> paintAxe(0x5FE0E0);

            case WOOD_SWORD -> paintSword(0xA9814F);
            case STONE_SWORD -> paintSword(0x9E9E9E);
            case IRON_SWORD -> paintSword(0xE8E8E8);
            case DIAMOND_SWORD -> paintSword(0x5FE0E0);

            case WOOD_SHOVEL -> paintShovel(0xA9814F);
            case STONE_SHOVEL -> paintShovel(0x9E9E9E);
            case IRON_SHOVEL -> paintShovel(0xE8E8E8);
            case DIAMOND_SHOVEL -> paintShovel(0x5FE0E0);

            case WOOD_HAMMER -> paintHammer(0xA9814F);
            case STONE_HAMMER -> paintHammer(0x9E9E9E);
            case IRON_HAMMER -> paintHammer(0xE8E8E8);
            case DIAMOND_HAMMER -> paintHammer(0x5FE0E0);

            case WOOD_BROADAXE -> paintBroadaxe(0xA9814F);
            case STONE_BROADAXE -> paintBroadaxe(0x9E9E9E);
            case IRON_BROADAXE -> paintBroadaxe(0xE8E8E8);
            case DIAMOND_BROADAXE -> paintBroadaxe(0x5FE0E0);

            case IRON_INGOT -> paintIngot(0xE8E8E8);
            case GOLD_INGOT -> paintIngot(0xE8C93A);
            case DIAMOND -> paintGem(0x5FE0E0);
            case RUBY -> paintGem(0xE8253A);
            case SAPPHIRE -> paintGem(0x2848E8);
            case GREEN_SAPPHIRE -> paintGem(0x28C848);

            // Armor: each piece shares one painter per slot, tinted by material.
            case WOOD_HELMET -> paintHelmet(0xA9814F);
            case STONE_HELMET -> paintHelmet(0x9E9E9E);
            case IRON_HELMET -> paintHelmet(0xE8E8E8);
            case DIAMOND_HELMET -> paintHelmet(0x5FE0E0);
            case WOOD_CHESTPLATE -> paintChestplate(0xA9814F);
            case STONE_CHESTPLATE -> paintChestplate(0x9E9E9E);
            case IRON_CHESTPLATE -> paintChestplate(0xE8E8E8);
            case DIAMOND_CHESTPLATE -> paintChestplate(0x5FE0E0);
            case WOOD_LEGGINGS -> paintLeggings(0xA9814F);
            case STONE_LEGGINGS -> paintLeggings(0x9E9E9E);
            case IRON_LEGGINGS -> paintLeggings(0xE8E8E8);
            case DIAMOND_LEGGINGS -> paintLeggings(0x5FE0E0);
            case WOOD_BOOTS -> paintBoots(0xA9814F);
            case STONE_BOOTS -> paintBoots(0x9E9E9E);
            case IRON_BOOTS -> paintBoots(0xE8E8E8);
            case DIAMOND_BOOTS -> paintBoots(0x5FE0E0);

            case RAW_PORKCHOP -> paintMeat(0xE8A0A0, 0xC87878);
            case RAW_BEEF -> paintMeat(0xC04848, 0x8E2E2E);
            case MUTTON -> paintMeat(0xD87870, 0xB04848);
            case ROTTEN_FLESH -> paintMeat(0x9A8A6A, 0x7A6A50);
            case BONES -> paintBones();
            case BONE_MEAL -> paintBoneMeal();
            case COAL -> paintCoal();
            case WOOL -> paintWool();
            case WOLF_PELT -> paintPelt(0xA8A8A8, 0x8A8A8A);
            case BEAR_HIDE -> paintPelt(0xEFEDE6, 0xD8D5CA);

            // Fur armor - a warm, cream-white material tint.
            case FUR_HELMET -> paintHelmet(0xEFE6D8);
            case FUR_CHESTPLATE -> paintChestplate(0xEFE6D8);
            case FUR_LEGGINGS -> paintLeggings(0xEFE6D8);
            case FUR_BOOTS -> paintBoots(0xEFE6D8);

            // Wolf-pelt armor - a darker, sturdier grey fur.
            case WOLF_HELMET -> paintHelmet(0x9A9A9A);
            case WOLF_CHESTPLATE -> paintChestplate(0x9A9A9A);
            case WOLF_LEGGINGS -> paintLeggings(0x9A9A9A);
            case WOLF_BOOTS -> paintBoots(0x9A9A9A);

            // Polar-bear armor - thick white hide.
            case BEAR_HELMET -> paintHelmet(0xEFEDE6);
            case BEAR_CHESTPLATE -> paintChestplate(0xEFEDE6);
            case BEAR_LEGGINGS -> paintLeggings(0xEFEDE6);
            case BEAR_BOOTS -> paintBoots(0xEFEDE6);

            // GTNH Ores - early game (stone tier)
            case CRUSHED_COPPER -> paintCrushedOre(0xE8772F);
            case COPPER_DUST -> paintDustOre(0xE8772F);
            case COPPER_INGOT -> paintIngot(0xE8772F);
            case CRUSHED_TIN -> paintCrushedOre(0xD2E4EA);
            case TIN_DUST -> paintDustOre(0xD2E4EA);
            case TIN_INGOT -> paintIngot(0xC8D8E0);
            case CRUSHED_BAUXITE -> paintCrushedOre(0xC65A2A);
            case BAUXITE_DUST -> paintDustOre(0xC65A2A);
            case ALUMINUM_INGOT -> paintIngot(0xD8DCE8);
            case CRUSHED_ZINC -> paintCrushedOre(0xA8D890);
            case ZINC_DUST -> paintDustOre(0xA8D890);
            case ZINC_INGOT -> paintIngot(0xA8D890);
            case CRUSHED_LEAD -> paintCrushedOre(0x5C6A88);
            case LEAD_DUST -> paintDustOre(0x5C6A88);
            case LEAD_INGOT -> paintIngot(0x5C6A88);
            case CRUSHED_SILVER -> paintCrushedOre(0xF2F4FF);
            case SILVER_DUST -> paintDustOre(0xF2F4FF);
            case SILVER_INGOT -> paintIngot(0xF2F4FF);

            // GTNH Ores - mid-game (iron tier)
            case CRUSHED_NICKEL -> paintCrushedOre(0xE6DC9A);
            case NICKEL_DUST -> paintDustOre(0xE6DC9A);
            case NICKEL_INGOT -> paintIngot(0xE6DC9A);
            case CRUSHED_COBALT -> paintCrushedOre(0x3A62F0);
            case COBALT_DUST -> paintDustOre(0x3A62F0);
            case COBALT_INGOT -> paintIngot(0x3A62F0);
            case CRUSHED_TUNGSTEN -> paintCrushedOre(0x6A88A8);
            case TUNGSTEN_DUST -> paintDustOre(0x6A88A8);
            case TUNGSTEN_INGOT -> paintIngot(0x6A88A8);
            case CRUSHED_MOLYBDENUM -> paintCrushedOre(0x7A96B0);
            case MOLYBDENUM_DUST -> paintDustOre(0x7A96B0);
            case MOLYBDENUM_INGOT -> paintIngot(0x7A96B0);
            case CRUSHED_PLATINUM -> paintCrushedOre(0xFFF4C8);
            case PLATINUM_DUST -> paintDustOre(0xFFF4C8);
            case PLATINUM_INGOT -> paintIngot(0xFFF4C8);

            // GTNH Ores - advanced (diamond tier)
            case CRUSHED_CHROMIUM -> paintCrushedOre(0xC0E8DC);
            case CHROMIUM_DUST -> paintDustOre(0xC0E8DC);
            case CHROMIUM_INGOT -> paintIngot(0xC0E8DC);
            case CRUSHED_MANGANESE -> paintCrushedOre(0xC86890);
            case MANGANESE_DUST -> paintDustOre(0xC86890);
            case MANGANESE_INGOT -> paintIngot(0xC86890);
            case CRUSHED_VANADIUM -> paintCrushedOre(0x7A9A48);
            case VANADIUM_DUST -> paintDustOre(0x7A9A48);
            case VANADIUM_INGOT -> paintIngot(0x7A9A48);
            case CRUSHED_BERYLLIUM -> paintCrushedOre(0xD0E8A8);
            case BERYLLIUM_DUST -> paintDustOre(0xD0E8A8);
            case BERYLLIUM_INGOT -> paintIngot(0xD0E8A8);
            case CRUSHED_TITANIUM -> paintCrushedOre(0xB0C8E8);
            case TITANIUM_DUST -> paintDustOre(0xB0C8E8);
            case TITANIUM_INGOT -> paintIngot(0xB0C8E8);

            // GTNH Ores - late-game (endgame tier)
            case CRUSHED_URANIUM -> paintCrushedOre(0x8CFF40);
            case URANIUM_DUST -> paintDustOre(0x8CFF40);
            case URANIUM_INGOT -> paintIngot(0x8CFF40);
            case CRUSHED_THORIUM -> paintCrushedOre(0x7A5A48);
            case THORIUM_DUST -> paintDustOre(0x7A5A48);
            case THORIUM_INGOT -> paintIngot(0x7A5A48);
            case CRUSHED_PLUTONIUM -> paintCrushedOre(0x3A7040);
            case PLUTONIUM_DUST -> paintDustOre(0x3A7040);
            case PLUTONIUM_INGOT -> paintIngot(0x3A7040);
            case CRUSHED_IRIDIUM -> paintCrushedOre(0xE8DCFF);
            case IRIDIUM_DUST -> paintDustOre(0xE8DCFF);
            case IRIDIUM_INGOT -> paintIngot(0xE8DCFF);

            // GTNH Extended ore pack — crushed secondary forms (53 minerals)
            // Iron-bearing silicates / oxides
            case CRUSHED_MAGNETITE          -> paintCrushedOre(0x5A5A78); // black iron oxide
            case CRUSHED_HEMATITE           -> paintCrushedOre(0xC04030); // red iron oxide
            case CRUSHED_BROWN_LIMONITE     -> paintCrushedOre(0xC08040); // brown iron hydroxide
            case CRUSHED_YELLOW_LIMONITE    -> paintCrushedOre(0xF0C040); // yellow iron hydroxide
            case CRUSHED_BANDED_IRON        -> paintCrushedOre(0xC07040); // dark red-grey banded iron
            case CRUSHED_VANADIUM_MAGNETITE -> paintCrushedOre(0x505888); // dark grey-purple
            case CRUSHED_PYRITE             -> paintCrushedOre(0xF0D030); // fool's gold, golden
            case CRUSHED_ARSENOPYRITE       -> paintCrushedOre(0xA8B888); // silver-white arsenide
            // Copper-bearing
            case CRUSHED_CHALCOPYRITE  -> paintCrushedOre(0xD8B028); // golden copper sulphide
            case CRUSHED_TETRAHEDRITE  -> paintCrushedOre(0x506858); // grey-green copper sulphide
            case CRUSHED_MALACHITE     -> paintCrushedOre(0x20C060); // bright green copper carbonate
            // Lead / Zinc / Galena
            case CRUSHED_GALENA        -> paintCrushedOre(0x90A0C0); // blue-grey lead sulphide
            case CRUSHED_SPHALERITE    -> paintCrushedOre(0xC8A070); // resinous brown zinc sulphide
            // Nickel / Cobalt
            case CRUSHED_GARNIERITE    -> paintCrushedOre(0x70D070); // light green nickel silicate
            case CRUSHED_PENTLANDITE   -> paintCrushedOre(0xC8B060); // bronze nickel-iron sulphide
            case CRUSHED_COBALTITE     -> paintCrushedOre(0x5070D0); // blue-grey cobalt arsenide
            // Tin / Tungsten
            case CRUSHED_CASSITERITE   -> paintCrushedOre(0xC8B898); // brown-grey tin oxide
            case CRUSHED_SCHEELITE     -> paintCrushedOre(0xF0E090); // cream calcium tungstate
            case CRUSHED_WOLFRAMITE    -> paintCrushedOre(0x584868); // dark brown-black tungstate
            case CRUSHED_MOLYBDENITE   -> paintCrushedOre(0x6880A0); // silver-grey molybdenum sulphide
            case CRUSHED_FERBERITE     -> paintCrushedOre(0x403848); // dark iron tungstate
            // Chromium / Titanium
            case CRUSHED_CHROMITE      -> paintCrushedOre(0x384838); // dark green chrome iron oxide
            case CRUSHED_ILMENITE      -> paintCrushedOre(0x584850); // black titanium iron oxide
            case CRUSHED_RUTILE        -> paintCrushedOre(0xE87840); // red-brown titanium dioxide
            // Uranium / Thorium
            case CRUSHED_URANINITE     -> paintCrushedOre(0x508830); // dark green uranium oxide
            case CRUSHED_PITCHBLENDE   -> paintCrushedOre(0x3A4A28); // black pitchblende
            // Vanadium / Manganese
            case CRUSHED_VANADINITE    -> paintCrushedOre(0xE84828); // orange-red lead vanadate
            case CRUSHED_PYROLUSITE    -> paintCrushedOre(0x686878); // dark grey manganese dioxide
            // Rare earth / PGM
            case CRUSHED_MONAZITE      -> paintCrushedOre(0xE0A050); // brown-yellow rare earth phosphate
            case CRUSHED_BASTNASITE    -> paintCrushedOre(0xF0A040); // yellow-brown REE carbonate
            case CRUSHED_NEODYMIUM     -> paintCrushedOre(0xB090E8); // purple rare earth
            case CRUSHED_CERIUM        -> paintCrushedOre(0xF0D890); // pale yellow rare earth
            case CRUSHED_OSMIUM        -> paintCrushedOre(0x486878); // blue-silver PGM
            case CRUSHED_PALLADIUM     -> paintCrushedOre(0xE8D0B0); // warm silver PGM
            case CRUSHED_NAQUADAH          -> paintCrushedOre(0x2A8050); // dark green exotic
            case CRUSHED_NAQUADAH_ENRICHED -> paintCrushedOre(0x208020); // brighter green exotic
            case CRUSHED_TRINIUM           -> paintCrushedOre(0xA0D8D8); // silver-cyan exotic
            // Light metals / lithium minerals
            case CRUSHED_LEPIDOLITE    -> paintCrushedOre(0xE0A0D8); // lavender lithium mica
            case CRUSHED_LITHIUM       -> paintCrushedOre(0xE8F0FF); // pale icy lithium ore
            case CRUSHED_CINNABAR      -> paintCrushedOre(0xE82820); // bright red mercury sulphide
            // Gemstones
            case CRUSHED_RUBY          -> paintCrushedOre(0xFF2060);
            case CRUSHED_SAPPHIRE      -> paintCrushedOre(0x2060FF);
            case CRUSHED_GREEN_SAPPHIRE-> paintCrushedOre(0x20D070);
            case CRUSHED_PYROPE        -> paintCrushedOre(0xC01840); // deep red garnet
            case CRUSHED_SPESSARTINE   -> paintCrushedOre(0xF87820); // orange-red garnet
            // Non-metallic industrial minerals
            case CRUSHED_CALCITE       -> paintCrushedOre(0xFFF8F0); // white calcium carbonate
            case CRUSHED_OLIVINE       -> paintCrushedOre(0x88C040); // olive green silicate
            case CRUSHED_TALC          -> paintCrushedOre(0xE8F0D8); // pale green-white
            case CRUSHED_BENTONITE     -> paintCrushedOre(0xD8C8A0); // cream clay
            case CRUSHED_SODALITE      -> paintCrushedOre(0x4060E0); // deep blue lapis mineral
            case CRUSHED_LAZURITE      -> paintCrushedOre(0x2040C0); // lapis blue
            case CRUSHED_SALT          -> paintCrushedOre(0xFFFFFF); // white halite
            case CRUSHED_ROCK_SALT     -> paintCrushedOre(0xF0E0D0); // pink-white
            case CRUSHED_SALTPETER     -> paintCrushedOre(0xF8F0D8); // off-white potassium nitrate
            case CRUSHED_BORAX         -> paintCrushedOre(0xF8F0E8); // near-white sodium borate
            case CRUSHED_APATITE       -> paintCrushedOre(0x60B0E8); // sky blue phosphate
            case CRUSHED_PHOSPHATE     -> paintCrushedOre(0xD8D070); // yellow-green
            case CRUSHED_SULFUR        -> paintCrushedOre(0xFFF050); // bright yellow
            case CRUSHED_GRAPHITE      -> paintCrushedOre(0x2A2A32); // very dark grey
            case CRUSHED_PYROCHLORE    -> paintCrushedOre(0xA08060); // dark brown niobium ore

            // Impure ore piles (secondary small ore drops - 18 ores total)
            case IMPURE_COPPER -> paintImpurePile(0xE8772F);
            case IMPURE_TIN -> paintImpurePile(0xD2E4EA);
            case IMPURE_BAUXITE -> paintImpurePile(0xC65A2A);
            case IMPURE_ZINC -> paintImpurePile(0xA8D890);
            case IMPURE_LEAD -> paintImpurePile(0x5C6A88);
            case IMPURE_SILVER -> paintImpurePile(0xF2F4FF);
            case IMPURE_NICKEL -> paintImpurePile(0xE6DC9A);
            case IMPURE_COBALT -> paintImpurePile(0x3A62F0);
            case IMPURE_TUNGSTEN -> paintImpurePile(0x6A88A8);
            case IMPURE_MOLYBDENUM -> paintImpurePile(0x7A96B0);
            case IMPURE_PLATINUM -> paintImpurePile(0xFFF4C8);
            case IMPURE_CHROMIUM -> paintImpurePile(0xC0E8DC);
            case IMPURE_MANGANESE -> paintImpurePile(0xC86890);
            case IMPURE_BERYLLIUM -> paintImpurePile(0xD0E8A8);
            case IMPURE_TITANIUM -> paintImpurePile(0xB0C8E8);
            case IMPURE_URANIUM -> paintImpurePile(0x8CFF40);
            case IMPURE_PLUTONIUM -> paintImpurePile(0x3A7040);
            case IMPURE_IRIDIUM -> paintImpurePile(0xE8DCFF);

            // ----------------------------------------------------------------
            // Phase 0: Farming items
            // ----------------------------------------------------------------
            case SEEDS           -> paintSeeds();
            case WHEAT           -> paintWheat();
            case BREAD           -> paintBread();
            case POTATO          -> paintPotato(false);
            case POTATO_COOKED   -> paintPotato(true);
            case CARROT          -> paintCarrot();
            case CLAY_CANTEEN      -> paintCanteen(false);
            case CLAY_CANTEEN_FULL -> paintCanteen(true);
            case CLAY_BALL         -> paintClayBall();

            // Hoes - same silhouette painter, tinted by material
            case WOOD_HOE    -> paintHoe(0xA9814F);
            case STONE_HOE   -> paintHoe(0x9E9E9E);
            case IRON_HOE    -> paintHoe(0xE8E8E8);
            case DIAMOND_HOE -> paintHoe(0x5FE0E0);

            // ----------------------------------------------------------------
            // Phase 0.5: Tinkers' Construct sentinels.
            // Grey placeholders are used only when no TinkersItem payload is
            // available. Prefer {@link #bindTinkersItem(TinkersItem)} for
            // Part Builder / Tool Station items that carry a payload.
            // ----------------------------------------------------------------
            case TINKERS_PART -> paintPlaceholder(0x808080);
            case TINKERS_TOOL -> paintPlaceholder(0x404040);

            default -> throw new IllegalArgumentException("No item texture generator for " + type);
        };
    }

    private static BufferedImage blank() {
        return new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
    }

    /**
     * Grey placeholder tile for Tinkers' sentinel BlockTypes ({@code TINKERS_PART},
     * {@code TINKERS_TOOL}).  The real per-item texture is produced when the
     * {@code TinkersItem} payload is bound (future Part Builder / Tool Station GUI).
     */
    private static BufferedImage paintPlaceholder(int color) {
        BufferedImage img = blank();
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                // Checkerboard so sentinel items are visually distinct from air.
                if ((x + y) % 2 == 0) {
                    img.setRGB(x, y, 0xFF000000 | color);
                } else {
                    img.setRGB(x, y, 0xFF000000 | shade(color, 0.6f));
                }
            }
        }
        return img;
    }

    /** A short diagonal brown stick - the basic tool-crafting ingredient. */
    private static BufferedImage paintStick() {
        BufferedImage img = blank();
        drawThickLine(img, 3, 13, 12, 4, 0x8B5A2B);
        return img;
    }

    /** A Minecraft-style pickaxe: a claw head (center point + two down-curving prongs) on a diagonal handle. */
    private static BufferedImage paintPickaxe(int headColor) {
        BufferedImage img = blank();
        int handle = 0x6E4A2A, handleDark = 0x543A20;
        // Head bar (lit top, body, shaded bottom).
        for (int x = 2; x <= 13; x++) {
            img.setRGB(x, 2, 0xFF000000 | lighten(headColor));
            img.setRGB(x, 3, 0xFF000000 | headColor);
            img.setRGB(x, 4, 0xFF000000 | headColor);
            img.setRGB(x, 5, 0xFF000000 | shade(headColor, 0.8f));
        }
        // Center point (the claw's beak).
        for (int y = 6; y <= 8; y++) {
            img.setRGB(7, y, 0xFF000000 | shade(headColor, 1f - 0.07f * (y - 6)));
            img.setRGB(8, y, 0xFF000000 | shade(headColor, 1f - 0.07f * (y - 6)));
        }
        // Side prongs curving down and outward.
        for (int[] p : new int[][]{{3, 6}, {2, 7}, {1, 8}, {2, 8}, {12, 6}, {13, 7}, {14, 8}, {13, 8}}) {
            img.setRGB(p[0], p[1], 0xFF000000 | shade(headColor, 0.85f));
        }
        // Diagonal handle from the bottom-right up under the head.
        drawThickLine(img, 11, 15, 9, 9, handleDark);
        drawThickLine(img, 10, 15, 8, 9, handle);
        return img;
    }

    /** A Minecraft-style axe: a broad rounded blade with a curved cutting edge on a diagonal handle. */
    private static BufferedImage paintAxe(int headColor) {
        BufferedImage img = blank();
        int handle = 0x6E4A2A, handleDark = 0x543A20;
        // Blade: flat-ish back on the right, cutting edge bulging left.
        int[] edge = {6, 5, 4, 3, 2, 3, 4, 5, 6};
        for (int y = 1; y <= 9; y++) {
            int e = edge[y - 1];
            for (int x = e; x <= 10; x++) {
                int c = headColor;
                if (y == 1) {
                    c = lighten(headColor);
                } else if (y == 9) {
                    c = shade(headColor, 0.7f);
                }
                if (x == e) {
                    c = lighten(headColor);                    // bright cutting edge
                } else if (x == e + 1) {
                    c = shade(headColor, 0.9f);                // bevel shadow just inside
                }
                img.setRGB(x, y, 0xFF000000 | c);
            }
        }
        // Back edge highlight (right side).
        for (int y = 2; y <= 8; y++) {
            img.setRGB(10, y, 0xFF000000 | shade(headColor, 0.85f));
        }
        // Diagonal handle from the bottom-right up into the head.
        drawThickLine(img, 10, 15, 9, 7, handleDark);
        drawThickLine(img, 9, 15, 8, 7, handle);
        return img;
    }

    /** A Minecraft-style sword: a long blade with a fuller groove, a broad crossguard, and a gripped handle. */
    private static BufferedImage paintSword(int bladeColor) {
        BufferedImage img = blank();
        // Blade: 1px point, then a 3px body with a darker fuller down the middle.
        int[][] rows = {{8}, {7, 8}, {7, 8, 9}, {7, 8, 9}, {7, 8, 9}, {7, 8, 9}, {7, 8, 9}, {7, 8, 9}, {7, 8}};
        for (int y = 0; y < rows.length; y++) {
            int[] xs = rows[y];
            for (int x : xs) {
                int c;
                if (x == xs[0]) {
                    c = lighten(bladeColor);                    // lit edge
                } else if (x == xs[xs.length - 1]) {
                    c = shade(bladeColor, 0.85f);               // shaded edge
                } else {
                    c = shade(bladeColor, 0.92f);               // fuller groove
                }
                img.setRGB(x, y + 1, 0xFF000000 | c);
            }
        }
        // Crossguard: a broad dark bar with a lit band and an ornate center.
        for (int x = 3; x <= 12; x++) {
            img.setRGB(x, 10, 0xFF000000 | 0x4A4A4A);
            img.setRGB(x, 11, 0xFF000000 | 0x3A3A3A);
        }
        for (int x = 5; x <= 10; x++) {
            img.setRGB(x, 10, 0xFF000000 | 0x8A8A8A);
        }
        img.setRGB(8, 10, 0xFF000000 | 0xB0B0B0);
        img.setRGB(7, 11, 0xFF000000 | 0x2E2E2E);
        img.setRGB(8, 11, 0xFF000000 | 0x2E2E2E);
        img.setRGB(9, 11, 0xFF000000 | 0x2E2E2E);
        // Handle with a pommel.
        for (int y = 12; y <= 14; y++) {
            img.setRGB(7, y, 0xFF000000 | 0x6E4A2A);
            img.setRGB(8, y, 0xFF000000 | 0x8B5A2B);
        }
        img.setRGB(6, 12, 0xFF000000 | 0x4A3018);
        img.setRGB(9, 12, 0xFF000000 | 0x4A3018);
        img.setRGB(8, 15, 0xFF000000 | 0x4A3018);
        img.setRGB(7, 15, 0xFF000000 | 0x3A2613);
        return img;
    }

    /** A small round red apple with a stem - a foraged food item. */
    private static BufferedImage paintApple() {
        BufferedImage img = blank();
        double cx = 8, cy = 9.5, r = 4.5;
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                if (Math.hypot(x - cx, y - cy) <= r) {
                    img.setRGB(x, y, 0xFF000000 | 0xC62828);
                }
            }
        }
        img.setRGB(8, 4, 0xFF000000 | 0x5B3A21);
        img.setRGB(9, 3, 0xFF000000 | 0x4C8C2C);
        img.setRGB(10, 3, 0xFF000000 | 0x4C8C2C);
        return img;
    }

    /** A small cluster of dark berries on a stem - a foraged food item. */
    private static BufferedImage paintBerries() {
        BufferedImage img = blank();
        int berry = 0x7A2048;
        int[][] blobs = {{6, 7}, {9, 7}, {7, 9}, {10, 9}, {8, 11}};
        for (int[] o : blobs) {
            for (int dy = 0; dy < 2; dy++) {
                for (int dx = 0; dx < 2; dx++) {
                    img.setRGB(o[0] + dx, o[1] + dy, 0xFF000000 | berry);
                }
            }
        }
        for (int y = 4; y < 7; y++) {
            img.setRGB(8, y, 0xFF000000 | 0x4C8C2C);
        }
        return img;
    }

    /** A small rounded metal ingot bar, tinted by material - the smelted form of ore. */
    private static BufferedImage paintIngot(int color) {
        BufferedImage img = blank();
        for (int y = 6; y <= 10; y++) {
            for (int x = 3; x <= 12; x++) {
                boolean corner = (x == 3 || x == 12) && (y == 6 || y == 10);
                if (!corner) {
                    img.setRGB(x, y, 0xFF000000 | color);
                }
            }
        }
        // A light highlight along the top edge.
        for (int x = 4; x <= 11; x++) {
            img.setRGB(x, 7, 0xFF000000 | lighten(color));
        }
        return img;
    }

    /** Crushed ore: jagged irregular chunks with a ore-like texture. */
    private static BufferedImage paintCrushedOre(int color) {
        BufferedImage img = blank();
        // Paint irregular jagged chunks in the center.
        int[][] chunks = {
            {5, 5}, {6, 5}, {7, 6}, {8, 5},
            {4, 7}, {5, 8}, {6, 7}, {7, 8}, {8, 8}, {9, 7},
            {5, 10}, {6, 10}, {7, 11}, {8, 10}, {9, 10}
        };
        for (int[] p : chunks) {
            int c = ((p[0] + p[1]) % 2 == 0) ? color : shade(color, 0.85f);
            img.setRGB(p[0], p[1], 0xFF000000 | c);
            if (p[0] > 3 && p[0] < 12) img.setRGB(p[0] + 1, p[1], 0xFF000000 | shade(c, 0.9f));
        }
        return img;
    }

    /** Dust ore: fine powdery particles scattered across the item. */
    private static BufferedImage paintDustOre(int color) {
        BufferedImage img = blank();
        // Paint fine dust particles with some variation using color as seed.
        long seed = ((long) color * 31) ^ 0x9E3779B97F4A7C15L;
        Random rnd = new Random(seed);
        for (int y = 3; y <= 12; y++) {
            for (int x = 4; x <= 11; x++) {
                if (rnd.nextInt(100) < 45) {
                    int c = rnd.nextInt(2) == 0 ? color : shade(color, 0.75f);
                    img.setRGB(x, y, 0xFF000000 | c);
                }
            }
        }
        return img;
    }

    /** Impure ore pile: intermediate form between crushed ore and dust, with chunky irregular lumps. */
    private static BufferedImage paintImpurePile(int color) {
        BufferedImage img = blank();
        long seed = ((long) color * 37) ^ 0x8BADF00D5CAFEBAL;
        Random rnd = new Random(seed);
        // Paint irregular lumps representing partially processed ore.
        int lumps = 5 + rnd.nextInt(4);
        for (int lump = 0; lump < lumps; lump++) {
            int cx = 3 + rnd.nextInt(10);
            int cy = 3 + rnd.nextInt(10);
            int size = 1 + rnd.nextInt(2);
            for (int dy = -size; dy <= size; dy++) {
                for (int dx = -size; dx <= size; dx++) {
                    int px = cx + dx, py = cy + dy;
                    if (px >= 0 && px < SIZE && py >= 0 && py < SIZE) {
                        float d = dx * dx + dy * dy;
                        if (d <= size * size + 0.5f && rnd.nextFloat() < 0.7f) {
                            int c = rnd.nextFloat() < 0.4f ? shade(color, 0.85f) : color;
                            img.setRGB(px, py, 0xFF000000 | c);
                        }
                    }
                }
            }
        }
        return img;
    }

    /** A faceted gem (diamond shape), tinted by material - the smelted form of diamond ore. */
    private static BufferedImage paintGem(int color) {
        BufferedImage img = blank();
        int cx = 8, cy = 8;
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                if (Math.abs(x - cx) + Math.abs(y - cy) <= 5) {
                    img.setRGB(x, y, 0xFF000000 | color);
                }
            }
        }
        // A bright facet highlight.
        for (int i = 0; i < 3; i++) {
            img.setRGB(cx - 1 + i, cy - 2, 0xFF000000 | lighten(color));
        }
        return img;
    }

    /** A raw meat cut: a rounded chunk with a darker marbled band - the kill-drop food. */
    private static BufferedImage paintMeat(int meatColor, int marbledColor) {
        BufferedImage img = blank();
        for (int y = 4; y <= 11; y++) {
            for (int x = 2; x <= 13; x++) {
                // Rounded corners.
                boolean corner = (x == 2 || x == 13) && (y == 4 || y == 5 || y == 10 || y == 11)
                        || (x == 3 || x == 12) && (y == 4 || y == 11);
                if (!corner) {
                    img.setRGB(x, y, 0xFF000000 | meatColor);
                }
            }
        }
        // A couple of darker marbled streaks across the middle, and a light top edge.
        for (int x = 4; x <= 11; x++) {
            img.setRGB(x, 7, 0xFF000000 | marbledColor);
            img.setRGB(x + 1, 8, 0xFF000000 | marbledColor);
            img.setRGB(x, 4, 0xFF000000 | lighten(meatColor));
        }
        return img;
    }

    /** A dark, slightly irregular lump of coal with a couple of flat facets catching the light - the furnace fuel. */
    private static BufferedImage paintCoal() {
        BufferedImage img = blank();
        int cx = 8, cy = 8;
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                // An irregular lump (taller than wide, like a chunk of coal).
                double d = Math.hypot((x - cx) * 1.1, (y - cy) * 1.3);
                if (d <= 5.4) {
                    img.setRGB(x, y, 0xFF000000 | 0x2B2B2B);
                }
            }
        }
        // A few flat facets catching light, darkest at the bottom edge.
        for (int i = 0; i < 4; i++) {
            img.setRGB(cx - 1 + i, cy - 2, 0xFF000000 | 0x5A5A5A);
        }
        for (int i = 0; i < 3; i++) {
            img.setRGB(cx - 1 + i, cy - 1, 0xFF000000 | 0x4A4A4A);
        }
        img.setRGB(cx, cy, 0xFF000000 | 0x8A8A8A);
        img.setRGB(cx + 2, cy + 2, 0xFF000000 | 0x3A3A3A);
        return img;
    }

    /** A knobbly white bone - skeleton loot, a collectible for now. */
    private static BufferedImage paintBones() {
        BufferedImage img = blank();
        drawThickLine(img, 3, 13, 8, 6, 0xE8E0D0); // shaft
        img.setRGB(2, 12, 0xFF000000 | 0xE8E0D0);
        img.setRGB(3, 13, 0xFF000000 | 0xE8E0D0);
        img.setRGB(4, 14, 0xFF000000 | 0xE8E0D0);
        img.setRGB(11, 2, 0xFF000000 | 0xE8E0D0);
        img.setRGB(12, 3, 0xFF000000 | 0xE8E0D0);
        img.setRGB(13, 4, 0xFF000000 | 0xE8E0D0);
        return img;
    }

    /** A small heap of off-white powder — crushed bone used as fertilizer. */
    private static BufferedImage paintBoneMeal() {
        BufferedImage img = blank();
        // Cone-shaped pile: wide at the bottom, tapering up.
        int[] halfW = {0, 1, 2, 3, 4, 5, 5, 4, 3, 2};
        int baseY = 13;
        for (int i = 0; i < halfW.length; i++) {
            int y = baseY - i;
            int hw = halfW[i];
            for (int dx = -hw; dx <= hw; dx++) {
                int x = 8 + dx;
                boolean edge = Math.abs(dx) == hw;
                int c = edge ? 0xD8D0C0 : ((x + y) % 3 == 0 ? 0xFFF8EC : 0xE8E0D0);
                img.setRGB(x, y, 0xFF000000 | c);
            }
        }
        // A few brighter specks so it reads as powder, not a lump.
        img.setRGB(7, 11, 0xFF000000 | 0xFFFFFF);
        img.setRGB(9, 10, 0xFF000000 | 0xFFFFFF);
        img.setRGB(8, 8, 0xFF000000 | 0xF4ECD8);
        return img;
    }

    /** A fluffy clump of sheep's wool - the material fur armor is made from. */
    private static BufferedImage paintWool() {
        BufferedImage img = blank();
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                double d = Math.hypot(x - 8, y - 8);
                if (d <= 6.5) {
                    // A soft off-white with a few warm-shadowed tufts for depth.
                    int shade = (x * 31 + y * 17) % 7;
                    int c = shade < 2 ? 0xD8D2C4 : 0xF0ECE0;
                    img.setRGB(x, y, 0xFF000000 | c);
                }
            }
        }
        return img;
    }

    /** A rough fur pelt: a colored hide with darker speckles - the wolf/bear material. */
    private static BufferedImage paintPelt(int base, int dark) {
        BufferedImage img = blank();
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                double d = Math.hypot(x - 8, y - 8);
                if (d <= 6.5) {
                    int c = ((x * 7 + y * 13) % 5 == 0) ? dark : base;
                    img.setRGB(x, y, 0xFF000000 | c);
                }
            }
        }
        return img;
    }

    /** A shovel: a pointed spade head with a lit edge over a diagonal wood handle. */
    private static BufferedImage paintShovel(int headColor) {
        BufferedImage img = blank();
        int handle = 0x6E4A2A, handleDark = 0x543A20;
        // Diagonal handle from the bottom-right up to the head socket.
        drawThickLine(img, 10, 15, 9, 7, handleDark);
        drawThickLine(img, 9, 15, 8, 7, handle);
        // Spade head: a broad flat blade tapering to a point at the socket.
        int[] edge = {6, 5, 4, 4, 5};
        for (int y = 1; y <= 5; y++) {
            int e = edge[y - 1];
            for (int x = e; x <= 9; x++) {
                int c = y == 1 ? lighten(headColor) : (y == 5 ? shade(headColor, 0.8f) : headColor);
                if (x == e) c = lighten(headColor);        // lit cutting edge
                img.setRGB(x, y, 0xFF000000 | c);
            }
        }
        // Socket band where the head meets the handle.
        img.setRGB(8, 6, 0xFF000000 | shade(headColor, 0.9f));
        img.setRGB(9, 6, 0xFF000000 | shade(headColor, 0.9f));
        return img;
    }

    /** A hammer: a wide two-face striking head on a vertical wood handle. */
    private static BufferedImage paintHammer(int headColor) {
        BufferedImage img = blank();
        int handle = 0x6E4A2A, handleDark = 0x543A20;
        // Striking head: broad bar, lit top and end, shaded bottom.
        for (int x = 3; x <= 12; x++) {
            img.setRGB(x, 2, 0xFF000000 | lighten(headColor));
            img.setRGB(x, 3, 0xFF000000 | headColor);
            img.setRGB(x, 4, 0xFF000000 | headColor);
            img.setRGB(x, 5, 0xFF000000 | shade(headColor, 0.8f));
        }
        for (int y = 2; y <= 5; y++) {
            img.setRGB(3, y, 0xFF000000 | lighten(headColor));
            img.setRGB(12, y, 0xFF000000 | shade(headColor, 0.85f));
        }
        img.setRGB(4, 6, 0xFF000000 | headColor);
        img.setRGB(11, 6, 0xFF000000 | headColor);
        // Vertical handle with a grip.
        drawThickLine(img, 8, 6, 8, 13, handleDark);
        drawThickLine(img, 7, 6, 7, 13, handle);
        img.setRGB(7, 14, 0xFF000000 | 0x4A3018);
        img.setRGB(8, 14, 0xFF000000 | 0x4A3018);
        img.setRGB(7, 15, 0xFF000000 | 0x3A2613);
        img.setRGB(8, 15, 0xFF000000 | 0x3A2613);
        return img;
    }

    /** A broadaxe: a big, wide wedge blade (much broader than an axe) on a diagonal handle. */
    private static BufferedImage paintBroadaxe(int headColor) {
        BufferedImage img = blank();
        int handle = 0x6E4A2A, handleDark = 0x543A20;
        // Blade: a tall, wide wedge - flat back on the right, curved edge bulging left.
        int[] edge = {7, 5, 4, 2, 2, 3, 4, 6, 7};
        for (int y = 1; y <= 9; y++) {
            int e = edge[y - 1];
            for (int x = e; x <= 12; x++) {
                int c = headColor;
                if (y == 1) {
                    c = lighten(headColor);
                } else if (y == 9) {
                    c = shade(headColor, 0.7f);
                }
                if (x == e) {
                    c = lighten(headColor);                    // bright cutting edge
                } else if (x == e + 1) {
                    c = shade(headColor, 0.9f);                // bevel shadow
                }
                img.setRGB(x, y, 0xFF000000 | c);
            }
        }
        // Back edge highlight.
        for (int y = 2; y <= 8; y++) {
            img.setRGB(12, y, 0xFF000000 | shade(headColor, 0.85f));
        }
        // Long diagonal handle from the bottom-right into the head.
        drawThickLine(img, 10, 15, 8, 5, handleDark);
        drawThickLine(img, 9, 15, 7, 5, handle);
        return img;
    }

    /** A Minecraft-style helmet: a rounded dome with a face opening and a lit top edge. */
    private static BufferedImage paintHelmet(int color) {
        BufferedImage img = blank();
        // Dome: rows widening then narrowing, with a face gap at the bottom.
        int[][] rows = {{7, 8}, {6, 7, 8, 9}, {5, 6, 7, 8, 9, 10}, {5, 6, 7, 8, 9, 10}, {5, 6, 7, 8, 9, 10}};
        for (int y = 0; y < rows.length; y++) {
            int[] xs = rows[y];
            for (int x : xs) {
                int c = y == 0 ? lighten(color) : (y == rows.length - 1 ? shade(color, 0.8f) : color);
                if (x == xs[0] || x == xs[xs.length - 1]) c = shade(color, 0.85f);
                img.setRGB(x, y + 2, 0xFF000000 | c);
            }
        }
        // Face opening.
        for (int y = 6; y <= 8; y++) {
            for (int x = 5; x <= 10; x++) {
                img.setRGB(x, y, 0xFF000000 | 0x202020);
            }
        }
        // Brim.
        for (int x = 4; x <= 11; x++) {
            img.setRGB(x, 9, 0xFF000000 | shade(color, 0.7f));
            img.setRGB(x, 10, 0xFF000000 | shade(color, 0.6f));
        }
        return img;
    }

    /** A Minecraft-style chestplate: a broad torso with arm holes and a lit top. */
    private static BufferedImage paintChestplate(int color) {
        BufferedImage img = blank();
        int[][] rows = {{4, 5, 6, 7, 8, 9, 10, 11}, {4, 5, 6, 7, 8, 9, 10, 11}, {5, 6, 7, 8, 9, 10}, {5, 6, 7, 8, 9, 10}, {5, 6, 7, 8, 9, 10}, {5, 6, 7, 8, 9, 10}, {5, 6, 7, 8, 9, 10}, {5, 6, 7, 8, 9, 10}, {5, 6, 7, 8, 9, 10}};
        for (int y = 0; y < rows.length; y++) {
            int[] xs = rows[y];
            for (int x : xs) {
                int c = y == 0 ? lighten(color) : (y == rows.length - 1 ? shade(color, 0.8f) : color);
                if (x == xs[0] || x == xs[xs.length - 1]) c = shade(color, 0.85f);
                img.setRGB(x, y + 3, 0xFF000000 | c);
            }
        }
        // A center seam.
        for (int y = 4; y <= 11; y++) {
            img.setRGB(8, y, 0xFF000000 | shade(color, 0.7f));
        }
        return img;
    }

    /** A Minecraft-style pair of leggings: two legs from a hip band. */
    private static BufferedImage paintLeggings(int color) {
        BufferedImage img = blank();
        // Hip band.
        for (int x = 5; x <= 10; x++) {
            img.setRGB(x, 4, 0xFF000000 | lighten(color));
            img.setRGB(x, 5, 0xFF000000 | color);
        }
        // Two legs with a split down the middle.
        for (int y = 6; y <= 12; y++) {
            for (int x = 5; x <= 10; x++) {
                if (x == 7 || x == 8) {
                    img.setRGB(x, y, 0xFF000000 | 0x202020); // split
                } else {
                    int c = y == 12 ? shade(color, 0.7f) : (x == 5 || x == 10 ? shade(color, 0.85f) : color);
                    img.setRGB(x, y, 0xFF000000 | c);
                }
            }
        }
        return img;
    }

    /** A Minecraft-style boot: a short shaft with a wide foot, pointing left. */
    private static BufferedImage paintBoots(int color) {
        BufferedImage img = blank();
        // Shaft.
        for (int y = 5; y <= 9; y++) {
            for (int x = 7; x <= 11; x++) {
                img.setRGB(x, y, 0xFF000000 | (y == 5 ? lighten(color) : color));
            }
        }
        // Foot.
        for (int y = 10; y <= 12; y++) {
            for (int x = 4; x <= 12; x++) {
                img.setRGB(x, y, 0xFF000000 | (y == 12 ? shade(color, 0.7f) : (x == 4 ? lighten(color) : color)));
            }
        }
        return img;
    }

    private static int lighten(int color) {
        int r = Math.min(255, ((color >> 16) & 0xFF) + 40);
        int g = Math.min(255, ((color >> 8) & 0xFF) + 40);
        int b = Math.min(255, (color & 0xFF) + 40);
        return (r << 16) | (g << 8) | b;
    }

    /** Multiplies a 0xRRGGBB color's brightness by {@code f}. */
    private static int shade(int color, float f) {
        int r = Math.min(255, Math.max(0, Math.round(((color >> 16) & 0xFF) * f)));
        int g = Math.min(255, Math.max(0, Math.round(((color >> 8) & 0xFF) * f)));
        int b = Math.min(255, Math.max(0, Math.round((color & 0xFF) * f)));
        return (r << 16) | (g << 8) | b;
    }

    /** Plots a ~2px-thick straight line between two points. */
    private static void drawThickLine(BufferedImage img, int x0, int y0, int x1, int y1, int color) {
        int steps = Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0)) * 2 + 1;
        for (int i = 0; i <= steps; i++) {
            float t = steps == 0 ? 0 : (float) i / steps;
            int x = Math.round(x0 + (x1 - x0) * t);
            int y = Math.round(y0 + (y1 - y0) * t);
            for (int dx = 0; dx <= 1; dx++) {
                int px = x + dx, py = y;
                if (px >= 0 && px < SIZE && py >= 0 && py < SIZE) {
                    img.setRGB(px, py, 0xFF000000 | color);
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Phase 0: Farming item painters
    // -------------------------------------------------------------------------

    /** Wheat seeds: a tiny tan/brown cluster of dots suggesting a seed pouch. */
    private static BufferedImage paintSeeds() {
        BufferedImage img = blank();
        // A small tear-drop pouch shape
        int[][] dots = {{6,4},{7,3},{8,3},{9,4},{10,5},{10,6},{9,7},{8,8},{7,8},{6,7},{5,6},{5,5}};
        for (int[] d : dots) img.setRGB(d[0], d[1], 0xFF000000 | 0xC8A850);
        // Seed specks inside
        int[] sx = {7,8,9,7,9}; int[] sy = {5,5,5,6,6};
        for (int i = 0; i < sx.length; i++) img.setRGB(sx[i], sy[i], 0xFF000000 | 0x8A6020);
        return img;
    }

    /** Harvested wheat: a bundle of golden stalks. */
    private static BufferedImage paintWheat() {
        BufferedImage img = blank();
        // Three stalks
        for (int[] col : new int[][]{{5,0xC8A830},{8,0xD8B840},{11,0xC0A030}}) {
            int x = col[0], c = col[1];
            for (int y = 4; y <= 13; y++) {
                img.setRGB(x, y, 0xFF000000 | (y < 8 ? c : shade(c, 0.75f)));
            }
        }
        // Grain heads at top
        for (int x = 4; x <= 12; x++) {
            img.setRGB(x, 3, 0xFF000000 | 0xE8D060);
            if (x % 2 == 0) img.setRGB(x, 2, 0xFF000000 | 0xF0D870);
        }
        return img;
    }

    /** Bread: a golden-brown loaf shape. */
    private static BufferedImage paintBread() {
        BufferedImage img = blank();
        // Loaf body
        for (int y = 5; y <= 11; y++) {
            int l = (y == 5 || y == 11) ? 4 : 3;
            int r = (y == 5 || y == 11) ? 12 : 13;
            for (int x = l; x <= r; x++) {
                boolean edge = (y == 5 || y == 11 || x == l || x == r);
                img.setRGB(x, y, 0xFF000000 | (edge ? 0x8A5820 : 0xC8902A));
            }
        }
        // Golden top crust highlight
        for (int x = 4; x <= 12; x++) img.setRGB(x, 5, 0xFF000000 | 0xE8C060);
        return img;
    }

    /** Raw or baked potato. {@code cooked=true} darkens the skin. */
    private static BufferedImage paintPotato(boolean cooked) {
        BufferedImage img = blank();
        int skin = cooked ? 0x7A5018 : 0xC8962E;
        int flesh = cooked ? 0xA87030 : 0xF0C860;
        // Oval body
        for (int y = 4; y <= 12; y++) {
            int l = (y < 6 || y > 10) ? 5 : 4;
            int r = (y < 6 || y > 10) ? 11 : 12;
            for (int x = l; x <= r; x++) {
                boolean edge = (y == 4 || y == 12 || x == l || x == r);
                img.setRGB(x, y, 0xFF000000 | (edge ? skin : flesh));
            }
        }
        // Eyes (dark spots)
        img.setRGB(7, 6, 0xFF000000 | shade(skin, 0.5f));
        img.setRGB(9, 9, 0xFF000000 | shade(skin, 0.5f));
        return img;
    }

    /** Carrot: orange body with green leafy top. */
    private static BufferedImage paintCarrot() {
        BufferedImage img = blank();
        // Green leaves
        for (int y = 2; y <= 5; y++) {
            img.setRGB(6, y, 0xFF000000 | 0x2A9030);
            img.setRGB(8, y, 0xFF000000 | 0x3AA040);
            img.setRGB(10, y, 0xFF000000 | 0x2A9030);
        }
        // Orange carrot body (tapering downward)
        for (int y = 5; y <= 13; y++) {
            int half = Math.max(1, (14 - y) / 2);
            int cx = 8;
            for (int x = cx - half; x <= cx + half; x++) {
                boolean edge = (x == cx - half || x == cx + half);
                img.setRGB(x, y, 0xFF000000 | (edge ? 0xC05010 : 0xFF7020));
            }
        }
        return img;
    }

    /**
     * Clay canteen: a wide-bottomed jug shape. {@code full=true} draws a water
     * fill line inside the vessel to indicate it holds water.
     */
    private static BufferedImage paintCanteen(boolean full) {
        BufferedImage img = blank();
        int clay = 0xC8784A;
        int rim  = shade(clay, 0.7f);
        // Wide body
        for (int y = 4; y <= 13; y++) {
            int l = (y < 6) ? 6 : 4;
            int r = (y < 6) ? 10 : 12;
            for (int x = l; x <= r; x++) {
                boolean edge = (y == 4 || y == 13 || x == l || x == r);
                img.setRGB(x, y, 0xFF000000 | (edge ? rim : clay));
            }
        }
        // Neck / spout
        for (int y = 2; y <= 4; y++) {
            for (int x = 7; x <= 9; x++) {
                img.setRGB(x, y, 0xFF000000 | rim);
            }
        }
        // Water level inside when full
        if (full) {
            for (int y = 9; y <= 11; y++) {
                for (int x = 5; x <= 11; x++) {
                    if ((img.getRGB(x, y) & 0xFFFFFF) == (clay & 0xFFFFFF)) {
                        img.setRGB(x, y, 0xFF000000 | 0x4898D0);
                    }
                }
            }
        }
        return img;
    }

    /**
     * Hoe: a wooden handle with a right-angle metal head at the top.
     * The head color is the material; the handle is always wood-brown.
     */
    private static BufferedImage paintHoe(int headColor) {
        BufferedImage img = blank();
        int handle = 0x8B5E2A;
        // Diagonal handle
        drawThickLine(img, 5, 13, 10, 8, handle);
        // Horizontal blade at top
        for (int x = 6; x <= 12; x++) img.setRGB(x, 4, 0xFF000000 | headColor);
        for (int x = 6; x <= 12; x++) img.setRGB(x, 5, 0xFF000000 | shade(headColor, 0.75f));
        // Vertical socket connecting handle to blade
        img.setRGB(10, 5, 0xFF000000 | headColor);
        img.setRGB(10, 6, 0xFF000000 | headColor);
        img.setRGB(10, 7, 0xFF000000 | headColor);
        return img;
    }

    /** A small round blue-grey clay lump. */
    private static BufferedImage paintClayBall() {
        BufferedImage img = blank();
        int base = 0x8C9BA4;
        // Filled circle, radius ~5, centred at (8,8)
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                double dx = x - 7.5, dy = y - 7.5;
                if (dx * dx + dy * dy <= 25.0) {
                    // Simple shading: lighter upper-left, darker lower-right
                    float light = (float) (1.0 - (dx + dy) * 0.04);
                    light = Math.max(0.65f, Math.min(1.15f, light));
                    int r = Math.min(255, (int) (((base >> 16) & 0xFF) * light));
                    int g = Math.min(255, (int) (((base >> 8)  & 0xFF) * light));
                    int b = Math.min(255, (int) (( base        & 0xFF) * light));
                    img.setRGB(x, y, 0xFF000000 | (r << 16) | (g << 8) | b);
                }
            }
        }
        return img;
    }

    // =========================================================================
    // Phase 0.5 — Tinkers' Construct part painters
    // =========================================================================

    /** Head style constants for {@link #paintToolHead}. */
    private static final int HEAD_PICK = 0, HEAD_AXE = 1, HEAD_SWORD = 2, HEAD_SHOVEL = 3;

    /**
     * Paints a raw (unassembled) tool head item in the given material colour.
     * The silhouette matches the vanilla tool head but without a handle,
     * making it visually distinct from a complete tool.
     */
    private static BufferedImage paintToolHead(int headColor, int headStyle) {
        BufferedImage img = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        int lit  = lighten(headColor);
        int dark = darken(headColor);
        switch (headStyle) {
            case HEAD_PICK -> {
                // Claw shape, upper-right quadrant only (no handle)
                for (int x = 2; x <= 13; x++) {
                    img.setRGB(x, 3, 0xFF000000 | lit);
                    img.setRGB(x, 4, 0xFF000000 | headColor);
                    img.setRGB(x, 5, 0xFF000000 | dark);
                }
                img.setRGB(2,  6, 0xFF000000 | dark);
                img.setRGB(13, 6, 0xFF000000 | dark);
                img.setRGB(2,  7, 0xFF000000 | dark);
                img.setRGB(13, 7, 0xFF000000 | dark);
            }
            case HEAD_AXE -> {
                // Axe blade (upper-right crescent)
                for (int y = 2; y <= 9; y++) {
                    for (int x = Math.max(2, 8 - y); x <= 13; x++) {
                        img.setRGB(x, y, 0xFF000000 | headColor);
                    }
                    img.setRGB(13, y, 0xFF000000 | dark);
                }
                for (int x = 2; x <= 13; x++) img.setRGB(x, 2, 0xFF000000 | lit);
            }
            case HEAD_SWORD -> {
                // Blade (thin diagonal slab)
                for (int i = 0; i < 10; i++) {
                    img.setRGB(7 - i/2, 3 + i, 0xFF000000 | lit);
                    img.setRGB(8 - i/2, 3 + i, 0xFF000000 | headColor);
                    img.setRGB(9 - i/2, 3 + i, 0xFF000000 | dark);
                }
                // Guard cross-guard (horizontal bar)
                for (int x = 4; x <= 11; x++) img.setRGB(x, 12, 0xFF000000 | headColor);
            }
            case HEAD_SHOVEL -> {
                // Flat paddle head
                for (int y = 2; y <= 9; y++) {
                    for (int x = 5; x <= 10; x++) {
                        img.setRGB(x, y, 0xFF000000 | headColor);
                    }
                    img.setRGB(5,  y, 0xFF000000 | lit);
                    img.setRGB(10, y, 0xFF000000 | dark);
                }
                for (int x = 5; x <= 10; x++) img.setRGB(x, 2, 0xFF000000 | lit);
                for (int x = 5; x <= 10; x++) img.setRGB(x, 9, 0xFF000000 | dark);
            }
        }
        return img;
    }

    /**
     * Tool rod part texture — a short diagonal rod tinted by {@code color}.
     * When the material is wood the result looks like a plain stick; other
     * materials produce a tinted rod of the appropriate colour.
     */
    private static BufferedImage paintToolRod(int color) {
        BufferedImage img = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        int dark = shade(color, 0.6f);
        // Diagonal handle line, 7 pixels long, starting from lower-left
        for (int i = 0; i < 7; i++) {
            int x = 4 + i;
            int y = 12 - i;
            img.setRGB(x,     y, 0xFF000000 | color);
            img.setRGB(x + 1, y, 0xFF000000 | dark);
        }
        return img;
    }

    /**
     * Simple rectangular plate silhouette for Binding and Large Plate parts.
     * These have no dedicated silhouette painter, so a flat ingot-like bar
     * with the material's colour is used.
     */
    private static BufferedImage paintPlate(int color) {
        BufferedImage img = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        int lit  = lighten(color);
        int dark = darken(color);
        for (int y = 5; y <= 11; y++) {
            for (int x = 3; x <= 12; x++) {
                int c = (y == 5) ? lit : (y == 11) ? dark : color;
                if (x == 3)  c = shade(c, 0.85f);
                if (x == 12) c = dark;
                img.setRGB(x, y, 0xFF000000 | c);
            }
        }
        return img;
    }

    /** Darkens a colour by 40 points (used for shading tool-part items). */
    private static int darken(int color) {
        int r = Math.max(0, ((color >> 16) & 0xFF) - 40);
        int g = Math.max(0, ((color >>  8) & 0xFF) - 40);
        int b = Math.max(0, ( color        & 0xFF) - 40);
        return (r << 16) | (g << 8) | b;
    }

    /** Binds the given item's texture to texture unit 0. */
    public void bind(BlockType type) {
        Integer id = textureIds.get(type);
        if (id == null) {
            throw new IllegalArgumentException("No item texture loaded for " + type);
        }
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, id);
    }

    /**
     * Generates (lazily) and binds the texture for a specific {@link TinkersItem}
     * to texture unit 0.  Unlike {@link #bind(BlockType)}, which uses the grey
     * sentinel placeholder for the TINKERS_PART / TINKERS_TOOL sentinel types,
     * this method uses the item's actual material colour and part shape to produce
     * a distinct per-item texture.  Textures are cached by visual fingerprint so
     * each unique {@code (shape, material)} or {@code (kind, layers)} combination
     * is only generated once.
     *
     * @param item the Tinkers part or tool to render (must not be {@code null})
     */
    public void bindTinkersItem(TinkersItem item) {
        String key = tinkersKey(item);
        int id = tinkersTextureIds.computeIfAbsent(key,
                k -> GLTexture.upload(paintTinkersItem(item)));
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, id);
    }

    /**
     * A stable cache key for a Tinkers item that encodes only its visual
     * properties (shape + material for parts; kind + layers for tools).
     */
    private static String tinkersKey(TinkersItem item) {
        if (item instanceof TinkersItem.Part p) {
            return "P:" + p.shape.name() + ":" + p.material.name();
        } else if (item instanceof TinkersItem.Tool t) {
            StringBuilder sb = new StringBuilder("T:").append(t.kind.name());
            for (TinkersItem.ToolLayer l : t.layers) {
                sb.append(':').append(l.shape().name()).append('/').append(l.material().name());
            }
            return sb.toString();
        }
        return "?";
    }

    /** Dispatches to the right Tinkers texture painter. */
    private static BufferedImage paintTinkersItem(TinkersItem item) {
        if (item instanceof TinkersItem.Part p) return paintTinkersPart(p);
        if (item instanceof TinkersItem.Tool t) return paintTinkersTool(t);
        return paintPlaceholder(0x808080);
    }

    /**
     * Tool-part item texture: head silhouette tinted by material colour, or a
     * rod / generic shape for non-head parts.  Uses {@link ToolPartType#paintStyle}
     * which already matches the {@code HEAD_*} constants (0=PICK, 1=AXE, 2=SWORD,
     * 3=SHOVEL) so no extra mapping is needed.
     */
    private static BufferedImage paintTinkersPart(TinkersItem.Part part) {
        int color = part.color();
        int style = part.shape.paintStyle;
        // Styles 0-3 are head shapes that map 1:1 to HEAD_PICK/AXE/SWORD/SHOVEL.
        if (style >= HEAD_PICK && style <= HEAD_SHOVEL) {
            return paintToolHead(color, style);
        }
        // Rod shapes (TOOL_ROD, TOUGH_ROD) share the rod painter.
        if (part.shape.isRod()) {
            return paintToolRod(color);
        }
        // Binding (style 5) and Large Plate (style 6): use a simple plate silhouette.
        return paintPlate(color);
    }

    /**
     * Assembled-tool item texture: full tool silhouette tinted by the head layer's
     * material colour (the same colour logic as vanilla tier-based tools).
     */
    private static BufferedImage paintTinkersTool(TinkersItem.Tool tool) {
        int headColor = tool.color();
        return switch (tool.kind) {
            case PICKAXE  -> paintPickaxe(headColor);
            case AXE      -> paintAxe(headColor);
            case SWORD    -> paintSword(headColor);
            case SHOVEL   -> paintShovel(headColor);
            case HAMMER   -> paintHammer(headColor);
            case BROADAXE -> paintBroadaxe(headColor);
            default       -> paintPlaceholder(headColor);
        };
    }

    public void destroy() {
        for (int id : textureIds.values())        glDeleteTextures(id);
        for (int id : tinkersTextureIds.values()) glDeleteTextures(id);
    }
}
