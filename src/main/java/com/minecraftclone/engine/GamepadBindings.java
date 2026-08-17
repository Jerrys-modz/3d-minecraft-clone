package com.minecraftclone.engine;

import static org.lwjgl.glfw.GLFW.*;

/**
 * Remappable gamepad button bindings, the controller counterpart to
 * {@link KeyBindings}. Only actions that make sense as a single button are
 * offered here - movement stays the left stick (there's only one stick for
 * it, nothing to remap), and menu/mine/place/hotbar-cycle stay fixed to
 * Start/the triggers/the bumpers for the same reason Esc and the mouse
 * buttons aren't in {@link KeyBindings} either: they're not meaningfully
 * reassignable, just a fixed part of the control scheme.
 * <p>
 * Each row here reuses one of {@link KeyBindings}'s action constants (see
 * {@link #keyBindingAction}) rather than inventing a parallel action enum,
 * since it's the exact same gameplay action - just bound to a gamepad button
 * instead of (or alongside) a key. {@link Input#updateGamepad} looks up the
 * bound button via {@link #buttonFor}.
 */
public class GamepadBindings {

    /** Local row order shown in the settings menu's Controller tab, and each row's underlying {@link KeyBindings} action. */
    private static final int[] ACTIONS = {
            KeyBindings.JUMP, KeyBindings.SPRINT, KeyBindings.FLY_DOWN, KeyBindings.FLY_TOGGLE,
            KeyBindings.INVENTORY, KeyBindings.DEBUG, KeyBindings.SCREENSHOT, KeyBindings.FORECAST,
    };
    public static final int COUNT = ACTIONS.length;

    private static final int[] DEFAULT_BUTTONS = {
            GLFW_GAMEPAD_BUTTON_A, GLFW_GAMEPAD_BUTTON_LEFT_THUMB, GLFW_GAMEPAD_BUTTON_B, GLFW_GAMEPAD_BUTTON_X,
            GLFW_GAMEPAD_BUTTON_Y, GLFW_GAMEPAD_BUTTON_BACK, GLFW_GAMEPAD_BUTTON_DPAD_UP, GLFW_GAMEPAD_BUTTON_RIGHT_THUMB,
    };

    private final int[] buttons = new int[COUNT];

    public GamepadBindings() {
        reset();
    }

    public void reset() {
        System.arraycopy(DEFAULT_BUTTONS, 0, buttons, 0, COUNT);
    }

    /** The gamepad button bound to row {@code localIndex} (0..{@link #COUNT}-1, matching the settings menu's row order). */
    public int get(int localIndex) {
        return buttons[localIndex];
    }

    public void set(int localIndex, int button) {
        buttons[localIndex] = button;
    }

    /** The {@link KeyBindings} action row {@code localIndex} controls - for its display name. */
    public static int keyBindingAction(int localIndex) {
        return ACTIONS[localIndex];
    }

    /** Display name of row {@code localIndex} - shares {@link KeyBindings#name} since it's the same action. */
    public static String name(int localIndex) {
        return KeyBindings.name(ACTIONS[localIndex]);
    }

    /**
     * The gamepad button currently bound to {@code keyBindingAction} (a
     * {@link KeyBindings} constant, e.g. {@link KeyBindings#JUMP}), or -1 if
     * that action has no gamepad row (movement: always the left stick).
     */
    public int buttonFor(int keyBindingAction) {
        for (int i = 0; i < COUNT; i++) {
            if (ACTIONS[i] == keyBindingAction) return buttons[i];
        }
        return -1;
    }

    /** Short display name for a GLFW_GAMEPAD_BUTTON_* index, for the settings menu (mirrors {@link KeyBindings#keyName}). */
    public static String buttonName(int button) {
        return switch (button) {
            case GLFW_GAMEPAD_BUTTON_A -> "A";
            case GLFW_GAMEPAD_BUTTON_B -> "B";
            case GLFW_GAMEPAD_BUTTON_X -> "X";
            case GLFW_GAMEPAD_BUTTON_Y -> "Y";
            case GLFW_GAMEPAD_BUTTON_LEFT_BUMPER -> "LB";
            case GLFW_GAMEPAD_BUTTON_RIGHT_BUMPER -> "RB";
            case GLFW_GAMEPAD_BUTTON_BACK -> "Back";
            case GLFW_GAMEPAD_BUTTON_START -> "Start";
            case GLFW_GAMEPAD_BUTTON_GUIDE -> "Guide";
            case GLFW_GAMEPAD_BUTTON_LEFT_THUMB -> "L3";
            case GLFW_GAMEPAD_BUTTON_RIGHT_THUMB -> "R3";
            case GLFW_GAMEPAD_BUTTON_DPAD_UP -> "D-Up";
            case GLFW_GAMEPAD_BUTTON_DPAD_RIGHT -> "D-Right";
            case GLFW_GAMEPAD_BUTTON_DPAD_DOWN -> "D-Down";
            case GLFW_GAMEPAD_BUTTON_DPAD_LEFT -> "D-Left";
            default -> "?";
        };
    }

    /** Settings-file key names, one per row, in {@link #COUNT} order. */
    private static final String[] FILE_KEYS = {
            "pad_jump", "pad_sprint", "pad_fly_down", "pad_fly_toggle",
            "pad_inventory", "pad_debug", "pad_screenshot", "pad_forecast",
    };

    /** Appends {@code pad_xxx=button} lines for persistence (see Settings.save). */
    public void saveLines(java.util.List<String> lines) {
        for (int i = 0; i < COUNT; i++) {
            lines.add(FILE_KEYS[i] + "=" + buttons[i]);
        }
    }

    /** Applies a persisted {@code pad_xxx=button} entry; unknown keys are ignored. */
    public void loadEntry(String fileKey, String value) {
        for (int i = 0; i < COUNT; i++) {
            if (FILE_KEYS[i].equals(fileKey)) {
                try {
                    int button = Integer.parseInt(value.trim());
                    if (button >= 0 && button <= GLFW_GAMEPAD_BUTTON_LAST) {
                        buttons[i] = button;
                    }
                } catch (NumberFormatException ignored) {
                    // Malformed binding: keep the default for that row.
                }
                return;
            }
        }
    }
}
