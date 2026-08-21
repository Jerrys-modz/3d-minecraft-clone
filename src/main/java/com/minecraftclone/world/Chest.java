package com.minecraftclone.world;

import com.minecraftclone.player.JoinedStorage;
import com.minecraftclone.player.StorageContainer;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/**
 * A placed chest: a {@link StorageContainer} with {@link #SLOT_COUNT} (3x9)
 * slots, persisted with its chunk via {@link ChunkStorage} so its contents
 * survive a restart. Right-clicking it opens the container GUI.
 * <p>
 * Adjacent chests merge at open-time: two mutual neighbours on any
 * horizontal axis become a 54-slot double, and a 2x2 square becomes a
 * 108-slot quad. Pairing looks at the placed block, not the entity, so a
 * chest that has never been opened still joins. The pattern is the same as
 * {@link Furnace}: implement {@link BlockEntity} for persistence and
 * {@link StorageContainer} for the slot API.
 */
public class Chest implements BlockEntity, StorageContainer {

    public static final String TYPE = "chest";

    /** 3 rows of 9, like Minecraft's single chest. */
    public static final int SLOT_COUNT = 27;

    /**
     * True when a world cell holds a chest block. Used so pairing can see
     * a neighbour that has been placed but never opened (no entity yet).
     */
    @FunctionalInterface
    public interface Occupied {
        boolean isChest(int x, int y, int z);
    }

    /** Supplies the persisted chest at a cell, creating it on first use. */
    @FunctionalInterface
    public interface Factory {
        Chest getOrCreate(int x, int y, int z);
    }

    /** Horizontal neighbour a chest would merge with, same y. */
    public record Partner(int x, int z) {}

    /** Cardinal offsets in preference order: west, east, north, south. */
    private static final int[][] CARDINALS = {
            {-1, 0}, {1, 0}, {0, -1}, {0, 1}
    };

    private final BlockType[] types = new BlockType[SLOT_COUNT];
    private final int[] counts = new int[SLOT_COUNT];

    /**
     * The unique neighbour this chest forms a double with, or null if it
     * stays single. Preference is west, then east, then north, then south;
     * a neighbour is accepted only when this cell is also that neighbour's
     * preferred partner, so a 3-in-a-row never oscillates (the west pair
     * sticks, the leftover east chest stays single) and an L-shape pairs
     * on the east-west axis.
     */
    public static Partner doublePartner(int x, int y, int z, Occupied occupied) {
        if (occupied == null || !occupied.isChest(x, y, z)) return null;
        for (int[] d : CARDINALS) {
            int nx = x + d[0];
            int nz = z + d[1];
            if (!occupied.isChest(nx, y, nz)) continue;
            Partner theirs = preferredPartner(nx, y, nz, occupied);
            if (theirs != null && theirs.x() == x && theirs.z() == z) {
                return new Partner(nx, nz);
            }
        }
        return null;
    }

    private static Partner preferredPartner(int x, int y, int z, Occupied occupied) {
        for (int[] d : CARDINALS) {
            int nx = x + d[0];
            int nz = z + d[1];
            if (occupied.isChest(nx, y, nz)) return new Partner(nx, nz);
        }
        return null;
    }

