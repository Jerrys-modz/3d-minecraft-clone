package com.minecraftclone.engine.graphics;

import com.minecraftclone.engine.Shader;
import com.minecraftclone.util.FloatArray;
import com.minecraftclone.util.IntArray;
import org.joml.Vector3i;

import static org.lwjgl.opengl.GL11.*;

/**
 * Minecraft-style destroy-stage overlay: a slightly inflated cube whose
 * faces sample the atlas's accumulating crack tiles. Stage is derived from
 * hold-to-break progress (0 = just started, 9 = about to pop). Pure
 * {@link #stageIndex} is GPU-free for tests; {@link #render} needs GL.
 */
public class BreakOverlayRenderer {

    public static final int STAGES = TextureAtlas.DESTROY_STAGE_COUNT;
    private static final float INSET = 0.002f;

    private final Mesh mesh = new Mesh();
    private final FloatArray verts = new FloatArray(256);
    private final IntArray inds = new IntArray(64);

    /**
     * Destroy-stage tile index for a 0..1 break fraction, or {@code -1} when
     * the player is only looking at the block (no overlay).
     */
    public static int stageIndex(float breakFraction) {
        if (!(breakFraction > 0f)) return -1;
        int stage = (int) (breakFraction * STAGES);
        if (stage < 0) return -1;
        if (stage >= STAGES) return STAGES - 1;
        return stage;
    }

    /**
     * Draws the crack overlay on the targeted cell. {@code height} matches the
     * block outline (0.5 for slabs). No-ops when nothing is being mined.
     */
    public void render(Shader shader, TextureAtlas atlas, Vector3i blockPos, float height, float breakFraction) {
        int stage = stageIndex(breakFraction);
        if (stage < 0 || blockPos == null) return;
        float h = height <= 0f ? 1f : height;
        float[] uv = atlas.getUV(TextureAtlas.destroyStageTile(stage));
        verts.clear();
        inds.clear();
        int[] counter = {0};
        float x0 = blockPos.x - INSET, x1 = blockPos.x + 1f + INSET;
        float y0 = blockPos.y - INSET, y1 = blockPos.y + h + INSET;
        float z0 = blockPos.z - INSET, z1 = blockPos.z + 1f + INSET;
        addFace(uv, new float[][]{{x0, y1, z1}, {x1, y1, z1}, {x1, y1, z0}, {x0, y1, z0}}, counter); // top
        addFace(uv, new float[][]{{x0, y0, z0}, {x1, y0, z0}, {x1, y0, z1}, {x0, y0, z1}}, counter); // bottom
        addFace(uv, new float[][]{{x0, y0, z0}, {x0, y0, z1}, {x0, y1, z1}, {x0, y1, z0}}, counter); // west
        addFace(uv, new float[][]{{x1, y0, z1}, {x1, y0, z0}, {x1, y1, z0}, {x1, y1, z1}}, counter); // east
        addFace(uv, new float[][]{{x1, y0, z0}, {x0, y0, z0}, {x0, y1, z0}, {x1, y1, z0}}, counter); // north
        addFace(uv, new float[][]{{x0, y0, z1}, {x1, y0, z1}, {x1, y1, z1}, {x0, y1, z1}}, counter); // south
        mesh.upload(verts.toArray(), inds.toArray());

        glEnable(GL_POLYGON_OFFSET_FILL);
        glPolygonOffset(-1.5f, -1.5f);
        shader.bind();
        atlas.bind();
        mesh.render();
        glDisable(GL_POLYGON_OFFSET_FILL);
    }

    private void addFace(float[] uv, float[][] positions, int[] counter) {
        float u0 = uv[0], v0 = uv[1], u1 = uv[2], v1 = uv[3];
        float[][] uvs = {{u0, v0}, {u1, v0}, {u1, v1}, {u0, v1}};
        int base = counter[0];
        for (int i = 0; i < 4; i++) {
            verts.add(positions[i][0]);
            verts.add(positions[i][1]);
            verts.add(positions[i][2]);
            verts.add(uvs[i][0]);
            verts.add(uvs[i][1]);
            verts.add(1f);  // light
            verts.add(1f);  // blockLight: stay readable at night
            verts.add(0f);
            verts.add(0f);
            verts.add(0f);
        }
        inds.add(base);
        inds.add(base + 1);
        inds.add(base + 2);
        inds.add(base);
        inds.add(base + 2);
        inds.add(base + 3);
        counter[0] += 4;
    }

    public void destroy() {
        mesh.destroy();
    }
}
