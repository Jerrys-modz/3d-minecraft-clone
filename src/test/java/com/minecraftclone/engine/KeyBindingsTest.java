package com.minecraftclone.engine;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_A;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_D;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_F2;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_F3;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_CONTROL;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_SHIFT;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_S;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_SPACE;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_UP;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_W;

class KeyBindingsTest {

    @Test
    void defaultsMatchExpectedKeys() {
        KeyBindings kb = new KeyBindings();
        assertEquals(GLFW_KEY_W, kb.get(KeyBindings.FORWARD));
        assertEquals(GLFW_KEY_S, kb.get(KeyBindings.BACK));
        assertEquals(GLFW_KEY_A, kb.get(KeyBindings.LEFT));
        assertEquals(GLFW_KEY_D, kb.get(KeyBindings.RIGHT));
        assertEquals(GLFW_KEY_SPACE, kb.get(KeyBindings.JUMP));
        assertEquals(GLFW_KEY_LEFT_CONTROL, kb.get(KeyBindings.SPRINT));
        assertEquals(GLFW_KEY_LEFT_SHIFT, kb.get(KeyBindings.FLY_DOWN));
        assertEquals(GLFW_KEY_F3, kb.get(KeyBindings.DEBUG));
        assertEquals(GLFW_KEY_F2, kb.get(KeyBindings.SCREENSHOT));
    }

    @Test
    void setAndGet() {
        KeyBindings kb = new KeyBindings();
        kb.set(KeyBindings.FORWARD, GLFW_KEY_UP);
        assertEquals(GLFW_KEY_UP, kb.get(KeyBindings.FORWARD));
        // Unrelated actions are untouched.
        assertEquals(GLFW_KEY_S, kb.get(KeyBindings.BACK));
    }

    @Test
    void resetRestoresDefaults() {
        KeyBindings kb = new KeyBindings();
        kb.set(KeyBindings.FORWARD, GLFW_KEY_UP);
        kb.reset();
        assertEquals(GLFW_KEY_W, kb.get(KeyBindings.FORWARD));
    }

    @Test
    void keyNamesCoverLettersSpecialKeysAndFunctionKeys() {
        assertEquals("W", KeyBindings.keyName(GLFW_KEY_W));
        assertEquals("Space", KeyBindings.keyName(GLFW_KEY_SPACE));
        assertEquals("L Ctrl", KeyBindings.keyName(GLFW_KEY_LEFT_CONTROL));
        assertEquals("L Shift", KeyBindings.keyName(GLFW_KEY_LEFT_SHIFT));
        assertEquals("F3", KeyBindings.keyName(GLFW_KEY_F3));
        assertEquals("Up", KeyBindings.keyName(GLFW_KEY_UP));
    }

    @Test
    void modifierKeysAreExcludedFromRebinding() {
        assertTrue(KeyBindings.isModifierKey(GLFW_KEY_LEFT_SHIFT));
        assertTrue(KeyBindings.isModifierKey(GLFW_KEY_LEFT_CONTROL));
        assertFalse(KeyBindings.isModifierKey(GLFW_KEY_W));
        assertFalse(KeyBindings.isModifierKey(GLFW_KEY_SPACE));
    }

    @Test
    void saveAndLoadRoundTrip() {
        KeyBindings kb = new KeyBindings();
        kb.set(KeyBindings.FORWARD, GLFW_KEY_UP);
        kb.set(KeyBindings.SCREENSHOT, GLFW_KEY_LEFT_SHIFT);
        List<String> lines = new ArrayList<>();
        kb.saveLines(lines);

        KeyBindings loaded = new KeyBindings();
        for (String line : lines) {
            int eq = line.indexOf('=');
            loaded.loadEntry(line.substring(0, eq), line.substring(eq + 1));
        }
        assertEquals(GLFW_KEY_UP, loaded.get(KeyBindings.FORWARD));
        assertEquals(GLFW_KEY_LEFT_SHIFT, loaded.get(KeyBindings.SCREENSHOT));
        // Defaults preserved for untouched actions.
        assertEquals(GLFW_KEY_S, loaded.get(KeyBindings.BACK));
    }

    @Test
    void malformedEntryKeepsDefault() {
        KeyBindings kb = new KeyBindings();
        kb.loadEntry("key_forward", "not-a-number");
        assertEquals(GLFW_KEY_W, kb.get(KeyBindings.FORWARD));
        kb.loadEntry("unknown_key", "123");
        assertEquals(GLFW_KEY_W, kb.get(KeyBindings.FORWARD));
    }
}
