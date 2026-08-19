package com.minecraftclone.world.tinkers;

/**
 * Materials available for Tinkers' Construct tool parts and assembled tools.
 *
 * <p>Each entry carries everything a downstream system needs in one place:
 * durability, mining tier, mining speed, ingot crafting character, and the
 * RGB colour used to tint the material's item texture. Adding a new material
 * is a one-liner here — BlockType enum entries, crafting recipes, Mining
 * stats, and Creative Catalog listings are all driven generically off this
 * enum via {@link com.minecraftclone.world.BlockType#toolPart} and
 * {@link com.minecraftclone.world.BlockType#assembledTool}.
 *
 * <h3>Tier constants</h3>
 * Match {@code Mining.TIER_*}: 0=HAND, 1=WOOD, 2=STONE, 3=IRON, 4=DIAMOND.
 */
public enum TinkersMaterial {

    //              durability  miningSpd  miningTier  craftChar  color      displayName
    WOOD    (  59,   1.0f,   1,  'P',  0x7B5A2D, "Wood"),
    STONE   ( 131,   2.0f,   2,  'K',  0x9A9A9A, "Stone"),
    IRON    ( 500,   3.0f,   3,  'I',  0xD4D4D4, "Iron"),
    GOLD    (  32,   5.0f,   3,  'M',  0xFFD700, "Gold"),
    DIAMOND (2000,   4.0f,   4,  'D',  0x5CE7E7, "Diamond");

    /** Base durability for a tool whose primary part is made from this material. */
    public final int durability;
    /** Mining-speed multiplier for a head made from this material. */
    public final float miningSpeed;
    /**
     * Mining tier (0=HAND … 4=DIAMOND).  Determines which blocks can be
     * harvested.  Mirrors {@code Mining.TIER_*} without creating a circular
     * class dependency.
     */
    public final int miningTier;
    /**
     * Single character used in crafting grid patterns to represent this
     * material's primary ingredient (ingot, gem, or plank).
     * Must be registered in {@link com.minecraftclone.player.Crafting} CHARS.
     */
    public final char craftingChar;
    /** 0xRRGGBB colour used by {@link com.minecraftclone.engine.graphics.ItemTextures}. */
    public final int color;
    /** Human-readable name for tooltips / Creative Catalog tab labels. */
    public final String displayName;

    TinkersMaterial(int durability, float miningSpeed, int miningTier,
                    char craftingChar, int color, String displayName) {
        this.durability   = durability;
        this.miningSpeed  = miningSpeed;
        this.miningTier   = miningTier;
        this.craftingChar = craftingChar;
        this.color        = color;
        this.displayName  = displayName;
    }
}
