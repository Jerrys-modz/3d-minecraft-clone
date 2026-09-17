package com.minecraftclone.world;

import com.minecraftclone.player.Inventory;
import com.minecraftclone.player.Mortar;
import com.minecraftclone.player.StorageContainer;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/**
 * A placed Steam Ore Washer: washes crushed or impure ore into dust using
 * steam power and an adjacent water source. The Steam Age step between the
 * macerator (ore → crushed, 2×) and the furnace (dust → ingot).
 *
 * <h3>How it works</h3>
 * <ul>
 *   <li><b>Input</b> (slot 0): crushed ore or an impure pile (anything
 *       {@link Mortar#isGrindable grindable} that isn't already dust).</li>
 *   <li><b>Heat</b>: draws steam from an adjacent steaming boiler (direct
 *       or through pipes), same as the Steam Macerator.</li>
 *   <li><b>Water</b>: needs a neighbouring water block (source, flow, or
 *       static ocean). Frozen ice does not count, so a washer on a lake
 *       that ices over in winter stops until the thaw.</li>
 *   <li><b>Output</b> (slot 2): 1× dust. Slot 1 (the furnace GUI's fuel
 *       slot) occasionally receives a tiny byproduct dust of a related metal.</li>
 * </ul>
 */
public final class SteamOreWasherEntity implements BlockEntity, StorageContainer, ProgressMachine {

    public static final String TYPE = "steam_ore_washer";

    public static final int SLOT_INPUT = 0;
    /** Occupies the furnace GUI's fuel slot — byproduct dust, not fuel. */
    public static final int SLOT_BYPRODUCT = 1;
    public static final int SLOT_OUTPUT = 2;
    public static final int SLOT_COUNT = 3;

    /** Real-time seconds to wash one item (same pace as a furnace). */
    public static final float WASH_SECONDS = Furnace.SMELT_TIME;

    /** Chance that a wash also yields one byproduct dust. */
    public static final float BYPRODUCT_CHANCE = 0.25f;

    private final BlockType[] types = new BlockType[SLOT_COUNT];
    private final int[] counts = new int[SLOT_COUNT];
    private float progress;

    private World world;
    private int posX, posY, posZ;
    private boolean attached;
    private float boilerCheckTimer;
    /** Cached boiler reference (package-visible for test injection). */
    SteamBoilerEntity boiler;
    private float lastSteamFraction;
    private float drawRate = 1f;
    /** Seeded from position so byproduct rolls are deterministic per cell. */
    private int byproductSalt;

    public SteamOreWasherEntity() {
    }

    @Override
    public String type() { return TYPE; }

    @Override
    public BlockType blockType() { return BlockType.STEAM_ORE_WASHER; }

    @Override
    public int activeLightLevel() { return progress > 0 ? 13 : 0; }

    public void attach(int x, int y, int z, World world) {
        this.posX = x;
        this.posY = y;
        this.posZ = z;
        this.attached = true;
        this.world = world;
        this.byproductSalt = x * 73856093 ^ y * 19349663 ^ z * 83492791;
    }

    public boolean isHot() {
        return lastSteamFraction > 0f;
    }

    @Override
    public float burnFraction() {
        return lastSteamFraction;
    }

    @Override
    public float progressFraction() {
        return Math.min(1f, Math.max(0f, progress / WASH_SECONDS));
    }

    /** True if the input can produce dust into the output slot. */
    public boolean canWash() {
        BlockType input = types[SLOT_INPUT];
        BlockType output = primaryOutput(input);
        if (output == null) return false;
        BlockType held = types[SLOT_OUTPUT];
        return held == null || (held == output && counts[SLOT_OUTPUT] < Inventory.maxStack(output));
    }

    /** Dust produced from a crushed/impure input, or null if it isn't washable. */
    public static BlockType primaryOutput(BlockType input) {
        if (input == null) return null;
        if (!Mortar.isGrindable(input)) return null;
        return Mortar.dustFor(input);
    }

