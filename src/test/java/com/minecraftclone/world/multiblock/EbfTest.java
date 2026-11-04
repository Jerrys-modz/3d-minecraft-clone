package com.minecraftclone.world.multiblock;

import com.minecraftclone.engine.gui.ContainerGui;
import com.minecraftclone.player.Crafting;
import com.minecraftclone.player.InventoryController;
import com.minecraftclone.player.Mortar;
import com.minecraftclone.player.Smelting;
import com.minecraftclone.world.BatteryBlockEntity;
import com.minecraftclone.world.BlastRecipes;
import com.minecraftclone.world.BlockEntities;
import com.minecraftclone.world.BlockType;
import com.minecraftclone.world.CoilTier;
import com.minecraftclone.world.DimensionType;
import com.minecraftclone.world.World;
import com.minecraftclone.world.gen.WorldGenSettings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** The Electric Blast Furnace multi-block, its coil temperatures, and the gated metal recipes. */
class EbfTest {

    @TempDir
    Path dir;

    private static final int X = 4, Y = 100, Z = 4;   // structure's min corner

    private World newWorld() {
        World w = new World(42, new WorldGenSettings(), null,
                dir.resolve("w" + System.nanoTime()), DimensionType.OVERWORLD, true);
        w.ensureChunk(0, 0);
        for (int x = 0; x < 12; x++)
            for (int y = 96; y < 108; y++)
                for (int z = 0; z < 12; z++) w.setBlock(x, y, z, BlockType.AIR);
        return w;
    }

    /** Builds the 3x4x3 EBF with the given coil; controller in the lower coil ring, south side. */
    private static void build(World w, BlockType coil) {
        for (int y = 0; y < 4; y++) {
            for (int x = 0; x < 3; x++) {
                for (int z = 0; z < 3; z++) {
                    boolean core = x == 1 && z == 1 && (y == 1 || y == 2);
                    if (core) continue;
                    BlockType b = (y == 0 || y == 3) ? BlockType.HEATPROOF_CASING : coil;
                    w.setBlock(X + x, Y + y, Z + z, b);
                }
            }
        }
        w.setBlock(X + 1, Y + 1, Z, BlockType.EBF_CONTROLLER);
    }

    private static EbfEntity controller(World w) {
        return w.blockEntityAt(X + 1, Y + 1, Z) instanceof EbfEntity e ? e : null;
    }

    private static void power(World w) {
        w.setBlock(X + 1, Y + 1, Z - 1, BlockType.BATTERY_BLOCK);
        BatteryBlockEntity b = w.getOrCreateBatteryBlock(X + 1, Y + 1, Z - 1);
        b.setSlot(BatteryBlockEntity.SLOT_BATTERIES, BlockType.MV_BATTERY, 4);
        b.chargeEU(b.capacity());
    }

    // ------------------------------------------------------------------
    // Structure
    // ------------------------------------------------------------------

    @Test
    void formsWithMatchingCoilsAndReportsTheirHeat() {
        World w = newWorld();
        build(w, BlockType.KANTHAL_COIL);
        EbfEntity ebf = controller(w);
        assertNotNull(ebf, "the EBF should form");
        assertEquals(CoilTier.KANTHAL.kelvin, ebf.heatK());
    }

    @Test
    void mixedCoilsDoNotForm() {
        World w = newWorld();
        build(w, BlockType.CUPRONICKEL_COIL);
        w.setBlock(X, Y + 2, Z, BlockType.NICHROME_COIL);
        assertNull(controller(w));
    }

    @Test
    void casingWhereACoilBelongsDoesNotForm() {
        World w = newWorld();
        build(w, BlockType.CUPRONICKEL_COIL);
        w.setBlock(X + 2, Y + 2, Z + 2, BlockType.HEATPROOF_CASING);
        assertNull(controller(w));
    }

    @Test
    void breakingTheShellDeformsIt() {
        World w = newWorld();
        build(w, BlockType.NICHROME_COIL);
        assertNotNull(controller(w));
        w.setBlock(X, Y + 3, Z, BlockType.AIR);
        assertNull(controller(w));
        w.setBlock(X, Y + 3, Z, BlockType.HEATPROOF_CASING);
        assertNotNull(controller(w), "repairing it re-forms");
    }

    // ------------------------------------------------------------------
    // Recipes
    // ------------------------------------------------------------------

    @Test
    void refractoryMetalsLeftTheFurnace() {
        assertNull(Smelting.outputFor(BlockType.CRUSHED_RUTILE));
        assertNull(Smelting.outputFor(BlockType.CRUSHED_TUNGSTEN));
        assertEquals(BlockType.TITANIUM_INGOT, BlastRecipes.outputFor(BlockType.CRUSHED_RUTILE));
        assertEquals(BlockType.TITANIUM_INGOT, BlastRecipes.outputFor(BlockType.TITANIUM_DUST));
        assertEquals(BlockType.CHROMIUM_INGOT, BlastRecipes.outputFor(BlockType.PURIFIED_CHROMIUM));
        assertEquals(BlockType.TUNGSTENSTEEL_INGOT, BlastRecipes.outputFor(BlockType.TUNGSTENSTEEL_DUST));
        assertEquals(BlockType.COPPER_INGOT, Smelting.outputFor(BlockType.CRUSHED_COPPER), "ordinary metals still smelt");
    }

