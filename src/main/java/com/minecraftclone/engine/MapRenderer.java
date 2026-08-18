package com.minecraftclone.engine;

import com.minecraftclone.world.BlockType;
import com.minecraftclone.world.MapData;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.List;

/**
 * Renders the map as a classic grid showing explored chunks and discovered ore veins.
 * Supports both mini-map (corner) and full-screen views.
 */
public class MapRenderer {

    private static final int CHUNK_PIXEL_SIZE = 8; // Each chunk = 8x8 pixels on the mini-map
    private static final int MINI_MAP_WIDTH  = 160;
    private static final int MINI_MAP_HEIGHT = 160;

    // Ore vein colors (RGB) – used for both dots on the map and the legend.
    private static final Map<BlockType, Integer> ORE_COLORS = new LinkedHashMap<>();
    private static final Map<BlockType, String>  ORE_NAMES  = new LinkedHashMap<>();

    static {
        // Early-game ores
        reg(BlockType.COPPER_ORE,      0xB87333, "Copper");
        reg(BlockType.TIN_ORE,         0xC0C0C0, "Tin");
        reg(BlockType.BAUXITE_ORE,     0xE8D0B0, "Bauxite");
        reg(BlockType.ZINC_ORE,        0xA8B8C8, "Zinc");
        reg(BlockType.LEAD_ORE,        0x6A6A7A, "Lead");
        reg(BlockType.SILVER_ORE,      0xE0E0E0, "Silver");

        // Mid-game ores
        reg(BlockType.NICKEL_ORE,      0xD4D4A0, "Nickel");
        reg(BlockType.COBALT_ORE,      0x2E5090, "Cobalt");
        reg(BlockType.TUNGSTEN_ORE,    0x4A4A5A, "Tungsten");
        reg(BlockType.MOLYBDENUM_ORE,  0x5A5A6A, "Molybdenum");
        reg(BlockType.PLATINUM_ORE,    0xE8E0D0, "Platinum");

        // Advanced ores
        reg(BlockType.CHROMIUM_ORE,    0xC8B8B8, "Chromium");
        reg(BlockType.MANGANESE_ORE,   0x6A5A5A, "Manganese");
        reg(BlockType.VANADIUM_ORE,    0x7A8A8A, "Vanadium");
        reg(BlockType.BERYLLIUM_ORE,   0xD0D0E8, "Beryllium");
        reg(BlockType.TITANIUM_ORE,    0xB8C8C8, "Titanium");

        // Late-game ores
        reg(BlockType.URANIUM_ORE,     0x9AC870, "Uranium");
        reg(BlockType.THORIUM_ORE,     0x7A7A8A, "Thorium");
        reg(BlockType.PLUTONIUM_ORE,   0x6A8A5A, "Plutonium");
        reg(BlockType.IRIDIUM_ORE,     0xE8E8F0, "Iridium");

        // Vanilla ores
        reg(BlockType.COAL_ORE,        0x3A3A3A, "Coal");
        reg(BlockType.IRON_ORE,        0xD4A574, "Iron");
        reg(BlockType.GOLD_ORE,        0xFFD700, "Gold");
        reg(BlockType.DIAMOND_ORE,     0x00FFFF, "Diamond");

        // Extended GTNH ore pack — iron chain
        reg(BlockType.MAGNETITE_ORE,          0x4A4A5A, "Magnetite");
        reg(BlockType.HEMATITE_ORE,           0x8B3A3A, "Hematite");
        reg(BlockType.BROWN_LIMONITE_ORE,     0xA0784A, "Brown Limonite");
        reg(BlockType.YELLOW_LIMONITE_ORE,    0xD4B44A, "Yellow Limonite");
        reg(BlockType.BANDED_IRON_ORE,        0x8A6A5A, "Banded Iron");
        reg(BlockType.VANADIUM_MAGNETITE_ORE, 0x6A6A8A, "Vanadium Magnetite");
        // Copper chain
        reg(BlockType.CHALCOPYRITE_ORE,  0xC89A40, "Chalcopyrite");
        reg(BlockType.TETRAHEDRITE_ORE,  0x7A8A7A, "Tetrahedrite");
        reg(BlockType.MALACHITE_ORE,     0x2A8A4A, "Malachite");
        // Lead / Zinc
        reg(BlockType.GALENA_ORE,        0x8A8AA0, "Galena");
        reg(BlockType.SPHALERITE_ORE,    0xB4A080, "Sphalerite");
        // Nickel / Cobalt
        reg(BlockType.GARNIERITE_ORE,    0x7AAA7A, "Garnierite");
        reg(BlockType.PENTLANDITE_ORE,   0x9A8A5A, "Pentlandite");
        reg(BlockType.COBALTITE_ORE,     0x4A5A9A, "Cobaltite");
        // Sulfur chain
        reg(BlockType.PYRITE_ORE,        0xC8B840, "Pyrite");
        reg(BlockType.ARSENOPYRITE_ORE,  0x8A9A7A, "Arsenopyrite");
        reg(BlockType.SULFUR_ORE,        0xE8E050, "Sulfur");
        reg(BlockType.CINNABAR_ORE,      0xD43A2A, "Cinnabar");
        // Tin
        reg(BlockType.CASSITERITE_ORE,   0xD0C8B8, "Cassiterite");
        // Tungsten family
        reg(BlockType.SCHEELITE_ORE,     0xE8D880, "Scheelite");
        reg(BlockType.WOLFRAMITE_ORE,    0x6A5A7A, "Wolframite");
        reg(BlockType.MOLYBDENITE_ORE,   0x7A7A8A, "Molybdenite");
        reg(BlockType.FERBERITE_ORE,     0x5A5A6A, "Ferberite");
        // Chromium / Titanium
        reg(BlockType.CHROMITE_ORE,      0x3A4A3A, "Chromite");
        reg(BlockType.ILMENITE_ORE,      0x6A5A6A, "Ilmenite");
        reg(BlockType.RUTILE_ORE,        0xC87A5A, "Rutile");
        // Uranium family
        reg(BlockType.URANINITE_ORE,     0x4A7A3A, "Uraninite");
        reg(BlockType.PITCHBLENDE_ORE,   0x5A6A3A, "Pitchblende");
        // Rare earth
        reg(BlockType.MONAZITE_ORE,      0xC8A870, "Monazite");
        reg(BlockType.BASTNASITE_ORE,    0xD49A60, "Bastnasite");
        // Vanadium / Manganese minerals
        reg(BlockType.VANADINITE_ORE,    0xD45A3A, "Vanadinite");
        reg(BlockType.PYROLUSITE_ORE,    0x6A6A7A, "Pyrolusite");
        // Carbon
        reg(BlockType.GRAPHITE_ORE,      0x3A3A4A, "Graphite");
        // Light metals
        reg(BlockType.LITHIUM_ORE,       0xD0D8E8, "Lithium");
        // GTNH exotics
        reg(BlockType.NAQUADAH_ORE,          0x2A3A2A, "Naquadah");
        reg(BlockType.NAQUADAH_ENRICHED_ORE, 0x4A6A4A, "Naquadah Enriched");
        reg(BlockType.TRINIUM_ORE,           0xC0D0D0, "Trinium");
        // Additional rare earths / PGM
        reg(BlockType.NEODYMIUM_ORE,     0x9A8AC8, "Neodymium");
        reg(BlockType.CERIUM_ORE,        0xE8D0A0, "Cerium");
        reg(BlockType.OSMIUM_ORE,        0x4A5A6A, "Osmium");
        reg(BlockType.PALLADIUM_ORE,     0xD0C0A8, "Palladium");
        // Rock/mineral ores
        reg(BlockType.CALCITE_ORE,       0xE8E4D8, "Calcite");
        reg(BlockType.OLIVINE_ORE,       0x6A9A5A, "Olivine");
        reg(BlockType.TALC_ORE,          0xD0D8B8, "Talc");
        reg(BlockType.BENTONITE_ORE,     0xC8C0A0, "Bentonite");
        // Lapis components
        reg(BlockType.SODALITE_ORE,      0x4A6AC8, "Sodalite");
        reg(BlockType.LAZURITE_ORE,      0x3A50B8, "Lazurite");
        // Evaporites
        reg(BlockType.SALT_ORE,          0xF0F0F0, "Salt");
        reg(BlockType.ROCK_SALT_ORE,     0xE8E0E0, "Rock Salt");
        reg(BlockType.SALTPETER_ORE,     0xE0E8E0, "Saltpeter");
        reg(BlockType.BORAX_ORE,         0xF0EEE8, "Borax");
        // Phosphates
        reg(BlockType.APATITE_ORE,       0x8AC8C8, "Apatite");
        reg(BlockType.PHOSPHATE_ORE,     0xA8D0A8, "Phosphate");
        reg(BlockType.PYROCHLORE_ORE,    0xB8A878, "Pyrochlore");
        // Gemstones
        reg(BlockType.LEPIDOLITE_ORE,    0xD8A8D8, "Lepidolite");
        reg(BlockType.RUBY_ORE,          0xD43060, "Ruby");
        reg(BlockType.SAPPHIRE_ORE,      0x3060D4, "Sapphire");
        reg(BlockType.GREEN_SAPPHIRE_ORE,0x3A9A5A, "Green Sapphire");
        reg(BlockType.PYROPE_ORE,        0xA83050, "Pyrope");
        reg(BlockType.SPESSARTINE_ORE,   0xD87030, "Spessartine");
    }

