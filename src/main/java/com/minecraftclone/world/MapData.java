package com.minecraftclone.world;

import java.io.*;
import java.nio.file.*;
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
        // Group into 4x4 column cells to reduce density; keep at most one vein per ore type per cell
        List<OreVeinRecord> veins = new ArrayList<>();
        int baseX = chunkX * 16;
        int baseZ = chunkZ * 16;

        // 4x4 cells in a 16x16 chunk = 4x4 grid of cells
        for (int cellX = 0; cellX < 4; cellX++) {
            for (int cellZ = 0; cellZ < 4; cellZ++) {
                int minX = baseX + cellX * 4;
                int minZ = baseZ + cellZ * 4;
                Map<BlockType, OreVeinRecord> cellVeins = new HashMap<>();

                for (int y = 5; y < 64; y++) {
                    for (int x = minX; x < minX + 4; x++) {
                        for (int z = minZ; z < minZ + 4; z++) {
                            BlockType block = world.getBlock(x, y, z);
                            if (isFullSizeOre(block) && !cellVeins.containsKey(block)) {
                                cellVeins.put(block, new OreVeinRecord(x, y, z, block));
                            }
                        }
                    }
                }
                veins.addAll(cellVeins.values());
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
            Math.floorDiv((int) Math.floor(worldX), 16),
            Math.floorDiv((int) Math.floor(worldZ), 16)
        };
    }

    /**
     * Encode chunk coordinates into a single long key for hashing.
     */
    private static long encodeChunkKey(int chunkX, int chunkZ) {
        return ((long) chunkX & 0xFFFFFFFFL) | (((long) chunkZ & 0xFFFFFFFFL) << 32);
    }

    // ── Persistence ───────────────────────────────────────────────────────────

    private static final int SAVE_VERSION = 1;

    /**
     * Persist explored-chunk and vein data to disk. Called when switching
     * dimensions or returning to the main menu so exploration is not lost.
     *
     * @param file target file path (created along with any missing parent dirs)
     */
    public void saveTo(Path file) {
        try {
            Path parent = file.getParent();
            if (parent != null) Files.createDirectories(parent);
            try (DataOutputStream out = new DataOutputStream(
                    new BufferedOutputStream(Files.newOutputStream(file)))) {
                out.writeInt(SAVE_VERSION);

                // Explored chunk keys
                out.writeInt(exploredChunks.size());
                for (long key : exploredChunks) {
                    out.writeLong(key);
                }

                // Vein records per chunk
                out.writeInt(veinsByChunk.size());
                for (Map.Entry<Long, List<OreVeinRecord>> entry : veinsByChunk.entrySet()) {
                    out.writeLong(entry.getKey());
                    List<OreVeinRecord> veins = entry.getValue();
                    out.writeInt(veins.size());
                    for (OreVeinRecord vein : veins) {
                        out.writeInt(vein.worldX);
                        out.writeInt(vein.worldY);
                        out.writeInt(vein.worldZ);
                        out.writeUTF(vein.oreType.name());
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("Failed to save map data to " + file + ": " + e.getMessage());
        }
    }

    /**
     * Load previously saved exploration data from disk. Ignores the file
     * gracefully if it does not exist (new world) or its format is unknown.
     *
     * @param file the map data file to load
     */
    public void loadFrom(Path file) {
        if (!Files.exists(file)) return;
        try (DataInputStream in = new DataInputStream(
                new BufferedInputStream(Files.newInputStream(file)))) {
            int version = in.readInt();
            if (version != SAVE_VERSION) {
                System.err.println("Unknown map data version " + version + ", skipping.");
                return;
            }

            int chunkCount = in.readInt();
            for (int i = 0; i < chunkCount; i++) {
                exploredChunks.add(in.readLong());
            }

            int veinChunkCount = in.readInt();
            for (int i = 0; i < veinChunkCount; i++) {
                long key = in.readLong();
                int veinCount = in.readInt();
                if (veinCount < 0 || veinCount > 100_000) {
                    System.err.println("Corrupt map data: invalid vein count " + veinCount + ", skipping.");
                    return;
                }
                List<OreVeinRecord> veins = new ArrayList<>(veinCount);
                for (int j = 0; j < veinCount; j++) {
                    int x = in.readInt();
                    int y = in.readInt();
                    int z = in.readInt();
                    String typeName = in.readUTF();
                    try {
                        BlockType type = BlockType.valueOf(typeName);
                        veins.add(new OreVeinRecord(x, y, z, type));
                    } catch (IllegalArgumentException ignored) {
                        // Block type removed/renamed; skip the vein.
                    }
                }
                if (!veins.isEmpty()) {
                    veinsByChunk.put(key, veins);
                }
            }
        } catch (IOException e) {
            System.err.println("Failed to load map data from " + file + ": " + e.getMessage());
        }
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
