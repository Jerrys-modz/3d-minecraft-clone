package com.minecraftclone.world;

/**
 * The worlds a single save can contain. Each dimension has its own {@link World}
 * with its own terrain generator, chunk storage subdirectory, and sky/fog look.
 * <p>
 * Dimensions are linked by portal blocks: a {@link BlockType#NETHER_PORTAL}
 * links the Overworld and the Nether, and a {@link BlockType#END_PORTAL} links
 * the Overworld and the End. Walking into a portal block teleports the player.
 */
public enum DimensionType {
    OVERWORLD("Overworld", "overworld"),
    NETHER("The Nether", "nether"),
    END("The End", "end");

    private final String displayName;
    private final String saveFolder;

    DimensionType(String displayName, String saveFolder) {
        this.displayName = displayName;
        this.saveFolder = saveFolder;
    }

    /** Human-readable name, e.g. for the F3 overlay and teleport messages. */
    public String displayName() {
        return displayName;
    }

    /** The save-directory subfolder this dimension's edited chunks live in. */
    public String saveFolder() {
        return saveFolder;
    }

    /**
     * The dimension a portal of the given type leads to from this one. Nether
     * portals bounce Overworld &lt;-&gt; Nether, End portals bounce Overworld
     * &lt;-&gt; End; from the wrong end the portal still leads to the Overworld.
     */
    public static DimensionType portalDestination(BlockType portal, DimensionType from) {
        if (portal == BlockType.NETHER_PORTAL) {
            return from == NETHER ? OVERWORLD : NETHER;
        }
        if (portal == BlockType.END_PORTAL) {
            return from == END ? OVERWORLD : END;
        }
        return OVERWORLD;
    }
}
