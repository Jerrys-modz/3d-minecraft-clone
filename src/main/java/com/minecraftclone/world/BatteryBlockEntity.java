package com.minecraftclone.world;

import com.minecraftclone.player.Inventory;
import com.minecraftclone.player.StorageContainer;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.EOFException;
import java.io.IOException;

/**
 * A placed Battery Block: stores EU so generators can run ahead and machines
 * can burst-draw without the generator keeping perfect pace.
 *
 * <h3>How it works</h3>
 * <ul>
 *   <li>Passively accepts EU from an adjacent {@link CoalGeneratorEntity}
 *       or another Battery Block via the cable network every tick.</li>
 *   <li>Exposes its stored EU to adjacent machines (directly or via cables)
 *       through {@link #drainEU(float)} — the same API as the generator.</li>
 *   <li>Capacity: {@value #MAX_EU} EU (about 25 coal burned in a generator).</li>
 * </ul>
 *
 * <p>It has one slot for {@link BlockType#LV_BATTERY} items (the GTNH
 * "battery buffer"): each inserted battery adds {@value #EU_PER_BATTERY} EU
 * of capacity, up to {@value #MAX_BATTERIES} batteries. Charge lives in the
 * block, so pulling batteries out clamps the stored EU to the new capacity —
 * that energy is lost.
 */
public final class BatteryBlockEntity implements BlockEntity, ProgressMachine, EuSource, StorageContainer {

    public static final String TYPE = "battery_block";

    /** Base EU capacity with no battery items inserted. */
    public static final float MAX_EU = 5_000f;

    /** Extra capacity per inserted LV battery. */
    public static final float EU_PER_BATTERY = 10_000f;

    /** Battery items beyond this count add no capacity. */
    public static final int MAX_BATTERIES = 16;

    public static final int SLOT_BATTERIES = 0;
    public static final int SLOT_COUNT = 1;

    private float eu;
    private BlockType slotType;
    private int slotCount;

    /** Current capacity: base plus inserted batteries. */
    public float capacity() {
        int batteries = slotType == BlockType.LV_BATTERY ? Math.min(slotCount, MAX_BATTERIES) : 0;
        return MAX_EU + batteries * EU_PER_BATTERY;
    }

    /** Number of LV batteries currently adding capacity. */
    public int batteryCount() {
        return slotType == BlockType.LV_BATTERY ? Math.min(slotCount, MAX_BATTERIES) : 0;
    }

    // ------------------------------------------------------------------
    // BlockEntity
    // ------------------------------------------------------------------

    @Override public String type()         { return TYPE; }
    @Override public BlockType blockType() { return BlockType.BATTERY_BLOCK; }
    @Override public int activeLightLevel() { return 0; }

    // ------------------------------------------------------------------
    // Accessors
    // ------------------------------------------------------------------

    /** EU currently stored. */
    public float euStored() { return eu; }

    /**
     * Drains up to {@code amount} EU from the battery.
     * Returns how much was actually drawn (may be less when nearly empty).
     */
    public float drainEU(float amount) {
        float drawn = Math.min(amount, eu);
        eu -= drawn;
        return drawn;
    }

    /**
     * Charges the battery by up to {@code amount} EU.
     * Returns how much was actually absorbed (may be less when nearly full).
     */
    public float chargeEU(float amount) {
        float space = capacity() - eu;
        float taken = Math.min(amount, space);
        eu += taken;
        return taken;
    }

    /** 0..1 fill fraction (drives the GUI arrow). */
    @Override public float progressFraction() { return Math.min(1f, eu / capacity()); }

    /** The flame is not meaningful for a battery; always returns 0. */
    @Override public float burnFraction() { return 0f; }

    @Override public boolean isActive() { return eu > 0f; }

    // ------------------------------------------------------------------
    // Simulation — passive charging from an adjacent/networked generator
    // ------------------------------------------------------------------

    private World world;
    private int posX, posY, posZ;
    private boolean attached;
    private float scanTimer;

    /** The Coal Generator found on the last scan, used to charge this battery. */
    private CoalGeneratorEntity chargeSource;
    /** Share of generator EU that survives the cable run into this battery. */
    private float chargeEfficiency = 1f;

    /** Called by the World when this entity is placed/loaded. */
    public void attach(int x, int y, int z, World world) {
        this.posX = x; this.posY = y; this.posZ = z;
        this.attached = true; this.world = world;
    }

    @Override
    public void tick(float dt) {
        if (dt <= 0 || eu >= capacity()) return;

        // Re-scan for a generator a couple of times per second.
        scanTimer -= dt;
        if (scanTimer <= 0f) {
            scanTimer = 0.5f;
            if (attached && world != null) {
                EuNetwork.Feed feed = EuNetwork.resolve(world, posX, posY, posZ, true);
                chargeSource = (CoalGeneratorEntity) feed.source();
                chargeEfficiency = feed.efficiency();
            }
        }

        if (chargeSource == null || chargeSource.euStored() <= 0f) return;
        float want   = Math.min(dt * CoalGeneratorEntity.EU_PER_BURN_SECOND, capacity() - eu);
        float drawn  = chargeSource.drainEU(want);
        eu = Math.min(capacity(), eu + drawn * chargeEfficiency);   // cable loss on the way in
    }

    // ------------------------------------------------------------------
    // StorageContainer — the battery slot
    // ------------------------------------------------------------------

    @Override public int size() { return SLOT_COUNT; }

    @Override
    public BlockType typeOf(int slot) { return slot == SLOT_BATTERIES ? slotType : null; }

    @Override
    public int countOf(int slot) { return slot == SLOT_BATTERIES ? slotCount : 0; }

    @Override
    public void setSlot(int slot, BlockType type, int count) {
        if (slot != SLOT_BATTERIES) return;
        slotType = (type == null || count <= 0) ? null : type;
        slotCount = slotType == null ? 0 : count;
        eu = Math.min(eu, capacity());   // pulled batteries take their share of charge with them
    }

    @Override
    public int add(BlockType type, int amount) {
        if (type != BlockType.LV_BATTERY || amount <= 0) return amount;
        if (slotType != null && slotType != type) return amount;
        int space = Math.min(MAX_BATTERIES, Inventory.maxStack(type)) - slotCount;
        int take = Math.min(space, amount);
        if (take <= 0) return amount;
        slotType = type;
        slotCount += take;
        return amount - take;
    }

    @Override
    public int getCount(BlockType type) { return type != null && type == slotType ? slotCount : 0; }

    // ------------------------------------------------------------------
    // Persistence — v0 was just the EU float; the slot was appended later.
    // ------------------------------------------------------------------

    @Override
    public void writeTo(DataOutput out) throws IOException {
        out.writeFloat(eu);
        out.writeShort(slotType == null ? 0 : slotType.id);
        out.writeInt(slotCount);
    }

    @Override
    public void readFrom(DataInput in) throws IOException {
        eu = in.readFloat();
        slotType = null;
        slotCount = 0;
        try {
            int id = in.readUnsignedShort();
            int count = in.readInt();
            slotType = id == 0 || count <= 0 ? null : BlockType.byId(id);
            slotCount = slotType == null ? 0 : count;
        } catch (EOFException oldSave) {
            // Pre-battery-slot payload: just the charge.
        }
        eu = Math.max(0f, Math.min(eu, capacity()));
    }
}
