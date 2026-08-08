package com.minecraftclone.engine;

import com.minecraftclone.engine.graphics.LineMesh;
import org.joml.Matrix4f;
import org.joml.Vector3i;
import org.joml.Vector4f;

import static org.lwjgl.opengl.GL11.*;

/** Draws the crosshair and the wireframe outline around the block the player is looking at. */
public class Hud {

    private final Shader lineShader;
    private final LineMesh crosshair = new LineMesh();
    private final LineMesh cubeOutline = new LineMesh();
    private final Matrix4f identity = new Matrix4f();
    private final Matrix4f modelMatrix = new Matrix4f();

    public Hud(Shader lineShader) {
        this.lineShader = lineShader;
        buildCrosshair();
        buildCubeOutline();
    }

    private void buildCrosshair() {
        float s = 0.02f;
        float ar = 1.0f; // aspect correction applied via non-uniform scale in the shader's model matrix at draw time
        float[] verts = {
                -s, 0, 0, s, 0, 0,
                0, -s, 0, 0, s, 0,
        };
        crosshair.upload(verts);
    }

    private void buildCubeOutline() {
        float e = 0.002f; // slightly larger than the unit cube to avoid z-fighting with the block face
        float min = -e, max = 1 + e;
        float[] c = {
                // bottom square
                min, min, min, max, min, min,
                max, min, min, max, min, max,
                max, min, max, min, min, max,
                min, min, max, min, min, min,
                // top square
                min, max, min, max, max, min,
                max, max, min, max, max, max,
                max, max, max, min, max, max,
                min, max, max, min, max, min,
                // verticals
                min, min, min, min, max, min,
                max, min, min, max, max, min,
                max, min, max, max, max, max,
                min, min, max, min, max, max,
        };
        cubeOutline.upload(c);
    }

    public void renderCrosshair(float aspectRatio) {
        glDisable(GL_DEPTH_TEST);
        lineShader.bind();
        lineShader.setUniform("projection", identity);
        lineShader.setUniform("view", identity);
        Matrix4f model = modelMatrix.identity().scale(1f / aspectRatio, 1f, 1f);
        lineShader.setUniform("model", model);
        lineShader.setUniform("color", new Vector4f(1, 1, 1, 0.9f));
        glLineWidth(2f);
        crosshair.render();
        lineShader.unbind();
        glEnable(GL_DEPTH_TEST);
    }

    public void renderBlockOutline(Matrix4f projection, Matrix4f view, Vector3i blockPos) {
        lineShader.bind();
        lineShader.setUniform("projection", projection);
        lineShader.setUniform("view", view);
        Matrix4f model = modelMatrix.identity().translate(blockPos.x, blockPos.y, blockPos.z);
        lineShader.setUniform("model", model);
        lineShader.setUniform("color", new Vector4f(0, 0, 0, 0.6f));
        glLineWidth(2.5f);
        cubeOutline.render();
        lineShader.unbind();
    }

    public void destroy() {
        crosshair.destroy();
        cubeOutline.destroy();
    }
}
