package com.minecraftclone;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Mutable graphics/game settings, edited in-game from the settings menu (Esc).
 * There are two row kinds: boolean toggles and numeric ranges. The menu
 * renders one row per setting from the static metadata below, and the game
 * reads the values via the getters (see {@code Main.applySettings}).
 * <p>
 * Settings are persisted to a small {@code key=value} text file via
 * {@link #save(Path)} / {@link #load(Path)} and restored on the next launch.
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

    /**
     * Writes all settings to {@code file} as simple {@code key=value} lines.
     * Best-effort: failures are logged, not thrown, so a read-only directory
     * never stops the game.
     */
    public void save(Path file) {
        List<String> lines = new ArrayList<>();
        lines.add("leaves_transparent=" + (toggles[LEAVES_TRANSPARENT] ? 1 : 0));
        lines.add("render_distance=" + getRenderDistance());
        lines.add("vsync=" + (toggles[VSYNC] ? 1 : 0));
        lines.add("fov=" + Math.round(ranges[FOV]));
        lines.add("mouse_sensitivity=" + ranges[SENSITIVITY]);
        try {
            if (file.getParent() != null) {
                Files.createDirectories(file.getParent());
            }
            Files.write(file, lines, StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.err.println("Could not save settings to " + file + ": " + e.getMessage());
        }
    }

    /**
     * Loads settings from {@code file}, keeping defaults for any missing or
     * malformed entries. Returns a fresh {@link Settings} with defaults if the
     * file doesn't exist yet.
     */
    public static Settings load(Path file) {
        Settings s = new Settings();
        if (file == null || !Files.isRegularFile(file)) return s;
        try {
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                int eq = line.indexOf('=');
                if (eq <= 0) continue;
                String key = line.substring(0, eq).trim();
                String value = line.substring(eq + 1).trim();
                try {
                    switch (key) {
                        case "leaves_transparent" -> s.toggles[LEAVES_TRANSPARENT] = parseBool(value);
                        case "render_distance" -> s.ranges[RENDER_DISTANCE] = clamp(RENDER_DISTANCE, Float.parseFloat(value));
                        case "vsync" -> s.toggles[VSYNC] = parseBool(value);
                        case "fov" -> s.ranges[FOV] = clamp(FOV, Float.parseFloat(value));
                        case "mouse_sensitivity" -> s.ranges[SENSITIVITY] = clamp(SENSITIVITY, Float.parseFloat(value));
                        default -> { /* ignore unknown keys for forward compatibility */ }
                    }
                } catch (NumberFormatException ignored) {
                    // Malformed value: keep the default for that entry.
                }
            }
        } catch (IOException e) {
            System.err.println("Could not load settings from " + file + ": " + e.getMessage());
        }
        return s;
    }

    private static boolean parseBool(String value) {
        return value.equals("1") || value.equalsIgnoreCase("true");
    }

    private static float clamp(int row, float value) {
        return Math.max(minValue(row), Math.min(maxValue(row), value));
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
