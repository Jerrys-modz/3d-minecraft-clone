package com.minecraftclone.player;

import com.minecraftclone.world.BlockType;

import java.util.EnumMap;
import java.util.Map;

/**
 * Armor rules: which slot each armor piece fills, how many defense points it
 * grants, how warm it is against the cold, and how long it lasts. Kept as a
 * static registry separate from {@link BlockType} (like
 * {@link com.minecraftclone.world.Mining} for tools) so that enum stays
 * focused on rendering/collision.
 * <p>
 * Defense follows Minecraft's armor-points model: each piece grants a number
 * of points, the total is capped at {@value #DEFENSE_CAP}, and incoming
 * damage is reduced by a percentage of that total - see
 * {@link #damageMultiplier(int)}. A full diamond set (the cap) blocks 80% of
 * all damage; a full wood set blocks 28%.
 * <p>
 * Warmth (0..1 per piece) is how insulating the material is against the cold
 * (see {@link Player} - it cuts cold exposure). Insulators like fur and wool
 * are the warmest, wood is moderate, and bare metal / diamond are poor
 * insulators (they conduct heat away), so the warmest set and the most
 * protective set are different things. See {@link #warmth(int)}.
 * <p>
 * Durability works like tool wear: each piece has a uses budget, and taking
 * damage costs durability (roughly one use per four damage dealt - see
 * {@link #durabilityCost(float)}). At zero the piece is consumed, exactly like
 * a broken tool.
 */
public final class Armor {

    /** The four armor slots, in the order they appear on the inventory screen. */
    public enum Slot {HELMET, CHESTPLATE, LEGGINGS, BOOTS}

    /** Maximum armor points a player can have; above this, defense stops helping. */
    public static final int DEFENSE_CAP = 20;
    /** Warmth needed to fully resist the cold (all four pieces fully warm). */
    public static final float WARMTH_CAP = 4f;

    public record ArmorStats(Slot slot, int defense, float warmth, int maxUses, float radiationBlock) {
        /** Convenience constructor for non-radiation armour pieces (radiationBlock = 0). */
        ArmorStats(Slot slot, int defense, float warmth, int maxUses) {
            this(slot, defense, warmth, maxUses, 0f);
        }
    }

    private static final Map<BlockType, ArmorStats> ARMOR = new EnumMap<>(BlockType.class);

