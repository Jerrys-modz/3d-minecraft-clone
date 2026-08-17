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
}
