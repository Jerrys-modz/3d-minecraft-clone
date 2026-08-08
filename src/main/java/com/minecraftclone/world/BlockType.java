package com.minecraftclone.world;

/**
 * All placeable/generatable block types. Each entry defines which tile of
 * the procedural texture atlas is used for the top, side and bottom faces,
 * plus a few gameplay flags (solidity, transparency).
 * <p>
 * Atlas tile indices refer to {@link com.minecraftclone.engine.graphics.TextureAtlas}.
 */
public enum BlockType {
    AIR(0, false, true, 0, 0, 0),
    GRASS(1, true, false, 1, 2, 3),
    DIRT(2, true, false, 3, 3, 3),
    STONE(3, true, false, 4, 4, 4),
    SAND(4, true, false, 5, 5, 5),
    WATER(5, false, true, 6, 6, 6),
    WOOD_LOG(6, true, false, 8, 7, 8),
    LEAVES(7, true, true, 9, 9, 9),
    BEDROCK(8, true, false, 10, 10, 10),
    PLANKS(9, true, false, 11, 11, 11),
    SNOW(10, true, false, 12, 13, 3),
    GRAVEL(11, true, false, 14, 14, 14),
    CACTUS(12, true, false, 15, 15, 15);

    public final byte id;
    public final boolean solid;
    public final boolean transparent;
    public final int topTile;
    public final int sideTile;
    public final int bottomTile;

    BlockType(int id, boolean solid, boolean transparent, int topTile, int sideTile, int bottomTile) {
        this.id = (byte) id;
        this.solid = solid;
        this.transparent = transparent;
        this.topTile = topTile;
        this.sideTile = sideTile;
        this.bottomTile = bottomTile;
    }

    private static final BlockType[] BY_ID = new BlockType[values().length];

    static {
        for (BlockType t : values()) {
            BY_ID[t.id] = t;
        }
    }

    public static BlockType byId(byte id) {
        if (id < 0 || id >= BY_ID.length) {
            return AIR;
        }
        return BY_ID[id];
    }

    /** True if this block stops the player / blocks a raycast (water and air do not). */
    public boolean isCollidable() {
        return this != AIR && this != WATER;
    }
}
