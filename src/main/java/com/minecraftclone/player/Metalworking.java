package com.minecraftclone.player;

import com.minecraftclone.world.BlockType;
import com.minecraftclone.world.Mining;

/**
 * Hand metalworking: a hammer flattens one ingot into a plate, and a file
 * draws one ingot into a rod. Right-click with the tool selected (or with
 * the ingot selected while the tool sits in the bag). Each use wears the
 * tool; hammers use their existing mining durability, the file lasts
 * {@link Mining}'s registered uses.
 *
 * <p>Lookup is name-driven: {@code COPPER_INGOT} → {@code COPPER_PLATE} /
 * {@code COPPER_ROD}. Metals without a plate/rod item are skipped, so the
 * table stays in sync with the enum without a hundreds-long registry.
 */
public final class Metalworking {

    private Metalworking() {
    }

    public static boolean isFile(BlockType type) {
        return type == BlockType.FILE;
    }

    public static boolean isHammer(BlockType type) {
        return Mining.isHammer(type);
    }

    public static boolean hasHammer(Inventory inv) {
        return inv != null && hammerSlot(inv, -1) >= 0;
    }

    public static boolean hasFile(Inventory inv) {
        return inv != null && indexOf(inv, BlockType.FILE, -1) >= 0;
    }

    /** Plate this ingot hammers into, or {@code null} if it isn't plateable. */
    public static BlockType plateFor(BlockType input) {
        return formFor(input, "_PLATE");
    }

    /** Rod this ingot files into, or {@code null} if it isn't drawable. */
    public static BlockType rodFor(BlockType input) {
        return formFor(input, "_ROD");
    }

    public static boolean isPlateable(BlockType input) {
        return plateFor(input) != null;
    }

    public static boolean isDrawable(BlockType input) {
        return rodFor(input) != null;
    }

    /**
     * Hammers one ingot from {@code inv} into a plate. Prefer the selected
     * slot when it's plateable; otherwise the first plateable stack. Requires
     * a hammer in the inventory (or selected). Returns the plate, or
     * {@code null} if nothing happened. When {@code creative} is true the
     * hammer does not wear and the ingot is not consumed.
     *
     * @param toolBroke set to {@code true} (index 0) when this use broke the
     *                  hammer; ignored when {@code null}
     */
    public static BlockType hammerPlate(Inventory inv, int selectedSlot, ToolDurability durability,
                                        boolean creative, boolean[] toolBroke) {
        return process(inv, selectedSlot, durability, creative, toolBroke, true);
    }

    /**
     * Files one ingot from {@code inv} into a rod. Same contract as
     * {@link #hammerPlate} but requires a file and yields a rod.
     */
    public static BlockType fileRod(Inventory inv, int selectedSlot, ToolDurability durability,
                                    boolean creative, boolean[] toolBroke) {
        return process(inv, selectedSlot, durability, creative, toolBroke, false);
    }

    private static BlockType process(Inventory inv, int selectedSlot, ToolDurability durability,
                                     boolean creative, boolean[] toolBroke, boolean plate) {
        if (inv == null) return null;
        if (toolBroke != null && toolBroke.length > 0) toolBroke[0] = false;

        int toolSlot = plate ? hammerSlot(inv, selectedSlot) : indexOf(inv, BlockType.FILE, selectedSlot);
        if (toolSlot < 0) return null;
        BlockType toolType = inv.typeOf(toolSlot);

        int inputSlot = metalSlot(inv, selectedSlot, toolSlot, plate);
        if (inputSlot < 0) return null;

        BlockType input = inv.typeOf(inputSlot);
        BlockType output = plate ? plateFor(input) : rodFor(input);
        if (output == null) return null;

        if (!creative) {
            if (!inv.remove(input, 1)) return null;
            int leftover = inv.add(output, 1);
            if (leftover > 0) {
                inv.add(input, 1);
                return null;
            }
            if (durability != null && durability.use(toolType)) {
                inv.remove(toolType, 1);
                if (toolBroke != null && toolBroke.length > 0) toolBroke[0] = true;
            }
        } else {
            inv.add(output, 1);
        }
        return output;
    }

    private static BlockType formFor(BlockType input, String suffix) {
        if (input == null) return null;
        String name = input.name();
        if (!name.endsWith("_INGOT")) return null;
        return byName(name.substring(0, name.length() - 6) + suffix);
    }

    private static BlockType byName(String name) {
        try {
            return BlockType.valueOf(name);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static int indexOf(Inventory inv, BlockType type, int preferred) {
        if (preferred >= 0 && preferred < inv.size() && inv.typeOf(preferred) == type) {
            return preferred;
        }
        for (int i = 0; i < inv.size(); i++) {
            if (inv.typeOf(i) == type) return i;
        }
        return -1;
    }

    private static int hammerSlot(Inventory inv, int preferred) {
        if (preferred >= 0 && preferred < inv.size() && isHammer(inv.typeOf(preferred))) {
            return preferred;
        }
        for (int i = 0; i < inv.size(); i++) {
            if (isHammer(inv.typeOf(i))) return i;
        }
        return -1;
    }

    private static int metalSlot(Inventory inv, int selected, int toolSlot, boolean plate) {
        if (selected >= 0 && selected < inv.size() && selected != toolSlot) {
            BlockType t = inv.typeOf(selected);
            if (plate ? isPlateable(t) : isDrawable(t)) return selected;
        }
        for (int i = 0; i < inv.size(); i++) {
            if (i == toolSlot) continue;
            BlockType t = inv.typeOf(i);
            if (plate ? isPlateable(t) : isDrawable(t)) return i;
        }
        return -1;
    }
}
