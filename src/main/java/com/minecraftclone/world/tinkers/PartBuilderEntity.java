package com.minecraftclone.world.tinkers;

import com.minecraftclone.player.ItemStack;
import com.minecraftclone.world.BlockEntity;
import com.minecraftclone.world.BlockType;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/**
 * Block entity for the {@link BlockType#PART_BUILDER}.
 *
 * <p>Persists the material-slot contents and the last-selected part shape
 * across chunk saves and loads.  The GUI model ({@link PartBuilderGui}) drives
 * all crafting logic; this entity is purely responsible for persistence and
 * identity.
 *
 * <h3>Persistence format (version 1)</h3>
 * <pre>
 *   byte   — format version (1)
 *   byte   — selected shape ordinal, or -1 if none
 *   short  — material BlockType id, or 0 if slot empty
 *   byte   — material stack count (0 if slot empty)
 * </pre>
 */
public final class PartBuilderEntity implements BlockEntity {

    /** Stable type string registered in {@link com.minecraftclone.world.BlockEntities}. */
    public static final String TYPE = "part_builder";

    private static final byte FORMAT_VERSION = 1;

    private final PartBuilderGui gui = new PartBuilderGui();

    /**
     * The GUI model.  The renderer and player-interaction code read and write
     * the slot/shape state through this object.
     */
    public PartBuilderGui gui() { return gui; }

    @Override public String type()      { return TYPE; }
    @Override public BlockType blockType() { return BlockType.PART_BUILDER; }

    // -----------------------------------------------------------------------
    // Persistence
    // -----------------------------------------------------------------------

    @Override
    public void writeTo(DataOutput out) throws IOException {
        out.writeByte(FORMAT_VERSION);

        // Selected shape (-1 = none)
        ToolPartType shape = gui.selectedShape();
        out.writeByte(shape == null ? -1 : shape.ordinal());

        // Material slot (type id + count)
        BlockType mat   = gui.materialType();
        int       count = gui.materialCount();
        if (mat != null && count > 0) {
            out.writeShort(mat.id);
            out.writeByte(count);
        } else {
            out.writeShort(0);
            out.writeByte(0);
        }
    }

    @Override
    public void readFrom(DataInput in) throws IOException {
        byte version = in.readByte();
        if (version != FORMAT_VERSION) {
            // Unknown version — skip remaining bytes is not possible without
            // knowing the length, so reset to empty state.
            gui.reset();
            return;
        }

        // Shape selection
        int shapeOrd = in.readByte(); // signed byte, -1 = none
        if (shapeOrd >= 0 && shapeOrd < ToolPartType.values().length) {
            gui.setSelectedShape(ToolPartType.values()[shapeOrd]);
        } else {
            gui.setSelectedShape(null);
        }

        // Material slot
        int matId  = in.readUnsignedShort();
        int count  = in.readUnsignedByte();
        if (matId > 0 && count > 0) {
            BlockType mat = BlockType.byId(matId);
            if (mat != null) {
                gui.setMaterial(ItemStack.of(mat, count));
            }
        }
    }
}
