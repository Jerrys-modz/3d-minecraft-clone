package com.minecraftclone.engine.graphics;

import java.awt.image.BufferedImage;
import java.util.Random;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.GL_TEXTURE0;
import static org.lwjgl.opengl.GL13.glActiveTexture;

/**
 * A small procedurally-generated block texture atlas, laid out on an 8x8 grid
 * of 16x16 pixel tiles (128x128 total). Generating the art at runtime keeps
 * blocks fully self-contained with no external image assets to ship - and,
 * unlike items, blocks genuinely benefit from living on one shared sheet
 * since chunk meshing batches many block faces into a single draw call.
 * (Inventory-only items are procedurally generated too, each into its own
 * small GL texture - see {@link ItemTextures} - and the HUD's text font lives
 * in its own small atlas - see {@link FontAtlas} - since neither is a block.)
 * <p>
 * Tile indices match {@link com.minecraftclone.world.BlockType}'s
 * topTile/sideTile/bottomTile fields. Not every tile in the 8x8 grid is
 * painted - some indices are left over from tiles that moved out to their
 * own assets - unpainted tiles are simply fully transparent and unused.
 * Tiles for cross-shaped decoration blocks (grass/flowers/berry bush) are
 * painted onto a transparent background and rely on the chunk fragment
 * shader's alpha-cutout discard.
 * <p>
 * Textures are painted with seamless 2-octave value noise (see {@link #fbm})
 * rather than per-pixel static: each material gets a coherent, organic grain,
 * a light-from-above tint, and its own detail pass (pebbles, bark ridges,
 * wood grain, ore highlights...), with a per-tile noise offset so identical
 * materials never line up into a visible repeating grid.
 */
public class TextureAtlas {

    public static final int GRID = 32;
    public static final int TILE_PX = 16;
    public static final int ATLAS_PX = GRID * TILE_PX;

    /** Tile index of the alpha-cutout leaves texture used when the "see-through leaves" setting is on. */
    public static final int LEAVES_CUTOUT_TILE = 24;
    /** Pink blossom cutout — cherry leaves must not share the green oak holes. */
    public static final int CHERRY_LEAVES_CUTOUT_TILE = 77;
    /** Tile index of the full-cube lamp texture. */
    public static final int LAMP_TILE = 25;
    /** Tile index of the furnace's front/side face. */
    public static final int FURNACE_TILE = 26;
    /** Tile index of the crafting table's workbench face. */
    public static final int CRAFTING_TABLE_TILE = 48;
    /** Tile index of the furnace's front face when it's actively burning - the mouth glows orange. */
    public static final int FURNACE_LIT_TILE = 49;
    /** Tile index of the chest's front face (lid, body, brass lock). */
    public static final int CHEST_TILE = 50;
    /** Tile index of the chest's lid, viewed from above. */
    public static final int CHEST_TOP_TILE = 64;
    /** Tile index of the chest's side (lid seam, no lock). */
    public static final int CHEST_SIDE_TILE = 65;
    /** Tile index of the chest's underside. */
    public static final int CHEST_BOTTOM_TILE = 66;
    /** Left half of a double/quad chest front (lock sits on the join). */
    public static final int CHEST_DOUBLE_FRONT_L = 244;
    /** Right half of a double/quad chest front. */
    public static final int CHEST_DOUBLE_FRONT_R = 245;
    /** Left half of a double-chest lid (latch on the join). */
    public static final int CHEST_DOUBLE_TOP_L = 246;
    /** Right half of a double-chest lid. */
    public static final int CHEST_DOUBLE_TOP_R = 247;
    /** Left half of a quad-chest back-row lid (no latch). */
    public static final int CHEST_DOUBLE_TOP_PLAIN_L = 248;
    /** Right half of a quad-chest back-row lid. */
    public static final int CHEST_DOUBLE_TOP_PLAIN_R = 249;
    /** Left half of a double/quad chest back. */
    public static final int CHEST_DOUBLE_BACK_L = 250;
    /** Right half of a double/quad chest back. */
    public static final int CHEST_DOUBLE_BACK_R = 251;
    /** Left half of a double/quad chest underside. */
    public static final int CHEST_DOUBLE_BOTTOM_L = 252;
    /** Right half of a double/quad chest underside. */
    public static final int CHEST_DOUBLE_BOTTOM_R = 253;
    /** First of {@link #DESTROY_STAGE_COUNT} accumulating crack overlays (hold-to-break). */
    public static final int DESTROY_STAGE_TILE = 67;
    public static final int DESTROY_STAGE_COUNT = 10;
    /** Tile index of the barrel's face. */
    public static final int BARREL_TILE = 51;
    /** Tile index of the lightning-lit fire cross. */
    public static final int FIRE_TILE = 52;

    private int textureId;

    public void generate() {
        textureId = GLTexture.upload(buildImage());
    }

