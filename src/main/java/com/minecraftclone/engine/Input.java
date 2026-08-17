package com.minecraftclone.engine;

import org.lwjgl.glfw.GLFWGamepadState;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.Arrays;

import static org.lwjgl.glfw.GLFW.*;

/**
 * Polls keyboard/mouse state from GLFW and tracks per-frame "just pressed" edges and
 * mouse deltas. Also polls the first connected gamepad (see {@link #updateGamepad}) and
 * folds it into this same keyboard/mouse state, so every existing input.isKeyDown/
 * isMouseDown call site in the game - movement, mining, placing, menus, inventory
 * drag-drop, settings sliders - picks up controller input automatically without
 * needing to know a gamepad exists at all.
 */
public class Input {

    private final long windowHandle;

    // keysDownReal/mouseDownReal hold only what the keyboard/mouse callbacks below have
    // reported - the actual hardware truth. keysDown/mouseDown are what every other
    // class in the game reads (isKeyDown, isMouseDown, and the isKeyJustPressed/
    // isMouseJustPressed edge below): each frame they're rebuilt from the real arrays
    // and then this frame's gamepad presses are OR'd back in by updateGamepad. Keeping
    // them separate is what lets a gamepad press "release" cleanly - OR-ing a virtual
    // press directly into keysDown with no real baseline to reset from each frame would
    // leave it stuck true forever once pressed, since nothing ever generates a release
    // event for a key nobody's keyboard actually holds.
    private final boolean[] keysDownReal = new boolean[GLFW_KEY_LAST + 1];
    private final boolean[] keysDown = new boolean[GLFW_KEY_LAST + 1];
    private final boolean[] keysDownPrev = new boolean[GLFW_KEY_LAST + 1];

    /** Most recently pressed key code, for capturing keybinds (reset on consume). */
    private int lastKeyPressed = GLFW_KEY_UNKNOWN;

    /** Characters typed this frame (for text fields like the world seed), reset on consume. */
    private final StringBuilder typedChars = new StringBuilder();

    private final boolean[] mouseDownReal = new boolean[GLFW_MOUSE_BUTTON_LAST + 1];
    private final boolean[] mouseDown = new boolean[GLFW_MOUSE_BUTTON_LAST + 1];
    private final boolean[] mouseDownPrev = new boolean[GLFW_MOUSE_BUTTON_LAST + 1];

    private double mouseX, mouseY;
    private double lastMouseX, lastMouseY;
    private double deltaX, deltaY;
    private boolean firstMouseEvent = true;

    private double scrollDelta;

    // Gamepad (see updateGamepad): a moderate deadzone keeps stick drift/noise near
    // center from registering as movement/look, and analog triggers report a smooth
    // -1 (released) to 1 (pressed) range that's normalized to 0..1 and thresholded to
    // behave like a digital mouse click for mining/placing.
    private static final float STICK_DEADZONE = 0.22f;
    private static final float TRIGGER_PRESS_THRESHOLD = 0.5f;
    private static final float LOOK_SPEED_PX_PER_SEC = 900f; // gamepad look, expressed as an equivalent mouse-pixel rate so it goes through the same sensitivity setting as the mouse
    private static final float CURSOR_SPEED_PX_PER_SEC = 900f; // virtual OS-cursor speed while navigating a GUI with the right stick
    private final GLFWGamepadState gamepadState = GLFWGamepadState.malloc();
    private final boolean[] gamepadButtonsDown = new boolean[GLFW_GAMEPAD_BUTTON_LAST + 1];
    private final boolean[] gamepadButtonsPrev = new boolean[GLFW_GAMEPAD_BUTTON_LAST + 1];

