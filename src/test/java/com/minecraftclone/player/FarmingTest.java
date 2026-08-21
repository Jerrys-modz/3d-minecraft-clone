package com.minecraftclone.player;

import com.minecraftclone.world.BlockType;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class FarmingTest {

    private static final class Cells {
        final Map<Long, BlockType> m = new HashMap<>();

        static long k(int x, int y, int z) {
            return ((long) x << 42) ^ ((long) y << 21) ^ (z & 0x1FFFFF);
        }

        BlockType get(int x, int y, int z) {
            return m.getOrDefault(k(x, y, z), BlockType.AIR);
        }

        void set(int x, int y, int z, BlockType t) {
            m.put(k(x, y, z), t);
        }
    }

    @Test
    void hoeTillsSoilNotStoneOrSand() {
        assertTrue(Farming.canTill(BlockType.DIRT));
        assertTrue(Farming.canTill(BlockType.GRASS));
        assertTrue(Farming.canTill(BlockType.SWAMP_GRASS));
        assertTrue(Farming.canTill(BlockType.MYCELIUM));
        assertFalse(Farming.canTill(BlockType.STONE));
        assertFalse(Farming.canTill(BlockType.SAND));
        assertFalse(Farming.canTill(BlockType.FARMLAND));
        assertFalse(Farming.canTill(BlockType.FARMLAND_WET));
        assertFalse(Farming.canTill(null));
    }

    @Test
    void tillingDirtMakesFarmlandAndLeavesItThere() {
        Cells w = new Cells();
        w.set(0, 4, 0, BlockType.DIRT);
        assertTrue(Farming.tillAt(w::get, w::set, 0, 4, 0));
        assertEquals(BlockType.FARMLAND, w.get(0, 4, 0), "dirt must become farmland, not air");
    }

    @Test
    void tillingNextToWaterMakesWetFarmland() {
        Cells w = new Cells();
        w.set(0, 4, 0, BlockType.GRASS);
        w.set(2, 4, 0, BlockType.WATER_SOURCE);
        assertTrue(Farming.tillAt(w::get, w::set, 0, 4, 0));
        assertEquals(BlockType.FARMLAND_WET, w.get(0, 4, 0));
    }

    @Test
    void cannotTillUnderASolidFloor() {
        Cells w = new Cells();
        w.set(0, 4, 0, BlockType.DIRT);
        w.set(0, 5, 0, BlockType.STONE);
        assertFalse(Farming.tillAt(w::get, w::set, 0, 4, 0));
        assertEquals(BlockType.DIRT, w.get(0, 4, 0));
    }

    @Test
    void tallGrassOnTopDoesNotBlockTilling() {
        Cells w = new Cells();
        w.set(0, 4, 0, BlockType.GRASS);
        w.set(0, 5, 0, BlockType.TALL_GRASS);
        assertTrue(Farming.tillAt(w::get, w::set, 0, 4, 0));
        assertEquals(BlockType.FARMLAND, w.get(0, 4, 0));
        assertEquals(BlockType.TALL_GRASS, w.get(0, 5, 0));
    }

    @Test
    void boneMealAdvancesACropByOneOrTwoStages() {
        Cells w = new Cells();
        w.set(0, 4, 0, BlockType.FARMLAND_WET);
        w.set(0, 5, 0, BlockType.WHEAT_STAGE_1);
        assertTrue(Farming.applyBonemeal(w::get, w::set, 0, 5, 0, new Random(1)));
        BlockType grown = w.get(0, 5, 0);
        assertTrue(grown == BlockType.WHEAT_STAGE_2 || grown == BlockType.WHEAT_STAGE_3,
                "expected stage 2 or 3, got " + grown);
        assertEquals(BlockType.FARMLAND_WET, w.get(0, 4, 0), "farmland itself is not consumed");
    }

    @Test
    void boneMealOnFarmlandGrowsTheCropAbove() {
        Cells w = new Cells();
        w.set(0, 4, 0, BlockType.FARMLAND);
        w.set(0, 5, 0, BlockType.POTATO_CROP_1);
        assertTrue(Farming.applyBonemeal(w::get, w::set, 0, 4, 0, new Random(2)));
        assertNotEquals(BlockType.POTATO_CROP_1, w.get(0, 5, 0));
    }

    @Test
    void boneMealDoesNotConsumeOnFullyGrownCrops() {
        Cells w = new Cells();
        w.set(0, 5, 0, BlockType.WHEAT_STAGE_4);
        assertFalse(Farming.applyBonemeal(w::get, w::set, 0, 5, 0, new Random(3)));
        assertEquals(BlockType.WHEAT_STAGE_4, w.get(0, 5, 0));
    }

    @Test
    void boneMealOnGrassSproutsPlantsAbove() {
        Cells w = new Cells();
        w.set(0, 4, 0, BlockType.GRASS);
        assertTrue(Farming.applyBonemeal(w::get, w::set, 0, 4, 0, new Random(4)));
        BlockType sprout = w.get(0, 5, 0);
        assertTrue(sprout == BlockType.TALL_GRASS
                        || sprout == BlockType.FLOWER_RED
                        || sprout == BlockType.FLOWER_YELLOW,
                "center grass must sprout a plant, got " + sprout);
        assertEquals(BlockType.GRASS, w.get(0, 4, 0), "the grass block itself stays");
    }

    @Test
    void boneMealOnTallGrassFertilizesTheGrassUnderneath() {
        Cells w = new Cells();
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                w.set(dx, 4, dz, BlockType.GRASS);
            }
        }
        w.set(0, 5, 0, BlockType.TALL_GRASS);
        assertTrue(Farming.applyBonemeal(w::get, w::set, 0, 5, 0, new Random(7)));
        assertEquals(BlockType.TALL_GRASS, w.get(0, 5, 0), "existing plant is left alone");
        boolean anyNeighbour = false;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (dx == 0 && dz == 0) continue;
                BlockType above = w.get(dx, 5, dz);
                if (above == BlockType.TALL_GRASS || above == BlockType.FLOWER_RED
                        || above == BlockType.FLOWER_YELLOW) {
                    anyNeighbour = true;
                }
            }
        }
        assertTrue(anyNeighbour, "bone meal on tall grass should sprout neighbours");
    }

    @Test
    void isNearWaterAcceptsWaterVariantsAndIgnoresLava() {
        Cells w = new Cells();
        w.set(0, 4, 0, BlockType.FARMLAND);
        assertFalse(Farming.isNearWater(w::get, 0, 4, 0));
        w.set(2, 4, 0, BlockType.LAVA_SOURCE);
        assertFalse(Farming.isNearWater(w::get, 0, 4, 0), "lava must not hydrate farmland");
        w.set(3, 4, 0, BlockType.WATER_SOURCE);
        assertTrue(Farming.isNearWater(w::get, 0, 4, 0));
        Cells flow = new Cells();
        flow.set(1, 5, 0, BlockType.WATER_FLOW);
        assertTrue(Farming.isNearWater(flow::get, 0, 4, 0), "flowing water one block above hydrates");
    }

    @Test
    void harvestDropReturnsSugarCaneItself() {
        assertEquals(BlockType.SUGAR_CANE, Farming.harvestDrop(BlockType.SUGAR_CANE));
    }
}
