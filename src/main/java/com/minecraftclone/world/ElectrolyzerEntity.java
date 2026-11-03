package com.minecraftclone.world;

import java.util.EnumMap;
import java.util.Map;

/**
 * A placed LV Electrolyzer: splits compound minerals into their elements.
 * Each operation turns one crushed compound ore into <b>2 dust</b> of its
 * main metal plus <b>1 dust</b> of the secondary element (iron from
 * chalcopyrite, silver from galena, sulfur from pyrite…), every time.
 * It is also the only way to get {@link BlockType#ALUMINUM_DUST} — bauxite
 * electrolyses into aluminium.
 *
 * <p>The secondary product lands in the byproduct slot. That slot holds a
 * single item type, so switching to an input with a different secondary
 * element while it's full loses the secondary until it's emptied.
 */
public final class ElectrolyzerEntity extends ElectricMachineEntity {

    public static final String TYPE = "electrolyzer";

    /** Total EU to split one item — electrolysis is power-hungry. */
    public static final float EU_PER_OPERATION = 500f;

    /** Seconds per item at full power. */
    public static final float SPLIT_SECONDS = Furnace.SMELT_TIME;

    /** Primary dust per input. */
    public static final int PRIMARY_YIELD = 2;

    /** One recipe: primary dust and an optional secondary element. */
    public record Split(BlockType primary, BlockType secondary) { }

    private static final Map<BlockType, Split> RECIPES = new EnumMap<>(BlockType.class);

    static {
        split(BlockType.CRUSHED_CHALCOPYRITE, BlockType.COPPER_DUST, BlockType.IRON_DUST);       // CuFeS2
        split(BlockType.CRUSHED_TETRAHEDRITE, BlockType.COPPER_DUST, BlockType.SILVER_DUST);     // argentian fahlore
        split(BlockType.CRUSHED_MALACHITE, BlockType.COPPER_DUST, null);                         // Cu2CO3(OH)2
        split(BlockType.CRUSHED_GALENA, BlockType.LEAD_DUST, BlockType.SILVER_DUST);             // argentiferous PbS
        split(BlockType.CRUSHED_SPHALERITE, BlockType.ZINC_DUST, BlockType.CRUSHED_SULFUR);      // ZnS
        split(BlockType.CRUSHED_PYRITE, BlockType.IRON_DUST, BlockType.CRUSHED_SULFUR);          // FeS2
        split(BlockType.CRUSHED_ARSENOPYRITE, BlockType.IRON_DUST, BlockType.CRUSHED_SULFUR);    // FeAsS
        split(BlockType.CRUSHED_PENTLANDITE, BlockType.NICKEL_DUST, BlockType.IRON_DUST);        // (Fe,Ni)9S8
        split(BlockType.CRUSHED_GARNIERITE, BlockType.NICKEL_DUST, null);
        split(BlockType.CRUSHED_COBALTITE, BlockType.COBALT_DUST, BlockType.CRUSHED_SULFUR);     // CoAsS
        split(BlockType.CRUSHED_CHROMITE, BlockType.CHROMIUM_DUST, BlockType.IRON_DUST);         // FeCr2O4
        split(BlockType.CRUSHED_ILMENITE, BlockType.TITANIUM_DUST, BlockType.IRON_DUST);         // FeTiO3
        split(BlockType.CRUSHED_RUTILE, BlockType.TITANIUM_DUST, null);                          // TiO2
        split(BlockType.CRUSHED_VANADIUM_MAGNETITE, BlockType.VANADIUM_DUST, BlockType.IRON_DUST);
        split(BlockType.CRUSHED_MOLYBDENITE, BlockType.MOLYBDENUM_DUST, BlockType.CRUSHED_SULFUR); // MoS2
        split(BlockType.CRUSHED_SCHEELITE, BlockType.TUNGSTEN_DUST, null);                       // CaWO4
        split(BlockType.CRUSHED_WOLFRAMITE, BlockType.TUNGSTEN_DUST, BlockType.MANGANESE_DUST);  // (Fe,Mn)WO4
        split(BlockType.CRUSHED_FERBERITE, BlockType.TUNGSTEN_DUST, BlockType.IRON_DUST);        // FeWO4
        split(BlockType.CRUSHED_CASSITERITE, BlockType.TIN_DUST, null);                          // SnO2
        split(BlockType.CRUSHED_PYROLUSITE, BlockType.MANGANESE_DUST, null);                     // MnO2
        split(BlockType.CRUSHED_URANINITE, BlockType.URANIUM_DUST, null);                        // UO2
        split(BlockType.CRUSHED_PITCHBLENDE, BlockType.URANIUM_DUST, BlockType.LEAD_DUST);       // decay lead
        split(BlockType.CRUSHED_MAGNETITE, BlockType.IRON_DUST, null);
        split(BlockType.CRUSHED_HEMATITE, BlockType.IRON_DUST, null);
        split(BlockType.CRUSHED_BAUXITE, BlockType.ALUMINUM_DUST, BlockType.TITANIUM_DUST);      // TiO2 impurity
        split(BlockType.BAUXITE_DUST, BlockType.ALUMINUM_DUST, null);
        split(BlockType.SAND, BlockType.SILICON_DUST, null);                                    // SiO2
        split(BlockType.CRUSHED_LITHIUM, BlockType.LITHIUM_DUST, null);
        split(BlockType.CRUSHED_LEPIDOLITE, BlockType.LITHIUM_DUST, BlockType.ALUMINUM_DUST);   // Li-Al mica
    }

    private static void split(BlockType input, BlockType primary, BlockType secondary) {
        RECIPES.put(input, new Split(primary, secondary));
    }

    /** The split for {@code input}, or null if it isn't an electrolysable compound. */
    public static Split recipeFor(BlockType input) {
        return input == null ? null : RECIPES.get(input);
    }

    @Override public String type()         { return TYPE; }
    @Override public BlockType blockType() { return BlockType.ELECTROLYZER; }

    @Override protected BlockType primaryOutput(BlockType input) {
        Split s = recipeFor(input);
        return s == null ? null : s.primary();
    }
    @Override protected int primaryYield(BlockType input)      { return PRIMARY_YIELD; }
    @Override protected BlockType byproductOf(BlockType input) {
        Split s = recipeFor(input);
        return s == null ? null : s.secondary();
    }
    @Override protected float byproductChance()                { return 1f; }
    @Override protected float euPerOperation()                 { return EU_PER_OPERATION; }
    @Override protected float operationSeconds()               { return SPLIT_SECONDS; }
}
