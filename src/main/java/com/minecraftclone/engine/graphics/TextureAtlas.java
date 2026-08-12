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

        paintTile(image, 34, rnd, 0xBEE7EA, 0xA8D3D6, false); // glass
        paintGlassPanes(image, 34);
        paintLeavesCutout(image, LEAVES_CUTOUT_TILE, rnd);
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
