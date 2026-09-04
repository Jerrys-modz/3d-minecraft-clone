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
 *   <li>Love-mode timer countdown via {@link Mob#tickTimers(float)}.</li>
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

    /** Creates a minimal World backed by the JUnit temp directory. */
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

    /** Pigs are breedable. */
    @Test
    void pigIsBreadable() {
        assertTrue(Mob.isBreedable(Mob.Type.PIG));
    }

    /** Cows are breedable. */
    @Test
    void cowIsBreedable() {
        assertTrue(Mob.isBreedable(Mob.Type.COW));
    }

    /** Sheep are breedable. */
    @Test
    void sheepIsBreedable() {
        assertTrue(Mob.isBreedable(Mob.Type.SHEEP));
    }

    /** Horses are not breedable. */
    @Test
    void horseIsNotBreedable() {
        assertFalse(Mob.isBreedable(Mob.Type.HORSE));
    }

    /** Zombies are not breedable. */
    @Test
    void zombieIsNotBreedable() {
        assertFalse(Mob.isBreedable(Mob.Type.ZOMBIE));
    }

    /** Wolves are not breedable. */
    @Test
    void wolfIsNotBreedable() {
        assertFalse(Mob.isBreedable(Mob.Type.WOLF));
    }

    // -----------------------------------------------------------------------
    // Valid breeding foods
    // -----------------------------------------------------------------------

    /** Carrot is valid breeding food for a pig. */
    @Test
    void carrotFeedsPig() {
        assertTrue(Mob.isValidBreedingFood(Mob.Type.PIG, BlockType.CARROT));
    }

    /** Potato is valid breeding food for a pig. */
    @Test
    void potatoFeedsPig() {
        assertTrue(Mob.isValidBreedingFood(Mob.Type.PIG, BlockType.POTATO));
    }

    /** Wheat is valid breeding food for a cow. */
    @Test
    void wheatFeedsCow() {
        assertTrue(Mob.isValidBreedingFood(Mob.Type.COW, BlockType.WHEAT));
    }

    /** Wheat is valid breeding food for a sheep. */
    @Test
    void wheatFeedsSheep() {
        assertTrue(Mob.isValidBreedingFood(Mob.Type.SHEEP, BlockType.WHEAT));
    }

    // -----------------------------------------------------------------------
    // Invalid breeding foods
    // -----------------------------------------------------------------------

    /** Wheat is not valid breeding food for a pig. */
    @Test
    void wheatDoesNotFeedPig() {
        assertFalse(Mob.isValidBreedingFood(Mob.Type.PIG, BlockType.WHEAT));
    }

    /** Carrot is not valid breeding food for a cow. */
    @Test
    void carrotDoesNotFeedCow() {
        assertFalse(Mob.isValidBreedingFood(Mob.Type.COW, BlockType.CARROT));
    }

    /** Dirt is not valid breeding food for any breedable mob. */
    @Test
    void dirtDoesNotFeedAnything() {
        assertFalse(Mob.isValidBreedingFood(Mob.Type.PIG,   BlockType.DIRT));
        assertFalse(Mob.isValidBreedingFood(Mob.Type.COW,   BlockType.DIRT));
        assertFalse(Mob.isValidBreedingFood(Mob.Type.SHEEP, BlockType.DIRT));
    }

    // -----------------------------------------------------------------------
    // feed() and love-mode entry
    // -----------------------------------------------------------------------

    /** Feeding a capable mob with valid food returns true and enters love mode. */
    @Test
    void feedWithValidFoodEntersLoveMode() {
        Mob pig = mob(Mob.Type.PIG);
        assertTrue(pig.isBreedingCapable());

        boolean fed = pig.feed(BlockType.CARROT);

        assertTrue(fed, "feed() should return true for valid food");
        assertTrue(pig.isInLoveMode(), "pig should be in love mode after being fed");
    }

    /** Feeding a mob with food not valid for its type returns false and leaves it out of love mode. */
    @Test
    void feedWithInvalidFoodDoesNotEnterLoveMode() {
        Mob pig = mob(Mob.Type.PIG);
        boolean fed = pig.feed(BlockType.WHEAT);

        assertFalse(fed, "feed() should return false for invalid food");
        assertFalse(pig.isInLoveMode());
    }

    /** Feeding a mob that is already in love mode returns false. */
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

    /** Love mode expires once LOVE_DURATION seconds have elapsed. */
    @Test
    void loveModeExpiresAfterDuration() {
        Mob sheep = mob(Mob.Type.SHEEP);
        sheep.feed(BlockType.WHEAT);
        assertTrue(sheep.isInLoveMode());

        // Tick past LOVE_DURATION (25 s)
        tick(sheep, Mob.LOVE_DURATION + 1f);
        assertFalse(sheep.isInLoveMode(), "love mode should expire after LOVE_DURATION seconds");
    }

    /** Love mode is still active just before LOVE_DURATION elapses. */
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

    /** Applying a breeding cooldown clears love mode and marks the mob as not breeding-capable. */
    @Test
    void afterBreedingCooldownMobIsNotBreedingCapable() {
        Mob cow = mob(Mob.Type.COW);
        cow.feed(BlockType.WHEAT);
        cow.applyBreedingCooldown();

        assertFalse(cow.isInLoveMode(), "love mode should be cleared by cooldown");
        assertFalse(cow.isBreedingCapable(), "mob should not be breeding-capable during cooldown");
    }

    /** The mob becomes breeding-capable again once BREED_COOLDOWN seconds have elapsed. */
    @Test
    void mobBecomesBreedingCapableAfterCooldownExpires() {
        Mob cow = mob(Mob.Type.COW);
        cow.feed(BlockType.WHEAT);
        cow.applyBreedingCooldown();

        // Tick past BREED_COOLDOWN (300 s)
        tick(cow, Mob.BREED_COOLDOWN + 1f);
        assertTrue(cow.isBreedingCapable(), "mob should be breedable again after cooldown");
    }

    /** Attempting to feed a mob during its breeding cooldown returns false. */
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

    /** setBaby(true) is reflected immediately by isBaby(). */
    @Test
    void setBabyFlagIsReflected() {
        Mob pig = mob(Mob.Type.PIG);
        assertFalse(pig.isBaby());

        pig.setBaby(true);
        assertTrue(pig.isBaby());
    }

    /** A baby grows into an adult once BABY_GROW_TIME seconds have elapsed. */
    @Test
    void babyGrowsUpAfterGrowTime() {
        Mob pig = mob(Mob.Type.PIG);
        pig.setBaby(true);
        assertTrue(pig.isBaby());

        tick(pig, Mob.BABY_GROW_TIME + 1f);
        assertFalse(pig.isBaby(), "baby should have grown up after BABY_GROW_TIME seconds");
    }

    /** A baby is still a baby just before BABY_GROW_TIME elapses. */
    @Test
    void babyStillYoungBeforeGrowTimeExpires() {
        Mob pig = mob(Mob.Type.PIG);
        pig.setBaby(true);

        tick(pig, Mob.BABY_GROW_TIME - 1f);
        assertTrue(pig.isBaby(), "baby should still be a baby before BABY_GROW_TIME");
    }

    /** Baby mobs cannot enter love mode or breed. */
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
     * Two love-mode adults of the same type within BREED_RANGE cause
     * {@link World#tickBreeding(Random)} to spawn exactly one baby.
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

    /** Both parents receive a breeding cooldown after tickBreeding produces a baby. */
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

    /** tickBreeding does not pair mobs of different types, even when both are in love mode. */
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

    /** tickBreeding does not pair adults that are farther apart than BREED_RANGE. */
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

    /** tickBreeding is a no-op when neither mob is in love mode. */
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
