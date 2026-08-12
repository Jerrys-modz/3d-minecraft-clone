package com.minecraftclone;

import com.minecraftclone.engine.*;
import com.minecraftclone.engine.graphics.FontAtlas;
import com.minecraftclone.engine.graphics.ItemTextures;
import com.minecraftclone.engine.graphics.TextureAtlas;
import com.minecraftclone.player.Crafting;
import com.minecraftclone.player.MiningController;
import com.minecraftclone.player.Player;
import com.minecraftclone.player.PlayerStats;
import com.minecraftclone.util.Raycaster;
import com.minecraftclone.util.ResourceLoader;
import com.minecraftclone.world.BlockType;
import com.minecraftclone.world.Mining;
import com.minecraftclone.world.World;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector3i;
import org.joml.Vector4f;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;

/**
 * Entry point: wires together the window, world, player and renderer and
 * runs the main game loop.
 * <p>
 * Controls: WASD to move, mouse to look, Space to jump, Left-Ctrl to sprint,
 * F to toggle flight, hold Left-click to break the targeted block (speed and
 * whether it's even possible depend on the selected tool - see {@link
 * com.minecraftclone.world.Mining}), Right-click to place the selected block
 * (or eat it, if it's food), C to craft the selected item from its recipe,
 * 1-9 or scroll wheel to pick a block, F3 to toggle the on-screen debug
 * overlay, Esc to open/close the settings menu (where graphics options like
 * see-through leaves live).
 */
public class Main {

    private static final float REACH_DISTANCE = 6.0f;
    private static final float NEAR_PLANE = 0.05f;
    private static final float FAR_PLANE = 400f;
    private static final float AUTOSAVE_INTERVAL_SECONDS = 60f;

    private static final BlockType[] HOTBAR = {
            BlockType.STONE, BlockType.DIRT, BlockType.GRASS, BlockType.SAND,
            BlockType.WOOD_LOG, BlockType.PLANKS, BlockType.LEAVES,
            BlockType.GRAVEL, BlockType.SNOW,
            BlockType.COAL_ORE, BlockType.IRON_ORE, BlockType.GOLD_ORE, BlockType.DIAMOND_ORE,
            BlockType.TALL_GRASS, BlockType.FLOWER_RED, BlockType.FLOWER_YELLOW, BlockType.CACTUS,
            BlockType.LAVA, BlockType.GLASS, BlockType.TORCH, BlockType.APPLE, BlockType.BERRIES,
            BlockType.STICK,
            BlockType.WOOD_PICKAXE, BlockType.STONE_PICKAXE, BlockType.IRON_PICKAXE, BlockType.DIAMOND_PICKAXE,
            BlockType.WOOD_AXE, BlockType.STONE_AXE, BlockType.IRON_AXE, BlockType.DIAMOND_AXE,
            BlockType.WOOD_SWORD, BlockType.STONE_SWORD, BlockType.IRON_SWORD, BlockType.DIAMOND_SWORD
    };

    private static final int APPLE_DROP_CHANCE = 8;    // 1 in 8 leaves broken also yield an apple
    private static final int BERRIES_PER_BUSH = 2;

    private static final Vector4f WHITE = new Vector4f(1f, 1f, 1f, 1f);

    /** Queues a transient on-screen message (rendered via {@link Hud#renderMessages}). */
    private static void showMessage(List<Hud.Message> messages, String text, Vector4f color, float duration) {
        messages.add(new Hud.Message(text, color, duration));
    }

    /** Pushes the current in-memory {@link Settings} into the world/renderer/player. */
    private void applySettings(Settings settings, World world, Player player, Window window) {
        world.setLeavesTransparent(settings.isLeavesTransparent());
        world.setRenderDistance(settings.getRenderDistance());
        window.setVsync(settings.isVsync());
        player.setMouseSensitivity(settings.getMouseSensitivity());
    }

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
        Shader hudShader = new Shader(
                ResourceLoader.loadAsString("/shaders/hud.vert"),
                ResourceLoader.loadAsString("/shaders/hud.frag"));

        TextureAtlas atlas = new TextureAtlas();
        atlas.generate();
        ItemTextures itemTextures = new ItemTextures();
        itemTextures.generate();
        FontAtlas font = new FontAtlas();
        font.generate();

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

        Hud hud = new Hud(lineShader, hudShader, font);
        List<Hud.Message> messages = new ArrayList<>();
        boolean[] showDebug = {false};
        Settings settings = new Settings();
        boolean[] menuOpen = {false};
        int[] menuSelection = {0};

        window.setCursorCaptured(true);

        // Ensure the renderer/player start consistent with the settings defaults.
        applySettings(settings, world, player, window);

        int[] selectedSlot = {0};
        Random loot = new Random();
        DayNightCycle dayNightCycle = new DayNightCycle();
        MiningController mining = new MiningController();

