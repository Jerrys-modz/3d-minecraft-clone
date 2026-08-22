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

    /** Cached heat state, refreshed periodically from the world (multiplayer snapshots may override). */
    private boolean hot;
    private float heatCheckTimer;

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
        return hot;
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
     * Heat indicator for the HUD flame: full while lava feeds the structure,
     * empty while cold (progress paused, nothing lost).
     */
    public float heatFraction() {
        return hot ? 1f : 0f;
    }

    @Override
    public void tick(float dt) {
        if (!formed) return;
        // Heat is world-derived: rescan under the floor a couple times per
        // second rather than every tick (the answer rarely changes).
        heatCheckTimer -= dt;
        if (heatCheckTimer <= 0f) {
            heatCheckTimer = 0.5f;
            hot = scanForLavaBelow();
        }
        advance(dt, hot);
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

    /** Any lava in the layer directly beneath the smeltery's floor footprint? */
    private boolean scanForLavaBelow() {
        if (world == null || instance == null) return false;
        int y = instance.minY - 1;
        if (y < 0) return false;
        for (int x = instance.minX + 1; x <= instance.maxX - 1; x++) {
            for (int z = instance.minZ + 1; z <= instance.maxZ - 1; z++) {
                BlockType below = world.getBlock(x, y, z);
                if (below == BlockType.LAVA || below == BlockType.LAVA_SOURCE || below == BlockType.LAVA_FLOW) {
                    return true;
                }
            }
        }
        return false;
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
        // The output slot only ever fills from melting; taking empties it.
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

    @Override
    public void writeTo(DataOutput out) throws IOException {
        super.writeTo(out);
        for (int i = 0; i < SLOT_COUNT; i++) {
            out.writeShort(types[i] == null ? 0 : types[i].id);
            out.writeInt(counts[i]);
        }
        out.writeFloat(meltProgress);
        out.writeBoolean(hot);
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
        meltProgress = Math.max(0f, in.readFloat());
        hot = in.readBoolean();
        // Do not restore 'formed' from disk; always start unformed and let MultiBlockManager re-form
        this.formed = false;
    }
}
