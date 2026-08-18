package com.minecraftclone.world.gen;

import com.minecraftclone.util.Noise;
import com.minecraftclone.world.BlockType;

/**
 * GTNH (Greg Tech New Horizons) ore generation system with two ore types:
 * 1. Small Ores - semi-common single blocks scattered throughout, mineable at -1 tier
 * 2. Veins - regular vein clusters every 3 chunks, containing large ore concentrations
 */
public class GthnOreGenerator {

    /** Vein spacing: ore veins generate every this many chunks. */
    private static final int VEIN_SPACING = 3;

    /** Ore vein definitions: depth range, base rarity threshold, and primary ore composition. */
    private static class OreVeinDef {
        final int minDepth, maxDepth;
        final double threshold;
        final BlockType primaryOre;
        final BlockType[] secondaryOres;

        OreVeinDef(int minDepth, int maxDepth, double threshold, BlockType primaryOre, BlockType... secondaryOres) {
            this.minDepth = minDepth;
            this.maxDepth = maxDepth;
            this.threshold = threshold;
            this.primaryOre = primaryOre;
            this.secondaryOres = secondaryOres;
        }
    }

    /** Vein definitions ordered by rarity (rarest first). */
    private static final OreVeinDef[] VEIN_DEFS = {
        // Late-game tier: 5-15 blocks deep
        new OreVeinDef(5, 15, 0.92, BlockType.IRIDIUM_ORE, BlockType.PLATINUM_ORE, BlockType.TUNGSTEN_ORE),
        new OreVeinDef(5, 15, 0.88, BlockType.PLUTONIUM_ORE, BlockType.THORIUM_ORE, BlockType.URANIUM_ORE),

        // Advanced tier: 8-25 blocks deep
        new OreVeinDef(8, 25, 0.82, BlockType.TITANIUM_ORE, BlockType.BERYLLIUM_ORE, BlockType.VANADIUM_ORE),
        new OreVeinDef(8, 25, 0.78, BlockType.CHROMIUM_ORE, BlockType.MANGANESE_ORE),

        // Mid-game tier: 12-40 blocks deep
        new OreVeinDef(12, 40, 0.68, BlockType.PLATINUM_ORE, BlockType.COBALT_ORE, BlockType.MOLYBDENUM_ORE),
        new OreVeinDef(12, 40, 0.62, BlockType.NICKEL_ORE, BlockType.TUNGSTEN_ORE),

        // Early-game tier: 20-60 blocks deep
        new OreVeinDef(20, 60, 0.52, BlockType.COPPER_ORE, BlockType.TIN_ORE, BlockType.SILVER_ORE),
        new OreVeinDef(20, 60, 0.48, BlockType.BAUXITE_ORE, BlockType.ZINC_ORE, BlockType.LEAD_ORE),

        // Iron formation veins (Y: 10-50)
        new OreVeinDef(10, 50, 0.55, BlockType.MAGNETITE_ORE, BlockType.HEMATITE_ORE, BlockType.BROWN_LIMONITE_ORE, BlockType.VANADIUM_MAGNETITE_ORE),
        new OreVeinDef(10, 50, 0.50, BlockType.YELLOW_LIMONITE_ORE, BlockType.BROWN_LIMONITE_ORE, BlockType.BANDED_IRON_ORE, BlockType.MALACHITE_ORE),

        // Copper/sulfide veins (Y: 10-40)
        new OreVeinDef(10, 40, 0.55, BlockType.CHALCOPYRITE_ORE, BlockType.PYRITE_ORE, BlockType.MALACHITE_ORE, BlockType.TETRAHEDRITE_ORE),
        new OreVeinDef(10, 40, 0.52, BlockType.TETRAHEDRITE_ORE, BlockType.CHALCOPYRITE_ORE, BlockType.COBALTITE_ORE),

        // Lead/silver (Y: 5-30)
        new OreVeinDef(5, 30, 0.60, BlockType.GALENA_ORE, BlockType.SULFUR_ORE, BlockType.ARSENOPYRITE_ORE),

        // Nickel/cobalt (Y: 10-40)
        new OreVeinDef(10, 40, 0.62, BlockType.GARNIERITE_ORE, BlockType.PENTLANDITE_ORE, BlockType.COBALTITE_ORE),

        // Tin (Y: 40-80)
        new OreVeinDef(40, 80, 0.55, BlockType.CASSITERITE_ORE, BlockType.CASSITERITE_ORE),

        // Tungsten (Y: 5-20)
        new OreVeinDef(5, 20, 0.75, BlockType.SCHEELITE_ORE, BlockType.WOLFRAMITE_ORE, BlockType.FERBERITE_ORE, BlockType.MOLYBDENITE_ORE),

        // Chromium (Y: 10-40)
        new OreVeinDef(10, 40, 0.70, BlockType.CHROMITE_ORE, BlockType.MALACHITE_ORE),

        // Titanium (Y: 10-40)
        new OreVeinDef(10, 40, 0.72, BlockType.ILMENITE_ORE, BlockType.RUTILE_ORE),

        // Uranium/nuclear (Y: 5-20)
        new OreVeinDef(5, 20, 0.85, BlockType.URANINITE_ORE, BlockType.URANINITE_ORE),
        new OreVeinDef(5, 20, 0.80, BlockType.PITCHBLENDE_ORE, BlockType.URANINITE_ORE),

        // Rare earth (Y: 5-30)
        new OreVeinDef(5, 30, 0.78, BlockType.MONAZITE_ORE, BlockType.BASTNASITE_ORE, BlockType.NEODYMIUM_ORE, BlockType.CERIUM_ORE),

        // Sulfur/pyrite (Y: 5-15)
        new OreVeinDef(5, 15, 0.55, BlockType.SULFUR_ORE, BlockType.PYRITE_ORE, BlockType.CINNABAR_ORE, BlockType.ARSENOPYRITE_ORE),

        // Platinum group (Y: 5-20)
        new OreVeinDef(5, 20, 0.90, BlockType.OSMIUM_ORE, BlockType.PALLADIUM_ORE),

        // Naquadah (Y: 5-20, rare)
        new OreVeinDef(5, 20, 0.95, BlockType.NAQUADAH_ORE, BlockType.NAQUADAH_ENRICHED_ORE, BlockType.TRINIUM_ORE),

        // Lithium (Y: 30-60)
        new OreVeinDef(30, 60, 0.68, BlockType.LITHIUM_ORE, BlockType.LEPIDOLITE_ORE),

        // Salt/mineral (Y: 50-80)
        new OreVeinDef(50, 80, 0.55, BlockType.SALT_ORE, BlockType.ROCK_SALT_ORE, BlockType.BORAX_ORE, BlockType.SALTPETER_ORE),

        // Calcite/minerals (Y: 10-40)
        new OreVeinDef(10, 40, 0.50, BlockType.CALCITE_ORE, BlockType.OLIVINE_ORE, BlockType.TALC_ORE, BlockType.BENTONITE_ORE),

        // Lapis (sodalite/lazurite) (Y: 10-40)
        new OreVeinDef(10, 40, 0.60, BlockType.SODALITE_ORE, BlockType.LAZURITE_ORE),

        // Apatite/phosphate (Y: 30-60)
        new OreVeinDef(30, 60, 0.60, BlockType.APATITE_ORE, BlockType.PHOSPHATE_ORE, BlockType.PYROCHLORE_ORE),

        // Manganese/vanadium
        new OreVeinDef(10, 40, 0.68, BlockType.PYROLUSITE_ORE, BlockType.VANADINITE_ORE),

        // Graphite/ruby (Y: 5-25)
        new OreVeinDef(5, 25, 0.72, BlockType.GRAPHITE_ORE, BlockType.RUBY_ORE, BlockType.PYROPE_ORE),

        // Gemstones (Y: 20-60)
        new OreVeinDef(20, 60, 0.68, BlockType.SAPPHIRE_ORE, BlockType.GREEN_SAPPHIRE_ORE, BlockType.SPESSARTINE_ORE),
    };

