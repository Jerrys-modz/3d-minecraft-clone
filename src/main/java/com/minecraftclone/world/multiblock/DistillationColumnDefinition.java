package com.minecraftclone.world.multiblock;

import com.minecraftclone.world.BlockType;
import com.minecraftclone.world.World;

/**
 * The multi-block Distillation Column: a 3×3 column of distillation casing
 * around a hollow 1×1 core, 1 to {@value #MAX_STOREYS} storeys of core tall
 * (3 to 10 blocks overall), with the controller in a side wall facing the
 * core. Each storey runs one more batch in parallel. Stored on the instance
 * under {@link #STOREYS_KEY}.
 */
public final class DistillationColumnDefinition implements MultiBlockDefinition {

    public static final DistillationColumnDefinition INSTANCE = new DistillationColumnDefinition();

    public static final int MAX_STOREYS = 8;
    public static final String STOREYS_KEY = "storeys";

    private DistillationColumnDefinition() {}

    @Override public String id() { return "distillation_column"; }

    @Override
    public boolean isRelevantBlock(BlockType type) {
        return isShellBlock(type) || isInteriorBlock(type);
    }

    @Override
    public boolean isShellBlock(BlockType type) {
        return type == BlockType.DISTILLATION_CASING || type == BlockType.DISTILLATION_CONTROLLER;
    }

    @Override
    public boolean isControllerBlock(BlockType type) {
        return type == BlockType.DISTILLATION_CONTROLLER;
    }

    @Override
    public boolean isInteriorBlock(BlockType type) {
        return type == BlockType.AIR;
    }

    @Override public int minInteriorW() { return 1; }
    @Override public int maxInteriorW() { return 1; }
    @Override public int minInteriorH() { return 1; }
    @Override public int maxInteriorH() { return MAX_STOREYS; }
    @Override public int minInteriorD() { return 1; }
    @Override public int maxInteriorD() { return 1; }

    @Override
    public MultiBlockInstance tryForm(World world, int cx, int cy, int cz) {
        MultiBlockInstance inst = PatternValidator.findHollowBox(world, cx, cy, cz, this);
        if (inst == null) return null;
        if (cy == inst.minY || cy == inst.maxY) return null;      // controller must face the core from a side
        inst.set(STOREYS_KEY, inst.maxY - inst.minY - 1);
        return inst;
    }

    @Override
    public void onDeform(MultiBlockInstance instance, World world) {
        world.removeBlockEntityAndRemesh(instance.controllerX, instance.controllerY, instance.controllerZ);
    }

    @Override
    public MultiBlockEntity createEntity(MultiBlockInstance instance) {
        return new DistillationColumnEntity(instance);
    }
}
