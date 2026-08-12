package com.minecraftclone.engine.graphics;

import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.util.Random;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE;
import static org.lwjgl.opengl.GL13.GL_TEXTURE0;
import static org.lwjgl.opengl.GL13.glActiveTexture;
import static org.lwjgl.system.MemoryUtil.memAlloc;
import static org.lwjgl.system.MemoryUtil.memFree;

/**
 * A small procedurally-generated block texture atlas, laid out on an 8x8 grid
 * of 16x16 pixel tiles (128x128 total). Generating the art at runtime keeps
 * the project fully self-contained with no external image assets to ship.
 * <p>
 * Tile indices match {@link com.minecraftclone.world.BlockType}'s
 * topTile/sideTile/bottomTile fields. Tiles for cross-shaped decoration
 * blocks (grass/flowers) are painted onto a transparent background and rely
 * on the chunk fragment shader's alpha-cutout discard.
 */
public class TextureAtlas {

    public static final int GRID = 8;
    public static final int TILE_PX = 16;
    public static final int ATLAS_PX = GRID * TILE_PX;

    /** Tiles 24-33: digits '0'-'9', for the HUD's inventory-count text. See {@link #digitTile}. */
    public static final int DIGIT_TILE_BASE = 24;

    private static final String[] DIGIT_GLYPHS = {
            "111101101101111", // 0
            "010110010010111", // 1
            "111001111100111", // 2
            "111001111001111", // 3
            "101101111001001", // 4
            "111100111001111", // 5
            "111100111101111", // 6
            "111001010010010", // 7
            "111101111101111", // 8
            "111101111001111", // 9
    };

    /** Atlas tile index for the given digit (0-9). */
    public static int digitTile(int digit) {
        return DIGIT_TILE_BASE + Math.max(0, Math.min(9, digit));
    }

    private int textureId;

