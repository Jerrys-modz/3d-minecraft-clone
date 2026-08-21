package com.minecraftclone.world.gen;

import com.minecraftclone.util.Noise;
import com.minecraftclone.world.BlockType;

import java.util.EnumMap;
import java.util.Map;

/**
 * GTNH (GregTech: New Horizons) ore generation.
 *
 * <p>Veins sit on the GTNH chunk lattice: a chunk is an <em>ore chunk</em> iff
 * {@code |cx| % 3 == 1} and {@code |cz| % 3 == 1}. Away from the origin that
 * is an ore chunk, two empty, another ore chunk. Each ore chunk gets
 * <strong>exactly one</strong> mix (weighted toward early-game), placed as a
 * dense 9-block-tall cuboid 16–31 blocks wide — the GTNH overworld shape —
 * rather than a scattered noise blob.
 *
 * <p>Small ores are two systems, like GTNH:
 * <ul>
 *   <li><b>Global</b> — sparse single blocks of common overworld metals
 *       (copper, tin, zinc, silver, nickel) anywhere in stone, for early tools.</li>
 *   <li><b>Indicators</b> — a halo around each vein of that mix's small ores.
 *       A cluster of small chalcopyrite means a chalcopyrite vein is nearby;
 *       small naquadah never appears unless that mix actually generated.</li>
 * </ul>
 */
public class GthnOreGenerator {

    /** Vein spacing in chunks. GTNH: ore chunk, then two non-ore, then another. */
    public static final int VEIN_SPACING = 3;

    /** GTNH overworld veins are always this many blocks tall. */
    public static final int VEIN_HEIGHT = 9;

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

    private static final int TOTAL_MIX_WEIGHT = computeTotalWeight();

    /**
     * GTNH VisualProspecting-style mix identity: the vein's primary ore names
     * the mix ("Copper Mix"), and the composition lists primary + secondaries.
     */
    public static final class MixInfo {
        public final String name;
        public final BlockType primary;
        public final BlockType[] composition;

        public MixInfo(String name, BlockType primary, BlockType[] composition) {
            this.name = name;
            this.primary = primary;
            this.composition = composition;
        }

        /** Comma-separated ore labels for hover popups, e.g. "Copper, Tin, Silver". */
        public String compositionLabel() {
            StringBuilder sb = new StringBuilder();
            for (BlockType t : composition) {
                if (sb.length() > 0) sb.append(", ");
                sb.append(oreLabel(t));
            }
            return sb.toString();
        }
    }

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

    /**
     * Full-size ore → small-ore block. Built from {@link #SMALL_ORE_DEFS} by
     * name ({@code SMALL_COPPER_ORE} → {@code COPPER_ORE}) so it stays in sync.
     */
    private static final Map<BlockType, BlockType> SMALL_OF = new EnumMap<>(BlockType.class);

    /**
     * GTNH overworld global small ores we actually ship (no small iron/gold/coal/
     * diamond/redstone/lapis — vanilla full ores cover those).
     */
    static final BlockType[] GLOBAL_SMALL = {
            BlockType.SMALL_COPPER_ORE,
            BlockType.SMALL_TIN_ORE,
            BlockType.SMALL_ZINC_ORE,
            BlockType.SMALL_SILVER_ORE,
            BlockType.SMALL_NICKEL_ORE,
    };

    static {
        for (SmallOreDef d : SMALL_ORE_DEFS) {
            String name = d.ore.name();
            if (!name.startsWith("SMALL_")) continue;
            try {
                SMALL_OF.put(BlockType.valueOf(name.substring("SMALL_".length())), d.ore);
            } catch (IllegalArgumentException ignored) {
                // SMALL_* with no matching full ore — skip.
            }
        }
    }

    private final Noise veinNoise;
    private final long seed;

    public GthnOreGenerator(long seed, int seaLevel) {
        this.seed = seed;
        this.veinNoise = new Noise(seed ^ 0x1A2B3C4D5E6F7A8BL);
    }

    /**
     * GTNH ore-chunk lattice: {@code |chunkX| % 3 == 1} and {@code |chunkZ| % 3 == 1}.
     * That is an ore chunk, then two empty, then another — except next to 0
     * where {@code abs} packs (-1, 1) one chunk closer, matching the wiki.
     */
    public static boolean isOreChunk(int chunkX, int chunkZ) {
        return Math.abs(chunkX) % VEIN_SPACING == 1
                && Math.abs(chunkZ) % VEIN_SPACING == 1;
    }

