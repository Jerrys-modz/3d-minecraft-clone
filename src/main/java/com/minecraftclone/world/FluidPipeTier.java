package com.minecraftclone.world;

import java.util.EnumSet;
import java.util.Set;

/**
 * Fluid pipe materials. Each tier carries a set of fluids: bronze carries the
 * cold ones (water, oil, fuels) but softens in lava's heat, steel adds lava,
 * and only stainless steel resists sulfuric acid. A connected
 * pipe network carries only what its <em>weakest</em> pipe can (the same
 * weakest-link rule as cables and steam pipes), so one bronze segment in a
 * steel lava line stops the lava.
 */
public enum FluidPipeTier {

    /** Cold fluids: water, crude oil and the fuels — lava is too hot and acid corrodes it. */
    BRONZE(BlockType.FLUID_PIPE, EnumSet.of(Fluid.WATER, Fluid.OIL, Fluid.DIESEL, Fluid.HEAVY_FUEL)),

    /** Cold fluids and lava. */
    STEEL(BlockType.FLUID_PIPE_STEEL, EnumSet.of(Fluid.WATER, Fluid.OIL, Fluid.DIESEL, Fluid.HEAVY_FUEL, Fluid.LAVA)),

    /** Everything, including sulfuric acid. */
    STAINLESS(BlockType.FLUID_PIPE_STAINLESS, EnumSet.allOf(Fluid.class));

    public final BlockType block;
    private final Set<Fluid> carries;

    FluidPipeTier(BlockType block, Set<Fluid> carries) {
        this.block = block;
        this.carries = carries;
    }

    /** True if this pipe material can carry {@code fluid}. */
    public boolean carries(Fluid fluid) {
        return fluid != null && carries.contains(fluid);
    }

    /** The tier of a pipe block, or null if it isn't a fluid pipe. */
    public static FluidPipeTier of(BlockType block) {
        for (FluidPipeTier t : values()) {
            if (t.block == block) return t;
        }
        return null;
    }

    /** True if {@code block} is a fluid pipe of any tier. */
    public static boolean isFluidPipe(BlockType block) {
        return of(block) != null;
    }

    /** The cheapest tier that carries {@code fluid}. */
    public static FluidPipeTier minimumFor(Fluid fluid) {
        for (FluidPipeTier t : values()) {
            if (t.carries(fluid)) return t;
        }
        return STAINLESS;
    }
}
