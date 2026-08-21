package com.minecraftclone;

import com.minecraftclone.engine.GamepadBindings;
import com.minecraftclone.engine.KeyBindings;
import com.minecraftclone.world.gen.WorldGenSettings;

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
 * Game mode is not stored here — it lives on each world's {@code world.txt}
 * and is only shown on the in-game pause menu (and Create New World).
 */
public class Settings {

    // Row indices, grouped by settings tab (see TAB_ROW_START / TAB_ROW_END below).
    public static final int LEAVES_TRANSPARENT = 0; // Graphics
    public static final int RENDER_DISTANCE = 1;    // Graphics
    public static final int VSYNC = 2;              // Graphics
    public static final int FOV = 3;                // Graphics
    public static final int BRIGHTNESS = 4;         // Graphics
    public static final int CLOUDS = 5;             // 0-3: off/light/normal/heavy (Graphics)
    public static final int CLOUD_SPEED = 6;        // 0-2: slow/normal/fast (Graphics)
    public static final int STARS = 7;              // toggle (Graphics)
    public static final int DARK_GUI = 8;           // toggle (Graphics)
    public static final int GAME_MODE = 9;          // Gameplay (in-world only; stored on the world)
    public static final int SENSITIVITY = 10;       // Gameplay
    public static final int INVERT_MOUSE_Y = 11;    // toggle (Gameplay)
    public static final int VIEW_BOBBING = 12;      // toggle (Gameplay)
    public static final int SOUND_VOLUME = 13;      // Audio - master, multiplies every category below
    public static final int MUSIC_VOLUME = 14;      // Audio
    public static final int AMBIENT_VOLUME = 15;    // Audio
    public static final int MOBS_VOLUME = 16;       // Audio
    public static final int MACHINES_VOLUME = 17;   // Audio - blocks, doors, tools, crafting
    public static final int PLAYER_VOLUME = 18;     // Audio - footsteps, jump/land, eat, hurt, ...
    public static final int UI_VOLUME = 19;         // Audio
    public static final int ROW_COUNT = 20;

    // Settings tabs: each owns a contiguous slice of the rows above. The
    // Controls and Controller tabs have no Settings rows - they show the
    // keybind/gamepad-binding lists instead.
    public static final int TAB_GRAPHICS = 0;
    public static final int TAB_GAMEPLAY = 1;
    public static final int TAB_AUDIO = 2;
    public static final int TAB_CONTROLS = 3;
    public static final int TAB_CONTROLLER = 4;
    public static final int TAB_COUNT = 5;
    private static final int[] TAB_ROW_START = {LEAVES_TRANSPARENT, GAME_MODE, SOUND_VOLUME, ROW_COUNT, ROW_COUNT};
    private static final int[] TAB_ROW_END = {GAME_MODE, SOUND_VOLUME, ROW_COUNT, ROW_COUNT, ROW_COUNT};

    /** Display name of a settings tab, for the tab bar. */
    public static String tabLabel(int tab) {
        return switch (tab) {
            case TAB_GRAPHICS -> "Graphics";
            case TAB_GAMEPLAY -> "Gameplay";
            case TAB_AUDIO -> "Audio";
            case TAB_CONTROLS -> "Controls";
            case TAB_CONTROLLER -> "Controller";
            default -> "?";
        };
    }

    /** Number of setting rows on {@code tab} (0 for the keybinds-only Controls tab). */
    public static int tabRowCount(int tab) {
        return tabRowCount(tab, true);
    }

    /**
     * Number of setting rows on {@code tab}. Game mode lives on the world, so the
     * title-screen Settings page ({@code inWorld == false}) hides that Gameplay row;
     * the in-game pause menu still shows it so you can change the current world's mode.
     */
    public static int tabRowCount(int tab, boolean inWorld) {
        return TAB_ROW_END[tab] - tabRowStart(tab, inWorld);
    }

