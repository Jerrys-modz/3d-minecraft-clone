package com.minecraftclone.world;

import com.minecraftclone.player.StorageContainer;
import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/**
 * A placed coal-fired Steam Boiler: the heat source of the Steam Age.
 *
 * <p>Right-click with any furnace fuel to load it (single fuel slot). While
 * burning, it builds up a steam buffer measured in seconds of steam time
 * (capped). Adjacent steam machines - the Steam Furnace today - drain that
 * buffer while they work; an idle boiler keeps its steam indefinitely.
 *
 * <p>The entity implements {@link StorageContainer} with one fuel slot and
 * {@link ProgressMachine} so the HUD gauge machinery renders it like a lit
 * furnace front. State persists with its chunk.
 */
public final class SteamBoilerEntity implements BlockEntity, StorageContainer, ProgressMachine {

    public static final String TYPE = "steam_boiler";

    public static final int SLOT_FUEL = 0;
    public static final int SLOT_COUNT = 1;

    /** Seconds of steam the buffer can hold (about six coal's worth). */
    public static final float MAX_STEAM_SECONDS = Furnace.COAL_BURN_TIME * 6f;

    private BlockType fuelType;
    private int fuelCount;
    /** Remaining seconds of solid-fuel burn currently generating steam. */
    private float burnTime;
    private float burnDuration;
    /** Steam buffer, in seconds of machine-run-time it can supply. */
    private float steam;

    @Override
    public String type() { return TYPE; }

    @Override
    public BlockType blockType() { return BlockType.STEAM_BOILER; }

    /** A steaming boiler lights its surroundings like a torch. */
    @Override
    public int activeLightLevel() {
        return burnTime > 0 ? 13 : 0;
    }

    // -----------------------------------------------------------------------
    // Accessors
    // -----------------------------------------------------------------------

    /** Seconds of steam stored in the buffer. */
    public float steamSeconds() {
        return steam;
    }

    /**
     * Drains up to {@code seconds} of steam from the buffer (called by steam
     * machines). Returns the amount actually drained.
     */
    public float drainSteam(float seconds) {
        if (seconds <= 0) return 0f;
        float take = Math.min(seconds, steam);
        steam -= take;
        return take;
    }

    /**
     * Adds up to {@code count} units of {@code fuel} to the fuel slot if it is
     * a furnace fuel. Returns how many were accepted.
     */
    public int addFuel(BlockType fuel, int count) {
        if (!Furnace.isFuel(fuel) || count <= 0 || !isBlockValid()) return 0;
        if (fuelType != null && fuelType != fuel) return 0;
        int space = 64 - fuelCount;
        int take = Math.min(space, count);
        if (take <= 0) return 0;
        fuelType = fuel;
        fuelCount += take;
        return take;
    }

    /** 0..1 fraction of the current fuel burn remaining - drives the GUI flame. */
    @Override
    public float burnFraction() {
        return burnDuration <= 0 ? 0f : Math.min(1f, burnTime / burnDuration);
    }

    /** The boiler has no progress arrow of its own. */
    @Override
    public float progressFraction() {
        return 0f;
    }

    /** True while solid fuel is actively burning into steam. */
    public boolean isBurning() {
        return burnTime > 0;
    }

    private boolean isBlockValid() {
        return blockType() == BlockType.STEAM_BOILER;
    }

    // -----------------------------------------------------------------------
    // Simulation
    // -----------------------------------------------------------------------

    @Override
    public void tick(float dt) {
        if (dt <= 0) return;
        if (burnTime <= 0) {
            if (isBurningFuelAvailable()) {
                burnDuration = Furnace.fuelDuration(fuelType);
                burnTime = burnDuration;
                fuelCount--;
                if (fuelCount <= 0) fuelType = null;
            } else {
                return;
            }
        }
        // Burn down, converting time into steam until the buffer caps out.
        burnTime = Math.max(0f, burnTime - dt);
        steam = Math.min(MAX_STEAM_SECONDS, steam + dt);
    }

    private boolean isBurningFuelAvailable() {
        return fuelType != null && fuelCount > 0 && steam < MAX_STEAM_SECONDS;
    }

    // -----------------------------------------------------------------------
    // StorageContainer (single fuel slot)
    // -----------------------------------------------------------------------

    @Override
    public int size() {
        return SLOT_COUNT;
    }

    @Override
    public BlockType typeOf(int slot) {
        return slot == SLOT_FUEL ? fuelType : null;
    }

    @Override
    public int countOf(int slot) {
        return slot == SLOT_FUEL ? fuelCount : 0;
    }

    @Override
    public void setSlot(int slot, BlockType type, int count) {
        if (slot != SLOT_FUEL) return;
        if (type == null || count <= 0) {
            fuelType = null;
            fuelCount = 0;
        } else {
            if (!Furnace.isFuel(type)) return; // fuel slot only accepts real fuels
            fuelType = type;
            fuelCount = count;
        }
    }

    @Override
    public int add(BlockType type, int amount) {
        return addFuel(type, amount);
    }

    @Override
    public int getCount(BlockType type) {
        return fuelType == type ? fuelCount : 0;
    }

    // -----------------------------------------------------------------------
    // Persistence
    // -----------------------------------------------------------------------

    private static final byte PAYLOAD_VERSION = 1;

    @Override
    public void writeTo(DataOutput out) throws IOException {
        out.writeByte(PAYLOAD_VERSION);
        out.writeShort(fuelType == null ? 0 : fuelType.id);
        out.writeInt(fuelCount);
        out.writeFloat(burnTime);
        out.writeFloat(burnDuration);
        out.writeFloat(steam);
    }

    @Override
    public void readFrom(DataInput in) throws IOException {
        byte version = in.readByte();
        if (version != PAYLOAD_VERSION) {
            clearState();
            return;
        }
        int id = in.readUnsignedShort();
        fuelType = id == 0 ? null : BlockType.byId(id);
        fuelCount = Math.max(0, in.readInt());
        burnTime = Math.max(0f, in.readFloat());
        burnDuration = Math.max(0f, in.readFloat());
        steam = Math.max(0f, Math.min(MAX_STEAM_SECONDS, in.readFloat()));
    }

    private void clearState() {
        fuelType = null;
        fuelCount = 0;
        burnTime = 0f;
        burnDuration = 0f;
        steam = 0f;
    }
}
