package com.minecraftclone.engine;

import static org.lwjgl.glfw.GLFW.*;

/** Polls keyboard/mouse state from GLFW and tracks per-frame "just pressed" edges and mouse deltas. */
public class Input {

    private final long windowHandle;

    private final boolean[] keysDown = new boolean[GLFW_KEY_LAST + 1];
    private final boolean[] keysDownPrev = new boolean[GLFW_KEY_LAST + 1];

    private final boolean[] mouseDown = new boolean[GLFW_MOUSE_BUTTON_LAST + 1];
    private final boolean[] mouseDownPrev = new boolean[GLFW_MOUSE_BUTTON_LAST + 1];

    private double mouseX, mouseY;
    private double lastMouseX, lastMouseY;
    private double deltaX, deltaY;
    private boolean firstMouseEvent = true;

    private double scrollDelta;

    public Input(long windowHandle) {
        this.windowHandle = windowHandle;

        glfwSetKeyCallback(windowHandle, (win, key, scancode, action, mods) -> {
            if (key < 0 || key > GLFW_KEY_LAST) return;
            if (action == GLFW_PRESS) keysDown[key] = true;
            else if (action == GLFW_RELEASE) keysDown[key] = false;
        });

        glfwSetMouseButtonCallback(windowHandle, (win, button, action, mods) -> {
            if (button < 0 || button > GLFW_MOUSE_BUTTON_LAST) return;
            if (action == GLFW_PRESS) mouseDown[button] = true;
            else if (action == GLFW_RELEASE) mouseDown[button] = false;
        });

        glfwSetCursorPosCallback(windowHandle, (win, xpos, ypos) -> {
            mouseX = xpos;
            mouseY = ypos;
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

    public boolean isKeyDown(int key) {
        return keysDown[key];
    }

    public boolean isKeyJustPressed(int key) {
        return keysDown[key] && !keysDownPrev[key];
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
