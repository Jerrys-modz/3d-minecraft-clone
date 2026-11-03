package com.minecraftclone.world;

import com.minecraftclone.engine.gui.ContainerGui;
import com.minecraftclone.player.Crafting;
import com.minecraftclone.player.Inventory;
import com.minecraftclone.world.gen.WorldGenSettings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the LV Macerator, the shared {@link EuNetwork} lookups, the
 * placement hook that gives Electric Age blocks their entities, and the
 * far-from-origin block-key decoding fix.
 */
class ElectricMaceratorTest {

    @TempDir
    Path dir;

    private World newWorld() {
        World world = new World(42, new WorldGenSettings(), null,
                dir.resolve("w" + System.nanoTime()), DimensionType.OVERWORLD, true);
        world.ensureChunk(0, 0);
        return world;
    }

    private static BatteryBlockEntity chargedBattery(float eu) {
        BatteryBlockEntity bat = new BatteryBlockEntity();
        bat.chargeEU(eu);
        return bat;
    }

    // ------------------------------------------------------------------
    // Recipes
    // ------------------------------------------------------------------

    @Test
    void oresAndCrushedOresYieldDoubleDust() {
        assertEquals(BlockType.IRON_DUST, ElectricMaceratorEntity.outputFor(BlockType.IRON_ORE));
        assertEquals(2, ElectricMaceratorEntity.yieldFor(BlockType.IRON_ORE));
        assertEquals(BlockType.COPPER_DUST, ElectricMaceratorEntity.outputFor(BlockType.CRUSHED_COPPER));
        assertEquals(2, ElectricMaceratorEntity.yieldFor(BlockType.CRUSHED_COPPER));
    }

    @Test
    void coalGrindsOneToOneSoItCannotBeMultiplied() {
        assertEquals(BlockType.COAL_DUST, ElectricMaceratorEntity.outputFor(BlockType.COAL));
        assertEquals(1, ElectricMaceratorEntity.yieldFor(BlockType.COAL));
    }

    @Test
    void nonGrindableItemsAreRejected() {
        assertNull(ElectricMaceratorEntity.outputFor(BlockType.DIRT));
        assertNull(ElectricMaceratorEntity.outputFor(BlockType.IRON_DUST));
        ElectricMaceratorEntity mac = new ElectricMaceratorEntity();
        assertEquals(5, mac.add(BlockType.DIRT, 5), "dirt must not enter the input slot");
        assertEquals(0, mac.add(BlockType.IRON_ORE, 5));
        assertEquals(5, mac.countOf(ElectricMaceratorEntity.SLOT_INPUT));
    }

    @Test
    void craftingRecipeUsesSteelCasing() {
        BlockType X = BlockType.COPPER_INGOT, D = BlockType.DIAMOND, C = BlockType.STEEL_CASING;
        var result = Crafting.match3x3(new BlockType[]{
                X, D, X,
                X, C, X,
                X, X, X});
        assertNotNull(result);
        assertEquals(BlockType.ELECTRIC_MACERATOR, result.output());
    }

    // ------------------------------------------------------------------
    // Simulation
    // ------------------------------------------------------------------

    @Test
    void grindsOreIntoDoubleDustOnEu() {
        ElectricMaceratorEntity mac = new ElectricMaceratorEntity();
        BatteryBlockEntity bat = chargedBattery(BatteryBlockEntity.MAX_EU);
        mac.euSource = bat;
        mac.setSlot(ElectricMaceratorEntity.SLOT_INPUT, BlockType.IRON_ORE, 3);

        for (int i = 0; i < 400; i++) mac.tick(0.05f);   // 20 s, plenty for 3 ops

        assertNull(mac.typeOf(ElectricMaceratorEntity.SLOT_INPUT));
        assertEquals(BlockType.IRON_DUST, mac.typeOf(ElectricMaceratorEntity.SLOT_OUTPUT));
        assertEquals(6, mac.countOf(ElectricMaceratorEntity.SLOT_OUTPUT));
        assertEquals(BatteryBlockEntity.MAX_EU - 3 * ElectricMaceratorEntity.EU_PER_OPERATION,
                bat.euStored(),
                // The tick that finishes the last item may overshoot by up to one tick's draw.
                ElectricMaceratorEntity.EU_PER_OPERATION / ElectricMaceratorEntity.GRIND_SECONDS * 0.05f + 0.01f);
    }