    /**
     * Look up the GTNH mix a full-size ore belongs to. Primaries win when an
     * ore is both a primary of one vein and a secondary of another; unmatched
     * vanilla ores (coal/iron/gold/diamond) become a single-ore "mix" named
     * after themselves so they still get a waypoint.
     */
    public static MixInfo mixInfo(BlockType ore) {
        if (ore == null) return null;
        MixInfo primaryHit = null;
        MixInfo secondaryHit = null;
        for (OreVeinDef def : VEIN_DEFS) {
            if (def.primaryOre == ore) {
                primaryHit = toInfo(def);
                break;
            }
            if (secondaryHit == null) {
                for (BlockType s : def.secondaryOres) {
                    if (s == ore) {
                        secondaryHit = toInfo(def);
                        break;
                    }
                }
            }
        }
        if (primaryHit != null) return primaryHit;
        if (secondaryHit != null) return secondaryHit;
        if (ore.solid && !ore.name().startsWith("SMALL_") && ore.name().endsWith("_ORE")) {
            return new MixInfo(oreLabel(ore), ore, new BlockType[]{ore});
        }
        return null;
    }

    /** Convenience: {@code mixInfo(ore).name}, or {@code null}. */
    public static String mixName(BlockType ore) {
        MixInfo info = mixInfo(ore);
        return info == null ? null : info.name;
    }

    /**
     * The mix assigned to this ore chunk, or {@code null} if the chunk is not
     * on the GTNH lattice. Deterministic per seed.
     */
    public MixInfo mixForOreChunk(int chunkX, int chunkZ) {
        if (!isOreChunk(chunkX, chunkZ)) return null;
        return toInfo(pickMix(mixHash(chunkX, chunkZ)));
    }

    static String oreLabel(BlockType t) {
        String n = t.displayName();
        if (n.endsWith(" Ore")) n = n.substring(0, n.length() - 4);
        return n;
    }

    private static MixInfo toInfo(OreVeinDef def) {
        java.util.LinkedHashSet<BlockType> unique = new java.util.LinkedHashSet<>();
        unique.add(def.primaryOre);
        for (BlockType s : def.secondaryOres) unique.add(s);
        return new MixInfo(oreLabel(def.primaryOre) + " Mix", def.primaryOre,
                unique.toArray(new BlockType[0]));
    }

    /**
     * Check if a block position should be a GTNH ore (vein or small ore).
     * Returns {@link BlockType#STONE} if no ore should generate at this location.
     */
    public BlockType oreAt(int wx, int y, int wz) {
        if (y < 1 || y >= 96) return BlockType.STONE;

        BlockType vein = veinOreAt(wx, y, wz);
        if (vein != BlockType.STONE) return vein;

        return smallOreAt(wx, y, wz);
    }

