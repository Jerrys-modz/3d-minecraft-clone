package com.minecraftclone.world;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/**
 * A placed Oil Pump (MV): drills down to the {@link BlockType#OIL_DEPOSIT}
 * blocks beneath it — within {@value #RADIUS} blocks sideways, any depth —
 * and pumps {@value #MB_PER_CYCLE} mB of crude oil per cycle into a Steel
 * Tank that is adjacent or piped. Each deposit holds {@value #MB_PER_DEPOSIT}
 * mB and turns back into stone when drained, so depletion is saved with the
 * chunk itself. MV-rated: safe on gold cable.
 */
public final class OilPumpEntity implements BlockEntity, ProgressMachine, WorldAttached {

    public static final String TYPE = "oil_pump";

    public static final int RADIUS = 4;
    public static final int MB_PER_DEPOSIT = 1000;
    public static final int MB_PER_CYCLE = 250;
    public static final float CYCLE_SECONDS = 4f;
    public static final float EU_PER_CYCLE = 200f;

    private World world;
    private int posX, posY, posZ;
    private boolean attached;

    private float progress;
    private float scanTimer;
    private float sourceTimer;
    private EuNetwork.Feed feed = EuNetwork.Feed.NONE;
    private boolean active;

    /** Current target deposit (valid when {@link #hasTarget}). */
    private int tx, ty, tz;
    private boolean hasTarget;
    /** mB already drawn from the current target. */
    private int drawnFromTarget;

    @Override
    public void attach(int x, int y, int z, World world) {
        this.posX = x; this.posY = y; this.posZ = z;
        this.attached = true; this.world = world;
    }

    @Override public String type()          { return TYPE; }
    @Override public BlockType blockType()  { return BlockType.OIL_PUMP; }
    @Override public boolean isActive()     { return active; }
    @Override public float burnFraction()   { return active ? 1f : 0f; }
    @Override public float progressFraction() { return Math.min(1f, progress / CYCLE_SECONDS); }

    /** True when a deposit below is being drained. */
    public boolean hasDeposit() { return hasTarget; }

    /** Depth of the current target, or -1. */
    public int targetY() { return hasTarget ? ty : -1; }

    @Override
    public void tick(float dt) {
        active = false;
        if (dt <= 0 || !attached || world == null) return;

        if (hasTarget && world.getBlock(tx, ty, tz) != BlockType.OIL_DEPOSIT) {
            hasTarget = false;     // mined out from under us
            drawnFromTarget = 0;
        }
        scanTimer -= dt;
        if (!hasTarget && scanTimer <= 0f) {
            scanTimer = 5f;
            findDeposit();
        }
        sourceTimer -= dt;
        if (sourceTimer <= 0f) {
            sourceTimer = 0.5f;
            feed = EuNetwork.resolve(world, posX, posY, posZ);
        }
        if (!hasTarget || feed.source() == null || feed.source().euStored() <= 0f) return;
        FluidTankEntity tank = FluidNetwork.findTankFor(world, posX, posY, posZ, Fluid.OIL, MB_PER_CYCLE);
        if (tank == null) return;

        float euPerSecond = EU_PER_CYCLE / CYCLE_SECONDS;
        float drawn = feed.source().drainEU(Math.min(euPerSecond * dt * feed.throughput(), feed.source().euStored()));
        if (drawn <= 0f) return;
        active = true;
        progress += drawn * feed.efficiency() / euPerSecond;
        if (progress < CYCLE_SECONDS) return;
        progress -= CYCLE_SECONDS;
        tank.fill(Fluid.OIL, MB_PER_CYCLE);
        drawnFromTarget += MB_PER_CYCLE;
        if (drawnFromTarget >= MB_PER_DEPOSIT) {
            world.setBlockAfterTick(tx, ty, tz, BlockType.STONE);
            hasTarget = false;
            drawnFromTarget = 0;
            scanTimer = 0f;
        }
    }

    /** Nearest deposit below the pump (closest depth first, then closest sideways). */
    private void findDeposit() {
        for (int y = posY - 1; y >= 1; y--) {
            int bestD = Integer.MAX_VALUE;
            for (int dx = -RADIUS; dx <= RADIUS; dx++) {
                for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                    if (world.getBlock(posX + dx, y, posZ + dz) != BlockType.OIL_DEPOSIT) continue;
                    int d = dx * dx + dz * dz;
                    if (d < bestD) {
                        bestD = d;
                        tx = posX + dx; ty = y; tz = posZ + dz;
                        hasTarget = true;
                    }
                }
            }
            if (hasTarget) return;
        }
    }

    @Override
    public void writeTo(DataOutput out) throws IOException {
        out.writeFloat(progress);
        out.writeBoolean(hasTarget);
        out.writeInt(tx); out.writeInt(ty); out.writeInt(tz);
        out.writeInt(drawnFromTarget);
    }

    @Override
    public void readFrom(DataInput in) throws IOException {
        progress = Math.max(0f, in.readFloat());
        hasTarget = in.readBoolean();
        tx = in.readInt(); ty = in.readInt(); tz = in.readInt();
        drawnFromTarget = Math.max(0, in.readInt());
    }
}
