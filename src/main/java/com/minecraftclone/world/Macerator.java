package com.minecraftclone.world;

import com.minecraftclone.player.Inventory;
import com.minecraftclone.player.MaceratorRecipes;
import com.minecraftclone.player.StorageContainer;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/**
 * A Macerator machine: turns ore blocks into crushed ore. Works like Minecraft's
 * furnace but takes ore as input and produces crushed ore as output.
 * <p>
 * The macerator grinds ore blocks into finer crushed forms. Unlike a furnace
 * which requires fuel, the macerator runs on EU (energy units) which are
 * generated elsewhere in the Greg Tech system. For now, we implement a simpler
 * version that processes instantly or with a simple timer.
 */
public class Macerator implements BlockEntity, StorageContainer {

    public static final String TYPE = "macerator";

    public static final int SLOT_INPUT = 0;
    public static final int SLOT_OUTPUT = 1;
    public static final int SLOT_COUNT = 2;

    /** Seconds of processing needed to macerate one ore block into crushed ore. */
    public static final float PROCESS_TIME = 4f;

    private final BlockType[] types = new BlockType[SLOT_COUNT];
    private final int[] counts = new int[SLOT_COUNT];

    /** Seconds accumulated toward processing the current input item. */
    private float progress;

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public BlockType blockType() {
        return BlockType.FURNACE; // TODO: Add MACERATOR block type later
    }

    public BlockType typeOf(int slot) {
        return types[slot];
    }

    public int countOf(int slot) {
        return counts[slot];
    }

    public boolean isEmpty(int slot) {
        return types[slot] == null;
    }

    public boolean isEmpty() {
        for (int i = 0; i < SLOT_COUNT; i++) {
            if (types[i] != null) return false;
        }
        return true;
    }

    @Override
    public int size() {
        return SLOT_COUNT;
    }

    /** Adds {@code amount} of {@code type}: ore goes to input, anything else is left over. */
    @Override
    public int add(BlockType type, int amount) {
        if (type == null || amount <= 0) return amount;
        int slot = MaceratorRecipes.isSmeltable(type) ? SLOT_INPUT : SLOT_OUTPUT;
        int max = StorageContainer.maxStack(type);
        int current = counts[slot];
        if (types[slot] != null && types[slot] != type) return amount;
        int space = max - current;
        if (space <= 0) return amount;
        int take = Math.min(space, amount);
        setSlot(slot, type, current + take);
        return amount - take;
    }

    @Override
    public int getCount(BlockType type) {
        int total = 0;
        for (int i = 0; i < SLOT_COUNT; i++) {
            if (types[i] == type) total += counts[i];
        }
        return total;
    }

    /** Replaces slot {@code slot}'s stack; a null type or non-positive count clears it. */
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

    public void clearSlot(int slot) {
        types[slot] = null;
        counts[slot] = 0;
    }

    public boolean isProcessing() {
        return progress > 0;
    }

    /** True while the macerator has input ore and can place output in the output slot. */
    public boolean canProcess() {
        BlockType input = types[SLOT_INPUT];
        BlockType output = MaceratorRecipes.outputFor(input);
        if (output == null) return false;
        BlockType held = types[SLOT_OUTPUT];
        if (held != null) {
            if (held != output) return false;
            if (counts[SLOT_OUTPUT] >= Inventory.maxStack(output)) return false;
        }
        return true;
    }

    /** 0..1 how far the current ore is toward completion - drives the GUI arrow. */
    public float progressFraction() {
        if (canProcess() && progress > 0) {
            return progress / PROCESS_TIME;
        }
        return 0;
    }

    /**
     * Advances the macerator by {@code dt} seconds: accumulates processing time
     * and produces crushed ore when an ore finishes processing.
     */
    public void tick(float dt) {
        if (dt <= 0) return;
        if (canProcess()) {
            progress += dt;
            while (progress >= PROCESS_TIME && canProcess()) {
                progress -= PROCESS_TIME;
                BlockType output = MaceratorRecipes.outputFor(types[SLOT_INPUT]);
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
    }

    /** Writes the macerator's slots and processing state to {@code out}. */
    public void writeTo(DataOutput out) throws IOException {
        out.writeFloat(progress);
        for (int i = 0; i < SLOT_COUNT; i++) {
            out.writeByte(types[i] == null ? 0 : types[i].id);
            out.writeByte(counts[i]);
        }
    }

    /** Restores the slots and processing state written by {@link #writeTo}. */
    public void readFrom(DataInput in) throws IOException {
        progress = in.readFloat();
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
