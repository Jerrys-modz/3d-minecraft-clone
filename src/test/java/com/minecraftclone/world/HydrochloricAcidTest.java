package com.minecraftclone.world;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Salt-brine electrolysis to HCl + sodium, and the chloride titanium route. */
class HydrochloricAcidTest {

    private static BatteryBlockEntity battery() {
        BatteryBlockEntity b = new BatteryBlockEntity();
        b.setSlot(BatteryBlockEntity.SLOT_BATTERIES, BlockType.MV_BATTERY, 4);
        b.chargeEU(b.capacity());
        return b;
    }

    private static void run(ElectricMachineEntity m, float seconds) {
        for (float t = 0; t < seconds; t += 0.05f) m.tick(0.05f);
    }

    @Test
    void brineElectrolysisMakesSodiumAndHcl() {
        ElectrolyzerEntity e = new ElectrolyzerEntity();
        e.euSource = battery();
        FluidTankEntity water = new FluidTankEntity();
        water.fill(Fluid.WATER, 2000);
        FluidTankEntity acid = new FluidTankEntity();
        e.waterOverride = water;
        e.acidOverride = acid;
        e.setSlot(ElectricMachineEntity.SLOT_INPUT, BlockType.CRUSHED_SALT, 3);   // only 2 batches of water

        run(e, ElectrolyzerEntity.SPLIT_SECONDS * 3 + 2f);

        assertEquals(BlockType.SODIUM_DUST, e.typeOf(ElectricMachineEntity.SLOT_OUTPUT));
        assertEquals(4, e.countOf(ElectricMachineEntity.SLOT_OUTPUT));
        assertEquals(Fluid.HYDROCHLORIC_ACID, acid.fluid());
        assertEquals(2000, acid.amount());
        assertEquals(0, water.amount());
        assertEquals(1, e.countOf(ElectricMachineEntity.SLOT_INPUT), "stops when the water runs out");
    }

    @Test
    void nonBrineElectrolysisNeedsNoTanks() {
        ElectrolyzerEntity e = new ElectrolyzerEntity();
        e.euSource = battery();
        e.setSlot(ElectricMachineEntity.SLOT_INPUT, BlockType.CRUSHED_CHALCOPYRITE, 1);
        run(e, ElectrolyzerEntity.SPLIT_SECONDS + 1f);
        assertEquals(BlockType.COPPER_DUST, e.typeOf(ElectricMachineEntity.SLOT_OUTPUT));
    }

    @Test
    void hclTriplesTitaniumFromIlmenite() {
        ChemicalReactorEntity r = new ChemicalReactorEntity();
        r.euSource = battery();
        FluidTankEntity hcl = new FluidTankEntity();
        hcl.fill(Fluid.HYDROCHLORIC_ACID, 500);
        r.sourceOverride = hcl;
        r.setSlot(ElectricMachineEntity.SLOT_INPUT, BlockType.CRUSHED_ILMENITE, 2);

        run(r, ChemicalReactorEntity.REACT_SECONDS * 2 + 1f);

        assertEquals(BlockType.TITANIUM_DUST, r.typeOf(ElectricMachineEntity.SLOT_OUTPUT));
        assertEquals(2 * ChemicalReactorEntity.TITANIUM_YIELD, r.countOf(ElectricMachineEntity.SLOT_OUTPUT));
        assertEquals(BlockType.IRON_DUST, r.typeOf(ElectricMachineEntity.SLOT_BYPRODUCT));
        assertEquals(0, hcl.amount());
    }

    @Test
    void rutileHasNoByproductAndSulfuricAcidWontDo() {
        ChemicalReactorEntity r = new ChemicalReactorEntity();
        r.euSource = battery();
        FluidTankEntity sulfuric = new FluidTankEntity();
        sulfuric.fill(Fluid.SULFURIC_ACID, 1000);
        r.sourceOverride = sulfuric;
        r.setSlot(ElectricMachineEntity.SLOT_INPUT, BlockType.CRUSHED_RUTILE, 1);
        run(r, ChemicalReactorEntity.REACT_SECONDS + 1f);
        assertNull(r.typeOf(ElectricMachineEntity.SLOT_OUTPUT), "rutile needs HCl, not sulfuric acid");
    }

    @Test
    void hclOnlyFlowsThroughStainless() {
        assertFalse(FluidPipeTier.STEEL.carries(Fluid.HYDROCHLORIC_ACID));
        assertTrue(FluidPipeTier.STAINLESS.carries(Fluid.HYDROCHLORIC_ACID));
        assertEquals(BlockType.HYDROCHLORIC_ACID_CELL, Fluid.cellFor(Fluid.HYDROCHLORIC_ACID));
    }
}
