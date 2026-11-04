package com.minecraftclone.world;

import com.minecraftclone.player.Smelting;

import java.util.EnumMap;
import java.util.Map;

/**
 * Electric Blast Furnace recipes: refractory metals that need more heat than
 * any furnace. Each recipe has a required temperature; the EBF's coils set
 * how hot it gets (see {@link CoilTier}).
 *
 * <ul>
 *   <li>Titanium, chromium — {@value #TITANIUM_K} K (cupronickel coils)</li>
 *   <li>Iridium — {@value #IRIDIUM_K} K (kanthal coils)</li>
 *   <li>Tungsten, tungstensteel — {@value #TUNGSTEN_K} K (nichrome coils)</li>
 * </ul>
 * Inputs are the metal's dust plus every crushed/purified ore whose furnace
 * recipe was moved here by {@link Smelting} (rutile, ilmenite, chromite,
 * scheelite, wolframite, …).
 */
public final class BlastRecipes {

    private BlastRecipes() {
    }

    public static final int TITANIUM_K = 1700;
    public static final int IRIDIUM_K = 2700;
    public static final int TUNGSTEN_K = 3000;

    /** Required temperature per output ingot. */
    private static final Map<BlockType, Integer> TEMPERATURE = new EnumMap<>(BlockType.class);
    /** Dusts that blast straight into their ingot. */
    private static final Map<BlockType, BlockType> DUSTS = new EnumMap<>(BlockType.class);

    static {
        TEMPERATURE.put(BlockType.TITANIUM_INGOT, TITANIUM_K);
        TEMPERATURE.put(BlockType.CHROMIUM_INGOT, TITANIUM_K);
        TEMPERATURE.put(BlockType.IRIDIUM_INGOT, IRIDIUM_K);
        TEMPERATURE.put(BlockType.TUNGSTEN_INGOT, TUNGSTEN_K);
        TEMPERATURE.put(BlockType.TUNGSTENSTEEL_INGOT, TUNGSTEN_K);
        DUSTS.put(BlockType.TITANIUM_DUST, BlockType.TITANIUM_INGOT);
        DUSTS.put(BlockType.CHROMIUM_DUST, BlockType.CHROMIUM_INGOT);
        DUSTS.put(BlockType.IRIDIUM_DUST, BlockType.IRIDIUM_INGOT);
        DUSTS.put(BlockType.TUNGSTEN_DUST, BlockType.TUNGSTEN_INGOT);
        DUSTS.put(BlockType.TUNGSTENSTEEL_DUST, BlockType.TUNGSTENSTEEL_INGOT);
    }

    /** The ingot {@code input} blasts into, or null if it isn't an EBF recipe. */
    public static BlockType outputFor(BlockType input) {
        if (input == null) return null;
        BlockType out = DUSTS.get(input);
        if (out != null) return out;
        out = Smelting.blastGatedOutputFor(input);
        if (out != null) return out;
        BlockType crushed = PurifiedOre.crushedOf(input);
        return crushed == null ? null : Smelting.blastGatedOutputFor(crushed);
    }

    /** Kelvin needed to blast {@code input}, or 0 if it isn't a recipe. */
    public static int temperatureFor(BlockType input) {
        BlockType out = outputFor(input);
        return out == null ? 0 : TEMPERATURE.getOrDefault(out, TITANIUM_K);
    }
}
