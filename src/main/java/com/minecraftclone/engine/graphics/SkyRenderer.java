package com.minecraftclone.engine.graphics;

import com.minecraftclone.engine.Shader;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.nio.FloatBuffer;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.system.MemoryUtil.memAllocFloat;
import static org.lwjgl.system.MemoryUtil.memFree;

/**
 * Draws the procedural skybox: a fullscreen quad rendered at the far plane
 * before the world, shaded by the sky shader (gradient, sun, moon, stars,
 * clouds). The quad is pinned to depth 1 so chunks drawn afterwards (with a
 * depth test) always sit in front of it.
 */
public class SkyRenderer {

    // A single oversized triangle that always covers the whole screen (no strip
    // winding/alternation to trip over), even when back-face culling is active.
    private static final float[] QUAD = {
            -1f, -1f,
             3f, -1f,
            -1f,  3f,
    };

    private final int vaoId;
    private final int vboId;
    private final Matrix4f invProj = new Matrix4f();
    private final Matrix4f invView = new Matrix4f();

    public SkyRenderer() {
        vaoId = glGenVertexArrays();
        vboId = glGenBuffers();
        glBindVertexArray(vaoId);
        FloatBuffer buffer = memAllocFloat(QUAD.length);
        buffer.put(QUAD).flip();
        glBindBuffer(GL_ARRAY_BUFFER, vboId);
        glBufferData(GL_ARRAY_BUFFER, buffer, GL_STATIC_DRAW);
        memFree(buffer);
        glVertexAttribPointer(0, 2, GL_FLOAT, false, 2 * Float.BYTES, 0);
        glEnableVertexAttribArray(0);
        glBindVertexArray(0);
    }

    public void render(Shader skyShader, Matrix4f projection, Matrix4f view,
                       Vector3f sunDir, float daylight, float cloudTime,
                       float cloudDensity, float cloudSpeed, float stars,
                       Vector3f zenith, Vector3f horizon, Vector3f night, Vector3f sun, Vector3f moon) {
        projection.invert(invProj);
        view.invert(invView);

        skyShader.bind();
        skyShader.setUniform("invProj", invProj);
        skyShader.setUniform("invView", invView);
        skyShader.setUniform("sunDir", sunDir);
        skyShader.setUniform("daylight", daylight);
        skyShader.setUniform("cloudTime", cloudTime);
        skyShader.setUniform("cloudDensity", cloudDensity);
        skyShader.setUniform("cloudSpeed", cloudSpeed);
        skyShader.setUniform("stars", stars);
        skyShader.setUniform("zenithColor", zenith);
        skyShader.setUniform("horizonColor", horizon);
        skyShader.setUniform("nightColor", night);
        skyShader.setUniform("sunColor", sun);
        skyShader.setUniform("moonColor", moon);

        glBindVertexArray(vaoId);
        // The game culls back faces globally; the sky quad must always cover the
        // whole screen, so draw it with culling off (state is restored after).
        boolean cull = glIsEnabled(GL_CULL_FACE);
        if (cull) {
            glDisable(GL_CULL_FACE);
        }
        glDrawArrays(GL_TRIANGLES, 0, 3);
        if (cull) {
            glEnable(GL_CULL_FACE);
        }
        glBindVertexArray(0);
        skyShader.unbind();
    }

    public void destroy() {
        glDeleteBuffers(vboId);
        glDeleteVertexArrays(vaoId);
    }
}
