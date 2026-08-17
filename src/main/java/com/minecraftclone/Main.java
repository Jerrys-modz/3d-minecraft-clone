package com.minecraftclone;

import com.minecraftclone.engine.*;
import com.minecraftclone.engine.audio.AudioEngine;
import com.minecraftclone.engine.audio.BlockAction;
import com.minecraftclone.engine.audio.SoundCategory;
import com.minecraftclone.engine.audio.SoundEvent;
import com.minecraftclone.engine.audio.SoundMaterial;
import com.minecraftclone.engine.graphics.FontAtlas;
import com.minecraftclone.engine.graphics.GuiTextures;
import com.minecraftclone.engine.graphics.HandRenderer;
import com.minecraftclone.engine.graphics.ItemRenderer;
import com.minecraftclone.engine.graphics.ItemTextures;
import com.minecraftclone.engine.graphics.MobRenderer;
import com.minecraftclone.engine.graphics.MobTextures;
import com.minecraftclone.engine.graphics.SkyRenderer;
import com.minecraftclone.engine.graphics.TextureAtlas;
import com.minecraftclone.engine.graphics.WeatherRenderer;
import com.minecraftclone.engine.gui.ContainerGui;
import com.minecraftclone.player.Armor;
import com.minecraftclone.player.CraftingGrid;
import com.minecraftclone.player.CreativeCatalog;
import com.minecraftclone.player.Inventory;
import com.minecraftclone.player.InventoryController;
import com.minecraftclone.player.JoinedStorage;
import com.minecraftclone.player.MiningController;
import com.minecraftclone.player.Player;
import com.minecraftclone.player.PlayerStats;
import com.minecraftclone.player.StorageContainer;
import com.minecraftclone.util.AABB;
import com.minecraftclone.util.Raycaster;
import com.minecraftclone.util.ResourceLoader;
import com.minecraftclone.world.Barrel;
import com.minecraftclone.world.Chest;
import com.minecraftclone.world.BlockType;
import com.minecraftclone.world.Chunk;
import com.minecraftclone.world.DimensionType;
import com.minecraftclone.world.Door;
import com.minecraftclone.world.Furnace;
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
    /** How long after teleporting through a portal before another portal can trigger (so you don't bounce straight back). */
    private static final float PORTAL_COOLDOWN_SECONDS = 2.0f;
    /** Overworld/nether coordinate ratio, Minecraft-style (1 block in the nether = 8 in the overworld). */
    private static final float NETHER_SCALE = 8f;

    private static final int APPLE_DROP_CHANCE = 8;    // 1 in 8 leaves broken also yield an apple

    // Weather sky tints: overcast skies lean toward these greys, and a lightning
    // flash briefly washes the sky toward white.
    private static final Vector3f OVERCAST_HORIZON = new Vector3f(0.40f, 0.42f, 0.46f);
    private static final Vector3f OVERCAST_ZENITH = new Vector3f(0.28f, 0.30f, 0.34f);
    private static final Vector3f FLASH_COLOR = new Vector3f(0.85f, 0.92f, 1.0f);
    private static final int BERRIES_PER_BUSH = 2;

    private static final float FOOTSTEP_INTERVAL = 0.38f; // seconds between footstep sounds while walking on the ground
    private static final float SWIM_STROKE_INTERVAL = 0.55f; // seconds between stroke sounds while swimming and moving
    // Minimum time between two splash sounds. isInWater() samples the whole
    // body hitbox against water, so standing/floating with the hitbox edge
    // sitting almost exactly on a water surface's block boundary - e.g. right
    // after breaking the block that was keeping you just above it, or
    // treading water at the surface - lets ordinary per-frame physics noise
    // walk that sample back and forth across the boundary every frame. Without
    // a cooldown, each flicker fired its own splash, which sounded like a
    // continuous harsh clicking rather than the occasional in/out splash it
    // was meant to be.
    private static final float SPLASH_COOLDOWN_SECONDS = 0.25f;

    /** Procedurally-generated GUI art (light/dark panels and slots), shared by every screen. */
    private GuiTextures guiTextures;

    /** HUD renderer; referenced by {@link #applySettings} to push the GUI theme live. */
    private Hud hud;

    private static final Vector4f WHITE = new Vector4f(1f, 1f, 1f, 1f);
    private static final Vector3f WORLD_UP = new Vector3f(0f, 1f, 0f);

    // Per-dimension sky/fog look (the overworld uses the day/night cycle).
    private static final Vector3f NETHER_HORIZON = new Vector3f(0.28f, 0.07f, 0.03f);
    private static final Vector3f NETHER_ZENITH = new Vector3f(0.16f, 0.04f, 0.02f);
    private static final Vector3f END_HORIZON = new Vector3f(0.03f, 0.03f, 0.06f);
    private static final Vector3f END_ZENITH = new Vector3f(0.01f, 0.01f, 0.02f);
    private static final float NETHER_AMBIENT = 0.7f;   // dim lava/glowstone self-light
    private static final float END_AMBIENT = 0.45f;     // dim, star-lit void

    /** Queues a transient on-screen message (rendered via {@link Hud#renderMessages}). */
    private static void showMessage(List<Hud.Message> messages, String text, Vector4f color, float duration) {
        messages.add(new Hud.Message(text, color, duration));
    }

    /** Pushes the current in-memory {@link Settings} into every world/renderer/player/audio. */
    private void applySettings(Settings settings, World[] worlds, Player player, Window window, AudioEngine audio) {
        if (worlds != null) {
            for (World world : worlds) {
                world.setLeavesTransparent(settings.isLeavesTransparent());
                world.setRenderDistance(settings.getRenderDistance());
            }
        }
        window.setVsync(settings.isVsync());
        player.setMouseSensitivity(settings.getMouseSensitivity());
        player.setGameMode(settings.getGameMode());
        player.setInvertMouseY(settings.isInvertMouseY());
        player.setViewBobbing(settings.isViewBobbing());
        hud.setGuiTextures(guiTextures, settings.isDarkGui());
        audio.setMasterVolume(settings.getSoundVolume());
        audio.setCategoryVolume(SoundCategory.MUSIC, settings.getMusicVolume());
        audio.setCategoryVolume(SoundCategory.AMBIENT, settings.getAmbientVolume());
        audio.setCategoryVolume(SoundCategory.MOBS, settings.getMobsVolume());
        audio.setCategoryVolume(SoundCategory.MACHINES, settings.getMachinesVolume());
        audio.setCategoryVolume(SoundCategory.PLAYER, settings.getPlayerVolume());
        audio.setCategoryVolume(SoundCategory.UI, settings.getUiVolume());
    }

    /**
     * Shared keyboard + mouse interaction for the settings page, used both by the
     * in-game Esc menu and the main-menu Settings button. {@code world} may be
     * null (main menu) - {@link #applySettings} tolerates that. {@code tab}
     * selects the active section (Graphics / Gameplay / Controls); navigation
     * wraps within the active tab's rows, and Tab (or clicking a tab) switches.
     */
    private void handleSettingsMenuInput(Input input, Settings settings, Path settingsFile, World[] worlds,
                                         Player player, Window window, Hud hud, AudioEngine audio,
                                         int[] menuSelection, int[] sliderDragRow, int[] bindingAction,
                                         int[] settingsTab) {
        int tab = settingsTab[0];
        boolean bindingTab = tab == Settings.TAB_CONTROLS || tab == Settings.TAB_CONTROLLER;
        int rows = tab == Settings.TAB_CONTROLS ? KeyBindings.COUNT
                : tab == Settings.TAB_CONTROLLER ? GamepadBindings.COUNT
                : Settings.tabRowCount(tab);

        // Tab key switches to the next section; the selection resets to the top.
        if (input.isKeyJustPressed(GLFW_KEY_TAB)) {
            settingsTab[0] = (tab + 1) % Settings.TAB_COUNT;
            menuSelection[0] = 0;
            sliderDragRow[0] = -1;
            bindingAction[0] = -1;
            return;
        }

        if (bindingAction[0] >= 0) {
            if (tab == Settings.TAB_CONTROLLER) {
                // Capturing a gamepad button for a Controller-tab row: bind the
                // next button pressed. A/X/B/Start can't be captured this way -
                // they're reserved for confirm/right-click/back/menu, and each
                // one's own press fires that reserved action (including, for
                // B/Start, this same Esc-equivalent cancel) before it could ever
                // be read here as "the new binding". Esc from the keyboard also
                // cancels, same as on the Controls tab.
                if (input.isKeyJustPressed(GLFW_KEY_ESCAPE)) {
                    bindingAction[0] = -1;
                } else {
                    int pressed = input.consumeLastGamepadButtonPressed();
                    if (pressed >= 0) {
                        settings.getGamepadBinds().set(bindingAction[0], pressed);
                        settings.save(settingsFile);
                        bindingAction[0] = -1;
                        return;
                    }
                }
            } else {
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
            }
        } else {
            // Navigate with arrows/WASD; toggle/step settings or start a
            // keybind/gamepad-binding capture with Enter/Space/Left/Right.
            if (input.isKeyJustPressed(GLFW_KEY_UP) || input.isKeyJustPressed(GLFW_KEY_W)) {
                menuSelection[0] = Math.floorMod(menuSelection[0] - 1, rows);
            }
            if (input.isKeyJustPressed(GLFW_KEY_DOWN) || input.isKeyJustPressed(GLFW_KEY_S)) {
                menuSelection[0] = Math.floorMod(menuSelection[0] + 1, rows);
            }
            if (input.isKeyJustPressed(GLFW_KEY_LEFT)) {
                if (!bindingTab) {
                    adjustSettingsRow(settings, settingsFile, worlds, player, window, audio,
                            Settings.rowInTab(tab, menuSelection[0]), -1);
                }
            }
            if (input.isKeyJustPressed(GLFW_KEY_RIGHT)) {
                if (!bindingTab) {
                    adjustSettingsRow(settings, settingsFile, worlds, player, window, audio,
                            Settings.rowInTab(tab, menuSelection[0]), +1);
                }
            }
            if (input.isKeyJustPressed(GLFW_KEY_ENTER) || input.isKeyJustPressed(GLFW_KEY_SPACE)) {
                if (bindingTab) {
                    bindingAction[0] = menuSelection[0];
                    // Discard whatever triggered this capture, so it doesn't
                    // immediately get read back as *the new binding* itself.
                    input.consumeLastKeyPressed();
                    input.consumeLastGamepadButtonPressed();
                } else {
                    adjustSettingsRow(settings, settingsFile, worlds, player, window, audio,
                            Settings.rowInTab(tab, menuSelection[0]), +1);
                }
            }
        }

        // Mouse: hover to select, click a tab to switch, click a toggle, click
        // or drag a slider, or click a keybind/gamepad-binding row to start capturing it.
        float sLx = ((float) input.getMouseX() / window.getWidth() * 2f - 1f) * window.getAspectRatio();
        float sLy = 1f - (float) input.getMouseY() / window.getHeight() * 2f;
        int hoverTab = hud.settingsTabAt(sLx, sLy);
        if (hoverTab >= 0) {
            if (input.isMouseJustPressed(GLFW_MOUSE_BUTTON_LEFT) && hoverTab != tab) {
                settingsTab[0] = hoverTab;
                menuSelection[0] = 0;
                sliderDragRow[0] = -1;
                bindingAction[0] = -1;
                audio.play(SoundEvent.UI_CLICK);
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
                if (bindingTab) {
                    bindingAction[0] = clicked;
                    input.consumeLastKeyPressed();
                    input.consumeLastGamepadButtonPressed();
                    audio.play(SoundEvent.UI_CLICK);
                } else {
                    int row = Settings.rowInTab(tab, clicked);
                    if (Settings.isToggle(row)) {
                        adjustSettingsRow(settings, settingsFile, worlds, player, window, audio, row, +1);
                    } else {
                        float frac = hud.settingsTrackAt(sLx, sLy, tab);
                        if (frac >= 0f) {
                            settings.setFromFraction(row, frac);
                            applySettings(settings, worlds, player, window, audio);
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
            applySettings(settings, worlds, player, window, audio);
            settings.save(settingsFile);
        }
        if (!input.isMouseDown(GLFW_MOUSE_BUTTON_LEFT)) {
            sliderDragRow[0] = -1;
        }
    }

    /** Steps/toggles a single settings row and pushes the change everywhere it applies. */
    private void adjustSettingsRow(Settings settings, Path settingsFile, World[] worlds, Player player,
                                   Window window, AudioEngine audio, int row, int direction) {
        settings.adjust(row, direction);
        applySettings(settings, worlds, player, window, audio);
        settings.save(settingsFile);
        audio.play(SoundEvent.UI_CLICK);
    }

    /** Closes any open container screen (inventory/crafting table/furnace), returning cursor/grid items to the inventory. */
    private void closeInventory(InventoryController controller, ContainerGui[] activeGui, ContainerGui inventoryGui, boolean[] inventoryOpen, AudioEngine audio) {
        controller.returnGridToInventory();
        controller.returnCursorToInventory();
        activeGui[0] = inventoryGui;
        inventoryOpen[0] = false;
        audio.play(SoundEvent.UI_CLOSE);
    }

    /** Opens the given container gui, rebinding the controller and releasing the cursor for mouse use. */
    private void openGui(InventoryController controller, ContainerGui[] activeGui, Window window, Input input, boolean[] inventoryOpen, AudioEngine audio) {
        controller.setGui(activeGui[0]);
        inventoryOpen[0] = true;
        window.setCursorCaptured(false);
        input.resetMouseDelta();
        audio.play(SoundEvent.UI_OPEN);
    }

    /** Plays the "took a craft/smelt result" chime for the output slot, a generic click for anything else. */
    private void playSlotSound(AudioEngine audio, ContainerGui gui, int slotId) {
        audio.play(gui != null && gui.isOutputSlot(slotId) ? SoundEvent.CRAFT : SoundEvent.UI_CLICK);
    }

    /** Resets the calendar for a freshly started world and applies its weeks-per-month setting. */
    private void startCalendar(DayNightCycle dayNightCycle, Calendar calendar, WorldGenSettings genSettings) {
        dayNightCycle.resetDays();
        calendar.reset();
        calendar.setWeeksPerMonth(genSettings.getWeeksPerMonth());
    }

    /**
     * Breaks one block cell (whatever it is: a door, an overlay decoration, or a
     * solid block), dropping its loot and wearing the tool in survival. Shared by
     * the normal break and the hammer's 3x3 area mine.
     */
    private void breakBlockAt(World world, Player player, GameMode mode, BlockType heldItem, Random loot,
                              List<Hud.Message> messages, AudioEngine audio, int bx, int by, int bz) {
        BlockType overlay = world.getOverlay(bx, by, bz);
        boolean targetingOverlay = overlay != BlockType.AIR;
        BlockType targetType = targetingOverlay ? overlay : world.getBlock(bx, by, bz);
        if (targetType == BlockType.AIR || targetType == BlockType.BEDROCK) return;
        if (!Mining.canBreak(targetType, heldItem)) return; // e.g. an ore the hammer can't mine

        audio.playBlockSound(SoundMaterial.of(targetType), BlockAction.BREAK, bx + 0.5f, by + 0.5f, bz + 0.5f, 1f);

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
                if (targetType == BlockType.CHEST || targetType == BlockType.BARREL) {
                    // A broken chest/barrel spills its contents.
                    com.minecraftclone.player.StorageContainer storage = world.chestAt(bx, by, bz) != null
                            ? world.chestAt(bx, by, bz) : world.barrelAt(bx, by, bz);
                    if (storage != null) {
                        for (int s = 0; s < com.minecraftclone.world.Chest.SLOT_COUNT; s++) {
                            if (storage.typeOf(s) != null) {
                                world.spawnItem(bx, by, bz, storage.typeOf(s), storage.countOf(s), loot);
                            }
                        }
                        world.removeBlockEntity(bx, by, bz);
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
                audio.play(SoundEvent.TOOL_BREAK);
            }
        }
    }

    /** Closes the creative screen, returning any cursor item to the inventory. */
    private void closeCreative(InventoryController controller, boolean[] creativeOpen, AudioEngine audio) {
        controller.returnCursorToInventory();
        creativeOpen[0] = false;
        audio.play(SoundEvent.UI_CLOSE);
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
        guiTextures = new GuiTextures();
        guiTextures.generate();
        // Best-effort: a machine with no audio device at all (routine for a
        // headless CI/verification environment) leaves this permanently
        // disabled rather than throwing - see AudioEngine's own javadoc.
        AudioEngine audio = new AudioEngine();
        audio.init();

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
        World[] worlds = null;
        DimensionType[] currentDim = {DimensionType.OVERWORLD};
        boolean[] started = {false};
        List<String> worldNames = new ArrayList<>();

        hud = new Hud(lineShader, hudShader, font);
        hud.setGuiTextures(guiTextures, false); // theme is applied via applySettings below
        ItemRenderer itemRenderer = new ItemRenderer();
        HandRenderer handRenderer = new HandRenderer();
        MobRenderer mobRenderer = new MobRenderer();
        WeatherParticles weatherParticles = new WeatherParticles();
        WeatherRenderer weatherRenderer = new WeatherRenderer();
        List<LightningBolt> bolts = new ArrayList<>();
        Random lightningRnd = new Random(); // strike placement
        float prevFlash = 0f; // previous frame's lightning intensity - to catch a new flash
        List<Hud.Message> messages = new ArrayList<>();
        boolean[] showDebug = {false};
        boolean[] forecastOpen = {false};
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

        // Ensure the renderer/player/window/audio all match the loaded settings.
        applySettings(settings, worlds, player, window, audio);

        int[] selectedSlot = {0};
        Random loot = new Random();
        DayNightCycle dayNightCycle = new DayNightCycle();
        Calendar calendar = new Calendar(); // in-game calendar: days, seasons, years
        Climate climate = new Climate(calendar, dayNightCycle); // weather + biome temperature/humidity
        MiningController mining = new MiningController();
        float[] animTime = {0f}; // free-running clock driving the flowing-water/lava texture scroll
        float[] attackCooldown = {0f}; // time until the next mob hit can land
        Mob[] targetedMobRef = {null}; // the mob the crosshair is aimed at this frame, if any
        float[] footstepTimer = {0f}; // time until the next footstep sound while walking/sprinting on the ground
        float[] swimStrokeTimer = {0f}; // time until the next stroke sound while swimming and moving
        boolean[] wasInWater = {false}; // last frame's Player#isInWater(), to fire a splash sound only on the change
        float[] splashCooldown = {0f}; // time until another splash sound is allowed - see SPLASH_COOLDOWN_SECONDS

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
        // Autotest hook: strike a bolt in front of the camera on the final frame,
        // so the bolt + its fire can be screenshotted right as they appear.
        boolean forceLightning = System.getenv("MCCLONE_AUTOTEST_LIGHTNING") != null;
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
        if (System.getenv("MCCLONE_AUTOTEST_TEMP") != null) {
            climate.forceTemperature(Float.parseFloat(System.getenv("MCCLONE_AUTOTEST_TEMP")));
        }
        if (System.getenv("MCCLONE_AUTOTEST_FORECAST") != null) {
            forecastOpen[0] = true;
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
            worlds = new World[DimensionType.values().length];
            for (DimensionType dim : DimensionType.values()) {
                worlds[dim.ordinal()] = new World(seed, genSettings, atlas, autoDir, dim);
            }
            currentDim[0] = DimensionType.OVERWORLD;
            world = worlds[currentDim[0].ordinal()];
            startCalendar(dayNightCycle, calendar, genSettings);
            for (World w : worlds) {
                w.setRenderDistance(settings.getRenderDistance());
                w.setLeavesTransparent(settings.isLeavesTransparent());
            }
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
        // Diagnostic autotest hook: unlike MCCLONE_AUTOTEST_PLACE above (which
        // places directly via world.setBlock, bypassing the player entirely),
        // this drives the exact same placeOrEatHeldItem() a real right-click
        // calls - survival inventory consumption included - via a real
        // raycast from the camera. Logs inventory/world state before and
        // after so a "block vanishes from the hotbar but never appears"
        // report can be confirmed or ruled out headlessly.
        if (System.getenv("MCCLONE_AUTOTEST_PLACE_SURVIVAL") != null && started[0]) {
            try {
                BlockType toPlace = BlockType.valueOf(System.getenv("MCCLONE_AUTOTEST_PLACE_SURVIVAL"));
                player.setGameMode(GameMode.SURVIVAL);
                player.getInventory().add(toPlace, 1);
                int before = player.getInventory().getCount(toPlace);
                Raycaster.Hit testHit = Raycaster.cast(world, player.getEyePosition(), player.getCamera().getFront(), REACH_DISTANCE);
                if (testHit != null) {
                    BlockType worldBefore = world.getBlock(testHit.placePos.x, testHit.placePos.y, testHit.placePos.z);
                    placeOrEatHeldItem(world, player, GameMode.SURVIVAL, toPlace, testHit, audio, handRenderer);
                    int after = player.getInventory().getCount(toPlace);
                    BlockType worldAfter = world.getBlock(testHit.placePos.x, testHit.placePos.y, testHit.placePos.z);
                    // Differential check: try a raw world.setBlock at the exact same
                    // coordinates right after, to tell "the chunk there wasn't loaded
                    // yet" apart from "placeOrEatHeldItem's own logic didn't place it".
                    world.setBlock(testHit.placePos.x, testHit.placePos.y, testHit.placePos.z, BlockType.STONE);
                    BlockType worldAfterRawSet = world.getBlock(testHit.placePos.x, testHit.placePos.y, testHit.placePos.z);
                    System.out.println("AUTOTEST_PLACE_SURVIVAL: held=" + toPlace
                            + " invBefore=" + before + " invAfter=" + after
                            + " worldAfterRawSet=" + worldAfterRawSet
                            + " placePos=" + testHit.placePos.x + "," + testHit.placePos.y + "," + testHit.placePos.z
                            + " worldBefore=" + worldBefore + " worldAfter=" + worldAfter);
                } else {
                    System.out.println("AUTOTEST_PLACE_SURVIVAL: no raycast hit within reach");
                }
            } catch (IllegalArgumentException ignored) {
                System.err.println("MCCLONE_AUTOTEST_PLACE_SURVIVAL: unknown block " + System.getenv("MCCLONE_AUTOTEST_PLACE_SURVIVAL"));
            }
        }
        // Opt-in autotest hook: lay a short run of stairs (stepping up away from
        // the camera) or a row of fences (so their rails connect), for screenshots.
        if (System.getenv("MCCLONE_AUTOTEST_PARTIAL") != null && started[0]) {
            Vector3f front = player.getCamera().getFront();
            int px = (int) Math.floor(player.getPosition().x + front.x * 3f);
            int py = (int) Math.floor(player.getPosition().y) + 1;
            int pz = (int) Math.floor(player.getPosition().z + front.z * 3f);
            String mode = System.getenv("MCCLONE_AUTOTEST_PARTIAL");
            byte facing = (byte) (Math.abs(front.x) >= Math.abs(front.z)
                    ? (front.x >= 0 ? 3 : 2)
                    : (front.z >= 0 ? 1 : 0));
            if (mode.equals("stairs")) {
                // Each stair advances along its facing direction and climbs (increases Y).
                for (int i = 0; i < 4; i++) {
                    int stepX = px;
                    int stepZ = pz;
                    // Advance along the facing direction: facing 0=north(-Z), 1=south(+Z), 2=west(-X), 3=east(+X)
                    switch (facing) {
                        case 0 -> stepZ = pz - i;  // north
                        case 1 -> stepZ = pz + i;  // south
                        case 2 -> stepX = px - i;  // west
                        case 3 -> stepX = px + i;  // east
                    }
                    world.setBlock(stepX, py + i, stepZ, BlockType.STONE_STAIRS);
                    world.setBlockOrientation(stepX, py + i, stepZ, facing);
                }
                for (int i = 0; i < 4; i++) {
                    int stepX = px;
                    int stepZ = pz;
                    switch (facing) {
                        case 0 -> stepZ = pz - i;
                        case 1 -> stepZ = pz + i;
                        case 2 -> stepX = px - i;
                        case 3 -> stepX = px + i;
                    }
                    System.out.println("Stair[" + i + "] = " + world.getBlock(stepX, py + i, stepZ));
                }
                System.out.println("Placed 4 stone stairs facing " + facing + " in a climbing run");
            } else if (mode.equals("fence")) {
                for (int i = 0; i < 3; i++) {
                    world.setBlock(px + i, py, pz, BlockType.WOODEN_FENCE);
                }
                for (int i = 0; i < 3; i++) {
                    System.out.println("Fence[" + i + "] = " + world.getBlock(px + i, py, pz));
                }
                System.out.println("Placed 3 wooden fences in a row");
            }
            for (int i = 0; i < 5; i++) world.update(player.getPosition().x, player.getPosition().z);
        }
        // Opt-in autotest hook: carve a small water pool in front of the player and
        // drop a pig into it, so mob swimming (floating at the surface) can be
        // screenshotted. MCCLONE_AUTOTEST_MOB_SWIM_DROWN=1 caps the pool with a
        // ceiling so the pig stays submerged long enough to drown instead. Named
        // distinctly from MCCLONE_AUTOTEST_SWIM below (that one submerges the
        // player, not a mob) - sharing a name used to fire both hooks together,
        // with the second one's pool/teleport stomping the first's setup.
        if (System.getenv("MCCLONE_AUTOTEST_MOB_SWIM") != null && started[0]) {
            Vector3f front = player.getCamera().getFront();
            int px = (int) Math.floor(player.getPosition().x + front.x * 3f);
            int pz = (int) Math.floor(player.getPosition().z + front.z * 3f);
            // Derive pool base Y from the local terrain surface rather than hardcoding it.
            int surfaceY = -1;
            for (int y = Chunk.HEIGHT - 1; y >= 0; y--) {
                BlockType b = world.getBlock(px, y, pz);
                if (b != BlockType.AIR && !b.isFluid()) {
                    surfaceY = y;
                    break;
                }
            }
            if (surfaceY < 0) surfaceY = 0; // fallback if no solid surface found
            int poolBaseY = surfaceY;
            for (int x = px - 2; x <= px + 2; x++) {
                for (int z = pz - 2; z <= pz + 2; z++) {
                    for (int y = poolBaseY; y <= poolBaseY + 3; y++) {
                        world.setBlock(x, y, z, y == poolBaseY ? BlockType.STONE : BlockType.WATER);
                    }
                }
            }
            boolean drown = System.getenv("MCCLONE_AUTOTEST_MOB_SWIM_DROWN") != null;
            if (drown) {
                for (int x = px - 2; x <= px + 2; x++) {
                    for (int z = pz - 2; z <= pz + 2; z++) {
                        world.setBlock(x, poolBaseY + 4, z, BlockType.STONE); // ceiling so it can't surface
                    }
                }
            }
            float pigY = poolBaseY + 2f;
            world.spawnMobAt(Mob.Type.PIG, px + 0.5f, pigY, pz + 0.5f);
            System.out.println("Spawned a pig in water at " + (px + 0.5f) + "," + pigY + "," + (pz + 0.5f) + " (surface at Y=" + surfaceY + ")");
            // Advance the mob simulation for sufficient time to observe swimming or drowning.
            // The grace period is 15 seconds, so run for 20 seconds to demonstrate drowning if the pool is capped.
            float totalTime = drown ? 20f : 2f;
            int steps = (int)(totalTime / 0.1f); // 0.1s per step
            Vector3f autotestPos = player.getPosition();
            AABB autotestBox = new AABB(autotestPos.x - 0.3f, autotestPos.y, autotestPos.z - 0.3f,
                    autotestPos.x + 0.3f, autotestPos.y + 1.8f, autotestPos.z + 0.3f);
            for (int i = 0; i < steps; i++) {
                world.updateMobs(0.1f, autotestPos, autotestBox, false, new java.util.Random(), true);
            }
        }
        // Opt-in autotest hook: open a chest GUI pre-loaded with a few items, so
        // the container screen can be screenshotted. MCCLONE_AUTOTEST_DOUBLE
        // merges a second chest beside it to screenshot a 54-slot double chest;
        // MCCLONE_AUTOTEST_QUAD merges a 2x2 square into a 108-slot quad chest.
        if (System.getenv("MCCLONE_AUTOTEST_CHEST_GUI") != null && started[0]) {
            Chest west = world.getOrCreateChest(0, 0, 0);
            west.setSlot(0, BlockType.IRON_INGOT, 5);
            west.setSlot(1, BlockType.APPLE, 12);
            west.setSlot(2, BlockType.WOOD_LOG, 64);
            StorageContainer container = west;
            if (System.getenv("MCCLONE_AUTOTEST_DOUBLE") != null) {
                Chest east = world.getOrCreateChest(1, 0, 0);
                east.setSlot(3, BlockType.GOLD_INGOT, 4);
                east.setSlot(4, BlockType.DIAMOND, 2);
                container = new JoinedStorage(west, east);
            }
            if (System.getenv("MCCLONE_AUTOTEST_QUAD") != null) {
                Chest east = world.getOrCreateChest(1, 0, 0);
                Chest south = world.getOrCreateChest(0, 0, 1);
                Chest corner = world.getOrCreateChest(1, 0, 1);
                east.setSlot(3, BlockType.GOLD_INGOT, 4);
                south.setSlot(4, BlockType.DIAMOND, 2);
                corner.setSlot(5, BlockType.COAL, 9);
                container = new JoinedStorage(
                        new JoinedStorage(west, east),
                        new JoinedStorage(south, corner));
            }
            activeGui[0] = new ContainerGui(ContainerGui.Kind.CHEST, player.getInventory(), craftingGrid, container);
            openGui(inventoryController, activeGui, window, input, inventoryOpen, audio);
        }
        // Opt-in autotest hook: carve a deep pool of water around the player and
        // drop them in the middle of it, so the player's own swim physics
        // (buoyancy, no onGround) can be screenshotted with the F3 "Swimming"
        // debug line on - distinct from MCCLONE_AUTOTEST_MOB_SWIM above, which
        // submerges a mob instead.
        if (System.getenv("MCCLONE_AUTOTEST_SWIM") != null && started[0]) {
            Vector3f p = player.getPosition();
            int cx = (int) Math.floor(p.x);
            int cz = (int) Math.floor(p.z);
            int surfaceY = world.getSurfaceHeight(cx, cz);
            int poolTop = surfaceY + 3;
            int poolBottom = surfaceY - 4;
            for (int x = cx - 2; x <= cx + 2; x++) {
                for (int z = cz - 2; z <= cz + 2; z++) {
                    for (int y = poolBottom; y <= poolTop; y++) {
                        world.setBlock(x, y, z, BlockType.WATER_SOURCE);
                    }
                }
            }
            for (int i = 0; i < 5; i++) world.update(p.x, p.z);
            player.teleportTo(cx + 0.5f, poolTop - 2f, cz + 0.5f);
            System.out.println("Autotest swim pool: y " + poolBottom + " to " + poolTop + ", player at y " + (poolTop - 2));
        }
        // Opt-in autotest hook: carve two adjacent water bodies of different
        // total heights (a tall deep pool next to a shorter shallow one) and
        // submerge the player on the deep side, level with the shallow side's
        // own surface, looking across the boundary - exactly the underwater
        // ledge shape that used to paint a phantom water-surface face midway
        // through the deep side (see Chunk#emitFluid's sealedByFluidAbove
        // check: a submerged cell's corner gets pulled down by averaging with
        // the shorter neighbor's own surface at that same Y, which used to be
        // (mis)read as "this corner needs its own top face too").
        if (System.getenv("MCCLONE_AUTOTEST_UNDERWATER_LEDGE") != null && started[0]) {
            Vector3f p = player.getPosition();
            int cx = (int) Math.floor(p.x);
            int cz = (int) Math.floor(p.z);
            int surfaceY = world.getSurfaceHeight(cx, cz);
            int bottom = surfaceY - 6;
            int deepTop = surfaceY + 3;
            int shallowTop = surfaceY - 1; // several blocks shorter than the deep side
            for (int x = cx - 4; x <= cx + 4; x++) {
                for (int z = cz - 2; z <= cz + 2; z++) {
                    int top = x < cx ? deepTop : shallowTop;
                    for (int y = bottom; y <= top; y++) {
                        world.setBlock(x, y, z, BlockType.WATER_SOURCE);
                    }
                }
            }
            for (int i = 0; i < 5; i++) world.update(p.x, p.z);
            // Deep side, right up against the step, level with the shallow
            // side's own surface (where its corner-averaging pull-down would
            // show up), facing across it (+X, toward the shallow side).
            player.teleportTo(cx - 1.5f, shallowTop - 1f, cz + 0.5f);
            player.getCamera().setYaw(0f);
            player.getCamera().setPitch(10f);
            System.out.println("Autotest underwater ledge: deep top " + deepTop + ", shallow top " + shallowTop
                    + ", bottom " + bottom + ", player at y " + shallowTop);
        }
        // Diagnostic autotest hook: scans outward from spawn for the deepest
        // nearby ocean floor point (and, as a tiebreaker, the steepest
        // neighboring drop), and dives the player down to it, facing the
        // slope - a general-purpose way to screenshot real generated ocean
        // floor/cliff terrain headlessly, without hand-carving a scene.
        if (System.getenv("MCCLONE_AUTOTEST_FIND_OCEAN_CLIFF") != null && started[0]) {
            int seaLevel = TerrainGenerator.DEFAULT_SEA_LEVEL;
            // getSurfaceHeight only reads already-loaded chunks (falling back to
            // sea level otherwise), so stream a wide area around spawn in first.
            for (int i = 0; i < 400; i++) world.update(player.getPosition().x, player.getPosition().z);
            int bestX = 0, bestZ = 0, bestDeep = seaLevel, bestShallow = 0, bestDrop = -1;
            int oceanTiles = 0, minHeightSeen = seaLevel;
            for (int x = -90; x <= 90; x += 2) {
                for (int z = -90; z <= 90; z += 2) {
                    if (world.getBiome(x, z) != TerrainGenerator.Biome.OCEAN) continue;
                    // Skip columns whose chunk isn't actually loaded/generated yet
                    // (an ungenerated column reads all-AIR, which floorHeight would
                    // otherwise misreport as a dramatic "floor at bedrock").
                    if (world.getBlock(x, seaLevel, z) != BlockType.WATER) continue;
                    if (world.getBlock(x + 2, seaLevel, z) == BlockType.AIR) continue;
                    oceanTiles++;
                    int h = floorHeight(world, x, z, seaLevel);
                    minHeightSeen = Math.min(minHeightSeen, h);
                    int hE = floorHeight(world, x + 2, z, seaLevel);
                    int drop = hE - h;
                    // Rank by depth first (dive at the deepest point found), tie
                    // broken by whichever also has the steepest neighboring drop.
                    if (h < bestDeep || (h == bestDeep && drop > bestDrop)) {
                        bestDrop = drop;
                        bestX = x;
                        bestZ = z;
                        bestDeep = h;
                        bestShallow = hE;
                    }
                }
            }
            System.out.println("Autotest ocean cliff scan: oceanTiles=" + oceanTiles + " minHeightSeen=" + minHeightSeen);
            for (int i = 0; i < 8; i++) world.update(bestX, bestZ);
            player.teleportTo(bestX + 0.5f, bestDeep + 1.5f, bestZ + 0.5f);
            player.getCamera().setYaw(0f);
            player.getCamera().setPitch(0f);
            System.out.println("Autotest ocean cliff: deep(" + bestX + "," + bestZ + ")=" + bestDeep
                    + " shallow(" + (bestX + 2) + "," + bestZ + ")=" + bestShallow
                    + " drop=" + (bestShallow - bestDeep) + ", player at y " + (bestDeep + 1.5f));
        }
        // Opt-in autotest hook: open a furnace mid-smelt (partially burned fuel,
        // partway through smelting) so the flame/progress-arrow decorations can
        // be screenshotted in the actual container GUI screen - regression
        // coverage for Hud#renderFurnaceProgress silently no-op'ing when it ran
        // right after the textured-GUI panel path left no shader bound.
        if (System.getenv("MCCLONE_AUTOTEST_FURNACE_GUI") != null && started[0]) {
            Furnace furnace = world.getOrCreateFurnace(0, 5, 0);
            furnace.setSlot(Furnace.SLOT_INPUT, BlockType.IRON_ORE, 8);
            furnace.setSlot(Furnace.SLOT_FUEL, BlockType.COAL, 8);
            furnace.tick(4f);
            activeGui[0] = new ContainerGui(ContainerGui.Kind.FURNACE, player.getInventory(), craftingGrid, furnace);
            openGui(inventoryController, activeGui, window, input, inventoryOpen, audio);
            System.out.println("Autotest furnace GUI: burn=" + furnace.burnFraction() + " progress=" + furnace.progressFraction());
        }
        // Opt-in autotest hook: open the player's own inventory screen, equipping a
        // full iron set (plus a couple of bag items) so the armor column renders.
        if (System.getenv("MCCLONE_AUTOTEST_INVENTORY") != null && started[0]) {
            player.getInventory().setArmor(Inventory.ARMOR_SLOT_HELMET, BlockType.IRON_HELMET);
            player.getInventory().setArmor(Inventory.ARMOR_SLOT_CHESTPLATE, BlockType.IRON_CHESTPLATE);
            player.getInventory().setArmor(Inventory.ARMOR_SLOT_LEGGINGS, BlockType.IRON_LEGGINGS);
            player.getInventory().setArmor(Inventory.ARMOR_SLOT_BOOTS, BlockType.IRON_BOOTS);
            player.getInventory().setSlot(0, BlockType.APPLE, 12);
            player.getInventory().setSlot(1, BlockType.DIAMOND_PICKAXE, 1);
            activeGui[0] = inventoryGui;
            openGui(inventoryController, activeGui, window, input, inventoryOpen, audio);
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
        float[] teleportCooldown = {0f};

        while (!window.shouldClose()) {
            input.beginFrame();
            window.pollEvents();
            input.update();

            if (window.isResized()) {
                glViewport(0, 0, window.getWidth(), window.getHeight());
                window.setResized(false);
            }

            float dt = timer.getDeltaTime();
            // Controller support (e.g. Steam Deck): folds the first connected
            // gamepad's sticks/buttons/triggers into this frame's keyboard/mouse
            // state - see Input#updateGamepad for the full mapping. A no-op with
            // nothing connected, so this is safe to call unconditionally every frame.
            input.updateGamepad(dt, settings.getKeyBinds(), settings.getGamepadBinds());
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
                // Snow accumulates on the ground and water freezes over while it's
                // cold, melting back away when it warms - see World.updateSeasonalSurfaces.
                world.updateSeasonalSurfaces(dt, player.getPosition().x, player.getPosition().z, climate);
                // Weather ambience: the precipitation hiss follows the weather's
                // intensity, and a lightning flash that just started gets its rumble.
                audio.setWeatherAmbience(climate.isPrecipitation() ? climate.getWeatherStrength() : 0f);
                float flash = climate.getFlashIntensity();
                if (flash > 0f && prevFlash <= 0f) {
                    audio.play(SoundEvent.THUNDER, 0.8f);
                    if (world != null) {
                        float lightningDamage = lightningStrike(world, player, bolts, lightningRnd);
                        if (lightningDamage > 0f) {
                            player.takeDamage(lightningDamage);
                        }
                    }
                }
                prevFlash = flash;
                // Burning cells tick down, spread, hurt mobs and get doused by water.
                world.tickFires(dt);
                // Bolts flare and die quickly; drop any that have finished.
                for (int i = bolts.size() - 1; i >= 0; i--) {
                    LightningBolt bolt = bolts.get(i);
                    bolt.update(dt);
                    if (!bolt.isAlive()) bolts.remove(i);
                }
                // Autotest hook: force a strike just before the screenshot so the
                // bolt (0.35s lifetime) is still crackling in frame.
                if (forceLightning && frameCount == autoTestFrames - 1) {
                    Vector3f front = player.getCamera().getFront();
                    Vector3f pos = player.getPosition();
                    World.LightningStrikeResult result = world.strikeLightning(lightningRnd, pos.x + front.x * 14f, pos.z + front.z * 14f, pos);
                    if (result.bolt() != null) {
                        bolts.add(result.bolt());
                        audio.play(SoundEvent.THUNDER, 0.8f);
                        if (result.playerDamage() > 0f) {
                            player.takeDamage(result.playerDamage());
                        }
                        System.out.println("Autotest lightning strike at frame " + frameCount);
                    } else {
                        System.out.println("Autotest lightning: no surface at strike target");
                    }
                }
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
                    handleSettingsMenuInput(input, settings, settingsFile, worlds, player, window, hud, audio,
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
                                worlds = new World[DimensionType.values().length];
                                for (DimensionType dim : DimensionType.values()) {
                                    worlds[dim.ordinal()] = new World(seed, genSettings, atlas, worldDir, dim);
                                }
                                currentDim[0] = DimensionType.OVERWORLD;
                                world = worlds[currentDim[0].ordinal()];
                                startCalendar(dayNightCycle, calendar, genSettings);
                                for (World w : worlds) {
                                    w.setRenderDistance(settings.getRenderDistance());
                                    w.setLeavesTransparent(settings.isLeavesTransparent());
                                }
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
                            worlds = new World[DimensionType.values().length];
                            for (DimensionType dim : DimensionType.values()) {
                                worlds[dim.ordinal()] = new World(seed, genSettings, atlas, worldDir, dim);
                            }
                            currentDim[0] = DimensionType.OVERWORLD;
                            world = worlds[currentDim[0].ordinal()];
                            startCalendar(dayNightCycle, calendar, genSettings);
                            for (World w : worlds) {
                                w.setRenderDistance(settings.getRenderDistance());
                                w.setLeavesTransparent(settings.isLeavesTransparent());
                            }
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
                    closeInventory(inventoryController, activeGui, inventoryGui, inventoryOpen, audio);
                } else if (creativeOpen[0]) {
                    closeCreative(inventoryController, creativeOpen, audio);
                } else {
                    menuOpen[0] = !menuOpen[0];
                }
                window.setCursorCaptured(!menuOpen[0] && !inventoryOpen[0] && !creativeOpen[0]);
                input.resetMouseDelta();
            }

            if (input.isKeyJustPressed(settings.getKeyBinds().get(KeyBindings.INVENTORY))) {
                if (inventoryOpen[0]) {
                    closeInventory(inventoryController, activeGui, inventoryGui, inventoryOpen, audio);
                } else if (creativeOpen[0]) {
                    closeCreative(inventoryController, creativeOpen, audio);
                } else if (settings.getGameMode().isCreative()) {
                    creativeOpen[0] = true;
                    menuOpen[0] = false;
                    audio.play(SoundEvent.UI_OPEN);
                } else {
                    activeGui[0] = inventoryGui;
                    inventoryOpen[0] = true;
                    menuOpen[0] = false;
                    audio.play(SoundEvent.UI_OPEN);
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
                        audio.play(SoundEvent.UI_CLICK);
                    } else {
                        int item = hud.creativeItemAt(logicalX, logicalY, creativeTab[0]);
                        if (item >= 0) {
                            inventoryController.pickCreativeItem(CreativeCatalog.TABS[creativeTab[0]].items()[item], shift);
                            audio.play(SoundEvent.UI_CLICK);
                        } else if (hud.destroySlotAt(logicalX, logicalY)) {
                            inventoryController.destroyCursor();
                            audio.play(SoundEvent.UI_CLICK);
                        } else {
                            int hb = hud.hotbarSlotAt(logicalX, logicalY);
                            if (hb >= 0) {
                                if (shift) {
                                    inventoryController.click(hb, false, true);
                                } else {
                                    inventoryController.beginDrag(hb, false);
                                }
                                audio.play(SoundEvent.UI_CLICK);
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
                        audio.play(SoundEvent.UI_CLICK);
                    }
                    int item = hud.creativeItemAt(logicalX, logicalY, creativeTab[0]);
                    if (item >= 0) {
                        inventoryController.pickCreativeItem(CreativeCatalog.TABS[creativeTab[0]].items()[item], false);
                        audio.play(SoundEvent.UI_CLICK);
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
                    playSlotSound(audio, activeGui[0], hoveredSlot[0]);
                }
                if (input.isMouseJustPressed(GLFW_MOUSE_BUTTON_RIGHT)) {
                    if (shift) {
                        inventoryController.click(hoveredSlot[0], true, true);
                    } else {
                        inventoryController.beginDrag(hoveredSlot[0], true);
                    }
                    playSlotSound(audio, activeGui[0], hoveredSlot[0]);
                }
                if (input.isMouseDown(GLFW_MOUSE_BUTTON_LEFT) || input.isMouseDown(GLFW_MOUSE_BUTTON_RIGHT)) {
                    inventoryController.continueDrag(hoveredSlot[0]);
                }
                if (!input.isMouseDown(GLFW_MOUSE_BUTTON_LEFT) && !input.isMouseDown(GLFW_MOUSE_BUTTON_RIGHT)) {
                    inventoryController.endDrag(hoveredSlot[0]);
                }
            } else if (menuOpen[0]) {
                handleSettingsMenuInput(input, settings, settingsFile, worlds, player, window, hud, audio,
                        menuSelection, sliderDragRow, bindingAction, settingsTab);
            }

            if (input.isKeyJustPressed(settings.getKeyBinds().get(KeyBindings.DEBUG))) {
                showDebug[0] = !showDebug[0];
            }
            if (input.isKeyJustPressed(settings.getKeyBinds().get(KeyBindings.FORECAST))) {
                forecastOpen[0] = !forecastOpen[0];
            }
            screenshotRequested = input.isKeyJustPressed(settings.getKeyBinds().get(KeyBindings.SCREENSHOT));

            if (!menuOpen[0] && !inventoryOpen[0] && !creativeOpen[0]) {
                // Cold exposure factor, driven by the LOCAL temperature at the
                // player's position (which already folds in the biome, season,
                // night, weather, altitude and underground depth - so a deep cave
                // stays comfortable while the surface blizzards, and a mountain
                // peak is harsher than its foothills). Ramps 0 above ~2°C to 1
                // at ~-20°C. Player then weighs shelter, fires and armor warmth.
                // The underground reference is the GENERATED terrain height (not
                // the current surface) so a player-built roof doesn't fool the
                // depth model into thinking a cave is at the surface.
                float px = player.getPosition().x, pz = player.getPosition().z;
                float playerY = player.getPosition().y;
                TerrainGenerator.Biome pBiome = world.getBiome((int) Math.floor(px), (int) Math.floor(pz));
                float localTemp = climate.temperatureFor(pBiome, playerY, world.getTerrainHeight((int) Math.floor(px), (int) Math.floor(pz)));
                float coldFactor = Math.max(0f, Math.min(1f, (2f - localTemp) / 22f));
                player.update(dt, input, world, coldFactor);

                // Dimension portals: walking into a NETHER_PORTAL or END_PORTAL block
                // teleports the player to the linked dimension (with a short cooldown
                // so they don't instantly bounce back through the arrival portal).
                teleportCooldown[0] = Math.max(0f, teleportCooldown[0] - dt);
                if (teleportCooldown[0] <= 0f) {
                    Vector3f p = player.getPosition();
                    BlockType portal = world.getBlock((int) Math.floor(p.x), (int) Math.floor(p.y + 0.5f), (int) Math.floor(p.z));
                    if (!portal.isPortal()) {
                        portal = world.getBlock((int) Math.floor(p.x), (int) Math.floor(p.y + 1.5f), (int) Math.floor(p.z));
                    }
                    if (portal.isPortal()) {
                        teleportThroughPortal(player, worlds, currentDim, portal);
                        world = worlds[currentDim[0].ordinal()];
                        teleportCooldown[0] = PORTAL_COOLDOWN_SECONDS;
                        showMessage(messages, "Welcome to " + currentDim[0].displayName(),
                                new Vector4f(0.7f, 0.5f, 0.9f, 1f), 2.5f);
                    }
                }

                if (player.hasJustJumped()) audio.play(SoundEvent.JUMP);
                if (player.hasJustLanded()) audio.play(SoundEvent.LAND);
                splashCooldown[0] = Math.max(0f, splashCooldown[0] - dt);
                // isInWater() (whole-body hitbox) rather than isSubmerged() (eye point
                // only) - a jump into a shallow pool that never reaches eye height, or
                // wading in up to your knees, is still an entry that should splash.
                // isSubmerged() alone stayed silent for exactly that case.
                if (player.isInWater() != wasInWater[0]) {
                    wasInWater[0] = player.isInWater();
                    // Diving in (or surfacing) is a splash either way - even when the
                    // cooldown below suppresses the sound itself (a flicker burst),
                    // the stroke timer still needs resetting so the periodic stroke
                    // splash further down doesn't immediately double up on it.
                    swimStrokeTimer[0] = SWIM_STROKE_INTERVAL;
                    // Cooldown-gated: see SPLASH_COOLDOWN_SECONDS - the raw signal
                    // can flicker several times a frame-group near a surface
                    // boundary, but the actual splash sound should only fire for
                    // the first crossing in a burst.
                    if (splashCooldown[0] <= 0f) {
                        audio.play(SoundEvent.SPLASH);
                        splashCooldown[0] = SPLASH_COOLDOWN_SECONDS;
                    }
                }
                // A footstep every FOOTSTEP_INTERVAL seconds while walking/
                // sprinting on the ground - timed off a countdown rather than a
                // fixed-distance-traveled check, since it's simpler and the
                // difference isn't audible. Reset (not just left to drift) the
                // instant the player stops, so the very next step after
                // starting to walk again lands right away instead of waiting
                // out whatever was left on the old countdown.
                if (player.isMovingOnGround()) {
                    footstepTimer[0] -= dt;
                    if (footstepTimer[0] <= 0f) {
                        footstepTimer[0] = FOOTSTEP_INTERVAL;
                        SoundMaterial ground = SoundMaterial.of(player.blockUnderfoot(world));
                        Vector3f p = player.getPosition();
                        audio.playBlockSound(ground, BlockAction.STEP, p.x, p.y, p.z, 1f);
                    }
                } else {
                    footstepTimer[0] = 0f;
                }
                // A stroke sound every SWIM_STROKE_INTERVAL seconds while actively
                // swimming - same countdown pattern as footsteps above, just reusing
                // the splash sound (quieter, since it repeats) rather than a
                // dedicated stroke effect.
                if (player.isSwimmingAndMoving()) {
                    swimStrokeTimer[0] -= dt;
                    // Same cooldown as the transition splash above - both play
                    // SoundEvent.SPLASH, so without sharing the gate a stroke landing
                    // right after a transition (or another stroke) could still
                    // double up on it.
                    if (swimStrokeTimer[0] <= 0f && splashCooldown[0] <= 0f) {
                        swimStrokeTimer[0] = SWIM_STROKE_INTERVAL;
                        audio.play(SoundEvent.SPLASH, 0.5f);
                        splashCooldown[0] = SPLASH_COOLDOWN_SECONDS;
                    }
                } else {
                    swimStrokeTimer[0] = 0f;
                }
            }

            // Keep streaming/remeshing even with the menu open, so toggling a
            // rendering setting (e.g. see-through leaves) takes effect live.
            world.update(player.getPosition().x, player.getPosition().z);

            // The OpenAL listener follows the camera every frame regardless of
            // whether player.update() ran this frame, so positional sounds still
            // pan/attenuate correctly while a menu is open. The camera never
            // rolls (only yaw/pitch), so world-up always doubles as its up vector.
            audio.setListener(player.getCamera().getPosition(), player.getCamera().getFront(), WORLD_UP);

            if (player.getStats().isDead()) {
                System.out.println("You died. Respawning...");
                showMessage(messages, "You died!", new Vector4f(0.9f, 0.25f, 0.25f, 1f), 3.0f);
                audio.play(SoundEvent.DEATH);
                // Drop the whole inventory onto the ground, Minecraft-style.
                Vector3f deathPos = player.getPosition();
                for (int slot = 0; slot < Inventory.SIZE; slot++) {
                    BlockType t = player.getInventory().typeOf(slot);
                    if (t != null) {
                        world.spawnItem((int) Math.floor(deathPos.x), (int) Math.floor(deathPos.y),
                                (int) Math.floor(deathPos.z), t, player.getInventory().countOf(slot), loot);
                    }
                }
                // Worn armor drops too (one piece per slot).
                for (int slot = 0; slot < Inventory.ARMOR_SLOT_COUNT; slot++) {
                    BlockType t = player.getInventory().armorType(slot);
                    if (t != null) {
                        world.spawnItem((int) Math.floor(deathPos.x), (int) Math.floor(deathPos.y),
                                (int) Math.floor(deathPos.z), t, 1, loot);
                    }
                }
                player.getInventory().clear();
                player.getInventory().clearArmor();
                player.getDurability().reset();
                // Respawn back in the overworld, wherever you died.
                if (currentDim[0] != DimensionType.OVERWORLD) {
                    currentDim[0] = DimensionType.OVERWORLD;
                    world = worlds[currentDim[0].ordinal()];
                    for (int i = 0; i < 80; i++) {
                        world.update(0, 0);
                    }
                }
                player.respawn(world, 0.5f, 0.5f);
            }

            // Item-entity physics + pickup.
            if (world.updateItems(dt, player.getPosition(), player.getInventory())) {
                audio.play(SoundEvent.ITEM_PICKUP);
            }

            // Furnaces (and any other block entities) work in the background,
            // ticking forward with world time.
            world.tickBlockEntities(dt);

            // Mobs: passives wander, hostiles hunt the player (spawning at night and
            // melting away at dawn); the damage their hits and arrows deal is applied
            // to the player's health, where the existing death/respawn handling picks
            // it up. Use takeDamage (not getStats().damage) so mob damage routes through
            // the same armor-mitigation and armor-wear path as environmental hazards.
            Vector3f playerPos = player.getPosition();
            AABB playerBox = new AABB(playerPos.x - 0.3f, playerPos.y, playerPos.z - 0.3f,
                    playerPos.x + 0.3f, playerPos.y + 1.8f, playerPos.z + 0.3f);
            // Creative/spectator are invulnerable (see GameMode#isInvulnerable) - mobs
            // couldn't land a hit either way, so don't have them chase/attack a player
            // they can never actually hurt; they just wander like passives instead.
            boolean playerTargetable = !settings.getGameMode().isInvulnerable();
            float mobDamage = world.updateMobs(dt, playerPos, playerBox, dayNightCycle.isNight(), loot, playerTargetable);
            if (mobDamage > 0f) {
                player.takeDamage(mobDamage);
                audio.play(SoundEvent.HURT);
            }
            // Every damage source for this frame (environmental hazards inside
            // player.update(), lightning and mob hits via takeDamage() above/below it)
            // has now had its chance to run - wear armor for the frame's total and
            // clear the accumulator, once, here. See Player.finalizeDamage's javadoc
            // for why this can't just live inside player.update() itself.
            player.finalizeDamage();

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
                if (worlds != null) {
                    for (World w : worlds) {
                        w.saveAllModified();
                    }
                }
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
                    boolean killed = world.damageMob(targetedMob, damage, player.getPosition().x, player.getPosition().z, loot);
                    audio.playAt(killed ? SoundEvent.MOB_DEATH : SoundEvent.ATTACK,
                            targetedMob.position.x, targetedMob.position.y, targetedMob.position.z, 1f);
                    attackCooldown[0] = 0.45f;
                    // Swords wear out with use (creative tools never break).
                    if (!mode.isCreative() && Mining.isSword(heldItem) && player.getDurability().use(heldItem)) {
                        player.getInventory().remove(heldItem, 1);
                        System.out.println("Your " + heldItem + " broke!");
                        showMessage(messages, "Your " + heldItem + " broke!",
                                new Vector4f(1f, 0.72f, 0.3f, 1f), 2.5f);
                        audio.play(SoundEvent.TOOL_BREAK);
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
                                    breakBlockAt(world, player, mode, heldItem, loot, messages, audio, bx + dx, by, bz + dz);
                                }
                            }
                        } else {
                            breakBlockAt(world, player, mode, heldItem, loot, messages, audio, bx, by, bz);
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
                            audio.playAt(SoundEvent.DOOR, hit.blockPos.x + 0.5f, hit.blockPos.y + 0.5f, hit.blockPos.z + 0.5f, 1f);
                            handRenderer.triggerSwing();
                        }
                    } else if (Door.isTrapdoor(targeted)) {
                        if (noMob && mode.canPlace()) {
                            Door.toggleSingle(world, world::setBlock, hit.blockPos.x, hit.blockPos.y, hit.blockPos.z);
                            audio.playAt(SoundEvent.DOOR, hit.blockPos.x + 0.5f, hit.blockPos.y + 0.5f, hit.blockPos.z + 0.5f, 1f);
                            handRenderer.triggerSwing();
                        }
                    } else if (noMob && targeted == BlockType.FURNACE) {
                        // Right-click a furnace to open its smelting gui.
                        Furnace furnace = world.getOrCreateFurnace(hit.blockPos.x, hit.blockPos.y, hit.blockPos.z);
                        activeGui[0] = new ContainerGui(ContainerGui.Kind.FURNACE, player.getInventory(), craftingGrid, furnace);
                        openGui(inventoryController, activeGui, window, input, inventoryOpen, audio);
                    } else if (noMob && targeted == BlockType.CRAFTING_TABLE) {
                        // Right-click a crafting table to open the 3x3 crafting gui.
                        activeGui[0] = new ContainerGui(ContainerGui.Kind.CRAFTING_TABLE, player.getInventory(), craftingGrid, null);
                        openGui(inventoryController, activeGui, window, input, inventoryOpen, audio);
                    } else if (noMob && targeted == BlockType.CHEST) {
                        // Right-click a chest to open its storage gui; an adjacent
                        // chest merges into a 54-slot double chest.
                        world.getOrCreateChest(hit.blockPos.x, hit.blockPos.y, hit.blockPos.z);
                        activeGui[0] = new ContainerGui(ContainerGui.Kind.CHEST, player.getInventory(), craftingGrid,
                                world.chestContainerAt(hit.blockPos.x, hit.blockPos.y, hit.blockPos.z));
                        openGui(inventoryController, activeGui, window, input, inventoryOpen, audio);
                    } else if (noMob && targeted == BlockType.BARREL) {
                        // Right-click a barrel to open its storage gui.
                        world.getOrCreateBarrel(hit.blockPos.x, hit.blockPos.y, hit.blockPos.z);
                        activeGui[0] = new ContainerGui(ContainerGui.Kind.CHEST, player.getInventory(), craftingGrid,
                                world.barrelAt(hit.blockPos.x, hit.blockPos.y, hit.blockPos.z));
                        openGui(inventoryController, activeGui, window, input, inventoryOpen, audio);
                    } else if (noMob && mode.canPlace() && heldItem != null) {
                        placeOrEatHeldItem(world, player, mode, heldItem, hit, audio, handRenderer);
                    }
                }
            }

            }

            // --- Render ---
            Matrix4f projection = player.getCamera().getProjectionMatrix(settings.getFov(), window.getAspectRatio(), NEAR_PLANE, FAR_PLANE);
            Matrix4f view = player.getCamera().getViewMatrix();

            // Per-dimension sky: the overworld runs the live day/night cycle; the
            // Nether is a constant dim red glow (no sun/clouds/stars), and the End
            // is a star-lit void. Weather (overcast skies, lightning flashes) dims
            // the overworld sky; the other dimensions' skies are fixed. Reused
            // scratch vectors keep this allocation-free.
            float overcast = climate.getOvercast();
            float flash = climate.getFlashIntensity();
            Vector3f horizonColor;
            Vector3f zenithColor;
            Vector3f nightZenith;
            Vector3f sunColor;
            Vector3f moonColor;
            float daylight;
            float ambientBrightness;
            float clouds = settings.getCloudAmount() / 3f;
            float stars = settings.isStars() ? 1f : 0f;
            if (currentDim[0] == DimensionType.NETHER) {
                horizonColor = NETHER_HORIZON;
                zenithColor = NETHER_ZENITH;
                nightZenith = NETHER_ZENITH;
                sunColor = NETHER_ZENITH;
                moonColor = NETHER_ZENITH;
                daylight = 1f;
                clouds = 0f;
                stars = 0f;
                ambientBrightness = NETHER_AMBIENT * settings.getBrightness();
            } else if (currentDim[0] == DimensionType.END) {
                horizonColor = END_HORIZON;
                zenithColor = END_ZENITH;
                nightZenith = END_ZENITH;
                sunColor = END_ZENITH;
                moonColor = END_ZENITH;
                daylight = 0.5f;
                clouds = 0f;
                stars = 1f;
                ambientBrightness = END_AMBIENT * settings.getBrightness();
            } else {
                horizonColor = new Vector3f(dayNightCycle.getHorizonColor());
                zenithColor = new Vector3f(dayNightCycle.getZenithColor());
                nightZenith = dayNightCycle.getNightZenithColor();
                sunColor = dayNightCycle.getSunColor();
                moonColor = dayNightCycle.getMoonColor();
                daylight = dayNightCycle.getDaylightFactor();
                clouds = settings.getCloudAmount() / 3f;
                stars = settings.isStars() ? 1f : 0f;
                if (overcast > 0f) {
                    horizonColor.lerp(OVERCAST_HORIZON, overcast);
                    zenithColor.lerp(OVERCAST_ZENITH, overcast);
                }
                if (flash > 0f) {
                    horizonColor.lerp(FLASH_COLOR, flash * 0.8f);
                    zenithColor.lerp(FLASH_COLOR, flash * 0.8f);
                }
                ambientBrightness = dayNightCycle.getAmbientBrightness() * settings.getBrightness()
                        * (1f - 0.55f * overcast) + flash * 0.5f;
            }
            window.setClearColor(horizonColor.x, horizonColor.y, horizonColor.z, 1f);
            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

            // Procedural sky (gradient, sun/moon, stars, clouds), pinned to the far
            // plane so the world always draws in front of it.
            glDisable(GL_DEPTH_TEST);
            skyRenderer.render(skyShader, projection, view,
                    dayNightCycle.getSunDirection(), daylight, dayNightCycle.getCloudPhase(),
                    Math.min(1.5f, clouds + (currentDim[0] == DimensionType.OVERWORLD ? overcast * 0.9f : 0f)),
                    0.5f + settings.getCloudSpeed() * 0.5f,
                    currentDim[0] == DimensionType.OVERWORLD ? stars * (1f - overcast) : stars,
                    currentDim[0] == DimensionType.OVERWORLD ? overcast : 0f,
                    zenithColor, horizonColor, nightZenith, sunColor, moonColor);
            glEnable(GL_DEPTH_TEST);

            if (started[0]) {
            chunkShader.bind();
            chunkShader.setUniform("projection", projection);
            chunkShader.setUniform("view", view);
            chunkShader.setUniform("atlas", 0);
            chunkShader.setUniform("fogColor", horizonColor);
            chunkShader.setUniform("fogStart", (world.getRenderDistance() - 2) * 16f);
            chunkShader.setUniform("fogEnd", world.getRenderDistance() * 16f);
            chunkShader.setUniform("ambientBrightness", ambientBrightness);
            chunkShader.setUniform("time", animTime[0]);
            chunkShader.setUniform("atlasGrid", (float) TextureAtlas.GRID);
            chunkShader.setUniform("time", animTime[0]);
            chunkShader.setUniform("atlasGrid", (float) TextureAtlas.GRID);
            atlas.bind();
            world.render(chunkShader, projection, view);
            itemRenderer.render(chunkShader, atlas, itemTextures, world.getItems(), player.getCamera());
            mobRenderer.render(mobTextures, world.getMobs(), world.getArrows());
            chunkShader.unbind();
            }

            // Rain/snow particles and lightning bolts, drawn against the world
            // (depth-tested) but hidden behind menus just like the crosshair.
            if (started[0] && !menuOpen[0] && !inventoryOpen[0] && !creativeOpen[0]) {
                weatherRenderer.render(lineShader, projection, view, weatherParticles, bolts);
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
                // Raycaster.cast() reports a degenerate hit (point == origin,
                // blockPos == the eye's own cell) when the eye's own cell holds
                // an overlay decoration (e.g. seaweed) - intentional, so seaweed
                // right at your feet can still be targeted/broken - but drawing
                // a wireframe cube around a block the camera is literally inside
                // of has nowhere sane to project to: its edges radiate out to
                // the screen corners, a glitchy "x-ray" wireframe look. Skip the
                // outline specifically for that degenerate case; breaking/
                // placing against hit.blockPos is unaffected.
                boolean degenerateHit = hit != null
                        && hit.point.distanceSquared(player.getEyePosition()) < 0.0025f;
                if (hit != null && targetedMobRef[0] == null && !degenerateHit) {
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
                            player.getStats().getBreath(), PlayerStats.MAX_BREATH,
                            player.isSubmerged(),
                            Inventory.HOTBAR_SIZE, window.getAspectRatio());
                }
                // A blue tint washes over the screen while your eyes are
                // underwater, drawn before the frost vignette so a freezing
                // dip under icy water still layers both.
                hud.renderUnderwaterOverlay(player.isSubmerged());
                // A frost vignette fades in over everything as you freeze outside
                // in a storm (see Player/PlayerStats cold exposure).
                hud.renderFrostOverlay(player.getStats().getColdness());
            }
            hud.renderMessages(messages, window.getAspectRatio());
            if (started[0] && forecastOpen[0] && !menuOpen[0] && !inventoryOpen[0] && !creativeOpen[0]) {
                hud.renderForecast(climate, calendar, window.getAspectRatio());
            }
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
                if (player.isSwimming()) {
                    hud.drawTextLeft("Swimming" + (player.isSubmerged() ? " (submerged)" : ""),
                            -0.95f, y - (line++) * step, textSize, WHITE, aspect);
                }
                if (input.isGamepadConnected()) {
                    hud.drawTextLeft("Gamepad: " + input.getGamepadName(),
                            -0.95f, y - (line++) * step, textSize, WHITE, aspect);
                }
                BlockType sel = player.getInventory().typeOf(selectedSlot[0]);
                hud.drawTextLeft("Selected: " + (sel == null ? "-" : sel.toString()),
                        -0.95f, y - (line++) * step, textSize, WHITE, aspect);
                hud.drawTextLeft("Dimension: " + currentDim[0].displayName(),
                        -0.95f, y - (line++) * step, textSize, WHITE, aspect);
                TerrainGenerator.Biome biome = world.getBiome((int) Math.floor(pos.x), (int) Math.floor(pos.z));
                hud.drawTextLeft("Biome: " + biome,
                        -0.95f, y - (line++) * step, textSize, WHITE, aspect);
                hud.drawTextLeft("Calendar: " + calendar.getDayOfWeekName() + " - Week " + calendar.getWeekOfMonth()
                                + " of " + calendar.getMonthName() + " (" + calendar.getSeason().displayName
                                + "), Year " + calendar.getYear(),
                        -0.95f, y - (line++) * step, textSize, WHITE, aspect);
                hud.drawTextLeft(String.format(Locale.ROOT, "Weather: %s (%.0f%%) - next: %s in ~%dh",
                                climate.getWeather().displayName, climate.getWeatherStrength() * 100f,
                                climate.nextWeatherChange().displayName, climate.hoursUntilChange()),
                        -0.95f, y - (line++) * step, textSize, WHITE, aspect);
                hud.drawTextLeft(String.format(Locale.ROOT, "Climate: %.1f C at %.0f, %.0f%% humidity",
                                climate.temperatureFor(biome, pos.y, world.getTerrainHeight((int) Math.floor(pos.x), (int) Math.floor(pos.z))),
                                pos.y, climate.humidityFor(biome) * 100f),
                        -0.95f, y - (line++) * step, textSize, WHITE, aspect);
                hud.drawTextLeft(String.format(Locale.ROOT, "Cold exposure: %.0f%%",
                                player.getStats().getColdness() * 100f),
                        -0.95f, y - (line++) * step, textSize, WHITE, aspect);
                hud.drawTextLeft(String.format(Locale.ROOT, "Shelter: space %.0f%% warm, armor %.0f%% warmth",
                                player.getSpaceWarmth() * 100f,
                                player.getInventory().totalArmorWarmth() / Armor.WARMTH_CAP * 100f),
                        -0.95f, y - (line++) * step, textSize, WHITE, aspect);
                if (player.isSubmerged()) {
                    hud.drawTextLeft(String.format(Locale.ROOT, "Breath: %.1fs / %.0fs",
                                    player.getStats().getBreath(), PlayerStats.MAX_BREATH),
                            -0.95f, y - (line++) * step, textSize, WHITE, aspect);
                }
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

        if (worlds != null) {
            for (World w : worlds) {
                w.saveAllModified();
            }
        }
        settings.save(settingsFile);

        hud.destroy();
        guiTextures.destroy();
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
        if (worlds != null) {
            for (World w : worlds) {
                w.destroy();
            }
        }
        audio.destroy();
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
    /**
     * Picks a strike target near the player (a random distance, biased toward
     * where the camera is looking so the bolt usually lands in view) and spawns
     * the cosmetic bolt; the strike's fire and mob/player blast happen in World.
     * Returns player damage from the strike.
     */
    private static float lightningStrike(World world, Player player, List<LightningBolt> bolts, Random rnd) {
        Vector3f front = player.getCamera().getFront();
        Vector3f pos = player.getPosition();
        float angle = player.getCamera().getYaw() + (rnd.nextFloat() - 0.5f) * 2.2f;
        float dist = 12f + rnd.nextFloat() * 30f;
        World.LightningStrikeResult result = world.strikeLightning(rnd, pos.x + (float) Math.cos(angle) * dist,
                pos.z + (float) Math.sin(angle) * dist, pos);
        if (result.bolt() != null) bolts.add(result.bolt());
        return result.playerDamage();
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

    /**
     * Teleports the player through a portal block into the linked dimension. The
     * overworld &lt;-&gt; nether swap scales coordinates 1:8 (Minecraft-style), so a
     * base built at the same portal on each side lines up; the End always drops
     * you at its central island (the only solid ground it has). The arrival spot
     * is pre-generated and placed a couple of blocks above the local surface, so
     * you never fall through ungenerated terrain. {@code currentDim} is updated
     * in place and the caller swaps {@code world} to match.
     */
    private static void teleportThroughPortal(Player player, World[] worlds, DimensionType[] currentDim, BlockType portal) {
        DimensionType from = currentDim[0];
        DimensionType to = DimensionType.portalDestination(portal, from);
        World targetWorld = worlds[to.ordinal()];
        Vector3f pos = player.getPosition();

        float x, z;
        if (to == DimensionType.END) {
            // The End is one island at the origin - always arrive at its heart.
            x = 0.5f;
            z = 0.5f;
        } else if (from == DimensionType.OVERWORLD && to == DimensionType.NETHER) {
            x = pos.x / NETHER_SCALE;
            z = pos.z / NETHER_SCALE;
        } else if (from == DimensionType.NETHER && to == DimensionType.OVERWORLD) {
            x = pos.x * NETHER_SCALE;
            z = pos.z * NETHER_SCALE;
        } else {
            x = pos.x;
            z = pos.z;
        }

        // Generate/mesh the arrival area so the player has solid ground under them.
        for (int i = 0; i < 100; i++) {
            targetWorld.update(x, z);
        }
        int fx = (int) Math.floor(x);
        int fz = (int) Math.floor(z);
        int surfaceY = landingSurfaceY(targetWorld, fx, fz);

        // When arriving in a new dimension, spawn a matching portal block right at
        // the landing spot so there's always a way back home (Minecraft spawns the
        // return portal on the far side too). A NETHER_PORTAL returns you from the
        // Nether, an END_PORTAL from the End. The player lands a couple of blocks
        // away so they don't instantly step back through it.
        if (to != DimensionType.OVERWORLD) {
            BlockType returnPortal = to == DimensionType.NETHER ? BlockType.NETHER_PORTAL : BlockType.END_PORTAL;
            targetWorld.setBlock(fx, surfaceY + 1, fz, returnPortal);
            // Nudge the landing spot clear of the portal.
            x += 2.5f;
            fx = (int) Math.floor(x);
            int offsetY = landingSurfaceY(targetWorld, fx, fz);
            if (offsetY > 1) {
                surfaceY = offsetY;
            }
        }

        player.teleportTo(x, surfaceY + 2f, z);

        currentDim[0] = to;
    }

    /**
     * The surface height to land on in a dimension. The overworld's highest block
     * is its terrain (open sky above), and the End's is its island top - but the
     * Nether is sealed by a bedrock ceiling, so its highest block would be ~110
     * blocks up in the roof. There, scan down for the first solid block that has
     * open air above it: the cavern floor a player can actually stand on.
     */
    private static int landingSurfaceY(World world, int bx, int bz) {
        if (world.getDimension() == DimensionType.NETHER) {
            for (int y = Chunk.HEIGHT - 2; y >= 2; y--) {
                if (world.getBlock(bx, y, bz) != BlockType.AIR && world.getBlock(bx, y + 1, bz) == BlockType.AIR) {
                    return y;
                }
            }
            return 40;
        }
        int y = world.getSurfaceHeight(bx, bz);
        return y < 1 ? 64 : y;
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

    /**
     * Right-click with a held item that isn't a door/trapdoor/GUI block: eats
     * it if it's food, otherwise places it as a world block (or, for a
     * submersible decoration like seaweed, into the target cell's overlay
     * slot instead of replacing the fluid there). Shared by the real
     * mouse-click handler and the {@code MCCLONE_AUTOTEST_PLACE_SURVIVAL}
     * headless verification hook, so both exercise the exact same logic a
     * player's right-click does - including the inventory consumption in
     * survival, which the older direct-{@code world.setBlock} autotest hook
     * bypasses entirely.
     */
    private void placeOrEatHeldItem(World world, Player player, GameMode mode, BlockType heldItem,
                                     Raycaster.Hit hit, AudioEngine audio, HandRenderer handRenderer) {
        if (heldItem.isEdible() && !mode.isCreative()) {
            if (player.eat(heldItem)) {
                audio.play(SoundEvent.EAT);
                handRenderer.triggerSwing();
            }
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
            // For doors, validate the upper cell before consuming the item.
            if (heldItem == BlockType.DOOR && !blocked && !intersectsPlayer(player, p)) {
                if (p.y + 1 >= Chunk.HEIGHT) {
                    blocked = true;
                } else if (world.getBlock(p.x, p.y + 1, p.z) != BlockType.AIR) {
                    blocked = true;
                } else if (intersectsPlayer(player, new Vector3i(p.x, p.y + 1, p.z))) {
                    blocked = true;
                }
            }
            if (!blocked && !intersectsPlayer(player, p)) {
                boolean placed = mode.isCreative() || player.getInventory().remove(heldItem, 1);
                if (placed) {
                    handRenderer.triggerSwing();
                    if (intoFluid) {
                        world.setOverlay(p.x, p.y, p.z, heldItem);
                    } else {
                        world.setBlock(p.x, p.y, p.z, heldItem);
                    }
                    audio.playBlockSound(SoundMaterial.of(heldItem), BlockAction.PLACE, p.x + 0.5f, p.y + 0.5f, p.z + 0.5f, 1f);
                    // Doors, trapdoors, and other directional blocks
                    // (e.g. a furnace) face the player: the front sits
                    // on the side of the block nearest to them (opposite
                    // the look direction). Stairs also store a facing so
                    // their stepped mesh rises away from the player.
                    if (heldItem.isDirectional() || heldItem == BlockType.DOOR || heldItem == BlockType.TRAPDOOR || heldItem.isStair()) {
                        Vector3f front = player.getCamera().getFront();
                        byte facing = (byte) (Math.abs(front.x) >= Math.abs(front.z)
                                ? (front.x >= 0 ? 3 : 2)
                                : (front.z >= 0 ? 1 : 0));
                        world.setBlockOrientation(p.x, p.y, p.z, facing);
                        // A door is 2 blocks tall: place the top half unconditionally
                        // (validation already ensured it's safe).
                        if (heldItem == BlockType.DOOR) {
                            world.setBlock(p.x, p.y + 1, p.z, BlockType.DOOR);
                            world.setBlockOrientation(p.x, p.y + 1, p.z, facing);
                        }
                    }
                }
            }
        }
    }

    /**
     * The Y of the first solid (non-air, non-fluid) block scanning down from
     * {@code fromY} - unlike {@link World#getSurfaceHeight}, which treats
     * water as "non-air" and so just returns the water's own top surface,
     * this actually finds the sea/lake floor underneath it. Diagnostic-only
     * (see {@code MCCLONE_AUTOTEST_FIND_OCEAN_CLIFF}).
     */
    private static int floorHeight(World world, int x, int z, int fromY) {
        for (int y = fromY; y >= 0; y--) {
            BlockType t = world.getBlock(x, y, z);
            if (t != BlockType.AIR && !t.isFluid()) return y;
        }
        return 0;
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
