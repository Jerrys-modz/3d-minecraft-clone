package com.minecraftclone.player;

import com.minecraftclone.world.BlockType;

/**
 * Hand grinding: a mortar (and pestle) turns ore, crushed ore or an impure
 * pile into dust at 1:1, no machine required. Right-click with the mortar
 * selected (or with a grindable ore selected while a mortar sits in the bag)
 * to grind one item. Each grind wears the mortar; it breaks after
 * {@link com.minecraftclone.world.Mining}'s registered uses.
 *
 * <p>Dust lookup is name-driven ({@code COPPER_ORE}/{@code CRUSHED_COPPER}/
 * {@code IMPURE_COPPER} → {@code COPPER_DUST}) plus a fallback through
 * {@link Smelting}: a crushed mineral that smelts to {@code IRON_INGOT}
 * grinds to {@code IRON_DUST}. That keeps the table in sync with the GTNH
 * ore set without a hundreds-long registry.
 */
public final class Mortar {

    private Mortar() {
    }

    /** True if {@code type} is the mortar item. */
    public static boolean isMortar(BlockType type) {
        return type == BlockType.MORTAR;
    }

    /**
     * The dust this input grinds into, or {@code null} if it isn't grindable.
     * Already-dust items return themselves (so callers can skip them).
     */
    public static BlockType dustFor(BlockType input) {
        if (input == null) return null;
        String name = input.name();
        if (name.endsWith("_DUST")) return input;

        // Two-step coal: ore → coal, then coal → coal dust. Named lookup
        // would send COAL_ORE straight to COAL_DUST now that the dust exists.
        if (input == BlockType.COAL_ORE) return BlockType.COAL;
        if (input == BlockType.COAL) return BlockType.COAL_DUST;

        BlockType named = namedDust(input);
        if (named != null) return named;

        // Vanilla ores that don't follow the GTNH *_ORE → *_DUST pattern.
        if (input == BlockType.IRON_ORE) return BlockType.IRON_DUST;
        if (input == BlockType.GOLD_ORE) return BlockType.GOLD_DUST;

        // Crushed/impure minerals whose smelting output is an ingot: grind to
        // that metal's dust when the dust item exists (magnetite → iron dust).
        BlockType smelted = Smelting.outputFor(input);
        if (smelted != null) {
            String smeltName = smelted.name();
            if (smeltName.endsWith("_INGOT")) {
                BlockType dust = byName(smeltName.substring(0, smeltName.length() - 6) + "_DUST");
                if (dust != null) return dust;
            }
            if (smelted == BlockType.COAL) return BlockType.COAL;
        }
        return null;
    }

    /** True if {@code input} can be ground into a different dust item. */
    public static boolean isGrindable(BlockType input) {
        BlockType dust = dustFor(input);
        return dust != null && dust != input;
    }

    /**
     * Grinds one item from {@code inv}. Prefer the selected hotbar slot when
     * it's grindable; otherwise the first grindable stack. Requires a mortar
     * in the inventory (or selected). Returns the dust produced, or {@code null}
     * if nothing happened. When {@code creative} is true the mortar does not
     * wear and the input is not consumed.
     *
     * @param mortarBroke set to {@code true} (index 0) when this grind wore
     *                    the mortar out; ignored when {@code null}
     */
    public static BlockType grind(Inventory inv, int selectedSlot, ToolDurability durability,
                                  boolean creative, boolean[] mortarBroke) {
        if (inv == null) return null;
        if (mortarBroke != null && mortarBroke.length > 0) mortarBroke[0] = false;

        int mortarSlot = indexOf(inv, BlockType.MORTAR, selectedSlot);
        if (mortarSlot < 0) return null;

        int inputSlot = grindableSlot(inv, selectedSlot, mortarSlot);
        if (inputSlot < 0) return null;

        BlockType input = inv.typeOf(inputSlot);
        BlockType dust = dustFor(input);
        if (dust == null || dust == input) return null;

        if (!creative) {
            if (!inv.remove(input, 1)) return null;
            int leftover = inv.add(dust, 1);
            if (leftover > 0) {
                // Bag was full: put the ore back so the grind is a no-op.
                inv.add(input, 1);
                return null;
            }
            if (durability != null && durability.use(BlockType.MORTAR)) {
                inv.remove(BlockType.MORTAR, 1);
                if (mortarBroke != null && mortarBroke.length > 0) mortarBroke[0] = true;
            }
        } else {
            // Creative: give the dust without consuming the ore or mortar.
            inv.add(dust, 1);
        }
        return dust;
    }

    private static BlockType namedDust(BlockType input) {
        String name = input.name();
        String metal;
        if (name.startsWith("CRUSHED_")) {
            metal = name.substring("CRUSHED_".length());
        } else if (name.startsWith("IMPURE_")) {
            metal = name.substring("IMPURE_".length());
        } else if (name.startsWith("SMALL_") && name.endsWith("_ORE")) {
            metal = name.substring("SMALL_".length(), name.length() - 4);
        } else if (name.endsWith("_ORE")) {
            metal = name.substring(0, name.length() - 4);
        } else {
            return null;
        }
        return byName(metal + "_DUST");
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

    private static int grindableSlot(Inventory inv, int selected, int mortarSlot) {
        if (selected >= 0 && selected < inv.size() && selected != mortarSlot
                && isGrindable(inv.typeOf(selected))) {
            return selected;
        }
        for (int i = 0; i < inv.size(); i++) {
            if (i == mortarSlot) continue;
            if (isGrindable(inv.typeOf(i))) return i;
        }
        return -1;
    }
}
