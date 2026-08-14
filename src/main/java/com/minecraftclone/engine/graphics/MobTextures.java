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
 * Procedurally paints each {@link Mob.Type}'s skin - a single 16x16 texture per
 * kind, laid out in fixed horizontal regions so the 3D voxel body parts
 * (see {@link MobRenderer}) can map their faces onto it: body side, body top
 * (lighter), head side, head front (with the face: eyes/snout), and legs.
 * Following the project's "self-contained" rule, mob art is generated at
 * startup (like block textures) rather than shipped as image files.
 */
public class MobTextures {

    private static final int W = 16;
    private static final int H = 16;

    // Texture regions (u0, v0, u1, v1) - v increases downward, one row = 1/16.
    public static final float[] BODY = {0f, 0f, 1f, 8f / 16f};
    public static final float[] BODY_TOP = {0f, 8f / 16f, 1f, 10f / 16f};
    public static final float[] HEAD_SIDE = {0f, 10f / 16f, 1f, 12f / 16f};
    public static final float[] HEAD_FRONT = {0f, 12f / 16f, 1f, 14f / 16f};
    public static final float[] LEG = {0f, 14f / 16f, 1f, 1f};

    private static final int PIG_BODY = 0xF2A9B8;
    private static final int PIG_DARK = 0xD98A9C;
    private static final int PIG_BELLY = 0xF7C3CE;
    private static final int PIG_SNOUT = 0xE890A0;

    private static final int COW_BODY = 0x9C6B3C;
    private static final int COW_DARK = 0x7A4F28;
    private static final int COW_LIGHT = 0xB8865A;
    private static final int COW_WHITE = 0xEFE8DA;
    private static final int COW_MUZZLE = 0xE8B0A0;
    private static final int COW_HOOF = 0x3A2A1A;

    private static final int WOOL = 0xEDE8DC;
    private static final int WOOL_SHADE = 0xDDD4C4;
    private static final int WOOL_LIGHT = 0xF6F2EA;
    private static final int FACE_DARK = 0x6A584A;
    private static final int EYE = 0x26262B;

    private final Map<Mob.Type, Integer> textureIds = new EnumMap<>(Mob.Type.class);
    private int arrowTextureId = -1;

    /** Paints and uploads every mob type's skin, plus the skeleton-arrow sprite. */
    public void generate() {
        for (Mob.Type type : Mob.Type.values()) {
            textureIds.put(type, GLTexture.upload(build(type)));
        }
        arrowTextureId = GLTexture.upload(buildArrow());
    }

    private BufferedImage build(Mob.Type type) {
        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        Random rnd = new Random(987);
        switch (type) {
            case PIG -> paintPig(img, rnd);
            case COW -> paintCow(img, rnd);
            case SHEEP -> paintSheep(img, rnd);
            case ZOMBIE -> paintZombie(img, rnd);
            case SKELETON -> paintSkeleton(img, rnd);
        }
        return img;
    }

