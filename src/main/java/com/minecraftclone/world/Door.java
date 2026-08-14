package com.minecraftclone.world;

/**
 * Functional door helpers. A door is a 2-block-tall column of {@link
 * BlockType#DOOR} (closed, solid) or {@link BlockType#DOOR_OPEN} (open,
 * walk-through); opening/closing toggles both halves together. The read and
 * write sides are passed in separately so the logic is testable without a full
 * {@link World} - {@link World} is used directly by the game.
 */
public final class Door {

    private Door() {
    }

    public static boolean isDoor(BlockType t) {
        return t == BlockType.DOOR || t == BlockType.DOOR_OPEN;
    }

    /** Y of the door column's bottom half, resolving either half to the shared pair. */
    public static int bottomHalf(BlockAccessor world, int x, int y, int z) {
        if (isDoor(world.getBlock(x, y - 1, z))) return y - 1;
        return y;
    }

    /** Opens/closes a door and its paired half. */
    public static void toggle(BlockAccessor get, BlockSetter set, int x, int y, int z) {
        int bottom = bottomHalf(get, x, y, z);
        for (int yy = bottom; yy <= bottom + 1; yy++) {
            BlockType cur = get.getBlock(x, yy, z);
            if (cur == BlockType.DOOR) set.set(x, yy, z, BlockType.DOOR_OPEN);
            else if (cur == BlockType.DOOR_OPEN) set.set(x, yy, z, BlockType.DOOR);
        }
    }

    /** Removes both halves of a door column, so breaking one never leaves a floating half. */
    public static void breakDoor(BlockAccessor get, BlockSetter set, int x, int y, int z) {
        int bottom = bottomHalf(get, x, y, z);
        for (int yy = bottom; yy <= bottom + 1; yy++) {
            if (isDoor(get.getBlock(x, yy, z))) {
                set.set(x, yy, z, BlockType.AIR);
            }
        }
    }

    /** A block writer - implemented by {@link World#setBlock} in the game. */
    @FunctionalInterface
    public interface BlockSetter {
        void set(int x, int y, int z, BlockType t);
    }
}
