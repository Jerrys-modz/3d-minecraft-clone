package com.minecraftclone.world;

import com.minecraftclone.player.Mortar;

/**
 * A placed LV Macerator: the first electric ore-processing machine. Grinds
 * ore, crushed ore, purified ore and impure piles into dust like the mortar
 * does, but <b>doubles</b> the dust from anything ore-derived, runs on EU
 * instead of mortar wear, and works twice as fast as a furnace.
 *
 * <p>Slot 0 takes anything {@link Mortar#isGrindable grindable}; slot 2 holds
 * the dust. Slot 1 (byproduct) stays empty — the macerator has none. See
 * {@link ElectricMachineEntity} for power and slot handling.
 */
public final class ElectricMaceratorEntity extends ElectricMachineEntity {

    public static final String TYPE = "electric_macerator";

    /** Total EU to grind one item. */
    public static final float EU_PER_OPERATION = 300f;

    /** Time to grind one item at full power - twice as fast as a furnace. */
    public static final float GRIND_SECONDS = Furnace.SMELT_TIME * 0.5f;

    /** Dust multiplier for ore-derived inputs (ore, crushed, purified, impure). */
    public static final int ORE_YIELD = 2;

    /** The dust produced from {@code input}, or null if it can't be macerated. */
    public static BlockType outputFor(BlockType input) {
        return Mortar.isGrindable(input) ? Mortar.dustFor(input) : null;
    }

    /**
     * How many output items one {@code input} yields: {@value #ORE_YIELD} for
     * ores, crushed/purified ores and impure piles; 1 for everything else
     * (coal → coal dust), so the machine can't multiply non-ore materials.
     */
    public static int yieldFor(BlockType input) {
        if (input == null) return 0;
        String n = input.name();
        boolean oreDerived = n.endsWith("_ORE") || n.startsWith("CRUSHED_")
                || n.startsWith("PURIFIED_") || n.startsWith("IMPURE_");
        return oreDerived ? ORE_YIELD : 1;
    }

    /** Alias of {@link #canProcess()} kept for readability in macerator code/tests. */
    public boolean canGrind() { return canProcess(); }

    @Override public String type()         { return TYPE; }
    @Override public BlockType blockType() { return BlockType.ELECTRIC_MACERATOR; }

    @Override protected BlockType primaryOutput(BlockType input) { return outputFor(input); }
    @Override protected int primaryYield(BlockType input)        { return yieldFor(input); }
    @Override protected float euPerOperation()                   { return EU_PER_OPERATION; }
    @Override protected float operationSeconds()                 { return GRIND_SECONDS; }
}