    public void generate() {
        textureId = uploadImage(buildImage());
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
        paintTile(image, 6, rnd, 0x3B6FD1, 0x2E58A8, false);  // water
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
        paintTile(image, 20, rnd, 0xE25822, 0xB33A12, true);   // lava

        paintCrossGrass(image, 21, rnd, 0x4C8C2C);                        // tall grass
        paintCrossFlower(image, 22, rnd, 0x3D6E2E, 0xD0392B, 0xE8C93A);   // red flower
        paintCrossFlower(image, 23, rnd, 0x3D6E2E, 0xF2D33A, 0xB5651D);   // yellow flower

        for (int d = 0; d <= 9; d++) {
            paintDigit(image, digitTile(d), DIGIT_GLYPHS[d], 0xFFFFFF);
        }

        paintTile(image, 34, rnd, 0xBEE7EA, 0xA8D3D6, false); // glass
        paintGlassPanes(image, 34);
        paintApple(image, 35);
        paintBerries(image, 36);
        paintBerryBush(image, 37, rnd);

        paintStick(image, 38);
        paintPickaxe(image, 39, 0xA9814F);  // wood
        paintPickaxe(image, 40, 0x9E9E9E);  // stone
        paintPickaxe(image, 41, 0xE8E8E8);  // iron
        paintPickaxe(image, 42, 0x5FE0E0);  // diamond
        paintAxe(image, 43, 0xA9814F);      // wood
        paintAxe(image, 44, 0x9E9E9E);      // stone
        paintAxe(image, 45, 0xE8E8E8);      // iron
        paintAxe(image, 46, 0x5FE0E0);      // diamond
        paintSword(image, 47, 0xA9814F);    // wood
        paintSword(image, 48, 0x9E9E9E);    // stone
        paintSword(image, 49, 0xE8E8E8);    // iron
        paintSword(image, 50, 0x5FE0E0);    // diamond

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

    /** A small round red apple with a stem, on a transparent background - a foraged food item. */
    private void paintApple(BufferedImage img, int index) {
        int ox = tileX(index);
        int oy = tileY(index);
        double cx = 8, cy = 9.5, r = 4.5;
        for (int y = 0; y < TILE_PX; y++) {
            for (int x = 0; x < TILE_PX; x++) {
                if (Math.hypot(x - cx, y - cy) <= r) {
                    img.setRGB(ox + x, oy + y, 0xFF000000 | 0xC62828);
                }
            }
        }
        img.setRGB(ox + 8, oy + 4, 0xFF000000 | 0x5B3A21);
        img.setRGB(ox + 9, oy + 3, 0xFF000000 | 0x4C8C2C);
        img.setRGB(ox + 10, oy + 3, 0xFF000000 | 0x4C8C2C);
    }

    /** A small cluster of dark berries on a stem, on a transparent background - a foraged food item. */
    private void paintBerries(BufferedImage img, int index) {
        int ox = tileX(index);
        int oy = tileY(index);
        int berry = 0x7A2048;
        int[][] blobs = {{6, 7}, {9, 7}, {7, 9}, {10, 9}, {8, 11}};
        for (int[] o : blobs) {
            for (int dy = 0; dy < 2; dy++) {
                for (int dx = 0; dx < 2; dx++) {
                    img.setRGB(ox + o[0] + dx, oy + o[1] + dy, 0xFF000000 | berry);
                }
            }
        }
        for (int y = 4; y < 7; y++) {
            img.setRGB(ox + 8, oy + y, 0xFF000000 | 0x4C8C2C);
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

    /** A short diagonal brown stick, on a transparent background - the basic tool-crafting ingredient. */
    private void paintStick(BufferedImage img, int index) {
        int ox = tileX(index);
        int oy = tileY(index);
        drawThickLine(img, ox, oy, 3, 13, 12, 4, 0x8B5A2B);
    }

    /** A vertical handle with a wide two-pronged head near the top, tinted by material - pickaxe icon. */
    private void paintPickaxe(BufferedImage img, int index, int headColor) {
        int ox = tileX(index);
        int oy = tileY(index);
        drawThickLine(img, ox, oy, 8, 15, 7, 5, 0x6E4A2A); // handle
        drawThickLine(img, ox, oy, 3, 7, 13, 4, headColor);  // head, left prong to right prong
        drawThickLine(img, ox, oy, 3, 7, 8, 3, headColor);
        drawThickLine(img, ox, oy, 13, 4, 8, 3, headColor);
    }

    /** A vertical handle with a triangular blade near the top, tinted by material - axe icon. */
    private void paintAxe(BufferedImage img, int index, int headColor) {
        int ox = tileX(index);
        int oy = tileY(index);
        drawThickLine(img, ox, oy, 6, 15, 7, 6, 0x6E4A2A); // handle
        for (int y = 2; y <= 8; y++) {
            int width = 6 - Math.abs(y - 5);
            for (int x = 0; x < width; x++) {
                int px = 7 + x, py = y;
                if (px >= 0 && px < TILE_PX && py >= 0 && py < TILE_PX) {
                    img.setRGB(ox + px, oy + py, 0xFF000000 | headColor);
                }
            }
        }
    }

    /** A pointed vertical blade over a dark crossguard and a short handle, tinted by material - sword icon. */
    private void paintSword(BufferedImage img, int index, int bladeColor) {
        int ox = tileX(index);
        int oy = tileY(index);
        drawThickLine(img, ox, oy, 8, 2, 8, 10, bladeColor); // blade
        img.setRGB(ox + 8, oy + 2, 0xFF000000 | bladeColor); // sharpen the tip to a single pixel
        for (int x = 4; x <= 11; x++) {
            img.setRGB(ox + x, oy + 11, 0xFF000000 | 0x4A4A4A); // crossguard
        }
        drawThickLine(img, ox, oy, 8, 12, 8, 15, 0x6E4A2A); // handle
    }

    /** Plots a ~2px-thick straight line between two points in tile-local coordinates. */
    private void drawThickLine(BufferedImage img, int ox, int oy, int x0, int y0, int x1, int y1, int color) {
        int steps = Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0)) * 2 + 1;
        for (int i = 0; i <= steps; i++) {
            float t = steps == 0 ? 0 : (float) i / steps;
            int x = Math.round(x0 + (x1 - x0) * t);
            int y = Math.round(y0 + (y1 - y0) * t);
            for (int dx = 0; dx <= 1; dx++) {
                int px = x + dx, py = y;
                if (px >= 0 && px < TILE_PX && py >= 0 && py < TILE_PX) {
                    img.setRGB(ox + px, oy + py, 0xFF000000 | color);
                }
            }
        }
    }

    /**
     * Draws a digit from a 3x5 bitmap (each glyph string is 15 chars, row-major,
     * '1' = filled), scaled up ~3x and centered in the tile, on a transparent
     * background - a compact enough pixel font to read at small HUD sizes.
     */
    private void paintDigit(BufferedImage img, int index, String glyph, int color) {
        int ox = tileX(index);
        int oy = tileY(index);
        int scale = 3;
        int glyphPxW = 3 * scale;
        int glyphPxH = 5 * scale;
        int offX = (TILE_PX - glyphPxW) / 2;
        int offY = (TILE_PX - glyphPxH) / 2;
        for (int row = 0; row < 5; row++) {
            for (int col = 0; col < 3; col++) {
                if (glyph.charAt(row * 3 + col) != '1') continue;
                for (int sy = 0; sy < scale; sy++) {
                    for (int sx = 0; sx < scale; sx++) {
                        int x = offX + col * scale + sx;
                        int y = offY + row * scale + sy;
                        img.setRGB(ox + x, oy + y, 0xFF000000 | color);
                    }
                }
            }
        }
    }

    private int uploadImage(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        ByteBuffer buffer = memAlloc(width * height * 4);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int argb = image.getRGB(x, y);
                buffer.put((byte) ((argb >> 16) & 0xFF)); // R
                buffer.put((byte) ((argb >> 8) & 0xFF));  // G
                buffer.put((byte) (argb & 0xFF));         // B
                buffer.put((byte) ((argb >> 24) & 0xFF)); // A
            }
        }
        buffer.flip();

        int id = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, id);
        glPixelStorei(GL_UNPACK_ALIGNMENT, 1);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        // Nearest filtering keeps the classic blocky Minecraft look.
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, buffer);
        glBindTexture(GL_TEXTURE_2D, 0);

        memFree(buffer);
        return id;
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