    /** Binds a mob type's skin to texture unit 0. */
    public void bind(Mob.Type type) {
        Integer id = textureIds.get(type);
        if (id == null) {
            throw new IllegalArgumentException("No mob texture loaded for " + type);
        }
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, id);
    }

    /** Binds the skeleton-arrow sprite to texture unit 0. */
    public void bindArrow() {
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, arrowTextureId);
    }

    /** A diagonal arrow sprite for skeleton projectiles: brown shaft, grey head, white fletching. */
    private static BufferedImage buildArrow() {
        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        for (int i = 0; i <= 10; i++) {
            img.setRGB(2 + i, 14 - i, 0xFF000000 | 0x8B5A2B);
        }
        for (int x = 11; x <= 13; x++) {
            img.setRGB(x, 14 - x, 0xFF000000 | 0xC9C9C9);
            img.setRGB(x + 1, 14 - x, 0xFF000000 | 0xC9C9C9);
        }
        img.setRGB(2, 14, 0xFF000000 | 0xE8E0D0);
        img.setRGB(3, 14, 0xFF000000 | 0xE8E0D0);
        img.setRGB(3, 13, 0xFF000000 | 0xE8E0D0);
        return img;
    }

    private void paintPig(BufferedImage img, Random rnd) {
        fill(img, 0, 15, 0, 7, PIG_BODY);          // body
        fill(img, 0, 15, 4, 7, PIG_BELLY);         // lighter belly
        speckle(img, rnd, 0, 15, 0, 7, PIG_DARK, 0.10f);
        fill(img, 0, 15, 8, 9, PIG_BELLY);         // body top
        fill(img, 0, 15, 10, 11, PIG_BODY);        // head side
        fill(img, 0, 15, 12, 13, PIG_BODY);        // head front
        set(img, 3, 12, EYE); set(img, 4, 12, EYE);          // eyes
        set(img, 11, 12, EYE); set(img, 12, 12, EYE);
        fill(img, 5, 10, 13, 13, PIG_SNOUT);       // snout
        fill(img, 0, 15, 14, 15, PIG_DARK);        // legs
    }

    private void paintCow(BufferedImage img, Random rnd) {
        fill(img, 0, 15, 0, 7, COW_BODY);          // body
        speckle(img, rnd, 0, 15, 0, 7, COW_DARK, 0.12f);
        fill(img, 0, 15, 6, 7, COW_WHITE);         // white belly stripe
        fill(img, 4, 11, 0, 1, COW_WHITE);         // white patch on the side
        fill(img, 0, 15, 8, 9, COW_LIGHT);         // body top
        fill(img, 0, 15, 10, 11, COW_BODY);        // head side
        fill(img, 0, 15, 12, 13, COW_WHITE);       // head front: white face
        set(img, 3, 12, EYE); set(img, 4, 12, EYE);          // eyes
        set(img, 11, 12, EYE); set(img, 12, 12, EYE);
        fill(img, 6, 9, 13, 13, COW_MUZZLE);       // muzzle
        fill(img, 0, 15, 14, 15, COW_HOOF);        // legs
    }

    private void paintSheep(BufferedImage img, Random rnd) {
        fill(img, 0, 15, 0, 7, WOOL);              // woolly body
        speckle(img, rnd, 0, 15, 0, 7, WOOL_SHADE, 0.25f);
        fill(img, 0, 15, 8, 9, WOOL_LIGHT);        // body top
        fill(img, 0, 15, 10, 11, FACE_DARK);       // head side
        fill(img, 0, 15, 12, 13, FACE_DARK);       // head front
        set(img, 4, 12, EYE); set(img, 11, 12, EYE);          // eyes
        fill(img, 0, 15, 14, 15, FACE_DARK);       // legs
    }

    private void paintZombie(BufferedImage img, Random rnd) {
        fill(img, 0, 15, 0, 7, 0x6FA34E);          // moldy green body
        speckle(img, rnd, 0, 15, 0, 7, 0x54803C, 0.15f);
        fill(img, 0, 15, 8, 9, 0x7FB85E);          // body top
        fill(img, 0, 15, 10, 11, 0x6FA34E);        // head side
        fill(img, 0, 15, 12, 13, 0x6FA34E);        // head front
        set(img, 3, 12, EYE); set(img, 4, 12, EYE);          // eyes
        set(img, 11, 12, EYE); set(img, 12, 12, EYE);
        fill(img, 5, 10, 13, 13, 0x4A6636);        // gaping mouth
        fill(img, 0, 15, 14, 15, 0x4E7337);        // legs
    }

    private void paintSkeleton(BufferedImage img, Random rnd) {
        fill(img, 0, 15, 0, 7, 0xE8E0D0);          // bone-white body
        speckle(img, rnd, 0, 15, 0, 7, 0xD8D0BE, 0.15f);
        fill(img, 0, 15, 8, 9, 0xF2ECE0);          // body top
        fill(img, 0, 15, 10, 11, 0xE8E0D0);        // head side
        fill(img, 0, 15, 12, 13, 0xE8E0D0);        // head front
        set(img, 3, 12, EYE); set(img, 4, 12, EYE);          // empty eye sockets
        set(img, 11, 12, EYE); set(img, 12, 12, EYE);
        fill(img, 0, 15, 14, 15, 0xD8D0BE);        // legs
    }

    /** Sprinkles a few {@code color} pixels through a region for a speckled, textured look. */
    private static void speckle(BufferedImage img, Random rnd, int x0, int x1, int y0, int y1, int color, float density) {
        for (int y = y0; y <= y1; y++) {
            for (int x = x0; x <= x1; x++) {
                if (rnd.nextFloat() < density) {
                    set(img, x, y, color);
                }
            }
        }
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
        if (arrowTextureId != -1) {
            glDeleteTextures(arrowTextureId);
        }
    }
}
