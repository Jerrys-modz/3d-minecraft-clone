package com.minecraftclone.engine.graphics;

import com.minecraftclone.util.FloatArray;
import com.minecraftclone.util.IntArray;
import com.minecraftclone.world.RemotePlayer;

import java.util.List;

/**
 * Draws remote players (other humans connected to the same server) as simple
 * 3D humanoid figures - a body, head, two arms and two legs - using the same
 * interleaved vertex format and baked directional shading as the chunk mesh,
 * so they sit in the blocky scene like the mobs do. Each figure is rotated to
 * face its yaw and tilted slightly with its pitch so you can see where it's
 * looking. All remote players are batched into a single mesh (one draw call),
 * and positions are linearly interpolated between server updates so movement
 * looks smooth rather than stuttery.
 */
public class PlayerRenderer {

    private static final float LIGHT_TOP = 1.0f;
    private static final float LIGHT_BOTTOM = 0.4f;
    private static final float LIGHT_EW = 0.6f;
    private static final float LIGHT_NS = 0.75f;

    // Humanoid box parts, in local coords with the player's feet at y=0 facing +z.
    private static final float[][] BODY_UV = {MobTextures.BODY_TOP, MobTextures.BODY, MobTextures.BODY, MobTextures.BODY, MobTextures.BODY, MobTextures.BODY};
    private static final float[][] HEAD_UV = {
            MobTextures.HEAD_SIDE, MobTextures.HEAD_SIDE, MobTextures.HEAD_SIDE,
            MobTextures.HEAD_SIDE, MobTextures.HEAD_FRONT, MobTextures.HEAD_SIDE};
    private static final float[][] LIMB_UV = {MobTextures.LEG, MobTextures.LEG, MobTextures.LEG, MobTextures.LEG, MobTextures.LEG, MobTextures.LEG};

    /** Each part: halfWidth, halfHeight, halfDepth, centerX, centerY, centerZ, uv per face. */
    private static final float[][] PARTS = {
            {0.28f, 0.35f, 0.14f, 0f, 0.92f, 0f},    // body
            {0.24f, 0.24f, 0.24f, 0f, 1.52f, 0f},    // head
            {0.07f, 0.32f, 0.07f, -0.34f, 0.98f, 0f}, // left arm
            {0.07f, 0.32f, 0.07f, 0.34f, 0.98f, 0f},  // right arm
            {0.09f, 0.36f, 0.09f, -0.14f, 0.36f, 0f}, // left leg
            {0.09f, 0.36f, 0.09f, 0.14f, 0.36f, 0f},  // right leg
    };

    // Cache the per-part UV arrays (index 5 in each row is the "which region" selector).
    private static final float[][][] FACE_UV = {
            BODY_UV, HEAD_UV, LIMB_UV, LIMB_UV, LIMB_UV, LIMB_UV,
    };

    private final Mesh mesh = new Mesh();

    /** Draws every remote player as a batched set of 3D boxes. Caller must have the chunk shader bound. */
    public void render(MobTextures textures, List<RemotePlayer> players) {
        if (players.isEmpty()) return;

        FloatArray v = new FloatArray(players.size() * PARTS.length * 192);
        IntArray i = new IntArray(players.size() * PARTS.length * 36);
        int[] counter = {0};
        for (RemotePlayer p : players) {
            for (int pi = 0; pi < PARTS.length; pi++) {
                emitBox(v, i, counter, p, pi);
            }
        }
        mesh.upload(v.toArray(), i.toArray());
        textures.bindPlayer();
        mesh.render();
    }

    private void emitBox(FloatArray v, IntArray i, int[] counter, RemotePlayer p, int pi) {
        float[] part = PARTS[pi];
        float yaw = p.renderYaw;
        float pitchRad = (float) Math.toRadians(p.renderPitchDegrees);
        float cos = (float) Math.cos(yaw), sin = (float) Math.sin(yaw);
        float cosP = (float) Math.cos(pitchRad), sinP = (float) Math.sin(pitchRad);

        float hw = part[0], hh = part[1], hd = part[2];
        float x0 = part[3] - hw, x1 = part[3] + hw;
        float y0 = part[4] - hh, y1 = part[4] + hh;
        float z0 = part[5] - hd, z1 = part[5] + hd;

        float[][] corners = {
                {x0, y0, z0}, {x1, y0, z0}, {x0, y0, z1}, {x1, y0, z1},
                {x0, y1, z0}, {x1, y1, z0}, {x0, y1, z1}, {x1, y1, z1},
        };
        int[][] faces = {{6, 7, 5, 4}, {0, 1, 3, 2}, {3, 1, 5, 7}, {0, 2, 6, 4}, {2, 3, 7, 6}, {1, 0, 4, 5}};
        float[] lights = {LIGHT_TOP, LIGHT_BOTTOM, LIGHT_EW, LIGHT_EW, LIGHT_NS, LIGHT_NS};
        float[][] uvs = FACE_UV[pi];

        for (int f = 0; f < 6; f++) {
            float[] region = uvs[f];
            float u0 = region[0], v0 = region[1], u1 = region[2], v1 = region[3];
            int base = counter[0];
            for (int k = 0; k < 4; k++) {
                float[] corner = corners[faces[f][k]];
                // Yaw around the vertical axis, then tilt the figure forward/back
                // by pitch around the (left-right) X axis.
                float rx = corner[0] * cos + corner[2] * sin;
                float rz = -corner[0] * sin + corner[2] * cos;
                float ry = corner[1] * cosP - rz * sinP;
                float rz2 = corner[1] * sinP + rz * cosP;
                v.add(p.position.x + rx);
                v.add(p.position.y + ry);
                v.add(p.position.z + rz2);
                v.add(k == 1 || k == 2 ? u1 : u0);
                v.add(k == 0 || k == 1 ? v1 : v0);
                v.add(lights[f]);
                v.add(0f); // blockLight
                v.add(0f); // fluidFlow
                v.add(0f);
                v.add(0f); // flowDir
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
