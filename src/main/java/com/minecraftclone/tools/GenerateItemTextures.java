package com.minecraftclone.tools;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

/**
 * One-off offline tool: paints each inventory-only item (tools, food) as its
 * own standalone 16x16 pixel-art PNG and writes it to
 * {@code src/main/resources/items/}, where {@link
 * com.minecraftclone.engine.graphics.ItemTextures} loads it from the
 * classpath at runtime.
 * <p>
 * Unlike the block {@link com.minecraftclone.engine.graphics.TextureAtlas}
 * (which is generated in memory on every launch, since chunk meshing needs
 * many blocks batched into one shared sheet), items get real committed
 * files: each one is its own texture, so there's no batching to preserve and
 * a real asset makes items easy to spot, tweak, or eventually replace by
 * hand.
 * <p>
 * Not invoked at runtime - run this tool's {@code main} manually and commit
 * the resulting PNGs whenever an item's art needs to be (re)generated.
 */
public final class GenerateItemTextures {

    private static final int SIZE = 16;

    private GenerateItemTextures() {
    }

    public static void main(String[] args) throws IOException {
        File outDir = new File(args.length > 0 ? args[0] : "src/main/resources/items");
        if (!outDir.exists() && !outDir.mkdirs()) {
            throw new IOException("Could not create output directory: " + outDir);
        }

        write(outDir, "stick", paintStick());
        write(outDir, "apple", paintApple());
        write(outDir, "berries", paintBerries());

        write(outDir, "wood_pickaxe", paintPickaxe(0xA9814F));
        write(outDir, "stone_pickaxe", paintPickaxe(0x9E9E9E));
        write(outDir, "iron_pickaxe", paintPickaxe(0xE8E8E8));
        write(outDir, "diamond_pickaxe", paintPickaxe(0x5FE0E0));

        write(outDir, "wood_axe", paintAxe(0xA9814F));
        write(outDir, "stone_axe", paintAxe(0x9E9E9E));
        write(outDir, "iron_axe", paintAxe(0xE8E8E8));
        write(outDir, "diamond_axe", paintAxe(0x5FE0E0));

        write(outDir, "wood_sword", paintSword(0xA9814F));
        write(outDir, "stone_sword", paintSword(0x9E9E9E));
        write(outDir, "iron_sword", paintSword(0xE8E8E8));
        write(outDir, "diamond_sword", paintSword(0x5FE0E0));

        write(outDir, "iron_ingot", paintIngot(0xE8E8E8));
        write(outDir, "gold_ingot", paintIngot(0xE8C93A));
        write(outDir, "diamond", paintGem(0x5FE0E0));

        write(outDir, "raw_porkchop", paintMeat(0xE8A0A0, 0xC87878));
        write(outDir, "raw_beef", paintMeat(0xC04848, 0x8E2E2E));
        write(outDir, "mutton", paintMeat(0xD87870, 0xB04848));

        System.out.println("Wrote 21 item textures to " + outDir.getAbsolutePath());
    }

    private static void write(File outDir, String name, BufferedImage image) throws IOException {
        File file = new File(outDir, name + ".png");
        ImageIO.write(image, "png", file);
        System.out.println("  " + file);
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

    /** A vertical handle with a wide two-pronged head near the top, tinted by material. */
    private static BufferedImage paintPickaxe(int headColor) {
        BufferedImage img = blank();
        drawThickLine(img, 8, 15, 7, 5, 0x6E4A2A); // handle
        drawThickLine(img, 3, 7, 13, 4, headColor);  // head, left prong to right prong
        drawThickLine(img, 3, 7, 8, 3, headColor);
        drawThickLine(img, 13, 4, 8, 3, headColor);
        return img;
    }

    /** A vertical handle with a triangular blade near the top, tinted by material. */
    private static BufferedImage paintAxe(int headColor) {
        BufferedImage img = blank();
        drawThickLine(img, 6, 15, 7, 6, 0x6E4A2A); // handle
        for (int y = 2; y <= 8; y++) {
            int width = 6 - Math.abs(y - 5);
            for (int x = 0; x < width; x++) {
                int px = 7 + x, py = y;
                if (px >= 0 && px < SIZE && py >= 0 && py < SIZE) {
                    img.setRGB(px, py, 0xFF000000 | headColor);
                }
            }
        }
        return img;
    }

    /** A pointed vertical blade over a dark crossguard and a short handle, tinted by material. */
    private static BufferedImage paintSword(int bladeColor) {
        BufferedImage img = blank();
        drawThickLine(img, 8, 2, 8, 10, bladeColor); // blade
        img.setRGB(8, 2, 0xFF000000 | bladeColor); // sharpen the tip to a single pixel
        for (int x = 4; x <= 11; x++) {
            img.setRGB(x, 11, 0xFF000000 | 0x4A4A4A); // crossguard
        }
        drawThickLine(img, 8, 12, 8, 15, 0x6E4A2A); // handle
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

    private static int lighten(int color) {
        int r = Math.min(255, ((color >> 16) & 0xFF) + 40);
        int g = Math.min(255, ((color >> 8) & 0xFF) + 40);
        int b = Math.min(255, (color & 0xFF) + 40);
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
}