    @Test
    void grindingStillKnowsTheMetal() {
        assertEquals(BlockType.CHROMIUM_DUST, Mortar.dustFor(BlockType.CRUSHED_CHROMITE));
        assertEquals(BlockType.TITANIUM_DUST, Mortar.dustFor(BlockType.CRUSHED_RUTILE));
    }

    @Test
    void temperaturesClimbByMetal() {
        assertEquals(BlastRecipes.TITANIUM_K, BlastRecipes.temperatureFor(BlockType.TITANIUM_DUST));
        assertEquals(BlastRecipes.IRIDIUM_K, BlastRecipes.temperatureFor(BlockType.IRIDIUM_DUST));
        assertEquals(BlastRecipes.TUNGSTEN_K, BlastRecipes.temperatureFor(BlockType.TUNGSTEN_DUST));
        assertTrue(CoilTier.CUPRONICKEL.kelvin >= BlastRecipes.TITANIUM_K);
        assertTrue(CoilTier.KANTHAL.kelvin >= BlastRecipes.IRIDIUM_K);
        assertTrue(CoilTier.NICHROME.kelvin >= BlastRecipes.TUNGSTEN_K);
        assertTrue(CoilTier.KANTHAL.kelvin < BlastRecipes.TUNGSTEN_K);
    }

    // ------------------------------------------------------------------
    // Running
    // ------------------------------------------------------------------

    @Test
    void blastsTitaniumWithCupronickelCoils() {
        World w = newWorld();
        build(w, BlockType.CUPRONICKEL_COIL);
        power(w);
        EbfEntity ebf = controller(w);
        ebf.setSlot(EbfEntity.SLOT_INPUT, BlockType.TITANIUM_DUST, 2);
        for (float t = 0; t < EbfEntity.BLAST_SECONDS * 2 + 1f; t += 0.05f) w.tickBlockEntities(0.05f);
        assertEquals(BlockType.TITANIUM_INGOT, ebf.typeOf(EbfEntity.SLOT_OUTPUT));
        assertEquals(2, ebf.countOf(EbfEntity.SLOT_OUTPUT));
    }

    @Test
    void tooColdForTungsten() {
        World w = newWorld();
        build(w, BlockType.KANTHAL_COIL);
        power(w);
        EbfEntity ebf = controller(w);
        ebf.setSlot(EbfEntity.SLOT_INPUT, BlockType.TUNGSTEN_DUST, 1);
        assertTrue(ebf.tooCold());
        for (int i = 0; i < 400; i++) w.tickBlockEntities(0.05f);
        assertNull(ebf.typeOf(EbfEntity.SLOT_OUTPUT));
    }

    @Test
    void contentsSurviveSaveAndReform() throws Exception {
        World w = newWorld();
        build(w, BlockType.NICHROME_COIL);
        EbfEntity ebf = controller(w);
        ebf.setSlot(EbfEntity.SLOT_INPUT, BlockType.TUNGSTEN_DUST, 5);
        var buf = new java.io.ByteArrayOutputStream();
        ebf.writeTo(new java.io.DataOutputStream(buf));
        EbfEntity loaded = (EbfEntity) BlockEntities.create(EbfEntity.TYPE);
        loaded.readFrom(new java.io.DataInputStream(new java.io.ByteArrayInputStream(buf.toByteArray())));
        assertEquals(5, loaded.countOf(EbfEntity.SLOT_INPUT));
        assertEquals(0, loaded.heatK(), "unformed until the structure is re-detected");
    }

    @Test
    void recipesAndShiftClick() {
        BlockType X_ = BlockType.COPPER_INGOT, Ni = BlockType.NICKEL_INGOT, N = null;
        assertEquals(BlockType.CUPRONICKEL_COIL, Crafting.match3x3(new BlockType[]{X_, Ni, X_, Ni, N, Ni, X_, Ni, X_}).output());
        BlockType V = BlockType.INVAR_PLATE, S = BlockType.STEEL_CASING;
        assertEquals(BlockType.HEATPROOF_CASING, Crafting.match3x3(new BlockType[]{V, V, V, V, S, V, V, V, V}).output());
        var ts = Crafting.match2x2(new BlockType[]{BlockType.TUNGSTEN_DUST, BlockType.STEEL_DUST, N, N});
        assertEquals(BlockType.TUNGSTENSTEEL_DUST, ts.output());
        assertEquals(ContainerGui.CONTAINER_START + EbfEntity.SLOT_INPUT,
                InventoryController.quickMoveFurnaceTarget(new EbfEntity(), BlockType.TITANIUM_DUST));
        assertEquals(-1, InventoryController.quickMoveFurnaceTarget(new EbfEntity(), BlockType.COAL));
    }
}
