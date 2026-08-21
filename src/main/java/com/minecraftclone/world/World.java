package com.minecraftclone.world;

import com.minecraftclone.engine.Climate;
import com.minecraftclone.engine.LightningBolt;
import com.minecraftclone.engine.Shader;
import com.minecraftclone.engine.Weather;
import com.minecraftclone.engine.graphics.TextureAtlas;
import com.minecraftclone.player.Inventory;
import com.minecraftclone.player.ItemStack;
import com.minecraftclone.util.AABB;
import com.minecraftclone.Difficulty;
import com.minecraftclone.world.gen.EndGenerator;
import com.minecraftclone.world.gen.NetherGenerator;
import com.minecraftclone.world.gen.TerrainGenerator;
import com.minecraftclone.world.gen.TerrainGenerator.Biome;
import com.minecraftclone.world.gen.WorldGenSettings;
import com.minecraftclone.world.gen.WorldGenerator;
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
 * <p>
 * Each dimension of a save is its own {@link World} instance: its own
 * {@link WorldGenerator}, its own chunk-storage subdirectory, and the same
 * unbounded streaming behaviour. A game holds one World per
 * {@link DimensionType} and renders only the active one.
 */
public class World implements BlockAccessor {

    // Chunks are keyed by a packed long (chunkX in the high 32 bits, chunkZ in the
    // low 32) rather than a ChunkPos record, so the hot getBlock/setBlock lookups
    // don't allocate a key object on every call.
    private final Map<Long, Chunk> chunks = new HashMap<>();
    /** Per-block block entities (furnaces today, machines/chests tomorrow), keyed by {@link #blockKey}. */
    private final Map<Long, BlockEntity> blockEntities = new HashMap<>();
    private final WorldGenerator generator;
    private final TextureAtlas atlas;
    private final ChunkStorage storage;
    private final DimensionType dimension;
    private final MapData mapData = new MapData();

    private int renderDistance = 6;
    private boolean leavesTransparent = false;
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
    /** Map sampling of newly loaded chunks — same idea as mesh: don't hitch one frame. */
    private static final double MAP_SAMPLE_BUDGET_SECONDS = 0.004;
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

    // Seasonal surface updates (snow accumulation + water freezing) run on a
    // fixed wall-clock cadence for the same frame-rate independence reason as
    // the fluid tick above (see FLUID_TICK_SECONDS), and probe random columns
    // in a radius around the player so a blizzard visibly buries the ground
    // rather than costing a full-column sweep every tick.
    private static final double SNOW_UPDATE_SECONDS = 0.5;
    private static final int SNOW_RADIUS = 24;
    /** Freezing point: water freezes and snow sticks only where it's at or below this. */
    private static final float FREEZING_TEMP = 0f;
    /** Thaw point: accumulated snow melts and ice thaws only above this (a little hysteresis so a column stuck near 0°C doesn't flip-flop every tick). */
    private static final float THAW_TEMP = 1f;
    /** How deep a snow pile may get: a light coat in ordinary snow, buried under a blizzard. */
    private static final int SNOW_MAX_LAYERS_NORMAL = 1;
    private static final int SNOW_MAX_LAYERS_BLIZZARD = 3;

    // Lightning fire: each burning cell carries a per-block remaining-seconds
    // timer (keyed like block entities), ticked every frame by tickFires. Fires
    // are transient - lightning lights them, they spread to nearby flammable
    // blocks, get put out by water or rain, and burn out on their own.
    private static final float FIRE_BURN_MIN_SECONDS = 4f;
    private static final float FIRE_BURN_MAX_SECONDS = 9f;
    private static final float FIRE_SPREAD_CHANCE_PER_SECOND = 0.8f;
    private static final float FIRE_SPREAD_RADIUS = 2f;
    private static final float FIRE_MOB_DAMAGE_RADIUS = 1.7f;
    private static final float FIRE_MOB_DAMAGE_PER_SECOND = 6f;
    private static final int FIRE_MAX_ACTIVE = 48;
    private final Map<Long, Float> fires = new HashMap<>();
    private double lastSnowUpdateNanos = Double.NaN;
    private final Random snowRandom = new Random();

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

    public World(long seed, WorldGenSettings genSettings, TextureAtlas atlas, Path saveDir, DimensionType dimension) {
        this.dimension = dimension;
        this.generator = switch (dimension) {
            case NETHER -> new NetherGenerator(seed);
            case END -> new EndGenerator(seed);
            default -> new TerrainGenerator(seed, genSettings);
        };
        this.atlas = atlas;
        // Edited chunks for each dimension live in their own subdirectory, so
        // coordinates never collide across dimensions.
        this.storage = new ChunkStorage(saveDir.resolve(dimension.saveFolder()));

        // Register all multi-block definitions.
        // Add new definitions here as the game grows; order doesn't matter.
        multiBlockManager.register(com.minecraftclone.world.multiblock.SmelteryDefinition.INSTANCE);
    }

    public DimensionType getDimension() {
        return dimension;
    }