    /**
     * Related-metal byproduct for a dust, or {@code null} when there isn't a
     * sensible pairing. Used only as a bonus slot-2 drop.
     */
    public static BlockType byproductOf(BlockType dust) {
        if (dust == null) return null;
        return switch (dust) {
            case COPPER_DUST -> BlockType.TIN_DUST;
            case TIN_DUST -> BlockType.COPPER_DUST;
            case ZINC_DUST -> BlockType.LEAD_DUST;
            case LEAD_DUST -> BlockType.SILVER_DUST;
            case SILVER_DUST -> BlockType.LEAD_DUST;
            case IRON_DUST -> BlockType.NICKEL_DUST;
            case NICKEL_DUST -> BlockType.IRON_DUST;
            case GOLD_DUST -> BlockType.SILVER_DUST;
            case COBALT_DUST -> BlockType.NICKEL_DUST;
            default -> null;
        };
    }

    /** Adjacent water that hasn't frozen over. Package-visible for tests. */
    boolean hasWater() {
        if (!attached || world == null) return false;
        for (int[] d : FACES) {
            BlockType n = world.getBlock(posX + d[0], posY + d[1], posZ + d[2]);
            if (n != null && n.isWater()) return true;
        }
        return false;
    }

    /** Override for unit tests that don't want to stand up a World. */
    Boolean waterOverride;

    @Override
    public boolean isActive() {
        return progress > 0f && lastSteamFraction > 0f;
    }

    @Override
    public void tick(float dt) {
        if (dt <= 0) return;
        boilerCheckTimer -= dt;
        if (boilerCheckTimer <= 0f) {
            boilerCheckTimer = 0.5f;
            if (attached && world != null) {
                boiler = findSteamSource();
                com.minecraftclone.world.pipes.PipeNetwork net =
                        world.pipeNetworks().networkAt(com.minecraftclone.world.pipes.PipeType.STEAM, posX, posY, posZ);
                drawRate = net != null && net.minTier != null ? net.minTier.throughput : 1f;
            }
        }
        boolean watered = waterOverride != null ? waterOverride : hasWater();
        boolean hot = boiler != null && boiler.steamSeconds() > 0f && canWash() && watered;
        if (!hot) {
            lastSteamFraction = 0f;
            return;
        }

        float draw = Math.min(dt * drawRate, boiler.steamSeconds());
        if (draw <= 0f) return;
        boiler.drainSteam(draw);
        lastSteamFraction = Math.min(1f, boiler.steamSeconds() / SteamBoilerEntity.MAX_STEAM_SECONDS);

        progress += draw;
        while (progress >= WASH_SECONDS && canWash()) {
            progress -= WASH_SECONDS;
            BlockType result = primaryOutput(types[SLOT_INPUT]);
            counts[SLOT_INPUT]--;
            if (counts[SLOT_INPUT] == 0) types[SLOT_INPUT] = null;
            if (types[SLOT_OUTPUT] == result) {
                counts[SLOT_OUTPUT]++;
            } else {
                types[SLOT_OUTPUT] = result;
                counts[SLOT_OUTPUT] = 1;
            }
            maybeEmitByproduct(result);
        }
    }

    private void maybeEmitByproduct(BlockType primary) {
        BlockType by = byproductOf(primary);
        if (by == null) return;
        // Deterministic 25% from a cheap mix of position + items washed.
        int washed = counts[SLOT_OUTPUT] + counts[SLOT_BYPRODUCT] + byproductSalt;
        if ((Integer.rotateLeft(washed * 0x9E3779B9, 13) & 0xFF) >= (int) (BYPRODUCT_CHANCE * 256)) {
            return;
        }
        if (types[SLOT_BYPRODUCT] == null) {
            types[SLOT_BYPRODUCT] = by;
            counts[SLOT_BYPRODUCT] = 1;
        } else if (types[SLOT_BYPRODUCT] == by
                && counts[SLOT_BYPRODUCT] < Inventory.maxStack(by)) {
            counts[SLOT_BYPRODUCT]++;
        }
    }

