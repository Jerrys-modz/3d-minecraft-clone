package com.minecraftclone.engine.graphics;

import com.minecraftclone.util.FloatArray;
import com.minecraftclone.util.IntArray;
import com.minecraftclone.world.ArrowEntity;
import com.minecraftclone.world.Mob;

import java.util.List;

import static org.lwjgl.opengl.GL11.*;

/**
 * Draws {@link Mob}s as small 3D voxel animals - each one a handful of textured
 * boxes (body, head, four legs) using the same interleaved vertex format and
 * baked directional shading as the world's chunk mesh (position, uv, light,
 * blockLight, fluidFlow), so they render as solid creatures that sit in the
 * blocky scene rather than flat camera-facing sprites. The boxes are rotated to
 * face the mob's heading, and all mobs of the same kind are batched into a
 * single mesh (one draw call per kind). Faces map onto per-kind skin regions
 * (see {@link MobTextures}); the head's front face uses the "face" region with
 * eyes/snout, and the body top uses the lighter top region.
 */
public class MobRenderer {

    private static final float LIGHT_TOP = 1.0f;
    private static final float LIGHT_BOTTOM = 0.4f;
    private static final float LIGHT_EW = 0.6f;
    private static final float LIGHT_NS = 0.75f;

    /** A box body part, in local coords with the mob's feet at y=0 and facing +z. */
    private static final class Part {
        final float hw, hh, hd;      // half extents
        final float cx, cy, cz;      // center offset from the feet
        final float[][] faces;       // skin region per face: TOP, BOTTOM, +X, -X, +Z, -Z

        Part(float hw, float hh, float hd, float cx, float cy, float cz, float[][] faces) {
            this.hw = hw;
            this.hh = hh;
            this.hd = hd;
            this.cx = cx;
            this.cy = cy;
            this.cz = cz;
            this.faces = faces;
        }
    }

    private static final float[][] BODY_FACES = {
            MobTextures.BODY_TOP, MobTextures.BODY, MobTextures.BODY, MobTextures.BODY, MobTextures.BODY, MobTextures.BODY};
    private static final float[][] HEAD_FACES = {
            MobTextures.HEAD_SIDE, MobTextures.HEAD_SIDE, MobTextures.HEAD_SIDE,
            MobTextures.HEAD_SIDE, MobTextures.HEAD_FRONT, MobTextures.HEAD_SIDE};
    private static final float[][] LEG_FACES = {
            MobTextures.LEG, MobTextures.LEG, MobTextures.LEG, MobTextures.LEG, MobTextures.LEG, MobTextures.LEG};