    public Input(long windowHandle) {
        this.windowHandle = windowHandle;

        glfwSetKeyCallback(windowHandle, (win, key, scancode, action, mods) -> {
            if (key < 0 || key > GLFW_KEY_LAST) return;
            if (action == GLFW_PRESS) {
                keysDownReal[key] = true;
                lastKeyPressed = key;
            } else if (action == GLFW_RELEASE) {
                keysDownReal[key] = false;
            }
        });

        glfwSetMouseButtonCallback(windowHandle, (win, button, action, mods) -> {
            if (button < 0 || button > GLFW_MOUSE_BUTTON_LAST) return;
            if (action == GLFW_PRESS) mouseDownReal[button] = true;
            else if (action == GLFW_RELEASE) mouseDownReal[button] = false;
        });

        glfwSetCursorPosCallback(windowHandle, (win, xpos, ypos) -> {
            mouseX = xpos;
            mouseY = ypos;
        });

        glfwSetCharCallback(windowHandle, (win, codepoint) -> {
            typedChars.append((char) codepoint);
        });

        glfwSetScrollCallback(windowHandle, (win, xoffset, yoffset) -> scrollDelta += yoffset);
    }

    /**
     * Snapshot "previous" edge state and clear the per-frame scroll delta.
     * Must run at the very start of the frame, before glfwPollEvents.
     */
    public void beginFrame() {
        System.arraycopy(keysDown, 0, keysDownPrev, 0, keysDown.length);
        System.arraycopy(mouseDown, 0, mouseDownPrev, 0, mouseDown.length);
        scrollDelta = 0;
    }

    /** Call once per frame, after polling events, before reading "just pressed" state. */
    public void update() {
        if (firstMouseEvent) {
            lastMouseX = mouseX;
            lastMouseY = mouseY;
            firstMouseEvent = false;
        }
        deltaX = mouseX - lastMouseX;
        deltaY = mouseY - lastMouseY;
        lastMouseX = mouseX;
        lastMouseY = mouseY;
    }

