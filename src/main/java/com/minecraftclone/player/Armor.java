package com.minecraftclone.player;

import com.minecraftclone.world.BlockType;

import java.util.EnumMap;
import java.util.Map;

/**
 * Armor rules: which slot each armor piece fills, how many defense points it
 * grants, and how long it lasts. Kept as a static registry separate from
 * {@link BlockType} (like {@link com.minecraftclone.world.Mining} for tools)
 * so that enum stays focused on rendering/collision.
 * <p>
 * Defense follows Minecraft's armor-points model: each piece grants a number
 * of points, the total is capped at {@value #DEFENSE_CAP}, and incoming
 * damage is reduced by a percentage of that total - see
 * {@link #damageMultiplier(int)}. A full diamond set (the cap) blocks 80% of
 * all damage; a full wood set blocks 28%.
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

    public record ArmorStats(Slot slot, int defense, int maxUses) {
    }

    private static final Map<BlockType, ArmorStats> ARMOR = new EnumMap<>(BlockType.class);

    static {
        // Wood - the weakest tier (a full set is 7 points -> 28% reduction).
        put(BlockType.WOOD_HELMET, Slot.HELMET, 1, 55);
        put(BlockType.WOOD_CHESTPLATE, Slot.CHESTPLATE, 3, 80);
        put(BlockType.WOOD_LEGGINGS, Slot.LEGGINGS, 2, 70);
        put(BlockType.WOOD_BOOTS, Slot.BOOTS, 1, 45);
        // Stone - a mid tier (full set = 11 points -> 44%).
        put(BlockType.STONE_HELMET, Slot.HELMET, 2, 110);
        put(BlockType.STONE_CHESTPLATE, Slot.CHESTPLATE, 4, 176);
        put(BlockType.STONE_LEGGINGS, Slot.LEGGINGS, 3, 154);
        put(BlockType.STONE_BOOTS, Slot.BOOTS, 2, 88);
        // Iron - the strong standard tier (full set = 17 points -> 68%).
        put(BlockType.IRON_HELMET, Slot.HELMET, 3, 209);
        put(BlockType.IRON_CHESTPLATE, Slot.CHESTPLATE, 6, 335);
        put(BlockType.IRON_LEGGINGS, Slot.LEGGINGS, 5, 293);
        put(BlockType.IRON_BOOTS, Slot.BOOTS, 3, 167);
        // Diamond - the best tier (full set = 22 points, capped at the 20-point
        // DEFENSE_CAP -> the maximum 80% reduction).
        put(BlockType.DIAMOND_HELMET, Slot.HELMET, 4, 1301);
        put(BlockType.DIAMOND_CHESTPLATE, Slot.CHESTPLATE, 8, 2083);
        put(BlockType.DIAMOND_LEGGINGS, Slot.LEGGINGS, 6, 1822);
        put(BlockType.DIAMOND_BOOTS, Slot.BOOTS, 4, 1041);
    }

    private static void put(BlockType type, Slot slot, int defense, int maxUses) {
        ARMOR.put(type, new ArmorStats(slot, defense, maxUses));
    }

    private Armor() {
    }

    public static boolean isArmor(BlockType type) {
        return type != null && ARMOR.containsKey(type);
    }

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

    /**
     * Damage multiplier for {@code defensePoints} total armor points:
     * Minecraft's {@code 1 - min(points, 20)/25}, so 20 points leaves 20% of
     * damage (an 80% reduction) and 0 points leaves 100%.
     */
    public static float damageMultiplier(int defensePoints) {
        return 1f - Math.min(DEFENSE_CAP, defensePoints) / 25f;
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
