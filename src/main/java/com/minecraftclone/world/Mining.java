package com.minecraftclone.world;

import com.minecraftclone.player.ItemStack;
import com.minecraftclone.world.tinkers.TinkersItem;

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
        // Hoes till dirt on use; they are NOT shovels. Registering them as
        // SHOVEL used to make left-click delete dirt at shovel speed (a
        // diamond hoe broke dirt in ~0.03s — it looked like the block vanished).
        TOOLS.put(BlockType.WOOD_HOE,    new ToolStats(ToolKind.NONE, TIER_WOOD,    60));
        TOOLS.put(BlockType.STONE_HOE,   new ToolStats(ToolKind.NONE, TIER_STONE,  132));
        TOOLS.put(BlockType.IRON_HOE,    new ToolStats(ToolKind.NONE, TIER_IRON,   251));
        TOOLS.put(BlockType.DIAMOND_HOE, new ToolStats(ToolKind.NONE, TIER_DIAMOND, 1562));

        // Phase 0.5: Tinkers' Construct assembled tools are handled dynamically via
        // TinkersItem.Tool and TinkersRegistry — no static TOOLS entries needed.
        // Mining speed / tier for Tinkers tools is read from toolStatsFor(ItemStack).

        // Phase 0.5: Tinkers' Construct structure blocks (pickaxe required; moderate hardness)
        put(BlockType.SEARED_BRICK,         3.0f, ToolKind.PICKAXE, TIER_STONE);
        put(BlockType.SEARED_GLASS,         0.3f, ToolKind.PICKAXE, TIER_HAND);
        put(BlockType.SEARED_TANK,          3.0f, ToolKind.PICKAXE, TIER_STONE);
        put(BlockType.SMELTERY_DRAIN,       3.0f, ToolKind.PICKAXE, TIER_STONE);
        put(BlockType.SMELTERY_CONTROLLER,  3.5f, ToolKind.PICKAXE, TIER_STONE);
        // Crafting station blocks
        put(BlockType.CASTING_TABLE,        2.5f, ToolKind.PICKAXE, TIER_STONE);
        put(BlockType.CASTING_BASIN,        2.5f, ToolKind.PICKAXE, TIER_STONE);
        put(BlockType.PART_BUILDER,         2.5f, ToolKind.AXE,     TIER_HAND);
        put(BlockType.TOOL_STATION,         2.5f, ToolKind.AXE,     TIER_HAND);
        put(BlockType.ANVIL,                5.0f, ToolKind.HAMMER,  TIER_HAND);

        // Farming blocks
        put(BlockType.FARMLAND,     0.6f, ToolKind.SHOVEL, TIER_HAND);
        put(BlockType.FARMLAND_WET, 0.6f, ToolKind.SHOVEL, TIER_HAND);
        put(BlockType.CLAY,         0.6f, ToolKind.SHOVEL, TIER_HAND);
        // Crops: instant, no tool required.
        put(BlockType.WHEAT_STAGE_1,  0f, ToolKind.NONE, TIER_HAND);
        put(BlockType.WHEAT_STAGE_2,  0f, ToolKind.NONE, TIER_HAND);
        put(BlockType.WHEAT_STAGE_3,  0f, ToolKind.NONE, TIER_HAND);
        put(BlockType.WHEAT_STAGE_4,  0f, ToolKind.NONE, TIER_HAND);
        put(BlockType.POTATO_CROP_1,  0f, ToolKind.NONE, TIER_HAND);
        put(BlockType.POTATO_CROP_2,  0f, ToolKind.NONE, TIER_HAND);
        put(BlockType.POTATO_CROP_3,  0f, ToolKind.NONE, TIER_HAND);
        put(BlockType.CARROT_CROP_1,  0f, ToolKind.NONE, TIER_HAND);
        put(BlockType.CARROT_CROP_2,  0f, ToolKind.NONE, TIER_HAND);
        put(BlockType.CARROT_CROP_3,  0f, ToolKind.NONE, TIER_HAND);
        put(BlockType.SUGAR_CANE,     0f, ToolKind.NONE, TIER_HAND);

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
        put(BlockType.BIRCH_LEAVES, 0.2f, ToolKind.AXE, TIER_HAND);
        put(BlockType.JUNGLE_LEAVES, 0.2f, ToolKind.AXE, TIER_HAND);
        put(BlockType.PINE_LEAVES, 0.2f, ToolKind.AXE, TIER_HAND);
        put(BlockType.TALL_GRASS, 0f, ToolKind.NONE, TIER_HAND);
        put(BlockType.OAK_SAPLING, 0f, ToolKind.NONE, TIER_HAND);
        put(BlockType.BIRCH_SAPLING, 0f, ToolKind.NONE, TIER_HAND);
        put(BlockType.JUNGLE_SAPLING, 0f, ToolKind.NONE, TIER_HAND);
        put(BlockType.PINE_SAPLING, 0f, ToolKind.NONE, TIER_HAND);
        put(BlockType.CHERRY_SAPLING, 0f, ToolKind.NONE, TIER_HAND);
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
        put(BlockType.BED, 1.0f, ToolKind.NONE, TIER_HAND);
        put(BlockType.BED_HEAD, 1.0f, ToolKind.NONE, TIER_HAND);
        put(BlockType.BED_OCCUPIED, 1.0f, ToolKind.NONE, TIER_HAND);
        put(BlockType.BED_HEAD_OCCUPIED, 1.0f, ToolKind.NONE, TIER_HAND);

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
        put(BlockType.BIRCH_LOG, 1.5f, ToolKind.BROADAXE, TIER_HAND);
        put(BlockType.JUNGLE_LOG, 2.0f, ToolKind.BROADAXE, TIER_HAND); // jungle wood is denser
        put(BlockType.PINE_LOG, 1.5f, ToolKind.BROADAXE, TIER_HAND);
        put(BlockType.CHERRY_LOG, 1.5f, ToolKind.BROADAXE, TIER_HAND);
        put(BlockType.PLANKS, 1.0f, ToolKind.BROADAXE, TIER_HAND);
        put(BlockType.PLANKS_SLAB, 1.0f, ToolKind.BROADAXE, TIER_HAND);
        put(BlockType.CRAFTING_TABLE, 1.5f, ToolKind.BROADAXE, TIER_HAND);
        put(BlockType.CHEST, 1.5f, ToolKind.BROADAXE, TIER_HAND);
        put(BlockType.BARREL, 1.2f, ToolKind.BROADAXE, TIER_HAND);
        put(BlockType.PLANKS_STAIRS, 1.0f, ToolKind.BROADAXE, TIER_HAND);
        put(BlockType.WOODEN_FENCE, 1.0f, ToolKind.BROADAXE, TIER_HAND);

        // Textiles - soft blocks.
        put(BlockType.WOOL, 0.8f, ToolKind.NONE, TIER_HAND);

        // Stone and masonry - a hammer is the builder's tool (bare hands still work, just slow).
        put(BlockType.STONE, 2.5f, ToolKind.HAMMER, TIER_HAND);
        put(BlockType.STONE_SLAB, 2.5f, ToolKind.HAMMER, TIER_HAND);
        put(BlockType.STONE_STAIRS, 2.5f, ToolKind.HAMMER, TIER_HAND);
        put(BlockType.GLASS, 0.5f, ToolKind.NONE, TIER_HAND);
        put(BlockType.LAMP, 0.5f, ToolKind.NONE, TIER_HAND);
        put(BlockType.FURNACE, 3.5f, ToolKind.HAMMER, TIER_HAND);

        // Ores - a pickaxe of at least the given tier is required, not just faster.
        put(BlockType.COAL_ORE, 2.5f, ToolKind.PICKAXE, TIER_WOOD);
        put(BlockType.IRON_ORE, 4.0f, ToolKind.PICKAXE, TIER_STONE);
        put(BlockType.GOLD_ORE, 4.0f, ToolKind.PICKAXE, TIER_IRON);
        put(BlockType.DIAMOND_ORE, 6.0f, ToolKind.PICKAXE, TIER_IRON);

        // GTNH Ores - early game (stone tier)
        put(BlockType.COPPER_ORE, 2.0f, ToolKind.PICKAXE, TIER_STONE);
        put(BlockType.TIN_ORE, 2.0f, ToolKind.PICKAXE, TIER_STONE);
        put(BlockType.BAUXITE_ORE, 2.0f, ToolKind.PICKAXE, TIER_STONE);
        put(BlockType.ZINC_ORE, 2.0f, ToolKind.PICKAXE, TIER_STONE);
        put(BlockType.LEAD_ORE, 2.0f, ToolKind.PICKAXE, TIER_STONE);
        put(BlockType.SILVER_ORE, 2.0f, ToolKind.PICKAXE, TIER_STONE);

        // GTNH Ores - mid-game (iron tier)
        put(BlockType.NICKEL_ORE, 3.0f, ToolKind.PICKAXE, TIER_IRON);
        put(BlockType.COBALT_ORE, 3.0f, ToolKind.PICKAXE, TIER_IRON);
        put(BlockType.TUNGSTEN_ORE, 3.0f, ToolKind.PICKAXE, TIER_IRON);
        put(BlockType.MOLYBDENUM_ORE, 3.0f, ToolKind.PICKAXE, TIER_IRON);
        put(BlockType.PLATINUM_ORE, 3.0f, ToolKind.PICKAXE, TIER_IRON);

        // GTNH Ores - advanced (diamond tier)
        put(BlockType.CHROMIUM_ORE, 5.0f, ToolKind.PICKAXE, TIER_DIAMOND);
        put(BlockType.MANGANESE_ORE, 5.0f, ToolKind.PICKAXE, TIER_DIAMOND);
        put(BlockType.VANADIUM_ORE, 5.0f, ToolKind.PICKAXE, TIER_DIAMOND);
        put(BlockType.BERYLLIUM_ORE, 5.0f, ToolKind.PICKAXE, TIER_DIAMOND);
        put(BlockType.TITANIUM_ORE, 5.0f, ToolKind.PICKAXE, TIER_DIAMOND);

        // GTNH Ores - late-game (endgame tier)
        put(BlockType.URANIUM_ORE, 7.0f, ToolKind.PICKAXE, TIER_DIAMOND);
        put(BlockType.THORIUM_ORE, 7.0f, ToolKind.PICKAXE, TIER_DIAMOND);
        put(BlockType.PLUTONIUM_ORE, 7.0f, ToolKind.PICKAXE, TIER_DIAMOND);
        put(BlockType.IRIDIUM_ORE, 7.0f, ToolKind.PICKAXE, TIER_DIAMOND);

        // GTNH Small Ores - mining indicators, -1 tier (one tier easier than full ore)
        // Early-game small ores (wood tier - easier than stone tier full ores)
        put(BlockType.SMALL_COPPER_ORE, 1.0f, ToolKind.PICKAXE, TIER_WOOD);
        put(BlockType.SMALL_TIN_ORE, 1.0f, ToolKind.PICKAXE, TIER_WOOD);
        put(BlockType.SMALL_BAUXITE_ORE, 1.0f, ToolKind.PICKAXE, TIER_WOOD);
        put(BlockType.SMALL_ZINC_ORE, 1.0f, ToolKind.PICKAXE, TIER_WOOD);
        put(BlockType.SMALL_LEAD_ORE, 1.0f, ToolKind.PICKAXE, TIER_WOOD);
        put(BlockType.SMALL_SILVER_ORE, 1.0f, ToolKind.PICKAXE, TIER_WOOD);

        // Mid-game small ores (stone tier - easier than iron tier full ores)
        put(BlockType.SMALL_NICKEL_ORE, 1.5f, ToolKind.PICKAXE, TIER_STONE);
        put(BlockType.SMALL_COBALT_ORE, 1.5f, ToolKind.PICKAXE, TIER_STONE);
        put(BlockType.SMALL_TUNGSTEN_ORE, 1.5f, ToolKind.PICKAXE, TIER_STONE);
        put(BlockType.SMALL_MOLYBDENUM_ORE, 1.5f, ToolKind.PICKAXE, TIER_STONE);
        put(BlockType.SMALL_PLATINUM_ORE, 1.5f, ToolKind.PICKAXE, TIER_STONE);

        // Advanced small ores (iron tier - easier than diamond tier full ores)
        put(BlockType.SMALL_CHROMIUM_ORE, 2.5f, ToolKind.PICKAXE, TIER_IRON);
        put(BlockType.SMALL_MANGANESE_ORE, 2.5f, ToolKind.PICKAXE, TIER_IRON);
        put(BlockType.SMALL_VANADIUM_ORE, 2.5f, ToolKind.PICKAXE, TIER_IRON);
        put(BlockType.SMALL_BERYLLIUM_ORE, 2.5f, ToolKind.PICKAXE, TIER_IRON);
        put(BlockType.SMALL_TITANIUM_ORE, 2.5f, ToolKind.PICKAXE, TIER_IRON);

        // Late-game small ores (diamond tier - same as full ores, can't go lower)
        put(BlockType.SMALL_URANIUM_ORE, 3.5f, ToolKind.PICKAXE, TIER_DIAMOND);
        put(BlockType.SMALL_THORIUM_ORE, 3.5f, ToolKind.PICKAXE, TIER_DIAMOND);
        put(BlockType.SMALL_PLUTONIUM_ORE, 3.5f, ToolKind.PICKAXE, TIER_DIAMOND);
        put(BlockType.SMALL_IRIDIUM_ORE, 3.5f, ToolKind.PICKAXE, TIER_DIAMOND);

        // ── Extended GTNH ore pack ────────────────────────────────────────────
        // Iron chain (iron tier - common industrial metals)
        put(BlockType.MAGNETITE_ORE,          3.0f, ToolKind.PICKAXE, TIER_IRON);
        put(BlockType.HEMATITE_ORE,           3.0f, ToolKind.PICKAXE, TIER_IRON);
        put(BlockType.BROWN_LIMONITE_ORE,     2.5f, ToolKind.PICKAXE, TIER_STONE);
        put(BlockType.YELLOW_LIMONITE_ORE,    2.5f, ToolKind.PICKAXE, TIER_STONE);
        put(BlockType.BANDED_IRON_ORE,        3.0f, ToolKind.PICKAXE, TIER_IRON);
        put(BlockType.VANADIUM_MAGNETITE_ORE, 3.0f, ToolKind.PICKAXE, TIER_IRON);
        // Copper chain (stone tier - early game)
        put(BlockType.CHALCOPYRITE_ORE,  2.5f, ToolKind.PICKAXE, TIER_STONE);
        put(BlockType.TETRAHEDRITE_ORE,  2.5f, ToolKind.PICKAXE, TIER_STONE);
        put(BlockType.MALACHITE_ORE,     2.5f, ToolKind.PICKAXE, TIER_STONE);
        // Lead / Zinc (stone tier)
        put(BlockType.GALENA_ORE,        2.5f, ToolKind.PICKAXE, TIER_STONE);
        put(BlockType.SPHALERITE_ORE,    2.5f, ToolKind.PICKAXE, TIER_STONE);
        // Nickel / Cobalt (iron tier)
        put(BlockType.GARNIERITE_ORE,    3.0f, ToolKind.PICKAXE, TIER_IRON);
        put(BlockType.PENTLANDITE_ORE,   3.0f, ToolKind.PICKAXE, TIER_IRON);
        put(BlockType.COBALTITE_ORE,     3.0f, ToolKind.PICKAXE, TIER_IRON);
        // Sulfur chain (stone tier)
        put(BlockType.PYRITE_ORE,        2.5f, ToolKind.PICKAXE, TIER_STONE);
        put(BlockType.ARSENOPYRITE_ORE,  2.5f, ToolKind.PICKAXE, TIER_STONE);
        put(BlockType.SULFUR_ORE,        2.0f, ToolKind.PICKAXE, TIER_STONE);
        put(BlockType.CINNABAR_ORE,      2.5f, ToolKind.PICKAXE, TIER_STONE);
        // Tin (stone tier)
        put(BlockType.CASSITERITE_ORE,   2.5f, ToolKind.PICKAXE, TIER_STONE);
        // Tungsten family (diamond tier - very hard)
        put(BlockType.SCHEELITE_ORE,     3.5f, ToolKind.PICKAXE, TIER_DIAMOND);
        put(BlockType.WOLFRAMITE_ORE,    3.5f, ToolKind.PICKAXE, TIER_DIAMOND);
        put(BlockType.MOLYBDENITE_ORE,   3.0f, ToolKind.PICKAXE, TIER_IRON);
        put(BlockType.FERBERITE_ORE,     3.5f, ToolKind.PICKAXE, TIER_DIAMOND);
        // Chromium / Titanium (iron/diamond tier)
        put(BlockType.CHROMITE_ORE,      3.0f, ToolKind.PICKAXE, TIER_IRON);
        put(BlockType.ILMENITE_ORE,      3.0f, ToolKind.PICKAXE, TIER_IRON);
        put(BlockType.RUTILE_ORE,        3.0f, ToolKind.PICKAXE, TIER_IRON);
        // Uranium family (diamond tier - radioactive, requires best tools)
        put(BlockType.URANINITE_ORE,     3.5f, ToolKind.PICKAXE, TIER_DIAMOND);
        put(BlockType.PITCHBLENDE_ORE,   3.5f, ToolKind.PICKAXE, TIER_DIAMOND);
        // Rare earth (iron tier)
        put(BlockType.MONAZITE_ORE,      3.0f, ToolKind.PICKAXE, TIER_IRON);
        put(BlockType.BASTNASITE_ORE,    3.0f, ToolKind.PICKAXE, TIER_IRON);
        // Vanadium / Manganese minerals (iron tier)
        put(BlockType.VANADINITE_ORE,    3.0f, ToolKind.PICKAXE, TIER_IRON);
        put(BlockType.PYROLUSITE_ORE,    2.5f, ToolKind.PICKAXE, TIER_STONE);
        // Carbon / Light metals (stone tier)
        put(BlockType.GRAPHITE_ORE,      2.5f, ToolKind.PICKAXE, TIER_STONE);
        put(BlockType.LITHIUM_ORE,       2.5f, ToolKind.PICKAXE, TIER_STONE);
        // GTNH exotics (diamond tier)
        put(BlockType.NAQUADAH_ORE,          4.0f, ToolKind.PICKAXE, TIER_DIAMOND);
        put(BlockType.NAQUADAH_ENRICHED_ORE, 4.0f, ToolKind.PICKAXE, TIER_DIAMOND);
        put(BlockType.TRINIUM_ORE,           4.0f, ToolKind.PICKAXE, TIER_DIAMOND);
        // Additional rare earths / PGM (diamond tier)
        put(BlockType.NEODYMIUM_ORE,     3.5f, ToolKind.PICKAXE, TIER_DIAMOND);
        put(BlockType.CERIUM_ORE,        3.5f, ToolKind.PICKAXE, TIER_DIAMOND);
        put(BlockType.OSMIUM_ORE,        4.0f, ToolKind.PICKAXE, TIER_DIAMOND);
        put(BlockType.PALLADIUM_ORE,     3.5f, ToolKind.PICKAXE, TIER_DIAMOND);
        // Rock/mineral ores (stone tier)
        put(BlockType.CALCITE_ORE,       2.0f, ToolKind.PICKAXE, TIER_STONE);
        put(BlockType.OLIVINE_ORE,       2.5f, ToolKind.PICKAXE, TIER_STONE);
        put(BlockType.TALC_ORE,          2.0f, ToolKind.PICKAXE, TIER_STONE);
        put(BlockType.BENTONITE_ORE,     2.0f, ToolKind.PICKAXE, TIER_STONE);
        // Lapis components (stone tier)
        put(BlockType.SODALITE_ORE,      2.5f, ToolKind.PICKAXE, TIER_STONE);
        put(BlockType.LAZURITE_ORE,      2.5f, ToolKind.PICKAXE, TIER_STONE);
        // Evaporites (stone tier)
        put(BlockType.SALT_ORE,          1.5f, ToolKind.PICKAXE, TIER_STONE);
        put(BlockType.ROCK_SALT_ORE,     1.5f, ToolKind.PICKAXE, TIER_STONE);
        put(BlockType.SALTPETER_ORE,     2.0f, ToolKind.PICKAXE, TIER_STONE);
        put(BlockType.BORAX_ORE,         2.0f, ToolKind.PICKAXE, TIER_STONE);
        // Phosphates (stone tier)
        put(BlockType.APATITE_ORE,       2.5f, ToolKind.PICKAXE, TIER_STONE);
        put(BlockType.PHOSPHATE_ORE,     2.5f, ToolKind.PICKAXE, TIER_STONE);
        put(BlockType.PYROCHLORE_ORE,    3.0f, ToolKind.PICKAXE, TIER_IRON);
        // Gemstones (iron tier)
        put(BlockType.LEPIDOLITE_ORE,    3.0f, ToolKind.PICKAXE, TIER_IRON);
        put(BlockType.RUBY_ORE,          3.0f, ToolKind.PICKAXE, TIER_IRON);
        put(BlockType.SAPPHIRE_ORE,      3.0f, ToolKind.PICKAXE, TIER_IRON);
        put(BlockType.GREEN_SAPPHIRE_ORE,3.0f, ToolKind.PICKAXE, TIER_IRON);
        put(BlockType.PYROPE_ORE,        3.0f, ToolKind.PICKAXE, TIER_IRON);
        put(BlockType.SPESSARTINE_ORE,   3.0f, ToolKind.PICKAXE, TIER_IRON);

        // Extended small ores (half the hardness of their full-vein counterpart, same tier)
        put(BlockType.SMALL_MAGNETITE_ORE,          1.5f, ToolKind.PICKAXE, TIER_IRON);
        put(BlockType.SMALL_HEMATITE_ORE,           1.5f, ToolKind.PICKAXE, TIER_IRON);
        put(BlockType.SMALL_BROWN_LIMONITE_ORE,     1.2f, ToolKind.PICKAXE, TIER_STONE);
        put(BlockType.SMALL_YELLOW_LIMONITE_ORE,    1.2f, ToolKind.PICKAXE, TIER_STONE);
        put(BlockType.SMALL_BANDED_IRON_ORE,        1.5f, ToolKind.PICKAXE, TIER_IRON);
        put(BlockType.SMALL_VANADIUM_MAGNETITE_ORE, 1.5f, ToolKind.PICKAXE, TIER_IRON);
        put(BlockType.SMALL_CHALCOPYRITE_ORE,  1.2f, ToolKind.PICKAXE, TIER_STONE);
        put(BlockType.SMALL_TETRAHEDRITE_ORE,  1.2f, ToolKind.PICKAXE, TIER_STONE);
        put(BlockType.SMALL_MALACHITE_ORE,     1.2f, ToolKind.PICKAXE, TIER_STONE);
        put(BlockType.SMALL_GALENA_ORE,        1.2f, ToolKind.PICKAXE, TIER_STONE);
        put(BlockType.SMALL_SPHALERITE_ORE,    1.2f, ToolKind.PICKAXE, TIER_STONE);
        put(BlockType.SMALL_GARNIERITE_ORE,    1.5f, ToolKind.PICKAXE, TIER_IRON);
        put(BlockType.SMALL_PENTLANDITE_ORE,   1.5f, ToolKind.PICKAXE, TIER_IRON);
        put(BlockType.SMALL_COBALTITE_ORE,     1.5f, ToolKind.PICKAXE, TIER_IRON);
        put(BlockType.SMALL_PYRITE_ORE,        1.2f, ToolKind.PICKAXE, TIER_STONE);
        put(BlockType.SMALL_ARSENOPYRITE_ORE,  1.2f, ToolKind.PICKAXE, TIER_STONE);
        put(BlockType.SMALL_SULFUR_ORE,        1.0f, ToolKind.PICKAXE, TIER_STONE);
        put(BlockType.SMALL_CINNABAR_ORE,      1.2f, ToolKind.PICKAXE, TIER_STONE);
        put(BlockType.SMALL_CASSITERITE_ORE,   1.2f, ToolKind.PICKAXE, TIER_STONE);
        put(BlockType.SMALL_SCHEELITE_ORE,     1.8f, ToolKind.PICKAXE, TIER_DIAMOND);
        put(BlockType.SMALL_WOLFRAMITE_ORE,    1.8f, ToolKind.PICKAXE, TIER_DIAMOND);
        put(BlockType.SMALL_MOLYBDENITE_ORE,   1.5f, ToolKind.PICKAXE, TIER_IRON);
        put(BlockType.SMALL_CHROMITE_ORE,      1.5f, ToolKind.PICKAXE, TIER_IRON);
        put(BlockType.SMALL_ILMENITE_ORE,      1.5f, ToolKind.PICKAXE, TIER_IRON);
        put(BlockType.SMALL_RUTILE_ORE,        1.5f, ToolKind.PICKAXE, TIER_IRON);
        put(BlockType.SMALL_URANINITE_ORE,     1.8f, ToolKind.PICKAXE, TIER_DIAMOND);
        put(BlockType.SMALL_PITCHBLENDE_ORE,   1.8f, ToolKind.PICKAXE, TIER_DIAMOND);
        put(BlockType.SMALL_MONAZITE_ORE,      1.5f, ToolKind.PICKAXE, TIER_IRON);
        put(BlockType.SMALL_NAQUADAH_ORE,      2.0f, ToolKind.PICKAXE, TIER_DIAMOND);
        put(BlockType.SMALL_TRINIUM_ORE,       2.0f, ToolKind.PICKAXE, TIER_DIAMOND);
        put(BlockType.SMALL_OSMIUM_ORE,        2.0f, ToolKind.PICKAXE, TIER_DIAMOND);
        put(BlockType.SMALL_PALLADIUM_ORE,     1.8f, ToolKind.PICKAXE, TIER_DIAMOND);
        put(BlockType.SMALL_RUBY_ORE,          1.5f, ToolKind.PICKAXE, TIER_IRON);
        put(BlockType.SMALL_NEODYMIUM_ORE,     1.8f, ToolKind.PICKAXE, TIER_DIAMOND);
        put(BlockType.SMALL_CERIUM_ORE,        1.8f, ToolKind.PICKAXE, TIER_DIAMOND);
        put(BlockType.SMALL_PYROLUSITE_ORE,    1.2f, ToolKind.PICKAXE, TIER_STONE);
        put(BlockType.SMALL_GRAPHITE_ORE,      1.2f, ToolKind.PICKAXE, TIER_STONE);
        put(BlockType.SMALL_LITHIUM_ORE,       1.2f, ToolKind.PICKAXE, TIER_STONE);
        put(BlockType.SMALL_VANADINITE_ORE,    1.5f, ToolKind.PICKAXE, TIER_IRON);
        put(BlockType.SMALL_BASTNASITE_ORE,    1.5f, ToolKind.PICKAXE, TIER_IRON);

        // Dimension blocks.
        put(BlockType.NETHERRACK, 1.2f, ToolKind.PICKAXE, TIER_HAND);
        put(BlockType.SOUL_SAND, 0.5f, ToolKind.NONE, TIER_HAND);
        put(BlockType.GLOWSTONE, 0.6f, ToolKind.NONE, TIER_HAND);
        put(BlockType.END_STONE, 2.2f, ToolKind.PICKAXE, TIER_HAND);
        put(BlockType.OBSIDIAN, 8.0f, ToolKind.PICKAXE, TIER_IRON); // tough - needs at least an iron pickaxe
        put(BlockType.NETHER_PORTAL, 0.1f, ToolKind.NONE, TIER_HAND);
        put(BlockType.END_PORTAL, 0.1f, ToolKind.NONE, TIER_HAND);

        // Bedrock is handled separately (unbreakable) by Main, not through hardness.

        // Explosives.
        put(BlockType.TNT, 0.0f, ToolKind.NONE, TIER_HAND); // instant break (but explodes when ignited)
    }

    private static void put(BlockType type, float hardness, ToolKind effectiveTool, int requiredTier) {
        BLOCKS.put(type, new BlockInfo(hardness, effectiveTool, requiredTier));
    }

    private Mining() {
    }

    public static boolean isTool(BlockType type) {
        return TOOLS.containsKey(type) || (type != null && type.isTinkersTool());
    }

    /**
     * True if the held {@link ItemStack} is a tool (vanilla or Tinkers').
     */
    public static boolean isTool(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        return isTool(stack.type());
    }

    public static ToolStats toolStats(BlockType type) {
        return TOOLS.get(type);
    }

    /**
     * Returns synthetic {@link ToolStats} for a Tinkers' assembled tool read
     * from the {@link TinkersItem.Tool} payload, or {@code null} for non-tools.
     */
    public static ToolStats toolStatsFor(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        // Vanilla tool — look up the static table.
        ToolStats vanillaStats = TOOLS.get(stack.type());
        if (vanillaStats != null) return vanillaStats;
        // Tinkers' tool — derive stats from the item payload.
        TinkersItem.Tool tool = stack.tinkersTool();
        if (tool != null) {
            ToolKind kind = tool.kind;
            return new ToolStats(kind, tool.miningTier(), tool.maxDurability);
        }
        return null;
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
     * True if the held {@link ItemStack} (vanilla or Tinkers') is sufficient
     * to break {@code block} at all (ignoring speed).
     * <p>Named {@code canBreakItem} rather than {@code canBreak} to avoid
     * overload ambiguity when callers pass {@code null} as the second argument.
     */
    public static boolean canBreakItem(BlockType block, ItemStack heldItem) {
        if (heldItem == null || heldItem.isEmpty()) return canBreak(block, (BlockType) null);
        // For Tinkers' tools read tier and kind from the payload.
        TinkersItem.Tool tinkersTool = heldItem.tinkersTool();
        if (tinkersTool != null) {
            BlockInfo info = infoFor(block);
            if (info.requiredTier() <= TIER_HAND) return true;
            return tinkersTool.kind == info.effectiveTool()
                && tinkersTool.miningTier() >= info.requiredTier();
        }
        return canBreak(block, heldItem.type());
    }

    /**
     * Whether the player can actually remove {@code block} right now.
     * Creative ignores tool-tier gates (bare hands break iron ore); bedrock
     * and air are never removable. Survival still needs the right tool.
     */
    public static boolean canRemove(BlockType block, ItemStack held, boolean creative) {
        if (block == null || block == BlockType.AIR || block == BlockType.BEDROCK) return false;
        return creative || canBreakItem(block, held);
    }

    /**
     * One-line harvest status for the look-at overlay: {@code "Can mine"},
     * {@code "Need Iron Pickaxe"}, {@code "Unbreakable"}, or {@code "Can't mine"}.
     * {@code null} if there's nothing to show (air / missing block).
     *
     * @param instantBreak creative-style: any breakable block is harvestable
     * @param canBreakBlocks false in adventure/spectator
     */
    public static String harvestHint(BlockType block, ItemStack held, boolean instantBreak, boolean canBreakBlocks) {
        if (block == null || block == BlockType.AIR) return null;
        if (block == BlockType.BEDROCK) return "Unbreakable";
        if (!canBreakBlocks) return "Can't mine";
        if (instantBreak || canBreakItem(block, held)) return "Can mine";
        BlockInfo info = infoFor(block);
        String tool = prettyEnum(info.effectiveTool().name());
        String tier = tierName(info.requiredTier());
        if (info.effectiveTool() == ToolKind.NONE) return "Need a better tool";
        if (tier.isEmpty()) return "Need " + tool;
        return "Need " + tier + " " + tool;
    }

    /** True when {@link #harvestHint} is the success line (green on the overlay). */
    public static boolean harvestHintPositive(String hint) {
        return "Can mine".equals(hint);
    }

    static String tierName(int tier) {
        return switch (tier) {
            case TIER_WOOD -> "Wood";
            case TIER_STONE -> "Stone";
            case TIER_IRON -> "Iron";
            case TIER_DIAMOND -> "Diamond";
            default -> "";
        };
    }

    static String prettyEnum(String name) {
        StringBuilder sb = new StringBuilder(name.length());
        boolean upper = true;
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
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

    /**
     * Seconds required to break {@code block} with the held {@link ItemStack}
     * (vanilla or Tinkers').  Returns {@link Float#POSITIVE_INFINITY} if the
     * item cannot break the block at all.
     * <p>Named {@code breakTimeItem} rather than {@code breakTimeSeconds} to
     * avoid overload ambiguity when callers pass {@code null} as the second
     * argument (both {@code BlockType} and {@code ItemStack} are reference types).
     */
    public static float breakTimeItem(BlockType block, ItemStack heldItem) {
        if (!canBreakItem(block, heldItem)) return Float.POSITIVE_INFINITY;
        BlockInfo info = infoFor(block);
        if (info.hardnessSeconds() <= 0f) return 0f;

        TinkersItem.Tool tinkersTool = (heldItem != null) ? heldItem.tinkersTool() : null;
        float speedMultiplier = 1f;
        if (tinkersTool != null && tinkersTool.kind == info.effectiveTool()) {
            // Tinkers' speed is a float multiplier from the head material.
            speedMultiplier = tinkersTool.miningSpeed();
        } else if (heldItem != null) {
            ToolStats held = TOOLS.get(heldItem.type());
            if (held != null && held.kind() == info.effectiveTool()) {
                int power = held.tier() + (held.kind() == ToolKind.BROADAXE ? 1 : 0);
                speedMultiplier = 1 << power;
            }
        }
        return info.hardnessSeconds() / speedMultiplier;
    }

    /** True if {@code item} is one of the swords (the combat tool). */
    public static boolean isSword(BlockType item) {
        ToolStats stats = TOOLS.get(item);
        return stats != null && stats.kind() == ToolKind.SWORD;
    }

    /** True if the held stack is a sword (vanilla or Tinkers'). */
    public static boolean isSword(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        TinkersItem.Tool tool = stack.tinkersTool();
        if (tool != null) return tool.kind == ToolKind.SWORD;
        return isSword(stack.type());
    }

    /** True if {@code item} is a hammer (the 3x3 area-mining tool). */
    public static boolean isHammer(BlockType item) {
        ToolStats stats = TOOLS.get(item);
        return stats != null && stats.kind() == ToolKind.HAMMER;
    }

    /** True if the held stack is a hammer (vanilla or Tinkers'). */
    public static boolean isHammer(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        TinkersItem.Tool tool = stack.tinkersTool();
        if (tool != null) return tool.kind == ToolKind.HAMMER;
        return isHammer(stack.type());
    }

    /** True if {@code item} is a hoe (used to till dirt into farmland via right-click). */
    public static boolean isHoe(BlockType item) {
        return item != null && item.isHoe();
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

    /**
     * Hit damage for the held {@link ItemStack}. Tinkers' swords use the head
     * material's mining tier (wood/stone/iron/diamond → 4/5/6/7).
     */
    public static float attackDamage(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return 1f;
        TinkersItem.Tool tool = stack.tinkersTool();
        if (tool != null && tool.kind == ToolKind.SWORD) {
            return switch (tool.miningTier()) {
                case TIER_WOOD -> 4f;
                case TIER_STONE -> 5f;
                case TIER_IRON -> 6f;
                default -> tool.miningTier() >= TIER_DIAMOND ? 7f : 4f;
            };
        }
        return attackDamage(stack.type());
    }
}
