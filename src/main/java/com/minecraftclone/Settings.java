package com.minecraftclone;

/**
 * Mutable graphics/game settings, edited in-game from the settings menu
 * (Esc). Each toggle is one row in that menu - see {@link #ROW_LABELS} and
 * {@link #toggles} - and the renderer reads the values directly to decide
 * how to draw (see e.g. {@link com.minecraftclone.world.World#setLeavesTransparent}).
 */
public class Settings {

    /** Menu row index of the "see-through leaves" toggle. */
    public static final int LEAVES_TRANSPARENT = 0;
    /** Number of toggleable settings shown in the menu. */
    public static final int ROW_COUNT = 1;

    /** One label per setting, in menu display order. */
    public static final String[] ROW_LABELS = {
            "See-through leaves",
    };

    /** Toggle values, parallel to {@link #ROW_LABELS}. */
    public final boolean[] toggles = new boolean[ROW_COUNT];

    public boolean isLeavesTransparent() {
        return toggles[LEAVES_TRANSPARENT];
    }
}
