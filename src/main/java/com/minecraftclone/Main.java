package com.minecraftclone;

import com.minecraftclone.engine.*;
import com.minecraftclone.engine.graphics.TextureAtlas;
import com.minecraftclone.player.Player;
import com.minecraftclone.util.Raycaster;
import com.minecraftclone.util.ResourceLoader;
import com.minecraftclone.world.BlockType;
import com.minecraftclone.world.World;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector3i;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;

/**
 * Entry point: wires together the window, world, player and renderer and
 * runs the main game loop.
 * <p>
 * Controls: WASD to move, mouse to look, Space to jump, Left-Ctrl to sprint,
 * F to toggle flight, Left-click to break a block, Right-click to place the
 * selected block, 1-9 or scroll wheel to pick a block, Esc to release the
 * mouse cursor.
 */
public class Main {

    private static final float REACH_DISTANCE = 6.0f;
    private static final float FOV_DEGREES = 75f;
    private static final float NEAR_PLANE = 0.05f;
    private static final float FAR_PLANE = 400f;
    private static final float AUTOSAVE_INTERVAL_SECONDS = 60f;

    private static final BlockType[] HOTBAR = {
            BlockType.STONE, BlockType.DIRT, BlockType.GRASS, BlockType.SAND,
            BlockType.WOOD_LOG, BlockType.PLANKS, BlockType.LEAVES,
            BlockType.GRAVEL, BlockType.SNOW
    };

    public static void main(String[] args) {
        new Main().run();
    }

