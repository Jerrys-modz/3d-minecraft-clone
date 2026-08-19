package com.minecraftclone.world.tinkers;

import com.minecraftclone.world.Mining.ToolKind;

/**
 * The shape of a Tinkers' Construct tool part.
 *
 * <p>Each value names the {@link ToolKind} produced when this head is assembled
 * with a tool rod at the Tool Station, plus a paint-style index used by
 * {@link com.minecraftclone.engine.graphics.ItemTextures} to give each shape
 * a distinct silhouette.
 *
 * <p>Parts are assembled at the Tool Station only — there are no crafting-table
 * recipes for them; {@code patternRows} has been intentionally removed.
 *
 * <h3>Adding a new part shape</h3>
 * Add a new enum constant here with the appropriate {@link ToolKind} (or
 * {@code null} for non-head parts).  The Part Builder GUI enumerates all values
 * dynamically, so no other file needs to change.
 */
public enum ToolPartType {

    // -----------------------------------------------------------------------
    // Head parts — each assembles into a distinct tool kind when paired with
    // a TOOL_ROD (and optionally a BINDING) at the Tool Station.
    // -----------------------------------------------------------------------

    //                assembledKind        style
    PICK_HEAD   (ToolKind.PICKAXE,    0),
    AXE_HEAD    (ToolKind.AXE,        1),
    SWORD_BLADE (ToolKind.SWORD,      2),
    SHOVEL_HEAD (ToolKind.SHOVEL,     3),

    // -----------------------------------------------------------------------
    // Structural / non-head parts — no assembledKind of their own; they
    // contribute bonus stats (durability, modifiers) when included as extras.
    // -----------------------------------------------------------------------

    /** Standard handle; required partner for every head. */
    TOOL_ROD    (null,                4),

    /**
     * Structural connector placed between head and rod.  Optional at the
     * Tool Station (slot 2+); provides a small durability bonus from its
     * material.
     */
    BINDING     (null,                5),

    /**
     * Wide, heavy head used by broad mining tools (Hammer, Excavator).
     * Paired with a Tough Rod rather than a standard Tool Rod.
     * Not yet assembler-linked (no ToolKind yet); reserved for Phase 1 tools.
     */
    LARGE_PLATE (null,                6),

    /**
     * Reinforced rod for broad/heavy tools.  Provides significantly more
     * durability bonus than a standard Tool Rod when used as the rod layer.
     */
    TOUGH_ROD   (null,                7);

    // -----------------------------------------------------------------------

    /**
     * The tool kind produced by assembling this head with a TOOL_ROD at the
     * Tool Station, or {@code null} for non-head parts (TOOL_ROD, BINDING,
     * LARGE_PLATE, TOUGH_ROD, …).
     */
    public final ToolKind assembledKind;

    /**
     * Paint-style index passed to
     * {@code ItemTextures.paintTinkersHead(color, style)} so each shape has
     * a distinct silhouette.
     */
    public final int paintStyle;

    ToolPartType(ToolKind assembledKind, int paintStyle) {
        this.assembledKind = assembledKind;
        this.paintStyle    = paintStyle;
    }

    /** True for head-shaped parts that can be assembled into a tool (assembledKind != null). */
    public boolean isHead() { return assembledKind != null; }

    /**
     * True for rod-like parts that occupy the rod slot in the Tool Station
     * (standard TOOL_ROD or the heavier TOUGH_ROD).
     */
    public boolean isRod() { return this == TOOL_ROD || this == TOUGH_ROD; }
}
