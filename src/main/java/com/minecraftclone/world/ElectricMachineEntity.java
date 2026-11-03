package com.minecraftclone.world;

import com.minecraftclone.player.Inventory;
import com.minecraftclone.player.StorageContainer;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/**
 * Shared body of the LV item processors (Macerator, Ore Washer, Thermal
 * Centrifuge): one input slot, one byproduct slot and one output slot laid
 * out like the furnace GUI, powered by EU found through {@link EuNetwork}.
 *
 * <p>Subclasses supply the recipe ({@link #primaryOutput}, {@link #primaryYield},
 * {@link #byproductOf}), the cost/speed, and optionally an extra
 * environmental requirement such as adjacent water ({@link #environmentReady}).
 *
 * <h3>Slots</h3>
 * <ul>
 *   <li><b>0 — input</b></li>
 *   <li><b>1 — byproduct</b>: drawn where a furnace's fuel goes; filled
 *       automatically, never accepts inserts via shift-click.</li>
 *   <li><b>2 — output</b></li>
 * </ul>
 *
 * <p>Byproducts use a deterministic credit accumulator rather than a random
 * roll: a 25% byproduct is emitted on exactly every 4th operation, so saves
 * and multiplayer snapshots stay reproducible.
 */
public abstract class ElectricMachineEntity implements BlockEntity, StorageContainer, ProgressMachine {

    public static final int SLOT_INPUT     = 0;
    public static final int SLOT_BYPRODUCT = 1;
    public static final int SLOT_OUTPUT    = 2;
    public static final int SLOT_COUNT     = 3;

    private final BlockType[] types  = new BlockType[SLOT_COUNT];
    private final int[]       counts = new int[SLOT_COUNT];
    private float progress;
    private float lastEuFraction;
    private float byproductCredit;

    protected World world;
    protected int posX, posY, posZ;
    protected boolean attached;
    private float sourceCheckTimer;
    private float drawRate = 1f;
    /** EU source found on the last scan (package-visible for test injection). */
    EuSource euSource;

    /** Called by the World so the entity can locate its EU source. */
    public void attach(int x, int y, int z, World world) {
        this.posX = x; this.posY = y; this.posZ = z;
        this.attached = true; this.world = world;
    }

    // ------------------------------------------------------------------
    // Recipe hooks
    // ------------------------------------------------------------------

    /** The main product of {@code input}, or null if this machine can't process it. */
    protected abstract BlockType primaryOutput(BlockType input);

    /** How many {@link #primaryOutput} items one input yields (≥ 1). */
    protected abstract int primaryYield(BlockType input);

    /** Optional byproduct for {@code input}; null for none. */
    protected BlockType byproductOf(BlockType input) { return null; }

    /** Fraction (0..1) of operations that also emit a byproduct. */
    protected float byproductChance() { return 0f; }

    /** EU drawn per completed operation. */
    protected abstract float euPerOperation();

    /** Seconds per operation at full power through direct adjacency. */
    protected abstract float operationSeconds();

    /** Extra world precondition (e.g. adjacent water). Default: always ready. */
    protected boolean environmentReady() { return true; }

    /** True if {@code item} belongs in this machine's input slot. */
    public boolean acceptsInput(BlockType item) {
        return primaryOutput(item) != null;
    }

    // ------------------------------------------------------------------
    // ProgressMachine
    // ------------------------------------------------------------------

    @Override public int activeLightLevel()   { return isActive() ? 10 : 0; }
    @Override public boolean isActive()       { return lastEuFraction > 0f; }
    @Override public float burnFraction()     { return lastEuFraction; }
    @Override public float progressFraction() {
        return Math.min(1f, Math.max(0f, progress / operationSeconds()));
    }

    // ------------------------------------------------------------------
    // Simulation
    // ------------------------------------------------------------------

    /** True if the input can be processed into the empty/matching output slot without overflow. */
    public boolean canProcess() {
        BlockType input = types[SLOT_INPUT];
        BlockType output = primaryOutput(input);
        if (output == null) return false;
        BlockType held = types[SLOT_OUTPUT];
        return held == null
                || (held == output && counts[SLOT_OUTPUT] + primaryYield(input) <= Inventory.maxStack(output));
    }

    @Override
    public void tick(float dt) {
        if (dt <= 0) return;

        sourceCheckTimer -= dt;
        if (sourceCheckTimer <= 0f) {
            sourceCheckTimer = 0.5f;
            if (attached && world != null) {
                euSource = EuNetwork.findSource(world, posX, posY, posZ);
                drawRate = EuNetwork.drawRate(world, posX, posY, posZ);
            }
        }

        if (euSource == null || euSource.euStored() <= 0f || !canProcess() || !environmentReady()) {
            lastEuFraction = 0f;
            return;
        }

        float seconds = operationSeconds();
        float euPerSecond = euPerOperation() / seconds;
        float drawn = euSource.drainEU(Math.min(euPerSecond * dt * drawRate, euSource.euStored()));
        if (drawn <= 0f) {
            lastEuFraction = 0f;
            return;
        }
        lastEuFraction = Math.max(0.05f, Math.min(1f, euSource.euStored() / EuNetwork.capacityOf(euSource)));

        progress += drawn / euPerSecond;
        while (progress >= seconds && canProcess()) {
            progress -= seconds;
            completeOperation();
        }
        if (!canProcess()) progress = Math.min(progress, seconds);
    }

