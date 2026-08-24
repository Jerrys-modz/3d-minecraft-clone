package com.minecraftclone.world;

import com.minecraftclone.player.Inventory;
import com.minecraftclone.player.ItemStack;
import com.minecraftclone.player.Smelting;
import com.minecraftclone.player.StorageContainer;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/**
 * A placed Steam Macerator: crushes smeltable ore into double output using
 * steam power. The core progression incentive of the Steam Age - build a
 * boiler and pipes, and every ore you mine yields twice as much.
 *
 * <h3>How it works</h3>
 * <ul>
 *   <li><b>Input</b> (slot 0): any smeltable item (see {@link Smelting#isSmeltable}).</li>
 *   <li><b>Heat</b>: draws steam from an adjacent, steaming Steam Boiler
 *       (direct adjacency or through connected pipes).</li>
 *   <li><b>Output</b> (slot 1): 2x whatever the furnace would produce from
 *       the same input.</li>
 * </ul>
 *
 * <p>The entity implements {@link StorageContainer} with 2 slots and
 * {@link ProgressMachine} for the HUD bars, so it renders and behaves like a
 * furnace variant with the existing FURNACE GUI kind.
 */
public final class SteamMaceratorEntity implements BlockEntity, StorageContainer, ProgressMachine {

    public static final String TYPE = "steam_macerator";

    public static final int SLOT_INPUT = 0;
    public static final int SLOT_OUTPUT = 1;
    public static final int SLOT_COUNT = 2;

    /** Real-time seconds to crush one item (same pace as a furnace). */
    public static final float CRUSH_SECONDS = Furnace.SMELT_TIME;

    /** Output multiplier: the whole point of the macerator. */
    public static final int YIELD_MULTIPLIER = 2;

    private final BlockType[] types = new BlockType[SLOT_COUNT];
    private final int[] counts = new int[SLOT_COUNT];
    private float progress;

    private World world;
    private int posX, posY, posZ;
    private boolean attached;
    private float boilerCheckTimer;
    /** Cached boiler reference (package-visible for test injection). */
    SteamBoilerEntity boiler;
    private float lastSteamFraction;

    public SteamMaceratorEntity() {
    }

    @Override
    public String type() { return TYPE; }

    @Override
    public BlockType blockType() { return BlockType.STEAM_MACERATOR; }

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

    /** 0..1 progress of the item currently being crushed (drives the arrow). */
    public float progressFraction() {
        return Math.min(1f, Math.max(0f, progress / CRUSH_SECONDS));
    }

    /** True if the input can produce output into the empty/matching output slot. */
    public boolean canCrush() {
        BlockType input = types[SLOT_INPUT];
        BlockType output = Smelting.outputFor(input);
        if (output == null) return false;
        BlockType held = types[SLOT_OUTPUT];
        return held == null || held == output;
    }

    @Override
    public void tick(float dt) {
        if (dt <= 0) return;
        boilerCheckTimer -= dt;
        if (boilerCheckTimer <= 0f) {
            boilerCheckTimer = 0.5f;
            if (attached && world != null) {
                // Scan for a steaming boiler: direct adjacency or through pipes.
                com.minecraftclone.world.pipes.PipeNetwork net =
                        world.pipeNetworks().networkAt(com.minecraftclone.world.pipes.PipeType.STEAM, posX, posY, posZ);
                boiler = null;
                if (net != null) {
                    for (long key : net.cells) {
                        int cx = (int) (key & 0x1FFFFFL) << 21 >> 21;
                        int cy = (int) ((key >> 21) & 0x1FFFFFL) << 21 >> 21;
                        int cz = (int) ((key >> 42) & 0x1FFFFFL) << 21 >> 21;
                        for (int[] d : new int[][]{{1,0,0},{-1,0,0},{0,1,0},{0,-1,0},{0,0,1},{0,0,-1}}) {
                            BlockEntity e = world.blockEntityAt(cx + d[0], cy + d[1], cz + d[2]);
                            if (e instanceof SteamBoilerEntity b) { boiler = b; break; }
                        }
                        if (boiler != null) break;
                    }
                }
                // Also check direct adjacency (no pipes needed).
                for (int[] d : new int[][]{{1,0,0},{-1,0,0},{0,1,0},{0,-1,0},{0,0,1},{0,0,-1}}) {
                    BlockEntity e = world.blockEntityAt(posX + d[0], posY + d[1], posZ + d[2]);
                    if (e instanceof SteamBoilerEntity b) { boiler = b; break; }
                }
            }
        }
        boolean hot = boiler != null && boiler.steamSeconds() > 0f && canCrush();
        if (!hot) {
            lastSteamFraction = 0f;
            return;
        }

        float draw = Math.min(dt, boiler.steamSeconds());
        boiler.drainSteam(draw);
        lastSteamFraction = Math.min(1f, boiler.steamSeconds() / SteamBoilerEntity.MAX_STEAM_SECONDS);

        progress += dt;
        while (progress >= CRUSH_SECONDS && canCrush()) {
            progress -= CRUSH_SECONDS;
            BlockType result = Smelting.outputFor(types[SLOT_INPUT]);
            counts[SLOT_INPUT]--;
            if (counts[SLOT_INPUT] == 0) types[SLOT_INPUT] = null;
            if (types[SLOT_OUTPUT] == result) {
                counts[SLOT_OUTPUT] += YIELD_MULTIPLIER;
            } else {
                types[SLOT_OUTPUT] = result;
                counts[SLOT_OUTPUT] = YIELD_MULTIPLIER;
            }
        }
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
}
