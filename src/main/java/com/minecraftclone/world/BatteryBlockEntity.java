package com.minecraftclone.world;

import java.io.DataInput;
import java.io.DataOutput;
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
 * <p>The Battery Block does NOT have a player-accessible inventory — it is
 * wired up purely through block adjacency and the cable network.
 */
public final class BatteryBlockEntity implements BlockEntity, ProgressMachine, EuSource {

    public static final String TYPE = "battery_block";

    /** Maximum EU stored (≈ 25 coal × EU_PER_BURN_SECOND × COAL_BURN_TIME). */
    public static final float MAX_EU = 5_000f;

    private float eu;

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
        float space = MAX_EU - eu;
        float taken = Math.min(amount, space);
        eu += taken;
        return taken;
    }

    /** 0..1 fill fraction (drives the GUI arrow). */
    @Override public float progressFraction() { return Math.min(1f, eu / MAX_EU); }

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

    /** A CoalGenerator or BatteryBlock found on the last scan, used to charge this battery. */
    private CoalGeneratorEntity chargeSource;

    /** Called by the World when this entity is placed/loaded. */
    public void attach(int x, int y, int z, World world) {
        this.posX = x; this.posY = y; this.posZ = z;
        this.attached = true; this.world = world;
    }

    @Override
    public void tick(float dt) {
        if (dt <= 0 || eu >= MAX_EU) return;

        // Re-scan for a generator a couple of times per second.
        scanTimer -= dt;
        if (scanTimer <= 0f) {
            scanTimer = 0.5f;
            if (attached && world != null) chargeSource = findGenerator();
        }

        if (chargeSource == null || chargeSource.euStored() <= 0f) return;
        float want   = Math.min(dt * CoalGeneratorEntity.EU_PER_BURN_SECOND, MAX_EU - eu);
        float drawn  = chargeSource.drainEU(want);
        eu += drawn;
    }

    private static final int[][] FACES = {
        {1,0,0},{-1,0,0},{0,1,0},{0,-1,0},{0,0,1},{0,0,-1}
    };

    /**
     * Finds a Coal Generator to charge from — direct adjacency first,
     * then via the energy cable network.
     */
    private CoalGeneratorEntity findGenerator() {
        if (!attached || world == null) return null;
        for (int[] d : FACES) {
            BlockEntity e = world.blockEntityAt(posX+d[0], posY+d[1], posZ+d[2]);
            if (e instanceof CoalGeneratorEntity g) return g;
        }
        com.minecraftclone.world.pipes.PipeNetwork net =
                world.pipeNetworks().networkAt(
                        com.minecraftclone.world.pipes.PipeType.ENERGY, posX, posY, posZ);
        if (net == null) return null;
        for (long key : net.cells) {
            int cx = (int)(key & 0x1FFFFFL) << 21 >> 21;
            int cy = (int)((key >> 21) & 0x1FFFFFL) << 21 >> 21;
            int cz = (int)((key >> 42) & 0x1FFFFFL) << 21 >> 21;
            for (int[] d : FACES) {
                BlockEntity e = world.blockEntityAt(cx+d[0], cy+d[1], cz+d[2]);
                if (e instanceof CoalGeneratorEntity g) return g;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------
    // Persistence
    // ------------------------------------------------------------------

    @Override
    public void writeTo(DataOutput out) throws IOException {
        out.writeFloat(eu);
    }

    @Override
    public void readFrom(DataInput in) throws IOException {
        eu = in.readFloat();
    }
}
