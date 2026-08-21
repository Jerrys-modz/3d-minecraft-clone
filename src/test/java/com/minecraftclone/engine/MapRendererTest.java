package com.minecraftclone.engine;

import com.minecraftclone.world.BlockAccessor;
import com.minecraftclone.world.BlockType;
import com.minecraftclone.world.MapData;
import com.minecraftclone.world.gen.GthnOreGenerator;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JourneyMap-style terrain colours, GTNH mix names and waypoint clustering
 * are all GPU-free, so they can be locked in here without a display.
 */
class MapRendererTest {

    @Test
    void terrainPaletteLooksLikeMinecraftMaps() {
        assertTrue(green(MapRenderer.terrainColor(BlockType.GRASS))
                        > red(MapRenderer.terrainColor(BlockType.GRASS)),
                "grass should read green");
        assertTrue(blue(MapRenderer.terrainColor(BlockType.WATER))
                        > green(MapRenderer.terrainColor(BlockType.WATER)),
                "water should read blue");
        assertTrue(red(MapRenderer.terrainColor(BlockType.SAND))
                        > blue(MapRenderer.terrainColor(BlockType.SAND)),
                "sand should read yellow");
        assertTrue(MapRenderer.terrainColor(BlockType.SNOW) > 0xE0E0E0,
                "snow should be near-white");
        assertTrue(red(MapRenderer.terrainColor(BlockType.NETHERRACK))
                        > blue(MapRenderer.terrainColor(BlockType.NETHERRACK)),
                "netherrack should read red");
        assertNotEquals(MapRenderer.terrainColor(BlockType.GRASS),
                MapRenderer.terrainColor(BlockType.WATER));
        assertNotEquals(MapRenderer.terrainColor(BlockType.GRASS),
                MapRenderer.terrainColor(BlockType.SAND));
    }

    @Test
    void heightShadingBrightensPeaksAndDarkensValleys() {
        int base = MapRenderer.terrainColor(BlockType.GRASS);
        int higher = MapRenderer.shade(base, 50, 40);
        int lower = MapRenderer.shade(base, 30, 40);
        assertTrue(luma(higher) > luma(base), "higher than west neighbour is brighter");
        assertTrue(luma(lower) < luma(base), "lower than west neighbour is darker");
    }

    @Test
    void mixNamesFollowGtnhPrimaries() {
        assertEquals("Copper Mix", GthnOreGenerator.mixName(BlockType.COPPER_ORE));
        assertEquals("Copper Mix", GthnOreGenerator.mixName(BlockType.TIN_ORE),
                "tin is a copper-mix secondary");
        assertEquals("Magnetite Mix", GthnOreGenerator.mixName(BlockType.MAGNETITE_ORE));
        assertEquals("Chalcopyrite Mix", GthnOreGenerator.mixName(BlockType.CHALCOPYRITE_ORE));
        assertEquals("Coal", GthnOreGenerator.mixName(BlockType.COAL_ORE),
                "vanilla ores keep their own name");
        assertNull(GthnOreGenerator.mixName(BlockType.GRASS));
        assertTrue(GthnOreGenerator.mixInfo(BlockType.COPPER_ORE).compositionLabel()
                .contains("Tin"));
    }

    @Test
    void nearbySameMixVeinsCollapseToOneWaypoint() {
        MapData data = new MapData();
        data.exploreChunk(0, 0, (x, y, z) -> {
            if (y == 20 && x >= 2 && x <= 6 && z >= 2 && z <= 6) return BlockType.COPPER_ORE;
            if (y == 20 && x >= 8 && x <= 10 && z >= 8 && z <= 10) return BlockType.TIN_ORE;
            if (y == 40) return BlockType.GRASS;
            if (y < 40) return BlockType.DIRT;
            return BlockType.AIR;
        });
        data.discoverOre(4, 20, 4, BlockType.COPPER_ORE);
        data.discoverOre(9, 20, 9, BlockType.TIN_ORE);
        List<MapRenderer.MixWaypoint> wps = MapRenderer.clusterMixWaypoints(data);
        assertEquals(1, wps.size(), "copper + tin in one vein should be one Copper Mix");
        assertEquals("Copper Mix", wps.get(0).mixName);
        assertTrue(wps.get(0).composition.contains("Tin"));
        assertEquals(BlockType.COPPER_ORE, wps.get(0).primary);
    }

