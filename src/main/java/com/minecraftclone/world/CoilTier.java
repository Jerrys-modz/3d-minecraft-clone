package com.minecraftclone.world;

/** EBF heating coils: the weakest coil in the structure sets its temperature. */
public enum CoilTier {
    CUPRONICKEL(BlockType.CUPRONICKEL_COIL, 1800),
    KANTHAL(BlockType.KANTHAL_COIL, 2700),
    NICHROME(BlockType.NICHROME_COIL, 3600);

    public final BlockType block;
    /** Maximum temperature (K) an EBF built from these coils reaches. */
    public final int kelvin;

    CoilTier(BlockType block, int kelvin) {
        this.block = block;
        this.kelvin = kelvin;
    }

    /** The coil tier of a block, or null if it isn't a coil. */
    public static CoilTier of(BlockType block) {
        for (CoilTier t : values()) {
            if (t.block == block) return t;
        }
        return null;
    }
}
