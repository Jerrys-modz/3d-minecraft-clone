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
 * (see {@link MobRenderer}) can map their faces onto it. The face gets four
 * whole rows (brow, eyes, nose, mouth) so it reads clearly once stretched
 * onto the 3D head cube, and the head side carries a profile detail too since
 * mobs are seen from every angle in-game.
 * <p>
 * Region layout (rows, top to bottom): body side (0-5), body top (6), head
 * side (7-9), head front / face (10-13), legs (14-15).
 * Following the project's "self-contained" rule, mob art is generated at
 * startup (like block textures) rather than shipped as image files.
 */
public class MobTextures {

    private static final int W = 16;
    private static final int H = 16;

    // Texture regions (u0, v0, u1, v1) - v increases downward, one row = 1/16.
    // Health-bar texture UV regions (separate 2x1 texture: left pixel = green fill, right = dark bg).
    /** Left pixel of the health-bar texture: bright green (health filled portion). */
    public static final float[] HEALTH_GREEN = {0f, 0f, 0.5f, 1f};
    /** Right pixel of the health-bar texture: dark background (health empty portion). */
    public static final float[] HEALTH_BG    = {0.5f, 0f, 1f, 1f};

    public static final float[] BODY = {0f, 0f, 1f, 6f / 16f};
    public static final float[] BODY_TOP = {0f, 6f / 16f, 1f, 7f / 16f};
    public static final float[] HEAD_SIDE = {0f, 7f / 16f, 1f, 10f / 16f};
    public static final float[] HEAD_FRONT = {0f, 10f / 16f, 1f, 14f / 16f};
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

    private static final int WOLF_BODY = 0x9A9A9A;
    private static final int WOLF_DARK = 0x6E6E6E;
    private static final int WOLF_LIGHT = 0xC6C6C6;
    private static final int WOLF_BELLY = 0xE0E0E0;

    private static final int BEAR_BODY = 0xE8E8E2;
    private static final int BEAR_DARK = 0xC8C8BE;
    private static final int BEAR_NOSE = 0x3A3A3A;

    private final Map<Mob.Type, Integer> textureIds = new EnumMap<>(Mob.Type.class);
    private int arrowTextureId = -1;
    private int playerTextureId = -1;
    /** 2×1 texture used to draw health bars above remote players: green | dark. */
    private int healthBarTexId = -1;

    /** Paints and uploads every mob type's skin, plus the skeleton-arrow sprite and the player skin. */
    public void generate() {
        for (Mob.Type type : Mob.Type.values()) {
            textureIds.put(type, GLTexture.upload(build(type)));
        }
        arrowTextureId = GLTexture.upload(buildArrow());
        playerTextureId = GLTexture.upload(buildPlayer());
        healthBarTexId = GLTexture.upload(buildHealthBar());
    }

    /**
     * Builds the 2×1 health-bar texture.
     * Left pixel (u < 0.5): bright green — the filled portion of the bar.
     * Right pixel (u ≥ 0.5): dark charcoal — the empty background.
     */
    private static BufferedImage buildHealthBar() {
        BufferedImage img = new BufferedImage(2, 1, BufferedImage.TYPE_INT_ARGB);
        img.setRGB(0, 0, 0xFF33CC33); // green fill
        img.setRGB(1, 0, 0xFF222222); // dark background
        return img;
    }

    /** Binds the health-bar texture to texture unit 0 (for rendering remote-player health bars). */
    public void bindHealthBar() {
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, healthBarTexId);
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
            case WOLF -> paintWolf(img, rnd);
            case POLAR_BEAR -> paintPolarBear(img, rnd);
            case SPIDER -> paintSpider(img, rnd);
            case CREEPER -> paintCreeper(img, rnd);
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

