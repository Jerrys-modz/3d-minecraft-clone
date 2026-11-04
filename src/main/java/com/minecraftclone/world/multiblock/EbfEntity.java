package com.minecraftclone.world.multiblock;

import com.minecraftclone.player.Inventory;
import com.minecraftclone.player.StorageContainer;
import com.minecraftclone.world.BlastRecipes;
import com.minecraftclone.world.BlockType;
import com.minecraftclone.world.EuNetwork;
import com.minecraftclone.world.ProgressMachine;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/**
 * The formed Electric Blast Furnace's controller entity: an input slot and an
 * output slot in the furnace GUI layout. Blasts one item per
 * {@value #BLAST_SECONDS} s for {@value #EU_PER_ITEM} EU when the coils are
 * hot enough for its recipe ({@link BlastRecipes}). Powered like any machine
 * (cables / battery / generator at the controller); MV-rated.
 */
public final class EbfEntity extends MultiBlockEntity implements StorageContainer, ProgressMachine {

    public static final String TYPE = "ebf";

    public static final int SLOT_INPUT = 0;
    public static final int SLOT_UNUSED = 1;   // the furnace GUI's fuel position — power replaces fuel
    public static final int SLOT_OUTPUT = 2;
    public static final int SLOT_COUNT = 3;

    public static final float BLAST_SECONDS = 10f;
    public static final float EU_PER_ITEM = 1000f;

    private final BlockType[] types = new BlockType[SLOT_COUNT];
    private final int[] counts = new int[SLOT_COUNT];
    private float progress;
    private float sourceTimer;
    private EuNetwork.Feed feed = EuNetwork.Feed.NONE;
    private boolean working;

    /** Deserialisation constructor (re-formed through the manager's scan). */
    public EbfEntity() {
        super(null);
        formed = false;
    }

    public EbfEntity(MultiBlockInstance instance) {
        super(instance);
    }

    @Override public String type()         { return TYPE; }
    @Override public BlockType blockType() { return BlockType.EBF_CONTROLLER; }
    @Override public int activeLightLevel() { return working ? 14 : 0; }
    @Override public boolean isActive()    { return formed && working; }

    /** The furnace's temperature from its coils (0 when unformed). */
    public int heatK() {
        Object h = instance == null ? null : instance.get(EbfDefinition.HEAT_KEY);
        return formed && h instanceof Integer k ? k : 0;
    }

    /** True if the current input has a recipe but the coils aren't hot enough for it. */
    public boolean tooCold() {
        int need = BlastRecipes.temperatureFor(types[SLOT_INPUT]);
        return need > 0 && heatK() < need;
    }

    @Override public float burnFraction()     { return working ? 1f : 0f; }
    @Override public float progressFraction() { return Math.min(1f, progress / BLAST_SECONDS); }

    /** True if the input can be blasted into the output slot. */
    public boolean canBlast() {
        BlockType in = types[SLOT_INPUT];
        BlockType out = BlastRecipes.outputFor(in);
        if (out == null || tooCold()) return false;
        return types[SLOT_OUTPUT] == null
                || (types[SLOT_OUTPUT] == out && counts[SLOT_OUTPUT] < Inventory.maxStack(out));
    }

    @Override
    public void tick(float dt) {
        working = false;
        if (dt <= 0 || !formed || world == null || instance == null) return;
        sourceTimer -= dt;
        if (sourceTimer <= 0f) {
            sourceTimer = 0.5f;
            feed = EuNetwork.resolve(world, instance.controllerX, instance.controllerY, instance.controllerZ);
        }
        if (!canBlast() || feed.source() == null || feed.source().euStored() <= 0f) return;
        if (feed.voltage().exceeds(com.minecraftclone.world.CableTier.Voltage.MV)) {   // MV-rated
            world.reportOverload(instance.controllerX, instance.controllerY, instance.controllerZ);
            return;
        }
        float euPerSecond = EU_PER_ITEM / BLAST_SECONDS;
        float drawn = feed.source().drainEU(Math.min(euPerSecond * dt * feed.throughput(), feed.source().euStored()));
        if (drawn <= 0f) return;
        working = true;
        progress += drawn * feed.efficiency() / euPerSecond;
        while (progress >= BLAST_SECONDS && canBlast()) {
            progress -= BLAST_SECONDS;
            BlockType out = BlastRecipes.outputFor(types[SLOT_INPUT]);
            if (--counts[SLOT_INPUT] == 0) types[SLOT_INPUT] = null;
            if (types[SLOT_OUTPUT] == out) counts[SLOT_OUTPUT]++;
            else { types[SLOT_OUTPUT] = out; counts[SLOT_OUTPUT] = 1; }
        }
        if (!canBlast()) progress = Math.min(progress, BLAST_SECONDS);
    }

    // ------------------------------------------------------------------
    // StorageContainer
    // ------------------------------------------------------------------

    @Override public int size() { return SLOT_COUNT; }
    @Override public BlockType typeOf(int slot) { return slot >= 0 && slot < SLOT_COUNT ? types[slot] : null; }
    @Override public int countOf(int slot)      { return slot >= 0 && slot < SLOT_COUNT ? counts[slot] : 0; }

    @Override
    public void setSlot(int slot, BlockType type, int count) {
        if (slot < 0 || slot >= SLOT_COUNT) return;
        types[slot] = type == null || count <= 0 ? null : type;
        counts[slot] = types[slot] == null ? 0 : count;
    }

    @Override
    public int add(BlockType type, int amount) {
        if (BlastRecipes.outputFor(type) == null || amount <= 0) return amount;
        if (types[SLOT_INPUT] != null && types[SLOT_INPUT] != type) return amount;
        int take = Math.min(Inventory.maxStack(type) - counts[SLOT_INPUT], amount);
        if (take <= 0) return amount;
        types[SLOT_INPUT] = type;
        counts[SLOT_INPUT] += take;
        return amount - take;
    }

    @Override
    public int getCount(BlockType type) {
        int n = 0;
        for (int i = 0; i < SLOT_COUNT; i++) if (type != null && types[i] == type) n += counts[i];
        return n;
    }

    // ------------------------------------------------------------------
    // Persistence (after the base class's formed flag)
    // ------------------------------------------------------------------

    @Override
    public void writeTo(DataOutput out) throws IOException {
        super.writeTo(out);
        for (int i = 0; i < SLOT_COUNT; i++) {
            out.writeShort(types[i] == null ? 0 : types[i].id);
            out.writeInt(counts[i]);
        }
        out.writeFloat(progress);
    }

    @Override
    public void readFrom(DataInput in) throws IOException {
        super.readFrom(in);
        for (int i = 0; i < SLOT_COUNT; i++) {
            int id = in.readUnsignedShort();
            int count = in.readInt();
            types[i] = id == 0 || count <= 0 ? null : BlockType.byId(id);
            counts[i] = types[i] == null ? 0 : count;
        }
        progress = Math.max(0f, in.readFloat());
    }
}