    /**
     * Storage shown when opening the chest at {@code (x,y,z)}: a 2x2 of
     * chest blocks becomes a 108-slot quad (row-major, west-to-east then
     * north-to-south); otherwise two mutual neighbours become a 54-slot
     * double; otherwise the single 27-slot chest. Neighbours are detected
     * by {@code occupied} (the placed block, not the entity), so a chest
     * that has never been opened still joins.
     */
    public static StorageContainer containerAt(int x, int y, int z, Occupied occupied, Factory factory) {
        if (occupied == null || factory == null) return null;
        if (!occupied.isChest(x, y, z)) return null;
        Chest here = factory.getOrCreate(x, y, z);
        if (here == null) return null;

        for (int ox = -1; ox <= 0; ox++) {
            for (int oz = -1; oz <= 0; oz++) {
                int x0 = ox == 0 ? x : x - 1;
                int z0 = oz == 0 ? z : z - 1;
                if (occupied.isChest(x0, y, z0)
                        && occupied.isChest(x0 + 1, y, z0)
                        && occupied.isChest(x0, y, z0 + 1)
                        && occupied.isChest(x0 + 1, y, z0 + 1)) {
                    Chest a = factory.getOrCreate(x0, y, z0);
                    Chest b = factory.getOrCreate(x0 + 1, y, z0);
                    Chest c = factory.getOrCreate(x0, y, z0 + 1);
                    Chest d = factory.getOrCreate(x0 + 1, y, z0 + 1);
                    if (a != null && b != null && c != null && d != null) {
                        return new JoinedStorage(new JoinedStorage(a, b), new JoinedStorage(c, d));
                    }
                }
            }
        }

        Partner p = doublePartner(x, y, z, occupied);
        if (p == null) return here;
        Chest other = factory.getOrCreate(p.x(), y, p.z());
        if (other == null) return here;
        if (p.x() < x || (p.x() == x && p.z() < z)) {
            return new JoinedStorage(other, here);
        }
        return new JoinedStorage(here, other);
    }

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public BlockType blockType() {
        return BlockType.CHEST;
    }

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
        if (type == null || amount <= 0) return amount;
        int max = StorageContainer.maxStack(type);
        int remaining = amount;
        for (int i = 0; i < SLOT_COUNT && remaining > 0; i++) {
            if (types[i] == type && counts[i] < max) {
                int space = max - counts[i];
                int take = Math.min(space, remaining);
                counts[i] += take;
                remaining -= take;
            }
        }
        for (int i = 0; i < SLOT_COUNT && remaining > 0; i++) {
            if (types[i] == null) {
                int take = Math.min(max, remaining);
                types[i] = type;
                counts[i] = take;
                remaining -= take;
            }
        }
        return remaining;
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

    /**
     * Format version byte written at the start of the chest payload.
     * 0 = legacy byte-ID format (IDs 0-255 only).
     * 1 = short-ID format (IDs 0-32767, added when IDs exceeded 255).
     */
    private static final byte PAYLOAD_VERSION = 1;

    /** Writes the chest's slots to {@code out} (see {@link ChunkStorage}). */
    @Override
    public void writeTo(DataOutput out) throws IOException {
        out.writeByte(PAYLOAD_VERSION);
        for (int i = 0; i < SLOT_COUNT; i++) {
            out.writeShort(types[i] == null ? 0 : types[i].id);
            out.writeByte(counts[i]);
        }
    }

    /** Restores the slots written by {@link #writeTo}. */
    @Override
    public void readFrom(DataInput in) throws IOException {
        byte maybeVersion = in.readByte();
        boolean newFormat = (maybeVersion == PAYLOAD_VERSION);
        if (newFormat) {
            for (int i = 0; i < SLOT_COUNT; i++) {
                int id = in.readUnsignedShort();
                int count = in.readUnsignedByte();
                if (id == 0 || count <= 0) {
                    types[i] = null;
                    counts[i] = 0;
                } else {
                    types[i] = BlockType.byId(id);
                    counts[i] = count;
                }
            }
        } else {
            // Legacy format: maybeVersion was actually the first slot's byte ID.
            int legacyCount = in.readUnsignedByte();
            if (legacyCount <= 0) {
                types[0] = null;
                counts[0] = 0;
            } else {
                types[0] = BlockType.byId(maybeVersion & 0xFF);
                counts[0] = legacyCount;
            }
            for (int i = 1; i < SLOT_COUNT; i++) {
                int id = in.readUnsignedByte();
                int count = in.readUnsignedByte();
                if (id == 0 || count <= 0) {
                    types[i] = null;
                    counts[i] = 0;
                } else {
                    types[i] = BlockType.byId(id);
                    counts[i] = count;
                }
            }
        }
    }
}
