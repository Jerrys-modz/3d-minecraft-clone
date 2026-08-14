package com.minecraftclone.world;

import com.minecraftclone.engine.Shader;
import com.minecraftclone.engine.graphics.TextureAtlas;
import com.minecraftclone.player.Inventory;
import com.minecraftclone.world.gen.TerrainGenerator;
import com.minecraftclone.world.gen.WorldGenSettings;
import org.joml.Vector3f;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
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

    // Dropped item entities (from breaking blocks / death). Transient - not saved.
    private final List<ItemEntity> items = new ArrayList<>();
    private static final float ITEM_GRAVITY = 24f;
    private static final float ITEM_TERMINAL_VELOCITY = -40f;
    private static final float ITEM_HALF_HEIGHT = 0.15f;
    private static final float PICKUP_RADIUS = 1.5f;

    public World(long seed, WorldGenSettings genSettings, TextureAtlas atlas, Path saveDir) {
        this.generator = new TerrainGenerator(seed, genSettings);
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

        // Opening a cell next to static (world-gen) water/lava doesn't make it flow
        // on its own: FluidSim only tracks *_SOURCE/*_FLOW blocks, and treating an
        // entire static lake/ocean as "live" would mean rescanning its whole interior
        // every tick just for nothing to happen. Instead, promote only the specific
        // boundary block(s) that actually end up touching the new opening into a real,
        // tracked source, right where the flow is needed - the natural fix for
        // "breaking a block next to [natural] water doesn't make it flow".
        if (type == BlockType.AIR) {
            promoteIfStaticFluid(worldX + 1, worldY, worldZ);
            promoteIfStaticFluid(worldX - 1, worldY, worldZ);
            promoteIfStaticFluid(worldX, worldY + 1, worldZ);
            promoteIfStaticFluid(worldX, worldY - 1, worldZ);
            promoteIfStaticFluid(worldX, worldY, worldZ + 1);
            promoteIfStaticFluid(worldX, worldY, worldZ - 1);
        }
    }

    /** If the block at this position is static WATER/LAVA, promotes it to the matching tracked source. See setBlock. */
    private void promoteIfStaticFluid(int x, int y, int z) {
        BlockType promoted = getBlock(x, y, z).promotedFluidSource();
        if (promoted != null) {
            setBlock(x, y, z, promoted);
        }
    }

    private void markNeighborDirty(int cx, int cz) {
        Chunk c = getChunk(cx, cz);
        if (c != null) c.markDirty();
    }

    /**
     * Sets a block without flagging the chunk as player-modified - used by the
     * fluid simulation for transient flow cells, which are recomputed (not saved).
     */
    public void setFluidBlock(int worldX, int worldY, int worldZ, BlockType type) {
        if (worldY < 0 || worldY >= Chunk.HEIGHT) return;
        int cx = worldToChunk(worldX);
        int cz = worldToChunk(worldZ);
        Chunk chunk = getChunk(cx, cz);
        if (chunk == null) return;
        int lx = Math.floorMod(worldX, Chunk.SIZE);
        int lz = Math.floorMod(worldZ, Chunk.SIZE);
        chunk.setLocal(lx, worldY, lz, type);
        if (lx == 0) markNeighborDirty(cx - 1, cz);
        if (lx == Chunk.SIZE - 1) markNeighborDirty(cx + 1, cz);
        if (lz == 0) markNeighborDirty(cx, cz - 1);
        if (lz == Chunk.SIZE - 1) markNeighborDirty(cx, cz + 1);
    }

    /**
     * Recomputes the fluid flow field from every loaded fluid source: fills
     * empty cells within reach with flow, and dries up flow cells that are no
     * longer connected to a source. Runs once per frame.
     */
    public void updateFluids() {
        List<FluidSim.FluidBlock> sources = new ArrayList<>();
        List<FluidSim.FluidBlock> flows = new ArrayList<>();
        for (Chunk c : chunks.values()) {
            int ox = c.getOriginX();
            int oz = c.getOriginZ();
            for (int[] local : c.getLocalFluidBlocks()) {
                int wx = ox + local[0], wy = local[1], wz = oz + local[2];
                BlockType t = c.getLocal(local[0], local[1], local[2]);
                if (t.isFluidSource()) {
                    sources.add(new FluidSim.FluidBlock(wx, wy, wz, t));
                } else if (t.isFluidFlow()) {
                    flows.add(new FluidSim.FluidBlock(wx, wy, wz, t));
                }
            }
        }
        if (sources.isEmpty() && flows.isEmpty()) return;

        FluidSim.Result result = FluidSim.compute(this, sources, flows);
        for (Map.Entry<Long, BlockType> e : result.fill().entrySet()) {
            setFluidBlock(FluidSim.keyX(e.getKey()), FluidSim.keyY(e.getKey()), FluidSim.keyZ(e.getKey()), e.getValue());
        }
        for (long k : result.remove()) {
            setFluidBlock(FluidSim.keyX(k), FluidSim.keyY(k), FluidSim.keyZ(k), BlockType.AIR);
        }
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
        return generator.getSeaLevel();
    }

    /** The biome at a world column, recomputed deterministically - used by the F3 debug overlay. */
    public TerrainGenerator.Biome getBiome(int worldX, int worldZ) {
        return generator.biomeAtWorld(worldX, worldZ);
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

        // Flow after streaming so newly loaded chunks participate in the field.
        updateFluids();

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

    /** Spawns a dropped item of {@code type} at the given block position (centered, with a small random kick). */
    public void spawnItem(int blockX, int blockY, int blockZ, BlockType type, int count, java.util.Random rnd) {
        ItemEntity e = new ItemEntity(type, count, blockX + 0.5f, blockY + 0.5f, blockZ + 0.5f);
        e.velocity.set((rnd.nextFloat() - 0.5f) * 1.5f, 2.5f + rnd.nextFloat() * 1.5f, (rnd.nextFloat() - 0.5f) * 1.5f);
        items.add(e);
    }

    /** All currently-dropped item entities (read-only; rendered by the caller). */
    public List<ItemEntity> getItems() {
        return items;
    }

    /**
     * Advances item physics (gravity, resting on blocks) and collects any item
     * the player is close enough to pick up into {@code inventory}. Call once
     * per frame from the main thread.
     */
    public void updateItems(float dt, Vector3f playerPos, Inventory inventory) {
        for (Iterator<ItemEntity> it = items.iterator(); it.hasNext(); ) {
            ItemEntity e = it.next();
            e.age += dt;
            if (e.isExpired() || e.position.y < -64f) {
                it.remove();
                continue;
            }

            e.velocity.y -= ITEM_GRAVITY * dt;
            e.velocity.y = Math.max(e.velocity.y, ITEM_TERMINAL_VELOCITY);
            e.position.x += e.velocity.x * dt;
            e.position.y += e.velocity.y * dt;
            e.position.z += e.velocity.z * dt;
            // Air friction on the horizontal kick.
            float friction = Math.max(0f, 1f - 6f * dt);
            e.velocity.x *= friction;
            e.velocity.z *= friction;

            // Rest on top of a solid block below the item.
            int bx = (int) Math.floor(e.position.x);
            int by = (int) Math.floor(e.position.y - ITEM_HALF_HEIGHT);
            int bz = (int) Math.floor(e.position.z);
            BlockType below = getBlock(bx, by, bz);
            if (below.isCollidable()) {
                e.position.y = by + below.collisionHeight + ITEM_HALF_HEIGHT;
                e.velocity.y = 0f;
            }

            if (e.canPickup()) {
                float dx = e.position.x - playerPos.x;
                float dy = e.position.y - (playerPos.y + 0.9f);
                float dz = e.position.z - playerPos.z;
                if (dx * dx + dy * dy + dz * dz < PICKUP_RADIUS * PICKUP_RADIUS) {
                    inventory.add(e.type, e.count);
                    it.remove();
                }
            }
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
