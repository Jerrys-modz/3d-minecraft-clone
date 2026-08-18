package com.minecraftclone.player;

import com.minecraftclone.world.BlockType;

/**
 * The creative-mode item catalog, organized into Minecraft-style tabs. Each
 * tab lists every item in that category; the creative inventory screen renders
 * the selected tab as a grid you click to add items to the hotbar.
 * <p>
 * Add a new block to a tab here when you add a new {@link BlockType}, so
 * creative mode can hand it out.
 */
public final class CreativeCatalog {

    /** One creative tab: a display label and the items it contains. */
    public record Tab(String label, BlockType[] items) {
    }

    public static final Tab[] TABS = {
            new Tab("Building", new BlockType[]{
                    BlockType.STONE, BlockType.DIRT, BlockType.GRASS, BlockType.SAND,
                    BlockType.WOOD_LOG, BlockType.PLANKS, BlockType.LEAVES, BlockType.GRAVEL, BlockType.SNOW,
                    BlockType.GLASS, BlockType.STONE_SLAB, BlockType.PLANKS_SLAB, BlockType.LAMP, BlockType.FURNACE,
                    BlockType.CRAFTING_TABLE, BlockType.CHEST, BlockType.BARREL,
                    BlockType.STONE_STAIRS, BlockType.PLANKS_STAIRS, BlockType.WOODEN_FENCE,
                    BlockType.CACTUS, BlockType.WATER_SOURCE, BlockType.LAVA_SOURCE,
                    BlockType.SWAMP_GRASS, BlockType.RED_CLAY, BlockType.MYCELIUM, BlockType.ICE, BlockType.PACKED_ICE,
                    BlockType.PUMPKIN,
                    BlockType.NETHERRACK, BlockType.SOUL_SAND, BlockType.GLOWSTONE, BlockType.END_STONE, BlockType.OBSIDIAN,
                    BlockType.NETHER_PORTAL, BlockType.END_PORTAL,
            }),
            new Tab("Decoration", new BlockType[]{
                    BlockType.TALL_GRASS, BlockType.FLOWER_RED, BlockType.FLOWER_YELLOW,
                    BlockType.BERRY_BUSH, BlockType.TORCH, BlockType.DEAD_BUSH,
                    BlockType.MUSHROOM_RED, BlockType.MUSHROOM_BROWN, BlockType.VINE,
                    BlockType.BAMBOO, BlockType.LILY_PAD, BlockType.SEAWEED, BlockType.DOOR, BlockType.TRAPDOOR,
                    BlockType.BED, BlockType.BED_HEAD,
            }),
            new Tab("Materials", new BlockType[]{
                    BlockType.COAL_ORE, BlockType.IRON_ORE, BlockType.GOLD_ORE, BlockType.DIAMOND_ORE,
                    BlockType.COAL, BlockType.STICK, BlockType.IRON_INGOT, BlockType.GOLD_INGOT, BlockType.DIAMOND,
                    BlockType.WOOL,
                    // GTNH Ores - early game
                    BlockType.COPPER_ORE, BlockType.CRUSHED_COPPER, BlockType.COPPER_DUST, BlockType.COPPER_INGOT,
                    BlockType.TIN_ORE, BlockType.CRUSHED_TIN, BlockType.TIN_DUST, BlockType.TIN_INGOT,
                    BlockType.BAUXITE_ORE, BlockType.CRUSHED_BAUXITE, BlockType.BAUXITE_DUST, BlockType.ALUMINUM_INGOT,
                    BlockType.ZINC_ORE, BlockType.CRUSHED_ZINC, BlockType.ZINC_DUST, BlockType.ZINC_INGOT,
                    BlockType.LEAD_ORE, BlockType.CRUSHED_LEAD, BlockType.LEAD_DUST, BlockType.LEAD_INGOT,
                    BlockType.SILVER_ORE, BlockType.CRUSHED_SILVER, BlockType.SILVER_DUST, BlockType.SILVER_INGOT,
                    // GTNH Ores - mid-game
                    BlockType.NICKEL_ORE, BlockType.CRUSHED_NICKEL, BlockType.NICKEL_DUST, BlockType.NICKEL_INGOT,
                    BlockType.COBALT_ORE, BlockType.CRUSHED_COBALT, BlockType.COBALT_DUST, BlockType.COBALT_INGOT,
                    BlockType.TUNGSTEN_ORE, BlockType.CRUSHED_TUNGSTEN, BlockType.TUNGSTEN_DUST, BlockType.TUNGSTEN_INGOT,
                    BlockType.MOLYBDENUM_ORE, BlockType.CRUSHED_MOLYBDENUM, BlockType.MOLYBDENUM_DUST, BlockType.MOLYBDENUM_INGOT,
                    BlockType.PLATINUM_ORE, BlockType.CRUSHED_PLATINUM, BlockType.PLATINUM_DUST, BlockType.PLATINUM_INGOT,
                    // GTNH Ores - advanced
                    BlockType.CHROMIUM_ORE, BlockType.CRUSHED_CHROMIUM, BlockType.CHROMIUM_DUST, BlockType.CHROMIUM_INGOT,
                    BlockType.MANGANESE_ORE, BlockType.CRUSHED_MANGANESE, BlockType.MANGANESE_DUST, BlockType.MANGANESE_INGOT,
                    BlockType.VANADIUM_ORE, BlockType.CRUSHED_VANADIUM, BlockType.VANADIUM_DUST, BlockType.VANADIUM_INGOT,
                    BlockType.BERYLLIUM_ORE, BlockType.CRUSHED_BERYLLIUM, BlockType.BERYLLIUM_DUST, BlockType.BERYLLIUM_INGOT,
                    BlockType.TITANIUM_ORE, BlockType.CRUSHED_TITANIUM, BlockType.TITANIUM_DUST, BlockType.TITANIUM_INGOT,
                    // GTNH Ores - late-game
                    BlockType.URANIUM_ORE, BlockType.CRUSHED_URANIUM, BlockType.URANIUM_DUST, BlockType.URANIUM_INGOT,
                    BlockType.THORIUM_ORE, BlockType.CRUSHED_THORIUM, BlockType.THORIUM_DUST, BlockType.THORIUM_INGOT,
                    BlockType.PLUTONIUM_ORE, BlockType.CRUSHED_PLUTONIUM, BlockType.PLUTONIUM_DUST, BlockType.PLUTONIUM_INGOT,
                    BlockType.IRIDIUM_ORE, BlockType.CRUSHED_IRIDIUM, BlockType.IRIDIUM_DUST, BlockType.IRIDIUM_INGOT,
            }),
            new Tab("Tools", new BlockType[]{
                    BlockType.WOOD_PICKAXE, BlockType.STONE_PICKAXE, BlockType.IRON_PICKAXE, BlockType.DIAMOND_PICKAXE,
                    BlockType.WOOD_AXE, BlockType.STONE_AXE, BlockType.IRON_AXE, BlockType.DIAMOND_AXE,
                    BlockType.WOOD_SHOVEL, BlockType.STONE_SHOVEL, BlockType.IRON_SHOVEL, BlockType.DIAMOND_SHOVEL,
                    BlockType.WOOD_HAMMER, BlockType.STONE_HAMMER, BlockType.IRON_HAMMER, BlockType.DIAMOND_HAMMER,
                    BlockType.WOOD_BROADAXE, BlockType.STONE_BROADAXE, BlockType.IRON_BROADAXE, BlockType.DIAMOND_BROADAXE,
            }),
            new Tab("Combat", new BlockType[]{
                    BlockType.WOOD_SWORD, BlockType.STONE_SWORD, BlockType.IRON_SWORD, BlockType.DIAMOND_SWORD,
            }),
            new Tab("Armor", new BlockType[]{
                    BlockType.WOOD_HELMET, BlockType.STONE_HELMET, BlockType.IRON_HELMET, BlockType.DIAMOND_HELMET,
                    BlockType.WOOD_CHESTPLATE, BlockType.STONE_CHESTPLATE, BlockType.IRON_CHESTPLATE, BlockType.DIAMOND_CHESTPLATE,
                    BlockType.WOOD_LEGGINGS, BlockType.STONE_LEGGINGS, BlockType.IRON_LEGGINGS, BlockType.DIAMOND_LEGGINGS,
                    BlockType.WOOD_BOOTS, BlockType.STONE_BOOTS, BlockType.IRON_BOOTS, BlockType.DIAMOND_BOOTS,
                    BlockType.FUR_HELMET, BlockType.FUR_CHESTPLATE, BlockType.FUR_LEGGINGS, BlockType.FUR_BOOTS,
                    BlockType.WOLF_HELMET, BlockType.WOLF_CHESTPLATE, BlockType.WOLF_LEGGINGS, BlockType.WOLF_BOOTS,
                    BlockType.BEAR_HELMET, BlockType.BEAR_CHESTPLATE, BlockType.BEAR_LEGGINGS, BlockType.BEAR_BOOTS,
            }),
            new Tab("Food", new BlockType[]{
                    BlockType.APPLE, BlockType.BERRIES,
                    BlockType.RAW_PORKCHOP, BlockType.RAW_BEEF, BlockType.MUTTON,
            }),
    };

    private CreativeCatalog() {
    }
}
