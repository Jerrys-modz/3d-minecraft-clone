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
                    // Phase 0: farming structures
                    BlockType.FARMLAND, BlockType.FARMLAND_WET,
                    BlockType.CLAY,
                    // Phase 0.5: Tinkers' Construct structure blocks
                    BlockType.SEARED_BRICK, BlockType.SEARED_GLASS, BlockType.SEARED_TANK,
                    BlockType.SMELTERY_DRAIN, BlockType.SMELTERY_CONTROLLER,
                    BlockType.CASTING_TABLE, BlockType.CASTING_BASIN,
                    BlockType.PART_BUILDER, BlockType.TOOL_STATION,
            }),
            new Tab("Decoration", new BlockType[]{
                    BlockType.TALL_GRASS, BlockType.FLOWER_RED, BlockType.FLOWER_YELLOW,
                    BlockType.BERRY_BUSH, BlockType.TORCH, BlockType.DEAD_BUSH,
                    BlockType.MUSHROOM_RED, BlockType.MUSHROOM_BROWN, BlockType.VINE,
                    BlockType.BAMBOO, BlockType.LILY_PAD, BlockType.SEAWEED, BlockType.DOOR, BlockType.TRAPDOOR,
                    BlockType.BED, BlockType.BED_HEAD,
                    // Phase 0: crops (all stages) and sugar cane as decoration
                    BlockType.WHEAT_STAGE_1, BlockType.WHEAT_STAGE_2, BlockType.WHEAT_STAGE_3, BlockType.WHEAT_STAGE_4,
                    BlockType.POTATO_CROP_1, BlockType.POTATO_CROP_2, BlockType.POTATO_CROP_3,
                    BlockType.CARROT_CROP_1, BlockType.CARROT_CROP_2, BlockType.CARROT_CROP_3,
                    BlockType.SUGAR_CANE,
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
                    // GTNH Small Ore Variants - mining indicators (striped texture)
                    BlockType.SMALL_COPPER_ORE, BlockType.SMALL_TIN_ORE, BlockType.SMALL_BAUXITE_ORE, BlockType.SMALL_ZINC_ORE,
                    BlockType.SMALL_LEAD_ORE, BlockType.SMALL_SILVER_ORE,
                    BlockType.SMALL_NICKEL_ORE, BlockType.SMALL_COBALT_ORE, BlockType.SMALL_TUNGSTEN_ORE, BlockType.SMALL_MOLYBDENUM_ORE,
                    BlockType.SMALL_PLATINUM_ORE,
                    BlockType.SMALL_CHROMIUM_ORE, BlockType.SMALL_MANGANESE_ORE, BlockType.SMALL_VANADIUM_ORE, BlockType.SMALL_BERYLLIUM_ORE,
                    BlockType.SMALL_TITANIUM_ORE,
                    BlockType.SMALL_URANIUM_ORE, BlockType.SMALL_THORIUM_ORE, BlockType.SMALL_PLUTONIUM_ORE, BlockType.SMALL_IRIDIUM_ORE,
                    // GTNH Impure Ore Piles - secondary small ore drops (70%/30% split, 18 ores)
                    BlockType.IMPURE_COPPER, BlockType.IMPURE_TIN, BlockType.IMPURE_BAUXITE, BlockType.IMPURE_ZINC,
                    BlockType.IMPURE_LEAD, BlockType.IMPURE_SILVER,
                    BlockType.IMPURE_NICKEL, BlockType.IMPURE_COBALT, BlockType.IMPURE_TUNGSTEN, BlockType.IMPURE_MOLYBDENUM,
                    BlockType.IMPURE_PLATINUM,
                    BlockType.IMPURE_CHROMIUM, BlockType.IMPURE_MANGANESE, BlockType.IMPURE_BERYLLIUM,
                    BlockType.IMPURE_TITANIUM,
                    BlockType.IMPURE_URANIUM, BlockType.IMPURE_PLUTONIUM, BlockType.IMPURE_IRIDIUM,
                    // Extended GTNH ore pack — all crushed secondary forms
                    // Iron minerals
                    BlockType.MAGNETITE_ORE, BlockType.CRUSHED_MAGNETITE,
                    BlockType.HEMATITE_ORE, BlockType.CRUSHED_HEMATITE,
                    BlockType.BROWN_LIMONITE_ORE, BlockType.CRUSHED_BROWN_LIMONITE,
                    BlockType.YELLOW_LIMONITE_ORE, BlockType.CRUSHED_YELLOW_LIMONITE,
                    BlockType.BANDED_IRON_ORE, BlockType.CRUSHED_BANDED_IRON,
                    BlockType.VANADIUM_MAGNETITE_ORE, BlockType.CRUSHED_VANADIUM_MAGNETITE,
                    BlockType.PYRITE_ORE, BlockType.CRUSHED_PYRITE,
                    BlockType.ARSENOPYRITE_ORE, BlockType.CRUSHED_ARSENOPYRITE,
                    // Copper minerals
                    BlockType.CHALCOPYRITE_ORE, BlockType.CRUSHED_CHALCOPYRITE,
                    BlockType.TETRAHEDRITE_ORE, BlockType.CRUSHED_TETRAHEDRITE,
                    BlockType.MALACHITE_ORE, BlockType.CRUSHED_MALACHITE,
                    // Lead / Zinc
                    BlockType.GALENA_ORE, BlockType.CRUSHED_GALENA,
                    BlockType.SPHALERITE_ORE, BlockType.CRUSHED_SPHALERITE,
                    // Nickel / Cobalt
                    BlockType.GARNIERITE_ORE, BlockType.CRUSHED_GARNIERITE,
                    BlockType.PENTLANDITE_ORE, BlockType.CRUSHED_PENTLANDITE,
                    BlockType.COBALTITE_ORE, BlockType.CRUSHED_COBALTITE,
                    // Tin / Tungsten
                    BlockType.CASSITERITE_ORE, BlockType.CRUSHED_CASSITERITE,
                    BlockType.SCHEELITE_ORE, BlockType.CRUSHED_SCHEELITE,
                    BlockType.WOLFRAMITE_ORE, BlockType.CRUSHED_WOLFRAMITE,
                    BlockType.MOLYBDENITE_ORE, BlockType.CRUSHED_MOLYBDENITE,
                    BlockType.FERBERITE_ORE, BlockType.CRUSHED_FERBERITE,
                    // Chromium / Titanium
                    BlockType.CHROMITE_ORE, BlockType.CRUSHED_CHROMITE,
                    BlockType.ILMENITE_ORE, BlockType.CRUSHED_ILMENITE,
                    BlockType.RUTILE_ORE, BlockType.CRUSHED_RUTILE,
                    // Uranium / Thorium
                    BlockType.URANINITE_ORE, BlockType.CRUSHED_URANINITE,
                    BlockType.PITCHBLENDE_ORE, BlockType.CRUSHED_PITCHBLENDE,
                    // Vanadium / Manganese
                    BlockType.VANADINITE_ORE, BlockType.CRUSHED_VANADINITE,
                    BlockType.PYROLUSITE_ORE, BlockType.CRUSHED_PYROLUSITE,
                    // Rare earth / PGM
                    BlockType.MONAZITE_ORE, BlockType.CRUSHED_MONAZITE,
                    BlockType.BASTNASITE_ORE, BlockType.CRUSHED_BASTNASITE,
                    BlockType.NEODYMIUM_ORE, BlockType.CRUSHED_NEODYMIUM,
                    BlockType.CERIUM_ORE, BlockType.CRUSHED_CERIUM,
                    BlockType.OSMIUM_ORE, BlockType.CRUSHED_OSMIUM,
                    BlockType.PALLADIUM_ORE, BlockType.CRUSHED_PALLADIUM,
                    BlockType.NAQUADAH_ORE, BlockType.CRUSHED_NAQUADAH,
                    BlockType.NAQUADAH_ENRICHED_ORE, BlockType.CRUSHED_NAQUADAH_ENRICHED,
                    BlockType.TRINIUM_ORE, BlockType.CRUSHED_TRINIUM,
                    // Light metals / lithium
                    BlockType.LEPIDOLITE_ORE, BlockType.CRUSHED_LEPIDOLITE,
                    BlockType.LITHIUM_ORE, BlockType.CRUSHED_LITHIUM,
                    BlockType.CINNABAR_ORE, BlockType.CRUSHED_CINNABAR,
                    // Gemstones
                    BlockType.RUBY_ORE, BlockType.CRUSHED_RUBY, BlockType.SMALL_RUBY_ORE, BlockType.RUBY,
                    BlockType.SAPPHIRE_ORE, BlockType.CRUSHED_SAPPHIRE, BlockType.SAPPHIRE,
                    BlockType.GREEN_SAPPHIRE_ORE, BlockType.CRUSHED_GREEN_SAPPHIRE, BlockType.GREEN_SAPPHIRE,
                    BlockType.PYROPE_ORE, BlockType.CRUSHED_PYROPE,
                    BlockType.SPESSARTINE_ORE, BlockType.CRUSHED_SPESSARTINE,
                    // Industrial minerals
                    BlockType.CALCITE_ORE, BlockType.CRUSHED_CALCITE,
                    BlockType.OLIVINE_ORE, BlockType.CRUSHED_OLIVINE,
                    BlockType.TALC_ORE, BlockType.CRUSHED_TALC,
                    BlockType.BENTONITE_ORE, BlockType.CRUSHED_BENTONITE,
                    BlockType.SODALITE_ORE, BlockType.CRUSHED_SODALITE,
                    BlockType.LAZURITE_ORE, BlockType.CRUSHED_LAZURITE,
                    BlockType.SALT_ORE, BlockType.CRUSHED_SALT,
                    BlockType.ROCK_SALT_ORE, BlockType.CRUSHED_ROCK_SALT,
                    BlockType.SALTPETER_ORE, BlockType.CRUSHED_SALTPETER,
                    BlockType.BORAX_ORE, BlockType.CRUSHED_BORAX,
                    BlockType.APATITE_ORE, BlockType.CRUSHED_APATITE,
                    BlockType.PHOSPHATE_ORE, BlockType.CRUSHED_PHOSPHATE,
                    BlockType.SULFUR_ORE, BlockType.CRUSHED_SULFUR,
                    BlockType.GRAPHITE_ORE, BlockType.CRUSHED_GRAPHITE,
                    BlockType.PYROCHLORE_ORE, BlockType.CRUSHED_PYROCHLORE,
            }),
            new Tab("Tools", new BlockType[]{
                    BlockType.WOOD_PICKAXE, BlockType.STONE_PICKAXE, BlockType.IRON_PICKAXE, BlockType.DIAMOND_PICKAXE,
                    BlockType.WOOD_AXE, BlockType.STONE_AXE, BlockType.IRON_AXE, BlockType.DIAMOND_AXE,
                    BlockType.WOOD_SHOVEL, BlockType.STONE_SHOVEL, BlockType.IRON_SHOVEL, BlockType.DIAMOND_SHOVEL,
                    BlockType.WOOD_HAMMER, BlockType.STONE_HAMMER, BlockType.IRON_HAMMER, BlockType.DIAMOND_HAMMER,
                    BlockType.WOOD_BROADAXE, BlockType.STONE_BROADAXE, BlockType.IRON_BROADAXE, BlockType.DIAMOND_BROADAXE,
                    // Phase 0: hoes
                    BlockType.WOOD_HOE, BlockType.STONE_HOE, BlockType.IRON_HOE, BlockType.DIAMOND_HOE,
                    // Phase 0.5: Tinkers' Construct tool parts + assembled tools
                    BlockType.WOOD_TOOL_ROD,
                    BlockType.IRON_PICK_HEAD, BlockType.IRON_AXE_HEAD,
                    BlockType.IRON_SWORD_BLADE, BlockType.IRON_SHOVEL_HEAD,
                    BlockType.TINKERS_PICKAXE, BlockType.TINKERS_AXE,
                    BlockType.TINKERS_SWORD, BlockType.TINKERS_SHOVEL,
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
                    // Phase 0: farming food items + canteen
                    BlockType.SEEDS, BlockType.WHEAT, BlockType.BREAD,
                    BlockType.POTATO, BlockType.POTATO_COOKED, BlockType.CARROT,
                    BlockType.CLAY_BALL, BlockType.CLAY_CANTEEN, BlockType.CLAY_CANTEEN_FULL,
            }),
    };

    private CreativeCatalog() {
    }
}
