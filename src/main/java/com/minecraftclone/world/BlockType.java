package com.minecraftclone.world;

import com.minecraftclone.engine.graphics.TextureAtlas;

/**
 * All placeable/generatable block types. Each entry defines which tile of
 * the procedural texture atlas is used for the top, side and bottom faces,
 * plus a few gameplay flags (solidity, transparency, cross-shape, food value).
 * <p>
 * Atlas tile indices refer to {@link com.minecraftclone.engine.graphics.TextureAtlas}.
 * <p>
 * <b>Adding a new block</b> (5 steps, all one-liners except the texture):
 * <ol>
 *   <li>Add an enum constant here with a <b>unique, never-reused id</b> - ids are
 *       persisted in save files, so reusing an old id would corrupt existing worlds
 *       (a duplicate id fails fast at startup).</li>
 *   <li>Paint its texture in {@code TextureAtlas.buildImage()} (or point it at an
 *       existing tile index).</li>
 *   <li>Give it break times in {@link com.minecraftclone.world.Mining}: {@code put(type, hardness, tool, tier)}.</li>
 *   <li>Add a {@link com.minecraftclone.player.Crafting#shaped} / {@code #shapeless}
 *       recipe, or a {@code Smelting#outputFor} smelting entry, if it's made from ingredients.</li>
 *   <li>Add it to a tab in {@link com.minecraftclone.player.CreativeCatalog} so creative mode offers it.</li>
 * </ol>
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
    TORCH(38, false, true, 38, 8), // cross-shaped, non-collidable, and a light source (see lightLevel)
    LAMP(39, true, false, 25, 25, 25, 0, 15), // full-cube light source, brighter than a torch
    FURNACE(40, true, false, 4, 4, 4, 26, TextureAtlas.FURNACE_LIT_TILE, 0, 0), // smelting station: stone top/bottom/sides, furnace-face front (tile 26) that glows when burning
    STONE_SLAB(44, true, false, true, 4),   // bottom-half slab, stone texture
    PLANKS_SLAB(45, true, false, true, 11), // bottom-half slab, planks texture
    // Fluids: SOURCE variants are placeable and flow (see FluidSim); the WATER/LAVA
    // above are the static terrain fills that don't move. FLOW variants are the
    // transient flowing cells the simulation fills in and dries up.
    WATER_SOURCE(46, false, true, 6, 6, 6),
    WATER_FLOW(47, false, true, 6, 6, 6),
    LAVA_SOURCE(48, false, true, 20, 20, 20),
    LAVA_FLOW(49, false, true, 20, 20, 20),
    // Biome-specific surface blocks and decorations.
    SWAMP_GRASS(50, true, false, 27, 28, 3),
    RED_CLAY(51, true, false, 29, 29, 29),
    MYCELIUM(52, true, false, 30, 31, 3),
    ICE(53, true, true, 32, 32, 32),
    DEAD_BUSH(54, false, true, 33),
    MUSHROOM_RED(55, false, true, 35),
    MUSHROOM_BROWN(56, false, true, 36),
    VINE(57, false, true, 39),
    CHERRY_LEAVES(58, true, true, 40),
    PACKED_ICE(59, true, false, 41),
    BAMBOO(60, false, true, 42),
    LILY_PAD(61, false, true, 43),
    PUMPKIN(62, true, false, 44, 44, 44),
    SEAWEED(63, false, true, 45),
    DOOR(64, true, false, 46, 46, 46),
    DOOR_OPEN(70, false, true, 46),
    TRAPDOOR(71, true, false, 47, 47, 47),
    TRAPDOOR_OPEN(72, false, true, 47),
    CRAFTING_TABLE(73, true, false, 48, 48, 48), // workbench: right-click opens the 3x3 crafting GUI
    CHEST(87, true, false, 50, 50, 50),          // storage: right-click opens a 27-slot container GUI (54 when doubled)
    BARREL(88, true, false, 51, 51, 51),         // storage: cheaper single 27-slot container, never doubles

    // Inventory-only items: food and tools. Never placed as a world block,
    // so they have no atlas tile - each gets its own procedurally generated
    // texture instead, see com.minecraftclone.engine.graphics.ItemTextures.
    // Mining stats for
    // tools (kind/tier/durability) live in Mining.java, not here, to keep
    // this enum focused on rendering/collision.
    APPLE(23, 20),   // food: restores 20 hunger
    BERRIES(24, 10), // food: restores 10 hunger
    STICK(25, 0),
    WOOD_PICKAXE(26, 0),
    STONE_PICKAXE(27, 0),
    IRON_PICKAXE(28, 0),
    DIAMOND_PICKAXE(29, 0),
    WOOD_AXE(30, 0),
    STONE_AXE(31, 0),
    IRON_AXE(32, 0),
    DIAMOND_AXE(33, 0),
    WOOD_SWORD(34, 0),
    STONE_SWORD(35, 0),
    IRON_SWORD(36, 0),
    DIAMOND_SWORD(37, 0),
    // Shovels, hammers and broadaxes - the rest of the tool set (see Mining):
    // shovel = soft ground, hammer = stone/building, broadaxe = wood (faster than an axe).
    WOOD_SHOVEL(75, 0),
    STONE_SHOVEL(76, 0),
    IRON_SHOVEL(77, 0),
    DIAMOND_SHOVEL(78, 0),
    WOOD_HAMMER(79, 0),
    STONE_HAMMER(80, 0),
    IRON_HAMMER(81, 0),
    DIAMOND_HAMMER(82, 0),
    WOOD_BROADAXE(83, 0),
    STONE_BROADAXE(84, 0),
    IRON_BROADAXE(85, 0),
    DIAMOND_BROADAXE(86, 0),
    IRON_INGOT(41, 0), // smelted from iron ore (see Smelting)
    GOLD_INGOT(42, 0), // smelted from gold ore
    DIAMOND(43, 0),    // smelted from diamond ore
    COAL(74, 0),       // mined from coal ore, the furnace fuel (see Smelting)
    // Raw meat - dropped by passive mobs when killed (see World.damageMob), edible.
    RAW_PORKCHOP(65, 16),
    RAW_BEEF(66, 16),
    MUTTON(67, 12),
    // Hostile-mob loot - see World.damageMob. Rotten flesh is barely edible.
    ROTTEN_FLESH(68, 4),
    BONES(69, 0),
    // Snow-capped slabs: a bottom-half slab that's been covered by accumulating
    // snow (see World.tryAddSnow). Full-height solid blocks that MESH as a slab
    // under a snow cap, so the snow sits flush rather than floating above the
    // slab's half-height top. Melting restores the plain slab underneath.
    SNOWY_STONE_SLAB(89, true, false, 12, 4, 4),
    SNOWY_PLANKS_SLAB(90, true, false, 12, 11, 11);

    public final byte id;
    public final boolean solid;
    public final boolean transparent;
    public final boolean cross;
    public final int topTile;
    public final int sideTile;
    public final int bottomTile;
    /** Atlas tile for the block's front face (the face it "faces" toward via its orientation), when directional. */
    public final int frontTile;
    /** Atlas tile used for the front face while the block is "active" (e.g. a burning furnace's glowing mouth); equals {@link #frontTile} for blocks that don't change. */
    public final int litFrontTile;
    public final int foodValue;
    /** True for inventory-only items (food/tools): no atlas tile, own procedurally generated texture, never placeable as a world block. */
    public final boolean isItem;
    /** 0-15, Minecraft-style: how brightly this block glows (0 = not a light source). See torch handling in {@link Chunk}. */
    public final int lightLevel;
    /** True if this block is a bottom-half slab (partial cube), which meshes and collides at half height. */
    public final boolean slab;
    /** Vertical extent of this block's collision box in blocks (1.0 for full cubes, 0.5 for slabs). */
    public final float collisionHeight;

    /** Full-cube block: distinct top/side/bottom textures, collides with the player. */
    BlockType(int id, boolean solid, boolean transparent, int topTile, int sideTile, int bottomTile) {
        this(id, solid, transparent, topTile, sideTile, bottomTile, sideTile);
    }

    /** Full-cube block with a food value (not currently used - cubes aren't eaten - but kept symmetric). */
    BlockType(int id, boolean solid, boolean transparent, int topTile, int sideTile, int bottomTile, int foodValue) {
        this(id, solid, transparent, topTile, sideTile, bottomTile, foodValue, 0);
    }

    /** Full-cube block with a distinct front face (the face it faces via orientation), e.g. a furnace. */
    BlockType(int id, boolean solid, boolean transparent, int topTile, int sideTile, int bottomTile, int frontTile, int foodValue, int lightLevel) {
        this(id, solid, transparent, topTile, sideTile, bottomTile, frontTile, frontTile, foodValue, lightLevel);
    }

    /** Full-cube block with a distinct front face that also changes while active (a lit furnace mouth). */
    BlockType(int id, boolean solid, boolean transparent, int topTile, int sideTile, int bottomTile, int frontTile, int litFrontTile, int foodValue, int lightLevel) {
        this.id = (byte) id;
        this.solid = solid;
        this.transparent = transparent;
        this.cross = false;
        this.slab = false;
        this.topTile = topTile;
        this.sideTile = sideTile;
        this.bottomTile = bottomTile;
        this.frontTile = frontTile;
        this.litFrontTile = litFrontTile;
        this.foodValue = foodValue;
        this.isItem = false;
        this.lightLevel = lightLevel;
        this.collisionHeight = 1.0f;
    }

    /** Full-cube block that also emits light (e.g. a lamp), plus an (unused) food value. */
    BlockType(int id, boolean solid, boolean transparent, int topTile, int sideTile, int bottomTile, int foodValue, int lightLevel) {
        this(id, solid, transparent, topTile, sideTile, bottomTile, sideTile, foodValue, lightLevel);
    }

    /** Bottom-half slab: a partial cube, one atlas tile for all faces, colliding only in its lower half. */
    BlockType(int id, boolean solid, boolean transparent, boolean slab, int tile) {
        this.id = (byte) id;
        this.solid = solid;
        this.transparent = transparent;
        this.cross = false;
        this.slab = slab;
        this.topTile = tile;
        this.sideTile = tile;
        this.bottomTile = tile;
        this.frontTile = tile;
        this.litFrontTile = tile;
        this.foodValue = 0;
        this.isItem = false;
        this.lightLevel = 0;
        this.collisionHeight = slab ? 0.5f : 1.0f;
    }

    /** Cross-shaped (billboard-X) world decoration block, e.g. grass/flowers/berry bush: one atlas tile, never collides. */
    BlockType(int id, boolean solid, boolean transparent, int tile) {
        this(id, solid, transparent, tile, 0);
    }

    /** Cross-shaped world decoration that's also a light source, e.g. a torch. */
    BlockType(int id, boolean solid, boolean transparent, int tile, int lightLevel) {
        this.id = (byte) id;
        this.solid = solid;
        this.transparent = transparent;
        this.cross = true;
        this.slab = false;
        this.topTile = tile;
        this.sideTile = tile;
        this.bottomTile = tile;
        this.frontTile = tile;
        this.litFrontTile = tile;
        this.foodValue = 0;
        this.isItem = false;
        this.lightLevel = lightLevel;
        this.collisionHeight = 1.0f;
    }

    /** Inventory-only item (tool, or foraged food like apple/berries): no atlas tile, has its own procedurally generated texture, never placeable as a world block. */
    BlockType(int id, int foodValue) {
        this.id = (byte) id;
        this.solid = false;
        this.transparent = true;
        this.cross = false;
        this.slab = false;
        this.topTile = -1;
        this.sideTile = -1;
        this.bottomTile = -1;
        this.frontTile = -1;
        this.litFrontTile = -1;
        this.foodValue = foodValue;
        this.isItem = true;
        this.lightLevel = 0;
        this.collisionHeight = 1.0f;
    }

    // Sparse id lookup: sized to the largest id rather than values().length, so
    // ids don't have to be a dense 0..N-1 range (e.g. slabs use 44/45 while
    // other features may take 39-43). byId guards against out-of-range anyway.
    private static final BlockType[] BY_ID;

    static {
        int maxId = 0;
        for (BlockType t : values()) {
            maxId = Math.max(maxId, t.id);
        }
        BY_ID = new BlockType[maxId + 1];
        for (BlockType t : values()) {
            if (BY_ID[t.id] != null) {
                // Fail fast on a duplicate id - a re-used id would silently corrupt save files.
                throw new IllegalStateException("Duplicate block id " + t.id + ": " + t + " and " + BY_ID[t.id]);
            }
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

    /** True if this block has a distinct front face that faces its orientation (e.g. a furnace). */
    public boolean isDirectional() {
        return frontTile != sideTile;
    }

    public boolean isEdible() {
        return foodValue > 0;
    }

    public boolean isLightSource() {
        return lightLevel > 0;
    }

    /** True for any water-family block (static, source, or flow). */
    public boolean isWater() {
        return this == WATER || this == WATER_SOURCE || this == WATER_FLOW;
    }

    /** True for any lava-family block (static, source, or flow). */
    public boolean isLava() {
        return this == LAVA || this == LAVA_SOURCE || this == LAVA_FLOW;
    }

    /** True for any fluid (water or lava), including static and flowing variants. */
    public boolean isFluid() {
        return isWater() || isLava();
    }

    /**
     * True if this block's top face can support an accumulated snow layer:
     * an opaque full cube (or a bottom-half slab, which snow caps flush - see
     * {@link #isSnowCappedSlab()}) that isn't a cross-shaped plant, so snow
     * piles up on ground, stone, wood, roofs, sand and snow itself, but not on
     * leaves, glass, ice, fluids, torches or flowers.
     */
    public boolean canHoldSnow() {
        return solid && !transparent && !cross && !isTranslucent();
    }

    /** True for a bottom-half slab that's been covered by a flush snow cap (see the snowy slab entries above). */
    public boolean isSnowCappedSlab() {
        return this == SNOWY_STONE_SLAB || this == SNOWY_PLANKS_SLAB;
    }

    /** True for the placeable, flowing fluid source blocks. */
    public boolean isFluidSource() {
        return this == WATER_SOURCE || this == LAVA_SOURCE;
    }

    /** True for the transient flowing cells the fluid simulation fills and dries. */
    public boolean isFluidFlow() {
        return this == WATER_FLOW || this == LAVA_FLOW;
    }

    /** True for source or flow fluid (i.e. the flowing kinds, not the static terrain fills). */
    public boolean isFlowingFluid() {
        return isFluidSource() || isFluidFlow();
    }

    /** True if a ray should pass straight through this block (air, static water, or transient flow). */
    public boolean isPassThrough() {
        return this == AIR || this == WATER || this == WATER_FLOW || this == LAVA_FLOW;
    }

    /** True if this block is drawn in the see-through translucent render pass (glass, ice). */
    public boolean isTranslucent() {
        return this == GLASS || this == ICE;
    }

    /**
     * True for decoration that grows/sits <em>inside</em> a fluid cell rather
     * than needing to replace it - Minecraft's "waterlogged" plants (seagrass,
     * kelp) work the same way. World-gen and manual placement both route this
     * into the target cell's overlay slot instead of overwriting the water
     * there - see {@link Chunk#setOverlay} and {@link BlockAccessor#getOverlay}.
     */
    public boolean isSubmersible() {
        return this == SEAWEED;
    }

    /** True for either half of a functional door (closed solid, or open walk-through). */
    public boolean isDoor() {
        return this == DOOR || this == DOOR_OPEN;
    }

    /** True for a functional trapdoor (closed solid panel, or open walk-through). */
    public boolean isTrapdoor() {
        return this == TRAPDOOR || this == TRAPDOOR_OPEN;
    }

    /** A human-readable name for HUD tooltips, e.g. "DIAMOND_PICKAXE" -> "Diamond Pickaxe". */
    public String displayName() {
        StringBuilder sb = new StringBuilder(name().length());
        boolean upper = true;
        for (int i = 0; i < name().length(); i++) {
            char c = name().charAt(i);
            if (c == '_') {
                sb.append(' ');
                upper = true;
            } else {
                sb.append(upper ? Character.toUpperCase(c) : Character.toLowerCase(c));
                upper = false;
            }
        }
        return sb.toString();
    }

    /**
     * The tracked, flowing source variant a static (world-gen) fluid block should be
     * promoted to once it ends up bordering open space - null if this block isn't a
     * static fluid (including if it's already a source/flow). See World#setBlock: an
     * entire ocean/lake is deliberately left untracked for performance, but the one
     * boundary block that actually needs to flow into a freshly-broken opening is
     * promoted right there, so it starts participating in FluidSim.
     */
    public BlockType promotedFluidSource() {
        if (this == WATER) return WATER_SOURCE;
        if (this == LAVA) return LAVA_SOURCE;
        return null;
    }
}
