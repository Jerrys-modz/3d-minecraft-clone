package com.minecraftclone.world;

import java.util.EnumMap;
import java.util.Map;

/**
 * Break-time and tool-tier rules, kept as static registries separate from
 * {@link BlockType} so that enum stays focused on rendering/collision.
 * <p>
 * Every block has a base hardness (seconds to break bare-handed) and,
 * optionally, an "effective" tool kind that speeds up breaking it and/or a
 * minimum tool tier required to break it at all (e.g. you can't punch out a
 * diamond - you need at least an iron pickaxe).
 * <p>
 * The tool set is a full survival-craft spread, each kind with its own
 * material: the pickaxe (ores - tier-gated), the shovel (soft earth), the
 * hammer (stone and masonry), the broadaxe (the heavy wood-cutter, twice as
 * fast as any other tool at wood), the axe (plant/fibrous material like
 * leaves and cacti), and the sword (combat - no mining speed bonus).
 */
public final class Mining {

    public enum ToolKind {NONE, PICKAXE, AXE, SWORD, SHOVEL, HAMMER, BROADAXE}

    /** Higher is better. 0 = bare hands. */
    public static final int TIER_HAND = 0;
    public static final int TIER_WOOD = 1;
    public static final int TIER_STONE = 2;
    public static final int TIER_IRON = 3;
    public static final int TIER_DIAMOND = 4;

    public record ToolStats(ToolKind kind, int tier, int maxUses) {
    }

    public record BlockInfo(float hardnessSeconds, ToolKind effectiveTool, int requiredTier) {
    }

    private static final Map<BlockType, ToolStats> TOOLS = new EnumMap<>(BlockType.class);
    private static final Map<BlockType, BlockInfo> BLOCKS = new EnumMap<>(BlockType.class);
    private static final BlockInfo DEFAULT_BLOCK_INFO = new BlockInfo(0.4f, ToolKind.NONE, TIER_HAND);

