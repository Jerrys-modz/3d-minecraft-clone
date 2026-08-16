package com.minecraftclone.world;

import com.minecraftclone.player.Inventory;
import com.minecraftclone.player.Smelting;
import com.minecraftclone.player.StorageContainer;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.EnumMap;
import java.util.Map;

/**
 * The state of a single placed furnace: three slots (input, fuel, output) and
 * a smelting timer. Unlike the old instant smelt-on-keypress, a furnace works
 * like Minecraft's - load it with ore and fuel and it smelts over time, ticking
 * forward with the world (see {@link World#tickBlockEntities(float)}).
 * <p>
 * Fuel is anything in {@link #isFuel(BlockType)} - coal is the workhorse (one
 * coal smelts {@link #COAL_SMELTS} items), with wood logs, planks and sticks
 * as weaker fallbacks. A unit of fuel burns for {@link #fuelDuration(BlockType)}
 * seconds, during which a smeltable input advances {@link #SMELT_TIME} seconds
 * per item. Progress pauses while the furnace is out of
 * fuel (without being lost) and resumes when more fuel is added; once the
 * timer completes, one input is consumed and one output is produced.
 * <p>
 * A furnace is intentionally a tiny, self-contained container (no "inventory"
 * bag of its own beyond its three slots), kept in memory per block position by
 * {@link World} as a {@link BlockEntity}. Its contents and smelting progress
 * are saved alongside its chunk (see {@link ChunkStorage}) and restored when
 * the chunk reloads, so a furnace keeps working across a restart.
 */
public class Furnace implements BlockEntity, StorageContainer {

    public static final String TYPE = "furnace";

    public static final int SLOT_INPUT = 0;
    public static final int SLOT_FUEL = 1;
    public static final int SLOT_OUTPUT = 2;
    public static final int SLOT_COUNT = 3;

    /** Seconds of smelting needed to refine one item. */
    public static final float SMELT_TIME = 8f;
    /** How many items a single coal smelts (12, like Minecraft's coal). */
    public static final int COAL_SMELTS = 12;
    /** Seconds a single coal burns for. */
    public static final float COAL_BURN_TIME = COAL_SMELTS * SMELT_TIME;

    /** How many seconds each fuel type burns for, keyed by {@link BlockType}. */
    private static final Map<BlockType, Float> FUEL_DURATION = new EnumMap<>(BlockType.class);

    static {
        fuel(BlockType.COAL, COAL_SMELTS);
        fuel(BlockType.WOOD_LOG, 2);
        fuel(BlockType.PLANKS, 2);
        fuel(BlockType.STICK, 1);
    }

    /** Registers a fuel: {@code smelts} items smelted per unit of that fuel. */
    private static void fuel(BlockType type, float smelts) {
        FUEL_DURATION.put(type, smelts * SMELT_TIME);
    }

    /** True if {@code type} burns in a furnace. */
    public static boolean isFuel(BlockType type) {
        return type != null && FUEL_DURATION.containsKey(type);
    }

    /** How many seconds one unit of {@code type} burns for (0 if it isn't a fuel). */
    public static float fuelDuration(BlockType type) {
        Float seconds = FUEL_DURATION.get(type);
        return seconds == null ? 0f : seconds;
    }

    private final BlockType[] types = new BlockType[SLOT_COUNT];
    private final int[] counts = new int[SLOT_COUNT];

    /** Seconds of fuel left in the burn (decrements while burning). */
    private float burnTime;
    /** Total seconds the current fuel burned for (drives the GUI flame fraction). */
    private float burnDuration;
    /** Seconds accumulated toward refining the current input item. */
    private float progress;

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public BlockType blockType() {
        return BlockType.FURNACE;
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

    /** Adds {@code amount} of {@code type}: ore goes to input, fuel to the fuel slot, anything else is left over. */
    @Override
    public int add(BlockType type, int amount) {
        if (type == null || amount <= 0) return amount;
        int slot = isFuel(type) ? SLOT_FUEL : SLOT_INPUT;
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

    public boolean isBurning() {
        return burnTime > 0;
    }

    /** The furnace is "active" (glowing front, emitting light) while it's burning. */
    @Override
    public boolean isActive() {
        return isBurning();
    }

    /** A burning furnace lights its surroundings like a torch (Minecraft's lit furnace is level 13). */
    @Override
    public int activeLightLevel() {
        return isBurning() ? 13 : 0;
    }

    /** 0..1 how much of the current fuel's burn is left - drives the GUI flame. */
    public float burnFraction() {
        return burnDuration <= 0 ? 0f : burnTime / burnDuration;
    }

    /** 0..1 how far the current item is toward completion - drives the GUI arrow. */
    public float progressFraction() {
        if (isBurning() && canSmelt() && progress > 0) {
            return progress / SMELT_TIME;
        }
        return 0;
    }

    /** True while the furnace is lit and loaded with smeltable input and an output is possible. */
    public boolean canSmelt() {
        BlockType input = types[SLOT_INPUT];
        BlockType output = Smelting.outputFor(input);
        if (output == null) return false;
        BlockType held = types[SLOT_OUTPUT];
        if (held != null) {
            if (held != output) return false;
            if (counts[SLOT_OUTPUT] >= Inventory.maxStack(output)) return false;
        }
        return true;
    }

    /**
     * Advances the furnace by {@code dt} seconds of world time: lights the fire
     * when fuel is present, burns down the fuel, and accumulates smelting
     * progress toward the next item. Does nothing (and loses no progress) while
     * there is no fuel.
     */
    public void tick(float dt) {
        if (dt <= 0) return;
        if (burnTime <= 0) {
            if (isFuel(types[SLOT_FUEL]) && counts[SLOT_FUEL] > 0) {
                BlockType fuel = types[SLOT_FUEL];
                counts[SLOT_FUEL]--;
                if (counts[SLOT_FUEL] == 0) types[SLOT_FUEL] = null;
                burnDuration = fuelDuration(fuel);
                burnTime = burnDuration;
            } else {
                return;
            }
        }
        // Advance progress before burning down the fuel so the tick that exhausts
        // a unit of fuel still finishes the item it paid for.
        if (canSmelt()) {
            progress += dt;
            while (progress >= SMELT_TIME && canSmelt()) {
                progress -= SMELT_TIME;
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
        burnTime = Math.max(0, burnTime - dt);
    }

    /** Writes the furnace's slots and smelting state to {@code out} (see {@link ChunkStorage}). */
    public void writeTo(DataOutput out) throws IOException {
        out.writeFloat(burnTime);
        out.writeFloat(burnDuration);
        out.writeFloat(progress);
        for (int i = 0; i < SLOT_COUNT; i++) {
            out.writeByte(types[i] == null ? 0 : types[i].id);
            out.writeByte(counts[i]);
        }
    }

    /** Restores the slots and smelting state written by {@link #writeTo}. */
    public void readFrom(DataInput in) throws IOException {
        burnTime = in.readFloat();
        burnDuration = in.readFloat();
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
