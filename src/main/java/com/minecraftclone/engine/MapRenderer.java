package com.minecraftclone.engine;

import com.minecraftclone.world.BlockType;
import com.minecraftclone.world.Chunk;
import com.minecraftclone.world.MapData;
import com.minecraftclone.world.gen.GthnOreGenerator;
import com.minecraftclone.world.gen.GthnOreGenerator.MixInfo;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.List;

/**
 * JourneyMap-style top-down terrain renderer: block-resolution surface colours
 * with height shading, a zoomed-in mini-map, and GTNH VisualProspecting mix
 * waypoints (coloured diamonds + hover popups) on the full map.
 */
public class MapRenderer {

    public static final int MINI_MAP_SIZE = 192;
    /** Mini-map zoom: pixels per block (3px × 192 → ~64 blocks across). */
    public static final int MINI_PIXELS_PER_BLOCK = 3;
    public static final float DEFAULT_SCALE = 3.0f;
    public static final float MIN_SCALE = 0.5f;
    public static final float MAX_SCALE = 16.0f;

    private static final int UNEXPLORED = 0x16161C;
    private static final int EXPLORED_FALLBACK = 0x5A5A5A; // v1 saves, no surface yet
    private static final int CLUSTER_RADIUS = 40;

    // Ore vein colours (RGB) – used for mix waypoints and the legend.
    private static final Map<BlockType, Integer> ORE_COLORS = new LinkedHashMap<>();
    private static final Map<BlockType, String>  ORE_NAMES  = new LinkedHashMap<>();

    static {
        // Early-game ores
        reg(BlockType.COPPER_ORE,      0xE8772F, "Copper");
        reg(BlockType.TIN_ORE,         0xD2E4EA, "Tin");
        reg(BlockType.BAUXITE_ORE,     0xC65A2A, "Bauxite");
        reg(BlockType.ZINC_ORE,        0xA8D890, "Zinc");
        reg(BlockType.LEAD_ORE,        0x5C6A88, "Lead");
        reg(BlockType.SILVER_ORE,      0xF2F4FF, "Silver");

        // Mid-game ores
        reg(BlockType.NICKEL_ORE,      0xE6DC9A, "Nickel");
        reg(BlockType.COBALT_ORE,      0x3A62F0, "Cobalt");
        reg(BlockType.TUNGSTEN_ORE,    0x6A88A8, "Tungsten");
        reg(BlockType.MOLYBDENUM_ORE,  0x7A96B0, "Molybdenum");
        reg(BlockType.PLATINUM_ORE,    0xFFF4C8, "Platinum");

        // Advanced ores
        reg(BlockType.CHROMIUM_ORE,    0xC0E8DC, "Chromium");
        reg(BlockType.MANGANESE_ORE,   0xC86890, "Manganese");
        reg(BlockType.VANADIUM_ORE,    0x7A9A48, "Vanadium");
        reg(BlockType.BERYLLIUM_ORE,   0xD0E8A8, "Beryllium");
        reg(BlockType.TITANIUM_ORE,    0xB0C8E8, "Titanium");

        // Late-game ores
        reg(BlockType.URANIUM_ORE,     0x8CFF40, "Uranium");
        reg(BlockType.THORIUM_ORE,     0x7A5A48, "Thorium");
        reg(BlockType.PLUTONIUM_ORE,    0x3A7040, "Plutonium");
        reg(BlockType.IRIDIUM_ORE,      0xE8DCFF, "Iridium");

        // Vanilla ores
        reg(BlockType.COAL_ORE,        0x3A3A3A, "Coal");
        reg(BlockType.IRON_ORE,        0xD4A574, "Iron");
        reg(BlockType.GOLD_ORE,        0xFFD700, "Gold");
        reg(BlockType.DIAMOND_ORE,     0x00FFFF, "Diamond");

        // Extended GTNH ore pack — iron chain
        reg(BlockType.MAGNETITE_ORE,          0x5A5A78, "Magnetite");
        reg(BlockType.HEMATITE_ORE,           0xC04030, "Hematite");
        reg(BlockType.BROWN_LIMONITE_ORE,     0xC08040, "Brown Limonite");
        reg(BlockType.YELLOW_LIMONITE_ORE,    0xF0C040, "Yellow Limonite");
        reg(BlockType.BANDED_IRON_ORE,        0xC07040, "Banded Iron");
        reg(BlockType.VANADIUM_MAGNETITE_ORE, 0x505888, "Vanadium Magnetite");
        // Copper chain
        reg(BlockType.CHALCOPYRITE_ORE,  0xD8B028, "Chalcopyrite");
        reg(BlockType.TETRAHEDRITE_ORE,  0x506858, "Tetrahedrite");
        reg(BlockType.MALACHITE_ORE,     0x20C060, "Malachite");
        // Lead / Zinc
        reg(BlockType.GALENA_ORE,        0x8A8AA0, "Galena");
        reg(BlockType.SPHALERITE_ORE,    0xB4A080, "Sphalerite");
        // Nickel / Cobalt
        reg(BlockType.GARNIERITE_ORE,    0x7AAA7A, "Garnierite");
        reg(BlockType.PENTLANDITE_ORE,   0x9A8A5A, "Pentlandite");
        reg(BlockType.COBALTITE_ORE,     0x4A5A9A, "Cobaltite");
        // Sulfur chain
        reg(BlockType.PYRITE_ORE,        0xF0D030, "Pyrite");
        reg(BlockType.ARSENOPYRITE_ORE,  0xA8B888, "Arsenopyrite");
        reg(BlockType.SULFUR_ORE,        0xFFF050, "Sulfur");
        reg(BlockType.CINNABAR_ORE,      0xE82820, "Cinnabar");
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
        reg(BlockType.NAQUADAH_ORE,          0x2A8050, "Naquadah");
        reg(BlockType.NAQUADAH_ENRICHED_ORE, 0x208020, "Naquadah Enriched");
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
        reg(BlockType.RUBY_ORE,          0xFF2060, "Ruby");
        reg(BlockType.SAPPHIRE_ORE,      0x2060FF, "Sapphire");
        reg(BlockType.GREEN_SAPPHIRE_ORE,0x20D070, "Green Sapphire");
        reg(BlockType.PYROPE_ORE,        0xC01840, "Pyrope");
        reg(BlockType.SPESSARTINE_ORE,   0xF87820, "Spessartine");
    }

