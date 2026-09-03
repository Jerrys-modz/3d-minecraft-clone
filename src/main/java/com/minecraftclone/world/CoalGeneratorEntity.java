package com.minecraftclone.world;

import com.minecraftclone.player.Inventory;
import com.minecraftclone.player.ItemStack;
import com.minecraftclone.player.StorageContainer;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/**
 * A placed Coal Generator: the Electric Age equivalent of the Steam Boiler.
 * Burns solid fuel (coal, wood, etc.) and accumulates EU in an internal
 * buffer that adjacent machines and cables drain.
 *
 * <h3>How it works</h3>
 * <ul>
 *   <li><b>Fuel slot</b> (slot 0): any item that burns in a furnace.</li>
 *   <li><b>EU production</b>: {@value #EU_PER_BURN_SECOND} EU/s while burning,
 *       capped at {@value #MAX_EU} EU in the buffer.</li>
 *   <li><b>Output</b>: EU drawn by adjacent {@link ElectricFurnaceEntity} or
 *       {@link BatteryBlockEntity} (directly or through cables).</li>
 * </ul>
 *
 * <p>Implements {@link StorageContainer} with one fuel slot and
 * {@link ProgressMachine} so the existing furnace HUD renders it: the flame
 * gauge shows remaining burn time, the arrow shows buffer fill level.
 */
public final class CoalGeneratorEntity implements BlockEntity, StorageContainer, ProgressMachine, EuSource {

    public static final String TYPE = "coal_generator";

    public static final int SLOT_FUEL  = 0;
    public static final int SLOT_COUNT = 1;

    /** EU produced per second of active burning. */
    public static final float EU_PER_BURN_SECOND = 200f;

    /** Maximum EU the internal buffer can hold (~50 coal's worth at COAL_BURN_TIME). */
    public static final float MAX_EU = 10_000f;

    private BlockType fuelType;
    private int fuelCount;
    /** Remaining seconds of solid-fuel burn currently generating EU. */
    private float burnTime;
    private float burnDuration;
    /** EU stored in the internal buffer. */
    private float eu;

    // ------------------------------------------------------------------
    // BlockEntity
    // ------------------------------------------------------------------

    @Override public String type()      { return TYPE; }
    @Override public BlockType blockType() { return BlockType.COAL_GENERATOR; }

    /** A running generator glows like a furnace. */
    @Override public int activeLightLevel() { return burnTime > 0 ? 13 : 0; }

    // ------------------------------------------------------------------
    // Accessors (used by machines + tests)
    // ------------------------------------------------------------------

    /** EU stored in the buffer. */
    @Override
    public float euStored() { return eu; }

    /** True while the generator is actively burning fuel. */
    public boolean isBurning() { return burnTime > 0f; }

    /**
     * Drains up to {@code amount} EU from the buffer. Returns how much was
     * actually drawn (may be less if the buffer is low).
     */
    @Override
    public float drainEU(float amount) {
        float drawn = Math.min(amount, eu);
        eu -= drawn;
        return drawn;
    }

    /** 0..1 burn-time indicator for the GUI flame. */
    @Override
    public float burnFraction() {
        return burnDuration > 0 ? Math.min(1f, burnTime / burnDuration) : 0f;
    }

    /** 0..1 buffer-fill indicator for the GUI arrow. */
    @Override
    public float progressFraction() {
        return MAX_EU > 0 ? Math.min(1f, eu / MAX_EU) : 0f;
    }

    /** True while the buffer isn't full (so the generator has reason to run). */
    @Override
    public boolean isActive() { return eu < MAX_EU || burnTime > 0f; }

    // ------------------------------------------------------------------
    // Simulation
    // ------------------------------------------------------------------

    @Override
    public void tick(float dt) {
        if (dt <= 0) return;

        // Start a new burn if idle and buffer isn't full.
        if (burnTime <= 0f && eu < MAX_EU && fuelType != null && fuelCount > 0) {
            float duration = Furnace.fuelDuration(fuelType);
            if (duration > 0f) {
                burnTime     = duration;
                burnDuration = duration;
                fuelCount--;
                if (fuelCount == 0) fuelType = null;
            }
        }

        if (burnTime > 0f) {
            float step  = Math.min(dt, burnTime);
            burnTime   -= step;
            float produced = step * EU_PER_BURN_SECOND;
            eu = Math.min(eu + produced, MAX_EU);
        }
    }

    // ------------------------------------------------------------------
    // StorageContainer — 1-slot fuel inventory
    // ------------------------------------------------------------------

    @Override public int size() { return SLOT_COUNT; }

    @Override
    public BlockType typeOf(int slot) {
        if (slot != SLOT_FUEL) return null;
        return fuelType;
    }

    @Override
    public int countOf(int slot) {
        if (slot != SLOT_FUEL) return 0;
        return fuelCount;
    }

    @Override
    public void setSlot(int slot, BlockType type, int count) {
        if (slot == SLOT_FUEL) {
            fuelType  = count > 0 ? type : null;
            fuelCount = Math.max(0, count);
        }
    }

    @Override
    public int add(BlockType type, int amount) {
        if (!canInsert(SLOT_FUEL, type) || amount <= 0) return amount;
        int max   = Inventory.maxStack(type);
        int space = max - fuelCount;
        if (space <= 0 || (fuelType != null && fuelType != type)) return amount;
        int take = Math.min(space, amount);
        fuelType   = type;
        fuelCount += take;
        return amount - take;
    }

    @Override
    public int getCount(BlockType type) {
        return (fuelType == type) ? fuelCount : 0;
    }

    /** True if {@code item} can be burned in this slot. */
    public boolean canInsert(int slot, BlockType item) {
        return slot == SLOT_FUEL && Furnace.isFuel(item);
    }

    /** Players cannot pull fuel back out of a running generator. */
    public boolean canExtract(int slot) { return false; }

    /** Insert fuel — returns whether the insert succeeded. */
    public boolean insert(int slot, BlockType item, int count) {
        if (!canInsert(slot, item) || count <= 0) return false;
        if (fuelType == null || fuelType == item) {
            fuelType   = item;
            fuelCount += count;
            return true;
        }
        return false;
    }

    public ItemStack extract(int slot, int max) { return null; }

    // ------------------------------------------------------------------
    // Persistence
    // ------------------------------------------------------------------

    @Override
    public void writeTo(DataOutput out) throws IOException {
        out.writeShort(fuelType != null ? fuelType.id : 0);
        out.writeInt(fuelCount);
        out.writeFloat(burnTime);
        out.writeFloat(burnDuration);
        out.writeFloat(eu);
    }

    @Override
    public void readFrom(DataInput in) throws IOException {
        int fuelId    = in.readUnsignedShort();
        fuelType  = (fuelId == 0) ? null : BlockType.byId(fuelId);
        fuelCount = in.readInt();
        burnTime  = in.readFloat();
        burnDuration = in.readFloat();
        eu        = in.readFloat();
    }
}
