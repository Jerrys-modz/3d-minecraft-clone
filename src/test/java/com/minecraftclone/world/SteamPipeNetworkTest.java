package com.minecraftclone.world;

import com.minecraftclone.world.gen.WorldGenSettings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Tests the steam-pipe network resolution: a steam furnace must find a
 * boiler through any connected run of STEAM_PIPE blocks, but never through
 * gaps or unrelated blocks. Uses a real (headless) World so block placement
 * and entity lookups behave exactly like live gameplay.
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

    @Test
    void directAdjacencyStillWorks() {
        World w = newWorld();
        w.setBlock(0, 64, 1, BlockType.STEAM_BOILER);
        SteamBoilerEntity boiler = w.getOrCreateSteamBoiler(0, 64, 1);
        boiler.addFuel(BlockType.COAL, 4);
        for (int i = 0; i < 5; i++) boiler.tick(1f);

        assertNotNull(SteamFurnaceEntity.findSteamSource(w, 0, 64, 0));
    }

    @Test
    void pipeRunConnectsDistantBoiler() {
        World w = newWorld();
        // Boiler at z=6; furnace at z=0; pipes bridging z=1..5 along x=0.
        w.setBlock(0, 64, 6, BlockType.STEAM_BOILER);
        SteamBoilerEntity boiler = w.getOrCreateSteamBoiler(0, 64, 6);
        boiler.addFuel(BlockType.COAL, 8);
        for (int i = 0; i < 5; i++) boiler.tick(1f);

        for (int z = 5; z >= 1; z--) {
            w.setBlock(0, 64, z, BlockType.STEAM_PIPE);
        }

        SteamBoilerEntity found = SteamFurnaceEntity.findSteamSource(w, 0, 64, 0);
        assertNotNull(found, "a connected pipe run should conduct to the boiler");
        assertEquals(boiler.steamSeconds(), found.steamSeconds(), 0.001f);
    }

    @Test
    void gapInPipesBreaksTheNetwork() {
        World w = newWorld();
        w.setBlock(0, 64, 6, BlockType.STEAM_BOILER);
        w.getOrCreateSteamBoiler(0, 64, 6).addFuel(BlockType.COAL, 8);

        // Pipes with a one-block hole at z=3.
        for (int z = 5; z >= 1; z--) {
            if (z != 3) w.setBlock(0, 64, z, BlockType.STEAM_PIPE);
        }
        assertNull(SteamFurnaceEntity.findSteamSource(w, 0, 64, 0));
    }
}
