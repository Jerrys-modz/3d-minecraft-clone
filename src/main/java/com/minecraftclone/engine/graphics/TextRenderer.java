package com.minecraftclone.engine.graphics;

import com.minecraftclone.engine.Shader;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.List;

/**
 * Batches arbitrary printable-ASCII text into textured quads sampled from the
 * shared {@link FontAtlas}, for every piece of on-screen text (hotbar counts,
 * status messages, the F3 debug overlay). Uses the same "logical square"
 * coordinate space as the rest of the HUD: text is positioned by its
 * bottom-left corner, glyphs are {@code size} logical units tall, and a whole
 * batch is uploaded and drawn in a single call once per frame.
 */
public class TextRenderer {

    private static final float GLYPH_GAP_FRACTION = 0.08f;

    private final FontAtlas font;
    private final Shader shader;
    private final IconMesh mesh = new IconMesh();

    private final List<Float> vertices = new ArrayList<>();
    private final List<Integer> indices = new ArrayList<>();
    private final int[] vertexCounter = {0};

    public TextRenderer(FontAtlas font, Shader shader) {
        this.font = font;
        this.shader = shader;
    }

    /** Clears the current text batch. Call once per frame before any {@link #add}. */
    public void begin() {
        vertices.clear();
        indices.clear();
        vertexCounter[0] = 0;
    }

    /** Width in logical units that {@code text} will occupy at the given glyph size. */
    public float measure(String text, float size) {
        if (text.isEmpty()) return 0f;
        float gap = size * GLYPH_GAP_FRACTION;
        return text.length() * (size + gap) - gap;
    }

    /**
     * Adds {@code text} to the batch starting at its bottom-left corner (x, y),
     * with each glyph {@code size} logical units tall plus a small gap.
     * Unprintable/non-ASCII characters render as '?'. Returns the text width.
     */
    public float add(String text, float x, float y, float size) {
        float gap = size * GLYPH_GAP_FRACTION;
        float cursor = x;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c < FontAtlas.FIRST_CHAR || c > FontAtlas.LAST_CHAR) c = '?';
            if (c != ' ') {
                float[] uv = font.getUV(c);
                addQuad(cursor, y, cursor + size, y + size, uv);
            }
            cursor += size + gap;
        }
        return cursor - gap - x;
    }

    /** Uploads the accumulated batch and draws it tinted {@code color} using the given HUD transform. */
    public void render(Matrix4f transform, Vector4f color) {
        if (vertices.isEmpty()) return;
        mesh.upload(toFloatArray(vertices), toIntArray(indices));
        shader.bind();
        shader.setUniform("transform", transform);
        shader.setUniform("atlas", 0);
        shader.setUniform("color", color);
        font.bind();
        mesh.render();
        shader.unbind();
    }

    public void destroy() {
        mesh.destroy();
    }

    private void addQuad(float minX, float minY, float maxX, float maxY, float[] uv) {
        float u0 = uv[0], v0 = uv[1], u1 = uv[2], v1 = uv[3];
        int base = vertexCounter[0];
        vertices.add(minX); vertices.add(minY); vertices.add(u0); vertices.add(v1);
        vertices.add(maxX); vertices.add(minY); vertices.add(u1); vertices.add(v1);
        vertices.add(maxX); vertices.add(maxY); vertices.add(u1); vertices.add(v0);
        vertices.add(minX); vertices.add(maxY); vertices.add(u0); vertices.add(v0);
        indices.add(base); indices.add(base + 1); indices.add(base + 2);
        indices.add(base); indices.add(base + 2); indices.add(base + 3);
        vertexCounter[0] += 4;
    }

    private static float[] toFloatArray(List<Float> values) {
        float[] array = new float[values.size()];
        for (int i = 0; i < array.length; i++) array[i] = values.get(i);
        return array;
    }

    private static int[] toIntArray(List<Integer> values) {
        int[] array = new int[values.size()];
        for (int i = 0; i < array.length; i++) array[i] = values.get(i);
        return array;
    }
}
