package com.minecraftclone.world;

import com.minecraftclone.world.gen.WorldGenSettings;
import com.minecraftclone.world.pipes.PipeType;
import com.minecraftclone.world.SteamPipeTier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests steam-pipe network resolution end to end: a steam furnace must find
 * a boiler through any connected run of STEAM_PIPE blocks, but never through
 * gaps or unrelated blocks. Uses a real (headless) World so block placement,
 * the pipe network cache and entity lookups behave exactly like live gameplay.
 */
class SteamPipeNetworkTest {

    @TempDir
    Path dir;

    private World newWorld() {
        WorldGenSettings s = new WorldGenSettings();
        World w = new World(42, s, null, dir.resolve("w" + System.nanoTime()), DimensionType.OVERWORLD, true);
        // Load the spawn chunk so setBlock actually lands.
        w.ensureChunk(0, 0);
        return w;
    }

    /** Builds a fueled boiler at (x, z) on y=64 and runs it for a few seconds. */
    private SteamBoilerEntity placeBoiler(World w, int x, int z) {
        w.setBlock(x, 64, z, BlockType.STEAM_BOILER);
        SteamBoilerEntity boiler = w.getOrCreateSteamBoiler(x, 64, z);
        boiler.addFuel(BlockType.COAL, 8);
        for (int i = 0; i < 5; i++) boiler.tick(1f);
        return boiler;
    }

    @Test
    void directAdjacencyStillWorks() {
        World w = newWorld();
        SteamBoilerEntity boiler = placeBoiler(w, 0, 1);

        SteamFurnaceEntity sf = w.getOrCreateSteamFurnace(0, 64, 0);
        sf.setSlot(SteamFurnaceEntity.SLOT_INPUT, BlockType.IRON_ORE, 3);

        for (int i = 0; i < 120; i++) {
            boiler.tick(0.25f);
            sf.tick(0.25f);
        }
        assertEquals(BlockType.IRON_INGOT, sf.typeOf(SteamFurnaceEntity.SLOT_OUTPUT));
        assertEquals(3, sf.countOf(SteamFurnaceEntity.SLOT_OUTPUT));
    }

    @Test
    void pipeRunConnectsDistantBoiler() {
        World w = newWorld();
        SteamBoilerEntity boiler = placeBoiler(w, 0, 6);

        for (int z = 5; z >= 1; z--) {
            w.setBlock(0, 64, z, BlockType.STEAM_PIPE_BRONZE);
        }
        SteamFurnaceEntity sf = w.getOrCreateSteamFurnace(0, 64, 0);
        sf.attach(0, 64, 0, w);
        sf.setSlot(SteamFurnaceEntity.SLOT_INPUT, BlockType.IRON_ORE, 3);

        // Tick both: the boiler converts its fuel into steam while the
        // furnace draws it through the pipes.
        for (int i = 0; i < 120; i++) {
            boiler.tick(0.25f);
            sf.tick(0.25f);
        }
        assertEquals(3, sf.countOf(SteamFurnaceEntity.SLOT_OUTPUT), "pipe run should conduct steam");
    }

    @Test
    void gapInPipesBreaksTheNetwork() {
        World w = newWorld();
        placeBoiler(w, 0, 6);

        // Pipes with a one-block hole at z=3.
        for (int z = 5; z >= 1; z--) {
            if (z != 3) w.setBlock(0, 64, z, BlockType.STEAM_PIPE_BRONZE);
        }
        SteamFurnaceEntity sf = w.getOrCreateSteamFurnace(0, 64, 0);
        sf.attach(0, 64, 0, w);
        sf.setSlot(SteamFurnaceEntity.SLOT_INPUT, BlockType.IRON_ORE, 3);

        for (int i = 0; i < 60; i++) sf.tick(0.25f);
        assertEquals(0, sf.countOf(SteamFurnaceEntity.SLOT_OUTPUT));
    }