    /** Builds the CPU-side atlas image without touching the GPU - split out so tooling/tests can inspect the art directly. */
    public BufferedImage buildImage() {
        BufferedImage image = new BufferedImage(ATLAS_PX, ATLAS_PX, BufferedImage.TYPE_INT_ARGB);
        Random rnd = new Random(1337);

        // --- Core terrain ---
        paintGrassTop(image, 1, rnd);
        paintFringedSide(image, 2, rnd, 0x63B034, 0x3E7A24);            // grass side (dirt + grass fringe)
        paintDirt(image, 3, rnd);
        paintStone(image, 4, rnd);
        paintSand(image, 5, rnd);
        paintFluidTile(image, 6, rnd, 0x4FCBE0, 0x0C3D73, 195, 0xE0FBFF); // water (richer teal-blue, was a flatter grey-blue)
        paintLogSide(image, 7, rnd);
        paintLogTop(image, 8, rnd);
        paintLeaves(image, 9, rnd);
        paintBedrock(image, 10, rnd);
        paintPlanks(image, 11, rnd);
        paintSnow(image, 12, rnd);
        paintFringedSide(image, 13, rnd, 0xFFFFFF, 0xD8E3F0);            // snow side
        paintGravel(image, 14, rnd);
        paintCactus(image, 15, rnd);

        paintOreTile(image, 16, rnd, 0x2A2A2A, 0x0C0C0C);                // coal ore
        paintOreTile(image, 17, rnd, 0xD8A66A, 0xA87C44);                // iron ore
        paintOreTile(image, 18, rnd, 0xFFD93A, 0xC49212);                // gold ore
        paintOreTile(image, 19, rnd, 0x6FE8E8, 0x2EA8B8, OreStyle.GEM);  // diamond ore
        paintFluidTile(image, 20, rnd, 0xF2A93B, 0x9E2A0C, 225, 0xFFE066); // lava

        paintCrossGrass(image, 21, rnd, 0x4C8C2C);                        // tall grass
        paintCrossFlower(image, 22, rnd, 0x3D6E2E, 0xD0392B, 0x241008, false); // red poppy (dark center)
        paintCrossFlower(image, 23, rnd, 0x3D6E2E, 0xF2D33A, 0xB5651D, true);  // yellow dandelion (fluffy)

        // --- Biome blocks ---
        paintGrassTop(image, 27, rnd, 0x4A9444, 0x2E6B2A);               // swamp grass top
        paintFringedSide(image, 28, rnd, 0x4A9444, 0x2E6B2A);            // swamp grass side
        paintRedClay(image, 29, rnd);
        paintMycelium(image, 30, rnd);
        paintFringedSide(image, 31, rnd, 0x9A7FB0, 0x6E5584);            // mycelium side
        paintFluidTile(image, 32, rnd, 0x9ADBEA, 0x7FC4D6, 200, 0xF2FEFF); // ice
        paintDeadBush(image, 33, rnd);
        paintGlass(image, 34);
        paintMushroom(image, 35, rnd, 0xD0392B, 0xB3251C);               // red mushroom
        paintMushroom(image, 36, rnd, 0x9A6A3A, 0x7A5028);               // brown mushroom
        paintVine(image, 39, rnd);
        paintLeaves(image, 40, rnd, 0xF2AEBF, 0xC97E97);                 // cherry leaves (pink)
        paintPackedIce(image, 41, rnd);
        paintBamboo(image, 42, rnd);
        paintLilyPad(image, 43, rnd);
        paintPumpkin(image, 44, rnd);
        paintSeaweed(image, 45, rnd);
        paintDoor(image, 46, rnd);
        paintTrapdoor(image, 47, rnd);
        paintWool(image, 59, rnd);

        // --- GTNH Ores: early-game (tiles 80-85) ---
        paintOreTile(image, 80, rnd, 0xE8772F, 0x9A3E12);                // copper (bright orange)
        paintOreTile(image, 81, rnd, 0xD2E4EA, 0x6A8A9A);                // tin (pale cyan-silver)
        paintOreTile(image, 82, rnd, 0xC65A2A, 0x7A2A10);                // bauxite (rusty terracotta)
        paintOreTile(image, 83, rnd, 0xA8D890, 0x4A8A40);                // zinc (apple-sage)
        paintOreTile(image, 84, rnd, 0x5C6A88, 0x2A3048);                // lead (blue-grey)
        paintOreTile(image, 85, rnd, 0xF2F4FF, 0x8A96B0);                // silver (cool white)

        // --- GTNH Ores: mid-game (tiles 86-90) ---
        paintOreTile(image, 86, rnd, 0xE6DC9A, 0x9A8C50);                // nickel (pale brass)
        paintOreTile(image, 87, rnd, 0x3A62F0, 0x1A28A8);                // cobalt (vivid blue)
        paintOreTile(image, 88, rnd, 0x6A88A8, 0x1A2838);                // tungsten (steel blue)
        paintOreTile(image, 89, rnd, 0x7A96B0, 0x3A5068);                // molybdenum (slate)
        paintOreTile(image, 90, rnd, 0xFFF4C8, 0xC0A86A);                // platinum (warm cream)

        // --- GTNH Ores: advanced (tiles 91-95) ---
        paintOreTile(image, 91, rnd, 0xC0E8DC, 0x5A9888);                // chromium (teal-silver)
        paintOreTile(image, 92, rnd, 0xC86890, 0x782848);                // manganese (mauve)
        paintOreTile(image, 93, rnd, 0x7A9A48, 0x3A5818);                // vanadium (olive)
        paintOreTile(image, 94, rnd, 0xD0E8A8, 0x7AAA58);                // beryllium (pale mint)
        paintOreTile(image, 95, rnd, 0xB0C8E8, 0x5878A8);                // titanium (ice blue)

        // --- GTNH Ores: late-game (tiles 96-99) ---
        paintOreTile(image, 96, rnd, 0x8CFF40, 0x3A9808, OreStyle.GLOW); // uranium (radioactive lime)
        paintOreTile(image, 97, rnd, 0x7A5A48, 0x3A2818);                // thorium (taupe)
        paintOreTile(image, 98, rnd, 0x3A7040, 0x102818, OreStyle.GLOW); // plutonium (sickly green)
        paintOreTile(image, 99, rnd, 0xE8DCFF, 0x8878C0);                // iridium (lavender-white)

        // --- Small GTNH Ores: sparse flecks, not stripes (tiles 100-119) ---
        paintSmallOreTile(image, 100, rnd, 0xE8772F, 0x9A3E12);          // small copper
        paintSmallOreTile(image, 101, rnd, 0xD2E4EA, 0x6A8A9A);          // small tin
        paintSmallOreTile(image, 102, rnd, 0xC65A2A, 0x7A2A10);          // small bauxite
        paintSmallOreTile(image, 103, rnd, 0xA8D890, 0x4A8A40);          // small zinc
        paintSmallOreTile(image, 104, rnd, 0x5C6A88, 0x2A3048);          // small lead
        paintSmallOreTile(image, 105, rnd, 0xF2F4FF, 0x8A96B0);          // small silver
        paintSmallOreTile(image, 106, rnd, 0xE6DC9A, 0x9A8C50);          // small nickel
        paintSmallOreTile(image, 107, rnd, 0x3A62F0, 0x1A28A8);          // small cobalt
        paintSmallOreTile(image, 108, rnd, 0x6A88A8, 0x1A2838);          // small tungsten
        paintSmallOreTile(image, 109, rnd, 0x7A96B0, 0x3A5068);          // small molybdenum
        paintSmallOreTile(image, 110, rnd, 0xFFF4C8, 0xC0A86A);          // small platinum
        paintSmallOreTile(image, 111, rnd, 0xC0E8DC, 0x5A9888);          // small chromium
        paintSmallOreTile(image, 112, rnd, 0xC86890, 0x782848);          // small manganese
        paintSmallOreTile(image, 113, rnd, 0x7A9A48, 0x3A5818);          // small vanadium
        paintSmallOreTile(image, 114, rnd, 0xD0E8A8, 0x7AAA58);          // small beryllium
        paintSmallOreTile(image, 115, rnd, 0xB0C8E8, 0x5878A8);          // small titanium
        paintSmallOreTile(image, 116, rnd, 0x8CFF40, 0x3A9808, OreStyle.GLOW); // small uranium
        paintSmallOreTile(image, 117, rnd, 0x7A5A48, 0x3A2818);          // small thorium
        paintSmallOreTile(image, 118, rnd, 0x3A7040, 0x102818, OreStyle.GLOW); // small plutonium
        paintSmallOreTile(image, 119, rnd, 0xE8DCFF, 0x8878C0);          // small iridium

        // --- New GTNH ore blocks (tiles 120-179) ---
        paintOreTile(image, 120, rnd, 0x5A5A78, 0x181828); // magnetite
        paintOreTile(image, 121, rnd, 0xC04030, 0x701810); // hematite
        paintOreTile(image, 122, rnd, 0xC08040, 0x804818); // brown limonite
        paintOreTile(image, 123, rnd, 0xF0C040, 0xC08818); // yellow limonite
        paintOreTile(image, 124, rnd, 0xC07040, 0x402010, OreStyle.BANDED); // banded iron
        paintOreTile(image, 125, rnd, 0x505888, 0x282840); // vanadium magnetite
        paintOreTile(image, 126, rnd, 0xD8B028, 0x6A7010); // chalcopyrite
        paintOreTile(image, 127, rnd, 0x506858, 0x283838); // tetrahedrite
        paintOreTile(image, 128, rnd, 0x20C060, 0x087030, OreStyle.BANDED); // malachite
        paintOreTile(image, 129, rnd, 0x90A0C0, 0x506080); // galena
        paintOreTile(image, 130, rnd, 0xC8A070, 0x806040); // sphalerite
        paintOreTile(image, 131, rnd, 0x70D070, 0x388838); // garnierite
        paintOreTile(image, 132, rnd, 0xC8B060, 0x887830); // pentlandite
        paintOreTile(image, 133, rnd, 0x5070D0, 0x284090); // cobaltite
        paintOreTile(image, 134, rnd, 0xF0D030, 0xB09010); // pyrite
        paintOreTile(image, 135, rnd, 0xA8B888, 0x687858); // arsenopyrite
        paintOreTile(image, 136, rnd, 0xFFF050, 0xC8A010, OreStyle.CRYSTAL); // sulfur
        paintOreTile(image, 137, rnd, 0xE82820, 0xA01008); // cinnabar
        paintOreTile(image, 138, rnd, 0xC8B898, 0x887858); // cassiterite
        paintOreTile(image, 139, rnd, 0xF0E090, 0xC0A040); // scheelite
        paintOreTile(image, 140, rnd, 0x584868, 0x281838); // wolframite
        paintOreTile(image, 141, rnd, 0x6880A0, 0x384860); // molybdenite
        paintOreTile(image, 142, rnd, 0x403848, 0x181820); // ferberite
        paintOreTile(image, 143, rnd, 0x384838, 0x182018); // chromite
        paintOreTile(image, 144, rnd, 0x584850, 0x281828); // ilmenite
        paintOreTile(image, 145, rnd, 0xE87840, 0xA84818); // rutile
        paintOreTile(image, 146, rnd, 0x508830, 0x284810, OreStyle.GLOW); // uraninite
        paintOreTile(image, 147, rnd, 0x3A4A28, 0x1A2410); // pitchblende
        paintOreTile(image, 148, rnd, 0xE0A050, 0xA06820); // monazite
        paintOreTile(image, 149, rnd, 0xF0A040, 0xC06818); // bastnasite
        paintOreTile(image, 150, rnd, 0xE84828, 0xA82010); // vanadinite
        paintOreTile(image, 151, rnd, 0x686878, 0x383848); // pyrolusite
        paintOreTile(image, 152, rnd, 0x2A2A32, 0x101018); // graphite
        paintOreTile(image, 153, rnd, 0xE8F0FF, 0xA0B8D8); // lithium
        paintOreTile(image, 154, rnd, 0x2A8050, 0x0A2818, OreStyle.GLOW); // naquadah
        paintOreTile(image, 155, rnd, 0x208020, 0x084008, OreStyle.GLOW); // naquadah enriched
        paintOreTile(image, 156, rnd, 0xA0D8D8, 0x60A0A0); // trinium
        paintOreTile(image, 157, rnd, 0xB090E8, 0x7050B0); // neodymium
        paintOreTile(image, 158, rnd, 0xF0D890, 0xC0A050); // cerium
        paintOreTile(image, 159, rnd, 0x486878, 0x284050); // osmium
        paintOreTile(image, 160, rnd, 0xE8D0B0, 0xB09070); // palladium
        paintOreTile(image, 161, rnd, 0xFFF8F0, 0xD8D0C0, OreStyle.CRYSTAL); // calcite
        paintOreTile(image, 162, rnd, 0x88C040, 0x508018, OreStyle.GEM); // olivine
        paintOreTile(image, 163, rnd, 0xE8F0D8, 0xB0C0A0); // talc
        paintOreTile(image, 164, rnd, 0xD8C8A0, 0xA09070); // bentonite
        paintOreTile(image, 165, rnd, 0x4060E0, 0x2030A0); // sodalite
        paintOreTile(image, 166, rnd, 0x2040C0, 0x102080); // lazurite
        paintOreTile(image, 167, rnd, 0xFFFFFF, 0xD0D0E0, OreStyle.CRYSTAL); // salt
        paintOreTile(image, 168, rnd, 0xF0E0D0, 0xC8B0A0, OreStyle.CRYSTAL); // rock salt
        paintOreTile(image, 169, rnd, 0xF8F0D8, 0xD0C8A8, OreStyle.CRYSTAL); // saltpeter
        paintOreTile(image, 170, rnd, 0xF8F0E8, 0xD8D0C0, OreStyle.CRYSTAL); // borax
        paintOreTile(image, 171, rnd, 0x60B0E8, 0x3080B8); // apatite
        paintOreTile(image, 172, rnd, 0xD8D070, 0xA8A040); // phosphate
        paintOreTile(image, 173, rnd, 0xA08060, 0x685038); // pyrochlore
        paintOreTile(image, 174, rnd, 0xE0A0D8, 0xB070B0, OreStyle.BANDED); // lepidolite
        paintOreTile(image, 175, rnd, 0xFF2060, 0xB00030, OreStyle.GEM); // ruby
        paintOreTile(image, 176, rnd, 0x2060FF, 0x0030C0, OreStyle.GEM); // sapphire
        paintOreTile(image, 177, rnd, 0x20D070, 0x009040, OreStyle.GEM); // green sapphire
        paintOreTile(image, 178, rnd, 0xC01840, 0x800020, OreStyle.GEM); // pyrope
        paintOreTile(image, 179, rnd, 0xF87820, 0xC05010, OreStyle.GEM); // spessartine

        // --- New GTNH small ores (tiles 180-219) ---
        paintSmallOreTile(image, 180, rnd, 0x5A5A78, 0x181828); // small magnetite
        paintSmallOreTile(image, 181, rnd, 0xC04030, 0x701810); // small hematite
        paintSmallOreTile(image, 182, rnd, 0xC08040, 0x804818); // small brown limonite
        paintSmallOreTile(image, 183, rnd, 0xF0C040, 0xC08818); // small yellow limonite
        paintSmallOreTile(image, 184, rnd, 0xC07040, 0x402010, OreStyle.BANDED); // small banded iron
        paintSmallOreTile(image, 185, rnd, 0x505888, 0x282840); // small vanadium magnetite
        paintSmallOreTile(image, 186, rnd, 0xD8B028, 0x6A7010); // small chalcopyrite
        paintSmallOreTile(image, 187, rnd, 0x506858, 0x283838); // small tetrahedrite
        paintSmallOreTile(image, 188, rnd, 0x20C060, 0x087030, OreStyle.BANDED); // small malachite
        paintSmallOreTile(image, 189, rnd, 0x90A0C0, 0x506080); // small galena
        paintSmallOreTile(image, 190, rnd, 0xC8A070, 0x806040); // small sphalerite
        paintSmallOreTile(image, 191, rnd, 0x70D070, 0x388838); // small garnierite
        paintSmallOreTile(image, 192, rnd, 0xC8B060, 0x887830); // small pentlandite
        paintSmallOreTile(image, 193, rnd, 0x5070D0, 0x284090); // small cobaltite
        paintSmallOreTile(image, 194, rnd, 0xF0D030, 0xB09010); // small pyrite
        paintSmallOreTile(image, 195, rnd, 0xA8B888, 0x687858); // small arsenopyrite
        paintSmallOreTile(image, 196, rnd, 0xFFF050, 0xC8A010, OreStyle.CRYSTAL); // small sulfur
        paintSmallOreTile(image, 197, rnd, 0xE82820, 0xA01008); // small cinnabar
        paintSmallOreTile(image, 198, rnd, 0xC8B898, 0x887858); // small cassiterite
        paintSmallOreTile(image, 199, rnd, 0xF0E090, 0xC0A040); // small scheelite
        paintSmallOreTile(image, 200, rnd, 0x584868, 0x281838); // small wolframite
        paintSmallOreTile(image, 201, rnd, 0x6880A0, 0x384860); // small molybdenite
        paintSmallOreTile(image, 202, rnd, 0x384838, 0x182018); // small chromite
        paintSmallOreTile(image, 203, rnd, 0x584850, 0x281828); // small ilmenite
        paintSmallOreTile(image, 204, rnd, 0xE87840, 0xA84818); // small rutile
        paintSmallOreTile(image, 205, rnd, 0x508830, 0x284810, OreStyle.GLOW); // small uraninite
        paintSmallOreTile(image, 206, rnd, 0x3A4A28, 0x1A2410); // small pitchblende
        paintSmallOreTile(image, 207, rnd, 0xE0A050, 0xA06820); // small monazite
        paintSmallOreTile(image, 208, rnd, 0xF0A040, 0xC06818); // small bastnasite
        paintSmallOreTile(image, 209, rnd, 0xE84828, 0xA82010); // small vanadinite
        paintSmallOreTile(image, 210, rnd, 0x686878, 0x383848); // small pyrolusite
        paintSmallOreTile(image, 211, rnd, 0x2A2A32, 0x101018); // small graphite
        paintSmallOreTile(image, 212, rnd, 0xE8F0FF, 0xA0B8D8); // small lithium
        paintSmallOreTile(image, 213, rnd, 0x2A8050, 0x0A2818, OreStyle.GLOW); // small naquadah
        paintSmallOreTile(image, 214, rnd, 0xA0D8D8, 0x60A0A0); // small trinium
        paintSmallOreTile(image, 215, rnd, 0xB090E8, 0x7050B0); // small neodymium
        paintSmallOreTile(image, 216, rnd, 0xF0D890, 0xC0A050); // small cerium
        paintSmallOreTile(image, 217, rnd, 0x486878, 0x284050); // small osmium
        paintSmallOreTile(image, 218, rnd, 0xE8D0B0, 0xB09070); // small palladium
        paintSmallOreTile(image, 219, rnd, 0xFF2060, 0xB00030, OreStyle.GEM); // small ruby

        // --- Nether / End dimension blocks ---
        paintNetherrack(image, 52, rnd);
        paintSoulSand(image, 53, rnd);
        paintTile(image, 54, rnd, 0xF5E6A0, 0xE8CE6A, true, 0xFFF7C0, 0.5f); // glowstone (bright, yellowish)
        paintNetherPortal(image, 55, rnd);
        paintTile(image, 56, rnd, 0xE4E0C8, 0xCFC8A8, true);   // end stone (pale, sandy)
        paintObsidian(image, 57, rnd);
        paintEndPortal(image, 58, rnd);
        // Bed textures: 4 tiles for a proper Minecraft-style bed
        paintBedTop(image, 60, rnd);    // blanket top (red with pattern)
        paintBedSide(image, 61, rnd);   // blanket side
        paintBedFoot(image, 62, rnd);   // foot end (darker)
        paintBedPillow(image, 63, rnd); // pillow/head end (white with red trim)

        // --- Furniture / fixtures ---
        paintLeavesCutout(image, LEAVES_CUTOUT_TILE, rnd);
        paintLeavesCutout(image, CHERRY_LEAVES_CUTOUT_TILE, rnd, 0xF2AEBF, 0xC97E97);
        paintLamp(image, LAMP_TILE);
        paintFurnace(image, FURNACE_TILE);
        paintFurnaceLit(image, FURNACE_LIT_TILE);
        paintCraftingTable(image, CRAFTING_TABLE_TILE);
        paintChest(image, CHEST_TILE, ChestFace.FRONT);
        paintChest(image, CHEST_TOP_TILE, ChestFace.TOP);
        paintChest(image, CHEST_SIDE_TILE, ChestFace.SIDE);
        paintChest(image, CHEST_BOTTOM_TILE, ChestFace.BOTTOM);
        paintChestPair(image, CHEST_DOUBLE_FRONT_L, CHEST_DOUBLE_FRONT_R, ChestFace.FRONT);
        paintChestPair(image, CHEST_DOUBLE_TOP_L, CHEST_DOUBLE_TOP_R, ChestFace.TOP);
        paintChestPair(image, CHEST_DOUBLE_TOP_PLAIN_L, CHEST_DOUBLE_TOP_PLAIN_R, ChestFace.TOP_PLAIN);
        paintChestPair(image, CHEST_DOUBLE_BACK_L, CHEST_DOUBLE_BACK_R, ChestFace.BACK);
        paintChestPair(image, CHEST_DOUBLE_BOTTOM_L, CHEST_DOUBLE_BOTTOM_R, ChestFace.BOTTOM);
        paintBarrel(image, BARREL_TILE);
        paintBerryBush(image, 37, rnd);
        paintTorch(image, 38);
        paintFire(image, FIRE_TILE, rnd);
        for (int s = 0; s < DESTROY_STAGE_COUNT; s++) {
            paintDestroyStage(image, DESTROY_STAGE_TILE + s, s);
        }

        // --- Phase 0: Farming tiles (220-231) ---
        paintFarmlandTop(image, 220, rnd);       // FARMLAND top (tilled dark dirt)
        paintCropStage(image, 221, rnd, 0x90C840, 0.20f); // WHEAT_STAGE_1 (tiny sprout)
        paintCropStage(image, 222, rnd, 0x90C840, 0.40f); // WHEAT_STAGE_2 (young wheat)
        paintCropStage(image, 223, rnd, 0xB8D840, 0.70f); // WHEAT_STAGE_3 (growing)
        paintCropStage(image, 224, rnd, 0xE8C840, 1.00f); // WHEAT_STAGE_4 (mature golden wheat)
        paintCropStage(image, 225, rnd, 0x60B030, 0.25f); // POTATO_CROP_1 (sprout)
        paintCropStage(image, 226, rnd, 0x60B030, 0.60f); // POTATO_CROP_2 (growing)
        paintCropStage(image, 227, rnd, 0x78C040, 1.00f); // POTATO_CROP_3 (mature, leafy)
        paintCropStage(image, 228, rnd, 0x48A828, 0.25f); // CARROT_CROP_1 (sprout)
        paintCropStage(image, 229, rnd, 0x48A828, 0.60f); // CARROT_CROP_2 (growing)
        paintCropStage(image, 230, rnd, 0xFF8020, 1.00f); // CARROT_CROP_3 (mature, orange tips)
        paintSugarCane(image, 231, rnd);                  // SUGAR_CANE
        paintClayBlock(image, 232, rnd);                  // CLAY (blue-grey terrain block)
        paintWetFarmlandTop(image, 233, rnd);             // FARMLAND_WET top (dark moist soil)

        // --- Phase 0.5: Tinkers' Construct blocks (234-243) ---
        paintSearedBrick(image, 234, rnd);                // SEARED_BRICK
        paintSearedGlass(image, 235);                     // SEARED_GLASS
        paintSearedTank(image, 236, rnd);                 // SEARED_TANK
        paintSmelteryDrain(image, 237, rnd);              // SMELTERY_DRAIN
        paintSmelteryController(image, 238, rnd, false);  // SMELTERY_CONTROLLER sides/top/bottom
        paintSmelteryController(image, 239, rnd, true);   // SMELTERY_CONTROLLER front (active/lit)
        paintCastingTable(image, 240, rnd);               // CASTING_TABLE
        paintCastingBasin(image, 241, rnd);               // CASTING_BASIN
        paintPartBuilder(image, 242, rnd);                // PART_BUILDER
        paintToolStation(image, 243, rnd);                // TOOL_STATION
        paintAnvil(image, 254, rnd);                      // ANVIL

        // --- Steam Age machines (256-260) ---
        paintSteamBoiler(image, 256, rnd, false);         // STEAM_BOILER front (cold)
        paintSteamBoiler(image, 257, rnd, true);          // STEAM_BOILER front (steaming)
        paintSteamFurnace(image, 258, rnd, false);        // STEAM_FURNACE sides/top/bottom + front off
        paintSteamFurnace(image, 259, rnd, true);         // STEAM_FURNACE front (active)
        paintSteamPipe(image, 260, rnd, 0xB87333, 0xFF6E4420, 0xB87333);   // STEAM_PIPE_BRONZE
        paintSteamPipe(image, 261, rnd, 0x7B5A2D, 0xFF4A3418, 0x7B5A2D);   // STEAM_PIPE_WOOD
        paintSteamPipe(image, 262, rnd, 0xD8D8D8, 0xFF6A6A6A, 0xA8A8A8);   // STEAM_PIPE_IRON
        paintSteamPipe(image, 263, rnd, 0xC8C8E8, 0xFF5060A0, 0x8888B8);   // STEAM_PIPE_STEEL
        paintSteamMacerator(image, 264, rnd, false);      // STEAM_MACERATOR front (idle)
        paintSteamMacerator(image, 265, rnd, true);       // STEAM_MACERATOR front (working)

        // Electric Age tiles (266-274).
        paintCoalGenerator(image, 266, rnd, false, false); // COAL_GENERATOR body (all sides)
        paintCoalGenerator(image, 267, rnd, true, false);  // COAL_GENERATOR front (cold)
        paintCoalGenerator(image, 268, rnd, true, true);   // COAL_GENERATOR front (burning)
        paintCopperCable(image, 269, rnd);                // COPPER_CABLE
        paintGoldCable(image, 270, rnd);                  // GOLD_CABLE
        paintElectricFurnace(image, 271, rnd, false, false); // ELECTRIC_FURNACE body
        paintElectricFurnace(image, 272, rnd, true, false);  // ELECTRIC_FURNACE front (idle)
        paintElectricFurnace(image, 273, rnd, true, true);   // ELECTRIC_FURNACE front (active)
        paintBatteryBlock(image, 274, rnd);               // BATTERY_BLOCK

        // Explosives (275).
        paintTnt(image, 275, rnd);                        // TNT

        return image;
    }

    private static int tileX(int index) {
        return (index % GRID) * TILE_PX;
    }

    private static int tileY(int index) {
        return (index / GRID) * TILE_PX;
    }

    // ---------------------------------------------------------------------
    // Color helpers
    // ---------------------------------------------------------------------

