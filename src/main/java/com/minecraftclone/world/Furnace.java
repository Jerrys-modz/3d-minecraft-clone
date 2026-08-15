package com.minecraftclone.world;

import com.minecraftclone.player.Inventory;
import com.minecraftclone.player.Smelting;

/**
 * The state of a single placed furnace: three slots (input, fuel, output) and
 * a smelting timer. Unlike the old instant smelt-on-keypress, a furnace works
 * like Minecraft's - load it with ore and coal and it smelts over time, ticking
 * forward with the world (see {@link World#tickFurnaces(float)}).
 * <p>
 * Fuel is coal ore ({@link Smelting#FUEL}); each unit burns for {@link
 * #BURN_TIME} seconds, during which a smeltable input advances {@link
 * #SMELT_TIME} seconds per item. Progress pauses while the furnace is out of
 * fuel (without being lost) and resumes when more fuel is added; once the
 * timer completes, one input is consumed and one output is produced.
 * <p>
 * A furnace is intentionally a tiny, self-contained container (no "inventory"
 * bag of its own beyond its three slots), kept in memory per block position by
 * {@link World}; its contents survive while the world is loaded but are not
 * yet written to the save file.
 */
public class Furnace {

    public static final int SLOT_INPUT = 0;
    public static final int SLOT_FUEL = 1;
    public static final int SLOT_OUTPUT = 2;
    public static final int SLOT_COUNT = 3;

    /** Seconds of smelting needed to refine one item. */
    public static final float SMELT_TIME = 8f;
    /** Seconds a single unit of fuel burns for. */
    public static final float BURN_TIME = 8f;

    private final BlockType[] types = new BlockType[SLOT_COUNT];
    private final int[] counts = new int[SLOT_COUNT];

    /** Seconds of fuel left in the burn (decrements while burning). */
    private float burnTime;
    /** Seconds accumulated toward refining the current input item. */
    private float progress;

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

    /** 0..1 how much of the current fuel's burn is left - drives the GUI flame. */
    public float burnFraction() {
        return burnTime / BURN_TIME;
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
     * when coal is present, burns down the fuel, and accumulates smelting
     * progress toward the next item. Does nothing (and loses no progress) while
     * there is no fuel.
     */
    public void tick(float dt) {
        if (dt <= 0) return;
        if (burnTime <= 0) {
            if (types[SLOT_FUEL] == Smelting.FUEL && counts[SLOT_FUEL] > 0) {
                counts[SLOT_FUEL]--;
                if (counts[SLOT_FUEL] == 0) types[SLOT_FUEL] = null;
                burnTime = BURN_TIME;
            } else {
                return;
            }
        }
        // Advance progress before burning down the fuel so the tick that exhausts
        // a coal still finishes the item it paid for (1 fuel = 1 smelt).
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

    /** Total number of items in the furnace (used to decide whether to keep it around). */
    public int totalItems() {
        int total = 0;
        for (int i = 0; i < SLOT_COUNT; i++) {
            total += counts[i];
        }
        return total;
    }
}
