package com.minecraftclone.engine.graphics;

import com.minecraftclone.engine.LightningBolt;
import com.minecraftclone.engine.Shader;
import com.minecraftclone.engine.WeatherParticles;
import com.minecraftclone.util.FloatArray;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.util.List;

import static org.lwjgl.opengl.GL11.GL_CULL_FACE;
import static org.lwjgl.opengl.GL11.GL_LINES;
import static org.lwjgl.opengl.GL11.GL_TRIANGLES;
import static org.lwjgl.opengl.GL11.glDisable;
import static org.lwjgl.opengl.GL11.glEnable;
import static org.lwjgl.opengl.GL11.glIsEnabled;
import static org.lwjgl.opengl.GL11.glLineWidth;

/**
 * Draws the {@link WeatherParticles} using the shared "line" shader (which just
 * outputs a uniform color): rain as thin world-vertical {@code GL_LINES} streaks
 * in a translucent blue, snow as small white crossed-plane quads, and any active
 * {@link LightningBolt}s as bright jagged polylines. All are depth-tested
 * against the world, so precipitation and bolts hide behind terrain.
 */
public class WeatherRenderer {

    private final LineMesh rainMesh = new LineMesh(GL_LINES);
    private final LineMesh snowMesh = new LineMesh(GL_TRIANGLES);
    private final LineMesh boltMesh = new LineMesh(GL_LINES);
    private final FloatArray verts = new FloatArray(4096);
    private final Matrix4f identity = new Matrix4f();

    /** Draws the current particles and bolts, if any. Caller must have a world projection/view ready. */
    public void render(Shader lineShader, Matrix4f projection, Matrix4f view,
                       WeatherParticles particles, List<LightningBolt> bolts) {
        lineShader.bind();
        lineShader.setUniform("projection", projection);
        lineShader.setUniform("view", view);
        lineShader.setUniform("model", identity.identity());

        verts.clear();
        int rain = particles.writeRain(verts);
        if (rain > 0) {
            lineShader.setUniform("color", new Vector4f(0.72f, 0.82f, 1f, 0.45f));
            glLineWidth(1.5f);
            rainMesh.upload(verts.toArray());
            rainMesh.render();
        }

        verts.clear();
        int snow = particles.writeSnow(verts);
        if (snow > 0) {
            lineShader.setUniform("color", new Vector4f(1f, 1f, 1f, 0.85f));
            snowMesh.upload(verts.toArray());
            // The crossed snow planes are single-sided; turn culling off so a flake
            // stays visible from any angle (restore the prior state after).
            boolean cull = glIsEnabled(GL_CULL_FACE);
            if (cull) glDisable(GL_CULL_FACE);
            snowMesh.render();
            if (cull) glEnable(GL_CULL_FACE);
        }

        verts.clear();
        int boltVerts = 0;
        for (LightningBolt bolt : bolts) {
            bolt.write(verts);
            boltVerts++;
        }
        if (boltVerts > 0) {
            lineShader.setUniform("color", new Vector4f(1f, 0.96f, 0.82f, 1f));
            glLineWidth(2.5f);
            boltMesh.upload(verts.toArray());
            boltMesh.render();
        }

        lineShader.unbind();
    }

    public void destroy() {
        rainMesh.destroy();
        snowMesh.destroy();
        boltMesh.destroy();
    }
}