    /** Binds the player skin to texture unit 0 (for rendering remote players). */
    public void bindPlayer() {
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, playerTextureId);
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
        fill(img, 0, 15, 0, 5, PIG_BODY);          // body
        fill(img, 0, 15, 3, 5, PIG_BELLY);         // lighter belly
        speckle(img, rnd, 0, 15, 0, 5, PIG_DARK, 0.10f);
        fill(img, 0, 15, 6, 6, PIG_BELLY);         // body top
        // Head side: shaded pink with an eye in profile.
        for (int y = 7; y <= 9; y++) {
            for (int x = 0; x <= 15; x++) set(img, x, y, shade(PIG_BODY, 1f - 0.03f * (y - 7)));
        }
        set(img, 13, 8, EYE); set(img, 14, 8, EYE);
        // Head front: eyes, then a big snout with nostrils.
        for (int y = 10; y <= 13; y++) for (int x = 0; x <= 15; x++) set(img, x, y, PIG_BODY);
        set(img, 3, 11, EYE); set(img, 4, 11, EYE);
        set(img, 11, 11, EYE); set(img, 12, 11, EYE);
        fill(img, 5, 10, 12, 13, PIG_SNOUT);
        set(img, 7, 12, PIG_DARK); set(img, 8, 12, PIG_DARK);
        fill(img, 0, 15, 14, 15, PIG_DARK);        // legs
    }

    private void paintCow(BufferedImage img, Random rnd) {
        fill(img, 0, 15, 0, 5, COW_BODY);          // body
        speckle(img, rnd, 0, 15, 0, 5, COW_DARK, 0.12f);
        fill(img, 0, 15, 4, 5, COW_WHITE);         // white belly stripe
        fill(img, 4, 11, 0, 1, COW_WHITE);         // white patch on the side
        fill(img, 0, 15, 6, 6, COW_LIGHT);         // body top
        // Head side: brown with an eye in profile.
        for (int y = 7; y <= 9; y++) {
            for (int x = 0; x <= 15; x++) set(img, x, y, shade(COW_BODY, 1f - 0.03f * (y - 7)));
        }
        set(img, 13, 8, EYE); set(img, 14, 8, EYE);
        // Head front: white face, eyes, muzzle.
        for (int y = 10; y <= 13; y++) for (int x = 0; x <= 15; x++) set(img, x, y, COW_WHITE);
        set(img, 3, 11, EYE); set(img, 4, 11, EYE);
        set(img, 11, 11, EYE); set(img, 12, 11, EYE);
        fill(img, 6, 9, 12, 13, COW_MUZZLE);
        set(img, 7, 13, COW_DARK); set(img, 8, 13, COW_DARK); // nostrils
        fill(img, 0, 15, 14, 15, COW_HOOF);        // legs
    }

    private void paintSheep(BufferedImage img, Random rnd) {
        fill(img, 0, 15, 0, 5, WOOL);              // woolly body
        speckle(img, rnd, 0, 15, 0, 5, WOOL_SHADE, 0.25f);
        fill(img, 0, 15, 6, 6, WOOL_LIGHT);        // body top
        // Head side: dark face shading.
        for (int y = 7; y <= 9; y++) {
            for (int x = 0; x <= 15; x++) set(img, x, y, shade(FACE_DARK, 1f - 0.04f * (y - 7)));
        }
        set(img, 13, 8, EYE); set(img, 14, 8, EYE);
        // Head front: dark face with eyes.
        for (int y = 10; y <= 13; y++) for (int x = 0; x <= 15; x++) set(img, x, y, FACE_DARK);
        set(img, 4, 11, EYE); set(img, 11, 11, EYE);
        fill(img, 0, 15, 14, 15, FACE_DARK);       // legs
    }

    /** A lean grey wolf with a pale belly and muzzle - a forest predator. */
    private void paintWolf(BufferedImage img, Random rnd) {
        fill(img, 0, 15, 0, 5, WOLF_BODY);         // body
        speckle(img, rnd, 0, 15, 0, 5, WOLF_DARK, 0.15f);
        fill(img, 0, 15, 4, 5, WOLF_BELLY);        // pale belly
        fill(img, 0, 15, 6, 6, WOLF_LIGHT);        // body top
        // Head side: grey with a pale jaw and a dark ear line.
        for (int y = 7; y <= 9; y++) {
            for (int x = 0; x <= 15; x++) set(img, x, y, shade(WOLF_BODY, 1f - 0.03f * (y - 7)));
        }
        set(img, 13, 8, EYE); set(img, 14, 8, EYE);
        // Head front: eyes, pale snout, dark nose.
        for (int y = 10; y <= 13; y++) for (int x = 0; x <= 15; x++) set(img, x, y, WOLF_BODY);
        set(img, 4, 11, EYE); set(img, 11, 11, EYE);
        fill(img, 6, 9, 12, 13, WOLF_LIGHT);       // snout
        set(img, 7, 13, WOLF_DARK); set(img, 8, 13, WOLF_DARK);
        fill(img, 0, 15, 14, 15, WOLF_DARK);       // legs
    }

    /** A huge white polar bear with a dark muzzle - the king of the frozen wastes. */
    private void paintPolarBear(BufferedImage img, Random rnd) {
        fill(img, 0, 15, 0, 5, BEAR_BODY);         // body
        speckle(img, rnd, 0, 15, 0, 5, BEAR_DARK, 0.12f);
        fill(img, 0, 15, 6, 6, BEAR_BODY);         // body top
        // Head side: white with a dark eye.
        for (int y = 7; y <= 9; y++) {
            for (int x = 0; x <= 15; x++) set(img, x, y, shade(BEAR_BODY, 1f - 0.02f * (y - 7)));
        }
        set(img, 13, 8, EYE); set(img, 14, 8, EYE);
        // Head front: small eyes, big dark nose/muzzle.
        for (int y = 10; y <= 13; y++) for (int x = 0; x <= 15; x++) set(img, x, y, BEAR_BODY);
        set(img, 4, 11, EYE); set(img, 11, 11, EYE);
        fill(img, 5, 10, 12, 13, BEAR_NOSE);       // muzzle
        set(img, 6, 13, EYE); set(img, 7, 13, EYE); set(img, 8, 13, EYE); set(img, 9, 13, EYE);
        fill(img, 0, 15, 14, 15, BEAR_DARK);       // legs
    }

    /** A moldy zombie: a torn shirt over rotting flesh, with a proper 4-row face. */
    private void paintZombie(BufferedImage img, Random rnd) {
        int shirt = 0x4A6A5E, shirtDark = 0x395447;
        int skin = 0x6FA34E, skinDark = 0x3F5A2C, skinLight = 0x8CC270;
        int pants = 0x3E4E3E;
        // Body: a torn shirt over the shoulders, rotting skin below.
        for (int x = 0; x <= 15; x++) {
            set(img, x, 0, shade(shirt, 1f));
            set(img, x, 1, shade(shirt, 0.94f));
            set(img, x, 2, rnd.nextFloat() < 0.35f ? shade(skin, 0.9f) : shade(shirt, 0.88f));
        }
        for (int y = 3; y <= 5; y++) {
            float f = 1.05f - 0.06f * (y - 3);
            for (int x = 0; x <= 15; x++) {
                float roll = rnd.nextFloat();
                int c = shade(skin, f);
                if (roll < 0.12f) c = shade(skinDark, f);
                else if (roll < 0.20f) c = shade(skinLight, f);
                set(img, x, y, c);
            }
        }
        set(img, 7, 4, 0xFF7E3A3A); set(img, 8, 4, 0xFF7E3A3A);
        fill(img, 0, 15, 6, 6, shade(shirt, 1.06f)); // body top (shoulders)
        // Head side: shaded green with an eye in profile.
        for (int y = 7; y <= 9; y++) {
            for (int x = 0; x <= 15; x++) {
                float f = 1.04f - 0.05f * (x / 15f) - 0.04f * (y - 7);
                set(img, x, y, shade(skin, f));
            }
        }
        set(img, 13, 8, 0x1E2620); set(img, 14, 8, 0x24301C);
        // Head front (4 rows): brow, sunken eyes, nose, gaping mouth.
        for (int y = 10; y <= 13; y++) {
            for (int x = 0; x <= 15; x++) set(img, x, y, shade(skin, y == 10 ? 0.95f : y == 13 ? 0.9f : 1f));
        }
        set(img, 7, 10, shade(skinDark, 1f)); set(img, 8, 10, shade(skinDark, 1f)); // brow
        fill(img, 2, 4, 11, 11, 0x1E2620);          // left eye socket
        fill(img, 11, 13, 11, 11, 0x1E2620);        // right eye socket
        set(img, 2, 12, 0x9FB87E); set(img, 13, 12, 0x9FB87E); // dull glint
        set(img, 7, 12, shade(skinDark, 1f)); set(img, 8, 12, shade(skinDark, 1f)); // nose
        fill(img, 5, 10, 13, 13, 0x24301C);         // gaping mouth
        set(img, 6, 13, 0xE8E0D0); set(img, 9, 13, 0xE8E0D0); // a couple of teeth
        // Legs: tattered pants.
        for (int y = 14; y <= 15; y++) {
            for (int x = 0; x <= 15; x++) {
                int c = shade(pants, 1f - 0.05f * (y - 14));
                if (rnd.nextFloat() < 0.12f) c = shade(skinDark, 1f);
                set(img, x, y, c);
            }
        }
    }

    /** The remote-player skin: a classic Steve-like skin in the same 16x16 layout as the mob skins. */
    private static BufferedImage buildPlayer() {
        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        int shirt = 0x3E6EB5, shirtDark = 0x2F5490, shirtLight = 0x4A7FC4;
        int pants = 0x4A4A6A, pantsDark = 0x36364F;
        int skin = 0xC8A67C, skinDark = 0xA88A64, skinLight = 0xD8BA92;
        // Body: a blue shirt.
        fill(img, 0, 15, 0, 5, shirt);
        fill(img, 0, 15, 0, 0, shirtLight);
        fill(img, 0, 15, 5, 5, shirtDark);
        fill(img, 0, 15, 6, 6, shirtLight); // body top (shoulders)
        // Head side: skin with an eye in profile.
        for (int y = 7; y <= 9; y++) {
            for (int x = 0; x <= 15; x++) {
                float f = 1.04f - 0.05f * (x / 15f) - 0.04f * (y - 7);
                set(img, x, y, shade(skin, f));
            }
        }
        set(img, 13, 8, 0x2A1E12); set(img, 14, 8, 0x2A1E12); // eye
        set(img, 12, 8, shade(skinDark, 1f));                 // nose
        // Head front (4 rows): hair, eyes, nose, mouth.
        fill(img, 0, 15, 10, 10, 0x3A2A18);              // hairline
        for (int y = 11; y <= 13; y++) {
            for (int x = 0; x <= 15; x++) set(img, x, y, shade(skin, 1f));
        }
        set(img, 7, 11, 0x2A1E12); set(img, 8, 11, 0x2A1E12); // brow
        fill(img, 2, 4, 11, 12, 0xFFFFFFFF);             // eye whites
        fill(img, 11, 13, 11, 12, 0xFFFFFFFF);
        fill(img, 3, 4, 11, 11, 0x3A6EA8);               // blue irises
        fill(img, 12, 13, 11, 11, 0x3A6EA8);
        fill(img, 3, 4, 12, 12, 0x1A1A1A);               // pupils
        fill(img, 12, 13, 12, 12, 0x1A1A1A);
        set(img, 7, 12, shade(skinDark, 1f)); set(img, 8, 12, shade(skinDark, 1f)); // nose
        set(img, 6, 13, shade(skinDark, 0.9f)); set(img, 7, 13, shade(skinDark, 0.9f));
        set(img, 8, 13, shade(skinDark, 0.9f)); set(img, 9, 13, shade(skinDark, 0.9f)); // mouth
        // Legs: blue-grey pants.
        fill(img, 0, 15, 14, 15, pants);
        fill(img, 0, 15, 14, 14, pantsDark);
        return img;
    }

    /** A skeleton: bone-white torso with a ribcage, and a full skull face. */
    private void paintSkeleton(BufferedImage img, Random rnd) {
        int bone = 0xE8E0D0, boneDark = 0xB0A68F, boneLight = 0xF6F0E4;
        // Body: bone shading with a dark spine running down the middle.
        for (int y = 0; y <= 5; y++) {
            float f = 1.05f - 0.05f * y;
            for (int x = 0; x <= 15; x++) {
                int c = shade(bone, f);
                if (x == 7 || x == 8) c = shade(boneDark, f * 0.9f);
                set(img, x, y, c);
            }
        }
        for (int ry = 2; ry <= 4; ry += 2) {
            for (int x = 3; x <= 12; x++) {
                if (x >= 6 && x <= 9) continue;
                set(img, x, ry, shade(boneDark, 1f));
                if (ry == 2) set(img, x, 1, shade(boneDark, 0.85f));
            }
        }
        for (int x = 4; x <= 11; x++) {
            if (x >= 7 && x <= 8) continue;
            set(img, x, 4, shade(boneDark, 1f)); // pelvis
        }
        fill(img, 0, 15, 6, 6, shade(boneLight, 1f)); // body top
        // Head side: skull profile with an eye socket near the front.
        for (int y = 7; y <= 9; y++) {
            for (int x = 0; x <= 15; x++) {
                float f = 1f - 0.04f * (y - 7) - 0.05f * (x / 15f);
                set(img, x, y, shade(bone, f));
            }
        }
        set(img, 13, 8, 0x1C1C22); set(img, 14, 8, 0x1C1C22);
        set(img, 12, 9, 0xC8C0AC);
        // Head front (4 rows): brow, deep sockets, nasal cavity, teeth.
        for (int y = 10; y <= 13; y++) {
            for (int x = 0; x <= 15; x++) set(img, x, y, shade(bone, y == 10 ? 0.98f : 1f));
        }
        set(img, 6, 10, 0xC8C0AC); set(img, 7, 10, 0xC8C0AC);
        set(img, 8, 10, 0xC8C0AC); set(img, 9, 10, 0xC8C0AC); // brow highlight
        fill(img, 2, 4, 11, 12, 0x1C1C22);          // left eye socket
        fill(img, 11, 13, 11, 12, 0x1C1C22);        // right eye socket
        set(img, 7, 12, 0x1C1C22); set(img, 8, 12, 0x1C1C22); // nasal cavity
        for (int x = 4; x <= 11; x++) {
            set(img, x, 13, (x % 2 == 0) ? 0x1C1C22 : bone); // teeth row
        }
        // Legs: bone with joint shading.
        for (int y = 14; y <= 15; y++) {
            for (int x = 0; x <= 15; x++) set(img, x, y, shade(boneDark, 1f - 0.06f * (y - 14)));
        }
    }

    /** A dark spider with red eyes - a wide, fast nocturnal hunter. */
    private void paintSpider(BufferedImage img, Random rnd) {
        int body = 0x2A2A2E, bodyDark = 0x1A1A1E, bodyLight = 0x3A3A42;
        int redEye = 0xC81A1A;
        // Body: dark grey/black with subtle shading.
        for (int y = 0; y <= 5; y++) {
            float f = 1.02f - 0.04f * (y / 5f);
            for (int x = 0; x <= 15; x++) {
                int c = shade(body, f);
                if (rnd.nextFloat() < 0.08f) c = shade(bodyDark, f);
                set(img, x, y, c);
            }
        }
        fill(img, 0, 15, 6, 6, shade(bodyLight, 1f)); // body top
        // Head side: dark with a red eye in profile.
        for (int y = 7; y <= 9; y++) {
            for (int x = 0; x <= 15; x++) set(img, x, y, shade(body, 1f - 0.03f * (y - 7)));
        }
        set(img, 13, 8, redEye); set(img, 14, 8, redEye);
        // Head front: multiple red eyes (spider has 8 eyes).
        for (int y = 10; y <= 13; y++) for (int x = 0; x <= 15; x++) set(img, x, y, body);
        set(img, 2, 11, redEye); set(img, 3, 11, redEye);
        set(img, 6, 11, redEye); set(img, 9, 11, redEye);
        set(img, 12, 11, redEye); set(img, 13, 11, redEye);
        set(img, 4, 12, redEye); set(img, 11, 12, redEye);
        fill(img, 0, 15, 14, 15, bodyDark); // legs
    }

    /** A green creeper with a dark face pattern - explodes when close. */
    private void paintCreeper(BufferedImage img, Random rnd) {
        int green = 0x0DA70B, greenDark = 0x087A07, greenLight = 0x12C910;
        int facePattern = 0x054705;
        // Body: mottled green.
        for (int y = 0; y <= 5; y++) {
            float f = 1.03f - 0.05f * (y / 5f);
            for (int x = 0; x <= 15; x++) {
                int c = shade(green, f);
                if (rnd.nextFloat() < 0.10f) c = shade(greenDark, f);
                else if (rnd.nextFloat() < 0.05f) c = shade(greenLight, f);
                set(img, x, y, c);
            }
        }
        fill(img, 0, 15, 6, 6, shade(greenLight, 1f)); // body top
        // Head side: green shading.
        for (int y = 7; y <= 9; y++) {
            for (int x = 0; x <= 15; x++) set(img, x, y, shade(green, 1f - 0.03f * (y - 7)));
        }
        // Head front: the iconic creeper face pattern (dark eyes and mouth).
        for (int y = 10; y <= 13; y++) for (int x = 0; x <= 15; x++) set(img, x, y, green);
        fill(img, 3, 5, 10, 11, facePattern);   // left eye
        fill(img, 10, 12, 10, 11, facePattern); // right eye
        fill(img, 6, 9, 12, 12, facePattern);   // nose
        fill(img, 5, 6, 13, 13, facePattern);   // mouth left
        fill(img, 9, 10, 13, 13, facePattern);  // mouth right
        fill(img, 0, 15, 14, 15, greenDark);    // legs
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

    /** Multiplies a 0xRRGGBB color's brightness by {@code f}. */
    private static int shade(int color, float f) {
        int r = Math.min(255, Math.max(0, Math.round(((color >> 16) & 0xFF) * f)));
        int g = Math.min(255, Math.max(0, Math.round(((color >> 8) & 0xFF) * f)));
        int b = Math.min(255, Math.max(0, Math.round((color & 0xFF) * f)));
        return (r << 16) | (g << 8) | b;
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
        if (playerTextureId != -1) {
            glDeleteTextures(playerTextureId);
        }
        if (healthBarTexId != -1) {
            glDeleteTextures(healthBarTexId);
        }
    }
}
