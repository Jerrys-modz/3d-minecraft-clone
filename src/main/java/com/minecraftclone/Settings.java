package com.minecraftclone;

import com.minecraftclone.engine.KeyBindings;

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
    public static final int GAME_MODE = 5;
    public static final int CLOUDS = 6;        // 0-3: off/light/normal/heavy
    public static final int CLOUD_SPEED = 7;   // 0-2: slow/normal/fast
    public static final int STARS = 8;         // toggle
    public static final int ROW_COUNT = 9;

    private final boolean[] toggles = new boolean[ROW_COUNT];
    private final float[] ranges = new float[ROW_COUNT];
    private final KeyBindings keyBinds = new KeyBindings();

    public Settings() {
        toggles[VSYNC] = true; // matches the window's default state
        ranges[RENDER_DISTANCE] = 6f;
        ranges[FOV] = 75f;
        ranges[SENSITIVITY] = 0.12f;
        ranges[CLOUDS] = 2f;     // normal
        ranges[CLOUD_SPEED] = 1f; // normal
        toggles[STARS] = true;
    }

    /** True if the given row is a boolean toggle; false for a numeric range. */
    public static boolean isToggle(int row) {
        return row == LEAVES_TRANSPARENT || row == VSYNC || row == STARS;
    }

    public static String label(int row) {
        return switch (row) {
            case LEAVES_TRANSPARENT -> "See-through leaves";
            case RENDER_DISTANCE -> "Render distance";
            case VSYNC -> "VSync";
            case FOV -> "Field of view";
            case SENSITIVITY -> "Mouse sensitivity";
            case GAME_MODE -> "Game mode";
            case CLOUDS -> "Clouds";
            case CLOUD_SPEED -> "Cloud speed";
            case STARS -> "Stars";
            default -> "?";
        };
    }

    /** Step applied per Left/Right keypress on a numeric range row. */
    public static float step(int row) {
        return switch (row) {
            case RENDER_DISTANCE -> 1f;
            case FOV -> 5f;
            case SENSITIVITY -> 0.01f;
            case GAME_MODE -> 1f;
            case CLOUDS -> 1f;
            case CLOUD_SPEED -> 1f;
            default -> 0f;
        };
    }

    public static float minValue(int row) {
        return switch (row) {
            case RENDER_DISTANCE -> 3f;
            case FOV -> 60f;
            case SENSITIVITY -> 0.03f;
            case GAME_MODE -> 0f;
            case CLOUDS -> 0f;
            case CLOUD_SPEED -> 0f;
            default -> 0f;
        };
    }

    public static float maxValue(int row) {
        return switch (row) {
            case RENDER_DISTANCE -> 12f;
            case FOV -> 110f;
            case SENSITIVITY -> 0.4f;
            case GAME_MODE -> GameMode.values().length - 1f;
            case CLOUDS -> 3f;
            case CLOUD_SPEED -> 2f;
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
        if (row == GAME_MODE) {
            return getGameMode().toString();
        }
        if (row == CLOUDS) {
            return switch (getCloudAmount()) {
                case 0 -> "Off";
                case 1 -> "Light";
                case 3 -> "Heavy";
                default -> "Normal";
            };
        }
        if (row == CLOUD_SPEED) {
            return switch (getCloudSpeed()) {
                case 0 -> "Slow";
                case 2 -> "Fast";
                default -> "Normal";
            };
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

    /** 0..1 fill fraction for a range row's slider (0 for toggles). */
    public float fraction(int row) {
        float denom = maxValue(row) - minValue(row);
        if (denom == 0f) return 1f;
        return (ranges[row] - minValue(row)) / denom;
    }

    /** Sets a range row from a slider fraction (0..1); discrete rows (game mode) round to the nearest option. */
    public void setFromFraction(int row, float fraction) {
        if (isToggle(row)) return;
        float f = Math.max(0f, Math.min(1f, fraction));
        float v = minValue(row) + (maxValue(row) - minValue(row)) * f;
        if (row == GAME_MODE) v = Math.round(v);
        ranges[row] = clamp(row, v);
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
        lines.add("game_mode=" + getGameMode().ordinal());
        lines.add("clouds=" + getCloudAmount());
        lines.add("cloud_speed=" + getCloudSpeed());
        lines.add("stars=" + (toggles[STARS] ? 1 : 0));
        keyBinds.saveLines(lines);
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
                        case "game_mode" -> s.ranges[GAME_MODE] = clamp(GAME_MODE, Float.parseFloat(value));
                        case "clouds" -> s.ranges[CLOUDS] = clamp(CLOUDS, Float.parseFloat(value));
                        case "cloud_speed" -> s.ranges[CLOUD_SPEED] = clamp(CLOUD_SPEED, Float.parseFloat(value));
                        case "stars" -> s.toggles[STARS] = parseBool(value);
                        default -> s.keyBinds.loadEntry(key, value);
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

    public GameMode getGameMode() {
        int idx = Math.round(ranges[GAME_MODE]);
        GameMode[] modes = GameMode.values();
        return modes[Math.max(0, Math.min(modes.length - 1, idx))];
    }

    /** 0 = off, 1 = light, 2 = normal, 3 = heavy. */
    public int getCloudAmount() {
        return Math.round(ranges[CLOUDS]);
    }

    /** 0 = slow, 1 = normal, 2 = fast. */
    public int getCloudSpeed() {
        return Math.round(ranges[CLOUD_SPEED]);
    }

    public boolean isStars() {
        return toggles[STARS];
    }

    /** Remappable gameplay key bindings (see {@link KeyBindings}). */
    public KeyBindings getKeyBinds() {
        return keyBinds;
    }
}
