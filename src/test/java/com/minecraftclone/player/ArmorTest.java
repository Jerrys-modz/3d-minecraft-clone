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
    void furIsWarmestAndMetalIsNot() {
        // Fur insulates far better than wood, and better than iron/diamond.
        assertTrue(Armor.warmth(BlockType.FUR_CHESTPLATE) > Armor.warmth(BlockType.WOOD_CHESTPLATE));
        assertTrue(Armor.warmth(BlockType.WOOD_CHESTPLATE) > Armor.warmth(BlockType.IRON_CHESTPLATE));
        assertTrue(Armor.warmth(BlockType.IRON_CHESTPLATE) > Armor.warmth(BlockType.DIAMOND_CHESTPLATE));
        // Bare metal and diamond conduct heat away - diamond is not warm at all.
        assertEquals(0f, Armor.warmth(BlockType.DIAMOND_HELMET), 1e-6f);
        assertTrue(Armor.warmth(BlockType.IRON_BOOTS) < 0.1f, "iron barely insulates");
        // Non-armor has no warmth.
        assertEquals(0f, Armor.warmth(BlockType.APPLE), 1e-6f);
    }

    @Test
    void aFullFurSetNearlyShrugsOffTheCold() {
        float fullFur = Armor.totalWarmth(BlockType.FUR_HELMET, BlockType.FUR_CHESTPLATE,
                BlockType.FUR_LEGGINGS, BlockType.FUR_BOOTS);
        assertTrue(fullFur >= Armor.WARMTH_CAP * 0.79f, "a full fur set should be almost fully warm: " + fullFur);
        assertEquals(0.0f, Armor.coldMultiplier(Armor.WARMTH_CAP), 1e-6f);
        assertEquals(1f, Armor.coldMultiplier(0f), 1e-6f);
        assertTrue(Armor.coldMultiplier(fullFur) < 0.25f, "fur cuts cold exposure to a fraction");
    }

    @Test
    void furDefendsPoorlyButIsRealArmor() {
        assertTrue(Armor.isArmor(BlockType.FUR_HELMET));
        assertTrue(Armor.isArmor(BlockType.FUR_BOOTS));
        // Full fur set is far weaker defensively than full diamond.
        int furDefense = Armor.totalDefense(BlockType.FUR_HELMET, BlockType.FUR_CHESTPLATE,
                BlockType.FUR_LEGGINGS, BlockType.FUR_BOOTS);
        assertTrue(furDefense < 10, "fur is barely protective: " + furDefense);
        assertTrue(furDefense > 0);
        // Wool is the material (a real item, not armor itself).
        assertFalse(Armor.isArmor(BlockType.WOOL));
    }

    @Test
    void furTiersGetWarmAndTougherAsTheyGetRarer() {
        // Warmth climbs with the tier: sheep wool < wolf pelt < polar bear hide.
        float fur = Armor.totalWarmth(BlockType.FUR_HELMET, BlockType.FUR_CHESTPLATE,
                BlockType.FUR_LEGGINGS, BlockType.FUR_BOOTS);
        float wolf = Armor.totalWarmth(BlockType.WOLF_HELMET, BlockType.WOLF_CHESTPLATE,
                BlockType.WOLF_LEGGINGS, BlockType.WOLF_BOOTS);
        float bear = Armor.totalWarmth(BlockType.BEAR_HELMET, BlockType.BEAR_CHESTPLATE,
                BlockType.BEAR_LEGGINGS, BlockType.BEAR_BOOTS);
        assertTrue(fur < wolf && wolf < bear, "warmth: fur < wolf < bear");
        assertEquals(0f, Armor.coldMultiplier(bear), 1e-6f, "a full bear set fully shrugs off the cold");

        // Defense climbs with the tier too - bear hide is tough.
        int furDefense = Armor.totalDefense(BlockType.FUR_HELMET, BlockType.FUR_CHESTPLATE,
                BlockType.FUR_LEGGINGS, BlockType.FUR_BOOTS);
        int wolfDefense = Armor.totalDefense(BlockType.WOLF_HELMET, BlockType.WOLF_CHESTPLATE,
                BlockType.WOLF_LEGGINGS, BlockType.WOLF_BOOTS);
        int bearDefense = Armor.totalDefense(BlockType.BEAR_HELMET, BlockType.BEAR_CHESTPLATE,
                BlockType.BEAR_LEGGINGS, BlockType.BEAR_BOOTS);
        assertTrue(furDefense < wolfDefense && wolfDefense < bearDefense, "defense: fur < wolf < bear");
        int fullIron = Armor.totalDefense(BlockType.IRON_HELMET, BlockType.IRON_CHESTPLATE,
                BlockType.IRON_LEGGINGS, BlockType.IRON_BOOTS);
        assertTrue(bearDefense < fullIron, "even a bear set stays weaker than full iron");
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