    private static void reg(BlockType type, int rgb, String name) {
        ORE_COLORS.put(type, rgb);
        ORE_NAMES.put(type, name);
    }

    /** Colour used for a mix waypoint (the mix primary's ore colour). */
    public static int oreColor(BlockType type) {
        return ORE_COLORS.getOrDefault(type, 0xFFFFFF);
    }

    private final MapData mapData;
    private float mapScale   = DEFAULT_SCALE;
    private float panWorldX  = 0f;
    private float panWorldZ  = 0f;

    // Cached mini-map
    private BufferedImage cachedMiniMapImage;
    private int lastMiniMapQx = Integer.MAX_VALUE;
    private int lastMiniMapQz = Integer.MAX_VALUE;
    private int lastMiniMapYaw = Integer.MAX_VALUE;
    private int lastMiniMapRevision = Integer.MIN_VALUE;
    /** Incremented each time the mini-map image is redrawn so Hud can re-upload the GPU texture. */
    private int miniMapVersion = 0;

    // Cached full-screen map
    private BufferedImage cachedFullMapImage;
    private int lastFullMapWidth = -1;
    private int lastFullMapHeight = -1;
    private int lastFullMapQx = Integer.MAX_VALUE;
    private int lastFullMapQz = Integer.MAX_VALUE;
    private int lastFullMapYaw = Integer.MAX_VALUE;
    private float lastFullMapScale = Float.NaN;
    private float lastFullPanX = Float.NaN;
    private float lastFullPanZ = Float.NaN;
    private int lastFullMouseX = Integer.MIN_VALUE;
    private int lastFullMouseY = Integer.MIN_VALUE;
    private int lastFullMapRevision = Integer.MIN_VALUE;
    /** Incremented each time the full-map image is redrawn so Hud can re-upload the GPU texture. */
    private int fullMapVersion = 0;

    private List<MixWaypoint> cachedWaypoints;
    private int cachedWaypointRevision = Integer.MIN_VALUE;

    public MapRenderer(MapData mapData) {
        this.mapData = mapData;
    }

    public static final class MixWaypoint {
        public final int worldX, worldY, worldZ;
        public final String mixName;
        public final String composition;
        public final BlockType primary;
        public final int color;

