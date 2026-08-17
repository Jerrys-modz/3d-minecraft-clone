package com.minecraftclone.engine.audio;

import com.minecraftclone.world.BlockType;

/**
 * Groups {@link BlockType}s into a handful of timbre categories so break/
 * place/footstep sounds (see {@link Sounds}) vary by what you're actually
 * standing on or digging into, the way Minecraft's block sound groups do -
 * without needing a bespoke synthesized sound per block type (50+ of them).
 */
public enum SoundMaterial {
    STONE, WOOD, DIRT, GRAVEL, SAND, GLASS, LEAVES, DEFAULT;

    /** The material a block's break/place/footstep sound should use. */
    public static SoundMaterial of(BlockType type) {
        if (type == null) return DEFAULT;
        return switch (type) {
            case STONE, BEDROCK, COAL_ORE, IRON_ORE, GOLD_ORE, DIAMOND_ORE,
                    FURNACE, STONE_SLAB, STONE_STAIRS, RED_CLAY, ICE, PACKED_ICE -> STONE;
            case WOOD_LOG, PLANKS, PLANKS_SLAB, PLANKS_STAIRS, WOODEN_FENCE, CRAFTING_TABLE, DOOR, DOOR_OPEN,
                    TRAPDOOR, TRAPDOOR_OPEN -> WOOD;
            case DIRT, GRASS, SWAMP_GRASS, MYCELIUM, SNOW -> DIRT;
            case GRAVEL -> GRAVEL;
            case SAND -> SAND;
            case GLASS, LAMP -> GLASS;
            case LEAVES, CHERRY_LEAVES, TALL_GRASS, FLOWER_RED, FLOWER_YELLOW,
                    BERRY_BUSH, DEAD_BUSH, MUSHROOM_RED, MUSHROOM_BROWN, VINE,
                    BAMBOO, LILY_PAD, SEAWEED, CACTUS -> LEAVES;
            default -> DEFAULT;
        };
    }
}
