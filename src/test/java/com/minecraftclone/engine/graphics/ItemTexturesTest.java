package com.minecraftclone.engine.graphics;

import com.minecraftclone.world.BlockType;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/** Every inventory item must have a procedural icon, or startup throws. */
class ItemTexturesTest {

    @Test
    void everyItemHasATexture() {
        Random rnd = new Random(1);
        for (BlockType type : BlockType.values()) {
            if (!type.isItem) continue;
            BufferedImage img = assertDoesNotThrow(() -> ItemTextures.paint(type, rnd), type.name());
            assertNotNull(img, type.name());
        }
    }

    @Test
    void purifiedOreIsABrighterCrushedOre() {
        Random rnd = new Random(1);
        BufferedImage crushed = ItemTextures.paint(BlockType.CRUSHED_COPPER, rnd);
        BufferedImage purified = ItemTextures.paint(BlockType.PURIFIED_COPPER, rnd);
        long crushedSum = 0, purifiedSum = 0;
        for (int y = 0; y < crushed.getHeight(); y++) {
            for (int x = 0; x < crushed.getWidth(); x++) {
                int a = crushed.getRGB(x, y), b = purified.getRGB(x, y);
                assertEquals(a >>> 24 != 0, b >>> 24 != 0, "same silhouette at " + x + "," + y);
                crushedSum += (a >> 16 & 0xFF) + (a >> 8 & 0xFF) + (a & 0xFF);
                purifiedSum += (b >> 16 & 0xFF) + (b >> 8 & 0xFF) + (b & 0xFF);
            }
        }
        assertTrue(purifiedSum > crushedSum);
    }
}