    @Test
    void differentMixesStaySeparateWaypoints() {
        MapData data = new MapData();
        // Chunk (0,0): copper. Chunk (4,0) is 64 blocks east — outside cluster radius.
        data.exploreChunk(0, 0, oreColumn(4, 4, BlockType.COPPER_ORE));
        data.exploreChunk(4, 0, oreColumn(4 * 16 + 4, 4, BlockType.MAGNETITE_ORE));
        data.discoverOre(4, 22, 4, BlockType.COPPER_ORE);
        data.discoverOre(4 * 16 + 4, 22, 4, BlockType.MAGNETITE_ORE);
        List<MapRenderer.MixWaypoint> wps = MapRenderer.clusterMixWaypoints(data);
        Set<String> names = wps.stream().map(w -> w.mixName).collect(Collectors.toSet());
        assertEquals(Set.of("Copper Mix", "Magnetite Mix"), names);
    }

    @Test
    void undiscoveredOresDoNotGetWaypoints() {
        MapData data = new MapData();
        data.exploreChunk(0, 0, oreColumn(4, 4, BlockType.COPPER_ORE));
        assertTrue(MapRenderer.clusterMixWaypoints(data).isEmpty(),
                "terrain exploration must not reveal the mix");
        data.discoverOre(4, 22, 4, BlockType.SMALL_COPPER_ORE);
        List<MapRenderer.MixWaypoint> wps = MapRenderer.clusterMixWaypoints(data);
        assertEquals(1, wps.size());
        assertEquals("Copper Mix", wps.get(0).mixName);
    }

    @Test
    void miniMapIsBlockResolutionAndShowsTerrainNotJustGray() {
        MapData data = new MapData();
        data.exploreChunk(0, 0, (x, y, z) -> {
            if (y > 40) return BlockType.AIR;
            if (y == 40) return BlockType.GRASS;
            return BlockType.DIRT;
        });
        MapRenderer renderer = new MapRenderer(data);
        BufferedImage img = renderer.renderMiniMap(8f, 8f, -90f);
        assertEquals(MapRenderer.MINI_MAP_SIZE, img.getWidth());
        assertEquals(MapRenderer.MINI_MAP_SIZE, img.getHeight());
        int blocksAcross = MapRenderer.MINI_MAP_SIZE / MapRenderer.MINI_PIXELS_PER_BLOCK;
        assertTrue(blocksAcross >= 64 && blocksAcross <= 96,
                "mini-map should show ~4–6 chunks around the player, got " + blocksAcross);

        int greenPixels = 0;
        int grayish = 0;
        int total = img.getWidth() * img.getHeight();
        for (int y = 0; y < img.getHeight(); y++) {
            for (int x = 0; x < img.getWidth(); x++) {
                int rgb = img.getRGB(x, y) & 0xFFFFFF;
                if (green(rgb) > red(rgb) + 10 && green(rgb) > blue(rgb) + 10) greenPixels++;
                int dr = Math.abs(red(rgb) - green(rgb));
                int dg = Math.abs(green(rgb) - blue(rgb));
                if (dr < 12 && dg < 12) grayish++;
            }
        }
        assertTrue(greenPixels > 200, "explored grass should paint green, got " + greenPixels
                + " / " + total);
        assertTrue(greenPixels > 0 && grayish < total,
                "terrain should not be a flat grey ore grid");
    }

    @Test
    void compassLettersStayReadableOnSnow() {
        MapData data = new MapData();
        BlockAccessor snow = (x, y, z) -> {
            if (y > 40) return BlockType.AIR;
            if (y == 40) return BlockType.SNOW;
            return BlockType.DIRT;
        };
        for (int cx = -4; cx <= 4; cx++) {
            for (int cz = -4; cz <= 4; cz++) {
                data.exploreChunk(cx, cz, snow);
            }
        }
        MapRenderer renderer = new MapRenderer(data);
        BufferedImage img = renderer.renderMiniMap(8f, 8f, -90f);
        int size = MapRenderer.MINI_MAP_SIZE;

        Region n = sample(img, size / 2 - 22, 0, size / 2 + 22, 40);
        assertTrue(n.dark, "N should sit on a dark pill, not float over snow");
        assertTrue(n.gold, "N should be gold");
        assertTrue(n.contrast > 200, "N must contrast with snow, delta=" + n.contrast);

        Region s = sample(img, size / 2 - 22, size - 40, size / 2 + 22, size);
        assertTrue(s.dark, "S should sit on a dark pill");
        assertTrue(s.white, "S should be a bright letter");
        assertTrue(s.contrast > 200, "S must contrast with snow, delta=" + s.contrast);

        Region e = sample(img, size - 40, size / 2 - 22, size, size / 2 + 22);
        assertTrue(e.dark && e.white && e.contrast > 200, "E should be a bright letter on a dark pill");

        Region w = sample(img, 0, size / 2 - 22, 40, size / 2 + 22);
        assertTrue(w.dark && w.white && w.contrast > 200, "W should be a bright letter on a dark pill");
    }

