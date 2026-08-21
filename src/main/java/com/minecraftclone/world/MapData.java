package com.minecraftclone.world;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * Persistent map data tracking explored chunks, their top-down surface
 * (block + height per column, for a JourneyMap-style terrain view) and
 * discovered ore vein locations. Chunks paint onto the map as they load
 * within render distance (see {@link World#mapLoadedChunks}). Ore-mix
 * waypoints are GTNH VisualProspecting-style: walking a chunk is not
 * enough — the player has to look at or mine the ore (or its small-ore
 * surface signal) before it shows on the map.
 */
public class MapData {

    private static final int COLS = Chunk.SIZE * Chunk.SIZE;

    /** Set of explored chunk coordinates (as long key: chunkX | (chunkZ << 32)). */
    private final Set<Long> exploredChunks = new HashSet<>();

    /** Map of chunk coordinates to ore veins found in that chunk. */
    private final Map<Long, List<OreVeinRecord>> veinsByChunk = new HashMap<>();

    /** 16×16 surface block ids (by {@link BlockType#id}) per explored chunk. */
    private final Map<Long, short[]> surfaceBlocks = new HashMap<>();

    /** 16×16 surface heights (0–255) per explored chunk. */
    private final Map<Long, byte[]> surfaceHeights = new HashMap<>();

    /** Bumped on every explore / surface refresh so the renderer can drop its cache. */
    private int revision;

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

    /** Cache-busting counter; increments whenever exploration data changes. */
    public int getRevision() {
        return revision;
    }

    /**
     * Mark a chunk as explored and sample its surface when none exists yet.
     * The surface is sampled once per chunk: chunks from a v1 save (explored,
     * but with no surface) are sampled on their next visit, so an older world
     * picks up terrain colours. Later edits to an already-sampled chunk are
     * not repainted. Ore veins are <em>not</em> scanned here — see
     * {@link #discoverOre}.
     */
    public void exploreChunk(int chunkX, int chunkZ, BlockAccessor world) {
        if (looksUnloaded(world, chunkX, chunkZ)) {
            return;
        }

        long chunkKey = encodeChunkKey(chunkX, chunkZ);
        exploredChunks.add(chunkKey);

        // v1 saves are already "explored" but have no surface — sample those
        // (and any brand-new chunk) once. Don't resample every call.
        if (!hasSurface(chunkX, chunkZ)) {
            sampleSurface(chunkX, chunkZ, world);
            revision++;
        }
    }

    /**
     * Sample a chunk that is already generated in memory. Skips the all-air
     * probe (the world would not hand us an ungenerated chunk) and reads
     * blocks from the chunk directly so sky columns don't walk 256 air cells.
     * Ore veins are not scanned; the player has to find them.
     */
    public void exploreGeneratedChunk(Chunk chunk) {
        if (chunk == null || !chunk.isGenerated()) return;
        int chunkX = chunk.getPos().x();
        int chunkZ = chunk.getPos().z();
        long chunkKey = encodeChunkKey(chunkX, chunkZ);
        exploredChunks.add(chunkKey);

        if (!hasSurface(chunkX, chunkZ)) {
            sampleSurface(chunk);
            revision++;
        }
    }

    /**
     * Record a mix waypoint because the player found this ore — looked at it
     * or mined it — the way VisualProspecting only shows a mix after you
     * prospect it. Small ores count as their full-size mix. One of each ore
     * type per chunk. Returns true when this is a new find.
     */
    public boolean discoverOre(int x, int y, int z, BlockType type) {
        BlockType ore = prospectableOre(type);
        if (ore == null) return false;
        int cx = Math.floorDiv(x, Chunk.SIZE);
        int cz = Math.floorDiv(z, Chunk.SIZE);
        long key = encodeChunkKey(cx, cz);
        List<OreVeinRecord> veins = veinsByChunk.get(key);
        if (veins != null) {
            for (OreVeinRecord v : veins) {
                if (v.oreType == ore) return false;
            }
        } else {
            veins = new ArrayList<>();
            veinsByChunk.put(key, veins);
        }
        veins.add(new OreVeinRecord(x, y, z, ore));
        revision++;
        return true;
    }

    /**
     * The full-size ore a found block should prospect as, or {@code null} if
     * it isn't an ore. {@code SMALL_COPPER_ORE} becomes {@code COPPER_ORE} so
     * a surface small-ore (GTNH's prospecting signal) reveals the mix.
     */
    public static BlockType prospectableOre(BlockType type) {
        if (isFullSizeOre(type)) return type;
        if (type == null) return null;
        String n = type.name();
        if (n.startsWith("SMALL_") && n.endsWith("_ORE")) {
            try {
                BlockType full = BlockType.valueOf(n.substring("SMALL_".length()));
                if (isFullSizeOre(full)) return full;
            } catch (IllegalArgumentException ignored) {
                // SMALL_* with no matching full ore.
            }
        }
        return null;
    }

    private void sampleSurface(Chunk chunk) {
        long key = encodeChunkKey(chunk.getPos().x(), chunk.getPos().z());
        short[] blocks = new short[COLS];
        byte[] heights = new byte[COLS];
        int yStart = chunk.getHighestNonAirY();
        for (int lz = 0; lz < Chunk.SIZE; lz++) {
            for (int lx = 0; lx < Chunk.SIZE; lx++) {
                int y = Math.max(yStart, 0);
                BlockType b = yStart < 0 ? BlockType.AIR : chunk.getLocal(lx, y, lz);
                while (y > 0 && isMapDecoration(b)) {
                    y--;
                    b = chunk.getLocal(lx, y, lz);
                }
                int idx = lz * Chunk.SIZE + lx;
                blocks[idx] = b.id;
                heights[idx] = (byte) y;
            }
        }
        surfaceBlocks.put(key, blocks);
        surfaceHeights.put(key, heights);
    }

    /**
     * Unloaded chunks (and End void) are all air — skip them so we don't
     * freeze an empty surface before terrain has generated.
     */
    static boolean looksUnloaded(BlockAccessor world, int chunkX, int chunkZ) {
        int x = chunkX * Chunk.SIZE + Chunk.SIZE / 2;
        int z = chunkZ * Chunk.SIZE + Chunk.SIZE / 2;
        for (int y = 0; y < Chunk.HEIGHT; y++) {
            if (world.getBlock(x, y, z) != BlockType.AIR) return false;
        }
        return true;
    }

    /**
     * Sample the topmost "map-worthy" block of every column in the chunk.
     * Cross-shaped plants (grass, flowers) are skipped so the ground/water
     * under them is what actually paints the map.
     */
    private void sampleSurface(int chunkX, int chunkZ, BlockAccessor world) {
        long key = encodeChunkKey(chunkX, chunkZ);
        short[] blocks = new short[COLS];
        byte[] heights = new byte[COLS];
        int baseX = chunkX * Chunk.SIZE;
        int baseZ = chunkZ * Chunk.SIZE;
        for (int lz = 0; lz < Chunk.SIZE; lz++) {
            for (int lx = 0; lx < Chunk.SIZE; lx++) {
                int wx = baseX + lx;
                int wz = baseZ + lz;
                int y = Chunk.HEIGHT - 1;
                BlockType b = world.getBlock(wx, y, wz);
                while (y > 0 && isMapDecoration(b)) {
                    y--;
                    b = world.getBlock(wx, y, wz);
                }
                int idx = lz * Chunk.SIZE + lx;
                blocks[idx] = b.id;
                heights[idx] = (byte) y;
            }
        }
        surfaceBlocks.put(key, blocks);
        surfaceHeights.put(key, heights);
    }

    /**
     * Plants and empty air shouldn't paint the map — skip down to the ground
     * (or water) underneath. Leaves/logs stay so forests read as tree cover.
     */
    static boolean isMapDecoration(BlockType b) {
        return b == null || b == BlockType.AIR || b.cross;
    }

    /**
     * Get all discovered ore veins in an explored chunk.
     */
    public List<OreVeinRecord> getVeinsInChunk(int chunkX, int chunkZ) {
        long key = encodeChunkKey(chunkX, chunkZ);
        return veinsByChunk.getOrDefault(key, Collections.emptyList());
    }

    /** Flattened list of every discovered vein, for mix-waypoint clustering. */
    public List<OreVeinRecord> allVeins() {
        if (veinsByChunk.isEmpty()) return Collections.emptyList();
        List<OreVeinRecord> all = new ArrayList<>();
        for (List<OreVeinRecord> list : veinsByChunk.values()) {
            all.addAll(list);
        }
        return all;
    }

    /**
     * Check if a chunk has been explored.
     */
    public boolean isChunkExplored(int chunkX, int chunkZ) {
        return exploredChunks.contains(encodeChunkKey(chunkX, chunkZ));
    }

    /** True when this chunk has a sampled surface (v2 saves / re-explored v1). */
    public boolean hasSurface(int chunkX, int chunkZ) {
        return surfaceBlocks.containsKey(encodeChunkKey(chunkX, chunkZ));
    }

    /**
     * Top-down block at a world column, or {@code null} if that chunk has no
     * surface sample yet.
     */
    public BlockType getSurfaceBlock(int worldX, int worldZ) {
        int cx = Math.floorDiv(worldX, Chunk.SIZE);
        int cz = Math.floorDiv(worldZ, Chunk.SIZE);
        short[] blocks = surfaceBlocks.get(encodeChunkKey(cx, cz));
        if (blocks == null) return null;
        int lx = Math.floorMod(worldX, Chunk.SIZE);
        int lz = Math.floorMod(worldZ, Chunk.SIZE);
        return BlockType.byId(blocks[lz * Chunk.SIZE + lx] & 0xFFFF);
    }

    /**
     * Surface height at a world column, or {@code -1} if unsampled.
     */
    public int getSurfaceY(int worldX, int worldZ) {
        int cx = Math.floorDiv(worldX, Chunk.SIZE);
        int cz = Math.floorDiv(worldZ, Chunk.SIZE);
        byte[] heights = surfaceHeights.get(encodeChunkKey(cx, cz));
        if (heights == null) return -1;
        int lx = Math.floorMod(worldX, Chunk.SIZE);
        int lz = Math.floorMod(worldZ, Chunk.SIZE);
        return heights[lz * Chunk.SIZE + lx] & 0xFF;
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
            Math.floorDiv((int) Math.floor(worldX), Chunk.SIZE),
            Math.floorDiv((int) Math.floor(worldZ), Chunk.SIZE)
        };
    }

    /**
     * Encode chunk coordinates into a single long key for hashing.
     */
    public static long encodeChunkKey(int chunkX, int chunkZ) {
        return ((long) chunkX & 0xFFFFFFFFL) | (((long) chunkZ & 0xFFFFFFFFL) << 32);
    }

    public static int decodeChunkX(long key) {
        return (int) key;
    }

    public static int decodeChunkZ(long key) {
        return (int) (key >>> 32);
    }

    // ── Persistence ───────────────────────────────────────────────────────────

    private static final int SAVE_VERSION = 2;

    /**
     * Persist explored-chunk, surface and vein data to disk. Called when
     * switching dimensions or returning to the main menu so exploration is
     * not lost.
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

                // v2: surface samples
                out.writeInt(surfaceBlocks.size());
                for (Map.Entry<Long, short[]> entry : surfaceBlocks.entrySet()) {
                    long key = entry.getKey();
                    out.writeLong(key);
                    short[] blocks = entry.getValue();
                    byte[] heights = surfaceHeights.getOrDefault(key, new byte[COLS]);
                    for (int i = 0; i < COLS; i++) {
                        out.writeShort(blocks[i]);
                    }
                    out.write(heights, 0, COLS);
                }
            }
        } catch (IOException e) {
            System.err.println("Failed to save map data to " + file + ": " + e.getMessage());
        }
    }

    /**
     * Load previously saved exploration data from disk. Ignores the file
     * gracefully if it does not exist (new world) or its format is unknown.
     * Version 1 saves (chunks + veins, no terrain) still load; terrain fills
     * in as those chunks are re-entered.
     *
     * @param file the map data file to load
     */
    public void loadFrom(Path file) {
        if (!Files.exists(file)) return;
        try (DataInputStream in = new DataInputStream(
                new BufferedInputStream(Files.newInputStream(file)))) {
            int version = in.readInt();
            if (version < 1 || version > SAVE_VERSION) {
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

            if (version >= 2) {
                int surfaceCount = in.readInt();
                if (surfaceCount < 0 || surfaceCount > 1_000_000) {
                    System.err.println("Corrupt map data: invalid surface count " + surfaceCount + ", skipping.");
                    return;
                }
                for (int i = 0; i < surfaceCount; i++) {
                    long key = in.readLong();
                    short[] blocks = new short[COLS];
                    for (int j = 0; j < COLS; j++) {
                        blocks[j] = in.readShort();
                    }
                    byte[] heights = in.readNBytes(COLS);
                    if (heights.length < COLS) {
                        byte[] padded = new byte[COLS];
                        System.arraycopy(heights, 0, padded, 0, heights.length);
                        heights = padded;
                    }
                    surfaceBlocks.put(key, blocks);
                    surfaceHeights.put(key, heights);
                }
            }
            revision++;
        } catch (IOException e) {
            System.err.println("Failed to load map data from " + file + ": " + e.getMessage());
        }
    }

    /**
     * Check if a block is a full-size GTNH / vanilla ore (not a small ore).
     */
    public static boolean isFullSizeOre(BlockType type) {
        return type != null && type.solid && !type.name().startsWith("SMALL_") &&
               type.name().endsWith("_ORE");
    }
}
