package com.minecraftclone.engine.graphics;

import com.minecraftclone.world.Mob;

import java.awt.image.BufferedImage;
import java.util.EnumMap;
import java.util.Map;
import java.util.Random;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.GL_TEXTURE0;
import static org.lwjgl.opengl.GL13.glActiveTexture;

/**
 * Procedurally paints each {@link Mob.Type}'s sprite - a small front-facing
 * pixel-art animal - onto its own GL texture, one per mob kind. Following the
 * project's "self-contained" rule, mob art is generated at startup (like block
 * textures) rather than shipped as image files. Mobs are billboards, so each
 * sprite is a single transparent 16x24 image; the renderer draws it as a
 * camera-facing quad.
 */
public class MobTextures {

    private static final int W = 16;
    private static final int H = 24;

    private static final int PIG_BODY = 0xF2A9B8;
    private static final int PIG_DARK = 0xD98A9C;
    private static final int PIG_BELLY = 0xF7C3CE;
    private static final int PIG_SNOUT = 0xE890A0;

    private static final int COW_BODY = 0x9C6B3C;
    private static final int COW_DARK = 0x7A4F28;
    private static final int COW_WHITE = 0xEFE8DA;
    private static final int COW_MUZZLE = 0xE8B0A0;
    private static final int COW_HORN = 0xD9C9A0;
    private static final int COW_HOOF = 0x3A2A1A;

    private static final int WOOL = 0xEDE8DC;
    private static final int WOOL_SHADE = 0xDDD4C4;
    private static final int FACE_DARK = 0x6A584A;
    private static final int EYE = 0x26262B;

    private final Map<Mob.Type, Integer> textureIds = new EnumMap<>(Mob.Type.class);

    /** Paints and uploads every mob type's sprite to its own GL texture. */
    public void generate() {
        for (Mob.Type type : Mob.Type.values()) {
            textureIds.put(type, GLTexture.upload(build(type)));
        }
    }

    private BufferedImage build(Mob.Type type) {
        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        switch (type) {
            case PIG -> paintPig(img);
            case COW -> paintCow(img);
            case SHEEP -> paintSheep(img);
        }
        return img;
    }

    /** Binds a mob type's sprite to texture unit 0. */
    public void bind(Mob.Type type) {
        Integer id = textureIds.get(type);
        if (id == null) {
            throw new IllegalArgumentException("No mob texture loaded for " + type);
        }
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, id);
    }

    private void paintPig(BufferedImage img) {
        fill(img, 3, 12, 9, 20, PIG_BODY);       // body
        fill(img, 5, 10, 15, 19, PIG_BELLY);     // lighter belly
        fill(img, 3, 4, 20, 23, PIG_DARK);       // legs
        fill(img, 6, 7, 20, 23, PIG_DARK);
        fill(img, 8, 9, 20, 23, PIG_DARK);
        fill(img, 11, 12, 20, 23, PIG_DARK);
        fill(img, 6, 9, 14, 17, PIG_SNOUT);      // snout
        set(img, 4, 8, PIG_DARK); set(img, 5, 8, PIG_DARK);   // ears
        set(img, 10, 8, PIG_DARK); set(img, 11, 8, PIG_DARK);
        set(img, 5, 12, EYE); set(img, 10, 12, EYE);          // eyes
        set(img, 7, 15, EYE); set(img, 8, 15, EYE);           // nostrils
    }

    private void paintCow(BufferedImage img) {
        fill(img, 2, 13, 9, 20, COW_BODY);       // body
        fill(img, 5, 10, 16, 19, COW_WHITE);     // white belly
        fill(img, 4, 11, 8, 12, COW_WHITE);      // white face patch
        fill(img, 2, 3, 20, 23, COW_HOOF);       // legs
        fill(img, 6, 7, 20, 23, COW_HOOF);
        fill(img, 9, 10, 20, 23, COW_HOOF);
        fill(img, 12, 13, 20, 23, COW_HOOF);
        set(img, 3, 8, COW_HORN); set(img, 4, 8, COW_HORN);   // horns
        set(img, 11, 8, COW_HORN); set(img, 12, 8, COW_HORN);
        fill(img, 6, 9, 13, 16, COW_MUZZLE);     // muzzle
        set(img, 5, 12, EYE); set(img, 10, 12, EYE);          // eyes
        set(img, 7, 15, EYE); set(img, 8, 15, EYE);           // nostrils
    }

    private void paintSheep(BufferedImage img) {
        Random rnd = new Random(987);
        for (int y = 7; y <= 19; y++) {          // woolly body with a soft speckle
            for (int x = 2; x <= 13; x++) {
                set(img, x, y, rnd.nextFloat() < 0.75f ? WOOL : WOOL_SHADE);
            }
        }
        fill(img, 3, 4, 20, 23, FACE_DARK);      // legs
        fill(img, 6, 7, 20, 23, FACE_DARK);
        fill(img, 8, 9, 20, 23, FACE_DARK);
        fill(img, 11, 12, 20, 23, FACE_DARK);
        fill(img, 5, 10, 13, 19, FACE_DARK);     // dark face
        set(img, 4, 12, FACE_DARK); set(img, 11, 12, FACE_DARK); // ears
        set(img, 6, 14, EYE); set(img, 9, 14, EYE);             // eyes
    }

    private static void set(BufferedImage img, int x, int y, int rgb) {
        if (x >= 0 && x < W && y >= 0 && y < H) {
            img.setRGB(x, y, 0xFF000000 | rgb);
        }
    }

    private static void fill(BufferedImage img, int x0, int x1, int y0, int y1, int rgb) {
        for (int y = y0; y <= y1; y++) {
            for (int x = x0; x <= x1; x++) {
                set(img, x, y, rgb);
            }
        }
    }

    public void destroy() {
        for (int id : textureIds.values()) {
            glDeleteTextures(id);
        }
    }
}