    static {
        // Wood - the weakest tier (a full set is 7 points -> 28% reduction), a
        // middling insulator (wood retains some heat).
        put(BlockType.WOOD_HELMET, Slot.HELMET, 1, 0.30f, 55);
        put(BlockType.WOOD_CHESTPLATE, Slot.CHESTPLATE, 3, 0.40f, 80);
        put(BlockType.WOOD_LEGGINGS, Slot.LEGGINGS, 2, 0.35f, 70);
        put(BlockType.WOOD_BOOTS, Slot.BOOTS, 1, 0.25f, 45);
        // Stone - a mid tier (full set = 11 points -> 44%); a poor insulator.
        put(BlockType.STONE_HELMET, Slot.HELMET, 2, 0.15f, 110);
        put(BlockType.STONE_CHESTPLATE, Slot.CHESTPLATE, 4, 0.20f, 176);
        put(BlockType.STONE_LEGGINGS, Slot.LEGGINGS, 3, 0.15f, 154);
        put(BlockType.STONE_BOOTS, Slot.BOOTS, 2, 0.10f, 88);
        // Iron - the strong standard tier (full set = 17 points -> 68%); a poor
        // insulator (metal conducts heat away).
        put(BlockType.IRON_HELMET, Slot.HELMET, 3, 0.10f, 209);
        put(BlockType.IRON_CHESTPLATE, Slot.CHESTPLATE, 6, 0.15f, 335);
        put(BlockType.IRON_LEGGINGS, Slot.LEGGINGS, 5, 0.10f, 293);
        put(BlockType.IRON_BOOTS, Slot.BOOTS, 3, 0.05f, 167);
        // Diamond - the best tier (full set = 22 points, capped at the 20-point
        // DEFENSE_CAP -> the maximum 80% reduction); no insulation at all.
        put(BlockType.DIAMOND_HELMET, Slot.HELMET, 4, 0f, 1301);
        put(BlockType.DIAMOND_CHESTPLATE, Slot.CHESTPLATE, 8, 0f, 2083);
        put(BlockType.DIAMOND_LEGGINGS, Slot.LEGGINGS, 6, 0f, 1822);
        put(BlockType.DIAMOND_BOOTS, Slot.BOOTS, 4, 0f, 1041);
        // Fur - the warmest tier (a full set = 3.2 warmth -> a warm jacket in a
        // blizzard), but barely any defense (full set = 5 points -> 20%). Made
        // from sheep's wool, so winter survival and combat want different gear.
        put(BlockType.FUR_HELMET, Slot.HELMET, 1, 0.75f, 90);
        put(BlockType.FUR_CHESTPLATE, Slot.CHESTPLATE, 2, 1f, 120);
        put(BlockType.FUR_LEGGINGS, Slot.LEGGINGS, 1, 0.85f, 110);
        put(BlockType.FUR_BOOTS, Slot.BOOTS, 1, 0.60f, 80);
        // Wolf pelt - the second fur tier, from the hostile wolves of the woods
        // (full set = 3.6 warmth, ~8 defense). Warmer and tougher than sheep wool.
        put(BlockType.WOLF_HELMET, Slot.HELMET, 2, 0.85f, 160);
        put(BlockType.WOLF_CHESTPLATE, Slot.CHESTPLATE, 3, 1f, 220);
        put(BlockType.WOLF_LEGGINGS, Slot.LEGGINGS, 2, 0.90f, 200);
        put(BlockType.WOLF_BOOTS, Slot.BOOTS, 1, 0.85f, 130);
        // Polar bear hide - the rarest, warmest, toughest fur tier, from the
        // great bears of the frozen wastes (full set = 4.0 warmth = the cap, and
        // ~12 defense - a tough hide even against monsters).
        put(BlockType.BEAR_HELMET, Slot.HELMET, 3, 1f, 350);
        put(BlockType.BEAR_CHESTPLATE, Slot.CHESTPLATE, 4, 1f, 480);
        put(BlockType.BEAR_LEGGINGS, Slot.LEGGINGS, 3, 1f, 430);
        put(BlockType.BEAR_BOOTS, Slot.BOOTS, 2, 1f, 280);
        // Hazmat suit - rubber outer layer over steel, sealed against radiation.
        // Each piece blocks 0.25 radiation exposure; a full set (4 × 0.25 = 1.0)
        // is completely radiation-proof. Moderate defence (iron-tier ballpark)
        // but no warmth (the sealed suit traps nothing against the cold).
        putRad(BlockType.HAZMAT_HELMET,     Slot.HELMET,     3, 0f, 200, 0.25f);
        putRad(BlockType.HAZMAT_CHESTPLATE, Slot.CHESTPLATE, 6, 0f, 320, 0.25f);
        putRad(BlockType.HAZMAT_LEGGINGS,   Slot.LEGGINGS,   5, 0f, 280, 0.25f);
        putRad(BlockType.HAZMAT_BOOTS,      Slot.BOOTS,      3, 0f, 160, 0.25f);
    }

    private static void put(BlockType type, Slot slot, int defense, float warmth, int maxUses) {
        ARMOR.put(type, new ArmorStats(slot, defense, warmth, maxUses, 0f));
    }

    private static void putRad(BlockType type, Slot slot, int defense, float warmth, int maxUses, float radiationBlock) {
        ARMOR.put(type, new ArmorStats(slot, defense, warmth, maxUses, radiationBlock));
    }

    private Armor() {
    }

