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
public class Chunk {

    public static final int SIZE = 16;
    public static final int HEIGHT = 128;

    // Face shading factors, faking simple fixed directional lighting.
    private static final float LIGHT_TOP = 1.0f;
    private static final float LIGHT_BOTTOM = 0.4f;
    private static final float LIGHT_NORTH_SOUTH = 0.75f;
    private static final float LIGHT_EAST_WEST = 0.6f;

    private final ChunkPos pos;
    private final byte[] blocks = new byte[SIZE * HEIGHT * SIZE];
    private final Mesh mesh = new Mesh();
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

    private volatile boolean dirty = true;
    private boolean generated = false;
    private boolean hasMeshData = false;
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
        if (type.isLightSource()) lightSources.add(new int[]{x, y, z, type.lightLevel});
        if (type.isFlowingFluid()) fluidBlocks.add(new int[]{x, y, z});
        dirty = true;
    }

    /** This block's fluid flow level (see {@link #fluidLevels}); 0 for anything that isn't a tracked flow block. */
    public int getFluidLevel(int x, int y, int z) {
        if (!inBounds(x, y, z)) return 0;
        return fluidLevels[index(x, y, z)];
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

    /** Like {@link #setLocal}, but also flags the chunk as needing to be saved to disk when it unloads. */
    public void setLocalFromPlayer(int x, int y, int z, BlockType type) {
        setLocal(x, y, z, type);
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

    /** Full O(chunk volume) rescan of light sources - only needed after a wholesale block replacement (disk load). */
    private void rebuildLightSourceIndex() {
        lightSources.clear();
        for (int y = 0; y < HEIGHT; y++) {
            for (int z = 0; z < SIZE; z++) {
                for (int x = 0; x < SIZE; x++) {
                    BlockType t = getLocal(x, y, z);
                    if (t.isLightSource()) lightSources.add(new int[]{x, y, z, t.lightLevel});
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
        FloatArray vertices = new FloatArray(4096);
        IntArray indices = new IntArray(4096);
        int[] vertexCounter = {0};

        int originX = getOriginX();
        int originZ = getOriginZ();

        for (int y = 0; y < HEIGHT; y++) {
            for (int z = 0; z < SIZE; z++) {
                for (int x = 0; x < SIZE; x++) {
                    BlockType block = getLocal(x, y, z);
                    if (block == BlockType.AIR) continue;

                    int wx = originX + x;
                    int wy = y;
                    int wz = originZ + z;
                    float blockLight = computeBlockLight(nearbyLights, wx + 0.5f, wy + 0.5f, wz + 0.5f);

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
                    if (block.isFluid()) {
                        emitFluid(world, vertices, indices, vertexCounter, wx, wy, wz, block, atlas, blockLight);
                        continue;
                    }

                    // +Y top
                    if (isFaceVisible(world, x, y + 1, z, wx, wy + 1, wz, block)) {
                        emitFace(vertices, indices, vertexCounter, wx, wy, wz, Face.TOP, block, atlas, blockLight);
                    }
                    // -Y bottom
                    if (isFaceVisible(world, x, y - 1, z, wx, wy - 1, wz, block)) {
                        emitFace(vertices, indices, vertexCounter, wx, wy, wz, Face.BOTTOM, block, atlas, blockLight);
                    }
                    // +X east
                    if (isFaceVisible(world, x + 1, y, z, wx + 1, wy, wz, block)) {
                        emitFace(vertices, indices, vertexCounter, wx, wy, wz, Face.EAST, block, atlas, blockLight);
                    }
                    // -X west
                    if (isFaceVisible(world, x - 1, y, z, wx - 1, wy, wz, block)) {
                        emitFace(vertices, indices, vertexCounter, wx, wy, wz, Face.WEST, block, atlas, blockLight);
                    }
                    // +Z south
                    if (isFaceVisible(world, x, y, z + 1, wx, wy, wz + 1, block)) {
                        emitFace(vertices, indices, vertexCounter, wx, wy, wz, Face.SOUTH, block, atlas, blockLight);
                    }
                    // -Z north
                    if (isFaceVisible(world, x, y, z - 1, wx, wy, wz - 1, block)) {
                        emitFace(vertices, indices, vertexCounter, wx, wy, wz, Face.NORTH, block, atlas, blockLight);
                    }
                }
            }
        }

        mesh.upload(vertices.toArray(), indices.toArray());
        hasMeshData = indices.size() > 0;
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
            return neighbor == BlockType.AIR || neighbor.cross || neighbor.slab;
        }

        // Cross-shaped decoration (grass/flowers) and slabs don't cover a full cell,
        // and translucent water doesn't occlude, so a solid neighbor's face toward
        // them must still be drawn - treat them like air for culling purposes.
        if (neighbor == BlockType.AIR || neighbor.cross || neighbor.slab || neighbor.isWater()) return true;
        // With see-through leaves on, leaf blocks stop occluding faces too - both
        // the leaf block's own faces and the blocks behind it get drawn, so the
        // cutout holes in the leaves texture actually show what's behind.
        return leavesTransparent && neighbor == BlockType.LEAVES;
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
        int maxLevel = type.isWater() ? FluidSim.WATER_FLOW_DISTANCE : FluidSim.LAVA_FLOW_DISTANCE;
        float t = Math.min(world.getFluidLevel(wx, wy, wz), maxLevel) / (float) maxLevel;
        return FLOW_TOP_NEAR - t * (FLOW_TOP_NEAR - FLOW_TOP_FAR);
    }

    /**
     * The direction (in tile-UV space) the flow's surface animation should
     * travel, so the water texture visibly creeps away from its source instead
     * of always scrolling straight down. The cell flows away from its neighbor
     * with the lowest stored level (nearest the source); a source or a cell with
     * no lower neighbor is still, so it returns (0,0) and doesn't animate.
     * Sides are deliberately left still (returned for the top face only) - a
     * vertical wall scrolling the banded tile looked wrong when water started
     * flowing.
     */
    private static float[] fluidFlowDir(BlockAccessor world, int wx, int wy, int wz, BlockType block) {
        if (block.isFluidSource()) return new float[]{0f, 0f};
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
        return minLevel < level ? new float[]{dirX, dirZ} : new float[]{0f, 0f};
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
     * given corner are the 2x2 block of cells around it.
     */
    private float fluidCornerTop(BlockAccessor world, int cx, int wy, int cz, BlockType block) {
        float sum = 0f;
        int count = 0;
        for (int dx = -1; dx <= 0; dx++) {
            for (int dz = -1; dz <= 0; dz++) {
                int x = cx + dx, z = cz + dz;
                BlockType t = fluidNeighborType(world, x, wy, z);
                if (sameFluidFamily(t, block)) {
                    sum += fluidTop(world, x, wy, z, t);
                    count++;
                }
            }
        }
        // count is never 0 in practice - block (wx,wz) itself is always one of
        // the 4 cells sharing each of its own corners, and always matches its
        // own family - but stay defensive rather than divide by zero.
        return count > 0 ? sum / count : 0f;
    }

    /** Emits a lowered translucent surface for fluid instead of a solid full cube. */
    private void emitFluid(BlockAccessor world, FloatArray vertices, IntArray indices, int[] vertexCounter,
                            int wx, int wy, int wz, BlockType block, TextureAtlas atlas, float blockLight) {
        float[] uv = atlas.getUV(block.topTile);
        float[][] uvs = {{uv[0], uv[3]}, {uv[2], uv[3]}, {uv[2], uv[1]}, {uv[0], uv[1]}};
        float x0 = wx, y0 = wy, z0 = wz, x1 = wx + 1, z1 = wz + 1;
        // Only the transient flowing kind animates - a still source/static body doesn't.
        float flow = block.isFluidFlow() ? 1f : 0f;
        // The top surface scrolls along the actual flow direction (away from the
        // source) rather than always straight down.
        float[] flowDir = fluidFlowDir(world, wx, wy, wz, block);

        float yNW = wy + fluidCornerTop(world, wx, wy, wz, block);
        float yNE = wy + fluidCornerTop(world, wx + 1, wy, wz, block);
        float ySE = wy + fluidCornerTop(world, wx + 1, wy, wz + 1, block);
        float ySW = wy + fluidCornerTop(world, wx, wy, wz + 1, block);

        // isFaceVisible's water branch treats "fluid above" as fully hiding this
        // top face - true when every corner is already at full height, but a
        // corner pulled down by a shorter neighbor doesn't actually touch the
        // cell above it even when that cell is also fluid (its own geometry
        // only ever starts at this cell's ceiling, y+1, never lower). Skipping
        // the face there left a real gap - looking down through it showed
        // whatever was below (e.g. the floor a waterfall just landed on)
        // instead of this cell's own water surface. All 4 corners already at
        // the ceiling is the one case the general culling still correctly
        // applies - two full cells stacked really do hide the seam.
        float minCorner = Math.min(Math.min(yNW, yNE), Math.min(ySE, ySW));
        if (minCorner < wy + 1f || isFaceVisible(world, wx - getOriginX(), wy + 1, wz - getOriginZ(), wx, wy + 1, wz, block)) {
            emitQuad(vertices, indices, vertexCounter,
                    new float[][]{{x0, ySW, z1}, {x1, ySE, z1}, {x1, yNE, z0}, {x0, yNW, z0}},
                    uvs, LIGHT_TOP, blockLight, flow, flowDir[0], flowDir[1]);
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
     * heights (absolute world Y) at this edge - the same values the top face
     * uses at its matching corners (see {@link #fluidCornerTop}), so this
     * face's top edge lines up with the top face exactly, whatever shape it
     * has. {@code yA} is the edge's "first" corner and {@code yB} its
     * "second", in the same order as each face's vertex winding below.
     */
    private void emitFluidSide(BlockAccessor world, FloatArray vertices, IntArray indices, int[] vertexCounter,
                                int wx, int wy, int wz, BlockType block, float blockLight, float flow,
                                Face face, float[][] uvs, float yA, float yB) {
        int nx = wx + (face == Face.EAST ? 1 : face == Face.WEST ? -1 : 0);
        int nz = wz + (face == Face.SOUTH ? 1 : face == Face.NORTH ? -1 : 0);
        int nlx = nx - getOriginX(), nlz = nz - getOriginZ();

        BlockType neighbor = inBounds(nlx, wy, nlz) ? getLocal(nlx, wy, nlz) : world.getBlock(nx, wy, nz);
        if (sameFluidFamily(neighbor, block)) {
            // This edge's corner heights are computed the exact same way from
            // the neighbor's side (averaging the same shared cells), so its
            // own top face already meets this one exactly here - no vertical
            // wall needed between two blended fluid surfaces.
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
        emitQuad(vertices, indices, vertexCounter, positions, uvs, light, blockLight, flow);
    }

    private void emitFace(FloatArray vertices, IntArray indices, int[] vertexCounter,
                           int wx, int wy, int wz, Face face, BlockType block, TextureAtlas atlas, float blockLight) {
        int tile = switch (face) {
            case TOP -> block.topTile;
            case BOTTOM -> block.bottomTile;
            default -> block.sideTile;
        };
        // See-through leaves use the alpha-cutout variant of the leaves texture
        // (the shader discards its transparent holes) so the canopy is translucent.
        if (leavesTransparent && block == BlockType.LEAVES) {
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
        if (below == BlockType.AIR || below.cross || below.slab) {
            emitQuad(vertices, indices, vertexCounter,
                    new float[][]{{x0, y0, z0}, {x1, y0, z0}, {x1, y0, z1}, {x0, y0, z1}},
                    uvs, LIGHT_BOTTOM, blockLight);
        }

        BlockType east = world.getBlock(wx + 1, wy, wz);
        if (east == BlockType.AIR || east.cross) {
            emitQuad(vertices, indices, vertexCounter,
                    new float[][]{{x1, y0, z1}, {x1, y0, z0}, {x1, y1, z0}, {x1, y1, z1}},
                    uvs, LIGHT_EAST_WEST, blockLight);
        }
        BlockType west = world.getBlock(wx - 1, wy, wz);
        if (west == BlockType.AIR || west.cross) {
            emitQuad(vertices, indices, vertexCounter,
                    new float[][]{{x0, y0, z0}, {x0, y0, z1}, {x0, y1, z1}, {x0, y1, z0}},
                    uvs, LIGHT_EAST_WEST, blockLight);
        }
        BlockType south = world.getBlock(wx, wy, wz + 1);
        if (south == BlockType.AIR || south.cross) {
            emitQuad(vertices, indices, vertexCounter,
                    new float[][]{{x0, y0, z1}, {x1, y0, z1}, {x1, y1, z1}, {x0, y1, z1}},
                    uvs, LIGHT_NORTH_SOUTH, blockLight);
        }
        BlockType north = world.getBlock(wx, wy, wz - 1);
        if (north == BlockType.AIR || north.cross) {
            emitQuad(vertices, indices, vertexCounter,
                    new float[][]{{x1, y0, z0}, {x0, y0, z0}, {x0, y1, z0}, {x1, y1, z0}},
                    uvs, LIGHT_NORTH_SOUTH, blockLight);
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
        if (hasMeshData) {
            mesh.render();
        }
    }

    public void destroy() {
        mesh.destroy();
    }
}
