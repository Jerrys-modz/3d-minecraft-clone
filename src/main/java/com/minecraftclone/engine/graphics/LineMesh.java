package com.minecraftclone.engine.graphics;

import java.nio.FloatBuffer;

import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.system.MemoryUtil.memAllocFloat;
import static org.lwjgl.system.MemoryUtil.memFree;

/** A minimal position-only vertex buffer, drawn with GL_LINES. Used for the crosshair and block-selection outline. */
public class LineMesh {

    private final int vaoId;
    private final int vboId;
    private int vertexCount;

    public LineMesh() {
        vaoId = glGenVertexArrays();
        vboId = glGenBuffers();
    }

    public void upload(float[] positions) {
        glBindVertexArray(vaoId);
        FloatBuffer buffer = memAllocFloat(positions.length);
        buffer.put(positions).flip();
        glBindBuffer(GL_ARRAY_BUFFER, vboId);
        glBufferData(GL_ARRAY_BUFFER, buffer, GL_STATIC_DRAW);
        memFree(buffer);

        glVertexAttribPointer(0, 3, GL_FLOAT, false, 3 * Float.BYTES, 0);
        glEnableVertexAttribArray(0);

        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glBindVertexArray(0);
        vertexCount = positions.length / 3;
    }

    public void render() {
        glBindVertexArray(vaoId);
        glDrawArrays(GL_LINES, 0, vertexCount);
        glBindVertexArray(0);
    }

    public void destroy() {
        glDeleteBuffers(vboId);
        glDeleteVertexArrays(vaoId);
    }
}
