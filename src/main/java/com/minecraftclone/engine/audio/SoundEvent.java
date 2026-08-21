package com.minecraftclone.engine.audio;

/**
 * Every non-material-dependent sound effect in the game (see {@link Sounds}
 * for how each is synthesized, and {@link SoundMaterial}/{@link BlockAction}
 * for the block break/place/footstep sounds, which vary by material instead
 * of having one fixed sound each). Each event carries a {@link SoundCategory}
 * so the Audio settings tab's per-category sliders know which volume to mix
 * it against.
 */
public enum SoundEvent {
    UI_CLICK(SoundCategory.UI),      // a menu/inventory/crafting slot interaction
    UI_OPEN(SoundCategory.UI),       // opening the inventory or a container
    UI_CLOSE(SoundCategory.UI),      // closing the inventory or a container
    JUMP(SoundCategory.PLAYER),
    LAND(SoundCategory.PLAYER),      // a hard-enough fall landing
    SPLASH(SoundCategory.PLAYER),    // entering or leaving water
    EAT(SoundCategory.PLAYER),
    HURT(SoundCategory.PLAYER),      // the player takes damage, from any source
    DEATH(SoundCategory.PLAYER),
    ATTACK(SoundCategory.MOBS),      // a melee hit lands on a mob
    MOB_DEATH(SoundCategory.MOBS),
    TOOL_BREAK(SoundCategory.MACHINES), // a tool/sword wears out and breaks
    ITEM_PICKUP(SoundCategory.PLAYER),
    DOOR(SoundCategory.MACHINES),    // a door/trapdoor opens or closes
    CHEST_OPEN(SoundCategory.MACHINES),  // wooden chest lid creak + latch
    CHEST_CLOSE(SoundCategory.MACHINES), // wooden chest lid slam
    CRAFT(SoundCategory.MACHINES),   // a crafting/smelting output is taken
    RAIN(SoundCategory.AMBIENT),     // a looping precipitation hiss, loudness follows the weather
    THUNDER(SoundCategory.AMBIENT);  // a lightning flash in a thunderstorm

    private final SoundCategory category;

    SoundEvent(SoundCategory category) {
        this.category = category;
    }

    public SoundCategory category() {
        return category;
    }
}