    private static Part[] parts(Mob.Type type) {
        return switch (type) {
            case PIG -> new Part[]{
                    new Part(0.42f, 0.23f, 0.27f, 0f, 0.42f, 0f, BODY_FACES),   // body
                    new Part(0.22f, 0.17f, 0.19f, 0f, 0.70f, 0.30f, HEAD_FACES), // head
                    new Part(0.07f, 0.20f, 0.07f, -0.27f, 0.20f, -0.16f, LEG_FACES),
                    new Part(0.07f, 0.20f, 0.07f, 0.27f, 0.20f, -0.16f, LEG_FACES),
                    new Part(0.07f, 0.20f, 0.07f, -0.27f, 0.20f, 0.16f, LEG_FACES),
                    new Part(0.07f, 0.20f, 0.07f, 0.27f, 0.20f, 0.16f, LEG_FACES),
            };
            case COW -> new Part[]{
                    new Part(0.47f, 0.25f, 0.32f, 0f, 0.50f, 0f, BODY_FACES),
                    new Part(0.24f, 0.19f, 0.21f, 0f, 0.80f, 0.35f, HEAD_FACES),
                    new Part(0.08f, 0.225f, 0.08f, -0.32f, 0.225f, -0.20f, LEG_FACES),
                    new Part(0.08f, 0.225f, 0.08f, 0.32f, 0.225f, -0.20f, LEG_FACES),
                    new Part(0.08f, 0.225f, 0.08f, -0.32f, 0.225f, 0.20f, LEG_FACES),
                    new Part(0.08f, 0.225f, 0.08f, 0.32f, 0.225f, 0.20f, LEG_FACES),
            };
            case SHEEP -> new Part[]{
                    new Part(0.42f, 0.27f, 0.29f, 0f, 0.50f, 0f, BODY_FACES),
                    new Part(0.19f, 0.17f, 0.17f, 0f, 0.72f, 0.32f, HEAD_FACES),
                    new Part(0.065f, 0.20f, 0.065f, -0.28f, 0.20f, -0.18f, LEG_FACES),
                    new Part(0.065f, 0.20f, 0.065f, 0.28f, 0.20f, -0.18f, LEG_FACES),
                    new Part(0.065f, 0.20f, 0.065f, -0.28f, 0.20f, 0.18f, LEG_FACES),
                    new Part(0.065f, 0.20f, 0.065f, 0.28f, 0.20f, 0.18f, LEG_FACES),
            };
            case WOLF -> new Part[]{ // a lean, low-slung quadruped
                    new Part(0.36f, 0.20f, 0.26f, 0f, 0.44f, 0f, BODY_FACES),
                    new Part(0.20f, 0.15f, 0.18f, 0f, 0.68f, 0.30f, HEAD_FACES),
                    new Part(0.06f, 0.20f, 0.06f, -0.24f, 0.20f, -0.15f, LEG_FACES),
                    new Part(0.06f, 0.20f, 0.06f, 0.24f, 0.20f, -0.15f, LEG_FACES),
                    new Part(0.06f, 0.20f, 0.06f, -0.24f, 0.20f, 0.15f, LEG_FACES),
                    new Part(0.06f, 0.20f, 0.06f, 0.24f, 0.20f, 0.15f, LEG_FACES),
            };
            case POLAR_BEAR -> new Part[]{ // a big, heavy quadruped
                    new Part(0.48f, 0.30f, 0.34f, 0f, 0.52f, 0f, BODY_FACES),
                    new Part(0.26f, 0.20f, 0.23f, 0f, 0.78f, 0.34f, HEAD_FACES),
                    new Part(0.09f, 0.22f, 0.09f, -0.30f, 0.22f, -0.20f, LEG_FACES),
                    new Part(0.09f, 0.22f, 0.09f, 0.30f, 0.22f, -0.20f, LEG_FACES),
                    new Part(0.09f, 0.22f, 0.09f, -0.30f, 0.22f, 0.20f, LEG_FACES),
                    new Part(0.09f, 0.22f, 0.09f, 0.30f, 0.22f, 0.20f, LEG_FACES),
            };
            case ZOMBIE, SKELETON -> new Part[]{ // humanoid: body, head, two arms, two legs
                    new Part(0.26f, 0.35f, 0.13f, 0f, 0.85f, 0f, BODY_FACES),
                    new Part(0.23f, 0.23f, 0.23f, 0f, 1.40f, 0f, HEAD_FACES),
                    new Part(0.07f, 0.30f, 0.07f, -0.32f, 0.90f, 0f, LEG_FACES),
                    new Part(0.07f, 0.30f, 0.07f, 0.32f, 0.90f, 0f, LEG_FACES),
                    new Part(0.09f, 0.30f, 0.09f, -0.13f, 0.30f, 0f, LEG_FACES),
                    new Part(0.09f, 0.30f, 0.09f, 0.13f, 0.30f, 0f, LEG_FACES),
            };
            case SPIDER -> new Part[]{ // wide low body + small head + 4 visible legs (2 per side)
                    new Part(0.60f, 0.22f, 0.40f, 0f, 0.40f, 0f, BODY_FACES),
                    new Part(0.22f, 0.20f, 0.20f, 0f, 0.55f, 0.32f, HEAD_FACES),
                    // left legs — outboard of the body half-width (±0.60)
                    new Part(0.22f, 0.05f, 0.05f, -0.78f, 0.34f, -0.22f, LEG_FACES),
                    new Part(0.22f, 0.05f, 0.05f, -0.78f, 0.30f, 0.22f, LEG_FACES),
                    // right legs
                    new Part(0.22f, 0.05f, 0.05f, 0.78f, 0.34f, -0.22f, LEG_FACES),
                    new Part(0.22f, 0.05f, 0.05f, 0.78f, 0.30f, 0.22f, LEG_FACES),
            };
            case CREEPER -> new Part[]{ // four-legged blocky body with square head
                    new Part(0.24f, 0.36f, 0.18f, 0f, 0.72f, 0f, BODY_FACES),
                    new Part(0.22f, 0.22f, 0.22f, 0f, 1.20f, 0f, HEAD_FACES),
                    new Part(0.08f, 0.22f, 0.08f, -0.10f, 0.22f, -0.12f, LEG_FACES),
                    new Part(0.08f, 0.22f, 0.08f, 0.10f, 0.22f, -0.12f, LEG_FACES),
                    new Part(0.08f, 0.22f, 0.08f, -0.10f, 0.22f, 0.12f, LEG_FACES),
                    new Part(0.08f, 0.22f, 0.08f, 0.10f, 0.22f, 0.12f, LEG_FACES),
            };
        };
    }

    private final Mesh mesh = new Mesh();

