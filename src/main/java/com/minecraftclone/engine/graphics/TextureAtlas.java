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

    public static final int GRID = 16;
    public static final int TILE_PX = 16;
    public static final int ATLAS_PX = GRID * TILE_PX;

    /** Tile index of the alpha-cutout leaves texture used when the "see-through leaves" setting is on. */
    public static final int LEAVES_CUTOUT_TILE = 24;
    /** Tile index of the full-cube lamp texture. */
    public static final int LAMP_TILE = 25;
    /** Tile index of the furnace's front/side face. */
    public static final int FURNACE_TILE = 26;
    /** Tile index of the crafting table's workbench face. */
    public static final int CRAFTING_TABLE_TILE = 48;
    /** Tile index of the furnace's front face when it's actively burning - the mouth glows orange. */
    public static final int FURNACE_LIT_TILE = 49;
    /** Tile index of the chest's lid/front face. */
    public static final int CHEST_TILE = 50;
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

        paintOreTile(image, 16, rnd, 0x242424, 0x0E0E0E);                // coal ore
        paintOreTile(image, 17, rnd, 0xD8A66A, 0xA87C44);                // iron ore
        paintOreTile(image, 18, rnd, 0xFFD93A, 0xE0A81E);                // gold ore
        paintOreTile(image, 19, rnd, 0x6FE8E8, 0x3FBFBF);                // diamond ore
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
        paintOreTile(image, 80, rnd, 0xB87333, 0x8B5A2B);                // copper ore (reddish-brown)
        paintOreTile(image, 81, rnd, 0xC0C0C0, 0xA0A0A0);                // tin ore (silver-gray)
        paintOreTile(image, 82, rnd, 0xE8E8E8, 0xD0D0D0);                // bauxite ore (white/aluminum)
        paintOreTile(image, 83, rnd, 0xA8B8C8, 0x8898A8);                // zinc ore (silver-blue)
        paintOreTile(image, 84, rnd, 0x4A4A4A, 0x2A2A2A);                // lead ore (dark gray)
        paintOreTile(image, 85, rnd, 0xE0E0E0, 0xC0C0C0);                // silver ore (bright silver)

        // --- GTNH Ores: mid-game (tiles 86-90) ---
        paintOreTile(image, 86, rnd, 0xC8D8C8, 0xA8B8A8);                // nickel ore (pale gray-green)
        paintOreTile(image, 87, rnd, 0x5A6AB8, 0x404890);                // cobalt ore (blue-ish)
        paintOreTile(image, 88, rnd, 0x3A3A3A, 0x1A1A1A);                // tungsten ore (dark gray)
        paintOreTile(image, 89, rnd, 0x5A5A5A, 0x3A3A3A);                // molybdenum ore (dark metallic)
        paintOreTile(image, 90, rnd, 0xF0F0F0, 0xD0D0D0);                // platinum ore (silver-white)

        // --- GTNH Ores: advanced (tiles 91-95) ---
        paintOreTile(image, 91, rnd, 0xD8D8D8, 0xB8B8B8);                // chromium ore (silvery)
        paintOreTile(image, 92, rnd, 0x4A4A5A, 0x2A2A3A);                // manganese ore (dark gray-purple)
        paintOreTile(image, 93, rnd, 0x5A6A7A, 0x3A4A5A);                // vanadium ore (dark blue-gray)
        paintOreTile(image, 94, rnd, 0xE0E0E0, 0xC0C0C0);                // beryllium ore (light silver)
        paintOreTile(image, 95, rnd, 0xD0D0D0, 0xB0B0B0);                // titanium ore (metallic silver)

        // --- GTNH Ores: late-game (tiles 96-99) ---
        paintOreTile(image, 96, rnd, 0x7AB850, 0x5A9830);                // uranium ore (greenish)
        paintOreTile(image, 97, rnd, 0x5A5A5A, 0x3A3A3A);                // thorium ore (dark gray)
        paintOreTile(image, 98, rnd, 0x4A6A3A, 0x2A4A1A);                // plutonium ore (dark greenish-black)
        paintOreTile(image, 99, rnd, 0xD8D8F0, 0xB8B8D0);                // iridium ore (bright silver-rainbow)

        // --- Small GTNH Ores: Striped indicator ores (tiles 100-119) ---
        // Early-game small ores (striped, mineable with stone tools)
        paintSmallOreTile(image, 100, rnd, 0xB87333, 0x8B5A2B);           // small copper ore (striped)
        paintSmallOreTile(image, 101, rnd, 0xC0C0C0, 0xA0A0A0);           // small tin ore (striped)
        paintSmallOreTile(image, 102, rnd, 0xE8E8E8, 0xD0D0D0);           // small bauxite ore (striped)
        paintSmallOreTile(image, 103, rnd, 0xA8B8C8, 0x8898A8);           // small zinc ore (striped)
        paintSmallOreTile(image, 104, rnd, 0x4A4A4A, 0x2A2A2A);           // small lead ore (striped)
        paintSmallOreTile(image, 105, rnd, 0xE0E0E0, 0xC0C0C0);           // small silver ore (striped)

        // Mid-game small ores (striped, mineable with iron tools)
        paintSmallOreTile(image, 106, rnd, 0xC8D8C8, 0xA8B8A8);           // small nickel ore (striped)
        paintSmallOreTile(image, 107, rnd, 0x5A6AB8, 0x404890);           // small cobalt ore (striped)
        paintSmallOreTile(image, 108, rnd, 0x3A3A3A, 0x1A1A1A);           // small tungsten ore (striped)
        paintSmallOreTile(image, 109, rnd, 0x5A5A5A, 0x3A3A3A);           // small molybdenum ore (striped)
        paintSmallOreTile(image, 110, rnd, 0xF0F0F0, 0xD0D0D0);           // small platinum ore (striped)

        // Advanced small ores (striped, mineable with diamond tools)
        paintSmallOreTile(image, 111, rnd, 0xD8D8D8, 0xB8B8B8);           // small chromium ore (striped)
        paintSmallOreTile(image, 112, rnd, 0x4A4A5A, 0x2A2A3A);           // small manganese ore (striped)
        paintSmallOreTile(image, 113, rnd, 0x5A6A7A, 0x3A4A5A);           // small vanadium ore (striped)
        paintSmallOreTile(image, 114, rnd, 0xE0E0E0, 0xC0C0C0);           // small beryllium ore (striped)
        paintSmallOreTile(image, 115, rnd, 0xD0D0D0, 0xB0B0B0);           // small titanium ore (striped)

        // Late-game small ores (striped, mineable with diamond tools)
        paintSmallOreTile(image, 116, rnd, 0x7AB850, 0x5A9830);           // small uranium ore (striped)
        paintSmallOreTile(image, 117, rnd, 0x5A5A5A, 0x3A3A3A);           // small thorium ore (striped)
        paintSmallOreTile(image, 118, rnd, 0x4A6A3A, 0x2A4A1A);           // small plutonium ore (striped)
        paintSmallOreTile(image, 119, rnd, 0xD8D8F0, 0xB8B8D0);           // small iridium ore (striped)

        // --- New GTNH ore blocks (tiles 120-179) ---
        paintOreTile(image, 120, rnd, 0x4A4A5A, 0x2A2A3A); // magnetite
        paintOreTile(image, 121, rnd, 0x8B3A3A, 0x6B1A1A); // hematite
        paintOreTile(image, 122, rnd, 0xA0784A, 0x7A5828); // brown limonite
        paintOreTile(image, 123, rnd, 0xD4B44A, 0xA88A28); // yellow limonite
        paintOreTile(image, 124, rnd, 0x8A6A5A, 0x6A4A3A); // banded iron
        paintOreTile(image, 125, rnd, 0x6A6A8A, 0x4A4A6A); // vanadium magnetite
        paintOreTile(image, 126, rnd, 0xC89A40, 0x9A7020); // chalcopyrite
        paintOreTile(image, 127, rnd, 0x7A8A7A, 0x5A6A5A); // tetrahedrite
        paintOreTile(image, 128, rnd, 0x2A8A4A, 0x0A6A2A); // malachite
        paintOreTile(image, 129, rnd, 0x8A8AA0, 0x6A6A80); // galena
        paintOreTile(image, 130, rnd, 0xB4A080, 0x947A60); // sphalerite
        paintOreTile(image, 131, rnd, 0x7AAA7A, 0x5A8A5A); // garnierite
        paintOreTile(image, 132, rnd, 0x9A8A5A, 0x7A6A3A); // pentlandite
        paintOreTile(image, 133, rnd, 0x4A5A9A, 0x2A3A7A); // cobaltite
        paintOreTile(image, 134, rnd, 0xC8B840, 0xA89820); // pyrite
        paintOreTile(image, 135, rnd, 0x8A9A7A, 0x6A7A5A); // arsenopyrite
        paintOreTile(image, 136, rnd, 0xE8E050, 0xC8C030); // sulfur
        paintOreTile(image, 137, rnd, 0xD43A2A, 0xB41A0A); // cinnabar
        paintOreTile(image, 138, rnd, 0xD0C8B8, 0xA8A090); // cassiterite
        paintOreTile(image, 139, rnd, 0xE8D880, 0xC8B860); // scheelite
        paintOreTile(image, 140, rnd, 0x6A5A7A, 0x4A3A5A); // wolframite
        paintOreTile(image, 141, rnd, 0x7A7A8A, 0x5A5A6A); // molybdenite
        paintOreTile(image, 142, rnd, 0x5A4A5A, 0x3A2A3A); // ferberite
        paintOreTile(image, 143, rnd, 0x3A4A3A, 0x1A2A1A); // chromite
        paintOreTile(image, 144, rnd, 0x6A5A6A, 0x4A3A4A); // ilmenite
        paintOreTile(image, 145, rnd, 0xC87A5A, 0xA85A3A); // rutile
        paintOreTile(image, 146, rnd, 0x4A7A3A, 0x2A5A1A); // uraninite
        paintOreTile(image, 147, rnd, 0x5A6A3A, 0x3A4A1A); // pitchblende
        paintOreTile(image, 148, rnd, 0xC8A870, 0xA88850); // monazite
        paintOreTile(image, 149, rnd, 0xD49A60, 0xB47A40); // bastnasite
        paintOreTile(image, 150, rnd, 0xD45A3A, 0xB43A1A); // vanadinite
        paintOreTile(image, 151, rnd, 0x6A6A7A, 0x4A4A5A); // pyrolusite
        paintOreTile(image, 152, rnd, 0x3A3A4A, 0x1A1A2A); // graphite
        paintOreTile(image, 153, rnd, 0xD0D8E8, 0xB0B8C8); // lithium
        paintOreTile(image, 154, rnd, 0x2A3A2A, 0x0A1A0A); // naquadah
        paintOreTile(image, 155, rnd, 0x1A4A1A, 0x002A00); // naquadah enriched
        paintOreTile(image, 156, rnd, 0xC0D0D0, 0xA0B0B0); // trinium
        paintOreTile(image, 157, rnd, 0x9A8AC8, 0x7A6AA8); // neodymium
        paintOreTile(image, 158, rnd, 0xE8D0A0, 0xC8B080); // cerium
        paintOreTile(image, 159, rnd, 0x4A5A6A, 0x2A3A4A); // osmium
        paintOreTile(image, 160, rnd, 0xD0C0A8, 0xB0A088); // palladium
        paintOreTile(image, 161, rnd, 0xF0EEE0, 0xD0CEC0); // calcite
        paintOreTile(image, 162, rnd, 0x7A9A4A, 0x5A7A2A); // olivine
        paintOreTile(image, 163, rnd, 0xC8D0B8, 0xA8B098); // talc
        paintOreTile(image, 164, rnd, 0xC8B8A0, 0xA89880); // bentonite
        paintOreTile(image, 165, rnd, 0x3A5AA0, 0x1A3A80); // sodalite
        paintOreTile(image, 166, rnd, 0x2A4A9A, 0x0A2A7A); // lazurite
        paintOreTile(image, 167, rnd, 0xF0F0F0, 0xD0D0D0); // salt
        paintOreTile(image, 168, rnd, 0xE0D8D0, 0xC0B8B0); // rock salt
        paintOreTile(image, 169, rnd, 0xE8E0D0, 0xC8C0B0); // saltpeter
        paintOreTile(image, 170, rnd, 0xF0E8E0, 0xD0C8C0); // borax
        paintOreTile(image, 171, rnd, 0x8AB8D8, 0x6A98B8); // apatite
        paintOreTile(image, 172, rnd, 0xC8C080, 0xA8A060); // phosphate
        paintOreTile(image, 173, rnd, 0x7A6A5A, 0x5A4A3A); // pyrochlore
        paintOreTile(image, 174, rnd, 0xD8B8D8, 0xB898B8); // lepidolite
        paintOreTile(image, 175, rnd, 0xD43060, 0xB41040); // ruby
        paintOreTile(image, 176, rnd, 0x2050D0, 0x0030B0); // sapphire
        paintOreTile(image, 177, rnd, 0x30A060, 0x108040); // green sapphire
        paintOreTile(image, 178, rnd, 0xA02040, 0x800020); // pyrope
        paintOreTile(image, 179, rnd, 0xE07030, 0xC05010); // spessartine

        // --- New GTNH small ores (tiles 180-219) ---
        paintSmallOreTile(image, 180, rnd, 0x4A4A5A, 0x2A2A3A); // small magnetite
        paintSmallOreTile(image, 181, rnd, 0x8B3A3A, 0x6B1A1A); // small hematite
        paintSmallOreTile(image, 182, rnd, 0xA0784A, 0x7A5828); // small brown limonite
        paintSmallOreTile(image, 183, rnd, 0xD4B44A, 0xA88A28); // small yellow limonite
        paintSmallOreTile(image, 184, rnd, 0x8A6A5A, 0x6A4A3A); // small banded iron
        paintSmallOreTile(image, 185, rnd, 0x6A6A8A, 0x4A4A6A); // small vanadium magnetite
        paintSmallOreTile(image, 186, rnd, 0xC89A40, 0x9A7020); // small chalcopyrite
        paintSmallOreTile(image, 187, rnd, 0x7A8A7A, 0x5A6A5A); // small tetrahedrite
        paintSmallOreTile(image, 188, rnd, 0x2A8A4A, 0x0A6A2A); // small malachite
        paintSmallOreTile(image, 189, rnd, 0x8A8AA0, 0x6A6A80); // small galena
        paintSmallOreTile(image, 190, rnd, 0xB4A080, 0x947A60); // small sphalerite
        paintSmallOreTile(image, 191, rnd, 0x7AAA7A, 0x5A8A5A); // small garnierite
        paintSmallOreTile(image, 192, rnd, 0x9A8A5A, 0x7A6A3A); // small pentlandite
        paintSmallOreTile(image, 193, rnd, 0x4A5A9A, 0x2A3A7A); // small cobaltite
        paintSmallOreTile(image, 194, rnd, 0xC8B840, 0xA89820); // small pyrite
        paintSmallOreTile(image, 195, rnd, 0x8A9A7A, 0x6A7A5A); // small arsenopyrite
        paintSmallOreTile(image, 196, rnd, 0xE8E050, 0xC8C030); // small sulfur
        paintSmallOreTile(image, 197, rnd, 0xD43A2A, 0xB41A0A); // small cinnabar
        paintSmallOreTile(image, 198, rnd, 0xD0C8B8, 0xA8A090); // small cassiterite
        paintSmallOreTile(image, 199, rnd, 0xE8D880, 0xC8B860); // small scheelite
        paintSmallOreTile(image, 200, rnd, 0x6A5A7A, 0x4A3A5A); // small wolframite
        paintSmallOreTile(image, 201, rnd, 0x7A7A8A, 0x5A5A6A); // small molybdenite
        paintSmallOreTile(image, 202, rnd, 0x3A4A3A, 0x1A2A1A); // small chromite
        paintSmallOreTile(image, 203, rnd, 0x6A5A6A, 0x4A3A4A); // small ilmenite
        paintSmallOreTile(image, 204, rnd, 0xC87A5A, 0xA85A3A); // small rutile
        paintSmallOreTile(image, 205, rnd, 0x4A7A3A, 0x2A5A1A); // small uraninite
        paintSmallOreTile(image, 206, rnd, 0x5A6A3A, 0x3A4A1A); // small pitchblende
        paintSmallOreTile(image, 207, rnd, 0xC8A870, 0xA88850); // small monazite
        paintSmallOreTile(image, 208, rnd, 0xD49A60, 0xB47A40); // small bastnasite
        paintSmallOreTile(image, 209, rnd, 0xD45A3A, 0xB43A1A); // small vanadinite
        paintSmallOreTile(image, 210, rnd, 0x6A6A7A, 0x4A4A5A); // small pyrolusite
        paintSmallOreTile(image, 211, rnd, 0x3A3A4A, 0x1A1A2A); // small graphite
        paintSmallOreTile(image, 212, rnd, 0xD0D8E8, 0xB0B8C8); // small lithium
        paintSmallOreTile(image, 213, rnd, 0x2A3A2A, 0x0A1A0A); // small naquadah
        paintSmallOreTile(image, 214, rnd, 0xC0D0D0, 0xA0B0B0); // small trinium
        paintSmallOreTile(image, 215, rnd, 0x9A8AC8, 0x7A6AA8); // small neodymium
        paintSmallOreTile(image, 216, rnd, 0xE8D0A0, 0xC8B080); // small cerium
        paintSmallOreTile(image, 217, rnd, 0x4A5A6A, 0x2A3A4A); // small osmium
        paintSmallOreTile(image, 218, rnd, 0xD0C0A8, 0xB0A088); // small palladium
        paintSmallOreTile(image, 219, rnd, 0xD43060, 0xB41040); // small ruby

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
        paintLamp(image, LAMP_TILE);
        paintFurnace(image, FURNACE_TILE);
        paintFurnaceLit(image, FURNACE_LIT_TILE);
        paintCraftingTable(image, CRAFTING_TABLE_TILE);
        paintChest(image, CHEST_TILE);
        paintBarrel(image, BARREL_TILE);
        paintBerryBush(image, 37, rnd);
        paintTorch(image, 38);
        paintFire(image, FIRE_TILE, rnd);

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

    /** Stone base with a few clustered "ore" blobs (highlighted top edge, darker rim). */
    private void paintOreTile(BufferedImage img, int index, Random rnd, int oreColor, int oreDark) {
        paintStone(img, index, rnd);
        int ox = tileX(index);
        int oy = tileY(index);
        int blobs = 3 + rnd.nextInt(3);
        for (int b = 0; b < blobs; b++) {
            int cx = 2 + rnd.nextInt(TILE_PX - 4);
            int cy = 2 + rnd.nextInt(TILE_PX - 4);
            int size = 1 + rnd.nextInt(2);
            for (int dy = -size - 1; dy <= size + 1; dy++) {
                for (int dx = -size - 1; dx <= size + 1; dx++) {
                    int px = cx + dx, py = cy + dy;
                    if (px < 0 || px >= TILE_PX || py < 0 || py >= TILE_PX) continue;
                    float d = dx * dx + dy * dy;
                    if (d <= size * size && rnd.nextFloat() < 0.85f) {
                        // Rim darker, centre brighter, top lit.
                        float t = 1f - Math.min(1f, d / (size * size + 1f));
                        int color = lerpColor(oreDark, oreColor, t);
                        if (dy < 0) color = shade(color, 1.18f);
                        img.setRGB(ox + px, oy + py, 0xFF000000 | color);
                    } else if (d <= (size + 1) * (size + 1) && rnd.nextFloat() < 0.5f) {
                        img.setRGB(ox + px, oy + py, 0xFF000000 | oreDark);
                    }
                }
            }
        }
    }

    /** Stone base with horizontal stripes of ore color (visual indicator for small ores). */
    private void paintSmallOreTile(BufferedImage img, int index, Random rnd, int oreColor, int oreDark) {
        paintStone(img, index, rnd);
        int ox = tileX(index);
        int oy = tileY(index);
        // Paint alternating horizontal stripes for distinctive small ore appearance.
        int stripeHeight = 2;
        for (int y = 0; y < TILE_PX; y++) {
            int stripePhase = (y / stripeHeight) & 1;
            int color = stripePhase == 0 ? oreColor : oreDark;
            for (int x = 0; x < TILE_PX; x++) {
                img.setRGB(ox + x, oy + y, 0xFF000000 | color);
            }
        }
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
     * The sides and bottom reuse the dirt tile (index 2) directly from the atlas;
     * only the top face uses this special tile.
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
        int minY  = maxY - Math.max(2, Math.round(TILE_PX * fillFrac));

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

    /** A wooden chest front: plank fill with a curved lid seam, a brass lock plate and a latch. */
    private void paintChest(BufferedImage img, int index) {
        int ox = tileX(index);
        int oy = tileY(index);
        int plank = 0xB8863F;
        int grain = 0x9A6F2F;
        int seam = 0x7A5A2E;
        int metal = 0xC9B458;
        int lock = 0x8A6E2E;
        for (int y = 0; y < TILE_PX; y++) {
            for (int x = 0; x < TILE_PX; x++) {
                img.setRGB(ox + x, oy + y, 0xFF000000 | (x % 2 == 0 && y % 4 == 1 ? grain : plank));
            }
        }
        // Curved lid seam near the top (a shallow arc), splitting lid from body.
        for (int x = 1; x < TILE_PX - 1; x++) {
            int y = 4 + Math.round(2f * (float) Math.sin(x * (Math.PI / (TILE_PX - 2))));
            img.setRGB(ox + x, oy + y, 0xFF000000 | seam);
        }
        // Brass lock plate at the front-center, with a darker keyhole.
        for (int dy = -2; dy <= 2; dy++) {
            for (int dx = -2; dx <= 2; dx++) {
                int x = 8 + dx, y = 8 + dy;
                if (x < 0 || x >= TILE_PX || y < 0 || y >= TILE_PX) continue;
                img.setRGB(ox + x, oy + y, 0xFF000000 | metal);
            }
        }
        img.setRGB(ox + 8, oy + 8, 0xFF000000 | lock);
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
        int ox = tileX(index);
        int oy = tileY(index);
        for (int cy = 0; cy < TILE_PX; cy += 2) {
            for (int cx = 0; cx < TILE_PX; cx += 2) {
                boolean hole = rnd.nextFloat() < 0.32f;
                int base = rnd.nextFloat() < 0.55f ? 0x3E8E35 : 0x2F701F;
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

    /** Clamp a colour channel to [0, 255]. */
    private static int clamp(int v) { return Math.max(0, Math.min(255, v)); }
}
