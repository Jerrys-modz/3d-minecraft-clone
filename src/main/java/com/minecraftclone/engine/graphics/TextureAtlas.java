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
 * A small procedurally-generated block texture atlas, laid out on a 4x4 grid
 * of 16x16 pixel tiles (64x64 total). Generating the art at runtime keeps the
 * project fully self-contained with no external image assets to ship.
 * <p>
 * Tile indices (0-15) match {@link com.minecraftclone.world.BlockType}'s
 * topTile/sideTile/bottomTile fields.
 */
public class TextureAtlas {

    public static final int GRID = 4;
    public static final int TILE_PX = 16;
    public static final int ATLAS_PX = GRID * TILE_PX;

    private int textureId;

    public void generate() {
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

        textureId = uploadImage(image);
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
