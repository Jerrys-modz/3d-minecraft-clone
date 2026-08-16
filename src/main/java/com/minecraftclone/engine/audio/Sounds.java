package com.minecraftclone.engine.audio;

import java.util.EnumMap;
import java.util.Map;

/**
 * Synthesizes every sound effect's PCM buffer once (see {@link SoundSynth})
 * and hands them out by {@link SoundEvent}, or by {@link SoundMaterial} +
 * {@link BlockAction} for the block break/place/footstep sounds. Pure data -
 * no OpenAL calls here, so it's trivially unit-testable; {@link AudioEngine}
 * is what uploads these into actual AL buffers.
 */
public final class Sounds {

    private final Map<SoundEvent, short[]> fixed = new EnumMap<>(SoundEvent.class);
    private final Map<SoundMaterial, short[]> breakSounds = new EnumMap<>(SoundMaterial.class);
    private final Map<SoundMaterial, short[]> placeSounds = new EnumMap<>(SoundMaterial.class);
    private final Map<SoundMaterial, short[]> stepSounds = new EnumMap<>(SoundMaterial.class);

    public Sounds() {
        buildFixedSounds();
        for (SoundMaterial m : SoundMaterial.values()) {
            breakSounds.put(m, blockSound(m, BlockAction.BREAK));
            placeSounds.put(m, blockSound(m, BlockAction.PLACE));
            stepSounds.put(m, blockSound(m, BlockAction.STEP));
        }
    }

    public short[] get(SoundEvent event) {
        return fixed.get(event);
    }

    public short[] get(SoundMaterial material, BlockAction action) {
        return switch (action) {
            case BREAK -> breakSounds.get(material);
            case PLACE -> placeSounds.get(material);
            case STEP -> stepSounds.get(material);
        };
    }

    private void buildFixedSounds() {
        fixed.put(SoundEvent.UI_CLICK, SoundSynth.tone(900, 700, 0.04f, 0.35f, 0f));
        fixed.put(SoundEvent.UI_OPEN, SoundSynth.tone(420, 720, 0.09f, 0.3f, 0f));
        fixed.put(SoundEvent.UI_CLOSE, SoundSynth.tone(720, 420, 0.09f, 0.3f, 0f));

        fixed.put(SoundEvent.JUMP, SoundSynth.mix(
                SoundSynth.tone(320, 520, 0.09f, 0.32f, 0f),
                SoundSynth.noise(101, 0.05f, 0.15f, 0f, 0.6f)));

        fixed.put(SoundEvent.LAND, SoundSynth.mix(
                SoundSynth.noise(102, 0.14f, 0.6f, 0f, 0.18f),
                SoundSynth.tone(130, 80, 0.12f, 0.35f, 0f)));

        fixed.put(SoundEvent.SPLASH, SoundSynth.mix(
                SoundSynth.noise(103, 0.28f, 0.55f, 0f, 0.5f),
                SoundSynth.noise(104, 0.1f, 0.35f, 0f, 0.85f)));

        fixed.put(SoundEvent.EAT, SoundSynth.concat(
                SoundSynth.noise(105, 0.06f, 0.5f, 0f, 0.3f), SoundSynth.silence(0.03f),
                SoundSynth.noise(106, 0.06f, 0.5f, 0f, 0.3f), SoundSynth.silence(0.03f),
                SoundSynth.noise(107, 0.07f, 0.5f, 0f, 0.3f)));

        fixed.put(SoundEvent.HURT, SoundSynth.mix(
                SoundSynth.tone(300, 150, 0.18f, 0.5f, 0f),
                SoundSynth.noise(108, 0.16f, 0.3f, 0f, 0.4f)));

        fixed.put(SoundEvent.DEATH, SoundSynth.mix(
                SoundSynth.tone(260, 60, 0.9f, 0.6f, 0f),
                SoundSynth.noise(109, 0.6f, 0.25f, 0f, 0.3f)));

        fixed.put(SoundEvent.ATTACK, SoundSynth.mix(
                SoundSynth.noise(110, 0.08f, 0.6f, 0f, 0.7f),
                SoundSynth.tone(700, 400, 0.05f, 0.3f, 0f)));

        fixed.put(SoundEvent.MOB_DEATH, SoundSynth.mix(
                SoundSynth.tone(200, 50, 0.3f, 0.5f, 0f),
                SoundSynth.noise(111, 0.25f, 0.3f, 0f, 0.35f)));

        fixed.put(SoundEvent.TOOL_BREAK, SoundSynth.mix(
                SoundSynth.noise(112, 0.1f, 0.7f, 0f, 0.8f),
                SoundSynth.tone(500, 180, 0.15f, 0.4f, 0f)));

        fixed.put(SoundEvent.ITEM_PICKUP, SoundSynth.tone(500, 950, 0.09f, 0.4f, 0f));

        fixed.put(SoundEvent.DOOR, SoundSynth.mix(
                SoundSynth.noise(113, 0.18f, 0.5f, 0f, 0.25f),
                SoundSynth.tone(150, 120, 0.18f, 0.3f, 0f)));

        // A simple ascending major arpeggio (C5-E5-G5) - a pleasant "ding" for
        // taking a crafting/smelting result, distinct from the generic UI click.
        fixed.put(SoundEvent.CRAFT, SoundSynth.concat(
                SoundSynth.tone(523, 523, 0.08f, 0.35f, 0.05f),
                SoundSynth.tone(659, 659, 0.08f, 0.35f, 0.05f),
                SoundSynth.tone(784, 784, 0.14f, 0.4f, 0f)));
    }

    /**
     * Builds a break/place/footstep sound for {@code material}: a filtered
     * noise burst (see {@link SoundSynth#noise}) whose brightness (duller vs.
     * crisper) captures most of the material's character, plus - for the
     * denser materials - a low tone layered in for some body. {@code action}
     * scales duration/loudness (a break is longer and louder than a footstep
     * tap) without changing the material's timbre.
     */
    private static short[] blockSound(SoundMaterial material, BlockAction action) {
        long seed = material.ordinal() * 31L + action.ordinal() * 7L + 200L;
        float brightness = switch (material) {
            case STONE -> 0.22f;
            case WOOD -> 0.35f;
            case DIRT -> 0.18f;
            case GRAVEL -> 0.7f;
            case SAND -> 0.55f;
            case GLASS -> 0.85f;
            case LEAVES -> 0.6f;
            case DEFAULT -> 0.3f;
        };
        float toneFreq = switch (material) {
            case STONE -> 110f;
            case WOOD -> 180f;
            case GLASS -> 1400f;
            default -> 0f; // dirt/gravel/sand/leaves/default: noise-only, no resonant body
        };

        float duration = switch (action) {
            case BREAK -> 0.22f;
            case PLACE -> 0.12f;
            case STEP -> 0.065f;
        };
        float amp = switch (action) {
            case BREAK -> 0.85f;
            case PLACE -> 0.55f;
            case STEP -> 0.3f;
        };

        short[] noiseLayer = SoundSynth.noise(seed, duration, amp, 0f, brightness);
        if (toneFreq <= 0f) return noiseLayer;
        short[] toneLayer = SoundSynth.tone(toneFreq, toneFreq * 0.8f, duration * 0.7f, amp * 0.4f, 0f);
        return SoundSynth.mix(noiseLayer, toneLayer);
    }
}