    static {
        TOOLS.put(BlockType.WOOD_PICKAXE, new ToolStats(ToolKind.PICKAXE, TIER_WOOD, 60));
        TOOLS.put(BlockType.STONE_PICKAXE, new ToolStats(ToolKind.PICKAXE, TIER_STONE, 132));
        TOOLS.put(BlockType.IRON_PICKAXE, new ToolStats(ToolKind.PICKAXE, TIER_IRON, 251));
        TOOLS.put(BlockType.DIAMOND_PICKAXE, new ToolStats(ToolKind.PICKAXE, TIER_DIAMOND, 1562));
        TOOLS.put(BlockType.WOOD_AXE, new ToolStats(ToolKind.AXE, TIER_WOOD, 60));
        TOOLS.put(BlockType.STONE_AXE, new ToolStats(ToolKind.AXE, TIER_STONE, 132));
        TOOLS.put(BlockType.IRON_AXE, new ToolStats(ToolKind.AXE, TIER_IRON, 251));
        TOOLS.put(BlockType.DIAMOND_AXE, new ToolStats(ToolKind.AXE, TIER_DIAMOND, 1562));
        TOOLS.put(BlockType.WOOD_SWORD, new ToolStats(ToolKind.SWORD, TIER_WOOD, 60));
        TOOLS.put(BlockType.STONE_SWORD, new ToolStats(ToolKind.SWORD, TIER_STONE, 132));
        TOOLS.put(BlockType.IRON_SWORD, new ToolStats(ToolKind.SWORD, TIER_IRON, 251));
        TOOLS.put(BlockType.DIAMOND_SWORD, new ToolStats(ToolKind.SWORD, TIER_DIAMOND, 1562));
        TOOLS.put(BlockType.WOOD_SHOVEL, new ToolStats(ToolKind.SHOVEL, TIER_WOOD, 60));
        TOOLS.put(BlockType.STONE_SHOVEL, new ToolStats(ToolKind.SHOVEL, TIER_STONE, 132));
        TOOLS.put(BlockType.IRON_SHOVEL, new ToolStats(ToolKind.SHOVEL, TIER_IRON, 251));
        TOOLS.put(BlockType.DIAMOND_SHOVEL, new ToolStats(ToolKind.SHOVEL, TIER_DIAMOND, 1562));
        TOOLS.put(BlockType.WOOD_HAMMER, new ToolStats(ToolKind.HAMMER, TIER_WOOD, 60));
        TOOLS.put(BlockType.STONE_HAMMER, new ToolStats(ToolKind.HAMMER, TIER_STONE, 132));
        TOOLS.put(BlockType.IRON_HAMMER, new ToolStats(ToolKind.HAMMER, TIER_IRON, 251));
        TOOLS.put(BlockType.DIAMOND_HAMMER, new ToolStats(ToolKind.HAMMER, TIER_DIAMOND, 1562));
        TOOLS.put(BlockType.WOOD_BROADAXE, new ToolStats(ToolKind.BROADAXE, TIER_WOOD, 60));
        TOOLS.put(BlockType.STONE_BROADAXE, new ToolStats(ToolKind.BROADAXE, TIER_STONE, 132));
        TOOLS.put(BlockType.IRON_BROADAXE, new ToolStats(ToolKind.BROADAXE, TIER_IRON, 251));
        TOOLS.put(BlockType.DIAMOND_BROADAXE, new ToolStats(ToolKind.BROADAXE, TIER_DIAMOND, 1562));

        // Soft earth - a shovel makes it quick (no tier requirement, just speed).
        put(BlockType.DIRT, 0.5f, ToolKind.SHOVEL, TIER_HAND);
        put(BlockType.GRASS, 0.6f, ToolKind.SHOVEL, TIER_HAND);
        put(BlockType.SAND, 0.5f, ToolKind.SHOVEL, TIER_HAND);
        put(BlockType.GRAVEL, 0.6f, ToolKind.SHOVEL, TIER_HAND);
        put(BlockType.SNOW, 0.2f, ToolKind.SHOVEL, TIER_HAND);
        // Plant/fibrous material - an axe is quickest (the sword's combat-only now).
        put(BlockType.CACTUS, 0.4f, ToolKind.AXE, TIER_HAND);

        // Instant, no tool - decoration.
        put(BlockType.LEAVES, 0.2f, ToolKind.AXE, TIER_HAND);
        put(BlockType.CHERRY_LEAVES, 0.2f, ToolKind.AXE, TIER_HAND);
        put(BlockType.TALL_GRASS, 0f, ToolKind.NONE, TIER_HAND);
        put(BlockType.FLOWER_RED, 0f, ToolKind.NONE, TIER_HAND);
        put(BlockType.FLOWER_YELLOW, 0f, ToolKind.NONE, TIER_HAND);
        put(BlockType.BERRY_BUSH, 0f, ToolKind.NONE, TIER_HAND);
        put(BlockType.TORCH, 0f, ToolKind.NONE, TIER_HAND);
        put(BlockType.DEAD_BUSH, 0f, ToolKind.NONE, TIER_HAND);
        put(BlockType.MUSHROOM_RED, 0f, ToolKind.NONE, TIER_HAND);
        put(BlockType.MUSHROOM_BROWN, 0f, ToolKind.NONE, TIER_HAND);
        put(BlockType.VINE, 0f, ToolKind.NONE, TIER_HAND);
        put(BlockType.BAMBOO, 0f, ToolKind.NONE, TIER_HAND);
        put(BlockType.LILY_PAD, 0f, ToolKind.NONE, TIER_HAND);
        put(BlockType.SEAWEED, 0f, ToolKind.NONE, TIER_HAND);
        put(BlockType.DOOR, 0f, ToolKind.NONE, TIER_HAND);
        put(BlockType.DOOR_OPEN, 0f, ToolKind.NONE, TIER_HAND);
        put(BlockType.TRAPDOOR, 0f, ToolKind.NONE, TIER_HAND);
        put(BlockType.TRAPDOOR_OPEN, 0f, ToolKind.NONE, TIER_HAND);

        // Biome surface blocks - a shovel helps, nothing required.
        put(BlockType.SWAMP_GRASS, 0.6f, ToolKind.SHOVEL, TIER_HAND);
        put(BlockType.RED_CLAY, 0.6f, ToolKind.SHOVEL, TIER_HAND);
        put(BlockType.MYCELIUM, 0.6f, ToolKind.SHOVEL, TIER_HAND);
        put(BlockType.ICE, 0.5f, ToolKind.SHOVEL, TIER_HAND);
        put(BlockType.PACKED_ICE, 0.5f, ToolKind.SHOVEL, TIER_HAND);
        put(BlockType.PUMPKIN, 0.5f, ToolKind.NONE, TIER_HAND);

        // Fluids - sources are quick to pick back up; flow is transient and instant.
        put(BlockType.WATER_SOURCE, 0.2f, ToolKind.NONE, TIER_HAND);
        put(BlockType.LAVA_SOURCE, 0.2f, ToolKind.NONE, TIER_HAND);
        put(BlockType.WATER_FLOW, 0f, ToolKind.NONE, TIER_HAND);
        put(BlockType.LAVA_FLOW, 0f, ToolKind.NONE, TIER_HAND);

        // Wood - a broadaxe is the heavy wood-cutter (twice as fast as an axe would be).
        put(BlockType.WOOD_LOG, 1.5f, ToolKind.BROADAXE, TIER_HAND);
        put(BlockType.PLANKS, 1.0f, ToolKind.BROADAXE, TIER_HAND);
        put(BlockType.PLANKS_SLAB, 1.0f, ToolKind.BROADAXE, TIER_HAND);

        // Stone and masonry - a hammer is the builder's tool (bare hands still work, just slow).
        put(BlockType.STONE, 2.5f, ToolKind.HAMMER, TIER_HAND);
        put(BlockType.STONE_SLAB, 2.5f, ToolKind.HAMMER, TIER_HAND);
        put(BlockType.GLASS, 0.5f, ToolKind.NONE, TIER_HAND);
        put(BlockType.LAMP, 0.5f, ToolKind.NONE, TIER_HAND);
        put(BlockType.FURNACE, 3.5f, ToolKind.HAMMER, TIER_HAND);

        // Ores - a pickaxe of at least the given tier is required, not just faster.
        put(BlockType.COAL_ORE, 2.5f, ToolKind.PICKAXE, TIER_WOOD);
        put(BlockType.IRON_ORE, 4.0f, ToolKind.PICKAXE, TIER_STONE);
        put(BlockType.GOLD_ORE, 4.0f, ToolKind.PICKAXE, TIER_IRON);
        put(BlockType.DIAMOND_ORE, 6.0f, ToolKind.PICKAXE, TIER_IRON);

        // Bedrock is handled separately (unbreakable) by Main, not through hardness.
    }

