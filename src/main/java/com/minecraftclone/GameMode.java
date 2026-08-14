package com.minecraftclone;

/**
 * Minecraft-style game modes. Each changes how the player interacts with the
 * world and whether they can be hurt:
 * <ul>
 *   <li>{@link #SURVIVAL} - the default: gather, craft, take damage.</li>
 *   <li>{@link #CREATIVE} - instant break, infinite placement, flight, no damage.</li>
 *   <li>{@link #ADVENTURE} - no breaking or placing, but still vulnerable.</li>
 *   <li>{@link #SPECTATOR} - no-clip flight through the world, no interaction or damage.</li>
 * </ul>
 */
public enum GameMode {
    SURVIVAL,
    CREATIVE,
    ADVENTURE,
    SPECTATOR;

    /** True if this mode can't be hurt and doesn't drain hunger/stamina. */
    public boolean isInvulnerable() {
        return this == CREATIVE || this == SPECTATOR;
    }

    /** True if the player can break blocks in this mode. */
    public boolean canBreak() {
        return this == SURVIVAL || this == CREATIVE;
    }

    /** True if the player can place blocks in this mode. */
    public boolean canPlace() {
        return this == SURVIVAL || this == CREATIVE;
    }

    /** True if placing blocks doesn't consume inventory (creative). */
    public boolean isCreative() {
        return this == CREATIVE;
    }

    /** True if the player can fly freely through the world (spectator no-clip). */
    public boolean isSpectator() {
        return this == SPECTATOR;
    }
}
