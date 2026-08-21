package com.minecraftclone.engine.audio;

import com.minecraftclone.world.BlockType;

/**
 * Groups {@link BlockType}s into a handful of timbre categories so break/
 * place/footstep/hit sounds (see {@link Sounds}) vary by what you're actually
 * standing on or digging into, the way Minecraft's block sound groups do -
 * without needing a bespoke synthesized sound per block type (50+ of them).
 */
public enum SoundMaterial {
    STONE, WOOD, DIRT, GRAVEL, SAND, GLASS, LEAVES, DEFAULT;

    /** The material a block's break/place/footstep/hit sound should use. */
    public static SoundMaterial of(BlockType type) {
        if (type == null) return DEFAULT;
        return switch (type) {
            case STONE, BEDROCK, COAL_ORE, IRON_ORE, GOLD_ORE, DIAMOND_ORE,
                    FURNACE, STONE_SLAB, STONE_STAIRS, RED_CLAY, ICE, PACKED_ICE,
                    NETHERRACK, END_STONE, OBSIDIAN, GLOWSTONE,
                    SEARED_BRICK, SEARED_TANK, SMELTERY_DRAIN, SMELTERY_CONTROLLER,
                    CASTING_TABLE, CASTING_BASIN -> STONE;
            case WOOD_LOG, PLANKS, PLANKS_SLAB, PLANKS_STAIRS, WOODEN_FENCE, CRAFTING_TABLE, DOOR, DOOR_OPEN,
                    TRAPDOOR, TRAPDOOR_OPEN, CHEST, BARREL, PART_BUILDER, TOOL_STATION,
                    ADVANCED_CRAFTING_TABLE -> WOOD;
            case DIRT, GRASS, SWAMP_GRASS, MYCELIUM, SNOW, FARMLAND, FARMLAND_WET, CLAY, SOUL_SAND -> DIRT;
            case GRAVEL -> GRAVEL;
            case SAND -> SAND;
            case GLASS, LAMP, SEARED_GLASS -> GLASS;
            case LEAVES, CHERRY_LEAVES, TALL_GRASS, FLOWER_RED, FLOWER_YELLOW,
                    BERRY_BUSH, DEAD_BUSH, MUSHROOM_RED, MUSHROOM_BROWN, VINE,
                    BAMBOO, LILY_PAD, SEAWEED, CACTUS -> LEAVES;
            default -> fromName(type);
        };
    }

    /**
     * Catch-all for the large GTNH ore set (and anything added later) so an
     * unlisted copper ore still chips like stone instead of the generic thud.
     */
    private static SoundMaterial fromName(BlockType type) {
        String n = type.name();
        if (n.contains("ORE") || n.contains("STONE") || n.contains("BRICK")
                || n.contains("NETHERRACK") || n.contains("OBSIDIAN")
                || n.contains("SEARED") || n.contains("SMELTERY") || n.contains("COBBLE")) {
            return STONE;
        }
        if (n.contains("WOOD") || n.contains("PLANK") || n.contains("LOG")
                || n.contains("CHEST") || n.contains("BARREL") || n.contains("DOOR")
                || n.contains("FENCE") || n.contains("TABLE") || n.contains("CRAFT")) {
            return WOOD;
        }
        if (n.contains("SAND")) return SAND;
        if (n.contains("GRAVEL")) return GRAVEL;
        if (n.contains("GLASS") || n.contains("ICE") || n.contains("LAMP")) return GLASS;
        if (type.cross || n.contains("LEAVES") || n.contains("FLOWER") || n.contains("CROP")
                || n.contains("WHEAT") || n.contains("BUSH") || n.contains("VINE")
                || n.contains("MUSHROOM") || n.contains("CANE") || n.contains("GRASS")) {
            return LEAVES;
        }
        if (n.contains("DIRT") || n.contains("CLAY") || n.contains("SNOW")
                || n.contains("FARMLAND") || n.contains("SOUL")) {
            return DIRT;
        }
        return DEFAULT;
    }
}
