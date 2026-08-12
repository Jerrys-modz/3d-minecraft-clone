package com.minecraftclone.engine;

import com.minecraftclone.Settings;
import com.minecraftclone.engine.graphics.FontAtlas;
import com.minecraftclone.engine.graphics.IconMesh;
import com.minecraftclone.engine.graphics.ItemTextures;
import com.minecraftclone.engine.graphics.LineMesh;
import com.minecraftclone.engine.graphics.TextRenderer;
import com.minecraftclone.engine.graphics.TextureAtlas;
import com.minecraftclone.player.Inventory;
import com.minecraftclone.player.ToolDurability;
import com.minecraftclone.world.BlockType;
import com.minecraftclone.world.Mining;
import org.joml.Matrix4f;
import org.joml.Vector3i;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.opengl.GL11.*;

/**
 * Draws all 2D overlay elements: the crosshair, the wireframe outline around
 * the targeted block, the hotbar (block icons + inventory counts, with the
 * selected slot highlighted), the health/hunger/stamina bars, transient
 * on-screen messages, and the F3 debug overlay.
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

    private static final float DURABILITY_BAR_HEIGHT = 0.008f; // thin wear strip along the bottom of a worn tool's icon

    private static final float STAT_BAR_HEIGHT = 0.022f;
    private static final float STAT_BAR_GAP = 0.007f;          // gap between stacked bars
    private static final float STAT_BAR_STACK_MARGIN = 0.014f; // gap between the bar stack and the hotbar panel below it

    private static final Vector4f WHITE = new Vector4f(1f, 1f, 1f, 1f);

    private final Shader lineShader;
    private final Shader hudShader;

    private final LineMesh crosshair = new LineMesh();
    private final LineMesh cubeOutline = new LineMesh();
    private final LineMesh hotbarBackground = new LineMesh(GL_TRIANGLES);
    private final LineMesh hotbarHighlight = new LineMesh(GL_LINES);
    private final IconMesh hotbarBlockIcons = new IconMesh();  // batched: all non-item slots, sampling the shared block atlas
    private final IconMesh hotbarItemIcon = new IconMesh();    // reused per slot: each item has its own texture, so items can't batch
    private final TextRenderer text;                           // all HUD text: counts, messages, debug overlay
    private final LineMesh statBarBackground = new LineMesh(GL_TRIANGLES);
    private final LineMesh statBarFill = new LineMesh(GL_TRIANGLES);
    private final LineMesh durabilityBarBackground = new LineMesh(GL_TRIANGLES);
    private final LineMesh durabilityBarFill = new LineMesh(GL_TRIANGLES);
    private final LineMesh settingsPanel = new LineMesh(GL_TRIANGLES);

    private final Matrix4f identity = new Matrix4f();
    private final Matrix4f modelMatrix = new Matrix4f();
    private final Matrix4f hudTransform = new Matrix4f();

    public Hud(Shader lineShader, Shader hudShader, FontAtlas font) {
        this.lineShader = lineShader;
        this.hudShader = hudShader;
        this.text = new TextRenderer(font, hudShader);
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

    /** @param breakFraction 0 (just looking at it) to 1 (about to break) - tints the outline red and thickens it as it climbs. */
    public void renderBlockOutline(Matrix4f projection, Matrix4f view, Vector3i blockPos, float breakFraction) {
        lineShader.bind();
        lineShader.setUniform("projection", projection);
        lineShader.setUniform("view", view);
        Matrix4f model = modelMatrix.identity().translate(blockPos.x, blockPos.y, blockPos.z);
        lineShader.setUniform("model", model);
        float p = Math.max(0f, Math.min(1f, breakFraction));
        lineShader.setUniform("color", new Vector4f(p, 0f, 0f, 0.6f + p * 0.35f));
        glLineWidth(2.5f + p * 2.5f);
        cubeOutline.render();
        lineShader.unbind();
    }

    private float hotbarWidth(int count) {
        return count * HOTBAR_SLOT_SIZE + (count - 1) * HOTBAR_SLOT_GAP;
    }

    /** X-center of hotbar slot {@code i} out of {@code count}, in logical (-1..1, square-viewport) space. */
    private float slotCenterX(int i, int count) {
        float leftEdge = -hotbarWidth(count) / 2f;
        return leftEdge + i * (HOTBAR_SLOT_SIZE + HOTBAR_SLOT_GAP) + HOTBAR_SLOT_SIZE / 2f;
    }

    private float slotCenterY() {
        return -1f + HOTBAR_BOTTOM_MARGIN + HOTBAR_SLOT_SIZE / 2f;
    }

    /** Y of the top edge of the hotbar's background panel, in logical space - the stat bars stack upward from here. */
    private float hotbarPanelTopY() {
        return slotCenterY() + HOTBAR_SLOT_SIZE / 2f + HOTBAR_PADDING;
    }

    public void renderHotbar(TextureAtlas atlas, ItemTextures itemTextures, ToolDurability durability,
                              BlockType[] hotbar, Inventory inventory, int selectedSlot, float aspectRatio) {
        glDisable(GL_DEPTH_TEST);

        int count = hotbar.length;
        float centerY = slotCenterY();

        hudTransform.identity().scale(1f / aspectRatio, 1f, 1f);

        // Background panel behind the whole row.
        float totalWidth = hotbarWidth(count);
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

        // Icons come from three separate texture sources, so they can't all
        // batch into one draw call the way they used to: block icons still
        // batch together (one shared atlas), each item icon needs its own
        // individual bind+draw (its own PNG), and the count text batches
        // together again through the shared font atlas + TextRenderer.
        List<Float> blockVertices = new ArrayList<>();
        List<Integer> blockIndices = new ArrayList<>();
        int[] blockVertexCounter = {0};

        List<float[]> durabilityBars = new ArrayList<>(); // {cx, fraction} for worn tool slots only

        float iconHalf = HOTBAR_SLOT_SIZE / 2f - 0.008f;

        text.begin();

        for (int i = 0; i < count; i++) {
            float cx = slotCenterX(i, count);
            BlockType type = hotbar[i];

            if (type.isItem) {
                // Own texture, own draw call - can't be batched with the shared atlas.
                float[] quadVerts = {
                        cx - iconHalf, centerY - iconHalf, 0f, 1f,
                        cx + iconHalf, centerY - iconHalf, 1f, 1f,
                        cx + iconHalf, centerY + iconHalf, 1f, 0f,
                        cx - iconHalf, centerY + iconHalf, 0f, 0f,
                };
                hotbarItemIcon.upload(quadVerts, QUAD_INDICES);

                hudShader.bind();
                hudShader.setUniform("transform", hudTransform);
                hudShader.setUniform("atlas", 0);
                hudShader.setUniform("color", WHITE);
                itemTextures.bind(type);
                hotbarItemIcon.render();
                hudShader.unbind();
            } else {
                addQuad(blockVertices, blockIndices, blockVertexCounter,
                        cx - iconHalf, centerY - iconHalf, cx + iconHalf, centerY + iconHalf,
                        atlas.getUV(type.topTile));
            }

            if (Mining.isTool(type)) {
                float fraction = durability.fraction(type);
                if (fraction < 1f) {
                    durabilityBars.add(new float[]{cx, fraction});
                }
            }

            int amount = inventory.getCount(type);
            if (amount > 0) {
                String countText = Integer.toString(Math.min(amount, 999));
                float digitSize = 0.032f;
                float textWidth = text.measure(countText, digitSize);
                // Right-aligned to the icon's top corner; y is the text block's bottom edge.
                text.add(countText, cx + iconHalf - textWidth, centerY + iconHalf - digitSize, digitSize);
            }
        }

        hudShader.bind();
        hudShader.setUniform("transform", hudTransform);
        hudShader.setUniform("atlas", 0);
        hudShader.setUniform("color", WHITE);

        hotbarBlockIcons.upload(toFloatArray(blockVertices), toIntArray(blockIndices));
        atlas.bind();
        hotbarBlockIcons.render();

        hudShader.unbind();

        text.render(hudTransform, WHITE);

        if (!durabilityBars.isEmpty()) {
            lineShader.bind();
            lineShader.setUniform("projection", identity);
            lineShader.setUniform("view", identity);
            lineShader.setUniform("model", hudTransform);
            float barY1 = centerY - iconHalf;                 // icon's bottom edge
            float barY0 = barY1 - DURABILITY_BAR_HEIGHT;
            for (float[] bar : durabilityBars) {
                renderDurabilityBar(bar[0] - iconHalf, bar[0] + iconHalf, barY0, barY1, bar[1]);
            }
            lineShader.unbind();
        }

        glEnable(GL_DEPTH_TEST);
    }

    /** Draws one tool's wear strip: dark background + a fill that shrinks and shifts green->yellow->red as it wears. */
    private void renderDurabilityBar(float minX, float maxX, float minY, float maxY, float fraction) {
        float[] bg = {
                minX, minY, 0, maxX, minY, 0, maxX, maxY, 0,
                minX, minY, 0, maxX, maxY, 0, minX, maxY, 0,
        };
        durabilityBarBackground.upload(bg);
        lineShader.setUniform("color", new Vector4f(0f, 0f, 0f, 0.6f));
        durabilityBarBackground.render();

        float clamped = Math.max(0f, Math.min(1f, fraction));
        float fillMaxX = minX + (maxX - minX) * clamped;
        float r = clamped < 0.5f ? 1f : (1f - clamped) * 2f;
        float g = clamped > 0.5f ? 1f : clamped * 2f;
        float[] fill = {
                minX, minY, 0, fillMaxX, minY, 0, fillMaxX, maxY, 0,
                minX, minY, 0, fillMaxX, maxY, 0, minX, maxY, 0,
        };
        durabilityBarFill.upload(fill);
        lineShader.setUniform("color", new Vector4f(r, g, 0f, 0.95f));
        durabilityBarFill.render();
    }

    private static final int[] QUAD_INDICES = {0, 1, 2, 0, 2, 3};

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

    /** Health (red), hunger (orange) and stamina (yellow) bars, stacked above the hotbar. */
    public void renderStatusBars(float health, float maxHealth, float hunger, float maxHunger,
                                  float stamina, float maxStamina, int hotbarSlotCount, float aspectRatio) {
        glDisable(GL_DEPTH_TEST);
        hudTransform.identity().scale(1f / aspectRatio, 1f, 1f);

        lineShader.bind();
        lineShader.setUniform("projection", identity);
        lineShader.setUniform("view", identity);
        lineShader.setUniform("model", hudTransform);

        float width = hotbarWidth(hotbarSlotCount);
        float minX = -width / 2f;
        float maxX = width / 2f;
        float y = hotbarPanelTopY() + STAT_BAR_STACK_MARGIN;

        // Bottom to top: stamina, hunger, health - health ends up on top, most prominent.
        y = renderStatBar(minX, maxX, y, stamina / maxStamina, new Vector4f(0.92f, 0.80f, 0.15f, 0.95f));
        y = renderStatBar(minX, maxX, y, hunger / maxHunger, new Vector4f(0.85f, 0.55f, 0.15f, 0.95f));
        renderStatBar(minX, maxX, y, health / maxHealth, new Vector4f(0.82f, 0.15f, 0.15f, 0.95f));

        lineShader.unbind();
        glEnable(GL_DEPTH_TEST);
    }

    /** Draws one bar (dark background + colored fill proportional to {@code fraction}) and returns the y just above it. */
    private float renderStatBar(float minX, float maxX, float bottomY, float fraction, Vector4f fillColor) {
        float topY = bottomY + STAT_BAR_HEIGHT;
        float[] bg = {
                minX, bottomY, 0, maxX, bottomY, 0, maxX, topY, 0,
                minX, bottomY, 0, maxX, topY, 0, minX, topY, 0,
        };
        statBarBackground.upload(bg);
        lineShader.setUniform("color", new Vector4f(0f, 0f, 0f, 0.45f));
        statBarBackground.render();

        float clamped = Math.max(0f, Math.min(1f, fraction));
        if (clamped > 0f) {
            float fillMaxX = minX + (maxX - minX) * clamped;
            float[] fill = {
                    minX, bottomY, 0, fillMaxX, bottomY, 0, fillMaxX, topY, 0,
                    minX, bottomY, 0, fillMaxX, topY, 0, minX, topY, 0,
            };
            statBarFill.upload(fill);
            lineShader.setUniform("color", fillColor);
            statBarFill.render();
        }

        return topY + STAT_BAR_GAP;
    }

    /**
     * A transient line of on-screen text with its own color and lifetime.
     * {@code Main} owns the list and updates {@link #age} / drops expired
     * entries; {@link Hud#renderMessages} just draws what's still alive.
     */
    public static class Message {
        public final String text;
        public final Vector4f color;
        public final float duration;
        public float age;

        public Message(String text, Vector4f color, float duration) {
            this.text = text;
            this.color = color;
            this.duration = duration;
        }

        public boolean isExpired() {
            return age >= duration;
        }
    }

    /**
     * Draws each still-visible {@link Message} centered horizontally, stacked
     * upward from just below the crosshair area, in its own color.
     */
    public void renderMessages(List<Message> messages, float aspectRatio) {
        if (messages.isEmpty()) return;
        glDisable(GL_DEPTH_TEST);
        hudTransform.identity().scale(1f / aspectRatio, 1f, 1f);

        float size = 0.04f;
        float lineHeight = 0.055f;
        float bottomY = 0.12f;
        for (Message m : messages) {
            if (m.isExpired()) continue;
            text.begin();
            float width = text.measure(m.text, size);
            text.add(m.text, -width / 2f, bottomY, size);
            text.render(hudTransform, m.color);
            bottomY += lineHeight;
        }

        glEnable(GL_DEPTH_TEST);
    }

    /** Draws a single left-aligned line of text (e.g. the F3 debug overlay). */
    public void drawTextLeft(String value, float x, float bottomY, float size, Vector4f color, float aspectRatio) {
        glDisable(GL_DEPTH_TEST);
        hudTransform.identity().scale(1f / aspectRatio, 1f, 1f);

        text.begin();
        text.add(value, x, bottomY, size);
        text.render(hudTransform, color);

        glEnable(GL_DEPTH_TEST);
    }

    /**
     * Draws the pause/settings menu: a semi-transparent panel with a title, one
     * row per toggle in {@link Settings}, and a highlighted selection marker.
     * {@code selectedIndex} points at the currently-highlighted row.
     */
    public void renderSettingsMenu(Settings settings, int selectedIndex, float aspectRatio) {
        glDisable(GL_DEPTH_TEST);
        hudTransform.identity().scale(1f / aspectRatio, 1f, 1f);

        String[] labels = settings.ROW_LABELS;
        int rows = labels.length;
        float panelW = 0.58f;
        float rowH = 0.05f;
        float titleH = 0.07f;
        float pad = 0.035f;
        float panelH = pad * 2f + titleH + rows * rowH;
        float centerY = 0.12f;
        float left = -panelW / 2f;
        float top = centerY + panelH / 2f;

        // Semi-transparent panel background.
        float[] panel = {
                left, centerY - panelH / 2f, 0, left + panelW, centerY - panelH / 2f, 0, left + panelW, centerY + panelH / 2f, 0,
                left, centerY - panelH / 2f, 0, left + panelW, centerY + panelH / 2f, 0, left, centerY + panelH / 2f, 0,
        };
        settingsPanel.upload(panel);
        lineShader.bind();
        lineShader.setUniform("projection", identity);
        lineShader.setUniform("view", identity);
        lineShader.setUniform("model", hudTransform);
        lineShader.setUniform("color", new Vector4f(0f, 0f, 0f, 0.55f));
        settingsPanel.render();
        lineShader.unbind();

        // Title, centered near the top of the panel.
        drawCenteredText("Settings", 0f, top - pad - 0.045f, 0.045f, WHITE);

        // One row per setting: a ">" marker + label on the left, ON/OFF on the right.
        Vector4f idle = new Vector4f(0.88f, 0.88f, 0.88f, 1f);
        Vector4f idleValue = new Vector4f(0.7f, 0.7f, 0.7f, 1f);
        Vector4f highlight = new Vector4f(1f, 0.85f, 0.4f, 1f);
        for (int i = 0; i < rows; i++) {
            float rowTop = top - pad - titleH - i * rowH;
            float baseline = rowTop - rowH + 0.015f;
            boolean selected = i == selectedIndex;
            drawTextAt(selected ? ">" : " ", left + 0.05f, baseline, 0.038f, selected ? highlight : idle);
            drawTextAt(labels[i], left + 0.09f, baseline, 0.038f, selected ? highlight : idle);
            String value = settings.toggles[i] ? "ON" : "OFF";
            float valueWidth = text.measure(value, 0.038f);
            drawTextAt(value, left + panelW - 0.06f - valueWidth, baseline, 0.038f, selected ? highlight : idleValue);
        }

        drawCenteredText("Enter/Space: toggle    Esc: close", 0f, centerY - panelH / 2f - 0.05f, 0.028f, idleValue);

        glEnable(GL_DEPTH_TEST);
    }

    /** Draws one line of text left-aligned at (bottomLeftX, bottomY), assuming {@link #hudTransform} is already set up. */
    private void drawTextAt(String value, float bottomLeftX, float bottomY, float size, Vector4f color) {
        text.begin();
        text.add(value, bottomLeftX, bottomY, size);
        text.render(hudTransform, color);
    }

    /** Draws one line of text horizontally centered on {@code centerX}, assuming {@link #hudTransform} is already set up. */
    private void drawCenteredText(String value, float centerX, float bottomY, float size, Vector4f color) {
        text.begin();
        text.add(value, centerX - text.measure(value, size) / 2f, bottomY, size);
        text.render(hudTransform, color);
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
        hotbarBlockIcons.destroy();
        hotbarItemIcon.destroy();
        text.destroy();
        statBarBackground.destroy();
        statBarFill.destroy();
        durabilityBarBackground.destroy();
        durabilityBarFill.destroy();
        settingsPanel.destroy();
    }
}