    @Test
    void noPowerMeansNoProgress() {
        ElectricMaceratorEntity mac = new ElectricMaceratorEntity();
        mac.euSource = new BatteryBlockEntity(); // empty
        mac.setSlot(ElectricMaceratorEntity.SLOT_INPUT, BlockType.IRON_ORE, 1);
        mac.tick(10f);
        assertEquals(0f, mac.progressFraction());
        assertFalse(mac.isActive());
        assertEquals(1, mac.countOf(ElectricMaceratorEntity.SLOT_INPUT));
    }

    @Test
    void fullOutputStopsGrindingWithoutWastingEu() {
        ElectricMaceratorEntity mac = new ElectricMaceratorEntity();
        BatteryBlockEntity bat = chargedBattery(1000f);
        mac.euSource = bat;
        int max = Inventory.maxStack(BlockType.IRON_DUST);
        mac.setSlot(ElectricMaceratorEntity.SLOT_INPUT, BlockType.IRON_ORE, 1);
        mac.setSlot(ElectricMaceratorEntity.SLOT_OUTPUT, BlockType.IRON_DUST, max - 1);
        assertFalse(mac.canGrind(), "a 2-dust result would overflow the stack");
        mac.tick(5f);
        assertEquals(1000f, bat.euStored(), 0.001f);
        assertEquals(max - 1, mac.countOf(ElectricMaceratorEntity.SLOT_OUTPUT));
    }

    @Test
    void persistenceRoundTrip() throws Exception {
        ElectricMaceratorEntity mac = new ElectricMaceratorEntity();
        mac.setSlot(ElectricMaceratorEntity.SLOT_INPUT, BlockType.CRUSHED_TIN, 7);
        mac.setSlot(ElectricMaceratorEntity.SLOT_OUTPUT, BlockType.TIN_DUST, 4);
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        mac.writeTo(new DataOutputStream(buf));

        ElectricMaceratorEntity loaded = new ElectricMaceratorEntity();
        loaded.readFrom(new DataInputStream(new ByteArrayInputStream(buf.toByteArray())));
        assertEquals(BlockType.CRUSHED_TIN, loaded.typeOf(ElectricMaceratorEntity.SLOT_INPUT));
        assertEquals(7, loaded.countOf(ElectricMaceratorEntity.SLOT_INPUT));
        assertEquals(BlockType.TIN_DUST, loaded.typeOf(ElectricMaceratorEntity.SLOT_OUTPUT));
        assertEquals(4, loaded.countOf(ElectricMaceratorEntity.SLOT_OUTPUT));
    }

    @Test
    void registeredForSaveLoad() {
        assertTrue(BlockEntities.isRegistered(ElectricMaceratorEntity.TYPE));
    }

    // ------------------------------------------------------------------
    // World integration
    // ------------------------------------------------------------------

    @Test
    void placingElectricBlocksCreatesTheirEntities() {
        World world = newWorld();
        world.setBlock(0, 64, 0, BlockType.BATTERY_BLOCK);
        world.setBlock(1, 64, 0, BlockType.COAL_GENERATOR);
        world.setBlock(2, 64, 0, BlockType.ELECTRIC_FURNACE);
        world.setBlock(3, 64, 0, BlockType.ELECTRIC_MACERATOR);
        assertInstanceOf(BatteryBlockEntity.class, world.blockEntityAt(0, 64, 0));
        assertInstanceOf(CoalGeneratorEntity.class, world.blockEntityAt(1, 64, 0));
        assertInstanceOf(ElectricFurnaceEntity.class, world.blockEntityAt(2, 64, 0));
        assertInstanceOf(ElectricMaceratorEntity.class, world.blockEntityAt(3, 64, 0));
    }

