package com.minecraftclone.engine;

import com.minecraftclone.Settings;
import com.minecraftclone.engine.KeyBindings;
import com.minecraftclone.engine.graphics.FontAtlas;
import com.minecraftclone.engine.graphics.GuiTextures;
import com.minecraftclone.engine.graphics.IconMesh;
import com.minecraftclone.engine.graphics.ItemTextures;
import com.minecraftclone.engine.graphics.LineMesh;
import com.minecraftclone.engine.graphics.TextRenderer;
import com.minecraftclone.engine.graphics.TextureAtlas;
import com.minecraftclone.engine.gui.ContainerGui;
import com.minecraftclone.player.Crafting;
import com.minecraftclone.player.CraftingGrid;
import com.minecraftclone.player.CreativeCatalog;
import com.minecraftclone.player.Inventory;
import com.minecraftclone.player.InventoryController;
import com.minecraftclone.player.ToolDurability;
import com.minecraftclone.world.gen.WorldGenSettings;import com.minecraftclone.util.FloatArray;
import com.minecraftclone.util.IntArray;
import com.minecraftclone.world.BlockType;
import com.minecraftclone.world.Furnace;
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
    /** Width of a Minecraft-style 9-slice panel's border (logical units). */
    private static final float GUI_BORDER = 0.018f;
    private static final float INV_GRID_CENTER_X = 0.12f;   // horizontal center of the 9-wide inventory grid
    private static final float INV_TOP_ROW_Y = 0.16f;       // center y of the inventory's top row
    private static final float CRAFT_LEFT_X = -0.86f + INV_SLOT / 2f; // center x of the crafting grid's left column
    private static final float CRAFT_TOP_ROW_Y = INV_TOP_ROW_Y;
    private static final float OUTPUT_X = -0.50f;           // crafting result slot
    private static final float OUTPUT_Y = INV_TOP_ROW_Y - INV_STEP;

    // Furnace GUI layout (logical square units). The input/fuel slots sit in a
    // column to the left of the inventory grid, the output between them and the
    // grid, with a burn flame and a progress arrow between the two columns.
    private static final float FURNACE_INPUT_X = -0.75f;   // center x of the input & fuel slots
    private static final float FURNACE_OUTPUT_X = -0.38f;  // center x of the output slot
    private static final float FURNACE_FUEL_Y = INV_TOP_ROW_Y - 2f * INV_STEP;
    private static final float FURNACE_MID_Y = INV_TOP_ROW_Y - INV_STEP; // output row / flame row
    private static final float FURNACE_FLAME_X = FURNACE_INPUT_X + 0.09f;
    private static final float FURNACE_ARROW_X0 = FURNACE_FLAME_X + 0.035f;
    private static final float FURNACE_ARROW_X1 = FURNACE_OUTPUT_X - 0.09f;

    // Chest GUI layout: a grid of the chest's slots (3x9 single, 6x9 double)
    // stacked directly above the player's 3x9 main grid (the hotbar sits below
    // that as row 3). The top row rises to fit however many rows the container
    // has.
    private static final int CHEST_COLUMNS = 9;

    // Creative inventory screen layout (logical square units).
    private static final float CAT_SLOT = 0.09f;
    private static final float CAT_GAP = 0.014f;
    private static final float CAT_STEP = CAT_SLOT + CAT_GAP;
    private static final float CAT_GRID_TOP_Y = 0.44f;      // center y of the catalog's first row
    private static final float TAB_W = 0.31f;
    private static final float TAB_GAP = 0.012f;
    private static final float TAB_CENTER_Y = 0.80f;        // center y of the tab strip
    private static final float TAB_H = 0.07f;

    // Settings menu layout (logical square units) - shared by rendering and mouse hit-testing.
    private static final float SETTINGS_SIZE = 0.034f;
    private static final float SETTINGS_LEFT_PAD = 0.075f;
    private static final float SETTINGS_RIGHT_PAD = 0.07f;
    private static final float SETTINGS_LABEL_GAP = 0.12f;  // label end -> slider/toggle start
    private static final float SETTINGS_TRACK_W = 0.22f;    // slider track width
    private static final float SETTINGS_VALUE_W = 0.24f;    // value text width beside the track
    private static final float SETTINGS_VALUE_GAP = 0.02f;  // track -> value text gap
    private static final float SETTINGS_ROW_H = 0.05f;
    private static final float SETTINGS_TITLE_H = 0.07f;
    private static final float SETTINGS_TAB_H = 0.055f;     // the tab strip under the title
    private static final float SETTINGS_TAB_GAP = 0.015f;
    private static final float SETTINGS_TAB_ROWS_GAP = 0.035f; // breathing room between the tabs and the first row
    private static final float SETTINGS_PAD = 0.035f;
    private static final float SETTINGS_CENTER_Y = 0.12f;
    // The panel is sized for the tallest tab (Controls: the keybind list) so the
    // tab strip stays in the same place when switching between tabs.
    private static final int SETTINGS_MAX_ROWS = Math.max(
            Settings.tabRowCount(Settings.TAB_GRAPHICS),
            Math.max(Settings.tabRowCount(Settings.TAB_GAMEPLAY),
                    Math.max(Settings.tabRowCount(Settings.TAB_AUDIO), KeyBindings.COUNT)));

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
    private final LineMesh tooltipPanel = new LineMesh(GL_TRIANGLES);
    private final LineMesh settingsTrack = new LineMesh(GL_TRIANGLES);
    private final LineMesh settingsFill = new LineMesh(GL_TRIANGLES);
    /** Textured GUI art (9-slice panels + slots, light/dark theme). */
    private GuiTextures guiTextures;
    private boolean darkGui = false;
    private final IconMesh guiQuadMesh = new IconMesh();
    private final FloatArray guiVerts = new FloatArray(512);
    private final IntArray guiInds = new IntArray(256);

    // Reusable per-frame scratch buffers for the hotbar icon batch and wear bars,
    // so building the HUD doesn't allocate (or box) anything on the hot path.
    private final FloatArray blockVertices = new FloatArray(1024);
    private final IntArray blockIndices = new IntArray(1024);
    private final FloatArray slotBgVerts = new FloatArray(1024);
    /** Scratch buffer for the furnace flame/arrow decoration quads. */
    private final FloatArray furnaceDeco = new FloatArray(32);
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

    /** Provides the GUI art (must be generated before any screen is drawn) and the current light/dark theme. */
    public void setGuiTextures(GuiTextures guiTextures, boolean darkGui) {
        this.guiTextures = guiTextures;
        this.darkGui = darkGui;
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
     * Draws the weather forecast panel: the current weather with the next change,
     * today's hourly forecast at 3-hour marks, and a rolling 7-day forecast. Like
     * a real forecast, it's less reliable the further out it is.
     */
    public void renderForecast(Climate climate, Calendar calendar, float aspectRatio) {
        glDisable(GL_DEPTH_TEST);
        hudTransform.identity().scale(1f / aspectRatio, 1f, 1f);

        float x = -0.85f;
        float y = 0.88f;
        Vector4f dim = new Vector4f(0.7f, 0.7f, 0.7f, 1f);

        drawTextAt("Weather Forecast", x, y, 0.035f, WHITE);
        y -= 0.05f;
        drawTextAt("Now: " + forecastLabel(climate.getWeather(), climate.getWeatherStrength())
                        + "   next: " + climate.nextWeatherChange().displayName + " in ~"
                        + climate.hoursUntilChange() + "h",
                x, y, 0.024f, WHITE);
        y -= 0.045f;

        // Today's hourly forecast, at 3-hour marks from the current hour.
        Climate.ForecastSlot[] today = climate.getHourlyForecastForDay(0);
        Climate.ForecastSlot[] tomorrow = climate.getHourlyForecastForDay(1);
        int now = climate.getCurrentHourOfDay();
        StringBuilder hourly = new StringBuilder("Today: ");
        int count = 0;
        for (int h = now; count < 8 && h < now + 24; h += 3) {
            Climate.ForecastSlot slot = h < 24 ? today[h] : tomorrow[h - 24];
            if (count > 0) hourly.append(", ");
            hourly.append(h % 24).append("h ").append(slot.weather().displayName);
            count++;
        }
        drawTextAt(hourly.toString(), x, y, 0.022f, WHITE);
        y -= 0.05f;

        // Rolling 7-day forecast.
        Climate.ForecastSlot[] days = climate.getDailyForecast();
        for (int d = 0; d < days.length; d++) {
            String day = calendar.dayOfWeekNameAt(climate.getDailyDayIndex(d));
            drawTextAt(day + ": " + forecastLabel(days[d].weather(), days[d].strength()), x, y, 0.024f, WHITE);
            y -= 0.038f;
        }

        drawTextAt("Forecast less reliable further out", x, y - 0.005f, 0.018f, dim);
        glEnable(GL_DEPTH_TEST);
    }

    /** A forecast label: "Rain (heavy)" etc., with no strength for clear skies. */
    private static String forecastLabel(Weather weather, float strength) {
        if (!weather.isPrecipitation()) {
            return weather.displayName;
        }
        String s = strength < 0.4f ? "light" : strength < 0.75f ? "moderate" : "heavy";
        return weather.displayName + " (" + s + ")";
    }

    /**
     * Draws the pause/settings menu: a semi-transparent panel with a title, a
     * tab strip (Graphics / Gameplay / Controls) under it, and the rows of the
     * active tab - setting rows for the first two tabs, the keybind list for
     * Controls. The panel is always sized for the tallest tab so the tabs stay
     * put when switching. {@code selectedIndex} is a row index within the
     * active tab; {@code capturingAction} >= 0 means that keybind row is
     * waiting for a key press.
     */
    public void renderSettingsMenu(Settings settings, int selectedTab, int selectedIndex, int capturingAction, float aspectRatio) {
        glDisable(GL_DEPTH_TEST);
        hudTransform.identity().scale(1f / aspectRatio, 1f, 1f);

        float size = SETTINGS_SIZE;
        float panelW = settingsPanelWidth();
        float panelH = SETTINGS_PAD * 2f + SETTINGS_TITLE_H + SETTINGS_TAB_H + SETTINGS_TAB_ROWS_GAP
                + SETTINGS_MAX_ROWS * SETTINGS_ROW_H;
        float left = -panelW / 2f;
        float top = SETTINGS_CENTER_Y + panelH / 2f;

        // Minecraft-style textured panel background (9-slice) when GUI art is available.
        if (guiTextures != null) {
            guiVerts.clear();
            guiInds.clear();
            renderGuiPanel(left, SETTINGS_CENTER_Y - panelH / 2f, left + panelW, SETTINGS_CENTER_Y + panelH / 2f);
            flushGuiQuads();
        } else {
            float[] panel = {
                    left, SETTINGS_CENTER_Y - panelH / 2f, 0, left + panelW, SETTINGS_CENTER_Y - panelH / 2f, 0,
                    left + panelW, SETTINGS_CENTER_Y + panelH / 2f, 0,
                    left, SETTINGS_CENTER_Y - panelH / 2f, 0, left + panelW, SETTINGS_CENTER_Y + panelH / 2f, 0,
                    left, SETTINGS_CENTER_Y + panelH / 2f, 0,
            };
            settingsPanel.upload(panel);
            lineShader.bind();
            lineShader.setUniform("projection", identity);
            lineShader.setUniform("view", identity);
            lineShader.setUniform("model", hudTransform);
            lineShader.setUniform("color", new Vector4f(0f, 0f, 0f, 0.55f));
            settingsPanel.render();
            lineShader.unbind();
        }

        // Title, centered near the top of the panel.
        drawCenteredTextShadowed("Settings", 0f, top - SETTINGS_PAD - 0.04f, 0.042f, WHITE);

        Vector4f idle = new Vector4f(0.88f, 0.88f, 0.88f, 1f);
        Vector4f idleValue = new Vector4f(0.7f, 0.7f, 0.7f, 1f);
        Vector4f highlight = new Vector4f(1f, 0.85f, 0.4f, 1f);

        // Tab strip: one button per tab, the active one highlighted.
        float tabCenterY = settingsTabCenterY();
        for (int t = 0; t < Settings.TAB_COUNT; t++) {
            float tabCx = settingsTabCenterX(t);
            float tabW = settingsTabWidth(t);
            if (t == selectedTab) {
                float[] bg = {
                        tabCx - tabW / 2f, tabCenterY - SETTINGS_TAB_H / 2f, 0, tabCx + tabW / 2f, tabCenterY - SETTINGS_TAB_H / 2f, 0,
                        tabCx + tabW / 2f, tabCenterY + SETTINGS_TAB_H / 2f, 0,
                        tabCx - tabW / 2f, tabCenterY - SETTINGS_TAB_H / 2f, 0, tabCx + tabW / 2f, tabCenterY + SETTINGS_TAB_H / 2f, 0,
                        tabCx - tabW / 2f, tabCenterY + SETTINGS_TAB_H / 2f, 0,
                };
                settingsFill.upload(bg);
                lineShader.bind();
                lineShader.setUniform("projection", identity);
                lineShader.setUniform("view", identity);
                lineShader.setUniform("model", hudTransform);
                lineShader.setUniform("color", new Vector4f(0.3f, 0.3f, 0.3f, 0.9f));
                settingsFill.render();
                lineShader.unbind();
            }
            drawCenteredText(Settings.tabLabel(t), tabCx, tabCenterY - 0.024f, 0.03f,
                    t == selectedTab ? WHITE : idleValue);
        }

        // One row per entry in the active tab. The Controls tab shows the
        // keybind list; the other two show their Settings rows.
        int rows = settingsRowsForTab(selectedTab);
        if (selectedTab == Settings.TAB_CONTROLS) {
            for (int action = 0; action < rows; action++) {
                float baseline = settingsRowTop(selectedTab, action) - SETTINGS_ROW_H + 0.013f;
                boolean selected = action == selectedIndex;
                boolean capturing = action == capturingAction;
                Vector4f rowColor = selected || capturing ? highlight : idle;
                drawTextAt(selected ? ">" : " ", left + 0.04f, baseline, size, selected ? highlight : idle);
                drawTextAt(KeyBindings.name(action), left + SETTINGS_LEFT_PAD, baseline, size, rowColor);
                String keyText = capturing ? "?" : KeyBindings.keyName(settings.getKeyBinds().get(action));
                float valueWidth = text.measure(keyText, size);
                drawTextAt(keyText, left + panelW - SETTINGS_RIGHT_PAD - valueWidth, baseline, size,
                        capturing ? highlight : idleValue);
            }
        } else {
            for (int local = 0; local < rows; local++) {
                int row = settingsRowForTab(selectedTab, local);
                float baseline = settingsRowTop(selectedTab, local) - SETTINGS_ROW_H + 0.013f;
                boolean selected = local == selectedIndex;
                drawTextAt(selected ? ">" : " ", left + 0.04f, baseline, size, selected ? highlight : idle);
                drawTextAt(Settings.label(row), left + SETTINGS_LEFT_PAD, baseline, size, selected ? highlight : idle);
                String value = settings.valueText(row);
                float valueWidth = text.measure(value, size);
                drawTextAt(value, left + panelW - SETTINGS_RIGHT_PAD - valueWidth, baseline, size,
                        selected ? highlight : idleValue);
            }
        }

        // Sliders for the range rows of the active (non-Controls) tab.
        if (selectedTab != Settings.TAB_CONTROLS) {
            float[] cx = settingsControlX();
            lineShader.bind();
            lineShader.setUniform("projection", identity);
            lineShader.setUniform("view", identity);
            lineShader.setUniform("model", hudTransform);
            for (int local = 0; local < rows; local++) {
                int row = settingsRowForTab(selectedTab, local);
                if (Settings.isToggle(row)) continue;
                float trackY = settingsRowTop(selectedTab, local) - SETTINGS_ROW_H / 2f;
                float trackH = 0.012f;
                float frac = settings.fraction(row);
                float fillEnd = cx[0] + (cx[1] - cx[0]) * frac;

                // Track background.
                settingsTrack.upload(new float[]{
                        cx[0], trackY - trackH / 2f, 0, cx[1], trackY - trackH / 2f, 0, cx[1], trackY + trackH / 2f, 0,
                        cx[0], trackY - trackH / 2f, 0, cx[1], trackY + trackH / 2f, 0, cx[0], trackY + trackH / 2f, 0,
                });
                lineShader.setUniform("color", new Vector4f(0.15f, 0.15f, 0.15f, 0.8f));
                settingsTrack.render();

                // Fill up to the current value.
                if (frac > 0f) {
                    settingsFill.upload(new float[]{
                            cx[0], trackY - trackH / 2f, 0, fillEnd, trackY - trackH / 2f, 0, fillEnd, trackY + trackH / 2f, 0,
                            cx[0], trackY - trackH / 2f, 0, fillEnd, trackY + trackH / 2f, 0, cx[0], trackY + trackH / 2f, 0,
                    });
                    lineShader.setUniform("color", local == selectedIndex
                            ? new Vector4f(0.4f, 0.85f, 1f, 0.95f)
                            : new Vector4f(0.6f, 0.6f, 0.6f, 0.9f));
                    settingsFill.render();
                }

                // Knob.
                float knobHalf = 0.02f;
                settingsFill.upload(new float[]{
                        fillEnd - knobHalf, trackY - knobHalf, 0, fillEnd + knobHalf, trackY - knobHalf, 0,
                        fillEnd + knobHalf, trackY + knobHalf, 0,
                        fillEnd - knobHalf, trackY - knobHalf, 0, fillEnd + knobHalf, trackY + knobHalf, 0,
                        fillEnd - knobHalf, trackY + knobHalf, 0,
                });
                lineShader.setUniform("color", new Vector4f(0.95f, 0.95f, 0.95f, 1f));
                settingsFill.render();
            }
            lineShader.unbind();
        }

        drawCenteredText(selectedTab == Settings.TAB_CONTROLS
                        ? "Click/Enter: rebind    Tab: next section    Esc: close"
                        : "Click/Enter: toggle or adjust    Tab: next section    Esc: close",
                0f, SETTINGS_CENTER_Y - panelH / 2f - 0.045f, 0.026f, idleValue);

        glEnable(GL_DEPTH_TEST);
    }


    /** Main menu button indices. */
    public static final int MENU_PLAY = 0;
    public static final int MENU_SETTINGS = 1;
    public static final int MENU_QUIT = 2;
    public static final int MENU_COUNT = 3;

    /** The main menu (title screen) shown before a world is created. */
    public void renderMainMenu(int selectedIndex, float aspectRatio) {
        glDisable(GL_DEPTH_TEST);
        hudTransform.identity().scale(1f / aspectRatio, 1f, 1f);
        Vector4f idle = new Vector4f(0.88f, 0.88f, 0.88f, 1f);
        Vector4f highlight = new Vector4f(1f, 0.85f, 0.4f, 1f);
        drawCenteredText("3D Minecraft Clone", 0f, 0.5f, 0.085f, WHITE);
        String[] items = {"Play", "Settings", "Quit"};
        for (int i = 0; i < items.length; i++) {
            boolean selected = i == selectedIndex;
            float y = 0.05f - i * 0.1f;
            drawCenteredText(items[i], 0f, y, 0.045f, selected ? highlight : idle);
            if (selected) {
                float w = text.measure(items[i], 0.045f);
                drawCenteredText(">", -w / 2f - 0.06f, y, 0.045f, highlight);
            }
        }
        glEnable(GL_DEPTH_TEST);
    }

    /** The main-menu button under the mouse (Play/Settings/Quit), or -1. */
    public int mainMenuItemAt(float logicalX, float logicalY) {
        String[] items = {"Play", "Settings", "Quit"};
        for (int i = 0; i < items.length; i++) {
            float y = 0.05f - i * 0.1f;
            // Hover band around each button: about twice the text height and a
            // generous half-width so clicking the label or its surroundings works.
            if (Math.abs(logicalY - y) <= 0.045f && Math.abs(logicalX) <= 0.4f) {
                return i;
            }
        }
        return -1;
    }


    /** The world-selection screen (Minecraft singleplayer): saved worlds + a Create New World button. */
    public void renderWorldSelectMenu(java.util.List<String> worldNames, int selectedIndex, float aspectRatio) {
        glDisable(GL_DEPTH_TEST);
        hudTransform.identity().scale(1f / aspectRatio, 1f, 1f);
        Vector4f idle = new Vector4f(0.88f, 0.88f, 0.88f, 1f);
        Vector4f highlight = new Vector4f(1f, 0.85f, 0.4f, 1f);
        Vector4f dim = new Vector4f(0.62f, 0.62f, 0.62f, 1f);

        int rows = worldNames.size() + 1; // worlds + Create New World
        float panelW = 0.95f;
        float panelH = SETTINGS_PAD * 2f + SETTINGS_TITLE_H + rows * 0.07f;
        float left = -panelW / 2f;
        float top = 0.5f + panelH / 2f;

        // Minecraft-style textured panel background (9-slice) when GUI art is available.
        if (guiTextures != null) {
            guiVerts.clear();
            guiInds.clear();
            renderGuiPanel(left, 0.5f - panelH / 2f, left + panelW, 0.5f + panelH / 2f);
            flushGuiQuads();
        } else {
            float[] panel = {
                    left, 0.5f - panelH / 2f, 0, left + panelW, 0.5f - panelH / 2f, 0,
                    left + panelW, 0.5f + panelH / 2f, 0,
                    left, 0.5f - panelH / 2f, 0, left + panelW, 0.5f + panelH / 2f, 0,
                    left, 0.5f + panelH / 2f, 0,
            };
            settingsPanel.upload(panel);
            lineShader.bind();
            lineShader.setUniform("projection", identity);
            lineShader.setUniform("view", identity);
            lineShader.setUniform("model", hudTransform);
            lineShader.setUniform("color", new Vector4f(0f, 0f, 0f, 0.6f));
            settingsPanel.render();
            lineShader.unbind();
        }

        drawCenteredTextShadowed("Select World", 0f, top - SETTINGS_PAD - 0.04f, 0.07f, WHITE);

        float y = 0.3f;
        int shown = 0;
        int total = worldNames.size() + 1; // worlds + Create New World
        for (String name : worldNames) {
            boolean selected = shown == selectedIndex;
            drawCenteredText(name, 0f, y, 0.04f, selected ? highlight : idle);
            if (selected) {
                float w = text.measure(name, 0.04f);
                drawCenteredText(">", -w / 2f - 0.06f, y, 0.04f, highlight);
            }
            y -= 0.07f;
            shown++;
        }
        boolean selected = shown == selectedIndex;
        drawCenteredText("Create New World", 0f, y, 0.04f, selected ? highlight : idle);
        if (selected) {
            float w = text.measure("Create New World", 0.04f);
            drawCenteredText(">", -w / 2f - 0.06f, y, 0.04f, highlight);
        }
        glEnable(GL_DEPTH_TEST);
    }

    /**
     * The world-select entry under the mouse: the index of a saved world, or
     * {@code worldCount} for the Create New World button; -1 if over nothing.
     * Rows start at y=0.3 and step 0.07 downward, matching the renderer.
     */
    public int worldSelectItemAt(float logicalX, float logicalY, int worldCount) {
        int total = worldCount + 1;
        for (int i = 0; i < total; i++) {
            float y = 0.3f - i * 0.07f;
            if (Math.abs(logicalY - y) <= 0.05f && Math.abs(logicalX) <= 0.45f) {
                return i;
            }
        }
        return -1;
    }
    /** The world-generation settings page (Minecraft-style "More World Options"). */
    public void renderWorldGenMenu(WorldGenSettings wgs, int selectedIndex, int editingRow, float aspectRatio) {
        glDisable(GL_DEPTH_TEST);
        hudTransform.identity().scale(1f / aspectRatio, 1f, 1f);

        int rows = WorldGenSettings.ROW_COUNT + 1; // options + Done
        float size = SETTINGS_SIZE;
        float panelW = 0.95f;
        float panelH = SETTINGS_PAD * 2f + SETTINGS_TITLE_H + rows * SETTINGS_ROW_H;
        float left = -panelW / 2f;
        float top = SETTINGS_CENTER_Y + panelH / 2f;

        // Minecraft-style textured panel background (9-slice) when GUI art is available.
        if (guiTextures != null) {
            guiVerts.clear();
            guiInds.clear();
            renderGuiPanel(left, SETTINGS_CENTER_Y - panelH / 2f, left + panelW, SETTINGS_CENTER_Y + panelH / 2f);
            flushGuiQuads();
        } else {
            float[] panel = {
                    left, SETTINGS_CENTER_Y - panelH / 2f, 0, left + panelW, SETTINGS_CENTER_Y - panelH / 2f, 0,
                    left + panelW, SETTINGS_CENTER_Y + panelH / 2f, 0,
                    left, SETTINGS_CENTER_Y - panelH / 2f, 0, left + panelW, SETTINGS_CENTER_Y + panelH / 2f, 0,
                    left, SETTINGS_CENTER_Y + panelH / 2f, 0,
            };
            settingsPanel.upload(panel);
            lineShader.bind();
            lineShader.setUniform("projection", identity);
            lineShader.setUniform("view", identity);
            lineShader.setUniform("model", hudTransform);
            lineShader.setUniform("color", new Vector4f(0f, 0f, 0f, 0.6f));
            settingsPanel.render();
            lineShader.unbind();
        }

        drawCenteredTextShadowed("World Generation", 0f, top - SETTINGS_PAD - 0.04f, 0.042f, WHITE);

        Vector4f idle = new Vector4f(0.88f, 0.88f, 0.88f, 1f);
        Vector4f idleValue = new Vector4f(0.7f, 0.7f, 0.7f, 1f);
        Vector4f highlight = new Vector4f(1f, 0.85f, 0.4f, 1f);
        for (int i = 0; i < rows; i++) {
            float baseline = worldGenRowTop(i) - SETTINGS_ROW_H + 0.013f;
            boolean selected = i == selectedIndex;
            if (i < WorldGenSettings.ROW_COUNT) {
                boolean seedRow = i == WorldGenSettings.ROW_SEED;
                boolean activeSeed = i == editingRow;
                Vector4f color = selected ? highlight : (activeSeed ? highlight : idle);
                drawTextAt(selected ? ">" : " ", left + 0.04f, baseline, size, selected ? highlight : idle);
                drawTextAt(WorldGenSettings.label(i), left + SETTINGS_LEFT_PAD, baseline, size, color);
                String value = activeSeed ? wgs.valueText(i) + "_" : wgs.valueText(i);
                float valueWidth = text.measure(value, size);
                drawTextAt(value, left + panelW - SETTINGS_RIGHT_PAD - valueWidth, baseline, size,
                        activeSeed ? highlight : idleValue);
            } else {
                // Done / back button.
                drawTextAt(selected ? ">" : " ", left + 0.04f, baseline, size, selected ? highlight : idle);
                drawTextAt("Done", left + SETTINGS_LEFT_PAD, baseline, size, selected ? highlight : idle);
            }
        }

        drawCenteredText(editingRow >= 0 ? "Type (backspace deletes, Enter to keep)"
                : "Select a row, Enter to edit; Esc to close",
                0f, SETTINGS_CENTER_Y - panelH / 2f - 0.045f, 0.026f, idleValue);

        glEnable(GL_DEPTH_TEST);
    }
    /** Number of interactive rows for a settings tab (keybind actions on Controls, Settings rows elsewhere). */
    private static int settingsRowsForTab(int tab) {        return tab == Settings.TAB_CONTROLS ? KeyBindings.COUNT : Settings.tabRowCount(tab);
    }

    /** The Settings row index shown as local row {@code local} on {@code tab} (only valid for non-Controls tabs). */
    private static int settingsRowForTab(int tab, int local) {
        return Settings.rowInTab(tab, local);
    }

    /** Width of the settings panel: widest label + room for a slider track and its value text. */
    private float settingsPanelWidth() {
        float widest = 0f;
        for (int i = 0; i < Settings.ROW_COUNT; i++) {
            widest = Math.max(widest, text.measure(Settings.label(i), SETTINGS_SIZE));
        }
        for (int a = 0; a < KeyBindings.COUNT; a++) {
            widest = Math.max(widest, text.measure(KeyBindings.name(a), SETTINGS_SIZE));
        }
        return widest + SETTINGS_LABEL_GAP + SETTINGS_TRACK_W + SETTINGS_VALUE_GAP + SETTINGS_VALUE_W
                + SETTINGS_LEFT_PAD + SETTINGS_RIGHT_PAD;
    }

    /** Logical center-y of the tab strip. */
    private float settingsTabCenterY() {
        float panelH = SETTINGS_PAD * 2f + SETTINGS_TITLE_H + SETTINGS_TAB_H + SETTINGS_TAB_ROWS_GAP
                + SETTINGS_MAX_ROWS * SETTINGS_ROW_H;
        float top = SETTINGS_CENTER_Y + panelH / 2f;
        return top - SETTINGS_PAD - SETTINGS_TITLE_H - SETTINGS_TAB_H / 2f;
    }

    /** Width of tab {@code t}'s button. */
    private float settingsTabWidth(int t) {
        return text.measure(Settings.tabLabel(t), 0.03f) + 0.06f;
    }

    /** Logical center-x of tab {@code t}: the strip is centered, spaced by {@link #SETTINGS_TAB_GAP}. */
    private float settingsTabCenterX(int t) {
        float total = 0f;
        for (int i = 0; i < Settings.TAB_COUNT; i++) {
            total += settingsTabWidth(i) + SETTINGS_TAB_GAP;
        }
        total -= SETTINGS_TAB_GAP;
        float x = -total / 2f;
        for (int i = 0; i < t; i++) {
            x += settingsTabWidth(i) + SETTINGS_TAB_GAP;
        }
        return x + settingsTabWidth(t) / 2f;
    }

    /** Top edge (logical y) of row {@code i} on the given tab. */
    private float settingsRowTop(int tab, int i) {
        float panelH = SETTINGS_PAD * 2f + SETTINGS_TITLE_H + SETTINGS_TAB_H + SETTINGS_TAB_ROWS_GAP
                + SETTINGS_MAX_ROWS * SETTINGS_ROW_H;
        float top = SETTINGS_CENTER_Y + panelH / 2f;
        return top - SETTINGS_PAD - SETTINGS_TITLE_H - SETTINGS_TAB_H - SETTINGS_TAB_ROWS_GAP - i * SETTINGS_ROW_H;
    }

    /** Top edge (logical y) of row {@code i} in the world-generation page's own panel. */
    private float worldGenRowTop(int i) {
        int rows = WorldGenSettings.ROW_COUNT + 1;
        float panelH = SETTINGS_PAD * 2f + SETTINGS_TITLE_H + rows * SETTINGS_ROW_H;
        float top = SETTINGS_CENTER_Y + panelH / 2f;
        return top - SETTINGS_PAD - SETTINGS_TITLE_H - i * SETTINGS_ROW_H;
    }

    /** The world-gen row (0..ROW_COUNT, with ROW_COUNT being the Done button) under the mouse, or -1. */
    public int worldGenRowAt(float logicalX, float logicalY) {
        int rows = WorldGenSettings.ROW_COUNT + 1;
        float panelW = 0.95f;
        float left = -panelW / 2f;
        for (int i = 0; i < rows; i++) {
            float rowTop = worldGenRowTop(i);
            if (logicalX >= left && logicalX <= left + panelW
                    && logicalY <= rowTop && logicalY >= rowTop - SETTINGS_ROW_H) {
                return i;
            }
        }
        return -1;
    }

    /** Slider control x-range {left, right} for the settings rows. */
    private float[] settingsControlX() {
        float panelW = settingsPanelWidth();
        float left = -panelW / 2f;
        float right = left + panelW - SETTINGS_RIGHT_PAD - SETTINGS_VALUE_GAP - SETTINGS_VALUE_W;
        return new float[]{right - SETTINGS_TRACK_W, right};
    }

    /** The settings-tab button under the mouse, or -1. */
    public int settingsTabAt(float logicalX, float logicalY) {
        float cy = settingsTabCenterY();
        for (int t = 0; t < Settings.TAB_COUNT; t++) {
            float cx = settingsTabCenterX(t);
            if (Math.abs(logicalX - cx) <= settingsTabWidth(t) / 2f
                    && Math.abs(logicalY - cy) <= SETTINGS_TAB_H / 2f) {
                return t;
            }
        }
        return -1;
    }

    /** The settings-menu row (within tab {@code tab}) under the mouse, or -1. */
    public int settingsRowAt(float logicalX, float logicalY, int tab) {
        float panelW = settingsPanelWidth();
        float left = -panelW / 2f;
        for (int i = 0; i < settingsRowsForTab(tab); i++) {
            float rowTop = settingsRowTop(tab, i);
            if (logicalX >= left && logicalX <= left + panelW
                    && logicalY <= rowTop && logicalY >= rowTop - SETTINGS_ROW_H) {
                return i;
            }
        }
        return -1;
    }

    /** If the mouse is over a range row's slider track, the click fraction (0..1); otherwise -1. */
    public float settingsTrackAt(float logicalX, float logicalY, int tab) {
        int row = settingsRowAt(logicalX, logicalY, tab);
        if (row < 0 || tab == Settings.TAB_CONTROLS || Settings.isToggle(settingsRowForTab(tab, row))) return -1f;
        float[] cx = settingsControlX();
        if (logicalX < cx[0] - 0.012f || logicalX > cx[1] + 0.012f) return -1f;
        return settingsSliderAt(logicalX, row, tab);
    }

    /** Clamped slider fraction (0..1) from an x position - for dragging a known row. */
    public float settingsSliderAt(float logicalX, int row, int tab) {
        float[] cx = settingsControlX();
        return Math.max(0f, Math.min(1f, (logicalX - cx[0]) / (cx[1] - cx[0])));
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

    /** Like {@link #drawCenteredText} but with a dark drop shadow, so light text stays readable on the light GUI theme. */
    private void drawCenteredTextShadowed(String value, float centerX, float bottomY, float size, Vector4f color) {
        float shadowOffset = size * 0.06f;
        text.begin();
        text.add(value, centerX - text.measure(value, size) / 2f + shadowOffset, bottomY - shadowOffset, size);
        text.render(hudTransform, new Vector4f(0f, 0f, 0f, darkGui ? 0.9f : 0.45f));
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

    /** Center y of the chest grid's bottom row - fixed, so the gap to the player grid below it is always the same. */
    private static final float CHEST_BOTTOM_ROW_Y = INV_TOP_ROW_Y + 0.15f;

    /** Center y of a chest gui's top row: the fixed bottom row plus however many rows the chest has above it. */
    private float chestTopRowY(ContainerGui gui) {
        int rows = (gui.container().size() + CHEST_COLUMNS - 1) / CHEST_COLUMNS;
        return CHEST_BOTTOM_ROW_Y + (rows - 1) * INV_STEP;
    }

    /**
     * How far a chest gui shifts <em>down</em> to fit on screen. A tall chest
     * (e.g. a 2x2 = 108 slots = 12 rows) would otherwise push its top row above
     * the top edge; shifting the whole screen down keeps every slot the same
     * size. Zero for chests short enough to fit in the normal position.
     */
    private float chestLayoutShift(ContainerGui gui) {
        if (gui.kind() != ContainerGui.Kind.CHEST) return 0f;
        float panelTop = chestTopRowY(gui) + INV_SLOT / 2f + 0.055f;
        return Math.max(0f, panelTop - 1f);
    }

    /** Center (logical x, y) of the given slot id in the open gui; see {@link ContainerGui} for numbering. */
    private float[] slotCenter(ContainerGui gui, int slotId) {
        if (gui.isOutputSlot(slotId)) {
            return new float[]{OUTPUT_X, OUTPUT_Y};
        }
        if (gui.isGridSlot(slotId)) {
            int g = slotId - ContainerGui.GRID_START;
            int r = g / CraftingGrid.WIDTH, c = g % CraftingGrid.WIDTH;
            return new float[]{CRAFT_LEFT_X + c * INV_STEP, CRAFT_TOP_ROW_Y - r * INV_STEP};
        }
        if (gui.isContainerSlot(slotId)) {
            int cs = slotId - ContainerGui.CONTAINER_START;
            if (gui.kind() == ContainerGui.Kind.CHEST) {
                // A chest is a rows-by-9 grid of slots above the player's inventory.
                int r = cs / CHEST_COLUMNS, c = cs % CHEST_COLUMNS;
                return new float[]{invGridLeft() + c * INV_STEP, chestTopRowY(gui) - r * INV_STEP - chestLayoutShift(gui)};
            }
            // Furnace: a 3-slot column - input, fuel, output.
            int fs = cs;
            if (fs == Furnace.SLOT_OUTPUT) return new float[]{FURNACE_OUTPUT_X, FURNACE_MID_Y};
            float y = fs == Furnace.SLOT_INPUT ? INV_TOP_ROW_Y : FURNACE_FUEL_Y;
            return new float[]{FURNACE_INPUT_X, y};
        }
        if (gui.isPlayerSlot(slotId)) {
            int r, c;
            if (slotId < Inventory.HOTBAR_SIZE) {
                r = 3;
                c = slotId;
            } else {
                int s = slotId - Inventory.HOTBAR_SIZE;
                r = s / 9;
                c = s % 9;
            }
            return new float[]{invGridLeft() + c * INV_STEP, INV_TOP_ROW_Y - r * INV_STEP - chestLayoutShift(gui)};
        }
        return null;
    }

    /** Resolves a mouse position (in logical-square coords) to a slot id in the open gui, or -1 if it's over nothing. */
    public int containerSlotAt(ContainerGui gui, float logicalX, float logicalY) {
        for (int id = 0; id < gui.slotCount(); id++) {
            float[] c = slotCenter(gui, id);
            if (c == null) continue;
            float half = INV_SLOT / 2f;
            if (Math.abs(logicalX - c[0]) <= half && Math.abs(logicalY - c[1]) <= half) {
                return id;
            }
        }
        return -1;
    }

    /** Center (logical x) of creative tab {@code i} out of {@code count}. */
    private float tabCenterX(int i, int count) {
        float total = count * TAB_W + (count - 1) * TAB_GAP;
        float left = -total / 2f + TAB_W / 2f;
        return left + i * (TAB_W + TAB_GAP);
    }

    /** Center (logical x, y) of catalog item {@code index} (row-major, 9 per row). */
    private float[] catalogItemCenter(int index) {
        int r = index / 9, c = index % 9;
        float gridW = 9 * CAT_SLOT + 8 * CAT_GAP;
        float left = -gridW / 2f + CAT_SLOT / 2f;
        return new float[]{left + c * CAT_STEP, CAT_GRID_TOP_Y - r * CAT_STEP};
    }

    /** Center (logical x) of the creative "destroy item" slot, just right of the hotbar. */
    private float destroySlotX() {
        return slotCenterX(Inventory.HOTBAR_SIZE - 1, Inventory.HOTBAR_SIZE) + HOTBAR_SLOT_SIZE + HOTBAR_SLOT_GAP;
    }

    /** The creative tab under the mouse, or -1. */
    public int creativeTabAt(float logicalX, float logicalY) {
        int count = CreativeCatalog.TABS.length;
        for (int i = 0; i < count; i++) {
            float cx = tabCenterX(i, count);
            if (Math.abs(logicalX - cx) <= TAB_W / 2f && Math.abs(logicalY - TAB_CENTER_Y) <= TAB_H / 2f) return i;
        }
        return -1;
    }

    /** The index (into the given tab's items) of the catalog item under the mouse, or -1. */
    public int creativeItemAt(float logicalX, float logicalY, int tab) {
        BlockType[] items = CreativeCatalog.TABS[tab].items();
        float half = CAT_SLOT / 2f;
        for (int i = 0; i < items.length; i++) {
            float[] c = catalogItemCenter(i);
            if (Math.abs(logicalX - c[0]) <= half && Math.abs(logicalY - c[1]) <= half) return i;
        }
        return -1;
    }

    /** The hotbar slot (0-8) under the mouse using in-game hotbar geometry, or -1. */
    public int hotbarSlotAt(float logicalX, float logicalY) {
        for (int i = 0; i < Inventory.HOTBAR_SIZE; i++) {
            float cx = slotCenterX(i, Inventory.HOTBAR_SIZE);
            float cy = slotCenterY();
            if (Math.abs(logicalX - cx) <= HOTBAR_SLOT_SIZE / 2f && Math.abs(logicalY - cy) <= HOTBAR_SLOT_SIZE / 2f) return i;
        }
        return -1;
    }

    /** True if the mouse is over the creative "destroy item" slot. */
    public boolean destroySlotAt(float logicalX, float logicalY) {
        float cx = destroySlotX();
        float cy = slotCenterY();
        return Math.abs(logicalX - cx) <= HOTBAR_SLOT_SIZE / 2f && Math.abs(logicalY - cy) <= HOTBAR_SLOT_SIZE / 2f;
    }

    // --- Textured GUI helpers (Minecraft-style panels and slots) ---

    /** Adds one textured quad (x, y, u, v) to the batch for {@link #flushGuiQuads}. */
    private void addGuiQuad(float minX, float minY, float maxX, float maxY, float[] uv) {
        float u0 = uv[0], v0 = uv[1], u1 = uv[2], v1 = uv[3];
        int base = guiVerts.size() / 4;
        guiVerts.add(minX); guiVerts.add(minY); guiVerts.add(u0); guiVerts.add(v1);
        guiVerts.add(maxX); guiVerts.add(minY); guiVerts.add(u1); guiVerts.add(v1);
        guiVerts.add(maxX); guiVerts.add(maxY); guiVerts.add(u1); guiVerts.add(v0);
        guiVerts.add(minX); guiVerts.add(maxY); guiVerts.add(u0); guiVerts.add(v0);
        guiInds.add(base); guiInds.add(base + 1); guiInds.add(base + 2);
        guiInds.add(base); guiInds.add(base + 2); guiInds.add(base + 3);
    }

    /** Draws a 9-slice panel from {@code left},{@code bottom} to {@code right},{@code top}, with a fixed border. */
    private void renderGuiPanel(float left, float bottom, float right, float top) {
        float b = GUI_BORDER;
        // 9-slice: four corners fixed, four edges stretched, center stretched.
        addGuiQuad(left, bottom, left + b, bottom + b, guiTextures.panelUV(0, 2, darkGui));
        addGuiQuad(left + b, bottom, right - b, bottom + b, guiTextures.panelUV(1, 2, darkGui));
        addGuiQuad(right - b, bottom, right, bottom + b, guiTextures.panelUV(2, 2, darkGui));
        addGuiQuad(left, bottom + b, left + b, top - b, guiTextures.panelUV(0, 1, darkGui));
        addGuiQuad(left + b, bottom + b, right - b, top - b, guiTextures.panelUV(1, 1, darkGui));
        addGuiQuad(right - b, bottom + b, right, top - b, guiTextures.panelUV(2, 1, darkGui));
        addGuiQuad(left, top - b, left + b, top, guiTextures.panelUV(0, 0, darkGui));
        addGuiQuad(left + b, top - b, right - b, top, guiTextures.panelUV(1, 0, darkGui));
        addGuiQuad(right - b, top - b, right, top, guiTextures.panelUV(2, 0, darkGui));
    }

    /** Draws one textured slot cell centred at ({@code cx}, {@code cy}) with half-size {@code half}. */
    private void renderGuiSlot(float cx, float cy, float half) {
        addGuiQuad(cx - half, cy - half, cx + half, cy + half, guiTextures.slotUV(darkGui));
    }

    /** Uploads and draws the accumulated GUI-textured quads (panels + slots) in one pass. */
    private void flushGuiQuads() {
        if (guiInds.isEmpty()) return;
        guiQuadMesh.upload(guiVerts.toArray(), guiInds.toArray());
        hudShader.bind();
        hudShader.setUniform("transform", hudTransform);
        hudShader.setUniform("atlas", 0);
        hudShader.setUniform("color", WHITE);
        guiTextures.bind();
        guiQuadMesh.render();
        hudShader.unbind();
        guiVerts.clear();
        guiInds.clear();
    }

    /**
     * Draws any full-screen container gui: the 36-slot inventory grid (with the
     * hotbar as its bottom row) plus the open container's slots - the 3x3
     * crafting grid and its output for the inventory/crafting-table screens, or
     * the input/fuel/output slots (with a burning flame and progress arrow) for
     * a furnace. The cursor stack tracks the mouse and the hovered slot is
     * highlighted. {@code cursorLx}/{@code cursorLy} are the mouse position in
     * logical-square coordinates.
     */
    public void renderContainerGui(ContainerGui gui, InventoryController controller,
                                   int hoveredSlot, TextureAtlas atlas, ItemTextures itemTextures,
                                   ToolDurability durability, float aspectRatio, float cursorLx, float cursorLy) {
        glDisable(GL_DEPTH_TEST);
        hudTransform.identity().scale(1f / aspectRatio, 1f, 1f);

        // Panel background spanning the container area and the inventory grid.
        // A chest's grid stacks above the player grid, so its panel extends
        // upward to cover it (higher for a 54-slot double chest, taller still
        // for a 108-slot 2x2); it also hugs the 9-wide inventory grid rather
        // than reaching out to the crafting grid's column, since a chest has no
        // crafting grid. Tall chests shift the whole screen down so every slot
        // stays the same size.
        float gridW = invGridWidth();
        boolean chest = gui.kind() == ContainerGui.Kind.CHEST;
        float shift = chestLayoutShift(gui);
        float panelLeft = chest
                ? INV_GRID_CENTER_X - gridW / 2f - 0.03f
                : CRAFT_LEFT_X - INV_SLOT / 2f - 0.03f;
        float panelRight = INV_GRID_CENTER_X + gridW / 2f + 0.03f;
        float panelTop = chest
                ? chestTopRowY(gui) + INV_SLOT / 2f + 0.055f - shift
                : INV_TOP_ROW_Y + INV_SLOT / 2f + 0.055f;
        float panelBottom = (INV_TOP_ROW_Y - 3 * INV_STEP) - INV_SLOT / 2f - 0.07f - shift;

        // Minecraft-style textured panel (9-slice) behind the whole screen.
        if (guiTextures != null) {
            guiVerts.clear();
            guiInds.clear();
            renderGuiPanel(panelLeft, panelBottom, panelRight, panelTop);
            // Slot cells for every interactive slot.
            float half = INV_SLOT / 2f - 0.004f;
            for (int id = 0; id < gui.slotCount(); id++) {
                float[] c = slotCenter(gui, id);
                if (c != null) renderGuiSlot(c[0], c[1], half);
            }
            flushGuiQuads();
        } else {
            // Fallback (no GUI art): translucent flat panel + dark slot squares.
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
            lineShader.unbind();

            slotBgVerts.clear();
            float half = INV_SLOT / 2f - 0.004f;
            for (int id = 0; id < gui.slotCount(); id++) {
                float[] c = slotCenter(gui, id);
                if (c != null) addQuad3(slotBgVerts, c[0] - half, c[1] - half, c[0] + half, c[1] + half);
            }
            inventorySlotBg.upload(slotBgVerts.toArray());
            lineShader.bind();
            lineShader.setUniform("projection", identity);
            lineShader.setUniform("view", identity);
            lineShader.setUniform("model", hudTransform);
            lineShader.setUniform("color", new Vector4f(0f, 0f, 0f, 0.45f));
            inventorySlotBg.render();
            lineShader.unbind();
        }

        // Furnace decorations (flame + arrow) behind the slot icons.
        if (gui.kind() == ContainerGui.Kind.FURNACE) {
            renderFurnaceProgress(gui.furnace());
        }

        // A solid divider band between the chest's grid and the player's
        // inventory, so the two spaces read as separate sections at a glance.
        // The chest's bottom row sits CHEST_BOTTOM_ROW_Y above the player's top
        // row, leaving a clear gap for it.
        if (chest) {
            float sepCenter = (CHEST_BOTTOM_ROW_Y + INV_TOP_ROW_Y) / 2f - shift;
            float sepHalf = 0.016f;
            inventoryPanel.upload(new float[]{
                    panelLeft + 0.02f, sepCenter - sepHalf, 0, panelRight - 0.02f, sepCenter - sepHalf, 0,
                    panelRight - 0.02f, sepCenter + sepHalf, 0,
                    panelLeft + 0.02f, sepCenter - sepHalf, 0, panelRight - 0.02f, sepCenter + sepHalf, 0,
                    panelLeft + 0.02f, sepCenter + sepHalf, 0,
            });
            lineShader.bind();
            lineShader.setUniform("projection", identity);
            lineShader.setUniform("view", identity);
            lineShader.setUniform("model", hudTransform);
            lineShader.setUniform("color", darkGui
                    ? new Vector4f(0.7f, 0.7f, 0.7f, 0.25f)
                    : new Vector4f(0.1f, 0.1f, 0.1f, 0.35f));
            inventoryPanel.render();
            lineShader.unbind();
        }

        // Hover highlight.
        if (hoveredSlot >= 0) {
            float[] c = slotCenter(gui, hoveredSlot);
            if (c != null) {
                inventoryHover.upload(outlineLines(c[0], c[1], INV_SLOT / 2f + 0.004f));
                lineShader.bind();
                lineShader.setUniform("projection", identity);
                lineShader.setUniform("view", identity);
                lineShader.setUniform("model", hudTransform);
                lineShader.setUniform("color", new Vector4f(1f, 1f, 1f, 0.9f));
                glLineWidth(2f);
                inventoryHover.render();
                lineShader.unbind();
            }
        }

        // Icons + counts + wear bars for every occupied slot (the crafting
        // output is derived from the recipe rather than stored).
        beginSlotBatch();
        float iconHalf = INV_SLOT / 2f - 0.006f;
        for (int id = 0; id < gui.slotCount(); id++) {
            float[] c = slotCenter(gui, id);
            BlockType t = gui.typeOf(id);
            if (t != null) addSlotIcon(c[0], c[1], iconHalf, t, gui.countOf(id), itemTextures, atlas, durability);
        }
        Crafting.Recipe recipe = gui.currentRecipe();
        if (recipe != null) {
            float[] c = slotCenter(gui, ContainerGui.OUTPUT_SLOT);
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

        // Tooltip for the hovered slot.
        BlockType tip = null;
        if (gui.isOutputSlot(hoveredSlot)) {
            tip = recipe != null ? recipe.output() : null;
        } else if (hoveredSlot >= 0) {
            tip = gui.typeOf(hoveredSlot);
        }
        if (tip != null) {
            renderTooltip(tooltipLines(tip, durability), cursorLx, cursorLy, aspectRatio);
        }

        // Title + hint line. The title gets a shadow so it stays legible on the
        // bright light-theme panel.
        drawCenteredTextShadowed(gui.title(), 0f, panelTop - 0.05f, 0.045f, WHITE);
        drawCenteredText("Left: take/place stack    Right: one item    Shift-click: move    Drag: spread    Esc: close",
                0f, panelBottom - 0.04f, 0.022f, new Vector4f(0.7f, 0.7f, 0.7f, 1f));

        glEnable(GL_DEPTH_TEST);
    }

    /** Draws the furnace's burn flame and smelting progress arrow (both driven by the furnace state). */
    private void renderFurnaceProgress(Furnace furnace) {
        // Flame track behind the flame itself.
        furnaceDeco.clear();
        float flameHalf = 0.0225f;
        float flameTop = FURNACE_MID_Y + 0.045f;
        float flameBottom = FURNACE_MID_Y - 0.045f;
        addQuad3(furnaceDeco, FURNACE_FLAME_X - flameHalf, flameBottom, FURNACE_FLAME_X + flameHalf, flameTop);
        inventoryPanel.upload(furnaceDeco.toArray());
        lineShader.setUniform("color", new Vector4f(0.15f, 0.15f, 0.15f, 0.9f));
        inventoryPanel.render();

        // Flame fill rises from the bottom as the fuel burns down.
        furnaceDeco.clear();
        float flameHeight = (flameTop - flameBottom) * Math.min(1f, Math.max(0f, furnace.burnFraction()));
        if (flameHeight > 0f) {
            addQuad3(furnaceDeco, FURNACE_FLAME_X - flameHalf, flameBottom, FURNACE_FLAME_X + flameHalf, flameBottom + flameHeight);
            inventoryPanel.upload(furnaceDeco.toArray());
            lineShader.setUniform("color", new Vector4f(0.98f, 0.55f, 0.12f, 1f));
            inventoryPanel.render();
        }

        // Arrow track from the fuel column toward the output slot.
        float arrowHalf = 0.0225f;
        furnaceDeco.clear();
        addQuad3(furnaceDeco, FURNACE_ARROW_X0, FURNACE_MID_Y - arrowHalf, FURNACE_ARROW_X1, FURNACE_MID_Y + arrowHalf);
        inventoryPanel.upload(furnaceDeco.toArray());
        lineShader.setUniform("color", new Vector4f(0.3f, 0.3f, 0.3f, 0.9f));
        inventoryPanel.render();

        // Arrow fill grows left to right with smelting progress.
        furnaceDeco.clear();
        float fill = (FURNACE_ARROW_X1 - FURNACE_ARROW_X0) * Math.min(1f, Math.max(0f, furnace.progressFraction()));
        if (fill > 0f) {
            addQuad3(furnaceDeco, FURNACE_ARROW_X0, FURNACE_MID_Y - arrowHalf, FURNACE_ARROW_X0 + fill, FURNACE_MID_Y + arrowHalf);
            inventoryPanel.upload(furnaceDeco.toArray());
            lineShader.setUniform("color", new Vector4f(0.95f, 0.95f, 0.95f, 1f));
            inventoryPanel.render();
        }
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

    /** The tooltip lines for an item: its display name, plus durability for tools. */
    private String[] tooltipLines(BlockType type, ToolDurability durability) {
        if (Mining.isTool(type)) {
            int max = Mining.toolStats(type).maxUses();
            int rem = durability.remaining(type);
            return new String[]{type.displayName(), "Durability: " + rem + " / " + max};
        }
        return new String[]{type.displayName()};
    }

    /**
     * Draws a small Minecraft-style tooltip panel with {@code lines}, positioned
     * below-right of the mouse and clamped to stay on screen. Drawn last so it
     * sits on top of the cursor stack.
     */
    public void renderTooltip(String[] lines, float logicalX, float logicalY, float aspectRatio) {
        glDisable(GL_DEPTH_TEST);
        hudTransform.identity().scale(1f / aspectRatio, 1f, 1f);

        float size = 0.024f;
        float lineH = size + 0.002f;
        float pad = 0.022f;
        float w = 0f;
        for (String line : lines) {
            w = Math.max(w, text.measure(line, size));
        }
        w += pad * 2f;
        float h = lines.length * lineH + pad * 2f - 0.002f;

        float x = Math.max(-1f + w / 2f, Math.min(1f - w / 2f, logicalX + 0.06f));
        float y = Math.max(-1f, Math.min(1f - h, logicalY - 0.06f - h));
        float left = x - w / 2f;

        float[] panel = {
                left, y, 0, left + w, y, 0, left + w, y + h, 0,
                left, y, 0, left + w, y + h, 0, left, y + h, 0,
        };
        tooltipPanel.upload(panel);
        lineShader.bind();
        lineShader.setUniform("projection", identity);
        lineShader.setUniform("view", identity);
        lineShader.setUniform("model", hudTransform);
        lineShader.setUniform("color", new Vector4f(0.08f, 0.08f, 0.1f, 0.92f));
        tooltipPanel.render();
        lineShader.unbind();

        for (int i = 0; i < lines.length; i++) {
            drawTextAt(lines[i], left + pad, y + pad + i * lineH, size, WHITE);
        }
        glEnable(GL_DEPTH_TEST);
    }

    /**
     * Draws the creative-mode inventory screen: a tabbed item catalog on top of
     * the normal 9-slot hotbar plus a "destroy item" slot, with the cursor stack
     * following the mouse. {@code selectedTab} is the active category and
     * {@code selectedSlot} the highlighted hotbar slot.
     */
    public void renderCreative(Inventory inventory, InventoryController controller, int selectedTab, int selectedSlot,
                               TextureAtlas atlas, ItemTextures itemTextures, ToolDurability durability,
                               float aspectRatio, float cursorLx, float cursorLy) {
        glDisable(GL_DEPTH_TEST);
        hudTransform.identity().scale(1f / aspectRatio, 1f, 1f);

        // Full-screen dim behind everything (logical x spans the whole viewport).
        float full = aspectRatio;
        float[] dim = {
                -full, -1, 0, full, -1, 0, full, 1, 0,
                -full, -1, 0, full, 1, 0, -full, 1, 0,
        };
        inventoryPanel.upload(dim);
        lineShader.bind();
        lineShader.setUniform("projection", identity);
        lineShader.setUniform("view", identity);
        lineShader.setUniform("model", hudTransform);
        lineShader.setUniform("color", new Vector4f(0f, 0f, 0f, 0.62f));
        inventoryPanel.render();
        lineShader.unbind();

        // Tabs: a highlight behind the selected tab, labels for all.
        int tabCount = CreativeCatalog.TABS.length;
        for (int i = 0; i < tabCount; i++) {
            float cx = tabCenterX(i, tabCount);
            boolean selected = i == selectedTab;
            if (selected) {
                float[] bg = {
                        cx - TAB_W / 2f, TAB_CENTER_Y - TAB_H / 2f, 0, cx + TAB_W / 2f, TAB_CENTER_Y - TAB_H / 2f, 0,
                        cx + TAB_W / 2f, TAB_CENTER_Y + TAB_H / 2f, 0,
                        cx - TAB_W / 2f, TAB_CENTER_Y - TAB_H / 2f, 0, cx + TAB_W / 2f, TAB_CENTER_Y + TAB_H / 2f, 0,
                        cx - TAB_W / 2f, TAB_CENTER_Y + TAB_H / 2f, 0,
                };
                inventorySlotBg.upload(bg);
                lineShader.bind();
                lineShader.setUniform("projection", identity);
                lineShader.setUniform("view", identity);
                lineShader.setUniform("model", hudTransform);
                lineShader.setUniform("color", new Vector4f(0.35f, 0.35f, 0.35f, 0.95f));
                inventorySlotBg.render();
                lineShader.unbind();
            }
            drawCenteredText(CreativeCatalog.TABS[i].label(), cx, TAB_CENTER_Y - 0.022f, 0.026f,
                    selected ? WHITE : new Vector4f(0.72f, 0.72f, 0.72f, 1f));
        }

        // Textured slot cells for the catalog grid and the hotbar + destroy slot.
        float centerY = slotCenterY();
        float dx = destroySlotX();
        if (guiTextures != null) {
            guiVerts.clear();
            guiInds.clear();
            float half = CAT_SLOT / 2f - 0.005f;
            BlockType[] items = CreativeCatalog.TABS[selectedTab].items();
            for (int i = 0; i < items.length; i++) {
                float[] c = catalogItemCenter(i);
                renderGuiSlot(c[0], c[1], half);
            }
            for (int i = 0; i < Inventory.HOTBAR_SIZE; i++) {
                float cx = slotCenterX(i, Inventory.HOTBAR_SIZE);
                renderGuiSlot(cx, centerY, HOTBAR_SLOT_SIZE / 2f - 0.005f);
            }
            renderGuiSlot(dx, centerY, HOTBAR_SLOT_SIZE / 2f - 0.005f);
            flushGuiQuads();
        } else {
            // Fallback: flat dark slot squares.
            BlockType[] items = CreativeCatalog.TABS[selectedTab].items();
            float half = CAT_SLOT / 2f - 0.005f;
            slotBgVerts.clear();
            for (int i = 0; i < items.length; i++) {
                float[] c = catalogItemCenter(i);
                addQuad3(slotBgVerts, c[0] - half, c[1] - half, c[0] + half, c[1] + half);
            }
            lineShader.bind();
            lineShader.setUniform("projection", identity);
            lineShader.setUniform("view", identity);
            lineShader.setUniform("model", hudTransform);
            inventorySlotBg.upload(slotBgVerts.toArray());
            lineShader.setUniform("color", new Vector4f(0f, 0f, 0f, 0.35f));
            inventorySlotBg.render();
            lineShader.unbind();

            slotBgVerts.clear();
            for (int i = 0; i < Inventory.HOTBAR_SIZE; i++) {
                float cx = slotCenterX(i, Inventory.HOTBAR_SIZE);
                addQuad3(slotBgVerts, cx - half, centerY - half, cx + half, centerY + half);
            }
            addQuad3(slotBgVerts, dx - half, centerY - half, dx + half, centerY + half);
            lineShader.bind();
            lineShader.setUniform("projection", identity);
            lineShader.setUniform("view", identity);
            lineShader.setUniform("model", hudTransform);
            inventorySlotBg.upload(slotBgVerts.toArray());
            lineShader.setUniform("color", new Vector4f(0f, 0f, 0f, 0.45f));
            inventorySlotBg.render();
            lineShader.unbind();
        }

        // Catalog item icons.
        BlockType[] items = CreativeCatalog.TABS[selectedTab].items();
        float half = CAT_SLOT / 2f - 0.005f;
        beginSlotBatch();
        for (int i = 0; i < items.length; i++) {
            float[] c = catalogItemCenter(i);
            addSlotIcon(c[0], c[1], half, items[i], 1, itemTextures, atlas, durability);
        }
        flushBlockBatch(atlas);
        text.render(hudTransform, WHITE);

        // Catalog item hover highlight.
        int hoverItem = creativeItemAt(cursorLx, cursorLy, selectedTab);
        if (hoverItem >= 0) {
            float[] c = catalogItemCenter(hoverItem);
            inventoryHover.upload(outlineLines(c[0], c[1], CAT_SLOT / 2f + 0.004f));
            lineShader.bind();
            lineShader.setUniform("projection", identity);
            lineShader.setUniform("view", identity);
            lineShader.setUniform("model", hudTransform);
            lineShader.setUniform("color", new Vector4f(1f, 1f, 1f, 0.9f));
            glLineWidth(2f);
            inventoryHover.render();
            lineShader.unbind();
        }

        beginSlotBatch();
        float hotbarHalf = HOTBAR_SLOT_SIZE / 2f - 0.008f;
        for (int i = 0; i < Inventory.HOTBAR_SIZE; i++) {
            addSlotIcon(slotCenterX(i, Inventory.HOTBAR_SIZE), centerY, hotbarHalf,
                    inventory.typeOf(i), inventory.countOf(i), itemTextures, atlas, durability);
        }
        flushBlockBatch(atlas);
        text.render(hudTransform, WHITE);

        // Hotbar slot numbers.
        text.begin();
        for (int i = 0; i < Inventory.HOTBAR_SIZE; i++) {
            text.add(String.valueOf(i + 1), slotCenterX(i, Inventory.HOTBAR_SIZE) - hotbarHalf + 0.006f,
                    centerY - hotbarHalf + 0.002f, 0.022f);
        }
        text.render(hudTransform, new Vector4f(0.55f, 0.55f, 0.55f, 1f));

        // Selected-slot + hover highlight on the hotbar, and the destroy "X".
        lineShader.bind();
        lineShader.setUniform("projection", identity);
        lineShader.setUniform("view", identity);
        lineShader.setUniform("model", hudTransform);
        float selCx = slotCenterX(Math.max(0, Math.min(Inventory.HOTBAR_SIZE - 1, selectedSlot)), Inventory.HOTBAR_SIZE);
        inventoryHover.upload(outlineLines(selCx, centerY, HOTBAR_SLOT_SIZE / 2f + 0.006f));
        lineShader.setUniform("color", new Vector4f(1f, 1f, 1f, 0.95f));
        glLineWidth(2f);
        inventoryHover.render();
        int hoverHotbar = hotbarSlotAt(cursorLx, cursorLy);
        if (hoverHotbar >= 0 && hoverHotbar != selectedSlot) {
            float hx = slotCenterX(hoverHotbar, Inventory.HOTBAR_SIZE);
            inventoryHover.upload(outlineLines(hx, centerY, HOTBAR_SLOT_SIZE / 2f + 0.006f));
            lineShader.setUniform("color", new Vector4f(1f, 1f, 1f, 0.5f));
            inventoryHover.render();
        }
        if (destroySlotAt(cursorLx, cursorLy)) {
            inventoryHover.upload(outlineLines(dx, centerY, HOTBAR_SLOT_SIZE / 2f + 0.006f));
            lineShader.setUniform("color", new Vector4f(1f, 0.3f, 0.3f, 0.9f));
            inventoryHover.render();
        }
        lineShader.unbind();

        renderDurabilityBars(hotbarHalf);

        // "X" in the destroy slot.
        drawCenteredText("X", dx, centerY - 0.022f, 0.04f, new Vector4f(0.85f, 0.85f, 0.85f, 1f));

        // Cursor stack following the mouse.
        if (controller.hasCursorItem()) {
            drawCursorStack(atlas, itemTextures, controller.cursorType(), controller.cursorCount(),
                    cursorLx + 0.02f, cursorLy - 0.02f);
        }

        // Tooltip for the hovered catalog item or hotbar slot.
        BlockType tip = null;
        int hb = hotbarSlotAt(cursorLx, cursorLy);
        int ci = creativeItemAt(cursorLx, cursorLy, selectedTab);
        if (hb >= 0) {
            tip = inventory.typeOf(hb);
        } else if (ci >= 0) {
            tip = CreativeCatalog.TABS[selectedTab].items()[ci];
        }
        if (tip != null) {
            renderTooltip(tooltipLines(tip, durability), cursorLx, cursorLy, aspectRatio);
        }

        drawCenteredText("Creative    Click: add to cursor    Shift-click: to hotbar    X: delete",
                0f, centerY - HOTBAR_SLOT_SIZE / 2f - 0.05f, 0.022f, new Vector4f(0.7f, 0.7f, 0.7f, 1f));

        glEnable(GL_DEPTH_TEST);
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
        tooltipPanel.destroy();
        settingsTrack.destroy();
        settingsFill.destroy();
        guiQuadMesh.destroy();
    }
}
