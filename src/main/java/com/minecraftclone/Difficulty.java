package com.minecraftclone;

/**
 * Minecraft-style world difficulty. Picked when you create a world (and stored
 * in that world's {@code world.txt}), then changeable later from the in-game
 * Options screen — never a global setting.
 * <ul>
 *   <li>{@link #PEACEFUL} - no hostile mobs; hunger/thirst don't drain; health
 *       regenerates quickly.</li>
 *   <li>{@link #EASY} - hostiles deal half damage; starvation stops at half
 *       health.</li>
 *   <li>{@link #NORMAL} - the default: standard spawn rates and damage.</li>
 *   <li>{@link #HARD} - hostiles hit harder and more often; starvation can kill.</li>
 * </ul>
 */
public enum Difficulty {
    PEACEFUL,
    EASY,
    NORMAL,
    HARD;

    /** Title-cased name for menus (Peaceful / Easy / Normal / Hard). */
    public String displayName() {
        String n = name();
        return n.charAt(0) + n.substring(1).toLowerCase();
    }

    @Override
    public String toString() {
        return displayName();
    }

    /** False on Peaceful: hostiles never spawn and existing ones despawn. */
    public boolean allowsHostileMobs() {
        return this != PEACEFUL;
    }

    /** Multiplier applied to melee hits and skeleton arrows. */
    public float mobDamageMultiplier() {
        return switch (this) {
            case PEACEFUL -> 0f;
            case EASY -> 0.5f;
            case NORMAL -> 1f;
            case HARD -> 1.5f;
        };
    }

    /** Multiplier on passive hunger/thirst drain (and cold hunger burn). 0 on Peaceful. */
    public float hungerDrainMultiplier() {
        return switch (this) {
            case PEACEFUL -> 0f;
            case EASY -> 0.5f;
            case NORMAL -> 1f;
            case HARD -> 1.5f;
        };
    }

    /**
     * Health floor starvation and dehydration won't go below. Hard (0) can kill;
     * Easy stops at half; Normal leaves a sliver.
     */
    public float starvationHealthFloor() {
        return switch (this) {
            case PEACEFUL -> Float.POSITIVE_INFINITY;
            case EASY -> 50f;
            case NORMAL -> 5f;
            case HARD -> 0f;
        };
    }

    /** Peaceful regenerates much faster than the other difficulties. */
    public float healthRegenMultiplier() {
        return this == PEACEFUL ? 8f : 1f;
    }

    /** Cap on loaded hostiles around the player. */
    public int maxHostiles() {
        return switch (this) {
            case PEACEFUL -> 0;
            case EASY -> 8;
            case NORMAL -> 12;
            case HARD -> 18;
        };
    }

    /**
     * 1-in-N night ticks try to spawn a hostile. Lower is more frequent.
     * Peaceful never rolls a spawn.
     */
    public int hostileSpawnOdds() {
        return switch (this) {
            case PEACEFUL -> Integer.MAX_VALUE;
            case EASY -> 32;
            case NORMAL -> 20;
            case HARD -> 12;
        };
    }
}
