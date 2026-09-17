package com.minecraftclone.world;

import com.minecraftclone.player.Inventory;
import com.minecraftclone.player.StorageContainer;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/**
 * A placed Primitive Blast Furnace: the first dedicated high-heat furnace.
 * Crafted from fire bricks (themselves smelted clay) and loaded like a
 * regular furnace. Iron-chain inputs become {@link BlockType#WROUGHT_IRON_INGOT};
 * {@link BlockType#STEEL_DUST} (wrought iron mixed with coal dust) becomes
 * {@link BlockType#STEEL_INGOT}. A regular furnace cannot reach this heat,
 * so steel stays gated behind the kiln.
 *
 * <p>Takes twice as long as a regular furnace ({@link #SMELT_TIME} = 16s)
 * and burns the same solid fuels. State persists with its chunk.
 */
public final class PrimitiveBlastFurnaceEntity implements BlockEntity, StorageContainer, ProgressMachine {

    public static final String TYPE = "primitive_blast_furnace";

    public static final int SLOT_INPUT = Furnace.SLOT_INPUT;
    public static final int SLOT_FUEL = Furnace.SLOT_FUEL;
    public static final int SLOT_OUTPUT = Furnace.SLOT_OUTPUT;
    public static final int SLOT_COUNT = Furnace.SLOT_COUNT;

    /** Seconds to refine one item — twice a regular furnace, this is a slow brick kiln. */
    public static final float SMELT_TIME = Furnace.SMELT_TIME * 2f;

    private final BlockType[] types = new BlockType[SLOT_COUNT];
    private final int[] counts = new int[SLOT_COUNT];
    private float burnTime;
    private float burnDuration;
    private float progress;

    @Override
    public String type() { return TYPE; }

    @Override
    public BlockType blockType() { return BlockType.PRIMITIVE_BLAST_FURNACE; }

    /**
     * Iron-chain input → wrought iron. Steel dust (wrought iron + coal dust)
     * → steel ingot. Vanilla iron ore/ingot/dust plus the crushed iron-bearing
     * minerals the regular furnace would smelt to iron.
     */
    public static BlockType outputFor(BlockType input) {
        if (input == null) return null;
        if (input == BlockType.STEEL_DUST) {
            return BlockType.STEEL_INGOT;
        }
        if (input == BlockType.IRON_ORE
                || input == BlockType.IRON_INGOT
                || input == BlockType.IRON_DUST
                || input == BlockType.CRUSHED_MAGNETITE
                || input == BlockType.CRUSHED_HEMATITE
                || input == BlockType.CRUSHED_BROWN_LIMONITE
                || input == BlockType.CRUSHED_YELLOW_LIMONITE
                || input == BlockType.CRUSHED_BANDED_IRON
                || input == BlockType.CRUSHED_PYRITE
                || input == BlockType.CRUSHED_ARSENOPYRITE
                || input == BlockType.CRUSHED_OLIVINE) {
            return BlockType.WROUGHT_IRON_INGOT;
        }
        return null;
    }

    public static boolean isInput(BlockType type) {
        return outputFor(type) != null;
    }

    @Override
    public boolean isActive() {
        return burnTime > 0;
    }

    @Override
    public int activeLightLevel() {
        return burnTime > 0 ? 13 : 0;
    }

    public boolean isBurning() {
        return burnTime > 0;
    }

    @Override
    public float burnFraction() {
        return burnDuration <= 0 ? 0f : burnTime / burnDuration;
    }

    @Override
    public float progressFraction() {
        if (isBurning() && canSmelt() && progress > 0) {
            return progress / SMELT_TIME;
        }
        return 0;
    }

    public boolean canSmelt() {
        BlockType output = outputFor(types[SLOT_INPUT]);
        if (output == null) return false;
        BlockType held = types[SLOT_OUTPUT];
        if (held != null) {
            if (held != output) return false;
            if (counts[SLOT_OUTPUT] >= Inventory.maxStack(output)) return false;
        }
        return true;
    }

    @Override
    public void tick(float dt) {
        if (dt <= 0) return;
        if (burnTime <= 0) {
            if (Furnace.isFuel(types[SLOT_FUEL]) && counts[SLOT_FUEL] > 0) {
                BlockType fuel = types[SLOT_FUEL];
                counts[SLOT_FUEL]--;
                if (counts[SLOT_FUEL] == 0) types[SLOT_FUEL] = null;
                burnDuration = Furnace.fuelDuration(fuel);
                burnTime = burnDuration;
            } else {
                return;
            }
        }
        if (canSmelt()) {
            progress += dt;
            while (progress >= SMELT_TIME && canSmelt()) {
                progress -= SMELT_TIME;
                BlockType output = outputFor(types[SLOT_INPUT]);
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
        burnTime = Math.max(0, burnTime - dt);
    }

    @Override
    public int size() { return SLOT_COUNT; }

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
        if (type == null || amount <= 0) return amount;
        int slot = Furnace.isFuel(type) ? SLOT_FUEL : SLOT_INPUT;
        if (slot == SLOT_INPUT && !isInput(type)) return amount;
        int max = StorageContainer.maxStack(type);
        if (types[slot] != null && types[slot] != type) return amount;
        int space = max - counts[slot];
        if (space <= 0) return amount;
        int take = Math.min(space, amount);
        setSlot(slot, type, counts[slot] + take);
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

    private static final byte PAYLOAD_VERSION = 1;

    @Override
    public void writeTo(DataOutput out) throws IOException {
        out.writeByte(PAYLOAD_VERSION);
        out.writeFloat(burnTime);
        out.writeFloat(burnDuration);
        out.writeFloat(progress);
        for (int i = 0; i < SLOT_COUNT; i++) {
            out.writeShort(types[i] == null ? 0 : types[i].id);
            out.writeByte(counts[i]);
        }
    }

    @Override
    public void readFrom(DataInput in) throws IOException {
        byte version = in.readByte();
        if (version != PAYLOAD_VERSION) {
            burnTime = burnDuration = progress = 0;
            for (int i = 0; i < SLOT_COUNT; i++) {
                types[i] = null;
                counts[i] = 0;
            }
            return;
        }
        burnTime = in.readFloat();
        burnDuration = in.readFloat();
        progress = in.readFloat();
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
    }
}
