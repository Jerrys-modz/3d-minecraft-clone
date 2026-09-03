package com.minecraftclone.engine;

import com.minecraftclone.Settings;
import com.minecraftclone.engine.GamepadBindings;
import com.minecraftclone.engine.KeyBindings;
import com.minecraftclone.engine.graphics.FontAtlas;
import com.minecraftclone.engine.graphics.GLTexture;
import com.minecraftclone.engine.graphics.GuiTextures;
import com.minecraftclone.engine.graphics.IconMesh;
import com.minecraftclone.engine.graphics.ItemTextures;
import com.minecraftclone.engine.graphics.LineMesh;
import com.minecraftclone.engine.graphics.TextRenderer;
import com.minecraftclone.engine.graphics.TextureAtlas;
import com.minecraftclone.engine.gui.ContainerGui;
import com.minecraftclone.player.Armor;
import com.minecraftclone.player.Crafting;
import com.minecraftclone.player.CreativeCatalog;
import com.minecraftclone.player.RecipeBookGui;
import com.minecraftclone.player.Inventory;
import com.minecraftclone.player.InventoryController;
import com.minecraftclone.player.ItemStack;
import com.minecraftclone.player.ToolDurability;
import com.minecraftclone.world.gen.WorldGenSettings;
import com.minecraftclone.util.FloatArray;
import com.minecraftclone.util.IntArray;
import com.minecraftclone.world.BlockType;
import com.minecraftclone.world.Furnace;
import com.minecraftclone.world.Mining;
import org.joml.Matrix4f;
import org.joml.Vector3i;
import org.joml.Vector4f;

import java.util.List;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.GL_TEXTURE0;
import static org.lwjgl.opengl.GL13.glActiveTexture;

