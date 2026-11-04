package com.minecraftclone.world;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/**
 * A placed Distillation Tower (MV): refines {@value #OIL_PER_BATCH} mB of
 * crude oil into {@value #DIESEL_PER_BATCH} mB of diesel,
 * {@value #HEAVY_PER_BATCH} mB of heavy fuel and — if a third tank can take
 * it — {@value #LUBRICANT_PER_BATCH} mB of lubricant (otherwise vented). Every fluid comes from or goes
 * to Steel Tanks that are adjacent or piped, one tank per fluid; it waits
 * until there is room for both products. MV-rated: safe on gold cable.
 *
 * <p>A single block here; GTNH's multi-block tower is a later upgrade.
 */
public final class DistillationTowerEntity implements BlockEntity, ProgressMachine, WorldAttached {

    public static final String TYPE = "distillation_tower";

    public static final int OIL_PER_BATCH = 1000;
    public static final int DIESEL_PER_BATCH = 600;
    public static final int HEAVY_PER_BATCH = 300;
    /** Optional third fraction; vented if no tank can take it. */
    public static final int LUBRICANT_PER_BATCH = 100;
    public static final float BATCH_SECONDS = 10f;
    public static final float EU_PER_BATCH = 600f;

    private World world;
    private int posX, posY, posZ;
    private boolean attached;
    private float progress;
    private float sourceTimer;
    private EuNetwork.Feed feed = EuNetwork.Feed.NONE;
    private boolean active;

    @Override
    public void attach(int x, int y, int z, World world) {
        this.posX = x; this.posY = y; this.posZ = z;
        this.attached = true; this.world = world;
    }

    @Override public String type()            { return TYPE; }
    @Override public BlockType blockType()    { return BlockType.DISTILLATION_TOWER; }
    @Override public boolean isActive()       { return active; }
    @Override public int activeLightLevel()   { return active ? 10 : 0; }
    @Override public float burnFraction()     { return active ? 1f : 0f; }
    @Override public float progressFraction() { return Math.min(1f, progress / BATCH_SECONDS); }

    @Override
    public void tick(float dt) {
        active = false;
        if (dt <= 0 || !attached || world == null) return;
        sourceTimer -= dt;
        if (sourceTimer <= 0f) {
            sourceTimer = 0.5f;
            feed = EuNetwork.resolve(world, posX, posY, posZ);
        }
        if (feed.source() == null || feed.source().euStored() <= 0f) return;
        if (feed.voltage().exceeds(CableTier.Voltage.MV)) {   // MV-rated: HV without an MV Transformer blows it
            world.reportOverload(posX, posY, posZ);
            return;
        }
        if (!canRunBatch(world, posX, posY, posZ)) return;

        float euPerSecond = EU_PER_BATCH / BATCH_SECONDS;
        float drawn = feed.source().drainEU(Math.min(euPerSecond * dt * feed.throughput(), feed.source().euStored()));
        if (drawn <= 0f) return;
        active = true;
        progress += drawn * feed.efficiency() / euPerSecond;
        if (progress < BATCH_SECONDS) return;
        progress -= BATCH_SECONDS;
        runBatch(world, posX, posY, posZ);
    }

    /** True if the tanks around {@code (x, y, z)} can take one batch: oil in, room for diesel and heavy fuel. */
    public static boolean canRunBatch(World world, int x, int y, int z) {
        FluidTankEntity oil = FluidNetwork.findTankWith(world, x, y, z, Fluid.OIL, OIL_PER_BATCH);
        FluidTankEntity diesel = FluidNetwork.findTankFor(world, x, y, z, Fluid.DIESEL, DIESEL_PER_BATCH);
        FluidTankEntity heavy = FluidNetwork.findTankFor(world, x, y, z, Fluid.HEAVY_FUEL, HEAVY_PER_BATCH, diesel);
        return oil != null && diesel != null && heavy != null;
    }

    /**
     * Distils one batch through the tanks around {@code (x, y, z)} (shared with
     * the multi-block tower). Returns false, changing nothing, if it can't run.
     */
    public static boolean runBatch(World world, int x, int y, int z) {
        FluidTankEntity oil = FluidNetwork.findTankWith(world, x, y, z, Fluid.OIL, OIL_PER_BATCH);
        FluidTankEntity diesel = FluidNetwork.findTankFor(world, x, y, z, Fluid.DIESEL, DIESEL_PER_BATCH);
        FluidTankEntity heavy = FluidNetwork.findTankFor(world, x, y, z, Fluid.HEAVY_FUEL, HEAVY_PER_BATCH, diesel);
        if (oil == null || diesel == null || heavy == null) return false;
        oil.drain(Fluid.OIL, OIL_PER_BATCH);
        diesel.fill(Fluid.DIESEL, DIESEL_PER_BATCH);
        heavy.fill(Fluid.HEAVY_FUEL, HEAVY_PER_BATCH);
        FluidTankEntity lube = FluidNetwork.findTankFor(world, x, y, z, Fluid.LUBRICANT,
                LUBRICANT_PER_BATCH, diesel, heavy, oil);
        if (lube != null) lube.fill(Fluid.LUBRICANT, LUBRICANT_PER_BATCH);
        return true;
    }

    @Override
    public void writeTo(DataOutput out) throws IOException {
        out.writeFloat(progress);
    }

    @Override
    public void readFrom(DataInput in) throws IOException {
        progress = Math.max(0f, in.readFloat());
    }
}
