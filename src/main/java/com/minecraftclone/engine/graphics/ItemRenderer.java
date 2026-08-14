package com.minecraftclone.engine.graphics;

import com.minecraftclone.engine.Camera;
import com.minecraftclone.engine.Shader;
import com.minecraftclone.util.FloatArray;
import com.minecraftclone.util.IntArray;
import com.minecraftclone.world.ItemEntity;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.opengl.GL11.*;

/**
 * Draws dropped {@link ItemEntity}s as small camera-facing billboards, bobbing
 * in place. Block items batch into one mesh sampled from the shared block
 * atlas; inventory-only items (food/tools/ingots) draw individually since each
 * has its own PNG texture. The caller must already have the chunk shader bound
 * with projection/view/fog/ambient uniforms set.
 */
public class ItemRenderer {

    private static final float HALF_SIZE = 0.18f;

    private final Mesh blockMesh = new Mesh();
    private final Mesh itemMesh = new Mesh();
    private final Vector3f right = new Vector3f();

    public void render(Shader shader, TextureAtlas atlas, ItemTextures itemTextures,
                       List<ItemEntity> items, Camera camera) {
        if (items.isEmpty()) return;

        Vector3f r = camera.getRight();
        right.set(r);

        FloatArray blockV = new FloatArray(items.size() * 28);
        IntArray blockI = new IntArray(items.size() * 6);
        int[] blockCounter = {0};
        List<ItemEntity> itemItems = new ArrayList<>();

        for (ItemEntity e : items) {
            if (e.type.isItem) {
                itemItems.add(e);
            } else {
                addBillboard(blockV, blockI, blockCounter, e, atlas.getUV(e.type.topTile));
            }
        }

        glDisable(GL_CULL_FACE);

        if (blockCounter[0] > 0) {
            blockMesh.upload(blockV.toArray(), blockI.toArray());
            atlas.bind();
            blockMesh.render();
        }

        for (ItemEntity e : itemItems) {
            FloatArray v = new FloatArray(28);
            IntArray i = new IntArray(6);
            int[] c = {0};
            addBillboard(v, i, c, e, new float[]{0f, 0f, 1f, 1f});
            itemMesh.upload(v.toArray(), i.toArray());
            itemTextures.bind(e.type);
            itemMesh.render();
        }

        glEnable(GL_CULL_FACE);
    }

    private void addBillboard(FloatArray verts, IntArray inds, int[] counter, ItemEntity e, float[] uv) {
        Vector3f p = e.position;
        float bob = (float) Math.sin(e.age * 3.0) * 0.03f;
        float cy = p.y + bob;
        float rx = right.x * HALF_SIZE, rz = right.z * HALF_SIZE;
        float u0 = uv[0], v0 = uv[1], u1 = uv[2], v1 = uv[3];
        int base = counter[0];

        // Four corners of the billboard: bottom-left, bottom-right, top-right, top-left.
        // Trailing 0f is the fluidFlow attribute (see Mesh) - items never animate like flowing water.
        verts.add(p.x - rx); verts.add(cy - HALF_SIZE); verts.add(p.z - rz); verts.add(u0); verts.add(v1); verts.add(1f); verts.add(0f); verts.add(0f);
        verts.add(p.x + rx); verts.add(cy - HALF_SIZE); verts.add(p.z + rz); verts.add(u1); verts.add(v1); verts.add(1f); verts.add(0f); verts.add(0f);
        verts.add(p.x + rx); verts.add(cy + HALF_SIZE); verts.add(p.z + rz); verts.add(u1); verts.add(v0); verts.add(1f); verts.add(0f); verts.add(0f);
        verts.add(p.x - rx); verts.add(cy + HALF_SIZE); verts.add(p.z - rz); verts.add(u0); verts.add(v0); verts.add(1f); verts.add(0f); verts.add(0f);

        inds.add(base); inds.add(base + 1); inds.add(base + 2);
        inds.add(base); inds.add(base + 2); inds.add(base + 3);
        counter[0] += 4;
    }

    public void destroy() {
        blockMesh.destroy();
        itemMesh.destroy();
    }
}
