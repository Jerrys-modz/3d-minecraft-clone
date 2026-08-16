package com.minecraftclone.engine.audio;

import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * This test environment has no audio device at all (see AudioEngine's class
 * javadoc), so {@link AudioEngine#init()} is expected to leave the engine
 * disabled here. That's exactly the path these tests exercise: every method,
 * including the new per-category volume sliders, must stay a safe no-op
 * with sound disabled rather than throwing.
 */
class AudioEngineTest {

    @Test
    void categoryVolumesDefaultToFullAndAcceptEveryCategory() {
        AudioEngine audio = new AudioEngine();
        for (SoundCategory category : SoundCategory.values()) {
            assertDoesNotThrow(() -> audio.setCategoryVolume(category, 0.5f));
        }
    }

    @Test
    void categoryVolumeIsClampedLikeMasterVolume() {
        AudioEngine audio = new AudioEngine();
        assertDoesNotThrow(() -> {
            audio.setCategoryVolume(SoundCategory.MOBS, -5f);
            audio.setCategoryVolume(SoundCategory.MOBS, 5f);
        });
    }

    @Test
    void disabledEngineNoOpsEveryMethodIncludingCategoryVolume() {
        AudioEngine audio = new AudioEngine();
        audio.init(); // no audio device in this environment - leaves it disabled
        assertFalse(audio.isEnabled());

        assertDoesNotThrow(() -> {
            audio.setMasterVolume(0.5f);
            for (SoundCategory category : SoundCategory.values()) {
                audio.setCategoryVolume(category, 0.5f);
            }
            audio.setListener(new Vector3f(), new Vector3f(0, 0, -1), new Vector3f(0, 1, 0));
            audio.play(SoundEvent.UI_CLICK);
            audio.playAt(SoundEvent.LAND, 0f, 0f, 0f, 1f);
            audio.playBlockSound(SoundMaterial.STONE, BlockAction.BREAK, 0f, 0f, 0f, 1f);
            audio.destroy();
        });
    }
}
