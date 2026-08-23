package com.minecraftclone.world.multiblock;

import com.minecraftclone.player.ItemStack;
import com.minecraftclone.player.Smelting;
import com.minecraftclone.player.StorageContainer;
import com.minecraftclone.world.BlockType;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/**
 * Block entity placed at a formed Smeltery's controller position - and the
 * smeltery's actual brain.
 *
 * <h3>How it works</h3>
 * <ul>
 *   <li><b>Input</b> (slot 0): any smeltable item (see {@link Smelting#isSmeltable})
 *       goes in through the controller's GUI.</li>
 *   <li><b>Heat</b>: the structure needs lava in the blocks directly beneath
 *       its floor; while hot, one queued item melts every {@link #MELT_SECONDS}
 *       seconds. Cold pauses progress without losing it.</li>
 *   <li><b>Yield</b>: raw ores melt into twice as many ingots as a furnace
 *       gives (the Tinkers-style reward); crushed ores and dusts yield one.</li>
 *   <li><b>Output</b> (slot 1): melted ingots accumulate here until taken.</li>
 * </ul>
 *
 * <p>The entity implements {@link StorageContainer} so the standard
 * inventory-GUI machinery (click / drag / shift-click) drives its two slots
 * exactly like a furnace's. While formed, {@link #isActive()} returns
 * {@code true} (lit controller front, light level 13). Slots and progress
 * persist with the chunk via {@link #writeTo}/{@link #readFrom}.
 */
public final class SmelteryEntity extends MultiBlockEntity implements StorageContainer {

    /** Stable type name persisted with the entity. Must match {@link BlockEntities} registration. */
    public static final String TYPE = "smeltery";

    /** How brightly the active smeltery glows (same as a torch, 13/15). */
    private static final int LIGHT_LEVEL = 13;

    /** Real-time seconds to melt one item (same pace as a furnace). */
    public static final float MELT_SECONDS = 8f;

    /** How many seconds of melting one scooped lava block provides. */
    public static final float LAVA_SECONDS = 100f;

    /** Fuel buffer cap (six lava buckets' worth). */
    public static final float MAX_FUEL = LAVA_SECONDS * 6f;

    /** Raw ores melt into this many ingots (dusts/crushed ores yield one). */
    public static final int ORE_YIELD = 2;

    /** Max units of one item type the input slot holds (like stack limits). */
    public static final int MAX_INPUT_COUNT = 999;

    /** Slot indices in {@link StorageContainer} order: input first, then output. */
    public static final int SLOT_INPUT = 0;
    public static final int SLOT_OUTPUT = 1;
    public static final int SLOT_COUNT = 2;

    private final BlockType[] types = new BlockType[SLOT_COUNT];
    private final int[] counts = new int[SLOT_COUNT];

    /** Progress (seconds) toward melting one item out of the input slot. */
    private float meltProgress;

    /**
     * Remaining burn time (seconds) in the fuel buffer, filled by pouring
     * lava buckets into a Seared Tank. One lava block holds
     * {@link #LAVA_SECONDS}; melting only runs while this is above zero.
     */
    private float lavaFuel;

    /** No-arg constructor for {@link com.minecraftclone.world.BlockEntities} deserialization. */
    public SmelteryEntity() {
        super(null); // instance is null during load; MultiBlockManager re-registers on form
        // Deserialized entities start as unformed; MultiBlockManager's formation scan will re-activate them
        this.formed = false;
    }

    public SmelteryEntity(MultiBlockInstance instance) {
        super(instance);
    }

    @Override
    public String type() { return TYPE; }

    @Override
    public BlockType blockType() { return BlockType.SMELTERY_CONTROLLER; }

    @Override
    public int activeLightLevel() { return LIGHT_LEVEL; }

    // -----------------------------------------------------------------------
    // Heat + simulation
    // -----------------------------------------------------------------------

    /** True if this item can go into the input slot (mirrors {@link Smelting#isSmeltable}). */
    public boolean accepts(BlockType type) {
        return Smelting.isSmeltable(type);
    }

    /**
     * Adds up to {@code count} of {@code type} to the input slot. Returns how
     * many were actually accepted (0 if not smeltable, unformed, or full).
     */
    public int insert(BlockType type, int count) {
        if (!formed || !accepts(type) || count <= 0) return 0;
        if (types[SLOT_INPUT] != null && types[SLOT_INPUT] != type) return 0;
        int space = MAX_INPUT_COUNT - counts[SLOT_INPUT];
        int take = Math.min(space, count);
        if (take <= 0) return 0;
        types[SLOT_INPUT] = type;
        counts[SLOT_INPUT] += take;
        return take;
    }

