package com.minecraftclone.world.multiblock;

import com.minecraftclone.world.BlockType;
import com.minecraftclone.world.World;

/**
 * Defines the Tinkers' Construct Smeltery multi-block structure.
 *
 * <h3>Structure rules (vanilla Tinkers' Construct 1.7.10 variant)</h3>
 * <ul>
 *   <li><b>Shell blocks</b> (walls, floor, ceiling): {@link BlockType#SEARED_BRICK},
 *       {@link BlockType#SEARED_GLASS}, {@link BlockType#SEARED_TANK},
 *       {@link BlockType#SMELTERY_DRAIN}, or {@link BlockType#SMELTERY_CONTROLLER}.</li>
 *   <li><b>Controller</b>: exactly one {@link BlockType#SMELTERY_CONTROLLER} on any
 *       wall face (not floor or ceiling — but technically the validator doesn't
 *       enforce this restriction; any shell position is accepted).</li>
 *   <li><b>Interior</b>: air or any non-lava fluid.  Lava placed inside the
 *       smeltery does not count as a valid interior block.</li>
 *   <li><b>Size</b>: interior from 1×1×1 to 7×7×7 (giving exterior 3×3×3 to
 *       9×9×9).  Practical smelteries are typically 3×1×3 interior
 *       (5×3×5 exterior).</li>
 * </ul>
 *
 * <p>When formed, the controller block is replaced with
 * {@link BlockType#SMELTERY_CONTROLLER} (its same type — the block entity's
 * {@code isActive()} drives the visual switch to the lit front tile) and a
 * {@link SmelteryEntity} is registered at the controller position.
 *
 * <p>When deformed (any shell or interior block changes), the block entity is
 * removed automatically by {@link MultiBlockManager} — the controller reverts
 * to its unlit tile because no active entity remains.
 */
public final class SmelteryDefinition implements MultiBlockDefinition {

    /** Singleton — registered once at game startup. */
    public static final SmelteryDefinition INSTANCE = new SmelteryDefinition();

    @Override
    public String id() { return "smeltery"; }

    // -----------------------------------------------------------------------
    // Shell / interior predicates
    // -----------------------------------------------------------------------

    @Override
    public boolean isRelevantBlock(BlockType type) {
        // A seared block change closes the shell; an interior change (mining the
        // inside out, removing lava) completes the hollow. Both must trigger a rescan.
        return isShellBlock(type) || isInteriorBlock(type);
    }

    @Override
    public boolean isShellBlock(BlockType type) {
        if (type == null) return false;
        return type == BlockType.SEARED_BRICK
            || type == BlockType.SEARED_GLASS
            || type == BlockType.SEARED_TANK
            || type == BlockType.SMELTERY_DRAIN
            || type == BlockType.SMELTERY_CONTROLLER;
    }

    @Override
    public boolean isControllerBlock(BlockType type) {
        return type == BlockType.SMELTERY_CONTROLLER;
    }

    @Override
    public boolean isInteriorBlock(BlockType type) {
        if (type == null) return false;
        if (type == BlockType.AIR) return true;
        // Water and other non-lava fluids are valid inside a smeltery
        if (type.isFluid() && type != BlockType.LAVA && type != BlockType.LAVA_SOURCE && type != BlockType.LAVA_FLOW)
            return true;
        return false;
    }

    // -----------------------------------------------------------------------
    // Dimensions
    // -----------------------------------------------------------------------

    @Override public int minInteriorW() { return 1; }
    @Override public int maxInteriorW() { return 7; }
    @Override public int minInteriorH() { return 1; }
    @Override public int maxInteriorH() { return 7; }
    @Override public int minInteriorD() { return 1; }
    @Override public int maxInteriorD() { return 7; }

    // -----------------------------------------------------------------------
    // Lifecycle
    // -----------------------------------------------------------------------

    @Override
    public void onForm(MultiBlockInstance instance, World world) {
        // The controller block doesn't change its ID — the block entity's
        // isActive() drives the litFrontTile visually.  Nothing else to do here.
    }

    @Override
    public void onDeform(MultiBlockInstance instance, World world) {
        // Remove the block entity and dirty the surrounding chunks so the
        // front-face reverts from the lit tile to the unlit tile immediately.
        // registerMultiBlockEntity dirtied chunks on form; we mirror that here.
        world.removeBlockEntityAndRemesh(
                instance.controllerX, instance.controllerY, instance.controllerZ);
    }

    @Override
    public MultiBlockEntity createEntity(MultiBlockInstance instance) {
        return new SmelteryEntity(instance);
    }

    private SmelteryDefinition() {}
}