    public MapData getMapData() {
        return mapData;
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

    /**
     * Paint every generated chunk within {@link #renderDistance} of the player
     * onto the map. Nearby chunks first; work is time-budgeted so a 12-chunk
     * view fills in over a few frames instead of hitching. Terrain only —
     * ore-mix waypoints wait until the player finds the ore.
     */
    public void mapLoadedChunks(int playerChunkX, int playerChunkZ) {
        double start = System.nanoTime() / 1e9;
        visitRenderDistanceRing(playerChunkX, playerChunkZ, renderDistance, (cx, cz) -> {
            if (mapOneChunk(cx, cz, start)) return false;
            return true;
        });
    }

    /**
     * Walk every chunk in the Chebyshev square of radius {@code rd} around
     * the player (the same square chunk streaming uses), nearest first.
     * {@code visitor} returning false stops the walk.
     *
     * @return number of chunks visited
     */
    static int visitRenderDistanceRing(int playerChunkX, int playerChunkZ, int rd,
                                       java.util.function.BiPredicate<Integer, Integer> visitor) {
        if (!visitor.test(playerChunkX, playerChunkZ)) return 1;
        int visited = 1;
        for (int r = 1; r <= rd; r++) {
            for (int i = -r; i < r; i++) {
                if (!visitor.test(playerChunkX + i, playerChunkZ - r)) return visited + 1;
                visited++;
                if (!visitor.test(playerChunkX + r, playerChunkZ + i)) return visited + 1;
                visited++;
                if (!visitor.test(playerChunkX - i, playerChunkZ + r)) return visited + 1;
                visited++;
                if (!visitor.test(playerChunkX - r, playerChunkZ - i)) return visited + 1;
                visited++;
            }
        }
        return visited;
    }

    /** @return true when the map-sample time budget is spent after mapping a chunk. */
    private boolean mapOneChunk(int chunkX, int chunkZ, double startSeconds) {
        Chunk c = getChunk(chunkX, chunkZ);
        if (c == null || !c.isGenerated() || mapData.hasSurface(chunkX, chunkZ)) {
            return false;
        }
        mapData.exploreGeneratedChunk(c);
        return (System.nanoTime() / 1e9 - startSeconds) >= MAP_SAMPLE_BUDGET_SECONDS;
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

        // Doubles and quads change a neighbour's texture, including the
        // diagonal of a 2x2 that can sit across four chunks.
        if (old == BlockType.CHEST || type == BlockType.CHEST) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dz == 0) continue;
                    int ncx = worldToChunk(worldX + dx);
                    int ncz = worldToChunk(worldZ + dz);
                    if (ncx != cx || ncz != cz) markNeighborDirty(ncx, ncz);
                }
            }
        }

        // Notify the multi-block manager so smeltery (and future) structures can
        // form or deform when their structural blocks change.
        multiBlockManager.onBlockChanged(this, worldX, worldY, worldZ);

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

            // Cactus, bamboo, and seaweed lose support when the block below is removed -
            // break all stacked blocks above to simulate gravity (they fall and break).
            BlockType above = getBlock(worldX, worldY + 1, worldZ);
            if (above == BlockType.CACTUS || above == BlockType.BAMBOO || above == BlockType.SEAWEED) {
                breakStackedPlant(worldX, worldY + 1, worldZ, above);
            }
            // Vines hang downward, so breaking a vine breaks all vines below it.
            if (old == BlockType.VINE) {
                breakHangingPlant(worldX, worldY - 1, worldZ, BlockType.VINE);
            }
        }
    }

    /** Recursively break stacked plant blocks (cactus/bamboo/seaweed) above the removed block. */
    private void breakStackedPlant(int x, int y, int z, BlockType plantType) {
        if (plantType == BlockType.SEAWEED) {
            BlockType overlay = getOverlay(x, y, z);
            if (overlay == plantType) {
                setOverlay(x, y, z, BlockType.AIR);
                breakStackedPlant(x, y + 1, z, plantType);
            }
        } else {
            BlockType block = getBlock(x, y, z);
            if (block == plantType) {
                setBlock(x, y, z, BlockType.AIR);
                breakStackedPlant(x, y + 1, z, plantType);
            }
        }
    }

    /** Recursively break hanging plant blocks (vines/seaweed) below the removed block. */
    private void breakHangingPlant(int x, int y, int z, BlockType plantType) {
        if (plantType == BlockType.SEAWEED) {
            BlockType overlay = getOverlay(x, y, z);
            if (overlay == plantType) {
                setOverlay(x, y, z, BlockType.AIR);
                breakHangingPlant(x, y - 1, z, plantType);
            }
        } else {
            BlockType block = getBlock(x, y, z);
            if (block == plantType) {
                setBlock(x, y, z, BlockType.AIR);
                breakHangingPlant(x, y - 1, z, plantType);
            }
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
     * immediately beside it on any horizontal axis (a Minecraft-style double
     * chest), or a 108-slot container when four chests form a 2x2 square (a
     * "quad chest"). Pairing looks at the placed {@link BlockType#CHEST} block,
     * not the entity, so a neighbour that has never been opened still joins,
     * and north-south pairs merge the same way east-west ones do. Each block
     * keeps its own persisted slots - the merge is just a combined view,
     * ordered so the layout is stable whichever half you open.
     */
    public com.minecraftclone.player.StorageContainer chestContainerAt(int x, int y, int z) {
        return Chest.containerAt(x, y, z,
                (cx, cy, cz) -> getBlock(cx, cy, cz) == BlockType.CHEST,
                this::getOrCreateChest);
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

    /** Returns the Part Builder entity at a position, creating (and registering) it on first use. */
    public com.minecraftclone.world.tinkers.PartBuilderEntity getOrCreatePartBuilder(int x, int y, int z) {
        BlockEntity existing = blockEntities.get(blockKey(x, y, z));
        if (existing instanceof com.minecraftclone.world.tinkers.PartBuilderEntity pb) return pb;
        com.minecraftclone.world.tinkers.PartBuilderEntity pb = new com.minecraftclone.world.tinkers.PartBuilderEntity();
        blockEntities.put(blockKey(x, y, z), pb);
        return pb;
    }

    /** Returns the Tool Station entity at a position, creating (and registering) it on first use. */
    public com.minecraftclone.world.tinkers.ToolStationEntity getOrCreateToolStation(int x, int y, int z) {
        BlockEntity existing = blockEntities.get(blockKey(x, y, z));
        if (existing instanceof com.minecraftclone.world.tinkers.ToolStationEntity ts) return ts;
        com.minecraftclone.world.tinkers.ToolStationEntity ts = new com.minecraftclone.world.tinkers.ToolStationEntity();
        blockEntities.put(blockKey(x, y, z), ts);
        return ts;
    }

    /** Forgets a block entity - call when its block is mined or removed. */
    public void removeBlockEntity(int x, int y, int z) {
        blockEntities.remove(blockKey(x, y, z));
    }

    /**
     * Removes a block entity and immediately dirties the surrounding 3×3 chunks
     * so that the renderer picks up the visual change (e.g. the lit front tile of
     * a Smeltery Controller reverting to its unlit tile after the structure deforms).
     * This mirrors the dirty-on-form logic in {@link #registerMultiBlockEntity}.
     */
    public void removeBlockEntityAndRemesh(int x, int y, int z) {
        blockEntities.remove(blockKey(x, y, z));
        int cx = worldToChunk(x), cz = worldToChunk(z);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                markNeighborDirty(cx + dx, cz + dz);
            }
        }
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

    /** Drops in-memory block entities that lived in an unloading chunk (already saved if modified). */
    private void removeBlockEntitiesInChunk(Chunk c) {
        int minX = c.getOriginX();
        int minZ = c.getOriginZ();
        blockEntities.entrySet().removeIf(e -> {
            int x = keyX(e.getKey());
            int z = keyZ(e.getKey());
            return x >= minX && x < minX + Chunk.SIZE && z >= minZ && z < minZ + Chunk.SIZE;
        });
    }

    /**
     * After a chunk is inserted, try to form any smeltery whose controller is
     * in this chunk, and retry already-loaded controllers now that a neighbor
     * may have arrived (PatternValidator aborts when a neighbor isn't generated).
     */
    private void tryFormMultiblocksInChunk(Chunk chunk) {
        int originX = chunk.getOriginX();
        int originZ = chunk.getOriginZ();
        short[] raw = chunk.getRawBlocks();
        short controllerId = BlockType.SMELTERY_CONTROLLER.id;
        for (int i = 0; i < raw.length; i++) {
            if (raw[i] != controllerId) continue;
            int lx = i % Chunk.SIZE;
            int tmp = i / Chunk.SIZE;
            int lz = tmp % Chunk.SIZE;
            int ly = tmp / Chunk.SIZE;
            multiBlockManager.tryFormAt(this, originX + lx, ly, originZ + lz);
        }
        int pad = 8; // SmelteryDefinition.scanRadius() == maxInterior + 1
        int minX = originX - pad;
        int maxX = originX + Chunk.SIZE + pad;
        int minZ = originZ - pad;
        int maxZ = originZ + Chunk.SIZE + pad;
        for (Map.Entry<Long, BlockEntity> e : blockEntities.entrySet()) {
            if (e.getValue().blockType() != BlockType.SMELTERY_CONTROLLER) continue;
            int x = keyX(e.getKey());
            int z = keyZ(e.getKey());
            if (x >= minX && x < maxX && z >= minZ && z < maxZ) {
                multiBlockManager.tryFormAt(this, x, keyY(e.getKey()), z);
            }
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

    /** Height of the highest non-air block at the given world column (or the generator's sea level if the chunk isn't loaded). */
    public int getSurfaceHeight(int worldX, int worldZ) {
        for (int y = Chunk.HEIGHT - 1; y >= 0; y--) {
            if (getBlock(worldX, y, worldZ) != BlockType.AIR) {
                return y;
            }
        }
        return generator.seaLevel();
    }

    /**
     * The natural terrain height at the given world column - the surface the
     * generator produced, ignoring any blocks the player has placed or removed.
     * Used as the climate's underground/surface reference so a player-built roof
     * doesn't fool the temperature model into thinking a cave is at the surface.
     */
    public int getTerrainHeight(int worldX, int worldZ) {
        return generator.terrainHeight(worldX, worldZ);
    }

    /** Sets the per-block facing hint (used by doors) at a world position. */
    public void setBlockOrientation(int worldX, int worldY, int worldZ, byte orientation) {
        int cx = worldToChunk(worldX), cz = worldToChunk(worldZ);
        Chunk chunk = getChunk(cx, cz);
        if (chunk == null) return;
        chunk.setOrientation(Math.floorMod(worldX, Chunk.SIZE), worldY, Math.floorMod(worldZ, Chunk.SIZE), orientation);
        chunk.markDirty();
    }

    /** The per-block facing hint at a world position (0 if unset) - used for stair/fence collision. */
    public byte getBlockOrientation(int worldX, int worldY, int worldZ) {
        int cx = worldToChunk(worldX), cz = worldToChunk(worldZ);
        Chunk chunk = getChunk(cx, cz);
        if (chunk == null) return 0;
        return chunk.getOrientation(Math.floorMod(worldX, Chunk.SIZE), worldY, Math.floorMod(worldZ, Chunk.SIZE));
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

        // Load / generate nearest-first so the chunks in front of you exist
        // before far corners of the view. Budgeted by wall-clock time (see
        // GENERATE_BUDGET_SECONDS).
        long generateDeadline = System.nanoTime() + (long) (GENERATE_BUDGET_SECONDS * 1e9);
        int[] generated = {0};
        visitRenderDistanceRing(pcx, pcz, renderDistance, (cx, cz) -> {
            int dx = cx - pcx, dz = cz - pcz;
            if (dx * dx + dz * dz > renderDistance * renderDistance) return true;
            if (chunks.containsKey(key(cx, cz))) return true;
            if (generated[0] > 0 && System.nanoTime() >= generateDeadline) return false;
            loadOrGenerateChunk(cx, cz);
            generated[0]++;
            return true;
        });

        // Unload far chunks. Most frames unload nothing, so the removal list
        // is only allocated once something actually needs to go.
        int unloadRadius = renderDistance + 2;
        List<Chunk> toRemove = null;
        for (Chunk c : chunks.values()) {
            if (c.getPos().distanceSq(pcx, pcz) > (double) unloadRadius * unloadRadius) {
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
                // Notify multiblock manager so it can drop instances whose
                // controller lives in this chunk (shell-only unloads leave the
                // formed instance intact so the controller entity is saved).
                multiBlockManager.onChunkUnload(this, c.getPos().x(), c.getPos().z());
                removeBlockEntitiesInChunk(c);
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

        // Remesh: first-time meshes beat neighbor-seam remeshes (otherwise a
        // flying player keeps re-dirtying nearby chunks and the new ones at
        // the rim never get a mesh until you punch them). Then nearest-first.
        // Budgeted by wall-clock time (see MESH_BUDGET_SECONDS).
        List<Chunk> dirty = new ArrayList<>();
        for (Chunk c : chunks.values()) {
            if (c.needsMesh()) dirty.add(c);
        }
        dirty.sort((a, b) -> compareMeshQueue(
                a.needsFirstMesh(), a.getPos().distanceSq(pcx, pcz),
                b.needsFirstMesh(), b.getPos().distanceSq(pcx, pcz)));
        long meshDeadline = System.nanoTime() + (long) (MESH_BUDGET_SECONDS * 1e9);
        for (int i = 0; i < dirty.size(); i++) {
            if (i > 0 && System.nanoTime() >= meshDeadline) break;
            Chunk c = dirty.get(i);
            c.rebuildMesh(this, atlas, collectNearbyLights(c.getPos()), leavesTransparent);
        }
    }

    /**
     * First-time meshes jump the queue; otherwise nearest to the player.
     * A flying player constantly re-dirties nearby chunks, and without this
     * those remeshes starve new chunks at the rim — they exist (you can
     * punch them) but never appear until a block update.
     */
    static int compareMeshQueue(boolean aFirst, double aDist, boolean bFirst, double bDist) {
        int byMesh = Boolean.compare(bFirst, aFirst);
        if (byMesh != 0) return byMesh;
        return Double.compare(aDist, bDist);
    }

    /** Generate or load one chunk and stitch it to already-loaded neighbors. */
    private void loadOrGenerateChunk(int cx, int cz) {
        Chunk chunk = new Chunk(new ChunkPos(cx, cz));
        chunks.put(key(cx, cz), chunk);
        if (storage.hasSavedChunk(chunk.getPos())) {
            for (ChunkStorage.BlockEntitySave es : storage.load(chunk)) {
                if (getBlock(es.x(), es.y(), es.z()) == es.entity().blockType()) {
                    blockEntities.put(blockKey(es.x(), es.y(), es.z()), es.entity());
                    multiBlockManager.tryFormAt(this, es.x(), es.y(), es.z());
                }
            }
            short[] raw = chunk.getRawBlocks();
            boolean hadFire = false;
            for (int i = 0; i < raw.length; i++) {
                if (raw[i] == BlockType.FIRE.id) {
                    raw[i] = BlockType.AIR.id;
                    hadFire = true;
                }
            }
            if (hadFire) chunk.setRawBlocks(raw);
            // A corrupt/unreadable save would leave an empty hole in the world
            // forever because we skipped generate. Fall back to terrain.
            if (!chunk.isGenerated()) {
                generator.generate(chunk);
            }
        } else {
            generator.generate(chunk);
        }
        tryFormMultiblocksInChunk(chunk);
        markNeighborDirty(cx - 1, cz);
        markNeighborDirty(cx + 1, cz);
        markNeighborDirty(cx, cz - 1);
        markNeighborDirty(cx, cz + 1);
    }

    /**
     * Drives seasonal surface changes near the player: while it's freezing it
     * lays real {@link BlockType#SNOW} blocks on exposed surfaces (a thin coat
     * in ordinary snow, piled deeper and faster in a blizzard) and freezes
     * exposed water over to ice; once the weather warms it melts the
     * accumulated snow back away and thaws the ice. Both are gated per-column
     * by the local temperature, so in winter the snowline creeps down into
     * temperate biomes (and lakes freeze over) and it all retreats in spring -
     * while naturally snowy biomes (taiga, snowy, tundra, mountains, frozen
     * ocean) keep their snow.
     */
    public void updateSeasonalSurfaces(double dt, float playerX, float playerZ, Climate climate) {
        Weather weather = climate.getWeather();
        boolean snowing = weather == Weather.SNOW || weather == Weather.BLIZZARD;
        boolean blizzard = weather == Weather.BLIZZARD;
        double nowNanos = System.nanoTime();
        if (Double.isNaN(lastSnowUpdateNanos)) {
            lastSnowUpdateNanos = nowNanos;
        } else if ((nowNanos - lastSnowUpdateNanos) / 1e9 < SNOW_UPDATE_SECONDS) {
            return;
        } else {
            lastSnowUpdateNanos = nowNanos;
        }
        // Blizzards lay snow faster (and deeper), and melting works harder so
        // the snowline retreats at a visible pace once the weather warms up.
        int passes = snowing ? (blizzard ? 16 : 8) : 12;
        int px = (int) Math.floor(playerX);
        int pz = (int) Math.floor(playerZ);
        for (int i = 0; i < passes; i++) {
            int x = px + snowRandom.nextInt(SNOW_RADIUS * 2) - SNOW_RADIUS;
            int z = pz + snowRandom.nextInt(SNOW_RADIUS * 2) - SNOW_RADIUS;
            if (x == px && z == pz) continue; // never bury the player's own column
            if (snowing) tryAddSnow(x, z, climate, blizzard);
            else tryMeltSnow(x, z, climate);
            // Freezing is temperature-driven (independent of precipitation), but
            // never right around the player - a winter swim shouldn't cage you.
            if (Math.abs(x - px) > 1 || Math.abs(z - pz) > 1) {
                tryUpdateWater(x, z, climate);
            }
        }
    }

    private void tryAddSnow(int x, int z, Climate climate, boolean blizzard) {
        int y = findSurfaceY(x, z);
        if (y < 0) return;
        // Altitude-aware surface temperature: high mountain ledges freeze (and
        // snow) even when their foothills are above freezing.
        if (climate.temperatureFor(getBiome(x, z), y, y) > FREEZING_TEMP) return;
        BlockType surface = getBlock(x, y, z);
        if (surface.slab) {
            // Cap a bottom-half slab flush with snow by swapping in a snow-capped
            // slab (which meshes as a slab under a snow cap), so there's no
            // half-block gap above the slab's half-height top. Melting restores
            // the plain slab, and the block type itself remembers it - so a
            // saved-and-reloaded snowy slab still melts back correctly.
            if (getBlock(x, y + 1, z) != BlockType.AIR) return;
            setBlock(x, y, z, snowCapped(surface));
            return;
        }
        if (!surface.canHoldSnow()) return;
        if (getBlock(x, y + 1, z) != BlockType.AIR) return; // something's already there
        if (snowDepth(x, z) >= (blizzard ? SNOW_MAX_LAYERS_BLIZZARD : SNOW_MAX_LAYERS_NORMAL)) return;
        setBlock(x, y + 1, z, BlockType.SNOW);
    }

    private void tryMeltSnow(int x, int z, Climate climate) {
        Biome biome = getBiome(x, z);
        if (permanentSnow(biome)) return;
        int y = findSurfaceY(x, z);
        if (y < 0) return;
        // Altitude-aware: the top of a high mountain stays frozen past the thaw.
        if (climate.temperatureFor(biome, y, y) <= THAW_TEMP) return;
        BlockType top = getBlock(x, y, z);
        if (top.isSnowCappedSlab()) {
            setBlock(x, y, z, uncapped(top)); // the snow cap melts, the slab shows again
            return;
        }
        if (top == BlockType.SNOW && getBlock(x, y + 1, z) == BlockType.AIR) {
            setBlock(x, y, z, BlockType.AIR); // peel one layer off the top of the pile
        }
    }

    /** Freezes exposed water over to ice while it's freezing, thaws it back to water once warm. */
    private void tryUpdateWater(int x, int z, Climate climate) {
        int y = findSurfaceWaterY(x, z);
        if (y < 0) return;
        // Altitude-aware: high mountain lakes freeze earlier and thaw later.
        float temperature = climate.temperatureFor(getBiome(x, z), y, y);
        if (temperature <= FREEZING_TEMP) {
            if (getBlock(x, y + 1, z) == BlockType.AIR) {
                BlockType water = getBlock(x, y, z);
                // Only freeze static WATER, not WATER_SOURCE - sources keep flowing
                // even in winter so their flow field stays intact through freeze/thaw.
                if (water == BlockType.WATER) {
                    setBlock(x, y, z, BlockType.ICE);
                }
            }
        } else if (temperature > THAW_TEMP) {
            if (getBlock(x, y, z) == BlockType.ICE && getBlock(x, y + 1, z) == BlockType.AIR) {
                setBlock(x, y, z, BlockType.WATER); // the surface ice thaws back into water
            }
        }
    }

    /**
     * The top-most exposed water-surface cell (WATER, or ICE when the lake is
     * frozen over - both with only air above), or -1 if the column has none.
     * Returning ice too is what lets the thaw branch actually reach a frozen
     * surface once it warms up.
     */
    private int findSurfaceWaterY(int x, int z) {
        for (int y = Chunk.HEIGHT - 1; y >= 0; y--) {
            BlockType b = getBlock(x, y, z);
            if (b == BlockType.WATER || b == BlockType.ICE) return y;
            if (b != BlockType.AIR) return -1; // solid (ground, a roof) sits above any water
        }
        return -1;
    }

    /** The block an exposed water cell becomes at freezing temperatures: ice, or the same block unchanged. */
    static BlockType frozenForm(BlockType water) {
        return (water == BlockType.WATER || water == BlockType.WATER_SOURCE) ? BlockType.ICE : water;
    }

    /** The block a surface cell becomes once it thaws: water, or the same block unchanged. */
    static BlockType thawedForm(BlockType block) {
        return block == BlockType.ICE ? BlockType.WATER : block;
    }

    /** The snow-capped-slab form of a plain bottom-half slab. */
    private static BlockType snowCapped(BlockType slab) {
        return slab == BlockType.STONE_SLAB ? BlockType.SNOWY_STONE_SLAB : BlockType.SNOWY_PLANKS_SLAB;
    }

    /** The plain bottom-half slab a snow-capped slab melts back to. */
    private static BlockType uncapped(BlockType capped) {
        return capped == BlockType.SNOWY_STONE_SLAB ? BlockType.STONE_SLAB : BlockType.PLANKS_SLAB;
    }

    /** Result of a lightning strike: the cosmetic bolt (or null) plus player damage. */
    public record LightningStrikeResult(LightningBolt bolt, float playerDamage) {
    }

    /**
     * A lightning strike landing at {@code targetX/targetZ}: lights a fire at
     * the surface (and sets any nearby flammable blocks alight), blasts mobs and
     * the player in the immediate area, and returns the cosmetic bolt for the
     * renderer (or null if there's nowhere to strike). The player-facing flash
     * and rumble are driven separately by the climate's thunderstorm.
     */
    public LightningStrikeResult strikeLightning(Random rnd, float targetX, float targetZ, Vector3f playerPos) {
        int x = (int) Math.floor(targetX);
        int z = (int) Math.floor(targetZ);
        int surfaceY = findSurfaceY(x, z);
        if (surfaceY < 0) return new LightningStrikeResult(null, 0f);

        // Light the ground on fire at the strike point, plus flammable neighbors
        // (a struck tree actually catches).
        igniteCell(x, surfaceY + 1, z);
        for (int dx = -(int) FIRE_SPREAD_RADIUS; dx <= FIRE_SPREAD_RADIUS; dx++) {
            for (int dz = -(int) FIRE_SPREAD_RADIUS; dz <= FIRE_SPREAD_RADIUS; dz++) {
                if (rnd.nextFloat() < 0.5f) {
                    int fy = findSurfaceY(x + dx, z + dz);
                    if (fy >= 0 && isFlammable(getBlock(x + dx, fy, z + dz))) {
                        igniteCell(x + dx, fy + 1, z + dz);
                    }
                }
            }
        }

        // Blast anything living within a few blocks of the strike point.
        float strikeX = x + 0.5f;
        float strikeZ = z + 0.5f;
        List<Mob> nearbyMobs = new ArrayList<>();
        for (Mob mob : mobs) {
            float dx = mob.position.x - strikeX;
            float dz = mob.position.z - strikeZ;
            if (dx * dx + dz * dz <= 16f) { // ~4-block radius
                nearbyMobs.add(mob);
            }
        }
        for (Mob mob : nearbyMobs) {
            damageMob(mob, 10f, strikeX, strikeZ, rnd);
        }

        // Damage player within the same radius.
        float playerDamage = 0f;
        if (playerPos != null) {
            float dx = playerPos.x - strikeX;
            float dz = playerPos.z - strikeZ;
            if (dx * dx + dz * dz <= 16f) {
                playerDamage = 10f;
            }
        }

        return new LightningStrikeResult(
                new LightningBolt(strikeX, surfaceY + 46f, strikeZ,
                        strikeX, surfaceY + 1f, strikeZ, rnd),
                playerDamage);
    }

    /**
     * Ticks every burning fire cell by {@code dt}: they burn out on their own,
     * spread to nearby flammable blocks, hurt mobs standing in or next to them,
     * and are doused by water. Call once per frame.
     */
    public void tickFires(float dt) {
        if (fires.isEmpty()) return;
        List<Long> gone = null;
        for (Map.Entry<Long, Float> e : fires.entrySet()) {
            long key = e.getKey();
            int x = keyX(key), y = keyY(key), z = keyZ(key);
            // Validate the block is still fire; remove timer if not.
            if (getBlock(x, y, z) != BlockType.FIRE) {
                if (gone == null) gone = new ArrayList<>();
                gone.add(key);
                continue;
            }
            // Doused by water?
            boolean wet = isWet(x + 1, y, z) || isWet(x - 1, y, z)
                    || isWet(x, y, z + 1) || isWet(x, y, z - 1) || isWet(x, y + 1, z);
            float remaining = wet ? 0f : e.getValue() - dt;
            if (remaining <= 0f) {
                if (getBlock(x, y, z) == BlockType.FIRE) setBlock(x, y, z, BlockType.AIR);
                if (gone == null) gone = new ArrayList<>();
                gone.add(key);
            } else {
                e.setValue(remaining);
            }
        }
        if (gone != null) {
            for (Long key : gone) fires.remove(key);
        }
        if (fires.isEmpty()) return;

        // Spread to flammable neighbors and burn anything standing in the flames.
        // Snapshot the keys and mobs: spreading adds fires and burning kills mobs,
        // both of which would otherwise trip a concurrent-modification exception.
        List<Long> burning = new ArrayList<>(fires.keySet());
        List<Mob> burnList = null;
        for (Long key : burning) {
            if (snowRandom.nextFloat() < FIRE_SPREAD_CHANCE_PER_SECOND * dt
                    && fires.size() < FIRE_MAX_ACTIVE) {
                spreadFrom(keyX(key), keyY(key), keyZ(key));
            }
            for (Mob mob : mobs) {
                float dx = mob.position.x - (keyX(key) + 0.5f);
                float dy = mob.position.y - (keyY(key) + 0.5f);
                float dz = mob.position.z - (keyZ(key) + 0.5f);
                if (dx * dx + dy * dy + dz * dz <= FIRE_MOB_DAMAGE_RADIUS * FIRE_MOB_DAMAGE_RADIUS) {
                    if (burnList == null) burnList = new ArrayList<>();
                    burnList.add(mob);
                }
            }
        }
        if (burnList != null) {
            for (Mob mob : burnList) {
                damageMob(mob, FIRE_MOB_DAMAGE_PER_SECOND * dt, mob.position.x, mob.position.z, snowRandom);
            }
        }
    }

    private void spreadFrom(int x, int y, int z) {
        int[][] dirs = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
        for (int[] d : dirs) {
            int nx = x + d[0], ny = y + d[1], nz = z + d[2];
            BlockType n = getBlock(nx, ny, nz);
            if (isFlammable(n)) {
                igniteCell(nx, ny, nz);
                return;
            }
        }
    }

    private void igniteCell(int x, int y, int z) {
        if (y < 0 || y >= Chunk.HEIGHT) return;
        if (getBlock(x, y, z) == BlockType.FIRE) return;
        if (getBlock(x, y, z) != BlockType.AIR) return;
        if (fires.size() >= FIRE_MAX_ACTIVE) return;
        setBlock(x, y, z, BlockType.FIRE);
        fires.put(blockKey(x, y, z), FIRE_BURN_MIN_SECONDS + snowRandom.nextFloat() * (FIRE_BURN_MAX_SECONDS - FIRE_BURN_MIN_SECONDS));
    }

    /** True if this block is a fire hazard a struck/neighbouring flame can spread to. */
    static boolean isFlammable(BlockType block) {
        return block == BlockType.LEAVES || block == BlockType.WOOD_LOG
                || block == BlockType.PLANKS || block == BlockType.CHERRY_LEAVES;
    }

    private boolean isWet(int x, int y, int z) {
        if (y < 0 || y >= Chunk.HEIGHT) return false;
        return getBlock(x, y, z).isWater();
    }

    /** The y of the top-most non-air, non-fluid block in a column, or -1 if none. */
    private int findSurfaceY(int x, int z) {
        for (int y = Chunk.HEIGHT - 1; y >= 0; y--) {
            BlockType b = getBlock(x, y, z);
            if (b != BlockType.AIR && !b.isFluid()) return y;
        }
        return -1;
    }

    /** How many stacked snow blocks a column's surface carries (1 = a natural snow floor). */
    private int snowDepth(int x, int z) {
        int y = findSurfaceY(x, z);
        if (y < 0 || getBlock(x, y, z) != BlockType.SNOW) return 0;
        int depth = 0;
        while (y >= 0 && getBlock(x, y, z) == BlockType.SNOW) {
            depth++;
            y--;
        }
        return depth;
    }

    /** Biomes whose ground is naturally snow (generated as snow), so it never melts away. */
    private static boolean permanentSnow(Biome b) {
        return b == Biome.TAIGA || b == Biome.SNOWY || b == Biome.TUNDRA
                || b == Biome.MOUNTAIN || b == Biome.FROZEN_OCEAN;
    }

    /**
     * Renders every loaded chunk whose bounding box is inside {@code projection * view}'s
     * view frustum - at max render distance, most of the (up to a few hundred) loaded
     * chunks are behind or well off to the side of the camera at any given moment, so
     * skipping those avoids paying for their draw calls (and the GL state changes/vertex
     * shader invocations that go with them) every single frame for geometry that was
     * never going to end up on screen anyway.
     * <p>
     * Call {@link #renderOpaque} then entities then {@link #renderTranslucent} so
     * swimming mobs sit under the water instead of floating on top of it (water
     * doesn't write depth).
     */
    public void render(Shader shader, Matrix4f projection, Matrix4f view) {
        renderOpaque(projection, view);
        renderTranslucent();
    }

    /** Opaque terrain, including ice (writes depth so water can't show through a frozen sheet). */
    public void renderOpaque(Matrix4f projection, Matrix4f view) {
        projection.mul(view, viewProjection);
        frustum.set(viewProjection);

        visibleChunks.clear();
        for (Chunk chunk : chunks.values()) {
            if (isChunkVisible(chunk)) visibleChunks.add(chunk);
        }
        for (Chunk chunk : visibleChunks) {
            chunk.render();
        }
    }

    /**
     * Water, lava, glass. Depth writes off so overlapping translucent faces
     * blend instead of punching holes. Must run after entities so a swimming
     * mob is already in the depth buffer and the water surface covers the
     * submerged part of the body.
     */
    public void renderTranslucent() {
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
        if (type == null || count <= 0) return;
        ItemEntity e = new ItemEntity(type, count, blockX + 0.5f, blockY + 0.5f, blockZ + 0.5f);
        e.velocity.set((rnd.nextFloat() - 0.5f) * 1.5f, 2.5f + rnd.nextFloat() * 1.5f, (rnd.nextFloat() - 0.5f) * 1.5f);
        items.add(e);
    }

    /** Spawns a dropped {@link ItemStack}, preserving any Tinkers' payload. */
    public void spawnItem(int blockX, int blockY, int blockZ, ItemStack stack, java.util.Random rnd) {
        if (stack == null || stack.isEmpty()) return;
        ItemEntity e = new ItemEntity(stack.type(), stack.count(),
                blockX + 0.5f, blockY + 0.5f, blockZ + 0.5f, stack.tinkersItem());
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
                    if (e.tinkersItem != null) {
                        ItemStack leftover = inventory.addStack(e.asStack());
                        if (leftover.isEmpty()) {
                            pickedUp = true;
                            it.remove();
                        }
                    } else {
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
        }
        return pickedUp;
    }

    public int getLoadedChunkCount() {
        return chunks.size();
    }

    /**
     * Returns an unmodifiable view of all currently loaded chunks.
     * Used by the farming random-tick system to iterate over simulation-range chunks.
     */
    public java.util.Collection<Chunk> getLoadedChunks() {
        return java.util.Collections.unmodifiableCollection(chunks.values());
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
     *
     * @param targetable whether hostiles are allowed to notice/chase/attack the
     *                   player this frame - false in creative/spectator (see
     *                   GameMode#isInvulnerable), where mobs already can't land a
     *                   hit and so should just ignore the player and wander like
     *                   passives, rather than uselessly stalking someone they can
     *                   never actually hurt. Spawning/despawning around the
     *                   player's position still happens either way.
     * @param difficulty  world difficulty: Peaceful despawns hostiles and never
     *                   spawns them; Easy/Hard scale damage and spawn rate.
     */
    public float updateMobs(float dt, Vector3f playerPos, AABB playerBox, boolean night, Random rnd, boolean targetable) {
        return updateMobs(dt, playerPos, playerBox, night, rnd, targetable, Difficulty.NORMAL);
    }

    public float updateMobs(float dt, Vector3f playerPos, AABB playerBox, boolean night, Random rnd,
                            boolean targetable, Difficulty difficulty) {
        if (difficulty == null) difficulty = Difficulty.NORMAL;
        float despawnSq = MOB_DESPAWN_RADIUS * MOB_DESPAWN_RADIUS;
        Vector3f targetPos = targetable ? playerPos : null;
        float damage = 0f;
        float damageMul = difficulty.mobDamageMultiplier();
        for (Iterator<Mob> it = mobs.iterator(); it.hasNext(); ) {
            Mob mob = it.next();
            float dx = mob.position.x - playerPos.x;
            float dz = mob.position.z - playerPos.z;
            // The undead are gone by daylight (they burn/melt at dawn); everything
            // far away or fallen out of the world despawns. Wild animals (wolves,
            // bears) don't have the dawn-despawn flag, so they stay out by day.
            // Peaceful despawns every hostile immediately.
            boolean gone = mob.isHostile() && (
                    !difficulty.allowsHostileMobs()
                            || (mob.type.dawnDespawns && !night));
            if (gone || dx * dx + dz * dz > despawnSq || mob.position.y < -64f) {
                it.remove();
                continue;
            }
            mob.update(dt, this, rnd, targetPos);
            // Remove dead mobs immediately after update completes, using the same
            // removal path as combat deaths, so a mob killed by drowning doesn't
            // continue rendering or attacking on subsequent frames.
            if (mob.isDead()) {
                it.remove();
                continue;
            }
            damage += mob.getMeleeRequest() * damageMul;
            if (mob.wantsToShoot()) {
                spawnArrow(mob, playerPos, rnd);
            }
        }

        if (difficulty.allowsHostileMobs()
                && night && hostileCount() < difficulty.maxHostiles()
                && rnd.nextInt(difficulty.hostileSpawnOdds()) == 0) {
            trySpawnHostile(rnd, playerPos.x, playerPos.z);
        }
        if (mobs.size() < MAX_MOBS && rnd.nextInt(MOB_SPAWN_ODDS) == 0) {
            trySpawnMob(rnd, playerPos.x, playerPos.z, difficulty);
        }

        damage += updateArrows(dt, playerBox) * damageMul;
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
        spawnInitialMobs(rnd, playerWorldX, playerWorldZ, count, Difficulty.NORMAL);
    }

    public void spawnInitialMobs(Random rnd, float playerWorldX, float playerWorldZ, int count, Difficulty difficulty) {
        if (difficulty == null) difficulty = Difficulty.NORMAL;
        for (int i = 0; i < count && mobs.size() < MAX_MOBS; i++) {
            trySpawnMob(rnd, playerWorldX, playerWorldZ, difficulty);
        }
    }

    /** Adds a specific mob at a world position - used by autotest screenshots (e.g. one floating in water). */
    public void spawnMobAt(Mob.Type type, float x, float y, float z) {
        if (mobs.size() < MAX_MOBS) {
            mobs.add(new Mob(type, x, y, z));
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
            mobs.add(new Mob(type, x + 0.5f, y + 1f + type.height / 2f, z + 0.5f));
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

    /** Spawns one mob on a random grass/snow surface within range; does nothing if no spot qualifies. */
    private void trySpawnMob(Random rnd, float playerWorldX, float playerWorldZ, Difficulty difficulty) {
        int radius = (int) MOB_SPAWN_RADIUS;
        for (int attempt = 0; attempt < 6; attempt++) {
            int x = (int) Math.floor(playerWorldX) + rnd.nextInt(radius * 2) - radius;
            int z = (int) Math.floor(playerWorldZ) + rnd.nextInt(radius * 2) - radius;
            if (!isFullyGenerated(x, z)) continue;
            int y = getSurfaceHeight(x, z);
            if (y < 1 || y >= Chunk.HEIGHT - 1) continue;
            BlockType surface = getBlock(x, y, z);
            if (surface != BlockType.GRASS && surface != BlockType.SNOW) continue;
            if (getBlock(x, y + 1, z) != BlockType.AIR) continue;
            Mob.Type type = pickSurfaceMobType(rnd, getBiome(x, z), difficulty);
            if (type.hostile && !difficulty.allowsHostileMobs()) continue;
            mobs.add(new Mob(type, x + 0.5f, y + 1f + type.height / 2f, z + 0.5f));
            return;
        }
    }

    /**
     * Picks which mob fills a surface-spawn slot. The original common pool (pigs,
     * cows, sheep, zombies, skeletons) stays as it was; the wild predators are
     * rare, biome-tied bonuses - a wolf turns up in wooded biomes now and then,
     * a polar bear very rarely in the frozen wastes - so the better fur pelts are
     * harder to come by.
     */
    static Mob.Type pickSurfaceMobType(Random rnd, TerrainGenerator.Biome biome) {
        return pickSurfaceMobType(rnd, biome, Difficulty.NORMAL);
    }

    static Mob.Type pickSurfaceMobType(Random rnd, TerrainGenerator.Biome biome, Difficulty difficulty) {
        if (difficulty != null && !difficulty.allowsHostileMobs()) {
            Mob.Type[] passives = {Mob.Type.PIG, Mob.Type.COW, Mob.Type.SHEEP};
            return passives[rnd.nextInt(passives.length)];
        }
        float roll = rnd.nextFloat();
        boolean woods = biome == TerrainGenerator.Biome.FOREST || biome == TerrainGenerator.Biome.TAIGA
                || biome == TerrainGenerator.Biome.CHERRY_GROVE || biome == TerrainGenerator.Biome.FLOWER_MEADOW;
        boolean frozen = biome == TerrainGenerator.Biome.SNOWY || biome == TerrainGenerator.Biome.TUNDRA
                || biome == TerrainGenerator.Biome.FROZEN_OCEAN || biome == TerrainGenerator.Biome.MOUNTAIN;
        if (frozen && roll < 0.04f) return Mob.Type.POLAR_BEAR; // rare, and dangerous
        if (woods && roll < 0.14f) return Mob.Type.WOLF;        // uncommon
        Mob.Type[] common = {Mob.Type.PIG, Mob.Type.COW, Mob.Type.SHEEP,
                Mob.Type.ZOMBIE, Mob.Type.SKELETON};
        return common[rnd.nextInt(common.length)];
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
            // Sheep are woolly: killing one also drops wool to make fur armor with.
            if (mob.type == Mob.Type.SHEEP) {
                spawnItem((int) Math.floor(mob.position.x), (int) Math.floor(mob.position.y),
                        (int) Math.floor(mob.position.z), BlockType.WOOL, 1 + rnd.nextInt(3), rnd);
            }
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

    // =========================================================================
    // Multi-block system
    // =========================================================================

    /** The multi-block manager — tracks formed structures and drives formation / deformation. */
    private final com.minecraftclone.world.multiblock.MultiBlockManager multiBlockManager =
            new com.minecraftclone.world.multiblock.MultiBlockManager();

    /**
     * Returns the multi-block manager.  Call {@code manager().register(def)} at startup
     * for each {@link com.minecraftclone.world.multiblock.MultiBlockDefinition}.
     */
    public com.minecraftclone.world.multiblock.MultiBlockManager multiBlockManager() {
        return multiBlockManager;
    }

    /**
     * Register a {@link com.minecraftclone.world.multiblock.MultiBlockEntity} at the
     * given controller position.  Called by
     * {@link com.minecraftclone.world.multiblock.MultiBlockManager} when a structure forms.
     * The entity is stored in the shared {@code blockEntities} map so it participates in
     * the existing tick, persistence, and active-light systems without duplication.
     */
    public void registerMultiBlockEntity(int x, int y, int z,
            com.minecraftclone.world.multiblock.MultiBlockEntity entity) {
        blockEntities.put(blockKey(x, y, z), entity);
        // Dirty the surrounding chunks so the lit-front tile updates immediately.
        int cx = worldToChunk(x), cz = worldToChunk(z);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                markNeighborDirty(cx + dx, cz + dz);
            }
        }
    }

    /**
     * The formed multi-block instance whose controller is at {@code (x, y, z)},
     * or {@code null} if no structure is formed there.
     */
    public com.minecraftclone.world.multiblock.MultiBlockInstance multiBlockAt(int x, int y, int z) {
        return multiBlockManager.instanceAt(x, y, z);
    }

    /**
     * The formed multi-block instance whose bounding box contains {@code (x, y, z)},
     * or {@code null}.
     */
    public com.minecraftclone.world.multiblock.MultiBlockInstance multiBlockContaining(int x, int y, int z) {
        return multiBlockManager.instanceContaining(x, y, z);
    }
}