    /** The Settings row index shown as local row {@code local} on tab {@code tab}. */
    public static int rowInTab(int tab, int local) {
        return rowInTab(tab, local, true);
    }

    /** The Settings row index shown as local row {@code local} on tab {@code tab}. */
    public static int rowInTab(int tab, int local, boolean inWorld) {
        return tabRowStart(tab, inWorld) + local;
    }

    /**
     * First Settings row of {@code tab}. Title-screen Settings skip {@link #GAME_MODE}
     * so it isn't treated as a global option.
     */
    public static int tabRowStart(int tab, boolean inWorld) {
        int start = TAB_ROW_START[tab];
        if (!inWorld && tab == TAB_GAMEPLAY) {
            return SENSITIVITY;
        }
        return start;
    }

    /** True for rows that belong to a loaded world rather than global {@code settings.txt}. */
    public static boolean isWorldSetting(int row) {
        return row == GAME_MODE;
    }

    private final boolean[] toggles = new boolean[ROW_COUNT];
    private final float[] ranges = new float[ROW_COUNT];
    private final KeyBindings keyBinds = new KeyBindings();
    private final GamepadBindings gamepadBinds = new GamepadBindings();
    private final WorldGenSettings worldGen = new WorldGenSettings();

    public Settings() {
        toggles[VSYNC] = true; // matches the window's default state
        ranges[RENDER_DISTANCE] = 6f;
        ranges[FOV] = 75f;
        ranges[BRIGHTNESS] = 1f;
        ranges[SENSITIVITY] = 0.12f;
        ranges[CLOUDS] = 2f;     // normal
        ranges[CLOUD_SPEED] = 1f; // normal
        toggles[STARS] = true;
        toggles[VIEW_BOBBING] = true;
        ranges[SOUND_VOLUME] = 1f;
        ranges[MUSIC_VOLUME] = 1f;
        ranges[AMBIENT_VOLUME] = 1f;
        ranges[MOBS_VOLUME] = 1f;
        ranges[MACHINES_VOLUME] = 1f;
        ranges[PLAYER_VOLUME] = 1f;
        ranges[UI_VOLUME] = 1f;
        // Light GUI by default (Minecraft's modern look); Dark is a toggle.
    }

    /** True if the given row is a boolean toggle; false for a numeric range. */
    public static boolean isToggle(int row) {
        return row == LEAVES_TRANSPARENT || row == VSYNC || row == STARS || row == DARK_GUI
                || row == INVERT_MOUSE_Y || row == VIEW_BOBBING;
    }

    /** True for every Audio-tab volume slider (master + each category) - they all share the same 0-100% shape. */
    private static boolean isVolumeRow(int row) {
        return row == SOUND_VOLUME || row == MUSIC_VOLUME || row == AMBIENT_VOLUME
                || row == MOBS_VOLUME || row == MACHINES_VOLUME || row == PLAYER_VOLUME || row == UI_VOLUME;
    }

    public static String label(int row) {
        return switch (row) {
            case LEAVES_TRANSPARENT -> "See-through leaves";
            case RENDER_DISTANCE -> "Render distance";
            case VSYNC -> "VSync";
            case FOV -> "Field of view";
            case BRIGHTNESS -> "Brightness";
            case SENSITIVITY -> "Mouse sensitivity";
            case GAME_MODE -> "Game mode";
            case CLOUDS -> "Clouds";
            case CLOUD_SPEED -> "Cloud speed";
            case STARS -> "Stars";
            case DARK_GUI -> "Dark GUI";
            case INVERT_MOUSE_Y -> "Invert mouse Y";
            case VIEW_BOBBING -> "View bobbing";
            case SOUND_VOLUME -> "Master volume";
            case MUSIC_VOLUME -> "Music volume";
            case AMBIENT_VOLUME -> "Ambient volume";
            case MOBS_VOLUME -> "Mobs volume";
            case MACHINES_VOLUME -> "Machines volume";
            case PLAYER_VOLUME -> "Player volume";
            case UI_VOLUME -> "UI volume";
            default -> "?";
        };
    }

