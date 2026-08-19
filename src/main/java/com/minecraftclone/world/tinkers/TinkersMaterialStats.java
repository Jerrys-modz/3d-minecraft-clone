package com.minecraftclone.world.tinkers;

/**
 * Per-material stats used by {@link TinkersRegistry}.
 *
 * <p>Every {@link com.minecraftclone.world.BlockType} that can be smelted in
 * the Smeltery and cast at the Casting Table/Basin into tool parts should call
 * {@link TinkersRegistry#register} with one of these records.  No enum changes
 * needed — this is a pure runtime registry.
 *
 * @param baseDurability Base durability a head made from this material gives.
 * @param miningSpeed    Mining-speed multiplier when used as the head material.
 * @param miningTier     Tier gate (matches {@code Mining.TIER_*}: 0=hand … 4=diamond).
 * @param color          0xRRGGBB tint applied to the procedural item texture.
 */
public record TinkersMaterialStats(
    int   baseDurability,
    float miningSpeed,
    int   miningTier,
    int   color
) {}
