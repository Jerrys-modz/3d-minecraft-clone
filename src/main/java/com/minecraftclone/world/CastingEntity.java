package com.minecraftclone.world;

import com.minecraftclone.player.ItemStack;
import com.minecraftclone.world.tinkers.TinkersItem;
import com.minecraftclone.world.tinkers.TinkersRegistry;
import com.minecraftclone.world.tinkers.ToolPartType;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * A placed Casting Table or Casting Basin: turns molten-metal work from the
 * Smeltery into finished tool parts.
 *
 * <h3>How it works</h3>
 * <ol>
 *   <li><b>Imprint</b>: right-click the table while holding any Tinkers part
 *       (a Part Builder product). The part is consumed and its shape becomes
 *       the table's cast.</li>
 *   <li><b>Feed</b>: right-click holding a registered Tinkers material (any
 *       metal ingot or similar - see {@link TinkersRegistry}). Material goes
 *       into an internal buffer.</li>
 *   <li><b>Cast</b>: while a formed Smeltery controller is within a few
 *       blocks (its heat "powers" the cast), one part is produced every
 *       {@link #CAST_SECONDS} seconds, consuming one material unit. The
 *       Basin variant casts {@link #batchSize()} parts at once.</li>
 *   <li><b>Collect</b>: right-click empty-handed to gather the finished
 *       parts.</li>
 * </ul>
 *
 * <p>State persists with its chunk ({@link #writeTo}/{@link #readFrom},
 * versioned) and syncs to other clients through the standard container
 * packets in multiplayer.
 */
public final class CastingEntity implements BlockEntity {

    /** Stable type names persisted with the entity (one per block variant). */
    public static final String TABLE_TYPE = "casting_table";
    public static final String BASIN_TYPE = "casting_basin";

    /** Real-time seconds to cast one part. */
    public static final float CAST_SECONDS = 10f;

    /** Buffer caps: the Basin is the bulk variant of the pair. */
    public static final int TABLE_INPUT_CAP = 16;
    public static final int TABLE_OUTPUT_CAP = 4;
    public static final int BASIN_INPUT_CAP = 64;
    public static final int BASIN_OUTPUT_CAP = 16;

    private final BlockType block;
    private final boolean basin;

    /** The imprinted cast: which part shape this table produces (null = none yet). */
    private ToolPartType castShape;

    /** Raw material waiting to be cast (one registered material type at a time). */
    private BlockType inputType;
    private int inputCount;

    /** Finished parts ready for collection. */
    private final List<ItemStack> outputs = new ArrayList<>();

    /** Progress toward the next part. */
    private float progress;

    /** Cached "powered" state: a formed smeltery is nearby. */
    private boolean powered;
    private float powerCheckTimer;
    private World world;

    public CastingEntity(BlockType block, boolean basin) {
        this.block = block;
        this.basin = basin;
    }

    @Override
    public String type() {
        return basin ? BASIN_TYPE : TABLE_TYPE;
    }

    @Override
    public BlockType blockType() {
        return block;
    }

    /** Called by the World helper so the entity can scan for a powering smeltery. */
    public void attach(int x, int y, int z, World world) {
        this.posX = x;
        this.posY = y;
        this.posZ = z;
        this.hasPosition = true;
        this.world = world;
    }

    // -----------------------------------------------------------------------
    // Accessors (HUD / messages)
    // -----------------------------------------------------------------------

    /** The imprinted cast shape, or null before a part has been sacrificed. */
    public ToolPartType castShape() {
        return castShape;
    }

    /** Whether the last scan found a powering smeltery. */
    public boolean isPowered() {
        return powered;
    }

    /** Forces the powered state - tests use this instead of building a real smeltery. */
    void setPoweredForTesting(boolean powered) {
        this.powered = powered;
    }

    public int inputCount() {
        return inputCount;
    }

    public BlockType inputType() {
        return inputType;
    }

    /** How many finished parts are waiting. */
    public int outputCount() {
        return outputs.size();
    }

    /** 0..1 progress of the part currently casting. */
    public float progressFraction() {
        return Math.min(1f, Math.max(0f, progress / CAST_SECONDS));
    }

    /** How many parts one production cycle yields at once. */
    public int batchSize() {
        return basin ? 3 : 1;
    }

    public int inputCapacity() {
        return basin ? BASIN_INPUT_CAP : TABLE_INPUT_CAP;
    }

    public int outputCapacity() {
        return basin ? BASIN_OUTPUT_CAP : TABLE_OUTPUT_CAP;
    }

    /** Remaining space for this material, or zero when it cannot currently be inserted. */
    public int inputSpaceFor(BlockType type) {
        if (castShape == null || !TinkersRegistry.isMaterial(type)) return 0;
        if (inputType != null && inputType != type) return 0;
        return Math.max(0, inputCapacity() - inputCount);
    }

    // -----------------------------------------------------------------------
    // Player interaction
    // -----------------------------------------------------------------------

    /**
     * Imprints (or replaces) the cast from a held Tinkers part. Returns true
     * when the cast changed.
     */
    public boolean imprintCast(ItemStack part) {
        if (part.isEmpty() || !part.isTinkersPart()) return false;
        TinkersItem.Part p = part.tinkersPart();
        if (p == null) return false;
        castShape = p.shape;
        return true;
    }

    /**
     * Adds up to {@code count} of a registered material to the input buffer.
     * Returns how many were accepted (0 when the cast isn't set, the type
     * doesn't match what's queued, or it's full).
     */
    public int insertMaterial(BlockType type, int count) {
        if (count <= 0) return 0;
        int take = Math.min(inputSpaceFor(type), count);
        if (take <= 0) return 0;
        inputType = type;
        inputCount += take;
        return take;
    }

    /** A stable view of finished parts for capacity checks before collection. */
    public List<ItemStack> outputsSnapshot() {
        return List.copyOf(outputs);
    }

    /** Takes up to {@code maxCount} finished parts, leaving the rest stored. */
    public List<ItemStack> takeOutputs(int maxCount) {
        int count = Math.min(Math.max(0, maxCount), outputs.size());
        List<ItemStack> taken = new ArrayList<>(outputs.subList(0, count));
        outputs.subList(0, count).clear();
        return taken;
    }

    /** Takes every finished part. Prefer {@link #takeOutputs(int)} when inventory space is limited. */
    public List<ItemStack> takeOutputs() {
        return takeOutputs(outputs.size());
    }

    // -----------------------------------------------------------------------
    // Simulation
    // -----------------------------------------------------------------------

    @Override
    public void tick(float dt) {
        // Re-scan for a powering smeltery a couple times per second only.
        powerCheckTimer -= dt;
        if (powerCheckTimer <= 0f) {
            powerCheckTimer = 0.5f;
            powered = scanForSmeltery();
        }
        if (!powered || castShape == null || inputCount <= 0) return;
        if (outputs.size() >= outputCapacity()) return; // collect first

        progress += dt;
        while (progress >= CAST_SECONDS && inputCount > 0 && outputs.size() < outputCapacity()) {
            progress -= CAST_SECONDS;
            int remainingOutputCapacity = outputCapacity() - outputs.size();
            int made = Math.min(Math.min(batchSize(), inputCount), remainingOutputCapacity);
            for (int i = 0; i < made; i++) {
                inputCount--;
                outputs.add(ItemStack.tinkersPart(new TinkersItem.Part(castShape, inputType)));
            }
        }
        if (inputCount <= 0) {
            inputType = null;
            progress = 0f;
        }
    }

    /** Any FORMED smeltery controller within a small radius of this block? */
    private boolean scanForSmeltery() {
        // Without a world context (tests, or an entity not yet registered)
        // keep whatever the cached state is rather than forcing a re-scan.
        if (world == null || !hasPosition) return powered;
        int r = 4;
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -3; dy <= 3; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    int x = blockX() + dx;
                    int y = clampY(blockY() + dy);
                    int z = blockZ() + dz;
                    if (world.getBlock(x, y, z) != BlockType.SMELTERY_CONTROLLER) continue;
                    BlockEntity e = world.blockEntityAt(x, y, z);
                    if (e instanceof com.minecraftclone.world.multiblock.SmelteryEntity se && se.isActive()) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    // Position cache - set alongside the world reference on registration.
    private int posX, posY, posZ;
    private boolean hasPosition;

    public void attachPosition(int x, int y, int z) {
        posX = x;
        posY = y;
        posZ = z;
        hasPosition = true;
    }

    private int blockX() { return hasPosition ? posX : 0; }
    private int blockY() { return hasPosition ? posY : 0; }
    private int blockZ() { return hasPosition ? posZ : 0; }

    private static int clampY(int y) {
        return Math.max(0, Math.min(Chunk.HEIGHT - 1, y));
    }

    // -----------------------------------------------------------------------
    // Persistence
    // -----------------------------------------------------------------------

    private static final byte SAVE_VERSION = 1;

    @Override
    public void writeTo(DataOutput out) throws IOException {
        out.writeByte(SAVE_VERSION);
        out.writeBoolean(castShape != null);
        if (castShape != null) out.writeUTF(castShape.name());
        out.writeBoolean(inputType != null);
        if (inputType != null) {
            out.writeShort(inputType.id);
            out.writeInt(inputCount);
        }
        out.writeInt(outputs.size());
        for (ItemStack s : outputs) {
            // Cast parts are vanilla-shaped Tinkers items: shape + material.
            TinkersItem.Part p = s.tinkersPart();
            out.writeUTF(p.shape.name());
            out.writeShort(p.material.id);
        }
        out.writeFloat(progress);
    }

    @Override
    public void readFrom(DataInput in) throws IOException {
        try {
            byte version = in.readByte();
            if (version != SAVE_VERSION) {
                clearState();
                return;
            }
            castShape = in.readBoolean() ? ToolPartType.valueOf(in.readUTF()) : null;
            inputType = null;
            inputCount = 0;
            if (in.readBoolean()) {
                int id = in.readUnsignedShort();
                int count = in.readInt();
                BlockType t = BlockType.byId(id);
                if (TinkersRegistry.isMaterial(t) && count > 0) {
                    inputType = t;
                    inputCount = Math.min(count, inputCapacity());
                }
            }
            outputs.clear();
            int n = in.readInt();
            if (n < 0 || n > outputCapacity()) {
                clearState();
                return;
            }
            for (int i = 0; i < n; i++) {
                ToolPartType shape = ToolPartType.valueOf(in.readUTF());
                BlockType mat = BlockType.byId(in.readUnsignedShort());
                if (TinkersRegistry.isMaterial(mat)) {
                    outputs.add(ItemStack.tinkersPart(new TinkersItem.Part(shape, mat)));
                }
            }
            progress = Math.max(0f, in.readFloat());
        } catch (java.io.EOFException | IllegalArgumentException legacy) {
            clearState(); // unknown/legacy payload: start clean instead of crashing
        }
    }

    private void clearState() {
        castShape = null;
        inputType = null;
        inputCount = 0;
        outputs.clear();
        progress = 0f;
    }
}