        System.out.println("Controls: WASD move, mouse look, Space jump, Left-Ctrl sprint, F fly toggle,");
        System.out.println("          hold Left-click to mine (speed/possibility depends on your tool),");
        System.out.println("          Right-click place (or eat, if selected item is food),");
        System.out.println("          C craft selected item, 1-9/scroll select block, F3 debug, Esc settings menu.");

        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        // Opt-in headless smoke-test mode: run a fixed number of frames, save a
        // screenshot, then exit. Used by CI / manual verification, never enabled
        // during normal play.
        boolean autoTest = System.getenv("MCCLONE_AUTOTEST") != null;
        int autoTestFrames = autoTest ? Integer.parseInt(System.getenv().getOrDefault("MCCLONE_AUTOTEST_FRAMES", "60")) : 0;
        String autoTestPath = System.getenv().getOrDefault("MCCLONE_AUTOTEST_PATH", "screenshot.png");
        if (System.getenv("MCCLONE_AUTOTEST_TIME") != null) {
            dayNightCycle.setTime(Float.parseFloat(System.getenv("MCCLONE_AUTOTEST_TIME")));
        }
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
            dayNightCycle.update(dt);

            if (input.isKeyJustPressed(GLFW_KEY_ESCAPE)) {
                menuOpen[0] = !menuOpen[0];
                window.setCursorCaptured(!menuOpen[0]);
                input.resetMouseDelta();
            }

            if (menuOpen[0]) {
                // Pause menu: navigate with arrows/WASD; toggle boolean settings or
                // step range settings with Enter/Space/Left/Right.
                if (input.isKeyJustPressed(GLFW_KEY_UP) || input.isKeyJustPressed(GLFW_KEY_W)) {
                    menuSelection[0] = Math.floorMod(menuSelection[0] - 1, Settings.ROW_COUNT);
                }
                if (input.isKeyJustPressed(GLFW_KEY_DOWN) || input.isKeyJustPressed(GLFW_KEY_S)) {
                    menuSelection[0] = Math.floorMod(menuSelection[0] + 1, Settings.ROW_COUNT);
                }
                if (input.isKeyJustPressed(GLFW_KEY_LEFT)) {
                    settings.adjust(menuSelection[0], -1);
                    applySettings(settings, world, player, window);
                }
                if (input.isKeyJustPressed(GLFW_KEY_RIGHT)) {
                    settings.adjust(menuSelection[0], +1);
                    applySettings(settings, world, player, window);
                }
                if (input.isKeyJustPressed(GLFW_KEY_ENTER) || input.isKeyJustPressed(GLFW_KEY_SPACE)) {
                    settings.adjust(menuSelection[0], +1);
                    applySettings(settings, world, player, window);
                }
            }

            if (input.isKeyJustPressed(GLFW_KEY_F3)) {
                showDebug[0] = !showDebug[0];
            }
            boolean screenshotRequested = input.isKeyJustPressed(GLFW_KEY_F2);

            if (!menuOpen[0]) {
                player.update(dt, input, world);
            }

            // Keep streaming/remeshing even with the menu open, so toggling a
            // rendering setting (e.g. see-through leaves) takes effect live.
            world.update(player.getPosition().x, player.getPosition().z);

            if (player.getStats().isDead()) {
                System.out.println("You died. Respawning...");
                showMessage(messages, "You died!", new Vector4f(0.9f, 0.25f, 0.25f, 1f), 3.0f);
                player.getInventory().clear();
                player.getDurability().reset();
                player.respawn(world, 0.5f, 0.5f);
            }

            // Age and drop expired on-screen messages (death notice, craft/tool feedback...).
            for (int i = messages.size() - 1; i >= 0; i--) {
                messages.get(i).age += dt;
                if (messages.get(i).isExpired()) {
                    messages.remove(i);
                }
            }

            // Periodically flush edits to disk so a crash doesn't lose much progress
            // (chunks are also always saved on a clean exit, see below).
            timeSinceAutosave += dt;
            if (timeSinceAutosave >= AUTOSAVE_INTERVAL_SECONDS) {
                timeSinceAutosave = 0f;
                world.saveAllModified();
            }