    /** Draws every mob as a batched set of 3D boxes, plus skeleton arrows. Caller must have the chunk shader bound. */
    public void render(MobTextures textures, List<Mob> mobs, List<ArrowEntity> arrows) {
        if (mobs.isEmpty() && arrows.isEmpty()) return;

        for (Mob.Type type : Mob.Type.values()) {
            int count = 0;
            for (Mob m : mobs) {
                if (m.type == type) count++;
            }
            if (count == 0) continue;

            Part[] parts = parts(type);
            FloatArray v = new FloatArray(count * parts.length * 192); // 6 faces * 4 verts * 8 floats
            IntArray i = new IntArray(count * parts.length * 36);      // 6 faces * 6 indices
            int[] c = {0};
            for (Mob m : mobs) {
                if (m.type == type) {
                    for (Part part : parts) {
                        emitBox(v, i, c, m, part);
                    }
                }
            }
            mesh.upload(v.toArray(), i.toArray());
            textures.bind(type);
            mesh.render();
        }

        if (!arrows.isEmpty()) {
            // Arrows: small crossed billboards (visible from any angle), one pass.
            FloatArray v = new FloatArray(arrows.size() * 64);
            IntArray i = new IntArray(arrows.size() * 12);
            int[] c = {0};
            for (ArrowEntity a : arrows) {
                emitArrow(v, i, c, a);
            }
            glDisable(GL_CULL_FACE);
            mesh.upload(v.toArray(), i.toArray());
            textures.bindArrow();
            mesh.render();
            glEnable(GL_CULL_FACE);
        }
    }

    /** Emits an arrow as two crossed planes (an X), like the game's cross decorations. */
    private void emitArrow(FloatArray v, IntArray i, int[] counter, ArrowEntity a) {
        float s = 0.13f;
        float cx = a.position.x, cy = a.position.y, cz = a.position.z;
        float[][][] planes = {
                {{cx - s, cy - s, cz - s}, {cx + s, cy - s, cz + s}, {cx + s, cy + s, cz + s}, {cx - s, cy + s, cz - s}},
                {{cx - s, cy - s, cz + s}, {cx + s, cy - s, cz - s}, {cx + s, cy + s, cz - s}, {cx - s, cy + s, cz + s}},
        };
        int base = counter[0];
        for (float[][] p : planes) {
            for (int k = 0; k < 4; k++) {
                v.add(p[k][0]);
                v.add(p[k][1]);
                v.add(p[k][2]);
                v.add(k == 1 || k == 2 ? 1f : 0f);
                v.add(k == 0 || k == 1 ? 1f : 0f);
                v.add(1f);
                v.add(0f);  // blockLight
                v.add(0f);  // fluidFlow
                v.add(0f);
                v.add(0f);  // flowDir
            }
            i.add(base);
            i.add(base + 1);
            i.add(base + 2);
            i.add(base);
            i.add(base + 2);
            i.add(base + 3);
            base += 4;
        }
        counter[0] += 8;
    }

    private void emitBox(FloatArray v, IntArray i, int[] counter, Mob mob, Part part) {
        float yaw = mob.yaw;
        float cos = (float) Math.cos(yaw), sin = (float) Math.sin(yaw);
        float feetY = mob.position.y - mob.type.height / 2f;
        float x0 = part.cx - part.hw, x1 = part.cx + part.hw;
        float y0 = part.cy - part.hh, y1 = part.cy + part.hh;
        float z0 = part.cz - part.hd, z1 = part.cz + part.hd;

        // Local corners (x, y, z), wound to match the chunk mesh so back-face
        // culling leaves the outside visible.
        float[][] corners = {
                {x0, y0, z0}, {x1, y0, z0}, {x0, y0, z1}, {x1, y0, z1},
                {x0, y1, z0}, {x1, y1, z0}, {x0, y1, z1}, {x1, y1, z1},
        };
        int[][] faces = {{6, 7, 5, 4}, {0, 1, 3, 2}, {3, 1, 5, 7}, {0, 2, 6, 4}, {2, 3, 7, 6}, {1, 0, 4, 5}};
        float[] lights = {LIGHT_TOP, LIGHT_BOTTOM, LIGHT_EW, LIGHT_EW, LIGHT_NS, LIGHT_NS};

        for (int f = 0; f < 6; f++) {
            float[] region = part.faces[f];
            float u0 = region[0], v0 = region[1], u1 = region[2], v1 = region[3];
            int base = counter[0];
            for (int k = 0; k < 4; k++) {
                float[] corner = corners[faces[f][k]];
                // Rotate the corner's (x, z) offset by the mob's yaw around the feet axis.
                float wx = mob.position.x + corner[0] * cos + corner[2] * sin;
                float wz = mob.position.z - corner[0] * sin + corner[2] * cos;
                float wy = feetY + corner[1];
                v.add(wx);
                v.add(wy);
                v.add(wz);
                v.add(k == 1 || k == 2 ? u1 : u0);
                v.add(k == 0 || k == 1 ? v1 : v0);
                v.add(lights[f]);
                v.add(0f);  // blockLight
                v.add(0f);  // fluidFlow
                v.add(0f);
                v.add(0f);  // flowDir
            }
            i.add(base);
            i.add(base + 1);
            i.add(base + 2);
            i.add(base);
            i.add(base + 2);
            i.add(base + 3);
            counter[0] += 4;
        }
    }

    public void destroy() {
        mesh.destroy();
    }
}
