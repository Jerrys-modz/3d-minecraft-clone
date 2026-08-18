package com.minecraftclone.world;

import com.minecraftclone.engine.graphics.Mesh;
import com.minecraftclone.engine.graphics.TextureAtlas;
import com.minecraftclone.util.FloatArray;
import com.minecraftclone.util.IntArray;

import java.util.ArrayList;
import java.util.List;

/**
 * A SIZE x HEIGHT x SIZE column of blocks, plus the GPU mesh built from its
 * currently-visible faces. Vertex positions are baked in absolute world
 * coordinates so chunks can be rendered with no per-chunk model matrix.
 */
public class Chunk implements ChunkStorage.PersistableChunk {

    public static final int SIZE = 16;
    public static final int HEIGHT = 256;

    // Face shading factors, faking simple fixed directional lighting.
    private static final float LIGHT_TOP = 1.0f;
    private static final float LIGHT_BOTTOM = 0.4f;
    private static final float LIGHT_NORTH_SOUTH = 0.75f;
    private static final float LIGHT_EAST_WEST = 0.6f;

    private final ChunkPos pos;
    private final byte[] blocks = new byte[SIZE * HEIGHT * SIZE];
    // Per-block facing hint for doors (0:+Z, 1:-Z, 2:+X, 3:-X) - only doors use it.
    private final byte[] orientations = new byte[SIZE * HEIGHT * SIZE];
    // Created lazily on first mesh/upload, so a Chunk can be constructed (and even
    // fully generated) without a live GL context - e.g. from a unit test, or off
    // the main thread later. Only the GL thread ever touches it.
    private Mesh mesh;
    private Mesh translucentMesh;
    // Local positions (+ light level) of every light-emitting block (torches) currently
    // in this chunk, kept incrementally up to date - see setLocal/setRawBlocks. Small and
    // rare enough that a flat list beats a spatial index; consulted by rebuildMesh to bake
    // a static, non-occlusion-aware glow around each one (see World.collectNearbyLights).
    private final List<int[]> lightSources = new ArrayList<>();
    // Local positions of every flowing-fluid block (source or flow) currently in
    // this chunk, kept incrementally up to date - consulted by World's fluid sim.
    private final List<int[]> fluidBlocks = new ArrayList<>();
    // Per-block fluid flow "level" (0 = right next to a source, up to FluidSim's
    // max distance) - only meaningful where blocks[] holds a *_FLOW type; grades
    // the rendered surface height (see fluidTop) so flow visibly thins out with
    // distance. Not persisted (flow cells themselves aren't saved either), and
    // default-zero everywhere else is exactly what a non-flow cell should read.
    private final byte[] fluidLevels = new byte[SIZE * HEIGHT * SIZE];
    // Decoration layered *inside* a cell alongside its primary block - e.g.
    // seaweed growing inside a water cell instead of replacing it, the way
    // Minecraft's waterlogged plants work (see BlockType#isSubmersible). AIR
    // (0) means no overlay; a full parallel array rather than a sparse list
    // since it's read once per rebuilt cell right next to blocks[] anyway.
    // Persisted alongside blocks[] (see getRawOverlays/setRawOverlays) -
    // unlike fluidLevels, this isn't cheap to regenerate on load (world-gen
    // only runs once, when a chunk is first created).
    private final byte[] overlays = new byte[SIZE * HEIGHT * SIZE];

    // Highest Y that's ever held a non-air block, updated incrementally in
    // setLocal - lets rebuildMesh skip scanning the (often large) stretch of
    // empty sky above the terrain instead of always walking the full 128-tall
    // column. Deliberately a *high-water mark*, not a live "current highest
    // block": breaking the topmost block doesn't lower it back down, since
    // that would need an expensive rescan to find the new true top - it just
    // means rebuildMesh scans a few needless empty layers until something
    // taller is eventually built there, which is always safe (never causes a
    // real block above it to be skipped).
    private int highestNonAirY = -1;

    // rebuildMesh's scratch vertex/index buffers, kept as reused instance
    // state instead of freshly allocated on every call. A chunk that's
    // remeshed repeatedly - a fluid pool re-flowing, a torch dirtying its
    // neighbors, the "see-through leaves" toggle marking every loaded chunk
    // dirty at once - used to pay for both the allocation and the geometric
    // regrowth (several doubling reallocations + array copies, since a fresh
    // FloatArray/IntArray always restarts at its small initial capacity)
    // again from scratch each time, even though the mesh is usually a
    // similar size to last time. Reused buffers only pay that growth cost
    // once, the first time a chunk's mesh reaches its largest-ever size -
    // rebuildMesh always calls clear() before refilling them, so stale data
    // from a previous build never leaks through.
    private final FloatArray meshVertices = new FloatArray(4096);
    private final IntArray meshIndices = new IntArray(4096);
    private final FloatArray meshTransVertices = new FloatArray(1024);
    private final IntArray meshTransIndices = new IntArray(1024);

    private volatile boolean dirty = true;
    private boolean generated = false;
    private boolean hasMeshData = false;
    private boolean hasTranslucentData = false;
    private boolean modifiedByPlayer = false;
    private boolean leavesTransparent = false;

    public Chunk(ChunkPos pos) {
        this.pos = pos;
    }

    public ChunkPos getPos() {
        return pos;
    }

    public int getOriginX() {
        return pos.x() * SIZE;
    }

    public int getOriginZ() {
        return pos.z() * SIZE;
    }

    public boolean isGenerated() {
        return generated;
    }

    public void markGenerated() {
        generated = true;
    }

    public boolean isDirty() {
        return dirty;
    }

    public void markDirty() {
        dirty = true;
    }

    private static int index(int x, int y, int z) {
        return (y * SIZE + z) * SIZE + x;
    }

    private static boolean inBounds(int x, int y, int z) {
        return x >= 0 && x < SIZE && y >= 0 && y < HEIGHT && z >= 0 && z < SIZE;
    }

    public BlockType getLocal(int x, int y, int z) {
        if (!inBounds(x, y, z)) return BlockType.AIR;
        return BlockType.byId(blocks[index(x, y, z)]);
    }

    /** The stored facing hint for a door block (0:+Z, 1:-Z, 2:+X, 3:-X). */
    public byte getOrientation(int x, int y, int z) {
        if (!inBounds(x, y, z)) return 0;
        return orientations[index(x, y, z)];
    }

    public void setOrientation(int x, int y, int z, byte orientation) {
        if (inBounds(x, y, z)) {
            orientations[index(x, y, z)] = orientation;
        }
    }

    public void setLocal(int x, int y, int z, BlockType type) {
        setLocal(x, y, z, type, 0);
    }

    /**
     * Like {@link #setLocal(int, int, int, BlockType)}, but also records a
     * fluid flow level (0 = right next to a source; ignored for non-flow
     * types) - see {@link #fluidLevels} and World's fluid simulation.
     */
    public void setLocal(int x, int y, int z, BlockType type, int level) {
        if (!inBounds(x, y, z)) return;
        BlockType old = getLocal(x, y, z);
        if (old.isLightSource()) removeLightSource(x, y, z);
        if (old.isFlowingFluid()) removeFluid(x, y, z);
        blocks[index(x, y, z)] = type.id;
        fluidLevels[index(x, y, z)] = (byte) level;
        if (type != BlockType.AIR && y > highestNonAirY) highestNonAirY = y;
        if (type.isLightSource()) lightSources.add(new int[]{x, y, z, type.lightLevel});
        if (type.isFlowingFluid()) fluidBlocks.add(new int[]{x, y, z});
        // An overlay decoration (see #overlays) only makes sense growing inside a
        // fluid cell - if this cell's primary block just became something else
        // (solid stone, air, ...), any overlay it had stops making sense too and
        // would otherwise sit there forever, invisibly baked into whatever
        // replaced the fluid (and rendering right through a solid replacement).
        if (!type.isFluid() && overlays[index(x, y, z)] != BlockType.AIR.id) {
            overlays[index(x, y, z)] = BlockType.AIR.id;
        }
        // A replaced block loses whatever facing it had (a door torn out and
        // rebuilt, or a furnace mined and re-placed). Directional blocks get a
        // fresh facing set explicitly by the caller right after this. Doors
        // keep their facing when toggled open/closed - only a *different* kind
        // of block replacing them clears it.
        boolean sameDoorFamily = (old.isDoor() && type.isDoor()) || (old.isTrapdoor() && type.isTrapdoor());
        if (!sameDoorFamily && old != type && orientations[index(x, y, z)] != 0) {
            orientations[index(x, y, z)] = 0;
        }
        dirty = true;
    }

    /** This block's fluid flow level (see {@link #fluidLevels}); 0 for anything that isn't a tracked flow block. */
    public int getFluidLevel(int x, int y, int z) {
        if (!inBounds(x, y, z)) return 0;
        return fluidLevels[index(x, y, z)];
    }

    /** The decoration layered inside this cell (see {@link #overlays}); {@link BlockType#AIR} if none. */
    public BlockType getOverlay(int x, int y, int z) {
        if (!inBounds(x, y, z)) return BlockType.AIR;
        return BlockType.byId(overlays[index(x, y, z)]);
    }

    /**
     * Sets (or clears, with {@link BlockType#AIR}) the decoration layered
     * inside this cell, independent of whatever its primary block is - see
     * {@link #overlays}. Doesn't touch {@link #blocks} at all, so placing or
     * clearing an overlay never disturbs the fluid (or whatever else) it's
     * sitting inside.
     */
    public void setOverlay(int x, int y, int z, BlockType type) {
        if (!inBounds(x, y, z)) return;
        overlays[index(x, y, z)] = type.id;
        dirty = true;
    }

    private void removeLightSource(int x, int y, int z) {
        lightSources.removeIf(s -> s[0] == x && s[1] == y && s[2] == z);
    }

    private void removeFluid(int x, int y, int z) {
        fluidBlocks.removeIf(s -> s[0] == x && s[1] == y && s[2] == z);
    }

    /** Local positions of every light-emitting block in this chunk, as {x, y, z, lightLevel}. Treat as read-only. */
    public List<int[]> getLocalLightSources() {
        return lightSources;
    }

    /** Local positions of every flowing-fluid block (source or flow) in this chunk, as {x, y, z}. Treat as read-only. */
    public List<int[]> getLocalFluidBlocks() {
        return fluidBlocks;
    }

    /** See {@link #highestNonAirY}: the top Y rebuildMesh needs to scan up to (inclusive). */
    public int getHighestNonAirY() {
        return highestNonAirY;
    }

    /** Like {@link #setLocal}, but also flags the chunk as needing to be saved to disk when it unloads. */
    public void setLocalFromPlayer(int x, int y, int z, BlockType type) {
        setLocal(x, y, z, type);
        modifiedByPlayer = true;
    }

