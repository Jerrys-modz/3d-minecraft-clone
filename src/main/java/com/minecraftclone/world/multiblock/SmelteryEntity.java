package com.minecraftclone.world.multiblock;

import com.minecraftclone.world.BlockType;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/**
 * Block entity placed at a formed Smeltery's controller position.
 *
 * <p>While the smeltery is intact, {@link #isActive()} returns {@code true},
 * which causes the controller to render with its lit front tile (the glowing
 * orange mouth) and emit light at level {@link #activeLightLevel()}.
 *
 * <h3>Future work</h3>
 * <ul>
 *   <li>Input inventory: items placed into the smeltery via the Smeltery Drain
 *       or GUI slot.</li>
 *   <li>Molten metal tracking: a map of {@code TinkersMaterial → millibuckets}
 *       for each liquid currently in the smeltery.</li>
 *   <li>Fuel consumption: the smeltery needs lava under it (or a separate
 *       Smeltery Heater in later versions) to stay hot.</li>
 *   <li>Alloying: certain material combinations auto-alloy when co-molten.</li>
 *   <li>Casting: a Casting Table/Basin below the Drain pulls molten metal out
 *       onto a cast to produce tool parts.</li>
 * </ul>
 */
public final class SmelteryEntity extends MultiBlockEntity {

    /** Stable type name persisted with the entity. Must match {@link BlockEntities} registration. */
    public static final String TYPE = "smeltery";

    /** How brightly the active smeltery glows (same as a torch, 13/15). */
    private static final int LIGHT_LEVEL = 13;

    /**
     * Accumulated heat — increases while the smeltery is formed and lava is
     * present below, decreases when cold.  Not yet used for actual smelting;
     * reserved for future fuel logic.
     */
    private float heatAccum = 0f;

    /** No-arg constructor for {@link com.minecraftclone.world.BlockEntities} deserialization. */
    public SmelteryEntity() {
        super(null); // instance is null during load; MultiBlockManager re-registers on form
    }

    public SmelteryEntity(MultiBlockInstance instance) {
        super(instance);
    }

    @Override
    public String type() { return TYPE; }

    @Override
    public BlockType blockType() { return BlockType.SMELTERY_CONTROLLER; }

    @Override
    public int activeLightLevel() { return LIGHT_LEVEL; }

    @Override
    public void tick(float dt) {
        if (!formed) return;
        // Placeholder heat accumulation — future: check for lava below drain,
        // consume fuel, advance smelting queue, output to casting table.
        heatAccum = Math.min(heatAccum + dt * 0.2f, 100f);
    }

    // -----------------------------------------------------------------------
    // Persistence
    // -----------------------------------------------------------------------

    @Override
    public void writeTo(DataOutput out) throws IOException {
        super.writeTo(out);
        out.writeFloat(heatAccum);
    }

    @Override
    public void readFrom(DataInput in) throws IOException {
        super.readFrom(in);
        heatAccum = in.readFloat();
    }
}
