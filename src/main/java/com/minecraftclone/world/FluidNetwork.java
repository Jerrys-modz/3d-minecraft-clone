package com.minecraftclone.world;

import com.minecraftclone.world.pipes.PipeNetwork;
import com.minecraftclone.world.pipes.PipeType;

/**
 * Finds fluid tanks a machine can draw from: face-adjacent tanks first, then
 * tanks on the perimeter of the connected fluid-pipe network.
 */
public final class FluidNetwork {

    private FluidNetwork() {
    }

    private static final int[][] FACES = {
            {1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1},
    };

    /** A tank reachable from {@code (x, y, z)} holding at least {@code mB} of {@code fluid}, or null. */
    public static FluidTankEntity findTankWith(World world, int x, int y, int z, Fluid fluid, int mB) {
        return find(world, x, y, z, t -> t.has(fluid, mB));
    }

    /** A tank reachable from {@code (x, y, z)} with room for {@code mB} of {@code fluid}, or null. */
    public static FluidTankEntity findTankFor(World world, int x, int y, int z, Fluid fluid, int mB) {
        return find(world, x, y, z, t -> t.canFill(fluid, mB));
    }

    private static FluidTankEntity find(World world, int x, int y, int z,
                                        java.util.function.Predicate<FluidTankEntity> ok) {
        if (world == null) return null;
        for (int[] d : FACES) {
            if (world.blockEntityAt(x + d[0], y + d[1], z + d[2]) instanceof FluidTankEntity t && ok.test(t)) {
                return t;
            }
        }
        PipeNetwork net = world.pipeNetworks().networkAt(PipeType.FLUID, x, y, z);
        if (net == null) return null;
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
}
