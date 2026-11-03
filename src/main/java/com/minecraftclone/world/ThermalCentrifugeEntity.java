package com.minecraftclone.world;

import com.minecraftclone.player.Mortar;

/**
 * A placed LV Thermal Centrifuge: the last step of the electric ore chain.
 * Spins purified ore into <b>3× dust</b>, plus a related-metal byproduct
 * dust on every other operation. Slower and hungrier than the macerator —
 * the reward for building the full macerator-free chain
 * (crushed → Ore Washer → purified → Thermal Centrifuge → 3 dust).
 */
public final class ThermalCentrifugeEntity extends ElectricMachineEntity {

    public static final String TYPE = "thermal_centrifuge";

    /** Total EU to spin one item. */
    public static final float EU_PER_OPERATION = 600f;

    /** Time per item at full power — a full furnace cycle. */
    public static final float SPIN_SECONDS = Furnace.SMELT_TIME;

    /** Dust per purified ore. */
    public static final int YIELD = 3;

    /** Fraction of operations that also yield a byproduct dust. */
    public static final float BYPRODUCT_CHANCE = 0.5f;

    /** The dust a purified ore spins into, or null for anything else. */
    public static BlockType outputFor(BlockType input) {
        return PurifiedOre.isPurified(input) ? Mortar.dustFor(input) : null;
    }

    @Override public String type()         { return TYPE; }
    @Override public BlockType blockType() { return BlockType.THERMAL_CENTRIFUGE; }

    @Override protected BlockType primaryOutput(BlockType input) { return outputFor(input); }
    @Override protected int primaryYield(BlockType input)        { return YIELD; }
    @Override protected BlockType byproductOf(BlockType input) {
        BlockType dust = outputFor(input);
        return dust == null ? null : SteamOreWasherEntity.byproductOf(dust);
    }
    @Override protected float byproductChance()                  { return BYPRODUCT_CHANCE; }
    @Override protected float euPerOperation()                   { return EU_PER_OPERATION; }
    @Override protected float operationSeconds()                 { return SPIN_SECONDS; }
}
