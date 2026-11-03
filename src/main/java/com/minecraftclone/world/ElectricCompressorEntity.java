package com.minecraftclone.world;

import com.minecraftclone.player.Metalworking;

/**
 * A placed LV Compressor: presses ingots into plates on EU — the electric
 * replacement for hammering them one at a time (no hammer wear, no clicks).
 * Accepts any ingot that has a plate form (copper, tin, iron, gold, bronze,
 * brass, steel, wrought iron, the Phase 3 alloys), one plate per ingot, and
 * slices a silicon boule into {@value #WAFERS_PER_BOULE} wafers.
 */
public final class ElectricCompressorEntity extends ElectricMachineEntity {

    public static final String TYPE = "electric_compressor";

    /** Total EU to press one plate. */
    public static final float EU_PER_OPERATION = 250f;

    /** Seconds per plate at full power. */
    public static final float PRESS_SECONDS = Furnace.SMELT_TIME * 0.5f;

    /** Wafers sliced from one silicon boule. */
    public static final int WAFERS_PER_BOULE = 4;

    /** The plate {@code input} presses into (or wafers from a silicon boule), or null. */
    public static BlockType outputFor(BlockType input) {
        if (input == BlockType.SILICON_BOULE) return BlockType.SILICON_WAFER;
        return input == null ? null : Metalworking.plateFor(input);
    }

    @Override public String type()         { return TYPE; }
    @Override public BlockType blockType() { return BlockType.ELECTRIC_COMPRESSOR; }

    @Override protected BlockType primaryOutput(BlockType input) { return outputFor(input); }
    @Override protected int primaryYield(BlockType input) {
        return input == BlockType.SILICON_BOULE ? WAFERS_PER_BOULE : 1;
    }
    @Override protected float euPerOperation()                   { return EU_PER_OPERATION; }
    @Override protected float operationSeconds()                 { return PRESS_SECONDS; }
}
