package com.minecraftclone.world;

import com.minecraftclone.player.StorageContainer;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/**
 * A placed chest: a {@link StorageContainer} with {@link #SLOT_COUNT} (3x9)
 * slots, persisted with its chunk via {@link ChunkStorage} so its contents
 * survive a restart. Right-clicking it opens the container GUI.
 * <p>
 * The pattern is deliberately the same as {@link Furnace}: implement
 * {@link BlockEntity} for persistence and {@link StorageContainer} for the
 * slot API, and the GUI, mouse controller and quick-move logic all work
 * against it with no per-container code - the point of the shared
 * {@link StorageContainer} contract. A future item network (AE2-style) can
 * expose the same interface backed by virtual storage.
 */
public class Chest implements BlockEntity, StorageContainer {

    public static final String TYPE = "chest";

    /** 3 rows of 9, like Minecraft's single chest. */
    public static final int SLOT_COUNT = 27;

    private final BlockType[] types = new BlockType[SLOT_COUNT];
    private final int[] counts = new int[SLOT_COUNT];

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

    /** Writes the chest's slots to {@code out} (see {@link ChunkStorage}). */
    @Override
    public void writeTo(DataOutput out) throws IOException {
        for (int i = 0; i < SLOT_COUNT; i++) {
            out.writeByte(types[i] == null ? 0 : types[i].id);
            out.writeByte(counts[i]);
        }
    }

    /** Restores the slots written by {@link #writeTo}. */
    @Override
    public void readFrom(DataInput in) throws IOException {
        for (int i = 0; i < SLOT_COUNT; i++) {
            int id = in.readUnsignedByte();
            int count = in.readUnsignedByte();
            if (id == 0 || count <= 0) {
                types[i] = null;
                counts[i] = 0;
            } else {
                types[i] = BlockType.byId((byte) id);
                counts[i] = count;
            }
        }
    }
}
