package com.minecraftclone;

import com.minecraftclone.engine.*;
import com.minecraftclone.engine.graphics.FontAtlas;
import com.minecraftclone.engine.graphics.HandRenderer;
import com.minecraftclone.engine.graphics.ItemRenderer;
import com.minecraftclone.engine.graphics.ItemTextures;
import com.minecraftclone.engine.graphics.MobRenderer;
import com.minecraftclone.engine.graphics.MobTextures;
import com.minecraftclone.engine.graphics.SkyRenderer;
import com.minecraftclone.engine.graphics.TextureAtlas;
import com.minecraftclone.engine.graphics.WeatherRenderer;
import com.minecraftclone.engine.gui.ContainerGui;
import com.minecraftclone.player.CraftingGrid;
import com.minecraftclone.player.CreativeCatalog;
import com.minecraftclone.player.Inventory;
import com.minecraftclone.player.InventoryController;
import com.minecraftclone.player.MiningController;
import com.minecraftclone.player.Player;
import com.minecraftclone.player.PlayerStats;
import com.minecraftclone.util.AABB;
import com.minecraftclone.util.Raycaster;
import com.minecraftclone.util.ResourceLoader;
import com.minecraftclone.world.BlockType;
import com.minecraftclone.world.Furnace;
import com.minecraftclone.world.Door;
import com.minecraftclone.world.Mining;
import com.minecraftclone.world.Mob;
import com.minecraftclone.world.World;
import com.minecraftclone.world.gen.TerrainGenerator;
import com.minecraftclone.world.gen.WorldGenSettings;
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
 * (or eat it, if it's food; or open a furnace/crafting table's GUI when aiming at one),
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
        if (world != null) {
            world.setLeavesTransparent(settings.isLeavesTransparent());
            world.setRenderDistance(settings.getRenderDistance());
        }
        window.setVsync(settings.isVsync());
        player.setMouseSensitivity(settings.getMouseSensitivity());
        player.setGameMode(settings.getGameMode());
        player.setInvertMouseY(settings.isInvertMouseY());
        player.setViewBobbing(settings.isViewBobbing());
    }

    /**
     * Shared keyboard + mouse interaction for the settings page, used both by the
     * in-game Esc menu and the main-menu Settings button. {@code world} may be
     * null (main menu) - {@link #applySettings} tolerates that. {@code tab}
     * selects the active section (Graphics / Gameplay / Controls); navigation
     * wraps within the active tab's rows, and Tab (or clicking a tab) switches.
     */
    private void handleSettingsMenuInput(Input input, Settings settings, Path settingsFile, World world,
                                         Player player, Window window, Hud hud,
                                         int[] menuSelection, int[] sliderDragRow, int[] bindingAction,
                                         int[] settingsTab) {
        int tab = settingsTab[0];
        int rows = tab == Settings.TAB_CONTROLS ? KeyBindings.COUNT : Settings.tabRowCount(tab);

        // Tab key switches to the next section; the selection resets to the top.
        if (input.isKeyJustPressed(GLFW_KEY_TAB)) {
            settingsTab[0] = (tab + 1) % Settings.TAB_COUNT;
            menuSelection[0] = 0;
            sliderDragRow[0] = -1;
            bindingAction[0] = -1;
            return;
        }

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
                menuSelection[0] = Math.floorMod(menuSelection[0] - 1, rows);
            }
            if (input.isKeyJustPressed(GLFW_KEY_DOWN) || input.isKeyJustPressed(GLFW_KEY_S)) {
                menuSelection[0] = Math.floorMod(menuSelection[0] + 1, rows);
            }
            if (input.isKeyJustPressed(GLFW_KEY_LEFT)) {
                if (tab != Settings.TAB_CONTROLS) {
                    adjustSettingsRow(settings, settingsFile, world, player, window,
                            Settings.rowInTab(tab, menuSelection[0]), -1);
                }
            }
            if (input.isKeyJustPressed(GLFW_KEY_RIGHT)) {
                if (tab != Settings.TAB_CONTROLS) {
                    adjustSettingsRow(settings, settingsFile, world, player, window,
                            Settings.rowInTab(tab, menuSelection[0]), +1);
                }
            }
            if (input.isKeyJustPressed(GLFW_KEY_ENTER) || input.isKeyJustPressed(GLFW_KEY_SPACE)) {
                if (tab == Settings.TAB_CONTROLS) {
                    bindingAction[0] = menuSelection[0];
                    input.consumeLastKeyPressed(); // discard the Enter/Space that started capture
                } else {
                    adjustSettingsRow(settings, settingsFile, world, player, window,
                            Settings.rowInTab(tab, menuSelection[0]), +1);
                }
            }
        }

        // Mouse: hover to select, click a tab to switch, click a toggle, click
        // or drag a slider, or click a keybind row to start capturing it.
        float sLx = ((float) input.getMouseX() / window.getWidth() * 2f - 1f) * window.getAspectRatio();
        float sLy = 1f - (float) input.getMouseY() / window.getHeight() * 2f;
        int hoverTab = hud.settingsTabAt(sLx, sLy);
        if (hoverTab >= 0) {
            if (input.isMouseJustPressed(GLFW_MOUSE_BUTTON_LEFT) && hoverTab != tab) {
                settingsTab[0] = hoverTab;
                menuSelection[0] = 0;
                sliderDragRow[0] = -1;
                bindingAction[0] = -1;
            }
            return;
        }
        int hoverRow = hud.settingsRowAt(sLx, sLy, tab);
        if (hoverRow >= 0) {
            menuSelection[0] = hoverRow;
        }
        if (bindingAction[0] < 0 && input.isMouseJustPressed(GLFW_MOUSE_BUTTON_LEFT)) {
            int clicked = hud.settingsRowAt(sLx, sLy, tab);
            if (clicked >= 0) {
                if (tab == Settings.TAB_CONTROLS) {
                    bindingAction[0] = clicked;
                    input.consumeLastKeyPressed();
                } else {
                    int row = Settings.rowInTab(tab, clicked);
                    if (Settings.isToggle(row)) {
                        adjustSettingsRow(settings, settingsFile, world, player, window, row, +1);
                    } else {
                        float frac = hud.settingsTrackAt(sLx, sLy, tab);
                        if (frac >= 0f) {
                            settings.setFromFraction(row, frac);
                            applySettings(settings, world, player, window);
                            settings.save(settingsFile);
                            sliderDragRow[0] = clicked;
                        }
                    }
                }
            }
        }
        if (input.isMouseDown(GLFW_MOUSE_BUTTON_LEFT) && sliderDragRow[0] >= 0) {
            float frac = hud.settingsSliderAt(sLx, sliderDragRow[0], tab);
            settings.setFromFraction(Settings.rowInTab(tab, sliderDragRow[0]), frac);
            applySettings(settings, world, player, window);
            settings.save(settingsFile);
        }
        if (!input.isMouseDown(GLFW_MOUSE_BUTTON_LEFT)) {
            sliderDragRow[0] = -1;
        }
    }

    /** Steps/toggles a single settings row and pushes the change everywhere it applies. */
    private void adjustSettingsRow(Settings settings, Path settingsFile, World world, Player player,
                                   Window window, int row, int direction) {
        settings.adjust(row, direction);
        applySettings(settings, world, player, window);
        settings.save(settingsFile);
    }

    /** Closes any open container screen (inventory/crafting table/furnace), returning cursor/grid items to the inventory. */
    private void closeInventory(InventoryController controller, ContainerGui[] activeGui, ContainerGui inventoryGui, boolean[] inventoryOpen) {
        controller.returnGridToInventory();
        controller.returnCursorToInventory();
        activeGui[0] = inventoryGui;
        inventoryOpen[0] = false;
    }

    /** Opens the given container gui, rebinding the controller and releasing the cursor for mouse use. */
    private void openGui(InventoryController controller, ContainerGui[] activeGui, Window window, Input input, boolean[] inventoryOpen) {
        controller.setGui(activeGui[0]);
        inventoryOpen[0] = true;
        window.setCursorCaptured(false);
        input.resetMouseDelta();
    }

    /** Resets the calendar for a freshly started world and applies its days-per-season setting. */
    private void startCalendar(DayNightCycle dayNightCycle, Calendar calendar, WorldGenSettings genSettings) {
        dayNightCycle.resetDays();
        calendar.reset();
        calendar.setDaysPerSeason(genSettings.getDaysPerSeason());
    }

    /**
     * Breaks one block cell (whatever it is: a door, an overlay decoration, or a
     * solid block), dropping its loot and wearing the tool in survival. Shared by
     * the normal break and the hammer's 3x3 area mine.
     */
    private void breakBlockAt(World world, Player player, GameMode mode, BlockType heldItem, Random loot,
                              List<Hud.Message> messages, int bx, int by, int bz) {
        BlockType overlay = world.getOverlay(bx, by, bz);
        boolean targetingOverlay = overlay != BlockType.AIR;
        BlockType targetType = targetingOverlay ? overlay : world.getBlock(bx, by, bz);
        if (targetType == BlockType.AIR || targetType == BlockType.BEDROCK) return;
        if (!Mining.canBreak(targetType, heldItem)) return; // e.g. an ore the hammer can't mine

        if (Door.isDoor(targetType)) {
            Door.breakDoor(world, world::setBlock, bx, by, bz); // remove both halves
        } else if (targetingOverlay) {
            // Clear just the decoration - the water (or whatever else) it was
            // sitting inside is untouched.
            world.setOverlay(bx, by, bz, BlockType.AIR);
        } else {
            world.setBlock(bx, by, bz, BlockType.AIR);
        }

        if (!mode.isCreative()) {
            // Drop the item into the world (to be picked up) rather than adding it
            // straight to the inventory. Transient fluid flow drops nothing - only
            // a fluid source drops itself.
            if (targetType.isFluidFlow()) {
                // nothing to drop
            } else if (targetType == BlockType.BERRY_BUSH) {
                world.spawnItem(bx, by, bz, BlockType.BERRIES, BERRIES_PER_BUSH, loot);
            } else if (targetType == BlockType.COAL_ORE) {
                // Coal ore drops coal (the furnace fuel), not the ore itself.
                world.spawnItem(bx, by, bz, BlockType.COAL, 1, loot);
            } else {
                // An open door/trapdoor drops the closed item.
                BlockType drop = targetType == BlockType.DOOR_OPEN ? BlockType.DOOR
                        : targetType == BlockType.TRAPDOOR_OPEN ? BlockType.TRAPDOOR : targetType;
                world.spawnItem(bx, by, bz, drop, 1, loot);
                if (targetType == BlockType.FURNACE) {
                    // A broken furnace spills whatever it was smelting or burning.
                    Furnace furnace = world.furnaceAt(bx, by, bz);
                    if (furnace != null) {
                        for (int s = 0; s < Furnace.SLOT_COUNT; s++) {
                            if (furnace.typeOf(s) != null) {
                                world.spawnItem(bx, by, bz, furnace.typeOf(s), furnace.countOf(s), loot);
                            }
                        }
                    }
                }
                if (targetType == BlockType.LEAVES && loot.nextInt(APPLE_DROP_CHANCE) == 0) {
                    world.spawnItem(bx, by, bz, BlockType.APPLE, 1, loot);
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

        String saveDirEnv = System.getenv("MCCLONE_SAVE_DIR");
        Path saveRoot = saveDirEnv != null ? Paths.get(saveDirEnv).getParent() : Paths.get("saves");
        Path settingsFile = saveRoot.resolve("settings.txt");
        Settings settings = Settings.load(settingsFile);
        WorldGenSettings genSettings = new WorldGenSettings();
        System.out.println("Save directory: " + saveRoot.toAbsolutePath());
        Player player = new Player();
        player.setKeyBinds(settings.getKeyBinds());
        // The world is created lazily when the player picks a world or creates one.
        World world = null;
        boolean[] started = {false};
        List<String> worldNames = new ArrayList<>();

        Hud hud = new Hud(lineShader, hudShader, font);
        ItemRenderer itemRenderer = new ItemRenderer();
        HandRenderer handRenderer = new HandRenderer();
        MobRenderer mobRenderer = new MobRenderer();
        WeatherParticles weatherParticles = new WeatherParticles();
        WeatherRenderer weatherRenderer = new WeatherRenderer();
        List<Hud.Message> messages = new ArrayList<>();
        boolean[] showDebug = {false};
        boolean[] menuOpen = {false};
        int[] menuSelection = {0};
        int[] settingsTab = {Settings.TAB_GRAPHICS}; // active settings tab
        int[] sliderDragRow = {-1};
        int[] bindingAction = {-1}; // >= 0: capturing a key for this action (settings menu)
        CraftingGrid craftingGrid = new CraftingGrid();
        InventoryController inventoryController = new InventoryController(player.getInventory(), craftingGrid);
        ContainerGui inventoryGui = new ContainerGui(ContainerGui.Kind.INVENTORY, player.getInventory(), craftingGrid, null);
        ContainerGui[] activeGui = {inventoryGui}; // the container screen currently shown, if any
        boolean[] inventoryOpen = {false};
        boolean[] creativeOpen = {false};
        int[] creativeTab = {0};
        int[] hoveredSlot = {-1};
        boolean[] mainMenuOpen = {true};
        boolean[] mainSettingsOpen = {false}; // settings page opened from the main menu
        boolean[] worldSelectOpen = {false};
        boolean[] worldGenOpen = {false};
        int[] mainMenuSelection = {Hud.MENU_PLAY};
        int[] worldSelectSelection = {0};
        int[] worldGenSelection = {0};
        int[] editingRow = {-1};

        window.setCursorCaptured(false); // free cursor in the main menu

        // Ensure the renderer/player/window all match the loaded settings.
        applySettings(settings, world, player, window);

        int[] selectedSlot = {0};
        Random loot = new Random();
        DayNightCycle dayNightCycle = new DayNightCycle();
        Calendar calendar = new Calendar(); // in-game calendar: days, seasons, years
        Climate climate = new Climate(calendar, dayNightCycle); // weather + biome temperature/humidity
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
        System.out.println("          E inventory (click/drag items), 1-9/scroll select,");
        System.out.println("          right-click a furnace/crafting table for its GUI,");
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
        String weatherOverride = System.getenv("MCCLONE_AUTOTEST_WEATHER");
        if (weatherOverride != null) {
            try {
                climate.forceWeather(Weather.valueOf(weatherOverride.toUpperCase()));
            } catch (IllegalArgumentException ignored) {
                // Unknown weather override - leave the random forecast alone.
            }
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
        if (System.getenv("MCCLONE_AUTOTEST_SETTINGS_TAB") != null) {
            settingsTab[0] = Math.max(0, Math.min(Settings.TAB_COUNT - 1,
                    Integer.parseInt(System.getenv("MCCLONE_AUTOTEST_SETTINGS_TAB"))));
        }
        if (System.getenv("MCCLONE_AUTOTEST_MAINMENU_SETTINGS") != null) {
            mainSettingsOpen[0] = true;
        }
        if (System.getenv("MCCLONE_AUTOTEST_WORLDSELECT") != null) {
            worldNames = listWorlds(saveRoot);
            worldSelectOpen[0] = true;
        }
        if (System.getenv("MCCLONE_AUTOTEST_WORLDGEN") != null) {
            worldGenOpen[0] = true;
        }
        if (System.getenv("MCCLONE_AUTOTEST_DEBUG") != null) {
            showDebug[0] = true;
        }
        if (autoTest && System.getenv("MCCLONE_AUTOTEST_SHOW_MENU") == null) {
            // Auto-play for the headless smoke test (unless the menu is being screenshotted).
            Path autoDir = saveDirEnv != null ? Paths.get(saveDirEnv) : saveRoot.resolve("world");
            genSettings = loadWorldGenSettings(autoDir);
            genSettings.setName(autoDir.getFileName().toString());
            saveWorldGenSettings(autoDir, genSettings);
            long seed = genSettings.resolveSeed();
            world = new World(seed, genSettings, atlas, autoDir);
            startCalendar(dayNightCycle, calendar, genSettings);
            world.setRenderDistance(settings.getRenderDistance());
            world.setLeavesTransparent(settings.isLeavesTransparent());
            for (int i = 0; i < 200; i++) world.update(0, 0);
            float[] spawn = findSpawn(world);
            player.spawn(world, spawn[0], spawn[1]);
            for (int i = 0; i < 80; i++) world.update(player.getPosition().x, player.getPosition().z);
            world.spawnInitialMobs(new Random(), player.getPosition().x, player.getPosition().z, 12);
            System.out.println("World seed: " + seed);
            started[0] = true;
            mainMenuOpen[0] = false;
        }
        // Opt-in autotest hook: place a directional block (e.g. a furnace) in front
        // of the camera so its front-vs-side faces can be screenshotted. Yaw sets
        // the furnace's facing via the same code path a player's placement uses.
        if (System.getenv("MCCLONE_AUTOTEST_PLACE") != null && started[0]) {
            try {
                BlockType placed = BlockType.valueOf(System.getenv("MCCLONE_AUTOTEST_PLACE"));
                Vector3f front = player.getCamera().getFront();
                int px = (int) Math.floor(player.getPosition().x + front.x * 2f);
                int py = (int) Math.floor(player.getPosition().y);
                int pz = (int) Math.floor(player.getPosition().z + front.z * 2f);
                world.setBlock(px, py, pz, placed);
                byte facing = (byte) (Math.abs(front.x) >= Math.abs(front.z)
                        ? (front.x >= 0 ? 3 : 2)
                        : (front.z >= 0 ? 1 : 0));
                if (System.getenv("MCCLONE_AUTOTEST_PLACE_FACING") != null) {
                    facing = (byte) Integer.parseInt(System.getenv("MCCLONE_AUTOTEST_PLACE_FACING"));
                }
                world.setBlockOrientation(px, py, pz, facing);
                // Autotest hook: load the furnace with ore + fuel and tick it until
                // it's burning, so the lit front tile can be screenshotted.
                if (placed == BlockType.FURNACE && System.getenv("MCCLONE_AUTOTEST_LIT") != null) {
                    Furnace furnace = world.getOrCreateFurnace(px, py, pz);
                    furnace.setSlot(Furnace.SLOT_INPUT, BlockType.IRON_ORE, 8);
                    furnace.setSlot(Furnace.SLOT_FUEL, BlockType.COAL, 8);
                    furnace.tick(1f);
                    System.out.println("Furnace burning: " + furnace.isBurning());
                }
                for (int i = 0; i < 5; i++) world.update(player.getPosition().x, player.getPosition().z);
                System.out.println("Placed " + placed + " at " + px + "," + py + "," + pz + " facing " + facing);
            } catch (IllegalArgumentException ignored) {
                System.err.println("MCCLONE_AUTOTEST_PLACE: unknown block " + System.getenv("MCCLONE_AUTOTEST_PLACE"));
            }
        }
        // Opt-in autotest hook: put a specific block/item in the held hotbar slot so
        // the first-person hand can be screenshotted holding something.
        if (System.getenv("MCCLONE_AUTOTEST_HELD") != null) {
            try {
                BlockType held = BlockType.valueOf(System.getenv("MCCLONE_AUTOTEST_HELD"));
                player.getInventory().setSlot(0, held, 1);
                selectedSlot[0] = 0;
            } catch (IllegalArgumentException ignored) {
                System.err.println("MCCLONE_AUTOTEST_HELD: unknown block " + System.getenv("MCCLONE_AUTOTEST_HELD"));
            }
        }
        // Opt-in autotest hook: start the hand swing animation from the first frame
        // (place/use/break), so a mid-swing screenshot can be captured.
        if (System.getenv("MCCLONE_AUTOTEST_SWING") != null) {
            handRenderer.triggerSwing();
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
            // The calendar advances with the day/night cycle, and its season
            // feeds the cycle's daylight length back - so days grow and shrink
            // through the year (long summer days, short winter days).
            calendar.update(dayNightCycle.getDayIndex());
            dayNightCycle.setDaylightFraction(calendar.daylightFraction());
            if (world != null) {
                TerrainGenerator.Biome playerBiome = world.getBiome(
                        (int) Math.floor(player.getPosition().x), (int) Math.floor(player.getPosition().z));
                climate.update(dt, playerBiome);
                // Rain/snow falls around the player's eye, scaled by the weather.
                Vector3f eye = player.getEyePosition();
                weatherParticles.update(dt, eye.x, eye.y, eye.z, climate.getWeather(), climate.getWeatherStrength());
            }
            animTime[0] += dt;
            attackCooldown[0] -= dt;
            Raycaster.Hit hit = null;
            float breakFraction = 0f;
            boolean screenshotRequested = false;

            // Main menu / world select / world-gen page input (before a world starts).
            if (!started[0]) {
                if (mainSettingsOpen[0]) {
                    // Settings page opened from the main menu: same controls as the
                    // in-game Esc menu, but Esc returns to the main menu.
                    handleSettingsMenuInput(input, settings, settingsFile, world, player, window, hud,
                            menuSelection, sliderDragRow, bindingAction, settingsTab);
                    if (input.isKeyJustPressed(GLFW_KEY_ESCAPE) && bindingAction[0] < 0) {
                        mainSettingsOpen[0] = false;
                        sliderDragRow[0] = -1;
                        mainMenuSelection[0] = Hud.MENU_SETTINGS;
                    }
                } else if (worldGenOpen[0]) {
                    if (editingRow[0] >= 0) {
                        String typed = input.consumeTypedChars();
                        boolean nameRow = editingRow[0] == WorldGenSettings.ROW_NAME;
                        StringBuilder sb = new StringBuilder(nameRow ? genSettings.getName() : genSettings.getSeedText());
                        for (int i = 0; i < typed.length(); i++) {
                            char ch = typed.charAt(i);
                            if (Character.isLetterOrDigit(ch) || ch == '-' || (nameRow && ch == ' ')) sb.append(ch);
                        }
                        if (input.isKeyJustPressed(GLFW_KEY_BACKSPACE)) {
                            if (sb.length() > 0) sb.deleteCharAt(sb.length() - 1);
                        }
                        if (nameRow) genSettings.setName(sb.toString());
                        else genSettings.setSeedText(sb.toString());
                        if (input.isKeyJustPressed(GLFW_KEY_ENTER) || input.isKeyJustPressed(GLFW_KEY_ESCAPE)) {
                            editingRow[0] = -1;
                        }
                    } else {
                        if (input.isKeyJustPressed(GLFW_KEY_UP) || input.isKeyJustPressed(GLFW_KEY_W)) {
                            worldGenSelection[0] = Math.floorMod(worldGenSelection[0] - 1, WorldGenSettings.ROW_COUNT + 1);
                        }
                        if (input.isKeyJustPressed(GLFW_KEY_DOWN) || input.isKeyJustPressed(GLFW_KEY_S)) {
                            worldGenSelection[0] = Math.floorMod(worldGenSelection[0] + 1, WorldGenSettings.ROW_COUNT + 1);
                        }
                        if (input.isKeyJustPressed(GLFW_KEY_LEFT)) {
                            genSettings.adjust(worldGenSelection[0], -1);
                        }
                        if (input.isKeyJustPressed(GLFW_KEY_RIGHT)) {
                            genSettings.adjust(worldGenSelection[0], +1);
                        }
                        // Mouse: hover to select a row, click to edit/adjust/Done (same as Enter).
                        boolean clickedGen = false;
                        float genLx = ((float) input.getMouseX() / window.getWidth() * 2f - 1f) * window.getAspectRatio();
                        float genLy = 1f - (float) input.getMouseY() / window.getHeight() * 2f;
                        int hoverGen = hud.worldGenRowAt(genLx, genLy);
                        if (hoverGen >= 0) {
                            worldGenSelection[0] = hoverGen;
                            clickedGen = input.isMouseJustPressed(GLFW_MOUSE_BUTTON_LEFT);
                        }
                        if (input.isKeyJustPressed(GLFW_KEY_ENTER) || input.isKeyJustPressed(GLFW_KEY_SPACE) || clickedGen) {
                            if (worldGenSelection[0] == WorldGenSettings.ROW_NAME || worldGenSelection[0] == WorldGenSettings.ROW_SEED) {
                                editingRow[0] = worldGenSelection[0];
                                input.consumeTypedChars();
                            } else if (worldGenSelection[0] == WorldGenSettings.ROW_COUNT) {
                                Path worldDir = saveRoot.resolve(genSettings.getName());
                                saveWorldGenSettings(worldDir, genSettings);
                                long seed = genSettings.resolveSeed();
                                if (genSettings.isSeedBlank()) {
                                    genSettings.setSeedText(Long.toString(seed));
                                    saveWorldGenSettings(worldDir, genSettings);
                                }
                                world = new World(seed, genSettings, atlas, worldDir);
                                startCalendar(dayNightCycle, calendar, genSettings);
                            world.setRenderDistance(settings.getRenderDistance());
                                world.setLeavesTransparent(settings.isLeavesTransparent());
                                for (int i = 0; i < 200; i++) world.update(0, 0);
                                float[] spawn = findSpawn(world);
                                player.spawn(world, spawn[0], spawn[1]);
                                for (int i = 0; i < 80; i++) world.update(player.getPosition().x, player.getPosition().z);
                                world.spawnInitialMobs(new Random(), player.getPosition().x, player.getPosition().z, 12);
                                System.out.println("World: " + genSettings.getName() + " seed: " + seed);
                                started[0] = true;
                                mainMenuOpen[0] = false;
                                worldSelectOpen[0] = false;
                                worldGenOpen[0] = false;
                                window.setCursorCaptured(true);
                                input.resetMouseDelta();
                            } else {
                                genSettings.adjust(worldGenSelection[0], +1);
                            }
                        }
                        if (input.isKeyJustPressed(GLFW_KEY_ESCAPE)) {
                            worldGenOpen[0] = false;
                            worldSelectOpen[0] = true;
                            worldSelectSelection[0] = worldNames.size();
                        }
                    }
                } else if (worldSelectOpen[0]) {
                    int totalWorlds = worldNames.size() + 1;
                    if (input.isKeyJustPressed(GLFW_KEY_UP) || input.isKeyJustPressed(GLFW_KEY_W)) {
                        worldSelectSelection[0] = Math.floorMod(worldSelectSelection[0] - 1, totalWorlds);
                    }
                    if (input.isKeyJustPressed(GLFW_KEY_DOWN) || input.isKeyJustPressed(GLFW_KEY_S)) {
                        worldSelectSelection[0] = Math.floorMod(worldSelectSelection[0] + 1, totalWorlds);
                    }
                    // Mouse: hover to select a world / the Create New World button,
                    // click to play it (or open the world-gen page), same as Enter.
                    boolean clickedWorld = false;
                    float wsLx = ((float) input.getMouseX() / window.getWidth() * 2f - 1f) * window.getAspectRatio();
                    float wsLy = 1f - (float) input.getMouseY() / window.getHeight() * 2f;
                    int hoverWorld = hud.worldSelectItemAt(wsLx, wsLy, worldNames.size());
                    if (hoverWorld >= 0) {
                        worldSelectSelection[0] = hoverWorld;
                        clickedWorld = input.isMouseJustPressed(GLFW_MOUSE_BUTTON_LEFT);
                    }
                    if (input.isKeyJustPressed(GLFW_KEY_ENTER) || input.isKeyJustPressed(GLFW_KEY_SPACE) || clickedWorld) {
                        if (worldSelectSelection[0] < worldNames.size()) {
                            genSettings = loadWorldGenSettings(saveRoot.resolve(worldNames.get(worldSelectSelection[0])));
                            Path worldDir = saveRoot.resolve(genSettings.getName());
                            long seed = genSettings.resolveSeed();
                            if (genSettings.isSeedBlank()) {
                                genSettings.setSeedText(Long.toString(seed));
                                saveWorldGenSettings(worldDir, genSettings);
                            }
                            world = new World(seed, genSettings, atlas, worldDir);
                            startCalendar(dayNightCycle, calendar, genSettings);
                            world.setRenderDistance(settings.getRenderDistance());
                            world.setLeavesTransparent(settings.isLeavesTransparent());
                            for (int i = 0; i < 200; i++) world.update(0, 0);
                            float[] spawn = findSpawn(world);
                            player.spawn(world, spawn[0], spawn[1]);
                            for (int i = 0; i < 80; i++) world.update(player.getPosition().x, player.getPosition().z);
                            world.spawnInitialMobs(new Random(), player.getPosition().x, player.getPosition().z, 12);
                            System.out.println("World: " + genSettings.getName() + " seed: " + seed);
                            started[0] = true;
                            mainMenuOpen[0] = false;
                            worldSelectOpen[0] = false;
                            window.setCursorCaptured(true);
                            input.resetMouseDelta();
                        } else {
                            genSettings = new WorldGenSettings();
                            genSettings.setName(uniqueWorldName(worldNames));
                            worldGenOpen[0] = true;
                            worldGenSelection[0] = 0;
                        }
                    }
                    if (input.isKeyJustPressed(GLFW_KEY_ESCAPE)) {
                        worldSelectOpen[0] = false;
                        mainMenuOpen[0] = true;
                    }
                } else {
                    // Main menu: navigate with arrows/WASD, or hover + click with the mouse.
                    if (input.isKeyJustPressed(GLFW_KEY_UP) || input.isKeyJustPressed(GLFW_KEY_W)) {
                        mainMenuSelection[0] = Math.floorMod(mainMenuSelection[0] - 1, Hud.MENU_COUNT);
                    }
                    if (input.isKeyJustPressed(GLFW_KEY_DOWN) || input.isKeyJustPressed(GLFW_KEY_S)) {
                        mainMenuSelection[0] = Math.floorMod(mainMenuSelection[0] + 1, Hud.MENU_COUNT);
                    }
                    boolean clickedMenu = false;
                    float mMenuLx = ((float) input.getMouseX() / window.getWidth() * 2f - 1f) * window.getAspectRatio();
                    float mMenuLy = 1f - (float) input.getMouseY() / window.getHeight() * 2f;
                    int hoverMenu = hud.mainMenuItemAt(mMenuLx, mMenuLy);
                    if (hoverMenu >= 0) {
                        mainMenuSelection[0] = hoverMenu;
                        clickedMenu = input.isMouseJustPressed(GLFW_MOUSE_BUTTON_LEFT);
                    }
                    if (input.isKeyJustPressed(GLFW_KEY_ENTER) || input.isKeyJustPressed(GLFW_KEY_SPACE) || clickedMenu) {
                        if (mainMenuSelection[0] == Hud.MENU_PLAY) {
                            worldNames = listWorlds(saveRoot);
                            worldSelectOpen[0] = true;
                            worldSelectSelection[0] = 0;
                        } else if (mainMenuSelection[0] == Hud.MENU_SETTINGS) {
                            mainSettingsOpen[0] = true;
                            settingsTab[0] = Settings.TAB_GRAPHICS;
                            menuSelection[0] = 0;
                            sliderDragRow[0] = -1;
                            bindingAction[0] = -1;
                        } else if (mainMenuSelection[0] == Hud.MENU_QUIT) {
                            glfwSetWindowShouldClose(window.getHandle(), true);
                        }
                    }
                    if (input.isKeyJustPressed(GLFW_KEY_ESCAPE)) {
                        glfwSetWindowShouldClose(window.getHandle(), true);
                    }
                }
            } else {
            if (input.isKeyJustPressed(GLFW_KEY_ESCAPE)) {
                if (bindingAction[0] >= 0) {
                    bindingAction[0] = -1; // Esc cancels a keybind capture
                } else if (inventoryOpen[0]) {
                    closeInventory(inventoryController, activeGui, inventoryGui, inventoryOpen);
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
                    closeInventory(inventoryController, activeGui, inventoryGui, inventoryOpen);
                } else if (creativeOpen[0]) {
                    closeCreative(inventoryController, creativeOpen);
                } else if (settings.getGameMode().isCreative()) {
                    creativeOpen[0] = true;
                    menuOpen[0] = false;
                } else {
                    activeGui[0] = inventoryGui;
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
                hoveredSlot[0] = hud.containerSlotAt(activeGui[0], logicalX, logicalY);

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
                handleSettingsMenuInput(input, settings, settingsFile, world, player, window, hud,
                        menuSelection, sliderDragRow, bindingAction, settingsTab);
            }

            if (input.isKeyJustPressed(settings.getKeyBinds().get(KeyBindings.DEBUG))) {
                showDebug[0] = !showDebug[0];
            }
            screenshotRequested = input.isKeyJustPressed(settings.getKeyBinds().get(KeyBindings.SCREENSHOT));

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

            // Furnaces (and any other block entities) work in the background,
            // ticking forward with world time.
            world.tickBlockEntities(dt);

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
                if (world != null) world.saveAllModified();
            }

            hit = null;
            breakFraction = 0f;

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

                // A cell can hold an overlay decoration inside its primary block (e.g.
                // seaweed inside water - see BlockType#isSubmersible); aiming at one
                // targets the decoration itself, the same way Minecraft lets you break
                // waterlogged seagrass without touching the water it's growing in.
                BlockType targetOverlay = hit != null ? world.getOverlay(hit.blockPos.x, hit.blockPos.y, hit.blockPos.z) : BlockType.AIR;
                boolean targetingOverlay = targetOverlay != BlockType.AIR;
                BlockType targetType = targetingOverlay ? targetOverlay
                        : hit != null ? world.getBlock(hit.blockPos.x, hit.blockPos.y, hit.blockPos.z) : BlockType.AIR;
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
                        handRenderer.triggerSwing();
                        int bx = hit.blockPos.x, by = hit.blockPos.y, bz = hit.blockPos.z;
                        if (Mining.isHammer(heldItem)) {
                            // A hammer mines a 3x3 area: the target block plus its
                            // eight horizontal neighbours, all in one swing.
                            for (int dx = -1; dx <= 1; dx++) {
                                for (int dz = -1; dz <= 1; dz++) {
                                    breakBlockAt(world, player, mode, heldItem, loot, messages, bx + dx, by, bz + dz);
                                }
                            }
                        } else {
                            breakBlockAt(world, player, mode, heldItem, loot, messages, bx, by, bz);
                        }
                    }
                }

                // Right-click: toggle a door/trapdoor, place a block, or eat food.
                // (Never interact through a mob - check targetedMobRef first.)
                if (input.isMouseJustPressed(GLFW_MOUSE_BUTTON_RIGHT) && hit != null) {
                    boolean noMob = targetedMobRef[0] == null;
                    BlockType targeted = world.getBlock(hit.blockPos.x, hit.blockPos.y, hit.blockPos.z);
                    if (Door.isDoor(targeted)) {
                        if (noMob && mode.canPlace()) {
                            Door.toggle(world, world::setBlock, hit.blockPos.x, hit.blockPos.y, hit.blockPos.z);
                            handRenderer.triggerSwing();
                        }
                    } else if (Door.isTrapdoor(targeted)) {
                        if (noMob && mode.canPlace()) {
                            Door.toggleSingle(world, world::setBlock, hit.blockPos.x, hit.blockPos.y, hit.blockPos.z);
                            handRenderer.triggerSwing();
                        }
                    } else if (noMob && targeted == BlockType.FURNACE) {
                        // Right-click a furnace to open its smelting gui.
                        Furnace furnace = world.getOrCreateFurnace(hit.blockPos.x, hit.blockPos.y, hit.blockPos.z);
                        activeGui[0] = new ContainerGui(ContainerGui.Kind.FURNACE, player.getInventory(), craftingGrid, furnace);
                        openGui(inventoryController, activeGui, window, input, inventoryOpen);
                    } else if (noMob && targeted == BlockType.CRAFTING_TABLE) {
                        // Right-click a crafting table to open the 3x3 crafting gui.
                        activeGui[0] = new ContainerGui(ContainerGui.Kind.CRAFTING_TABLE, player.getInventory(), craftingGrid, null);
                        openGui(inventoryController, activeGui, window, input, inventoryOpen);
                    } else if (noMob && mode.canPlace() && heldItem != null) {
                        if (heldItem.isEdible() && !mode.isCreative()) {
                            player.eat(heldItem);
                            handRenderer.triggerSwing();
                        } else if (!heldItem.isItem) {
                            // Pure inventory items (tools, and any future non-edible item)
                            // have no world tile and can never be placed as a block.
                            Vector3i p = hit.placePos;
                            // A submersible decoration (seaweed) placed where a fluid
                            // already is grows inside it instead of replacing it - the
                            // same rule world-gen follows (see TerrainGenerator). If that
                            // cell's overlay slot is already taken, there's simply nowhere
                            // to put it - skip placing rather than fall through and
                            // overwrite the fluid it would have grown inside.
                            boolean targetIsFluid = world.getBlock(p.x, p.y, p.z).isFluid();
                            boolean overlayFull = world.getOverlay(p.x, p.y, p.z) != BlockType.AIR;
                            boolean blocked = heldItem.isSubmersible() ? (targetIsFluid && overlayFull) : false;
                            boolean intoFluid = heldItem.isSubmersible() && targetIsFluid && !overlayFull;
                            if (!blocked && !intersectsPlayer(player, p)) {
                                boolean placed = mode.isCreative() || player.getInventory().remove(heldItem, 1);
                                if (placed) {
                                    handRenderer.triggerSwing();
                                    if (intoFluid) {
                                        world.setOverlay(p.x, p.y, p.z, heldItem);
                                    } else {
                                        world.setBlock(p.x, p.y, p.z, heldItem);
                                    }
                                    // Doors, trapdoors, and other directional blocks
                                    // (e.g. a furnace) face the player: the front sits
                                    // on the side of the block nearest to them (opposite
                                    // the look direction).
                                    if (heldItem.isDirectional() || heldItem == BlockType.DOOR || heldItem == BlockType.TRAPDOOR) {
                                        Vector3f front = player.getCamera().getFront();
                                        byte facing = (byte) (Math.abs(front.x) >= Math.abs(front.z)
                                                ? (front.x >= 0 ? 3 : 2)
                                                : (front.z >= 0 ? 1 : 0));
                                        world.setBlockOrientation(p.x, p.y, p.z, facing);
                                        // A door is 2 blocks tall: also place the top half.
                                        if (heldItem == BlockType.DOOR && world.getBlock(p.x, p.y + 1, p.z) == BlockType.AIR) {
                                            world.setBlock(p.x, p.y + 1, p.z, BlockType.DOOR);
                                            world.setBlockOrientation(p.x, p.y + 1, p.z, facing);
                                        }
                                    }
                                }
                            }
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

            if (started[0]) {
            chunkShader.bind();
            chunkShader.setUniform("projection", projection);
            chunkShader.setUniform("view", view);
            chunkShader.setUniform("atlas", 0);
            chunkShader.setUniform("fogColor", horizonColor);
            chunkShader.setUniform("fogStart", (world.getRenderDistance() - 2) * 16f);
            chunkShader.setUniform("fogEnd", world.getRenderDistance() * 16f);
            chunkShader.setUniform("ambientBrightness", dayNightCycle.getAmbientBrightness() * settings.getBrightness());
            chunkShader.setUniform("time", animTime[0]);
            chunkShader.setUniform("atlasGrid", (float) TextureAtlas.GRID);
            atlas.bind();
            world.render(chunkShader, projection, view);
            itemRenderer.render(chunkShader, atlas, itemTextures, world.getItems(), player.getCamera());
            mobRenderer.render(mobTextures, world.getMobs(), world.getArrows());
            chunkShader.unbind();
            }

            // Rain/snow particles, drawn against the world (depth-tested) but
            // hidden behind menus just like the crosshair and hotbar.
            if (started[0] && !menuOpen[0] && !inventoryOpen[0] && !creativeOpen[0]) {
                weatherRenderer.render(lineShader, projection, view, weatherParticles);
            }

            // First-person held item (Minecraft-style): drawn after the world so it
            // always sits on top, hidden while any menu/inventory is up and in
            // spectator (no hand to look at).
            if (started[0] && !menuOpen[0] && !inventoryOpen[0] && !creativeOpen[0]
                    && !settings.getGameMode().isSpectator()) {
                handRenderer.render(chunkShader, atlas, itemTextures,
                        player.getInventory().typeOf(selectedSlot[0]),
                        player.getBobPhase(), animTime[0], dt, projection);
            }

            if (started[0] && !menuOpen[0] && !inventoryOpen[0] && !creativeOpen[0]) {
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
            if (showDebug[0] && world != null) {
                Vector3f pos = player.getPosition();
                float aspect = window.getAspectRatio();
                float textSize = 0.035f;
                float y = 0.95f;
                float step = 0.052f;
                int line = 0;
                hud.drawTextLeft(String.format(Locale.ROOT, "FPS: %d (%.1f ms/frame)", timer.getFps(), timer.getDeltaTime() * 1000f),
                        -0.95f, y - (line++) * step, textSize, WHITE, aspect);
                hud.drawTextLeft(String.format(Locale.ROOT, "XYZ: %.1f / %.1f / %.1f", pos.x, pos.y, pos.z),
                        -0.95f, y - (line++) * step, textSize, WHITE, aspect);
                int chunkX = World.worldToChunk((int) Math.floor(pos.x));
                int chunkZ = World.worldToChunk((int) Math.floor(pos.z));
                hud.drawTextLeft(String.format(Locale.ROOT, "Chunk: %d, %d (local %d, %d)",
                                chunkX, chunkZ, Math.floorMod((int) Math.floor(pos.x), 16), Math.floorMod((int) Math.floor(pos.z), 16)),
                        -0.95f, y - (line++) * step, textSize, WHITE, aspect);
                Vector3f front = player.getCamera().getFront();
                String facing = Math.abs(front.x) >= Math.abs(front.z)
                        ? (front.x >= 0 ? "east (+X)" : "west (-X)")
                        : (front.z >= 0 ? "south (+Z)" : "north (-Z)");
                hud.drawTextLeft(String.format(Locale.ROOT, "Facing: %s (yaw %.1f, pitch %.1f)",
                                facing, player.getCamera().getYaw(), player.getCamera().getPitch()),
                        -0.95f, y - (line++) * step, textSize, WHITE, aspect);
                BlockType sel = player.getInventory().typeOf(selectedSlot[0]);
                hud.drawTextLeft("Selected: " + (sel == null ? "-" : sel.toString()),
                        -0.95f, y - (line++) * step, textSize, WHITE, aspect);
                TerrainGenerator.Biome biome = world.getBiome((int) Math.floor(pos.x), (int) Math.floor(pos.z));
                hud.drawTextLeft("Biome: " + biome,
                        -0.95f, y - (line++) * step, textSize, WHITE, aspect);
                hud.drawTextLeft("Season: " + calendar.getSeason().displayName + " - Day " + calendar.getDay()
                                + "/" + calendar.getDaysPerSeason() + ", Year " + calendar.getYear(),
                        -0.95f, y - (line++) * step, textSize, WHITE, aspect);
                hud.drawTextLeft(String.format(Locale.ROOT, "Weather: %s (%.0f%%) - next: %s in %.0fm",
                                climate.getWeather().displayName, climate.getWeatherStrength() * 100f,
                                climate.getNextWeather().displayName, climate.getWeatherTimeLeft() / 60f),
                        -0.95f, y - (line++) * step, textSize, WHITE, aspect);
                hud.drawTextLeft(String.format(Locale.ROOT, "Climate: %.1f C, %.0f%% humidity",
                                climate.temperatureFor(biome), climate.humidityFor(biome) * 100f),
                        -0.95f, y - (line++) * step, textSize, WHITE, aspect);
                hud.drawTextLeft(String.format(Locale.ROOT, "Chunks: %d visible / %d loaded (render distance %d)",
                                world.getVisibleChunkCount(), world.getLoadedChunkCount(), world.getRenderDistance()),
                        -0.95f, y - (line++) * step, textSize, WHITE, aspect);
                hud.drawTextLeft(String.format(Locale.ROOT, "Entities: %d mobs, %d items", world.getMobs().size(), world.getItems().size()),
                        -0.95f, y - (line++) * step, textSize, WHITE, aspect);
                Runtime rt = Runtime.getRuntime();
                long usedMb = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024);
                long maxMb = rt.maxMemory() / (1024 * 1024);
                hud.drawTextLeft(String.format(Locale.ROOT, "Memory: %d / %d MB", usedMb, maxMb),
                        -0.95f, y - (line++) * step, textSize, WHITE, aspect);

                // Whatever the crosshair is currently aimed at, recomputed independently
                // of the break/place handling above (which only runs with no menu open) -
                // the debug overlay should keep showing the last-aimed block even while a
                // menu's open, same as the crosshair target itself doesn't move.
                if (hit != null) {
                    Vector3i bp = hit.blockPos;
                    BlockType primary = world.getBlock(bp.x, bp.y, bp.z);
                    BlockType overlay = world.getOverlay(bp.x, bp.y, bp.z);
                    BlockType looking = overlay != BlockType.AIR ? overlay : primary;
                    hud.drawTextLeft(String.format(Locale.ROOT, "Looking at: %s @ %d, %d, %d", looking, bp.x, bp.y, bp.z),
                            -0.95f, y - (line++) * step, textSize, WHITE, aspect);
                    if (overlay != BlockType.AIR) {
                        hud.drawTextLeft("  (growing in " + primary + ")",
                                -0.95f, y - (line++) * step, textSize, WHITE, aspect);
                    }
                    if (primary.isFluid()) {
                        hud.drawTextLeft("  Fluid level: " + world.getFluidLevel(bp.x, bp.y, bp.z),
                                -0.95f, y - (line++) * step, textSize, WHITE, aspect);
                    }
                    BlockType heldItem = player.getInventory().typeOf(selectedSlot[0]);
                    String breakInfo = Mining.canBreak(looking, heldItem)
                            ? String.format(Locale.ROOT, "  Break time: %.2fs", Mining.breakTimeSeconds(looking, heldItem))
                            : "  Cannot break with current tool";
                    hud.drawTextLeft(breakInfo, -0.95f, y - (line++) * step, textSize, WHITE, aspect);
                } else {
                    hud.drawTextLeft("Looking at: nothing in range",
                            -0.95f, y - (line++) * step, textSize, WHITE, aspect);
                }
            }
            if (menuOpen[0]) {
                hud.renderSettingsMenu(settings, settingsTab[0], menuSelection[0], bindingAction[0], window.getAspectRatio());
            }
            if (!started[0]) {
                if (mainSettingsOpen[0]) {
                    hud.renderSettingsMenu(settings, settingsTab[0], menuSelection[0], bindingAction[0], window.getAspectRatio());
                } else if (worldGenOpen[0]) {
                    hud.renderWorldGenMenu(genSettings, worldGenSelection[0], editingRow[0], window.getAspectRatio());
                } else if (worldSelectOpen[0]) {
                    hud.renderWorldSelectMenu(worldNames, worldSelectSelection[0], window.getAspectRatio());
                } else {
                    hud.renderMainMenu(mainMenuSelection[0], window.getAspectRatio());
                }
            }
            if (inventoryOpen[0]) {
                // Scale mouse X back by aspect to match the HUD's logical-square space.
                float logicalX = ((float) input.getMouseX() / window.getWidth() * 2f - 1f) * window.getAspectRatio();
                float logicalY = 1f - (float) input.getMouseY() / window.getHeight() * 2f;
                hud.renderContainerGui(activeGui[0], inventoryController, hoveredSlot[0],
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

        if (world != null) world.saveAllModified();
        settings.save(settingsFile);

        hud.destroy();
        itemRenderer.destroy();
        handRenderer.destroy();
        mobRenderer.destroy();
        weatherRenderer.destroy();
        chunkShader.destroy();
        lineShader.destroy();
        hudShader.destroy();
        skyShader.destroy();
        skyRenderer.destroy();
        atlas.destroy();
        itemTextures.destroy();
        mobTextures.destroy();
        font.destroy();
        if (world != null) world.destroy();
        window.close();
    }


    /** Names of the saved worlds (folders directly under {@code saveRoot}), sorted. */
    private static List<String> listWorlds(Path saveRoot) {
        List<String> names = new ArrayList<>();
        if (Files.isDirectory(saveRoot)) {
            try (var stream = Files.list(saveRoot)) {
                stream.filter(Files::isDirectory)
                        .filter(p -> !p.getFileName().toString().startsWith("."))
                        .map(p -> p.getFileName().toString())
                        .sorted()
                        .forEach(names::add);
            } catch (IOException ignored) {
            }
        }
        return names;
    }

    /** Loads a world's worldgen settings from its {@code world.txt}; the folder name is the default name. */
    private static WorldGenSettings loadWorldGenSettings(Path worldDir) {
        WorldGenSettings g = new WorldGenSettings();
        if (worldDir.getFileName() != null) {
            g.setName(worldDir.getFileName().toString());
        }
        Path file = worldDir.resolve("world.txt");
        if (Files.isRegularFile(file)) {
            try {
                for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                    int eq = line.indexOf('=');
                    if (eq > 0) {
                        g.loadEntry(line.substring(0, eq).trim(), line.substring(eq + 1).trim());
                    }
                }
            } catch (IOException ignored) {
            }
        }
        return g;
    }

    /** Writes a world's worldgen settings to its {@code world.txt}, creating the folder if needed. */
    private static void saveWorldGenSettings(Path worldDir, WorldGenSettings g) {
        List<String> lines = new ArrayList<>();
        g.saveLines(lines);
        try {
            Files.createDirectories(worldDir);
            Files.write(worldDir.resolve("world.txt"), lines, StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.err.println("Could not save world settings to " + worldDir + ": " + e.getMessage());
        }
    }

    /** A world name that doesn't collide with an existing save: "New World", "New World 2", ... */
    private static String uniqueWorldName(List<String> existing) {
        String base = "New World";
        if (!existing.contains(base)) return base;
        int n = 2;
        while (existing.contains(base + " " + n)) n++;
        return base + " " + n;
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
