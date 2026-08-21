package com.minecraftclone.engine.graphics;

import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link TextureAtlas#buildImage()} is deliberately pure (no GL calls - see
 * its own doc comment), so the generated art can be inspected directly here
 * without a display.
 */
class TextureAtlasTest {

    private static BufferedImage atlas() {
        return new TextureAtlas().buildImage();
    }

    private static BufferedImage tile(BufferedImage atlas, int index) {
        int px = TextureAtlas.TILE_PX;
        int ox = (index % TextureAtlas.GRID) * px;
        int oy = (index / TextureAtlas.GRID) * px;
        return atlas.getSubimage(ox, oy, px, px);
    }

    private static BufferedImage waterTile() {
        return tile(atlas(), 6); // BlockType.WATER's topTile/sideTile
    }

    @Test
    void buildImageProducesAFullSizedAtlas() {
        BufferedImage image = atlas();
        assertEquals(TextureAtlas.ATLAS_PX, image.getWidth());
        assertEquals(TextureAtlas.ATLAS_PX, image.getHeight());
    }

    /**
     * Regression test: the water tile used to read as a flat, grey-blue
     * zigzag reported as looking wrong - see paintFluidTile's redesign. This
     * locks in the two properties that were actually requested: a richer,
     * more saturated blue-teal palette (not the old washed-out grey-blue),
     * and real pixel-to-pixel variation (not a flat fill) - a future
     * regression that collapsed the ripple math back to a constant would
     * fail this even though it wouldn't be caught by just checking the
     * image's overall size.
     */
    @Test
    void waterTileIsTealAndNotAFlatFill() {
        BufferedImage water = waterTile();
        int px = TextureAtlas.TILE_PX;
        Set<Integer> distinctColors = new HashSet<>();
        int blueCount = 0;
        for (int y = 0; y < px; y++) {
            for (int x = 0; x < px; x++) {
                int rgb = water.getRGB(x, y) & 0xFFFFFF;
                distinctColors.add(rgb);
                int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
                // Teal/blue: blue channel clearly leads red (grey has r~=g~=b).
                if (b > r + 20) blueCount++;
            }
        }
        assertTrue(distinctColors.size() > 4, "expected real ripple variation, not a near-flat fill");
        assertTrue(blueCount > (px * px) / 2, "expected most of the tile to read as blue/teal rather than neutral grey");
    }

    @Test
    void copperOreReadsOrangeOnStoneNotAGreyBlob() {
        BufferedImage copper = tile(atlas(), 80);
        int orange = 0, stoneish = 0, px = TextureAtlas.TILE_PX;
        for (int y = 0; y < px; y++) {
            for (int x = 0; x < px; x++) {
                int rgb = copper.getRGB(x, y) & 0xFFFFFF;
                int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
                if (r > g + 20 && r > b + 30 && r > 140) orange++;
                if (Math.abs(r - g) < 18 && Math.abs(g - b) < 18) stoneish++;
            }
        }
        assertTrue(orange > 12, "copper should show a real orange vein, got " + orange + " orange pixels");
        assertTrue(stoneish > px * px / 3, "stone host rock should still be visible around the vein");
    }

    @Test
    void smallOresAreSparseFlecksNotFullTileStripes() {
        BufferedImage smallCopper = tile(atlas(), 100);
        int px = TextureAtlas.TILE_PX;
        int orange = 0, distinctRows = 0;
        for (int y = 0; y < px; y++) {
            boolean rowHasStone = false, rowHasOre = false;
            for (int x = 0; x < px; x++) {
                int rgb = smallCopper.getRGB(x, y) & 0xFFFFFF;
                int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
                if (r > g + 20 && r > b + 30 && r > 140) {
                    orange++;
                    rowHasOre = true;
                }
                if (Math.abs(r - g) < 18 && Math.abs(g - b) < 18) rowHasStone = true;
            }
            if (rowHasStone && rowHasOre) distinctRows++;
        }
        assertTrue(orange > 4 && orange < px * px / 3,
                "small ores should be sparse flecks, not a full-tile fill (" + orange + " ore pixels)");
        assertTrue(distinctRows > 0, "small-ore rows should mix stone and mineral, not solid stripes");
    }

    @Test
    void gtnhOresAreVisuallyDistinctFromEachOther() {
        BufferedImage image = atlas();
        // copper, tin, cobalt, uranium, ruby, malachite, sulfur, sapphire
        int[] tiles = {80, 81, 87, 96, 175, 128, 136, 176};
        long[] signatures = new long[tiles.length];
        for (int i = 0; i < tiles.length; i++) {
            BufferedImage t = tile(image, tiles[i]);
            long sumR = 0, sumG = 0, sumB = 0;
            int px = TextureAtlas.TILE_PX;
            for (int y = 0; y < px; y++) {
                for (int x = 0; x < px; x++) {
                    int rgb = t.getRGB(x, y) & 0xFFFFFF;
                    sumR += (rgb >> 16) & 0xFF;
                    sumG += (rgb >> 8) & 0xFF;
                    sumB += rgb & 0xFF;
                }
            }
            signatures[i] = (sumR << 32) ^ (sumG << 16) ^ sumB;
        }
        Set<Long> unique = new HashSet<>();
        for (long s : signatures) unique.add(s);
        assertEquals(tiles.length, unique.size(), "ores that used to share a grey palette must no longer look identical");
    }

