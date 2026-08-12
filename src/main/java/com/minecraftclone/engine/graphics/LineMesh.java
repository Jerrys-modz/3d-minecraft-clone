package com.minecraftclone.engine.graphics;

import java.nio.FloatBuffer;

import static org.lwjgl.opengl.GL11.GL_LINES;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.system.MemoryUtil.memAllocFloat;
import static org.lwjgl.system.MemoryUtil.memFree;

/**
 * A minimal position-only vertex buffer for flat-colored geometry (paired
 * with the "line" shader, which just outputs a uniform color): GL_LINES for
 * wireframes (crosshair, block-selection outline) or GL_TRIANGLES for filled
 * shapes (HUD background panels, the hotbar selection highlight).
 */
public class LineMesh {

    private final int vaoId;
    private final int vboId;
    private final int primitiveType;
    private int vertexCount;

    public LineMesh() {
        this(GL_LINES);
    }

    public LineMesh(int primitiveType) {
        this.primitiveType = primitiveType;
        vaoId = glGenVertexArrays();
        vboId = glGenBuffers();
    }

    public void upload(float[] positions) {
        glBindVertexArray(vaoId);
        FloatBuffer buffer = memAllocFloat(positions.length);
        buffer.put(positions).flip();
        glBindBuffer(GL_ARRAY_BUFFER, vboId);
        glBufferData(GL_ARRAY_BUFFER, buffer, GL_DYNAMIC_DRAW);
        memFree(buffer);

        glVertexAttribPointer(0, 3, GL_FLOAT, false, 3 * Float.BYTES, 0);
        glEnableVertexAttribArray(0);

        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glBindVertexArray(0);
        vertexCount = positions.length / 3;
    }

    public void render() {
        if (vertexCount == 0) return;
        glBindVertexArray(vaoId);
        glDrawArrays(primitiveType, 0, vertexCount);
        glBindVertexArray(0);
    }

    public void destroy() {
        glDeleteBuffers(vboId);
        glDeleteVertexArrays(vaoId);
    }
}
