package com.minecraftclone.world;

import com.minecraftclone.player.Mortar;

/**
 * A placed Chemical Reactor: combines an item with fluid from a Steel Tank.
 * It is the first <b>MV</b> machine — it runs safely on gold-cable MV power
 * with no transformer (and on LV too).
 *
 * <h3>Recipes</h3>
 * <ul>
 *   <li><b>Sulfuric acid</b>: crushed sulfur + {@value #WATER_PER_ACID} mB
 *       water → {@value #ACID_PER_BATCH} mB sulfuric acid, pumped into a tank
 *       that is empty or already holds acid.</li>
 *   <li><b>Acid leaching</b>: crushed ore + {@value #ACID_PER_LEACH} mB
 *       sulfuric acid → purified ore plus its byproduct dust <em>every</em>
 *       time (the water washer only manages one in four).</li>
 * </ul>
 * Tanks are found adjacent to the reactor or on its fluid-pipe network
 * ({@link FluidNetwork}). The acid destination must be a different tank from
 * the water source, since a tank holds one fluid.
 */
public final class ChemicalReactorEntity extends ElectricMachineEntity {

    public static final String TYPE = "chemical_reactor";

    public static final float EU_PER_OPERATION = 400f;
    public static final float REACT_SECONDS = Furnace.SMELT_TIME;

    public static final int WATER_PER_ACID = 1000;
    public static final int ACID_PER_BATCH = 1000;
    public static final int ACID_PER_LEACH = 250;

    /** Test hooks: stand-in tanks instead of looking around the world. */
    FluidTankEntity sourceOverride, destinationOverride;

    /** True for the sulfur → acid recipe. */
    public static boolean makesAcid(BlockType input) {
        return input == BlockType.CRUSHED_SULFUR;
    }

    /** The purified ore acid leaching produces from {@code input}, or null. */
    public static BlockType leachOutput(BlockType input) {
        return input != null && input.name().startsWith("CRUSHED_") && !makesAcid(input)
                ? ElectricOreWasherEntity.outputFor(input) : null;
    }

    @Override public String type()         { return TYPE; }
    @Override public BlockType blockType() { return BlockType.CHEMICAL_REACTOR; }
    @Override protected boolean mvRated()  { return true; }

    @Override
    protected boolean hasRecipe(BlockType input) {
        return makesAcid(input) || leachOutput(input) != null;
    }

    @Override protected BlockType primaryOutput(BlockType input) { return leachOutput(input); }
    @Override protected int primaryYield(BlockType input)        { return 1; }
    @Override protected BlockType byproductOf(BlockType input) {
        BlockType purified = leachOutput(input);
        return purified == null ? null : SteamOreWasherEntity.byproductOf(Mortar.dustFor(input));
    }
    @Override protected float byproductChance()                  { return 1f; }
    @Override protected float euPerOperation()                   { return EU_PER_OPERATION; }
    @Override protected float operationSeconds()                 { return REACT_SECONDS; }

    @Override
    protected boolean environmentReady() {
        BlockType input = typeOf(SLOT_INPUT);
        if (makesAcid(input)) {
            FluidTankEntity water = source(Fluid.WATER, WATER_PER_ACID);
            FluidTankEntity out = destination(Fluid.SULFURIC_ACID, ACID_PER_BATCH);
            return water != null && out != null && water != out;
        }
        if (leachOutput(input) != null) return source(Fluid.SULFURIC_ACID, ACID_PER_LEACH) != null;
        return false;
    }

    @Override
    protected void onOperationComplete(BlockType input) {
        if (makesAcid(input)) {
            FluidTankEntity water = source(Fluid.WATER, WATER_PER_ACID);
            FluidTankEntity out = destination(Fluid.SULFURIC_ACID, ACID_PER_BATCH);
            if (water != null && out != null && water != out) {
                water.drain(Fluid.WATER, WATER_PER_ACID);
                out.fill(Fluid.SULFURIC_ACID, ACID_PER_BATCH);
            }
        } else {
            FluidTankEntity acid = source(Fluid.SULFURIC_ACID, ACID_PER_LEACH);
            if (acid != null) acid.drain(Fluid.SULFURIC_ACID, ACID_PER_LEACH);
        }
    }

    private FluidTankEntity source(Fluid f, int mB) {
        if (sourceOverride != null) return sourceOverride.has(f, mB) ? sourceOverride : null;
        return attached ? FluidNetwork.findTankWith(world, posX, posY, posZ, f, mB) : null;
    }

    private FluidTankEntity destination(Fluid f, int mB) {
        if (destinationOverride != null) return destinationOverride.canFill(f, mB) ? destinationOverride : null;
        return attached ? FluidNetwork.findTankFor(world, posX, posY, posZ, f, mB) : null;
    }
}
