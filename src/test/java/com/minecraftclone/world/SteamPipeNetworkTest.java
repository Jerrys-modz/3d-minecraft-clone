package com.minecraftclone.world;

import com.minecraftclone.world.gen.WorldGenSettings;
import com.minecraftclone.world.pipes.PipeType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

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
            w.setBlock(0, 64, z, BlockType.STEAM_PIPE);
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
            if (z != 3) w.setBlock(0, 64, z, BlockType.STEAM_PIPE);
        }
        SteamFurnaceEntity sf = w.getOrCreateSteamFurnace(0, 64, 0);
        sf.attach(0, 64, 0, w);
        sf.setSlot(SteamFurnaceEntity.SLOT_INPUT, BlockType.IRON_ORE, 3);

        for (int i = 0; i < 60; i++) sf.tick(0.25f);
        assertEquals(0, sf.countOf(SteamFurnaceEntity.SLOT_OUTPUT));
    }

    @Test
    void networkCacheInvalidatesWhenPipesChange() {
        World w = newWorld();
        placeBoiler(w, 0, 6);

        // No pipes yet: no network at this position.
        assertNull(w.pipeNetworks().networkAt(PipeType.STEAM, 0, 64, 2));

        // Lay a pipe at (0,64,2): now the query resolves a one-cell network.
        w.setBlock(0, 64, 2, BlockType.STEAM_PIPE);
        var net = w.pipeNetworks().networkAt(PipeType.STEAM, 0, 64, 2);
        assertNotNull(net, "placing a pipe should expose a network");
        assertEquals(1, net.cells.size());

        // Break it again: the stale cached network must be dropped.
        w.setBlock(0, 64, 2, BlockType.AIR);
        assertNull(w.pipeNetworks().networkAt(PipeType.STEAM, 0, 64, 2));
    }
}
