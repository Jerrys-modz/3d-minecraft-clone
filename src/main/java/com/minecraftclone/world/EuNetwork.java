package com.minecraftclone.world;

import com.minecraftclone.world.pipes.PipeNetwork;
import com.minecraftclone.world.pipes.PipeType;

/**
 * Shared EU-network lookups for electric consumers (Electric Furnace,
 * Electric Macerator, ...). Keeps the "find a power supply" and
 * "weakest-link cable throughput" rules in one place so every LV machine
 * behaves the same way.
 */
public final class EuNetwork {

    private EuNetwork() {
    }

    private static final int[][] FACES = {
            {1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1},
    };

    /**
     * Finds an EU source for the machine at {@code (x, y, z)}: a charged
     * generator wins, then a charged battery, then any empty generator or
     * battery (so the machine resumes the moment it charges). Scans direct
     * neighbours first, then the perimeter of the connected cable network.
     * Returns null when nothing is wired to the machine.
     */
    public static EuSource findSource(World world, int x, int y, int z) {
        if (world == null) return null;
        Candidates c = new Candidates();
        for (int[] d : FACES) {
            if (c.offer(world.blockEntityAt(x + d[0], y + d[1], z + d[2]))) return c.chargedGenerator;
        }
        PipeNetwork net = world.pipeNetworks().networkAt(PipeType.ENERGY, x, y, z);
        if (net != null) {
            for (long key : net.cells) {
                int cx = unpackX(key), cy = unpackY(key), cz = unpackZ(key);
                for (int[] d : FACES) {
                    if (c.offer(world.blockEntityAt(cx + d[0], cy + d[1], cz + d[2]))) return c.chargedGenerator;
                }
            }
        }
        if (c.chargedBattery != null) return c.chargedBattery;
        return c.emptyGenerator != null ? c.emptyGenerator : c.emptyBattery;
    }

    /**
     * EU throughput multiplier of the cable network touching {@code (x, y, z)}:
     * the throughput of its weakest cable tier, or 1 when the machine is fed
     * by direct adjacency (no cables).
     */
    public static float drawRate(World world, int x, int y, int z) {
        if (world == null) return 1f;
        PipeNetwork net = world.pipeNetworks().networkAt(PipeType.ENERGY, x, y, z);
        if (net == null) return 1f;
        CableTier weakest = null;
        for (long key : net.cells) {
            CableTier t = CableTier.of(world.getBlock(unpackX(key), unpackY(key), unpackZ(key)));
            if (t != null && (weakest == null || t.throughput < weakest.throughput)) weakest = t;
        }
        return weakest != null ? weakest.throughput : 1f;
    }

    /** Buffer capacity of a source, used to normalise the GUI charge gauge. */
    public static float capacityOf(EuSource source) {
        if (source instanceof CoalGeneratorEntity) return CoalGeneratorEntity.MAX_EU;
        if (source instanceof BatteryBlockEntity) return BatteryBlockEntity.MAX_EU;
        return 1f;
    }

    static int unpackX(long key) { return (int) (key & 0x1FFFFFL) << 11 >> 11; }
    static int unpackY(long key) { return (int) ((key >> 21) & 0x1FFFFFL) << 11 >> 11; }
    static int unpackZ(long key) { return (int) ((key >> 42) & 0x1FFFFFL) << 11 >> 11; }

    /** Collects source candidates in priority order while scanning. */
    private static final class Candidates {
        CoalGeneratorEntity chargedGenerator;
        CoalGeneratorEntity emptyGenerator;
        BatteryBlockEntity chargedBattery;
        BatteryBlockEntity emptyBattery;

        /** Records {@code e}; returns true once a charged generator is found (best possible). */
        boolean offer(BlockEntity e) {
            if (e instanceof CoalGeneratorEntity g) {
                if (g.euStored() > 0f) {
                    chargedGenerator = g;
                    return true;
                }
                if (emptyGenerator == null) emptyGenerator = g;
            } else if (e instanceof BatteryBlockEntity b) {
                if (b.euStored() > 0f) {
                    if (chargedBattery == null) chargedBattery = b;
                } else if (emptyBattery == null) {
                    emptyBattery = b;
                }
            }
            return false;
        }
    }
}
