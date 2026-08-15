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
    private final java.util.List<Integer> allBuffers = new java.util.ArrayList<>();

    private boolean enabled = false;
    private long device = 0;
    private long context = 0;
    private int[] sources = new int[0];
    private int nextSource = 0;
    private float masterVolume = 1f;

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
                ALC10.alcCloseDevice(device);
                device = 0L;
                return;
            }
            ALC10.alcMakeContextCurrent(context);
            AL.createCapabilities(deviceCaps);

            sources = new int[MAX_SOURCES];
            for (int i = 0; i < MAX_SOURCES; i++) {
                sources[i] = AL10.alGenSources();
            }

            for (SoundEvent e : SoundEvent.values()) {
                fixedBuffers.put(e, upload(sounds.get(e)));
            }
            for (SoundMaterial m : SoundMaterial.values()) {
                breakBuffers.put(m, upload(sounds.get(m, BlockAction.BREAK)));
                placeBuffers.put(m, upload(sounds.get(m, BlockAction.PLACE)));
                stepBuffers.put(m, upload(sounds.get(m, BlockAction.STEP)));
            }

            enabled = true;
        } catch (Throwable t) {
            System.err.println("Audio: failed to initialize OpenAL (" + t + ") - sound disabled.");
            enabled = false;
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
        AL10.alSourcef(source, AL10.AL_GAIN, clampVolume(volume));
        AL10.alSourcei(source, AL10.AL_BUFFER, buffer);
        AL10.alSourcePlay(source);
    }

    /** Plays a non-positional sound at the given world position (block breaks, mobs, footsteps, ...). */
    public void playAt(SoundEvent event, float x, float y, float z, float volume) {
        playPositional(fixedBuffers.get(event), x, y, z, volume);
    }

    /** The block break/place/footstep sound for {@code material}, played at a world position. */
    public void playBlockSound(SoundMaterial material, BlockAction action, float x, float y, float z, float volume) {
        Map<SoundMaterial, Integer> table = switch (action) {
            case BREAK -> breakBuffers;
            case PLACE -> placeBuffers;
            case STEP -> stepBuffers;
        };
        playPositional(table.get(material), x, y, z, volume);
    }

    private void playPositional(Integer buffer, float x, float y, float z, float volume) {
        if (!enabled || buffer == null) return;
        int source = nextSource();
        AL10.alSourcei(source, AL10.AL_SOURCE_RELATIVE, AL10.AL_FALSE);
        AL10.alSource3f(source, AL10.AL_POSITION, x, y, z);
        AL10.alSourcef(source, AL10.AL_REFERENCE_DISTANCE, REFERENCE_DISTANCE);
        AL10.alSourcef(source, AL10.AL_MAX_DISTANCE, MAX_DISTANCE);
        AL10.alSourcef(source, AL10.AL_ROLLOFF_FACTOR, 1f);
        AL10.alSourcef(source, AL10.AL_GAIN, clampVolume(volume));
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
        return source;
    }

    private static float clampVolume(float v) {
        return Math.max(0f, Math.min(1f, v));
    }

    private int upload(short[] pcm) {
        int buffer = AL10.alGenBuffers();
        ShortBuffer data = memAllocShort(pcm.length);
        data.put(pcm).flip();
        AL10.alBufferData(buffer, AL10.AL_FORMAT_MONO16, data, SoundSynth.SAMPLE_RATE);
        memFree(data);
        allBuffers.add(buffer);
        return buffer;
    }

    /** Releases every OpenAL resource. Call once on shutdown. */
    public void destroy() {
        if (!enabled) return;
        for (int source : sources) {
            AL10.alDeleteSources(source);
        }
        for (int buffer : allBuffers) {
            AL10.alDeleteBuffers(buffer);
        }
        ALC10.alcMakeContextCurrent(0L);
        if (context != 0L) ALC10.alcDestroyContext(context);
        if (device != 0L) ALC10.alcCloseDevice(device);
        enabled = false;
    }
}
