package com.minecraftclone.world;

import com.minecraftclone.player.Inventory;
import com.minecraftclone.player.Mortar;
import com.minecraftclone.player.StorageContainer;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/**
 * A placed LV Macerator: the first electric ore-processing machine. Grinds
 * ore, crushed ore and impure piles into dust like the mortar does, but
 * <b>doubles</b> the dust from anything ore-derived, runs on EU instead of
 * mortar wear, and works twice as fast as a furnace.
 *
 * <h3>Slots</h3>
 * <ul>
 *   <li><b>Slot 0 — input</b>: anything {@link Mortar#isGrindable grindable}.</li>
 *   <li><b>Slot 1 — EU indicator</b>: always empty; the GUI flame shows charge.</li>
 *   <li><b>Slot 2 — output</b>: the ground dust.</li>
 * </ul>
 *
 * <p>Power comes from a {@link CoalGeneratorEntity} or {@link BatteryBlockEntity}
 * found through {@link EuNetwork} (direct adjacency or copper/gold cables),
 * throttled by the cable network's weakest tier, exactly like the Electric
 * Furnace.
 */
public final class ElectricMaceratorEntity implements BlockEntity, StorageContainer, ProgressMachine {

    public static final String TYPE = "electric_macerator";

    public static final int SLOT_INPUT  = 0;
    public static final int SLOT_EU     = 1;   // visual placeholder
    public static final int SLOT_OUTPUT = 2;
    public static final int SLOT_COUNT  = 3;

    /** Total EU to grind one item. */
    public static final float EU_PER_OPERATION = 300f;

    /** Time to grind one item at full power - twice as fast as a furnace. */
    public static final float GRIND_SECONDS = Furnace.SMELT_TIME * 0.5f;

    /** Dust multiplier for ore-derived inputs (ore, crushed ore, impure pile). */
    public static final int ORE_YIELD = 2;

    private final BlockType[] types  = new BlockType[SLOT_COUNT];
    private final int[]       counts = new int[SLOT_COUNT];
    private float progress;
    private float lastEuFraction;

    private World world;
    private int posX, posY, posZ;
    private boolean attached;
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
    // Recipes
    // ------------------------------------------------------------------

    /** The dust produced from {@code input}, or null if it can't be macerated. */
    public static BlockType outputFor(BlockType input) {
        return Mortar.isGrindable(input) ? Mortar.dustFor(input) : null;
    }

    /**
     * How many output items one {@code input} yields: {@value #ORE_YIELD} for
     * ores, crushed ores and impure piles; 1 for everything else (coal → coal
     * dust), so the machine can't be used to multiply non-ore materials.
     */
    public static int yieldFor(BlockType input) {
        if (input == null) return 0;
        String n = input.name();
        boolean oreDerived = n.endsWith("_ORE") || n.startsWith("CRUSHED_") || n.startsWith("IMPURE_");
        return oreDerived ? ORE_YIELD : 1;
    }

    // ------------------------------------------------------------------
    // BlockEntity / ProgressMachine
    // ------------------------------------------------------------------

    @Override public String type()          { return TYPE; }
    @Override public BlockType blockType()  { return BlockType.ELECTRIC_MACERATOR; }
    @Override public int activeLightLevel() { return isActive() ? 10 : 0; }
    @Override public boolean isActive()     { return lastEuFraction > 0f; }
    @Override public float burnFraction()   { return lastEuFraction; }
    @Override public float progressFraction() {
        return Math.min(1f, Math.max(0f, progress / GRIND_SECONDS));
    }

    // ------------------------------------------------------------------
    // Simulation
    // ------------------------------------------------------------------

    /** True if the input can grind into the empty/matching output slot without overflowing it. */
    public boolean canGrind() {
        BlockType input = types[SLOT_INPUT];
        BlockType output = outputFor(input);
        if (output == null) return false;
        BlockType held = types[SLOT_OUTPUT];
        return held == null
                || (held == output && counts[SLOT_OUTPUT] + yieldFor(input) <= Inventory.maxStack(output));
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

        if (euSource == null || euSource.euStored() <= 0f || !canGrind()) {
            lastEuFraction = 0f;
            return;
        }

        float euPerSecond = EU_PER_OPERATION / GRIND_SECONDS;
        float wanted = euPerSecond * dt * drawRate;
        float drawn = euSource.drainEU(Math.min(wanted, euSource.euStored()));
        if (drawn <= 0f) {
            lastEuFraction = 0f;
            return;
        }
        lastEuFraction = Math.max(0.05f, Math.min(1f, euSource.euStored() / EuNetwork.capacityOf(euSource)));

        progress += drawn / euPerSecond;
        while (progress >= GRIND_SECONDS && canGrind()) {
            progress -= GRIND_SECONDS;
            BlockType input = types[SLOT_INPUT];
            BlockType output = outputFor(input);
            int yield = yieldFor(input);
            counts[SLOT_INPUT]--;
            if (counts[SLOT_INPUT] == 0) types[SLOT_INPUT] = null;
            if (types[SLOT_OUTPUT] == output) {
                counts[SLOT_OUTPUT] += yield;
            } else {
                types[SLOT_OUTPUT] = output;
                counts[SLOT_OUTPUT] = yield;
            }
        }
        if (!canGrind()) progress = Math.min(progress, GRIND_SECONDS);
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
        if (outputFor(type) == null || amount <= 0) return amount;
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

    private static final byte PAYLOAD_VERSION = 1;

    @Override
    public void writeTo(DataOutput out) throws IOException {
        out.writeByte(PAYLOAD_VERSION);
        for (int i = 0; i < SLOT_COUNT; i++) {
            out.writeShort(types[i] == null ? 0 : types[i].id);
            out.writeInt(counts[i]);
        }
        out.writeFloat(progress);
    }

    @Override
    public void readFrom(DataInput in) throws IOException {
        byte version = in.readByte();
        for (int i = 0; i < SLOT_COUNT; i++) {
            types[i] = null;
            counts[i] = 0;
        }
        progress = 0f;
        if (version != PAYLOAD_VERSION) return;
        for (int i = 0; i < SLOT_COUNT; i++) {
            int id = in.readUnsignedShort();
            int count = in.readInt();
            types[i] = id == 0 || count <= 0 ? null : BlockType.byId(id);
            counts[i] = types[i] == null ? 0 : count;
        }
        progress = Math.max(0f, in.readFloat());
    }
}
