package com.minecraftclone.world.tinkers;

import com.minecraftclone.world.BlockType;
import com.minecraftclone.world.Mining.ToolKind;

import java.util.List;

/**
 * The data payload for a Tinkers' Construct item carried in an inventory slot.
 *
 * <p>Two concrete forms exist:
 * <ul>
 *   <li>{@link Part} — an unassembled tool part produced at the Part Builder.
 *       Holds one {@link ToolPartType} (shape) and one {@link BlockType}
 *       (material) — the material can be <em>any</em> type registered in
 *       {@link TinkersRegistry}, including modded ores added later.</li>
 *   <li>{@link Tool} — an assembled modular tool produced at the Tool Station.
 *       Carries an ordered list of {@link ToolLayer layers} (head, rod, …),
 *       a computed max-durability, and a mutable remaining-durability counter.
 *       Tool stats (tier, speed) derive from the head layer's material at
 *       query time via {@link TinkersRegistry}, so they update automatically
 *       when material stats change.</li>
 * </ul>
 *
 * <p>These objects are never stored directly in a slot — they ride inside an
 * {@link com.minecraftclone.player.ItemStack} alongside a sentinel
 * {@link BlockType} ({@code TINKERS_PART} or {@code TINKERS_TOOL}) that lets
 * the rest of the engine treat Tinkers items the same as vanilla ones for
 * slot-type checks.
 */
public sealed interface TinkersItem permits TinkersItem.Part, TinkersItem.Tool {

    // -----------------------------------------------------------------------
    // Part
    // -----------------------------------------------------------------------

    /** An unassembled tool part (produced at the Part Builder). */
    final class Part implements TinkersItem {

        public final ToolPartType shape;
        public final BlockType material;

        public Part(ToolPartType shape, BlockType material) {
            if (shape == null)    throw new IllegalArgumentException("shape is null");
            if (material == null) throw new IllegalArgumentException("material is null");
            this.shape    = shape;
            this.material = material;
        }

        /** Material stats from {@link TinkersRegistry}, or {@code null} if unregistered. */
        public TinkersMaterialStats stats() {
            return TinkersRegistry.statsFor(material);
        }

        /** 0xRRGGBB colour for procedural texture tinting, or grey if unregistered. */
        public int color() {
            TinkersMaterialStats s = stats();
            return s != null ? s.color() : 0xAAAAAA;
        }

        @Override
        public String toString() {
            return material.name() + "_" + shape.name();
        }
    }

    // -----------------------------------------------------------------------
    // Tool layer
    // -----------------------------------------------------------------------

    /** One layer of an assembled tool: which part shape and which material. */
    record ToolLayer(ToolPartType shape, BlockType material) {}

    // -----------------------------------------------------------------------
    // Tool
    // -----------------------------------------------------------------------

    /**
     * An assembled modular tool (produced at the Tool Station).
     * Durability is tracked here rather than in {@code ToolDurability} because
     * each individual tool item has independent wear — all IRON_PICKAXE items
     * are fungible, but a Wood-head Tinkers' pick and an Iron-head one are not.
     */
    final class Tool implements TinkersItem {

        public final ToolKind kind;
        /** Ordered: [0] = head, [1] = rod, [2+] = optional extras (binding, etc.). */
        public final List<ToolLayer> layers;
        public final int maxDurability;
        private int remaining;

        public Tool(ToolKind kind, List<ToolLayer> layers) {
            this.kind          = kind;
            this.layers        = List.copyOf(layers);
            this.maxDurability = computeDurability(layers);
            this.remaining     = this.maxDurability;
        }

        /** Restores a persisted tool while clamping its remaining durability. */
        public Tool(ToolKind kind, List<ToolLayer> layers, int remaining) {
            this.kind          = kind;
            this.layers        = List.copyOf(layers);
            this.maxDurability = computeDurability(layers);
            this.remaining     = Math.max(0, Math.min(this.maxDurability, remaining));
        }

        private static int computeDurability(List<ToolLayer> layers) {
            if (layers.isEmpty()) return 1;
            // Head layer governs base durability; rod adds 10 % if present.
            TinkersMaterialStats head = TinkersRegistry.statsFor(layers.get(0).material());
            int base = head != null ? head.baseDurability() : 1;
            float rodMod = 1.0f;
            if (layers.size() > 1) {
                TinkersMaterialStats rod = TinkersRegistry.statsFor(layers.get(1).material());
                if (rod != null) rodMod = 1.0f + rod.baseDurability() / 1000.0f;
            }
            return Math.max(1, (int)(base * rodMod));
        }

        /** The primary (head) material, or {@code null} if no layers. */
        public BlockType headMaterial() {
            return layers.isEmpty() ? null : layers.get(0).material();
        }

        /** Mining speed from the head material, falling back to 1.0 if unknown. */
        public float miningSpeed() {
            TinkersMaterialStats s = headStats();
            return s != null ? s.miningSpeed() : 1.0f;
        }

        /** Mining tier from the head material, falling back to TIER_HAND=0. */
        public int miningTier() {
            TinkersMaterialStats s = headStats();
            return s != null ? s.miningTier() : 0;
        }

        /** 0xRRGGBB colour for texture tinting (from head material). */
        public int color() {
            TinkersMaterialStats s = headStats();
            return s != null ? s.color() : 0xAAAAAA;
        }

        private TinkersMaterialStats headStats() {
            BlockType head = headMaterial();
            return head != null ? TinkersRegistry.statsFor(head) : null;
        }

        /**
         * Consumes one use of durability.
         * @return {@code true} if the tool just broke (remaining durability hit exactly 0).
         *         Returns {@code false} when already broken — callers must not
         *         double-break a tool by calling {@code use()} again after it returns true.
         */
        public boolean use() {
            if (remaining <= 0) return false;
            return --remaining == 0;
        }

        public int remaining()  { return Math.max(0, remaining); }
        public float fraction() { return maxDurability > 0 ? (float)Math.max(0, remaining) / maxDurability : 1f; }

        @Override
        public String toString() {
            return "Tinkers[" + kind + "," + (layers.isEmpty() ? "?" : layers.get(0).material().name()) + "]";
        }
    }
}