    private static void reg(BlockType type, int rgb, String name) {
        ORE_COLORS.put(type, rgb);
        ORE_NAMES.put(type, name);
    }

    private final MapData mapData;
    private float mapScale   = 1.0f;
    private float mapOffsetX = 0f;
    private float mapOffsetZ = 0f;

    public MapRenderer(MapData mapData) {
        this.mapData = mapData;
    }

    // ── Mini-map ─────────────────────────────────────────────────────────────

    /**
     * Render the mini-map (160×160) centred on the player.
     * Includes a direction arrow at the player marker, N/S/E/W compass labels,
     * and an outer border.
     *
     * @param playerWorldX player X in world space
     * @param playerWorldZ player Z in world space
     * @param playerYaw    camera yaw in degrees (−90 = North/−Z, 0 = East/+X)
     */
    public BufferedImage renderMiniMap(float playerWorldX, float playerWorldZ, float playerYaw) {
        BufferedImage img = new BufferedImage(MINI_MAP_WIDTH, MINI_MAP_HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        // Background
        g.setColor(new Color(20, 20, 20));
        g.fillRect(0, 0, MINI_MAP_WIDTH, MINI_MAP_HEIGHT);

        int[] playerChunk = MapData.getChunkCoords(playerWorldX, playerWorldZ);
        int playerChunkX = playerChunk[0];
        int playerChunkZ = playerChunk[1];

        int radius = 10;
        for (int cx = playerChunkX - radius; cx <= playerChunkX + radius; cx++) {
            for (int cz = playerChunkZ - radius; cz <= playerChunkZ + radius; cz++) {
                int screenX = MINI_MAP_WIDTH  / 2 + (cx - playerChunkX) * CHUNK_PIXEL_SIZE;
                int screenZ = MINI_MAP_HEIGHT / 2 + (cz - playerChunkZ) * CHUNK_PIXEL_SIZE;

                if (screenX + CHUNK_PIXEL_SIZE < 0 || screenX >= MINI_MAP_WIDTH)  continue;
                if (screenZ + CHUNK_PIXEL_SIZE < 0 || screenZ >= MINI_MAP_HEIGHT) continue;

                if (mapData.isChunkExplored(cx, cz)) {
                    g.setColor(new Color(90, 90, 90));
                    g.fillRect(screenX, screenZ, CHUNK_PIXEL_SIZE, CHUNK_PIXEL_SIZE);
                    for (MapData.OreVeinRecord vein : mapData.getVeinsInChunk(cx, cz)) {
                        int oreScreenX = screenX + ((vein.worldX & 0xF) * CHUNK_PIXEL_SIZE) / 16;
                        int oreScreenZ = screenZ + ((vein.worldZ & 0xF) * CHUNK_PIXEL_SIZE) / 16;
                        int color = ORE_COLORS.getOrDefault(vein.oreType, 0xFFFFFF);
                        g.setColor(new Color(color));
                        g.fillRect(oreScreenX, oreScreenZ, 2, 2);
                    }
                } else {
                    g.setColor(new Color(35, 35, 35));
                    g.fillRect(screenX, screenZ, CHUNK_PIXEL_SIZE, CHUNK_PIXEL_SIZE);
                }
                // Chunk grid
                g.setColor(new Color(50, 50, 50));
                g.drawRect(screenX, screenZ, CHUNK_PIXEL_SIZE, CHUNK_PIXEL_SIZE);
            }
        }

        int centerX = MINI_MAP_WIDTH  / 2;
        int centerZ = MINI_MAP_HEIGHT / 2;

        // Compass labels (N / S / E / W)
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 9));
        FontMetrics fm = g.getFontMetrics();
        g.setColor(new Color(220, 220, 220));
        drawCentred(g, fm, "N", centerX, 10);
        drawCentred(g, fm, "S", centerX, MINI_MAP_HEIGHT - 2);
        drawCentred(g, fm, "E", MINI_MAP_WIDTH - 5, centerZ + fm.getAscent() / 2);
        drawCentred(g, fm, "W", 5, centerZ + fm.getAscent() / 2);

