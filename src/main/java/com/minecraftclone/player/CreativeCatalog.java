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
                    BlockType.CACTUS, BlockType.WATER_SOURCE, BlockType.LAVA_SOURCE,
                    BlockType.SWAMP_GRASS, BlockType.RED_CLAY, BlockType.MYCELIUM, BlockType.ICE, BlockType.PACKED_ICE,
                    BlockType.PUMPKIN,
            }),
            new Tab("Decoration", new BlockType[]{
                    BlockType.TALL_GRASS, BlockType.FLOWER_RED, BlockType.FLOWER_YELLOW,
                    BlockType.BERRY_BUSH, BlockType.TORCH, BlockType.DEAD_BUSH,
                    BlockType.MUSHROOM_RED, BlockType.MUSHROOM_BROWN, BlockType.VINE,
                    BlockType.BAMBOO, BlockType.LILY_PAD, BlockType.SEAWEED, BlockType.DOOR, BlockType.TRAPDOOR,
            }),
            new Tab("Materials", new BlockType[]{
                    BlockType.COAL_ORE, BlockType.IRON_ORE, BlockType.GOLD_ORE, BlockType.DIAMOND_ORE,
                    BlockType.COAL, BlockType.STICK, BlockType.IRON_INGOT, BlockType.GOLD_INGOT, BlockType.DIAMOND,
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
            }),
            new Tab("Food", new BlockType[]{
                    BlockType.APPLE, BlockType.BERRIES,
                    BlockType.RAW_PORKCHOP, BlockType.RAW_BEEF, BlockType.MUTTON,
            }),
    };

    private CreativeCatalog() {
    }
}