    private static void put(BlockType type, float hardness, ToolKind effectiveTool, int requiredTier) {
        BLOCKS.put(type, new BlockInfo(hardness, effectiveTool, requiredTier));
    }

    private Mining() {
    }

    public static boolean isTool(BlockType type) {
        return TOOLS.containsKey(type);
    }

    public static ToolStats toolStats(BlockType type) {
        return TOOLS.get(type);
    }

    private static BlockInfo infoFor(BlockType block) {
        return BLOCKS.getOrDefault(block, DEFAULT_BLOCK_INFO);
    }

    /** True if {@code heldItem} is sufficient to break {@code block} at all (ignoring speed). */
    public static boolean canBreak(BlockType block, BlockType heldItem) {
        BlockInfo info = infoFor(block);
        if (info.requiredTier() <= TIER_HAND) return true;
        ToolStats held = TOOLS.get(heldItem);
        return held != null && held.kind() == info.effectiveTool() && held.tier() >= info.requiredTier();
    }

    /**
     * Seconds required to break {@code block} with {@code heldItem} (which may be
     * a non-tool, i.e. bare hands). Returns {@link Float#POSITIVE_INFINITY} if
     * {@code heldItem} isn't sufficient - check {@link #canBreak} first.
     */
    public static float breakTimeSeconds(BlockType block, BlockType heldItem) {
        if (!canBreak(block, heldItem)) return Float.POSITIVE_INFINITY;
        BlockInfo info = infoFor(block);
        if (info.hardnessSeconds() <= 0f) return 0f;

        ToolStats held = TOOLS.get(heldItem);
        float speedMultiplier = 1f;
        if (held != null && held.kind() == info.effectiveTool()) {
            // Each tier above hand roughly doubles speed for its effective tool;
            // a broadaxe is the heavy wood-cutter, one tier of speed stronger.
            int power = held.tier() + (held.kind() == ToolKind.BROADAXE ? 1 : 0);
            speedMultiplier = 1 << power; // wood=2x, stone=4x, iron=8x, diamond=16x (broadaxe: double)
        }
        return info.hardnessSeconds() / speedMultiplier;
    }

    /** True if {@code item} is one of the swords (the combat tool). */
    public static boolean isSword(BlockType item) {
        ToolStats stats = TOOLS.get(item);
        return stats != null && stats.kind() == ToolKind.SWORD;
    }

    /** True if {@code item} is a hammer (the 3x3 area-mining tool). */
    public static boolean isHammer(BlockType item) {
        ToolStats stats = TOOLS.get(item);
        return stats != null && stats.kind() == ToolKind.HAMMER;
    }

    /**
     * Hit damage dealt when attacking a mob with {@code heldItem}: swords hit
     * harder than bare hands (punch = 1, wood/stone/iron/diamond sword = 4/5/6/7).
     */
    public static float attackDamage(BlockType heldItem) {
        ToolStats stats = TOOLS.get(heldItem);
        if (stats != null && stats.kind() == ToolKind.SWORD) {
            return switch (stats.tier()) {
                case TIER_WOOD -> 4f;
                case TIER_STONE -> 5f;
                case TIER_IRON -> 6f;
                default -> 7f; // diamond
            };
        }
        return 1f;
    }
}
