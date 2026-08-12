package com.minecraftclone.engine;

import com.minecraftclone.engine.graphics.IconMesh;
import com.minecraftclone.engine.graphics.LineMesh;
import com.minecraftclone.engine.graphics.TextureAtlas;
import com.minecraftclone.player.Inventory;
import com.minecraftclone.world.BlockType;
import org.joml.Matrix4f;
import org.joml.Vector3i;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.opengl.GL11.*;

/**
 * Draws all 2D overlay elements: the crosshair, the wireframe outline around
 * the targeted block, and the hotbar (block icons + inventory counts, with
 * the selected slot highlighted).
 * <p>
 * All 2D geometry is built in a "logical square" coordinate space (-1..1 on
 * both axes, as if the viewport were square) and corrected for the real
 * aspect ratio with a uniform scale at draw time - the same trick the
 * crosshair already used, just applied consistently across every HUD
 * element now.
 */
public class Hud {

    private static final float HOTBAR_SLOT_SIZE = 0.09f;
    private static final float HOTBAR_SLOT_GAP = 0.012f;
    private static final float HOTBAR_BOTTOM_MARGIN = 0.06f; // distance from the bottom edge to the slot row's bottom
    private static final float HOTBAR_PADDING = 0.015f;      // background panel padding beyond the slots

    private final Shader lineShader;
    private final Shader hudShader;

    private final LineMesh crosshair = new LineMesh();
    private final LineMesh cubeOutline = new LineMesh();
    private final LineMesh hotbarBackground = new LineMesh(GL_TRIANGLES);
    private final LineMesh hotbarHighlight = new LineMesh(GL_LINES);
    private final IconMesh hotbarIcons = new IconMesh();

    private final Matrix4f identity = new Matrix4f();
    private final Matrix4f modelMatrix = new Matrix4f();
    private final Matrix4f hudTransform = new Matrix4f();

    public Hud(Shader lineShader, Shader hudShader) {
        this.lineShader = lineShader;
        this.hudShader = hudShader;
        buildCrosshair();
        buildCubeOutline();
    }

    private void buildCrosshair() {
        float s = 0.02f;
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

    /** X-center of hotbar slot {@code i} out of {@code count}, in logical (-1..1, square-viewport) space. */
    private float slotCenterX(int i, int count) {
        float totalWidth = count * HOTBAR_SLOT_SIZE + (count - 1) * HOTBAR_SLOT_GAP;
        float leftEdge = -totalWidth / 2f;
        return leftEdge + i * (HOTBAR_SLOT_SIZE + HOTBAR_SLOT_GAP) + HOTBAR_SLOT_SIZE / 2f;
    }

    private float slotCenterY() {
        return -1f + HOTBAR_BOTTOM_MARGIN + HOTBAR_SLOT_SIZE / 2f;
    }

    public void renderHotbar(TextureAtlas atlas, BlockType[] hotbar, Inventory inventory, int selectedSlot, float aspectRatio) {
        glDisable(GL_DEPTH_TEST);

        int count = hotbar.length;
        float centerY = slotCenterY();

        hudTransform.identity().scale(1f / aspectRatio, 1f, 1f);

        // Background panel behind the whole row.
        float totalWidth = count * HOTBAR_SLOT_SIZE + (count - 1) * HOTBAR_SLOT_GAP;
        float bgMinX = -totalWidth / 2f - HOTBAR_PADDING;
        float bgMaxX = totalWidth / 2f + HOTBAR_PADDING;
        float bgMinY = centerY - HOTBAR_SLOT_SIZE / 2f - HOTBAR_PADDING;
        float bgMaxY = centerY + HOTBAR_SLOT_SIZE / 2f + HOTBAR_PADDING;
        float[] bg = {
                bgMinX, bgMinY, 0, bgMaxX, bgMinY, 0, bgMaxX, bgMaxY, 0,
                bgMinX, bgMinY, 0, bgMaxX, bgMaxY, 0, bgMinX, bgMaxY, 0,
        };
        hotbarBackground.upload(bg);

        lineShader.bind();
        lineShader.setUniform("projection", identity);
        lineShader.setUniform("view", identity);
        lineShader.setUniform("model", hudTransform);
        lineShader.setUniform("color", new Vector4f(0f, 0f, 0f, 0.45f));
        hotbarBackground.render();

        // Selected-slot highlight border.
        float selCenterX = slotCenterX(Math.max(0, Math.min(count - 1, selectedSlot)), count);
        float hs = HOTBAR_SLOT_SIZE / 2f + 0.006f;
        float[] hl = {
                selCenterX - hs, centerY - hs, 0, selCenterX + hs, centerY - hs, 0,
                selCenterX + hs, centerY - hs, 0, selCenterX + hs, centerY + hs, 0,
                selCenterX + hs, centerY + hs, 0, selCenterX - hs, centerY + hs, 0,
                selCenterX - hs, centerY + hs, 0, selCenterX - hs, centerY - hs, 0,
        };
        hotbarHighlight.upload(hl);
        lineShader.setUniform("color", new Vector4f(1f, 1f, 1f, 0.95f));
        glLineWidth(2f);
        hotbarHighlight.render();
        lineShader.unbind();

        // Icons + count digits, batched into one textured draw call.
        List<Float> vertices = new ArrayList<>();
        List<Integer> indices = new ArrayList<>();
        int[] vertexCounter = {0};

        for (int i = 0; i < count; i++) {
            float cx = slotCenterX(i, count);
            float iconHalf = HOTBAR_SLOT_SIZE / 2f - 0.008f;
            addQuad(vertices, indices, vertexCounter,
                    cx - iconHalf, centerY - iconHalf, cx + iconHalf, centerY + iconHalf,
                    atlas.getUV(hotbar[i].topTile));

            int amount = inventory.getCount(hotbar[i]);
            if (amount > 0) {
                String text = Integer.toString(Math.min(amount, 999));
                float digitSize = 0.032f;
                float digitGap = 0.002f;
                float textWidth = text.length() * digitSize + (text.length() - 1) * digitGap;
                float startX = cx + iconHalf - textWidth;
                float y = centerY + iconHalf - digitSize;
                for (int c = 0; c < text.length(); c++) {
                    int digit = text.charAt(c) - '0';
                    float x = startX + c * (digitSize + digitGap);
                    addQuad(vertices, indices, vertexCounter,
                            x, y, x + digitSize, y + digitSize,
                            atlas.getUV(TextureAtlas.digitTile(digit)));
                }
            }
        }

        float[] vArray = new float[vertices.size()];
        for (int i = 0; i < vArray.length; i++) vArray[i] = vertices.get(i);
        int[] iArray = new int[indices.size()];
        for (int i = 0; i < iArray.length; i++) iArray[i] = indices.get(i);
        hotbarIcons.upload(vArray, iArray);

        hudShader.bind();
        hudShader.setUniform("transform", hudTransform);
        hudShader.setUniform("atlas", 0);
        atlas.bind();
        hotbarIcons.render();
        hudShader.unbind();

        glEnable(GL_DEPTH_TEST);
    }

    private void addQuad(List<Float> vertices, List<Integer> indices, int[] vertexCounter,
                          float minX, float minY, float maxX, float maxY, float[] uv) {
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

    public void destroy() {
        crosshair.destroy();
        cubeOutline.destroy();
        hotbarBackground.destroy();
        hotbarHighlight.destroy();
        hotbarIcons.destroy();
    }
}
