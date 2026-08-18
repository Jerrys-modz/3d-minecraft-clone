package com.minecraftclone.engine;

import com.minecraftclone.world.BlockType;
import com.minecraftclone.world.MapData;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.*;

/**
 * Renders the map as a classic grid showing explored chunks and discovered ore veins.
 * Supports both mini-map (corner) and full-screen views.
 */
public class MapRenderer {

    private static final int CHUNK_PIXEL_SIZE = 8; // Each chunk = 8x8 pixels
    private static final int MINI_MAP_WIDTH = 160;
    private static final int MINI_MAP_HEIGHT = 160;

    // Ore vein colors (RGB)
    private static final Map<BlockType, Integer> ORE_COLORS = new HashMap<>();

    static {
        // Early-game ores
        ORE_COLORS.put(BlockType.COPPER_ORE, 0xB87333);     // Copper orange
        ORE_COLORS.put(BlockType.TIN_ORE, 0xC0C0C0);        // Silver gray
        ORE_COLORS.put(BlockType.BAUXITE_ORE, 0xE8E8E8);    // Light gray
        ORE_COLORS.put(BlockType.ZINC_ORE, 0xA8B8C8);       // Zinc blue-gray
        ORE_COLORS.put(BlockType.LEAD_ORE, 0x4A4A4A);       // Dark gray
        ORE_COLORS.put(BlockType.SILVER_ORE, 0xE0E0E0);     // Light silver

        // Mid-game ores
        ORE_COLORS.put(BlockType.NICKEL_ORE, 0xD0D0D0);     // Nickel light gray
        ORE_COLORS.put(BlockType.COBALT_ORE, 0x2E5090);     // Cobalt blue
        ORE_COLORS.put(BlockType.TUNGSTEN_ORE, 0x4A4A5A);   // Tungsten dark
        ORE_COLORS.put(BlockType.MOLYBDENUM_ORE, 0x5A5A6A); // Molybdenum gray-blue
        ORE_COLORS.put(BlockType.PLATINUM_ORE, 0xE8E0D0);   // Platinum cream

        // Advanced ores
        ORE_COLORS.put(BlockType.CHROMIUM_ORE, 0xC8B8B8);   // Chromium gray-pink
        ORE_COLORS.put(BlockType.MANGANESE_ORE, 0x6A5A5A);  // Manganese brown-gray
        ORE_COLORS.put(BlockType.VANADIUM_ORE, 0x7A8A8A);   // Vanadium blue-gray
        ORE_COLORS.put(BlockType.BERYLLIUM_ORE, 0xD0D0E8);  // Beryllium light blue
        ORE_COLORS.put(BlockType.TITANIUM_ORE, 0xB8C8C8);   // Titanium cyan-gray

        // Late-game ores
        ORE_COLORS.put(BlockType.URANIUM_ORE, 0x9AC870);    // Uranium green
        ORE_COLORS.put(BlockType.THORIUM_ORE, 0x7A7A8A);    // Thorium gray-blue
        ORE_COLORS.put(BlockType.PLUTONIUM_ORE, 0x6A8A5A);  // Plutonium green-gray
        ORE_COLORS.put(BlockType.IRIDIUM_ORE, 0xE8E8F0);    // Iridium light purple

        // Vanilla ores
        ORE_COLORS.put(BlockType.COAL_ORE, 0x3A3A3A);       // Coal black
        ORE_COLORS.put(BlockType.IRON_ORE, 0xD4A574);       // Iron brown
        ORE_COLORS.put(BlockType.GOLD_ORE, 0xFFD700);       // Gold yellow
        ORE_COLORS.put(BlockType.DIAMOND_ORE, 0x00FFFF);    // Diamond cyan
    }

    private final MapData mapData;
    private float mapScale = 1.0f; // For full-screen map zoom
    private float mapOffsetX = 0;  // Pan offset
    private float mapOffsetZ = 0;

    public MapRenderer(MapData mapData) {
        this.mapData = mapData;
    }

