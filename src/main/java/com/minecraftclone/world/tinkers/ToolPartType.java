package com.minecraftclone.world.tinkers;

import com.minecraftclone.world.Mining.ToolKind;

/**
 * The shape of a Tinkers' Construct tool part.
 *
 * <p>Each value bundles the crafting-grid template (with {@code 'M'} as a
 * placeholder for the material ingredient), the {@link ToolKind} the finished
 * tool produces, and an index constant used as the {@code paintHead} style in
 * {@link com.minecraftclone.engine.graphics.ItemTextures}.
 *
 * <p>Adding a new part shape is a one-liner here; crafting recipes and
 * Creative Catalog entries update automatically via
 * {@link com.minecraftclone.world.BlockType#toolPart}.
 */
public enum ToolPartType {

    //               assembledKind          style  patternRows (3×3, 'M'=material, '.'=empty)
    PICK_HEAD   (ToolKind.PICKAXE,   0,  "MM.", "M..", "..."),  // L-shape prongs
    AXE_HEAD    (ToolKind.AXE,       1,  "MM.", "MM.", "..."),  // solid 2×2 blade
    SWORD_BLADE (ToolKind.SWORD,     2,  "M..", "M..", "..."),  // vertical bar
    SHOVEL_HEAD (ToolKind.SHOVEL,    3,  ".M.", ".M.", "..."),  // narrow paddle
    TOOL_ROD    (null,               4,  ".P.", ".P.", "...");  // planks-only handle

    /**
     * The tool kind produced by assembling this head with a TOOL_ROD, or
     * {@code null} for TOOL_ROD itself.
     */
    public final ToolKind assembledKind;
    /**
     * Paint-style index passed to
     * {@code ItemTextures.paintTinkersHead(color, style)} so each head shape
     * has a distinct silhouette.
     */
    public final int paintStyle;
    /**
     * Three-row crafting grid template.  {@code 'M'} is replaced at recipe
     * registration time with the material's
     * {@link TinkersMaterial#craftingChar}.  {@code 'P'} stays literal
     * (planks) for TOOL_ROD.
     */
    public final String[] patternRows;

    ToolPartType(ToolKind assembledKind, int paintStyle, String... patternRows) {
        this.assembledKind = assembledKind;
        this.paintStyle    = paintStyle;
        this.patternRows   = patternRows;
    }

    /** True for head-shaped parts that can be assembled into a tool. */
    public boolean isHead() { return assembledKind != null; }
}
