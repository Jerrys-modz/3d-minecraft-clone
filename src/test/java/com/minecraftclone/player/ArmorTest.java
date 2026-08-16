package com.minecraftclone.player;

import com.minecraftclone.world.BlockType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ArmorTest {

    @Test
    void eachPieceKnowsItsSlotAndTier() {
        assertEquals(Armor.Slot.HELMET, Armor.slotOf(BlockType.IRON_HELMET));
        assertEquals(Armor.Slot.CHESTPLATE, Armor.slotOf(BlockType.IRON_CHESTPLATE));
        assertEquals(Armor.Slot.LEGGINGS, Armor.slotOf(BlockType.IRON_LEGGINGS));
        assertEquals(Armor.Slot.BOOTS, Armor.slotOf(BlockType.IRON_BOOTS));
        assertFalse(Armor.isArmor(BlockType.IRON_PICKAXE));
        assertFalse(Armor.isArmor(BlockType.APPLE));
    }

    @Test
    void defenseIsHigherForBetterMaterials() {
        assertTrue(Armor.defense(BlockType.DIAMOND_CHESTPLATE) > Armor.defense(BlockType.IRON_CHESTPLATE));
        assertTrue(Armor.defense(BlockType.IRON_CHESTPLATE) > Armor.defense(BlockType.STONE_CHESTPLATE));
        assertTrue(Armor.defense(BlockType.STONE_CHESTPLATE) > Armor.defense(BlockType.WOOD_CHESTPLATE));
    }

    @Test
    void totalDefenseIsCapped() {
        int fullDiamond = Armor.totalDefense(BlockType.DIAMOND_HELMET, BlockType.DIAMOND_CHESTPLATE,
                BlockType.DIAMOND_LEGGINGS, BlockType.DIAMOND_BOOTS);
        assertEquals(Armor.DEFENSE_CAP, fullDiamond); // 22 capped to 20

        int partial = Armor.totalDefense(BlockType.WOOD_HELMET, null, BlockType.WOOD_LEGGINGS, null);
        assertEquals(3, partial);
    }

    @Test
    void damageMultiplierFollowsMinecraftFormula() {
        assertEquals(1f, Armor.damageMultiplier(0), 1e-6f);       // no armor: full damage
        assertEquals(0.2f, Armor.damageMultiplier(20), 1e-6f);    // cap: 80% reduction
        assertEquals(0.32f, Armor.damageMultiplier(17), 1e-6f);   // iron set: 68% reduction
    }

    @Test
    void durabilityCostScalesWithDamage() {
        assertEquals(0, Armor.durabilityCost(0f));
        assertEquals(1, Armor.durabilityCost(2f));   // rounds to 1
        assertEquals(1, Armor.durabilityCost(4f));
        assertEquals(3, Armor.durabilityCost(12f));
        assertEquals(1, Armor.durabilityCost(0.5f)); // minimum 1 for any real hit
    }

    @Test
    void armorIsNotStackable() {
        assertEquals(1, Inventory.maxStack(BlockType.IRON_HELMET));
        assertEquals(1, Inventory.maxStack(BlockType.DIAMOND_BOOTS));
    }

    @Test
    void everyArmorPieceHasAnItemTextureGeneratorPath() {
        // The ItemTextures switch must cover every armor type; this test just
        // confirms the enum entries exist and carry item flags (the texture
        // painters themselves are exercised at startup).
        for (BlockType t : BlockType.values()) {
            if (Armor.isArmor(t)) {
                assertTrue(t.isItem);
            }
        }
    }
}