    /**
     * Render mini-map to display in HUD corner (160x160).
     * Player is always in center (marked with player indicator).
     */
    public BufferedImage renderMiniMap(float playerWorldX, float playerWorldZ) {
        BufferedImage img = new BufferedImage(MINI_MAP_WIDTH, MINI_MAP_HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();

        // Background
        g.setColor(new Color(50, 50, 50));
        g.fillRect(0, 0, MINI_MAP_WIDTH, MINI_MAP_HEIGHT);

        int[] playerChunk = MapData.getChunkCoords(playerWorldX, playerWorldZ);
        int playerChunkX = playerChunk[0];
        int playerChunkZ = playerChunk[1];

        // Render chunks around player (10 chunks radius)
        int radius = 10;
        for (int cx = playerChunkX - radius; cx <= playerChunkX + radius; cx++) {
            for (int cz = playerChunkZ - radius; cz <= playerChunkZ + radius; cz++) {
                int screenX = MINI_MAP_WIDTH / 2 + (cx - playerChunkX) * CHUNK_PIXEL_SIZE;
                int screenZ = MINI_MAP_HEIGHT / 2 + (cz - playerChunkZ) * CHUNK_PIXEL_SIZE;

                if (mapData.isChunkExplored(cx, cz)) {
                    // Explored chunk - light gray
                    g.setColor(new Color(100, 100, 100));
                    g.fillRect(screenX, screenZ, CHUNK_PIXEL_SIZE, CHUNK_PIXEL_SIZE);

                    // Draw ore dots for this chunk
                    for (MapData.OreVeinRecord vein : mapData.getVeinsInChunk(cx, cz)) {
                        int oreScreenX = screenX + ((vein.worldX & 0xF) * CHUNK_PIXEL_SIZE) / 16;
                        int oreScreenZ = screenZ + ((vein.worldZ & 0xF) * CHUNK_PIXEL_SIZE) / 16;
                        int color = ORE_COLORS.getOrDefault(vein.oreType, 0xFFFFFF);
                        g.setColor(new Color(color));
                        g.fillRect(oreScreenX, oreScreenZ, 1, 1);
                    }
                } else {
                    // Unexplored chunk - dark gray
                    g.setColor(new Color(30, 30, 30));
                    g.fillRect(screenX, screenZ, CHUNK_PIXEL_SIZE, CHUNK_PIXEL_SIZE);
                }

                // Chunk grid
                g.setColor(new Color(60, 60, 60));
                g.drawRect(screenX, screenZ, CHUNK_PIXEL_SIZE, CHUNK_PIXEL_SIZE);
            }
        }

        // Player marker (center)
        g.setColor(new Color(0, 255, 0));
        int centerX = MINI_MAP_WIDTH / 2;
        int centerZ = MINI_MAP_HEIGHT / 2;
        g.fillOval(centerX - 2, centerZ - 2, 5, 5);

        g.dispose();
        return img;
    }

    /**
     * Render full-screen map with zoom and pan support.
     */
    public BufferedImage renderFullMap(int width, int height, float playerWorldX, float playerWorldZ) {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();

        // Background
        g.setColor(new Color(30, 30, 30));
        g.fillRect(0, 0, width, height);

        int[] playerChunk = MapData.getChunkCoords(playerWorldX, playerWorldZ);
        int playerChunkX = playerChunk[0];
        int playerChunkZ = playerChunk[1];

        int chunkPixelSize = (int) (CHUNK_PIXEL_SIZE * mapScale);
        int centerX = width / 2;
        int centerZ = height / 2;

        // Render chunks around player
        int radius = (width / chunkPixelSize) / 2 + 5;
        for (int cx = playerChunkX - radius; cx <= playerChunkX + radius; cx++) {
            for (int cz = playerChunkZ - radius; cz <= playerChunkZ + radius; cz++) {
                int screenX = centerX + (int) (mapOffsetX + (cx - playerChunkX) * chunkPixelSize);
                int screenZ = centerZ + (int) (mapOffsetZ + (cz - playerChunkZ) * chunkPixelSize);

                if (mapData.isChunkExplored(cx, cz)) {
                    g.setColor(new Color(100, 100, 100));
                    g.fillRect(screenX, screenZ, chunkPixelSize, chunkPixelSize);

                    // Draw ore dots
                    if (chunkPixelSize > 4) {
                        for (MapData.OreVeinRecord vein : mapData.getVeinsInChunk(cx, cz)) {
                            int oreScreenX = screenX + ((vein.worldX & 0xF) * chunkPixelSize) / 16;
                            int oreScreenZ = screenZ + ((vein.worldZ & 0xF) * chunkPixelSize) / 16;
                            int color = ORE_COLORS.getOrDefault(vein.oreType, 0xFFFFFF);
                            g.setColor(new Color(color));
                            g.fillRect(oreScreenX, oreScreenZ, Math.max(1, chunkPixelSize / 8), Math.max(1, chunkPixelSize / 8));
                        }
                    }
                } else {
                    g.setColor(new Color(30, 30, 30));
                    g.fillRect(screenX, screenZ, chunkPixelSize, chunkPixelSize);
                }

                g.setColor(new Color(60, 60, 60));
                g.drawRect(screenX, screenZ, chunkPixelSize, chunkPixelSize);
            }
        }

        // Player marker
        g.setColor(new Color(0, 255, 0));
        g.fillOval(centerX - 3, centerZ - 3, 7, 7);

        // Crosshair in center
        g.setColor(new Color(255, 255, 0));
        g.drawLine(centerX - 10, centerZ, centerX + 10, centerZ);
        g.drawLine(centerX, centerZ - 10, centerX, centerZ + 10);

        g.dispose();
        return img;
    }

    /**
     * Zoom the full-screen map (>1.0 = zoom in).
     */
    public void zoom(float factor) {
        mapScale = Math.max(0.5f, Math.min(4.0f, mapScale * factor));
    }

    /**
     * Pan the full-screen map.
     */
    public void pan(float deltaX, float deltaZ) {
        mapOffsetX += deltaX;
        mapOffsetZ += deltaZ;
    }

    /**
     * Reset pan and zoom.
     */
    public void resetView() {
        mapScale = 1.0f;
        mapOffsetX = 0;
        mapOffsetZ = 0;
    }
}
