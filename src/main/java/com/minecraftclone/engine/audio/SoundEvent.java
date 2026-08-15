package com.minecraftclone.engine.audio;

/**
 * Every non-material-dependent sound effect in the game (see {@link Sounds}
 * for how each is synthesized, and {@link SoundMaterial}/{@link BlockAction}
 * for the block break/place/footstep sounds, which vary by material instead
 * of having one fixed sound each).
 */
public enum SoundEvent {
    UI_CLICK,      // a menu/inventory/crafting slot interaction
    UI_OPEN,       // opening the inventory or a container
    UI_CLOSE,      // closing the inventory or a container
    JUMP,
    LAND,          // a hard-enough fall landing
    SPLASH,        // entering or leaving water
    EAT,
    HURT,          // the player takes damage, from any source
    DEATH,
    ATTACK,        // a melee hit lands on a mob
    MOB_DEATH,
    TOOL_BREAK,    // a tool/sword wears out and breaks
    ITEM_PICKUP,
    DOOR,          // a door/trapdoor opens or closes
    CRAFT          // a crafting/smelting output is taken
}
