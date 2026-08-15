package com.minecraftclone.engine;

import static org.lwjgl.glfw.GLFW.*;

/**
 * Remappable keyboard bindings for gameplay actions. Actions are identified by
 * an index ({@link #FORWARD} ... {@link #SCREENSHOT}) and map to a GLFW key
 * code. The settings menu lets the player re-bind any action by pressing a key;
 * bindings are persisted alongside the other settings (see
 * {@link com.minecraftclone.Settings}).
 */
public class KeyBindings {

    public static final int FORWARD = 0;
    public static final int BACK = 1;
    public static final int LEFT = 2;
    public static final int RIGHT = 3;
    public static final int JUMP = 4;
    public static final int SPRINT = 5;
    public static final int FLY_DOWN = 6;
    public static final int FLY_TOGGLE = 7;
    public static final int INVENTORY = 8;
    public static final int DEBUG = 9;
    public static final int SCREENSHOT = 10;
    public static final int COUNT = 11;

    private static final int[] DEFAULT_KEYS = {
            GLFW_KEY_W, GLFW_KEY_S, GLFW_KEY_A, GLFW_KEY_D,
            GLFW_KEY_SPACE, GLFW_KEY_LEFT_CONTROL, GLFW_KEY_LEFT_SHIFT, GLFW_KEY_F,
            GLFW_KEY_E, GLFW_KEY_F3, GLFW_KEY_F2,
    };

    private final int[] keys = new int[COUNT];

    public KeyBindings() {
        reset();
    }

    public void reset() {
        System.arraycopy(DEFAULT_KEYS, 0, keys, 0, COUNT);
    }

    public int get(int action) {
        return keys[action];
    }

    public void set(int action, int key) {
        keys[action] = key;
    }

    /** Display name of an action, for the settings menu. */
    public static String name(int action) {
        return switch (action) {
            case FORWARD -> "Forward";
            case BACK -> "Back";
            case LEFT -> "Left";
            case RIGHT -> "Right";
            case JUMP -> "Jump / fly up";
            case SPRINT -> "Sprint";
            case FLY_DOWN -> "Fly down";
            case FLY_TOGGLE -> "Fly toggle";
            case INVENTORY -> "Inventory";
            case DEBUG -> "Debug";
            case SCREENSHOT -> "Screenshot";
            default -> "?";
        };
    }

    /** True if {@code key} is a modifier-only key that shouldn't be rebound. */
    public static boolean isModifierKey(int key) {
        return key == GLFW_KEY_LEFT_SHIFT || key == GLFW_KEY_RIGHT_SHIFT
                || key == GLFW_KEY_LEFT_CONTROL || key == GLFW_KEY_RIGHT_CONTROL
                || key == GLFW_KEY_LEFT_ALT || key == GLFW_KEY_RIGHT_ALT;
    }

    /**
     * Short display name for a GLFW key code, without needing a live GLFW
     * context (letters/digits come from the key code; special keys use a table).
     */
    public static String keyName(int key) {
        if (key >= GLFW_KEY_A && key <= GLFW_KEY_Z) {
            return String.valueOf((char) key);
        }
        if (key >= GLFW_KEY_0 && key <= GLFW_KEY_9) {
            return String.valueOf((char) key);
        }
        return switch (key) {
            case GLFW_KEY_SPACE -> "Space";
            case GLFW_KEY_LEFT_CONTROL -> "L Ctrl";
            case GLFW_KEY_RIGHT_CONTROL -> "R Ctrl";
            case GLFW_KEY_LEFT_SHIFT -> "L Shift";
            case GLFW_KEY_RIGHT_SHIFT -> "R Shift";
            case GLFW_KEY_LEFT_ALT -> "L Alt";
            case GLFW_KEY_RIGHT_ALT -> "R Alt";
            case GLFW_KEY_ENTER -> "Enter";
            case GLFW_KEY_ESCAPE -> "Esc";
            case GLFW_KEY_TAB -> "Tab";
            case GLFW_KEY_BACKSPACE -> "Backspace";
            case GLFW_KEY_UP -> "Up";
            case GLFW_KEY_DOWN -> "Down";
            case GLFW_KEY_LEFT -> "Left";
            case GLFW_KEY_RIGHT -> "Right";
            case GLFW_KEY_COMMA -> ",";
            case GLFW_KEY_PERIOD -> ".";
            case GLFW_KEY_MINUS -> "-";
            case GLFW_KEY_EQUAL -> "=";
            case GLFW_KEY_SEMICOLON -> ";";
            case GLFW_KEY_APOSTROPHE -> "'";
            case GLFW_KEY_SLASH -> "/";
            case GLFW_KEY_BACKSLASH -> "\\";
            case GLFW_KEY_LEFT_BRACKET -> "[";
            case GLFW_KEY_RIGHT_BRACKET -> "]";
            default -> key >= GLFW_KEY_F1 && key <= GLFW_KEY_F12 ? "F" + (key - GLFW_KEY_F1 + 1) : "?";
        };
    }

    /** Settings-file key names, one per action, in {@link #COUNT} order. */
    private static final String[] FILE_KEYS = {
            "key_forward", "key_back", "key_left", "key_right", "key_jump", "key_sprint",
            "key_fly_down", "key_fly_toggle", "key_inventory", "key_debug", "key_screenshot",
    };

    /** Appends {@code key_xxx=code} lines for persistence (see Settings.save). */
    public void saveLines(java.util.List<String> lines) {
        for (int i = 0; i < COUNT; i++) {
            lines.add(FILE_KEYS[i] + "=" + keys[i]);
        }
    }

    /** Applies a persisted {@code key_xxx=code} entry; unknown keys are ignored. */
    public void loadEntry(String fileKey, String value) {
        for (int i = 0; i < COUNT; i++) {
            if (FILE_KEYS[i].equals(fileKey)) {
                try {
                    keys[i] = Integer.parseInt(value.trim());
                } catch (NumberFormatException ignored) {
                    // Malformed binding: keep the default for that action.
                }
                return;
            }
        }
    }
}
