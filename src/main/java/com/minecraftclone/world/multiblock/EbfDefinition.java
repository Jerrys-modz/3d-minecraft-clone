package com.minecraftclone.world.multiblock;

import com.minecraftclone.world.BlockType;
import com.minecraftclone.world.CoilTier;
import com.minecraftclone.world.World;

/**
 * The Electric Blast Furnace multi-block (GTNH layout): a 3×4×3 tower around
 * a hollow 1×2×1 core.
 *
 * <pre>
 *   top layer      9 × heat-proof casing
 *   coil layer 2   8 × heating coil around the core
 *   coil layer 1   8 × heating coil around the core (one may be the controller,
 *                  which must face the core from a side)
 *   bottom layer   9 × heat-proof casing
 * </pre>
 * All coils must be the same tier; it sets the furnace temperature
 * ({@link CoilTier}), stored on the instance under {@link #HEAT_KEY}.
 */
public final class EbfDefinition implements MultiBlockDefinition {

    public static final EbfDefinition INSTANCE = new EbfDefinition();

    /** Instance data key holding the coil temperature (Integer, kelvin). */
    public static final String HEAT_KEY = "heatK";

    private EbfDefinition() {}

    @Override public String id() { return "ebf"; }

    @Override
    public boolean isRelevantBlock(BlockType type) {
        return isShellBlock(type) || isInteriorBlock(type);
    }

    @Override
    public boolean isShellBlock(BlockType type) {
        return type == BlockType.EBF_CONTROLLER || type == BlockType.HEATPROOF_CASING || CoilTier.of(type) != null;
    }

    @Override
    public boolean isControllerBlock(BlockType type) {
        return type == BlockType.EBF_CONTROLLER;
    }

    @Override
    public boolean isInteriorBlock(BlockType type) {
        return type == BlockType.AIR;
    }

    @Override public int minInteriorW() { return 1; }
    @Override public int maxInteriorW() { return 1; }
    @Override public int minInteriorH() { return 2; }
    @Override public int maxInteriorH() { return 2; }
    @Override public int minInteriorD() { return 1; }
    @Override public int maxInteriorD() { return 1; }

    @Override
    public MultiBlockInstance tryForm(World world, int cx, int cy, int cz) {
        MultiBlockInstance inst = PatternValidator.findHollowBox(world, cx, cy, cz, this);
        if (inst == null) return null;
        // Casing layers top and bottom; one coil tier around the core.
        CoilTier tier = null;
        for (int y = inst.minY; y <= inst.maxY; y++) {
            boolean coilLayer = y > inst.minY && y < inst.maxY;
            for (int x = inst.minX; x <= inst.maxX; x++) {
                for (int z = inst.minZ; z <= inst.maxZ; z++) {
                    if (inst.isInterior(x, y, z)) continue;
                    if (x == cx && y == cy && z == cz) continue;      // the controller
                    BlockType b = world.getBlock(x, y, z);
                    if (!coilLayer) {
                        if (b != BlockType.HEATPROOF_CASING) return null;
                        continue;
                    }
                    CoilTier t = CoilTier.of(b);
                    if (t == null) return null;
                    if (tier == null) tier = t;
                    else if (t != tier) return null;                   // mixed coils don't form
                }
            }
        }
        if (cy == inst.minY || cy == inst.maxY || tier == null) return null;
        inst.set(HEAT_KEY, tier.kelvin);
        return inst;
    }

    @Override
    public void onDeform(MultiBlockInstance instance, World world) {
        world.removeBlockEntityAndRemesh(instance.controllerX, instance.controllerY, instance.controllerZ);
    }

    @Override
    public MultiBlockEntity createEntity(MultiBlockInstance instance) {
        return new EbfEntity(instance);
    }
}