    /** Small ore definitions: depth range and base rarity threshold. */
    private static class SmallOreDef {
        final int minDepth, maxDepth;
        final double threshold;
        final BlockType ore;

        SmallOreDef(int minDepth, int maxDepth, double threshold, BlockType ore) {
            this.minDepth = minDepth;
            this.maxDepth = maxDepth;
            this.threshold = threshold;
            this.ore = ore;
        }
    }

    /** Small ore definitions (more common than veins, distributed throughout). */
    private static final SmallOreDef[] SMALL_ORE_DEFS = {
        // Early-game ores: common throughout
        new SmallOreDef(15, 64, 0.70, BlockType.SMALL_COPPER_ORE),
        new SmallOreDef(15, 64, 0.72, BlockType.SMALL_TIN_ORE),
        new SmallOreDef(15, 64, 0.74, BlockType.SMALL_BAUXITE_ORE),
        new SmallOreDef(15, 64, 0.71, BlockType.SMALL_ZINC_ORE),
        new SmallOreDef(15, 64, 0.73, BlockType.SMALL_LEAD_ORE),
        new SmallOreDef(15, 64, 0.75, BlockType.SMALL_SILVER_ORE),

        // Mid-game ores: less common
        new SmallOreDef(15, 50, 0.78, BlockType.SMALL_NICKEL_ORE),
        new SmallOreDef(15, 50, 0.79, BlockType.SMALL_COBALT_ORE),
        new SmallOreDef(15, 50, 0.80, BlockType.SMALL_TUNGSTEN_ORE),
        new SmallOreDef(15, 50, 0.81, BlockType.SMALL_MOLYBDENUM_ORE),
        new SmallOreDef(15, 50, 0.82, BlockType.SMALL_PLATINUM_ORE),

        // Advanced ores: rare
        new SmallOreDef(10, 35, 0.85, BlockType.SMALL_CHROMIUM_ORE),
        new SmallOreDef(10, 35, 0.86, BlockType.SMALL_MANGANESE_ORE),
        new SmallOreDef(10, 35, 0.87, BlockType.SMALL_VANADIUM_ORE),
        new SmallOreDef(10, 35, 0.88, BlockType.SMALL_BERYLLIUM_ORE),
        new SmallOreDef(10, 35, 0.89, BlockType.SMALL_TITANIUM_ORE),

        // Late-game ores: very rare deep
        new SmallOreDef(5, 20, 0.90, BlockType.SMALL_URANIUM_ORE),
        new SmallOreDef(5, 20, 0.91, BlockType.SMALL_THORIUM_ORE),
        new SmallOreDef(5, 20, 0.92, BlockType.SMALL_PLUTONIUM_ORE),
        new SmallOreDef(5, 20, 0.93, BlockType.SMALL_IRIDIUM_ORE),

        // New GTNH compound small ores
        new SmallOreDef(10, 50, 0.75, BlockType.SMALL_MAGNETITE_ORE),
        new SmallOreDef(10, 50, 0.76, BlockType.SMALL_HEMATITE_ORE),
        new SmallOreDef(15, 60, 0.74, BlockType.SMALL_BROWN_LIMONITE_ORE),
        new SmallOreDef(15, 60, 0.73, BlockType.SMALL_YELLOW_LIMONITE_ORE),
        new SmallOreDef(10, 50, 0.77, BlockType.SMALL_BANDED_IRON_ORE),
        new SmallOreDef(10, 50, 0.79, BlockType.SMALL_VANADIUM_MAGNETITE_ORE),
        new SmallOreDef(10, 40, 0.75, BlockType.SMALL_CHALCOPYRITE_ORE),
        new SmallOreDef(10, 40, 0.76, BlockType.SMALL_TETRAHEDRITE_ORE),
        new SmallOreDef(10, 40, 0.74, BlockType.SMALL_MALACHITE_ORE),
        new SmallOreDef(5, 30, 0.78, BlockType.SMALL_GALENA_ORE),
        new SmallOreDef(10, 40, 0.76, BlockType.SMALL_SPHALERITE_ORE),
        new SmallOreDef(10, 40, 0.77, BlockType.SMALL_GARNIERITE_ORE),
        new SmallOreDef(10, 40, 0.78, BlockType.SMALL_PENTLANDITE_ORE),
        new SmallOreDef(10, 40, 0.80, BlockType.SMALL_COBALTITE_ORE),
        new SmallOreDef(5, 40, 0.74, BlockType.SMALL_PYRITE_ORE),
        new SmallOreDef(5, 40, 0.78, BlockType.SMALL_ARSENOPYRITE_ORE),
        new SmallOreDef(5, 15, 0.76, BlockType.SMALL_SULFUR_ORE),
        new SmallOreDef(5, 15, 0.79, BlockType.SMALL_CINNABAR_ORE),
        new SmallOreDef(40, 80, 0.76, BlockType.SMALL_CASSITERITE_ORE),
        new SmallOreDef(5, 20, 0.82, BlockType.SMALL_SCHEELITE_ORE),
        new SmallOreDef(5, 20, 0.83, BlockType.SMALL_WOLFRAMITE_ORE),
        new SmallOreDef(5, 25, 0.81, BlockType.SMALL_MOLYBDENITE_ORE),
        new SmallOreDef(10, 40, 0.83, BlockType.SMALL_CHROMITE_ORE),
        new SmallOreDef(10, 40, 0.82, BlockType.SMALL_ILMENITE_ORE),
        new SmallOreDef(10, 40, 0.84, BlockType.SMALL_RUTILE_ORE),
        new SmallOreDef(5, 20, 0.87, BlockType.SMALL_URANINITE_ORE),
        new SmallOreDef(5, 20, 0.86, BlockType.SMALL_PITCHBLENDE_ORE),
        new SmallOreDef(5, 30, 0.85, BlockType.SMALL_MONAZITE_ORE),
        new SmallOreDef(5, 30, 0.86, BlockType.SMALL_BASTNASITE_ORE),
        new SmallOreDef(5, 25, 0.84, BlockType.SMALL_VANADINITE_ORE),
        new SmallOreDef(10, 40, 0.80, BlockType.SMALL_PYROLUSITE_ORE),
        new SmallOreDef(5, 25, 0.79, BlockType.SMALL_GRAPHITE_ORE),
        new SmallOreDef(30, 60, 0.81, BlockType.SMALL_LITHIUM_ORE),
        new SmallOreDef(5, 20, 0.91, BlockType.SMALL_NAQUADAH_ORE),
        new SmallOreDef(5, 20, 0.94, BlockType.SMALL_TRINIUM_ORE),
        new SmallOreDef(5, 30, 0.87, BlockType.SMALL_NEODYMIUM_ORE),
        new SmallOreDef(5, 30, 0.86, BlockType.SMALL_CERIUM_ORE),
        new SmallOreDef(5, 20, 0.92, BlockType.SMALL_OSMIUM_ORE),
        new SmallOreDef(5, 20, 0.90, BlockType.SMALL_PALLADIUM_ORE),
        new SmallOreDef(5, 25, 0.88, BlockType.SMALL_RUBY_ORE),
    };

