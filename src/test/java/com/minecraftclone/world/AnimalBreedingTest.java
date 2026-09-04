package com.minecraftclone.world;

import com.minecraftclone.world.gen.WorldGenSettings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the Animal Breeding system.
 *
 * <p>Covered scenarios:
 * <ul>
 *   <li>Only PIG / COW / SHEEP are breedable types.</li>
 *   <li>Valid and invalid food per mob type.</li>
 *   <li>Love-mode timer countdown via {@link Mob#update(float)}.</li>
 *   <li>Breeding cooldown prevents immediate re-feeding.</li>
 *   <li>Baby flag is set and cleared after the grow-up timer elapses.</li>
 *   <li>{@link World#tickBreeding(Random)} spawns a baby between two nearby
 *       love-mode adults of the same type and applies cooldowns to both parents.</li>
 *   <li>No baby is produced for mismatched types or partners that are too far apart.</li>
 * </ul>
 */
class AnimalBreedingTest {

    @TempDir
    Path tmp;

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /** Creates a mob at the origin for easy testing. */
    private static Mob mob(Mob.Type type) {
        return new Mob(type, 0f, 1f, 0f);
    }

    /** Creates a minimal World in the temp directory. */
    private World world() {
        return new World(12345L, new WorldGenSettings(), null,
                tmp.resolve("w" + System.nanoTime()), DimensionType.OVERWORLD, true);
    }

    /** Advances a mob's breeding timers by {@code seconds} without running AI. */
    private static void tick(Mob m, float seconds) {
        m.tickTimers(seconds);
    }

    // -----------------------------------------------------------------------
    // Breedable types
    // -----------------------------------------------------------------------

    @Test
    void pigIsBreadable() {
        assertTrue(Mob.isBreedable(Mob.Type.PIG));
    }

    @Test
    void cowIsBreedable() {
        assertTrue(Mob.isBreedable(Mob.Type.COW));
    }

    @Test
    void sheepIsBreedable() {
        assertTrue(Mob.isBreedable(Mob.Type.SHEEP));
    }

    @Test
    void horseIsNotBreedable() {
        assertFalse(Mob.isBreedable(Mob.Type.HORSE));
    }

    @Test
    void zombieIsNotBreedable() {
        assertFalse(Mob.isBreedable(Mob.Type.ZOMBIE));
    }

    @Test
    void wolfIsNotBreedable() {
        assertFalse(Mob.isBreedable(Mob.Type.WOLF));
    }

    // -----------------------------------------------------------------------
    // Valid breeding foods
    // -----------------------------------------------------------------------

    @Test
    void carrotFeedsPig() {
        assertTrue(Mob.isValidBreedingFood(Mob.Type.PIG, BlockType.CARROT));
    }

    @Test
    void potatoFeedsPig() {
        assertTrue(Mob.isValidBreedingFood(Mob.Type.PIG, BlockType.POTATO));
    }

    @Test
    void wheatFeedsCow() {
        assertTrue(Mob.isValidBreedingFood(Mob.Type.COW, BlockType.WHEAT));
    }

    @Test
    void wheatFeedsSheep() {
        assertTrue(Mob.isValidBreedingFood(Mob.Type.SHEEP, BlockType.WHEAT));
    }

    // -----------------------------------------------------------------------
    // Invalid breeding foods
    // -----------------------------------------------------------------------

    @Test
    void wheatDoesNotFeedPig() {
        assertFalse(Mob.isValidBreedingFood(Mob.Type.PIG, BlockType.WHEAT));
    }

    @Test
    void carrotDoesNotFeedCow() {
        assertFalse(Mob.isValidBreedingFood(Mob.Type.COW, BlockType.CARROT));
    }

    @Test
    void dirtDoesNotFeedAnything() {
        assertFalse(Mob.isValidBreedingFood(Mob.Type.PIG,   BlockType.DIRT));
        assertFalse(Mob.isValidBreedingFood(Mob.Type.COW,   BlockType.DIRT));
        assertFalse(Mob.isValidBreedingFood(Mob.Type.SHEEP, BlockType.DIRT));
    }

    // -----------------------------------------------------------------------
    // feed() and love-mode entry
    // -----------------------------------------------------------------------

    @Test
    void feedWithValidFoodEntersLoveMode() {
        Mob pig = mob(Mob.Type.PIG);
        assertTrue(pig.isBreedingCapable());

        boolean fed = pig.feed(BlockType.CARROT);

        assertTrue(fed, "feed() should return true for valid food");
        assertTrue(pig.isInLoveMode(), "pig should be in love mode after being fed");
    }

    @Test
    void feedWithInvalidFoodDoesNotEnterLoveMode() {
        Mob pig = mob(Mob.Type.PIG);
        boolean fed = pig.feed(BlockType.WHEAT);

        assertFalse(fed, "feed() should return false for invalid food");
        assertFalse(pig.isInLoveMode());
    }

    @Test
    void feedWhileAlreadyInLoveModeReturnsFalse() {
        Mob cow = mob(Mob.Type.COW);
        cow.feed(BlockType.WHEAT);          // enters love mode
        assertTrue(cow.isInLoveMode());

        boolean secondFeed = cow.feed(BlockType.WHEAT);
        assertFalse(secondFeed, "second feed while already in love mode should return false");
    }

    // -----------------------------------------------------------------------
    // Love-mode countdown
    // -----------------------------------------------------------------------

    @Test
    void loveModeExpiresAfterDuration() {
        Mob sheep = mob(Mob.Type.SHEEP);
        sheep.feed(BlockType.WHEAT);
        assertTrue(sheep.isInLoveMode());

        // Tick past LOVE_DURATION (25 s)
        tick(sheep, Mob.LOVE_DURATION + 1f);
        assertFalse(sheep.isInLoveMode(), "love mode should expire after LOVE_DURATION seconds");
    }

    @Test
    void loveModeStillActiveBeforeDurationExpires() {
        Mob sheep = mob(Mob.Type.SHEEP);
        sheep.feed(BlockType.WHEAT);

        tick(sheep, Mob.LOVE_DURATION - 1f);   // just before expiry
        assertTrue(sheep.isInLoveMode());
    }

    // -----------------------------------------------------------------------
    // Breeding cooldown
    // -----------------------------------------------------------------------

    @Test
    void afterBreedingCooldownMobIsNotBreedingCapable() {
        Mob cow = mob(Mob.Type.COW);
        cow.feed(BlockType.WHEAT);
        cow.applyBreedingCooldown();

        assertFalse(cow.isInLoveMode(), "love mode should be cleared by cooldown");
        assertFalse(cow.isBreedingCapable(), "mob should not be breeding-capable during cooldown");
    }

    @Test
    void mobBecomesBreedingCapableAfterCooldownExpires() {
        Mob cow = mob(Mob.Type.COW);
        cow.feed(BlockType.WHEAT);
        cow.applyBreedingCooldown();

        // Tick past BREED_COOLDOWN (300 s)
        tick(cow, Mob.BREED_COOLDOWN + 1f);
        assertTrue(cow.isBreedingCapable(), "mob should be breedable again after cooldown");
    }

    @Test
    void feedDuringCooldownReturnsFalse() {
        Mob pig = mob(Mob.Type.PIG);
        pig.feed(BlockType.CARROT);
        pig.applyBreedingCooldown();

        boolean fed = pig.feed(BlockType.CARROT);
        assertFalse(fed, "feeding during cooldown should return false");
    }

    // -----------------------------------------------------------------------
    // Baby flag and growth
    // -----------------------------------------------------------------------

    @Test
    void setBabyFlagIsReflected() {
        Mob pig = mob(Mob.Type.PIG);
        assertFalse(pig.isBaby());

        pig.setBaby(true);
        assertTrue(pig.isBaby());
    }

    @Test
    void babyGrowsUpAfterGrowTime() {
        Mob pig = mob(Mob.Type.PIG);
        pig.setBaby(true);
        assertTrue(pig.isBaby());

        tick(pig, Mob.BABY_GROW_TIME + 1f);
        assertFalse(pig.isBaby(), "baby should have grown up after BABY_GROW_TIME seconds");
    }

    @Test
    void babyStillYoungBeforeGrowTimeExpires() {
        Mob pig = mob(Mob.Type.PIG);
        pig.setBaby(true);

        tick(pig, Mob.BABY_GROW_TIME - 1f);
        assertTrue(pig.isBaby(), "baby should still be a baby before BABY_GROW_TIME");
    }

    @Test
    void babyIsNotBreedingCapable() {
        Mob pig = mob(Mob.Type.PIG);
        pig.setBaby(true);
        assertFalse(pig.isBreedingCapable(), "babies cannot breed");
    }

    // -----------------------------------------------------------------------
    // World.tickBreeding — pair detection
    // -----------------------------------------------------------------------

    /**
     * Creates a minimal world with two love-mode mobs of the same type close
     * together; after {@code tickBreeding} a baby should appear in the mob list.
     */
    @Test
    void tickBreedingSpawnsBabyForNearbyPair() {
        World world = world();
        Random rnd = new Random(0);

        Mob ma = world.newMob(Mob.Type.PIG, 0f, 1f, 0f);
        Mob mb = world.newMob(Mob.Type.PIG, 1f, 1f, 0f);   // within BREED_RANGE (4)
        world.getMobs().add(ma);
        world.getMobs().add(mb);
        ma.feed(BlockType.CARROT);
        mb.feed(BlockType.CARROT);

        int before = world.getMobs().size();
        world.tickBreeding(rnd);
        int after = world.getMobs().size();

        assertEquals(before + 1, after, "one baby pig should have been spawned");

        // The newly spawned mob should be a baby pig
        Mob baby = world.getMobs().get(after - 1);
        assertEquals(Mob.Type.PIG, baby.type);
        assertTrue(baby.isBaby(), "newly spawned mob should be a baby");
    }

    @Test
    void tickBreedingAppliesCooldownToParents() {
        World world = world();
        Random rnd = new Random(0);

        Mob ma = world.newMob(Mob.Type.PIG, 0f, 1f, 0f);
        Mob mb = world.newMob(Mob.Type.PIG, 1f, 1f, 0f);
        world.getMobs().add(ma);
        world.getMobs().add(mb);
        ma.feed(BlockType.CARROT);
        mb.feed(BlockType.CARROT);

        world.tickBreeding(rnd);

        assertFalse(ma.isInLoveMode(), "parent A should not be in love mode after breeding");
        assertFalse(mb.isInLoveMode(), "parent B should not be in love mode after breeding");
        assertFalse(ma.isBreedingCapable(), "parent A should be on breeding cooldown");
        assertFalse(mb.isBreedingCapable(), "parent B should be on breeding cooldown");
    }

    @Test
    void tickBreedingDoesNotPairMismatchedTypes() {
        World world = world();
        Random rnd = new Random(0);

        Mob pig  = world.newMob(Mob.Type.PIG,  0f, 1f, 0f);
        Mob cow  = world.newMob(Mob.Type.COW,  1f, 1f, 0f);
        world.getMobs().add(pig);
        world.getMobs().add(cow);
        pig.feed(BlockType.CARROT);
        cow.feed(BlockType.WHEAT);

        int before = world.getMobs().size();
        world.tickBreeding(rnd);
        int after = world.getMobs().size();

        assertEquals(before, after, "no baby should be produced for mismatched types");
    }

    @Test
    void tickBreedingDoesNotPairMobsTooFarApart() {
        World world = world();
        Random rnd = new Random(0);

        // Place them farther apart than BREED_RANGE (4 blocks)
        float farAway = Mob.BREED_RANGE + 2f;
        Mob ma = world.newMob(Mob.Type.COW, 0f,        1f, 0f);
        Mob mb = world.newMob(Mob.Type.COW, farAway,   1f, 0f);
        world.getMobs().add(ma);
        world.getMobs().add(mb);
        ma.feed(BlockType.WHEAT);
        mb.feed(BlockType.WHEAT);

        int before = world.getMobs().size();
        world.tickBreeding(rnd);
        int after = world.getMobs().size();

        assertEquals(before, after, "no baby should be produced when parents are too far apart");
    }

    @Test
    void tickBreedingNoActionWithoutLoveModeParents() {
        World world = world();
        Random rnd = new Random(0);

        // Two unfed pigs — no love mode
        world.getMobs().add(world.newMob(Mob.Type.PIG, 0f, 1f, 0f));
        world.getMobs().add(world.newMob(Mob.Type.PIG, 1f, 1f, 0f));

        int before = world.getMobs().size();
        world.tickBreeding(rnd);
        int after = world.getMobs().size();

        assertEquals(before, after, "no baby should be spawned when neither parent is in love mode");
    }
}
