package com.minecraftclone.world;

import com.minecraftclone.world.gen.TerrainGenerator.Biome;
import com.minecraftclone.Difficulty;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The surface-mob spawn picker's biome gating (see
 * {@link World#pickSurfaceMobType}): wild predators are biome-tied so the better
 * fur pelts are harder to come by.
 */
class SurfaceMobSpawnTest {

    private static final Set<Mob.Type> COMMON = Set.of(
            Mob.Type.PIG, Mob.Type.COW, Mob.Type.SHEEP, Mob.Type.ZOMBIE, Mob.Type.SKELETON);

    @Test
    void wolvesOnlyAppearInWoodedBiomes() {
        // Spread the seeds widely: Java's Random gives near-identical first rolls
        // for consecutive small seeds, which would never cross the thresholds.
        Set<Mob.Type> woods = new HashSet<>();
        Set<Mob.Type> elsewhere = new HashSet<>();
        for (int i = 0; i < 400; i++) {
            long seed = (long) i * 0x9E3779B97F4A7C15L;
            woods.add(World.pickSurfaceMobType(new Random(seed), Biome.FOREST));
            elsewhere.add(World.pickSurfaceMobType(new Random(seed), Biome.PLAINS));
        }
        assertTrue(woods.contains(Mob.Type.WOLF), "wolves should turn up in forests");
        assertTrue(!elsewhere.contains(Mob.Type.WOLF), "wolves never spawn in the plains");
        assertTrue(!elsewhere.contains(Mob.Type.POLAR_BEAR), "bears never spawn in the plains");
    }

    @Test
    void polarBearsOnlyAppearInFrozenBiomes() {
        Set<Mob.Type> frozen = new HashSet<>();
        Set<Mob.Type> elsewhere = new HashSet<>();
        for (int i = 0; i < 800; i++) {
            long seed = (long) i * 0x9E3779B97F4A7C15L;
            frozen.add(World.pickSurfaceMobType(new Random(seed), Biome.TUNDRA));
            elsewhere.add(World.pickSurfaceMobType(new Random(seed), Biome.DESERT));
        }
        assertTrue(frozen.contains(Mob.Type.POLAR_BEAR), "bears should turn up in the frozen wastes");
        assertTrue(!elsewhere.contains(Mob.Type.POLAR_BEAR), "bears never spawn in the desert");
    }

    @Test
    void commonPoolStillFillsMostSlots() {
        for (int seed = 0; seed < 100; seed++) {
            Mob.Type type = World.pickSurfaceMobType(new Random(seed), Biome.PLAINS);
            assertTrue(COMMON.contains(type), "plains spawns stay in the common pool: " + type);
        }
    }

    @Test
    void predatorsDropTheirPelts() {
        assertEquals(BlockType.WOLF_PELT, new Mob(Mob.Type.WOLF, 0, 1, 0).dropType());
        assertEquals(BlockType.BEAR_HIDE, new Mob(Mob.Type.POLAR_BEAR, 0, 1, 0).dropType());
    }

    @Test
    void peacefulSurfaceSpawnsAreOnlyPassives() {
        Set<Mob.Type> types = new HashSet<>();
        for (int i = 0; i < 400; i++) {
            long seed = (long) i * 0x9E3779B97F4A7C15L;
            types.add(World.pickSurfaceMobType(new Random(seed), Biome.FOREST, Difficulty.PEACEFUL));
            types.add(World.pickSurfaceMobType(new Random(seed), Biome.TUNDRA, Difficulty.PEACEFUL));
            types.add(World.pickSurfaceMobType(new Random(seed), Biome.PLAINS, Difficulty.PEACEFUL));
        }
        for (Mob.Type type : types) {
            assertTrue(!type.hostile, "Peaceful must not pick hostiles: " + type);
        }
        assertTrue(types.contains(Mob.Type.PIG) || types.contains(Mob.Type.COW) || types.contains(Mob.Type.SHEEP));
    }
}
