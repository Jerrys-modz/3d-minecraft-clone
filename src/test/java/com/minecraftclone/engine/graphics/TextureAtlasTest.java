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

    private static BufferedImage waterTile() {
        BufferedImage atlas = new TextureAtlas().buildImage();
        int tile = 6; // BlockType.WATER's topTile/sideTile
        int px = TextureAtlas.TILE_PX;
        int ox = (tile % TextureAtlas.GRID) * px;
        int oy = (tile / TextureAtlas.GRID) * px;
        return atlas.getSubimage(ox, oy, px, px);
    }

    @Test
    void buildImageProducesAFullSizedAtlas() {
        BufferedImage atlas = new TextureAtlas().buildImage();
        assertEquals(TextureAtlas.ATLAS_PX, atlas.getWidth());
        assertEquals(TextureAtlas.ATLAS_PX, atlas.getHeight());
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
}
