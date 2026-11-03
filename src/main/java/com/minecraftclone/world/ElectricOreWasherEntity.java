package com.minecraftclone.world;

import com.minecraftclone.player.Mortar;

/**
 * A placed LV Ore Washer: step two of the electric ore chain. Rinses crushed
 * ore or an impure pile into <b>purified ore</b> (1:1), with a 25% related-metal
 * byproduct dust (same pairings as the {@link SteamOreWasherEntity}). Like
 * the steam washer it needs water: an adjacent, unfrozen water block (free —
 * a washer beside a lake that ices over in winter stops until the thaw), or
 * {@value #WATER_PER_WASH} mB per wash from a Steel Tank that is adjacent or
 * connected by fluid pipe.
 *
 * <p>Purified ore then goes to the {@link ThermalCentrifugeEntity} for 3×
 * dust (or the macerator for 2×, or a furnace for an ingot).
 */
public final class ElectricOreWasherEntity extends ElectricMachineEntity {

    public static final String TYPE = "electric_ore_washer";

    /** Total EU to wash one item. */
    public static final float EU_PER_OPERATION = 200f;

    /** Time to wash one item at full power. */
    public static final float WASH_SECONDS = Furnace.SMELT_TIME * 0.5f;

    /** Fraction of washes that also yield a byproduct dust. */
    public static final float BYPRODUCT_CHANCE = 0.25f;

    /** Tank water used per wash when no water block is adjacent. */
    public static final int WATER_PER_WASH = 100;

    /** Test hook: forces the water check instead of reading the world. */
    Boolean waterOverride;

    /** The purified ore for a crushed ore / impure pile, or null if not washable. */
    public static BlockType outputFor(BlockType input) {
        if (input == null) return null;
        String n = input.name();
        String metal;
        if (n.startsWith("CRUSHED_")) metal = n.substring("CRUSHED_".length());
        else if (n.startsWith("IMPURE_")) metal = n.substring("IMPURE_".length());
        else return null;
        return PurifiedOre.forMetal(metal);
    }

    /** The byproduct dust a wash of {@code input} can produce, or null. */
    public static BlockType byproductFor(BlockType input) {
        return outputFor(input) == null ? null : SteamOreWasherEntity.byproductOf(Mortar.dustFor(input));
    }

    @Override public String type()         { return TYPE; }
    @Override public BlockType blockType() { return BlockType.ELECTRIC_ORE_WASHER; }

    @Override protected BlockType primaryOutput(BlockType input) { return outputFor(input); }
    @Override protected int primaryYield(BlockType input)        { return 1; }
    @Override protected BlockType byproductOf(BlockType input)   { return byproductFor(input); }
    @Override protected float byproductChance()                  { return BYPRODUCT_CHANCE; }
    @Override protected float euPerOperation()                   { return EU_PER_OPERATION; }
    @Override protected float operationSeconds()                 { return WASH_SECONDS; }

    @Override
    protected boolean environmentReady() {
        if (waterOverride != null) return waterOverride;
        return hasAdjacentWater() || waterTank() != null;
    }

    @Override
    protected void onOperationComplete(BlockType input) {
        if (waterOverride != null || hasAdjacentWater()) return;   // open water is free
        FluidTankEntity tank = waterTank();
        if (tank != null) tank.drain(Fluid.WATER, WATER_PER_WASH);
    }

    private FluidTankEntity waterTank() {
        return attached ? FluidNetwork.findTankWith(world, posX, posY, posZ, Fluid.WATER, WATER_PER_WASH) : null;
    }
}