    /** Whether the last heat scan found lava under the floor (drives pause/HUD). */
    public boolean isHot() {
        return lavaFuel > 0f;
    }

    /** Seconds of melt time left in the fuel buffer. */
    public float lavaFuel() {
        return lavaFuel;
    }

    /**
     * Pours lava into the fuel buffer (one bucket = {@link #LAVA_SECONDS}).
     * Returns how many seconds were actually accepted (0 when full/unformed).
     */
    public float addFuel(float seconds) {
        if (!formed || seconds <= 0) return 0f;
        float space = MAX_FUEL - lavaFuel;
        float take = Math.min(space, seconds);
        if (take <= 0f) return 0f;
        lavaFuel += take;
        return take;
    }

    /** Total items waiting in the input slot (for HUD messages). */
    public int pendingCount() {
        return counts[SLOT_INPUT];
    }

    /** True once at least one ingot is ready to collect. */
    public boolean hasOutput() {
        return counts[SLOT_OUTPUT] > 0 && types[SLOT_OUTPUT] != null;
    }

    /** The type currently sitting in the output slot, or null. */
    public BlockType outputType() {
        return typeOf(SLOT_OUTPUT);
    }

    /** Takes everything out of the output slot, or null when empty. */
    public ItemStack takeOutput() {
        ItemStack out = stackIn(SLOT_OUTPUT);
        if (out.isEmpty()) return null;
        setSlot(SLOT_OUTPUT, null, 0);
        return out;
    }

    private ItemStack stackIn(int slot) {
        BlockType t = typeOf(slot);
        int c = countOf(slot);
        return (t == null || c <= 0) ? ItemStack.EMPTY : ItemStack.of(t, c);
    }

    /** 0..1 progress of the item currently melting (for the GUI arrow). */
    public float progressFraction() {
        return Math.min(1f, Math.max(0f, meltProgress / MELT_SECONDS));
    }

    /**
     * Fuel indicator for the HUD flame: the fraction of the buffer still
     * filled (drains as items melt; empty = paused, nothing lost).
     */
    public float heatFraction() {
        return Math.min(1f, Math.max(0f, lavaFuel / MAX_FUEL));
    }

    @Override
    public void tick(float dt) {
        if (!formed) return;
        // Burn fuel only while there is something to melt AND the output can
        // actually accept the result (furnace-style); an idle or blocked
        // smeltery keeps its lava. Only the elapsed time that was really
        // available is handed to advance(), so a nearly-dry tank can't melt
        // an item it no longer has heat for.
        boolean blocked = counts[SLOT_INPUT] > 0 && types[SLOT_OUTPUT] != null
                && Smelting.outputFor(types[SLOT_INPUT]) != types[SLOT_OUTPUT];
        boolean burning = lavaFuel > 0f && counts[SLOT_INPUT] > 0 && !blocked;
        if (burning) {
            float elapsed = Math.min(dt, lavaFuel);
            lavaFuel -= elapsed;
            advance(elapsed, true);
        }
    }

    /**
     * Advances melting by {@code dt}: hot moves input toward output, cold
     * pauses (progress kept). Split from {@link #tick(float)} so tests can
     * drive it deterministically without forming a structure.
     *
     * @return true if an item finished melting this call
     */
    public boolean advance(float dt, boolean hot) {
        if (!formed || !hot || counts[SLOT_INPUT] <= 0) return false;
        meltProgress += dt;
        if (meltProgress < MELT_SECONDS) return false;
        meltProgress = 0f;
        BlockType head = types[SLOT_INPUT];
        BlockType result = Smelting.outputFor(head);
        if (result == null) { // defensive: slot should only hold smeltables
            setSlot(SLOT_INPUT, null, 0);
            return false;
        }
        // Output aggregates same-type ingots; a different output type waits
        // (retry next tick) until the buffer is collected.
        if (types[SLOT_OUTPUT] != null && types[SLOT_OUTPUT] != result) {
            meltProgress = MELT_SECONDS;
            return false;
        }
        // Internal write path: fills output directly (setSlot would reject
        // GUI-initiated writes there, but melting owns this slot).
        types[SLOT_OUTPUT] = result;
        counts[SLOT_OUTPUT] += yieldFor(head);
        if (counts[SLOT_INPUT] <= 1) {
            setSlot(SLOT_INPUT, null, 0);
        } else {
            counts[SLOT_INPUT]--;
        }
        return true;
    }

