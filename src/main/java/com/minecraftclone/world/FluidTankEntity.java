package com.minecraftclone.world;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/**
 * A placed Steel Tank ({@value #CAPACITY_MB} mB) or Steel Drum
 * ({@value #DRUM_CAPACITY_MB} mB): stores a single
 * {@link Fluid}. Filled and emptied by hand with tin cells, and drawn from by
 * machines that are adjacent or connected through fluid pipes
 * (see {@link FluidNetwork}).
 */
public final class FluidTankEntity implements BlockEntity {

    public static final String TYPE = "steel_tank";

    /** Capacity in millibuckets (32 cells). */
    public static final int CAPACITY_MB = 32_000;

    public static final String DRUM_TYPE = "steel_drum";
    /** Drum capacity in millibuckets (16 cells) — cheaper, smaller. */
    public static final int DRUM_CAPACITY_MB = 16_000;

    private final boolean drum;

    /** A Steel Tank. */
    public FluidTankEntity() {
        this(false);
    }

    /** A Steel Tank, or a Steel Drum when {@code drum}. */
    public FluidTankEntity(boolean drum) {
        this.drum = drum;
    }

    /**
     * Whole cells of fluid this container holds — what it drops as filled
     * cells when broken (a part-cell remainder is lost).
     */
    public int wholeCells() {
        return fluid() == null ? 0 : amount / Fluid.CELL_MB;
    }

    /** Capacity of this container in mB. */
    public int capacity() {
        return drum ? DRUM_CAPACITY_MB : CAPACITY_MB;
    }

    private Fluid fluid;
    private int amount;

    @Override public String type()         { return drum ? DRUM_TYPE : TYPE; }
    @Override public BlockType blockType() { return drum ? BlockType.STEEL_DRUM : BlockType.STEEL_TANK; }
    @Override public void tick(float dt)   { }

    /** The stored fluid, or null when empty. */
    public Fluid fluid() { return amount > 0 ? fluid : null; }

    /** Stored amount in mB. */
    public int amount() { return amount; }

    /** True if {@code mB} of {@code f} would fit entirely. */
    public boolean canFill(Fluid f, int mB) {
        return f != null && mB > 0 && (amount == 0 || fluid == f) && amount + mB <= capacity();
    }

    /** Adds up to {@code mB} of {@code f}; returns how much was accepted. */
    public int fill(Fluid f, int mB) {
        if (f == null || mB <= 0 || (amount > 0 && fluid != f)) return 0;
        int take = Math.min(mB, capacity() - amount);
        if (take <= 0) return 0;
        fluid = f;
        amount += take;
        return take;
    }

    /** True if at least {@code mB} of {@code f} is stored. */
    public boolean has(Fluid f, int mB) {
        return amount >= mB && fluid == f && amount > 0;
    }

    /** Removes up to {@code mB} of {@code f}; returns how much was drained. */
    public int drain(Fluid f, int mB) {
        if (f == null || fluid != f || mB <= 0) return 0;
        int take = Math.min(mB, amount);
        amount -= take;
        if (amount == 0) fluid = null;
        return take;
    }

    @Override
    public void writeTo(DataOutput out) throws IOException {
        out.writeByte(fluid() == null ? -1 : fluid.ordinal());
        out.writeInt(amount);
    }

    @Override
    public void readFrom(DataInput in) throws IOException {
        int ord = in.readByte();
        int amt = in.readInt();
        Fluid[] all = Fluid.values();
        fluid = ord >= 0 && ord < all.length ? all[ord] : null;
        amount = fluid == null ? 0 : Math.max(0, Math.min(capacity(), amt));
    }
}