        // Direction arrow at player position
        drawDirectionArrow(g, centerX, centerZ, playerYaw, 7);

        // Outer border (2px)
        g.setColor(new Color(160, 160, 160));
        g.drawRect(0, 0, MINI_MAP_WIDTH - 1, MINI_MAP_HEIGHT - 1);
        g.setColor(new Color(100, 100, 100));
        g.drawRect(1, 1, MINI_MAP_WIDTH - 3, MINI_MAP_HEIGHT - 3);

        g.dispose();
        return img;
    }

    // ── Full-screen map ───────────────────────────────────────────────────────

    /**
     * Render the full-screen map.
     * Right side carries an ore legend showing every ore type seen so far.
     * Top-left shows the player's current world coordinates.
     * Bottom centre shows pan/zoom/close instructions.
     *
     * @param width        image width (window width)
     * @param height       image height (window height)
     * @param playerWorldX player X in world space
     * @param playerWorldZ player Z in world space
     * @param playerYaw    camera yaw in degrees
     */
    public BufferedImage renderFullMap(int width, int height,
                                       float playerWorldX, float playerWorldZ,
                                       float playerYaw) {
        // Legend panel on the right; map fills the rest.
        int legendW = Math.min(180, width / 5);
        int mapW    = width - legendW;

        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        // ── Map background ──────────────────────────────────────────────────
        g.setColor(new Color(20, 20, 25));
        g.fillRect(0, 0, mapW, height);

        int[] playerChunk = MapData.getChunkCoords(playerWorldX, playerWorldZ);
        int playerChunkX = playerChunk[0];
        int playerChunkZ = playerChunk[1];

        int chunkPx = Math.max(2, (int) (CHUNK_PIXEL_SIZE * mapScale));
        int cx0 = mapW / 2;
        int cz0 = height / 2;

        // Visible chunk radius
        int radiusX = mapW  / chunkPx / 2 + 2;
        int radiusZ = height / chunkPx / 2 + 2;

        // Track which ore types actually appear in the visible/explored area.
        Set<BlockType> seenOres = new LinkedHashSet<>();

        for (int cx = playerChunkX - radiusX; cx <= playerChunkX + radiusX; cx++) {
            for (int cz = playerChunkZ - radiusZ; cz <= playerChunkZ + radiusZ; cz++) {
                int screenX = cx0 + (int) (mapOffsetX + (cx - playerChunkX) * chunkPx);
                int screenZ = cz0 + (int) (mapOffsetZ + (cz - playerChunkZ) * chunkPx);

                if (screenX + chunkPx < 0 || screenX >= mapW)    continue;
                if (screenZ + chunkPx < 0 || screenZ >= height)   continue;

                if (mapData.isChunkExplored(cx, cz)) {
                    g.setColor(new Color(85, 85, 85));
                    g.fillRect(screenX, screenZ, chunkPx, chunkPx);

                    if (chunkPx >= 4) {
                        for (MapData.OreVeinRecord vein : mapData.getVeinsInChunk(cx, cz)) {
                            int oreX = screenX + ((vein.worldX & 0xF) * chunkPx) / 16;
                            int oreZ = screenZ + ((vein.worldZ & 0xF) * chunkPx) / 16;
                            int dotSz = Math.max(2, chunkPx / 6);
                            int color = ORE_COLORS.getOrDefault(vein.oreType, 0xFFFFFF);
                            g.setColor(new Color(color));
                            g.fillRect(oreX, oreZ, dotSz, dotSz);
                            seenOres.add(vein.oreType);
                        }
                    }
                } else {
                    g.setColor(new Color(30, 30, 35));
                    g.fillRect(screenX, screenZ, chunkPx, chunkPx);
                }

                // Grid lines only when chunks are large enough to distinguish
                if (chunkPx >= 6) {
                    g.setColor(new Color(50, 50, 50));
                    g.drawRect(screenX, screenZ, chunkPx, chunkPx);
                }
            }
        }

        // Player marker with direction arrow
        int markerX = cx0 + (int) mapOffsetX;
        int markerZ = cz0 + (int) mapOffsetZ;
        drawDirectionArrow(g, markerX, markerZ, playerYaw, 10);

        // Crosshair at screen centre (shows what coordinate the map is centred on)
        g.setColor(new Color(255, 220, 0, 160));
        int ch = 12;
        g.drawLine(cx0 - ch, cz0, cx0 + ch, cz0);
        g.drawLine(cx0, cz0 - ch, cx0, cz0 + ch);

        // ── HUD overlays on the map area ────────────────────────────────────
        Font hudFont = new Font(Font.SANS_SERIF, Font.PLAIN, 12);
        Font hudBold = new Font(Font.SANS_SERIF, Font.BOLD,  13);
        g.setFont(hudBold);

        // Coordinates (top-left)
        String coords = String.format("X: %.0f  Z: %.0f", playerWorldX, playerWorldZ);
        g.setColor(new Color(0, 0, 0, 160));
        g.fillRoundRect(5, 5, g.getFontMetrics().stringWidth(coords) + 10, 22, 6, 6);
        g.setColor(new Color(220, 220, 220));
        g.drawString(coords, 10, 21);

        // "Map" title (top centre)
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 16));
        String title = "World Map";
        FontMetrics tfm = g.getFontMetrics();
        g.setColor(new Color(0, 0, 0, 160));
        g.fillRoundRect(mapW / 2 - tfm.stringWidth(title) / 2 - 6, 4,
                tfm.stringWidth(title) + 12, 24, 6, 6);
        g.setColor(new Color(255, 255, 200));
        g.drawString(title, mapW / 2 - tfm.stringWidth(title) / 2, 21);

        // Instructions (bottom centre)
        g.setFont(hudFont);
        String hint = "WASD: pan  |  Scroll: zoom  |  R: reset  |  M / Esc: close";
        FontMetrics hfm = g.getFontMetrics();
        int hintW = hfm.stringWidth(hint);
        g.setColor(new Color(0, 0, 0, 160));
        g.fillRoundRect(mapW / 2 - hintW / 2 - 6, height - 22, hintW + 12, 18, 6, 6);
        g.setColor(new Color(180, 180, 180));
        g.drawString(hint, mapW / 2 - hintW / 2, height - 8);

        // ── Legend panel ────────────────────────────────────────────────────
        g.setColor(new Color(25, 25, 30));
        g.fillRect(mapW, 0, legendW, height);
        g.setColor(new Color(60, 60, 70));
        g.drawLine(mapW, 0, mapW, height);

        g.setFont(hudBold);
        g.setColor(new Color(210, 210, 210));
        g.drawString("Ore Legend", mapW + 8, 22);

        // Divider
        g.setColor(new Color(60, 60, 70));
        g.drawLine(mapW + 4, 28, width - 4, 28);

        // Discovered ores first; then all known (dimmed) if space allows.
        int ly = 44;
        int rowH = 16;
        g.setFont(hudFont);
        FontMetrics lfm = g.getFontMetrics();

        List<BlockType> legendOrder = new ArrayList<>();
        for (BlockType t : ORE_COLORS.keySet()) {
            if (seenOres.contains(t)) legendOrder.add(0, t);  // discovered first
        }
        // Add undiscovered if we haven't run off the panel yet
        for (BlockType t : ORE_COLORS.keySet()) {
            if (!seenOres.contains(t)) legendOrder.add(t);
        }

        for (BlockType ore : legendOrder) {
            if (ly + rowH > height - 6) break;
            int rgb  = ORE_COLORS.get(ore);
            String nm = ORE_NAMES.getOrDefault(ore, ore.name());
            boolean discovered = seenOres.contains(ore);

            // Colour swatch
            g.setColor(new Color(rgb));
            g.fillRect(mapW + 8, ly - 10, 10, 10);
            g.setColor(new Color(80, 80, 80));
            g.drawRect(mapW + 8, ly - 10, 10, 10);

            // Name (dimmed if not yet discovered)
            g.setColor(discovered ? new Color(210, 210, 210) : new Color(90, 90, 90));
            g.drawString(nm, mapW + 22, ly);
            ly += rowH;
        }

        g.dispose();
        return img;
    }

    // ── Zoom / Pan / Reset ────────────────────────────────────────────────────

    /** Zoom the full-screen map (>1.0 = zoom in). */
    public void zoom(float factor) {
        mapScale = Math.max(0.25f, Math.min(8.0f, mapScale * factor));
    }

    /** Pan the full-screen map. */
    public void pan(float deltaX, float deltaZ) {
        mapOffsetX += deltaX;
        mapOffsetZ += deltaZ;
    }

    /** Reset pan and zoom to defaults. */
    public void resetView() {
        mapScale   = 1.0f;
        mapOffsetX = 0f;
        mapOffsetZ = 0f;
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    /**
     * Draw a filled triangle pointing in the camera's facing direction.
     * The yaw convention matches {@link com.minecraftclone.engine.Camera}:
     * −90° = North (−Z), 0° = East (+X), 90° = South (+Z), 180° = West (−X).
     */
    private static void drawDirectionArrow(Graphics2D g, int cx, int cy, float yaw, int size) {
        double rad = Math.toRadians(yaw);
        // World (dx, dz) facing direction → screen (sx, sy): +sx = East, +sy = South (down)
        float dx = (float) Math.cos(rad);
        float dz = (float) Math.sin(rad);

        // Triangle tip, left base, right base
        float tipX  = cx + dx * (size + 2);
        float tipY  = cy + dz * (size + 2);
        float baseX = cx - dx * (size / 2f);
        float baseY = cy - dz * (size / 2f);
        float perpX = -dz;
        float perpY =  dx;
        float half  = size * 0.55f;

        int[] xs = {
            Math.round(tipX),
            Math.round(baseX + perpX * half),
            Math.round(baseX - perpX * half)
        };
        int[] ys = {
            Math.round(tipY),
            Math.round(baseY + perpY * half),
            Math.round(baseY - perpY * half)
        };

        g.setColor(new Color(0, 210, 0));
        g.fillPolygon(xs, ys, 3);
        g.setColor(new Color(0, 90, 0));
        g.drawPolygon(xs, ys, 3);
    }

    /** Draw a string horizontally centred on x. */
    private static void drawCentred(Graphics2D g, FontMetrics fm, String s, int cx, int y) {
        g.drawString(s, cx - fm.stringWidth(s) / 2, y);
    }
}
