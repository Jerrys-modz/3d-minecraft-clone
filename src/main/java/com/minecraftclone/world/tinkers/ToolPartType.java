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
 */
public enum ToolPartType {

    //               assembledKind        style
    PICK_HEAD   (ToolKind.PICKAXE,   0),
    AXE_HEAD    (ToolKind.AXE,       1),
    SWORD_BLADE (ToolKind.SWORD,     2),
    SHOVEL_HEAD (ToolKind.SHOVEL,    3),
    TOOL_ROD    (null,               4);

    /**
     * The tool kind produced by assembling this head with a TOOL_ROD at the
     * Tool Station, or {@code null} for TOOL_ROD itself.
     */
    public final ToolKind assembledKind;
    /**
     * Paint-style index passed to
     * {@code ItemTextures.paintTinkersHead(color, style)} so each head shape
     * has a distinct silhouette.
     */
    public final int paintStyle;

    ToolPartType(ToolKind assembledKind, int paintStyle) {
        this.assembledKind = assembledKind;
        this.paintStyle    = paintStyle;
    }

    /** True for head-shaped parts that can be assembled into a tool. */
    public boolean isHead() { return assembledKind != null; }
}
