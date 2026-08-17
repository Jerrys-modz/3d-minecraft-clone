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

    public static final int GRID = 8;
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
}