    /** Linearly interpolates between two 0xRRGGBB colors; t=0 -> c0, t=1 -> c1. */
    private static int lerpColor(int c0, int c1, float t) {
        int r = Math.round(((c0 >> 16) & 0xFF) + ((((c1 >> 16) & 0xFF) - ((c0 >> 16) & 0xFF)) * t));
        int g = Math.round(((c0 >> 8) & 0xFF) + ((((c1 >> 8) & 0xFF) - ((c0 >> 8) & 0xFF)) * t));
        int b = Math.round((c0 & 0xFF) + (((c1 & 0xFF) - (c0 & 0xFF)) * t));
        return (r << 16) | (g << 8) | b;
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    /** Multiplies a 0xRRGGBB color's brightness by {@code f}. */
    private static int shade(int color, float f) {
        int r = Math.min(255, Math.max(0, Math.round(((color >> 16) & 0xFF) * f)));
        int g = Math.min(255, Math.max(0, Math.round(((color >> 8) & 0xFF) * f)));
        int b = Math.min(255, Math.max(0, Math.round((color & 0xFF) * f)));
        return (r << 16) | (g << 8) | b;
    }

    // ---------------------------------------------------------------------
    // Seamless value noise
    // ---------------------------------------------------------------------

    private static int latticeValue(int gx, int gy) {
        int h = gx * 374761393 + gy * 668265263;
        h = (h ^ (h >> 13)) * 1274126177;
        h = h ^ (h >> 16);
        return h & 0xFFFF;
    }

    private static float smoothstep(float t) {
        return t * t * (3f - 2f * t);
    }

    /**
     * Seamless value noise at tile-space {@code (x, y)}, sampled from a
     * lattice of {@code 16/cell} cells per axis. Wraps in both directions so a
     * 16px tile tiles seamlessly, which is what lets a material repeat across
     * many blocks without visible seams.
     */
    private static float valueNoise(float x, float y, int cell) {
        int n = TILE_PX / cell;
        int gx = (int) Math.floor(x / cell);
        int gy = (int) Math.floor(y / cell);
        float fx = (x - gx * cell) / cell;
        float fy = (y - gy * cell) / cell;
        int gx0 = Math.floorMod(gx, n), gy0 = Math.floorMod(gy, n);
        int gx1 = Math.floorMod(gx + 1, n), gy1 = Math.floorMod(gy + 1, n);
        float u = smoothstep(fx), v = smoothstep(fy);
        float v00 = latticeValue(gx0, gy0) / 65535f;
        float v10 = latticeValue(gx1, gy0) / 65535f;
        float v01 = latticeValue(gx0, gy1) / 65535f;
        float v11 = latticeValue(gx1, gy1) / 65535f;
        return lerp(lerp(v00, v10, u), lerp(v01, v11, u), v);
    }

    /** Two-octave fractal noise in [0,1] - big soft blobs plus fine grain. */
    private static float fbm(float x, float y) {
        return 0.62f * valueNoise(x, y, 4) + 0.38f * valueNoise(x, y, 2);
    }

    /** Per-tile noise offset, so each copy of a tile has its own coherent grain instead of a repeating grid. */
    private static float noiseOffset(Random rnd) {
        return rnd.nextFloat() * 1000f;
    }

    /** A subtle light-from-above tint and corner darkening, so tiles read as solid blocks. */
    private static float faceShade(float x, float y) {
        float top = 1f + 0.04f - 0.09f * (y / (TILE_PX - 1f));
        float dx = (x - 7.5f) / 7.5f;
        float dy = (y - 7.5f) / 7.5f;
        return top * (1f - 0.04f * (dx * dx + dy * dy));
    }

    /**
     * The core material painter: base color mottled by seamless fbm noise,
     * pushed through a light-to-dark ramp and lightly shaded from above.
     * {@code light}/{@code dark} are the bright and dark ends of the ramp,
     * {@code contrast} (0..1) how far the noise spreads between them.
     */
    private void paintNoiseTile(BufferedImage img, int index, int light, int dark, float contrast, Random rnd) {
        int ox = tileX(index);
        int oy = tileY(index);
        float oxs = rnd != null ? noiseOffset(rnd) : 0f;
        float oys = rnd != null ? noiseOffset(rnd) : 0f;
        for (int y = 0; y < TILE_PX; y++) {
            for (int x = 0; x < TILE_PX; x++) {
                float n = (fbm(x + oxs, y + oys) - 0.5f) * contrast + 0.5f;
                int color = lerpColor(dark, light, n);
                img.setRGB(ox + x, oy + y, 0xFF000000 | shade(color, faceShade(x, y)));
            }
        }
    }

    /**
     * Generic fbm-grain material tile between {@code dark} and {@code light}. When
     * {@code opaque} the whole tile is painted solid; otherwise only the bright noise
     * regions are drawn onto a transparent background (for alpha-cutout blocks).
     */
    private void paintTile(BufferedImage img, int index, Random rnd, int light, int dark, boolean opaque) {
        int ox = tileX(index);
        int oy = tileY(index);
        float oxs = noiseOffset(rnd);
        float oys = noiseOffset(rnd);
        for (int y = 0; y < TILE_PX; y++) {
            for (int x = 0; x < TILE_PX; x++) {
                float n = (fbm(x + oxs, y + oys) - 0.5f) * 0.5f + 0.5f;
                if (!opaque && n < 0.35f) {
                    continue;
                }
                int color = shade(lerpColor(dark, light, n), faceShade(x, y));
                img.setRGB(ox + x, oy + y, 0xFF000000 | color);
            }
        }
    }

    /** {@link #paintTile(BufferedImage, int, Random, int, int, boolean)} plus {@code accent} speckles at {@code density} (0..1 fraction of pixels). */
    private void paintTile(BufferedImage img, int index, Random rnd, int light, int dark, boolean opaque, int accent, float density) {
        paintTile(img, index, rnd, light, dark, opaque);
        int ox = tileX(index);
        int oy = tileY(index);
        int count = Math.round(TILE_PX * TILE_PX * density);
        for (int i = 0; i < count; i++) {
            int x = rnd.nextInt(TILE_PX);
            int y = rnd.nextInt(TILE_PX);
            img.setRGB(ox + x, oy + y, 0xFF000000 | accent);
        }
    }

    // ---------------------------------------------------------------------
    // Terrain materials
    // ---------------------------------------------------------------------

    /** Grass top: bright fbm green with a scattering of darker patches and lighter blade tips. */
    private void paintGrassTop(BufferedImage img, int index, Random rnd) {
        paintGrassTop(img, index, rnd, 0x63B034, 0x3E7A24);
    }

    private void paintGrassTop(BufferedImage img, int index, Random rnd, int light, int dark) {
        paintNoiseTile(img, index, light, dark, 0.55f, rnd);
        int ox = tileX(index);
        int oy = tileY(index);
        // Lighter blade tips on the top rows.
        for (int y = 0; y < 3; y++) {
            for (int x = 0; x < TILE_PX; x++) {
                if (rnd.nextFloat() < 0.35f) {
                    img.setRGB(ox + x, oy + y, 0xFF000000 | shade(light, 1.12f));
                }
            }
        }
    }

    /** Dirt: a warm medium-brown (modern Minecraft tone) with granular speckles, small pebbles and light flecks. */
    private void paintDirt(BufferedImage img, int index, Random rnd) {
        paintNoiseTile(img, index, 0x9B7653, 0x6B4F34, 0.45f, rnd);
        int ox = tileX(index);
        int oy = tileY(index);
        for (int i = 0; i < 6; i++) {
            int x = rnd.nextInt(TILE_PX), y = rnd.nextInt(TILE_PX);
            img.setRGB(ox + x, oy + y, 0xFF000000 | 0x54402A);
        }
        for (int i = 0; i < 5; i++) {
            int x = rnd.nextInt(TILE_PX), y = rnd.nextInt(TILE_PX);
            img.setRGB(ox + x, oy + y, 0xFF000000 | 0xA88963);
        }
    }

    /** Stone: low-contrast gray mottling with a few faint diagonal cracks. */
    private void paintStone(BufferedImage img, int index, Random rnd) {
        paintNoiseTile(img, index, 0x9A9A9A, 0x6A6A6A, 0.4f, rnd);
        int ox = tileX(index);
        int oy = tileY(index);
        for (int c = 0; c < 3; c++) {
            int x = rnd.nextInt(TILE_PX - 3), y = rnd.nextInt(TILE_PX - 3);
            int dx = rnd.nextBoolean() ? 1 : -1;
            for (int i = 0; i < 3; i++) {
                img.setRGB(ox + x + i, oy + y + dx * i, 0xFF000000 | 0x555555);
            }
        }
    }

    /** Sand: fine warm grain with faint sparkle. */
    private void paintSand(BufferedImage img, int index, Random rnd) {
        paintNoiseTile(img, index, 0xE6D8A8, 0xC0AC78, 0.35f, rnd);
        int ox = tileX(index);
        int oy = tileY(index);
        for (int i = 0; i < 4; i++) {
            int x = rnd.nextInt(TILE_PX), y = rnd.nextInt(TILE_PX);
            img.setRGB(ox + x, oy + y, 0xFF000000 | 0xF4E8C8);
        }
    }

    /** A dirt-style base with a ragged colored fringe along the top edge (grass/snow/mycelium sides). */
    private void paintFringedSide(BufferedImage img, int index, Random rnd, int fringeLight, int fringeDark) {
        paintNoiseTile(img, index, 0x9B7653, 0x6B4F34, 0.45f, rnd);
        int ox = tileX(index);
        int oy = tileY(index);
        int maxRows = Math.round(TILE_PX * 0.30f);
        for (int x = 0; x < TILE_PX; x++) {
            // Ragged, column-varying fringe height.
            float n = fbm(x * 1.4f, 0f);
            int rows = 2 + (int) (n * maxRows);
            for (int y = 0; y < rows; y++) {
                float t = 1f - y / (float) rows;
                int color = lerpColor(fringeLight, fringeDark, t * 0.5f + 0.25f);
                img.setRGB(ox + x, oy + y, 0xFF000000 | color);
            }
        }
    }

    /** Log side: bark with vertical ridges and a few light highlights. */
    private void paintLogSide(BufferedImage img, int index, Random rnd) {
        paintNoiseTile(img, index, 0x7A5430, 0x4E341C, 0.45f, rnd);
        int ox = tileX(index);
        int oy = tileY(index);
        int dark = 0x3A2613;
        int light = 0x8A6A42;
        for (int x = 0; x < TILE_PX; x++) {
            // Wobbly vertical ridge lines.
            float n = fbm(x * 1.8f, 0f);
            if (n < 0.32f) {
                for (int y = 0; y < TILE_PX; y++) {
                    img.setRGB(ox + x, oy + y, 0xFF000000 | dark);
                }
            } else if (n > 0.8f) {
                for (int y = 0; y < TILE_PX; y++) {
                    img.setRGB(ox + x, oy + y, 0xFF000000 | light);
                }
            }
        }
    }

    /** Log top: pale wood with wobbly concentric growth rings around the center. */
    private void paintLogTop(BufferedImage img, int index, Random rnd) {
        paintNoiseTile(img, index, 0xD2AF72, 0xA8864E, 0.3f, rnd);
        int ox = tileX(index);
        int oy = tileY(index);
        int ring = 0x8A6B3F;
        double cx = TILE_PX / 2.0 - 0.5, cy = TILE_PX / 2.0 - 0.5;
        for (int y = 0; y < TILE_PX; y++) {
            for (int x = 0; x < TILE_PX; x++) {
                double wobble = 0.4 * Math.sin(x * 1.7) + 0.4 * Math.cos(y * 2.1);
                double d = Math.hypot(x - cx, y - cy) + wobble;
                if (((int) Math.round(d)) % 3 == 0) {
                    img.setRGB(ox + x, oy + y, 0xFF000000 | ring);
                }
            }
        }
    }

    /** Leaves: clustered low-frequency mottling with scattered darker and lighter leaves. */
    private void paintLeaves(BufferedImage img, int index, Random rnd) {
        paintLeaves(img, index, rnd, 0x4C9A38, 0x2F6B1F);
    }

    private void paintLeaves(BufferedImage img, int index, Random rnd, int light, int dark) {
        paintNoiseTile(img, index, light, dark, 0.75f, rnd);
        int ox = tileX(index);
        int oy = tileY(index);
        for (int i = 0; i < 6; i++) {
            int x = rnd.nextInt(TILE_PX), y = rnd.nextInt(TILE_PX);
            img.setRGB(ox + x, oy + y, 0xFF000000 | (rnd.nextBoolean() ? shade(light, 1.15f) : shade(dark, 0.8f)));
        }
    }

    /** Bedrock: near-black, barely-patterned. */
    private void paintBedrock(BufferedImage img, int index, Random rnd) {
        paintNoiseTile(img, index, 0x4A4A4A, 0x202020, 0.55f, rnd);
    }

    /** Planks: boards with horizontal seams and wood grain streaks. */
    private void paintPlanks(BufferedImage img, int index, Random rnd) {
        paintNoiseTile(img, index, 0xC69A56, 0x8E6A34, 0.5f, rnd);
        int ox = tileX(index);
        int oy = tileY(index);
        int seam = 0x6E4F28;
        int grain = 0xA88044;
        // Four horizontal boards.
        for (int b = 0; b < 4; b++) {
            int y0 = b * 4;
            int seamY = b * 4;
            for (int x = 0; x < TILE_PX; x++) {
                img.setRGB(ox + x, oy + seamY, 0xFF000000 | seam);
            }
            // Horizontal grain streaks across each board.
            for (int y = y0; y < y0 + 4; y++) {
                for (int x = 0; x < TILE_PX; x++) {
                    float n = fbm(x * 1.2f, y * 0.6f + b * 7f);
                    if (n > 0.78f) {
                        img.setRGB(ox + x, oy + y, 0xFF000000 | grain);
                    }
                }
            }
        }
    }

    /** Snow: near-white with a faint cool tint and sparkle. */
    private void paintSnow(BufferedImage img, int index, Random rnd) {
        paintNoiseTile(img, index, 0xFFFFFF, 0xD8E3F0, 0.35f, rnd);
        int ox = tileX(index);
        int oy = tileY(index);
        for (int i = 0; i < 5; i++) {
            int x = rnd.nextInt(TILE_PX), y = rnd.nextInt(TILE_PX);
            img.setRGB(ox + x, oy + y, 0xFF000000 | 0xFFFFFF);
        }
    }

    /** Gravel: a mix of rounded pebbles in grays and tans over a coarse base. */
    private void paintGravel(BufferedImage img, int index, Random rnd) {
        paintNoiseTile(img, index, 0x9A9284, 0x6E685E, 0.55f, rnd);
        int ox = tileX(index);
        int oy = tileY(index);
        int[] pebbles = {0xB0A898, 0x7A746A, 0x8A8070, 0xA89C84, 0x5E5850};
        for (int i = 0; i < 8; i++) {
            int x = rnd.nextInt(TILE_PX), y = rnd.nextInt(TILE_PX);
            int color = pebbles[rnd.nextInt(pebbles.length)];
            img.setRGB(ox + x, oy + y, 0xFF000000 | color);
            if (x + 1 < TILE_PX) img.setRGB(ox + x + 1, oy + y, 0xFF000000 | color);
            if (y + 1 < TILE_PX) img.setRGB(ox + x, oy + y + 1, 0xFF000000 | color);
        }
    }

    /** Cactus: green with vertical ridge seams and highlights. */
    private void paintCactus(BufferedImage img, int index, Random rnd) {
        paintNoiseTile(img, index, 0x5EA83C, 0x3A7A28, 0.5f, rnd);
        int ox = tileX(index);
        int oy = tileY(index);
        for (int x = 3; x < TILE_PX; x += 5) {
            for (int y = 0; y < TILE_PX; y++) {
                img.setRGB(ox + x, oy + y, 0xFF000000 | 0x2E621F);
            }
        }
    }

    /**
     * How mineral pixels are stamped onto stone. VEIN is the Minecraft-style
     * irregular overlay used by metals; GEM/CRYSTAL use angular faces;
     * BANDED stripes the vein (malachite, banded iron); GLOW adds bright specks.
     */
    private enum OreStyle { VEIN, GEM, CRYSTAL, BANDED, GLOW }

    /**
     * Hand-authored 16x16 overlays. Digits: 0 = leave stone, 1 = dark rim,
     * 2 = body, 3 = highlight. Pattern inspired by vanilla Minecraft's unique
     * ore overlay so veins read as embedded mineral, not random circles or
     * full-tile stripes.
     */
    private static final String[] ORE_VEIN_MASK = {
            "0002200000230000",
            "0023320002332000",
            "0023210000220002",
            "0002000000000233",
            "2200002300000232",
            "3320023320000021",
            "0220002210033000",
            "0000000100233200",
            "0002300000022100",
            "0023320000002000",
            "0023210000000033",
            "0002000220000232",
            "0200023320000221",
            "3320023210000000",
            "2210002000000000",
            "0000000000000000"
    };

    private static final String[] ORE_SMALL_MASK = {
            "0000000000000000",
            "0002300000000000",
            "0003210000023000",
            "0000000000020000",
            "0000000000000000",
            "0000002300000000",
            "0020003210000000",
            "0000000000000230",
            "0000000000000210",
            "0000230000000000",
            "0000020000000000",
            "0000000002300000",
            "0230000003210000",
            "0020000000000000",
            "0000000000002300",
            "0000000000000000"
    };

    private static final String[] ORE_GEM_MASK = {
            "0000300000003000",
            "0003230000032300",
            "0002320000023200",
            "0000100000001000",
            "0030000030000000",
            "0323000323000000",
            "0232000232000300",
            "0010000010003230",
            "0000000000002320",
            "0000030000000100",
            "0000323000000000",
            "0300232000300000",
            "3230010003230000",
            "2320000002320000",
            "0100003000100000",
            "0000032300000000"
    };

    private static final String[] ORE_CRYSTAL_MASK = {
            "0002220000000000",
            "0023332000222000",
            "0232223202333200",
            "0023112002322300",
            "0002220000231100",
            "0000000000022000",
            "0000222000000000",
            "0002333200022200",
            "0023222300233320",
            "0023112002322230",
            "0002220000231100",
            "0000000000022000",
            "0222000000000000",
            "2333200022200000",
            "2322300233320000",
            "0231100023110000"
    };

    /** Stone base with a Minecraft-style irregular mineral vein overlay. */
    private void paintOreTile(BufferedImage img, int index, Random rnd, int oreColor, int oreDark) {
        paintOreTile(img, index, rnd, oreColor, oreDark, OreStyle.VEIN);
    }

    private void paintOreTile(BufferedImage img, int index, Random rnd, int oreColor, int oreDark, OreStyle style) {
        paintStone(img, index, rnd);
        stampOreMask(img, index, oreColor, oreDark, style, false);
    }

    /** Stone with a few sparse mineral flecks - small ores used to be ugly full-tile stripes. */
    private void paintSmallOreTile(BufferedImage img, int index, Random rnd, int oreColor, int oreDark) {
        paintSmallOreTile(img, index, rnd, oreColor, oreDark, OreStyle.VEIN);
    }

    private void paintSmallOreTile(BufferedImage img, int index, Random rnd, int oreColor, int oreDark, OreStyle style) {
        paintStone(img, index, rnd);
        stampOreMask(img, index, oreColor, oreDark, style, true);
    }

    private void stampOreMask(BufferedImage img, int index, int oreColor, int oreDark, OreStyle style, boolean small) {
        int ox = tileX(index);
        int oy = tileY(index);
        String[] mask = oreMask(style, small);
        int oreHi = lerpColor(oreColor, 0xFFFFFF, 0.38f);
        // Flip so neighbouring ore types don't share the exact silhouette.
        boolean flipX = (index & 1) != 0;
        boolean flipY = (index & 2) != 0;
        for (int y = 0; y < TILE_PX; y++) {
            for (int x = 0; x < TILE_PX; x++) {
                int mx = flipX ? TILE_PX - 1 - x : x;
                int my = flipY ? TILE_PX - 1 - y : y;
                int cell = mask[my].charAt(mx) - '0';
                if (cell <= 0) continue;
                int color;
                if (style == OreStyle.BANDED) {
                    int band = Math.floorMod(x + y / 2, 3);
                    color = band == 0 ? oreDark : band == 1 ? oreColor : oreHi;
                } else {
                    color = cell == 1 ? oreDark : cell == 3 ? oreHi : oreColor;
                }
                img.setRGB(ox + x, oy + y, 0xFF000000 | color);
            }
        }
        if (style == OreStyle.GLOW) {
            // Extra radioactive sparkles on top of the vein.
            for (int y = 0; y < TILE_PX; y++) {
                for (int x = 0; x < TILE_PX; x++) {
                    int mx = flipX ? TILE_PX - 1 - x : x;
                    int my = flipY ? TILE_PX - 1 - y : y;
                    int cell = mask[my].charAt(mx) - '0';
                    if (cell == 3) {
                        img.setRGB(ox + x, oy + y, 0xFF000000 | lerpColor(oreColor, 0xFFFFFF, 0.62f));
                    }
                }
            }
        }
        if (style == OreStyle.GEM) {
            // White facet glints on the top-left of each highlight pixel.
            for (int y = 1; y < TILE_PX - 1; y++) {
                for (int x = 1; x < TILE_PX - 1; x++) {
                    int mx = flipX ? TILE_PX - 1 - x : x;
                    int my = flipY ? TILE_PX - 1 - y : y;
                    if (mask[my].charAt(mx) == '3') {
                        img.setRGB(ox + x, oy + y, 0xFF000000 | 0xFFFFFF);
                    }
                }
            }
        }
    }

    private static String[] oreMask(OreStyle style, boolean small) {
        if (small) return ORE_SMALL_MASK;
        return switch (style) {
            case GEM -> ORE_GEM_MASK;
            case CRYSTAL -> ORE_CRYSTAL_MASK;
            default -> ORE_VEIN_MASK;
        };
    }

    // ---------------------------------------------------------------------
    // Biome & derived materials
    // ---------------------------------------------------------------------

    private void paintRedClay(BufferedImage img, int index, Random rnd) {
        paintNoiseTile(img, index, 0xC05A30, 0x8A3D1E, 0.5f, rnd);
        int ox = tileX(index);
        int oy = tileY(index);
        for (int i = 0; i < 4; i++) {
            int x = rnd.nextInt(TILE_PX), y = rnd.nextInt(TILE_PX);
            img.setRGB(ox + x, oy + y, 0xFF000000 | 0xE07048);
        }
    }

    private void paintMycelium(BufferedImage img, int index, Random rnd) {
        paintNoiseTile(img, index, 0x9A7FB0, 0x6E5584, 0.55f, rnd);
        int ox = tileX(index);
        int oy = tileY(index);
        for (int i = 0; i < 5; i++) {
            int x = rnd.nextInt(TILE_PX), y = rnd.nextInt(TILE_PX);
            img.setRGB(ox + x, oy + y, 0xFF000000 | 0xC0A8D0);
        }
    }

    private void paintPackedIce(BufferedImage img, int index, Random rnd) {
        paintNoiseTile(img, index, 0xB0DCEF, 0x86C0D9, 0.4f, rnd);
        int ox = tileX(index);
        int oy = tileY(index);
        for (int i = 0; i < 5; i++) {
            int x = rnd.nextInt(TILE_PX), y = rnd.nextInt(TILE_PX);
            img.setRGB(ox + x, oy + y, 0xFF000000 | 0xE8FAFF);
        }
    }

    private void paintPumpkin(BufferedImage img, int index, Random rnd) {
        paintNoiseTile(img, index, 0xE8962E, 0xB8651F, 0.5f, rnd);
        int ox = tileX(index);
        int oy = tileY(index);
        // Vertical gourd ridges.
        for (int x = 2; x < TILE_PX; x += 3) {
            for (int y = 0; y < TILE_PX; y++) {
                img.setRGB(ox + x, oy + y, 0xFF000000 | 0x9A4E1A);
            }
        }
    }

    // ---------------------------------------------------------------------
    // Fluids
    // ---------------------------------------------------------------------

    /**
     * Paints a translucent fluid tile as two overlapping ripple waves between
     * a light and dark shade, plus a few bright sparkle pixels for glints off
     * the surface - water/lava's flowing translucent surface. Both waves run
     * an integer number of cycles across the tile (2 and 3), so the pattern
     * tiles - and the flowing-fluid scroll animation (see chunk.frag) wraps
     * with no visible seam - in either direction, the same guarantee the
     * previous single-band version had. Continuous sinusoids (rather than a
     * discrete per-column row shift feeding a hard triangle wave) read as
     * soft, organic ripples instead of a repeating zigzag/arrow motif, and
     * the second, finer wave crossing the first breaks it up the way real
     * overlapping wavelets do instead of one clean set of parallel bands.
     */
    private void paintFluidTile(BufferedImage img, int index, Random rnd, int lightColor, int darkColor, int alpha, int sparkleColor) {
        int ox = tileX(index);
        int oy = tileY(index);
        for (int y = 0; y < TILE_PX; y++) {
            for (int x = 0; x < TILE_PX; x++) {
                double primary = Math.sin(2 * Math.PI * (2.0 * y / TILE_PX + 0.18 * Math.sin(2 * Math.PI * x / TILE_PX)));
                double cross = Math.sin(2 * Math.PI * (3.0 * x / TILE_PX + 2.0 * y / TILE_PX));
                float t = 0.5f + 0.32f * (float) primary + 0.13f * (float) cross;
                t = Math.max(0f, Math.min(1f, t));
                img.setRGB(ox + x, oy + y, (alpha << 24) | lerpColor(darkColor, lightColor, t));
            }
        }
        int sparkles = 4 + rnd.nextInt(3);
        for (int i = 0; i < sparkles; i++) {
            int sx = rnd.nextInt(TILE_PX);
            int sy = rnd.nextInt(TILE_PX);
            img.setRGB(ox + sx, oy + sy, (Math.min(255, alpha + 45) << 24) | sparkleColor);
        }
    }

    // ---------------------------------------------------------------------
    // Cross-shaped decorations (transparent backgrounds)
    // ---------------------------------------------------------------------

    /** A tuft of a few vertical (slightly swaying) blade strokes on a fully transparent background. */
    private void paintCrossGrass(BufferedImage img, int index, Random rnd, int color) {
        int ox = tileX(index);
        int oy = tileY(index);
        int blades = 5 + rnd.nextInt(3);
        for (int b = 0; b < blades; b++) {
            int bx = 2 + rnd.nextInt(TILE_PX - 4);
            int height = 8 + rnd.nextInt(6);
            int topY = TILE_PX - height;
            int sway = rnd.nextInt(3) - 1;
            for (int y = topY; y < TILE_PX; y++) {
                int t = y - topY;
                int x = bx + (sway * t) / Math.max(1, height - 1);
                if (x < 0 || x >= TILE_PX) continue;
                // Blades shade lighter toward the tips.
                int c = t > height * 0.6f ? shade(color, 1.2f) : shade(color, 0.9f);
                img.setRGB(ox + x, oy + y, 0xFF000000 | c);
            }
        }
    }

    /**
     * A flower on a swaying stem: the red poppy (four petals around a dark
     * center) or the yellow dandelion (a dense fluffy head), each with a small
     * leaf pair at the base, on a transparent background.
     */
    private void paintCrossFlower(BufferedImage img, int index, Random rnd, int stemColor, int petalColor, int centerColor, boolean puffy) {
        int ox = tileX(index);
        int oy = tileY(index);
        int stemX = 8;
        // Swaying 2px stem with a lighter edge.
        for (int y = TILE_PX - 6; y < TILE_PX; y++) {
            img.setRGB(ox + stemX, oy + y, 0xFF000000 | stemColor);
            img.setRGB(ox + stemX + 1, oy + y, 0xFF000000 | shade(stemColor, 0.8f));
        }
        // Small leaves at the base of the stem.
        img.setRGB(ox + stemX - 2, oy + TILE_PX - 4, 0xFF000000 | stemColor);
        img.setRGB(ox + stemX + 3, oy + TILE_PX - 4, 0xFF000000 | stemColor);

        // Bloom head, radially shaded (lit from above).
        double cx = 7.5, cy = 6.5;
        for (int y = 0; y < TILE_PX; y++) {
            for (int x = 0; x < TILE_PX; x++) {
                double dx = x - cx, dy = (y - cy) * 1.15;
                double d = Math.hypot(dx, dy);
                if (d > 4.5) continue;
                if (puffy) {
                    // Dandelion: a dense fluffy head, brighter toward the top.
                    float t = Math.max(0f, 1f - (float) d / 4.5f);
                    img.setRGB(ox + x, oy + y, 0xFF000000 | lerpColor(shade(petalColor, 0.85f), shade(petalColor, 1.2f), t));
                } else {
                    // Poppy: four petals around a dark center; the diagonal
                    // bands between petals stay transparent as petal gaps.
                    if (d <= 1.3) {
                        img.setRGB(ox + x, oy + y, 0xFF000000 | centerColor);
                    } else {
                        if (Math.abs(dx) > 1.7 && Math.abs(dy) > 1.7 && d > 2.2) continue;
                        float t = Math.max(0f, 1f - (float) d / 4.5f);
                        img.setRGB(ox + x, oy + y, 0xFF000000 | lerpColor(shade(petalColor, 0.8f), shade(petalColor, 1.18f), t));
                    }
                }
            }
        }
        if (puffy) {
            // A few darker fluff seeds and green sepals under the head.
            for (int i = 0; i < 5; i++) {
                int x = 5 + rnd.nextInt(6);
                int y = 3 + rnd.nextInt(4);
                img.setRGB(ox + x, oy + y, 0xFF000000 | shade(petalColor, 0.85f));
            }
            img.setRGB(ox + 7, oy + 9, 0xFF000000 | stemColor);
            img.setRGB(ox + 8, oy + 9, 0xFF000000 | stemColor);
        }
    }

    /** A round leafy bush silhouette speckled with berries, on a transparent background. */
    private void paintBerryBush(BufferedImage img, int index, Random rnd) {
        int ox = tileX(index);
        int oy = tileY(index);
        double cx = 8, cy = 10, r = 6.5;
        for (int y = 0; y < TILE_PX; y++) {
            for (int x = 0; x < TILE_PX; x++) {
                double d = Math.hypot(x - cx, (y - cy) * 1.3);
                if (d <= r) {
                    float roll = rnd.nextFloat();
                    int color = roll < 0.15f ? 0x9C2B4E : (roll < 0.55f ? 0x3E8E35 : 0x2F701F);
                    // A light top edge makes the bush read as a sphere.
                    if (y < cy) color = shade(color, 1.15f);
                    img.setRGB(ox + x, oy + y, 0xFF000000 | color);
                }
            }
        }
    }

    /** A dead, dried-out twig clump on a transparent background. */
    private void paintDeadBush(BufferedImage img, int index, Random rnd) {
        int ox = tileX(index);
        int oy = tileY(index);
        int color = 0x6E5428;
        for (int b = 0; b < 6; b++) {
            int bx = 2 + rnd.nextInt(TILE_PX - 4);
            int height = 4 + rnd.nextInt(5);
            int topY = TILE_PX - height;
            int sway = rnd.nextInt(3) - 1;
            for (int y = topY; y < TILE_PX; y++) {
                int t = y - topY;
                int x = bx + (sway * t) / Math.max(1, height - 1);
                if (x < 0 || x >= TILE_PX) continue;
                img.setRGB(ox + x, oy + y, 0xFF000000 | color);
            }
        }
    }

    /** A simple stem + rounded cap on a transparent background. */
    private void paintMushroom(BufferedImage img, int index, Random rnd, int capColor, int capDark) {
        int ox = tileX(index);
        int oy = tileY(index);
        int stemColor = 0xD9D2C0;
        int stemX = TILE_PX / 2;
        for (int y = TILE_PX - 7; y < TILE_PX - 1; y++) {
            img.setRGB(ox + stemX, oy + y, 0xFF000000 | stemColor);
            img.setRGB(ox + stemX + 1, oy + y, 0xFF000000 | shade(stemColor, 0.9f));
        }
        double cx = TILE_PX / 2.0, cy = TILE_PX - 9.0;
        for (int y = 0; y < TILE_PX; y++) {
            for (int x = 0; x < TILE_PX; x++) {
                double d = Math.hypot(x - cx, (y - cy) * 1.5);
                if (d <= 5.5) {
                    int color = rnd.nextFloat() < 0.3f ? capDark : capColor;
                    if (y < cy) color = shade(color, 1.12f);
                    img.setRGB(ox + x, oy + y, 0xFF000000 | color);
                }
            }
        }
    }

    /** A thin green vine strand with a few leaves, on a transparent background. */
    private void paintVine(BufferedImage img, int index, Random rnd) {
        int ox = tileX(index);
        int oy = tileY(index);
        for (int y = 0; y < TILE_PX; y++) {
            img.setRGB(ox + 7, oy + y, 0xFF000000 | 0x2F6B2F);
            img.setRGB(ox + 8, oy + y, 0xFF000000 | 0x3E8E35);
        }
        for (int i = 0; i < 7; i++) {
            int x = 5 + rnd.nextInt(6);
            int y = 2 + rnd.nextInt(TILE_PX - 4);
            img.setRGB(ox + x, oy + y, 0xFF000000 | 0x4E9A3E);
        }
    }

    /** A tall green bamboo stalk with segment rings and a few leaves, on a transparent background. */
    private void paintBamboo(BufferedImage img, int index, Random rnd) {
        int ox = tileX(index);
        int oy = tileY(index);
        for (int y = 0; y < TILE_PX; y++) {
            img.setRGB(ox + 7, oy + y, 0xFF000000 | 0x5FA84C);
            img.setRGB(ox + 8, oy + y, 0xFF000000 | 0x7AC460);
        }
        for (int y = 2; y < TILE_PX; y += 5) {
            img.setRGB(ox + 6, oy + y, 0xFF000000 | 0x4A8A3A);
            img.setRGB(ox + 9, oy + y, 0xFF000000 | 0x4A8A3A);
        }
        for (int i = 0; i < 4; i++) {
            int x = 3 + rnd.nextInt(9);
            int y = 1 + rnd.nextInt(TILE_PX - 4);
            img.setRGB(ox + x, oy + y, 0xFF000000 | 0x4E9A3E);
        }
    }

    /** A flat elliptical pad floating on water, on a transparent background. */
    private void paintLilyPad(BufferedImage img, int index, Random rnd) {
        int ox = tileX(index);
        int oy = tileY(index);
        double cx = 8, cy = 10, r = 6;
        for (int y = 0; y < TILE_PX; y++) {
            for (int x = 0; x < TILE_PX; x++) {
                double d = Math.hypot(x - cx, (y - cy) * 0.5);
                if (d <= r) {
                    int color = rnd.nextFloat() < 0.3f ? 0x3E8E35 : 0x2F701F;
                    if (y < cy) color = shade(color, 1.12f);
                    img.setRGB(ox + x, oy + y, 0xFF000000 | color);
                }
            }
        }
    }

    /** Wavy green strands of seaweed, on a transparent background. */
    private void paintSeaweed(BufferedImage img, int index, Random rnd) {
        int ox = tileX(index);
        int oy = tileY(index);
        for (int b = 0; b < 3; b++) {
            int bx = 3 + b * 4 + rnd.nextInt(2);
            int height = 9 + rnd.nextInt(6);
            int topY = TILE_PX - height;
            int sway = (rnd.nextBoolean() ? 1 : -1) * (1 + rnd.nextInt(2));
            for (int y = topY; y < TILE_PX; y++) {
                int t = y - topY;
                int x = bx + (sway * t * t) / Math.max(1, height * height);
                if (x < 0 || x >= TILE_PX) continue;
                int c = t > height * 0.7f ? 0x4AA048 : 0x2F8F3A;
                img.setRGB(ox + x, oy + y, 0xFF000000 | c);
            }
        }
    }

    // ---------------------------------------------------------------------
    // Phase 0: Farming tiles
    // ---------------------------------------------------------------------

    /**
     * Farmland top: dark brown tilled dirt with faint horizontal furrow lines.
     * The sides and bottom reuse the dirt tile (index 3) directly from the atlas;
     * only the top face uses this special tile. Index 2 is the grass side
     * (dirt + green fringe) and must not be used for farmland sides.
     */
    private void paintFarmlandTop(BufferedImage img, int index, Random rnd) {
        int ox = tileX(index);
        int oy = tileY(index);
        // Base: slightly darker, moister-looking dirt
        for (int y = 0; y < TILE_PX; y++) {
            for (int x = 0; x < TILE_PX; x++) {
                int t = (int) (valueNoise(x, y, 4) * 20);
                int c = lerpColor(0x5A3A18, 0x3D2410, t / 20f);
                img.setRGB(ox + x, oy + y, 0xFF000000 | c);
            }
        }
        // Horizontal furrow lines every 4 pixels
        for (int y = 3; y < TILE_PX; y += 4) {
            for (int x = 0; x < TILE_PX; x++) {
                int cur = img.getRGB(ox + x, oy + y) & 0xFFFFFF;
                img.setRGB(ox + x, oy + y, 0xFF000000 | shade(cur, 0.6f));
            }
        }
    }

    /**
     * A crop growth stage rendered as a thin upright cross (same X-shape the
     * renderer uses for any {@code cross=true} block). {@code fillFrac} (0..1)
     * controls how tall the plant appears: 0.25 = a tiny sprout, 1.0 = fully grown.
     * The colour shifts from pure green when young to golden when ripe.
     */
    private void paintCropStage(BufferedImage img, int index, Random rnd, int topColor, float fillFrac) {
        int ox = tileX(index);
        int oy = tileY(index);
        // Clear to transparent first (crops render as a cutout cross)
        for (int y = 0; y < TILE_PX; y++)
            for (int x = 0; x < TILE_PX; x++)
                img.setRGB(ox + x, oy + y, 0);

        int stemColor = shade(topColor, 0.65f);
        int maxY  = TILE_PX - 1;
        // Clamp to 0: when fillFrac==1.0, raw minY would be 15-16=-1, which
        // overpaints one row of the tile above in the atlas.
        int minY  = Math.max(0, maxY - Math.max(2, Math.round(TILE_PX * fillFrac)));

        // Two crossing diagonal stripes (X plant) - each 2px wide
        for (int y = minY; y <= maxY; y++) {
            // Stripe from top-left to bottom-right
            int x1 = (int) ((y - minY) * (TILE_PX - 1f) / Math.max(1, maxY - minY));
            // Stripe from top-right to bottom-left
            int x2 = TILE_PX - 1 - x1;

            int c = y > maxY - Math.round((maxY - minY) * 0.3f) ? stemColor : topColor;
            for (int dx = -1; dx <= 1; dx++) {
                if (x1 + dx >= 0 && x1 + dx < TILE_PX)
                    img.setRGB(ox + x1 + dx, oy + y, 0xFF000000 | c);
                if (x2 + dx >= 0 && x2 + dx < TILE_PX)
                    img.setRGB(ox + x2 + dx, oy + y, 0xFF000000 | c);
            }
        }
    }

    /** Sugar cane: a tall, segmented green stalk rendered as a cross billboard. */
    private void paintSugarCane(BufferedImage img, int index, Random rnd) {
        int ox = tileX(index);
        int oy = tileY(index);
        // Transparent background
        for (int y = 0; y < TILE_PX; y++)
            for (int x = 0; x < TILE_PX; x++)
                img.setRGB(ox + x, oy + y, 0);

        // Draw two vertical stripes (the X stripes are wide for sugar cane)
        int[] xCols = {5, 6, 9, 10};
        for (int x : xCols) {
            for (int y = 0; y < TILE_PX; y++) {
                // Segment lines every 5 pixels (horizontal marks on the stalk)
                boolean segLine = (y % 5 == 0);
                int c = segLine ? 0x3A8A28 : (y < TILE_PX / 3 ? 0x58C040 : 0x48A830);
                img.setRGB(ox + x, oy + y, 0xFF000000 | c);
            }
        }
    }

    /** Blue-grey clay block: a uniform fine-grained surface with subtle speckle. */
    private void paintClayBlock(BufferedImage img, int index, Random rnd) {
        int ox = tileX(index);
        int oy = tileY(index);
        int base = 0x9BA7B0; // muted blue-grey
        for (int y = 0; y < TILE_PX; y++) {
            for (int x = 0; x < TILE_PX; x++) {
                int noise = rnd.nextInt(18) - 9;
                int r = Math.max(0, Math.min(255, ((base >> 16) & 0xFF) + noise));
                int g = Math.max(0, Math.min(255, ((base >> 8)  & 0xFF) + noise));
                int b = Math.max(0, Math.min(255, ( base        & 0xFF) + noise));
                img.setRGB(ox + x, oy + y, 0xFF000000 | (r << 16) | (g << 8) | b);
            }
        }
    }

    /** Wet/hydrated farmland top: dark near-black moist soil with faint furrow lines. */
    private void paintWetFarmlandTop(BufferedImage img, int index, Random rnd) {
        int ox = tileX(index);
        int oy = tileY(index);
        int base = 0x2A1A0E; // very dark moist brown
        for (int y = 0; y < TILE_PX; y++) {
            for (int x = 0; x < TILE_PX; x++) {
                boolean furrow = (y % 4 == 0);
                int noise = rnd.nextInt(12) - 6;
                int br = furrow ? base - 0x100800 : base;
                int r = Math.max(0, Math.min(255, ((br >> 16) & 0xFF) + noise));
                int g = Math.max(0, Math.min(255, ((br >> 8)  & 0xFF) + noise));
                int b = Math.max(0, Math.min(255, ( br        & 0xFF) + noise));
                img.setRGB(ox + x, oy + y, 0xFF000000 | (r << 16) | (g << 8) | b);
            }
        }
    }

    // ---------------------------------------------------------------------
    // Furniture / fixtures
    // ---------------------------------------------------------------------

    /** Semi-transparent glass with thin pane-divider lines, so you can see through it. */
    private void paintGlass(BufferedImage img, int index) {
        int ox = tileX(index);
        int oy = tileY(index);
        int fill = 0xBEE7EA;
        int line = 0x8FB9BC;
        for (int y = 0; y < TILE_PX; y++) {
            for (int x = 0; x < TILE_PX; x++) {
                img.setRGB(ox + x, oy + y, (150 << 24) | fill);
            }
        }
        for (int i = 0; i < TILE_PX; i++) {
            img.setRGB(ox + i, oy, (190 << 24) | line);
            img.setRGB(ox + i, oy + TILE_PX - 1, (190 << 24) | line);
            img.setRGB(ox, oy + i, (190 << 24) | line);
            img.setRGB(ox + TILE_PX - 1, oy + i, (190 << 24) | line);
        }
        int mid = TILE_PX / 2;
        for (int i = 0; i < TILE_PX; i++) {
            img.setRGB(ox + mid, oy + i, (190 << 24) | line);
            img.setRGB(ox + i, oy + mid, (190 << 24) | line);
        }
    }

    /** A wooden door panel: plank boards with grooves, a crossbar and a handle. */
    private void paintDoor(BufferedImage img, int index, Random rnd) {
        paintPlanks(img, index, rnd);
        int ox = tileX(index);
        int oy = tileY(index);
        int dark = 0x6E4F28;
        // Vertical board grooves.
        for (int x = 3; x < TILE_PX; x += 4) {
            for (int y = 0; y < TILE_PX; y++) {
                img.setRGB(ox + x, oy + y, 0xFF000000 | dark);
            }
        }
        // Crossbar.
        for (int x = 0; x < TILE_PX; x++) {
            img.setRGB(ox + x, oy + 9, 0xFF000000 | dark);
        }
        img.setRGB(ox + 13, oy + 11, 0xFF000000 | 0x3A2613);
    }

    /** A trapdoor top: planks with a metal hinge strip and a frame. */
    private void paintTrapdoor(BufferedImage img, int index, Random rnd) {
        paintPlanks(img, index, rnd);
        int ox = tileX(index);
        int oy = tileY(index);
        int dark = 0x6E4F28, metal = 0x8A8A8A;
        // Frame border.
        for (int i = 0; i < TILE_PX; i++) {
            img.setRGB(ox + i, oy, 0xFF000000 | dark);
            img.setRGB(ox + i, oy + TILE_PX - 1, 0xFF000000 | dark);
            img.setRGB(ox, oy + i, 0xFF000000 | dark);
            img.setRGB(ox + TILE_PX - 1, oy + i, 0xFF000000 | dark);
        }
        // Metal hinge strip along one edge.
        for (int i = 0; i < TILE_PX; i++) {
            img.setRGB(ox + i, oy + 2, 0xFF000000 | metal);
            img.setRGB(ox + i, oy + 3, 0xFF000000 | metal);
        }
    }

    /** A glowing full-cube lamp: a warm light panel with a bright center and a darker frame. */
    private void paintLamp(BufferedImage img, int index) {
        int ox = tileX(index);
        int oy = tileY(index);
        int frame = 0x6E4A2A;
        int warm = 0xFFE08A;
        int core = 0xFFFFF0;
        for (int y = 0; y < TILE_PX; y++) {
            for (int x = 0; x < TILE_PX; x++) {
                boolean edge = x < 2 || x >= TILE_PX - 2 || y < 2 || y >= TILE_PX - 2;
                boolean center = x >= 5 && x < 11 && y >= 5 && y < 11;
                int color = edge ? frame : (center ? core : warm);
                img.setRGB(ox + x, oy + y, 0xFF000000 | color);
            }
        }
    }

    /** A furnace's front face: a stone body with a dark mouth and a lintel above it. */
    private void paintFurnace(BufferedImage img, int index) {
        Random rnd = new Random(7);
        paintStone(img, index, rnd);
        int ox = tileX(index);
        int oy = tileY(index);
        int dark = 0x2E2E2E;
        int lintel = 0x6E6E6E;
        // Lintel across the top of the opening.
        for (int x = 3; x < 13; x++) {
            img.setRGB(ox + x, oy + 5, 0xFF000000 | lintel);
        }
        // Dark mouth.
        for (int y = 6; y < 12; y++) {
            for (int x = 4; x < 12; x++) {
                img.setRGB(ox + x, oy + y, 0xFF000000 | dark);
            }
        }
    }

    /** Dark red netherrack: a speckled brick-red base with black flecks for a charred, hellish look. */
    private void paintNetherrack(BufferedImage img, int index, Random rnd) {
        paintTile(img, index, rnd, 0x7A3B2E, 0x5C2A20, true);
        int ox = tileX(index);
        int oy = tileY(index);
        for (int b = 0; b < 6; b++) {
            int cx = rnd.nextInt(TILE_PX);
            int cy = rnd.nextInt(TILE_PX);
            img.setRGB(ox + cx, oy + cy, 0xFF000000 | 0x33130D);
            if (rnd.nextBoolean()) {
                img.setRGB(ox + (cx + 1) % TILE_PX, oy + cy, 0xFF000000 | 0x8A4634);
            }
        }
    }

    /** Soul sand: a dark, pitted brown - clearly NOT the overworld's pale beach sand. */
    private void paintSoulSand(BufferedImage img, int index, Random rnd) {
        int ox = tileX(index);
        int oy = tileY(index);
        for (int y = 0; y < TILE_PX; y++) {
            for (int x = 0; x < TILE_PX; x++) {
                img.setRGB(ox + x, oy + y, 0xFF000000 | 0x5A4632);
            }
        }
        // Mottled darker/redder patches so it reads as dim, grainy nether ground.
        for (int i = 0; i < 40; i++) {
            int cx = rnd.nextInt(TILE_PX);
            int cy = rnd.nextInt(TILE_PX);
            int color = rnd.nextFloat() < 0.5f ? 0x44351F : 0x6A4A34;
            for (int dy = -1; dy <= 1; dy++) {
                for (int dx = -1; dx <= 1; dx++) {
                    int px = cx + dx, py = cy + dy;
                    if (px >= 0 && px < TILE_PX && py >= 0 && py < TILE_PX && rnd.nextFloat() < 0.6f) {
                        img.setRGB(ox + px, oy + py, 0xFF000000 | color);
                    }
                }
            }
        }
        // A few deep pits for the classic crumbly soul-sand look.
        for (int i = 0; i < 6; i++) {
            int cx = rnd.nextInt(TILE_PX);
            int cy = rnd.nextInt(TILE_PX);
            img.setRGB(ox + cx, oy + cy, 0xFF000000 | 0x2B2012);
        }
    }

    /** A swirling nether portal: a dark purple vortex with a fiery orange core, on an opaque background. */
    private void paintNetherPortal(BufferedImage img, int index, Random rnd) {
        int ox = tileX(index);
        int oy = tileY(index);
        double cx = TILE_PX / 2.0 - 0.5, cy = TILE_PX / 2.0 - 0.5;
        for (int y = 0; y < TILE_PX; y++) {
            for (int x = 0; x < TILE_PX; x++) {
                double dx = (x - cx) / 8.0, dy = (y - cy) / 8.0;
                double d = Math.sqrt(dx * dx + dy * dy);
                double swirl = Math.sin(Math.atan2(dy, dx) * 3 + d * 6);
                int color = d < 0.35 ? 0xFFB84D : (swirl > 0 ? 0x7A2E8A : 0x3E1250);
                img.setRGB(ox + x, oy + y, 0xFF000000 | color);
            }
        }
    }

    /** Near-black obsidian with subtle purple sheen and a few glossy streaks. */
    private void paintObsidian(BufferedImage img, int index, Random rnd) {
        for (int y = 0; y < TILE_PX; y++) {
            for (int x = 0; x < TILE_PX; x++) {
                int base = rnd.nextFloat() < 0.08f ? 0x3A2A52 : 0x1A1426;
                img.setRGB(tileX(index) + x, tileY(index) + y, 0xFF000000 | base);
            }
        }
        int ox = tileX(index);
        int oy = tileY(index);
        for (int s = 0; s < 3; s++) {
            int sx = rnd.nextInt(TILE_PX);
            int sy = rnd.nextInt(TILE_PX);
            for (int i = 0; i < 4; i++) {
                int px = (sx + i) % TILE_PX;
                int py = (sy + (i * 3) % 4) % TILE_PX;
                img.setRGB(ox + px, oy + py, 0xFF000000 | 0x4A3A66);
            }
        }
    }

    /** A dark end portal: a near-black swirl with tiny green star-sparkles, like the void made solid. */
    private void paintEndPortal(BufferedImage img, int index, Random rnd) {
        int ox = tileX(index);
        int oy = tileY(index);
        double cx = TILE_PX / 2.0 - 0.5, cy = TILE_PX / 2.0 - 0.5;
        for (int y = 0; y < TILE_PX; y++) {
            for (int x = 0; x < TILE_PX; x++) {
                double dx = (x - cx) / 8.0, dy = (y - cy) / 8.0;
                double d = Math.sqrt(dx * dx + dy * dy);
                double swirl = Math.sin(Math.atan2(dy, dx) * 4 + d * 5);
                int color = d < 0.3 ? 0x1B6B2B : (swirl > 0 ? 0x143A1E : 0x0A1E10);
                img.setRGB(ox + x, oy + y, 0xFF000000 | color);
            }
        }
        for (int b = 0; b < 5; b++) {
            int sx = 1 + rnd.nextInt(TILE_PX - 2);
            int sy = 1 + rnd.nextInt(TILE_PX - 2);
            img.setRGB(ox + sx, oy + sy, 0xFF000000 | 0x9FE89F);
        }
    }

    /**
     * A lit furnace's front face: the same stone body and lintel as
     * {@link #paintFurnace}, but the mouth glows - warm orange around the rim
     * with a bright yellow core, the classic "furnace is burning" look. The
     * front tile is swapped for this one while the furnace is actively
     * smelting (see {@link com.minecraftclone.world.Chunk#emitFace}).
     */
    private void paintFurnaceLit(BufferedImage img, int index) {
        int ox = tileX(index);
        int oy = tileY(index);
        int stone = 0x8A8A8A;
        int lintel = 0x6E6E6E;
        int ember = 0xFFB040;
        int glow = 0xFFD060;
        int core = 0xFFF080;
        for (int y = 0; y < TILE_PX; y++) {
            for (int x = 0; x < TILE_PX; x++) {
                img.setRGB(ox + x, oy + y, 0xFF000000 | stone);
            }
        }
        // Lintel across the top of the opening.
        for (int x = 3; x < 13; x++) {
            img.setRGB(ox + x, oy + 5, 0xFF000000 | lintel);
        }
        // Glowing mouth: warm rim, brighter toward the center.
        for (int y = 6; y < 12; y++) {
            for (int x = 4; x < 12; x++) {
                boolean corePx = x >= 6 && x < 10 && y >= 7 && y < 11;
                boolean midPx = x >= 5 && x < 11 && y >= 6 && y < 12;
                int color = corePx ? core : (midPx ? glow : ember);
                img.setRGB(ox + x, oy + y, 0xFF000000 | color);
            }
        }
    }

    /** A planks workbench top: light wood with a darker 2x2 grid and corner bolts. */
    private void paintCraftingTable(BufferedImage img, int index) {
        Random rnd = new Random(11);
        paintPlanks(img, index, rnd);
        int ox = tileX(index);
        int oy = tileY(index);
        int grid = 0x6E4F28;
        // 2x2 inset panel, slightly darker, like the four planks of the recipe.
        for (int y = 3; y < 13; y++) {
            for (int x = 3; x < 13; x++) {
                if (x == 3 || x == 7 || x == 11 || y == 3 || y == 7 || y == 11) {
                    img.setRGB(ox + x, oy + y, 0xFF000000 | grid);
                }
            }
        }
    }

    /** A wooden oak chest: distinct lid/body, dark frame, brass lock on the front. */
    private enum ChestFace { FRONT, SIDE, TOP, BOTTOM, BACK, TOP_PLAIN }

    private void paintChest(BufferedImage img, int index, ChestFace face) {
        paintChestInto(img, tileX(index), tileY(index), TILE_PX, face);
    }

    /**
     * Paints a 32-wide chest and blits the left/right 16px halves. The join
     * has no rim and the lock/latch sits on the center so a pair reads as
     * one chest, the way Minecraft's large-chest texture does.
     */
    private void paintChestPair(BufferedImage img, int leftIndex, int rightIndex, ChestFace face) {
        BufferedImage wide = new BufferedImage(TILE_PX * 2, TILE_PX, BufferedImage.TYPE_INT_ARGB);
        paintChestInto(wide, 0, 0, TILE_PX * 2, face);
        blitChestHalf(wide, 0, img, leftIndex);
        blitChestHalf(wide, TILE_PX, img, rightIndex);
    }

    private void blitChestHalf(BufferedImage src, int srcX, BufferedImage dest, int destIndex) {
        int dx = tileX(destIndex);
        int dy = tileY(destIndex);
        for (int y = 0; y < TILE_PX; y++) {
            for (int x = 0; x < TILE_PX; x++) {
                dest.setRGB(dx + x, dy + y, src.getRGB(srcX + x, y));
            }
        }
    }

    private void paintChestInto(BufferedImage img, int ox, int oy, int width, ChestFace face) {
        Random rnd = new Random(50 + face.ordinal() * 31);
        ChestFace palette = switch (face) {
            case BACK -> ChestFace.FRONT;
            case TOP_PLAIN -> ChestFace.TOP;
            default -> face;
        };
        int light = switch (palette) {
            case TOP, TOP_PLAIN -> 0xD4B06C;
            case BOTTOM -> 0x8A5E2C;
            case FRONT, SIDE, BACK -> 0xC9A15B;
        };
        int dark = switch (palette) {
            case TOP, TOP_PLAIN -> 0x8E5C28;
            case BOTTOM -> 0x5A3818;
            case FRONT, SIDE, BACK -> 0x7A4E22;
        };
        float oxs = noiseOffset(rnd);
        float oys = noiseOffset(rnd);
        float xMid = (width - 1) / 2f;
        for (int y = 0; y < TILE_PX; y++) {
            for (int x = 0; x < width; x++) {
                float n = (fbm(x + oxs, y + oys) - 0.5f) * 0.42f + 0.5f;
                int color = lerpColor(dark, light, n);
                float dx = (x - xMid) / xMid;
                float dy = (y - 7.5f) / 7.5f;
                float top = 1f + 0.04f - 0.09f * (y / (TILE_PX - 1f));
                float f = top * (1f - 0.04f * (dx * dx + dy * dy));
                img.setRGB(ox + x, oy + y, 0xFF000000 | shade(color, f));
            }
        }
        int rim = 0x2C1A0C;
        int inner = 0x5A3818;
        int seam = 0x3A2410;
        int lidHi = 0xD8B878;
        int brassHi = 0xF2D878;
        int brass = 0xD4B44A;
        int brassLo = 0x8A6A18;
        int hole = 0x1A1008;

        boolean wall = face == ChestFace.FRONT || face == ChestFace.SIDE || face == ChestFace.BACK;
        int seamY = 5;

        if (wall) {
            // Lid is the top band: lift it; body below the seam: drop it.
            for (int y = 1; y < TILE_PX - 1; y++) {
                for (int x = 1; x < width - 1; x++) {
                    int rgb = img.getRGB(ox + x, oy + y) & 0xFFFFFF;
                    float f = y < seamY ? 1.10f : 0.88f;
                    img.setRGB(ox + x, oy + y, 0xFF000000 | shade(rgb, f));
                }
            }
            for (int x = 1; x < width - 1; x++) {
                img.setRGB(ox + x, oy + seamY, 0xFF000000 | seam);
                if (seamY + 1 < TILE_PX - 1) {
                    img.setRGB(ox + x, oy + seamY + 1, 0xFF000000 | shade(seam, 1.35f));
                }
            }
            for (int x = 2; x < width - 2; x++) {
                img.setRGB(ox + x, oy + 2, 0xFF000000 | shade(lidHi, 0.92f + 0.04f * (x % 3)));
            }
        }

        for (int x = 1; x < width - 1; x++) {
            img.setRGB(ox + x, oy + 1, 0xFF000000 | inner);
            img.setRGB(ox + x, oy + TILE_PX - 2, 0xFF000000 | inner);
        }
        for (int y = 1; y < TILE_PX - 1; y++) {
            img.setRGB(ox + 1, oy + y, 0xFF000000 | inner);
            img.setRGB(ox + width - 2, oy + y, 0xFF000000 | inner);
        }

        if (face == ChestFace.FRONT) {
            // Brass lock plate centred on the lid seam, with a keyhole.
            int lockX = width / 2 - 2;
            int[][] lock = {
                    {0, 1, brassHi}, {0, 2, brassHi},
                    {1, 0, brass}, {1, 1, brassHi}, {1, 2, brassHi}, {1, 3, brass},
                    {2, 0, brass}, {2, 1, hole}, {2, 2, hole}, {2, 3, brass},
                    {3, 0, brassLo}, {3, 1, brass}, {3, 2, brass}, {3, 3, brassLo},
                    {4, 1, brassLo}, {4, 2, brassLo},
            };
            for (int[] p : lock) {
                int y = seamY - 1 + p[0];
                int x = lockX + p[1];
                img.setRGB(ox + x, oy + y, 0xFF000000 | p[2]);
            }
        }

        if (face == ChestFace.TOP) {
            // Latch tab on the front edge of the lid, centred so a pair shares one.
            int latchX0 = width / 2 - 2;
            for (int y = 12; y <= 14; y++) {
                for (int x = latchX0; x <= latchX0 + 3; x++) {
                    int c = (y == 12) ? brassHi : ((x == latchX0 || x == latchX0 + 3) ? brassLo : brass);
                    img.setRGB(ox + x, oy + y, 0xFF000000 | c);
                }
            }
        }

        if (face == ChestFace.SIDE && width == TILE_PX) {
            // Thin dark straps so the side doesn't read as a plain plank.
            for (int y = 2; y < TILE_PX - 2; y++) {
                img.setRGB(ox + 3, oy + y, 0xFF000000 | inner);
                img.setRGB(ox + 12, oy + y, 0xFF000000 | inner);
            }
        }

        for (int x = 0; x < width; x++) {
            img.setRGB(ox + x, oy, 0xFF000000 | rim);
            img.setRGB(ox + x, oy + TILE_PX - 1, 0xFF000000 | rim);
        }
        for (int y = 0; y < TILE_PX; y++) {
            img.setRGB(ox, oy + y, 0xFF000000 | rim);
            img.setRGB(ox + width - 1, oy + y, 0xFF000000 | rim);
        }
    }

    /**
     * Destroy-stage crack overlay: transparent with accumulating dark cracks.
     * Same seed every stage so later frames keep earlier cracks and add more.
     */
    private void paintDestroyStage(BufferedImage img, int index, int stage) {
        int ox = tileX(index);
        int oy = tileY(index);
        int cracks = 4 + stage * 3;
        for (int c = 0; c < cracks; c++) {
            // Per-crack seed: crack c is identical in every stage, so later
            // stages keep the earlier cracks and only add new ones.
            java.util.Random rnd = new java.util.Random(4242L + c * 7919L);
            int x = 1 + rnd.nextInt(TILE_PX - 2);
            int y = 1 + rnd.nextInt(TILE_PX - 2);
            int len = 3 + rnd.nextInt(5) + stage / 3;
            int dx = rnd.nextInt(3) - 1;
            int dy = rnd.nextInt(3) - 1;
            if (dx == 0 && dy == 0) dy = 1;
            int color = (c % 4 == 0) ? 0xFF3A3A3A : 0xFF0A0A0A;
            for (int i = 0; i < len; i++) {
                if (x >= 0 && x < TILE_PX && y >= 0 && y < TILE_PX) {
                    img.setRGB(ox + x, oy + y, color);
                    if (stage >= 5 && x + 1 < TILE_PX) {
                        img.setRGB(ox + x + 1, oy + y, color);
                    }
                }
                x += dx;
                y += dy;
                if (rnd.nextFloat() < 0.35f) {
                    int ndx = rnd.nextInt(3) - 1;
                    int ndy = rnd.nextInt(3) - 1;
                    if (ndx != 0 || ndy != 0) {
                        dx = ndx;
                        dy = ndy;
                    }
                }
            }
        }
    }

    /** A red bed: a wool top with a darker foot-end panel and pillow area. */
    private void paintBed(BufferedImage img, int index, Random rnd) {
        int ox = tileX(index);
        int oy = tileY(index);
        // Base wool texture (red)
        paintNoiseTile(img, index, 0xC0392B, 0x922B21, 0.45f, rnd);
        // Darker foot-end panel (right side)
        for (int x = 10; x < TILE_PX; x++) {
            for (int y = 0; y < TILE_PX; y++) {
                img.setRGB(ox + x, oy + y, 0xFF000000 | shade(0x7B241C, faceShade(x, y)));
            }
        }
        // Pillow area (lighter, left side)
        for (int x = 0; x < 5; x++) {
            for (int y = 0; y < TILE_PX; y++) {
                img.setRGB(ox + x, oy + y, 0xFF000000 | shade(0xE74C3C, faceShade(x, y)));
            }
        }
    }

    /** Bed blanket top: rich red with a subtle diamond quilt pattern. */
    private void paintBedTop(BufferedImage img, int index, Random rnd) {
        int ox = tileX(index);
        int oy = tileY(index);
        // Base red blanket
        paintNoiseTile(img, index, 0xC0392B, 0x922821, 0.4f, rnd);
        // Diamond quilt pattern
        for (int y = 0; y < TILE_PX; y++) {
            for (int x = 0; x < TILE_PX; x++) {
                // Diamond pattern: alternating light/dark diamonds
                int dx = x - 8, dy = y - 8;
                if (dx < 0) dx = -dx;
                if (dy < 0) dy = -dy;
                if ((dx + dy) % 4 == 0) {
                    img.setRGB(ox + x, oy + y, 0xFF000000 | shade(0xA02020, faceShade(x, y)));
                }
            }
        }
        // Subtle stitch lines
        for (int i = 2; i < TILE_PX; i += 4) {
            for (int y = 0; y < TILE_PX; y++) {
                if (rnd.nextFloat() < 0.3f) {
                    img.setRGB(ox + i, oy + y, 0xFF000000 | shade(0x8B1C1C, faceShade(i, y)));
                }
            }
        }
    }

    /** Bed blanket side: red with horizontal stripe detail. */
    private void paintBedSide(BufferedImage img, int index, Random rnd) {
        int ox = tileX(index);
        int oy = tileY(index);
        // Base red
        paintNoiseTile(img, index, 0xB03030, 0x802020, 0.35f, rnd);
        // Horizontal stripes (quilt effect)
        for (int y = 0; y < TILE_PX; y++) {
            if (y % 4 == 0) {
                for (int x = 0; x < TILE_PX; x++) {
                    img.setRGB(ox + x, oy + y, 0xFF000000 | shade(0x901818, faceShade(x, y)));
                }
            }
        }
        // Darker bottom edge
        for (int y = TILE_PX - 2; y < TILE_PX; y++) {
            for (int x = 0; x < TILE_PX; x++) {
                img.setRGB(ox + x, oy + y, 0xFF000000 | shade(0x701010, faceShade(x, y)));
            }
        }
    }

    /** Bed foot end: dark wood frame with red accent. */
    private void paintBedFoot(BufferedImage img, int index, Random rnd) {
        int ox = tileX(index);
        int oy = tileY(index);
        // Dark wood frame
        paintNoiseTile(img, index, 0x5A4030, 0x3A2820, 0.5f, rnd);
        // Red accent stripe at top
        for (int x = 0; x < TILE_PX; x++) {
            for (int y = 0; y < 3; y++) {
                img.setRGB(ox + x, oy + y, 0xFF000000 | shade(0xB02020, faceShade(x, y)));
            }
        }
        // Wood grain lines
        for (int x = 2; x < TILE_PX; x += 5) {
            for (int y = 0; y < TILE_PX; y++) {
                if (rnd.nextFloat() < 0.4f) {
                    img.setRGB(ox + x, oy + y, 0xFF000000 | shade(0x4A3020, faceShade(x, y)));
                }
            }
        }
    }

    /** Bed pillow/head end: white pillow with red trim. */
    private void paintBedPillow(BufferedImage img, int index, Random rnd) {
        int ox = tileX(index);
        int oy = tileY(index);
        // White pillow base with subtle texture
        paintNoiseTile(img, index, 0xF0F0F0, 0xD0D0D0, 0.3f, rnd);
        // Red trim border
        for (int i = 0; i < 2; i++) {
            for (int x = 0; x < TILE_PX; x++) {
                img.setRGB(ox + x, oy + i, 0xFF000000 | shade(0xC03030, faceShade(x, i)));
                img.setRGB(ox + x, oy + TILE_PX - 1 - i, 0xFF000000 | shade(0xC03030, faceShade(x, TILE_PX - 1 - i)));
            }
            for (int y = 0; y < TILE_PX; y++) {
                img.setRGB(ox + i, oy + y, 0xFF000000 | shade(0xC03030, faceShade(i, y)));
                img.setRGB(ox + TILE_PX - 1 - i, oy + y, 0xFF000000 | shade(0xC03030, faceShade(TILE_PX - 1 - i, y)));
            }
        }
        // Pillow puff effect (lighter center)
        for (int y = 3; y < TILE_PX - 3; y++) {
            for (int x = 3; x < TILE_PX - 3; x++) {
                int dx = x - 8, dy = y - 8;
                float dist = (float) Math.sqrt(dx * dx + dy * dy);
                if (dist < 6) {
                    img.setRGB(ox + x, oy + y, 0xFF000000 | shade(0xFAFAFA, faceShade(x, y)));
                }
            }
        }
    }

    /** A wooden barrel: vertical stave planks with a metal band across the middle and a bung. */
    private void paintBarrel(BufferedImage img, int index) {
        int ox = tileX(index);
        int oy = tileY(index);
        int stave = 0xA8763A;
        int grain = 0x8F5F2A;
        int band = 0x8A8A8A;
        int bung = 0x5A3D1D;
        for (int y = 0; y < TILE_PX; y++) {
            for (int x = 0; x < TILE_PX; x++) {
                img.setRGB(ox + x, oy + y, 0xFF000000 | (y % 3 == 0 ? grain : stave));
            }
        }
        // Metal band across the middle.
        for (int y = 7; y < 10; y++) {
            for (int x = 0; x < TILE_PX; x++) {
                img.setRGB(ox + x, oy + y, 0xFF000000 | band);
            }
        }
        // A dark bung (the stopper hole) just above the band, off-center.
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                int x = 11 + dx, y = 5 + dy;
                if (x < 0 || x >= TILE_PX || y < 0 || y >= TILE_PX) continue;
                img.setRGB(ox + x, oy + y, 0xFF000000 | bung);
            }
        }
    }

    /** A short brown stick with a glowing orange/yellow flame on top, on a transparent background - the torch light source. */
    private void paintTorch(BufferedImage img, int index) {        int ox = tileX(index);
        int oy = tileY(index);
        for (int y = 7; y < TILE_PX; y++) {
            img.setRGB(ox + 7, oy + y, 0xFF000000 | 0x6E4A2A);
            img.setRGB(ox + 8, oy + y, 0xFF000000 | 0x8B5A2B);
        }
        double cx = 7.5, cy = 4.5;
        for (int y = 1; y <= 8; y++) {
            for (int x = 4; x <= 11; x++) {
                double d = Math.hypot(x - cx, (y - cy) * 1.2);
                if (d <= 3.2) {
                    int color = d <= 1.4 ? 0xFFE066 : (d <= 2.4 ? 0xF2A93B : 0xD9601A);
                    img.setRGB(ox + x, oy + y, 0xFF000000 | color);
                }
            }
        }
    }

    /** A taller, wispier flame than the torch's, with translucent edges - the lightning-struck fire. */
    private void paintFire(BufferedImage img, int index, Random rnd) {
        int ox = tileX(index);
        int oy = tileY(index);
        double cx = 7.5, cy = 5.5;
        for (int y = 0; y < TILE_PX; y++) {
            for (int x = 2; x <= 13; x++) {
                double d = Math.hypot(x - cx, (y - cy) * 0.9);
                double wobble = 0.5 * Math.sin(x * 1.7 + y * 2.3 + rnd.nextInt(4));
                if (d + wobble <= 5.2) {
                    int color = d <= 1.6 ? 0xFFF2A0 : (d <= 3.0 ? 0xF2A93B : 0xC73B12);
                    int alpha = d <= 4.0 ? 0xFF000000 : 0x99000000;
                    img.setRGB(ox + x, oy + y, alpha | color);
                }
            }
        }
    }

    /** A "fast leaves" tile: same palette as the solid leaves tile but with small square holes carved out. */
    private void paintLeavesCutout(BufferedImage img, int index, Random rnd) {
        paintLeavesCutout(img, index, rnd, 0x3E8E35, 0x2F701F);
    }

    private void paintLeavesCutout(BufferedImage img, int index, Random rnd, int light, int dark) {
        int ox = tileX(index);
        int oy = tileY(index);
        for (int cy = 0; cy < TILE_PX; cy += 2) {
            for (int cx = 0; cx < TILE_PX; cx += 2) {
                boolean hole = rnd.nextFloat() < 0.32f;
                int base = rnd.nextFloat() < 0.55f ? light : dark;
                for (int y = 0; y < 2; y++) {
                    for (int x = 0; x < 2; x++) {
                        int px = ox + cx + x, py = oy + cy + y;
                        img.setRGB(px, py, hole ? 0x00000000 : (0xFF000000 | base));
                    }
                }
            }
        }
    }

    /** Wool: soft, fluffy texture in white. */
    private void paintWool(BufferedImage img, int index, Random rnd) {
        paintTile(img, index, rnd, 0xF5F5F5, 0xE0E0E0, true);
    }

    /** Atlas tile for destroy-stage {@code stage} (0..{@link #DESTROY_STAGE_COUNT}-1). */
    public static int destroyStageTile(int stage) {
        int s = Math.max(0, Math.min(DESTROY_STAGE_COUNT - 1, stage));
        return DESTROY_STAGE_TILE + s;
    }

    public void bind() {
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, textureId);
    }

    /** Returns {u0, v0, u1, v1} for the given tile index, inset slightly to avoid edge bleeding. */
    public float[] getUV(int tileIndex) {
        int tx = tileIndex % GRID;
        int ty = tileIndex / GRID;
        float inset = 0.02f / GRID;
        float u0 = (float) tx / GRID + inset;
        float v0 = (float) ty / GRID + inset;
        float u1 = (float) (tx + 1) / GRID - inset;
        float v1 = (float) (ty + 1) / GRID - inset;
        return new float[]{u0, v0, u1, v1};
    }

    public void destroy() {
        glDeleteTextures(textureId);
    }

    // =========================================================================
    // Phase 0.5 — Tinkers' Construct tile painters
    // =========================================================================

    /**
     * SEARED_BRICK (tile 234): fired, blackened brick with orange-red mortar
     * lines.  The dark soot colour (#1A1008) contrasts with hot mortar (#8B3A10).
     */
    private void paintSearedBrick(BufferedImage img, int index, Random rnd) {
        int ox = tileX(index), oy = tileY(index);
        int brickColor = 0x1E1208;
        int mortarColor = 0x7A3010;
        for (int y = 0; y < TILE_PX; y++) {
            for (int x = 0; x < TILE_PX; x++) {
                // Horizontal mortar every 5 rows, vertical mortar offset per row
                int rowOffset = ((y / 5) % 2) * 4;
                boolean mortarH = (y % 5 == 0);
                boolean mortarV = ((x + rowOffset) % 8 == 0);
                int noise = rnd.nextInt(10) - 5;
                int base = (mortarH || mortarV) ? mortarColor : brickColor;
                int r = clamp(((base >> 16) & 0xFF) + noise);
                int g = clamp(((base >> 8)  & 0xFF) + noise);
                int b = clamp(( base        & 0xFF) + noise);
                img.setRGB(ox + x, oy + y, 0xFF000000 | (r << 16) | (g << 8) | b);
            }
        }
    }

    /**
     * SEARED_GLASS (tile 235): dark smoky-brown semi-transparent glass with a
     * copper-tinted grid frame.  Resembles Tinkers' classic seared glass.
     */
    private void paintSearedGlass(BufferedImage img, int index) {
        int ox = tileX(index), oy = tileY(index);
        int fill = 0x3A2010; // dark smoky interior
        int frame = 0x6B3A1A;// copper-brown frame
        int alpha = 160;
        for (int y = 0; y < TILE_PX; y++) {
            for (int x = 0; x < TILE_PX; x++) {
                boolean onFrame = (x == 0 || x == TILE_PX-1 || y == 0 || y == TILE_PX-1
                                || x == TILE_PX/2 || y == TILE_PX/2);
                int color = onFrame ? frame : fill;
                img.setRGB(ox + x, oy + y, (alpha << 24) | color);
            }
        }
    }

    /**
     * SEARED_TANK (tile 236): looks like a seared brick block but with a
     * small translucent window in the centre revealing molten orange inside.
     */
    private void paintSearedTank(BufferedImage img, int index, Random rnd) {
        // Start with seared brick
        paintSearedBrick(img, index, rnd);
        int ox = tileX(index), oy = tileY(index);
        // Draw a 6x8 window in the centre, orange-glow fill
        int wx = ox + 5, wy = oy + 4;
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 6; x++) {
                boolean border = (x == 0 || x == 5 || y == 0 || y == 7);
                int color = border ? 0x6B3A1A : 0xC84000;
                img.setRGB(wx + x, wy + y, 0xFF000000 | color);
            }
        }
    }

    /**
     * SMELTERY_DRAIN (tile 237): dark seared brick face with a square drain
     * hole (a 4×4 dark void in the centre).
     */
    private void paintSmelteryDrain(BufferedImage img, int index, Random rnd) {
        paintSearedBrick(img, index, rnd);
        int ox = tileX(index), oy = tileY(index);
        // Drain hole (6x6 void square centred on face)
        for (int y = 5; y < 11; y++) {
            for (int x = 5; x < 11; x++) {
                img.setRGB(ox + x, oy + y, 0xFF050502); // near-black void
            }
        }
        // Highlight rim
        for (int i = 5; i < 11; i++) {
            img.setRGB(ox + i, oy + 5,  0xFF6B3A1A);
            img.setRGB(ox + i, oy + 10, 0xFF3A1A08);
            img.setRGB(ox + 5,  oy + i, 0xFF6B3A1A);
            img.setRGB(ox + 10, oy + i, 0xFF3A1A08);
        }
    }

    /**
     * SMELTERY_CONTROLLER face (tiles 238 = inactive, 239 = active/lit).
     * Inactive: a dark square mouth on a seared brick background.
     * Active: the mouth glows deep orange-white (molten metal visible inside).
     */
    private void paintSmelteryController(BufferedImage img, int index, Random rnd, boolean active) {
        paintSearedBrick(img, index, rnd);
        int ox = tileX(index), oy = tileY(index);
        if (active) {
            // Glowing orange-white opening
            for (int y = 3; y < 13; y++) {
                for (int x = 3; x < 13; x++) {
                    float dist = (float) Math.sqrt((x - 7.5) * (x - 7.5) + (y - 7.5) * (y - 7.5));
                    int glow = (int) Math.max(0, 255 - dist * 22);
                    int r = Math.min(255, glow + 160);
                    int g = Math.min(255, glow * 80 / 255);
                    int b = 0;
                    img.setRGB(ox + x, oy + y, 0xFF000000 | (r << 16) | (g << 8) | b);
                }
            }
        } else {
            // Dark void opening
            for (int y = 3; y < 13; y++) {
                for (int x = 3; x < 13; x++) {
                    img.setRGB(ox + x, oy + y, 0xFF0A0502);
                }
            }
            // Rim highlight
            for (int i = 3; i < 13; i++) {
                img.setRGB(ox + i, oy + 3, 0xFF4A2810);
                img.setRGB(ox + 3, oy + i, 0xFF4A2810);
                img.setRGB(ox + i, oy + 12, 0xFF1A0A04);
                img.setRGB(ox + 12, oy + i, 0xFF1A0A04);
            }
        }
    }

    /**
     * STEAM_BOILER front (tiles 244 cold / 245 steaming): a bronze metal box
     * with riveted edges and a round pressure gauge; the gauge glows green
     * with steam pressure when the boiler is running.
     */
    private void paintSteamBoiler(BufferedImage img, int index, Random rnd, boolean hot) {
        paintBronzeMachineBox(img, index, rnd);
        int ox = tileX(index), oy = tileY(index);
        // Round pressure gauge in the centre.
        for (int y = 4; y < 12; y++) {
            for (int x = 4; x < 12; x++) {
                float dist = (float) Math.sqrt((x - 7.5) * (x - 7.5) + (y - 7.5) * (y - 7.5));
                if (dist > 3.8f) continue;
                int color = hot
                        ? (0xFF000000 | lerpColor(0x2FA84F, 0xA8F0B0, Math.max(0f, 1f - dist / 4f)))
                        : 0xFF1E2820;
                img.setRGB(ox + x, oy + y, color);
            }
        }
        // Needle.
        if (hot) {
            for (int i = 0; i <= 3; i++) {
                img.setRGB(ox + 7 + i, oy + 8 - i, 0xFF102010);
            }
        }
        // Steam wisps at the top when running.
        if (hot) {
            for (int i = 0; i < 6; i++) {
                int wx = ox + 3 + rnd.nextInt(10);
                int wy = oy + 1 + rnd.nextInt(3);
                img.setRGB(wx, wy, 0xFFD8E8E8);
            }
        }
    }

    /**
     * STEAM_PIPE (tile 260 bronze / 261 wood / 262 iron): a pipe segment with
     * coupling rings at both ends and a dark bore running horizontally.
     */

    /**
     * STEAM_PIPE (tile 260 bronze / 261 wood / 262 iron): a pipe segment with
     * coupling rings at both ends and a dark bore running horizontally.
     */
    private void paintSteamPipe(BufferedImage img, int index, Random rnd,
                                int bodyCol, int edgeCol, int plateCol) {
        paintMachineBox(img, index, rnd, bodyCol, edgeCol & 0xFFFFFF, plateCol);
        int ox = tileX(index), oy = tileY(index);
        for (int y = 5; y < 11; y++) {
            for (int x = 0; x < TILE_PX; x++) {
                boolean edge = y == 5 || y == 10;
                boolean bore = y >= 7 && y <= 8;
                int color = bore ? 0xFF1A1210 : (edge ? edgeCol : bodyCol);
                img.setRGB(ox + x, oy + y, 0xFF000000 | color);
            }
        }
        // Coupling rings at both ends.
        for (int x = 0; x <= 2; x++) {
            for (int y = 5; y < 11; y++) img.setRGB(ox + x, oy + y, 0xFF000000 | edgeCol);
        }
        for (int x = 13; x < TILE_PX; x++) {
            for (int y = 5; y < 11; y++) img.setRGB(ox + x, oy + y, 0xFF000000 | edgeCol);
        }
    }

    /**
     * STEAM_MACERATOR (tiles 264 idle / 265 working): a bronze machine box
     * with a dark grinding chamber; the chamber glows while crushing.
     */
    private void paintSteamMacerator(BufferedImage img, int index, Random rnd, boolean working) {
        paintBronzeMachineBox(img, index, rnd);
        int ox = tileX(index), oy = tileY(index);
        for (int y = 4; y < 12; y++) {
            for (int x = 3; x < 13; x++) {
                boolean edge = y == 4 || y == 11 || x == 3 || x == 12;
                int color;
                if (edge) { color = 0xFF3A2A18; }
                else if (working) {
                    // Glowing hot chamber with grinding sparks.
                    int sparkle = rnd.nextInt(8);
                    color = sparkle < 2 ? 0xFFF0C040 : 0xFF80300A;
                } else {
                    color = 0xFF1E1006;
                }
                img.setRGB(ox + x, oy + y, 0xFF000000 | color);
            }
        }
    }

    /**
     * STEAM_FURNACE (tiles 246 off / 247 active): a bronze machine box with a
     * dark furnace mouth; the mouth glows while the machine is working.
     */
    private void paintSteamFurnace(BufferedImage img, int index, Random rnd, boolean active) {
        paintBronzeMachineBox(img, index, rnd);
        int ox = tileX(index), oy = tileY(index);
        for (int y = 5; y < 12; y++) {
            for (int x = 4; x < 12; x++) {
                boolean edge = y == 5 || y == 11 || x == 4 || x == 11;
                int color = edge ? 0xFF3A2A18 : (active ? 0xFFC84810 : 0xFF140A04);
                img.setRGB(ox + x, oy + y, 0xFF000000 | color);
            }
        }
    }

    // ------------------------------------------------------------------
    // Electric Age tile painters (tiles 266-274)
    // ------------------------------------------------------------------

    /**
     * COAL_GENERATOR body / front (tiles 266-268): a dark iron machine box
     * with ember glow when burning.
     */
    private void paintCoalGenerator(BufferedImage img, int index, Random rnd,
                                    boolean front, boolean burning) {
        // Iron-grey hull.
        paintMachineBox(img, index, rnd, 0x6E7078, 0x404248, 0x9EA0A8);
        if (!front) return;
        if (burning) {
            // Burning front: small ember window in the lower-centre.
            int ox = tileX(index), oy = tileY(index);
            for (int y = 9; y < 14; y++) {
                for (int x = 4; x < 12; x++) {
                    boolean edge = y == 9 || y == 13 || x == 4 || x == 11;
                    int color = edge ? 0xFF2A2020 : (rnd.nextInt(4) == 0 ? 0xFFE08020 : 0xFFC05010);
                    img.setRGB(ox + x, oy + y, 0xFF000000 | color);
                }
            }
        } else {
            // Cold front: dark ash grate.
            int ox = tileX(index), oy = tileY(index);
            for (int y = 9; y < 14; y++) {
                for (int x = 4; x < 12; x++) {
                    boolean edge = y == 9 || y == 13 || x == 4 || x == 11;
                    img.setRGB(ox + x, oy + y, edge ? 0xFF252525 : 0xFF101010);
                }
            }
        }
    }

    /**
     * COPPER_CABLE (tile 269): terracotta-orange wire cross-section, with a
     * thin darker sheathing ring around a copper core.
     */
    private void paintCopperCable(BufferedImage img, int index, Random rnd) {
        int ox = tileX(index), oy = tileY(index);
        int sheath = 0xFF5C3010;
        int core   = 0xFFB87333;
        int bright = 0xFFDCA05A;
        for (int y = 0; y < TILE_PX; y++) {
            for (int x = 0; x < TILE_PX; x++) {
                int noise = rnd.nextInt(6) - 3;
                int dx = x - 8, dy = y - 8;
                int dist2 = dx * dx + dy * dy;
                int base;
                if (dist2 > 42) base = sheath;
                else if (dist2 > 20) base = core;
                else base = bright;
                int r = clamp(((base >> 16) & 0xFF) + noise);
                int g = clamp(((base >> 8) & 0xFF) + noise);
                int b = clamp((base & 0xFF) + noise);
                img.setRGB(ox + x, oy + y, 0xFF000000 | (r << 16) | (g << 8) | b);
            }
        }
    }

    /**
     * GOLD_CABLE (tile 270): golden yellow wire — same cross-section geometry
     * as copper cable but in a gold palette.
     */
    private void paintGoldCable(BufferedImage img, int index, Random rnd) {
        int ox = tileX(index), oy = tileY(index);
        int sheath = 0xFF604800;
        int core   = 0xFFD4A800;
        int bright = 0xFFFFD850;
        for (int y = 0; y < TILE_PX; y++) {
            for (int x = 0; x < TILE_PX; x++) {
                int noise = rnd.nextInt(6) - 3;
                int dx = x - 8, dy = y - 8;
                int dist2 = dx * dx + dy * dy;
                int base;
                if (dist2 > 42) base = sheath;
                else if (dist2 > 20) base = core;
                else base = bright;
                int r = clamp(((base >> 16) & 0xFF) + noise);
                int g = clamp(((base >> 8) & 0xFF) + noise);
                int b = clamp((base & 0xFF) + noise);
                img.setRGB(ox + x, oy + y, 0xFF000000 | (r << 16) | (g << 8) | b);
            }
        }
    }

    /**
     * ELECTRIC_FURNACE body / front (tiles 271-273): an iron hull with a
     * blue-tinged coil window; coils glow amber-white when active.
     */
    private void paintElectricFurnace(BufferedImage img, int index, Random rnd,
                                      boolean front, boolean active) {
        paintMachineBox(img, index, rnd, 0x6E7078, 0x404248, 0x9EA0A8);
        if (!front) return;
        int ox = tileX(index), oy = tileY(index);
        // Central coil window.
        for (int y = 4; y < 12; y++) {
            for (int x = 4; x < 12; x++) {
                boolean edge = y == 4 || y == 11 || x == 4 || x == 11;
                int color;
                if (edge) {
                    color = 0xFF303040;
                } else if (active) {
                    // Glowing electric blue-to-white heating element.
                    color = (rnd.nextInt(3) == 0) ? 0xFFFFFFB0 : 0xFF6080FF;
                } else {
                    color = 0xFF202030;
                }
                img.setRGB(ox + x, oy + y, 0xFF000000 | color);
            }
        }
    }

    /**
     * BATTERY_BLOCK (tile 274): dark charcoal-grey block with blue charge
     * indicator stripes — visually distinct from cables and generators.
     */
    private void paintBatteryBlock(BufferedImage img, int index, Random rnd) {
        int ox = tileX(index), oy = tileY(index);
        int body  = 0xFF2A2A30;
        int stripe = 0xFF2040A0;
        int rivet  = 0xFF5060B0;
        for (int y = 0; y < TILE_PX; y++) {
            for (int x = 0; x < TILE_PX; x++) {
                int noise = rnd.nextInt(6) - 3;
                boolean isStripe = (x >= 2 && x <= 4) || (x >= 11 && x <= 13);
                int base = isStripe ? stripe : body;
                int r = clamp(((base >> 16) & 0xFF) + noise);
                int g = clamp(((base >> 8) & 0xFF) + noise);
                int b = clamp((base & 0xFF) + noise);
                img.setRGB(ox + x, oy + y, 0xFF000000 | (r << 16) | (g << 8) | b);
            }
        }
        // Corner terminal rivets.
        for (int ry = 1; ry <= 14; ry += 13) {
            for (int rx = 1; rx <= 14; rx += 13) {
                img.setRGB(ox + rx, oy + ry, 0xFF000000 | rivet);
            }
        }
    }

    /** Shared machine hull painter: plated metal with corner rivets, tinted per material. */
    private void paintMachineBox(BufferedImage img, int index, Random rnd,
                                 int bronze, int dark, int light) {
        int ox = tileX(index), oy = tileY(index);
        for (int y = 0; y < TILE_PX; y++) {
            for (int x = 0; x < TILE_PX; x++) {
                int noise = rnd.nextInt(10) - 5;
                // Horizontal plating bands.
                boolean bandEdge = y % 5 == 0;
                int base = bandEdge ? dark : bronze;
                if ((x + y) % 7 == 0) base = light;
                int r = clamp(((base >> 16) & 0xFF) + noise);
                int g = clamp(((base >> 8) & 0xFF) + noise);
                int b = clamp((base & 0xFF) + noise);
                img.setRGB(ox + x, oy + y, 0xFF000000 | (r << 16) | (g << 8) | b);
            }
        }
        // Corner rivets.
        for (int ry = 1; ry <= 14; ry += 13) {
            for (int rx = 1; rx <= 14; rx += 13) {
                img.setRGB(ox + rx, oy + ry, 0xFFE8C890);
            }
        }
    }

    /** Shared bronze hull for Steam Age machines: plated bronze with corner rivets. */
    private void paintBronzeMachineBox(BufferedImage img, int index, Random rnd) {
        paintMachineBox(img, index, rnd, 0xB87333, 0x6E4420, 0xDCA05A);
    }

    /**
     * CASTING_TABLE top (tile 240): dark wood surface with a recessed casting
     * indent in the centre (where the cast sits).
     */
    private void paintCastingTable(BufferedImage img, int index, Random rnd) {
        int ox = tileX(index), oy = tileY(index);
        int wood = 0x3A2410;
        int recess = 0x1A0E06;
        for (int y = 0; y < TILE_PX; y++) {
            for (int x = 0; x < TILE_PX; x++) {
                int noise = rnd.nextInt(8) - 4;
                boolean inRecess = (x >= 4 && x <= 11 && y >= 4 && y <= 11);
                int base = inRecess ? recess : wood;
                int r = clamp(((base >> 16) & 0xFF) + noise);
                int g = clamp(((base >> 8)  & 0xFF) + noise);
                int b = clamp(( base        & 0xFF) + noise);
                img.setRGB(ox + x, oy + y, 0xFF000000 | (r << 16) | (g << 8) | b);
            }
        }
        // Rim highlight on recess
        for (int i = 4; i <= 11; i++) {
            img.setRGB(ox + i, oy + 4, 0xFF5A3820);
            img.setRGB(ox + 4, oy + i, 0xFF5A3820);
        }
    }

    /**
     * CASTING_BASIN top (tile 241): seared brick frame with a deep square
     * basin depression (orange-glow gradient inside).
     */
    private void paintCastingBasin(BufferedImage img, int index, Random rnd) {
        paintSearedBrick(img, index, rnd);
        int ox = tileX(index), oy = tileY(index);
        // Basin depression
        for (int y = 2; y < 14; y++) {
            for (int x = 2; x < 14; x++) {
                int r = clamp(180 + rnd.nextInt(16) - 8);
                int g = clamp(60 + rnd.nextInt(10));
                int b = 0;
                img.setRGB(ox + x, oy + y, 0xFF000000 | (r << 16) | (g << 8) | b);
            }
        }
        // Dark rim
        for (int i = 2; i < 14; i++) {
            img.setRGB(ox + i, oy + 2,  0xFF6B3A1A);
            img.setRGB(ox + i, oy + 13, 0xFF3A1A08);
            img.setRGB(ox + 2, oy + i,  0xFF6B3A1A);
            img.setRGB(ox + 13, oy + i, 0xFF3A1A08);
        }
    }

    /**
     * PART_BUILDER top (tile 242): looks like a small crafting table with a
     * pattern grid overlay in one half and raw material in the other.
     */
    private void paintPartBuilder(BufferedImage img, int index, Random rnd) {
        int ox = tileX(index), oy = tileY(index);
        int wood = 0x5A3820;
        // Wood base
        for (int y = 0; y < TILE_PX; y++) {
            for (int x = 0; x < TILE_PX; x++) {
                int noise = rnd.nextInt(10) - 5;
                int r = clamp(((wood >> 16) & 0xFF) + noise);
                int g = clamp(((wood >> 8)  & 0xFF) + noise);
                int b = clamp(( wood        & 0xFF) + noise);
                img.setRGB(ox + x, oy + y, 0xFF000000 | (r << 16) | (g << 8) | b);
            }
        }
        // Pattern grid (left half)
        for (int y = 2; y < 14; y += 4) {
            for (int x = 1; x < 7; x++) {
                img.setRGB(ox + x, oy + y, 0xFF2A1808);
            }
        }
        for (int x = 1; x < 7; x += 3) {
            for (int y = 2; y < 14; y++) {
                img.setRGB(ox + x, oy + y, 0xFF2A1808);
            }
        }
        // Material block (right half) — grey stone
        for (int y = 4; y < 12; y++) {
            for (int x = 9; x < 15; x++) {
                int g2 = 0x9E9E9E;
                int noise = rnd.nextInt(12) - 6;
                int r = clamp(((g2 >> 16) & 0xFF) + noise);
                int gv = clamp(((g2 >> 8)  & 0xFF) + noise);
                int bv = clamp(( g2        & 0xFF) + noise);
                img.setRGB(ox + x, oy + y, 0xFF000000 | (r << 16) | (gv << 8) | bv);
            }
        }
    }

    /**
     * TOOL_STATION top (tile 243): assembly-bench look — dark iron plate with
     * a groove for aligning tool parts and a hammer detail.
     */
    private void paintToolStation(BufferedImage img, int index, Random rnd) {
        int ox = tileX(index), oy = tileY(index);
        int iron = 0x9090A8;
        // Iron plate base
        for (int y = 0; y < TILE_PX; y++) {
            for (int x = 0; x < TILE_PX; x++) {
                int noise = rnd.nextInt(12) - 6;
                int r = clamp(((iron >> 16) & 0xFF) + noise);
                int g = clamp(((iron >> 8)  & 0xFF) + noise);
                int b = clamp(( iron        & 0xFF) + noise);
                img.setRGB(ox + x, oy + y, 0xFF000000 | (r << 16) | (g << 8) | b);
            }
        }
        // Alignment groove (horizontal channel)
        for (int x = 1; x < 15; x++) {
            img.setRGB(ox + x, oy + 7,  0xFF505060);
            img.setRGB(ox + x, oy + 8,  0xFF404050);
        }
        // Small hammer icon (top-left, 4x5 shape)
        int hColor = 0x303040;
        img.setRGB(ox + 2, oy + 2, 0xFF000000 | hColor);
        img.setRGB(ox + 3, oy + 2, 0xFF000000 | hColor);
        img.setRGB(ox + 4, oy + 2, 0xFF000000 | hColor);
        img.setRGB(ox + 3, oy + 3, 0xFF000000 | hColor);
        img.setRGB(ox + 3, oy + 4, 0xFF000000 | hColor);
        img.setRGB(ox + 3, oy + 5, 0xFF000000 | hColor);
    }

    /**
     * ANVIL face (tile 254): heavy cast-iron block — very dark iron-grey base
     * with a slight lighter "flat-top" band in the upper half (the anvil face),
     * a narrow dark waist indent in the middle, and a solid base band below.
     * A small hammer-impact mark is painted near the centre of the face.
     */
    private void paintAnvil(BufferedImage img, int index, Random rnd) {
        int ox = tileX(index), oy = tileY(index);
        int baseColor = 0x606070; // darker iron than tool station
        // Fill base
        for (int y = 0; y < TILE_PX; y++) {
            for (int x = 0; x < TILE_PX; x++) {
                int noise = rnd.nextInt(10) - 5;
                int r = clamp(((baseColor >> 16) & 0xFF) + noise);
                int g = clamp(((baseColor >> 8)  & 0xFF) + noise);
                int b = clamp(( baseColor        & 0xFF) + noise);
                img.setRGB(ox + x, oy + y, 0xFF000000 | (r << 16) | (g << 8) | b);
            }
        }
        // Lighter flat-top face area (top half, inset 1 px)
        for (int y = 1; y < 7; y++) {
            for (int x = 1; x < 15; x++) {
                int v = 0x808090 + (rnd.nextInt(8) - 4);
                int r = clamp((v >> 16) & 0xFF);
                int g = clamp((v >> 8)  & 0xFF);
                int b = clamp( v        & 0xFF);
                img.setRGB(ox + x, oy + y, 0xFF000000 | (r << 16) | (g << 8) | b);
            }
        }
        // Waist indent (narrower, rows 7-8)
        for (int x = 0; x < TILE_PX; x++) {
            img.setRGB(ox + x, oy + 7, 0xFF383840);
            img.setRGB(ox + x, oy + 8, 0xFF303038);
        }
        // Inner waist slightly lighter (only central 8 px)
        for (int x = 4; x < 12; x++) {
            img.setRGB(ox + x, oy + 7, 0xFF505058);
            img.setRGB(ox + x, oy + 8, 0xFF484850);
        }
        // Base block area (rows 9-14, full width)
        for (int y = 9; y < 15; y++) {
            for (int x = 1; x < 15; x++) {
                int v = 0x707078 + (rnd.nextInt(8) - 4);
                int r = clamp((v >> 16) & 0xFF);
                int g = clamp((v >> 8)  & 0xFF);
                int b = clamp( v        & 0xFF);
                img.setRGB(ox + x, oy + y, 0xFF000000 | (r << 16) | (g << 8) | b);
            }
        }
        // Impact mark — a small cross/divot near the centre of the face area
        int mk = 0x404048;
        img.setRGB(ox + 7, oy + 3, 0xFF000000 | mk);
        img.setRGB(ox + 8, oy + 3, 0xFF000000 | mk);
        img.setRGB(ox + 7, oy + 4, 0xFF000000 | mk);
        img.setRGB(ox + 8, oy + 4, 0xFF000000 | mk);
        img.setRGB(ox + 6, oy + 4, 0xFF000000 | mk);
        img.setRGB(ox + 9, oy + 4, 0xFF000000 | mk);
    }

    /**
     * TNT (tile 275): iconic red block with white horizontal bands on top and
     * bottom and a central "TNT" label band — purely procedural, no image files.
     * The tile is used for all faces (the look is symmetric enough to work).
     */
    private void paintTnt(BufferedImage img, int index, Random rnd) {
        int ox = tileX(index), oy = tileY(index);
        // Background: vivid red body.
        int red = 0xC83020;
        // White top band (rows 0-2), red body (rows 3-12), white bottom band (rows 13-15).
        for (int y = 0; y < TILE_PX; y++) {
            boolean band = (y <= 2 || y >= 13);
            int base = band ? 0xE8E8E8 : red;
            for (int x = 0; x < TILE_PX; x++) {
                int noise = rnd.nextInt(12) - 6;
                int r = clamp(((base >> 16) & 0xFF) + (band ? 0 : noise));
                int g = clamp(((base >>  8) & 0xFF) + (band ? 0 : noise));
                int b = clamp(( base        & 0xFF) + (band ? 0 : noise));
                img.setRGB(ox + x, oy + y, 0xFF000000 | (r << 16) | (g << 8) | b);
            }
        }
        // Draw "TNT" in dark charcoal across the middle band (rows 6-10, centred).
        // Each letter is a 3×5 pixel stroke; we paint T-N-T spaced by 1 blank column.
        // T letter at x=1
        drawPixelLetter(img, ox + 1, oy + 5, 'T', 0xFF1A1A1A);
        // N letter at x=5
        drawPixelLetter(img, ox + 5, oy + 5, 'N', 0xFF1A1A1A);
        // T letter at x=10
        drawPixelLetter(img, ox + 10, oy + 5, 'T', 0xFF1A1A1A);
    }

    /**
     * Paints a single 3×5 pixel letter ({@code T} or {@code N}) into the image
     * with the given top-left corner and colour.  Only T and N are supported —
     * the full pixel-font lives in {@code FontAtlas}.  Used exclusively by
     * {@link #paintTnt}.
     */
    private static void drawPixelLetter(BufferedImage img, int ox, int oy, char c, int argb) {
        boolean[][] t = {
            {true,  true,  true},
            {false, true,  false},
            {false, true,  false},
            {false, true,  false},
            {false, true,  false},
        };
        boolean[][] n = {
            {true,  false, true},
            {true,  true,  true},
            {true,  true,  true},
            {true,  false, true},
            {true,  false, true},
        };
        boolean[][] glyph = (c == 'T') ? t : n;
        for (int row = 0; row < glyph.length; row++) {
            for (int col = 0; col < glyph[row].length; col++) {
                if (glyph[row][col]) {
                    img.setRGB(ox + col, oy + row, argb);
                }
            }
        }
    }

    /** Clamp a colour channel to [0, 255]. */
    private static int clamp(int v) { return Math.max(0, Math.min(255, v)); }
}
