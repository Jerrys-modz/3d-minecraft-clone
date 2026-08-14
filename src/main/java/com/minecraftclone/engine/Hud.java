package com.minecraftclone.engine;

import com.minecraftclone.Settings;
import com.minecraftclone.engine.graphics.FontAtlas;
import com.minecraftclone.engine.graphics.IconMesh;
import com.minecraftclone.engine.graphics.ItemTextures;
import com.minecraftclone.engine.graphics.LineMesh;
import com.minecraftclone.engine.graphics.TextRenderer;
import com.minecraftclone.engine.graphics.TextureAtlas;
import com.minecraftclone.player.Crafting;
import com.minecraftclone.player.CraftingGrid;
import com.minecraftclone.player.Inventory;
import com.minecraftclone.player.InventoryController;
import com.minecraftclone.player.ToolDurability;
import com.minecraftclone.util.FloatArray;
import com.minecraftclone.util.IntArray;
import com.minecraftclone.world.BlockType;
import com.minecraftclone.world.Mining;
import org.joml.Matrix4f;
import org.joml.Vector3i;
import org.joml.Vector4f;

import java.util.List;

import static org.lwjgl.opengl.GL11.*;

/**
 * Draws all 2D overlay elements: the crosshair, the wireframe outline around
 * the targeted block, the hotbar (a 9-slot strip of the player's inventory,
 * with the selected slot highlighted), the health/hunger/stamina bars,
 * transient on-screen messages, the F3 debug overlay, and the Minecraft-style
 * inventory screen (36 slots + a 3x3 crafting grid with an output slot).
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

    // Inventory screen layout (logical square units).
    private static final float INV_SLOT = 0.082f;
    private static final float INV_GAP = 0.012f;
    private static final float INV_STEP = INV_SLOT + INV_GAP;
    private static final float INV_GRID_CENTER_X = 0.12f;   // horizontal center of the 9-wide inventory grid
    private static final float INV_TOP_ROW_Y = 0.16f;       // center y of the inventory's top row
    private static final float CRAFT_LEFT_X = -0.86f + INV_SLOT / 2f; // center x of the crafting grid's left column
    private static final float CRAFT_TOP_ROW_Y = INV_TOP_ROW_Y;
    private static final float OUTPUT_X = -0.50f;           // crafting result slot
    private static final float OUTPUT_Y = INV_TOP_ROW_Y - INV_STEP;

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
    private final LineMesh inventoryPanel = new LineMesh(GL_TRIANGLES);
    private final LineMesh inventorySlotBg = new LineMesh(GL_TRIANGLES);
    private final LineMesh inventoryHover = new LineMesh(GL_LINES);

    // Reusable per-frame scratch buffers for the hotbar icon batch and wear bars,
    // so building the HUD doesn't allocate (or box) anything on the hot path.
    private final FloatArray blockVertices = new FloatArray(1024);
    private final IntArray blockIndices = new IntArray(1024);
    private final FloatArray slotBgVerts = new FloatArray(1024);
    private final FloatArray barCx = new FloatArray(64);
    private final FloatArray barCy = new FloatArray(64);
    private final FloatArray barFrac = new FloatArray(64);
    private int blockVertexCounter;

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
    public void renderBlockOutline(Matrix4f projection, Matrix4f view, Vector3i blockPos, float breakFraction, float height) {
        lineShader.bind();
        lineShader.setUniform("projection", projection);
        lineShader.setUniform("view", view);
        // Scale Y by the block's height so slabs get a half-height outline.
        Matrix4f model = modelMatrix.identity().translate(blockPos.x, blockPos.y, blockPos.z).scale(1f, height, 1f);
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

    /** Renders the in-game 9-slot hotbar: the player's first 9 inventory slots. */
    public void renderHotbar(TextureAtlas atlas, ItemTextures itemTextures, ToolDurability durability,
                              Inventory inventory, int selectedSlot, float aspectRatio) {
        glDisable(GL_DEPTH_TEST);

        int count = Inventory.HOTBAR_SIZE;
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
        hotbarHighlight.upload(outlineLines(selCenterX, centerY, hs));
        lineShader.setUniform("color", new Vector4f(1f, 1f, 1f, 0.95f));
        glLineWidth(2f);
        hotbarHighlight.render();
        lineShader.unbind();

        // Icons + counts + wear bars, using the same batched path as the inventory screen.
        beginSlotBatch();
        float iconHalf = HOTBAR_SLOT_SIZE / 2f - 0.008f;
        for (int i = 0; i < count; i++) {
            addSlotIcon(slotCenterX(i, count), centerY, iconHalf,
                    inventory.typeOf(i), inventory.countOf(i), itemTextures, atlas, durability);
        }
        flushBlockBatch(atlas);
        text.render(hudTransform, WHITE);

        // Small slot-number labels (1-9) in the corner of each slot.
        text.begin();
        for (int i = 0; i < count; i++) {
            text.add(String.valueOf(i + 1), slotCenterX(i, count) - iconHalf + 0.006f, centerY - iconHalf + 0.002f, 0.022f);
        }
        text.render(hudTransform, new Vector4f(0.55f, 0.55f, 0.55f, 1f));

        renderDurabilityBars(iconHalf);

        glEnable(GL_DEPTH_TEST);
    }

    /** Resets the per-frame scratch buffers for a fresh slot batch. */
    private void beginSlotBatch() {
        blockVertices.clear();
        blockIndices.clear();
        blockVertexCounter = 0;
        barCx.clear();
        barCy.clear();
        barFrac.clear();
        text.begin();
    }

    /**
     * Adds one slot's icon to the batch: block icons accumulate into the shared
     * atlas batch, item icons draw immediately (their own texture), counts go
     * to the text batch, and worn tools record a wear-bar entry.
     */
    private void addSlotIcon(float cx, float cy, float half, BlockType type, int count,
                             ItemTextures itemTextures, TextureAtlas atlas, ToolDurability durability) {
        if (type == null) return;
        if (type.isItem) {
            float[] qv = {
                    cx - half, cy - half, 0f, 1f,
                    cx + half, cy - half, 1f, 1f,
                    cx + half, cy + half, 1f, 0f,
                    cx - half, cy + half, 0f, 0f,
            };
            hotbarItemIcon.upload(qv, QUAD_INDICES);
            hudShader.bind();
            hudShader.setUniform("transform", hudTransform);
            hudShader.setUniform("atlas", 0);
            hudShader.setUniform("color", WHITE);
            itemTextures.bind(type);
            hotbarItemIcon.render();
            hudShader.unbind();
        } else {
            addQuad(cx - half, cy - half, cx + half, cy + half, atlas.getUV(type.topTile));
        }

        if (count > 1) {
            String countText = Integer.toString(Math.min(count, 999));
            float digitSize = 0.028f;
            text.add(countText, cx + half - text.measure(countText, digitSize), cy - half, digitSize);
        }
        if (Mining.isTool(type)) {
            float fraction = durability.fraction(type);
            if (fraction < 1f) {
                barCx.add(cx);
                barCy.add(cy);
                barFrac.add(fraction);
            }
        }
    }

    /** Uploads and draws the accumulated block-icon batch. */
    private void flushBlockBatch(TextureAtlas atlas) {
        hudShader.bind();
        hudShader.setUniform("transform", hudTransform);
        hudShader.setUniform("atlas", 0);
        hudShader.setUniform("color", WHITE);
        hotbarBlockIcons.upload(blockVertices.toArray(), blockIndices.toArray());
        atlas.bind();
        hotbarBlockIcons.render();
        hudShader.unbind();
    }

    /** Draws the wear bars recorded by {@link #addSlotIcon}, spanning each tool icon's width. */
    private void renderDurabilityBars(float iconHalf) {
        if (barCx.isEmpty()) return;
        lineShader.bind();
        lineShader.setUniform("projection", identity);
        lineShader.setUniform("view", identity);
        lineShader.setUniform("model", hudTransform);
        for (int i = 0; i < barCx.size(); i++) {
            float cx = barCx.get(i);
            float cy = barCy.get(i);
            float y0 = cy - iconHalf;
            renderDurabilityBar(cx - iconHalf, cx + iconHalf, y0, y0 + DURABILITY_BAR_HEIGHT, barFrac.get(i));
        }
        lineShader.unbind();
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
     * row per setting in {@link Settings}, and a highlighted selection marker.
     * The panel sizes itself to the widest row so labels never overflow it.
     * {@code selectedIndex} points at the currently-highlighted row.
     */
    public void renderSettingsMenu(Settings settings, int selectedIndex, float aspectRatio) {
        glDisable(GL_DEPTH_TEST);
        hudTransform.identity().scale(1f / aspectRatio, 1f, 1f);

        int rows = Settings.ROW_COUNT;
        float size = 0.034f;
        float leftPad = 0.075f;   // panel-left edge to label start
        float rightPad = 0.07f;   // panel-right edge to value end
        float labelValueGap = 0.1f;

        // Panel width adapts to the longest row so nothing ever overflows.
        float widest = 0f;
        for (int i = 0; i < rows; i++) {
            widest = Math.max(widest,
                    text.measure(Settings.label(i), size) + labelValueGap + text.measure(settings.valueText(i), size));
        }
        float panelW = widest + leftPad + rightPad;

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
        drawCenteredText("Settings", 0f, top - pad - 0.04f, 0.042f, WHITE);

        // One row per setting: ">" marker + label on the left, value on the right.
        Vector4f idle = new Vector4f(0.88f, 0.88f, 0.88f, 1f);
        Vector4f idleValue = new Vector4f(0.7f, 0.7f, 0.7f, 1f);
        Vector4f highlight = new Vector4f(1f, 0.85f, 0.4f, 1f);
        for (int i = 0; i < rows; i++) {
            float rowTop = top - pad - titleH - i * rowH;
            float baseline = rowTop - rowH + 0.013f;
            boolean selected = i == selectedIndex;
            String value = settings.valueText(i);
            float valueWidth = text.measure(value, size);
            drawTextAt(selected ? ">" : " ", left + 0.04f, baseline, size, selected ? highlight : idle);
            drawTextAt(Settings.label(i), left + leftPad, baseline, size, selected ? highlight : idle);
            drawTextAt(value, left + panelW - rightPad - valueWidth, baseline, size, selected ? highlight : idleValue);
        }

        drawCenteredText("Up/Down: select    Left/Right: change    Esc: close",
                0f, centerY - panelH / 2f - 0.045f, 0.026f, idleValue);

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

    private void addQuad(float minX, float minY, float maxX, float maxY, float[] uv) {
        float u0 = uv[0], v0 = uv[1], u1 = uv[2], v1 = uv[3];
        int base = blockVertexCounter;
        blockVertices.add(minX); blockVertices.add(minY); blockVertices.add(u0); blockVertices.add(v1);
        blockVertices.add(maxX); blockVertices.add(minY); blockVertices.add(u1); blockVertices.add(v1);
        blockVertices.add(maxX); blockVertices.add(maxY); blockVertices.add(u1); blockVertices.add(v0);
        blockVertices.add(minX); blockVertices.add(maxY); blockVertices.add(u0); blockVertices.add(v0);
        blockIndices.add(base); blockIndices.add(base + 1); blockIndices.add(base + 2);
        blockIndices.add(base); blockIndices.add(base + 2); blockIndices.add(base + 3);
        blockVertexCounter += 4;
    }

    private void addQuad3(FloatArray out, float minX, float minY, float maxX, float maxY) {
        out.add(minX); out.add(minY); out.add(0);
        out.add(maxX); out.add(minY); out.add(0);
        out.add(maxX); out.add(maxY); out.add(0);
        out.add(minX); out.add(minY); out.add(0);
        out.add(maxX); out.add(maxY); out.add(0);
        out.add(minX); out.add(maxY); out.add(0);
    }

    private static float[] outlineLines(float cx, float cy, float half) {
        return new float[]{
                cx - half, cy - half, 0, cx + half, cy - half, 0,
                cx + half, cy - half, 0, cx + half, cy + half, 0,
                cx + half, cy + half, 0, cx - half, cy + half, 0,
                cx - half, cy + half, 0, cx - half, cy - half, 0,
        };
    }

    private float invGridWidth() {
        return 9 * INV_SLOT + 8 * INV_GAP;
    }

    private float invGridLeft() {
        return INV_GRID_CENTER_X - invGridWidth() / 2f + INV_SLOT / 2f;
    }

    /** Center (logical x, y) of the given slot id; see {@link InventoryController} for numbering. */
    private float[] slotCenter(int slotId) {
        if (slotId == InventoryController.OUTPUT_SLOT) {
            return new float[]{OUTPUT_X, OUTPUT_Y};
        }
        if (slotId >= Inventory.SIZE) {
            int g = slotId - Inventory.SIZE;
            int r = g / CraftingGrid.WIDTH, c = g % CraftingGrid.WIDTH;
            return new float[]{CRAFT_LEFT_X + c * INV_STEP, CRAFT_TOP_ROW_Y - r * INV_STEP};
        }
        int r, c;
        if (slotId < Inventory.HOTBAR_SIZE) {
            r = 3;
            c = slotId;
        } else {
            int s = slotId - Inventory.HOTBAR_SIZE;
            r = s / 9;
            c = s % 9;
        }
        return new float[]{invGridLeft() + c * INV_STEP, INV_TOP_ROW_Y - r * INV_STEP};
    }

    /** Resolves a mouse position (in logical-square coords) to a slot id, or -1 if it's over nothing. */
    public int inventorySlotAt(float logicalX, float logicalY) {
        for (int id = 0; id <= InventoryController.OUTPUT_SLOT; id++) {
            float[] c = slotCenter(id);
            float half = INV_SLOT / 2f;
            if (Math.abs(logicalX - c[0]) <= half && Math.abs(logicalY - c[1]) <= half) {
                return id;
            }
        }
        return -1;
    }

    /**
     * Draws the full Minecraft-style inventory screen: the 36-slot inventory
     * grid (with the hotbar as its bottom row), the 3x3 crafting grid, its
     * output slot, the cursor stack following the mouse, and hover highlight.
     * {@code cursorLx}/{@code cursorLy} are the mouse position in logical-square
     * coordinates, so the cursor stack can track it.
     */
    public void renderInventory(Inventory inventory, CraftingGrid grid, InventoryController controller,
                                int hoveredSlot, TextureAtlas atlas, ItemTextures itemTextures,
                                ToolDurability durability, float aspectRatio, float cursorLx, float cursorLy) {
        glDisable(GL_DEPTH_TEST);
        hudTransform.identity().scale(1f / aspectRatio, 1f, 1f);

        // Panel background spanning the inventory grid and crafting area.
        float gridW = invGridWidth();
        float panelLeft = CRAFT_LEFT_X - INV_SLOT / 2f - 0.03f;
        float panelRight = INV_GRID_CENTER_X + gridW / 2f + 0.03f;
        float panelTop = INV_TOP_ROW_Y + INV_SLOT / 2f + 0.055f;
        float panelBottom = (INV_TOP_ROW_Y - 3 * INV_STEP) - INV_SLOT / 2f - 0.07f;
        float[] panel = {
                panelLeft, panelBottom, 0, panelRight, panelBottom, 0, panelRight, panelTop, 0,
                panelLeft, panelBottom, 0, panelRight, panelTop, 0, panelLeft, panelTop, 0,
        };
        inventoryPanel.upload(panel);
        lineShader.bind();
        lineShader.setUniform("projection", identity);
        lineShader.setUniform("view", identity);
        lineShader.setUniform("model", hudTransform);
        lineShader.setUniform("color", new Vector4f(0.78f, 0.78f, 0.78f, 0.35f));
        inventoryPanel.render();

        // Slot backgrounds (dark squares) for every inventory slot, grid cell and the output slot.
        slotBgVerts.clear();
        float half = INV_SLOT / 2f - 0.004f;
        for (int id = 0; id <= InventoryController.OUTPUT_SLOT; id++) {
            float[] c = slotCenter(id);
            addQuad3(slotBgVerts, c[0] - half, c[1] - half, c[0] + half, c[1] + half);
        }
        inventorySlotBg.upload(slotBgVerts.toArray());
        lineShader.setUniform("color", new Vector4f(0f, 0f, 0f, 0.35f));
        inventorySlotBg.render();

        // Hover highlight.
        if (hoveredSlot >= 0) {
            float[] c = slotCenter(hoveredSlot);
            inventoryHover.upload(outlineLines(c[0], c[1], INV_SLOT / 2f + 0.004f));
            lineShader.setUniform("color", new Vector4f(1f, 1f, 1f, 0.9f));
            glLineWidth(2f);
            inventoryHover.render();
        }
        lineShader.unbind();

        // Icons + counts + wear bars for every occupied slot.
        beginSlotBatch();
        float iconHalf = INV_SLOT / 2f - 0.006f;
        for (int i = 0; i < Inventory.SIZE; i++) {
            float[] c = slotCenter(i);
            addSlotIcon(c[0], c[1], iconHalf, inventory.typeOf(i), inventory.countOf(i), itemTextures, atlas, durability);
        }
        for (int i = 0; i < CraftingGrid.SIZE; i++) {
            float[] c = slotCenter(Inventory.SIZE + i);
            BlockType t = grid.get(i);
            if (t != null) addSlotIcon(c[0], c[1], iconHalf, t, 1, itemTextures, atlas, durability);
        }
        Crafting.Recipe recipe = Crafting.match(grid.snapshot());
        if (recipe != null) {
            float[] c = slotCenter(InventoryController.OUTPUT_SLOT);
            addSlotIcon(c[0], c[1], iconHalf, recipe.output(), recipe.outputAmount(), itemTextures, atlas, durability);
        }
        flushBlockBatch(atlas);
        text.render(hudTransform, WHITE);
        renderDurabilityBars(iconHalf);

        // Cursor stack following the mouse, drawn on top.
        if (controller.hasCursorItem()) {
            drawCursorStack(atlas, itemTextures, controller.cursorType(), controller.cursorCount(),
                    cursorLx + 0.02f, cursorLy - 0.02f);
        }

        // Title + hint line.
        drawCenteredText("Inventory", 0f, panelTop - 0.05f, 0.045f, WHITE);
        drawCenteredText("Left: pick up    Right: one    Shift-click: move    Drag: split    Esc: close",
                0f, panelBottom - 0.04f, 0.022f, new Vector4f(0.7f, 0.7f, 0.7f, 1f));

        glEnable(GL_DEPTH_TEST);
    }

    /** Draws the cursor stack (icon + count) at the given logical position, above everything else. */
    private void drawCursorStack(TextureAtlas atlas, ItemTextures itemTextures, BlockType type, int count, float cx, float cy) {
        float half = INV_SLOT / 2f;
        if (type.isItem) {
            float[] qv = {
                    cx - half, cy - half, 0f, 1f,
                    cx + half, cy - half, 1f, 1f,
                    cx + half, cy + half, 1f, 0f,
                    cx - half, cy + half, 0f, 0f,
            };
            hotbarItemIcon.upload(qv, QUAD_INDICES);
            hudShader.bind();
            hudShader.setUniform("transform", hudTransform);
            hudShader.setUniform("atlas", 0);
            hudShader.setUniform("color", WHITE);
            itemTextures.bind(type);
            hotbarItemIcon.render();
            hudShader.unbind();
        } else {
            blockVertices.clear();
            blockIndices.clear();
            blockVertexCounter = 0;
            addQuad(cx - half, cy - half, cx + half, cy + half, atlas.getUV(type.topTile));
            hotbarBlockIcons.upload(blockVertices.toArray(), blockIndices.toArray());
            hudShader.bind();
            hudShader.setUniform("transform", hudTransform);
            hudShader.setUniform("atlas", 0);
            hudShader.setUniform("color", WHITE);
            atlas.bind();
            hotbarBlockIcons.render();
            hudShader.unbind();
        }

        text.begin();
        String countText = Integer.toString(Math.min(count, 999));
        float digitSize = 0.028f;
        text.add(countText, cx + half - text.measure(countText, digitSize), cy - half, digitSize);
        text.render(hudTransform, WHITE);
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
        inventoryPanel.destroy();
        inventorySlotBg.destroy();
        inventoryHover.destroy();
    }
}