            Raycaster.Hit hit = null;
            float breakFraction = 0f;
            if (!menuOpen[0]) {
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

                if (input.isKeyJustPressed(GLFW_KEY_C)) {
                    BlockType selected = HOTBAR[selectedSlot[0]];
                    if (Crafting.craft(player.getInventory(), selected)) {
                        System.out.println("Crafted " + selected + " (now have " + player.getInventory().getCount(selected) + ")");
                        showMessage(messages, "Crafted " + selected, new Vector4f(0.6f, 0.9f, 0.6f, 1f), 2.5f);
                    }
                }

                hit = Raycaster.cast(world, player.getEyePosition(), player.getCamera().getFront(), REACH_DISTANCE);

                BlockType targetType = hit != null ? world.getBlock(hit.blockPos.x, hit.blockPos.y, hit.blockPos.z) : BlockType.AIR;
                BlockType heldItem = HOTBAR[selectedSlot[0]];
                boolean holding = hit != null && input.isMouseDown(GLFW_MOUSE_BUTTON_LEFT);
                breakFraction = mining.update(hit != null ? hit.blockPos : null, targetType, heldItem, holding, dt);

                if (breakFraction >= 1f) {
                    mining.reset();
                    breakFraction = 0f;
                    world.setBlock(hit.blockPos.x, hit.blockPos.y, hit.blockPos.z, BlockType.AIR);
                    if (targetType == BlockType.BERRY_BUSH) {
                        // Harvesting a bush yields berries, not the bush itself - it doesn't regrow.
                        player.getInventory().add(BlockType.BERRIES, BERRIES_PER_BUSH);
                    } else {
                        player.getInventory().add(targetType, 1);
                        if (targetType == BlockType.LEAVES && loot.nextInt(APPLE_DROP_CHANCE) == 0) {
                            player.getInventory().add(BlockType.APPLE, 1);
                        }
                    }

                    // Wear down the tool that did the breaking; once its uses run out, it's gone.
                    if (Mining.isTool(heldItem) && player.getDurability().use(heldItem)) {
                        player.getInventory().remove(heldItem, 1);
                        System.out.println("Your " + heldItem + " broke!");
                        showMessage(messages, "Your " + heldItem + " broke!",
                                new Vector4f(1f, 0.72f, 0.3f, 1f), 2.5f);
                    }
                }

                if (input.isMouseJustPressed(GLFW_MOUSE_BUTTON_RIGHT) && hit != null) {
                    if (heldItem.isEdible()) {
                        player.eat(heldItem);
                    } else if (!heldItem.isItem) {
                        // Pure inventory items (tools, and any future non-edible item)
                        // have no world tile and can never be placed as a block.
                        Vector3i p = hit.placePos;
                        if (!intersectsPlayer(player, p) && player.getInventory().remove(heldItem, 1)) {
                            world.setBlock(p.x, p.y, p.z, heldItem);
                        }
                    }
                }
            }

            // --- Render ---
            Vector3f skyColor = dayNightCycle.getSkyColor();
            window.setClearColor(skyColor.x, skyColor.y, skyColor.z, 1f);
            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

            Matrix4f projection = player.getCamera().getProjectionMatrix(settings.getFov(), window.getAspectRatio(), NEAR_PLANE, FAR_PLANE);
            Matrix4f view = player.getCamera().getViewMatrix();

            chunkShader.bind();
            chunkShader.setUniform("projection", projection);
            chunkShader.setUniform("view", view);
            chunkShader.setUniform("atlas", 0);
            chunkShader.setUniform("fogColor", skyColor);
            chunkShader.setUniform("fogStart", (world.getRenderDistance() - 2) * 16f);
            chunkShader.setUniform("fogEnd", world.getRenderDistance() * 16f);
            chunkShader.setUniform("ambientBrightness", dayNightCycle.getAmbientBrightness());
            atlas.bind();
            world.render(chunkShader);
            chunkShader.unbind();

            if (!menuOpen[0]) {
                if (hit != null) {
                    hud.renderBlockOutline(projection, view, hit.blockPos, breakFraction);
                }
                hud.renderCrosshair(window.getAspectRatio());
            }
            hud.renderHotbar(atlas, itemTextures, player.getDurability(), HOTBAR, player.getInventory(), selectedSlot[0], window.getAspectRatio());
            hud.renderStatusBars(
                    player.getStats().getHealth(), PlayerStats.MAX_HEALTH,
                    player.getStats().getHunger(), PlayerStats.MAX_HUNGER,
                    player.getStats().getStamina(), PlayerStats.MAX_STAMINA,
                    HOTBAR.length, window.getAspectRatio());
            hud.renderMessages(messages, window.getAspectRatio());
            if (showDebug[0]) {
                Vector3f pos = player.getPosition();
                float aspect = window.getAspectRatio();
                float textSize = 0.035f;
                float y = 0.95f;
                float step = 0.052f;
                hud.drawTextLeft("FPS: " + timer.getFps(), -0.95f, y, textSize, WHITE, aspect);
                hud.drawTextLeft(String.format("XYZ: %.1f / %.1f / %.1f", pos.x, pos.y, pos.z),
                        -0.95f, y - step, textSize, WHITE, aspect);
                hud.drawTextLeft("Selected: " + HOTBAR[selectedSlot[0]],
                        -0.95f, y - 2f * step, textSize, WHITE, aspect);
            }
            if (menuOpen[0]) {
                hud.renderSettingsMenu(settings, menuSelection[0], window.getAspectRatio());
            }

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
        hudShader.destroy();
        atlas.destroy();
        itemTextures.destroy();
        font.destroy();
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