    /** Runs a fueled boiler + furnace joined by a 5-pipe run of the given tier; returns output item count. */
    private int runFurnaceWithPipes(BlockType pipeTier) {
        World w = newWorld();
        placeBoiler(w, 0, 6);
        for (int z = 5; z >= 1; z--) {
            w.setBlock(0, 64, z, pipeTier);
        }
        SteamFurnaceEntity sf = w.getOrCreateSteamFurnace(0, 64, 0);
        sf.attach(0, 64, 0, w);
        sf.setSlot(SteamFurnaceEntity.SLOT_INPUT, BlockType.IRON_ORE, 99);

        SteamBoilerEntity boiler = w.getOrCreateSteamBoiler(0, 64, 6);
        boiler.addFuel(BlockType.COAL, 64);

        for (int i = 0; i < 160; i++) {
            boiler.tick(0.25f);
            sf.tick(0.25f);
        }
        return sf.countOf(SteamFurnaceEntity.SLOT_OUTPUT);
    }

    @Test
    void networkCacheInvalidatesWhenPipesChange() {
        World w = newWorld();
        placeBoiler(w, 0, 6);

        // No pipes yet: no network at this position.
        assertNull(w.pipeNetworks().networkAt(PipeType.STEAM, 0, 64, 2));

        // Lay a pipe at (0,64,2): now the query resolves a one-cell network.
        w.setBlock(0, 64, 2, BlockType.STEAM_PIPE_BRONZE);
        var net = w.pipeNetworks().networkAt(PipeType.STEAM, 0, 64, 2);
        assertNotNull(net, "placing a pipe should expose a network");
        assertEquals(1, net.cells.size());

        // Break it again: the stale cached network must be dropped.
        w.setBlock(0, 64, 2, BlockType.AIR);
        assertNull(w.pipeNetworks().networkAt(PipeType.STEAM, 0, 64, 2));
    }

    @Test
    void woodPipeRunThrottlesComparedToBronze() {
        // Identical setups except pipe tier: after the same number of ticks,
        // the bronze-fed furnace must have strictly more output than the
        // wooden one (wood throttles to half rate).
        int bronzeOut = runFurnaceWithPipes(BlockType.STEAM_PIPE_BRONZE);
        int woodOut = runFurnaceWithPipes(BlockType.STEAM_PIPE_WOOD);

        assertTrue(bronzeOut > 0, "bronze pipes should smelt");
        assertTrue(woodOut > 0, "wood pipes should still conduct some steam");
        assertTrue(bronzeOut > woodOut,
                "bronze (" + bronzeOut + ") should out-produce wood (" + woodOut + ")");
    }

    @Test
    void oneWoodSegmentDowngradesWholeNetwork() {
        World w = newWorld();
        placeBoiler(w, 0, 6);
        // Mostly bronze run with ONE wooden segment in the middle.
        for (int z = 5; z >= 1; z--) {
            BlockType pipe = z == 3 ? BlockType.STEAM_PIPE_WOOD : BlockType.STEAM_PIPE_BRONZE;
            w.setBlock(0, 64, z, pipe);
        }
        SteamFurnaceEntity sf = w.getOrCreateSteamFurnace(0, 64, 0);
        sf.attach(0, 64, 0, w);
        sf.setSlot(SteamFurnaceEntity.SLOT_INPUT, BlockType.IRON_ORE, 99);

        float melted = 0f;
        for (int i = 0; i < 80; i++) {
            sf.tick(0.25f);
            melted = sf.progressFraction();
            if (i % 8 == 0) boilerRefTick(w);
        }
        // Weakest-link: even though only 1 of 5 segments is wood, flow runs at
        // wood throughput - verify by checking the network reports WOOD tier.
        var net = w.pipeNetworks().networkAt(PipeType.STEAM, 0, 64, 4);
        assertEquals(SteamPipeTier.WOOD, net.minTier);
    }

    private void boilerRefTick(World w) {
        // no-op placeholder; boiler ticks happen in the main loop of real use
    }
}
