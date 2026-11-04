package com.minecraftclone.world;

import com.minecraftclone.world.pipes.PipeNetwork;
import com.minecraftclone.world.pipes.PipeType;

/**
 * Finds fluid tanks a machine can draw from or fill: face-adjacent tanks
 * first, then tanks on the perimeter of the connected fluid-pipe network —
 * but only if every pipe in that network can carry the fluid
 * (see {@link FluidPipeTier}).
 */
public final class FluidNetwork {

    private FluidNetwork() {
    }

    private static final int[][] FACES = {
            {1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1},
    };

    /** A tank reachable from {@code (x, y, z)} holding at least {@code mB} of {@code fluid}, or null. */
    public static FluidTankEntity findTankWith(World world, int x, int y, int z, Fluid fluid, int mB) {
        return find(world, x, y, z, fluid, t -> t.has(fluid, mB));
    }

    /** A tank reachable from {@code (x, y, z)} with room for {@code mB} of {@code fluid}, or null. */
    public static FluidTankEntity findTankFor(World world, int x, int y, int z, Fluid fluid, int mB) {
        return find(world, x, y, z, fluid, t -> t.canFill(fluid, mB));
    }

    /**
     * Like {@link #findTankFor} but never returns {@code exclude} — for machines
     * with two outputs, so two products don't both pick the same empty tank.
     */
    public static FluidTankEntity findTankFor(World world, int x, int y, int z, Fluid fluid, int mB,
                                              FluidTankEntity... exclude) {
        return find(world, x, y, z, fluid, t -> !java.util.Arrays.asList(exclude).contains(t) && t.canFill(fluid, mB));
    }

    private static FluidTankEntity find(World world, int x, int y, int z, Fluid fluid,
                                        java.util.function.Predicate<FluidTankEntity> ok) {
        if (world == null) return null;
        for (int[] d : FACES) {
            if (world.blockEntityAt(x + d[0], y + d[1], z + d[2]) instanceof FluidTankEntity t && ok.test(t)) {
                return t;
            }
        }
        PipeNetwork net = world.pipeNetworks().networkAt(PipeType.FLUID, x, y, z);
        if (net == null || !carries(world, net, fluid)) return null;
        for (long key : net.cells) {
            int cx = EuNetwork.unpackX(key), cy = EuNetwork.unpackY(key), cz = EuNetwork.unpackZ(key);
            for (int[] d : FACES) {
                if (world.blockEntityAt(cx + d[0], cy + d[1], cz + d[2]) instanceof FluidTankEntity t && ok.test(t)) {
                    return t;
                }
            }
        }
        return null;
    }

    /** True if every pipe in {@code net} can carry {@code fluid} (weakest-link). */
    public static boolean carries(World world, PipeNetwork net, Fluid fluid) {
        for (long key : net.cells) {
            FluidPipeTier t = FluidPipeTier.of(world.getBlock(
                    EuNetwork.unpackX(key), EuNetwork.unpackY(key), EuNetwork.unpackZ(key)));
            if (t != null && !t.carries(fluid)) return false;
        }
        return true;
    }
}
