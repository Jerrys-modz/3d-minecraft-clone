package com.minecraftclone.engine.audio;

import com.minecraftclone.world.BlockType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SoundMaterialTest {

    @Test
    void nullMapsToDefault() {
        assertEquals(SoundMaterial.DEFAULT, SoundMaterial.of(null));
    }

    @Test
    void representativeBlocksMapToTheExpectedMaterial() {
        assertEquals(SoundMaterial.STONE, SoundMaterial.of(BlockType.STONE));
        assertEquals(SoundMaterial.STONE, SoundMaterial.of(BlockType.COAL_ORE));
        assertEquals(SoundMaterial.WOOD, SoundMaterial.of(BlockType.WOOD_LOG));
        assertEquals(SoundMaterial.WOOD, SoundMaterial.of(BlockType.PLANKS));
        assertEquals(SoundMaterial.WOOD, SoundMaterial.of(BlockType.DOOR));
        assertEquals(SoundMaterial.DIRT, SoundMaterial.of(BlockType.DIRT));
        assertEquals(SoundMaterial.DIRT, SoundMaterial.of(BlockType.GRASS));
        assertEquals(SoundMaterial.GRAVEL, SoundMaterial.of(BlockType.GRAVEL));
        assertEquals(SoundMaterial.SAND, SoundMaterial.of(BlockType.SAND));
        assertEquals(SoundMaterial.GLASS, SoundMaterial.of(BlockType.GLASS));
        assertEquals(SoundMaterial.LEAVES, SoundMaterial.of(BlockType.LEAVES));
        assertEquals(SoundMaterial.LEAVES, SoundMaterial.of(BlockType.TALL_GRASS));
    }

    @Test
    void everyBlockTypeMapsToSomeMaterialWithoutThrowing() {
        for (BlockType t : BlockType.values()) {
            SoundMaterial m = SoundMaterial.of(t);
            org.junit.jupiter.api.Assertions.assertNotNull(m);
        }
    }
}
