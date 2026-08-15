package com.minecraftclone.world;

/**
 * A barrel: the same 27-slot storage as a {@link Chest}, but cheaper to craft
 * (6 planks) and deliberately <em>not</em> mergeable into a double - a compact
 * single-block stash. It deliberately reuses {@link Chest}'s slot behaviour
 * (see {@link com.minecraftclone.player.StorageContainer}) - the only things
 * that differ are its type name, block type, and that it never merges.
 */
public class Barrel extends Chest {

    public static final String TYPE = "barrel";

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public BlockType blockType() {
        return BlockType.BARREL;
    }
}
