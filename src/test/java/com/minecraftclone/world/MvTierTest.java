package com.minecraftclone.world;

import com.minecraftclone.engine.gui.ContainerGui;
import com.minecraftclone.player.Crafting;
import com.minecraftclone.player.InventoryController;
import com.minecraftclone.world.gen.WorldGenSettings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** Silicon chain, integrated circuit, MV battery and the MV machine upgrade. */
class MvTierTest {

    @TempDir
    Path dir;

    private static final int Y = 100;

    private World newWorld() {
        World world = new World(42, new WorldGenSettings(), null,
                dir.resolve("w" + System.nanoTime()), DimensionType.OVERWORLD, true);
        world.ensureChunk(0, 0);
        for (int x = 0; x <= 4; x++)
            for (int y = 97; y <= 103; y++)
                for (int z = 0; z < 8; z++) world.setBlock(x, y, z, BlockType.AIR);
        return world;
    }

    private static BatteryBlockEntity bigBattery() {
        BatteryBlockEntity b = new BatteryBlockEntity();
        b.setSlot(BatteryBlockEntity.SLOT_BATTERIES, BlockType.MV_BATTERY, 2);
        b.chargeEU(b.capacity());
        return b;
    }

    // ------------------------------------------------------------------
    // Silicon chain
    // ------------------------------------------------------------------

    @Test
    void sandToWafers() {
        assertEquals(BlockType.SILICON_DUST, ElectrolyzerEntity.recipeFor(BlockType.SAND).primary());
        assertEquals(BlockType.SILICON_BOULE, PrimitiveBlastFurnaceEntity.outputFor(BlockType.SILICON_DUST));
        assertEquals(BlockType.SILICON_WAFER, ElectricCompressorEntity.outputFor(BlockType.SILICON_BOULE));

        ElectricCompressorEntity c = new ElectricCompressorEntity();
        c.euSource = bigBattery();
        c.setSlot(ElectricMachineEntity.SLOT_INPUT, BlockType.SILICON_BOULE, 2);
        for (int i = 0; i < 400; i++) c.tick(0.05f);
        assertEquals(BlockType.SILICON_WAFER, c.typeOf(ElectricMachineEntity.SLOT_OUTPUT));
        assertEquals(2 * ElectricCompressorEntity.WAFERS_PER_BOULE, c.countOf(ElectricMachineEntity.SLOT_OUTPUT));
    }

    @Test
    void lithiumFromTheElectrolyzer() {
        assertEquals(BlockType.LITHIUM_DUST, ElectrolyzerEntity.recipeFor(BlockType.CRUSHED_LITHIUM).primary());
        ElectrolyzerEntity.Split lep = ElectrolyzerEntity.recipeFor(BlockType.CRUSHED_LEPIDOLITE);
        assertEquals(BlockType.LITHIUM_DUST, lep.primary());
        assertEquals(BlockType.ALUMINUM_DUST, lep.secondary());
    }

    @Test
    void recipes() {
        BlockType W = BlockType.SILICON_WAFER, E = BlockType.ELECTRUM_PLATE, C = BlockType.COPPER_CABLE,
                G = BlockType.GOLD_CABLE, L = BlockType.LITHIUM_DUST, S = BlockType.STAINLESS_STEEL_PLATE,
                I = BlockType.INTEGRATED_CIRCUIT, M = BlockType.MV_CASING, N = null;
        assertEquals(BlockType.INTEGRATED_CIRCUIT,
                Crafting.match3x3(new BlockType[]{C, E, C, E, W, E, C, E, C}).output());
        assertEquals(BlockType.MV_BATTERY,
                Crafting.match3x3(new BlockType[]{N, G, N, E, L, E, E, L, E}).output());
        assertEquals(BlockType.MV_UPGRADE,
                Crafting.match3x3(new BlockType[]{S, I, S, I, M, I, S, I, S}).output());
    }

    // ------------------------------------------------------------------
    // MV battery
    // ------------------------------------------------------------------

    @Test
    void mvBatteriesHoldFourTimesAsMuch() {
        BatteryBlockEntity b = new BatteryBlockEntity();
        b.setSlot(BatteryBlockEntity.SLOT_BATTERIES, BlockType.MV_BATTERY, 2);
        assertEquals(BatteryBlockEntity.MAX_EU + 2 * BatteryBlockEntity.EU_PER_MV_BATTERY, b.capacity());
        assertEquals(4f, BatteryBlockEntity.EU_PER_MV_BATTERY / BatteryBlockEntity.EU_PER_BATTERY);
        assertEquals(0, b.add(BlockType.MV_BATTERY, 1));
        assertEquals(1, b.add(BlockType.LV_BATTERY, 1), "one battery kind per block");
        assertEquals(ContainerGui.CONTAINER_START + BatteryBlockEntity.SLOT_BATTERIES,
                InventoryController.quickMoveFurnaceTarget(b, BlockType.MV_BATTERY));
    }

