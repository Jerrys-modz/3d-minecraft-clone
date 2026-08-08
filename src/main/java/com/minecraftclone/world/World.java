package com.minecraftclone.world;

import com.minecraftclone.engine.Shader;
import com.minecraftclone.engine.graphics.TextureAtlas;
import com.minecraftclone.world.gen.TerrainGenerator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Owns all loaded chunks: streaming (load/unload around the player), meshing, block access and raycasting. */
public class World implements BlockAccessor {

    private final Map<ChunkPos, Chunk> chunks = new HashMap<>();
    private final TerrainGenerator generator;
    private final TextureAtlas atlas;

    private int renderDistance = 6;
    private static final int MAX_GENERATE_PER_TICK = 4;
    private static final int MAX_MESH_PER_TICK = 4;

    public World(long seed, TextureAtlas atlas) {
        this.generator = new TerrainGenerator(seed);
        this.atlas = atlas;
    }

    public void setRenderDistance(int renderDistance) {
        this.renderDistance = renderDistance;
    }

    public int getRenderDistance() {
        return renderDistance;
    }

    public static int worldToChunk(int worldCoord) {
        return Math.floorDiv(worldCoord, Chunk.SIZE);
    }

    private Chunk getChunk(int chunkX, int chunkZ) {
        return chunks.get(new ChunkPos(chunkX, chunkZ));
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
        chunk.setLocal(lx, worldY, lz, type);

        // If we touched a boundary column, the neighbor chunk's culled faces
        // there may now need to change too.
        if (lx == 0) markNeighborDirty(cx - 1, cz);
        if (lx == Chunk.SIZE - 1) markNeighborDirty(cx + 1, cz);
        if (lz == 0) markNeighborDirty(cx, cz - 1);
        if (lz == Chunk.SIZE - 1) markNeighborDirty(cx, cz + 1);
    }

    private void markNeighborDirty(int cx, int cz) {
        Chunk c = getChunk(cx, cz);
        if (c != null) c.markDirty();
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
                ChunkPos p = new ChunkPos(pcx + dx, pcz + dz);
                if (!chunks.containsKey(p)) {
                    Chunk chunk = new Chunk(p);
                    chunks.put(p, chunk);
                    generator.generate(chunk);
                    markNeighborDirty(p.x() - 1, p.z());
                    markNeighborDirty(p.x() + 1, p.z());
                    markNeighborDirty(p.x(), p.z() - 1);
                    markNeighborDirty(p.x(), p.z() + 1);
                    generated++;
                }
            }
        }

        // Unload far chunks.
        int unloadRadius = renderDistance + 2;
        List<ChunkPos> toRemove = new ArrayList<>();
        for (ChunkPos p : chunks.keySet()) {
            if (p.distanceSq(pcx, pcz) > (double) unloadRadius * unloadRadius) {
                toRemove.add(p);
            }
        }
        for (ChunkPos p : toRemove) {
            Chunk c = chunks.remove(p);
            if (c != null) c.destroy();
        }

        // Remesh a limited number of dirty chunks per tick, nearest first.
        List<Chunk> dirty = new ArrayList<>();
        for (Chunk c : chunks.values()) {
            if (c.isDirty()) dirty.add(c);
        }
        dirty.sort((a, b) -> Double.compare(a.getPos().distanceSq(pcx, pcz), b.getPos().distanceSq(pcx, pcz)));
        for (int i = 0; i < Math.min(MAX_MESH_PER_TICK, dirty.size()); i++) {
            dirty.get(i).rebuildMesh(this, atlas);
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

    public void destroy() {
        for (Chunk c : chunks.values()) {
            c.destroy();
        }
        chunks.clear();
    }
}
