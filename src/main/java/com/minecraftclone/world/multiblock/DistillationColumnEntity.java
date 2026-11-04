package com.minecraftclone.world.multiblock;

import com.minecraftclone.world.BlockType;
import com.minecraftclone.world.DistillationTowerEntity;
import com.minecraftclone.world.EuNetwork;
import com.minecraftclone.world.ProgressMachine;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/**
 * The formed Distillation Column's controller: the single-block tower's
 * recipe ({@link DistillationTowerEntity#runBatch}) run once per storey each
 * cycle, so throughput scales with height. The controller sits in a wall with
 * only one open face, so tanks and power may connect either at the controller
 * or at the casing block directly below it (no hatches yet). MV-rated.
 */
public final class DistillationColumnEntity extends MultiBlockEntity implements ProgressMachine {

    public static final String TYPE = "distillation_column";

    private float progress;
    private float sourceTimer;
    private EuNetwork.Feed feed = EuNetwork.Feed.NONE;
    private boolean working;
    private int lastBatches;

    /** Deserialisation constructor (re-formed through the manager's scan). */
    public DistillationColumnEntity() {
        super(null);
        formed = false;
    }

    public DistillationColumnEntity(MultiBlockInstance instance) {
        super(instance);
    }

    @Override public String type()          { return TYPE; }
    @Override public BlockType blockType()  { return BlockType.DISTILLATION_CONTROLLER; }
    @Override public int activeLightLevel() { return working ? 10 : 0; }
    @Override public boolean isActive()     { return formed && working; }
    @Override public float burnFraction()   { return working ? 1f : 0f; }
    @Override public float progressFraction() {
        return Math.min(1f, progress / DistillationTowerEntity.BATCH_SECONDS);
    }

    /** Storeys of core = parallel batches per cycle (0 when unformed). */
    public int storeys() {
        Object s = instance == null ? null : instance.get(DistillationColumnDefinition.STOREYS_KEY);
        return formed && s instanceof Integer n ? n : 0;
    }

    /** Batches completed in the last finished cycle. */
    public int lastBatches() { return lastBatches; }

    @Override
    public void tick(float dt) {
        working = false;
        int storeys = storeys();
        if (dt <= 0 || storeys == 0 || world == null) return;
        int x = instance.controllerX, y = instance.controllerY, z = instance.controllerZ;
        sourceTimer -= dt;
        if (sourceTimer <= 0f) {
            sourceTimer = 0.5f;
            feed = EuNetwork.resolve(world, x, y, z);
            if (feed.source() == null) feed = EuNetwork.resolve(world, x, y - 1, z);
        }
        if (feed.source() == null || feed.source().euStored() <= 0f) return;
        if (feed.voltage().exceeds(com.minecraftclone.world.CableTier.Voltage.MV)) {   // MV-rated
            world.reportOverload(x, y, z);
            return;
        }
        int ioY = DistillationTowerEntity.canRunBatch(world, x, y, z) ? y
                : DistillationTowerEntity.canRunBatch(world, x, y - 1, z) ? y - 1 : Integer.MIN_VALUE;
        if (ioY == Integer.MIN_VALUE) return;

        // EU for every storey's batch, drawn over one batch time.
        float euPerSecond = DistillationTowerEntity.EU_PER_BATCH * storeys / DistillationTowerEntity.BATCH_SECONDS;
        float drawn = feed.source().drainEU(Math.min(euPerSecond * dt * feed.throughput(), feed.source().euStored()));
        if (drawn <= 0f) return;
        working = true;
        progress += drawn * feed.efficiency() / euPerSecond;
        if (progress < DistillationTowerEntity.BATCH_SECONDS) return;
        progress -= DistillationTowerEntity.BATCH_SECONDS;
        lastBatches = 0;
        for (int i = 0; i < storeys && DistillationTowerEntity.runBatch(world, x, ioY, z); i++) lastBatches++;
    }

    @Override
    public void writeTo(DataOutput out) throws IOException {
        super.writeTo(out);
        out.writeFloat(progress);
    }

    @Override
    public void readFrom(DataInput in) throws IOException {
        super.readFrom(in);
        progress = Math.max(0f, in.readFloat());
    }
}