    /** True if {@code type} is a registered armor piece (null-safe). */
    public static boolean isArmor(BlockType type) {
        return type != null && ARMOR.containsKey(type);
    }

    /** Full {@link ArmorStats} for {@code type}, or {@code null} if it isn't armor. */
    public static ArmorStats stats(BlockType type) {
        return ARMOR.get(type);
    }

    /** The slot an armor piece fills, or null if {@code type} isn't armor. */
    public static Slot slotOf(BlockType type) {
        ArmorStats stats = ARMOR.get(type);
        return stats == null ? null : stats.slot();
    }

    /** Defense points granted by a single piece (0 for non-armor). */
    public static int defense(BlockType type) {
        ArmorStats stats = ARMOR.get(type);
        return stats == null ? 0 : stats.defense();
    }

    /** Total defense points of the armor pieces currently equipped. */
    public static int totalDefense(BlockType helmet, BlockType chestplate, BlockType leggings, BlockType boots) {
        return Math.min(DEFENSE_CAP,
                defense(helmet) + defense(chestplate) + defense(leggings) + defense(boots));
    }

    /** Warmth granted by a single piece (0 for non-armor). */
    public static float warmth(BlockType type) {
        ArmorStats stats = ARMOR.get(type);
        return stats == null ? 0f : stats.warmth();
    }

    /**
     * Total warmth of the armor pieces currently equipped, 0 (bare) to
     * {@link #WARMTH_CAP} (a fully warm set). The player's cold exposure scales
     * down with this - see {@link Player}.
     */
    public static float totalWarmth(BlockType helmet, BlockType chestplate, BlockType leggings, BlockType boots) {
        return Math.min(WARMTH_CAP,
                warmth(helmet) + warmth(chestplate) + warmth(leggings) + warmth(boots));
    }

    /**
     * The cold-exposure multiplier for {@code warmth} total warmth points:
     * 1 at no warmth (fully exposed) down to 0 at the {@link #WARMTH_CAP} cap
     * (a fully warm set shrugs off even a blizzard). Interpolates in between.
     */
    public static float coldMultiplier(float warmth) {
        return Math.max(0f, 1f - Math.min(WARMTH_CAP, warmth) / WARMTH_CAP);
    }

    /**
     * Damage multiplier for {@code defensePoints} total armor points:
     * Minecraft's {@code 1 - min(points, 20)/25}, so 20 points leaves 20% of
     * damage (an 80% reduction) and 0 points leaves 100%.
     */
    public static float damageMultiplier(int defensePoints) {
        return 1f - Math.min(DEFENSE_CAP, defensePoints) / 25f;
    }

    /**
     * How much of the radiation-exposure rate a single armour piece blocks (0 = none).
     * A hazmat piece returns 0.25; a full set sums to 1.0 and is completely radiation-proof.
     */
    public static float radiationBlock(BlockType type) {
        ArmorStats stats = ARMOR.get(type);
        return stats == null ? 0f : stats.radiationBlock();
    }

    /**
     * Total radiation-exposure multiplier for the four equipped pieces.
     * Returns 0 (no radiation passes through) when wearing a full hazmat set,
     * 1 (fully exposed) when wearing no radiation-blocking pieces.
     */
    public static float radiationMultiplier(BlockType helmet, BlockType chestplate, BlockType leggings, BlockType boots) {
        float block = radiationBlock(helmet) + radiationBlock(chestplate)
                    + radiationBlock(leggings) + radiationBlock(boots);
        return Math.max(0f, 1f - Math.min(1f, block));
    }

    /** Durability cost in uses for {@code damage} dealt (about one use per 4 damage, minimum 1). */
    public static int durabilityCost(float damage) {
        if (damage <= 0f) return 0;
        return Math.max(1, Math.round(damage / 4f));
    }

    /** Maximum uses a piece can take before it's consumed. */
    public static int maxUses(BlockType type) {
        ArmorStats stats = ARMOR.get(type);
        return stats == null ? 0 : stats.maxUses();
    }
}
