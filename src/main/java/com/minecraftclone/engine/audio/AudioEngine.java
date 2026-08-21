package com.minecraftclone.engine.audio;

import org.joml.Vector3f;
import org.lwjgl.openal.AL;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.ALC;
import org.lwjgl.openal.ALC10;
import org.lwjgl.openal.ALCCapabilities;

import java.nio.ShortBuffer;
import java.util.EnumMap;
import java.util.Map;

import static org.lwjgl.system.MemoryUtil.memAllocShort;
import static org.lwjgl.system.MemoryUtil.memFree;

/**
 * Owns the OpenAL device/context, uploads every {@link Sounds} buffer once,
 * and plays them through a small pool of reused sources - positional (block
 * breaks, footsteps, mobs) or not (UI clicks, the death cry).
 * <p>
 * Initialization is best-effort: a machine with no audio device at all (or
 * one OpenAL just can't open for whatever reason) logs a warning and leaves
 * the engine permanently disabled rather than throwing - every other method
 * on this class then silently no-ops, so the rest of the game runs exactly
 * as if sound were muted. This matters more than it might for a desktop
 * game: headless CI/verification environments routinely have no audio
 * hardware at all (no {@code /dev/snd}, no ALSA/PulseAudio), and the game
 * needs to keep working there.
 */
public class AudioEngine {

    private static final int MAX_SOURCES = 24;
    // Positional sounds fade out between these distances (blocks) - close
    // enough that footsteps/breaks feel local, far enough that a torch
    // dinging or a mob dying nearby is still audible from across a room.
    private static final float REFERENCE_DISTANCE = 4f;
    private static final float MAX_DISTANCE = 48f;

    private final Sounds sounds = new Sounds();
    private final Map<SoundEvent, Integer> fixedBuffers = new EnumMap<>(SoundEvent.class);
    private final Map<SoundMaterial, Integer> breakBuffers = new EnumMap<>(SoundMaterial.class);
    private final Map<SoundMaterial, Integer> placeBuffers = new EnumMap<>(SoundMaterial.class);
    private final Map<SoundMaterial, Integer> stepBuffers = new EnumMap<>(SoundMaterial.class);
    private final Map<SoundMaterial, Integer> hitBuffers = new EnumMap<>(SoundMaterial.class);
    private final java.util.List<Integer> allBuffers = new java.util.ArrayList<>();

    private final Map<SoundCategory, Float> categoryVolumes = new EnumMap<>(SoundCategory.class);

    private boolean enabled = false;
    private long device = 0;
    private long context = 0;
    private int[] sources = new int[0];
    private int nextSource = 0;
    private float masterVolume = 1f;
    /** A dedicated looping source for the precipitation hiss (rain/snow/storms). */
    private int weatherAmbientSource = 0;

    public AudioEngine() {
        for (SoundCategory category : SoundCategory.values()) {
            categoryVolumes.put(category, 1f);
        }
    }

