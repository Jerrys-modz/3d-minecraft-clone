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
 * (Inventory-only items have their own individual PNG files instead - see
 * {@link ItemTextures} - and the HUD's text font lives in its own small
 * atlas - see {@link FontAtlas} - since neither is a block.)
 * <p>
 * Tile indices match {@link com.minecraftclone.world.BlockType}'s
 * topTile/sideTile/bottomTile fields. Not every tile in the 8x8 grid is
 * painted - some indices are left over from tiles that moved out to their
 * own assets - unpainted tiles are simply fully transparent and unused.
 * Tiles for cross-shaped decoration blocks (grass/flowers/berry bush) are
 * painted onto a transparent background and rely on the chunk fragment
 * shader's alpha-cutout discard.
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

    private int textureId;

    public void generate() {
        textureId = GLTexture.upload(buildImage());
    }

    /** Builds the CPU-side atlas image without touching the GPU - split out so tooling/tests can inspect the art directly. */
    public BufferedImage buildImage() {
        BufferedImage image = new BufferedImage(ATLAS_PX, ATLAS_PX, BufferedImage.TYPE_INT_ARGB);
        Random rnd = new Random(1337);

        paintTile(image, 1, rnd, 0x5EA836, 0x4C8C2C, true);   // grass top
        paintTile(image, 2, rnd, 0x8B5A2B, 0x6E4623, true, 0x5EA836, 0.28f); // grass side (dirt + grass fringe)
        paintTile(image, 3, rnd, 0x8B5A2B, 0x6E4623, true);   // dirt
        paintTile(image, 4, rnd, 0x8A8A8A, 0x777777, true);   // stone
        paintTile(image, 5, rnd, 0xE0D2A0, 0xCBBB84, true);   // sand
        paintFluidTile(image, 6, 0x3B6FD1, 0x2E58A8, 190);     // water (translucent)
        paintTile(image, 7, rnd, 0x6E4A2A, 0x543A20, false);  // log side (bark stripes)
        paintLogStripes(image, 7, rnd);
        paintTile(image, 8, rnd, 0xC9A063, 0xAE8850, true);   // log top rings
        paintRings(image, 8);
        paintTile(image, 9, rnd, 0x3E8E35, 0x2F701F, true);   // leaves
        paintTile(image, 10, rnd, 0x3A3A3A, 0x232323, true);  // bedrock
        paintTile(image, 11, rnd, 0xC69A56, 0xAF8646, false); // planks
        paintPlankLines(image, 11);
        paintTile(image, 12, rnd, 0xF7F7FA, 0xE3E3EA, true);  // snow top
        paintTile(image, 13, rnd, 0x8B5A2B, 0x6E4623, true, 0xF7F7FA, 0.28f); // snow side
        paintTile(image, 14, rnd, 0x8D8878, 0x716C5E, true);  // gravel
        paintTile(image, 15, rnd, 0x4E8B3C, 0x3D6E2E, true);  // cactus

        paintOreTile(image, 16, rnd, 0x1B1B1B);               // coal ore (dark speckles in stone)
        paintOreTile(image, 17, rnd, 0xC08B5C);                // iron ore (rusty tan speckles)
        paintOreTile(image, 18, rnd, 0xE8C93A);                // gold ore (gold speckles)
        paintOreTile(image, 19, rnd, 0x5FE0E0);                // diamond ore (cyan speckles)
        paintFluidTile(image, 20, 0xE25822, 0xB33A12, 225);     // lava

        paintCrossGrass(image, 21, rnd, 0x4C8C2C);                        // tall grass
        paintCrossFlower(image, 22, rnd, 0x3D6E2E, 0xD0392B, 0xE8C93A);   // red flower
        paintCrossFlower(image, 23, rnd, 0x3D6E2E, 0xF2D33A, 0xB5651D);   // yellow flower

        paintTile(image, 34, rnd, 0xBEE7EA, 0xA8D3D6, false); // glass
        paintGlassPanes(image, 34);
        paintTile(image, 27, rnd, 0x2F6B2F, 0x245424, true);                 // swamp grass top
        paintTile(image, 28, rnd, 0x8B5A2B, 0x6E4623, true, 0x2F6B2F, 0.28f); // swamp grass side
        paintTile(image, 29, rnd, 0xB5532B, 0x94411F, true);                 // red clay (badlands)
        paintTile(image, 30, rnd, 0x8A6FA0, 0x6E5584, true);                 // mycelium top
        paintTile(image, 31, rnd, 0x8B5A2B, 0x6E4623, true, 0x8A6FA0, 0.28f); // mycelium side
        paintFluidTile(image, 32, 0x9ADBEA, 0x7FC4D6, 200);                  // ice
        paintDeadBush(image, 33, rnd);
        paintMushroom(image, 35, rnd, 0xD0392B, 0xB3251C);                   // red mushroom
        paintMushroom(image, 36, rnd, 0x8B5A2B, 0x6E4623);                   // brown mushroom
        paintVine(image, 39, rnd);
        paintTile(image, 40, rnd, 0xE8A0B4, 0xC97E97, true);   // cherry leaves (pink)
        paintTile(image, 41, rnd, 0xA5DBEE, 0x8AC6DE, true);   // packed ice
        paintBamboo(image, 42, rnd);
        paintLilyPad(image, 43, rnd);
        paintTile(image, 44, rnd, 0xE08A2E, 0xC7731F, true);   // pumpkin
        paintSeaweed(image, 45, rnd);
        paintNetherrack(image, 46, rnd);
        paintSoulSand(image, 47, rnd);
        paintTile(image, 48, rnd, 0xF5E6A0, 0xE8CE6A, true, 0xFFF7C0, 0.5f); // glowstone (bright, yellowish)
        paintNetherPortal(image, 49, rnd);
        paintTile(image, 50, rnd, 0xE4E0C8, 0xCFC8A8, true);   // end stone (pale, sandy)
        paintObsidian(image, 51, rnd);
        paintEndPortal(image, 52, rnd);
        paintLeavesCutout(image, LEAVES_CUTOUT_TILE, rnd);
        paintLamp(image, LAMP_TILE);
        paintFurnace(image, FURNACE_TILE);
        paintBerryBush(image, 37, rnd);
        paintTorch(image, 38);

        return image;
    }

    private static int tileX(int index) {
        return (index % GRID) * TILE_PX;
    }

    private static int tileY(int index) {
        return (index / GRID) * TILE_PX;
    }

    private void paintTile(BufferedImage img, int index, Random rnd, int baseColor, int altColor, boolean speckled) {
        paintTile(img, index, rnd, baseColor, altColor, speckled, 0, 0f);
    }

    /**
     * Fills a tile with a base color, sprinkling in noise speckles of altColor,
     * and optionally a fringe band of fringeColor along the top edge (used for
     * the grass/snow "side" tiles that blend into dirt).
     */
    private void paintTile(BufferedImage img, int index, Random rnd, int baseColor, int altColor,
                            boolean speckled, int fringeColor, float fringeHeightFraction) {
        int ox = tileX(index);
        int oy = tileY(index);
        int fringeRows = Math.round(TILE_PX * fringeHeightFraction);
        for (int y = 0; y < TILE_PX; y++) {
            for (int x = 0; x < TILE_PX; x++) {
                int color;
                if (fringeColor != 0 && y < fringeRows) {
                    color = (rnd.nextFloat() < 0.75f) ? fringeColor : baseColor;
                } else {
                    color = baseColor;
                    if (speckled && rnd.nextFloat() < 0.35f) {
                        color = altColor;
                    }
                }
                img.setRGB(ox + x, oy + y, 0xFF000000 | color);
            }
        }
    }

    /** Paints a translucent, lightly banded fluid tile without noisy speckles. */
    private void paintFluidTile(BufferedImage img, int index, int baseColor, int altColor, int alpha) {
        int ox = tileX(index);
        int oy = tileY(index);
        for (int y = 0; y < TILE_PX; y++) {
            int color = (y / 3) % 2 == 0 ? baseColor : altColor;
            for (int x = 0; x < TILE_PX; x++) {
                img.setRGB(ox + x, oy + y, (alpha << 24) | color);
            }
        }
    }

    private void paintLogStripes(BufferedImage img, int index, Random rnd) {
        int ox = tileX(index);
        int oy = tileY(index);
        int dark = 0x4A3018;
        for (int x = 0; x < TILE_PX; x += 4) {
            for (int y = 0; y < TILE_PX; y++) {
                if (rnd.nextFloat() < 0.6f) {
                    img.setRGB(ox + x, oy + y, 0xFF000000 | dark);
                }
            }
        }
    }

    private void paintRings(BufferedImage img, int index) {
        int ox = tileX(index);
        int oy = tileY(index);
        double cx = TILE_PX / 2.0 - 0.5;
        double cy = TILE_PX / 2.0 - 0.5;
        int dark = 0x8A6B3F;
        for (int y = 0; y < TILE_PX; y++) {
            for (int x = 0; x < TILE_PX; x++) {
                double d = Math.hypot(x - cx, y - cy);
                if (((int) Math.round(d)) % 3 == 0) {
                    img.setRGB(ox + x, oy + y, 0xFF000000 | dark);
                }
            }
        }
    }

    private void paintPlankLines(BufferedImage img, int index) {
        int ox = tileX(index);
        int oy = tileY(index);
        int dark = 0x8E6A34;
        for (int y = 0; y < TILE_PX; y++) {
            if (y % 4 == 0) {
                for (int x = 0; x < TILE_PX; x++) {
                    img.setRGB(ox + x, oy + y, 0xFF000000 | dark);
                }
            }
        }
    }

    /** Stone base with a few small clustered "ore" speckle blobs, for coal/iron/gold/diamond ore blocks. */
    private void paintOreTile(BufferedImage img, int index, Random rnd, int oreColor) {
        paintTile(img, index, rnd, 0x8A8A8A, 0x777777, true);
        int ox = tileX(index);
        int oy = tileY(index);
        int blobs = 3 + rnd.nextInt(3);
        for (int b = 0; b < blobs; b++) {
            int cx = rnd.nextInt(TILE_PX);
            int cy = rnd.nextInt(TILE_PX);
            int size = 1 + rnd.nextInt(2);
            for (int dy = -size; dy <= size; dy++) {
                for (int dx = -size; dx <= size; dx++) {
                    int px = cx + dx, py = cy + dy;
                    if (px < 0 || px >= TILE_PX || py < 0 || py >= TILE_PX) continue;
                    if (dx * dx + dy * dy <= size * size && rnd.nextFloat() < 0.85f) {
                        img.setRGB(ox + px, oy + py, 0xFF000000 | oreColor);
                    }
                }
            }
        }
    }

    /**
     * A tuft of a few vertical (slightly swaying) blade strokes on a fully
     * transparent background, for the cross-shaped tall grass decoration.
     */
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
                img.setRGB(ox + x, oy + y, 0xFF000000 | color);
            }
        }
    }

    /** A simple stem + petal cluster + center on a transparent background, for the cross-shaped flower decorations. */
    private void paintCrossFlower(BufferedImage img, int index, Random rnd, int stemColor, int petalColor, int centerColor) {
        int ox = tileX(index);
        int oy = tileY(index);
        int stemX = TILE_PX / 2;
        for (int y = TILE_PX - 6; y < TILE_PX; y++) {
            img.setRGB(ox + stemX, oy + y, 0xFF000000 | stemColor);
        }
        int headY = TILE_PX - 8;
        int[][] petalOffsets = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}, {-1, -1}, {1, -1}, {-1, 1}, {1, 1}};
        for (int[] o : petalOffsets) {
            int x = stemX + o[0], y = headY + o[1];
            if (x < 0 || x >= TILE_PX || y < 0 || y >= TILE_PX) continue;
            img.setRGB(ox + x, oy + y, 0xFF000000 | petalColor);
        }
        img.setRGB(ox + stemX, oy + headY, 0xFF000000 | centerColor);
    }

    /** Thin pane-divider lines (border + cross) over the base fill, for the glass block. */
    private void paintGlassPanes(BufferedImage img, int index) {
        int ox = tileX(index);
        int oy = tileY(index);
        int line = 0x8FB9BC;
        for (int i = 0; i < TILE_PX; i++) {
            img.setRGB(ox + i, oy, 0xFF000000 | line);
            img.setRGB(ox + i, oy + TILE_PX - 1, 0xFF000000 | line);
            img.setRGB(ox, oy + i, 0xFF000000 | line);
            img.setRGB(ox + TILE_PX - 1, oy + i, 0xFF000000 | line);
        }
        int mid = TILE_PX / 2;
        for (int i = 0; i < TILE_PX; i++) {
            img.setRGB(ox + mid, oy + i, 0xFF000000 | line);
            img.setRGB(ox + i, oy + mid, 0xFF000000 | line);
        }
    }

    /** A round leafy bush silhouette speckled with berries, on a transparent background - the harvestable world decoration. */
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
                    img.setRGB(ox + x, oy + y, 0xFF000000 | color);
                }
            }
        }
    }

    /**
     * A "fast leaves" tile for the see-through-leaves setting: the same green
     * palette as the solid leaves tile (index 9) but with small square holes
     * carved out, so the chunk shader's alpha-cutout (see chunk.frag's discard)
     * lets the world behind the canopy show through. Painted with its own draw
     * from the shared seeded Random, so the hole pattern differs from tile 9's.
     */
    /** A dead, dried-out twig clump on a transparent background - the badlands decoration. */
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

    /** A simple stem + rounded cap on a transparent background, for red/brown mushrooms. */
    private void paintMushroom(BufferedImage img, int index, Random rnd, int capColor, int capDark) {
        int ox = tileX(index);
        int oy = tileY(index);
        int stemColor = 0xD9D2C0;
        int stemX = TILE_PX / 2;
        for (int y = TILE_PX - 7; y < TILE_PX - 1; y++) {
            img.setRGB(ox + stemX, oy + y, 0xFF000000 | stemColor);
            img.setRGB(ox + stemX + 1, oy + y, 0xFF000000 | stemColor);
        }
        double cx = TILE_PX / 2.0, cy = TILE_PX - 9.0;
        for (int y = 0; y < TILE_PX; y++) {
            for (int x = 0; x < TILE_PX; x++) {
                double d = Math.hypot(x - cx, (y - cy) * 1.5);
                if (d <= 5.5) {
                    img.setRGB(ox + x, oy + y, 0xFF000000 | (rnd.nextFloat() < 0.3f ? capDark : capColor));
                }
            }
        }
    }

    /** A thin green vine strand with a few leaves, on a transparent background - hangs from jungle canopies. */
    private void paintVine(BufferedImage img, int index, Random rnd) {
        int ox = tileX(index);
        int oy = tileY(index);
        for (int y = 0; y < TILE_PX; y++) {
            img.setRGB(ox + 7, oy + y, 0xFF000000 | 0x2F6B2F);
            img.setRGB(ox + 8, oy + y, 0xFF000000 | 0x2F6B2F);
        }
        for (int i = 0; i < 7; i++) {
            int x = 5 + rnd.nextInt(6);
            int y = 2 + rnd.nextInt(TILE_PX - 4);
            img.setRGB(ox + x, oy + y, 0xFF000000 | 0x3E8E35);
        }
    }

    /** A tall green bamboo stalk with segment rings and a few leaves, on a transparent background. */
    private void paintBamboo(BufferedImage img, int index, Random rnd) {
        int ox = tileX(index);
        int oy = tileY(index);
        for (int y = 0; y < TILE_PX; y++) {
            img.setRGB(ox + 7, oy + y, 0xFF000000 | 0x5FA84C);
            img.setRGB(ox + 8, oy + y, 0xFF000000 | 0x5FA84C);
        }
        for (int y = 2; y < TILE_PX; y += 5) {
            img.setRGB(ox + 6, oy + y, 0xFF000000 | 0x4A8A3A);
            img.setRGB(ox + 9, oy + y, 0xFF000000 | 0x4A8A3A);
        }
        for (int i = 0; i < 4; i++) {
            int x = 3 + rnd.nextInt(9);
            int y = 1 + rnd.nextInt(TILE_PX - 4);
            img.setRGB(ox + x, oy + y, 0xFF000000 | 0x3E8E35);
        }
    }

    /** A flat elliptical pad floating on water, on a transparent background - swamp lily pads. */
    private void paintLilyPad(BufferedImage img, int index, Random rnd) {
        int ox = tileX(index);
        int oy = tileY(index);
        double cx = 8, cy = 10, r = 6;
        for (int y = 0; y < TILE_PX; y++) {
            for (int x = 0; x < TILE_PX; x++) {
                double d = Math.hypot(x - cx, (y - cy) * 0.5);
                if (d <= r) {
                    img.setRGB(ox + x, oy + y, 0xFF000000 | (rnd.nextFloat() < 0.3f ? 0x3E8E35 : 0x2F701F));
                }
            }
        }
    }

    /** Wavy green strands of seaweed, on a transparent background - grows on ocean floors. */
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
                img.setRGB(ox + x, oy + y, 0xFF000000 | 0x2F8F3A);
            }
        }
    }

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

    /**
     * A glowing full-cube lamp: a warm light panel with a bright center and a
     * darker frame, on an opaque background. The lamp itself emits light (see
     * {@link com.minecraftclone.world.BlockType#LAMP}), so this tile just gives
     * it a distinct "lit fixture" look; nearby blocks are brightened by the
     * light baking in {@link com.minecraftclone.world.Chunk}.
     */
    private void paintLamp(BufferedImage img, int index) {
        int ox = tileX(index);
        int oy = tileY(index);
        int frame = 0x6E4A2A;  // dark wood/graphite border
        int warm = 0xFFE08A;   // warm glow body
        int core = 0xFFFFF0;   // bright center
        for (int y = 0; y < TILE_PX; y++) {
            for (int x = 0; x < TILE_PX; x++) {
                boolean edge = x < 2 || x >= TILE_PX - 2 || y < 2 || y >= TILE_PX - 2;
                boolean center = x >= 5 && x < 11 && y >= 5 && y < 11;
                int color = edge ? frame : (center ? core : warm);
                img.setRGB(ox + x, oy + y, 0xFF000000 | color);
            }
        }
    }

    /**
     * A furnace's front face: a stone body with a dark rectangular mouth and a
     * lighter lintel above it, on an opaque background. The block reuses the
     * plain stone tile for its top/bottom, so only the (orientation-less) sides
     * get this "furnace" look - see {@link com.minecraftclone.world.BlockType#FURNACE}.
     */
    private void paintFurnace(BufferedImage img, int index) {
        int ox = tileX(index);
        int oy = tileY(index);
        int stone = 0x8A8A8A;
        int dark = 0x2E2E2E;
        int lintel = 0x6E6E6E;
        for (int y = 0; y < TILE_PX; y++) {
            for (int x = 0; x < TILE_PX; x++) {
                img.setRGB(ox + x, oy + y, 0xFF000000 | stone);
            }
        }
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

    /** A short brown stick with a glowing orange/yellow flame on top, on a transparent background - the torch light source. */
    private void paintTorch(BufferedImage img, int index) {
        int ox = tileX(index);
        int oy = tileY(index);
        // Stick: a thin vertical shaft in the lower two-thirds of the tile.
        for (int y = 7; y < TILE_PX; y++) {
            img.setRGB(ox + 7, oy + y, 0xFF000000 | 0x6E4A2A);
            img.setRGB(ox + 8, oy + y, 0xFF000000 | 0x8B5A2B);
        }
        // Flame: a small warm blob at the top, brighter toward the center.
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
