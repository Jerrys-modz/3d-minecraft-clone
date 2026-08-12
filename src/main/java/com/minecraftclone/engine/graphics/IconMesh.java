package com.minecraftclone.engine.graphics;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;

import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.system.MemoryUtil.*;

/**
 * A dynamically-rebuilt textured quad batch: position(2 floats) + uv(2
 * floats) per vertex, indexed triangles. Used by the HUD to draw hotbar
 * icons and digit glyphs sampled from the block texture atlas - the whole
 * batch is rebuilt and re-uploaded once per frame since its content (which
 * slot is selected, what the counts are) changes constantly.
 */
public class IconMesh {

    private static final int STRIDE_FLOATS = 4;

    private final int vaoId;
    private final int vboId;
    private final int eboId;
    private int indexCount;

    public IconMesh() {
        vaoId = glGenVertexArrays();
        vboId = glGenBuffers();
        eboId = glGenBuffers();
    }

    public void upload(float[] vertices, int[] indices) {
        glBindVertexArray(vaoId);

        FloatBuffer vertexBuffer = memAllocFloat(vertices.length);
        vertexBuffer.put(vertices).flip();
        glBindBuffer(GL_ARRAY_BUFFER, vboId);
        glBufferData(GL_ARRAY_BUFFER, vertexBuffer, GL_DYNAMIC_DRAW);
        memFree(vertexBuffer);

        IntBuffer indexBuffer = memAllocInt(indices.length);
        indexBuffer.put(indices).flip();
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, eboId);
        glBufferData(GL_ELEMENT_ARRAY_BUFFER, indexBuffer, GL_DYNAMIC_DRAW);
        memFree(indexBuffer);

        int strideBytes = STRIDE_FLOATS * Float.BYTES;
        glVertexAttribPointer(0, 2, GL_FLOAT, false, strideBytes, 0);
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(1, 2, GL_FLOAT, false, strideBytes, 2L * Float.BYTES);
        glEnableVertexAttribArray(1);

        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glBindVertexArray(0);

        this.indexCount = indices.length;
    }

    public void render() {
        if (indexCount == 0) return;
        glBindVertexArray(vaoId);
        glDrawElements(GL_TRIANGLES, indexCount, GL_UNSIGNED_INT, 0);
        glBindVertexArray(0);
    }

    public void destroy() {
        glDeleteBuffers(vboId);
        glDeleteBuffers(eboId);
        glDeleteVertexArrays(vaoId);
    }
}