    /**
     * Walk the 3×3 of neighbouring chunks and place ore if this block falls
     * inside that neighbour's cuboid. Veins can spill ~1 chunk out of the
     * ore chunk; they never overlap (centres are 48 blocks apart, max half-width 15).
     */
    private BlockType veinOreAt(int wx, int y, int wz) {
        int cx = Math.floorDiv(wx, 16);
        int cz = Math.floorDiv(wz, 16);
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                int ocx = cx + dx;
                int ocz = cz + dz;
                if (!isOreChunk(ocx, ocz)) continue;
                BlockType ore = oreFromVein(ocx, ocz, wx, y, wz);
                if (ore != BlockType.STONE) return ore;
            }
        }
        return BlockType.STONE;
    }

    private BlockType oreFromVein(int oreCx, int oreCz, int wx, int y, int wz) {
        long h = mixHash(oreCx, oreCz);
        OreVeinDef def = pickMix(h);
        int bottom = veinBottom(def, h);
        if (y < bottom || y >= bottom + VEIN_HEIGHT) return BlockType.STONE;

        int size = 16 + (int) ((h >>> 8) & 0xF); // 16–31, GTNH min width 16
        int centerX = oreCx * 16 + 8;
        int centerZ = oreCz * 16 + 8;
        int half = size / 2;
        if (Math.abs(wx - centerX) > half || Math.abs(wz - centerZ) > half) {
            return BlockType.STONE;
        }

        // A little stone Swiss-cheese so the cuboid isn't a perfect brick.
        double gap = veinNoise.fbm3(wx * 0.35, y * 0.35, wz * 0.35, 1, 0.5, 2.0);
        if (gap > 0.72) return BlockType.STONE;

        return selectVeinOre(wx, y, wz, def, y - bottom);
    }

    /** Pin a 9-tall vein inside the mix's depth window. */
    private static int veinBottom(OreVeinDef def, long h) {
        int span = def.maxDepth - def.minDepth + 1 - VEIN_HEIGHT;
        if (span <= 0) return def.minDepth;
        return def.minDepth + (int) Long.remainderUnsigned(h >>> 16, span + 1);
    }

    /**
     * Lower layers are primary-heavy, upper layers secondary — GTNH's
     * primary / secondary / between stack, compressed into 9 blocks.
     */
    private BlockType selectVeinOre(int wx, int y, int wz, OreVeinDef def, int localY) {
        if (def.secondaryOres.length == 0) return def.primaryOre;
        double n = (veinNoise.fbm3(wx * 0.22, y * 0.22, wz * 0.22, 1, 0.5, 2.0) + 1.0) * 0.5;
        double primaryChance;
        if (localY <= 3) primaryChance = 0.85;
        else if (localY <= 6) primaryChance = 0.45;
        else primaryChance = 0.25;
        if (n < primaryChance) return def.primaryOre;
        int idx = (int) (n * 13 * def.secondaryOres.length) % def.secondaryOres.length;
        if (idx < 0) idx = 0;
        return def.secondaryOres[idx];
    }

    private BlockType smallOreAt(int wx, int y, int wz) {
        BlockType indicator = indicatorAt(wx, y, wz);
        if (indicator != BlockType.STONE) return indicator;
        return globalSmallAt(wx, y, wz);
    }

    /**
     * Halo around each ore-chunk vein: sparse single blocks of that mix's
     * small ores. Radius is the cuboid plus 8, Y is the 9-tall vein ± 8.
     */
    private BlockType indicatorAt(int wx, int y, int wz) {
        int cx = Math.floorDiv(wx, 16);
        int cz = Math.floorDiv(wz, 16);
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                int ocx = cx + dx;
                int ocz = cz + dz;
                if (!isOreChunk(ocx, ocz)) continue;
                long h = mixHash(ocx, ocz);
                OreVeinDef def = pickMix(h);
                int bottom = veinBottom(def, h);
                if (y < bottom - 8 || y > bottom + VEIN_HEIGHT + 8) continue;
                int size = 16 + (int) ((h >>> 8) & 0xF);
                int halo = size / 2 + 8;
                int centerX = ocx * 16 + 8;
                int centerZ = ocz * 16 + 8;
                if (Math.abs(wx - centerX) > halo || Math.abs(wz - centerZ) > halo) continue;
                // Isolated blocks, not a noise blob: ~1/1024 of the halo.
                if ((posHash(wx, y, wz, 0x51ED) & 0x3FF) != 0) continue;
                BlockType small = smallForMix(def, wx, y, wz);
                if (small != BlockType.STONE) return small;
            }
        }
        return BlockType.STONE;
    }

    private BlockType smallForMix(OreVeinDef def, int wx, int y, int wz) {
        BlockType[] opts = new BlockType[1 + def.secondaryOres.length];
        int n = 0;
        BlockType primarySmall = SMALL_OF.get(def.primaryOre);
        if (primarySmall != null) opts[n++] = primarySmall;
        for (BlockType s : def.secondaryOres) {
            BlockType sm = SMALL_OF.get(s);
            if (sm != null) opts[n++] = sm;
        }
        if (n == 0) return BlockType.STONE;
        int pick = Math.floorMod(posHash(wx, y, wz, 0xC0DE), n);
        return opts[pick];
    }

    /** Sparse common metals anywhere in stone. Not a prospecting signal. */
    private BlockType globalSmallAt(int wx, int y, int wz) {
        if (y < 5 || y > 60) return BlockType.STONE;
        if ((posHash(wx, y, wz, 0x610B) & 0x3FF) != 0) return BlockType.STONE;
        int pick = Math.floorMod(posHash(wx, y, wz, 0xA11A), GLOBAL_SMALL.length);
        return GLOBAL_SMALL[pick];
    }

    static boolean isGlobalSmall(BlockType type) {
        for (BlockType g : GLOBAL_SMALL) {
            if (g == type) return true;
        }
        return false;
    }

    /** Small-ore block for a full-size ore, or {@code null} if this pack has none. */
    static BlockType smallOf(BlockType fullOre) {
        return SMALL_OF.get(fullOre);
    }

    private int posHash(int x, int y, int z, int salt) {
        long h = seed ^ salt;
        h ^= (long) x * 0x9E3779B97F4A7C15L;
        h ^= (long) y * 0xBF58476D1CE4E5B9L;
        h ^= (long) z * 0x94D049BB133111EBL;
        h ^= (h >>> 30);
        h *= 0xBF58476D1CE4E5B9L;
        h ^= (h >>> 27);
        return (int) h;
    }

    /** Lower threshold (more common mix) → higher pick weight. Naquadah stays rare. */
    private static int mixWeight(OreVeinDef def) {
        return Math.max(1, (int) Math.round((1.0 - def.threshold) * 20.0));
    }

    private static int computeTotalWeight() {
        int t = 0;
        for (OreVeinDef d : VEIN_DEFS) t += mixWeight(d);
        return t;
    }

    private static OreVeinDef pickMix(long h) {
        int r = (int) Long.remainderUnsigned(h, TOTAL_MIX_WEIGHT);
        for (OreVeinDef d : VEIN_DEFS) {
            r -= mixWeight(d);
            if (r < 0) return d;
        }
        return VEIN_DEFS[VEIN_DEFS.length - 1];
    }

    private long mixHash(int oreCx, int oreCz) {
        long h = seed;
        h ^= (long) oreCx * 0x9E3779B97F4A7C15L;
        h ^= (long) oreCz * 0xC2B2AE3D27D4EB4FL;
        h ^= (h >>> 30);
        h *= 0xBF58476D1CE4E5B9L;
        h ^= (h >>> 27);
        h *= 0x94D049BB133111EBL;
        h ^= (h >>> 31);
        return h;
    }
}
