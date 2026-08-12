package com.minecraftclone.world;

import com.minecraftclone.engine.Shader;
import com.minecraftclone.engine.graphics.TextureAtlas;
import com.minecraftclone.world.gen.TerrainGenerator;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Owns all loaded chunks: streaming (load/unload around the player), meshing,
 * block access and raycasting.
 * <p>
 * The world itself is unbounded - chunks are generated on demand from the
 * seed as the player approaches and released again once they're far enough
 * away, so memory stays bounded to roughly {@code renderDistance} regardless
 * of how far the player has cumulatively explored. Any chunk the player has
 * edited is persisted to disk on unload (see {@link ChunkStorage}) and
 * reloaded from there instead of being regenerated, so edits are never lost.
 */
public class World implements BlockAccessor {

    // Chunks are keyed by a packed long (chunkX in the high 32 bits, chunkZ in the
    // low 32) rather than a ChunkPos record, so the hot getBlock/setBlock lookups
    // don't allocate a key object on every call.
    private final Map<Long, Chunk> chunks = new HashMap<>();
    private final TerrainGenerator generator;
    private final TextureAtlas atlas;
    private final ChunkStorage storage;

    private int renderDistance = 6;
    private boolean leavesTransparent = false;
    private static final int MAX_GENERATE_PER_TICK = 4;
    private static final int MAX_MESH_PER_TICK = 4;

    public World(long seed, TextureAtlas atlas, Path saveDir) {
        this.generator = new TerrainGenerator(seed);
        this.atlas = atlas;
        this.storage = new ChunkStorage(saveDir);
    }

    /** Packs chunk-grid coordinates into a single key. {@code chunkZ} is masked so negative coordinates stay unique. */
    private static long key(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
    }

    public void setRenderDistance(int renderDistance) {
        this.renderDistance = renderDistance;
    }

    public int getRenderDistance() {
        return renderDistance;
    }

    public boolean isLeavesTransparent() {
        return leavesTransparent;
    }

    /**
     * Turns the "see-through leaves" setting on/off. Every loaded chunk is
     * flagged dirty so its mesh is rebuilt with the new leaf culling/tile on
     * the next few {@link #update} ticks (staggered per tick, like any other
     * remesh, so the change streams in rather than freezing a frame).
     */
    public void setLeavesTransparent(boolean value) {
        if (this.leavesTransparent == value) return;
        this.leavesTransparent = value;
        for (Chunk c : chunks.values()) {
            c.markDirty();
        }
    }

    public static int worldToChunk(int worldCoord) {
        return Math.floorDiv(worldCoord, Chunk.SIZE);
    }

    private Chunk getChunk(int chunkX, int chunkZ) {
        return chunks.get(key(chunkX, chunkZ));
    }

    @Override
    public BlockType getBlock(int worldX, int worldY, int worldZ) {
        if (worldY < 0 || worldY >= Chunk.HEIGHT) return BlockType.AIR;
        Chunk chunk = getChunk(worldToChunk(worldX), worldToChunk(worldZ));
        if (chunk == null) return BlockType.AIR;
        int lx = Math.floorMod(worldX, Chunk.SIZE);
        int lz = Math.floorMod(worldZ, Chunk.SIZE);
        return chunk.getLocal(lx, worldY, lz);
    }