    /** Opens the default OpenAL device and uploads every sound. Safe to call even with no audio hardware at all. */
    public void init() {
        try {
            device = ALC10.alcOpenDevice((java.nio.ByteBuffer) null);
            if (device == 0L) {
                System.err.println("Audio: no OpenAL device available - sound disabled.");
                return;
            }
            ALCCapabilities deviceCaps = ALC.createCapabilities(device);
            context = ALC10.alcCreateContext(device, (java.nio.IntBuffer) null);
            if (context == 0L) {
                System.err.println("Audio: could not create an OpenAL context - sound disabled.");
                cleanup();
                return;
            }
            ALC10.alcMakeContextCurrent(context);
            AL.createCapabilities(deviceCaps);

            sources = new int[MAX_SOURCES];
            for (int i = 0; i < MAX_SOURCES; i++) {
                sources[i] = AL10.alGenSources();
            }
            weatherAmbientSource = AL10.alGenSources();

            for (SoundEvent e : SoundEvent.values()) {
                fixedBuffers.put(e, upload(sounds.get(e)));
            }
            for (SoundMaterial m : SoundMaterial.values()) {
                breakBuffers.put(m, upload(sounds.get(m, BlockAction.BREAK)));
                placeBuffers.put(m, upload(sounds.get(m, BlockAction.PLACE)));
                stepBuffers.put(m, upload(sounds.get(m, BlockAction.STEP)));
                hitBuffers.put(m, upload(sounds.get(m, BlockAction.HIT)));
            }

            enabled = true;
        } catch (Throwable t) {
            System.err.println("Audio: failed to initialize OpenAL (" + t + ") - sound disabled.");
            cleanup();
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    /** Master volume (0..1) applied on top of every sound's own volume - see the settings menu's Audio tab. */
    public void setMasterVolume(float volume) {
        this.masterVolume = Math.max(0f, Math.min(1f, volume));
        if (enabled) {
            AL10.alListenerf(AL10.AL_GAIN, masterVolume);
        }
    }

    /**
     * Per-category volume (0..1), multiplied into every sound of that
     * category on top of the master volume - see the settings menu's Audio
     * tab sliders (Music, Ambient, Mobs, Machines, Player, UI).
     */
    public void setCategoryVolume(SoundCategory category, float volume) {
        categoryVolumes.put(category, Math.max(0f, Math.min(1f, volume)));
    }

    private float categoryVolume(SoundCategory category) {
        Float v = categoryVolumes.get(category);
        return v != null ? v : 1f;
    }

    /** Updates the OpenAL listener from the camera each frame, so positional sounds pan/attenuate correctly. */
    public void setListener(Vector3f position, Vector3f front, Vector3f up) {
        if (!enabled) return;
        AL10.alListener3f(AL10.AL_POSITION, position.x, position.y, position.z);
        AL10.alListenerfv(AL10.AL_ORIENTATION, new float[]{front.x, front.y, front.z, up.x, up.y, up.z});
    }

    /** Plays a non-positional sound (UI, the death cry) - always at full presence regardless of camera position. */
    public void play(SoundEvent event) {
        play(event, 1f);
    }

    public void play(SoundEvent event, float volume) {
        if (!enabled) return;
        Integer buffer = fixedBuffers.get(event);
        if (buffer == null) return;
        int source = nextSource();
        AL10.alSourcei(source, AL10.AL_SOURCE_RELATIVE, AL10.AL_TRUE);
        AL10.alSource3f(source, AL10.AL_POSITION, 0f, 0f, 0f);
        AL10.alSourcef(source, AL10.AL_GAIN, clampVolume(volume) * categoryVolume(event.category()));
        AL10.alSourcei(source, AL10.AL_BUFFER, buffer);
        AL10.alSourcePlay(source);
    }

    /** Plays a non-positional sound at the given world position (block breaks, mobs, footsteps, ...). */
    public void playAt(SoundEvent event, float x, float y, float z, float volume) {
        playPositional(fixedBuffers.get(event), event.category(), x, y, z, volume);
    }

    /** The block break/place/footstep sound for {@code material}, played at a world position. */
    public void playBlockSound(SoundMaterial material, BlockAction action, float x, float y, float z, float volume) {
        Map<SoundMaterial, Integer> table = switch (action) {
            case BREAK -> breakBuffers;
            case PLACE -> placeBuffers;
            case STEP -> stepBuffers;
            case HIT -> hitBuffers;
        };
        playPositional(table.get(material), categoryFor(action), x, y, z, volume);
    }

    /** Which slider a block break/place/footstep sound mixes against - footsteps read as a player sound, break/place as interacting with the world. */
    private static SoundCategory categoryFor(BlockAction action) {
        return action == BlockAction.STEP ? SoundCategory.PLAYER : SoundCategory.MACHINES;
    }

    private void playPositional(Integer buffer, SoundCategory category, float x, float y, float z, float volume) {
        if (!enabled || buffer == null) return;
        int source = nextSource();
        AL10.alSourcei(source, AL10.AL_SOURCE_RELATIVE, AL10.AL_FALSE);
        AL10.alSource3f(source, AL10.AL_POSITION, x, y, z);
        AL10.alSourcef(source, AL10.AL_REFERENCE_DISTANCE, REFERENCE_DISTANCE);
        AL10.alSourcef(source, AL10.AL_MAX_DISTANCE, MAX_DISTANCE);
        AL10.alSourcef(source, AL10.AL_ROLLOFF_FACTOR, 1f);
        AL10.alSourcef(source, AL10.AL_GAIN, clampVolume(volume) * categoryVolume(category));
        AL10.alSourcei(source, AL10.AL_BUFFER, buffer);
        AL10.alSourcePlay(source);
    }

    /**
     * The next source in the pool, round-robin. A source already playing
     * gets cut off early rather than skipping the new sound - with a couple
     * dozen sources and short (well under a second) effects, an occasional
     * cut-off footstep is never noticeable, and it's simpler and cheaper
     * than tracking each source's real playback state every call.
     */
    private int nextSource() {
        int source = sources[nextSource];
        nextSource = (nextSource + 1) % sources.length;
        AL10.alSourceStop(source);
        return source;
    }

    private static float clampVolume(float v) {
        return Math.max(0f, Math.min(1f, v));
    }

    private int upload(short[] pcm) {
        int buffer = AL10.alGenBuffers();
        ShortBuffer data = memAllocShort(pcm.length);
        try {
            data.put(pcm).flip();
            AL10.alBufferData(buffer, AL10.AL_FORMAT_MONO16, data, SoundSynth.SAMPLE_RATE);
            allBuffers.add(buffer);
            return buffer;
        } finally {
            memFree(data);
        }
    }

    /**
     * Drives the looping precipitation hiss (the {@link SoundEvent#RAIN} buffer):
     * a non-zero {@code amount} (0..1) starts/continues the loop at that volume,
     * zero stops it. Called every frame with the current weather's intensity, so
     * the loop follows the weather and the ambient volume slider live.
     * <p>
     * Only the gain is touched on every call - {@code alSourcePlay} restarts
     * playback from the beginning even on a source that's already playing, so
     * calling it unconditionally every frame (as this used to) retriggered the
     * "seamless" loop from sample 0 some 60 times a second instead of ever
     * letting it actually play: a harsh, clicking buzz instead of a smooth
     * hiss. The buffer/looping/play setup now only runs once, on the frame
     * playback actually starts (stopped -&gt; playing), same as any other loop.
     */
    public void setWeatherAmbience(float amount) {
        if (!enabled || weatherAmbientSource == 0) return;
        if (amount <= 0f) {
            AL10.alSourceStop(weatherAmbientSource);
            return;
        }
        AL10.alSourcef(weatherAmbientSource, AL10.AL_GAIN, clampVolume(amount) * categoryVolume(SoundCategory.AMBIENT));
        if (AL10.alGetSourcei(weatherAmbientSource, AL10.AL_SOURCE_STATE) == AL10.AL_PLAYING) {
            return; // already looping - just the gain update above, don't restart it
        }
        Integer buffer = fixedBuffers.get(SoundEvent.RAIN);
        AL10.alSourcei(weatherAmbientSource, AL10.AL_SOURCE_RELATIVE, AL10.AL_TRUE);
        AL10.alSource3f(weatherAmbientSource, AL10.AL_POSITION, 0f, 0f, 0f);
        if (buffer != null) {
            AL10.alSourcei(weatherAmbientSource, AL10.AL_BUFFER, buffer);
        }
        AL10.alSourcei(weatherAmbientSource, AL10.AL_LOOPING, AL10.AL_TRUE);
        AL10.alSourcePlay(weatherAmbientSource);
    }

    /** Idempotent cleanup: releases all OpenAL resources and resets state. Safe to call even if init failed partway. */
    private void cleanup() {
        if (sources.length > 0) {
            for (int source : sources) {
                if (source != 0) AL10.alDeleteSources(source);
            }
        }
        if (weatherAmbientSource != 0) {
            AL10.alDeleteSources(weatherAmbientSource);
            weatherAmbientSource = 0;
        }
        for (int buffer : allBuffers) {
            AL10.alDeleteBuffers(buffer);
        }
        allBuffers.clear();
        fixedBuffers.clear();
        breakBuffers.clear();
        placeBuffers.clear();
        stepBuffers.clear();
        sources = new int[0];
        nextSource = 0;

        ALC10.alcMakeContextCurrent(0L);
        if (context != 0L) {
            ALC10.alcDestroyContext(context);
            context = 0L;
        }
        if (device != 0L) {
            ALC10.alcCloseDevice(device);
            device = 0L;
        }
        enabled = false;
    }

    /** Releases every OpenAL resource. Call once on shutdown. */
    public void destroy() {
        cleanup();
    }
}
