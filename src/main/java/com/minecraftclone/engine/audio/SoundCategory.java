package com.minecraftclone.engine.audio;

/**
 * Broad groupings of {@link SoundEvent}s (and the material-based block
 * sounds) that the settings menu's Audio tab exposes as separate volume
 * sliders, on top of the overall master volume. Every sound belongs to
 * exactly one category - see {@link SoundEvent#category()} and
 * {@link AudioEngine#categoryFor(BlockAction)}.
 * <p>
 * {@link #MUSIC} and {@link #AMBIENT} have no sounds wired up yet (there is
 * no music or ambience system in the game), but their sliders still work:
 * a future music/ambience system just needs to call
 * {@link AudioEngine#setCategoryVolume} with the right category and the
 * settings menu, persistence, and mixing are already in place.
 */
public enum SoundCategory {
    MUSIC,
    AMBIENT,
    MOBS,
    MACHINES, // block break/place, doors, tool wear, crafting - interacting with the world
    PLAYER,   // footsteps, jumping/landing, splashing, eating, taking damage, dying, picking up items
    UI
}
