package com.minecraftclone.world.gen;

import com.minecraftclone.world.Chunk;

/**
 * A procedural terrain generator for one {@link com.minecraftclone.world.DimensionType}.
 * Implementations must be deterministic from the seed they're constructed with, so
 * an unmodified chunk can be regenerated (rather than saved) on reload.
 */
public interface WorldGenerator {

    /** Fills {@code chunk} with freshly generated terrain. */
    void generate(Chunk chunk);

    /** The default surface level used when no blocks exist at a column yet (sea level in the overworld, void level elsewhere). */
    int seaLevel();

    /** The overworld biome at a world column, for the F3 debug overlay. Non-overworld dimensions return their dimension's stand-in biome. */
    TerrainGenerator.Biome biomeAtWorld(int wx, int wz);
}
