package com.minecraftclone.player;

import com.minecraftclone.world.BlockType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ArmorDurabilityTest {

    @Test
    void armorReducesIncomingDamage() {
        PlayerStats stats = new PlayerStats();
        stats.setArmorMultiplier(Armor.damageMultiplier(Armor.totalDefense(
                BlockType.IRON_HELMET, BlockType.IRON_CHESTPLATE, BlockType.IRON_LEGGINGS, BlockType.IRON_BOOTS)));
        stats.damage(100f);
        // 17 points -> multiplier 0.32 -> 32 damage taken.
        assertEquals(100f - 32f, stats.getHealth(), 1e-4f);
    }

    @Test
    void noArmorMeansFullDamage() {
        PlayerStats stats = new PlayerStats();
        stats.damage(50f);
        assertEquals(50f, stats.getHealth(), 1e-4f);
    }

    @Test
    void armorWearsDownAndBreaks() {
        ToolDurability durability = new ToolDurability();
        assertFalse(durability.wear(BlockType.WOOD_HELMET, Armor.durabilityCost(4f)));
        assertEquals(54, durability.remaining(BlockType.WOOD_HELMET));
        // Wear it out completely; the caller unequips the piece once it breaks.
        boolean broke = false;
        int wears = 0;
        for (int i = 0; i < 60 && !broke; i++) {
            wears++;
            broke = durability.wear(BlockType.WOOD_HELMET, 1);
        }
        assertTrue(broke, "the last use should break the piece");
        assertEquals(54, wears, "one use was already spent above; 54 more wear it out");
    }

    @Test
    void wearBarsReportArmorFractions() {
        ToolDurability durability = new ToolDurability();
        durability.wear(BlockType.IRON_BOOTS, Armor.durabilityCost(8f));
        float frac = durability.fraction(BlockType.IRON_BOOTS);
        assertTrue(frac > 0.9f && frac < 1f, "one hit costs ~2/167 of an iron boot");
    }

    @Test
    void wearCostIsProportionalToDamage() {
        int light = Armor.durabilityCost(4f);
        int heavy = Armor.durabilityCost(40f);
        assertTrue(heavy > light);
    }
}
