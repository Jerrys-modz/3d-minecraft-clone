package com.minecraftclone.world.tinkers;

import com.minecraftclone.player.ItemStack;
import com.minecraftclone.world.BlockEntity;
import com.minecraftclone.world.BlockType;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/**
 * Block entity for the {@link BlockType#TOOL_STATION}.
 *
 * <p>Persists up to {@link ToolStationGui#INPUT_SLOTS} tool-part slots across
 * chunk saves and loads.  The GUI model ({@link ToolStationGui}) drives all
 * assembly logic; this entity is purely responsible for persistence and
 * identity.
 *
 * <h3>Persistence format (version 1)</h3>
 * <pre>
 *   byte   — format version (1)
 *   byte   — slot count written (always {@link ToolStationGui#INPUT_SLOTS})
 *   For each slot:
 *     byte  — slot kind: 0 = empty, 1 = tinkers part
 *     If kind == 1:
 *       byte  — ToolPartType ordinal
 *       short — material BlockType id
 * </pre>
 */
public final class ToolStationEntity implements BlockEntity {

    /** Stable type string registered in {@link com.minecraftclone.world.BlockEntities}. */
    public static final String TYPE = "tool_station";

    private static final byte FORMAT_VERSION = 1;

    private static final byte KIND_EMPTY = 0;
    private static final byte KIND_PART  = 1;

    private final ToolStationGui gui = new ToolStationGui();

    /**
     * The GUI model.  The renderer and player-interaction code read and write
     * slot state through this object.
     */
    public ToolStationGui gui() { return gui; }

    @Override public String type()         { return TYPE; }
    @Override public BlockType blockType() { return BlockType.TOOL_STATION; }

    // -----------------------------------------------------------------------
    // Persistence
    // -----------------------------------------------------------------------

    @Override
    public void writeTo(DataOutput out) throws IOException {
        out.writeByte(FORMAT_VERSION);
        out.writeByte(ToolStationGui.INPUT_SLOTS);

        for (int i = 0; i < ToolStationGui.INPUT_SLOTS; i++) {
            ItemStack s = gui.slot(i);
            if (s.isTinkersPart()) {
                TinkersItem.Part p = s.tinkersPart();
                if (p != null) {
                    out.writeByte(KIND_PART);
                    out.writeByte(p.shape.ordinal());
                    out.writeShort(p.material.id);
                    continue;
                }
            }
            out.writeByte(KIND_EMPTY);
        }
    }

    @Override
    public void readFrom(DataInput in) throws IOException {
        byte version = in.readByte();
        if (version != FORMAT_VERSION) {
            gui.clearAll();
            return;
        }

        int slotCount = in.readUnsignedByte();
        int load      = Math.min(slotCount, ToolStationGui.INPUT_SLOTS);

        ToolPartType[] shapes = ToolPartType.values();

        for (int i = 0; i < load; i++) {
            int kind = in.readUnsignedByte();
            if (kind == KIND_PART) {
                int shapeOrd = in.readUnsignedByte();
                int matId    = in.readUnsignedShort();
                ToolPartType shape = (shapeOrd < shapes.length) ? shapes[shapeOrd] : null;
                BlockType    mat   = BlockType.byId(matId);
                if (shape != null && mat != null) {
                    gui.setSlot(i, ItemStack.tinkersPart(new TinkersItem.Part(shape, mat)));
                } else {
                    gui.setSlot(i, ItemStack.EMPTY);
                }
            }
            // KIND_EMPTY — slot stays empty (already cleared in ToolStationGui constructor)
        }

        // Skip any extra slots from a saved format with more slots than the current version.
        for (int i = load; i < slotCount; i++) {
            int kind = in.readUnsignedByte();
            if (kind == KIND_PART) {
                in.readUnsignedByte();   // shape ordinal
                in.readUnsignedShort();  // material id
            }
        }
    }
}