    /**
     * Polls the first connected gamepad (if any) and folds it into this frame's
     * keyboard/mouse state. Call once per frame, after {@link #update()} (so the
     * real mouse delta is already computed) and after {@code dt} is known. A no-op
     * if nothing's connected.
     * <p>
     * While the cursor is captured (gameplay - see Window#setCursorCaptured), the
     * left stick becomes movement (mapped through {@code keyBinds}, so a rebound
     * layout still works), the right stick turns the camera, and the triggers
     * become the mining/placing mouse buttons. While the cursor is free (any menu,
     * the inventory, settings, a chest/furnace GUI...) the right stick instead
     * drives the real OS cursor position, so every existing mouse-driven screen in
     * the game works from a pad with no changes of its own.
     */
    public void updateGamepad(float dt, KeyBindings keyBinds) {
        // Rebuild this frame's exposed state from the real keyboard/mouse baseline
        // first - see the keysDownReal/keysDown split above for why this can't just
        // OR gamepad presses into keysDown directly.
        System.arraycopy(keysDownReal, 0, keysDown, 0, keysDown.length);
        System.arraycopy(mouseDownReal, 0, mouseDown, 0, mouseDown.length);

        int jid = findGamepad();
        if (jid < 0 || !glfwGetGamepadState(jid, gamepadState)) {
            Arrays.fill(gamepadButtonsDown, false);
            Arrays.fill(gamepadButtonsPrev, false);
            return;
        }

        ByteBuffer buttons = gamepadState.buttons();
        for (int i = 0; i < gamepadButtonsDown.length; i++) {
            gamepadButtonsDown[i] = buttons.get(i) == GLFW_PRESS;
        }

        FloatBuffer axes = gamepadState.axes();
        float leftX = applyDeadzone(axes.get(GLFW_GAMEPAD_AXIS_LEFT_X), STICK_DEADZONE);
        float leftY = applyDeadzone(axes.get(GLFW_GAMEPAD_AXIS_LEFT_Y), STICK_DEADZONE);
        float rightX = applyDeadzone(axes.get(GLFW_GAMEPAD_AXIS_RIGHT_X), STICK_DEADZONE);
        float rightY = applyDeadzone(axes.get(GLFW_GAMEPAD_AXIS_RIGHT_Y), STICK_DEADZONE);
        float leftTrigger = normalizeTrigger(axes.get(GLFW_GAMEPAD_AXIS_LEFT_TRIGGER));
        float rightTrigger = normalizeTrigger(axes.get(GLFW_GAMEPAD_AXIS_RIGHT_TRIGGER));

        boolean captured = glfwGetInputMode(windowHandle, GLFW_CURSOR) == GLFW_CURSOR_DISABLED;
        if (captured) {
            // Left stick -> movement, driven through the *current* bindings rather
            // than hardcoded WASD, so a rebound layout still works from a pad.
            mergeKey(keyBinds.get(KeyBindings.FORWARD), leftY < 0);
            mergeKey(keyBinds.get(KeyBindings.BACK), leftY > 0);
            mergeKey(keyBinds.get(KeyBindings.LEFT), leftX < 0);
            mergeKey(keyBinds.get(KeyBindings.RIGHT), leftX > 0);

            mergeKey(keyBinds.get(KeyBindings.JUMP), gamepadDown(GLFW_GAMEPAD_BUTTON_A));
            mergeKey(keyBinds.get(KeyBindings.SPRINT), gamepadDown(GLFW_GAMEPAD_BUTTON_LEFT_THUMB));
            mergeKey(keyBinds.get(KeyBindings.FLY_DOWN), gamepadDown(GLFW_GAMEPAD_BUTTON_B));
            mergeKey(keyBinds.get(KeyBindings.FLY_TOGGLE), gamepadJustPressed(GLFW_GAMEPAD_BUTTON_X));
            mergeKey(keyBinds.get(KeyBindings.INVENTORY), gamepadJustPressed(GLFW_GAMEPAD_BUTTON_Y));
            mergeKey(GLFW_KEY_ESCAPE, gamepadJustPressed(GLFW_GAMEPAD_BUTTON_START));

            // Triggers -> the same mouse buttons mining/placing already read.
            mergeMouseButton(GLFW_MOUSE_BUTTON_LEFT, leftTrigger > TRIGGER_PRESS_THRESHOLD);
            mergeMouseButton(GLFW_MOUSE_BUTTON_RIGHT, rightTrigger > TRIGGER_PRESS_THRESHOLD);

            // Bumpers (and the d-pad, redundantly, for a physical-feeling option on
            // Deck) cycle the hotbar the same way a scroll notch does.
            if (gamepadJustPressed(GLFW_GAMEPAD_BUTTON_LEFT_BUMPER) || gamepadJustPressed(GLFW_GAMEPAD_BUTTON_DPAD_LEFT)) {
                scrollDelta -= 1;
            }
            if (gamepadJustPressed(GLFW_GAMEPAD_BUTTON_RIGHT_BUMPER) || gamepadJustPressed(GLFW_GAMEPAD_BUTTON_DPAD_RIGHT)) {
                scrollDelta += 1;
            }

            // Right stick -> camera look, folded straight into this frame's mouse
            // delta so Player's existing mouse-look math (dx/dy * sensitivity)
            // drives the camera identically regardless of where the delta came from.
            deltaX += rightX * LOOK_SPEED_PX_PER_SEC * dt;
            deltaY += rightY * LOOK_SPEED_PX_PER_SEC * dt;
        } else {
            // A menu/inventory/GUI is open: right stick drives the real cursor
            // instead, so every existing mouse-driven screen just works.
            moveCursor(rightX, rightY, dt);
            mergeMouseButton(GLFW_MOUSE_BUTTON_LEFT, gamepadDown(GLFW_GAMEPAD_BUTTON_A));
            mergeMouseButton(GLFW_MOUSE_BUTTON_RIGHT, gamepadDown(GLFW_GAMEPAD_BUTTON_X));
            mergeKey(GLFW_KEY_ESCAPE, gamepadJustPressed(GLFW_GAMEPAD_BUTTON_B) || gamepadJustPressed(GLFW_GAMEPAD_BUTTON_START));
        }

        System.arraycopy(gamepadButtonsDown, 0, gamepadButtonsPrev, 0, gamepadButtonsDown.length);
    }