    private void run() {
        Window window = new Window("3D Minecraft Clone", 1280, 720);
        window.init();

        Input input = new Input(window.getHandle());
        Timer timer = new Timer();
        timer.init();

        Shader chunkShader = new Shader(
                ResourceLoader.loadAsString("/shaders/chunk.vert"),
                ResourceLoader.loadAsString("/shaders/chunk.frag"));
        Shader lineShader = new Shader(
                ResourceLoader.loadAsString("/shaders/line.vert"),
                ResourceLoader.loadAsString("/shaders/line.frag"));

        TextureAtlas atlas = new TextureAtlas();
        atlas.generate();

        Path saveDir = Paths.get(System.getenv().getOrDefault("MCCLONE_SAVE_DIR", "saves/world"));
        long seed = loadOrCreateSeed(saveDir);
        System.out.println("World seed: " + seed + " (save directory: " + saveDir.toAbsolutePath() + ")");
        World world = new World(seed, atlas, saveDir);
        world.setRenderDistance(6);

        // Warm up: generate/mesh the spawn area synchronously before the player drops in,
        // so they don't fall through an empty world.
        for (int i = 0; i < 200; i++) {
            world.update(0, 0);
        }

        Player player = new Player();
        player.spawn(world, 0.5f, 0.5f);

        Hud hud = new Hud(lineShader);

        window.setCursorCaptured(true);
        boolean[] cursorCaptured = {true};

        int[] selectedSlot = {0};

        Vector3f fogColor = new Vector3f(0.53f, 0.81f, 0.92f);

        System.out.println("Controls: WASD move, mouse look, Space jump, Left-Ctrl sprint, F fly toggle,");
        System.out.println("          Left-click break, Right-click place, 1-9/scroll select block, Esc release mouse.");

        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        // Opt-in headless smoke-test mode: run a fixed number of frames, save a
        // screenshot, then exit. Used by CI / manual verification, never enabled
        // during normal play.
        boolean autoTest = System.getenv("MCCLONE_AUTOTEST") != null;
        int autoTestFrames = autoTest ? Integer.parseInt(System.getenv().getOrDefault("MCCLONE_AUTOTEST_FRAMES", "60")) : 0;
        String autoTestPath = System.getenv().getOrDefault("MCCLONE_AUTOTEST_PATH", "screenshot.png");
        int frameCount = 0;
        float timeSinceAutosave = 0f;

        while (!window.shouldClose()) {
            input.beginFrame();
            window.pollEvents();
            input.update();

            if (window.isResized()) {
                glViewport(0, 0, window.getWidth(), window.getHeight());
                window.setResized(false);
            }

            float dt = timer.getDeltaTime();
            timer.updateFps(dt);

            if (input.isKeyJustPressed(GLFW_KEY_ESCAPE)) {
                cursorCaptured[0] = !cursorCaptured[0];
                window.setCursorCaptured(cursorCaptured[0]);
                input.resetMouseDelta();
            }
            boolean screenshotRequested = input.isKeyJustPressed(GLFW_KEY_F2);

            if (cursorCaptured[0]) {
                player.update(dt, input, world);
            }

            world.update(player.getPosition().x, player.getPosition().z);

            // Periodically flush edits to disk so a crash doesn't lose much progress
            // (chunks are also always saved on a clean exit, see below).
            timeSinceAutosave += dt;
            if (timeSinceAutosave >= AUTOSAVE_INTERVAL_SECONDS) {
                timeSinceAutosave = 0f;
                world.saveAllModified();
            }

            // Block selection via number keys.
            for (int i = 0; i < HOTBAR.length && i < 9; i++) {
                if (input.isKeyJustPressed(GLFW_KEY_1 + i)) {
                    selectedSlot[0] = i;
                }
            }
            double scroll = input.getScrollDelta();
            if (scroll != 0) {
                selectedSlot[0] = Math.floorMod(selectedSlot[0] - (int) Math.signum(scroll), HOTBAR.length);
            }

            Raycaster.Hit hit = null;
            if (cursorCaptured[0]) {
                hit = Raycaster.cast(world, player.getEyePosition(), player.getCamera().getFront(), REACH_DISTANCE);

                if (input.isMouseJustPressed(GLFW_MOUSE_BUTTON_LEFT) && hit != null) {
                    world.setBlock(hit.blockPos.x, hit.blockPos.y, hit.blockPos.z, BlockType.AIR);
                }
                if (input.isMouseJustPressed(GLFW_MOUSE_BUTTON_RIGHT) && hit != null) {
                    Vector3i p = hit.placePos;
                    if (!intersectsPlayer(player, p)) {
                        world.setBlock(p.x, p.y, p.z, HOTBAR[selectedSlot[0]]);
                    }
                }
            }

            // --- Render ---
            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

            Matrix4f projection = player.getCamera().getProjectionMatrix(FOV_DEGREES, window.getAspectRatio(), NEAR_PLANE, FAR_PLANE);
            Matrix4f view = player.getCamera().getViewMatrix();

            chunkShader.bind();
            chunkShader.setUniform("projection", projection);
            chunkShader.setUniform("view", view);
            chunkShader.setUniform("atlas", 0);
            chunkShader.setUniform("fogColor", fogColor);
            chunkShader.setUniform("fogStart", (world.getRenderDistance() - 2) * 16f);
            chunkShader.setUniform("fogEnd", world.getRenderDistance() * 16f);
            atlas.bind();
            world.render(chunkShader);
            chunkShader.unbind();

            if (hit != null) {
                hud.renderBlockOutline(projection, view, hit.blockPos);
            }
            hud.renderCrosshair(window.getAspectRatio());

            frameCount++;
            boolean autoTestShot = autoTest && frameCount >= autoTestFrames;
            if (screenshotRequested) {
                Screenshot.capture(window.getWidth(), window.getHeight(), "screenshot.png");
            }
            if (autoTestShot) {
                Screenshot.capture(window.getWidth(), window.getHeight(), autoTestPath);
            }

            window.swapBuffers();

            if (autoTestShot) {
                glfwSetWindowShouldClose(window.getHandle(), true);
            }
        }

        world.saveAllModified();

        hud.destroy();
        chunkShader.destroy();
        lineShader.destroy();
        atlas.destroy();
        world.destroy();
        window.close();
    }

    /** Reuses the seed from a previous run if this save directory already has one, otherwise mints and stores a new one. */
    private static long loadOrCreateSeed(Path saveDir) {
        Path seedFile = saveDir.resolve("seed.txt");
        try {
            if (Files.isRegularFile(seedFile)) {
                return Long.parseLong(Files.readString(seedFile, StandardCharsets.UTF_8).trim());
            }
            Files.createDirectories(saveDir);
            long seed = System.currentTimeMillis();
            Files.writeString(seedFile, Long.toString(seed), StandardCharsets.UTF_8);
            return seed;
        } catch (IOException | NumberFormatException e) {
            System.err.println("Could not read/write seed file (" + e.getMessage() + "), using a fresh in-memory seed.");
            return System.currentTimeMillis();
        }
    }

    private boolean intersectsPlayer(Player player, Vector3i blockPos) {
        Vector3f pos = player.getPosition();
        float hw = 0.3f;
        float minX = pos.x - hw, maxX = pos.x + hw;
        float minY = pos.y, maxY = pos.y + 1.8f;
        float minZ = pos.z - hw, maxZ = pos.z + hw;
        return blockPos.x < maxX && blockPos.x + 1 > minX
                && blockPos.y < maxY && blockPos.y + 1 > minY
                && blockPos.z < maxZ && blockPos.z + 1 > minZ;
    }
}