    // ------------------------------------------------------------------
    // MV upgrade
    // ------------------------------------------------------------------

    @Test
    void upgradeDoublesSpeedAtTheSameEuPerItem() {
        ElectricMaceratorEntity lv = new ElectricMaceratorEntity();
        ElectricMaceratorEntity mv = new ElectricMaceratorEntity();
        assertTrue(mv.installMvUpgrade());
        assertFalse(mv.installMvUpgrade(), "only once");
        BatteryBlockEntity lvBat = bigBattery(), mvBat = bigBattery();
        lv.euSource = lvBat;
        mv.euSource = mvBat;
        lv.setSlot(ElectricMachineEntity.SLOT_INPUT, BlockType.IRON_ORE, 64);
        mv.setSlot(ElectricMachineEntity.SLOT_INPUT, BlockType.IRON_ORE, 64);
        float seconds = ElectricMaceratorEntity.GRIND_SECONDS * 4;
        for (float t = 0; t < seconds - 1e-3f; t += 0.05f) {
            lv.tick(0.05f);
            mv.tick(0.05f);
        }
        int lvDone = 64 - lv.countOf(ElectricMachineEntity.SLOT_INPUT);
        int mvDone = 64 - mv.countOf(ElectricMachineEntity.SLOT_INPUT);
        assertTrue(mvDone >= 2 * lvDone - 1 && mvDone >= 7, "lv=" + lvDone + " mv=" + mvDone);
        float lvPerItem = (lvBat.capacity() - lvBat.euStored()) / Math.max(1, lvDone);
        float mvPerItem = (mvBat.capacity() - mvBat.euStored()) / Math.max(1, mvDone);
        assertEquals(lvPerItem, mvPerItem, ElectricMaceratorEntity.EU_PER_OPERATION * 0.2f);
    }

    @Test
    void reactorIsAlreadyMv() {
        ChemicalReactorEntity r = new ChemicalReactorEntity();
        assertTrue(r.isMv());
        assertFalse(r.installMvUpgrade());
        assertFalse(r.isMvUpgraded());
    }

    @Test
    void upgradedMachineSurvivesGoldCable() {
        World w = newWorld();
        w.setBlock(0, Y, 1, BlockType.GOLD_CABLE);
        w.setBlock(0, Y, 2, BlockType.BATTERY_BLOCK);
        w.getOrCreateBatteryBlock(0, Y, 2).chargeEU(BatteryBlockEntity.MAX_EU);
        w.setBlock(0, Y, 0, BlockType.ELECTRIC_MACERATOR);
        ElectricMaceratorEntity m = (ElectricMaceratorEntity) w.blockEntityAt(0, Y, 0);
        m.installMvUpgrade();
        m.setSlot(ElectricMachineEntity.SLOT_INPUT, BlockType.IRON_ORE, 1);
        for (int i = 0; i < 200; i++) w.tickBlockEntities(0.05f);
        assertEquals(BlockType.ELECTRIC_MACERATOR, w.getBlock(0, Y, 0));
        assertEquals(2, m.countOf(ElectricMachineEntity.SLOT_OUTPUT));
    }

    @Test
    void upgradeIsSavedAndOldSavesLoadAsLv() throws Exception {
        ElectricMaceratorEntity m = new ElectricMaceratorEntity();
        m.installMvUpgrade();
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        m.writeTo(new DataOutputStream(buf));
        ElectricMaceratorEntity loaded = new ElectricMaceratorEntity();
        loaded.readFrom(new DataInputStream(new ByteArrayInputStream(buf.toByteArray())));
        assertTrue(loaded.isMvUpgraded());

        ByteArrayOutputStream v2 = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(v2);
        out.writeByte(2);
        for (int i = 0; i < 3; i++) { out.writeShort(0); out.writeInt(0); }
        out.writeFloat(0f);
        out.writeFloat(0.5f);
        ElectricMaceratorEntity old = new ElectricMaceratorEntity();
        old.readFrom(new DataInputStream(new ByteArrayInputStream(v2.toByteArray())));
        assertFalse(old.isMvUpgraded());
    }
}
