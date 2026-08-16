package com.minecraftclone.world;

import com.minecraftclone.engine.Shader;
import com.minecraftclone.engine.graphics.TextureAtlas;
import com.minecraftclone.player.Inventory;
import com.minecraftclone.util.AABB;
import com.minecraftclone.world.gen.TerrainGenerator;
import com.minecraftclone.world.gen.WorldGenSettings;
import org.joml.FrustumIntersection;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.lwjgl.opengl.GL11.glDepthMask;

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
    /** Per-block block entities (furnaces today, machines/chests tomorrow), keyed by {@link #blockKey}. */
    private final Map<Long, BlockEntity> blockEntities = new HashMap<>();
    private final TerrainGenerator generator;
    /** May be null when the world is hosted headless (a dedicated server) - meshing is then skipped entirely. */
    private final TextureAtlas atlas;
    private final ChunkStorage storage;

    /** Called whenever a chunk is generated or loaded, with its grid coords - lets a client know to request it. */
    private java.util.function.BiConsumer<Integer, Integer> chunkListener;

    public void setChunkListener(java.util.function.BiConsumer<Integer, Integer> listener) {
        this.chunkListener = listener;
    }

    private void notifyChunkLoaded(int cx, int cz) {
        if (chunkListener != null) chunkListener.accept(cx, cz);
    }

    private int renderDistance = 6;
    private boolean leavesTransparent = false;
    /**
     * True for a headless (server) world: chunks are generated but never meshed.
     * {@link #atlas} is null in that case; update() skips the remesh pass.
     */
    private final boolean headless;
    /**
     * When true the streaming loop never unloads chunks - the whole generated
     * area stays in memory. Used by the server, where chunks are shared across
     * many players scattered around the map (each player's position streams its
     * own neighborhood, and unloading around one player would evict another's).
     */
    private boolean keepChunks = false;
    // Generation and meshing used to be budgeted by a fixed chunk *count* per
    // frame (generate/mesh up to N, however long that takes). That has the
    // same flaw the fluid-tick throttle below already learned the hard way:
    // per-chunk cost isn't constant (profiling found some chunks taking many
    // times longer than others - cave/ore-heavy terrain is far pricier than a
    // flat plain), so a handful of expensive chunks landing in one frame's
    // fixed-size batch could still blow the frame budget wide open, which is
    // exactly the "lags kinda bad at max render distance during world gen"
    // symptom. Budgeted by wall-clock time instead: keep generating/meshing
    // until the time budget is spent, not until a chunk count is reached.
    // Each loop still always does at least one unit of work when something is
    // pending, so streaming can't stall completely even if a single chunk
    // alone exceeds the budget.
    private static final double GENERATE_BUDGET_SECONDS = 0.006;
    private static final double MESH_BUDGET_SECONDS = 0.004;
    // world.update() (and so updateFluids()) runs once per rendered frame, so
    // recomputing the flood-fill every single call made "one ring per tick"
    // (see FluidSim) mean one ring per *frame*. A fixed *frame-count*
    // throttle (e.g. "every 12 calls") was tried first, on the assumption of
    // a steady ~60fps - but frame rate isn't a constant: vsync caps to
    // whatever the display's actual refresh rate is (which can be well over
    // 60Hz), and it's frequently not enforced at all under a software/
    // headless renderer, so "every 12 frames" could mean anywhere from
    // "every fifth of a second" to "every couple of milliseconds" depending
    // entirely on how fast frames happen to be rendering right now - not
    // actually throttled at all in the worst case. Timed off the real clock
    // instead (like the flow texture's own scroll animation already is),
    // this is independent of frame rate altogether.
    private static final double FLUID_TICK_SECONDS = 0.15;
    private double lastFluidTickNanos = Double.NaN;

    // Reused across frames (see render) rather than allocated fresh each call -
    // frustum culling runs every single frame regardless of whether anything
    // actually changed, so its own bookkeeping shouldn't add GC churn on top
    // of whatever it saves by skipping off-screen chunks.
    private final Matrix4f viewProjection = new Matrix4f();
    private final FrustumIntersection frustum = new FrustumIntersection();
    private final List<Chunk> visibleChunks = new ArrayList<>();

    // Dropped item entities (from breaking blocks / death). Transient - not saved.
    private final List<ItemEntity> items = new ArrayList<>();
    private static final float ITEM_GRAVITY = 24f;
    private static final float ITEM_TERMINAL_VELOCITY = -40f;
    private static final float ITEM_HALF_HEIGHT = 0.15f;
    private static final float PICKUP_RADIUS = 1.5f;

    // Passive animals wandering the surface. Transient - not saved.
    private final List<Mob> mobs = new ArrayList<>();
    /** Next id for a newly spawned mob - lets a multiplayer server address mobs by id. */
    private int nextMobId = 1;
    private static final int MAX_MOBS = 32;                    // loaded at once
    private static final float MOB_SPAWN_RADIUS = 40f;         // spawn within this many blocks
    private static final float MOB_DESPAWN_RADIUS = 72f;       // despawn beyond this
    private static final int MOB_SPAWN_ODDS = 30;              // 1 in N ticks try to spawn

    // Hostile monsters: spawn only at night, out of sight, in their own cap.
    private static final int MAX_HOSTILES = 12;
    private static final int HOSTILE_SPAWN_ODDS = 20;          // 1 in N night ticks try to spawn
    private static final float HOSTILE_MIN_DIST = 18f;         // never spawn right on top of the player
    private static final float HOSTILE_MAX_DIST = 32f;
    private static final float ARROW_DAMAGE = 3f;              // a skeleton hit, like its attackDamage

    // Skeleton arrows. Transient - not saved.
    private final List<ArrowEntity> arrows = new ArrayList<>();

    public World(long seed, WorldGenSettings genSettings, TextureAtlas atlas, Path saveDir) {
        this(seed, genSettings, atlas, saveDir, false);
    }

    /**
     * @param headless true to run without an OpenGL context (a dedicated server):
     *                 {@code atlas} must be null then and meshing is skipped.
     */
    public World(long seed, WorldGenSettings genSettings, TextureAtlas atlas, Path saveDir, boolean headless) {
        this.generator = new TerrainGenerator(seed, genSettings);
        this.atlas = atlas;
        this.headless = headless;
        this.storage = new ChunkStorage(saveDir);
    }

    /** Packs chunk-grid coordinates into a single key. {@code chunkZ} is masked so negative coordinates stay unique. */
    private static long key(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
    }

    public void setRenderDistance(int renderDistance) {
        this.renderDistance = renderDistance;
    }

    /** Prevents the streaming loop from unloading chunks (see {@link #keepChunks}). */
    public void setKeepChunks(boolean keepChunks) {
        this.keepChunks = keepChunks;
    }

    public boolean isHeadless() {
        return headless;
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

    /** True if the chunk at grid coordinate {@code (cx, cz)} is loaded/generated. */
    public boolean isChunkLoaded(int cx, int cz) {
        return chunks.containsKey(key(cx, cz));
    }

    /** The stored facing hint for a door block at a world position (0:+Z, 1:-Z, 2:+X, 3:-X). */
    public byte getOrientation(int worldX, int worldY, int worldZ) {
        if (worldY < 0 || worldY >= Chunk.HEIGHT) return 0;
        Chunk chunk = getChunk(worldToChunk(worldX), worldToChunk(worldZ));
        if (chunk == null) return 0;
        return chunk.getOrientation(Math.floorMod(worldX, Chunk.SIZE), worldY, Math.floorMod(worldZ, Chunk.SIZE));
    }

    /** True if a loaded chunk has been edited by a player (differs from seed regeneration). */
    public boolean isChunkModifiedByPlayer(int cx, int cz) {
        Chunk chunk = getChunk(cx, cz);
        return chunk != null && chunk.isModifiedByPlayer();
    }

    /** Returns the chunk's raw block-id array for sending to a client, or null if it isn't loaded. */
    public byte[] getChunkRawBlocks(int cx, int cz) {
        Chunk chunk = getChunk(cx, cz);
        return chunk == null ? null : chunk.getRawBlocks();
    }

    /** Returns the chunk's raw overlay-id array for sending to a client, or null if it isn't loaded. */
    public byte[] getChunkRawOverlays(int cx, int cz) {
        Chunk chunk = getChunk(cx, cz);
        return chunk == null ? null : chunk.getRawOverlays();
    }

    /** Returns the chunk's raw orientation array for sending to a client, or null if it isn't loaded. */
    public byte[] getChunkRawOrientations(int cx, int cz) {
        Chunk chunk = getChunk(cx, cz);
        return chunk == null ? null : chunk.getRawOrientations();
    }

    /** Applies server-provided raw chunk data to a loaded chunk (the multiplayer client path). */
    public void applyRemoteChunkData(int cx, int cz, byte[] blocks, byte[] overlays, byte[] orientations) {
        Chunk chunk = getChunk(cx, cz);
        if (chunk == null) return;
        if (blocks != null) chunk.setRawBlocks(blocks);
        if (overlays != null) chunk.setRawOverlays(overlays);
        if (orientations != null) chunk.setRawOrientations(orientations);
        chunk.markGenerated();
    }

    /** Loads (from disk) or generates the chunk at grid coordinate {@code (cx, cz)} if it isn't already loaded. */
    public void ensureChunk(int cx, int cz) {
        if (chunks.containsKey(key(cx, cz))) return;
        Chunk chunk = new Chunk(new ChunkPos(cx, cz));
        chunks.put(key(cx, cz), chunk);
        if (storage.hasSavedChunk(chunk.getPos())) {
            for (ChunkStorage.BlockEntitySave es : storage.load(chunk)) {
                if (getBlock(es.x(), es.y(), es.z()) == es.entity().blockType()) {
                    blockEntities.put(blockKey(es.x(), es.y(), es.z()), es.entity());
                }
            }
        } else {
            generator.generate(chunk);
        }
        markNeighborDirty(cx - 1, cz);
        markNeighborDirty(cx + 1, cz);
        markNeighborDirty(cx, cz - 1);
        markNeighborDirty(cx, cz + 1);
        notifyChunkLoaded(cx, cz);
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

    @Override
    public int getFluidLevel(int worldX, int worldY, int worldZ) {
        if (worldY < 0 || worldY >= Chunk.HEIGHT) return 0;
        Chunk chunk = getChunk(worldToChunk(worldX), worldToChunk(worldZ));
        if (chunk == null) return 0;
        int lx = Math.floorMod(worldX, Chunk.SIZE);
        int lz = Math.floorMod(worldZ, Chunk.SIZE);
        return chunk.getFluidLevel(lx, worldY, lz);
    }

    @Override
    public BlockType getOverlay(int worldX, int worldY, int worldZ) {
        if (worldY < 0 || worldY >= Chunk.HEIGHT) return BlockType.AIR;
        Chunk chunk = getChunk(worldToChunk(worldX), worldToChunk(worldZ));
        if (chunk == null) return BlockType.AIR;
        int lx = Math.floorMod(worldX, Chunk.SIZE);
        int lz = Math.floorMod(worldZ, Chunk.SIZE);
        return chunk.getOverlay(lx, worldY, lz);
    }

    /**
     * Sets (or clears, with {@link BlockType#AIR}) the decoration layered
     * inside a cell - e.g. seaweed inside a water cell - without touching
     * whatever its primary block is. See {@link Chunk#setOverlay}. Doesn't
     * need the neighbor-chunk-dirtying or light/fluid-promotion handling
     * {@link #setBlock} does: an overlay is a cross-shaped decoration, which
     * never occludes a face (so it can't change a neighbor chunk's culling)
     * and is never a light source or a fluid source/flow itself.
     */
    public void setOverlay(int worldX, int worldY, int worldZ, BlockType type) {
        if (worldY < 0 || worldY >= Chunk.HEIGHT) return;
        Chunk chunk = getChunk(worldToChunk(worldX), worldToChunk(worldZ));
        if (chunk == null) return;
        int lx = Math.floorMod(worldX, Chunk.SIZE);
        int lz = Math.floorMod(worldZ, Chunk.SIZE);
        chunk.setOverlayFromPlayer(lx, worldY, lz, type);
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

    /** Packs block coordinates into a single key (21 bits per axis, so negatives stay unique). */
    private static long blockKey(int x, int y, int z) {
        return ((long) x & 0x1FFFFFL) | (((long) y & 0x1FFFFFL) << 21) | (((long) z & 0x1FFFFFL) << 42);
    }

    private static int keyX(long key) {
        return (int) (key & 0x1FFFFFL) << 21 >> 21;
    }

    private static int keyY(long key) {
        return (int) ((key >> 21) & 0x1FFFFFL) << 21 >> 21;
    }

    private static int keyZ(long key) {
        return (int) ((key >> 42) & 0x1FFFFFL) << 21 >> 21;
    }

    /** The block entity at a block position, or null if none. */
    public BlockEntity blockEntityAt(int x, int y, int z) {
        return blockEntities.get(blockKey(x, y, z));
    }

    /** The furnace at a block position, or null if none (no furnace placed / never opened). */
    public Furnace furnaceAt(int x, int y, int z) {
        return blockEntityAt(x, y, z) instanceof Furnace furnace ? furnace : null;
    }

    /** The chest at a block position, or null if none (no chest placed / never opened). */
    public Chest chestAt(int x, int y, int z) {
        BlockEntity entity = blockEntityAt(x, y, z);
        if (entity instanceof Barrel) return null;
        return entity instanceof Chest chest ? chest : null;
    }

    /** The barrel at a block position, or null if none (no barrel placed / never opened). */
    public Barrel barrelAt(int x, int y, int z) {
        return blockEntityAt(x, y, z) instanceof Barrel barrel ? barrel : null;
    }

    /** Returns the barrel at a position, creating (and registering) it on first use. */
    public Barrel getOrCreateBarrel(int x, int y, int z) {
        BlockEntity existing = blockEntities.get(blockKey(x, y, z));
        if (existing instanceof Barrel barrel) return barrel;
        Barrel barrel = new Barrel();
        blockEntities.put(blockKey(x, y, z), barrel);
        return barrel;
    }

    /** Returns the chest at a position, creating (and registering) it on first use. */
    public Chest getOrCreateChest(int x, int y, int z) {
        BlockEntity existing = blockEntities.get(blockKey(x, y, z));
        if (existing instanceof Barrel) return null;
        if (existing instanceof Chest chest) return chest;
        Chest chest = new Chest();
        blockEntities.put(blockKey(x, y, z), chest);
        return chest;
    }

    /**
     * The storage the player sees when opening the chest at a position: a single
     * 27-slot chest, a 54-slot {@link JoinedStorage} when it has a chest
     * immediately beside it on the east-west axis (a Minecraft-style double
     * chest), or a 108-slot container when four chests form a 2x2 square (a
     * "quad chest"). Each block keeps its own persisted slots - the merge is
     * just a combined view, ordered so the layout is stable whichever half you
     * open. Same-y/z requirement keeps stacked or north-south chests single.
     */
    public com.minecraftclone.player.StorageContainer chestContainerAt(int x, int y, int z) {
        Chest here = chestAt(x, y, z);
        if (here == null) return null;
        // A 2x2 square of chests (all four cells present, at the same y) merges
        // into one 108-slot container, ordered row-major by world position so
        // it reads the same no matter which corner you open.
        for (int ox = -1; ox <= 0; ox++) {
            for (int oz = -1; oz <= 0; oz++) {
                int x0 = ox == 0 ? x : x - 1;
                int z0 = oz == 0 ? z : z - 1;
                Chest a = chestAt(x0, y, z0);
                Chest b = chestAt(x0 + 1, y, z0);
                Chest c = chestAt(x0, y, z0 + 1);
                Chest d = chestAt(x0 + 1, y, z0 + 1);
                if (a != null && b != null && c != null && d != null) {
                    // Make sure the whole 2x2 is present regardless of which
                    // corner was clicked, and order west-to-east, north-to-south.
                    return new com.minecraftclone.player.JoinedStorage(
                            new com.minecraftclone.player.JoinedStorage(a, b),
                            new com.minecraftclone.player.JoinedStorage(c, d));
                }
            }
        }
        Chest west = chestAt(x - 1, y, z);
        Chest east = chestAt(x + 1, y, z);
        if (west != null) return new com.minecraftclone.player.JoinedStorage(west, here);
        if (east != null) return new com.minecraftclone.player.JoinedStorage(here, east);
        return here;
    }

    /** True if the block at this position is currently active - for a furnace, that it's burning (its front glows). */
    @Override
    public boolean isBlockActive(int x, int y, int z) {
        BlockEntity entity = blockEntityAt(x, y, z);
        return entity != null && entity.isActive();
    }

    /** Returns the furnace at a position, creating (and registering) it on first use. */
    public Furnace getOrCreateFurnace(int x, int y, int z) {
        BlockEntity existing = blockEntities.get(blockKey(x, y, z));
        if (existing instanceof Furnace furnace) return furnace;
        Furnace furnace = new Furnace();
        blockEntities.put(blockKey(x, y, z), furnace);
        return furnace;
    }

    /** Forgets a block entity - call when its block is mined or removed. */
    public void removeBlockEntity(int x, int y, int z) {
        blockEntities.remove(blockKey(x, y, z));
    }

    /**
     * Advances every block entity by {@code dt} seconds of world time. Entities
     * whose block has since been replaced are pruned (their contents are dropped
     * by the caller when the block is mined). When an entity's active state
     * changes (a furnace lighting up or going out), its chunks are marked dirty
     * so the mesh re-bakes the glowing front tile and the light it emits.
     */
    public void tickBlockEntities(float dt) {
        if (dt <= 0) return;
        Iterator<Map.Entry<Long, BlockEntity>> it = blockEntities.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Long, BlockEntity> entry = it.next();
            long key = entry.getKey();
            BlockEntity entity = entry.getValue();
            if (getBlock(keyX(key), keyY(key), keyZ(key)) != entity.blockType()) {
                it.remove();
                continue;
            }
            boolean wasActive = entity.isActive();
            entity.tick(dt);
            boolean nowActive = entity.isActive();
            if (wasActive != nowActive) {
                // The lit/unlit front tile and the light it casts both changed -
                // remesh this chunk and its neighbors (the light radius can reach
                // across a chunk boundary).
                int cx = worldToChunk(keyX(key));
                int cz = worldToChunk(keyZ(key));
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        markNeighborDirty(cx + dx, cz + dz);
                    }
                }
            }
        }
    }

    /** The block entities living inside chunk {@code c}, excluding any whose block has since been removed. */
    private List<ChunkStorage.BlockEntitySave> blockEntitiesInChunk(Chunk c) {
        int minX = c.getOriginX();
        int minZ = c.getOriginZ();
        List<ChunkStorage.BlockEntitySave> out = new ArrayList<>();
        for (Map.Entry<Long, BlockEntity> e : blockEntities.entrySet()) {
            int x = keyX(e.getKey());
            int z = keyZ(e.getKey());
            if (x < minX || x >= minX + Chunk.SIZE || z < minZ || z >= minZ + Chunk.SIZE) continue;
            int y = keyY(e.getKey());
            if (getBlock(x, y, z) != e.getValue().blockType()) continue; // mined/removed - don't resurrect it
            out.add(new ChunkStorage.BlockEntitySave(x, y, z, e.getValue()));
        }
        return out;
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
        setFluidBlock(worldX, worldY, worldZ, type, 0);
    }

    /**
     * Like {@link #setFluidBlock(int, int, int, BlockType)}, but also records
     * this fill's distance from its source (see {@link FluidSim.Result#levels}
     * and {@link BlockAccessor#getFluidLevel}) so the renderer can grade the
     * surface height down as flowing fluid spreads farther from its source.
     */
    public void setFluidBlock(int worldX, int worldY, int worldZ, BlockType type, int level) {
        if (worldY < 0 || worldY >= Chunk.HEIGHT) return;
        int cx = worldToChunk(worldX);
        int cz = worldToChunk(worldZ);
        Chunk chunk = getChunk(cx, cz);
        if (chunk == null) return;
        int lx = Math.floorMod(worldX, Chunk.SIZE);
        int lz = Math.floorMod(worldZ, Chunk.SIZE);
        chunk.setLocal(lx, worldY, lz, type, level);
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
            long k = e.getKey();
            int level = result.levels().getOrDefault(k, 0);
            setFluidBlock(FluidSim.keyX(k), FluidSim.keyY(k), FluidSim.keyZ(k), e.getValue(), level);
        }
        // Existing flows' distances can change when the topology changes (a new
        // source placed next to old water), so refresh any that moved - skipping
        // unchanged ones so a settled field doesn't re-dirty chunks every frame.
        for (Map.Entry<Long, Integer> e : result.flowLevels().entrySet()) {
            long k = e.getKey();
            int x = FluidSim.keyX(k), y = FluidSim.keyY(k), z = FluidSim.keyZ(k);
            int level = e.getValue();
            if (level != getFluidLevel(x, y, z)) {
                BlockType t = getBlock(x, y, z);
                if (t.isFluidFlow()) {
                    setFluidBlock(x, y, z, t, level);
                }
            }
        }
        for (long k : result.remove()) {
            setFluidBlock(FluidSim.keyX(k), FluidSim.keyY(k), FluidSim.keyZ(k), BlockType.AIR);
        }
    }

    /**
     * Every light-emitting block within reach of chunk {@code center}, as world-space
     * {wx, wy, wz, lightLevel} - gathered from the chunk itself plus its 8 immediate
     * neighbors (the light falloff radius is well under a chunk's width, so nothing
     * farther away can reach in), plus any actively-burning furnaces in that area
     * (their glowing mouths light the room the way a torch does). Passed to
     * {@link Chunk#rebuildMesh} to bake local glow.
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
        // Active block entities (a burning furnace) emit light. Iterating the flat
        // entity map is fine - machines are rare, and this only runs when a chunk
        // remeshes.
        for (Map.Entry<Long, BlockEntity> e : blockEntities.entrySet()) {
            BlockEntity entity = e.getValue();
            if (!entity.isActive() || entity.activeLightLevel() <= 0) continue;
            int x = keyX(e.getKey()), y = keyY(e.getKey()), z = keyZ(e.getKey());
            int cx = worldToChunk(x), cz = worldToChunk(z);
            if (Math.abs(cx - center.x()) <= 1 && Math.abs(cz - center.z()) <= 1) {
                result.add(new int[]{x, y, z, entity.activeLightLevel()});
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

    /** Sets the per-block facing hint (used by doors) at a world position. */
    public void setBlockOrientation(int worldX, int worldY, int worldZ, byte orientation) {
        int cx = worldToChunk(worldX), cz = worldToChunk(worldZ);
        Chunk chunk = getChunk(cx, cz);
        if (chunk == null) return;
        chunk.setOrientation(Math.floorMod(worldX, Chunk.SIZE), worldY, Math.floorMod(worldZ, Chunk.SIZE), orientation);
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

        // Load / generate, budgeted by wall-clock time (see GENERATE_BUDGET_SECONDS
        // above for why this isn't a fixed chunk count anymore).
        long generateDeadline = System.nanoTime() + (long) (GENERATE_BUDGET_SECONDS * 1e9);
        int generated = 0;
        outer:
        for (int dx = -renderDistance; dx <= renderDistance; dx++) {
            for (int dz = -renderDistance; dz <= renderDistance; dz++) {
                if (dx * dx + dz * dz > renderDistance * renderDistance) continue;
                int cx = pcx + dx, cz = pcz + dz;
                if (!chunks.containsKey(key(cx, cz))) {
                    if (generated > 0 && System.nanoTime() >= generateDeadline) break outer;
                    Chunk chunk = new Chunk(new ChunkPos(cx, cz));
                    chunks.put(key(cx, cz), chunk);
                    if (storage.hasSavedChunk(chunk.getPos())) {
                        // A previously-edited chunk: restore the player's changes
                        // instead of regenerating pristine terrain, along with any
                        // block entities that were holding state in it.
                        for (ChunkStorage.BlockEntitySave es : storage.load(chunk)) {
                            if (getBlock(es.x(), es.y(), es.z()) == es.entity().blockType()) {
                                blockEntities.put(blockKey(es.x(), es.y(), es.z()), es.entity());
                            }
                        }
                    } else {
                        generator.generate(chunk);
                    }
                    markNeighborDirty(cx - 1, cz);
                    markNeighborDirty(cx + 1, cz);
                    markNeighborDirty(cx, cz - 1);
                    markNeighborDirty(cx, cz + 1);
                    notifyChunkLoaded(cx, cz);
                    generated++;
                }
            }
        }

        // Unload far chunks. Most frames unload nothing, so the removal list
        // is only allocated once something actually needs to go. A headless
        // server with keepChunks set never unloads (chunks are shared).
        int unloadRadius = renderDistance + 2;
        List<Chunk> toRemove = null;
        for (Chunk c : chunks.values()) {
            if (!keepChunks && c.getPos().distanceSq(pcx, pcz) > (double) unloadRadius * unloadRadius) {
                if (toRemove == null) toRemove = new ArrayList<>();
                toRemove.add(c);
            }
        }
        if (toRemove != null) {
            for (Chunk c : toRemove) {
                chunks.remove(key(c.getPos().x(), c.getPos().z()));
                if (c.isModifiedByPlayer()) {
                    storage.save(c, blockEntitiesInChunk(c));
                }
                c.destroy();
            }
        }

        // Flow after streaming so newly loaded chunks participate in the field -
        // throttled to a real, watchable pace (see FLUID_TICK_SECONDS) rather
        // than every single frame. Newly-loaded chunks just sit as they are
        // (already-generated fluid, if any) until the next fluid tick lands.
        double nowNanos = System.nanoTime();
        if (Double.isNaN(lastFluidTickNanos) || (nowNanos - lastFluidTickNanos) / 1e9 >= FLUID_TICK_SECONDS) {
            lastFluidTickNanos = nowNanos;
            updateFluids();
        }

        // Remesh dirty chunks nearest-first, budgeted by wall-clock time (see
        // MESH_BUDGET_SECONDS above) rather than a fixed count per tick. Skipped
        // entirely on a headless server (no GL context, chunks never meshed).
        if (!headless) {
            List<Chunk> dirty = new ArrayList<>();
            for (Chunk c : chunks.values()) {
                if (c.isDirty()) dirty.add(c);
            }
            dirty.sort((a, b) -> Double.compare(a.getPos().distanceSq(pcx, pcz), b.getPos().distanceSq(pcx, pcz)));
            long meshDeadline = System.nanoTime() + (long) (MESH_BUDGET_SECONDS * 1e9);
            for (int i = 0; i < dirty.size(); i++) {
                if (i > 0 && System.nanoTime() >= meshDeadline) break;
                Chunk c = dirty.get(i);
                c.rebuildMesh(this, atlas, collectNearbyLights(c.getPos()), leavesTransparent);
            }
        }
    }

    /**
     * Renders every loaded chunk whose bounding box is inside {@code projection * view}'s
     * view frustum - at max render distance, most of the (up to a few hundred) loaded
     * chunks are behind or well off to the side of the camera at any given moment, so
     * skipping those avoids paying for their draw calls (and the GL state changes/vertex
     * shader invocations that go with them) every single frame for geometry that was
     * never going to end up on screen anyway.
     */
    public void render(Shader shader, Matrix4f projection, Matrix4f view) {
        projection.mul(view, viewProjection);
        frustum.set(viewProjection);

        visibleChunks.clear();
        for (Chunk chunk : chunks.values()) {
            if (isChunkVisible(chunk)) visibleChunks.add(chunk);
        }

        for (Chunk chunk : visibleChunks) {
            chunk.render();
        }
        // See-through geometry (glass/ice) draws after everything opaque and blends
        // over it; depth writes are off so overlapping translucent faces don't cull
        // each other.
        glDepthMask(false);
        for (Chunk chunk : visibleChunks) {
            chunk.renderTranslucent();
        }
        glDepthMask(true);
    }

    private boolean isChunkVisible(Chunk chunk) {
        int originX = chunk.getOriginX();
        int originZ = chunk.getOriginZ();
        return frustum.testAab(originX, 0, originZ, originX + Chunk.SIZE, Chunk.HEIGHT, originZ + Chunk.SIZE);
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
     * per frame from the main thread. Returns true if at least one item was
     * picked up this frame (for a pickup sound - see Main).
     */
    public boolean updateItems(float dt, Vector3f playerPos, Inventory inventory) {
        boolean pickedUp = false;
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
                    int remaining = inventory.add(e.type, e.count);
                    if (remaining < e.count) {
                        pickedUp = true;
                        if (remaining > 0) {
                            e.count = remaining;
                        } else {
                            it.remove();
                        }
                    }
                }
            }
        }
        return pickedUp;
    }

    public int getLoadedChunkCount() {
        return chunks.size();
    }

    /** How many loaded chunks actually passed the frustum test in the most recent {@link #render}. */
    public int getVisibleChunkCount() {
        return visibleChunks.size();
    }

    /** All currently-alive mobs (read-only; rendered by the caller). */
    public List<Mob> getMobs() {
        return mobs;
    }

    /** All currently-flying skeleton arrows (read-only; rendered by the caller). */
    public List<ArrowEntity> getArrows() {
        return arrows;
    }

    /**
     * Advances every mob (wandering/pathing, gravity, collision), spawns new ones
     * near the player up to the cap, and despawns any that wander beyond
     * {@link #MOB_DESPAWN_RADIUS} so nothing trails the player across the whole
     * map. Hostile mobs spawn only at night, out of sight, and melt away at dawn;
     * their melee hits and arrows damage the player, whose total damage taken
     * this frame is returned. Call once per frame from the main thread.
     */
    public float updateMobs(float dt, Vector3f playerPos, AABB playerBox, boolean night, Random rnd) {
        float despawnSq = MOB_DESPAWN_RADIUS * MOB_DESPAWN_RADIUS;
        float damage = 0f;
        for (Iterator<Mob> it = mobs.iterator(); it.hasNext(); ) {
            Mob mob = it.next();
            float dx = mob.position.x - playerPos.x;
            float dz = mob.position.z - playerPos.z;
            // Hostiles are gone by daylight (they burn/melt at dawn); everything
            // far away or fallen out of the world despawns.
            boolean gone = mob.isHostile() && !night;
            if (gone || dx * dx + dz * dz > despawnSq || mob.position.y < -64f) {
                it.remove();
                continue;
            }
            mob.update(dt, this, rnd, playerPos);
            damage += mob.getMeleeRequest();
            if (mob.wantsToShoot()) {
                spawnArrow(mob, playerPos, rnd);
            }
        }

        if (night && hostileCount() < MAX_HOSTILES && rnd.nextInt(HOSTILE_SPAWN_ODDS) == 0) {
            trySpawnHostile(rnd, playerPos.x, playerPos.z);
        }
        if (mobs.size() < MAX_MOBS && rnd.nextInt(MOB_SPAWN_ODDS) == 0) {
            trySpawnMob(rnd, playerPos.x, playerPos.z);
        }

        damage += updateArrows(dt, playerBox);
        return damage;
    }

    /**
     * Multiplayer variant of {@link #updateMobs}: advances every mob with the
     * *nearest* of several players as its target, spawns around each player up
     * to the same global caps, and returns the damage each player took (indexed
     * like {@code playerPositions}) - melee hits, arrow hits, and fallback
     * damage all routed to the right player. Call once per server tick.
     */
    public float[] updateMobsMulti(float dt, List<Vector3f> playerPositions, List<AABB> playerBoxes, boolean night, Random rnd) {
        int count = playerPositions.size();
        float[] damage = new float[count];
        float despawnSq = MOB_DESPAWN_RADIUS * MOB_DESPAWN_RADIUS;

        for (Iterator<Mob> it = mobs.iterator(); it.hasNext(); ) {
            Mob mob = it.next();
            int nearest = nearestPlayerIndex(mob.position, playerPositions);
            Vector3f target = playerPositions.get(nearest);
            float dx = mob.position.x - target.x;
            float dz = mob.position.z - target.z;
            boolean gone = mob.isHostile() && !night;
            if (gone || dx * dx + dz * dz > despawnSq || mob.position.y < -64f) {
                it.remove();
                continue;
            }
            mob.update(dt, this, rnd, target);
            damage[nearest] += mob.getMeleeRequest();
            if (mob.wantsToShoot()) {
                spawnArrow(mob, target, rnd);
            }
        }

        if (night && hostileCount() < MAX_HOSTILES && rnd.nextInt(HOSTILE_SPAWN_ODDS) == 0) {
            int p = rnd.nextInt(count);
            trySpawnHostile(rnd, playerPositions.get(p).x, playerPositions.get(p).z);
        }
        if (mobs.size() < MAX_MOBS && rnd.nextInt(MOB_SPAWN_ODDS) == 0) {
            int p = rnd.nextInt(count);
            trySpawnMob(rnd, playerPositions.get(p).x, playerPositions.get(p).z);
        }

        float[] arrowDamage = updateArrowsMulti(dt, playerBoxes);
        for (int i = 0; i < count; i++) {
            damage[i] += arrowDamage[i];
        }
        return damage;
    }

    /** Index of the closest player to {@code pos} (mob AI targets the nearest, like Minecraft). */
    private static int nearestPlayerIndex(Vector3f pos, List<Vector3f> playerPositions) {
        int best = 0;
        float bestSq = Float.MAX_VALUE;
        for (int i = 0; i < playerPositions.size(); i++) {
            Vector3f p = playerPositions.get(i);
            float dx = pos.x - p.x, dz = pos.z - p.z;
            float sq = dx * dx + dz * dz;
            if (sq < bestSq) {
                bestSq = sq;
                best = i;
            }
        }
        return best;
    }

    /** Advances arrows against several players, returning per-player damage (indexed like {@code boxes}). */
    private float[] updateArrowsMulti(float dt, List<AABB> playerBoxes) {
        float[] damage = new float[playerBoxes.size()];
        for (Iterator<ArrowEntity> it = arrows.iterator(); it.hasNext(); ) {
            ArrowEntity a = it.next();
            a.age += dt;
            if (a.age > ArrowEntity.LIFETIME || a.position.y < -32f || a.stuck) {
                it.remove();
                continue;
            }
            a.velocity.y -= 20f * dt;

            float speed = (float) Math.sqrt(a.velocity.x * a.velocity.x + a.velocity.y * a.velocity.y + a.velocity.z * a.velocity.z);
            float move = speed * dt;
            if (move <= 0f) continue;
            int steps = Math.max(1, (int) Math.ceil(move / 0.2f));
            float sub = dt / steps;
            boolean consumed = false;
            for (int s = 0; s < steps && !consumed; s++) {
                a.position.x += a.velocity.x * sub;
                a.position.y += a.velocity.y * sub;
                a.position.z += a.velocity.z * sub;

                AABB arrowBox = new AABB(a.position.x - 0.1f, a.position.y - 0.1f, a.position.z - 0.1f,
                        a.position.x + 0.1f, a.position.y + 0.1f, a.position.z + 0.1f);
                for (int i = 0; i < playerBoxes.size(); i++) {
                    if (playerBoxes.get(i).intersects(arrowBox)) {
                        damage[i] += ARROW_DAMAGE;
                        consumed = true;
                        break;
                    }
                }
                if (getBlock((int) Math.floor(a.position.x), (int) Math.floor(a.position.y),
                        (int) Math.floor(a.position.z)).isCollidable()) {
                    a.stuck = true;
                    break;
                }
            }
            if (consumed) {
                it.remove();
            }
        }
        return damage;
    }

    /** Advances skeleton arrows (gravity, block/player collisions) and returns player damage taken. */
    private float updateArrows(float dt, AABB playerBox) {
        float damage = 0f;
        for (Iterator<ArrowEntity> it = arrows.iterator(); it.hasNext(); ) {
            ArrowEntity a = it.next();
            a.age += dt;
            if (a.age > ArrowEntity.LIFETIME || a.position.y < -32f || a.stuck) {
                it.remove();
                continue;
            }
            a.velocity.y -= 20f * dt;

            // Move in small sub-steps so a fast arrow can't tunnel through a block or the player.
            float speed = (float) Math.sqrt(a.velocity.x * a.velocity.x + a.velocity.y * a.velocity.y + a.velocity.z * a.velocity.z);
            float move = speed * dt;
            if (move <= 0f) continue;
            int steps = Math.max(1, (int) Math.ceil(move / 0.2f));
            float sub = dt / steps;
            boolean consumed = false;
            for (int s = 0; s < steps; s++) {
                a.position.x += a.velocity.x * sub;
                a.position.y += a.velocity.y * sub;
                a.position.z += a.velocity.z * sub;

                AABB arrowBox = new AABB(a.position.x - 0.1f, a.position.y - 0.1f, a.position.z - 0.1f,
                        a.position.x + 0.1f, a.position.y + 0.1f, a.position.z + 0.1f);
                if (playerBox.intersects(arrowBox)) {
                    damage += ARROW_DAMAGE;
                    consumed = true;
                    break;
                }
                if (getBlock((int) Math.floor(a.position.x), (int) Math.floor(a.position.y),
                        (int) Math.floor(a.position.z)).isCollidable()) {
                    a.stuck = true;
                    break;
                }
            }
            if (consumed) {
                it.remove();
            }
        }
        return damage;
    }

    private int hostileCount() {
        int n = 0;
        for (Mob m : mobs) {
            if (m.isHostile()) n++;
        }
        return n;
    }

    /** Seeds the world with an initial scattering of mobs so it feels alive from the start. */
    public void spawnInitialMobs(Random rnd, float playerWorldX, float playerWorldZ, int count) {
        for (int i = 0; i < count && mobs.size() < MAX_MOBS; i++) {
            trySpawnMob(rnd, playerWorldX, playerWorldZ);
        }
    }

    /** Spawns one hostile on solid ground just out of sight; does nothing if no spot qualifies. */
    private void trySpawnHostile(Random rnd, float playerWorldX, float playerWorldZ) {
        for (int attempt = 0; attempt < 8; attempt++) {
            float angle = rnd.nextFloat() * (float) Math.PI * 2f;
            float dist = HOSTILE_MIN_DIST + rnd.nextFloat() * (HOSTILE_MAX_DIST - HOSTILE_MIN_DIST);
            int x = (int) Math.floor(playerWorldX + (float) Math.cos(angle) * dist);
            int z = (int) Math.floor(playerWorldZ + (float) Math.sin(angle) * dist);
            if (!isFullyGenerated(x, z)) continue;
            int y = getSurfaceHeight(x, z);
            if (y < 1 || y >= Chunk.HEIGHT - 1) continue;
            if (!getBlock(x, y, z).isCollidable()) continue;
            if (getBlock(x, y + 1, z) != BlockType.AIR) continue;
            Mob.Type type = rnd.nextBoolean() ? Mob.Type.ZOMBIE : Mob.Type.SKELETON;
            mobs.add(newMob(type, x + 0.5f, y + 1f + type.height / 2f, z + 0.5f));
            return;
        }
    }

    /** Fires an arrow from a skeleton toward the player (with a little inaccuracy). */
    private void spawnArrow(Mob mob, Vector3f playerPos, Random rnd) {
        float sx = mob.position.x;
        float sy = mob.position.y + mob.type.height * 0.4f;
        float sz = mob.position.z;
        float tx = playerPos.x + (rnd.nextFloat() - 0.5f) * 1.5f;
        float ty = playerPos.y + 0.9f + (rnd.nextFloat() - 0.5f) * 0.8f;
        float tz = playerPos.z + (rnd.nextFloat() - 0.5f) * 1.5f;
        float dx = tx - sx, dy = ty - sy, dz = tz - sz;
        float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len <= 0f) return;
        float speed = 15f;
        arrows.add(new ArrowEntity(sx, sy, sz, dx / len * speed, dy / len * speed, dz / len * speed));
    }

    /** Spawns one mob on a random grass surface within range; does nothing if no spot qualifies. */
    private void trySpawnMob(Random rnd, float playerWorldX, float playerWorldZ) {
        int radius = (int) MOB_SPAWN_RADIUS;
        for (int attempt = 0; attempt < 6; attempt++) {
            int x = (int) Math.floor(playerWorldX) + rnd.nextInt(radius * 2) - radius;
            int z = (int) Math.floor(playerWorldZ) + rnd.nextInt(radius * 2) - radius;
            if (!isFullyGenerated(x, z)) continue;
            int y = getSurfaceHeight(x, z);
            if (y < 1 || y >= Chunk.HEIGHT - 1) continue;
            if (getBlock(x, y, z) != BlockType.GRASS) continue;
            if (getBlock(x, y + 1, z) != BlockType.AIR) continue;
            Mob.Type type = Mob.Type.values()[rnd.nextInt(Mob.Type.values().length)];
            mobs.add(newMob(type, x + 0.5f, y + 1f + type.height / 2f, z + 0.5f));
            return;
        }
    }

    /** Creates a mob with the next server id, ready to be added to {@link #mobs}. */
    private Mob newMob(Mob.Type type, float x, float y, float z) {
        Mob mob = new Mob(type, x, y, z);
        mob.id = nextMobId++;
        return mob;
    }

    /** The mob with the given id, or null (multiplayer attacks reference mobs by id). */
    public Mob mobById(int id) {
        for (Mob m : mobs) {
            if (m.id == id) return m;
        }
        return null;
    }

    /**
     * The mob nearest along the given view ray within {@code maxDist}, or null if
     * the player isn't aiming at one - the target of their attacks. The ray stops
     * at the first mob's box, so a mob in front of another is the one hit.
     */
    public Mob raycastMob(Vector3f origin, Vector3f dir, float maxDist) {
        Mob best = null;
        float bestT = maxDist;
        for (Mob m : mobs) {
            float t = Mob.rayIntersects(origin, dir, bestT, m.getAABB());
            if (t >= 0f) {
                best = m;
                bestT = t;
            }
        }
        return best;
    }

    /**
     * Damages a mob (knocking it back and panicking it); when its health runs out
     * it's removed and drops its raw meat. Returns true if the hit killed it.
     */
    public boolean damageMob(Mob mob, float amount, float sourceX, float sourceZ, Random rnd) {
        if (!mobs.contains(mob)) return false;
        boolean killed = mob.damage(amount, sourceX, sourceZ);
        if (killed) {
            mobs.remove(mob);
            BlockType drop = mob.dropType();
            int count = 1 + rnd.nextInt(mob.type == Mob.Type.SHEEP ? 2 : 3);
            spawnItem((int) Math.floor(mob.position.x), (int) Math.floor(mob.position.y),
                    (int) Math.floor(mob.position.z), drop, count, rnd);
        }
        return killed;
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
                storage.save(c, blockEntitiesInChunk(c));
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