    /** Like {@link #setOverlay}, but also flags the chunk as needing to be saved to disk when it unloads. */
    public void setOverlayFromPlayer(int x, int y, int z, BlockType type) {
        setOverlay(x, y, z, type);
        modifiedByPlayer = true;
    }

    public boolean isModifiedByPlayer() {
        return modifiedByPlayer;
    }

    public void markModifiedByPlayer() {
        modifiedByPlayer = true;
    }

    /** Raw block-id array for serialization. Returns the live backing array - treat as read-only. */
    public byte[] getRawBlocks() {
        return blocks;
    }

    /** Replaces this chunk's block data wholesale, e.g. when loading a saved chunk from disk. */
    public void setRawBlocks(byte[] data) {
        if (data.length != blocks.length) {
            throw new IllegalArgumentException("Expected " + blocks.length + " bytes, got " + data.length);
        }
        System.arraycopy(data, 0, blocks, 0, blocks.length);
        // Fluid levels aren't persisted (flow cells themselves usually aren't
        // either - see setFluidBlock) - reset to 0 and let the next fluid tick
        // recompute proper levels for any flow cells that happened to be saved.
        java.util.Arrays.fill(fluidLevels, (byte) 0);
        dirty = true;
        rebuildLightSourceIndex();
        rebuildFluidIndex();
    }

    /** Raw overlay-id array for serialization (see {@link #overlays}). Returns the live backing array - treat as read-only. */
    public byte[] getRawOverlays() {
        return overlays;
    }

    /** Replaces this chunk's overlay data wholesale, e.g. when loading a saved chunk from disk. */
    public void setRawOverlays(byte[] data) {
        if (data.length != overlays.length) {
            throw new IllegalArgumentException("Expected " + overlays.length + " bytes, got " + data.length);
        }
        System.arraycopy(data, 0, overlays, 0, overlays.length);
        dirty = true;
    }

    /** Raw orientation-byte array for serialization (see {@link #orientations}). Returns the live backing array - treat as read-only. */
    public byte[] getRawOrientations() {
        return orientations;
    }

    /** Replaces this chunk's orientation data wholesale, e.g. when loading a saved chunk from disk. */
    public void setRawOrientations(byte[] data) {
        if (data.length != orientations.length) {
            throw new IllegalArgumentException("Expected " + orientations.length + " bytes, got " + data.length);
        }
        System.arraycopy(data, 0, orientations, 0, orientations.length);
        dirty = true;
    }

    /**
     * Full O(chunk volume) rescan of light sources and the mesh-scan height
     * bound (see {@link #highestNonAirY}) - only needed after a wholesale
     * block replacement (disk load), which bypasses setLocal's incremental
     * bookkeeping. Combined into one pass since both need to visit every cell.
     */
    private void rebuildLightSourceIndex() {
        lightSources.clear();
        highestNonAirY = -1;
        for (int y = 0; y < HEIGHT; y++) {
            for (int z = 0; z < SIZE; z++) {
                for (int x = 0; x < SIZE; x++) {
                    BlockType t = getLocal(x, y, z);
                    if (t.isLightSource()) lightSources.add(new int[]{x, y, z, t.lightLevel});
                    if (t != BlockType.AIR && y > highestNonAirY) highestNonAirY = y;
                }
            }
        }
    }

    /** Full rescan of flowing-fluid blocks - only needed after a wholesale block replacement (disk load). */
    private void rebuildFluidIndex() {
        fluidBlocks.clear();
        for (int y = 0; y < HEIGHT; y++) {
            for (int z = 0; z < SIZE; z++) {
                for (int x = 0; x < SIZE; x++) {
                    if (getLocal(x, y, z).isFlowingFluid()) fluidBlocks.add(new int[]{x, y, z});
                }
            }
        }
    }

    /**
     * Rebuilds the CPU-side vertex data and uploads it to the GPU. Call from the main (GL) thread.
     *
     * @param nearbyLights every light-emitting block within reach of this chunk (this chunk's own
     *                     plus its neighbors', see {@code World.collectNearbyLights}), as world-space
     *                     {wx, wy, wz, lightLevel} - used to bake a static glow around each one.
     * @param leavesTransparent whether the "see-through leaves" setting is on: leaves then use the
     *                          alpha-cutout atlas tile and stop occluding faces toward them (so you
     *                          can see through a canopy). Rebuild meshes after toggling it.
     */
    public void rebuildMesh(BlockAccessor world, TextureAtlas atlas, List<int[]> nearbyLights, boolean leavesTransparent) {
        this.leavesTransparent = leavesTransparent;
        FloatArray vertices = meshVertices;
        IntArray indices = meshIndices;
        vertices.clear();
        indices.clear();
        int[] vertexCounter = {0};
        FloatArray transVertices = meshTransVertices;
        IntArray transIndices = meshTransIndices;
        transVertices.clear();
        transIndices.clear();
        int[] transCounter = {0};

        int originX = getOriginX();
        int originZ = getOriginZ();

        // Skip scanning the empty sky above the terrain (see highestNonAirY) -
        // ocean/plains chunks in particular can leave well over half of the
        // 128-tall column as air that would otherwise be walked on every
        // single rebuild for nothing.
        int topY = Math.min(HEIGHT - 1, highestNonAirY);
        for (int y = 0; y <= topY; y++) {
            for (int z = 0; z < SIZE; z++) {
                for (int x = 0; x < SIZE; x++) {
                    BlockType block = getLocal(x, y, z);
                    BlockType overlay = getOverlay(x, y, z);
                    if (block == BlockType.AIR && overlay == BlockType.AIR) continue;

                    int wx = originX + x;
                    int wy = y;
                    int wz = originZ + z;
                    float blockLight = computeBlockLight(nearbyLights, wx + 0.5f, wy + 0.5f, wz + 0.5f);

                    if (overlay != BlockType.AIR) {
                        // A decoration living inside its cell's primary block (see
                        // BlockType#isSubmersible) - e.g. seaweed inside a water cell or
                        // lily pads on top of water, drawn differently depending on type.
                        // Emitted before the primary block below so it's still drawn
                        // even on the rare cell whose "primary" is AIR (an overlay
                        // whose fluid was somehow removed without clearing it too).
                        if (overlay == BlockType.SEAWEED) {
                            emitSeaweed(vertices, indices, vertexCounter, wx, wy, wz, overlay, atlas, blockLight);
                        } else if (overlay == BlockType.LILY_PAD) {
                            emitLilyPad(world, vertices, indices, vertexCounter, wx, wy, wz, block, overlay, atlas, blockLight);
                        } else {
                            emitCross(vertices, indices, vertexCounter, wx, wy, wz, overlay, atlas, blockLight);
                        }
                    }
                    if (block == BlockType.AIR) continue;

                    // Doors are thin panels: closed facing their stored direction,
                    // open swung 90 degrees on the hinge.
                    if (block.isDoor()) {
                        emitDoorPanel(vertices, indices, vertexCounter, wx, wy, wz,
                                getOrientation(x, y, z), block == BlockType.DOOR_OPEN, atlas, blockLight);
                        continue;
                    }
                    // Trapdoors: a flat horizontal hatch when closed, a vertical panel when open.
                    if (block.isTrapdoor()) {
                        emitTrapdoorPanel(vertices, indices, vertexCounter, wx, wy, wz,
                                getOrientation(x, y, z), block == BlockType.TRAPDOOR_OPEN, atlas, blockLight);
                        continue;
                    }
                    if (block.cross) {
                        // Decoration (grass/flowers): two crossed planes, always fully
                        // visible - no face culling, since it never covers a whole cell.
                        emitCross(vertices, indices, vertexCounter, wx, wy, wz, block, atlas, blockLight);
                        continue;
                    }
                    if (block.slab) {
                        emitSlab(world, vertices, indices, vertexCounter, wx, wy, wz, block, atlas, blockLight);
                        continue;
                    }
                    if (block.isBed()) {
                        emitBed(world, vertices, indices, vertexCounter, wx, wy, wz, x, y, z, block, atlas, blockLight);
                        continue;
                    }
                    if (block.isStair()) {
                        emitStairs(world, vertices, indices, vertexCounter, wx, wy, wz, block,
                                getOrientation(x, y, z), atlas, blockLight);
                        continue;
                    }
                    if (block.isFence()) {
                        emitFence(world, vertices, indices, vertexCounter, wx, wy, wz, block, atlas, blockLight);
                        continue;
                    }
                    if (block.isSnowCappedSlab()) {
                        emitSnowySlab(world, vertices, indices, vertexCounter, wx, wy, wz, block, atlas, blockLight);
                        continue;
                    }
                    if (block.isFluid()) {
                        emitFluid(world, vertices, indices, vertexCounter, wx, wy, wz, block, atlas, blockLight);
                        continue;
                    }

                    // Translucent blocks (glass, ice) go into their own batch, drawn
                    // after everything opaque so they blend over what's behind them.
                    FloatArray bv = block.isTranslucent() ? transVertices : vertices;
                    IntArray bi = block.isTranslucent() ? transIndices : indices;
                    int[] bc = block.isTranslucent() ? transCounter : vertexCounter;

                    // +Y top
                    if (isFaceVisible(world, x, y + 1, z, wx, wy + 1, wz, block)) {
                        emitFace(world, bv, bi, bc, wx, wy, wz, Face.TOP, block, atlas, blockLight);
                    }
                    // -Y bottom
                    if (isFaceVisible(world, x, y - 1, z, wx, wy - 1, wz, block)) {
                        emitFace(world, bv, bi, bc, wx, wy, wz, Face.BOTTOM, block, atlas, blockLight);
                    }
                    // +X east
                    if (isFaceVisible(world, x + 1, y, z, wx + 1, wy, wz, block)) {
                        emitFace(world, bv, bi, bc, wx, wy, wz, Face.EAST, block, atlas, blockLight);
                    }
                    // -X west
                    if (isFaceVisible(world, x - 1, y, z, wx - 1, wy, wz, block)) {
                        emitFace(world, bv, bi, bc, wx, wy, wz, Face.WEST, block, atlas, blockLight);
                    }
                    // +Z south
                    if (isFaceVisible(world, x, y, z + 1, wx, wy, wz + 1, block)) {
                        emitFace(world, bv, bi, bc, wx, wy, wz, Face.SOUTH, block, atlas, blockLight);
                    }
                    // -Z north
                    if (isFaceVisible(world, x, y, z - 1, wx, wy, wz - 1, block)) {
                        emitFace(world, bv, bi, bc, wx, wy, wz, Face.NORTH, block, atlas, blockLight);
                    }
                }
            }
        }

        if (mesh == null) {
            mesh = new Mesh();
        }
        mesh.upload(vertices.toArray(), indices.toArray());
        hasMeshData = indices.size() > 0;
        if (translucentMesh == null) {
            translucentMesh = new Mesh();
        }
        translucentMesh.upload(transVertices.toArray(), transIndices.toArray());
        hasTranslucentData = transIndices.size() > 0;
        dirty = false;
    }

