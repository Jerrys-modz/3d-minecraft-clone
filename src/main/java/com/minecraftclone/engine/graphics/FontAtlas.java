package com.minecraftclone.engine.graphics;

import java.awt.image.BufferedImage;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.GL_TEXTURE0;
import static org.lwjgl.opengl.GL13.glActiveTexture;

/**
 * A tiny procedurally-generated strip of 10 tiles holding the digits 0-9,
 * for the HUD's inventory-count text. Split out from {@link TextureAtlas}
 * so the block atlas only has to hold block art - digits are neither a
 * block nor an inventory item, just HUD text, so they get their own
 * minimal strip rather than sharing space with either.
 */
public class FontAtlas {

    public static final int TILE_PX = 16;
    public static final int DIGIT_COUNT = 10;
    public static final int ATLAS_W = TILE_PX * DIGIT_COUNT;
    public static final int ATLAS_H = TILE_PX;

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

    private int textureId;

    public void generate() {
        textureId = GLTexture.upload(buildImage());
    }

    /** Builds the CPU-side strip image without touching the GPU - split out so tooling/tests can inspect the art directly. */
    public BufferedImage buildImage() {
        BufferedImage image = new BufferedImage(ATLAS_W, ATLAS_H, BufferedImage.TYPE_INT_ARGB);
        for (int d = 0; d < DIGIT_COUNT; d++) {
            paintDigit(image, d, DIGIT_GLYPHS[d]);
        }
        return image;
    }

    /**
     * Draws a digit from a 3x5 bitmap (each glyph string is 15 chars, row-major,
     * '1' = filled), scaled up ~3x and centered in its tile, on a transparent
     * background - a compact enough pixel font to read at small HUD sizes.
     */
    private void paintDigit(BufferedImage img, int digit, String glyph) {
        int ox = digit * TILE_PX;
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
                        int x = ox + offX + col * scale + sx;
                        int y = offY + row * scale + sy;
                        img.setRGB(x, y, 0xFFFFFFFF);
                    }
                }
            }
        }
    }

    public void bind() {
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, textureId);
    }

    /** Returns {u0, v0, u1, v1} for the given digit (0-9), inset slightly to avoid edge bleeding. */
    public float[] getUV(int digit) {
        int d = Math.max(0, Math.min(9, digit));
        float inset = 0.02f / DIGIT_COUNT;
        float u0 = (float) d / DIGIT_COUNT + inset;
        float u1 = (float) (d + 1) / DIGIT_COUNT - inset;
        return new float[]{u0, 0f, u1, 1f};
    }

    public void destroy() {
        glDeleteTextures(textureId);
    }
}