    private void completeOperation() {
        BlockType input = types[SLOT_INPUT];
        BlockType output = primaryOutput(input);
        int yield = primaryYield(input);
        BlockType by = byproductOf(input);
        counts[SLOT_INPUT]--;
        if (counts[SLOT_INPUT] == 0) types[SLOT_INPUT] = null;
        if (types[SLOT_OUTPUT] == output) {
            counts[SLOT_OUTPUT] += yield;
        } else {
            types[SLOT_OUTPUT] = output;
            counts[SLOT_OUTPUT] = yield;
        }
        if (by == null || byproductChance() <= 0f) return;
        byproductCredit += byproductChance();
        if (byproductCredit < 1f - 1e-4f) return;
        byproductCredit -= 1f;
        // A full or mismatched byproduct slot simply loses the bonus, like the steam washer.
        if (types[SLOT_BYPRODUCT] == null) {
            types[SLOT_BYPRODUCT] = by;
            counts[SLOT_BYPRODUCT] = 1;
        } else if (types[SLOT_BYPRODUCT] == by && counts[SLOT_BYPRODUCT] < Inventory.maxStack(by)) {
            counts[SLOT_BYPRODUCT]++;
        }
    }

    /** True if any face-adjacent block is (unfrozen) water. */
    protected boolean hasAdjacentWater() {
        if (!attached || world == null) return false;
        int[][] faces = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
        for (int[] d : faces) {
            BlockType n = world.getBlock(posX + d[0], posY + d[1], posZ + d[2]);
            if (n != null && n.isWater()) return true;
        }
        return false;
    }

    // ------------------------------------------------------------------
    // StorageContainer
    // ------------------------------------------------------------------

    @Override public int size() { return SLOT_COUNT; }

    @Override
    public BlockType typeOf(int slot) {
        return slot >= 0 && slot < SLOT_COUNT ? types[slot] : null;
    }

    @Override
    public int countOf(int slot) {
        return slot >= 0 && slot < SLOT_COUNT ? counts[slot] : 0;
    }

    @Override
    public void setSlot(int slot, BlockType type, int count) {
        if (slot < 0 || slot >= SLOT_COUNT) return;
        if (type == null || count <= 0) {
            types[slot] = null;
            counts[slot] = 0;
        } else {
            types[slot] = type;
            counts[slot] = count;
        }
    }

    @Override
    public int add(BlockType type, int amount) {
        if (!acceptsInput(type) || amount <= 0) return amount;
        if (types[SLOT_INPUT] != null && types[SLOT_INPUT] != type) return amount;
        int space = Inventory.maxStack(type) - counts[SLOT_INPUT];
        int take = Math.min(space, amount);
        if (take <= 0) return amount;
        types[SLOT_INPUT] = type;
        counts[SLOT_INPUT] += take;
        return amount - take;
    }

    @Override
    public int getCount(BlockType type) {
        if (type == null) return 0;
        int total = 0;
        for (int i = 0; i < SLOT_COUNT; i++) {
            if (types[i] == type) total += counts[i];
        }
        return total;
    }

    // ------------------------------------------------------------------
    // Persistence
    // ------------------------------------------------------------------

    /** v1: slots + progress. v2: adds the byproduct credit. */
    private static final byte PAYLOAD_VERSION = 2;

    @Override
    public void writeTo(DataOutput out) throws IOException {
        out.writeByte(PAYLOAD_VERSION);
        for (int i = 0; i < SLOT_COUNT; i++) {
            out.writeShort(types[i] == null ? 0 : types[i].id);
            out.writeInt(counts[i]);
        }
        out.writeFloat(progress);
        out.writeFloat(byproductCredit);
    }

    @Override
    public void readFrom(DataInput in) throws IOException {
        byte version = in.readByte();
        for (int i = 0; i < SLOT_COUNT; i++) {
            types[i] = null;
            counts[i] = 0;
        }
        progress = 0f;
        byproductCredit = 0f;
        if (version != 1 && version != 2) return;
        for (int i = 0; i < SLOT_COUNT; i++) {
            int id = in.readUnsignedShort();
            int count = in.readInt();
            types[i] = id == 0 || count <= 0 ? null : BlockType.byId(id);
            counts[i] = types[i] == null ? 0 : count;
        }
        progress = Math.max(0f, in.readFloat());
        if (version >= 2) byproductCredit = Math.max(0f, Math.min(1f, in.readFloat()));
    }
}
