package com.minecraftclone.engine.graphics;

import com.minecraftclone.world.BlockType;

import java.awt.image.BufferedImage;
import java.util.EnumMap;
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

            case IRON_INGOT -> paintIngot(0xE8E8E8);
            case GOLD_INGOT -> paintIngot(0xE8C93A);
            case DIAMOND -> paintGem(0x5FE0E0);

            case RAW_PORKCHOP -> paintMeat(0xE8A0A0, 0xC87878);
            case RAW_BEEF -> paintMeat(0xC04848, 0x8E2E2E);
            case MUTTON -> paintMeat(0xD87870, 0xB04848);
            case ROTTEN_FLESH -> paintMeat(0x9A8A6A, 0x7A6A50);
            case BONES -> paintBones();
            case COAL -> paintCoal();

            default -> throw new IllegalArgumentException("No item texture generator for " + type);
        };
    }

    private static BufferedImage blank() {
        return new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
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

    /** Binds the given item's texture to texture unit 0. */
    public void bind(BlockType type) {
        Integer id = textureIds.get(type);
        if (id == null) {
            throw new IllegalArgumentException("No item texture loaded for " + type);
        }
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, id);
    }

    public void destroy() {
        for (int id : textureIds.values()) {
            glDeleteTextures(id);
        }
    }
}
