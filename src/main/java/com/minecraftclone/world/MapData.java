package com.minecraftclone.world;

import java.util.*;

/**
 * Persistent map data tracking explored chunks and discovered ore vein locations.
 * Only records ore veins in chunks the player has visited.
 */
public class MapData {

    /** Set of explored chunk coordinates (as long key: chunkX | (chunkZ << 32)). */
    private final Set<Long> exploredChunks = new HashSet<>();

    /** Map of chunk coordinates to ore veins found in that chunk. */
    private final Map<Long, List<OreVeinRecord>> veinsByChunk = new HashMap<>();

    /**
     * Record of an ore vein's location and type.
     */
    public static class OreVeinRecord {
        public final int worldX, worldY, worldZ;
        public final BlockType oreType;

        public OreVeinRecord(int worldX, int worldY, int worldZ, BlockType oreType) {
            this.worldX = worldX;
            this.worldY = worldY;
            this.worldZ = worldZ;
            this.oreType = oreType;
        }
    }

    /**
     * Mark a chunk as explored and discover any ore veins it contains.
     * Called when the player enters a new chunk.
     */
    public void exploreChunk(int chunkX, int chunkZ, World world) {
        long chunkKey = encodeChunkKey(chunkX, chunkZ);
        if (exploredChunks.contains(chunkKey)) {
            return; // Already explored
        }

        exploredChunks.add(chunkKey);

        // Scan the chunk for ore veins (full-size ores only, not small ores)
        List<OreVeinRecord> veins = new ArrayList<>();
        int baseX = chunkX * 16;
        int baseZ = chunkZ * 16;

        for (int y = 5; y < 64; y++) {
            for (int x = baseX; x < baseX + 16; x++) {
                for (int z = baseZ; z < baseZ + 16; z++) {
                    BlockType block = world.getBlock(x, y, z);
                    if (isFullSizeOre(block)) {
                        veins.add(new OreVeinRecord(x, y, z, block));
                    }
                }
            }
        }

        if (!veins.isEmpty()) {
            veinsByChunk.put(chunkKey, veins);
        }
    }

    /**
     * Get all discovered ore veins in an explored chunk.
     */
    public List<OreVeinRecord> getVeinsInChunk(int chunkX, int chunkZ) {
        long key = encodeChunkKey(chunkX, chunkZ);
        return veinsByChunk.getOrDefault(key, Collections.emptyList());
    }

    /**
     * Check if a chunk has been explored.
     */
    public boolean isChunkExplored(int chunkX, int chunkZ) {
        return exploredChunks.contains(encodeChunkKey(chunkX, chunkZ));
    }

    /**
     * Get all explored chunks (for map rendering).
     */
    public Set<Long> getExploredChunks() {
        return new HashSet<>(exploredChunks);
    }

    /**
     * Get the player's current chunk coordinates from world position.
     */
    public static int[] getChunkCoords(float worldX, float worldZ) {
        return new int[]{
            Math.floorDiv((int) worldX, 16),
            Math.floorDiv((int) worldZ, 16)
        };
    }

    /**
     * Encode chunk coordinates into a single long key for hashing.
     */
    private static long encodeChunkKey(int chunkX, int chunkZ) {
        return ((long) chunkX & 0xFFFFFFFFL) | (((long) chunkZ & 0xFFFFFFFFL) << 32);
    }

    /**
     * Check if a block is a full-size GTNH ore (not small ore).
     */
    private static boolean isFullSizeOre(BlockType type) {
        // Check if it's a full-size ore (not a small ore)
        return type != null && type.solid && !type.toString().startsWith("SMALL_") &&
               (type.toString().endsWith("_ORE") || type == BlockType.COAL_ORE ||
                type == BlockType.IRON_ORE || type == BlockType.GOLD_ORE ||
                type == BlockType.DIAMOND_ORE);
    }
}