    public void setBlock(int worldX, int worldY, int worldZ, BlockType type) {
        if (worldY < 0 || worldY >= Chunk.HEIGHT) return;
        int cx = worldToChunk(worldX);
        int cz = worldToChunk(worldZ);
        Chunk chunk = getChunk(cx, cz);
        if (chunk == null) return;
        int lx = Math.floorMod(worldX, Chunk.SIZE);
        int lz = Math.floorMod(worldZ, Chunk.SIZE);
        BlockType old = chunk.getLocal(lx, worldY, lz);
        chunk.setLocalFromPlayer(lx, worldY, lz, type);

        // If we touched a boundary column, the neighbor chunk's culled faces
        // there may now need to change too.
        if (lx == 0) markNeighborDirty(cx - 1, cz);
        if (lx == Chunk.SIZE - 1) markNeighborDirty(cx + 1, cz);
        if (lz == 0) markNeighborDirty(cx, cz - 1);
        if (lz == Chunk.SIZE - 1) markNeighborDirty(cx, cz + 1);

        // Placing/removing a light source (e.g. a torch) can change the baked glow
        // in every chunk within its radius, not just literal boundary columns - the
        // light radius is well under a chunk's width, so the immediate 3x3 chunk
        // neighborhood is always enough to cover it.
        if (old.isLightSource() || type.isLightSource()) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    markNeighborDirty(cx + dx, cz + dz);
                }
            }
        }
    }

    private void markNeighborDirty(int cx, int cz) {
        Chunk c = getChunk(cx, cz);
        if (c != null) c.markDirty();
    }

    /**
     * Every light-emitting block within reach of chunk {@code center}, as world-space
     * {wx, wy, wz, lightLevel} - gathered from the chunk itself plus its 8 immediate
     * neighbors (the light falloff radius is well under a chunk's width, so nothing
     * farther away can reach in). Passed to {@link Chunk#rebuildMesh} to bake local glow.
     */
    private List<int[]> collectNearbyLights(ChunkPos center) {
        List<int[]> result = new ArrayList<>();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                Chunk c = getChunk(center.x() + dx, center.z() + dz);
                if (c == null) continue;
                int ox = c.getOriginX();
                int oz = c.getOriginZ();
                for (int[] local : c.getLocalLightSources()) {
                    result.add(new int[]{ox + local[0], local[1], oz + local[2], local[3]});
                }
            }
        }
        return result;
    }

    /** Height of the highest non-air block at the given world column (or SEA_LEVEL if the chunk isn't loaded). */
    public int getSurfaceHeight(int worldX, int worldZ) {
        for (int y = Chunk.HEIGHT - 1; y >= 0; y--) {
            if (getBlock(worldX, y, worldZ) != BlockType.AIR) {
                return y;
            }
        }
        return TerrainGenerator.SEA_LEVEL;
    }

    /**
     * Streams chunks around the given world position: generates newly-visible
     * chunks, unloads far-away ones, and rebuilds a limited number of dirty
     * meshes. Call once per frame from the main (GL) thread.
     */
    public void update(float playerWorldX, float playerWorldZ) {
        int pcx = worldToChunk((int) Math.floor(playerWorldX));
        int pcz = worldToChunk((int) Math.floor(playerWorldZ));

        // Load / generate.
        int generated = 0;
        for (int dx = -renderDistance; dx <= renderDistance && generated < MAX_GENERATE_PER_TICK; dx++) {
            for (int dz = -renderDistance; dz <= renderDistance && generated < MAX_GENERATE_PER_TICK; dz++) {
                if (dx * dx + dz * dz > renderDistance * renderDistance) continue;
                int cx = pcx + dx, cz = pcz + dz;
                if (!chunks.containsKey(key(cx, cz))) {
                    Chunk chunk = new Chunk(new ChunkPos(cx, cz));
                    chunks.put(key(cx, cz), chunk);
                    if (storage.hasSavedChunk(chunk.getPos())) {
                        // A previously-edited chunk: restore the player's changes
                        // instead of regenerating pristine terrain.
                        storage.load(chunk);
                    } else {
                        generator.generate(chunk);
                    }
                    markNeighborDirty(cx - 1, cz);
                    markNeighborDirty(cx + 1, cz);
                    markNeighborDirty(cx, cz - 1);
                    markNeighborDirty(cx, cz + 1);
                    generated++;
                }
            }
        }

        // Unload far chunks.
        int unloadRadius = renderDistance + 2;
        List<Chunk> toRemove = new ArrayList<>();
        for (Chunk c : chunks.values()) {
            if (c.getPos().distanceSq(pcx, pcz) > (double) unloadRadius * unloadRadius) {
                toRemove.add(c);
            }
        }
        for (Chunk c : toRemove) {
            chunks.remove(key(c.getPos().x(), c.getPos().z()));
            if (c.isModifiedByPlayer()) {
                storage.save(c);
            }
            c.destroy();
        }

        // Remesh a limited number of dirty chunks per tick, nearest first.
        List<Chunk> dirty = new ArrayList<>();
        for (Chunk c : chunks.values()) {
            if (c.isDirty()) dirty.add(c);
        }
        dirty.sort((a, b) -> Double.compare(a.getPos().distanceSq(pcx, pcz), b.getPos().distanceSq(pcx, pcz)));
        for (int i = 0; i < Math.min(MAX_MESH_PER_TICK, dirty.size()); i++) {
            Chunk c = dirty.get(i);
            c.rebuildMesh(this, atlas, collectNearbyLights(c.getPos()), leavesTransparent);
        }
    }

    public void render(Shader shader) {
        for (Chunk chunk : chunks.values()) {
            chunk.render();
        }
    }

    public int getLoadedChunkCount() {
        return chunks.size();
    }

    public boolean isFullyGenerated(int worldX, int worldZ) {
        Chunk c = getChunk(worldToChunk(worldX), worldToChunk(worldZ));
        return c != null && c.isGenerated();
    }

    /** Persists every currently-loaded, player-modified chunk. Call before exiting so edits near the player aren't lost. */
    public void saveAllModified() {
        int saved = 0;
        for (Chunk c : chunks.values()) {
            if (c.isModifiedByPlayer()) {
                storage.save(c);
                saved++;
            }
        }
        if (saved > 0) {
            System.out.println("Saved " + saved + " modified chunk(s).");
        }
    }

    public void destroy() {
        for (Chunk c : chunks.values()) {
            c.destroy();
        }
        chunks.clear();
    }
}
