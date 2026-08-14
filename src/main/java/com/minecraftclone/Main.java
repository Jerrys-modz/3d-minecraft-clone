package com.minecraftclone;

import com.minecraftclone.engine.*;
import com.minecraftclone.engine.graphics.FontAtlas;
import com.minecraftclone.engine.graphics.ItemRenderer;
import com.minecraftclone.engine.graphics.ItemTextures;
import com.minecraftclone.engine.graphics.MobRenderer;
import com.minecraftclone.engine.graphics.MobTextures;
import com.minecraftclone.engine.graphics.SkyRenderer;
import com.minecraftclone.engine.graphics.TextureAtlas;
import com.minecraftclone.player.CraftingGrid;
import com.minecraftclone.player.CreativeCatalog;
import com.minecraftclone.player.Inventory;
import com.minecraftclone.player.InventoryController;
import com.minecraftclone.player.MiningController;
import com.minecraftclone.player.Player;
import com.minecraftclone.player.PlayerStats;
import com.minecraftclone.player.Smelting;
import com.minecraftclone.util.AABB;
import com.minecraftclone.util.Raycaster;
import com.minecraftclone.util.ResourceLoader;
import com.minecraftclone.world.BlockType;
import com.minecraftclone.world.Mining;
import com.minecraftclone.world.Mob;
import com.minecraftclone.world.World;
import com.minecraftclone.world.gen.TerrainGenerator;
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
import java.util.Locale;
import java.util.Random;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;

/**
 * Entry point: wires together the window, world, player and renderer and
 * runs the main game loop.
 * <p>
 * Controls: WASD to move, mouse to look, Space to jump, Left-Ctrl (or double-tap
 * W) to sprint, F to toggle flight (double-tap W also takes off in creative and,
 * once already flying, boosts flight speed instead - only F lands you again),
 * hold Left-click to break the targeted block (speed and whether it's even
 * possible depend on the selected tool - see {@link com.minecraftclone.world.Mining} - creative instead
 * breaks one block per click), Right-click to place the selected block
 * (or eat it, if it's food), C to smelt the selected ore (aim at a furnace),
 * E to open the inventory (click/drag items, craft on the 3x3 grid),
 * 1-9 or scroll wheel to pick a hotbar slot, F3 to toggle the on-screen debug
 * overlay, Esc to open/close the settings menu (where graphics options like
 * see-through leaves live).
 */
public class Main {

