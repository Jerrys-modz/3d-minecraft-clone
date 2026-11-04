package com.minecraftclone.world;

import com.minecraftclone.world.pipes.PipeNetwork;
import com.minecraftclone.world.pipes.PipeType;

import java.util.HashMap;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * Shared EU-network rules for electric consumers (Electric Furnace, the LV
 * processors, Battery Blocks charging): finding a power supply, the cable
 * throughput along the way, distance loss, and voltage.
 *
 * <h3>Voltage</h3>
 * Tin and copper cable are LV, gold is <b>MV</b>, aluminium is <b>HV</b>.
 * Cables of any tier join into one network, which runs at the highest
 * voltage of any cable in it. Transformers are not cables: they split
 * networks and step what passes through them down — the
 * {@link BlockType#LV_TRANSFORMER} to LV, the {@link BlockType#MV_TRANSFORMER}
 * to MV. A machine receives the output of the transformer nearest it on the
 * path, or its own network's voltage when there is none (direct adjacency is
 * LV). {@link Feed#voltage} is the highest voltage any charged source pushes
 * at the machine; a machine rated below it is overvolted. Power reached
 * through a transformer keeps the throughput of the cables on the path.
 *
 * <h3>Distance loss</h3>
 * Every cable block on the cheapest path to the source loses a fraction of
 * the energy ({@link CableTier#lossPerBlock}): tin loses the most, gold the
 * least. Machines are charged the full EU they draw but only make progress
 * with the {@link Feed#efficiency} share that arrives — so long LV runs are
 * wasteful, and the MV-gold-plus-transformer line is the efficient way to
 * move power a long way.
 */
public final class EuNetwork {

    private EuNetwork() {
    }

    /** Lowest efficiency any path can fall to — power always arrives, just badly. */
    public static final float MIN_EFFICIENCY = 0.1f;

    /** Search bound so a sprawling cable web can't stall a tick. */
    private static final int MAX_NODES = 4096;

    private static final int[][] FACES = {
            {1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1},
    };

    /**
     * How a consumer is fed.
     *
     * @param source      the chosen supply, or null when nothing is wired up
     * @param efficiency  share (0..1] of drawn EU that survives the cable run
     * @param throughput  cable throughput multiplier (weakest cable on the path; 1 when direct)
     * @param voltage     the highest voltage any charged source delivers to the machine
     * @param cableBlocks number of cable blocks on the chosen path
     */
    public record Feed(EuSource source, float efficiency, float throughput, CableTier.Voltage voltage,
                       int cableBlocks) {
        public static final Feed NONE = new Feed(null, 1f, 1f, CableTier.Voltage.LV, 0);

        /** True when the delivered voltage would overvolt an LV machine. */
        public boolean overvoltage() {
            return voltage.exceeds(CableTier.Voltage.LV);
        }
    }

    /** A search frontier node: a cable or transformer cell. */
    /** {@code stepped} is the output voltage of the transformer nearest the machine, or null if none yet. */
    private record Node(int x, int y, int z, float loss, float throughput, CableTier.Voltage stepped, int cables)
            implements Comparable<Node> {
        @Override public int compareTo(Node o) { return Float.compare(loss, o.loss); }
    }

    /** A reachable source and the path that reaches it. */
    private record Candidate(EuSource source, float loss, float throughput, int cables) { }

    /** Finds the supply for the machine at {@code (x, y, z)} (see the class doc for the rules). */
    public static Feed resolve(World world, int x, int y, int z) {
        return resolve(world, x, y, z, false);
    }

    /**
     * Finds the supply for the consumer at {@code (x, y, z)}. With
     * {@code generatorsOnly}, only Coal Generators count (Battery Blocks
     * charging themselves). Priority: charged generator, charged battery,
     * empty generator, empty battery; ties go to the lowest-loss path.
     */
    public static Feed resolve(World world, int x, int y, int z, boolean generatorsOnly) {
        if (world == null) return Feed.NONE;
        long self = key(x, y, z);
        Map<Long, Float> best = new HashMap<>();
        PriorityQueue<Node> queue = new PriorityQueue<>();
        Candidate[] picks = new Candidate[4];
        CableTier.Voltage ownVoltage = networkVoltage(world, x, y, z);
        CableTier.Voltage[] delivered = {null};

        for (int[] d : FACES) {
            int nx = x + d[0], ny = y + d[1], nz = z + d[2];
            offerSource(world.blockEntityAt(nx, ny, nz), 0f, 1f, CableTier.Voltage.LV, 0, generatorsOnly, picks,
                    delivered);
            enqueue(world, queue, best, nx, ny, nz, 0f, Float.MAX_VALUE, null, 0);
        }
        int expanded = 0;
        while (!queue.isEmpty() && expanded++ < MAX_NODES) {
            Node n = queue.poll();
            Float known = best.get(key(n.x, n.y, n.z));
            if (known != null && known < n.loss - 1e-6f) continue;   // stale entry
            CableTier.Voltage arriving = n.stepped != null ? n.stepped : ownVoltage;
            for (int[] d : FACES) {
                int nx = n.x + d[0], ny = n.y + d[1], nz = n.z + d[2];
                if (key(nx, ny, nz) == self) continue;
                offerSource(world.blockEntityAt(nx, ny, nz), n.loss, n.throughput, arriving, n.cables,
                        generatorsOnly, picks, delivered);
                enqueue(world, queue, best, nx, ny, nz, n.loss, n.throughput, n.stepped, n.cables);
            }
        }

        Candidate pick = null;
        for (Candidate c : picks) {
            if (c != null) { pick = c; break; }
        }
        if (pick == null) return Feed.NONE;
        float throughput = pick.throughput == Float.MAX_VALUE ? 1f : pick.throughput;
        float efficiency = Math.max(MIN_EFFICIENCY, 1f - pick.loss);
        CableTier.Voltage voltage = delivered[0] != null ? delivered[0] : CableTier.Voltage.LV;
        return new Feed(pick.source, efficiency, throughput, voltage, pick.cables);
    }

    private static void enqueue(World world, PriorityQueue<Node> queue, Map<Long, Float> best,
                                int x, int y, int z, float loss, float throughput, CableTier.Voltage stepped,
                                int cables) {
        BlockType b = world.getBlock(x, y, z);
        CableTier tier = CableTier.of(b);
        CableTier.Voltage transformerOut = transformerOutput(b);
        if (tier == null && transformerOut == null) return;
        float newLoss = tier != null ? loss + tier.lossPerBlock : loss;
        float newThroughput = tier != null ? Math.min(throughput, tier.throughput) : throughput;
        long k = key(x, y, z);
        Float known = best.get(k);
        if (known != null && known <= newLoss) return;
        best.put(k, newLoss);
        CableTier.Voltage newStepped = stepped != null ? stepped : transformerOut;   // nearest transformer wins
        queue.add(new Node(x, y, z, newLoss, newThroughput, newStepped, cables + (tier != null ? 1 : 0)));
    }

    /** The voltage a transformer block outputs, or null if {@code b} isn't one. */
    public static CableTier.Voltage transformerOutput(BlockType b) {
        if (b == BlockType.LV_TRANSFORMER) return CableTier.Voltage.LV;
        if (b == BlockType.MV_TRANSFORMER) return CableTier.Voltage.MV;
        return null;
    }

    /**
     * Ranks {@code e} into {@code picks}: [charged gen, charged battery, empty gen, empty battery],
     * and raises {@code delivered[0]} to the voltage a charged source pushes at the machine.
     */
    private static void offerSource(BlockEntity e, float loss, float throughput, CableTier.Voltage voltage,
                                    int cables, boolean generatorsOnly, Candidate[] picks,
                                    CableTier.Voltage[] delivered) {
        int slot;
        if (e instanceof CoalGeneratorEntity g) {
            slot = g.euStored() > 0f ? 0 : 2;
        } else if (e instanceof BatteryBlockEntity b && !generatorsOnly) {
            slot = b.euStored() > 0f ? 1 : 3;
        } else {
            return;
        }
        if (slot <= 1 && (delivered[0] == null || voltage.exceeds(delivered[0]))) delivered[0] = voltage;
        Candidate c = new Candidate((EuSource) e, loss, throughput, cables);
        if (picks[slot] == null || loss < picks[slot].loss) picks[slot] = c;
    }

    /** The voltage of the cable network(s) touching {@code (x, y, z)}: their highest cable tier (LV if none). */
    public static CableTier.Voltage networkVoltage(World world, int x, int y, int z) {
        PipeNetwork net = world.pipeNetworks().networkAt(PipeType.ENERGY, x, y, z);
        CableTier.Voltage v = CableTier.Voltage.LV;
        if (net == null) return v;
        for (long k : net.cells) {
            CableTier t = CableTier.of(world.getBlock(unpackX(k), unpackY(k), unpackZ(k)));
            if (t != null && t.voltage.exceeds(v)) v = t.voltage;
        }
        return v;
    }

    /** True when the cable network(s) touching {@code (x, y, z)} run above LV. */
    public static boolean isMv(World world, int x, int y, int z) {
        return networkVoltage(world, x, y, z).exceeds(CableTier.Voltage.LV);
    }

    // ------------------------------------------------------------------
    // Convenience views kept for existing callers
    // ------------------------------------------------------------------

    /** The chosen source for the machine at {@code (x, y, z)}, or null. */
    public static EuSource findSource(World world, int x, int y, int z) {
        return resolve(world, x, y, z).source();
    }

    /** Throughput multiplier of the cable path feeding {@code (x, y, z)} (1 when direct or unfed). */
    public static float drawRate(World world, int x, int y, int z) {
        return resolve(world, x, y, z).throughput();
    }

    /** Buffer capacity of a source, used to normalise the GUI charge gauge. */
    public static float capacityOf(EuSource source) {
        if (source instanceof CoalGeneratorEntity) return CoalGeneratorEntity.MAX_EU;
        if (source instanceof BatteryBlockEntity b) return b.capacity();
        return 1f;
    }

    private static long key(int x, int y, int z) {
        return ((long) x & 0x1FFFFFL) | (((long) y & 0x1FFFFFL) << 21) | (((long) z & 0x1FFFFFL) << 42);
    }

    static int unpackX(long key) { return (int) (key & 0x1FFFFFL) << 11 >> 11; }
    static int unpackY(long key) { return (int) ((key >> 21) & 0x1FFFFFL) << 11 >> 11; }
    static int unpackZ(long key) { return (int) ((key >> 42) & 0x1FFFFFL) << 11 >> 11; }
}
