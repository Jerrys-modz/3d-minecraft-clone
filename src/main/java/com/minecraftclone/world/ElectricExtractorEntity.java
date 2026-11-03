package com.minecraftclone.world;

/**
 * A placed LV Extractor: squeezes latex out of jungle wood and turns it into
 * {@link BlockType#RUBBER} on EU — the renewable alternative to vulcanising
 * crushed sulfur, and the rubber source for hazmat suits and future cable
 * insulation. Jungle logs and saplings each yield one rubber.
 */
public final class ElectricExtractorEntity extends ElectricMachineEntity {

    public static final String TYPE = "electric_extractor";

    /** Total EU to extract one item. */
    public static final float EU_PER_OPERATION = 200f;

    /** Seconds per extraction at full power. */
    public static final float EXTRACT_SECONDS = Furnace.SMELT_TIME * 0.5f;

    /** What {@code input} extracts into, or null if it holds nothing useful. */
    public static BlockType outputFor(BlockType input) {
        if (input == BlockType.JUNGLE_LOG || input == BlockType.JUNGLE_SAPLING) return BlockType.RUBBER;
        return null;
    }

    @Override public String type()         { return TYPE; }
    @Override public BlockType blockType() { return BlockType.ELECTRIC_EXTRACTOR; }

    @Override protected BlockType primaryOutput(BlockType input) { return outputFor(input); }
    @Override protected int primaryYield(BlockType input)        { return 1; }
    @Override protected float euPerOperation()                   { return EU_PER_OPERATION; }
    @Override protected float operationSeconds()                 { return EXTRACT_SECONDS; }
}