        public MixWaypoint(int worldX, int worldY, int worldZ,
                           String mixName, String composition,
                           BlockType primary, int color) {
            this.worldX = worldX;
            this.worldY = worldY;
            this.worldZ = worldZ;
            this.mixName = mixName;
            this.composition = composition;
            this.primary = primary;
            this.color = color;
        }
    }

    // ── Mini-map ─────────────────────────────────────────────────────────────

    /**
     * Render the mini-map centred on the player at block resolution.
     * Includes a direction arrow, N/S/E/W compass labels, mix-vein diamonds
     * and an outer border.
     *
     * @param playerWorldX player X in world space
     * @param playerWorldZ player Z in world space
     * @param playerYaw    camera yaw in degrees (−90 = North/−Z, 0 = East/+X)
     */
    public BufferedImage renderMiniMap(float playerWorldX, float playerWorldZ, float playerYaw) {
        int qx = Math.round(playerWorldX * MINI_PIXELS_PER_BLOCK);
        int qz = Math.round(playerWorldZ * MINI_PIXELS_PER_BLOCK);
        int qYaw = Math.round(playerYaw);
        int rev = mapData.getRevision();

        if (cachedMiniMapImage != null
                && lastMiniMapQx == qx
                && lastMiniMapQz == qz
                && lastMiniMapYaw == qYaw
                && lastMiniMapRevision == rev) {
            return cachedMiniMapImage;
        }

        lastMiniMapQx = qx;
        lastMiniMapQz = qz;
        lastMiniMapYaw = qYaw;
        lastMiniMapRevision = rev;

        BufferedImage img = cachedMiniMapImage;
        if (img == null || img.getWidth() != MINI_MAP_SIZE || img.getHeight() != MINI_MAP_SIZE) {
            img = new BufferedImage(MINI_MAP_SIZE, MINI_MAP_SIZE, BufferedImage.TYPE_INT_RGB);
            cachedMiniMapImage = img;
        }

        int[] pixels = new int[MINI_MAP_SIZE * MINI_MAP_SIZE];
        float half = MINI_MAP_SIZE / 2f;
        for (int py = 0; py < MINI_MAP_SIZE; py++) {
            int wz = (int) Math.floor(playerWorldZ + (py - half) / MINI_PIXELS_PER_BLOCK);
            for (int px = 0; px < MINI_MAP_SIZE; px++) {
                int wx = (int) Math.floor(playerWorldX + (px - half) / MINI_PIXELS_PER_BLOCK);
                pixels[py * MINI_MAP_SIZE + px] = colorAt(wx, wz);
            }
        }
        img.setRGB(0, 0, MINI_MAP_SIZE, MINI_MAP_SIZE, pixels, 0, MINI_MAP_SIZE);

        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        for (MixWaypoint wp : waypoints()) {
            int sx = Math.round(half + (wp.worldX + 0.5f - playerWorldX) * MINI_PIXELS_PER_BLOCK);
            int sy = Math.round(half + (wp.worldZ + 0.5f - playerWorldZ) * MINI_PIXELS_PER_BLOCK);
            if (sx < 4 || sx >= MINI_MAP_SIZE - 4 || sy < 4 || sy >= MINI_MAP_SIZE - 4) continue;
            drawDiamond(g, sx, sy, 3, wp.color);
        }

        int centerX = MINI_MAP_SIZE / 2;
        int centerZ = MINI_MAP_SIZE / 2;

        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 10));
        FontMetrics fm = g.getFontMetrics();
        g.setColor(new Color(240, 240, 240));
        drawCentred(g, fm, "N", centerX, 12);
        drawCentred(g, fm, "S", centerX, MINI_MAP_SIZE - 4);
        drawCentred(g, fm, "E", MINI_MAP_SIZE - 6, centerZ + fm.getAscent() / 2);
        drawCentred(g, fm, "W", 6, centerZ + fm.getAscent() / 2);

        drawDirectionArrow(g, centerX, centerZ, playerYaw, 8);

        g.setColor(new Color(200, 200, 200));
        g.drawRect(0, 0, MINI_MAP_SIZE - 1, MINI_MAP_SIZE - 1);
        g.setColor(new Color(40, 40, 40));
        g.drawRect(1, 1, MINI_MAP_SIZE - 3, MINI_MAP_SIZE - 3);

        g.dispose();
        miniMapVersion++;
        return img;
    }

    /** Returns the version counter that increments each time the mini-map image is redrawn. */
    public int getMiniMapVersion() {
        return miniMapVersion;
    }

    // ── Full-screen map ───────────────────────────────────────────────────────

    /**
     * Render the full-screen map at block resolution, north-up, with mix
     * waypoints and a hover popup when the cursor sits on a diamond.
     *
     * @param width        image width (window width)
     * @param height       image height (window height)
     * @param playerWorldX player X in world space
     * @param playerWorldZ player Z in world space
     * @param playerYaw    camera yaw in degrees
     * @param mouseX       cursor X in window pixels (origin top-left)
     * @param mouseY       cursor Y in window pixels
     */
    public BufferedImage renderFullMap(int width, int height,
                                       float playerWorldX, float playerWorldZ,
                                       float playerYaw,
                                       int mouseX, int mouseY) {
        int qx = Math.round(playerWorldX * 2f);
        int qz = Math.round(playerWorldZ * 2f);
        int qYaw = Math.round(playerYaw);
        int rev = mapData.getRevision();

        if (cachedFullMapImage != null
                && lastFullMapWidth == width
                && lastFullMapHeight == height
                && lastFullMapQx == qx
                && lastFullMapQz == qz
                && lastFullMapYaw == qYaw
                && lastFullMapScale == mapScale
                && lastFullPanX == panWorldX
                && lastFullPanZ == panWorldZ
                && lastFullMouseX == mouseX
                && lastFullMouseY == mouseY
                && lastFullMapRevision == rev) {
            return cachedFullMapImage;
        }

        lastFullMapWidth = width;
        lastFullMapHeight = height;
        lastFullMapQx = qx;
        lastFullMapQz = qz;
        lastFullMapYaw = qYaw;
        lastFullMapScale = mapScale;
        lastFullPanX = panWorldX;
        lastFullPanZ = panWorldZ;
        lastFullMouseX = mouseX;
        lastFullMouseY = mouseY;
        lastFullMapRevision = rev;

        int legendW = Math.min(220, Math.max(160, width / 6));
        int mapW    = Math.max(1, width - legendW);

        BufferedImage img = cachedFullMapImage;
        if (img == null || img.getWidth() != width || img.getHeight() != height) {
            img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            cachedFullMapImage = img;
        }

        float viewX = playerWorldX + panWorldX;
        float viewZ = playerWorldZ + panWorldZ;
        float scale = mapScale;

        int[] pixels = new int[width * height];
        Arrays.fill(pixels, UNEXPLORED);
        float halfW = mapW / 2f;
        float halfH = height / 2f;
        for (int sy = 0; sy < height; sy++) {
            int wz = (int) Math.floor(viewZ + (sy - halfH) / scale);
            int row = sy * width;
            for (int sx = 0; sx < mapW; sx++) {
                int wx = (int) Math.floor(viewX + (sx - halfW) / scale);
                pixels[row + sx] = colorAt(wx, wz);
            }
        }
        img.setRGB(0, 0, width, height, pixels, 0, width);

        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        List<MixWaypoint> wps = waypoints();
        Set<String> seenMixes = new LinkedHashSet<>();
        boolean showLabels = scale >= 2.0f;
        MixWaypoint hovered = null;
        double hoverDist = 14;

        for (MixWaypoint wp : wps) {
            int sx = Math.round(halfW + (wp.worldX + 0.5f - viewX) * scale);
            int sy = Math.round(halfH + (wp.worldZ + 0.5f - viewZ) * scale);
            if (sx < 2 || sx >= mapW - 2 || sy < 2 || sy >= height - 2) continue;
            seenMixes.add(wp.mixName);
            int diamond = Math.max(4, Math.min(7, Math.round(scale * 1.4f)));
            drawDiamond(g, sx, sy, diamond, wp.color);
            if (showLabels) {
                g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 11));
                FontMetrics lfm = g.getFontMetrics();
                int tw = lfm.stringWidth(wp.mixName);
                int tx = sx + diamond + 4;
                int ty = sy + 4;
                if (tx + tw < mapW - 4) {
                    g.setColor(new Color(0, 0, 0, 170));
                    g.fillRoundRect(tx - 2, ty - lfm.getAscent(), tw + 4, lfm.getHeight(), 4, 4);
                    g.setColor(new Color(wp.color));
                    g.drawString(wp.mixName, tx, ty);
                }
            }
            double d = Math.hypot(mouseX - sx, mouseY - sy);
            if (d < hoverDist) {
                hoverDist = d;
                hovered = wp;
            }
        }

        int markerX = Math.round(halfW + (playerWorldX - viewX) * scale);
        int markerZ = Math.round(halfH + (playerWorldZ - viewZ) * scale);
        drawDirectionArrow(g, markerX, markerZ, playerYaw, 11);

        Font hudFont = new Font(Font.SANS_SERIF, Font.PLAIN, 12);
        Font hudBold = new Font(Font.SANS_SERIF, Font.BOLD,  13);
        g.setFont(hudBold);

        String coords = String.format("X: %.0f  Z: %.0f", playerWorldX, playerWorldZ);
        g.setColor(new Color(0, 0, 0, 170));
        g.fillRoundRect(5, 5, g.getFontMetrics().stringWidth(coords) + 10, 22, 6, 6);
        g.setColor(new Color(220, 220, 220));
        g.drawString(coords, 10, 21);

        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 16));
        String title = "World Map";
        FontMetrics tfm = g.getFontMetrics();
        g.setColor(new Color(0, 0, 0, 170));
        g.fillRoundRect(mapW / 2 - tfm.stringWidth(title) / 2 - 6, 4,
                tfm.stringWidth(title) + 12, 24, 6, 6);
        g.setColor(new Color(255, 255, 200));
        g.drawString(title, mapW / 2 - tfm.stringWidth(title) / 2, 21);

        g.setFont(hudFont);
        String hint = "WASD: pan  |  Scroll: zoom  |  R: reset  |  Hover a diamond for mix info  |  M / Esc: close";
        FontMetrics hfm = g.getFontMetrics();
        int hintW = hfm.stringWidth(hint);
        g.setColor(new Color(0, 0, 0, 170));
        g.fillRoundRect(Math.max(6, mapW / 2 - hintW / 2 - 6), height - 22, hintW + 12, 18, 6, 6);
        g.setColor(new Color(180, 180, 180));
        g.drawString(hint, Math.max(12, mapW / 2 - hintW / 2), height - 8);

        if (hovered != null) {
            drawMixPopup(g, hovered, mouseX, mouseY, mapW, height);
        }

        // ── Legend panel ────────────────────────────────────────────────────
        g.setColor(new Color(22, 22, 28));
        g.fillRect(mapW, 0, legendW, height);
        g.setColor(new Color(60, 60, 70));
        g.drawLine(mapW, 0, mapW, height);

        g.setFont(hudBold);
        g.setColor(new Color(210, 210, 210));
        g.drawString("Ore Mixes", mapW + 8, 22);

        g.setColor(new Color(60, 60, 70));
        g.drawLine(mapW + 4, 28, width - 4, 28);

        int ly = 44;
        int rowH = 16;
        g.setFont(hudFont);

        List<MixWaypoint> legend = new ArrayList<>();
        Set<String> listed = new HashSet<>();
        for (MixWaypoint wp : wps) {
            if (listed.add(wp.mixName)) legend.add(wp);
        }
        legend.sort(Comparator.comparing(w -> w.mixName));

        for (MixWaypoint wp : legend) {
            if (ly + rowH > height - 6) break;
            boolean visible = seenMixes.contains(wp.mixName);
            g.setColor(new Color(wp.color));
            g.fillPolygon(
                    new int[]{mapW + 13, mapW + 18, mapW + 13, mapW + 8},
                    new int[]{ly - 12, ly - 7, ly - 2, ly - 7},
                    4);
            g.setColor(new Color(40, 40, 40));
            g.drawPolygon(
                    new int[]{mapW + 13, mapW + 18, mapW + 13, mapW + 8},
                    new int[]{ly - 12, ly - 7, ly - 2, ly - 7},
                    4);
            g.setColor(visible ? new Color(220, 220, 220) : new Color(110, 110, 110));
            String nm = wp.mixName;
            FontMetrics lfm = g.getFontMetrics();
            while (nm.length() > 4 && lfm.stringWidth(nm) > legendW - 30) {
                nm = nm.substring(0, nm.length() - 2);
            }
            if (!nm.equals(wp.mixName)) nm = nm + "…";
            g.drawString(nm, mapW + 24, ly);
            ly += rowH;
        }

        if (legend.isEmpty()) {
            g.setColor(new Color(120, 120, 120));
            g.drawString("No veins yet", mapW + 8, ly);
            g.drawString("Explore to find", mapW + 8, ly + 16);
            g.drawString("GTNH mixes.", mapW + 8, ly + 32);
        }

        g.dispose();
        fullMapVersion++;
        return img;
    }

    /** Returns the version counter that increments each time the full-map image is redrawn. */
    public int getFullMapVersion() {
        return fullMapVersion;
    }

    // ── Zoom / Pan / Reset ────────────────────────────────────────────────────

    /** Zoom the full-screen map (>1.0 = zoom in). Scale is pixels-per-block. */
    public void zoom(float factor) {
        mapScale = Math.max(MIN_SCALE, Math.min(MAX_SCALE, mapScale * factor));
    }

    /**
     * Pan the full-screen map. {@code deltaX}/{@code deltaZ} are in screen
     * pixels; converted to world blocks using the current zoom so motion
     * feels constant on-screen.
     */
    public void pan(float deltaX, float deltaZ) {
        panWorldX += deltaX / mapScale;
        panWorldZ += deltaZ / mapScale;
    }

    /** Reset pan and zoom to defaults (player-centred, ~3 px/block). */
    public void resetView() {
        mapScale   = DEFAULT_SCALE;
        panWorldX  = 0f;
        panWorldZ  = 0f;
    }

    public float getMapScale() {
        return mapScale;
    }

    // ── Terrain colour ────────────────────────────────────────────────────────

    /**
     * JourneyMap-like map colour for a surface block. Public so tests can lock
     * the palette (grass is green, water is blue, sand is yellow, …).
     */
    public static int terrainColor(BlockType b) {
        if (b == null || b == BlockType.AIR) return UNEXPLORED;
        if (b.isWater()) return 0x3A6BC4;
        if (b.isLava()) return 0xE06018;
        return switch (b) {
            case GRASS, TALL_GRASS, FLOWER_RED, FLOWER_YELLOW -> 0x5DAA32;
            case SWAMP_GRASS, VINE, LILY_PAD -> 0x3E7A38;
            case MYCELIUM, MUSHROOM_RED, MUSHROOM_BROWN -> 0x6B5A68;
            case DIRT -> 0x966C4A;
            case SAND -> 0xE8D48A;
            case GRAVEL -> 0x7A7A72;
            case STONE, STONE_SLAB, STONE_STAIRS, BEDROCK -> 0x7A7A7A;
            case SNOW, SNOWY_STONE_SLAB, SNOWY_PLANKS_SLAB -> 0xF4F8FB;
            case ICE -> 0x8CB8E0;
            case PACKED_ICE -> 0x74A0D8;
            case WOOD_LOG -> 0x6B5530;
            case PLANKS, PLANKS_SLAB, PLANKS_STAIRS, WOODEN_FENCE, CRAFTING_TABLE -> 0xB8945F;
            case LEAVES -> 0x2F6B32;
            case CHERRY_LEAVES -> 0xE8A0B8;
            case CACTUS -> 0x3A8A32;
            case DEAD_BUSH -> 0x8A6A3A;
            case RED_CLAY -> 0xB45A32;
            case NETHERRACK -> 0x6E2E2E;
            case SOUL_SAND -> 0x4A3A28;
            case GLOWSTONE -> 0xE8C45A;
            case END_STONE -> 0xD8D49A;
            case OBSIDIAN -> 0x1A1028;
            case NETHER_PORTAL -> 0x6A20C0;
            case END_PORTAL -> 0x1A1A3A;
            case PUMPKIN -> 0xC07818;
            case WOOL -> 0xD8D8D8;
            case GLASS -> 0xC0D0E0;
            case BAMBOO -> 0x5A9A32;
            case SEAWEED -> 0x2A6A48;
            default -> {
                String n = b.name();
                if (n.contains("LEAVES")) yield 0x2F6B32;
                if (n.contains("LOG") || n.contains("WOOD") || n.contains("PLANK")) yield 0x6B5530;
                if (n.contains("SAND")) yield 0xE8D48A;
                if (n.contains("GRASS")) yield 0x5DAA32;
                if (n.contains("SNOW") || n.contains("ICE")) yield 0xE8F0F8;
                if (b.cross) yield 0x5DAA32;
                yield 0x6E6E68;
            }
        };
    }

    /**
     * Classic Minecraft map shading: brighter than the west neighbour if
     * higher, darker if lower, plus a gentle altitude wash so peaks pop.
     */
    public static int shade(int rgb, int height, int westHeight) {
        float f = 1.0f;
        if (westHeight >= 0) {
            if (height > westHeight) f = 1.22f;
            else if (height < westHeight) f = 0.78f;
        }
        f *= 0.82f + 0.36f * Math.min(1f, Math.max(0, height) / 80f);
        return scaleRgb(rgb, f);
    }

    private static int scaleRgb(int rgb, float f) {
        int r = Math.min(255, Math.max(0, Math.round(((rgb >> 16) & 0xFF) * f)));
        int g = Math.min(255, Math.max(0, Math.round(((rgb >> 8) & 0xFF) * f)));
        int b = Math.min(255, Math.max(0, Math.round((rgb & 0xFF) * f)));
        return (r << 16) | (g << 8) | b;
    }

    private int colorAt(int worldX, int worldZ) {
        int cx = Math.floorDiv(worldX, Chunk.SIZE);
        int cz = Math.floorDiv(worldZ, Chunk.SIZE);
        if (!mapData.isChunkExplored(cx, cz)) return UNEXPLORED;
        BlockType block = mapData.getSurfaceBlock(worldX, worldZ);
        if (block == null) return EXPLORED_FALLBACK;
        int h = mapData.getSurfaceY(worldX, worldZ);
        int westH = mapData.getSurfaceY(worldX - 1, worldZ);
        return shade(terrainColor(block), h, westH);
    }

    // ── Mix waypoints ─────────────────────────────────────────────────────────

    private List<MixWaypoint> waypoints() {
        int rev = mapData.getRevision();
        if (cachedWaypoints != null && cachedWaypointRevision == rev) return cachedWaypoints;
        cachedWaypoints = clusterMixWaypoints(mapData);
        cachedWaypointRevision = rev;
        return cachedWaypoints;
    }

    /**
     * Collapse per-block vein records into one VisualProspecting-style mix
     * waypoint per cluster (same mix name, within {@link #CLUSTER_RADIUS}
     * blocks). Public for tests.
     */
    public static List<MixWaypoint> clusterMixWaypoints(MapData data) {
        Map<String, List<MapData.OreVeinRecord>> byMix = new LinkedHashMap<>();
        Map<String, MixInfo> infoByName = new HashMap<>();
        for (MapData.OreVeinRecord vein : data.allVeins()) {
            MixInfo info = GthnOreGenerator.mixInfo(vein.oreType);
            if (info == null) continue;
            byMix.computeIfAbsent(info.name, k -> new ArrayList<>()).add(vein);
            infoByName.putIfAbsent(info.name, info);
        }

        List<MixWaypoint> out = new ArrayList<>();
        int r2 = CLUSTER_RADIUS * CLUSTER_RADIUS;
        for (Map.Entry<String, List<MapData.OreVeinRecord>> e : byMix.entrySet()) {
            MixInfo info = infoByName.get(e.getKey());
            List<MapData.OreVeinRecord> remaining = new ArrayList<>(e.getValue());
            while (!remaining.isEmpty()) {
                MapData.OreVeinRecord seed = remaining.remove(0);
                long sx = seed.worldX, sy = seed.worldY, sz = seed.worldZ;
                int n = 1;
                Iterator<MapData.OreVeinRecord> it = remaining.iterator();
                while (it.hasNext()) {
                    MapData.OreVeinRecord v = it.next();
                    int dx = v.worldX - seed.worldX;
                    int dz = v.worldZ - seed.worldZ;
                    if (dx * dx + dz * dz <= r2) {
                        sx += v.worldX;
                        sy += v.worldY;
                        sz += v.worldZ;
                        n++;
                        it.remove();
                    }
                }
                out.add(new MixWaypoint(
                        (int) (sx / n), (int) (sy / n), (int) (sz / n),
                        info.name, info.compositionLabel(), info.primary,
                        oreColor(info.primary)));
            }
        }
        return out;
    }

    // ── Drawing helpers ───────────────────────────────────────────────────────

    private static void drawMixPopup(Graphics2D g, MixWaypoint wp, int mouseX, int mouseY,
                                     int mapW, int height) {
        String title = wp.mixName;
        String comp = wp.composition;
        String yLine = "Y: " + wp.worldY;
        String xzLine = "X: " + wp.worldX + "  Z: " + wp.worldZ;

        Font titleFont = new Font(Font.SANS_SERIF, Font.BOLD, 13);
        Font bodyFont = new Font(Font.SANS_SERIF, Font.PLAIN, 11);
        g.setFont(titleFont);
        FontMetrics tfm = g.getFontMetrics();
        g.setFont(bodyFont);
        FontMetrics bfm = g.getFontMetrics();

        int inner = Math.max(tfm.stringWidth(title) + 22,
                Math.max(bfm.stringWidth(comp),
                        Math.max(bfm.stringWidth(yLine), bfm.stringWidth(xzLine))));
        int boxW = inner + 16;
        int boxH = 72;
        int px = mouseX + 16;
        int py = mouseY + 16;
        if (px + boxW > mapW - 6) px = mouseX - boxW - 12;
        if (py + boxH > height - 6) py = mouseY - boxH - 12;
        px = Math.max(6, px);
        py = Math.max(6, py);

        g.setColor(new Color(12, 12, 18, 230));
        g.fillRoundRect(px, py, boxW, boxH, 8, 8);
        g.setColor(new Color(wp.color));
        g.setStroke(new BasicStroke(2f));
        g.drawRoundRect(px, py, boxW, boxH, 8, 8);
        g.setStroke(new BasicStroke(1f));

        drawDiamond(g, px + 12, py + 16, 5, wp.color);
        g.setFont(titleFont);
        g.setColor(new Color(wp.color));
        g.drawString(title, px + 22, py + 20);

        g.setFont(bodyFont);
        g.setColor(new Color(210, 210, 210));
        g.drawString(comp, px + 8, py + 38);
        g.setColor(new Color(180, 180, 180));
        g.drawString(yLine, px + 8, py + 52);
        g.drawString(xzLine, px + 8, py + 64);
    }

    private static void drawDiamond(Graphics2D g, int cx, int cy, int size, int rgb) {
        int[] xs = {cx, cx + size, cx, cx - size};
        int[] ys = {cy - size, cy, cy + size, cy};
        g.setColor(new Color(rgb));
        g.fillPolygon(xs, ys, 4);
        g.setColor(new Color(20, 20, 20));
        g.drawPolygon(xs, ys, 4);
        // Inner highlight so dark ores still read as a waypoint.
        g.setColor(new Color(255, 255, 255, 90));
        int h = Math.max(1, size / 2);
        g.fillPolygon(new int[]{cx, cx + h, cx, cx - h},
                new int[]{cy - h, cy, cy + h, cy}, 4);
    }

    /**
     * Draw a filled triangle pointing in the camera's facing direction.
     * The yaw convention matches {@link com.minecraftclone.engine.Camera}:
     * −90° = North (−Z), 0° = East (+X), 90° = South (+Z), 180° = West (−X).
     */
    private static void drawDirectionArrow(Graphics2D g, int cx, int cy, float yaw, int size) {
        double rad = Math.toRadians(yaw);
        float dx = (float) Math.cos(rad);
        float dz = (float) Math.sin(rad);

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

        g.setColor(new Color(20, 20, 20));
        g.fillPolygon(new int[]{xs[0], xs[1], xs[2]},
                new int[]{ys[0] + 1, ys[1] + 1, ys[2] + 1}, 3);
        g.setColor(new Color(40, 220, 70));
        g.fillPolygon(xs, ys, 3);
        g.setColor(new Color(10, 80, 20));
        g.drawPolygon(xs, ys, 3);
    }

    /** Draw a string horizontally centred on x. */
    private static void drawCentred(Graphics2D g, FontMetrics fm, String s, int cx, int y) {
        g.drawString(s, cx - fm.stringWidth(s) / 2, y);
    }
}