    private final Noise veinNoise;
    private final Noise smallOreNoise;
    private final int seaLevel;

    public GthnOreGenerator(long seed, int seaLevel) {
        this.seaLevel = seaLevel;
        // Separate noise functions for veins and small ores to avoid correlation
        this.veinNoise = new Noise(seed ^ 0x1A2B3C4D5E6F7A8BL);
        this.smallOreNoise = new Noise(seed ^ 0x8B7A6F5E4D3C2B1AL);
    }

    /**
     * Check if a block position should be a GTNH ore (vein or small ore).
     * Returns BlockType.STONE if no ore should generate at this location.
     */
    public BlockType oreAt(int wx, int y, int wz) {
        if (y < 5 || y >= 64) return BlockType.STONE;

        // Check for vein ores (rarer, organized in clusters)
        for (OreVeinDef def : VEIN_DEFS) {
            if (y >= def.minDepth && y <= def.maxDepth) {
                if (isInVein(wx, y, wz, def.primaryOre)) {
                    if (veinNoise.fbm3(wx * 0.10, y * 0.10, wz * 0.10, 1, 0.5, 2.0) > def.threshold) {
                        return selectVeinOre(wx, y, wz, def);
                    }
                }
            }
        }

        // Check for small ores (more scattered, indicator ores)
        for (SmallOreDef def : SMALL_ORE_DEFS) {
            if (y >= def.minDepth && y <= def.maxDepth) {
                if (smallOreNoise.fbm3(wx * 0.08 + (def.ore.ordinal() * 1000), y * 0.08, wz * 0.08 + (def.ore.ordinal() * 1000), 1, 0.5, 2.0) > def.threshold) {
                    return def.ore;
                }
            }
        }

        return BlockType.STONE;
    }

