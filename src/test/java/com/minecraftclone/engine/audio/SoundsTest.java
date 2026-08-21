package com.minecraftclone.engine.audio;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exhaustively checks every sound the game can ask for actually exists and
 * is non-empty - would have caught a missing {@code fixed.put(...)} case (an
 * enum added to {@link SoundEvent} without a matching synthesis call, say)
 * before it ever became a silent-at-runtime {@code NullPointerException} in
 * {@link AudioEngine}.
 */
class SoundsTest {

    @Test
    void everySoundEventHasABuffer() {
        Sounds sounds = new Sounds();
        for (SoundEvent e : SoundEvent.values()) {
            short[] pcm = sounds.get(e);
            assertNotNull(pcm, "missing sound for " + e);
            assertTrue(pcm.length > 0, "empty sound for " + e);
        }
    }

    @Test
    void everyMaterialHasAllThreeBlockActionSounds() {
        Sounds sounds = new Sounds();
        for (SoundMaterial m : SoundMaterial.values()) {
            for (BlockAction a : BlockAction.values()) {
                short[] pcm = sounds.get(m, a);
                assertNotNull(pcm, "missing sound for " + m + "/" + a);
                assertTrue(pcm.length > 0, "empty sound for " + m + "/" + a);
            }
        }
    }

    /**
     * Regression test: SPLASH (entering/leaving water, and the periodic swim
     * stroke - see Main's swimStrokeTimer) used to be built from two noise-
     * only layers close enough in both duration and brightness to a sand
     * footstep tap that it was reported as "sounds like walking on sand".
     * A real splash needs to be unmistakably longer than any single footstep
     * tap - a lingering wash/foam tail, not one more dry crunch (it also now
     * carries a pitch-swept tone() layer no break/place/footstep sound has
     * at all - see the synthesis recipe in Sounds - though that's a
     * perceptual property this test doesn't try to detect from raw PCM).
     */
    @Test
    void splashIsClearlyLongerThanAFootstepTap() {
        Sounds sounds = new Sounds();
        short[] splash = sounds.get(SoundEvent.SPLASH);
        short[] sandStep = sounds.get(SoundMaterial.SAND, BlockAction.STEP);

        assertTrue(splash.length > sandStep.length * 3,
                "splash (" + splash.length + " samples) should run well longer than a footstep tap ("
                        + sandStep.length + " samples), not read as one more dry crunch");
    }

    /**
     * Opening a chest used to play the generic UI_OPEN beep. The lid creak
     * has to last long enough (and be a distinct buffer) that it can't be
     * mistaken for a 90ms menu chirp.
     */
    @Test
    void chestOpenIsALongerWoodenCreakThanTheUiBeep() {
        Sounds sounds = new Sounds();
        short[] open = sounds.get(SoundEvent.CHEST_OPEN);
        short[] close = sounds.get(SoundEvent.CHEST_CLOSE);
        short[] uiOpen = sounds.get(SoundEvent.UI_OPEN);
        short[] door = sounds.get(SoundEvent.DOOR);

        assertTrue(open.length > uiOpen.length * 3,
                "chest open (" + open.length + ") should outlast the UI beep (" + uiOpen.length + ")");
        assertTrue(close.length > uiOpen.length * 2,
                "chest close (" + close.length + ") should outlast the UI beep");
        assertTrue(open.length != close.length, "open creak and close slam should not be the same clip");
        assertTrue(open.length != door.length, "chest lid should not reuse the door clip");
    }

    @Test
    void hitIsAShorterPunchThanTheFinalBreak() {
        Sounds sounds = new Sounds();
        short[] stoneHit = sounds.get(SoundMaterial.STONE, BlockAction.HIT);
        short[] stoneBreak = sounds.get(SoundMaterial.STONE, BlockAction.BREAK);
        short[] woodHit = sounds.get(SoundMaterial.WOOD, BlockAction.HIT);
        short[] dirtHit = sounds.get(SoundMaterial.DIRT, BlockAction.HIT);
        assertTrue(stoneHit.length < stoneBreak.length,
                "a mining punch should be shorter than the block actually breaking");
        assertTrue(woodHit.length > 0 && dirtHit.length > 0);
        assertTrue(stoneHit.length != woodHit.length || energy(stoneHit) != energy(woodHit),
                "stone and wood punches should not be identical clips");
    }

    private static long energy(short[] pcm) {
        long sum = 0;
        for (short s : pcm) sum += Math.abs(s);
        return sum;
    }
}