    @Test
    void defaultFullMapZoomIsPixelsPerBlockNotChunks() {
        assertEquals(3.0f, MapRenderer.DEFAULT_SCALE, 0.01f);
        assertTrue(MapRenderer.MIN_SCALE < 1f);
        assertTrue(MapRenderer.MAX_SCALE >= 8f);
        MapRenderer renderer = new MapRenderer(new MapData());
        assertEquals(MapRenderer.DEFAULT_SCALE, renderer.getMapScale(), 0.01f);
        renderer.zoom(1.2f);
        assertTrue(renderer.getMapScale() > MapRenderer.DEFAULT_SCALE);
        renderer.resetView();
        assertEquals(MapRenderer.DEFAULT_SCALE, renderer.getMapScale(), 0.01f);
    }

    @Test
    void panMovesTheViewInScreenPixels() {
        MapRenderer renderer = new MapRenderer(new MapData());
        assertEquals(0f, renderer.getPanWorldX(), 0.01f);
        assertEquals(0f, renderer.getPanWorldZ(), 0.01f);
        renderer.pan(3f, 0f); // 3 px at 3 px/block = 1 block east
        assertEquals(1f, renderer.getPanWorldX(), 0.01f);
        renderer.pan(0f, -6f); // 2 blocks north
        assertEquals(-2f, renderer.getPanWorldZ(), 0.01f);
        renderer.resetView();
        assertEquals(0f, renderer.getPanWorldX(), 0.01f);
        assertEquals(0f, renderer.getPanWorldZ(), 0.01f);
    }

    @Test
    void zoomAtKeepsTheCursorWorldPointStable() {
        MapRenderer renderer = new MapRenderer(new MapData());
        int width = 800, height = 600;
        int legendW = Math.min(220, Math.max(160, width / 6));
        int mapW = width - legendW;
        int mouseX = mapW / 2 + 90;
        int mouseY = height / 2;
        float old = renderer.getMapScale();
        float worldOff = (mouseX - mapW / 2f) / old;
        renderer.zoomAt(2f, mouseX, mouseY, width, height);
        float newOff = (mouseX - mapW / 2f) / renderer.getMapScale();
        assertEquals(worldOff, renderer.getPanWorldX() + newOff, 0.05f);
        assertEquals(0f, renderer.getPanWorldZ(), 0.05f);
        float panBefore = renderer.getPanWorldX();
        renderer.zoomAt(2f, mapW + 10, height / 2, width, height);
        assertEquals(panBefore, renderer.getPanWorldX(), 0.01f,
                "zoom over the legend should not recentre");
    }

    private record Region(boolean dark, boolean gold, boolean white, int contrast) {}

    private static Region sample(BufferedImage img, int x0, int y0, int x1, int y1) {
        int minLuma = 255 * 3, maxLuma = 0;
        boolean dark = false, gold = false, white = false;
        int w = img.getWidth(), h = img.getHeight();
        for (int y = Math.max(0, y0); y < Math.min(h, y1); y++) {
            for (int x = Math.max(0, x0); x < Math.min(w, x1); x++) {
                int rgb = img.getRGB(x, y) & 0xFFFFFF;
                int r = red(rgb), g = green(rgb), b = blue(rgb);
                int l = r + g + b;
                minLuma = Math.min(minLuma, l);
                maxLuma = Math.max(maxLuma, l);
                if (l < 90) dark = true;
                if (r > 200 && g > 180 && b < 90) gold = true;
                if (r > 230 && g > 230 && b > 230) white = true;
            }
        }
        return new Region(dark, gold, white, maxLuma - minLuma);
    }

    private static BlockAccessor oreColumn(int oreX, int oreZ, BlockType ore) {
        return (x, y, z) -> {
            if (y == 22 && x == oreX && z == oreZ) return ore;
            if (y == 40) return BlockType.GRASS;
            if (y < 40) return BlockType.DIRT;
            return BlockType.AIR;
        };
    }

    private static int red(int rgb) { return (rgb >> 16) & 0xFF; }
    private static int green(int rgb) { return (rgb >> 8) & 0xFF; }
    private static int blue(int rgb) { return rgb & 0xFF; }
    private static int luma(int rgb) { return red(rgb) + green(rgb) + blue(rgb); }
}
