package com.minecraftclone.world;

import com.minecraftclone.player.Inventory;
import com.minecraftclone.player.ItemStack;
import com.minecraftclone.player.Smelting;
import com.minecraftclone.player.StorageContainer;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/**
 * A placed Electric Furnace: the Electric Age upgrade of the furnace.
 * Draws EU from an adjacent {@link CoalGeneratorEntity} or
 * {@link BatteryBlockEntity} (directly or through copper/gold cables)
 * and smelts items at <b>2× the speed</b> of a standard furnace.
 *
 * <h3>Slots</h3>
 * <ul>
 *   <li><b>Slot 0 — input</b>: any smeltable item.</li>
 *   <li><b>Slot 1 — EU indicator</b>: always empty; the HUD flame shows EU flow.</li>
 *   <li><b>Slot 2 — output</b>: smelted result.</li>
 * </ul>
 *
 * <p>EU cost per smelt: {@value #EU_PER_SMELT}. This is drawn continuously
 * over the smelt duration, throttled by the cable network's weakest tier
 * (same weakest-link rule as steam pipes).
 */
public final class ElectricFurnaceEntity implements BlockEntity, StorageContainer, ProgressMachine {

    public static final String TYPE = "electric_furnace";

    public static final int SLOT_INPUT  = 0;
    public static final int SLOT_EU     = 1;   // visual placeholder
    public static final int SLOT_OUTPUT = 2;
    public static final int SLOT_COUNT  = 3;

    /** Total EU to smelt one item. */
    public static final float EU_PER_SMELT = 400f;

    /** Time to smelt one item — 2× faster than a plain furnace. */
    public static final float SMELT_SECONDS = Furnace.SMELT_TIME * 0.5f;

    private final BlockType[] types  = new BlockType[SLOT_COUNT];
    private final int[]       counts = new int[SLOT_COUNT];
    private float progress;
    private float lastEuFraction;

    private World world;
    private int posX, posY, posZ;
    private boolean attached;
    private float sourceCheckTimer;
    /** EU source found on the last scan (generator or battery). */
    EuSource euSource;

    /** Called by the World so the entity can locate its EU source. */
    public void attach(int x, int y, int z, World world) {
        this.posX = x; this.posY = y; this.posZ = z;
        this.attached = true; this.world = world;
    }

    // ------------------------------------------------------------------
    // BlockEntity
    // ------------------------------------------------------------------

    @Override public String type()         { return TYPE; }
    @Override public BlockType blockType() { return BlockType.ELECTRIC_FURNACE; }
    @Override public int activeLightLevel() { return progress > 0 ? 13 : 0; }

    // ------------------------------------------------------------------
    // ProgressMachine
    // ------------------------------------------------------------------

    @Override public boolean isActive() { return lastEuFraction > 0f; }
    @Override public float burnFraction()     { return lastEuFraction; }
    @Override public float progressFraction() {
        return Math.min(1f, Math.max(0f, progress / SMELT_SECONDS));
    }

    // ------------------------------------------------------------------
    // Simulation
    // ------------------------------------------------------------------

    /** True if the input can produce output into the empty/matching output slot. */
    public boolean canSmelt() {
        BlockType input  = types[SLOT_INPUT];
        BlockType output = Smelting.outputFor(input);
        if (output == null) return false;
        BlockType held = types[SLOT_OUTPUT];
        return held == null || (held == output && counts[SLOT_OUTPUT] < Inventory.maxStack(output));
    }

    @Override
    public void tick(float dt) {
        if (dt <= 0) return;

        // Re-scan for an EU source a couple of times per second.
        sourceCheckTimer -= dt;
        if (sourceCheckTimer <= 0f) {
            sourceCheckTimer = 0.5f;
            if (attached && world != null) {
                euSource  = findEuSource();
                drawRate  = resolveDrawRate();
            }
        }

        boolean powered = euSource != null && euSource.euStored() > 0f && canSmelt();
        if (!powered) {
            lastEuFraction = 0f;
            return;
        }

        // Draw EU proportionally: EU_PER_SMELT over SMELT_SECONDS, throttled by cable tier.
        float euNeeded = (EU_PER_SMELT / SMELT_SECONDS) * dt * drawRate;
        float drawn    = euSource.drainEU(Math.min(euNeeded, euSource.euStored()));
        if (drawn <= 0f) { lastEuFraction = 0f; return; }

        // Advance smelting proportionally to the EU actually drawn.
        float effectiveDt = drawn / (EU_PER_SMELT / SMELT_SECONDS);
        lastEuFraction    = Math.min(1f, euSource.euStored()
                / (euSource instanceof CoalGeneratorEntity ? CoalGeneratorEntity.MAX_EU
                 : BatteryBlockEntity.MAX_EU));
        advance(effectiveDt);
    }

    /** EU throughput multiplier from the cable network (weakest-link tier). */
    private float drawRate = 1f;

    private float resolveDrawRate() {
        if (!attached || world == null) return 1f;
        com.minecraftclone.world.pipes.PipeNetwork net =
                world.pipeNetworks().networkAt(
                        com.minecraftclone.world.pipes.PipeType.ENERGY, posX, posY, posZ);
        if (net == null || net.minTier == null) return 1f;
        // minTier is a SteamPipeTier in the network snapshot; for ENERGY networks
        // the PipeNetworkManager uses CableTier throughput instead via the
        // overridden minTierThroughput stored in the snapshot.
        // Until the generic tier field lands, read the block directly:
        CableTier weakest = null;
        for (long key : net.cells) {
            int cx = (int)(key & 0x1FFFFFL) << 21 >> 21;
            int cy = (int)((key >> 21) & 0x1FFFFFL) << 21 >> 21;
            int cz = (int)((key >> 42) & 0x1FFFFFL) << 21 >> 21;
            CableTier t = CableTier.of(world.getBlock(cx, cy, cz));
            if (t != null && (weakest == null || t.throughput < weakest.throughput)) weakest = t;
        }
        return weakest != null ? weakest.throughput : 1f;
    }

