package com.minecraftclone.engine.graphics;

import com.minecraftclone.engine.Camera;
import com.minecraftclone.engine.Shader;
import com.minecraftclone.util.FloatArray;
import com.minecraftclone.util.IntArray;
import com.minecraftclone.world.Mob;
import org.joml.Vector3f;

import java.util.List;

import static org.lwjgl.opengl.GL11.*;

/**
 * Draws {@link Mob}s as camera-facing billboards (one quad per animal, using
 * its own procedural sprite - see {@link MobTextures}), batching all mobs of
 * the same kind into a single mesh so the whole herd costs a few draw calls.
 * Mobs bob gently while walking so they look alive. The caller must already
 * have the chunk shader bound with projection/view/fog/ambient uniforms set,
 * so mobs get the same fog and day/night lighting as everything else.
 */
public class MobRenderer {

    private final Mesh mesh = new Mesh();
    private final Vector3f right = new Vector3f();

    public void render(MobTextures textures, List<Mob> mobs, Camera camera) {
        if (mobs.isEmpty()) return;

        Vector3f r = camera.getRight();
        right.set(r);

        // Billboards need to be visible from both sides (like dropped items).
        glDisable(GL_CULL_FACE);
        for (Mob.Type type : Mob.Type.values()) {
            FloatArray v = new FloatArray(mobs.size() * 40);
            IntArray i = new IntArray(mobs.size() * 6);
            int[] c = {0};
            for (Mob mob : mobs) {
                if (mob.type == type) {
                    addBillboard(v, i, c, mob);
                }
            }
            if (c[0] > 0) {
                mesh.upload(v.toArray(), i.toArray());
                textures.bind(type);
                mesh.render();
            }
        }
        glEnable(GL_CULL_FACE);
    }

    private void addBillboard(FloatArray verts, IntArray inds, int[] counter, Mob mob) {
        float cy = mob.position.y + mob.bobOffset();
        float hw = mob.type.width / 2f, hh = mob.type.height / 2f;
        float rx = right.x * hw, rz = right.z * hw;
        int base = counter[0];

        // Four corners: bottom-left, bottom-right, top-right, top-left. 10 floats
        // per vertex (pos, uv, light, blockLight, fluidFlow, flowDir) - see Mesh.
        verts.add(mob.position.x - rx); verts.add(cy - hh); verts.add(mob.position.z - rz); verts.add(0f); verts.add(1f); verts.add(1f); verts.add(0f); verts.add(0f); verts.add(0f); verts.add(0f);
        verts.add(mob.position.x + rx); verts.add(cy - hh); verts.add(mob.position.z + rz); verts.add(1f); verts.add(1f); verts.add(1f); verts.add(0f); verts.add(0f); verts.add(0f); verts.add(0f);
        verts.add(mob.position.x + rx); verts.add(cy + hh); verts.add(mob.position.z + rz); verts.add(1f); verts.add(0f); verts.add(1f); verts.add(0f); verts.add(0f); verts.add(0f); verts.add(0f);
        verts.add(mob.position.x - rx); verts.add(cy + hh); verts.add(mob.position.z - rz); verts.add(0f); verts.add(0f); verts.add(1f); verts.add(0f); verts.add(0f); verts.add(0f); verts.add(0f);

        inds.add(base); inds.add(base + 1); inds.add(base + 2);
        inds.add(base); inds.add(base + 2); inds.add(base + 3);
        counter[0] += 4;
    }

    public void destroy() {
        mesh.destroy();
    }
}
