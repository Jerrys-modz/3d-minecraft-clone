package com.minecraftclone.engine.graphics;

import com.minecraftclone.world.BlockType;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.EnumMap;
import java.util.Map;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.GL_TEXTURE0;
import static org.lwjgl.opengl.GL13.glActiveTexture;

/**
 * Loads each inventory-only item's ({@link BlockType#isItem}) individual
 * 16x16 PNG - committed under {@code src/main/resources/items/}, produced
 * once by {@link com.minecraftclone.tools.GenerateItemTextures} - from the
 * classpath and uploads it as its own small GL texture.
 * <p>
 * Unlike blocks, items are never batched into one chunk-mesh draw call, so
 * there's no benefit to a shared sheet - each item gets bound and drawn
 * individually, and a real per-item file on disk is easier to inspect or
 * hand-edit than a tile baked into a larger procedural image.
 */
public class ItemTextures {

    private final Map<BlockType, Integer> textureIds = new EnumMap<>(BlockType.class);

    /** Loads (and uploads to the GPU) the texture for every item-type {@link BlockType}. */
    public void generate() {
        for (BlockType type : BlockType.values()) {
            if (!type.isItem) continue;
            textureIds.put(type, GLTexture.upload(load(type)));
        }
    }

    private BufferedImage load(BlockType type) {
        String resource = "/items/" + type.name().toLowerCase() + ".png";
        try (InputStream in = ItemTextures.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IOException("Item texture not found on classpath: " + resource);
            }
            return ImageIO.read(in);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load item texture: " + resource, e);
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
