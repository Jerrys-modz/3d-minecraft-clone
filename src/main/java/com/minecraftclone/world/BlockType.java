package com.minecraftclone.world;

/**
 * All placeable/generatable block types. Each entry defines which tile of
 * the procedural texture atlas is used for the top, side and bottom faces,
 * plus a few gameplay flags (solidity, transparency, cross-shape, food value).
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
    CACTUS(12, true, false, 15, 15, 15),
    COAL_ORE(13, true, false, 16, 16, 16),
    IRON_ORE(14, true, false, 17, 17, 17),
    GOLD_ORE(15, true, false, 18, 18, 18),
    DIAMOND_ORE(16, true, false, 19, 19, 19),
    LAVA(17, false, true, 20, 20, 20),
    TALL_GRASS(18, false, true, 21),
    FLOWER_RED(19, false, true, 22),
    FLOWER_YELLOW(20, false, true, 23),
    GLASS(21, true, false, 34, 34, 34),
    BERRY_BUSH(22, false, true, 37),
    APPLE(23, false, true, 35, 20),   // food: restores 20 hunger
    BERRIES(24, false, true, 36, 10), // food: restores 10 hunger

    // Tools: inventory-only items, never placed in the world. Mining stats
    // (tool kind/tier/durability) live in Mining.java, not here, to keep
    // this enum focused on rendering/collision.
    STICK(25, false, true, 38),
    WOOD_PICKAXE(26, false, true, 39),
    STONE_PICKAXE(27, false, true, 40),
    IRON_PICKAXE(28, false, true, 41),
    DIAMOND_PICKAXE(29, false, true, 42),
    WOOD_AXE(30, false, true, 43),
    STONE_AXE(31, false, true, 44),
    IRON_AXE(32, false, true, 45),
    DIAMOND_AXE(33, false, true, 46),
    WOOD_SWORD(34, false, true, 47),
    STONE_SWORD(35, false, true, 48),
    IRON_SWORD(36, false, true, 49),
    DIAMOND_SWORD(37, false, true, 50);

    public final byte id;
    public final boolean solid;
    public final boolean transparent;
    public final boolean cross;
    public final int topTile;
    public final int sideTile;
    public final int bottomTile;
    public final int foodValue;

    /** Full-cube block: distinct top/side/bottom textures, collides with the player. */
    BlockType(int id, boolean solid, boolean transparent, int topTile, int sideTile, int bottomTile) {
        this(id, solid, transparent, topTile, sideTile, bottomTile, 0);
    }

    /** Full-cube block with a food value (not currently used - cubes aren't eaten - but kept symmetric). */
    BlockType(int id, boolean solid, boolean transparent, int topTile, int sideTile, int bottomTile, int foodValue) {
        this.id = (byte) id;
        this.solid = solid;
        this.transparent = transparent;
        this.cross = false;
        this.topTile = topTile;
        this.sideTile = sideTile;
        this.bottomTile = bottomTile;
        this.foodValue = foodValue;
    }

    /** Cross-shaped (billboard-X) decoration block, e.g. grass/flowers: one texture, never collides. */
    BlockType(int id, boolean solid, boolean transparent, int tile) {
        this(id, solid, transparent, tile, 0);
    }

    /** Cross-shaped, edible item (apple/berries): never placed by the player, only eaten. */
    BlockType(int id, boolean solid, boolean transparent, int tile, int foodValue) {
        this.id = (byte) id;
        this.solid = solid;
        this.transparent = transparent;
        this.cross = true;
        this.topTile = tile;
        this.sideTile = tile;
        this.bottomTile = tile;
        this.foodValue = foodValue;
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

    /** True if this block stops the player / blocks a raycast. */
    public boolean isCollidable() {
        return solid;
    }

    public boolean isEdible() {
        return foodValue > 0;
    }
}
