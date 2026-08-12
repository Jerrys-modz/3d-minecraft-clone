package com.minecraftclone.engine;

import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryStack;

import java.nio.FloatBuffer;
import java.util.HashMap;
import java.util.Map;

import static org.lwjgl.opengl.GL20.*;

/** Compiles and links a vertex+fragment GLSL shader program and provides typed uniform setters. */
public class Shader {

    private final int programId;
    private final Map<String, Integer> uniformCache = new HashMap<>();

    public Shader(String vertexSource, String fragmentSource) {
        int vertexId = compile(GL_VERTEX_SHADER, vertexSource);
        int fragmentId = compile(GL_FRAGMENT_SHADER, fragmentSource);

        programId = glCreateProgram();
        glAttachShader(programId, vertexId);
        glAttachShader(programId, fragmentId);
        glLinkProgram(programId);

        if (glGetProgrami(programId, GL_LINK_STATUS) == 0) {
            throw new RuntimeException("Error linking shader program: " + glGetProgramInfoLog(programId, 2048));
        }

        glDetachShader(programId, vertexId);
        glDetachShader(programId, fragmentId);
        glDeleteShader(vertexId);
        glDeleteShader(fragmentId);
    }

    private int compile(int type, String source) {
        int shaderId = glCreateShader(type);
        glShaderSource(shaderId, source);
        glCompileShader(shaderId);
        if (glGetShaderi(shaderId, GL_COMPILE_STATUS) == 0) {
            throw new RuntimeException("Error compiling shader (type " + type + "): " + glGetShaderInfoLog(shaderId, 2048));
        }
        return shaderId;
    }

    public void bind() {
        glUseProgram(programId);
    }

    public void unbind() {
        glUseProgram(0);
    }

    public void destroy() {
        unbind();
        glDeleteProgram(programId);
    }

    private int location(String name) {
        return uniformCache.computeIfAbsent(name, n -> glGetUniformLocation(programId, n));
    }

    public void setUniform(String name, int value) {
        glUniform1i(location(name), value);
    }

    public void setUniform(String name, float value) {
        glUniform1f(location(name), value);
    }

    public void setUniform(String name, boolean value) {
        glUniform1i(location(name), value ? 1 : 0);
    }

    public void setUniform(String name, Vector2f value) {
        glUniform2f(location(name), value.x, value.y);
    }

    public void setUniform(String name, Vector3f value) {
        glUniform3f(location(name), value.x, value.y, value.z);
    }

    public void setUniform(String name, Vector4f value) {
        glUniform4f(location(name), value.x, value.y, value.z, value.w);
    }

    public void setUniform(String name, Matrix4f value) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer buffer = stack.mallocFloat(16);
            value.get(buffer);
            glUniformMatrix4fv(location(name), false, buffer);
        }
    }
}
