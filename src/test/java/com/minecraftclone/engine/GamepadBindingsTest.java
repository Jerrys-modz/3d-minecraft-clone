package com.minecraftclone.engine;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.lwjgl.glfw.GLFW.GLFW_GAMEPAD_BUTTON_A;
import static org.lwjgl.glfw.GLFW.GLFW_GAMEPAD_BUTTON_B;
import static org.lwjgl.glfw.GLFW.GLFW_GAMEPAD_BUTTON_BACK;
import static org.lwjgl.glfw.GLFW.GLFW_GAMEPAD_BUTTON_DPAD_UP;
import static org.lwjgl.glfw.GLFW.GLFW_GAMEPAD_BUTTON_LEFT_THUMB;
import static org.lwjgl.glfw.GLFW.GLFW_GAMEPAD_BUTTON_RIGHT_THUMB;
import static org.lwjgl.glfw.GLFW.GLFW_GAMEPAD_BUTTON_X;
import static org.lwjgl.glfw.GLFW.GLFW_GAMEPAD_BUTTON_Y;

class GamepadBindingsTest {

    @Test
    void defaultsMatchExpectedButtons() {
        GamepadBindings gb = new GamepadBindings();
        assertEquals(GLFW_GAMEPAD_BUTTON_A, gb.buttonFor(KeyBindings.JUMP));
        assertEquals(GLFW_GAMEPAD_BUTTON_LEFT_THUMB, gb.buttonFor(KeyBindings.SPRINT));
        assertEquals(GLFW_GAMEPAD_BUTTON_B, gb.buttonFor(KeyBindings.FLY_DOWN));
        assertEquals(GLFW_GAMEPAD_BUTTON_X, gb.buttonFor(KeyBindings.FLY_TOGGLE));
        assertEquals(GLFW_GAMEPAD_BUTTON_Y, gb.buttonFor(KeyBindings.INVENTORY));
    }

    @Test
    void movementHasNoGamepadRow() {
        // There's only one stick to use for it - not a remappable button action.
        GamepadBindings gb = new GamepadBindings();
        assertEquals(-1, gb.buttonFor(KeyBindings.FORWARD));
        assertEquals(-1, gb.buttonFor(KeyBindings.BACK));
        assertEquals(-1, gb.buttonFor(KeyBindings.LEFT));
        assertEquals(-1, gb.buttonFor(KeyBindings.RIGHT));
    }

    @Test
    void setAndGet() {
        GamepadBindings gb = new GamepadBindings();
        gb.set(0, GLFW_GAMEPAD_BUTTON_Y); // row 0 is JUMP - see GamepadBindings' ACTIONS order
        assertEquals(GLFW_GAMEPAD_BUTTON_Y, gb.buttonFor(KeyBindings.JUMP));
        // Unrelated rows are untouched.
        assertEquals(GLFW_GAMEPAD_BUTTON_LEFT_THUMB, gb.buttonFor(KeyBindings.SPRINT));
    }

    @Test
    void resetRestoresDefaults() {
        GamepadBindings gb = new GamepadBindings();
        gb.set(0, GLFW_GAMEPAD_BUTTON_Y);
        gb.reset();
        assertEquals(GLFW_GAMEPAD_BUTTON_A, gb.buttonFor(KeyBindings.JUMP));
    }

    @Test
    void namesShareKeyBindingsDisplayNames() {
        // Row 0 (JUMP) should read the same as the keyboard tab's own label.
        assertEquals(KeyBindings.name(KeyBindings.JUMP), GamepadBindings.name(0));
    }

    @Test
    void buttonNamesCoverFaceBumperStickAndDpadButtons() {
        assertEquals("A", GamepadBindings.buttonName(GLFW_GAMEPAD_BUTTON_A));
        assertEquals("Y", GamepadBindings.buttonName(GLFW_GAMEPAD_BUTTON_Y));
        assertEquals("Back", GamepadBindings.buttonName(GLFW_GAMEPAD_BUTTON_BACK));
        assertEquals("L3", GamepadBindings.buttonName(GLFW_GAMEPAD_BUTTON_LEFT_THUMB));
        assertEquals("R3", GamepadBindings.buttonName(GLFW_GAMEPAD_BUTTON_RIGHT_THUMB));
        assertEquals("D-Up", GamepadBindings.buttonName(GLFW_GAMEPAD_BUTTON_DPAD_UP));
    }

    @Test
    void saveAndLoadRoundTrip() {
        GamepadBindings gb = new GamepadBindings();
        gb.set(0, GLFW_GAMEPAD_BUTTON_Y); // JUMP -> Y
        gb.set(4, GLFW_GAMEPAD_BUTTON_BACK); // INVENTORY -> Back
        List<String> lines = new ArrayList<>();
        gb.saveLines(lines);

        GamepadBindings loaded = new GamepadBindings();
        for (String line : lines) {
            int eq = line.indexOf('=');
            loaded.loadEntry(line.substring(0, eq), line.substring(eq + 1));
        }
        assertEquals(GLFW_GAMEPAD_BUTTON_Y, loaded.buttonFor(KeyBindings.JUMP));
        assertEquals(GLFW_GAMEPAD_BUTTON_BACK, loaded.buttonFor(KeyBindings.INVENTORY));
        // Defaults preserved for untouched rows.
        assertEquals(GLFW_GAMEPAD_BUTTON_LEFT_THUMB, loaded.buttonFor(KeyBindings.SPRINT));
    }

    @Test
    void malformedEntryKeepsDefault() {
        GamepadBindings gb = new GamepadBindings();
        gb.loadEntry("pad_jump", "not-a-number");
        assertEquals(GLFW_GAMEPAD_BUTTON_A, gb.buttonFor(KeyBindings.JUMP));
        gb.loadEntry("pad_jump", "999"); // out of the valid GLFW_GAMEPAD_BUTTON_* range
        assertEquals(GLFW_GAMEPAD_BUTTON_A, gb.buttonFor(KeyBindings.JUMP));
        gb.loadEntry("unknown_key", "0");
        assertEquals(GLFW_GAMEPAD_BUTTON_A, gb.buttonFor(KeyBindings.JUMP));
    }

    @Test
    void everyDefaultButtonIsDistinct() {
        // Not a hard requirement (nothing stops a user from rebinding two
        // actions onto the same button - same as KeyBindings), but the
        // defaults themselves should give every action its own button so nothing
        // double-fires out of the box.
        GamepadBindings gb = new GamepadBindings();
        java.util.Set<Integer> seen = new java.util.HashSet<>();
        for (int i = 0; i < GamepadBindings.COUNT; i++) {
            assertTrue(seen.add(gb.get(i)), "default button collision at row " + i);
        }
    }
}
