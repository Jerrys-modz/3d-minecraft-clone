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
 * Swords exist as a tool kind (faster than bare hands on plant/fibrous
 * material like leaves and cacti) but there's no combat yet - no mobs to
 * swing them at - so for now that speed bonus is their only effect.
 */
public final class Mining {

    public enum ToolKind {NONE, PICKAXE, AXE, SWORD}

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

        // Soft, no tool required - fast either way.
        put(BlockType.DIRT, 0.5f, ToolKind.NONE, TIER_HAND);
        put(BlockType.GRASS, 0.6f, ToolKind.NONE, TIER_HAND);
        put(BlockType.SAND, 0.5f, ToolKind.NONE, TIER_HAND);
        put(BlockType.GRAVEL, 0.6f, ToolKind.NONE, TIER_HAND);
        put(BlockType.SNOW, 0.2f, ToolKind.NONE, TIER_HAND);
        // Swords are quicker through plant/fibrous material - no requirement, just a bonus.
        put(BlockType.CACTUS, 0.4f, ToolKind.SWORD, TIER_HAND);

        // Instant, no tool - decoration.
        put(BlockType.LEAVES, 0.2f, ToolKind.SWORD, TIER_HAND);
        put(BlockType.TALL_GRASS, 0f, ToolKind.NONE, TIER_HAND);
        put(BlockType.FLOWER_RED, 0f, ToolKind.NONE, TIER_HAND);
        put(BlockType.FLOWER_YELLOW, 0f, ToolKind.NONE, TIER_HAND);
        put(BlockType.BERRY_BUSH, 0f, ToolKind.NONE, TIER_HAND);
        put(BlockType.TORCH, 0f, ToolKind.NONE, TIER_HAND);

        // Wood - axe helps but isn't required.
        put(BlockType.WOOD_LOG, 1.5f, ToolKind.AXE, TIER_HAND);
        put(BlockType.PLANKS, 1.0f, ToolKind.AXE, TIER_HAND);

        // Stone family - pickaxe helps but bare hands still work (just slow).
        put(BlockType.STONE, 2.5f, ToolKind.PICKAXE, TIER_HAND);
        put(BlockType.GLASS, 0.5f, ToolKind.NONE, TIER_HAND);
        put(BlockType.LAMP, 0.5f, ToolKind.NONE, TIER_HAND);

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
            // Each tier above hand roughly doubles speed for its effective tool.
            speedMultiplier = 1 << held.tier(); // wood=2x, stone=4x, iron=8x, diamond=16x
        }
        return info.hardnessSeconds() / speedMultiplier;
    }
}
