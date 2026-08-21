package com.minecraftclone.world;

/**
 * Functional bed helpers. A bed is a 1x2 horizontal surface: the foot end
 * ({@link BlockType#BED} or {@link BlockType#BED_OCCUPIED}) and the head/pillow
 * end ({@link BlockType#BED_HEAD} or {@link BlockType#BED_HEAD_OCCUPIED}).
 * The read and write sides are passed in separately so the logic is testable
 * without a full {@link World} - {@link World} is used directly by the game.
 */
public final class Bed {

    private Bed() {
    }

    public static boolean isBed(BlockType t) {
        return t == BlockType.BED || t == BlockType.BED_HEAD
                || t == BlockType.BED_OCCUPIED || t == BlockType.BED_HEAD_OCCUPIED;
    }

    /**
     * Y of the bed column's foot end, resolving either half to the shared pair.
     * Beds are horizontal (X or Z direction), so we check both axes.
     */
    public static int footEnd(BlockAccessor world, int x, int y, int z) {
        if (isHead(world.getBlock(x - 1, y, z))) return x - 1;
        if (isHead(world.getBlock(x + 1, y, z))) return x;
        if (isHead(world.getBlock(x, y, z - 1))) return z - 1;
        if (isHead(world.getBlock(x, y, z + 1))) return z;
        return x; // Default to current position as foot
    }

    /** True if this block type is the head/pillow end of a bed. */
    public static boolean isHead(BlockType t) {
        return t == BlockType.BED_HEAD || t == BlockType.BED_HEAD_OCCUPIED;
    }

    /**
     * World-space of the foot half of the bed at {@code (x,y,z)}. Clicking either
     * half resolves to the same foot, so a spawn point stored from the pillow
     * still finds the bed after reload.
     */
    public static int[] footPos(BlockAccessor world, int x, int y, int z) {
        BlockType cur = world.getBlock(x, y, z);
        if (!isBed(cur)) return new int[]{x, y, z};
        if (!isHead(cur)) return new int[]{x, y, z};
        byte orientation = world.getBlockOrientation(x, y, z);
        return getOtherHalf(world, x, y, z, orientation);
    }

    /**
     * Gets the second position of the bed (the other half).
     * Returns the position of the head given foot position, or vice versa.
     */
    public static int[] getOtherHalf(BlockAccessor world, int x, int y, int z, byte orientation) {
        // Determine direction based on whether this is the head or foot.
        // The foot is placed first, then the head is placed adjacent in the opposite direction of facing.
        // So if we're at the foot, we go in the opposite direction of facing to find the head.
        // If we're at the head, we go in the facing direction to find the foot.
        boolean isHead = world != null && isHead(world.getBlock(x, y, z));
        // orientation: 0=+Z, 1=-Z, 2=+X, 3=-X (same as doors)
        switch (orientation) {
            case 0: return new int[]{x, y, isHead ? z + 1 : z - 1};  // facing +Z: head at z-1, foot at z+1 from head
            case 1: return new int[]{x, y, isHead ? z - 1 : z + 1};  // facing -Z: head at z+1, foot at z-1 from head
            case 2: return new int[]{x + (isHead ? 1 : -1), y, z};   // facing +X: head at x-1, foot at x+1 from head
            case 3: return new int[]{x + (isHead ? -1 : 1), y, z};   // facing -X: head at x+1, foot at x-1 from head
            default: return new int[]{x, y, isHead ? z + 1 : z - 1};
        }
    }

    /**
     * Places a bed (both halves) at the given position and orientation.
     * The foot end is placed at (x, y, z), the head end is placed adjacent based on orientation.
     */
    public static void place(BlockSetter set, int x, int y, int z, byte orientation, boolean occupied) {
        BlockType foot = occupied ? BlockType.BED_OCCUPIED : BlockType.BED;
        BlockType head = occupied ? BlockType.BED_HEAD_OCCUPIED : BlockType.BED_HEAD;
        
        set.set(x, y, z, foot);
        set.setOrientation(x, y, z, orientation);
        
        int[] other = getOtherHalf(null, x, y, z, orientation);
        set.set(other[0], other[1], other[2], head);
        set.setOrientation(other[0], other[1], other[2], orientation);
    }

    /**
     * Removes both halves of a bed column, so breaking one never leaves a floating half.
     */
    public static void breakBed(BlockAccessor get, BlockSetter set, int x, int y, int z) {
        BlockType cur = get.getBlock(x, y, z);
        if (!isBed(cur)) return;
        
        byte orientation = get.getBlockOrientation(x, y, z);
        int[] other = getOtherHalf(get, x, y, z, orientation);
        
        // Clear current position
        set.set(x, y, z, BlockType.AIR);
        // Clear other half
        set.set(other[0], other[1], other[2], BlockType.AIR);
    }

    /**
     * Toggles a bed between occupied and unoccupied state.
     */
    public static void setOccupied(BlockAccessor get, BlockSetter set, int x, int y, int z, boolean occupied) {
        BlockType cur = get.getBlock(x, y, z);
        if (!isBed(cur)) return;

        byte orientation = get.getBlockOrientation(x, y, z);
        int[] other = getOtherHalf(get, x, y, z, orientation);

        BlockType foot = occupied ? BlockType.BED_OCCUPIED : BlockType.BED;
        BlockType head = occupied ? BlockType.BED_HEAD_OCCUPIED : BlockType.BED_HEAD;

        // Determine which position is the foot and which is the head
        boolean curIsHead = isHead(cur);
        if (curIsHead) {
            set.set(x, y, z, head);
            set.setOrientation(x, y, z, orientation);
            set.set(other[0], other[1], other[2], foot);
            set.setOrientation(other[0], other[1], other[2], orientation);
        } else {
            set.set(x, y, z, foot);
            set.setOrientation(x, y, z, orientation);
            set.set(other[0], other[1], other[2], head);
            set.setOrientation(other[0], other[1], other[2], orientation);
        }
    }

    /** A block writer - implemented by {@link World#setBlock} in the game. */
    @FunctionalInterface
    public interface BlockSetter {
        void set(int x, int y, int z, BlockType t);
        default void setOrientation(int x, int y, int z, byte orientation) {
            // Default no-op for BlockAccessor that doesn't support orientation
        }
    }
}