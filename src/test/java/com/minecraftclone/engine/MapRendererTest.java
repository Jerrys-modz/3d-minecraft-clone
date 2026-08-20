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
        List<MapRenderer.MixWaypoint> wps = MapRenderer.clusterMixWaypoints(data);
        Set<String> names = wps.stream().map(w -> w.mixName).collect(Collectors.toSet());
        assertEquals(Set.of("Copper Mix", "Magnetite Mix"), names);
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
        assertEquals(64, MapRenderer.MINI_MAP_SIZE / MapRenderer.MINI_PIXELS_PER_BLOCK,
                "mini-map should show ~64 blocks (~4 chunks), not 20 gray chunks");

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
