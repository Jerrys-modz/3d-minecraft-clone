package com.minecraftclone.world.multiblock;

import com.minecraftclone.world.BlockEntity;
import com.minecraftclone.world.BlockType;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/**
 * A {@link BlockEntity} that lives at the controller position of a formed
 * {@link MultiBlockInstance}.  While the structure is intact, {@link #isActive}
 * returns {@code true}, which causes the controller block to use its
 * {@code litFrontTile} (e.g. a glowing smeltery mouth) and any
 * {@code activeLightLevel} to illuminate the surroundings.
 *
 * <p>Subclass this for each multi-block that needs per-tick behaviour
 * (e.g. {@link SmelteryEntity} for smelting items) and override
 * {@link #tick}, {@link #writeTo}, and {@link #readFrom}.
 *
 * <p>Breaking any block inside the bounding box causes the
 * {@link MultiBlockManager} to call {@link MultiBlockDefinition#onDeform},
 * which removes this entity — so the controller immediately reverts to its
 * inactive tile.
 */
public abstract class MultiBlockEntity implements BlockEntity {

    protected final MultiBlockInstance instance;
    /** True while the multi-block structure is formed. Set to false on deform. */
    protected boolean formed = true;

    protected MultiBlockEntity(MultiBlockInstance instance) {
        this.instance = instance;
    }

    /** Which block this entity lives in — the controller block type. */
    @Override
    public abstract BlockType blockType();

    @Override
    public boolean isActive() { return formed; }

    /** Mark this entity as deformed (the structure was broken). */
    public void deform() { formed = false; }

    @Override
    public void writeTo(DataOutput out) throws IOException {
        // Note: 'formed' is written for backward compatibility but ignored on load
        out.writeBoolean(formed);
    }

    @Override
    public void readFrom(DataInput in) throws IOException {
        // Discard the persisted 'formed' flag; entities always deserialize as unformed
        // and are re-formed through MultiBlockManager's normal scan process
        in.readBoolean();
        formed = false;
    }
}