    @Test
    void rubyAndSapphireReadAsSaturatedGems() {
        BufferedImage ruby = tile(atlas(), 175);
        BufferedImage sapphire = tile(atlas(), 176);
        int red = countWhere(ruby, (r, g, b) -> r > 160 && r > g + 40 && r > b + 20);
        int blue = countWhere(sapphire, (r, g, b) -> b > 160 && b > r + 40 && b > g + 20);
        assertTrue(red > 6, "ruby should have saturated red facets");
        assertTrue(blue > 6, "sapphire should have saturated blue facets");
    }

    @Test
    void oakChestFacesAreDistinctWithABrassLockOnTheFront() {
        BufferedImage image = atlas();
        BufferedImage front = tile(image, TextureAtlas.CHEST_TILE);
        BufferedImage top = tile(image, TextureAtlas.CHEST_TOP_TILE);
        BufferedImage side = tile(image, TextureAtlas.CHEST_SIDE_TILE);
        BufferedImage bottom = tile(image, TextureAtlas.CHEST_BOTTOM_TILE);

        int frontBrass = countWhere(front, TextureAtlasTest::isBrass);
        int sideBrass = countWhere(side, TextureAtlasTest::isBrass);
        int topBrass = countWhere(top, TextureAtlasTest::isBrass);
        assertTrue(frontBrass >= 8, "front should show a real brass lock, got " + frontBrass);
        assertTrue(frontBrass > sideBrass, "lock lives on the front, not the side");
        assertTrue(topBrass >= 4, "top should show the latch tab on the front edge");

        // Dark frame on every face so it doesn't read as a flat plank tile.
        assertTrue(isDark(front.getRGB(0, 0)), "front needs a dark rim");
        assertTrue(isDark(top.getRGB(0, 0)), "top needs a dark rim");
        assertTrue(isDark(side.getRGB(0, 0)), "side needs a dark rim");

        assertTrue(signature(front) != signature(top), "top and front must not share one tile");
        assertTrue(signature(front) != signature(side), "side and front must not share one tile");
        assertTrue(signature(top) != signature(bottom), "underside should be darker than the lid");
        assertTrue(distinctColors(front) > 6, "front should have grain, frame and lock, not a flat fill");
    }

    @Test
    void destroyStagesAccumulateCracksOnATransparentTile() {
        BufferedImage image = atlas();
        int stage0 = countOpaque(tile(image, TextureAtlas.DESTROY_STAGE_TILE));
        int stage9 = countOpaque(tile(image, TextureAtlas.DESTROY_STAGE_TILE + 9));
        int px = TextureAtlas.TILE_PX * TextureAtlas.TILE_PX;
        assertTrue(stage0 > 6, "first crack stage should already be visible, got " + stage0);
        assertTrue(stage0 < px / 2, "early stage must not fill the tile");
        assertTrue(stage9 > stage0 * 2, "later stages add more cracks, not a different pattern");
        assertTrue(stage9 < px, "destroy overlay stays a crack, not a solid cube");
    }

    @Test
    void earlyCrackPathsRemainPresentInLaterPreThickeningStages() {
        BufferedImage image = atlas();
        BufferedImage stage0 = tile(image, TextureAtlas.DESTROY_STAGE_TILE);
        BufferedImage stage4 = tile(image, TextureAtlas.DESTROY_STAGE_TILE + 4);
        for (int y = 0; y < TextureAtlas.TILE_PX; y++) {
            for (int x = 0; x < TextureAtlas.TILE_PX; x++) {
                if (((stage0.getRGB(x, y) >>> 24) & 0xFF) != 0) {
                    assertEquals(stage0.getRGB(x, y), stage4.getRGB(x, y),
                            "existing crack pixel changed at " + x + "," + y);
                }
            }
        }
    }

    private static int countOpaque(BufferedImage tile) {
        int n = 0, px = TextureAtlas.TILE_PX;
        for (int y = 0; y < px; y++) {
            for (int x = 0; x < px; x++) {
                if (((tile.getRGB(x, y) >> 24) & 0xFF) > 16) n++;
            }
        }
        return n;
    }

    private static boolean isBrass(int r, int g, int b) {
        return r > 150 && g > 90 && r > g && g > b + 20 && b < 140;
    }

    private static boolean isDark(int argb) {
        int rgb = argb & 0xFFFFFF;
        int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
        return r + g + b < 160;
    }

    private static long signature(BufferedImage t) {
        long sumR = 0, sumG = 0, sumB = 0;
        int px = TextureAtlas.TILE_PX;
        for (int y = 0; y < px; y++) {
            for (int x = 0; x < px; x++) {
                int rgb = t.getRGB(x, y) & 0xFFFFFF;
                sumR += (rgb >> 16) & 0xFF;
                sumG += (rgb >> 8) & 0xFF;
                sumB += rgb & 0xFF;
            }
        }
        return (sumR << 32) ^ (sumG << 16) ^ sumB;
    }

    private static int distinctColors(BufferedImage t) {
        Set<Integer> colors = new HashSet<>();
        int px = TextureAtlas.TILE_PX;
        for (int y = 0; y < px; y++) {
            for (int x = 0; x < px; x++) {
                colors.add(t.getRGB(x, y) & 0xFFFFFF);
            }
        }
        return colors.size();
    }

    private static int countWhere(BufferedImage tile, ChannelPred pred) {
        int n = 0, px = TextureAtlas.TILE_PX;
        for (int y = 0; y < px; y++) {
            for (int x = 0; x < px; x++) {
                int rgb = tile.getRGB(x, y) & 0xFFFFFF;
                if (pred.test((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF)) n++;
            }
        }
        return n;
    }

    @FunctionalInterface
    private interface ChannelPred {
        boolean test(int r, int g, int b);
    }
}
