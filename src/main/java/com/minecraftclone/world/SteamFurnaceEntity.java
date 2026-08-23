package com.minecraftclone.world;

import com.minecraftclone.player.Inventory;
import com.minecraftclone.player.ItemStack;
import com.minecraftclone.player.StorageContainer;
import com.minecraftclone.player.Smelting;
import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * A placed Steam Furnace: a furnace-style machine (input / output slots) that
 * draws its heat from an adjacent, steaming {@link SteamBoilerEntity} instead
 * of burning fuel in its own slot. Smelts whatever the standard
 * {@link Smelting} registry handles.
 *
 * <p>Implements {@link StorageContainer} with the same 3-slot layout as the
 * Furnace (input / steam intake / output) so the existing FURNACE GUI kind,
 * click machinery and progress bars work unchanged: the middle slot is where
 * the boiler connection "lands" and always renders empty - the heat gauge in
 * the HUD shows how much steam is actually flowing.
 */
public final class SteamFurnaceEntity implements BlockEntity, StorageContainer, ProgressMachine {

    public static final String TYPE = "steam_furnace";

    public static final int SLOT_INPUT = 0;
    public static final int SLOT_STEAM = 1;  // visual placeholder slot; heat comes from the boiler
    public static final int SLOT_OUTPUT = 2;
    public static final int SLOT_COUNT = 3;

    /** Real-time seconds to smelt one item (same pace as a furnace). */
    public static final float SMELT_SECONDS = Furnace.SMELT_TIME;

    private final BlockType[] types = new BlockType[SLOT_COUNT];
    private final int[] counts = new int[SLOT_COUNT];
    private float progress;
    private float lastSteamFraction; // for the HUD gauge between boiler scans

    private World world;
    private int posX, posY, posZ;
    private boolean attached;
    private float boilerCheckTimer;
    /** The adjacent boiler found on the last scan (or injected by tests). */
    SteamBoilerEntity boiler;

    public SteamFurnaceEntity() {
    }

    @Override
    public String type() { return TYPE; }

    @Override
    public BlockType blockType() { return BlockType.STEAM_FURNACE; }

    @Override
    public int activeLightLevel() { return progress > 0 ? 13 : 0; }

    /** Called by the World helper so the entity can find its boiler. */
    public void attach(int x, int y, int z, World world) {
        this.posX = x;
        this.posY = y;
        this.posZ = z;
        this.attached = true;
        this.world = world;
    }

    // -----------------------------------------------------------------------
    // Heat + simulation
    // -----------------------------------------------------------------------

    /** True while a connected boiler is supplying steam this tick. */
    public boolean isHot() {
        return lastSteamFraction > 0f;
    }

    /** 0..1 steam flow indicator (drives the GUI flame). */
    public float burnFraction() {
        return lastSteamFraction;
    }

    /** 0..1 progress of the item currently smelting (drives the arrow). */
    public float progressFraction() {
        return Math.min(1f, Math.max(0f, progress / SMELT_SECONDS));
    }

    /** True if the input can produce its output into the empty/matching output slot. */
    public boolean canSmelt() {
        BlockType input = types[SLOT_INPUT];
        BlockType output = Smelting.outputFor(input);
        if (output == null) return false;
        BlockType held = types[SLOT_OUTPUT];
        return held == null || (held == output && counts[SLOT_OUTPUT] < Inventory.maxStack(output));
    }

    @Override
    public void tick(float dt) {
        if (dt <= 0) return;
        // Re-scan for a steaming boiler (direct or through pipes) a couple
        // times per second. Unattached entities (tests) keep their injected
        // boiler reference instead of scanning.
        boilerCheckTimer -= dt;
        if (boilerCheckTimer <= 0f) {
            boilerCheckTimer = 0.5f;
            if (attached && world != null) {
                boiler = findSteamSource();
            }
        }
        boolean hot = boiler != null && boiler.steamSeconds() > 0f && canSmelt();
        if (!hot) {
            lastSteamFraction = 0f;
            return;
        }

        // Pull exactly the time we need out of the boiler's steam buffer.
        float draw = Math.min(dt, boiler.steamSeconds());
        boiler.drainSteam(draw);
        lastSteamFraction = Math.min(1f, boiler.steamSeconds() / SteamBoilerEntity.MAX_STEAM_SECONDS);
        advance(draw);
    }

    /** Advances smelting by {@code dt} seconds of steam-heated time. */
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

    private static final int[][] FACES = {
            {1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1},
    };

    /**
     * Finds a steaming boiler for this machine: either face-adjacent
     * directly, or through the connected steam pipe network (resolved via the
     * world's shared {@link com.minecraftclone.world.pipes.PipeNetworkManager},
     * which caches runs and caps them at a fixed cell budget). Returns null
     * when no powered boiler feeds this machine.
     */
    private SteamBoilerEntity findSteamSource() {
        if (!attached || world == null) return null;
        // Direct adjacency first - the common case, zero search cost.
        for (int[] d : FACES) {
            BlockEntity e = world.blockEntityAt(posX + d[0], posY + d[1], posZ + d[2]);
            if (e instanceof SteamBoilerEntity b) return b;
        }
        // Otherwise resolve through the pipe network and scan its perimeter.
        com.minecraftclone.world.pipes.PipeNetwork net =
                world.pipeNetworks().networkAt(com.minecraftclone.world.pipes.PipeType.STEAM, posX, posY, posZ);
        if (net == null) return null;
        for (long key : net.cells) {
            int cx = (int) (key & 0x1FFFFFL) << 21 >> 21;
            int cy = (int) ((key >> 21) & 0x1FFFFFL) << 21 >> 21;
            int cz = (int) ((key >> 42) & 0x1FFFFFL) << 21 >> 21;
            for (int[] d : FACES) {
                BlockEntity e = world.blockEntityAt(cx + d[0], cy + d[1], cz + d[2]);
                if (e instanceof SteamBoilerEntity b) return b;
            }
        }
        return null;
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
        // The steam intake slot never holds items.
        if (slot == SLOT_STEAM && !(type == null || count <= 0)) return;
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
        // Only the input slot accepts insertion, and only smeltables.
        if (!Smelting.isSmeltable(type) || amount <= 0) return amount;
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

    // -----------------------------------------------------------------------
    // Persistence
    // -----------------------------------------------------------------------

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
        if (version != PAYLOAD_VERSION) {
            clearState();
            return;
        }
        for (int i = 0; i < SLOT_COUNT; i++) {
            int id = in.readUnsignedShort();
            int count = in.readInt();
            types[i] = id == 0 || count <= 0 ? null : BlockType.byId(id);
            counts[i] = types[i] == null ? 0 : count;
        }
        progress = Math.max(0f, in.readFloat());
    }

    private void clearState() {
        for (int i = 0; i < SLOT_COUNT; i++) {
            types[i] = null;
            counts[i] = 0;
        }
        progress = 0f;
    }

    /** Unused leftover list helper kept package-private for future bulk ops. */
    static List<ItemStack> emptyList() {
        return new ArrayList<>(0);
    }
}
