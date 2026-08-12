package com.minecraftclone;

/**
 * Mutable graphics/game settings, edited in-game from the settings menu (Esc).
 * There are two row kinds: boolean toggles and numeric ranges. The menu
 * renders one row per setting from the static metadata below, and the game
 * reads the values via the getters (see {@code Main.applySettings}).
 */
public class Settings {

    public static final int LEAVES_TRANSPARENT = 0;
    public static final int RENDER_DISTANCE = 1;
    public static final int VSYNC = 2;
    public static final int FOV = 3;
    public static final int SENSITIVITY = 4;
    public static final int ROW_COUNT = 5;

    private final boolean[] toggles = new boolean[ROW_COUNT];
    private final float[] ranges = new float[ROW_COUNT];

    public Settings() {
        toggles[VSYNC] = true; // matches the window's default state
        ranges[RENDER_DISTANCE] = 6f;
        ranges[FOV] = 75f;
        ranges[SENSITIVITY] = 0.12f;
    }

    /** True if the given row is a boolean toggle; false for a numeric range. */
    public static boolean isToggle(int row) {
        return row == LEAVES_TRANSPARENT || row == VSYNC;
    }

    public static String label(int row) {
        return switch (row) {
            case LEAVES_TRANSPARENT -> "See-through leaves";
            case RENDER_DISTANCE -> "Render distance";
            case VSYNC -> "VSync";
            case FOV -> "Field of view";
            case SENSITIVITY -> "Mouse sensitivity";
            default -> "?";
        };
    }

    /** Step applied per Left/Right keypress on a numeric range row. */
    public static float step(int row) {
        return switch (row) {
            case RENDER_DISTANCE -> 1f;
            case FOV -> 5f;
            case SENSITIVITY -> 0.01f;
            default -> 0f;
        };
    }

    public static float minValue(int row) {
        return switch (row) {
            case RENDER_DISTANCE -> 3f;
            case FOV -> 60f;
            case SENSITIVITY -> 0.03f;
            default -> 0f;
        };
    }

    public static float maxValue(int row) {
        return switch (row) {
            case RENDER_DISTANCE -> 12f;
            case FOV -> 110f;
            case SENSITIVITY -> 0.4f;
            default -> 0f;
        };
    }

    /** The row's current value, formatted for the menu (ON/OFF or a number). */
    public String valueText(int row) {
        if (isToggle(row)) {
            return toggles[row] ? "ON" : "OFF";
        }
        if (row == SENSITIVITY) {
            return String.format("%.2f", ranges[row]);
        }
        return String.valueOf(Math.round(ranges[row]));
    }

    /**
     * Applies one step of change in the given direction (+1 or -1) to a row:
     * toggles flip regardless of direction, ranges move by their step and
     * clamp to their min/max.
     */
    public void adjust(int row, int direction) {
        if (isToggle(row)) {
            toggles[row] = !toggles[row];
            return;
        }
        float next = ranges[row] + step(row) * Math.signum(direction);
        ranges[row] = Math.max(minValue(row), Math.min(maxValue(row), next));
    }

    public boolean isLeavesTransparent() {
        return toggles[LEAVES_TRANSPARENT];
    }

    public int getRenderDistance() {
        return Math.round(ranges[RENDER_DISTANCE]);
    }

    public boolean isVsync() {
        return toggles[VSYNC];
    }

    public float getFov() {
        return ranges[FOV];
    }

    public float getMouseSensitivity() {
        return ranges[SENSITIVITY];
    }
}