    private static final float REACH_DISTANCE = 6.0f;
    private static final float NEAR_PLANE = 0.05f;
    private static final float FAR_PLANE = 400f;
    private static final float AUTOSAVE_INTERVAL_SECONDS = 60f;

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
        player.setGameMode(settings.getGameMode());
    }

    /** Closes the inventory screen, returning any cursor/grid items to the inventory. */
    private void closeInventory(InventoryController controller, boolean[] inventoryOpen) {
        controller.returnGridToInventory();
        controller.returnCursorToInventory();
        inventoryOpen[0] = false;
    }

    /** Closes the creative screen, returning any cursor item to the inventory. */
    private void closeCreative(InventoryController controller, boolean[] creativeOpen) {
        controller.returnCursorToInventory();
        creativeOpen[0] = false;
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
        Shader skyShader = new Shader(
                ResourceLoader.loadAsString("/shaders/sky.vert"),
                ResourceLoader.loadAsString("/shaders/sky.frag"));
        SkyRenderer skyRenderer = new SkyRenderer();

        TextureAtlas atlas = new TextureAtlas();
        atlas.generate();
        ItemTextures itemTextures = new ItemTextures();
        itemTextures.generate();
        MobTextures mobTextures = new MobTextures();
        mobTextures.generate();
        FontAtlas font = new FontAtlas();
        font.generate();

        Path saveDir = Paths.get(System.getenv().getOrDefault("MCCLONE_SAVE_DIR", "saves/world"));
        Path settingsFile = saveDir.resolve("settings.txt");
        long seed = loadOrCreateSeed(saveDir);
        Settings settings = Settings.load(settingsFile);
        System.out.println("World seed: " + seed + " (save directory: " + saveDir.toAbsolutePath() + ")");
        World world = new World(seed, atlas, saveDir);
        world.setRenderDistance(settings.getRenderDistance());
        world.setLeavesTransparent(settings.isLeavesTransparent());

        // Warm up: generate/mesh the spawn area synchronously before the player drops in,
        // so they don't fall through an empty world.
        for (int i = 0; i < 200; i++) {
            world.update(0, 0);
        }

        Player player = new Player();
        // Land on a sensible spawn: the origin itself may be ocean, so scan outward
        // for the nearest dry, non-mountain biome.
        float[] spawn = findSpawn(world);
        player.spawn(world, spawn[0], spawn[1]);

        // Stream the chunks around the actual spawn point (it may be far from the
        // origin the warm-up generated), then scatter a herd so the world feels
        // alive from the very first frame.
        for (int i = 0; i < 80; i++) {
            world.update(player.getPosition().x, player.getPosition().z);
        }
        world.spawnInitialMobs(new Random(), player.getPosition().x, player.getPosition().z, 12);

        Hud hud = new Hud(lineShader, hudShader, font);
        ItemRenderer itemRenderer = new ItemRenderer();
        MobRenderer mobRenderer = new MobRenderer();
        List<Hud.Message> messages = new ArrayList<>();
        boolean[] showDebug = {false};
        boolean[] menuOpen = {false};
        int[] menuSelection = {0};
        int[] sliderDragRow = {-1};
        int[] bindingAction = {-1}; // >= 0: capturing a key for this action (settings menu)
        CraftingGrid craftingGrid = new CraftingGrid();
        InventoryController inventoryController = new InventoryController(player.getInventory(), craftingGrid);
        boolean[] inventoryOpen = {false};
        boolean[] creativeOpen = {false};
        int[] creativeTab = {0};
        int[] hoveredSlot = {-1};

        window.setCursorCaptured(true);

        // Ensure the renderer/player/window all match the loaded settings.
        applySettings(settings, world, player, window);

        int[] selectedSlot = {0};
        Random loot = new Random();
        DayNightCycle dayNightCycle = new DayNightCycle();
        MiningController mining = new MiningController();
        float[] animTime = {0f}; // free-running clock driving the flowing-water/lava texture scroll
        float[] attackCooldown = {0f}; // time until the next mob hit can land
        Mob[] targetedMobRef = {null}; // the mob the crosshair is aimed at this frame, if any

        System.out.println("Controls: WASD move, mouse look, Space jump, Left-Ctrl or double-tap W to sprint,");
        System.out.println("          F to fly (double-tap W also takes off in creative and boosts speed");
        System.out.println("          once flying - only F lands you),");
        System.out.println("          hold Left-click to mine (speed/possibility depends on your tool;");
        System.out.println("          creative breaks one block per click),");
        System.out.println("          Right-click place (or eat, if selected item is food),");
        System.out.println("          E inventory (click/drag items), C smelt (aim at a furnace), 1-9/scroll select,");
        System.out.println("          F3 debug, Esc settings.");

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
        if (System.getenv("MCCLONE_AUTOTEST_CLOUD") != null) {
            dayNightCycle.setCloudPhase(Float.parseFloat(System.getenv("MCCLONE_AUTOTEST_CLOUD")));
        }
        if (System.getenv("MCCLONE_AUTOTEST_PITCH") != null) {
            player.getCamera().setPitch(Float.parseFloat(System.getenv("MCCLONE_AUTOTEST_PITCH")));
        }
        if (System.getenv("MCCLONE_AUTOTEST_YAW") != null) {
            player.getCamera().setYaw(Float.parseFloat(System.getenv("MCCLONE_AUTOTEST_YAW")));
        }
        if (System.getenv("MCCLONE_AUTOTEST_MENU") != null) {
            menuOpen[0] = true;
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
            animTime[0] += dt;
            attackCooldown[0] -= dt;

            if (input.isKeyJustPressed(GLFW_KEY_ESCAPE)) {
                if (bindingAction[0] >= 0) {
                    bindingAction[0] = -1; // Esc cancels a keybind capture
                } else if (inventoryOpen[0]) {
                    closeInventory(inventoryController, inventoryOpen);
                } else if (creativeOpen[0]) {
                    closeCreative(inventoryController, creativeOpen);
                } else {
                    menuOpen[0] = !menuOpen[0];
                }
                window.setCursorCaptured(!menuOpen[0] && !inventoryOpen[0] && !creativeOpen[0]);
                input.resetMouseDelta();
            }

            if (input.isKeyJustPressed(settings.getKeyBinds().get(KeyBindings.INVENTORY))) {
                if (inventoryOpen[0]) {
                    closeInventory(inventoryController, inventoryOpen);
                } else if (creativeOpen[0]) {
                    closeCreative(inventoryController, creativeOpen);
                } else if (settings.getGameMode().isCreative()) {
                    creativeOpen[0] = true;
                    menuOpen[0] = false;
                } else {
                    inventoryOpen[0] = true;
                    menuOpen[0] = false;
                }
                window.setCursorCaptured(!menuOpen[0] && !inventoryOpen[0] && !creativeOpen[0]);
                input.resetMouseDelta();
            }

            if (creativeOpen[0]) {
                // Creative catalog: click a tab to switch category, click an item to
                // put it on the cursor (shift-click moves it straight to a hotbar
                // slot), the destroy slot drops the cursor, and the hotbar behaves
                // like the survival inventory.
                float logicalX = ((float) input.getMouseX() / window.getWidth() * 2f - 1f) * window.getAspectRatio();
                float logicalY = 1f - (float) input.getMouseY() / window.getHeight() * 2f;
                boolean shift = input.isKeyDown(GLFW_KEY_LEFT_SHIFT) || input.isKeyDown(GLFW_KEY_RIGHT_SHIFT);

                if (input.isMouseJustPressed(GLFW_MOUSE_BUTTON_LEFT)) {
                    int tab = hud.creativeTabAt(logicalX, logicalY);
                    if (tab >= 0) {
                        creativeTab[0] = tab;
                    } else {
                        int item = hud.creativeItemAt(logicalX, logicalY, creativeTab[0]);
                        if (item >= 0) {
                            inventoryController.pickCreativeItem(CreativeCatalog.TABS[creativeTab[0]].items()[item], shift);
                        } else if (hud.destroySlotAt(logicalX, logicalY)) {
                            inventoryController.destroyCursor();
                        } else {
                            int hb = hud.hotbarSlotAt(logicalX, logicalY);
                            if (hb >= 0) {
                                if (shift) {
                                    inventoryController.click(hb, false, true);
                                } else {
                                    inventoryController.beginDrag(hb, false);
                                }
                            }
                        }
                    }
                }
                if (input.isMouseDown(GLFW_MOUSE_BUTTON_LEFT) || input.isMouseDown(GLFW_MOUSE_BUTTON_RIGHT)) {
                    inventoryController.continueDrag(hud.hotbarSlotAt(logicalX, logicalY));
                }
                if (!input.isMouseDown(GLFW_MOUSE_BUTTON_LEFT) && !input.isMouseDown(GLFW_MOUSE_BUTTON_RIGHT)) {
                    inventoryController.endDrag(hud.hotbarSlotAt(logicalX, logicalY));
                }
                if (input.isMouseJustPressed(GLFW_MOUSE_BUTTON_RIGHT)) {
                    int hb = hud.hotbarSlotAt(logicalX, logicalY);
                    if (hb >= 0) {
                        inventoryController.beginDrag(hb, true);
                    }
                    int item = hud.creativeItemAt(logicalX, logicalY, creativeTab[0]);
                    if (item >= 0) {
                        inventoryController.pickCreativeItem(CreativeCatalog.TABS[creativeTab[0]].items()[item], false);
                    }
                }
            } else if (inventoryOpen[0]) {
                // Mouse-driven inventory: resolve the hovered slot and apply clicks/drag.
                // The HUD draws in logical-square space then scales X by 1/aspect, so the
                // mouse's normalized X must be scaled back by aspect to hit-test correctly.
                float logicalX = ((float) input.getMouseX() / window.getWidth() * 2f - 1f) * window.getAspectRatio();
                float logicalY = 1f - (float) input.getMouseY() / window.getHeight() * 2f;
                hoveredSlot[0] = hud.inventorySlotAt(logicalX, logicalY);

                boolean shift = input.isKeyDown(GLFW_KEY_LEFT_SHIFT) || input.isKeyDown(GLFW_KEY_RIGHT_SHIFT);
                if (input.isMouseJustPressed(GLFW_MOUSE_BUTTON_LEFT)) {
                    if (shift) {
                        inventoryController.click(hoveredSlot[0], false, true);
                    } else {
                        inventoryController.beginDrag(hoveredSlot[0], false);
                    }
                }
                if (input.isMouseJustPressed(GLFW_MOUSE_BUTTON_RIGHT)) {
                    if (shift) {
                        inventoryController.click(hoveredSlot[0], true, true);
                    } else {
                        inventoryController.beginDrag(hoveredSlot[0], true);
                    }
                }
                if (input.isMouseDown(GLFW_MOUSE_BUTTON_LEFT) || input.isMouseDown(GLFW_MOUSE_BUTTON_RIGHT)) {
                    inventoryController.continueDrag(hoveredSlot[0]);
                }
                if (!input.isMouseDown(GLFW_MOUSE_BUTTON_LEFT) && !input.isMouseDown(GLFW_MOUSE_BUTTON_RIGHT)) {
                    inventoryController.endDrag(hoveredSlot[0]);
                }
            } else if (menuOpen[0]) {
                int totalMenuRows = Settings.ROW_COUNT + 1 + KeyBindings.COUNT;
                if (bindingAction[0] >= 0) {
                    // Capturing a key for a keybind row: bind the next non-modifier
                    // key press (Esc cancels).
                    int pressed = input.consumeLastKeyPressed();
                    if (pressed == GLFW_KEY_ESCAPE) {
                        bindingAction[0] = -1;
                    } else if (pressed != GLFW_KEY_UNKNOWN && !KeyBindings.isModifierKey(pressed)) {
                        settings.getKeyBinds().set(bindingAction[0], pressed);
                        settings.save(settingsFile);
                        bindingAction[0] = -1;
                    }
                } else {
                    // Navigate with arrows/WASD; toggle/step settings or start a
                    // keybind capture with Enter/Space/Left/Right.
                    if (input.isKeyJustPressed(GLFW_KEY_UP) || input.isKeyJustPressed(GLFW_KEY_W)) {
                        menuSelection[0] = Math.floorMod(menuSelection[0] - 1, totalMenuRows);
                    }
                    if (input.isKeyJustPressed(GLFW_KEY_DOWN) || input.isKeyJustPressed(GLFW_KEY_S)) {
                        menuSelection[0] = Math.floorMod(menuSelection[0] + 1, totalMenuRows);
                    }
                    if (input.isKeyJustPressed(GLFW_KEY_LEFT)) {
                        if (menuSelection[0] < Settings.ROW_COUNT) {
                            settings.adjust(menuSelection[0], -1);
                            applySettings(settings, world, player, window);
                            settings.save(settingsFile);
                        }
                    }
                    if (input.isKeyJustPressed(GLFW_KEY_RIGHT)) {
                        if (menuSelection[0] < Settings.ROW_COUNT) {
                            settings.adjust(menuSelection[0], +1);
                            applySettings(settings, world, player, window);
                            settings.save(settingsFile);
                        }
                    }
                    if (input.isKeyJustPressed(GLFW_KEY_ENTER) || input.isKeyJustPressed(GLFW_KEY_SPACE)) {
                        if (menuSelection[0] < Settings.ROW_COUNT) {
                            settings.adjust(menuSelection[0], +1);
                            applySettings(settings, world, player, window);
                            settings.save(settingsFile);
                        } else if (menuSelection[0] > Settings.ROW_COUNT) {
                            bindingAction[0] = menuSelection[0] - Settings.ROW_COUNT - 1;
                            input.consumeLastKeyPressed(); // discard the Enter/Space that started capture
                        }
                    }
                }

                // Mouse: hover to select, click a toggle, click or drag a slider,
                // or click a keybind row to start capturing it.
                float sLx = ((float) input.getMouseX() / window.getWidth() * 2f - 1f) * window.getAspectRatio();
                float sLy = 1f - (float) input.getMouseY() / window.getHeight() * 2f;
                int hoverRow = hud.settingsRowAt(sLx, sLy);
                if (hoverRow >= 0) {
                    menuSelection[0] = hoverRow;
                }
                if (bindingAction[0] < 0 && input.isMouseJustPressed(GLFW_MOUSE_BUTTON_LEFT)) {
                    int clicked = hud.settingsRowAt(sLx, sLy);
                    if (clicked >= 0) {
                        if (clicked < Settings.ROW_COUNT) {
                            if (Settings.isToggle(clicked)) {
                                settings.adjust(clicked, +1);
                                applySettings(settings, world, player, window);
                                settings.save(settingsFile);
                            } else {
                                float frac = hud.settingsTrackAt(sLx, sLy);
                                if (frac >= 0f) {
                                    settings.setFromFraction(clicked, frac);
                                    applySettings(settings, world, player, window);
                                    settings.save(settingsFile);
                                    sliderDragRow[0] = clicked;
                                }
                            }
                        } else if (clicked > Settings.ROW_COUNT) {
                            bindingAction[0] = clicked - Settings.ROW_COUNT - 1;
                            input.consumeLastKeyPressed();
                        }
                    }
                }
                if (input.isMouseDown(GLFW_MOUSE_BUTTON_LEFT) && sliderDragRow[0] >= 0) {
                    float frac = hud.settingsSliderAt(sLx, sliderDragRow[0]);
                    settings.setFromFraction(sliderDragRow[0], frac);
                    applySettings(settings, world, player, window);
                    settings.save(settingsFile);
                }
                if (!input.isMouseDown(GLFW_MOUSE_BUTTON_LEFT)) {
                    sliderDragRow[0] = -1;
                }
            }

            if (input.isKeyJustPressed(settings.getKeyBinds().get(KeyBindings.DEBUG))) {
                showDebug[0] = !showDebug[0];
            }
            boolean screenshotRequested = input.isKeyJustPressed(settings.getKeyBinds().get(KeyBindings.SCREENSHOT));

            if (!menuOpen[0] && !inventoryOpen[0] && !creativeOpen[0]) {
                player.update(dt, input, world);
            }

            // Keep streaming/remeshing even with the menu open, so toggling a
            // rendering setting (e.g. see-through leaves) takes effect live.
            world.update(player.getPosition().x, player.getPosition().z);

            if (player.getStats().isDead()) {
                System.out.println("You died. Respawning...");
                showMessage(messages, "You died!", new Vector4f(0.9f, 0.25f, 0.25f, 1f), 3.0f);
                // Drop the whole inventory onto the ground, Minecraft-style.
                Vector3f deathPos = player.getPosition();
                for (int slot = 0; slot < Inventory.SIZE; slot++) {
                    BlockType t = player.getInventory().typeOf(slot);
                    if (t != null) {
                        world.spawnItem((int) Math.floor(deathPos.x), (int) Math.floor(deathPos.y),
                                (int) Math.floor(deathPos.z), t, player.getInventory().countOf(slot), loot);
                    }
                }
                player.getInventory().clear();
                player.getDurability().reset();
                player.respawn(world, 0.5f, 0.5f);
            }

            // Item-entity physics + pickup.
            world.updateItems(dt, player.getPosition(), player.getInventory());

            // Mobs: passives wander, hostiles hunt the player (spawning at night and
            // melting away at dawn); the damage their hits and arrows deal is applied
            // to the player's health, where the existing death/respawn handling picks
            // it up.
            Vector3f playerPos = player.getPosition();
            AABB playerBox = new AABB(playerPos.x - 0.3f, playerPos.y, playerPos.z - 0.3f,
                    playerPos.x + 0.3f, playerPos.y + 1.8f, playerPos.z + 0.3f);
            float mobDamage = world.updateMobs(dt, playerPos, playerBox, dayNightCycle.isNight(), loot);
            if (mobDamage > 0f) {
                player.getStats().damage(mobDamage);
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

            // Block selection via number keys / scroll wheel - the hotbar is the
            // first 9 inventory slots (only in gameplay).
            if (!menuOpen[0] && !inventoryOpen[0] && !creativeOpen[0]) {
                for (int i = 0; i < Inventory.HOTBAR_SIZE; i++) {
                    if (input.isKeyJustPressed(GLFW_KEY_1 + i)) {
                        selectedSlot[0] = i;
                    }
                }
                double scroll = input.getScrollDelta();
                if (scroll != 0) {
                    selectedSlot[0] = Math.floorMod(selectedSlot[0] - (int) Math.signum(scroll), Inventory.HOTBAR_SIZE);
                }
            }

            if (!menuOpen[0] && !inventoryOpen[0] && !creativeOpen[0]) {
                hit = Raycaster.cast(world, player.getEyePosition(), player.getCamera().getFront(), REACH_DISTANCE);

                if (input.isKeyJustPressed(settings.getKeyBinds().get(KeyBindings.SMELT))) {
                    BlockType selected = player.getInventory().typeOf(selectedSlot[0]);
                    // Smelting: pressing C while aiming at a furnace smelts the selected
                    // ore into its ingot/gem (consuming coal as fuel).
                    BlockType targeted = hit != null ? world.getBlock(hit.blockPos.x, hit.blockPos.y, hit.blockPos.z) : BlockType.AIR;
                    if (selected != null && targeted == BlockType.FURNACE && Smelting.isSmeltable(selected)) {
                        if (Smelting.smelt(player.getInventory(), selected) != null) {
                            System.out.println("Smelted " + selected);
                            showMessage(messages, "Smelted " + selected, new Vector4f(0.6f, 0.9f, 0.6f, 1f), 2.5f);
                        } else {
                            showMessage(messages, "Smelting needs ore and coal", new Vector4f(1f, 0.72f, 0.3f, 1f), 2.5f);
                        }
                    }
                }

                BlockType targetType = hit != null ? world.getBlock(hit.blockPos.x, hit.blockPos.y, hit.blockPos.z) : BlockType.AIR;
                BlockType heldItem = player.getInventory().typeOf(selectedSlot[0]);
                GameMode mode = settings.getGameMode();

                // What the crosshair is aimed at: a mob takes priority over the block
                // behind it, so swinging at a pig doesn't dig up the ground behind it.
                Mob targetedMob = world.raycastMob(player.getEyePosition(), player.getCamera().getFront(), REACH_DISTANCE);
                targetedMobRef[0] = targetedMob;

                // Attacking: holding left-click hits the targeted mob on a short cooldown
                // (mobs can't be hurt in spectator - no interaction).
                if (targetedMob != null && !mode.isSpectator()
                        && input.isMouseDown(GLFW_MOUSE_BUTTON_LEFT) && attackCooldown[0] <= 0f) {
                    // Creative kills in one hit; survival/adventure deal tool damage
                    // (a sword hits harder than a bare-handed punch).
                    float damage = mode.isCreative() ? targetedMob.getMaxHealth() : Mining.attackDamage(heldItem);
                    world.damageMob(targetedMob, damage, player.getPosition().x, player.getPosition().z, loot);
                    attackCooldown[0] = 0.45f;
                    // Swords wear out with use (creative tools never break).
                    if (!mode.isCreative() && Mining.isSword(heldItem) && player.getDurability().use(heldItem)) {
                        player.getInventory().remove(heldItem, 1);
                        System.out.println("Your " + heldItem + " broke!");
                        showMessage(messages, "Your " + heldItem + " broke!",
                                new Vector4f(1f, 0.72f, 0.3f, 1f), 2.5f);
                    }
                }

                // Breaking: creative breaks instantly; adventure/spectator can't break.
                // Aiming at a mob means the swing is an attack, not a dig.
                if (mode.canBreak() && targetedMobRef[0] == null) {
                    boolean holding = hit != null && input.isMouseDown(GLFW_MOUSE_BUTTON_LEFT);
                    if (mode.isCreative()) {
                        // One block per click (a fresh press), not "instant-break
                        // whatever the crosshair happens to be on while the button
                        // stays down" - that made a held click plus a mouse sweep
                        // bulldoze a whole swath of blocks by accident.
                        breakFraction = (hit != null && input.isMouseJustPressed(GLFW_MOUSE_BUTTON_LEFT)) ? 1f : 0f;
                    } else {
                        breakFraction = mining.update(hit != null ? hit.blockPos : null, targetType, heldItem, holding, dt);
                    }

                    if (breakFraction >= 1f) {
                        mining.reset();
                        breakFraction = 0f;
                        world.setBlock(hit.blockPos.x, hit.blockPos.y, hit.blockPos.z, BlockType.AIR);
                        if (!mode.isCreative()) {
                            // Drop the item into the world (to be picked up) rather than
                            // adding it straight to the inventory. Transient fluid flow drops
                            // nothing - only a fluid source drops itself.
                            if (targetType.isFluidFlow()) {
                                // nothing to drop
                            } else if (targetType == BlockType.BERRY_BUSH) {
                                world.spawnItem(hit.blockPos.x, hit.blockPos.y, hit.blockPos.z, BlockType.BERRIES, BERRIES_PER_BUSH, loot);
                            } else {
                                world.spawnItem(hit.blockPos.x, hit.blockPos.y, hit.blockPos.z, targetType, 1, loot);
                                if (targetType == BlockType.LEAVES && loot.nextInt(APPLE_DROP_CHANCE) == 0) {
                                    world.spawnItem(hit.blockPos.x, hit.blockPos.y, hit.blockPos.z, BlockType.APPLE, 1, loot);
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
                    }
                }

                // Placing: creative places for free; adventure/spectator can't place.
                // (Never place into the mob the crosshair is on.)
                if (input.isMouseJustPressed(GLFW_MOUSE_BUTTON_RIGHT) && hit != null && mode.canPlace()
                        && heldItem != null && targetedMobRef[0] == null) {
                    if (heldItem.isEdible() && !mode.isCreative()) {
                        player.eat(heldItem);
                    } else if (!heldItem.isItem) {
                        // Pure inventory items (tools, and any future non-edible item)
                        // have no world tile and can never be placed as a block.
                        Vector3i p = hit.placePos;
                        if (!intersectsPlayer(player, p)) {
                            boolean placed = mode.isCreative() || player.getInventory().remove(heldItem, 1);
                            if (placed) {
                                world.setBlock(p.x, p.y, p.z, heldItem);
                            }
                        }
                    }
                }
            }

            // --- Render ---
            Matrix4f projection = player.getCamera().getProjectionMatrix(settings.getFov(), window.getAspectRatio(), NEAR_PLANE, FAR_PLANE);
            Matrix4f view = player.getCamera().getViewMatrix();

            Vector3f horizonColor = dayNightCycle.getHorizonColor();
            window.setClearColor(horizonColor.x, horizonColor.y, horizonColor.z, 1f);
            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

            // Procedural sky (gradient, sun/moon, stars, clouds), pinned to the far
            // plane so the world always draws in front of it.
            glDisable(GL_DEPTH_TEST);
            skyRenderer.render(skyShader, projection, view,
                    dayNightCycle.getSunDirection(), dayNightCycle.getDaylightFactor(), dayNightCycle.getCloudPhase(),
                    settings.getCloudAmount() / 3f,
                    0.5f + settings.getCloudSpeed() * 0.5f,
                    settings.isStars() ? 1f : 0f,
                    dayNightCycle.getZenithColor(), dayNightCycle.getHorizonColor(),
                    dayNightCycle.getNightZenithColor(), dayNightCycle.getSunColor(), dayNightCycle.getMoonColor());
            glEnable(GL_DEPTH_TEST);

            chunkShader.bind();
            chunkShader.setUniform("projection", projection);
            chunkShader.setUniform("view", view);
            chunkShader.setUniform("atlas", 0);
            chunkShader.setUniform("fogColor", horizonColor);
            chunkShader.setUniform("fogStart", (world.getRenderDistance() - 2) * 16f);
            chunkShader.setUniform("fogEnd", world.getRenderDistance() * 16f);
            chunkShader.setUniform("ambientBrightness", dayNightCycle.getAmbientBrightness());
            chunkShader.setUniform("time", animTime[0]);
            chunkShader.setUniform("atlasGrid", (float) TextureAtlas.GRID);
            atlas.bind();
            world.render(chunkShader);
            itemRenderer.render(chunkShader, atlas, itemTextures, world.getItems(), player.getCamera());
            mobRenderer.render(mobTextures, world.getMobs(), world.getArrows());
            chunkShader.unbind();

            if (!menuOpen[0] && !inventoryOpen[0] && !creativeOpen[0]) {
                if (hit != null && targetedMobRef[0] == null) {
                    float outlineHeight = world.getBlock(hit.blockPos.x, hit.blockPos.y, hit.blockPos.z).collisionHeight;
                    hud.renderBlockOutline(projection, view, hit.blockPos, breakFraction, outlineHeight);
                }
                hud.renderCrosshair(window.getAspectRatio());
                hud.renderHotbar(atlas, itemTextures, player.getDurability(), player.getInventory(), selectedSlot[0], window.getAspectRatio());
                // Creative/spectator have no health to show - hide the bars like Minecraft.
                if (!settings.getGameMode().isInvulnerable()) {
                    hud.renderStatusBars(
                            player.getStats().getHealth(), PlayerStats.MAX_HEALTH,
                            player.getStats().getHunger(), PlayerStats.MAX_HUNGER,
                            player.getStats().getStamina(), PlayerStats.MAX_STAMINA,
                            Inventory.HOTBAR_SIZE, window.getAspectRatio());
                }
            }
            hud.renderMessages(messages, window.getAspectRatio());
            if (showDebug[0]) {
                Vector3f pos = player.getPosition();
                float aspect = window.getAspectRatio();
                float textSize = 0.035f;
                float y = 0.95f;
                float step = 0.052f;
                hud.drawTextLeft("FPS: " + timer.getFps(), -0.95f, y, textSize, WHITE, aspect);
                hud.drawTextLeft(String.format(Locale.ROOT, "XYZ: %.1f / %.1f / %.1f", pos.x, pos.y, pos.z),
                        -0.95f, y - step, textSize, WHITE, aspect);
                BlockType sel = player.getInventory().typeOf(selectedSlot[0]);
                hud.drawTextLeft("Selected: " + (sel == null ? "-" : sel.toString()),
                        -0.95f, y - 2f * step, textSize, WHITE, aspect);
                hud.drawTextLeft("Biome: " + world.getBiome((int) Math.floor(pos.x), (int) Math.floor(pos.z)),
                        -0.95f, y - 3f * step, textSize, WHITE, aspect);
            }
            if (menuOpen[0]) {
                hud.renderSettingsMenu(settings, menuSelection[0], bindingAction[0], window.getAspectRatio());
            }
            if (inventoryOpen[0]) {
                // Scale mouse X back by aspect to match the HUD's logical-square space.
                float logicalX = ((float) input.getMouseX() / window.getWidth() * 2f - 1f) * window.getAspectRatio();
                float logicalY = 1f - (float) input.getMouseY() / window.getHeight() * 2f;
                hud.renderInventory(player.getInventory(), craftingGrid, inventoryController, hoveredSlot[0],
                        atlas, itemTextures, player.getDurability(), window.getAspectRatio(), logicalX, logicalY);
            }
            if (creativeOpen[0]) {
                float logicalX = ((float) input.getMouseX() / window.getWidth() * 2f - 1f) * window.getAspectRatio();
                float logicalY = 1f - (float) input.getMouseY() / window.getHeight() * 2f;
                hud.renderCreative(player.getInventory(), inventoryController, creativeTab[0], selectedSlot[0],
                        atlas, itemTextures, player.getDurability(), window.getAspectRatio(), logicalX, logicalY);
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
        settings.save(settingsFile);

        hud.destroy();
        itemRenderer.destroy();
        mobRenderer.destroy();
        chunkShader.destroy();
        lineShader.destroy();
        hudShader.destroy();
        skyShader.destroy();
        skyRenderer.destroy();
        atlas.destroy();
        itemTextures.destroy();
        mobTextures.destroy();
        font.destroy();
        world.destroy();
        window.close();
    }

    /** Finds a dry, non-mountain spawn near the origin by scanning outward in square rings. */
    private static float[] findSpawn(World world) {
        for (int r = 0; r <= 50; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue; // ring only
                    TerrainGenerator.Biome b = world.getBiome(dx, dz);
                    if (b == TerrainGenerator.Biome.OCEAN || b == TerrainGenerator.Biome.FROZEN_OCEAN
                            || b == TerrainGenerator.Biome.MOUNTAIN) continue;
                    return new float[]{dx + 0.5f, dz + 0.5f};
                }
            }
        }
        return new float[]{0.5f, 0.5f};
    }

    /** Reuses the seed from a previous run if this save directory already has one, otherwise mints and stores a new one. */
    private static long loadOrCreateSeed(Path saveDir) {        Path seedFile = saveDir.resolve("seed.txt");
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
