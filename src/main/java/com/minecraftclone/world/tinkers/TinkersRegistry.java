package com.minecraftclone.world.tinkers;

import com.minecraftclone.world.BlockType;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Runtime registry of tool materials for the Tinkers' Construct system.
 *
 * <p>Any {@link BlockType} that can serve as a tool material (typically an
 * ingot, gem, or similarly shaped cast product) calls {@link #register} once
 * at startup.  No enum or crafting table changes are needed — the Part Builder
 * and Tool Station GUIs enumerate {@link #materials()} dynamically.
 *
 * <h3>Adding a new material</h3>
 * <pre>
 *   // In your ore/material initialiser:
 *   TinkersRegistry.register(BlockType.MY_INGOT,
 *       new TinkersMaterialStats(durability, miningSpeed, miningTier, 0xRRGGBB));
 * </pre>
 * That is the entire change.  All tool combinations appear automatically in the
 * Part Builder, Tool Station, and item texture system.
 */
public final class TinkersRegistry {

    /** Ordered so iteration is stable (useful for UI tabs and tests). */
    private static final Map<BlockType, TinkersMaterialStats> MATERIALS = new LinkedHashMap<>();

    static {
        // Vanilla materials pre-registered at startup.
        // Tiers match Mining.TIER_*: 0=HAND, 1=WOOD, 2=STONE, 3=IRON, 4=DIAMOND.
        register(BlockType.PLANKS,     new TinkersMaterialStats(  59, 1.0f, 1, 0x7B5A2D));
        register(BlockType.STONE,      new TinkersMaterialStats( 131, 2.0f, 2, 0x9A9A9A));
        register(BlockType.IRON_INGOT, new TinkersMaterialStats( 500, 3.0f, 3, 0xD4D4D4));
        register(BlockType.GOLD_INGOT, new TinkersMaterialStats(  32, 5.0f, 3, 0xFFD700));
        register(BlockType.DIAMOND,    new TinkersMaterialStats(2000, 4.0f, 4, 0x5CE7E7));
        // New ores added in later phases can call register() here or from their
        // own initialiser class — no modifications to this file needed.
    }

    private TinkersRegistry() {}

    /**
     * Registers {@code material} as a valid Tinkers' tool material.
     * Throws if the type is already registered (prevents accidents with reload).
     */
    public static void register(BlockType material, TinkersMaterialStats stats) {
        if (MATERIALS.containsKey(material)) {
            throw new IllegalStateException("Tinkers material already registered: " + material);
        }
        MATERIALS.put(material, stats);
    }

    /** True if {@code type} is a registered Tinkers material. */
    public static boolean isMaterial(BlockType type) {
        return MATERIALS.containsKey(type);
    }

    /**
     * Stats for {@code material}, or {@code null} if it hasn't been registered.
     * Always check with {@link #isMaterial} or null-check the result before use.
     */
    public static TinkersMaterialStats statsFor(BlockType material) {
        return MATERIALS.get(material);
    }

    /** All registered materials, in registration order. */
    public static Collection<BlockType> materials() {
        return Collections.unmodifiableCollection(MATERIALS.keySet());
    }
}