    @Test
    void maceratorPoweredThroughCableFromNeverClickedBattery() {
        World world = newWorld();
        // Battery placed but never opened: it must still be found over the cable.
        world.setBlock(0, 64, 2, BlockType.BATTERY_BLOCK);
        ((BatteryBlockEntity) world.blockEntityAt(0, 64, 2)).chargeEU(2000f);
        world.setBlock(0, 64, 1, BlockType.COPPER_CABLE);
        world.setBlock(0, 64, 0, BlockType.ELECTRIC_MACERATOR);
        ElectricMaceratorEntity mac = (ElectricMaceratorEntity) world.blockEntityAt(0, 64, 0);
        mac.setSlot(ElectricMaceratorEntity.SLOT_INPUT, BlockType.IRON_ORE, 1);

        for (int i = 0; i < 200; i++) world.tickBlockEntities(0.05f);

        assertEquals(2, mac.countOf(ElectricMaceratorEntity.SLOT_OUTPUT));
    }

    @Test
    void euNetworkPrefersChargedSourceOverEmptyNeighbour() {
        World world = newWorld();
        world.setBlock(1, 64, 0, BlockType.COAL_GENERATOR);         // empty, adjacent
        world.setBlock(0, 64, 1, BlockType.COPPER_CABLE);
        world.setBlock(0, 64, 2, BlockType.BATTERY_BLOCK);
        BatteryBlockEntity bat = world.getOrCreateBatteryBlock(0, 64, 2);
        bat.chargeEU(100f);
        assertSame(bat, EuNetwork.findSource(world, 0, 64, 0));
        assertEquals(CableTier.COPPER.throughput, EuNetwork.drawRate(world, 0, 64, 0));
    }

    @Test
    void blockEntitiesFarFromOriginSurviveTicking() {
        World world = newWorld();
        int x = 1500, z = -1500;
        world.ensureChunk(World.worldToChunk(x), World.worldToChunk(z));
        world.setBlock(x, 64, z, BlockType.FURNACE);
        Furnace furnace = world.getOrCreateFurnace(x, 64, z);
        furnace.setSlot(Furnace.SLOT_INPUT, BlockType.IRON_ORE, 3);

        world.tickBlockEntities(0.05f);

        assertSame(furnace, world.blockEntityAt(x, 64, z),
                "the block key must decode to the real coordinate, not prune the entity");
    }

    // ------------------------------------------------------------------
    // Shift-click routing
    // ------------------------------------------------------------------

    @Test
    void shiftClickRoutesToTheRightMachineSlot() {
        int start = ContainerGui.CONTAINER_START;
        // Coal into a plain furnace goes to fuel; into a steam macerator it has nowhere to go.
        assertEquals(start + Furnace.SLOT_FUEL,
                com.minecraftclone.player.InventoryController.quickMoveFurnaceTarget(new Furnace(), BlockType.COAL));
        assertEquals(-1,
                com.minecraftclone.player.InventoryController.quickMoveFurnaceTarget(new SteamMaceratorEntity(), BlockType.COAL));
        assertEquals(-1,
                com.minecraftclone.player.InventoryController.quickMoveFurnaceTarget(new ElectricFurnaceEntity(), BlockType.COAL));
        // Generator: fuel only, into its single slot.
        assertEquals(start + CoalGeneratorEntity.SLOT_FUEL,
                com.minecraftclone.player.InventoryController.quickMoveFurnaceTarget(new CoalGeneratorEntity(), BlockType.COAL));
        assertEquals(-1,
                com.minecraftclone.player.InventoryController.quickMoveFurnaceTarget(new CoalGeneratorEntity(), BlockType.IRON_ORE));
        // Macerator: grindables into the input slot.
        assertEquals(start + ElectricMaceratorEntity.SLOT_INPUT,
                com.minecraftclone.player.InventoryController.quickMoveFurnaceTarget(new ElectricMaceratorEntity(), BlockType.IRON_ORE));
    }
}