    private static final int[][] FACES = {
        {1,0,0},{-1,0,0},{0,1,0},{0,-1,0},{0,0,1},{0,0,-1}
    };

    /**
     * Finds an EU source for this machine — generators first (highest EU/s),
     * then batteries. Scans direct neighbours then the cable network perimeter.
     */
    private EuSource findEuSource() {
        if (!attached || world == null) return null;
        // Direct adjacency — prefer generators over batteries.
        for (int[] d : FACES) {
            BlockEntity e = world.blockEntityAt(posX+d[0], posY+d[1], posZ+d[2]);
            if (e instanceof CoalGeneratorEntity g) return g;
        }
        for (int[] d : FACES) {
            BlockEntity e = world.blockEntityAt(posX+d[0], posY+d[1], posZ+d[2]);
            if (e instanceof BatteryBlockEntity b) return b;
        }
        // Through cable network.
        com.minecraftclone.world.pipes.PipeNetwork net =
                world.pipeNetworks().networkAt(
                        com.minecraftclone.world.pipes.PipeType.ENERGY, posX, posY, posZ);
        if (net == null) return null;
        CoalGeneratorEntity gen = null;
        BatteryBlockEntity  bat = null;
        for (long key : net.cells) {
            int cx = (int)(key & 0x1FFFFFL) << 21 >> 21;
            int cy = (int)((key >> 21) & 0x1FFFFFL) << 21 >> 21;
            int cz = (int)((key >> 42) & 0x1FFFFFL) << 21 >> 21;
            for (int[] d : FACES) {
                BlockEntity e = world.blockEntityAt(cx+d[0], cy+d[1], cz+d[2]);
                if (e instanceof CoalGeneratorEntity g && gen == null) gen = g;
                if (e instanceof BatteryBlockEntity  b && bat == null) bat = b;
            }
        }
        return gen != null ? gen : bat;
    }

    private void advance(float dt) {
        progress += dt;
        while (progress >= SMELT_SECONDS && canSmelt()) {
            progress -= SMELT_SECONDS;
            BlockType output = Smelting.outputFor(types[SLOT_INPUT]);
            counts[SLOT_INPUT]--;
            if (counts[SLOT_INPUT] == 0) types[SLOT_INPUT] = null;
            if (types[SLOT_OUTPUT] == output) {
                counts[SLOT_OUTPUT]++;
            } else {
                types[SLOT_OUTPUT] = output;
                counts[SLOT_OUTPUT] = 1;
            }
        }
    }

    // ------------------------------------------------------------------
    // StorageContainer
    // ------------------------------------------------------------------

    @Override public int size() { return SLOT_COUNT; }

    @Override
    public BlockType typeOf(int slot) {
        if (slot < 0 || slot >= SLOT_COUNT) return null;
        return types[slot];
    }

    @Override
    public int countOf(int slot) {
        if (slot < 0 || slot >= SLOT_COUNT) return 0;
        return counts[slot];
    }

    @Override
    public void setSlot(int slot, BlockType type, int count) {
        if (slot >= 0 && slot < SLOT_COUNT) {
            types[slot]  = count > 0 ? type : null;
            counts[slot] = Math.max(0, count);
        }
    }

    @Override
    public int add(BlockType type, int amount) {
        if (!canInsert(SLOT_INPUT, type) || amount <= 0) return amount;
        if (types[SLOT_INPUT] != null && types[SLOT_INPUT] != type) return amount;
        int space = Inventory.maxStack(type) - counts[SLOT_INPUT];
        int take  = Math.min(space, amount);
        if (take <= 0) return amount;
        types[SLOT_INPUT]   = type;
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

    /** True if this slot accepts the item (input slot only, smeltable items only). */
    public boolean canInsert(int slot, BlockType item) {
        return slot == SLOT_INPUT && Smelting.outputFor(item) != null;
    }

    /** True if this slot has output to take. */
    public boolean canExtract(int slot) { return slot == SLOT_OUTPUT && types[SLOT_OUTPUT] != null; }

    /** Insert into the input slot. */
    public boolean insert(int slot, BlockType item, int count) {
        if (!canInsert(slot, item) || count <= 0) return false;
        if (types[slot] == null || types[slot] == item) {
            types[slot]  = item;
            counts[slot] += count;
            return true;
        }
        return false;
    }

    /** Extract from the output slot. */
    public ItemStack extract(int slot, int max) {
        if (!canExtract(slot)) return null;
        int n = Math.min(max, counts[slot]);
        ItemStack s = ItemStack.of(types[slot], n);
        counts[slot] -= n;
        if (counts[slot] == 0) types[slot] = null;
        return s;
    }

    // ------------------------------------------------------------------
    // Persistence
    // ------------------------------------------------------------------

    @Override
    public void writeTo(DataOutput out) throws IOException {
        for (int i = 0; i < SLOT_COUNT; i++) {
            out.writeShort(types[i] != null ? types[i].id : 0);
            out.writeInt(counts[i]);
        }
        out.writeFloat(progress);
        out.writeFloat(lastEuFraction);
    }

    @Override
    public void readFrom(DataInput in) throws IOException {
        for (int i = 0; i < SLOT_COUNT; i++) {
            int id     = in.readUnsignedShort();
            types[i]   = (id == 0) ? null : BlockType.byId(id);
            counts[i]  = in.readInt();
        }
        progress       = in.readFloat();
        lastEuFraction = in.readFloat();
    }
}