    /** Step applied per Left/Right keypress on a numeric range row. */
    public static float step(int row) {
        if (isVolumeRow(row)) return 0.05f;
        return switch (row) {
            case RENDER_DISTANCE -> 1f;
            case FOV -> 5f;
            case BRIGHTNESS -> 0.05f;
            case SENSITIVITY -> 0.01f;
            case GAME_MODE -> 1f;
            case CLOUDS -> 1f;
            case CLOUD_SPEED -> 1f;
            default -> 0f;
        };
    }

    public static float minValue(int row) {
        if (isVolumeRow(row)) return 0f;
        return switch (row) {
            case RENDER_DISTANCE -> 3f;
            case FOV -> 60f;
            case BRIGHTNESS -> 0.5f;
            case SENSITIVITY -> 0.03f;
            case GAME_MODE -> 0f;
            case CLOUDS -> 0f;
            case CLOUD_SPEED -> 0f;
            default -> 0f;
        };
    }

    public static float maxValue(int row) {
        if (isVolumeRow(row)) return 1f;
        return switch (row) {
            case RENDER_DISTANCE -> 12f;
            case FOV -> 110f;
            case BRIGHTNESS -> 1.5f;
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
        if (row == BRIGHTNESS || isVolumeRow(row)) {
            return Math.round(ranges[row] * 100f) + "%";
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
        lines.add("brightness=" + ranges[BRIGHTNESS]);
        lines.add("mouse_sensitivity=" + ranges[SENSITIVITY]);
        lines.add("clouds=" + getCloudAmount());
        lines.add("cloud_speed=" + getCloudSpeed());
        lines.add("stars=" + (toggles[STARS] ? 1 : 0));
        lines.add("dark_gui=" + (toggles[DARK_GUI] ? 1 : 0));
        lines.add("invert_mouse_y=" + (toggles[INVERT_MOUSE_Y] ? 1 : 0));
        lines.add("view_bobbing=" + (toggles[VIEW_BOBBING] ? 1 : 0));
        lines.add("sound_volume=" + ranges[SOUND_VOLUME]);
        lines.add("music_volume=" + ranges[MUSIC_VOLUME]);
        lines.add("ambient_volume=" + ranges[AMBIENT_VOLUME]);
        lines.add("mobs_volume=" + ranges[MOBS_VOLUME]);
        lines.add("machines_volume=" + ranges[MACHINES_VOLUME]);
        lines.add("player_volume=" + ranges[PLAYER_VOLUME]);
        lines.add("ui_volume=" + ranges[UI_VOLUME]);
        keyBinds.saveLines(lines);
        gamepadBinds.saveLines(lines);
        // World-generation choices (seed, game mode, …) live in each world's
        // world.txt — they are not global settings, so they are not written here.
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
                        case "brightness" -> s.ranges[BRIGHTNESS] = clamp(BRIGHTNESS, Float.parseFloat(value));
                        case "mouse_sensitivity" -> s.ranges[SENSITIVITY] = clamp(SENSITIVITY, Float.parseFloat(value));
                        case "game_mode" -> { /* per-world; ignored in global settings.txt */ }
                        case "clouds" -> s.ranges[CLOUDS] = clamp(CLOUDS, Float.parseFloat(value));
                        case "cloud_speed" -> s.ranges[CLOUD_SPEED] = clamp(CLOUD_SPEED, Float.parseFloat(value));
                        case "stars" -> s.toggles[STARS] = parseBool(value);
                        case "dark_gui" -> s.toggles[DARK_GUI] = parseBool(value);
                        case "invert_mouse_y" -> s.toggles[INVERT_MOUSE_Y] = parseBool(value);
                        case "view_bobbing" -> s.toggles[VIEW_BOBBING] = parseBool(value);
                        case "sound_volume" -> loadFiniteRange(s, SOUND_VOLUME, value);
                        case "music_volume" -> loadFiniteRange(s, MUSIC_VOLUME, value);
                        case "ambient_volume" -> loadFiniteRange(s, AMBIENT_VOLUME, value);
                        case "mobs_volume" -> loadFiniteRange(s, MOBS_VOLUME, value);
                        case "machines_volume" -> loadFiniteRange(s, MACHINES_VOLUME, value);
                        case "player_volume" -> loadFiniteRange(s, PLAYER_VOLUME, value);
                        case "ui_volume" -> loadFiniteRange(s, UI_VOLUME, value);
                        default -> {
                            s.keyBinds.loadEntry(key, value);
                            s.gamepadBinds.loadEntry(key, value);
                            s.worldGen.loadEntry(key, value);
                        }
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

    /**
     * Parses a numeric range value that must reject non-finite input: a
     * corrupted or hand-edited settings.txt could otherwise pass NaN/Infinity
     * straight through {@code clamp} (which leaves them untouched) and hand
     * AudioEngine a non-finite gain. Malformed values keep the row's default.
     */
    private static void loadFiniteRange(Settings s, int row, String value) {
        float parsed = Float.parseFloat(value);
        if (Float.isFinite(parsed)) {
            s.ranges[row] = clamp(row, parsed);
        }
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

    /** Sets the in-memory game mode (persisted on the loaded world's {@code world.txt}, not here). */
    public void setGameMode(GameMode mode) {
        ranges[GAME_MODE] = mode == null ? 0 : mode.ordinal();
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

    /** True for the dark GUI theme; false for the light theme (the default). */
    public boolean isDarkGui() {
        return toggles[DARK_GUI];
    }

    /** Brightness multiplier (0.5-1.5) applied to the world's ambient light. */
    public float getBrightness() {
        return ranges[BRIGHTNESS];
    }

    public boolean isInvertMouseY() {
        return toggles[INVERT_MOUSE_Y];
    }

    public boolean isViewBobbing() {
        return toggles[VIEW_BOBBING];
    }

    /** Master sound volume (0..1), multiplied into every category volume below. */
    public float getSoundVolume() {
        return ranges[SOUND_VOLUME];
    }

    /** Music category volume (0..1). No music is wired up yet, but the slider is ready for one. */
    public float getMusicVolume() {
        return ranges[MUSIC_VOLUME];
    }

    /** Ambient/environment category volume (0..1). No ambience is wired up yet, but the slider is ready for one. */
    public float getAmbientVolume() {
        return ranges[AMBIENT_VOLUME];
    }

    /** Mob sounds category volume (0..1): attacks and mob deaths. */
    public float getMobsVolume() {
        return ranges[MOBS_VOLUME];
    }

    /** Machines/world category volume (0..1): block break/place, doors, tool wear, crafting. */
    public float getMachinesVolume() {
        return ranges[MACHINES_VOLUME];
    }

    /** Player category volume (0..1): footsteps, jump/land, splash, eat, hurt, death, item pickup. */
    public float getPlayerVolume() {
        return ranges[PLAYER_VOLUME];
    }

    /** UI category volume (0..1): menu/inventory/crafting clicks and open/close. */
    public float getUiVolume() {
        return ranges[UI_VOLUME];
    }

    /** World-generation settings for new worlds (see {@link WorldGenSettings}). */
    public WorldGenSettings getWorldGen() {
        return worldGen;
    }

    /** Remappable gameplay key bindings (see {@link KeyBindings}). */
    public KeyBindings getKeyBinds() {
        return keyBinds;
    }

    /** Remappable gamepad button bindings (see {@link GamepadBindings}). */
    public GamepadBindings getGamepadBinds() {
        return gamepadBinds;
    }
}