    /**
     * A face is drawn if the neighboring cell (which may be outside this chunk) is
     * empty or translucent relative to the block owning the face. Water faces are
     * only drawn toward non-water (air/decoration/solid); solid faces are drawn
     * toward anything non-opaque, including translucent water, so you can see the
     * sea floor through the water instead of a see-through hole.
     */
    private boolean isFaceVisible(BlockAccessor world, int localX, int localY, int localZ, int worldX, int worldY, int worldZ, BlockType block) {
        if (worldY < 0) return false;   // treat below-bedrock as solid void: never draw bottom faces
        if (worldY >= HEIGHT) return true; // above world height is open sky: always draw top faces
        BlockType neighbor;
        if (inBounds(localX, localY, localZ)) {
            neighbor = getLocal(localX, localY, localZ);
        } else {
            neighbor = world.getBlock(worldX, worldY, worldZ);
        }

        if (block.isWater()) {
            // Translucent water: cull faces toward other water (and toward solid -
            // the solid's own face shows through the water instead).
            return neighbor == BlockType.AIR || neighbor.cross || neighbor.slab || neighbor.isStair() || neighbor.isFence();
        }

        if (block.isTranslucent()) {
            // Glass/ice: draw faces toward empty space, decoration and water, but
            // cull toward solid blocks and toward other translucent blocks (no
            // internal glass-to-glass faces).
            return neighbor == BlockType.AIR || neighbor.cross || neighbor.slab
                    || neighbor.isStair() || neighbor.isFence() || neighbor.isWater();
        }

        // Cross-shaped decoration (grass/flowers), slabs, stairs and fences don't
        // cover a full cell, and translucent water doesn't occlude, so a solid
        // neighbor's face toward them must still be drawn - treat them like air.
        if (neighbor == BlockType.AIR || neighbor.cross || neighbor.slab
                || neighbor.isStair() || neighbor.isFence() || neighbor.isWater()) return true;
        // Doors and trapdoors are thin panels, so they don't occlude the faces
        // around a doorway or hatch.
        if (neighbor.isDoor() || neighbor.isTrapdoor()) return true;
        // Glass/ice are see-through, so a block behind them still gets its face
        // drawn (it shows through the glass instead of a hole).
        if (neighbor.isTranslucent()) return true;
        // With see-through leaves on, leaf blocks stop occluding faces too - both
        // the leaf block's own faces and the blocks behind it get drawn, so the
        // cutout holes in the leaves texture actually show what's behind.
        return leavesTransparent && (neighbor == BlockType.LEAVES || neighbor == BlockType.CHERRY_LEAVES);
    }

    /**
     * Brightness floor (0..1) contributed by nearby light sources at world-space point
     * (px, py, pz): each source's contribution falls off linearly by 1/15 per block of
     * straight-line distance (Minecraft-style falloff, but distance- rather than
     * flood-fill-based - it isn't blocked by walls, a deliberate simplification). Zero if
     * nothing is close enough, which is the common case for most of the world.
     */
    private float computeBlockLight(List<int[]> nearbyLights, float px, float py, float pz) {
        float best = 0f;
        for (int[] src : nearbyLights) {
            float dx = px - (src[0] + 0.5f);
            float dy = py - (src[1] + 0.5f);
            float dz = pz - (src[2] + 0.5f);
            float dist = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
            float contribution = (src[3] - dist) / 15f;
            if (contribution > best) best = contribution;
        }
        return Math.min(1f, best);
    }

    private enum Face {TOP, BOTTOM, NORTH, SOUTH, EAST, WEST}

    /** The world face a block's front texture should be on, for a given orientation byte (0:+Z, 1:-Z, 2:+X, 3:-X). */
    private static Face frontFaceFor(byte orientation) {
        return switch (orientation) {
            case 0 -> Face.SOUTH; // +Z
            case 1 -> Face.NORTH; // -Z
            case 2 -> Face.EAST;  // +X
            case 3 -> Face.WEST;  // -X
            default -> Face.SOUTH;
        };
    }

    // Resting-flow surface height range: just under a source at level 0,
    // thinning down to a shallow sheet at the farthest level it can reach -
    // see fluidTop.
    private static final float FLOW_TOP_NEAR = 0.86f;
    private static final float FLOW_TOP_FAR = 0.25f;

    /**
     * Fluid surface height (above its cell's floor, 0..1): 0.9 for a static/source
     * block, full height for an unsupported (falling) flow, otherwise a resting
     * "puddle" height that grades down from {@link #FLOW_TOP_NEAR} right next to
     * its source to {@link #FLOW_TOP_FAR} at the farthest it's spread - real
     * flowing fluid visibly thins out with distance, not one fixed puddle depth.
     */
    private float fluidTop(BlockAccessor world, int wx, int wy, int wz, BlockType type) {
        if (type == BlockType.WATER || type == BlockType.LAVA || type.isFluidSource()) return 0.9f;
        BlockType below = world.getBlock(wx, wy - 1, wz);
        // Falling, or stacked on more fluid - a vertical waterfall/lavafall
        // column, per FluidSim's own "only spread sideways off solid ground"
        // rule (below.isFluid() there too) - full height either way, never
        // graded. Once a multi-block-tall fall has fully filled in, only its
        // very bottom cell actually has air below it, so "below is air" alone
        // can't tell a column cell apart from a resting puddle: every cell
        // above the bottom one was rendering at its graded - often much
        // shorter - puddle height instead of full, a visibly banded, gappy
        // waterfall.
        if (below == BlockType.AIR || below.cross || below.isFluid()) return 1f;
        // The one cell this doesn't cover: the very bottom of a fall, where it
        // lands on solid ground. Its own below is solid, so the checks above
        // graded it down like a resting puddle even though it's still being
        // actively fed from directly above - the falling column's box always
        // starts at this cell's own ceiling (y+1), so anything less than full
        // height here left a visible sliver gap right at the point of impact,
        // and with corner-averaging pulling its other corners toward the
        // puddle it had started spreading into, that sliver read as a short,
        // disconnected panel rather than water reaching the ground. A cell
        // with fluid directly above it must be full height to meet it with no
        // gap, regardless of what's below.
        if (world.getBlock(wx, wy + 1, wz).isFluid()) return 1f;
        int maxLevel = type.isWater() ? FluidSim.WATER_FLOW_DISTANCE : FluidSim.LAVA_FLOW_DISTANCE;
        float t = Math.min(world.getFluidLevel(wx, wy, wz), maxLevel) / (float) maxLevel;
        return FLOW_TOP_NEAR - t * (FLOW_TOP_NEAR - FLOW_TOP_FAR);
    }

    /** A gentle, fixed "current" direction for fluid that has no real away-from-source gradient - see {@link #fluidFlowDir}. */
    private static final float[] IDLE_FLOW_DIR = {0.7f, 0.7f};

