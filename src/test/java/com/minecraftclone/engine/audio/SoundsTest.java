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
}