    /** True if a usable gamepad is currently connected - for a HUD/debug indicator. */
    public boolean isGamepadConnected() {
        return findGamepad() >= 0;
    }

    /** The connected gamepad's name (per its SDL mapping), or null if none is connected. */
    public String getGamepadName() {
        int jid = findGamepad();
        return jid >= 0 ? glfwGetGamepadName(jid) : null;
    }

    private static int findGamepad() {
        for (int jid = GLFW_JOYSTICK_1; jid <= GLFW_JOYSTICK_LAST; jid++) {
            if (glfwJoystickPresent(jid) && glfwJoystickIsGamepad(jid)) {
                return jid;
            }
        }
        return -1;
    }

    private boolean gamepadDown(int button) {
        return gamepadButtonsDown[button];
    }

    private boolean gamepadJustPressed(int button) {
        return gamepadButtonsDown[button] && !gamepadButtonsPrev[button];
    }

    /** OR's a virtual press into this frame's key state; never clears one (see the keysDownReal split above). */
    private void mergeKey(int key, boolean pressed) {
        if (pressed && key >= 0 && key <= GLFW_KEY_LAST) {
            keysDown[key] = true;
        }
    }

    private void mergeMouseButton(int button, boolean pressed) {
        if (pressed) {
            mouseDown[button] = true;
        }
    }

    /** Nudges the real OS cursor by a stick-driven velocity, clamped to the window - drives GUI navigation. */
    private void moveCursor(float dx, float dy, float dt) {
        if (dx == 0f && dy == 0f) return;
        int[] w = {0}, h = {0};
        glfwGetWindowSize(windowHandle, w, h);
        mouseX = clamp(mouseX + dx * CURSOR_SPEED_PX_PER_SEC * dt, 0, w[0]);
        mouseY = clamp(mouseY + dy * CURSOR_SPEED_PX_PER_SEC * dt, 0, h[0]);
        glfwSetCursorPos(windowHandle, mouseX, mouseY);
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    /** Zeroes out anything inside {@code deadzone} (stick drift/noise near center) rather than reporting tiny false input. */
    static float applyDeadzone(float value, float deadzone) {
        return Math.abs(value) < deadzone ? 0f : value;
    }

    /** Maps a GLFW trigger axis's -1 (released)..1 (fully pressed) range to a plain 0..1 range. */
    static float normalizeTrigger(float raw) {
        return Math.max(0f, Math.min(1f, (raw + 1f) / 2f));
    }

    public boolean isKeyDown(int key) {
        return keysDown[key];
    }

    public boolean isKeyJustPressed(int key) {
        return keysDown[key] && !keysDownPrev[key];
    }

    /** Returns the most recently pressed key (for keybind capture) and clears it. */
    public int consumeLastKeyPressed() {
        int key = lastKeyPressed;
        lastKeyPressed = GLFW_KEY_UNKNOWN;
        return key;
    }

    /** Returns and clears any characters typed since the last consume. */
    public String consumeTypedChars() {
        String s = typedChars.toString();
        typedChars.setLength(0);
        return s;
    }


    public boolean isMouseDown(int button) {
        return mouseDown[button];
    }

    public boolean isMouseJustPressed(int button) {
        return mouseDown[button] && !mouseDownPrev[button];
    }

    public double getMouseX() {
        return mouseX;
    }

    public double getMouseY() {
        return mouseY;
    }

    public double getDeltaX() {
        return deltaX;
    }

    public double getDeltaY() {
        return deltaY;
    }

    public double getScrollDelta() {
        return scrollDelta;
    }

    public void resetMouseDelta() {
        deltaX = 0;
        deltaY = 0;
    }
}