    private static final int[][] FACES = {
            {1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1},
    };

    private SteamBoilerEntity findSteamSource() {
        if (!attached || world == null) return null;
        for (int[] d : FACES) {
            BlockEntity e = world.blockEntityAt(posX + d[0], posY + d[1], posZ + d[2]);
            if (e instanceof SteamBoilerEntity b) return b;
        }
        com.minecraftclone.world.pipes.PipeNetwork net =
                world.pipeNetworks().networkAt(com.minecraftclone.world.pipes.PipeType.STEAM, posX, posY, posZ);
        if (net == null) return null;
        for (long key : net.cells) {
            int cx = (int) (key & 0x1FFFFFL) << 21 >> 21;
            int cy = (int) ((key >> 21) & 0x1FFFFFL) << 21 >> 21;
            int cz = (int) ((key >> 42) & 0x1FFFFFL) << 21 >> 21;
            for (int[] d : FACES) {
                BlockEntity e = world.blockEntityAt(cx + d[0], cy + d[1], cz + d[2]);
                if (e instanceof SteamBoilerEntity b) return b;
            }
        }
        return null;
    }

    @Override
    public int size() {
        return SLOT_COUNT;
    }

    @Override
    public BlockType typeOf(int slot) {
        if (slot < 0 || slot >= SLOT_COUNT) return null;
        return types[slot];
    }

    @Override
    public int countOf(int slot) {
        if (slot < 0 || slot >= SLOT_COUNT) return 0;
        return counts[slot];
    }

    @Override
    public void setSlot(int slot, BlockType type, int count) {
        if (slot < 0 || slot >= SLOT_COUNT) return;
        if (type == null || count <= 0) {
            types[slot] = null;
            counts[slot] = 0;
        } else {
            types[slot] = type;
            counts[slot] = count;
        }
    }

    @Override
    public int add(BlockType type, int amount) {
        if (primaryOutput(type) == null || amount <= 0) return amount;
        if (types[SLOT_INPUT] != null && types[SLOT_INPUT] != type) return amount;
        int space = Inventory.maxStack(type) - counts[SLOT_INPUT];
        int take = Math.min(space, amount);
        if (take <= 0) return amount;
        types[SLOT_INPUT] = type;
        counts[SLOT_INPUT] += take;
        return amount - take;
    }

    @Override
    public int getCount(BlockType type) {
        if (type == null) return 0;
        int total = 0;
        for (int i = 0; i < SLOT_COUNT; i++) {
            if (types[i] == type) total += counts[i];
        }
        return total;
    }

    private static final byte PAYLOAD_VERSION = 1;

    @Override
    public void writeTo(DataOutput out) throws IOException {
        out.writeByte(PAYLOAD_VERSION);
        for (int i = 0; i < SLOT_COUNT; i++) {
            out.writeShort(types[i] == null ? 0 : types[i].id);
            out.writeInt(counts[i]);
        }
        out.writeFloat(progress);
    }

    @Override
    public void readFrom(DataInput in) throws IOException {
        byte version = in.readByte();
        if (version != PAYLOAD_VERSION) {
            clearState();
            return;
        }
        for (int i = 0; i < SLOT_COUNT; i++) {
            int id = in.readUnsignedShort();
            int count = in.readInt();
            types[i] = id == 0 || count <= 0 ? null : BlockType.byId(id);
            counts[i] = types[i] == null ? 0 : count;
        }
        progress = Math.max(0f, in.readFloat());
    }

    private void clearState() {
        for (int i = 0; i < SLOT_COUNT; i++) {
            types[i] = null;
            counts[i] = 0;
        }
        progress = 0f;
    }
}
