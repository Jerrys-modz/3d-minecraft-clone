package com.minecraftclone.world;

import com.minecraftclone.player.Crafting;
import com.minecraftclone.player.Smelting;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** The LV Electrolyzer and aluminium dust. */
class ElectrolyzerTest {

    private static ElectrolyzerEntity powered() {
        ElectrolyzerEntity e = new ElectrolyzerEntity();
        BatteryBlockEntity b = new BatteryBlockEntity();
        b.setSlot(BatteryBlockEntity.SLOT_BATTERIES, BlockType.LV_BATTERY, 4);
        b.chargeEU(b.capacity());
        e.euSource = b;
        return e;
    }

    private static void run(ElectricMachineEntity m, float seconds) {
        for (float t = 0; t < seconds; t += 0.05f) m.tick(0.05f);
    }

    @Test
    void chalcopyriteSplitsIntoCopperAndIron() {
        ElectrolyzerEntity e = powered();
        e.setSlot(ElectricMachineEntity.SLOT_INPUT, BlockType.CRUSHED_CHALCOPYRITE, 3);
        run(e, ElectrolyzerEntity.SPLIT_SECONDS * 3 + 1f);
        assertNull(e.typeOf(ElectricMachineEntity.SLOT_INPUT));
        assertEquals(BlockType.COPPER_DUST, e.typeOf(ElectricMachineEntity.SLOT_OUTPUT));
        assertEquals(6, e.countOf(ElectricMachineEntity.SLOT_OUTPUT));
        assertEquals(BlockType.IRON_DUST, e.typeOf(ElectricMachineEntity.SLOT_BYPRODUCT));
        assertEquals(3, e.countOf(ElectricMachineEntity.SLOT_BYPRODUCT), "secondary every operation");
    }

    @Test
    void recipesWithoutSecondaryLeaveByproductEmpty() {
        ElectrolyzerEntity e = powered();
        e.setSlot(ElectricMachineEntity.SLOT_INPUT, BlockType.CRUSHED_RUTILE, 1);
        run(e, ElectrolyzerEntity.SPLIT_SECONDS + 1f);
        assertEquals(BlockType.TITANIUM_DUST, e.typeOf(ElectricMachineEntity.SLOT_OUTPUT));
        assertNull(e.typeOf(ElectricMachineEntity.SLOT_BYPRODUCT));
    }

    @Test
    void bauxiteIsTheAluminiumSource() {
        assertEquals(BlockType.ALUMINUM_DUST, ElectrolyzerEntity.recipeFor(BlockType.CRUSHED_BAUXITE).primary());
        assertEquals(BlockType.ALUMINUM_DUST, ElectrolyzerEntity.recipeFor(BlockType.BAUXITE_DUST).primary());
        assertEquals(BlockType.ALUMINUM_INGOT, Smelting.outputFor(BlockType.ALUMINUM_DUST));
        assertTrue(BlockType.ALUMINUM_DUST.isItem);
    }

    @Test
    void elementsAndPlainOresAreRejected() {
        ElectrolyzerEntity e = new ElectrolyzerEntity();
        assertFalse(e.acceptsInput(BlockType.COPPER_DUST));
        assertFalse(e.acceptsInput(BlockType.CRUSHED_COPPER));
        assertFalse(e.acceptsInput(BlockType.IRON_ORE));
        assertTrue(e.acceptsInput(BlockType.CRUSHED_GALENA));
    }

    @Test
    void everyRecipeProducesKnownItems() {
        for (BlockType t : BlockType.values()) {
            ElectrolyzerEntity.Split s = ElectrolyzerEntity.recipeFor(t);
            if (s == null) continue;
            assertNotNull(s.primary(), t.name());
            assertNotEquals(t, s.primary(), t + " must not split into itself");
        }
    }

    @Test
    void registeredCraftableAndPlaceable() {
        assertTrue(BlockEntities.isRegistered(ElectrolyzerEntity.TYPE));
        BlockType A = BlockType.GOLD_INGOT, G = BlockType.GLASS, X = BlockType.COPPER_INGOT,
                C = BlockType.STEEL_CASING, V = BlockType.VACUUM_TUBE;
        var r = Crafting.match3x3(new BlockType[]{A, G, A, X, C, X, X, V, X});
        assertNotNull(r);
        assertEquals(BlockType.ELECTROLYZER, r.output());
    }
}
