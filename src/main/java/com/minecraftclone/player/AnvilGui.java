package com.minecraftclone.player;

import com.minecraftclone.world.BlockType;
import com.minecraftclone.world.Mining;

/**
 * Pure model for the Anvil GUI — a two-input repair station that restores a
 * vanilla tool's durability in exchange for one unit of its base material.
 *
 * <h3>Slot layout</h3>
 * <pre>
 *   Slot 0  — tool input   (the damaged tool to repair)
 *   Slot 1  — material     (one unit of the tool's repair material)
 *   [output] — repaired tool (virtual, read from {@link #outputType()})
 * </pre>
 *
 * When both slots are filled with a compatible pair, {@link #canRepair()} is
 * {@code true} and {@link #consume()} removes 1 material, clears the tool slot,
 * and returns the tool type.  The caller is responsible for resetting
 * {@link ToolDurability} and handing the item back to the player.
 *
 * <p>Tinkers' Construct assembled tools are intentionally not handled here —
 * they have their own repair mechanic (replace individual worn-out parts at
 * the Tool Station), keeping the two systems cleanly separate.
 *
 * <p>This class is a <em>pure model</em>; all rendering is handled separately.
 */
public final class AnvilGui {

    /** Slot index for the tool to be repaired (relative, 0-based within the anvil). */
    public static final int SLOT_TOOL = 0;
    /** Slot index for the repair material. */
    public static final int SLOT_MATERIAL = 1;

    private BlockType toolType;
    private int       toolCount;
    private BlockType materialType;
    private int       materialCount;

    // -----------------------------------------------------------------------
    // Accessors
    // -----------------------------------------------------------------------

    public BlockType toolType()     { return toolType; }
    public int       toolCount()    { return toolCount; }
    public BlockType materialType() { return materialType; }
    public int       materialCount(){ return materialCount; }

    /**
     * The type shown in the (virtual) output slot when {@link #canRepair()} is
     * {@code true}, or {@code null} when inputs aren't a valid pair.
     */
    public BlockType outputType() { return canRepair() ? toolType : null; }

    // -----------------------------------------------------------------------
    // Mutators
    // -----------------------------------------------------------------------

    /** Sets the tool slot; clears it when {@code type} is {@code null} or {@code count ≤ 0}. */
    public void setTool(BlockType type, int count) {
        if (type == null || count <= 0) {
            toolType  = null;
            toolCount = 0;
        } else {
            toolType  = type;
            toolCount = count;
        }
    }

    /** Sets the material slot; clears it when {@code type} is {@code null} or {@code count ≤ 0}. */
    public void setMaterial(BlockType type, int count) {
        if (type == null || count <= 0) {
            materialType  = null;
            materialCount = 0;
        } else {
            materialType  = type;
            materialCount = count;
        }
    }

    // -----------------------------------------------------------------------
    // Repair logic
    // -----------------------------------------------------------------------

    /**
     * Returns {@code true} when the inputs form a valid repair pair: the tool
     * slot holds a vanilla tool and the material slot holds the correct repair
     * material for that tool's tier.
     */
    public boolean canRepair() {
        // Require exactly one tool: repairing consumes the whole tool slot and returns
        // exactly one repaired item.  Allowing toolCount > 1 would silently delete the
        // surplus tools, so we reject that case and require the player to split the stack.
        if (toolType == null || toolCount != 1) return false;
        if (Mining.toolStats(toolType) == null) return false; // not a repairable tool
        BlockType needed = repairMaterialOf(toolType);
        return needed != null && needed == materialType && materialCount >= 1;
    }

    /**
     * Consumes 1 repair material, clears the tool slot, and returns the tool
     * type so the caller can reset its {@link ToolDurability} entry and hand
     * the item back to the player.
     *
     * @return the repaired tool type, or {@code null} if {@link #canRepair()} is {@code false}
     */
    public BlockType consume() {
        if (!canRepair()) return null;
        BlockType repaired = toolType;
        toolType  = null;
        toolCount = 0;
        materialCount--;
        if (materialCount <= 0) {
            materialType  = null;
            materialCount = 0;
        }
        return repaired;
    }

    // -----------------------------------------------------------------------
    // Static helpers
    // -----------------------------------------------------------------------

    /**
     * Returns the single repair material for the given tool (determined by its
     * tier), or {@code null} for non-tools or unrecognised tiers.
     *
     * <ul>
     *   <li>Wood tier  → {@link BlockType#PLANKS}</li>
     *   <li>Stone tier → {@link BlockType#STONE}</li>
     *   <li>Iron tier  → {@link BlockType#IRON_INGOT}</li>
     *   <li>Diamond tier → {@link BlockType#DIAMOND}</li>
     * </ul>
     */
    public static BlockType repairMaterialOf(BlockType tool) {
        Mining.ToolStats stats = Mining.toolStats(tool);
        if (stats == null) return null;
        return switch (stats.tier()) {
            case Mining.TIER_WOOD    -> BlockType.PLANKS;
            case Mining.TIER_STONE   -> BlockType.STONE;
            case Mining.TIER_IRON    -> BlockType.IRON_INGOT;
            case Mining.TIER_DIAMOND -> BlockType.DIAMOND;
            default                  -> null;
        };
    }
}