/**
 * Draws all 2D overlay elements: the crosshair, the wireframe outline around
 * the targeted block, the hotbar (a 9-slot strip of the player's inventory,
 * with the selected slot highlighted and the held item's name fading out
 * above the bar), the health/hunger/stamina bars,
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

    /** Seconds the held-item name stays fully visible after a hotbar change. */
    public static final float HOTBAR_NAME_HOLD_SECONDS = 2.0f;
    /** Seconds the held-item name takes to fade after the hold. */
    public static final float HOTBAR_NAME_FADE_SECONDS = 1.0f;
    private static final float HOTBAR_NAME_SIZE = 0.034f;

    /** Bottom Y of the look-at overlay title (top of the logical square). */
    public static final float LOOK_AT_TITLE_Y = 0.90f;

    /**
     * Mini-map on-screen height as a fraction of the logical square (−1..1).
     * ~24% of the viewport, parked in the top-right with {@link #MINI_MAP_MARGIN}.
     */
    public static final float MINI_MAP_SIZE_Y = 0.48f;
    public static final float MINI_MAP_SIZE_Y_MIN = 0.22f;
    public static final float MINI_MAP_SIZE_Y_MAX = 0.90f;
    /** Viewport margin (NDC units) around the mini-map on the top and right. */
    public static final float MINI_MAP_MARGIN = 0.035f;
    /** Corner-handle hit size in logical-Y units while the HUD is being edited. */
    public static final float MINI_MAP_HANDLE = 0.05f;

    public static final int MINIMAP_HIT_NONE = 0;
    public static final int MINIMAP_HIT_BODY = 1;
    public static final int MINIMAP_HIT_TL = 2;
    public static final int MINIMAP_HIT_TR = 3;
    public static final int MINIMAP_HIT_BL = 4;
    public static final int MINIMAP_HIT_BR = 5;

    /** On-screen mini-map rectangle in HUD logical-square space. */
    public record MiniMapLayout(float sizeX, float sizeY, float cx, float cy) {
        public float minX() { return cx - sizeX / 2f; }
        public float maxX() { return cx + sizeX / 2f; }
        public float minY() { return cy - sizeY / 2f; }
        public float maxY() { return cy + sizeY / 2f; }
    }

    /** Logical width that keeps the mini-map square after the 1/aspect HUD scale. */
    static float miniMapSizeX(float aspectRatio) {
        return miniMapSizeX(aspectRatio, MINI_MAP_SIZE_Y);
    }

    static float miniMapSizeX(float aspectRatio, float sizeY) {
        return clampMiniMapSizeY(sizeY) * aspectRatio;
    }

    /** Logical center-x: right edge sits {@link #MINI_MAP_MARGIN} in from the viewport. */
    static float miniMapOffsetX(float aspectRatio) {
        return miniMapLayout(aspectRatio, MINI_MAP_SIZE_Y, Float.NaN, Float.NaN).cx;
    }

    /** Logical center-y: top edge sits {@link #MINI_MAP_MARGIN} down from the top. */
    static float miniMapOffsetY() {
        return miniMapLayout(1f, MINI_MAP_SIZE_Y, Float.NaN, Float.NaN).cy;
    }

    static float clampMiniMapSizeY(float sizeY) {
        return Math.max(MINI_MAP_SIZE_Y_MIN, Math.min(MINI_MAP_SIZE_Y_MAX, sizeY));
    }

    /**
     * Resolves a persisted (or default) mini-map layout into logical-square
     * coordinates. {@code ndcX}/{@code ndcY} of NaN parks it in the top-right.
     */
    public static MiniMapLayout miniMapLayout(float aspectRatio, float sizeY, float ndcX, float ndcY) {
        float sy = clampMiniMapSizeY(sizeY);
        float half = sy / 2f;
        float minNdc = -1f + MINI_MAP_MARGIN + half;
        float maxNdc = 1f - MINI_MAP_MARGIN - half;
        if (minNdc > maxNdc) {
            minNdc = maxNdc = 0f;
        }
        float nx = Float.isNaN(ndcX) ? (1f - MINI_MAP_MARGIN - half) : ndcX;
        float ny = Float.isNaN(ndcY) ? (1f - MINI_MAP_MARGIN - half) : ndcY;
        nx = Math.max(minNdc, Math.min(maxNdc, nx));
        ny = Math.max(minNdc, Math.min(maxNdc, ny));
        return new MiniMapLayout(sy * aspectRatio, sy, nx * aspectRatio, ny);
    }

    /** Hit-test the mini-map body and its four resize corners. */
    public static int miniMapHit(MiniMapLayout layout, float logicalX, float logicalY) {
        if (layout == null) return MINIMAP_HIT_NONE;
        float hsY = MINI_MAP_HANDLE;
        float hsX = MINI_MAP_HANDLE * (layout.sizeX() / Math.max(1e-6f, layout.sizeY()));
        if (near(logicalX, layout.minX(), hsX) && near(logicalY, layout.maxY(), hsY)) return MINIMAP_HIT_TL;
        if (near(logicalX, layout.maxX(), hsX) && near(logicalY, layout.maxY(), hsY)) return MINIMAP_HIT_TR;
        if (near(logicalX, layout.minX(), hsX) && near(logicalY, layout.minY(), hsY)) return MINIMAP_HIT_BL;
        if (near(logicalX, layout.maxX(), hsX) && near(logicalY, layout.minY(), hsY)) return MINIMAP_HIT_BR;
        if (logicalX >= layout.minX() && logicalX <= layout.maxX()
                && logicalY >= layout.minY() && logicalY <= layout.maxY()) {
            return MINIMAP_HIT_BODY;
        }
        return MINIMAP_HIT_NONE;
    }

    private static boolean near(float a, float b, float slop) {
        return Math.abs(a - b) <= slop;
    }

    /**
     * New layout after dragging a corner. The opposite corner stays put and
     * the map stays square on screen.
     */
    public static MiniMapLayout resizeMiniMap(int corner, MiniMapLayout cur, float mx, float my, float aspect) {
        float fixedX;
        float fixedY;
        switch (corner) {
            case MINIMAP_HIT_BR -> { fixedX = cur.minX(); fixedY = cur.maxY(); }
            case MINIMAP_HIT_BL -> { fixedX = cur.maxX(); fixedY = cur.maxY(); }
            case MINIMAP_HIT_TR -> { fixedX = cur.minX(); fixedY = cur.minY(); }
            case MINIMAP_HIT_TL -> { fixedX = cur.maxX(); fixedY = cur.minY(); }
            default -> { return cur; }
        }
        float sizeY = Math.max(Math.abs(mx - fixedX) / aspect, Math.abs(my - fixedY));
        sizeY = clampMiniMapSizeY(sizeY);
        float sizeX = sizeY * aspect;
        float cx = mx >= fixedX ? fixedX + sizeX / 2f : fixedX - sizeX / 2f;
        float cy = my >= fixedY ? fixedY + sizeY / 2f : fixedY - sizeY / 2f;
        return miniMapLayout(aspect, sizeY, cx / aspect, cy);
    }

    /** Scale about the current centre, staying square and on-screen. */
    public static MiniMapLayout scaleMiniMap(MiniMapLayout cur, float factor, float aspect) {
        if (cur == null) return null;
        return miniMapLayout(aspect, cur.sizeY() * factor, cur.cx() / aspect, cur.cy());
    }

    /** Layout from persisted settings (NaN NDC = default top-right). */
    public static MiniMapLayout miniMapLayout(Settings settings, float aspect) {
        if (settings == null) {
            return miniMapLayout(aspect, MINI_MAP_SIZE_Y, Float.NaN, Float.NaN);
        }
        return miniMapLayout(aspect, settings.getMiniMapSizeY(),
                settings.getMiniMapNdcX(), settings.getMiniMapNdcY());
    }

    /** Persist a logical-square layout back to NDC centre + size. */
    public static void writeMiniMapLayout(Settings settings, MiniMapLayout layout, float aspect) {
        if (settings == null || layout == null) return;
        settings.setMiniMapLayout(layout.sizeY(), layout.cx() / aspect, layout.cy());
    }

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
    /** Center x of the vertical armor-slot column, just left of the player's inventory grid. */
    private static final float ARMOR_X = -0.40f;

    /**
     * Gap between the player's inventory top row and the bottom row of a
     * crafting-table grid. Same spacing the chest GUI uses so the 3x3/5x5
     * sits <em>above</em> the bag instead of sharing the 2x2 inventory's
     * left-hand corner.
     */
    private static final float TABLE_CRAFT_GAP = 0.15f;

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

    // Part Builder GUI layout. The station sits above the player's inventory
    // with the same gap as the chest / crafting-table screens, so the shape
    // buttons never collide with the bag. Material on the inventory's left
    // column, a compact 4×2 shape grid in the middle, output on the right.
    private static final int PB_SHAPE_COLS = 4;
    private static final float PB_STATION_BOTTOM_Y = INV_TOP_ROW_Y + TABLE_CRAFT_GAP;

    // Tool Station GUI layout. Head / rod / extras along the inventory's
    // left columns, output on the right — same station band as the Part
    // Builder, so the title and role labels sit above the slots instead
    // of on top of them.
    private static final float TS_STATION_Y = INV_TOP_ROW_Y + TABLE_CRAFT_GAP + INV_STEP / 2f;

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
    private static final int CAT_COLUMNS = 9;
    /** Gap between the last visible catalog row and the top of the hotbar panel. */
    private static final float CAT_HOTBAR_GAP = 0.07f;
    private static final float CAT_SCROLLBAR_W = 0.028f;
    private static final float CAT_SCROLLBAR_GAP = 0.018f;
    private static final float TAB_W = 0.31f;
    private static final float TAB_GAP = 0.012f;
    private static final float TAB_CENTER_Y = 0.80f;        // center y of the tab strip
    private static final float TAB_H = 0.07f;
    private static final float SEARCH_CENTER_Y = 0.615f;    // search field between tabs and the grid
    private static final float SEARCH_H = 0.068f;
    private static final int SEARCH_MAX_CHARS = 28;

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
    private static final float SETTINGS_DONE_H = 0.07f;
    private static final float SETTINGS_CENTER_Y = 0.12f;
    /** Create-world panel: wide enough that a full random long seed sits beside "Seed". */
    private static final float WORLD_GEN_PANEL_W = 1.2f;
    /** Gap between a world-gen row's label and its value so a long seed never runs into "Seed". */
    private static final float WORLD_GEN_VALUE_GAP = 0.05f;
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
    private final LineMesh frostOverlay = new LineMesh(GL_TRIANGLES); // fullscreen cold vignette
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

    // Cached mini-map resources to avoid recreating every frame.
    private java.awt.image.BufferedImage cachedMiniMapImage;
    private int cachedMiniMapTextureId = -1;
    private int cachedMiniMapVersion = -1;
    private final IconMesh miniMapMesh = new IconMesh();
    private float cachedMiniMapSizeX, cachedMiniMapSizeY, cachedMiniMapOffsetX, cachedMiniMapOffsetY;

    // Cached full-screen map resources.
    private java.awt.image.BufferedImage cachedFullMapImage;
    private int cachedFullMapVersion = -1;
    private int cachedFullMapTextureId = -1;
    private final IconMesh fullMapMesh = new IconMesh();

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

    /** Last hotbar slot whose name was shown — used to restart the fade on a change. */
    private int hotbarNameSlot = Integer.MIN_VALUE;
    private String hotbarNameShown = "";
    private float hotbarNameAge = Float.POSITIVE_INFINITY;

    public Hud(Shader lineShader, Shader hudShader, FontAtlas font) {
        this.lineShader = lineShader;
        this.hudShader = hudShader;
        this.text = new TextRenderer(font, hudShader);
        buildCrosshair();
        buildCubeOutline();
        // A fullscreen quad in NDC covering the whole screen, used for the cold
        // vignette (drawn with an identity model/projection via the line shader).
        frostOverlay.upload(new float[]{
                -1, -1, 0, 1, -1, 0, 1, 1, 0,
                -1, -1, 0, 1, 1, 0, -1, 1, 0,
        });
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

    /**
     * WAILA-style overlay at the top of the screen: the name of whatever the
     * crosshair is on, and a harvest line ({@code Can mine} / {@code Need Iron
     * Pickaxe} / {@code Unbreakable}). Pass {@code harvest} null for mobs.
     */
    public void renderLookAt(String title, String harvest, float aspectRatio) {
        if (title == null || title.isEmpty()) return;
        glDisable(GL_DEPTH_TEST);
        hudTransform.identity().scale(1f / aspectRatio, 1f, 1f);

        float titleSize = 0.034f;
        float hintSize = 0.024f;
        float titleW = text.measure(title, titleSize);
        float hintW = harvest == null ? 0f : text.measure(harvest, hintSize);
        float textW = Math.max(titleW, hintW);
        float padX = 0.028f;
        float padY = 0.016f;
        float lineGap = 0.010f;
        float titleY = LOOK_AT_TITLE_Y;
        float hintY = titleY - hintSize - lineGap;
        float panelTop = titleY + titleSize + padY;
        float panelBot = (harvest == null ? titleY : hintY) - padY;
        float panelLeft = -textW / 2f - padX;
        float panelRight = textW / 2f + padX;

        float[] bg = {
                panelLeft, panelBot, 0, panelRight, panelBot, 0, panelRight, panelTop, 0,
                panelLeft, panelBot, 0, panelRight, panelTop, 0, panelLeft, panelTop, 0,
        };
        tooltipPanel.upload(bg);
        lineShader.bind();
        lineShader.setUniform("projection", identity);
        lineShader.setUniform("view", identity);
        lineShader.setUniform("model", hudTransform);
        lineShader.setUniform("color", new Vector4f(0f, 0f, 0f, 0.50f));
        tooltipPanel.render();
        lineShader.unbind();

        drawCenteredText(title, 0f, titleY, titleSize, WHITE);
        if (harvest != null && !harvest.isEmpty()) {
            boolean ok = Mining.harvestHintPositive(harvest);
            Vector4f color = ok
                    ? new Vector4f(0.48f, 0.90f, 0.42f, 1f)
                    : new Vector4f(0.95f, 0.38f, 0.32f, 1f);
            drawCenteredText(harvest, 0f, hintY, hintSize, color);
        }

        glEnable(GL_DEPTH_TEST);
    }

    /**
     * A translucent frost vignette over the whole screen, fading in with cold
     * exposure (0 = clear, 1 = freezing out in a blizzard). Drawn in NDC so it
     * covers the viewport regardless of aspect ratio.
     */
    public void renderFrostOverlay(float coldness) {
        if (coldness <= 0f) return;
        glDisable(GL_DEPTH_TEST);
        lineShader.bind();
        lineShader.setUniform("projection", identity);
        lineShader.setUniform("view", identity);
        lineShader.setUniform("model", modelMatrix.identity());
        float a = Math.min(1f, coldness) * 0.45f;
        lineShader.setUniform("color", new Vector4f(0.78f, 0.87f, 1f, a));
        frostOverlay.render();
        lineShader.unbind();
        glEnable(GL_DEPTH_TEST);
    }

    /**
     * A translucent blue tint over the whole screen while the camera's eyes
     * are underwater - the same fullscreen-quad technique as {@link #renderFrostOverlay},
     * reusing its NDC quad (it just fills the same full screen either way).
     */
    public void renderUnderwaterOverlay(boolean submerged) {
        if (!submerged) return;
        glDisable(GL_DEPTH_TEST);
        lineShader.bind();
        lineShader.setUniform("projection", identity);
        lineShader.setUniform("view", identity);
        lineShader.setUniform("model", modelMatrix.identity());
        lineShader.setUniform("color", new Vector4f(0.1f, 0.35f, 0.65f, 0.35f));
        frostOverlay.render();
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
                              Inventory inventory, int selectedSlot, float aspectRatio, float dt) {
        glDisable(GL_DEPTH_TEST);
        glDisable(GL_CULL_FACE);

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
                    inventory.stackOf(i), itemTextures, atlas, durability);
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

        renderHotbarHeldName(inventory.stackOf(selectedSlot), selectedSlot, dt);

        glEnable(GL_CULL_FACE);
        glEnable(GL_DEPTH_TEST);
    }

    /**
     * Minecraft-style selected-item overlay: the name of whatever is in the
     * highlighted hotbar slot, centred above the status bars, fully visible
     * for {@link #HOTBAR_NAME_HOLD_SECONDS} then fading over
     * {@link #HOTBAR_NAME_FADE_SECONDS}. Switching slots (or picking up a
     * different item in the same slot) restarts the timer.
     */
    private void renderHotbarHeldName(ItemStack stack, int selectedSlot, float dt) {
        String name = hotbarItemName(stack);
        if (name == null) {
            hotbarNameSlot = selectedSlot;
            hotbarNameShown = "";
            hotbarNameAge = Float.POSITIVE_INFINITY;
            return;
        }
        if (selectedSlot != hotbarNameSlot || !name.equals(hotbarNameShown)) {
            hotbarNameSlot = selectedSlot;
            hotbarNameShown = name;
            hotbarNameAge = 0f;
        } else {
            hotbarNameAge += Math.max(0f, dt);
        }
        float alpha = hotbarNameAlpha(hotbarNameAge);
        if (alpha <= 0.01f) return;

        float size = HOTBAR_NAME_SIZE;
        float x = -text.measure(name, size) / 2f;
        float y = hotbarHeldNameY();
        float shadow = size * 0.06f;
        text.begin();
        text.add(name, x + shadow, y - shadow, size);
        text.render(hudTransform, new Vector4f(0f, 0f, 0f, 0.55f * alpha));
        text.begin();
        text.add(name, x, y, size);
        text.render(hudTransform, new Vector4f(1f, 1f, 1f, alpha));
    }

    /**
     * Display name for the hotbar overlay, or {@code null} when the slot is empty.
     */
    public static String hotbarItemName(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        com.minecraftclone.world.tinkers.TinkersItem.Part part = stack.tinkersPart();
        if (part != null) {
            return part.material.displayName() + " " + titleFromEnum(part.shape.name());
        }
        com.minecraftclone.world.tinkers.TinkersItem.Tool tool = stack.tinkersTool();
        if (tool != null) {
            String kind = titleFromEnum(tool.kind.name());
            BlockType head = tool.headMaterial();
            return head != null ? head.displayName() + " " + kind : kind;
        }
        BlockType type = stack.type();
        return type == null ? null : type.displayName();
    }

    /** 1 while holding, then linear fade to 0. */
    public static float hotbarNameAlpha(float ageSeconds) {
        if (ageSeconds < 0f) return 1f;
        if (ageSeconds <= HOTBAR_NAME_HOLD_SECONDS) return 1f;
        float fade = (ageSeconds - HOTBAR_NAME_HOLD_SECONDS) / HOTBAR_NAME_FADE_SECONDS;
        return Math.max(0f, 1f - fade);
    }

    /**
     * Bottom Y of the held-item name: sits above the four status bars so it
     * doesn't draw through health/hunger.
     */
    public static float hotbarHeldNameY() {
        float panelTop = -1f + HOTBAR_BOTTOM_MARGIN + HOTBAR_SLOT_SIZE + HOTBAR_PADDING;
        return panelTop + STAT_BAR_STACK_MARGIN + 4f * (STAT_BAR_HEIGHT + STAT_BAR_GAP);
    }

    public static String titleFromEnum(String name) {
        StringBuilder sb = new StringBuilder(name.length());
        boolean upper = true;
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (c == '_') {
                sb.append(' ');
                upper = true;
            } else {
                sb.append(upper ? Character.toUpperCase(c) : Character.toLowerCase(c));
                upper = false;
            }
        }
        return sb.toString();
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
     * <p>
     * Accepts a full {@link ItemStack} so Tinkers' Construct parts and tools
     * render with their real per-item texture (via
     * {@link ItemTextures#bindTinkersItem}) rather than the grey sentinel placeholder.
     */
    private void addSlotIcon(float cx, float cy, float half, ItemStack stack,
                             ItemTextures itemTextures, TextureAtlas atlas, ToolDurability durability) {
        if (stack == null || stack.isEmpty()) return;
        BlockType type = stack.type();
        int count = stack.count();
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
            // Tinkers items carry a per-item payload that determines the real texture.
            if (stack.isTinkers()) {
                itemTextures.bindTinkersItem(stack.tinkersItem());
            } else {
                itemTextures.bind(type);
            }
            hotbarItemIcon.render();
            hudShader.unbind();
        } else {
            // Render as 3D isometric block
            addIsometricBlock(cx, cy, half, type, atlas);
        }

        if (count > 1) {
            String countText = Integer.toString(Math.min(count, 999));
            float digitSize = 0.028f;
            text.add(countText, cx + half - text.measure(countText, digitSize), cy - half, digitSize);
        }
        // Durability bar: Tinkers tools track wear per-item; vanilla tools use the shared ToolDurability map.
        if (stack.isTinkersTool()) {
            float fraction = stack.tinkersTool().fraction();
            if (fraction < 1f) {
                barCx.add(cx);
                barCy.add(cy);
                barFrac.add(fraction);
            }
        } else if (Mining.isTool(type) || Armor.isArmor(type)) {
            float fraction = durability.fraction(type);
            if (fraction < 1f) {
                barCx.add(cx);
                barCy.add(cy);
                barFrac.add(fraction);
            }
        }
    }

    /**
     * Silhouette of a Part Builder shape button. Uses the loaded material's
     * colour when one is in the slot so the player can preview the part;
     * the selected shape is gold-tinted, unselected ones are translucent
     * so they read as buttons rather than items sitting in the bag.
     */
    private void addGhostPartIcon(float cx, float cy, float half,
                                  com.minecraftclone.world.tinkers.ToolPartType shape,
                                  BlockType material, ItemTextures itemTextures, boolean selected) {
        if (itemTextures == null || shape == null) return;
        BlockType mat = material != null ? material : BlockType.PLANKS;
        ItemStack ghost = ItemStack.tinkersPart(
                new com.minecraftclone.world.tinkers.TinkersItem.Part(shape, mat));
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
        hudShader.setUniform("color", selected
                ? new Vector4f(1f, 0.92f, 0.35f, 1f)
                : new Vector4f(1f, 1f, 1f, 0.50f));
        itemTextures.bindTinkersItem(ghost.tinkersItem());
        hotbarItemIcon.render();
        hudShader.unbind();
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

    /**
     * Health (red), hunger (orange), thirst (cyan-blue) and stamina (yellow) bars,
     * stacked above the hotbar — plus a breath (cyan) bar on top, Minecraft-bubbles-style,
     * but only while {@code submerged}: it's meaningless (and always full) on dry land, so
     * showing it constantly would just be visual noise for a bar that never moves.
     *
     * <p>When {@code coldness} is above zero a frost-blue cold-exposure bar is appended
     * at the top of the stack. The bar reaches full width at maximum cold exposure (1.0).
     * It disappears completely once the player is warm again (coldness == 0).
     */
    public void renderStatusBars(float health, float maxHealth, float hunger, float maxHunger,
                                  float thirst, float maxThirst,
                                  float stamina, float maxStamina, float breath, float maxBreath,
                                  boolean submerged, float coldness,
                                  int hotbarSlotCount, float aspectRatio) {
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

        // Bottom to top: stamina, thirst (cyan-blue), hunger, health, (breath), (cold).
        // Thirst sits between hunger and stamina so the blue bar is easy to spot
        // without dominating; breath still appears above health when submerged;
        // the frost-blue cold-exposure bar is topmost and only visible when cold.
        y = renderStatBar(minX, maxX, y, stamina / maxStamina, new Vector4f(0.92f, 0.80f, 0.15f, 0.95f));
        y = renderStatBar(minX, maxX, y, thirst  / maxThirst,  new Vector4f(0.20f, 0.60f, 0.90f, 0.95f));
        y = renderStatBar(minX, maxX, y, hunger  / maxHunger,  new Vector4f(0.85f, 0.55f, 0.15f, 0.95f));
        y = renderStatBar(minX, maxX, y, health  / maxHealth,  new Vector4f(0.82f, 0.15f, 0.15f, 0.95f));
        if (submerged) {
            y = renderStatBar(minX, maxX, y, breath / maxBreath, new Vector4f(0.25f, 0.65f, 0.85f, 0.95f));
        }
        if (coldness > 0f) {
            // Frost-blue cold-exposure bar: pale at low exposure, deeper blue-white at max.
            renderStatBar(minX, maxX, y, Math.min(1f, coldness), new Vector4f(0.60f, 0.88f, 1.00f, 0.95f));
        }

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
            hourly.append(h % 24).append("h ").append(forecastLabel(slot.weather(), slot.strength()));
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
     * tab strip (Video / Gameplay / Sound / Controls) under it, and the rows of
     * the active tab - setting rows for the first two tabs, the keybind list for
     * Controls. The panel is always sized for the tallest tab so the tabs stay
     * put when switching. {@code selectedIndex} is a row index within the
     * active tab; {@code capturingAction} >= 0 means that keybind row is
     * waiting for a key press. A Done button at the bottom returns to the
     * pause Game Menu (in-world) or the title screen.
     */
    public void renderSettingsMenu(Settings settings, int selectedTab, int selectedIndex, int capturingAction, float aspectRatio, boolean inWorld) {
        glDisable(GL_DEPTH_TEST);
        hudTransform.identity().scale(1f / aspectRatio, 1f, 1f);

        float size = SETTINGS_SIZE;
        float panelW = settingsPanelWidth();
        float panelH = SETTINGS_PAD * 2f + SETTINGS_TITLE_H + SETTINGS_TAB_H + SETTINGS_TAB_ROWS_GAP
                + SETTINGS_MAX_ROWS * SETTINGS_ROW_H + SETTINGS_DONE_H;
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
        drawCenteredText("Options", 0f, top - SETTINGS_PAD - 0.04f, 0.042f, WHITE);

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
        // keybind list, Controller shows the gamepad-binding list, the rest
        // show their Settings rows.
        int rows = settingsRowsForTab(selectedTab, inWorld);
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
        } else if (selectedTab == Settings.TAB_CONTROLLER) {
            for (int local = 0; local < rows; local++) {
                float baseline = settingsRowTop(selectedTab, local) - SETTINGS_ROW_H + 0.013f;
                boolean selected = local == selectedIndex;
                boolean capturing = local == capturingAction;
                Vector4f rowColor = selected || capturing ? highlight : idle;
                drawTextAt(selected ? ">" : " ", left + 0.04f, baseline, size, selected ? highlight : idle);
                drawTextAt(GamepadBindings.name(local), left + SETTINGS_LEFT_PAD, baseline, size, rowColor);
                String buttonText = capturing ? "?" : GamepadBindings.buttonName(settings.getGamepadBinds().get(local));
                float valueWidth = text.measure(buttonText, size);
                drawTextAt(buttonText, left + panelW - SETTINGS_RIGHT_PAD - valueWidth, baseline, size,
                        capturing ? highlight : idleValue);
            }
        } else {
            for (int local = 0; local < rows; local++) {
                int row = settingsRowForTab(selectedTab, local, inWorld);
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

        // Sliders for the range rows of the active tab (Controls/Controller show
        // keybind/gamepad-binding lists instead, no sliders).
        if (selectedTab != Settings.TAB_CONTROLS && selectedTab != Settings.TAB_CONTROLLER) {
            float[] cx = settingsControlX();
            lineShader.bind();
            lineShader.setUniform("projection", identity);
            lineShader.setUniform("view", identity);
            lineShader.setUniform("model", hudTransform);
            for (int local = 0; local < rows; local++) {
                int row = settingsRowForTab(selectedTab, local, inWorld);
                if (Settings.isToggle(row) || Settings.isCycle(row)) continue;
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

        // Done: returns to the Game Menu (in-world) or the title screen.
        float doneY = SETTINGS_CENTER_Y - panelH / 2f + SETTINGS_PAD + 0.01f;
        drawCenteredText("Done", 0f, doneY, 0.032f, WHITE);

        drawCenteredText(selectedTab == Settings.TAB_CONTROLS || selectedTab == Settings.TAB_CONTROLLER
                        ? "Click/Enter: rebind    Tab: next section    Esc: back"
                        : "Click/Enter: toggle or adjust    Tab: next section    Esc: back",
                0f, SETTINGS_CENTER_Y - panelH / 2f - 0.045f, 0.026f, idleValue);

        glEnable(GL_DEPTH_TEST);
    }


    /** Pause menu (Game Menu) button indices. */
    public static final int PAUSE_BACK = 0;
    public static final int PAUSE_OPTIONS = 1;
    public static final int PAUSE_QUIT = 2;
    public static final int PAUSE_COUNT = 3;

    private static final String[] PAUSE_ITEMS = {
            "Back to Game",
            "Options...",
            "Save and Quit to Title"
    };

    /**
     * The in-game pause overlay (Minecraft's Game Menu): Back to Game, Options,
     * and Save and Quit to Title. Shown when Esc is pressed in a world, before
     * opening Options.
     */
    public void renderPauseMenu(int selectedIndex, float aspectRatio) {
        glDisable(GL_DEPTH_TEST);
        hudTransform.identity().scale(1f / aspectRatio, 1f, 1f);
        Vector4f idle = new Vector4f(0.88f, 0.88f, 0.88f, 1f);
        Vector4f highlight = new Vector4f(1f, 0.85f, 0.4f, 1f);

        float panelW = 1.05f;
        float panelH = 0.62f;
        float left = -panelW / 2f;
        if (guiTextures != null) {
            guiVerts.clear();
            guiInds.clear();
            renderGuiPanel(left, -0.18f, left + panelW, 0.44f);
            flushGuiQuads();
        } else {
            float[] panel = {
                    left, -0.18f, 0, left + panelW, -0.18f, 0,
                    left + panelW, 0.44f, 0,
                    left, -0.18f, 0, left + panelW, 0.44f, 0,
                    left, 0.44f, 0,
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

        drawCenteredText("Game Menu", 0f, 0.32f, 0.055f, WHITE);
        for (int i = 0; i < PAUSE_ITEMS.length; i++) {
            boolean selected = i == selectedIndex;
            float y = 0.16f - i * 0.12f;
            drawCenteredText(PAUSE_ITEMS[i], 0f, y, 0.038f, selected ? highlight : idle);
            if (selected) {
                float w = text.measure(PAUSE_ITEMS[i], 0.038f);
                drawCenteredText(">", -w / 2f - 0.06f, y, 0.038f, highlight);
            }
        }
        glEnable(GL_DEPTH_TEST);
    }

    /** The pause-menu button under the mouse, or -1. */
    public int pauseMenuItemAt(float logicalX, float logicalY) {
        for (int i = 0; i < PAUSE_ITEMS.length; i++) {
            float y = 0.16f - i * 0.12f;
            if (Math.abs(logicalY - y) <= 0.05f && Math.abs(logicalX) <= 0.5f) {
                return i;
            }
        }
        return -1;
    }

    /** True if the mouse is over the Options Done button. */
    public boolean settingsDoneAt(float logicalX, float logicalY) {
        float panelW = settingsPanelWidth();
        float panelH = SETTINGS_PAD * 2f + SETTINGS_TITLE_H + SETTINGS_TAB_H + SETTINGS_TAB_ROWS_GAP
                + SETTINGS_MAX_ROWS * SETTINGS_ROW_H + SETTINGS_DONE_H;
        float doneY = SETTINGS_CENTER_Y - panelH / 2f + SETTINGS_PAD + 0.01f;
        return Math.abs(logicalY - doneY) <= 0.035f && Math.abs(logicalX) <= panelW / 2f;
    }

    /** Main menu button indices. */
    public static final int MENU_PLAY = 0;
    public static final int MENU_MULTIPLAYER = 1;
    public static final int MENU_SETTINGS = 2;
    public static final int MENU_QUIT = 3;
    public static final int MENU_COUNT = 4;

    /** The main menu (title screen) shown before a world is created. */
    public void renderMainMenu(int selectedIndex, float aspectRatio) {
        glDisable(GL_DEPTH_TEST);
        hudTransform.identity().scale(1f / aspectRatio, 1f, 1f);
        Vector4f idle = new Vector4f(0.88f, 0.88f, 0.88f, 1f);
        Vector4f highlight = new Vector4f(1f, 0.85f, 0.4f, 1f);
        drawCenteredText("3D Minecraft Clone", 0f, 0.5f, 0.085f, WHITE);
        String[] items = {"Play", "Multiplayer", "Settings", "Quit"};
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

    /** The main-menu button under the mouse (Play/Multiplayer/Settings/Quit), or -1. */
    public int mainMenuItemAt(float logicalX, float logicalY) {
        String[] items = {"Play", "Multiplayer", "Settings", "Quit"};
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

        drawCenteredText("Select World", 0f, top - SETTINGS_PAD - 0.04f, 0.07f, WHITE);

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
        float panelW = WORLD_GEN_PANEL_W;
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

        drawCenteredText("World Generation", 0f, top - SETTINGS_PAD - 0.04f, 0.042f, WHITE);

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
                String label = WorldGenSettings.label(i);
                drawTextAt(label, left + SETTINGS_LEFT_PAD, baseline, size, color);
                String value = activeSeed ? wgs.valueText(i) + "_" : wgs.valueText(i);
                float innerW = panelW - SETTINGS_LEFT_PAD - SETTINGS_RIGHT_PAD;
                float valueSize = fitWorldGenValueSize(text.measure(label, size), text.measure(value, size),
                        size, innerW);
                float valueWidth = text.measure(value, valueSize);
                drawTextAt(value, left + panelW - SETTINGS_RIGHT_PAD - valueWidth, baseline, valueSize,
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
    /** Number of interactive rows for a settings tab (keybind/gamepad-binding actions on Controls/Controller, Settings rows elsewhere). */
    private static int settingsRowsForTab(int tab, boolean inWorld) {
        if (tab == Settings.TAB_CONTROLS) return KeyBindings.COUNT;
        if (tab == Settings.TAB_CONTROLLER) return GamepadBindings.COUNT;
        return Settings.tabRowCount(tab, inWorld);
    }

    /** The Settings row index shown as local row {@code local} on {@code tab} (only valid for plain Settings tabs). */
    private static int settingsRowForTab(int tab, int local, boolean inWorld) {
        return Settings.rowInTab(tab, local, inWorld);
    }

    /**
     * Width of the settings panel: whichever is wider between the row content
     * (widest label + room for a slider track and its value text) and the tab
     * strip across its top - without the max against the tab strip, adding a
     * long-labeled tab (e.g. "Controller") could make the strip wider than a
     * panel sized for content alone, hanging tab buttons off both edges of the
     * panel background instead of sitting inside it.
     */
    private float settingsPanelWidth() {
        float widest = 0f;
        for (int i = 0; i < Settings.ROW_COUNT; i++) {
            widest = Math.max(widest, text.measure(Settings.label(i), SETTINGS_SIZE));
        }
        for (int a = 0; a < KeyBindings.COUNT; a++) {
            widest = Math.max(widest, text.measure(KeyBindings.name(a), SETTINGS_SIZE));
        }
        float contentWidth = widest + SETTINGS_LABEL_GAP + SETTINGS_TRACK_W + SETTINGS_VALUE_GAP + SETTINGS_VALUE_W
                + SETTINGS_LEFT_PAD + SETTINGS_RIGHT_PAD;
        return Math.max(contentWidth, settingsTabStripWidth());
    }

    /** Total width of the tab strip (every tab button plus the gaps between them). */
    private float settingsTabStripWidth() {
        float total = 0f;
        for (int i = 0; i < Settings.TAB_COUNT; i++) {
            total += settingsTabWidth(i) + SETTINGS_TAB_GAP;
        }
        return total - SETTINGS_TAB_GAP;
    }

    /** Logical center-y of the tab strip. */
    private float settingsTabCenterY() {
        float panelH = SETTINGS_PAD * 2f + SETTINGS_TITLE_H + SETTINGS_TAB_H + SETTINGS_TAB_ROWS_GAP
                + SETTINGS_MAX_ROWS * SETTINGS_ROW_H + SETTINGS_DONE_H;
        float top = SETTINGS_CENTER_Y + panelH / 2f;
        return top - SETTINGS_PAD - SETTINGS_TITLE_H - SETTINGS_TAB_H / 2f;
    }

    /** Width of tab {@code t}'s button. */
    private float settingsTabWidth(int t) {
        return text.measure(Settings.tabLabel(t), 0.03f) + 0.06f;
    }

    /** Logical center-x of tab {@code t}: the strip is centered, spaced by {@link #SETTINGS_TAB_GAP}. */
    private float settingsTabCenterX(int t) {
        float x = -settingsTabStripWidth() / 2f;
        for (int i = 0; i < t; i++) {
            x += settingsTabWidth(i) + SETTINGS_TAB_GAP;
        }
        return x + settingsTabWidth(t) / 2f;
    }

    /** Top edge (logical y) of row {@code i} on the given tab. */
    private float settingsRowTop(int tab, int i) {
        float panelH = SETTINGS_PAD * 2f + SETTINGS_TITLE_H + SETTINGS_TAB_H + SETTINGS_TAB_ROWS_GAP
                + SETTINGS_MAX_ROWS * SETTINGS_ROW_H + SETTINGS_DONE_H;
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

    /**
     * Font size for a world-gen value so a long seed never runs into its label.
     * {@code innerWidth} is the row's content width (panel minus left/right pads).
     */
    static float fitWorldGenValueSize(float labelWidth, float valueWidth, float size, float innerWidth) {
        float max = innerWidth - labelWidth - WORLD_GEN_VALUE_GAP;
        if (valueWidth <= 0f || max <= 0f || valueWidth <= max) return size;
        return size * (max / valueWidth);
    }

    /** Inner content width of the Create New World panel (label + gap + value). */
    static float worldGenInnerWidth() {
        return WORLD_GEN_PANEL_W - SETTINGS_LEFT_PAD - SETTINGS_RIGHT_PAD;
    }

    /** The world-gen row (0..ROW_COUNT, with ROW_COUNT being the Done button) under the mouse, or -1. */
    public int worldGenRowAt(float logicalX, float logicalY) {
        int rows = WorldGenSettings.ROW_COUNT + 1;
        float panelW = WORLD_GEN_PANEL_W;
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

    /** Multiplayer connect-screen row indices. */
    public static final int MP_ROW_NAME = 0;
    public static final int MP_ROW_HOST = 1;
    public static final int MP_ROW_PORT = 2;
    public static final int MP_ROW_HOST_SERVER = 3; // start an embedded server, then join it
    public static final int MP_ROW_CONNECT = 4;     // join an existing server
    public static final int MP_ROW_BACK = 5;
    public static final int MP_ROW_COUNT = 6;

    private float mpRowTop(int i) {
        int rows = MP_ROW_COUNT;
        float panelH = SETTINGS_PAD * 2f + SETTINGS_TITLE_H + rows * SETTINGS_ROW_H;
        float top = SETTINGS_CENTER_Y + panelH / 2f;
        return top - SETTINGS_PAD - SETTINGS_TITLE_H - i * SETTINGS_ROW_H;
    }

    /** The multiplayer connect screen: name / host / port fields + Host Server / Connect / Back buttons. */
    public void renderMultiplayerMenu(String name, String host, String port, int selectedIndex, int editingRow, float aspectRatio) {
        glDisable(GL_DEPTH_TEST);
        hudTransform.identity().scale(1f / aspectRatio, 1f, 1f);

        int rows = MP_ROW_COUNT;
        float size = SETTINGS_SIZE;
        float panelW = 0.95f;
        float panelH = SETTINGS_PAD * 2f + SETTINGS_TITLE_H + rows * SETTINGS_ROW_H;
        float left = -panelW / 2f;
        float top = SETTINGS_CENTER_Y + panelH / 2f;

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

        drawCenteredText("Multiplayer", 0f, top - SETTINGS_PAD - 0.04f, 0.042f, WHITE);

        Vector4f idle = new Vector4f(0.88f, 0.88f, 0.88f, 1f);
        Vector4f idleValue = new Vector4f(0.7f, 0.7f, 0.7f, 1f);
        Vector4f highlight = new Vector4f(1f, 0.85f, 0.4f, 1f);
        String[] labels = {"Player name", "Host", "Port", "Host & Play", "Join Server", "Back"};
        String[] values = {name, host, port, null, null, null};
        for (int i = 0; i < rows; i++) {
            float baseline = mpRowTop(i) - SETTINGS_ROW_H + 0.013f;
            boolean selected = i == selectedIndex;
            boolean editable = i <= MP_ROW_PORT;
            boolean activeEdit = i == editingRow;
            Vector4f color = selected ? highlight : (activeEdit ? highlight : idle);
            drawTextAt(selected ? ">" : " ", left + 0.04f, baseline, size, selected ? highlight : idle);
            drawTextAt(labels[i], left + SETTINGS_LEFT_PAD, baseline, size, color);
            if (editable) {
                String value = activeEdit ? values[i] + "_" : values[i];
                float valueWidth = text.measure(value, size);
                drawTextAt(value, left + panelW - SETTINGS_RIGHT_PAD - valueWidth, baseline, size,
                        activeEdit ? highlight : idleValue);
            }
        }
        glEnable(GL_DEPTH_TEST);
    }

    /** The multiplayer row under the mouse, or -1. */
    public int multiplayerRowAt(float logicalX, float logicalY) {
        float panelW = 0.95f;
        float left = -panelW / 2f;
        for (int i = 0; i < MP_ROW_COUNT; i++) {
            float rowTop = mpRowTop(i);
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
    public int settingsRowAt(float logicalX, float logicalY, int tab, boolean inWorld) {
        float panelW = settingsPanelWidth();
        float left = -panelW / 2f;
        for (int i = 0; i < settingsRowsForTab(tab, inWorld); i++) {
            float rowTop = settingsRowTop(tab, i);
            if (logicalX >= left && logicalX <= left + panelW
                    && logicalY <= rowTop && logicalY >= rowTop - SETTINGS_ROW_H) {
                return i;
            }
        }
        return -1;
    }

    /** If the mouse is over a range row's slider track, the click fraction (0..1); otherwise -1. */
    public float settingsTrackAt(float logicalX, float logicalY, int tab, boolean inWorld) {
        int row = settingsRowAt(logicalX, logicalY, tab, inWorld);
        if (row < 0 || tab == Settings.TAB_CONTROLS || tab == Settings.TAB_CONTROLLER
                || Settings.isToggle(settingsRowForTab(tab, row, inWorld))
                || Settings.isCycle(settingsRowForTab(tab, row, inWorld))) return -1f;
        float[] cx = settingsControlX();
        if (logicalX < cx[0] - 0.012f || logicalX > cx[1] + 0.012f) return -1f;
        return settingsSliderAt(logicalX, row, tab);
    }

    /** Clamped slider fraction (0..1) from an x position - for dragging a known row. */
    public float settingsSliderAt(float logicalX, int row, int tab) {
        float[] cx = settingsControlX();
        return Math.max(0f, Math.min(1f, (logicalX - cx[0]) / (cx[1] - cx[0])));
    }

    /**
     * Draws one line of text left-aligned at (bottomLeftX, bottomY), with a
     * dark drop shadow behind it, assuming {@link #hudTransform} is already
     * set up. Every panel/menu screen's body text (settings rows, forecast,
     * container GUI labels, ...) goes through this - the light GUI theme's
     * panel fill is a pale gray, and most of this text's own colors (near-
     * white idle/dim grays, plain white) sit close enough to that gray to be
     * hard to read without the shadow; the shadow's own alpha still eases
     * off on the dark theme (see {@code darkGui} below) since its darker
     * panel already gives white text plenty of contrast on its own.
     */
    private void drawTextAt(String value, float bottomLeftX, float bottomY, float size, Vector4f color) {
        float shadowOffset = size * 0.06f;
        text.begin();
        text.add(value, bottomLeftX + shadowOffset, bottomY - shadowOffset, size);
        text.render(hudTransform, new Vector4f(0f, 0f, 0f, darkGui ? 0.9f : 0.45f));
        text.begin();
        text.add(value, bottomLeftX, bottomY, size);
        text.render(hudTransform, color);
    }

    /** Draws one line of text horizontally centered on {@code centerX}, with the same drop shadow as {@link #drawTextAt}. */
    private void drawCenteredText(String value, float centerX, float bottomY, float size, Vector4f color) {
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

    /**
     * Emits a quad from 4 arbitrary vertex positions (not necessarily axis-aligned).
     * Vertices are specified in counter-clockwise order: bottom-left, bottom-right, top-right, top-left.
     */
    private void addArbitraryQuad(float x0, float y0, float x1, float y1, float x2, float y2, float x3, float y3, float[] uv) {
        float u0 = uv[0], v0 = uv[1], u1 = uv[2], v1 = uv[3];
        int base = blockVertexCounter;
        blockVertices.add(x0); blockVertices.add(y0); blockVertices.add(u0); blockVertices.add(v1);
        blockVertices.add(x1); blockVertices.add(y1); blockVertices.add(u1); blockVertices.add(v1);
        blockVertices.add(x2); blockVertices.add(y2); blockVertices.add(u1); blockVertices.add(v0);
        blockVertices.add(x3); blockVertices.add(y3); blockVertices.add(u0); blockVertices.add(v0);
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

    /**
     * Renders a block as a 3D isometric shape in an inventory slot.
     * Full cubes fill the slot; slabs are the bottom half; stairs are two
     * steps (low tread in front, high tread in back).
     */
    private void addIsometricBlock(float cx, float cy, float half, BlockType type, TextureAtlas atlas) {
        float[] topUv = atlas.getUV(type.topTile);
        float[] sideUv = atlas.getUV(type.sideTile);
        float[] frontUv = type.isDirectional() ? atlas.getUV(type.frontTile) : sideUv;
        if (type.stair) {
            // Low step: full footprint, half height. High step: back half, top half.
            addIsometricBox(cx, cy, half, 0f, 0f, 0f, 1f, 0.5f, 1f, topUv, sideUv);
            addIsometricBox(cx, cy, half, 0f, 0.5f, 0.5f, 1f, 1f, 1f, topUv, sideUv);
            return;
        }
        float height = isometricIconHeight(type);
        addIsometricBox(cx, cy, half, 0f, 0f, 0f, 1f, height, 1f, topUv, sideUv, frontUv);
    }

    /**
     * One axis-aligned box in unit-cube space (0..1), projected to 2:1 dimetric
     * slot coordinates. Only the three camera-facing faces are emitted (top,
     * west, south) — the same three a Minecraft inventory cube shows.
     */
    private void addIsometricBox(float cx, float cy, float half,
                                 float x0, float y0, float z0, float x1, float y1, float z1,
                                 float[] topUv, float[] sideUv) {
        addIsometricBox(cx, cy, half, x0, y0, z0, x1, y1, z1, topUv, sideUv, sideUv);
    }

    private void addIsometricBox(float cx, float cy, float half,
                                 float x0, float y0, float z0, float x1, float y1, float z1,
                                 float[] topUv, float[] sideUv, float[] frontUv) {
        // West (x = x0): left parallelogram, CCW y-up
        addIsoQuad(cx, cy, half,
                x0, y0, z1,  x0, y0, z0,  x0, y1, z0,  x0, y1, z1, sideUv);
        // South (z = z0): right parallelogram — lock/front on directional blocks
        addIsoQuad(cx, cy, half,
                x0, y0, z0,  x1, y0, z0,  x1, y1, z0,  x0, y1, z0, frontUv);
        // Top (y = y1)
        addIsoQuad(cx, cy, half,
                x0, y1, z1,  x0, y1, z0,  x1, y1, z0,  x1, y1, z1, topUv);
    }

    private void addIsoQuad(float cx, float cy, float half,
                            float x0, float y0, float z0,
                            float x1, float y1, float z1,
                            float x2, float y2, float z2,
                            float x3, float y3, float z3,
                            float[] uv) {
        float[] p0 = isoPoint(cx, cy, half, x0, y0, z0);
        float[] p1 = isoPoint(cx, cy, half, x1, y1, z1);
        float[] p2 = isoPoint(cx, cy, half, x2, y2, z2);
        float[] p3 = isoPoint(cx, cy, half, x3, y3, z3);
        addArbitraryQuad(p0[0], p0[1], p1[0], p1[1], p2[0], p2[1], p3[0], p3[1], uv);
    }

    /**
     * Vertical extent of an inventory icon in unit-cube space. Slabs (and other
     * half-height blocks) are 0.5; stairs are drawn as two steps instead.
     */
    static float isometricIconHeight(BlockType type) {
        if (type == null) return 1f;
        if (type.slab || type.isSnowCappedSlab() || type.isBed()) return 0.5f;
        float h = type.collisionHeight;
        if (h <= 0f || h > 1f) return 1f;
        return h;
    }

    /**
     * 2:1 dimetric projection of a point in unit-cube space (x,y,z in 0..1)
     * into slot coordinates. Package-visible for icon-shape tests.
     */
    static float[] isoPoint(float cx, float cy, float half, float xw, float yw, float zw) {
        float x = half * 0.92f;
        float sx = cx + (xw - zw) * x;
        float sy = (cy - x) + yw * x + (xw + zw) * (x * 0.5f);
        return new float[]{sx, sy};
    }

    /**
     * 2:1 dimetric cube in slot space. Returns
     * {@code {halfWidth, topY, eqY, frontY, botEqY, botFrontY}}.
     * A full cube ({@code height == 1}) is a square centered on {@code cy};
     * shorter heights sit on that same ground line so slabs read as the
     * bottom half of a block.
     */
    static float[] isometricCube(float cx, float cy, float half, float height) {
        float h = height <= 0f || height > 1f ? 1f : height;
        float x = half * 0.92f;
        float[] backTop  = isoPoint(cx, cy, half, 1f, h, 1f);
        float[] leftTop  = isoPoint(cx, cy, half, 0f, h, 1f);
        float[] frontTop = isoPoint(cx, cy, half, 0f, h, 0f);
        float[] leftBot  = isoPoint(cx, cy, half, 0f, 0f, 1f);
        float[] frontBot = isoPoint(cx, cy, half, 0f, 0f, 0f);
        return new float[]{x, backTop[1], leftTop[1], frontTop[1], leftBot[1], frontBot[1]};
    }

    private static float[] outlineLines(float cx, float cy, float half) {
        return outlineRect(cx - half, cy - half, cx + half, cy + half);
    }

    private static float[] outlineRect(float minX, float minY, float maxX, float maxY) {
        return new float[]{
                minX, minY, 0, maxX, minY, 0,
                maxX, minY, 0, maxX, maxY, 0,
                maxX, maxY, 0, minX, maxY, 0,
                minX, maxY, 0, minX, minY, 0,
        };
    }

    private float invGridWidth() {
        return 9 * INV_SLOT + 8 * INV_GAP;
    }

    private float invGridLeft() {
        return invGridLeftX();
    }

    /** Center x of the player's inventory column 0. Package-visible for layout tests. */
    static float invGridLeftX() {
        float gridW = 9 * INV_SLOT + 8 * INV_GAP;
        return INV_GRID_CENTER_X - gridW / 2f + INV_SLOT / 2f;
    }

    /** Hit-box size of a container slot in logical-square units. */
    static float containerSlotSize() {
        return INV_SLOT;
    }

    static float pbStationBottomY() {
        return PB_STATION_BOTTOM_Y;
    }

    static float pbShapeTopY() {
        int rows = (ContainerGui.PB_SHAPE_COUNT + PB_SHAPE_COLS - 1) / PB_SHAPE_COLS;
        return PB_STATION_BOTTOM_Y + (rows - 1) * INV_STEP;
    }

    static float pbShapeLeftX() {
        return invGridLeftX() + 2 * INV_STEP;
    }

    static float pbMatX() { return invGridLeftX(); }
    static float pbOutX() { return invGridLeftX() + 8 * INV_STEP; }
    static float pbMatY() { return (PB_STATION_BOTTOM_Y + pbShapeTopY()) / 2f; }
    static float pbOutY() { return pbMatY(); }

    static float pbShapeX(int index) {
        return pbShapeLeftX() + (index % PB_SHAPE_COLS) * INV_STEP;
    }

    static float pbShapeY(int index) {
        return pbShapeTopY() - (index / PB_SHAPE_COLS) * INV_STEP;
    }

    /** Logical-square center of a Part Builder slot, or {@code null} if not a PB slot. */
    static float[] partBuilderSlotCenter(int slotId) {
        if (slotId == ContainerGui.PB_MATERIAL_SLOT) return new float[]{pbMatX(), pbMatY()};
        if (slotId == ContainerGui.PB_OUTPUT_SLOT)   return new float[]{pbOutX(), pbOutY()};
        if (slotId >= ContainerGui.PB_SHAPE_SLOT_0
                && slotId < ContainerGui.PB_SHAPE_SLOT_0 + ContainerGui.PB_SHAPE_COUNT) {
            int i = slotId - ContainerGui.PB_SHAPE_SLOT_0;
            return new float[]{pbShapeX(i), pbShapeY(i)};
        }
        return null;
    }

    /** Logical-square center of a player-inventory slot (0..35), ignoring chest shift. */
    static float[] playerInventorySlotCenter(int slotId) {
        int r, c;
        if (slotId < Inventory.HOTBAR_SIZE) {
            r = 3;
            c = slotId;
        } else {
            int s = slotId - Inventory.HOTBAR_SIZE;
            r = s / 9;
            c = s % 9;
        }
        return new float[]{invGridLeftX() + c * INV_STEP, INV_TOP_ROW_Y - r * INV_STEP};
    }

    static float tsSlotX(int index) {
        return invGridLeftX() + index * INV_STEP;
    }

    static float tsSlotY() {
        return TS_STATION_Y;
    }

    static float tsOutX() {
        return invGridLeftX() + 8 * INV_STEP;
    }

    static float tsOutY() {
        return TS_STATION_Y;
    }

    /** Logical-square center of a Tool Station slot, or {@code null} if not a TS slot. */
    static float[] toolStationSlotCenter(int slotId) {
        if (slotId >= ContainerGui.TS_SLOT_0
                && slotId < ContainerGui.TS_SLOT_0 + com.minecraftclone.world.tinkers.ToolStationGui.INPUT_SLOTS) {
            return new float[]{tsSlotX(slotId - ContainerGui.TS_SLOT_0), tsSlotY()};
        }
        if (slotId == ContainerGui.TS_OUTPUT_SLOT) return new float[]{tsOutX(), tsOutY()};
        return null;
    }

    /** Role name drawn above a Tool Station input slot. */
    static String tsRoleLabel(int index) {
        return switch (index) {
            case 0 -> "Head";
            case 1 -> "Rod";
            default -> "Extra";
        };
    }

    /** True for the placed 3x3 / 5x5 workbenches (not the player's 2x2). */
    private static boolean isTableCrafting(ContainerGui gui) {
        return gui.kind() == ContainerGui.Kind.CRAFTING_TABLE
                || gui.kind() == ContainerGui.Kind.ADVANCED_CRAFTING_TABLE;
    }

    /** Center y of a table-crafting grid's bottom row, stacked above the player inventory. */
    private static float tableCraftBottomY() {
        return INV_TOP_ROW_Y + TABLE_CRAFT_GAP;
    }

    /** Center x of column 0 of a table-crafting grid, aligned to the player's 9-wide bag. */
    private float tableCraftLeftX() {
        return invGridLeft();
    }

    /** Center y of row 0 of a table-crafting grid {@code height} rows tall. */
    private float tableCraftTopY(int height) {
        return tableCraftBottomY() + (height - 1) * INV_STEP;
    }

    /** Output slot just to the right of a {@code width}-column table grid, vertically centered. */
    private float tableCraftOutputX(int width) {
        return tableCraftLeftX() + width * INV_STEP + 0.06f;
    }

    private float tableCraftOutputY(int height) {
        return tableCraftTopY(height) - (height - 1) * INV_STEP / 2f;
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
        // Part Builder slots — stacked above the bag, 4×2 shape grid.
        if (gui.kind() == ContainerGui.Kind.PART_BUILDER) {
            float[] pb = partBuilderSlotCenter(slotId);
            if (pb != null) return pb;
        }
        // Tool Station slots — stacked above the bag, aligned to inventory columns.
        if (gui.kind() == ContainerGui.Kind.TOOL_STATION) {
            float[] ts = toolStationSlotCenter(slotId);
            if (ts != null) return ts;
        }

        if (gui.isOutputSlot(slotId)) {
            if (isTableCrafting(gui)) {
                int n = gui.gridWidth();
                return new float[]{tableCraftOutputX(n), tableCraftOutputY(n)};
            }
            return new float[]{OUTPUT_X, OUTPUT_Y};
        }
        if (gui.isGridSlot(slotId)) {
            int g = slotId - ContainerGui.GRID_START;
            int width = gui.gridWidth();
            int r = g / width, c = g % width;
            if (isTableCrafting(gui)) {
                return new float[]{
                        tableCraftLeftX() + c * INV_STEP,
                        tableCraftTopY(width) - r * INV_STEP
                };
            }
            return new float[]{CRAFT_LEFT_X + c * INV_STEP, CRAFT_TOP_ROW_Y - r * INV_STEP};
        }
        if (gui.isArmorSlot(slotId)) {
            // A vertical 1x4 column just left of the player's inventory grid:
            // helmet on top, then chestplate, leggings, boots. Aligned to the
            // grid's row spacing so the four read as one tidy stack.
            int a = slotId - ContainerGui.ARMOR_START;
            return new float[]{ARMOR_X, INV_TOP_ROW_Y - a * INV_STEP};
        }
        if (gui.isContainerSlot(slotId)) {
            int cs = slotId - ContainerGui.CONTAINER_START;
            if (gui.kind() == ContainerGui.Kind.CHEST) {
                // A chest is a rows-by-9 grid of slots above the player's inventory.
                int r = cs / CHEST_COLUMNS, c = cs % CHEST_COLUMNS;
                return new float[]{invGridLeft() + c * INV_STEP, chestTopRowY(gui) - r * INV_STEP - chestLayoutShift(gui)};
            }
            if (gui.kind() == ContainerGui.Kind.SMELTERY) {
                // Smeltery: input top-left, output mid-right - same layout the
                // furnace uses, minus the fuel slot (lava below does the heating).
                if (cs == com.minecraftclone.world.multiblock.SmelteryEntity.SLOT_OUTPUT) {
                    return new float[]{FURNACE_OUTPUT_X, FURNACE_MID_Y};
                }
                return new float[]{FURNACE_INPUT_X, INV_TOP_ROW_Y};
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
        // Walk high ids first so the 2x2/3x3 crafting grid, output, and armor
        // win if they sit near the player inventory (low ids 0..35).
        for (int id = gui.slotCount() - 1; id >= 0; id--) {
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

    /** Center (logical x, y) of catalog item {@code index} (row-major, 9 per row), shifted by {@code scrollRows}. */
    public static float[] catalogItemCenter(int index, float scrollRows) {
        int r = index / CAT_COLUMNS, c = index % CAT_COLUMNS;
        float gridW = CAT_COLUMNS * CAT_SLOT + (CAT_COLUMNS - 1) * CAT_GAP;
        float left = -gridW / 2f + CAT_SLOT / 2f;
        return new float[]{left + c * CAT_STEP, CAT_GRID_TOP_Y - r * CAT_STEP + scrollRows * CAT_STEP};
    }

    /** Bottom of the last fully-visible catalog row — sits above the hotbar. */
    static float catalogClipBottomY() {
        return -1f + HOTBAR_BOTTOM_MARGIN + HOTBAR_SLOT_SIZE + HOTBAR_PADDING + CAT_HOTBAR_GAP;
    }

    public static int catalogVisibleRows() {
        return Math.max(1, (int) Math.floor((CAT_GRID_TOP_Y - catalogClipBottomY()) / CAT_STEP) + 1);
    }

    static int catalogRowCount(int itemCount) {
        return (itemCount + CAT_COLUMNS - 1) / CAT_COLUMNS;
    }

    public static float catalogMaxScroll(int itemCount) {
        return Math.max(0f, catalogRowCount(itemCount) - catalogVisibleRows());
    }

    public static float clampCatalogScroll(float scrollRows, int itemCount) {
        float max = catalogMaxScroll(itemCount);
        if (scrollRows < 0f) return 0f;
        if (scrollRows > max) return max;
        return scrollRows;
    }

    public static boolean catalogItemVisible(int index, float scrollRows) {
        float y = catalogItemCenter(index, scrollRows)[1];
        return y <= CAT_GRID_TOP_Y + 1e-4f && y >= catalogClipBottomY() - 1e-4f;
    }

    public static float catalogGridRightX() {
        float gridW = CAT_COLUMNS * CAT_SLOT + (CAT_COLUMNS - 1) * CAT_GAP;
        return gridW / 2f;
    }

    static float catalogScrollbarX() {
        return catalogGridRightX() + CAT_SCROLLBAR_GAP + CAT_SCROLLBAR_W / 2f;
    }

    static float catalogScrollbarTopY() {
        return CAT_GRID_TOP_Y + CAT_SLOT / 2f;
    }

    static float catalogScrollbarBottomY() {
        int vis = catalogVisibleRows();
        return CAT_GRID_TOP_Y - (vis - 1) * CAT_STEP - CAT_SLOT / 2f;
    }

    /** True if the mouse is over the catalog scrollbar track (only when the tab overflows). */
    public boolean creativeScrollbarAt(float logicalX, float logicalY, int itemCount) {
        if (catalogMaxScroll(itemCount) <= 0f) return false;
        float cx = catalogScrollbarX();
        float top = catalogScrollbarTopY();
        float bot = catalogScrollbarBottomY();
        return Math.abs(logicalX - cx) <= CAT_SCROLLBAR_W / 2f + 0.006f
                && logicalY <= top + 0.004f && logicalY >= bot - 0.004f;
    }

    static float catalogGridLeftX() {
        return -catalogGridRightX();
    }

    static float searchBoxLeft() {
        return catalogGridLeftX();
    }

    static float searchBoxRight() {
        return catalogGridRightX();
    }

    static float searchBoxTop() {
        return SEARCH_CENTER_Y + SEARCH_H / 2f;
    }

    static float searchBoxBottom() {
        return SEARCH_CENTER_Y - SEARCH_H / 2f;
    }

    static float searchClearX() {
        return searchBoxRight() - 0.038f;
    }

    /** True if the mouse is over the creative search field. */
    public boolean creativeSearchAt(float logicalX, float logicalY) {
        return logicalX >= searchBoxLeft() && logicalX <= searchBoxRight()
                && Math.abs(logicalY - SEARCH_CENTER_Y) <= SEARCH_H / 2f;
    }

    /** True if the mouse is over the search field's clear "x". */
    public boolean creativeSearchClearAt(float logicalX, float logicalY) {
        return Math.abs(logicalX - searchClearX()) <= 0.03f
                && Math.abs(logicalY - SEARCH_CENTER_Y) <= SEARCH_H / 2f;
    }

    public static int searchMaxChars() {
        return SEARCH_MAX_CHARS;
    }

    /** Maps a click on the scrollbar to a scroll-row offset. */
    public float catalogScrollForY(float logicalY, int itemCount) {
        float top = catalogScrollbarTopY();
        float bot = catalogScrollbarBottomY();
        float span = top - bot;
        if (span <= 1e-4f) return 0f;
        float t = (top - logicalY) / span;
        if (t < 0f) t = 0f;
        if (t > 1f) t = 1f;
        return t * catalogMaxScroll(itemCount);
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

    /** The index into the currently shown catalog list under the mouse, or -1. */
    public int creativeItemAt(float logicalX, float logicalY, int itemCount, float scrollRows) {
        return catalogItemAt(logicalX, logicalY, itemCount, scrollRows);
    }

    static int catalogItemAt(float logicalX, float logicalY, int itemCount, float scrollRows) {
        float half = CAT_SLOT / 2f;
        for (int i = 0; i < itemCount; i++) {
            if (!catalogItemVisible(i, scrollRows)) continue;
            float[] c = catalogItemCenter(i, scrollRows);
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
        glDisable(GL_CULL_FACE);
        hudTransform.identity().scale(1f / aspectRatio, 1f, 1f);

        // Panel background spanning the container area and the inventory grid.
        // A chest's grid stacks above the player grid, so its panel extends
        // upward to cover it (higher for a 54-slot double chest, taller still
        // for a 108-slot 2x2); it also hugs the 9-wide inventory grid rather
        // than reaching out to the crafting grid's column, since a chest has no
        // crafting grid. Tall chests shift the whole screen down so every slot
        // stays the same size.
        float gridW = invGridWidth();
        boolean chest  = gui.kind() == ContainerGui.Kind.CHEST;
        boolean tinkers = gui.kind() == ContainerGui.Kind.PART_BUILDER
                       || gui.kind() == ContainerGui.Kind.TOOL_STATION;
        float shift = chestLayoutShift(gui);
        float panelLeft, panelRight, panelTop;
        if (chest) {
            panelLeft  = INV_GRID_CENTER_X - gridW / 2f - 0.03f;
            panelRight = INV_GRID_CENTER_X + gridW / 2f + 0.03f;
            panelTop   = chestTopRowY(gui) + INV_SLOT / 2f + 0.055f - shift;
        } else if (gui.kind() == ContainerGui.Kind.PART_BUILDER) {
            panelLeft  = INV_GRID_CENTER_X - gridW / 2f - 0.03f;
            panelRight = INV_GRID_CENTER_X + gridW / 2f + 0.03f;
            panelTop   = pbShapeTopY() + INV_SLOT / 2f + 0.10f;
        } else if (gui.kind() == ContainerGui.Kind.TOOL_STATION) {
            panelLeft  = INV_GRID_CENTER_X - gridW / 2f - 0.03f;
            panelRight = INV_GRID_CENTER_X + gridW / 2f + 0.03f;
            panelTop   = tsSlotY() + INV_SLOT / 2f + 0.10f;
        } else if (isTableCrafting(gui)) {
            int n = gui.gridWidth();
            panelLeft  = invGridLeft() - INV_SLOT / 2f - 0.03f;
            panelRight = INV_GRID_CENTER_X + gridW / 2f + 0.03f;
            panelTop   = tableCraftTopY(n) + INV_SLOT / 2f + 0.055f;
        } else {
            panelLeft  = CRAFT_LEFT_X - INV_SLOT / 2f - 0.03f;
            panelRight = INV_GRID_CENTER_X + gridW / 2f + 0.03f;
            panelTop   = INV_TOP_ROW_Y + INV_SLOT / 2f + 0.055f;
        }
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

        // Furnace decorations (flame + arrow) behind the slot icons. Steam
        // machines reuse the same bars via the shared ProgressMachine view.
        if (gui.kind() == ContainerGui.Kind.FURNACE && gui.furnace() != null) {
            renderFurnaceProgress(gui.furnace());
        } else if (gui.kind() == ContainerGui.Kind.FURNACE
                && gui.container() instanceof com.minecraftclone.world.ProgressMachine pm) {
            renderMachineProgress(pm);
        }
        // Smeltery decorations (heat flame + progress arrow) behind the slot icons.
        if (gui.kind() == ContainerGui.Kind.SMELTERY && gui.smeltery() != null) {
            renderSmelteryProgress(gui.smeltery());
        }

        // Part Builder decorations: selected-shape highlight + arrow.
        if (gui.kind() == ContainerGui.Kind.PART_BUILDER) {
            renderPartBuilderDecorations(gui);
        }

        // Tool Station decorations: arrow from inputs to output.
        if (gui.kind() == ContainerGui.Kind.TOOL_STATION) {
            renderToolStationDecorations(gui);
        }

        // Crafting-table arrow from the 3x3/5x5 to the result slot.
        if (isTableCrafting(gui)) {
            renderCraftingTableDecorations(gui);
        }

        // A solid divider band between the chest's grid and the player's
        // inventory, so the two spaces read as separate sections at a glance.
        // The chest's bottom row sits CHEST_BOTTOM_ROW_Y above the player's top
        // row, leaving a clear gap for it. Table crafting uses the same gap.
        if (chest || isTableCrafting(gui) || tinkers) {
            float upperBottom = chest ? CHEST_BOTTOM_ROW_Y
                    : isTableCrafting(gui) ? tableCraftBottomY()
                    : PB_STATION_BOTTOM_Y;
            float sepCenter = (upperBottom + INV_TOP_ROW_Y) / 2f - shift;
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
        com.minecraftclone.world.tinkers.PartBuilderGui pbGui =
                gui.kind() == ContainerGui.Kind.PART_BUILDER ? gui.partBuilderGui() : null;
        BlockType pbGhostMat = (pbGui != null && pbGui.materialType() != null)
                ? pbGui.materialType() : BlockType.PLANKS;
        com.minecraftclone.world.tinkers.ToolPartType pbSelected =
                pbGui != null ? pbGui.selectedShape() : null;
        for (int id = 0; id < gui.slotCount(); id++) {
            float[] c = slotCenter(gui, id);
            if (c == null) continue;
            if (gui.isPbShapeSlot(id)) {
                int idx = id - ContainerGui.PB_SHAPE_SLOT_0;
                com.minecraftclone.world.tinkers.ToolPartType shape =
                        com.minecraftclone.world.tinkers.ToolPartType.values()[idx];
                addGhostPartIcon(c[0], c[1], iconHalf, shape, pbGhostMat, itemTextures,
                        shape == pbSelected);
                continue;
            }
            if (gui.isTsInputSlot(id) && gui.stackOf(id).isEmpty()) {
                int idx = id - ContainerGui.TS_SLOT_0;
                com.minecraftclone.world.tinkers.ToolPartType ghost = switch (idx) {
                    case 0 -> com.minecraftclone.world.tinkers.ToolPartType.PICK_HEAD;
                    case 1 -> com.minecraftclone.world.tinkers.ToolPartType.TOOL_ROD;
                    default -> com.minecraftclone.world.tinkers.ToolPartType.BINDING;
                };
                addGhostPartIcon(c[0], c[1], iconHalf, ghost, BlockType.PLANKS, itemTextures, false);
                continue;
            }
            addSlotIcon(c[0], c[1], iconHalf, gui.stackOf(id), itemTextures, atlas, durability);
        }
        Crafting.Recipe recipe = gui.currentRecipe();
        if (recipe != null) {
            float[] c = slotCenter(gui, gui.outputSlotId());
            if (c != null) {
                addSlotIcon(c[0], c[1], iconHalf, ItemStack.of(recipe.output(), recipe.outputAmount()), itemTextures, atlas, durability);
            }
        }
        flushBlockBatch(atlas);
        text.render(hudTransform, WHITE);
        renderDurabilityBars(iconHalf);

        // Cursor stack following the mouse, drawn on top.
        if (controller.hasCursorItem()) {
            drawCursorStack(atlas, itemTextures, controller.cursor(), cursorLx + 0.02f, cursorLy - 0.02f);
        }

        // Tooltip for the hovered slot.
        if (gui.isPbShapeSlot(hoveredSlot)) {
            int idx = hoveredSlot - ContainerGui.PB_SHAPE_SLOT_0;
            com.minecraftclone.world.tinkers.ToolPartType shape = com.minecraftclone.world.tinkers.ToolPartType.values()[idx];
            renderTooltip(new String[]{"Shape: " + shape.name()
                    .replace('_', ' ').toLowerCase(java.util.Locale.ROOT)}, cursorLx, cursorLy, aspectRatio);
        } else if (gui.isTsInputSlot(hoveredSlot) && gui.stackOf(hoveredSlot).isEmpty()) {
            int idx = hoveredSlot - ContainerGui.TS_SLOT_0;
            String[] lines = switch (idx) {
                case 0 -> new String[]{"Head", "Pick, axe, sword or shovel head"};
                case 1 -> new String[]{"Rod", "Tool rod or tough rod"};
                default -> new String[]{"Extra", "Binding, plate or extra part"};
            };
            renderTooltip(lines, cursorLx, cursorLy, aspectRatio);
        } else if (hoveredSlot >= 0) {
            ItemStack hovered;
            if (gui.isPbOutputSlot(hoveredSlot)) {
                hovered = gui.partBuilderGui() != null ? gui.partBuilderGui().currentOutput() : ItemStack.EMPTY;
            } else if (gui.isTsOutputSlot(hoveredSlot)) {
                hovered = gui.toolStationGui() != null ? gui.toolStationGui().currentOutput() : ItemStack.EMPTY;
            } else if (gui.isOutputSlot(hoveredSlot)) {
                hovered = recipe != null ? ItemStack.of(recipe.output(), recipe.outputAmount()) : ItemStack.EMPTY;
            } else {
                hovered = gui.stackOf(hoveredSlot);
            }
            if (!hovered.isEmpty()) {
                renderTooltip(tooltipLines(hovered, durability), cursorLx, cursorLy, aspectRatio);
            }
        }

        // Title + hint line. The title gets a shadow so it stays legible on the
        // bright light-theme panel.
        drawCenteredText(gui.title(), 0f, panelTop - 0.05f, 0.045f, WHITE);
        drawCenteredText("Left: take/place stack    Right: one item    Shift-click: move    Drag: spread    Esc: close",
                0f, panelBottom - 0.04f, 0.022f, new Vector4f(0.7f, 0.7f, 0.7f, 1f));

        glEnable(GL_CULL_FACE);
        glEnable(GL_DEPTH_TEST);
    }

    /**
     * Draws a compact translucent panel (top-right) listing the online
     * players - held open with the Tab key in multiplayer.
     */
    public void renderPlayerList(List<String> names, float aspectRatio) {
        if (names == null || names.isEmpty()) return;
        float size = 0.035f;
        float rowStep = 0.055f;
        float panelW = 0.62f;
        float panelH = 0.06f + names.size() * rowStep;
        float x1 = 0.97f;                       // right edge
        float y1 = 0.97f;                       // top edge
        float y0 = y1 - panelH;
        float x0 = x1 - panelW;

        lineShader.bind();
        lineShader.setUniform("projection", identity);
        lineShader.setUniform("view", identity);
        lineShader.setUniform("model", hudTransform);

        FloatArray quads = new FloatArray(8);
        // Depth test off: world geometry must never occlude this HUD panel
        // (drawTextLeft already renders without it).
        org.lwjgl.opengl.GL11.glDisable(org.lwjgl.opengl.GL11.GL_DEPTH_TEST);
        // Convert logical-square coords to this HUD's aspect-corrected space.
        float lx0 = x0 * aspectRatio, lx1 = x1 * aspectRatio;
        addQuad3(quads, lx0, y0, lx1, y1);
        inventoryPanel.upload(quads.toArray());
        lineShader.setUniform("color", new Vector4f(0.05f, 0.05f, 0.08f, 0.72f));
        inventoryPanel.render();

        // Header + name rows.
        Vector4f header = new Vector4f(0.65f, 0.85f, 1f, 1f);
        Vector4f white = new Vector4f(1f, 1f, 1f, 1f);
        drawTextLeft("Players (" + names.size() + ")", x0 + 0.02f, y1 - 0.045f,
                size * 0.9f, header, aspectRatio);
        for (int i = 0; i < names.size(); i++) {
            drawTextLeft(names.get(i), x0 + 0.03f, y1 - 0.09f - i * rowStep,
                    size, white, aspectRatio);
        }
        org.lwjgl.opengl.GL11.glEnable(org.lwjgl.opengl.GL11.GL_DEPTH_TEST);
        lineShader.unbind();
    }

    /**
     * Draws the JEI-style recipe book: a searchable index of every crafting
     * and furnace recipe. Left side mirrors the creative catalog's grid;
     * selecting an entry opens a detail card on the right with its
     * ingredients layout and the station that crafts it.
     */
    public void renderRecipeBook(RecipeBookGui book, TextureAtlas atlas, ItemTextures itemTextures,
                                 ToolDurability durability, float aspectRatio, float cursorLx, float cursorLy) {
        glDisable(GL_DEPTH_TEST);
        glDisable(GL_CULL_FACE);
        hudTransform.identity().scale(1f / aspectRatio, 1f, 1f);

        // Full-screen dim behind everything.
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

        List<RecipeBookGui.Entry> entries = book.entries();

        // Title + hint.
        drawCenteredText("Recipe Book", 0f, 0.90f, 0.034f, WHITE);
        drawCenteredText("Type to search - click an item to see how it's made",
                0f, 0.855f, 0.024f, new Vector4f(0.65f, 0.65f, 0.7f, 1f));

        // Search box (top center).
        float sbLeft = -0.28f, sbRight = 0.28f, sbTop = 0.80f, sbBot = 0.755f;
        slotBgVerts.clear();
        addQuad3(slotBgVerts, sbLeft, sbBot, sbRight, sbTop);
        lineShader.bind();
        lineShader.setUniform("projection", identity);
        lineShader.setUniform("view", identity);
        lineShader.setUniform("model", hudTransform);
        inventorySlotBg.upload(slotBgVerts.toArray());
        lineShader.setUniform("color", new Vector4f(0.10f, 0.10f, 0.12f, 0.92f));
        inventorySlotBg.render();
        lineShader.unbind();
        String q = book.query();
        drawTextAt(q.isEmpty() ? "Search recipes..." : q, sbLeft + 0.02f, sbBot + 0.008f, 0.026f,
                q.isEmpty() ? new Vector4f(0.55f, 0.55f, 0.55f, 1f) : WHITE);

        // Index grid (same geometry as the creative catalog).
        int itemCount = entries.size();
        float scroll = clampCatalogScroll(book.scroll(), itemCount);
        float half = CAT_SLOT / 2f - 0.005f;

        guiVerts.clear();
        guiInds.clear();
        for (int i = 0; i < itemCount; i++) {
            if (!catalogItemVisible(i, scroll)) continue;
            float[] c = catalogItemCenter(i, scroll);
            renderGuiSlot(c[0], c[1], half);
        }
        flushGuiQuads();

        beginSlotBatch();
        for (int i = 0; i < itemCount; i++) {
            if (!catalogItemVisible(i, scroll)) continue;
            float[] c = catalogItemCenter(i, scroll);
            addSlotIcon(c[0], c[1], half, entries.get(i).preview, itemTextures, atlas, durability);
        }
        flushBlockBatch(atlas);
        text.render(hudTransform, WHITE);

        // Bookmark stars on bookmarked visible entries (drawn as gold quads).
        lineShader.bind();
        lineShader.setUniform("projection", identity);
        lineShader.setUniform("view", identity);
        lineShader.setUniform("model", hudTransform);
        for (int i = 0; i < itemCount; i++) {
            if (!catalogItemVisible(i, scroll)) continue;
            if (!entries.get(i).bookmarked) continue;
            float[] c = catalogItemCenter(i, scroll);
            float sx = c[0] - half + 0.004f;
            float sy = c[1] + half - 0.018f;
            float s = 0.014f;
            float[] star = {
                    sx, sy - s * 2f, 0, sx + s, sy - s * 2f, 0, sx + s, sy - s, 0,
                    sx, sy - s * 2f, 0, sx + s, sy - s, 0, sx, sy - s, 0,
            };
            inventorySlotBg.upload(star);
            lineShader.setUniform("color", new Vector4f(1f, 0.82f, 0.25f, 1f));
            inventorySlotBg.render();
        }
        lineShader.unbind();

        renderCatalogScrollbar(itemCount, scroll);

        if (itemCount == 0) {
            drawCenteredText("No matching recipes", 0f, CAT_GRID_TOP_Y - 0.01f, 0.028f,
                    new Vector4f(0.7f, 0.7f, 0.7f, 1f));
        }

        // Hover highlight over the index grid.
        int hover = creativeItemAt(cursorLx, cursorLy, itemCount, scroll);
        if (hover >= 0) {
            float[] c = catalogItemCenter(hover, scroll);
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

        // Detail card (right side) for the selected entry.
        RecipeBookGui.Entry sel = book.selectedEntry();
        if (sel != null) {
            float iconHalf = CAT_SLOT / 2f;
            float step = iconHalf * 2f + 0.02f;
            int rows = sel.gridCols > 0
                    ? (sel.ingredients.size() + sel.gridCols - 1) / sel.gridCols : 1;
            float cardH = Math.max(0.72f, 0.46f + rows * step);
            float dx0 = catalogGridRightX() + 0.10f;
            float dx1 = Math.min(aspectRatio * 0.95f, dx0 + 0.85f);
            float dy1 = CAT_GRID_TOP_Y + 0.30f;
            float dy0 = dy1 - cardH;
            slotBgVerts.clear();
            addQuad3(slotBgVerts, dx0, dy0, dx1, dy1);
            lineShader.bind();
            lineShader.setUniform("projection", identity);
            lineShader.setUniform("view", identity);
            lineShader.setUniform("model", hudTransform);
            inventorySlotBg.upload(slotBgVerts.toArray());
            lineShader.setUniform("color", new Vector4f(0.08f, 0.08f, 0.11f, 0.94f));
            inventorySlotBg.render();
            lineShader.unbind();

            float pad = 0.05f;
            drawTextLeft(sel.title, dx0 + pad, dy1 - 0.06f, 0.030f, WHITE, aspectRatio);
            drawTextLeft("Made at: " + sel.station, dx0 + pad, dy1 - 0.115f, 0.026f,
                    new Vector4f(0.65f, 0.85f, 1f, 1f), aspectRatio);
            drawTextLeft(sel.bookmarked ? "* Bookmarked (B to remove)" : "Press B to bookmark",
                    dx1 - pad - 0.24f, dy1 - 0.06f, 0.022f,
                    sel.bookmarked ? new Vector4f(1f, 0.82f, 0.25f, 1f)
                            : new Vector4f(0.55f, 0.55f, 0.6f, 1f), aspectRatio);

            beginSlotBatch();
            // Big output preview top-right of the card.
            addSlotIcon(dx1 - pad - iconHalf * 1.4f, dy1 - 0.16f, iconHalf * 1.4f,
                    sel.preview, itemTextures, atlas, durability);

            // Ingredients laid out by the entry's preferred layout.
            float iy = dy1 - 0.30f;
            drawTextLeft(sel.yieldText.startsWith("Smelt") || sel.yieldText.startsWith("Melt")
                    ? "Input:" : "Ingredients:", dx0 + pad, iy, 0.024f, WHITE, aspectRatio);
            for (int i = 0; i < sel.ingredients.size(); i++) {
                ItemStack ing = sel.ingredients.get(i);
                if (ing.isEmpty()) continue;
                float cx = dx0 + pad + iconHalf + (sel.gridCols > 0 ? (i % sel.gridCols) * step : i * step);
                float cy = iy - 0.09f - (sel.gridCols > 0 ? (i / sel.gridCols) * step : 0f);
                addSlotIcon(cx, cy, iconHalf, ing, itemTextures, atlas, durability);
            }
            flushBlockBatch(atlas);
            text.render(hudTransform, WHITE);

            // Yield line below the last ingredient row.
            float lastRow = sel.gridCols > 0 ? (sel.ingredients.size() - 1) / sel.gridCols : 0;
            drawTextLeft(sel.yieldText, dx0 + pad,
                    iy - 0.10f - lastRow * step - 0.03f, 0.026f,
                    new Vector4f(0.7f, 0.9f, 0.5f, 1f), aspectRatio);
        }

        glEnable(GL_DEPTH_TEST);
        glEnable(GL_CULL_FACE);
    }

    /**
     * Index into the recipe book's visible grid under the given cursor, or -1
     * (mirrors {@link #creativeItemAt}).
     */
    public int recipeBookItemAt(float logicalX, float logicalY, RecipeBookGui book) {
        int itemCount = book.entries().size();
        float scroll = clampCatalogScroll(book.scroll(), itemCount);
        return creativeItemAt(logicalX, logicalY, itemCount, scroll);
    }

    /**
     * Draws the furnace's burn flame and smelting progress arrow (both driven
     * by the furnace state). Binds {@link #lineShader} itself rather than
     * assuming it's already active - the textured-GUI panel path (see
     * {@link #flushGuiQuads}) explicitly unbinds whatever shader it used
     * right before this runs, so without this the color/quad uniform calls
     * below landed on no program at all and the flame/arrow silently stopped
     * drawing.
     */
    private void renderMachineProgress(com.minecraftclone.world.ProgressMachine machine) {
        renderFurnaceProgress(machine);
    }

    private void renderFurnaceProgress(com.minecraftclone.world.ProgressMachine machine) {
        lineShader.bind();
        lineShader.setUniform("projection", identity);
        lineShader.setUniform("view", identity);
        lineShader.setUniform("model", hudTransform);

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
        float flameHeight = (flameTop - flameBottom) * Math.min(1f, Math.max(0f, machine.burnFraction()));
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
        float fill = (FURNACE_ARROW_X1 - FURNACE_ARROW_X0) * Math.min(1f, Math.max(0f, machine.progressFraction()));
        if (fill > 0f) {
            addQuad3(furnaceDeco, FURNACE_ARROW_X0, FURNACE_MID_Y - arrowHalf, FURNACE_ARROW_X0 + fill, FURNACE_MID_Y + arrowHalf);
            inventoryPanel.upload(furnaceDeco.toArray());
            lineShader.setUniform("color", new Vector4f(0.95f, 0.95f, 0.95f, 1f));
            inventoryPanel.render();
        }

        lineShader.unbind();
    }

    /**
     * Draws the smeltery's heat flame and melting progress arrow. Same layout
     * as the furnace's, but the flame shows whether lava is heating the
     * structure (bright orange = hot, dark grey = cold/paused) and the arrow
     * fills with the melt progress of the input slot.
     */
    private void renderSmelteryProgress(com.minecraftclone.world.multiblock.SmelteryEntity smeltery) {
        lineShader.bind();
        lineShader.setUniform("projection", identity);
        lineShader.setUniform("view", identity);
        lineShader.setUniform("model", hudTransform);

        // Flame track behind the flame itself.
        furnaceDeco.clear();
        float flameHalf = 0.0225f;
        float flameTop = FURNACE_MID_Y + 0.045f;
        float flameBottom = FURNACE_MID_Y - 0.045f;
        addQuad3(furnaceDeco, FURNACE_FLAME_X - flameHalf, flameBottom, FURNACE_FLAME_X + flameHalf, flameTop);
        inventoryPanel.upload(furnaceDeco.toArray());
        lineShader.setUniform("color", new Vector4f(0.15f, 0.15f, 0.15f, 0.9f));
        inventoryPanel.render();

        // Flame fill: full-height while lava heats the structure.
        if (smeltery.isHot()) {
            furnaceDeco.clear();
            addQuad3(furnaceDeco, FURNACE_FLAME_X - flameHalf, flameBottom, FURNACE_FLAME_X + flameHalf, flameTop);
            inventoryPanel.upload(furnaceDeco.toArray());
            lineShader.setUniform("color", new Vector4f(0.98f, 0.55f, 0.12f, 1f));
            inventoryPanel.render();
        }

        // Arrow track from the input column toward the output slot.
        float arrowHalf = 0.0225f;
        furnaceDeco.clear();
        addQuad3(furnaceDeco, FURNACE_ARROW_X0, FURNACE_MID_Y - arrowHalf, FURNACE_ARROW_X1, FURNACE_MID_Y + arrowHalf);
        inventoryPanel.upload(furnaceDeco.toArray());
        lineShader.setUniform("color", new Vector4f(0.3f, 0.3f, 0.3f, 0.9f));
        inventoryPanel.render();

        // Arrow fill grows left to right with melt progress.
        furnaceDeco.clear();
        float fill = (FURNACE_ARROW_X1 - FURNACE_ARROW_X0) * Math.min(1f, Math.max(0f, smeltery.progressFraction()));
        if (fill > 0f) {
            addQuad3(furnaceDeco, FURNACE_ARROW_X0, FURNACE_MID_Y - arrowHalf, FURNACE_ARROW_X0 + fill, FURNACE_MID_Y + arrowHalf);
            inventoryPanel.upload(furnaceDeco.toArray());
            lineShader.setUniform("color", new Vector4f(0.95f, 0.95f, 0.95f, 1f));
            inventoryPanel.render();
        }

        lineShader.unbind();
    }

    /**
     * Draws Part Builder decorations: a gold selection border around the active
     * shape button, an arrow from the shape grid to the output slot (never
     * through the buttons), and Material / Output labels that sit above those
     * slots — well clear of the shape grid and the inventory below.
     */
    private void renderPartBuilderDecorations(ContainerGui gui) {
        com.minecraftclone.world.tinkers.PartBuilderGui pb = gui.partBuilderGui();
        if (pb == null) return;

        lineShader.bind();
        lineShader.setUniform("projection", identity);
        lineShader.setUniform("view", identity);
        lineShader.setUniform("model", hudTransform);

        // Gold border around selected shape button.
        com.minecraftclone.world.tinkers.ToolPartType sel = pb.selectedShape();
        if (sel != null) {
            com.minecraftclone.world.tinkers.ToolPartType[] shapes =
                    com.minecraftclone.world.tinkers.ToolPartType.values();
            for (int i = 0; i < shapes.length; i++) {
                if (shapes[i] == sel) {
                    float sx = pbShapeX(i);
                    float sy = pbShapeY(i);
                    float h = INV_SLOT / 2f + 0.005f;
                    inventoryHover.upload(outlineLines(sx, sy, h));
                    lineShader.setUniform("color", new Vector4f(1.0f, 0.82f, 0.1f, 1f));
                    glLineWidth(2.5f);
                    inventoryHover.render();
                    break;
                }
            }
        }

        // Arrow from the last shape column → output. Stops short of the grid
        // so it never paints over the buttons.
        float arrowHalf = 0.016f;
        float lastShapeX = pbShapeX(PB_SHAPE_COLS - 1);
        float arrowX0 = lastShapeX + INV_SLOT / 2f + 0.02f;
        float arrowX1 = pbOutX() - INV_SLOT / 2f - 0.02f;
        if (arrowX1 > arrowX0) {
            furnaceDeco.clear();
            addQuad3(furnaceDeco, arrowX0, pbOutY() - arrowHalf, arrowX1, pbOutY() + arrowHalf);
            inventoryPanel.upload(furnaceDeco.toArray());
            lineShader.setUniform("color", new Vector4f(0.7f, 0.7f, 0.7f, 0.6f));
            inventoryPanel.render();
        }

        lineShader.unbind();

        // Short labels above the material and output slots only — shape names
        // live in the hover tooltip so they don't collide with neighbouring buttons.
        float labelSize = 0.020f;
        float labelY = pbShapeTopY() + INV_SLOT / 2f + 0.012f;
        text.begin();
        String mat = "Material";
        String out = "Output";
        text.add(mat, pbMatX() - text.measure(mat, labelSize) / 2f, labelY, labelSize);
        text.add(out, pbOutX() - text.measure(out, labelSize) / 2f, labelY, labelSize);
        text.render(hudTransform, new Vector4f(0.9f, 0.9f, 0.9f, 1f));
    }

    /**
     * Draws the workbench arrow from the last column of the 3x3/5x5 to the result
     * slot. Binds {@link #lineShader} itself — the textured panel path unbinds
     * whatever shader it used right before decorations run.
     */
    private void renderCraftingTableDecorations(ContainerGui gui) {
        int n = gui.gridWidth();
        float lastColX = tableCraftLeftX() + (n - 1) * INV_STEP;
        float outX = tableCraftOutputX(n);
        float midY = tableCraftOutputY(n);
        float arrowHalf = 0.016f;
        float arrowX0 = lastColX + INV_SLOT / 2f + 0.02f;
        float arrowX1 = outX - INV_SLOT / 2f - 0.02f;
        if (arrowX1 <= arrowX0) return;

        lineShader.bind();
        lineShader.setUniform("projection", identity);
        lineShader.setUniform("view", identity);
        lineShader.setUniform("model", hudTransform);
        furnaceDeco.clear();
        addQuad3(furnaceDeco, arrowX0, midY - arrowHalf, arrowX1, midY + arrowHalf);
        inventoryPanel.upload(furnaceDeco.toArray());
        lineShader.setUniform("color", new Vector4f(0.7f, 0.7f, 0.7f, 0.6f));
        inventoryPanel.render();
        lineShader.unbind();
    }

    /**
     * Draws Tool Station decorations: an arrow from the last extra slot to the
     * output, a ready highlight, and centred role labels above each slot.
     */
    private void renderToolStationDecorations(ContainerGui gui) {
        com.minecraftclone.world.tinkers.ToolStationGui ts = gui.toolStationGui();
        if (ts == null) return;

        lineShader.bind();
        lineShader.setUniform("projection", identity);
        lineShader.setUniform("view", identity);
        lineShader.setUniform("model", hudTransform);

        // Arrow from last input slot → output slot.
        float arrowHalf = 0.016f;
        int last = com.minecraftclone.world.tinkers.ToolStationGui.INPUT_SLOTS - 1;
        float arrowX0 = tsSlotX(last) + INV_SLOT / 2f + 0.04f;
        float arrowX1 = tsOutX() - INV_SLOT / 2f - 0.04f;
        furnaceDeco.clear();
        addQuad3(furnaceDeco, arrowX0, tsSlotY() - arrowHalf, arrowX1, tsSlotY() + arrowHalf);
        inventoryPanel.upload(furnaceDeco.toArray());
        lineShader.setUniform("color", new Vector4f(0.7f, 0.7f, 0.7f, 0.6f));
        inventoryPanel.render();

        // "Ready!" highlight on output when assembly is possible.
        if (ts.canAssemble()) {
            float h = INV_SLOT / 2f + 0.005f;
            inventoryHover.upload(outlineLines(tsOutX(), tsOutY(), h));
            lineShader.setUniform("color", new Vector4f(0.2f, 1f, 0.2f, 1f));
            glLineWidth(2.5f);
            inventoryHover.render();
        }

        lineShader.unbind();

        // Slot-role labels centred above each slot so they don't collide.
        text.begin();
        float labelY = tsSlotY() + INV_SLOT / 2f + 0.012f;
        float labelSize = 0.018f;
        int n = com.minecraftclone.world.tinkers.ToolStationGui.INPUT_SLOTS;
        for (int i = 0; i < n; i++) {
            String role = tsRoleLabel(i);
            float w = text.measure(role, labelSize);
            text.add(role, tsSlotX(i) - w / 2f, labelY, labelSize);
        }
        float outW = text.measure("Output", labelSize);
        text.add("Output", tsOutX() - outW / 2f, labelY, labelSize);
        text.render(hudTransform, new Vector4f(0.9f, 0.9f, 0.9f, 1f));
    }

    /** Draws the cursor stack (icon + count) at the given logical position, above everything else. */
    private void drawCursorStack(TextureAtlas atlas, ItemTextures itemTextures, ItemStack stack, float cx, float cy) {
        if (stack == null || stack.isEmpty()) return;
        BlockType type = stack.type();
        int count = stack.count();
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
            if (stack.isTinkers()) {
                itemTextures.bindTinkersItem(stack.tinkersItem());
            } else {
                itemTextures.bind(type);
            }
            hotbarItemIcon.render();
            hudShader.unbind();
        } else {
            blockVertices.clear();
            blockIndices.clear();
            blockVertexCounter = 0;
            addIsometricBlock(cx, cy, half, type, atlas);
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

    /** The tooltip lines for an item: its display name, plus durability for tools and armor. */
    private String[] tooltipLines(BlockType type, ToolDurability durability) {
        return tooltipLines(type == null ? ItemStack.EMPTY : ItemStack.of(type, 1), durability);
    }

    /** Tooltip for a full stack, including Tinkers' part/tool payload. */
    private String[] tooltipLines(ItemStack stack, ToolDurability durability) {
        if (stack == null || stack.isEmpty()) return new String[]{""};
        com.minecraftclone.world.tinkers.TinkersItem.Part part = stack.tinkersPart();
        if (part != null) {
            return new String[]{"Tinkers Part",
                    part.shape.name().replace('_', ' ').toLowerCase(java.util.Locale.ROOT)
                            + " / " + part.material.displayName()};
        }
        com.minecraftclone.world.tinkers.TinkersItem.Tool tool = stack.tinkersTool();
        if (tool != null) {
            return new String[]{"Tinkers Tool",
                    tool.kind.name().toLowerCase(java.util.Locale.ROOT),
                    "Durability: " + tool.remaining() + "/" + tool.maxDurability};
        }
        BlockType type = stack.type();
        if (type == null) return new String[]{""};
        if (type.isTinkersToolPart()) return new String[]{"Tinkers Part"};
        if (type.isTinkersTool()) return new String[]{"Tinkers Tool"};
        if (Mining.isTool(type) || Armor.isArmor(type)) {
            int maxUses;
            if (Armor.isArmor(type)) {
                maxUses = Armor.maxUses(type);
            } else {
                Mining.ToolStats stats = Mining.toolStats(type);
                maxUses = stats != null ? stats.maxUses() : 0;
            }
            int rem = durability.remaining(type);
            return new String[]{type.displayName(), "Durability: " + rem + " / " + maxUses};
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

    private void renderCreativeSearchBox(String query, boolean focused) {
        float left = searchBoxLeft();
        float right = searchBoxRight();
        float top = SEARCH_CENTER_Y + SEARCH_H / 2f;
        float bot = SEARCH_CENTER_Y - SEARCH_H / 2f;
        slotBgVerts.clear();
        addQuad3(slotBgVerts, left, bot, right, top);
        lineShader.bind();
        lineShader.setUniform("projection", identity);
        lineShader.setUniform("view", identity);
        lineShader.setUniform("model", hudTransform);
        inventorySlotBg.upload(slotBgVerts.toArray());
        lineShader.setUniform("color", focused
                ? new Vector4f(0.18f, 0.18f, 0.22f, 0.95f)
                : new Vector4f(0.10f, 0.10f, 0.12f, 0.90f));
        inventorySlotBg.render();
        lineShader.unbind();

        float[] border = {
                left, bot, 0, right, bot, 0,
                right, bot, 0, right, top, 0,
                right, top, 0, left, top, 0,
                left, top, 0, left, bot, 0,
        };
        inventoryHover.upload(border);
        lineShader.bind();
        lineShader.setUniform("projection", identity);
        lineShader.setUniform("view", identity);
        lineShader.setUniform("model", hudTransform);
        lineShader.setUniform("color", focused
                ? new Vector4f(0.95f, 0.95f, 0.95f, 0.95f)
                : new Vector4f(0.55f, 0.55f, 0.55f, 0.85f));
        glLineWidth(1.5f);
        inventoryHover.render();
        lineShader.unbind();

        float textSize = 0.028f;
        float textX = left + 0.02f;
        float textY = SEARCH_CENTER_Y - 0.014f;
        boolean empty = query.isEmpty();
        String shown = empty ? (focused ? "" : "Search items...") : query;
        drawTextAt(shown, textX, textY, textSize, empty
                ? new Vector4f(0.55f, 0.55f, 0.55f, 1f)
                : WHITE);

        if (!empty) {
            drawCenteredText("x", searchClearX(), SEARCH_CENTER_Y - 0.014f, 0.030f,
                    new Vector4f(0.85f, 0.85f, 0.85f, 1f));
        }

        if (focused && (System.currentTimeMillis() / 400) % 2 == 0) {
            float caretX = textX + text.measure(query, textSize) + 0.004f;
            slotBgVerts.clear();
            addQuad3(slotBgVerts, caretX, SEARCH_CENTER_Y - 0.018f, caretX + 0.006f, SEARCH_CENTER_Y + 0.020f);
            lineShader.bind();
            lineShader.setUniform("projection", identity);
            lineShader.setUniform("view", identity);
            lineShader.setUniform("model", hudTransform);
            inventorySlotBg.upload(slotBgVerts.toArray());
            lineShader.setUniform("color", WHITE);
            inventorySlotBg.render();
            lineShader.unbind();
        }
    }

    private void renderCatalogScrollbar(int itemCount, float scrollRows) {
        float max = catalogMaxScroll(itemCount);
        if (max <= 0f) return;
        float cx = catalogScrollbarX();
        float halfW = CAT_SCROLLBAR_W / 2f;
        float top = catalogScrollbarTopY();
        float bot = catalogScrollbarBottomY();
        float trackH = top - bot;
        float thumbH = Math.max(0.055f, catalogVisibleRows() / (float) Math.max(catalogRowCount(itemCount), 1) * trackH);
        if (thumbH > trackH) thumbH = trackH;
        float t = scrollRows / max;
        float thumbTop = top - t * (trackH - thumbH);
        float thumbBot = thumbTop - thumbH;

        slotBgVerts.clear();
        addQuad3(slotBgVerts, cx - halfW, bot, cx + halfW, top);
        lineShader.bind();
        lineShader.setUniform("projection", identity);
        lineShader.setUniform("view", identity);
        lineShader.setUniform("model", hudTransform);
        inventorySlotBg.upload(slotBgVerts.toArray());
        lineShader.setUniform("color", new Vector4f(0.12f, 0.12f, 0.12f, 0.85f));
        inventorySlotBg.render();
        lineShader.unbind();

        slotBgVerts.clear();
        addQuad3(slotBgVerts, cx - halfW + 0.004f, thumbBot, cx + halfW - 0.004f, thumbTop);
        lineShader.bind();
        lineShader.setUniform("projection", identity);
        lineShader.setUniform("view", identity);
        lineShader.setUniform("model", hudTransform);
        inventorySlotBg.upload(slotBgVerts.toArray());
        lineShader.setUniform("color", new Vector4f(0.62f, 0.62f, 0.62f, 0.95f));
        inventorySlotBg.render();
        lineShader.unbind();
    }

    /**
     * Draws the creative-mode inventory screen: a tabbed item catalog on top of
     * the normal 9-slot hotbar plus a "destroy item" slot, with the cursor stack
     * following the mouse. {@code selectedTab} is the active category,
     * {@code catalogScroll} is how many rows the catalog has been scrolled,
     * {@code searchQuery} filters the grid (blank = current tab; otherwise all
     * items matching the query), and {@code searchFocused} draws the caret.
     */
    public void renderCreative(Inventory inventory, InventoryController controller, int selectedTab, int selectedSlot,
                               TextureAtlas atlas, ItemTextures itemTextures, ToolDurability durability,
                               float aspectRatio, float cursorLx, float cursorLy, float catalogScroll,
                               String searchQuery, boolean searchFocused) {
        glDisable(GL_DEPTH_TEST);
        glDisable(GL_CULL_FACE);
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

        if (searchQuery == null) searchQuery = "";
        renderCreativeSearchBox(searchQuery, searchFocused);

        // Textured slot cells for the catalog grid and the hotbar + destroy slot.
        float centerY = slotCenterY();
        float dx = destroySlotX();
        BlockType[] items = CreativeCatalog.itemsFor(selectedTab, searchQuery);
        catalogScroll = clampCatalogScroll(catalogScroll, items.length);
        if (guiTextures != null) {
            guiVerts.clear();
            guiInds.clear();
            float half = CAT_SLOT / 2f - 0.005f;
            for (int i = 0; i < items.length; i++) {
                if (!catalogItemVisible(i, catalogScroll)) continue;
                float[] c = catalogItemCenter(i, catalogScroll);
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
            float half = CAT_SLOT / 2f - 0.005f;
            slotBgVerts.clear();
            for (int i = 0; i < items.length; i++) {
                if (!catalogItemVisible(i, catalogScroll)) continue;
                float[] c = catalogItemCenter(i, catalogScroll);
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

        // Catalog item icons (only the rows currently in the viewport).
        float half = CAT_SLOT / 2f - 0.005f;
        beginSlotBatch();
        for (int i = 0; i < items.length; i++) {
            if (!catalogItemVisible(i, catalogScroll)) continue;
            float[] c = catalogItemCenter(i, catalogScroll);
            addSlotIcon(c[0], c[1], half, ItemStack.of(items[i], 1), itemTextures, atlas, durability);
        }
        flushBlockBatch(atlas);
        text.render(hudTransform, WHITE);

        renderCatalogScrollbar(items.length, catalogScroll);

        if (items.length == 0) {
            drawCenteredText("No matching items", 0f, CAT_GRID_TOP_Y - 0.01f, 0.028f,
                    new Vector4f(0.7f, 0.7f, 0.7f, 1f));
        }

        // Catalog item hover highlight.
        int hoverItem = creativeItemAt(cursorLx, cursorLy, items.length, catalogScroll);
        if (hoverItem >= 0) {
            float[] c = catalogItemCenter(hoverItem, catalogScroll);
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
                    inventory.stackOf(i), itemTextures, atlas, durability);
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
            drawCursorStack(atlas, itemTextures, controller.cursor(), cursorLx + 0.02f, cursorLy - 0.02f);
        }

        // Tooltip for the hovered catalog item or hotbar slot.
        BlockType tip = null;
        ItemStack tipStack = null;
        int hb = hotbarSlotAt(cursorLx, cursorLy);
        int ci = creativeItemAt(cursorLx, cursorLy, items.length, catalogScroll);
        if (hb >= 0) {
            tipStack = inventory.stackOf(hb);
        } else if (ci >= 0) {
            tip = items[ci];
        }
        if (tipStack != null && !tipStack.isEmpty()) {
            renderTooltip(tooltipLines(tipStack, durability), cursorLx, cursorLy, aspectRatio);
        } else if (tip != null) {
            renderTooltip(tooltipLines(tip, durability), cursorLx, cursorLy, aspectRatio);
        }

        drawCenteredText("Creative    Click search    Click: add    Shift-click: hotbar    Scroll: more    X: delete",
                0f, centerY - HOTBAR_SLOT_SIZE / 2f - 0.05f, 0.020f, new Vector4f(0.7f, 0.7f, 0.7f, 1f));

        glEnable(GL_CULL_FACE);
        glEnable(GL_DEPTH_TEST);
    }

    /**
     * Renders a mini-map image using the given layout.
     * Caches the texture and mesh to avoid recreating them every frame.
     * Pass {@code null} image to clear the cache (e.g., when changing worlds).
     * {@code imageVersion} comes from {@link com.minecraftclone.engine.MapRenderer#getMiniMapVersion()}
     * and increments each time the renderer redraws into its cached image; Hud uses it
     * to detect pixel changes that don't change the image reference.
     * {@code editing} draws resize handles and a move hint (hold Alt).
     */
    public void renderMiniMap(java.awt.image.BufferedImage miniMapImage, int imageVersion,
                              float aspectRatio, MiniMapLayout layout, boolean editing) {
        if (miniMapImage == null) {
            // Drop the cached texture only. Destroying miniMapMesh here used to
            // crash the next world: Save and Quit cleared the map, then Create
            // New World uploaded into a deleted VAO.
            if (cachedMiniMapTextureId >= 0) {
                glDeleteTextures(cachedMiniMapTextureId);
                cachedMiniMapTextureId = -1;
            }
            cachedMiniMapImage = null;
            cachedMiniMapVersion = -1;
            return;
        }
        if (layout == null) {
            layout = miniMapLayout(aspectRatio, MINI_MAP_SIZE_Y, Float.NaN, Float.NaN);
        }

        float sizeX = layout.sizeX();
        float sizeY = layout.sizeY();
        float offsetX = layout.cx();
        float offsetY = layout.cy();

        // Check if image pixels or layout has changed
        boolean imageChanged = cachedMiniMapImage != miniMapImage || cachedMiniMapVersion != imageVersion;
        boolean layoutChanged = cachedMiniMapSizeX != sizeX || cachedMiniMapSizeY != sizeY
                             || cachedMiniMapOffsetX != offsetX || cachedMiniMapOffsetY != offsetY;

        // Re-upload texture whenever the renderer has redrawn
        if (imageChanged) {
            if (cachedMiniMapTextureId >= 0) {
                glDeleteTextures(cachedMiniMapTextureId);
            }
            cachedMiniMapTextureId = GLTexture.upload(miniMapImage);
            cachedMiniMapImage = miniMapImage;
            cachedMiniMapVersion = imageVersion;
        }

        // Rebuild mesh if image or layout has changed
        if (imageChanged || layoutChanged) {
            cachedMiniMapSizeX = sizeX;
            cachedMiniMapSizeY = sizeY;
            cachedMiniMapOffsetX = offsetX;
            cachedMiniMapOffsetY = offsetY;

            float minX = layout.minX();
            float maxX = layout.maxX();
            float minY = layout.minY();
            float maxY = layout.maxY();

            float[] verts = {
                minX, minY, 0f, 1f,  // bottom-left: v=1
                maxX, minY, 1f, 1f,  // bottom-right: v=1
                maxX, maxY, 1f, 0f,  // top-right: v=0
                minX, maxY, 0f, 0f,  // top-left: v=0
            };
            int[] inds = {0, 1, 2, 0, 2, 3};
            miniMapMesh.upload(verts, inds);
        }

        glDisable(GL_DEPTH_TEST);
        hudShader.bind();
        hudShader.setUniform("transform", new Matrix4f().identity().scale(1f / aspectRatio, 1f, 1f));
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, cachedMiniMapTextureId);
        hudShader.setUniform("atlas", 0);
        hudShader.setUniform("color", new Vector4f(1f, 1f, 1f, 1f));
        miniMapMesh.render();
        hudShader.unbind();
        glBindTexture(GL_TEXTURE_2D, 0);

        if (editing) {
            hudTransform.identity().scale(1f / aspectRatio, 1f, 1f);
            inventoryHover.upload(outlineRect(layout.minX(), layout.minY(), layout.maxX(), layout.maxY()));
            lineShader.bind();
            lineShader.setUniform("projection", identity);
            lineShader.setUniform("view", identity);
            lineShader.setUniform("model", hudTransform);
            lineShader.setUniform("color", new Vector4f(1f, 1f, 1f, 0.95f));
            glLineWidth(2f);
            inventoryHover.render();
            lineShader.unbind();

            float hsY = 0.018f;
            float hsX = hsY * aspectRatio;
            float[] quads = handleQuad(layout.minX(), layout.maxY(), hsX, hsY);
            float[] tr = handleQuad(layout.maxX(), layout.maxY(), hsX, hsY);
            float[] br = handleQuad(layout.maxX(), layout.minY(), hsX, hsY);
            float[] bl = handleQuad(layout.minX(), layout.minY(), hsX, hsY);
            float[] all = new float[quads.length * 4];
            System.arraycopy(quads, 0, all, 0, quads.length);
            System.arraycopy(tr, 0, all, quads.length, tr.length);
            System.arraycopy(br, 0, all, quads.length * 2, br.length);
            System.arraycopy(bl, 0, all, quads.length * 3, bl.length);
            inventoryPanel.upload(all);
            lineShader.bind();
            lineShader.setUniform("projection", identity);
            lineShader.setUniform("view", identity);
            lineShader.setUniform("model", hudTransform);
            lineShader.setUniform("color", new Vector4f(1f, 1f, 1f, 0.95f));
            inventoryPanel.render();
            lineShader.unbind();

            drawCenteredText("Drag to move  ·  Corner to resize  ·  Scroll to scale  ·  R to reset",
                    offsetX, layout.minY() - 0.045f, 0.022f, new Vector4f(1f, 1f, 1f, 0.95f));
        }
        glEnable(GL_DEPTH_TEST);
    }

    /** Convenience overload: default top-right layout, no edit chrome. */
    public void renderMiniMap(java.awt.image.BufferedImage miniMapImage, int imageVersion, float aspectRatio) {
        renderMiniMap(miniMapImage, imageVersion, aspectRatio,
                miniMapLayout(aspectRatio, MINI_MAP_SIZE_Y, Float.NaN, Float.NaN), false);
    }

    private static float[] handleQuad(float cx, float cy, float halfX, float halfY) {
        return new float[]{
                cx - halfX, cy - halfY, 0, cx + halfX, cy - halfY, 0, cx + halfX, cy + halfY, 0,
                cx - halfX, cy - halfY, 0, cx + halfX, cy + halfY, 0, cx - halfX, cy + halfY, 0,
        };
    }

    /**
     * Renders the full-screen map image, filling the entire viewport.
     * Pass {@code null} to clear the cached texture (e.g. when closing the map).
     * {@code imageVersion} comes from {@link com.minecraftclone.engine.MapRenderer#getFullMapVersion()}
     * and increments each time the renderer redraws into its cached image.
     */
    public void renderFullMap(java.awt.image.BufferedImage mapImage, int imageVersion) {
        if (mapImage == null) {
            if (cachedFullMapTextureId >= 0) {
                glDeleteTextures(cachedFullMapTextureId);
                cachedFullMapTextureId = -1;
            }
            cachedFullMapImage = null;
            cachedFullMapVersion = -1;
            return;
        }

        if (cachedFullMapImage != mapImage || cachedFullMapVersion != imageVersion) {
            if (cachedFullMapTextureId >= 0) {
                glDeleteTextures(cachedFullMapTextureId);
            }
            cachedFullMapTextureId = GLTexture.upload(mapImage);
            cachedFullMapImage = mapImage;
            cachedFullMapVersion = imageVersion;

            // Full-screen quad: NDC −1..+1 in both axes, UV 0..1 with v flipped
            float[] verts = {
                -1f, -1f, 0f, 1f,  // bottom-left: v=1
                 1f, -1f, 1f, 1f,  // bottom-right: v=1
                 1f,  1f, 1f, 0f,  // top-right: v=0
                -1f,  1f, 0f, 0f,  // top-left: v=0
            };
            int[] inds = {0, 1, 2, 0, 2, 3};
            fullMapMesh.upload(verts, inds);
        }

        glDisable(GL_DEPTH_TEST);
        hudShader.bind();
        hudShader.setUniform("transform", new Matrix4f().identity());
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, cachedFullMapTextureId);
        hudShader.setUniform("atlas", 0);
        hudShader.setUniform("color", new Vector4f(1f, 1f, 1f, 1f));
        fullMapMesh.render();
        hudShader.unbind();
        glBindTexture(GL_TEXTURE_2D, 0);
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
        frostOverlay.destroy();
        if (cachedMiniMapTextureId >= 0) {
            glDeleteTextures(cachedMiniMapTextureId);
            cachedMiniMapTextureId = -1;
        }
        miniMapMesh.destroy();
        if (cachedFullMapTextureId >= 0) {
            glDeleteTextures(cachedFullMapTextureId);
            cachedFullMapTextureId = -1;
        }
        fullMapMesh.destroy();
    }
}
