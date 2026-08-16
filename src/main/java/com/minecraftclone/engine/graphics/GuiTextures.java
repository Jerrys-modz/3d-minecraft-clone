package com.minecraftclone.engine.graphics;

import java.awt.image.BufferedImage;
import java.util.Random;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.GL_TEXTURE0;
import static org.lwjgl.opengl.GL13.glActiveTexture;

/**
 * Procedurally-generated GUI art: a Minecraft-style 9-slice container panel and
 * a slot cell, each in a <em>light</em> and a <em>dark</em> theme. Everything
 * is painted at runtime into one small texture (like the block atlas and font
 * atlas), so the game stays fully self-contained.
 * <p>
 * Layout is a 6x3 grid of 16px tiles:
 * <pre>
 *   col 0-2   light panel 9-slice (corner TL, edge top, corner TR /
 *                               edge left, center, edge right /
 *                               corner BL, edge bottom, corner BR)
 *   col 3-5   dark panel 9-slice (same arrangement)
 *   row 3     slot: light (col 0), dark (col 1)
 * </pre>
 * A panel is drawn as nine quads sampled from the right 3x3 tile group, so the
 * border stays crisp at any size; a slot is a single quad.
 */
public class GuiTextures {

    public static final int TILE_PX = 16;
    public static final int COLS = 6;
    public static final int ROWS = 4;
    public static final int ATLAS_W = COLS * TILE_PX;
    public static final int ATLAS_H = ROWS * TILE_PX;

    /** Tile columns of the light 9-slice panel. */
    private static final int LIGHT_PANEL_COL = 0;
    /** Tile columns of the dark 9-slice panel. */
    private static final int DARK_PANEL_COL = 3;
    /** Tile (row, col) of the slot cell for each theme. */
    private static final int SLOT_ROW = 3;
    private static final int LIGHT_SLOT_COL = 0;
    private static final int DARK_SLOT_COL = 1;

    private int textureId;

    public void generate() {
        textureId = GLTexture.upload(buildImage());
    }

    /** Builds the CPU-side atlas image without touching the GPU. */
    public BufferedImage buildImage() {
        BufferedImage image = new BufferedImage(ATLAS_W, ATLAS_H, BufferedImage.TYPE_INT_ARGB);
        Random rnd = new Random(4711);
        paintPanel(image, LIGHT_PANEL_COL, rnd,
                0xC6C6C6, 0xBEBEBE, 0xF8F8F8, 0x9E9E9E); // light: fill, base, highlight, shadow
        paintPanel(image, DARK_PANEL_COL, rnd,
                0x282828, 0x3A3A3A, 0x4A4A4A, 0x141414); // dark
        paintSlot(image, SLOT_ROW, LIGHT_SLOT_COL, rnd, 0x8B8B8B, 0xB0B0B0, 0x565656);
        paintSlot(image, SLOT_ROW, DARK_SLOT_COL, rnd, 0x161616, 0x3E3E3E, 0x080808);
        paintSlot(image, SLOT_ROW, DARK_SLOT_COL, rnd, 0x161616, 0x3E3E3E, 0x080808);
        return image;
    }

    /**
     * Paints a 3x3 group of tiles starting at column {@code col} as a
     * Minecraft-style embossed panel: a light gray translucent fill with a
     * raised border - bright on the top+left edges and a soft shadow on the
     * bottom+right edges, so the whole panel reads as a light surface with a
     * subtle frame. The four corner tiles hold the corners, the edge tiles the
     * straight border runs, and the center tile is the fill - so drawing the
     * nine as a 9-slice keeps the border looking right at any panel size.
     */
    private void paintPanel(BufferedImage img, int col, Random rnd, int fill, int base, int light, int dark) {
        // [top, right, bottom, left] outer edges for each of the nine tiles
        // (row-major from the top-left corner).
        int[][] edges = {
                {1, 0, 0, 1}, // top-left corner
                {1, 0, 0, 0}, // top edge
                {1, 1, 0, 0}, // top-right corner
                {0, 0, 0, 1}, // left edge
                {0, 0, 0, 0}, // center
                {0, 1, 0, 0}, // right edge
                {0, 0, 1, 1}, // bottom-left corner
                {0, 0, 1, 0}, // bottom edge
                {0, 1, 1, 0}, // bottom-right corner
        };
        for (int i = 0; i < 9; i++) {
            int cx = col + (i % 3), cy = i / 3;
            int top = edges[i][0], right = edges[i][1], bottom = edges[i][2], left = edges[i][3];
            int ox = cx * TILE_PX, oy = cy * TILE_PX;
            for (int y = 0; y < TILE_PX; y++) {
                for (int x = 0; x < TILE_PX; x++) {
                    int color = fill;
                    if (rnd.nextFloat() < 0.06f) color = base; // faint grain
                    // Embossed bevel: a 2px bright band along the top+left outer
                    // edges and a 2px shadow band along the bottom+right edges.
                    if ((top != 0 && y < 2) || (left != 0 && x < 2)) {
                        color = light;
                    } else if ((bottom != 0 && y >= TILE_PX - 2) || (right != 0 && x >= TILE_PX - 2)) {
                        color = dark;
                    }
                    img.setRGB(ox + x, oy + y, 0xFAFFFFFF & (0xFF000000 | color));
                }
            }
        }
    }

    /**
     * Paints a slot cell the way Minecraft does: a medium-dark recessed square
     * with a 1px light border on the top+left, a dark border on the bottom+right
     * (so it reads as inset), and a subtly darker inner bevel. Item counts are
     * drawn in white on top, so the cell stays dark enough for them to pop.
     */
    private void paintSlot(BufferedImage img, int row, int col, Random rnd, int fill, int light, int dark) {
        int ox = col * TILE_PX, oy = row * TILE_PX;
        for (int y = 0; y < TILE_PX; y++) {
            for (int x = 0; x < TILE_PX; x++) {
                int color = fill;
                if (rnd.nextFloat() < 0.05f) color = Math.min(0xFFFFFF, fill + 0x0A0A0A);
                if (y == 0 || x == 0) {
                    color = light;
                } else if (y == TILE_PX - 1 || x == TILE_PX - 1) {
                    color = dark;
                } else if (y == 1 || x == 1) {
                    color = Math.min(0xFFFFFF, fill + 0x0E0E0E); // inner highlight
                }
                img.setRGB(ox + x, oy + y, 0xFF000000 | color);
            }
        }
    }

    /** The {u0, v0, u1, v1} of one 16px tile. */
    private float[] tileUV(int col, int row) {
        float u0 = (float) col / COLS;
        float v0 = (float) row / ROWS;
        float u1 = (float) (col + 1) / COLS;
        float v1 = (float) (row + 1) / ROWS;
        return new float[]{u0, v0, u1, v1};
    }

    /** The {u0, v0, u1, v1} of the 9-slice panel tile at (sliceRow, sliceCol) - 0..2 each - for {@code dark}. */
    public float[] panelUV(int sliceCol, int sliceRow, boolean dark) {
        return tileUV((dark ? DARK_PANEL_COL : LIGHT_PANEL_COL) + sliceCol, sliceRow);
    }

    /** The {u0, v0, u1, v1} of the slot cell for {@code dark}. */
    public float[] slotUV(boolean dark) {
        return tileUV(dark ? DARK_SLOT_COL : LIGHT_SLOT_COL, SLOT_ROW);
    }

    public void bind() {
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, textureId);
    }

    public void destroy() {
        glDeleteTextures(textureId);
    }
}
