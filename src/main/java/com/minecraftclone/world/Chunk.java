package com.minecraftclone.world;

import com.minecraftclone.engine.graphics.Mesh;
import com.minecraftclone.engine.graphics.TextureAtlas;

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

    private volatile boolean dirty = true;
    private boolean generated = false;
    private boolean hasMeshData = false;

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
        if (!inBounds(x, y, z)) return;
        blocks[index(x, y, z)] = type.id;
        dirty = true;
    }

    /** Rebuilds the CPU-side vertex data and uploads it to the GPU. Call from the main (GL) thread. */
    public void rebuildMesh(BlockAccessor world, TextureAtlas atlas) {
        List<Float> vertices = new ArrayList<>(4096);
        List<Integer> indices = new ArrayList<>(4096);
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

                    // +Y top
                    if (isFaceVisible(world, x, y + 1, z, wx, wy + 1, wz)) {
                        emitFace(vertices, indices, vertexCounter, wx, wy, wz, Face.TOP, block, atlas);
                    }
                    // -Y bottom
                    if (isFaceVisible(world, x, y - 1, z, wx, wy - 1, wz)) {
                        emitFace(vertices, indices, vertexCounter, wx, wy, wz, Face.BOTTOM, block, atlas);
                    }
                    // +X east
                    if (isFaceVisible(world, x + 1, y, z, wx + 1, wy, wz)) {
                        emitFace(vertices, indices, vertexCounter, wx, wy, wz, Face.EAST, block, atlas);
                    }
                    // -X west
                    if (isFaceVisible(world, x - 1, y, z, wx - 1, wy, wz)) {
                        emitFace(vertices, indices, vertexCounter, wx, wy, wz, Face.WEST, block, atlas);
                    }
                    // +Z south
                    if (isFaceVisible(world, x, y, z + 1, wx, wy, wz + 1)) {
                        emitFace(vertices, indices, vertexCounter, wx, wy, wz, Face.SOUTH, block, atlas);
                    }
                    // -Z north
                    if (isFaceVisible(world, x, y, z - 1, wx, wy, wz - 1)) {
                        emitFace(vertices, indices, vertexCounter, wx, wy, wz, Face.NORTH, block, atlas);
                    }
                }
            }
        }

        float[] vArray = new float[vertices.size()];
        for (int i = 0; i < vArray.length; i++) vArray[i] = vertices.get(i);
        int[] iArray = new int[indices.size()];
        for (int i = 0; i < iArray.length; i++) iArray[i] = indices.get(i);

        mesh.upload(vArray, iArray);
        hasMeshData = iArray.length > 0;
        dirty = false;
    }

    /** A face is drawn if the neighboring cell (which may be outside this chunk) is empty (air). */
    private boolean isFaceVisible(BlockAccessor world, int localX, int localY, int localZ, int worldX, int worldY, int worldZ) {
        if (worldY < 0) return false;   // treat below-bedrock as solid void: never draw bottom faces
        if (worldY >= HEIGHT) return true; // above world height is open sky: always draw top faces
        BlockType neighbor;
        if (inBounds(localX, localY, localZ)) {
            neighbor = getLocal(localX, localY, localZ);
        } else {
            neighbor = world.getBlock(worldX, worldY, worldZ);
        }
        return neighbor == BlockType.AIR;
    }

    private enum Face {TOP, BOTTOM, NORTH, SOUTH, EAST, WEST}

    private void emitFace(List<Float> vertices, List<Integer> indices, int[] vertexCounter,
                           int wx, int wy, int wz, Face face, BlockType block, TextureAtlas atlas) {
        int tile = switch (face) {
            case TOP -> block.topTile;
            case BOTTOM -> block.bottomTile;
            default -> block.sideTile;
        };
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

        int base = vertexCounter[0];
        for (int i = 0; i < 4; i++) {
            vertices.add(positions[i][0]);
            vertices.add(positions[i][1]);
            vertices.add(positions[i][2]);
            vertices.add(uvs[i][0]);
            vertices.add(uvs[i][1]);
            vertices.add(light);
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