    /**
     * The direction (in tile-UV space) the flow's <em>top</em> surface
     * animation should travel, so the water texture visibly creeps away
     * from its source instead of always scrolling straight down. The cell
     * flows away from its neighbor with the lowest stored level (nearest
     * the source); a source or a cell with no lower neighbor has no such
     * gradient to follow, but still isn't perfectly frozen - it drifts
     * along a fixed idle direction instead, so a calm lake or ocean has
     * some life to its surface rather than looking like a static image (see
     * {@link #IDLE_FLOW_DIR}). Side faces don't use this - a vertical wall
     * has no horizontal "away from source" direction to follow, so they
     * always just scroll straight down instead (see {@link #emitFluidSide}).
     */
    private static float[] fluidFlowDir(BlockAccessor world, int wx, int wy, int wz, BlockType block) {
        if (block.isFluidSource()) return IDLE_FLOW_DIR;
        int level = world.getFluidLevel(wx, wy, wz);
        int minLevel = level;
        float dirX = 0f, dirZ = 0f;
        int[][] offsets = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] o : offsets) {
            BlockType t = world.getBlock(wx + o[0], wy, wz + o[1]);
            if (t.isFluid()) {
                int l = world.getFluidLevel(wx + o[0], wy, wz + o[1]);
                if (l < minLevel) {
                    minLevel = l;
                    dirX = -o[0];
                    dirZ = -o[1];
                }
            }
        }
        return minLevel < level ? new float[]{dirX, dirZ} : IDLE_FLOW_DIR;
    }

    /** True for two blocks of the same fluid family (both water-ish, or both lava-ish) - see {@link #fluidCornerTop}. */
    private static boolean sameFluidFamily(BlockType a, BlockType b) {
        return (a.isWater() && b.isWater()) || (a.isLava() && b.isLava());
    }

    /** The block type at a world position, preferring this chunk's own array (cheaper) over the world lookup when it's local. */
    private BlockType fluidNeighborType(BlockAccessor world, int x, int y, int z) {
        int lx = x - getOriginX(), lz = z - getOriginZ();
        return inBounds(lx, y, lz) ? getLocal(lx, y, lz) : world.getBlock(x, y, z);
    }

    /**
     * A fluid top surface isn't one flat height across the whole block - each
     * of its 4 corners is averaged with the same-fluid-family cells sharing
     * that corner (the same technique Minecraft's own water rendering uses),
     * so two neighboring cells at different heights blend into one continuous
     * sloped surface that meets exactly at their shared edge, rather than
     * each having an independent flat top that doesn't quite line up with a
     * differently-graded neighbor (the source of several "gap" reports: a
     * flat single height per block just can't represent the surface dipping
     * toward a shorter neighbor on one side while staying full-height on the
     * others at the same time).
     * <p>
     * {@code cx, cz} is the corner's grid position - a corner of block
     * (wx, wz) is at (wx or wx+1, wz or wz+1). The four cells that touch a
     * given corner are the 2x2 block of cells around it. {@code selfTop} is
     * the emitting block's own (non-averaged) height - see below.
     */
    private float fluidCornerTop(BlockAccessor world, int cx, int wy, int cz, BlockType block, float selfTop) {
        float sum = 0f;
        for (int dx = -1; dx <= 0; dx++) {
            for (int dz = -1; dz <= 0; dz++) {
                int x = cx + dx, z = cz + dz;
                BlockType t = fluidNeighborType(world, x, wy, z);
                // A quadrant cell that isn't the same fluid family (open air,
                // solid ground, or just nothing filled there yet) has no
                // surface height of its own to contribute. Treating it as
                // "missing" and averaging over however many real neighbors
                // remain - one, at the ragged edge of a flow's reach - let a
                // single much-shorter or much-taller neighbor swing that
                // corner on its own, and because which of the 4 quadrant
                // cells exist changes from corner to corner along a diamond-
                // shaped flow boundary, adjacent blocks ended up with wildly
                // different corner heights right next to each other: a sharp
                // zigzag/sawtooth edge instead of a smooth taper. Standing in
                // for the missing cell with this block's own height instead
                // (always averaging over 4 terms) keeps a boundary corner
                // anchored close to its own block's natural height, nudged
                // only by whatever real neighbors happen to be there.
                sum += sameFluidFamily(t, block) ? fluidTop(world, x, wy, z, t) : selfTop;
            }
        }
        return sum / 4f;
    }

    /** Emits a lowered translucent surface for fluid instead of a solid full cube. */
    private void emitFluid(BlockAccessor world, FloatArray vertices, IntArray indices, int[] vertexCounter,
                            int wx, int wy, int wz, BlockType block, TextureAtlas atlas, float blockLight) {
        float[] uv = atlas.getUV(block.topTile);
        float[][] uvs = {{uv[0], uv[3]}, {uv[2], uv[3]}, {uv[2], uv[1]}, {uv[0], uv[1]}};
        float x0 = wx, y0 = wy, z0 = wz, x1 = wx + 1, z1 = wz + 1;
        // Every fluid animates its surface now, transient flow or a calm
        // source/terrain body alike - see fluidFlowDir's IDLE_FLOW_DIR for
        // what a body with no real flow gradient drifts along instead of
        // sitting frozen.
        float flow = 1f;
        // The top surface scrolls along the actual flow direction (away from the
        // source) rather than always straight down.
        float[] flowDir = fluidFlowDir(world, wx, wy, wz, block);

        float selfTop = fluidTop(world, wx, wy, wz, block);
        float yNW = wy + fluidCornerTop(world, wx, wy, wz, block, selfTop);
        float yNE = wy + fluidCornerTop(world, wx + 1, wy, wz, block, selfTop);
        float ySE = wy + fluidCornerTop(world, wx + 1, wy, wz + 1, block, selfTop);
        float ySW = wy + fluidCornerTop(world, wx, wy, wz + 1, block, selfTop);

        // isFaceVisible's water branch treats "fluid above" as fully hiding this
        // top face - true when every corner is already at full height, but a
        // corner pulled down by a shorter neighbor doesn't actually touch the
        // cell above it when that neighbor *isn't* fluid (its own geometry
        // only ever starts at this cell's ceiling, y+1, never lower). Skipping
        // the face there left a real gap - looking down through it showed
        // whatever was below (e.g. the floor a waterfall just landed on)
        // instead of this cell's own water surface.
        //
        // But when the cell directly above *is* the same fluid family, this
        // cell is genuinely submerged - fluidTop already gives a submerged
        // cell full height (1f) for itself, so any corner dip here only ever
        // comes from a shallower same-family neighbor at this same Y level
        // (an underwater ledge/step), not a real hole: that neighbor draws
        // its own top face to cover its own surface, and this cell stays
        // sealed above by more fluid either way. Drawing a top face here too
        // used to paint a spurious, wrongly-angled water surface partway
        // through open water - exactly what you'd see swimming past an
        // underwater ledge, a seam floating in the middle of the lake.
        boolean sealedByFluidAbove = sameFluidFamily(world.getBlock(wx, wy + 1, wz), block);
        float minCorner = Math.min(Math.min(yNW, yNE), Math.min(ySE, ySW));
        boolean topVisible = sealedByFluidAbove
                ? false
                : (minCorner < wy + 1f || isFaceVisible(world, wx - getOriginX(), wy + 1, wz - getOriginZ(), wx, wy + 1, wz, block));
        if (topVisible) {
            // A quad with 4 independently-graded corners usually isn't flat, so
            // splitting it into 2 triangles always bends it a little along
            // whichever diagonal gets picked - the two triangles meet there at
            // an angle, which reads as a faint crease/seam in the surface.
            // Always splitting along SW-NE (the fixed order below) put that
            // crease on whichever diagonal happened to have the *bigger* height
            // difference just as often as the smaller one, making it obvious.
            // Picking whichever diagonal's 2 endpoints are closer in height
            // keeps the bend as shallow as it can be for this corner data.
            boolean altDiagonal = Math.abs(ySE - yNW) < Math.abs(ySW - yNE);
            float[][] topPositions = altDiagonal
                    ? new float[][]{{x1, ySE, z1}, {x1, yNE, z0}, {x0, yNW, z0}, {x0, ySW, z1}}
                    : new float[][]{{x0, ySW, z1}, {x1, ySE, z1}, {x1, yNE, z0}, {x0, yNW, z0}};
            float[][] topUvs = altDiagonal
                    ? new float[][]{uvs[1], uvs[2], uvs[3], uvs[0]}
                    : uvs;
            emitQuad(vertices, indices, vertexCounter, topPositions, topUvs, LIGHT_TOP, blockLight, flow, flowDir[0], flowDir[1]);
        }
        if (isFaceVisible(world, wx - getOriginX(), wy - 1, wz - getOriginZ(), wx, wy - 1, wz, block)) {
            emitQuad(vertices, indices, vertexCounter,
                    new float[][]{{x0, y0, z0}, {x1, y0, z0}, {x1, y0, z1}, {x0, y0, z1}},
                    uvs, LIGHT_BOTTOM, blockLight, flow);
        }
        emitFluidSide(world, vertices, indices, vertexCounter, wx, wy, wz, block, blockLight, flow, Face.EAST, uvs, yNE, ySE);
        emitFluidSide(world, vertices, indices, vertexCounter, wx, wy, wz, block, blockLight, flow, Face.WEST, uvs, yNW, ySW);
        emitFluidSide(world, vertices, indices, vertexCounter, wx, wy, wz, block, blockLight, flow, Face.SOUTH, uvs, ySW, ySE);
        emitFluidSide(world, vertices, indices, vertexCounter, wx, wy, wz, block, blockLight, flow, Face.NORTH, uvs, yNW, yNE);
    }

    /**
     * A fluid block's vertical side face. {@code yA, yB} are the two corner
     * heights (absolute world Y) at this edge - normally the same values the
     * top face uses at its matching corners (see {@link #fluidCornerTop}),
     * so this face's top edge lines up with the top face exactly, whatever
     * shape it has. {@code yA} is the edge's "first" corner and {@code yB}
     * its "second", in the same order as each face's vertex winding below.
     */
    private void emitFluidSide(BlockAccessor world, FloatArray vertices, IntArray indices, int[] vertexCounter,
                                int wx, int wy, int wz, BlockType block, float blockLight, float flow,
                                Face face, float[][] uvs, float yA, float yB) {
        int nx = wx + (face == Face.EAST ? 1 : face == Face.WEST ? -1 : 0);
        int nz = wz + (face == Face.SOUTH ? 1 : face == Face.NORTH ? -1 : 0);
        int nlx = nx - getOriginX(), nlz = nz - getOriginZ();

        BlockType neighbor = inBounds(nlx, wy, nlz) ? getLocal(nlx, wy, nlz) : world.getBlock(nx, wy, nz);
        if (sameFluidFamily(neighbor, block)) {
            // fluidCornerTop falls back to *this* block's own height for any
            // quadrant cell that isn't fluid (see its doc) - which means the
            // neighbor across this edge, computing the very same shared
            // corners, can land on a different number: its fallback is its
            // own (different) height, not ours. Two independently-graded
            // cells meeting at a concave corner (e.g. two waterfall walls
            // landing next to each other) routinely disagree this way. The
            // corners usually still line up - but "usually" isn't "always",
            // and silently skipping the wall on the strength of "usually"
            // left a real hole (solid ground visible through the water)
            // exactly where they didn't. So actually check, using the
            // neighbor's own fallback the same way its own top face would.
            int[] cxA = {wx, wz}, cxB = {wx, wz};
            switch (face) {
                case EAST -> { cxA[0] = wx + 1; cxA[1] = wz; cxB[0] = wx + 1; cxB[1] = wz + 1; }
                case WEST -> { cxA[0] = wx; cxA[1] = wz; cxB[0] = wx; cxB[1] = wz + 1; }
                case SOUTH -> { cxA[0] = wx; cxA[1] = wz + 1; cxB[0] = wx + 1; cxB[1] = wz + 1; }
                case NORTH -> { cxA[0] = wx; cxA[1] = wz; cxB[0] = wx + 1; cxB[1] = wz; }
                default -> throw new IllegalArgumentException("Fluid side must be horizontal");
            }
            float neighborSelfTop = fluidTop(world, nx, wy, nz, neighbor);
            float nyA = wy + fluidCornerTop(world, cxA[0], wy, cxA[1], neighbor, neighborSelfTop);
            float nyB = wy + fluidCornerTop(world, cxB[0], wy, cxB[1], neighbor, neighborSelfTop);
            if (Math.abs(nyA - yA) < 0.001f && Math.abs(nyB - yB) < 0.001f) {
                // Corners genuinely agree - the neighbor's own top face
                // already meets this one exactly here, no wall needed.
                return;
            }
            // They don't: patch the gap with a thin curtain from this edge's
            // corners down (or up) to what the neighbor thinks they are,
            // rather than leaving whatever's below exposed. Double-sided
            // since either edge could be the taller one.
            float xA = cxA[0], zA = cxA[1], xB = cxB[0], zB = cxB[1];
            emitQuadBothSides(vertices, indices, vertexCounter,
                    new float[][]{{xA, yA, zA}, {xB, yB, zB}, {xB, nyB, zB}, {xA, nyA, zA}},
                    uvs, LIGHT_EAST_WEST, blockLight);
            return;
        }
        if (!isFaceVisible(world, nlx, wy, nlz, nx, wy, nz, block)) return;

        float x0 = wx, x1 = wx + 1, y0 = wy, z0 = wz, z1 = wz + 1;
        float[][] positions = switch (face) {
            case EAST -> new float[][]{{x1, y0, z1}, {x1, y0, z0}, {x1, yA, z0}, {x1, yB, z1}};
            case WEST -> new float[][]{{x0, y0, z0}, {x0, y0, z1}, {x0, yB, z1}, {x0, yA, z0}};
            case SOUTH -> new float[][]{{x0, y0, z1}, {x1, y0, z1}, {x1, yB, z1}, {x0, yA, z1}};
            case NORTH -> new float[][]{{x1, y0, z0}, {x0, y0, z0}, {x0, yA, z0}, {x1, yB, z0}};
            default -> throw new IllegalArgumentException("Fluid side must be horizontal");
        };
        float light = face == Face.NORTH || face == Face.SOUTH ? LIGHT_NORTH_SOUTH : LIGHT_EAST_WEST;
        // Every side face's V axis runs the same way regardless of which
        // direction it faces (see the uvs order above: v1 at the bottom
        // corners, v0 at the top ones), so a flowing side always scrolls
        // straight down its own texture - unlike the top face, there's no
        // horizontal "away from source" direction for a vertical wall to
        // follow. Previously these were left at (0,0) - always static -
        // because scrolling the *old* flat-banded tile sideways looked
        // wrong; now that it's a genuine vertical scroll on the wavy tile,
        // it reads as falling water instead.
        emitQuad(vertices, indices, vertexCounter, positions, uvs, light, blockLight, flow, 0f, flow > 0.5f ? 1f : 0f);
    }

    private void emitFace(BlockAccessor world, FloatArray vertices, IntArray indices, int[] vertexCounter,
                           int wx, int wy, int wz, Face face, BlockType block, TextureAtlas atlas, float blockLight) {
        int tile = switch (face) {
            case TOP -> block.topTile;
            case BOTTOM -> block.bottomTile;
            default -> {
                // A directional block (e.g. a furnace) shows its front tile on the
                // face it's oriented toward and its plain side tile on the rest.
                // While the block is "active" (a burning furnace), the front swaps
                // to its glowing variant.
                if (block.isDirectional() && face == frontFaceFor(getOrientation(wx - getOriginX(), wy, wz - getOriginZ()))) {
                    yield world.isBlockActive(wx, wy, wz) ? block.litFrontTile : block.frontTile;
                }
                yield block.sideTile;
            }
        };
        // See-through leaves use the alpha-cutout variant of the leaves texture
        // (the shader discards its transparent holes) so the canopy is translucent.
        if (leavesTransparent && (block == BlockType.LEAVES || block == BlockType.CHERRY_LEAVES)) {
            tile = TextureAtlas.LEAVES_CUTOUT_TILE;
        }
        float[] uv = atlas.getUV(tile);
        float u0 = uv[0], v0 = uv[1], u1 = uv[2], v1 = uv[3];
        float light = switch (face) {
            case TOP -> LIGHT_TOP;
            case BOTTOM -> LIGHT_BOTTOM;
            case NORTH, SOUTH -> LIGHT_NORTH_SOUTH;
            case EAST, WEST -> LIGHT_EAST_WEST;
        };

        float x0 = wx, y0 = wy, z0 = wz;
        float x1 = wx + 1, y1 = wy + 1, z1 = wz + 1;

        float[][] positions;
        float[][] uvs = {{u0, v1}, {u1, v1}, {u1, v0}, {u0, v0}};

        switch (face) {
            case TOP -> positions = new float[][]{{x0, y1, z1}, {x1, y1, z1}, {x1, y1, z0}, {x0, y1, z0}};
            case BOTTOM -> positions = new float[][]{{x0, y0, z0}, {x1, y0, z0}, {x1, y0, z1}, {x0, y0, z1}};
            case NORTH -> positions = new float[][]{{x1, y0, z0}, {x0, y0, z0}, {x0, y1, z0}, {x1, y1, z0}};
            case SOUTH -> positions = new float[][]{{x0, y0, z1}, {x1, y0, z1}, {x1, y1, z1}, {x0, y1, z1}};
            case EAST -> positions = new float[][]{{x1, y0, z1}, {x1, y0, z0}, {x1, y1, z0}, {x1, y1, z1}};
            case WEST -> positions = new float[][]{{x0, y0, z0}, {x0, y0, z1}, {x0, y1, z1}, {x0, y1, z0}};
            default -> throw new IllegalStateException();
        }

        emitQuad(vertices, indices, vertexCounter, positions, uvs, light, blockLight);
    }

    /**
     * Emits a cross-shaped decoration (two diagonal planes forming an "X" when
     * viewed from above), like grass tufts, flowers and torches. Each plane is
     * drawn as two quads with opposite winding so it's visible from both sides
     * without needing to disable backface culling.
     */
    /** The orientation a door swings to when open (90 degrees from its closed facing). */
    private static byte swingOrientation(byte o) {
        return switch (o) {
            case 0 -> 2; // +Z closed -> open toward +X
            case 1 -> 3; // -Z closed -> open toward -X
            case 2 -> 0; // +X closed -> open toward +Z
            default -> 1; // -X closed -> open toward -Z
        };
    }

    /** The thin vertical door panel quad for a facing direction. */
    private static float[][] doorPlane(int wx, int wy, int wz, byte orientation) {
        float thin = 0.06f;
        float x0 = wx, x1 = wx + 1, y0 = wy, y1 = wy + 1, z0 = wz, z1 = wz + 1;
        return switch (orientation) {
            case 1 -> new float[][]{{x0, y0, z0 + thin}, {x1, y0, z0 + thin}, {x1, y1, z0 + thin}, {x0, y1, z0 + thin}}; // -Z
            case 2 -> new float[][]{{x1 - thin, y0, z0}, {x1 - thin, y0, z1}, {x1 - thin, y1, z1}, {x1 - thin, y1, z0}}; // +X
            case 3 -> new float[][]{{x0 + thin, y0, z0}, {x0 + thin, y0, z1}, {x0 + thin, y1, z1}, {x0 + thin, y1, z0}}; // -X
            default -> new float[][]{{x0, y0, z1 - thin}, {x1, y0, z1 - thin}, {x1, y1, z1 - thin}, {x0, y1, z1 - thin}}; // +Z
        };
    }

    /** A door: a thin vertical panel, facing its stored direction (or swung open), both sides visible. */
    private void emitDoorPanel(FloatArray vertices, IntArray indices, int[] vertexCounter,
                               int wx, int wy, int wz, byte orientation, boolean open,
                               TextureAtlas atlas, float blockLight) {
        byte plane = open ? swingOrientation(orientation) : orientation;
        float[][] positions = doorPlane(wx, wy, wz, plane);
        float light = (plane == 2 || plane == 3) ? LIGHT_EAST_WEST : LIGHT_NORTH_SOUTH;
        float[] uv = atlas.getUV(BlockType.DOOR.topTile);
        float[][] uvs = {{uv[0], uv[3]}, {uv[2], uv[3]}, {uv[2], uv[1]}, {uv[0], uv[1]}};
        emitQuadBothSides(vertices, indices, vertexCounter, positions, uvs, light, blockLight);
    }

    /** A trapdoor: a thin flat hatch across the top of its cell (closed) or a vertical panel hung on its hinge (open). */
    private void emitTrapdoorPanel(FloatArray vertices, IntArray indices, int[] vertexCounter,
                                   int wx, int wy, int wz, byte orientation, boolean open,
                                   TextureAtlas atlas, float blockLight) {
        float thin = 0.12f;
        float x0 = wx, x1 = wx + 1, y0 = wy, y1 = wy + 1, z0 = wz, z1 = wz + 1;
        float[][] positions;
        float light;
        if (open) {
            positions = doorPlane(wx, wy, wz, orientation);
            light = (orientation == 2 || orientation == 3) ? LIGHT_EAST_WEST : LIGHT_NORTH_SOUTH;
        } else {
            positions = new float[][]{{x0, y1 - thin, z0}, {x1, y1 - thin, z0}, {x1, y1 - thin, z1}, {x0, y1 - thin, z1}};
            light = LIGHT_TOP;
        }
        float[] uv = atlas.getUV(BlockType.TRAPDOOR.topTile);
        float[][] uvs = {{uv[0], uv[3]}, {uv[2], uv[3]}, {uv[2], uv[1]}, {uv[0], uv[1]}};
        emitQuadBothSides(vertices, indices, vertexCounter, positions, uvs, light, blockLight);
    }

    private void emitCross(FloatArray vertices, IntArray indices, int[] vertexCounter,
                            int wx, int wy, int wz, BlockType block, TextureAtlas atlas, float blockLight) {
        float[] uv = atlas.getUV(block.topTile);
        float u0 = uv[0], v0 = uv[1], u1 = uv[2], v1 = uv[3];
        float[][] uvs = {{u0, v1}, {u1, v1}, {u1, v0}, {u0, v0}};
        float light = LIGHT_TOP;

        float x0 = wx, y0 = wy, z0 = wz, x1 = wx + 1, y1 = wy + 1, z1 = wz + 1;

        float[][] planeA = {{x0, y0, z0}, {x1, y0, z1}, {x1, y1, z1}, {x0, y1, z0}};
        float[][] planeB = {{x0, y0, z1}, {x1, y0, z0}, {x1, y1, z0}, {x0, y1, z1}};

        emitQuadBothSides(vertices, indices, vertexCounter, planeA, uvs, light, blockLight);
        emitQuadBothSides(vertices, indices, vertexCounter, planeB, uvs, light, blockLight);
    }

    /**
     * Emits seaweed with overlapping height so consecutive seaweed blocks overlap
     * slightly and hide the texture gaps between blocks. Since seaweed grows upward
     * from the ocean floor, we extend down 0.15 blocks to overlap with the block below.
     */
    private void emitSeaweed(FloatArray vertices, IntArray indices, int[] vertexCounter,
                             int wx, int wy, int wz, BlockType block, TextureAtlas atlas, float blockLight) {
        float[] uv = atlas.getUV(block.topTile);
        float u0 = uv[0], v0 = uv[1], u1 = uv[2], v1 = uv[3];
        float[][] uvs = {{u0, v1}, {u1, v1}, {u1, v0}, {u0, v0}};
        float light = LIGHT_TOP;

        float x0 = wx, z0 = wz, x1 = wx + 1, z1 = wz + 1;
        float y0 = wy - 0.15f;  // Extend down to overlap with block below
        float y1 = wy + 1.0f;   // Full height upward

        float[][] planeA = {{x0, y0, z0}, {x1, y0, z1}, {x1, y1, z1}, {x0, y1, z0}};
        float[][] planeB = {{x0, y0, z1}, {x1, y0, z0}, {x1, y1, z0}, {x0, y1, z1}};

        emitQuadBothSides(vertices, indices, vertexCounter, planeA, uvs, light, blockLight);
        emitQuadBothSides(vertices, indices, vertexCounter, planeB, uvs, light, blockLight);
    }

    /**
     * Emits a lily pad as a single flat quad on the surface of the water block.
     * Lily pads sit on top of water, not as cross decorations.
     */
    private void emitLilyPad(BlockAccessor world, FloatArray vertices, IntArray indices, int[] vertexCounter,
                             int wx, int wy, int wz, BlockType primaryBlock, BlockType overlay, TextureAtlas atlas, float blockLight) {
        float[] uv = atlas.getUV(overlay.topTile);
        float u0 = uv[0], v0 = uv[1], u1 = uv[2], v1 = uv[3];
        float[][] uvs = {{u0, v1}, {u1, v1}, {u1, v0}, {u0, v0}};
        float light = LIGHT_TOP;

        // Lily pad sits at the fluid surface height, using the same logic as water rendering
        float fluidHeight = fluidTop(world, wx, wy, wz, primaryBlock);
        float x0 = wx, y = wy + fluidHeight, z0 = wz, x1 = wx + 1, z1 = wz + 1;

        float[][] positions = {{x0, y, z1}, {x1, y, z1}, {x1, y, z0}, {x0, y, z0}};

        emitQuad(vertices, indices, vertexCounter, positions, uvs, light, blockLight);
    }

    /**
     * Emits a bottom-half slab: a top face at half height (always visible), a
     * bottom face only when nothing full sits below, and side faces only toward
     * air/cross neighbors (a full block covers the whole face, and an adjacent
     * slab shares the half-height face).
     */
    private void emitSlab(BlockAccessor world, FloatArray vertices, IntArray indices, int[] vertexCounter,
                          int wx, int wy, int wz, BlockType block, TextureAtlas atlas, float blockLight) {
        float[] uv = atlas.getUV(block.topTile);
        float u0 = uv[0], v0 = uv[1], u1 = uv[2], v1 = uv[3];
        float[][] uvs = {{u0, v1}, {u1, v1}, {u1, v0}, {u0, v0}};

        float x0 = wx, y0 = wy, z0 = wz, x1 = wx + 1, y1 = wy + 0.5f, z1 = wz + 1;

        // Top face is never covered from above (blocks start at whole-block heights).
        emitQuad(vertices, indices, vertexCounter,
                new float[][]{{x0, y1, z1}, {x1, y1, z1}, {x1, y1, z0}, {x0, y1, z0}},
                uvs, LIGHT_TOP, blockLight);

        // Bottom face: drawn unless a full-height block sits directly below.
        BlockType below = world.getBlock(wx, wy - 1, wz);
        if (below == BlockType.AIR || below.cross || below.slab || below.isStair() || below.isFence() || below.isWater() || below.isTranslucent() || below.isDoor() || below.isTrapdoor()) {
            emitQuad(vertices, indices, vertexCounter,
                    new float[][]{{x0, y0, z0}, {x1, y0, z0}, {x1, y0, z1}, {x0, y0, z1}},
                    uvs, LIGHT_BOTTOM, blockLight);
        }

        BlockType east = world.getBlock(wx + 1, wy, wz);
        if (east == BlockType.AIR || east.cross || east.slab || east.isStair() || east.isFence() || east.isWater() || east.isTranslucent() || east.isDoor() || east.isTrapdoor() || (leavesTransparent && (east == BlockType.LEAVES || east == BlockType.CHERRY_LEAVES))) {
            emitQuad(vertices, indices, vertexCounter,
                    new float[][]{{x1, y0, z1}, {x1, y0, z0}, {x1, y1, z0}, {x1, y1, z1}},
                    uvs, LIGHT_EAST_WEST, blockLight);
        }
        BlockType west = world.getBlock(wx - 1, wy, wz);
        if (west == BlockType.AIR || west.cross || west.slab || west.isStair() || west.isFence() || west.isWater() || west.isTranslucent() || west.isDoor() || west.isTrapdoor() || (leavesTransparent && (west == BlockType.LEAVES || west == BlockType.CHERRY_LEAVES))) {
            emitQuad(vertices, indices, vertexCounter,
                    new float[][]{{x0, y0, z0}, {x0, y0, z1}, {x0, y1, z1}, {x0, y1, z0}},
                    uvs, LIGHT_EAST_WEST, blockLight);
        }
        BlockType south = world.getBlock(wx, wy, wz + 1);
        if (south == BlockType.AIR || south.cross || south.slab || south.isStair() || south.isFence() || south.isWater() || south.isTranslucent() || south.isDoor() || south.isTrapdoor() || (leavesTransparent && (south == BlockType.LEAVES || south == BlockType.CHERRY_LEAVES))) {
            emitQuad(vertices, indices, vertexCounter,
                    new float[][]{{x0, y0, z1}, {x1, y0, z1}, {x1, y1, z1}, {x0, y1, z1}},
                    uvs, LIGHT_NORTH_SOUTH, blockLight);
        }
        BlockType north = world.getBlock(wx, wy, wz - 1);
        if (north == BlockType.AIR || north.cross || north.slab || north.isStair() || north.isFence() || north.isWater() || north.isTranslucent() || north.isDoor() || north.isTrapdoor() || (leavesTransparent && (north == BlockType.LEAVES || north == BlockType.CHERRY_LEAVES))) {
            emitQuad(vertices, indices, vertexCounter,
                    new float[][]{{x1, y0, z0}, {x0, y0, z0}, {x0, y1, z0}, {x1, y1, z0}},
                    uvs, LIGHT_NORTH_SOUTH, blockLight);
        }
    }

    /**
     * Emits a half-height directional bed block: top face at half height (always visible),
     * bottom face only when nothing full sits below, and side faces toward air/cross neighbors.
     * The front face (facing direction) uses a different texture than the sides.
     */
    private void emitBed(BlockAccessor world, FloatArray vertices, IntArray indices, int[] vertexCounter,
                        int wx, int wy, int wz, int x, int y, int z, BlockType block, TextureAtlas atlas, float blockLight) {
        byte orientation = getOrientation(x, y, z);
        
        float[] topUv = atlas.getUV(block.topTile);
        float[] sideUv = atlas.getUV(block.sideTile);
        float[] bottomUv = atlas.getUV(block.bottomTile);
        float[] frontUv = atlas.getUV(block.frontTile);
        
        float x0 = wx, y0 = wy, z0 = wz, x1 = wx + 1, y1 = wy + 0.5f, z1 = wz + 1;

        // Top face is never covered from above
        float[][] topUvs = {{topUv[0], topUv[3]}, {topUv[2], topUv[3]}, {topUv[2], topUv[1]}, {topUv[0], topUv[1]}};
        emitQuad(vertices, indices, vertexCounter,
                new float[][]{{x0, y1, z1}, {x1, y1, z1}, {x1, y1, z0}, {x0, y1, z0}},
                topUvs, LIGHT_TOP, blockLight);

        // Bottom face: drawn unless a full-height block sits directly below
        BlockType below = world.getBlock(wx, wy - 1, wz);
        if (below == BlockType.AIR || below.cross || below.slab || below.isStair() || below.isFence() || below.isWater() || below.isTranslucent() || below.isDoor() || below.isTrapdoor() || below.isBed()) {
            float[][] bottomUvs = {{bottomUv[0], bottomUv[1]}, {bottomUv[2], bottomUv[1]}, {bottomUv[2], bottomUv[3]}, {bottomUv[0], bottomUv[3]}};
            emitQuad(vertices, indices, vertexCounter,
                    new float[][]{{x0, y0, z0}, {x1, y0, z0}, {x1, y0, z1}, {x0, y0, z1}},
                    bottomUvs, LIGHT_BOTTOM, blockLight);
        }

        // Determine which face is the front (facing direction)
        // orientation: 0=+Z (south), 1=-Z (north), 2=+X (east), 3=-X (west)
        boolean frontOnEast = orientation == 2;
        boolean frontOnWest = orientation == 3;
        boolean frontOnSouth = orientation == 0;
        boolean frontOnNorth = orientation == 1;

        // East face (+X)
        BlockType east = world.getBlock(wx + 1, wy, wz);
        if (east == BlockType.AIR || east.cross || east.slab || east.isStair() || east.isFence() || east.isWater() || east.isTranslucent() || east.isDoor() || east.isTrapdoor() || east.isBed() || (leavesTransparent && east == BlockType.LEAVES)) {
            float[][] uvs = frontOnEast ? 
                new float[][]{{frontUv[0], frontUv[3]}, {frontUv[2], frontUv[3]}, {frontUv[2], frontUv[1]}, {frontUv[0], frontUv[1]}} :
                new float[][]{{sideUv[0], sideUv[3]}, {sideUv[2], sideUv[3]}, {sideUv[2], sideUv[1]}, {sideUv[0], sideUv[1]}};
            emitQuad(vertices, indices, vertexCounter,
                    new float[][]{{x1, y0, z1}, {x1, y0, z0}, {x1, y1, z0}, {x1, y1, z1}},
                    uvs, LIGHT_EAST_WEST, blockLight);
        }
        
        // West face (-X)
        BlockType west = world.getBlock(wx - 1, wy, wz);
        if (west == BlockType.AIR || west.cross || west.slab || west.isStair() || west.isFence() || west.isWater() || west.isTranslucent() || west.isDoor() || west.isTrapdoor() || west.isBed() || (leavesTransparent && west == BlockType.LEAVES)) {
            float[][] uvs = frontOnWest ? 
                new float[][]{{frontUv[2], frontUv[3]}, {frontUv[0], frontUv[3]}, {frontUv[0], frontUv[1]}, {frontUv[2], frontUv[1]}} :
                new float[][]{{sideUv[2], sideUv[3]}, {sideUv[0], sideUv[3]}, {sideUv[0], sideUv[1]}, {sideUv[2], sideUv[1]}};
            emitQuad(vertices, indices, vertexCounter,
                    new float[][]{{x0, y0, z0}, {x0, y0, z1}, {x0, y1, z1}, {x0, y1, z0}},
                    uvs, LIGHT_EAST_WEST, blockLight);
        }
        
        // South face (+Z)
        BlockType south = world.getBlock(wx, wy, wz + 1);
        if (south == BlockType.AIR || south.cross || south.slab || south.isStair() || south.isFence() || south.isWater() || south.isTranslucent() || south.isDoor() || south.isTrapdoor() || south.isBed() || (leavesTransparent && south == BlockType.LEAVES)) {
            float[][] uvs = frontOnSouth ? 
                new float[][]{{frontUv[0], frontUv[3]}, {frontUv[2], frontUv[3]}, {frontUv[2], frontUv[1]}, {frontUv[0], frontUv[1]}} :
                new float[][]{{sideUv[0], sideUv[3]}, {sideUv[2], sideUv[3]}, {sideUv[2], sideUv[1]}, {sideUv[0], sideUv[1]}};
            emitQuad(vertices, indices, vertexCounter,
                    new float[][]{{x0, y0, z1}, {x1, y0, z1}, {x1, y1, z1}, {x0, y1, z1}},
                    uvs, LIGHT_NORTH_SOUTH, blockLight);
        }
        
        // North face (-Z)
        BlockType north = world.getBlock(wx, wy, wz - 1);
        if (north == BlockType.AIR || north.cross || north.slab || north.isStair() || north.isFence() || north.isWater() || north.isTranslucent() || north.isDoor() || north.isTrapdoor() || north.isBed() || (leavesTransparent && north == BlockType.LEAVES)) {
            float[][] uvs = frontOnNorth ? 
                new float[][]{{frontUv[2], frontUv[3]}, {frontUv[0], frontUv[3]}, {frontUv[0], frontUv[1]}, {frontUv[2], frontUv[1]}} :
                new float[][]{{sideUv[2], sideUv[3]}, {sideUv[0], sideUv[3]}, {sideUv[0], sideUv[1]}, {sideUv[2], sideUv[1]}};
            emitQuad(vertices, indices, vertexCounter,
                    new float[][]{{x1, y0, z0}, {x0, y0, z0}, {x0, y1, z0}, {x1, y1, z0}},
                    uvs, LIGHT_NORTH_SOUTH, blockLight);
        }
    }

    /**
     * Emits a stepped stair. The stair faces the direction it was placed toward
     * (its orientation byte, {@link #frontFaceFor}): the low step is at the
     * front and the raised step at the back, so you step up away from the
     * facing direction. Two half-height steps keep the mesh simple: a full-width
     * lower step and a back-half upper step, each drawn as a mini-cube with
     * face culling like a solid block.
     */
    private void emitStairs(BlockAccessor world, FloatArray vertices, IntArray indices, int[] vertexCounter,
                            int wx, int wy, int wz, BlockType block, byte orientation,
                            TextureAtlas atlas, float blockLight) {
        float[] uv = atlas.getUV(block.topTile);
        float u0 = uv[0], v0 = uv[1], u1 = uv[2], v1 = uv[3];
        float[][] uvs = {{u0, v1}, {u1, v1}, {u1, v0}, {u0, v0}};

        float x0 = wx, y0 = wy, z0 = wz, x1 = wx + 1, z1 = wz + 1;
        float midY = wy + 0.5f, topY = wy + 1f;

        // The raised (back) half of the top step sits on the side OPPOSITE the
        // facing, so you climb away from the direction the stair faces: facing
        // +Z means the low step is at +Z and the high step at -Z, and so on.
        float hiX0 = x0, hiX1 = x1, hiZ0 = z0, hiZ1 = z1;
        if (orientation == 2) hiX1 = x0 + 0.5f;        // facing +X: high step on -X half
        else if (orientation == 3) hiX0 = x1 - 0.5f;   // facing -X: high step on +X half
        else if (orientation == 0) hiZ1 = z0 + 0.5f;   // facing +Z: high step on -Z half
        else if (orientation == 1) hiZ0 = z1 - 0.5f;   // facing -Z: high step on +Z half

        emitStep(world, vertices, indices, vertexCounter,
                x0, y0, z0, x1, midY, z1, uvs, atlas, blockLight);
        emitStep(world, vertices, indices, vertexCounter,
                hiX0, midY, hiZ0, hiX1, topY, hiZ1, uvs, atlas, blockLight);
    }

    /**
     * Emits one rectangular step box as a mini-cube with per-face culling.
     * Faces hidden by a full solid neighbour are skipped; the lower step's top
     * is covered by the raised step's back half, so it's drawn in full and the
     * raised step simply overlays it (they share the same tile, so no seam).
     */
    private void emitStep(BlockAccessor world, FloatArray vertices, IntArray indices, int[] vertexCounter,
                          float x0, float y0, float z0, float x1, float y1, float z1,
                          float[][] uvs, TextureAtlas atlas, float blockLight) {
        int bx0 = (int) x0, bz0 = (int) z0;

        // Top face: hidden only when a full cube sits directly above.
        emitQuad(vertices, indices, vertexCounter,
                new float[][]{{x0, y1, z1}, {x1, y1, z1}, {x1, y1, z0}, {x0, y1, z0}},
                uvs, LIGHT_TOP, blockLight);

        // Bottom face: drawn unless a full-height block sits below.
        BlockType below = world.getBlock(bx0, (int) y0 - 1, bz0);
        if (below == BlockType.AIR || below.cross || below.slab || below.isStair() || below.isFence() || below.isWater() || below.isTranslucent() || below.isDoor() || below.isTrapdoor()) {
            emitQuad(vertices, indices, vertexCounter,
                    new float[][]{{x0, y0, z0}, {x1, y0, z0}, {x1, y0, z1}, {x0, y0, z1}},
                    uvs, LIGHT_BOTTOM, blockLight);
        }

        // Side faces: culled against full-cube neighbours. For the half-width
        // raised step, the inward face toward the stair's own front half is
        // against the lower step (solid), so it gets culled - clean.
        int ex = (int) x1, wx2 = bx0 - 1, sz = (int) z1, nz = bz0 - 1;
        BlockType east = world.getBlock(ex, (int) y0, bz0);
        if (east == BlockType.AIR || east.cross || east.slab || east.isStair() || east.isFence() || east.isWater() || east.isTranslucent() || east.isDoor() || east.isTrapdoor()) {
            emitQuad(vertices, indices, vertexCounter,
                    new float[][]{{x1, y0, z1}, {x1, y0, z0}, {x1, y1, z0}, {x1, y1, z1}},
                    uvs, LIGHT_EAST_WEST, blockLight);
        }
        BlockType west = world.getBlock(wx2, (int) y0, bz0);
        if (west == BlockType.AIR || west.cross || west.slab || west.isStair() || west.isFence() || west.isWater() || west.isTranslucent() || west.isDoor() || west.isTrapdoor()) {
            emitQuad(vertices, indices, vertexCounter,
                    new float[][]{{x0, y0, z0}, {x0, y0, z1}, {x0, y1, z1}, {x0, y1, z0}},
                    uvs, LIGHT_EAST_WEST, blockLight);
        }
        BlockType south = world.getBlock(bx0, (int) y0, sz);
        if (south == BlockType.AIR || south.cross || south.slab || south.isStair() || south.isFence() || south.isWater() || south.isTranslucent() || south.isDoor() || south.isTrapdoor()) {
            emitQuad(vertices, indices, vertexCounter,
                    new float[][]{{x0, y0, z1}, {x1, y0, z1}, {x1, y1, z1}, {x0, y1, z1}},
                    uvs, LIGHT_NORTH_SOUTH, blockLight);
        }
        BlockType north = world.getBlock(bx0, (int) y0, nz);
        if (north == BlockType.AIR || north.cross || north.slab || north.isStair() || north.isFence() || north.isWater() || north.isTranslucent() || north.isDoor() || north.isTrapdoor()) {
            emitQuad(vertices, indices, vertexCounter,
                    new float[][]{{x1, y0, z0}, {x0, y0, z0}, {x0, y1, z0}, {x1, y1, z0}},
                    uvs, LIGHT_NORTH_SOUTH, blockLight);
        }
    }

    /** True if the neighbour {@code t} connects a fence to it (another fence or any full solid block, excluding partial blocks). */
    private static boolean fenceConnects(BlockType t) {
        return t != null && !t.isPassThrough() && (t.isFence() || (t.solid && !t.slab && !t.isStair() && !t.isDoor() && !t.isTrapdoor() && !t.isSnowCappedSlab()));
    }

    /**
     * Emits a fence: a thin central post plus rails toward any neighbour that
     * connects (another fence or a solid block). The post is a ~2px-wide column
     * and each rail a thin bar at ~3/4 height spanning the connecting side.
     */
    private void emitFence(BlockAccessor world, FloatArray vertices, IntArray indices, int[] vertexCounter,
                           int wx, int wy, int wz, BlockType block, TextureAtlas atlas, float blockLight) {
        float[] uv = atlas.getUV(block.topTile);
        float u0 = uv[0], v0 = uv[1], u1 = uv[2], v1 = uv[3];
        float[][] uvs = {{u0, v1}, {u1, v1}, {u1, v0}, {u0, v0}};

        float x0 = wx, y0 = wy, z0 = wz, x1 = wx + 1, y1 = wy + 1, z1 = wz + 1;
        float postInset = 0.15f;   // half-width of the post (thin, like Minecraft)
        float railY = wy + 0.75f;  // rail bar height (top edge)
        float railT = 0.12f;       // rail thickness

        // Central post: a thin column from the floor to the top.
        emitBox(vertices, indices, vertexCounter,
                wx + 0.5f - postInset, y0, wz + 0.5f - postInset,
                wx + 0.5f + postInset, y1, wz + 0.5f + postInset, uvs, blockLight);

        BlockType north = world.getBlock(wx, wy, wz - 1);
        BlockType south = world.getBlock(wx, wy, wz + 1);
        BlockType east = world.getBlock(wx + 1, wy, wz);
        BlockType west = world.getBlock(wx - 1, wy, wz);

        if (fenceConnects(north)) {
            emitBox(vertices, indices, vertexCounter,
                    x0 + 0.5f - postInset, railY - railT, z0,
                    x0 + 0.5f + postInset, railY, z0 + 0.5f - postInset, uvs, blockLight);
        }
        if (fenceConnects(south)) {
            emitBox(vertices, indices, vertexCounter,
                    x0 + 0.5f - postInset, railY - railT, z0 + 0.5f + postInset,
                    x0 + 0.5f + postInset, railY, z1, uvs, blockLight);
        }
        if (fenceConnects(east)) {
            emitBox(vertices, indices, vertexCounter,
                    x0 + 0.5f + postInset, railY - railT, z0 + 0.5f - postInset,
                    x1, railY, z0 + 0.5f + postInset, uvs, blockLight);
        }
        if (fenceConnects(west)) {
            emitBox(vertices, indices, vertexCounter,
                    x0, railY - railT, z0 + 0.5f - postInset,
                    x0 + 0.5f - postInset, railY, z0 + 0.5f + postInset, uvs, blockLight);
        }
    }

    /** Emits a solid rectangular box (6 faces, no culling) - used for fence posts and rails. */
    private void emitBox(FloatArray vertices, IntArray indices, int[] vertexCounter,
                         float x0, float y0, float z0, float x1, float y1, float z1,
                         float[][] uvs, float blockLight) {
        emitQuad(vertices, indices, vertexCounter,
                new float[][]{{x0, y1, z1}, {x1, y1, z1}, {x1, y1, z0}, {x0, y1, z0}}, uvs, LIGHT_TOP, blockLight);
        emitQuad(vertices, indices, vertexCounter,
                new float[][]{{x0, y0, z0}, {x1, y0, z0}, {x1, y0, z1}, {x0, y0, z1}}, uvs, LIGHT_BOTTOM, blockLight);
        emitQuad(vertices, indices, vertexCounter,
                new float[][]{{x1, y0, z1}, {x1, y0, z0}, {x1, y1, z0}, {x1, y1, z1}}, uvs, LIGHT_EAST_WEST, blockLight);
        emitQuad(vertices, indices, vertexCounter,
                new float[][]{{x0, y0, z0}, {x0, y0, z1}, {x0, y1, z1}, {x0, y1, z0}}, uvs, LIGHT_EAST_WEST, blockLight);
        emitQuad(vertices, indices, vertexCounter,
                new float[][]{{x0, y0, z1}, {x1, y0, z1}, {x1, y1, z1}, {x0, y1, z1}}, uvs, LIGHT_NORTH_SOUTH, blockLight);
        emitQuad(vertices, indices, vertexCounter,
                new float[][]{{x1, y0, z0}, {x0, y0, z0}, {x0, y1, z0}, {x1, y1, z0}}, uvs, LIGHT_NORTH_SOUTH, blockLight);
    }

    /**
     * Emits a full-height snow-capped slab: the slab material fills the lower
     * half (same visibility rules as {@link #emitSlab}) and a snow cap fills
     * the upper half, so accumulated snow sits flush on a slab instead of
     * floating a half-block gap above its half-height top. Faces follow the
     * same culling as {@link #emitSlab}: sides only toward air/cross, the top
     * never covered, the bottom hidden under a full block below.
     */
    private void emitSnowySlab(BlockAccessor world, FloatArray vertices, IntArray indices, int[] vertexCounter,
                               int wx, int wy, int wz, BlockType block, TextureAtlas atlas, float blockLight) {
        float[] uvCap = atlas.getUV(block.topTile);
        float[] uvSlab = atlas.getUV(block.sideTile);
        float u0 = uvCap[0], v0 = uvCap[1], u1 = uvCap[2], v1 = uvCap[3];
        float[][] capUvs = {{u0, v1}, {u1, v1}, {u1, v0}, {u0, v0}};
        float s0 = uvSlab[0], t0 = uvSlab[1], s1 = uvSlab[2], t1 = uvSlab[3];
        float[][] slabUvs = {{s0, t1}, {s1, t1}, {s1, t0}, {s0, t0}};

        float x0 = wx, z0 = wz, x1 = wx + 1, z1 = wz + 1;
        float yBot = wy, yMid = wy + 0.5f, yTop = wy + 1f;

        // The snow cap's top face is never covered from above (blocks start at whole-block heights).
        emitQuad(vertices, indices, vertexCounter,
                new float[][]{{x0, yTop, z1}, {x1, yTop, z1}, {x1, yTop, z0}, {x0, yTop, z0}},
                capUvs, LIGHT_TOP, blockLight);

        // The slab's bottom face: drawn unless a full-height block sits directly below.
        BlockType below = world.getBlock(wx, wy - 1, wz);
        if (below == BlockType.AIR || below.cross || below.slab) {
            emitQuad(vertices, indices, vertexCounter,
                    new float[][]{{x0, yBot, z0}, {x1, yBot, z0}, {x1, yBot, z1}, {x0, yBot, z1}},
                    slabUvs, LIGHT_BOTTOM, blockLight);
        }

        // Four sides: the slab's lower half, then the snow's upper half. Apply
        // the same visibility rules as solid blocks (including fluids and
        // translucent neighbors). When adjacent to a plain slab, emit only the
        // upper cap half - the lower slab half is shared and remains culled.
        BlockType east = world.getBlock(wx + 1, wy, wz);
        if (east == BlockType.AIR || east.cross || east.isWater() || east.isTranslucent() || east.isDoor() || east.isTrapdoor() || (leavesTransparent && (east == BlockType.LEAVES || east == BlockType.CHERRY_LEAVES))) {
            emitQuad(vertices, indices, vertexCounter,
                    new float[][]{{x1, yBot, z1}, {x1, yBot, z0}, {x1, yMid, z0}, {x1, yMid, z1}},
                    slabUvs, LIGHT_EAST_WEST, blockLight);
            emitQuad(vertices, indices, vertexCounter,
                    new float[][]{{x1, yMid, z1}, {x1, yMid, z0}, {x1, yTop, z0}, {x1, yTop, z1}},
                    capUvs, LIGHT_EAST_WEST, blockLight);
        } else if (east.slab || east.isSnowCappedSlab()) {
            emitQuad(vertices, indices, vertexCounter,
                    new float[][]{{x1, yMid, z1}, {x1, yMid, z0}, {x1, yTop, z0}, {x1, yTop, z1}},
                    capUvs, LIGHT_EAST_WEST, blockLight);
        }
        BlockType west = world.getBlock(wx - 1, wy, wz);
        if (west == BlockType.AIR || west.cross || west.isWater() || west.isTranslucent() || west.isDoor() || west.isTrapdoor() || (leavesTransparent && (west == BlockType.LEAVES || west == BlockType.CHERRY_LEAVES))) {
            emitQuad(vertices, indices, vertexCounter,
                    new float[][]{{x0, yBot, z0}, {x0, yBot, z1}, {x0, yMid, z1}, {x0, yMid, z0}},
                    slabUvs, LIGHT_EAST_WEST, blockLight);
            emitQuad(vertices, indices, vertexCounter,
                    new float[][]{{x0, yMid, z0}, {x0, yMid, z1}, {x0, yTop, z1}, {x0, yTop, z0}},
                    capUvs, LIGHT_EAST_WEST, blockLight);
        } else if (west.slab || west.isSnowCappedSlab()) {
            emitQuad(vertices, indices, vertexCounter,
                    new float[][]{{x0, yMid, z0}, {x0, yMid, z1}, {x0, yTop, z1}, {x0, yTop, z0}},
                    capUvs, LIGHT_EAST_WEST, blockLight);
        }
        BlockType south = world.getBlock(wx, wy, wz + 1);
        if (south == BlockType.AIR || south.cross || south.isWater() || south.isTranslucent() || south.isDoor() || south.isTrapdoor() || (leavesTransparent && (south == BlockType.LEAVES || south == BlockType.CHERRY_LEAVES))) {
            emitQuad(vertices, indices, vertexCounter,
                    new float[][]{{x0, yBot, z1}, {x1, yBot, z1}, {x1, yMid, z1}, {x0, yMid, z1}},
                    slabUvs, LIGHT_NORTH_SOUTH, blockLight);
            emitQuad(vertices, indices, vertexCounter,
                    new float[][]{{x0, yMid, z1}, {x1, yMid, z1}, {x1, yTop, z1}, {x0, yTop, z1}},
                    capUvs, LIGHT_NORTH_SOUTH, blockLight);
        } else if (south.slab || south.isSnowCappedSlab()) {
            emitQuad(vertices, indices, vertexCounter,
                    new float[][]{{x0, yMid, z1}, {x1, yMid, z1}, {x1, yTop, z1}, {x0, yTop, z1}},
                    capUvs, LIGHT_NORTH_SOUTH, blockLight);
        }
        BlockType north = world.getBlock(wx, wy, wz - 1);
        if (north == BlockType.AIR || north.cross || north.isWater() || north.isTranslucent() || north.isDoor() || north.isTrapdoor() || (leavesTransparent && (north == BlockType.LEAVES || north == BlockType.CHERRY_LEAVES))) {
            emitQuad(vertices, indices, vertexCounter,
                    new float[][]{{x1, yBot, z0}, {x0, yBot, z0}, {x0, yMid, z0}, {x1, yMid, z0}},
                    slabUvs, LIGHT_NORTH_SOUTH, blockLight);
            emitQuad(vertices, indices, vertexCounter,
                    new float[][]{{x1, yMid, z0}, {x0, yMid, z0}, {x0, yTop, z0}, {x1, yTop, z0}},
                    capUvs, LIGHT_NORTH_SOUTH, blockLight);
        } else if (north.slab || north.isSnowCappedSlab()) {
            emitQuad(vertices, indices, vertexCounter,
                    new float[][]{{x1, yMid, z0}, {x0, yMid, z0}, {x0, yTop, z0}, {x1, yTop, z0}},
                    capUvs, LIGHT_NORTH_SOUTH, blockLight);
        }
    }

    private void emitQuadBothSides(FloatArray vertices, IntArray indices, int[] vertexCounter,
                                    float[][] positions, float[][] uvs, float light, float blockLight) {
        emitQuad(vertices, indices, vertexCounter, positions, uvs, light, blockLight);
        float[][] reversed = {positions[3], positions[2], positions[1], positions[0]};
        float[][] uvsReversed = {uvs[3], uvs[2], uvs[1], uvs[0]};
        emitQuad(vertices, indices, vertexCounter, reversed, uvsReversed, light, blockLight);
    }

    private void emitQuad(FloatArray vertices, IntArray indices, int[] vertexCounter,
                           float[][] positions, float[][] uvs, float light, float blockLight) {
        emitQuad(vertices, indices, vertexCounter, positions, uvs, light, blockLight, 0f, 0f, 0f);
    }

    private void emitQuad(FloatArray vertices, IntArray indices, int[] vertexCounter,
                           float[][] positions, float[][] uvs, float light, float blockLight, float fluidFlow) {
        emitQuad(vertices, indices, vertexCounter, positions, uvs, light, blockLight, fluidFlow, 0f, 0f);
    }

    private void emitQuad(FloatArray vertices, IntArray indices, int[] vertexCounter,
                           float[][] positions, float[][] uvs, float light, float blockLight,
                           float fluidFlow, float flowDirX, float flowDirZ) {
        int base = vertexCounter[0];
        for (int i = 0; i < 4; i++) {
            vertices.add(positions[i][0]);
            vertices.add(positions[i][1]);
            vertices.add(positions[i][2]);
            vertices.add(uvs[i][0]);
            vertices.add(uvs[i][1]);
            vertices.add(light);
            vertices.add(blockLight);
            vertices.add(fluidFlow);
            vertices.add(flowDirX);
            vertices.add(flowDirZ);
        }
        indices.add(base);
        indices.add(base + 1);
        indices.add(base + 2);
        indices.add(base);
        indices.add(base + 2);
        indices.add(base + 3);
        vertexCounter[0] += 4;
    }

    public void render() {
        if (mesh != null && hasMeshData) {
            mesh.render();
        }
    }

    /** Renders the see-through (glass/ice) faces - the caller draws this after all opaque geometry. */
    public void renderTranslucent() {
        if (translucentMesh != null && hasTranslucentData) {
            translucentMesh.render();
        }
    }

    public void destroy() {
        if (mesh != null) {
            mesh.destroy();
            mesh = null;
        }
        if (translucentMesh != null) {
            translucentMesh.destroy();
            translucentMesh = null;
        }
    }
}