    /** Tinkers-style bonus: raw ores give double ingots, processed forms give one. */
    public static int yieldFor(BlockType type) {
        return type != null && type.name().endsWith("_ORE") ? ORE_YIELD : 1;
    }

    // -----------------------------------------------------------------------
    // StorageContainer (GUI slots)
    // -----------------------------------------------------------------------

    @Override
    public int size() {
        return SLOT_COUNT;
    }

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
        if (slot < 0 || slot >= SLOT_COUNT) return;
        boolean clearing = type == null || count <= 0;
        if (slot == SLOT_INPUT) {
            // Input: only smeltables, up to the cap. Clearing is always fine
            // (taking items back out); non-smeltable writes are ignored.
            if (clearing) {
                types[slot] = null;
                counts[slot] = 0;
            } else if (Smelting.isSmeltable(type)) {
                types[slot] = type;
                counts[slot] = Math.min(count, MAX_INPUT_COUNT);
            }
        } else {
            // Output: only ever cleared (collecting ingots). Filling it is the
            // melt loop's job; placing arbitrary items is rejected.
            if (clearing) {
                types[slot] = null;
                counts[slot] = 0;
            }
        }
    }

    @Override
    public int add(BlockType type, int amount) {
        // Inserting into the smeltery means the input slot, and only smeltables.
        if (!Smelting.isSmeltable(type) || amount <= 0 || !formed) return amount;
        if (types[SLOT_INPUT] == null || types[SLOT_INPUT] == type) {
            int space = MAX_INPUT_COUNT - counts[SLOT_INPUT];
            int take = Math.min(space, amount);
            if (take > 0) {
                types[SLOT_INPUT] = type;
                counts[SLOT_INPUT] += take;
                return amount - take;
            }
        }
        return amount; // leftover: slot busy with another type (or full)
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

    // -----------------------------------------------------------------------
    // Persistence
    // -----------------------------------------------------------------------

    /**
     * Payload version for this entity's save format. v1 (current): two slot
     * records + melt progress + fuel. Anything older (the placeholder era
     * wrote just a heat float) or unrecognized resets the state to empty
     * rather than crashing on an existing world.
     */
    private static final byte SAVE_VERSION = 1;

    @Override
    public void writeTo(DataOutput out) throws IOException {
        super.writeTo(out);
        out.writeByte(SAVE_VERSION);
        for (int i = 0; i < SLOT_COUNT; i++) {
            out.writeShort(types[i] == null ? 0 : types[i].id);
            out.writeInt(counts[i]);
        }
        out.writeFloat(meltProgress);
        out.writeFloat(lavaFuel);
    }

    @Override
    public void readFrom(DataInput in) throws IOException {
        super.readFrom(in);
        // Legacy payloads end right here (they carried no version byte), and a
        // future version would fail its checks below - either way, reset to a
        // clean state instead of throwing mid-read and corrupting the chunk.
        try {
            byte version = in.readByte();
            if (version != SAVE_VERSION) {
                clearState();
                return;
            }
            for (int i = 0; i < SLOT_COUNT; i++) {
                int id = in.readUnsignedShort();
                int count = in.readInt();
                types[i] = id == 0 || count <= 0 ? null : BlockType.byId(id);
                counts[i] = types[i] == null ? 0 : count;
            }
            meltProgress = Math.max(0f, in.readFloat());
            lavaFuel = Math.max(0f, Math.min(MAX_FUEL, in.readFloat()));
        } catch (java.io.EOFException legacy) {
            clearState(); // pre-versioning save: nothing usable to restore
        }
        // Do not restore 'formed' from disk; always start unformed and let MultiBlockManager re-form
        this.formed = false;
    }

    /** Empties slots, progress and fuel (used when a payload can't be understood). */
    private void clearState() {
        for (int i = 0; i < SLOT_COUNT; i++) {
            types[i] = null;
            counts[i] = 0;
        }
        meltProgress = 0f;
        lavaFuel = 0f;
    }
}