    /**
     * Determine if a position is within an ore vein cluster.
     * Veins spawn at regular intervals (every VEIN_SPACING chunks).
     */
    private boolean isInVein(int wx, int y, int wz, BlockType oreType) {
        // Determine vein center for this region
        int veinChunkX = Math.floorDiv(wx, 16 * VEIN_SPACING);
        int veinChunkZ = Math.floorDiv(wz, 16 * VEIN_SPACING);

        // Hash to determine if this chunk region contains a vein
        long hash = ((long) veinChunkX * 73856093L) ^ ((long) veinChunkZ * 19349663L) ^ ((long) oreType.ordinal() * 83492791L);
        if ((hash & 0xFF) > 128) return false; // 50% chance of vein spawning

        // Vein center position within this region
        int veinCenterX = veinChunkX * 16 * VEIN_SPACING + 8 * 16 + (int) ((hash >> 8) & 0xF) - 8;
        int veinCenterZ = veinChunkZ * 16 * VEIN_SPACING + 8 * 16 + (int) ((hash >> 16) & 0xF) - 8;
        int veinCenterY = 5 + (int) ((hash >> 24) & 0x3F);

        // Distance to vein center (vein radius ~15 blocks)
        double dist = Math.sqrt(
            Math.pow(wx - veinCenterX, 2) +
            Math.pow(y - veinCenterY, 2) +
            Math.pow(wz - veinCenterZ, 2)
        );

        return dist <= 15;
    }

    /**
     * Select an ore from within the vein: primary ore with scattered secondary ores.
     */
    private BlockType selectVeinOre(int wx, int y, int wz, OreVeinDef def) {
        double rand = veinNoise.fbm3(wx * 0.15 + 5000, y * 0.15, wz * 0.15 + 5000, 1, 0.5, 2.0);

        if (rand < 0.7) {
            return def.primaryOre;
        } else if (def.secondaryOres.length > 0) {
            // Pick a secondary ore based on noise
            int idx = (int) ((rand - 0.7) * 10 * def.secondaryOres.length) % def.secondaryOres.length;
            return def.secondaryOres[idx];
        }
        return def.primaryOre;
    }
}
