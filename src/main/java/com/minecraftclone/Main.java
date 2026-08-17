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
import com.minecraftclone.engine.graphics.PlayerRenderer;
import com.minecraftclone.engine.graphics.SkyRenderer;
import com.minecraftclone.engine.graphics.TextureAtlas;
import com.minecraftclone.engine.graphics.WeatherRenderer;
import com.minecraftclone.engine.gui.ContainerGui;
import com.minecraftclone.net.GameServer;
import com.minecraftclone.net.NetClient;
import com.minecraftclone.net.Packets;
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
import com.minecraftclone.world.RemotePlayer;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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

    // --- Multiplayer state (set from the main menu / network thread) ---
    /** The active server connection, or null when not in a multiplayer session. */
    private volatile NetClient netClient;
    /** An embedded server started by "Host & Play" (same process), or null. */
    private volatile GameServer hostServer;
    /** The last multiplayer connection error to surface to the user, or null. */
    private volatile String netError;
    /** Remote players seen via the server, keyed by their server-assigned id. */
    private final Map<Integer, RemotePlayer> remotePlayers = new LinkedHashMap<>();
    /** Remote mobs seen via the server, keyed by their server-assigned id. */
    private final Map<Integer, Mob> remoteMobs = new LinkedHashMap<>();
    /** Draws other players' bodies; created in run() once the GL context exists. */
    private PlayerRenderer playerRenderer;
    /** The dimension the local player is currently in (shared with multiplayer helpers). */
    private final DimensionType[] currentDim = {DimensionType.OVERWORLD};
    /** The per-dimension mirror worlds created by a multiplayer WELCOME, handed to run(). */
    private World[] returnMirrorWorlds;


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
                    adjustSettingsRow(settings, settingsFile, worlds, player, window, audio,
                            Settings.rowInTab(tab, menuSelection[0]), -1);
                }
            }
            if (input.isKeyJustPressed(GLFW_KEY_RIGHT)) {
                if (tab != Settings.TAB_CONTROLS) {
                    adjustSettingsRow(settings, settingsFile, worlds, player, window, audio,
                            Settings.rowInTab(tab, menuSelection[0]), +1);
                }
            }
            if (input.isKeyJustPressed(GLFW_KEY_ENTER) || input.isKeyJustPressed(GLFW_KEY_SPACE)) {
                if (tab == Settings.TAB_CONTROLS) {
                    bindingAction[0] = menuSelection[0];
                    input.consumeLastKeyPressed(); // discard the Enter/Space that started capture
                } else {
                    adjustSettingsRow(settings, settingsFile, worlds, player, window, audio,
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
                if (tab == Settings.TAB_CONTROLS) {
                    bindingAction[0] = clicked;
                    input.consumeLastKeyPressed();
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

    // ------------------------------------------------------------------
    // Multiplayer
    // ------------------------------------------------------------------

    private static int parsePort(String text) {
        try {
            int p = Integer.parseInt(text.trim());
            return (p >= 1 && p <= 65535) ? p : 25565;
        } catch (NumberFormatException e) {
            return 25565;
        }
    }

    /** Starts an embedded {@link GameServer} in this process, then connects the local client to it. */
    private void hostAndPlay(int port, Path saveRoot, String playerName) {
        Thread t = new Thread(() -> {
            try {
                WorldGenSettings serverSettings = new WorldGenSettings();
                long seed = serverSettings.resolveSeed();
                Path serverSaveDir = saveRoot.resolve("multiplayer_server");
                GameServer server = new GameServer(port, serverSettings, seed, serverSaveDir);
                hostServer = server;
                server.start();
                System.out.println("Hosted server on port " + server.getPort() + " (seed " + seed + ")");
                connectClient("127.0.0.1", server.getPort(), playerName);
            } catch (Exception e) {
                netError = "Failed to start server: " + e.getMessage();
                hostServer = null;
            }
        }, "host-and-play");
        t.setDaemon(true);
        t.start();
    }

    /** Connects the local client to an existing server. */
    private void joinServer(String host, int port, String playerName) {
        Thread t = new Thread(() -> connectClient(host, port, playerName), "join-server");
        t.setDaemon(true);
        t.start();
    }

    /** Connects, sends the join packet, and stashes the client for the main loop to pick up. */
    private void connectClient(String host, int port, String playerName) {
        try {
            NetClient client = new NetClient(host, port);
            client.sendJoin(playerName);
            netClient = client;
            netError = null;
        } catch (Exception e) {
            netError = "Could not connect to " + host + ":" + port + " - " + e.getMessage();
            netClient = null;
        }
    }

    /** Sends the local player's current position/look to the server (throttled by the caller). */
    private void sendPlayerMove(NetClient client, Player player) {
        if (client == null || !client.isConnected()) return;
        Vector3f p = player.getPosition();
        Camera cam = player.getCamera();
        try {
            client.sendMove(new Packets.Move(p.x, p.y, p.z, cam.getYaw(), cam.getPitch(),
                    player.isOnGround(), player.isFlying(), player.isSprinting()));
        } catch (IOException e) {
            netError = e.getMessage();
        }
    }

    /** Broadcasts a block change to the server (break/place intent). */
    private void sendBlockChange(byte dimension, int x, int y, int z, BlockType type, byte orientation, boolean overlay) {
        NetClient client = netClient;
        if (client == null || !client.isConnected()) return;
        try {
            if (type == BlockType.AIR) {
                client.sendBreakBlock(new Packets.BreakBlock(dimension, x, y, z, overlay));
            } else {
                client.sendPlaceBlock(new Packets.PlaceBlock(dimension, x, y, z, type.id, orientation, overlay));
            }
        } catch (IOException e) {
            netError = e.getMessage();
        }
    }

    /** Sends a cell's current state to the server (used after local edits like door toggles). */
    private void syncBlockToServer(byte dimension, World world, int x, int y, int z, boolean force) {
        NetClient client = netClient;
        if (client == null || !client.isConnected()) return;
        BlockType block = world.getBlock(x, y, z);
        if (block == BlockType.AIR) {
            if (force) sendBlockChange(dimension, x, y, z, BlockType.AIR, (byte) 0, false);
            return;
        }
        sendBlockChange(dimension, x, y, z, block, world.getOrientation(x, y, z), false);
    }

    /** Sends both halves of a door column to the server after a toggle. */
    private void syncDoorToServer(byte dimension, World world, int x, int y, int z) {
        NetClient client = netClient;
        if (client == null || !client.isConnected()) return;
        int bottom = Door.bottomHalf(world, x, y, z);
        for (int yy = bottom; yy <= bottom + 1; yy++) {
            BlockType block = world.getBlock(x, yy, z);
            sendBlockChange(dimension, x, yy, z, block, world.getOrientation(x, yy, z), false);
        }
    }

    /** Sends a chat message typed by the local player. */
    private void sendChat(String text) {
        NetClient client = netClient;
        if (client == null || !client.isConnected()) return;
        try {
            client.sendChat(text);
        } catch (IOException e) {
            netError = e.getMessage();
        }
    }

    /** Sends a mob attack to the server (server applies the damage authoritatively). */
    private void sendMobAttack(Mob mob, float damage) {
        NetClient client = netClient;
        if (client == null || !client.isConnected() || mob.id <= 0) return;
        try {
            client.sendMobAttack(mob.id, damage);
        } catch (IOException e) {
            netError = e.getMessage();
        }
    }

    /** The mob the crosshair is aimed at: local mobs, or remote players' mobs in multiplayer. */
    private Mob raycastTargetMob(Player player, World world) {
        Vector3f eye = player.getEyePosition();
        Vector3f front = player.getCamera().getFront();
        if (netClient != null && netClient.isConnected()) {
            return raycastRemoteMobs(eye, front);
        }
        return world.raycastMob(eye, front, REACH_DISTANCE);
    }

    /** Raycasts against the server-relayed mob list (multiplayer) using {@code Mob.rayIntersects}. */
    private Mob raycastRemoteMobs(Vector3f eye, Vector3f front) {
        Mob best = null;
        float bestT = REACH_DISTANCE;
        for (Mob m : remoteMobs.values()) {
            float t = Mob.rayIntersects(eye, front, bestT, m.getAABB());
            if (t >= 0f) {
                bestT = t;
                best = m;
            }
        }
        return best;
    }

    /** Tears down the multiplayer session and returns to the main menu. */
    private void leaveMultiplayer() {
        if (hostServer != null) {
            hostServer.close();
            hostServer = null;
        }
        if (netClient != null) {
            netClient.close();
            netClient = null;
        }
        remotePlayers.clear();
        remoteMobs.clear();
        netError = null;
    }

    /**
     * Drains every pending server packet and applies it to the local client
     * state. Returns a newly-created {@link World} if a WELCOME packet set one
     * up this frame (the caller assigns it to its {@code world} variable),
     * otherwise null. Runs entirely on the main thread.
     */
    private World processNetPackets(NetClient client, World world, World[] worlds, Player player, TextureAtlas atlas,
                                    Settings settings, Path saveRoot,
                                    WorldGenSettings genSettings,
                                    DayNightCycle dayNightCycle, Calendar calendar, boolean[] started,
                                    boolean[] mainMenuOpen, boolean[] multiplayerOpen, boolean[] mpConnecting,
                                    Window window, Input input, List<Hud.Message> messages, AudioEngine audio) {
        World created = null;
        Object packet;
        while ((packet = client.poll()) != null) {
            if (packet instanceof Packets.Welcome welcome) {
                // Server accepted us: build the client world from its seed + settings.
                WorldGenSettings remoteSettings = new WorldGenSettings();
                remoteSettings.setSeedText(Long.toString(welcome.seed()));
                remoteSettings.setWorldType(welcome.worldType());
                remoteSettings.setStructures(welcome.structures());
                remoteSettings.setSeaLevelIndex(welcome.seaLevelIndex());
                remoteSettings.setTerrainSizeIndex(welcome.terrainSizeIndex());
                remoteSettings.setWeeksPerMonthIndex(welcome.weeksPerMonth());
                // The client's world is a live mirror of the server's: unmodified
                // chunks regenerate from the seed, modified ones arrive over the
                // wire. A throwaway save dir keeps a previous session's stale
                // chunks from ever being loaded instead.
                Path clientSaveDir;
                try {
                    clientSaveDir = java.nio.file.Files.createTempDirectory("mclonemp");
                } catch (IOException e) {
                    clientSaveDir = saveRoot.getParent() != null
                            ? saveRoot.resolve("multiplayer_client").resolve(Long.toString(System.nanoTime()))
                            : Paths.get("multiplayer_client");
                }
                // The client mirrors every dimension the server hosts (Overworld /
                // Nether / End), each regenerated from the seed and synced over the wire.
                World[] mirrorWorlds = new World[DimensionType.values().length];
                for (DimensionType dim : DimensionType.values()) {
                    World w = new World(welcome.seed(), remoteSettings, atlas, clientSaveDir, dim);
                    w.setRenderDistance(settings.getRenderDistance());
                    w.setLeavesTransparent(settings.isLeavesTransparent());
                    byte dimId = (byte) dim.ordinal();
                    // When the client generates a chunk from the seed, ask the server
                    // whether a player has edited it; the server replies with full data
                    // or a vanilla ack.
                    w.setChunkListener((cx, cz) -> {
                        try {
                            if (netClient != null && netClient.isConnected()) {
                                netClient.sendChunkRequest(dimId, cx, cz);
                            }
                        } catch (IOException e) {
                            netError = e.getMessage();
                        }
                    });
                    mirrorWorlds[dim.ordinal()] = w;
                }
                World w = mirrorWorlds[DimensionType.OVERWORLD.ordinal()];
                for (int i = 0; i < 200; i++) w.update(0, 0);
                player.teleport(welcome.spawnX(), welcome.spawnY(), welcome.spawnZ());
                for (int i = 0; i < 80; i++) w.update(player.getPosition().x, player.getPosition().z);
                startCalendar(dayNightCycle, calendar, remoteSettings);
                System.out.println("Joined server (seed " + welcome.seed() + ", spawn " + welcome.spawnX() + "," + welcome.spawnY() + "," + welcome.spawnZ() + ")");
                created = w;
                world = w;
                currentDim[0] = DimensionType.OVERWORLD;
                // Hand the mirror worlds back to the caller so the dimension array and
                // the game loop's world switching stay consistent.
                returnMirrorWorlds = mirrorWorlds;
                try {
                    client.sendReady();
                } catch (IOException e) {
                    netError = e.getMessage();
                }
            } else if (packet instanceof Packets.PlayerJoined joined) {
                RemotePlayer rp = new RemotePlayer(joined.id(), joined.name());
                rp.update(joined.dimension(), joined.x(), joined.y(), joined.z(), joined.yaw(), joined.pitch(), false, false, false);
                rp.tick(1f); // snap the render pose to the join position
                remotePlayers.put(joined.id(), rp);
                showMessage(messages, joined.name() + " joined", new Vector4f(0.7f, 0.9f, 0.7f, 1f), 3f);
            } else if (packet instanceof Packets.PlayerLeft left) {
                RemotePlayer gone = remotePlayers.remove(left.id());
                if (gone != null) {
                    showMessage(messages, gone.name + " left", new Vector4f(0.9f, 0.7f, 0.7f, 1f), 3f);
                }
            } else if (packet instanceof Packets.PlayerState state) {
                RemotePlayer rp = remotePlayers.get(state.id());
                if (rp != null) {
                    rp.update(state.dimension(), state.x(), state.y(), state.z(), state.yaw(), state.pitch(),
                            state.onGround(), state.flying(), state.sprinting());
                }
            } else if (packet instanceof Packets.BlockChange change) {
                World target = (worlds != null && change.dimension() >= 0 && change.dimension() < worlds.length)
                        ? worlds[change.dimension()] : world;
                if (target != null) {
                    if (change.overlay()) {
                        target.setOverlay(change.x(), change.y(), change.z(), BlockType.byId(change.blockId()));
                    } else {
                        target.setBlock(change.x(), change.y(), change.z(), BlockType.byId(change.blockId()));
                        target.setBlockOrientation(change.x(), change.y(), change.z(), change.orientation());
                    }
                }
            } else if (packet instanceof Packets.ChunkData data) {
                World target = (worlds != null && data.dimension() >= 0 && data.dimension() < worlds.length)
                        ? worlds[data.dimension()] : world;
                if (target != null) {
                    target.applyRemoteChunkData(data.cx(), data.cz(), data.blocks(), data.overlays(), data.orientations());
                }
            } else if (packet instanceof Packets.ChunkAck ack) {
                // Chunk matches the seed - the client's own generation already has it.
            } else if (packet instanceof Packets.ChatMsg msg) {
                showMessage(messages, "<" + msg.name() + "> " + msg.text(), new Vector4f(0.92f, 0.92f, 0.92f, 1f), 5f);
            } else if (packet instanceof Packets.Reject reject) {
                showMessage(messages, reject.reason(), new Vector4f(0.9f, 0.3f, 0.3f, 1f), 5f);
                netError = reject.reason();
            } else if (packet instanceof Packets.DimensionChange change) {
                // Server moved us to another dimension - switch the active world.
                if (worlds != null && change.dimension() >= 0 && change.dimension() < worlds.length) {
                    currentDim[0] = DimensionType.values()[change.dimension()];
                    player.teleport(change.x(), change.y(), change.z());
                    World target = worlds[currentDim[0].ordinal()];
                    for (int i = 0; i < 80; i++) target.update(player.getPosition().x, player.getPosition().z);
                    world = target;
                    showMessage(messages, "Welcome to " + currentDim[0].displayName(),
                            new Vector4f(0.7f, 0.5f, 0.9f, 1f), 2.5f);
                }
            } else if (packet instanceof Packets.TimeSync sync) {
                dayNightCycle.setTime(sync.timeOfDay());
            } else if (packet instanceof Packets.PlayerDeath death) {
                RemotePlayer dead = remotePlayers.get(death.id());
                showMessage(messages, (dead != null ? dead.name : "A player") + " died",
                        new Vector4f(0.9f, 0.3f, 0.3f, 1f), 3f);
            } else if (packet instanceof Packets.MobSpawn spawn) {
                if (world != null && remoteMobs != null && !remoteMobs.containsKey(spawn.mobId())) {
                    Mob.Type type = spawn.typeId() >= 0 && spawn.typeId() < Mob.Type.values().length
                            ? Mob.Type.values()[spawn.typeId()] : Mob.Type.PIG;
                    Mob mob = new Mob(type, spawn.x(), spawn.y(), spawn.z());
                    mob.id = spawn.mobId();
                    mob.yaw = spawn.yaw();
                    mob.target.set(spawn.x(), spawn.y(), spawn.z());
                    mob.targetYaw = spawn.yaw();
                    remoteMobs.put(spawn.mobId(), mob);
                }
            } else if (packet instanceof Packets.MobState state) {
                if (world != null && remoteMobs != null) {
                    Mob mob = remoteMobs.get(state.mobId());
                    if (mob != null) {
                        mob.target.set(state.x(), state.y(), state.z());
                        mob.targetYaw = state.yaw();
                    }
                }
            } else if (packet instanceof Packets.MobRemove remove) {
                if (remoteMobs != null) {
                    Mob gone = remoteMobs.remove(remove.mobId());
                    // Drop the mob's loot where it died (unless it just despawned - y == -1000).
                    if (world != null && gone != null && remove.y() > -500f) {
                        BlockType drop = gone.dropType();
                        int count = 1 + (int) (Math.random() * (gone.type == Mob.Type.SHEEP ? 2 : 3));
                        world.spawnItem((int) Math.floor(remove.x()), (int) Math.floor(remove.y()),
                                (int) Math.floor(remove.z()), drop, count, new Random());
                    }
                }
            } else if (packet instanceof Packets.PlayerDamage dmg) {
                if (world != null && player != null) {
                    player.getStats().damage(dmg.amount());
                    audio.play(SoundEvent.HURT);
                }
            }
        }
        if (created != null) {
            started[0] = true;
            mainMenuOpen[0] = false;
            multiplayerOpen[0] = false;
            mpConnecting[0] = false;
            window.setCursorCaptured(true);
            input.resetMouseDelta();
        }
        return created;
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

        // Tell the server about the change so other players see it too (a door
        // clears both halves; an overlay clears only the decoration).
        if (netClient != null && netClient.isConnected()) {
            byte dim = (byte) currentDim[0].ordinal();
            if (Door.isDoor(targetType)) {
                int bottom = Door.bottomHalf(world, bx, by, bz);
                sendBlockChange(dim, bx, bottom, bz, BlockType.AIR, (byte) 0, false);
                sendBlockChange(dim, bx, bottom + 1, bz, BlockType.AIR, (byte) 0, false);
            } else if (targetingOverlay) {
                sendBlockChange(dim, bx, by, bz, BlockType.AIR, (byte) 0, true);
            } else {
                sendBlockChange(dim, bx, by, bz, BlockType.AIR, (byte) 0, false);
            }
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
        // Dedicated headless server: `--server [port]` (default 25565) hosts a
        // world with no window or GL at all. Clients join it with "Multiplayer"
        // -> "Join Server".
        if (args.length >= 1 && args[0].equals("--server")) {
            runDedicatedServer(args);
            return;
        }
        new Main().run();
    }

    /** Hosts a headless world that clients connect to; runs until interrupted. */
    private static void runDedicatedServer(String[] args) {
        int port = 25565;
        if (args.length >= 2) {
            try {
                port = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                System.err.println("Bad port '" + args[1] + "', using " + port);
            }
        }
        String saveEnv = System.getenv("MCCLONE_SAVE_DIR");
        Path saveRoot = saveEnv != null ? Paths.get(saveEnv).getParent() : Paths.get("saves");
        Path serverSaveDir = saveRoot.resolve("multiplayer_server");
        try {
            WorldGenSettings settings = new WorldGenSettings();
            long seed = settings.resolveSeed();
            GameServer server = new GameServer(port, settings, seed, serverSaveDir);
            server.start();
            System.out.println("Multiplayer server running on port " + server.getPort()
                    + " (seed " + seed + ", save dir " + serverSaveDir + ")");
            System.out.println("Press Ctrl+C to stop.");
            Thread.currentThread().join(); // block until interrupted
        } catch (Exception e) {
            System.err.println("Server failed: " + e.getMessage());
            e.printStackTrace();
        }
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
        currentDim[0] = DimensionType.OVERWORLD;
        boolean[] started = {false};
        List<String> worldNames = new ArrayList<>();

        hud = new Hud(lineShader, hudShader, font);
        hud.setGuiTextures(guiTextures, false); // theme is applied via applySettings below
        ItemRenderer itemRenderer = new ItemRenderer();
        HandRenderer handRenderer = new HandRenderer();
        MobRenderer mobRenderer = new MobRenderer();
        playerRenderer = new PlayerRenderer();
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
        boolean[] multiplayerOpen = {false}; // multiplayer connect screen
        int[] mainMenuSelection = {Hud.MENU_PLAY};
        int[] worldSelectSelection = {0};
        int[] worldGenSelection = {0};
        int[] editingRow = {-1};
        // Multiplayer connect screen state.
        int[] mpSelection = {0};
        int[] mpEditingRow = {-1};
        String[] mpName = {"Player"};
        String[] mpHost = {"127.0.0.1"};
        String[] mpPort = {"25565"};
        boolean[] mpConnecting = {false};
        float[] mpConnectingElapsed = {0f};
        float[] netMoveTimer = {0f}; // time since the last player-state packet was sent
        boolean[] chatOpen = {false};
        StringBuilder chatText = new StringBuilder();

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
        boolean[] wasSubmerged = {false}; // last frame's Player#isSubmerged(), to fire a splash sound only on the change

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
        // drop them in the middle of it, so swim physics (buoyancy, no onGround)
        // can be screenshotted with the F3 "Swimming" debug line on.
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

            // --- Multiplayer: surface connection errors, drain server packets, send local state. ---
            if (mpConnecting[0] && netError != null) {
                showMessage(messages, netError, new Vector4f(0.9f, 0.3f, 0.3f, 1f), 5f);
                leaveMultiplayer();
                mpConnecting[0] = false;
                multiplayerOpen[0] = false;
                mainMenuOpen[0] = true;
            }
            if (netClient != null) {
                if (netClient.isDisconnected()) {
                    String reason = netClient.getDisconnectReason();
                    if (started[0]) {
                        showMessage(messages, "Disconnected: " + reason, new Vector4f(0.9f, 0.3f, 0.3f, 1f), 5f);
                        started[0] = false;
                        // The worlds were throwaway multiplayer mirrors - release their
                        // chunks (and GL meshes) rather than leaving them resident.
                        if (worlds != null) {
                            for (World w : worlds) {
                                if (w != null) {
                                    w.saveAllModified();
                                    w.destroy();
                                }
                            }
                            for (int i = 0; i < worlds.length; i++) {
                                worlds[i] = null;
                            }
                        }
                        if (world != null) {
                            world = null;
                        }
                        returnMirrorWorlds = null;
                    } else if (mpConnecting[0]) {
                        showMessage(messages, "Could not connect: " + reason, new Vector4f(0.9f, 0.3f, 0.3f, 1f), 5f);
                    }
                    leaveMultiplayer();
                    mpConnecting[0] = false;
                    multiplayerOpen[0] = false;
                    mainMenuOpen[0] = true;
                    window.setCursorCaptured(false);
                    input.resetMouseDelta();
                } else {
                    World newWorld = processNetPackets(netClient, world, worlds, player, atlas, settings, saveRoot,
                            genSettings, dayNightCycle, calendar, started, mainMenuOpen, multiplayerOpen,
                            mpConnecting, window, input, messages, audio);
                    if (newWorld != null) {
                        world = newWorld;
                        // Multiplayer mirrors every dimension the server hosts - the
                        // WELCOME handler built them all; wire them into the dimension
                        // array so sky/portals/block-change routing stay consistent.
                        worlds = returnMirrorWorlds;
                        currentDim[0] = DimensionType.OVERWORLD;
                    }
                    // Send the local player's pose to the server at ~20 Hz.
                    if (started[0] && netMoveTimer[0] >= 0.05f) {
                        netMoveTimer[0] = 0f;
                        sendPlayerMove(netClient, player);
                    } else {
                        netMoveTimer[0] += dt;
                    }
                }
            }

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
                } else if (multiplayerOpen[0]) {
                    // Multiplayer connect screen: name/host/port fields + host & play / join / back.
                    if (mpEditingRow[0] >= 0) {
                        String typed = input.consumeTypedChars();
                        StringBuilder sb = new StringBuilder(switch (mpEditingRow[0]) {
                            case Hud.MP_ROW_NAME -> mpName[0];
                            case Hud.MP_ROW_HOST -> mpHost[0];
                            default -> mpPort[0];
                        });
                        for (int i = 0; i < typed.length(); i++) {
                            char ch = typed.charAt(i);
                            if (Character.isLetterOrDigit(ch) || ch == '-' || ch == '.' || ch == '_' || ch == ' ') {
                                if (mpEditingRow[0] == Hud.MP_ROW_PORT && !Character.isDigit(ch)) continue;
                                sb.append(ch);
                            }
                        }
                        if (input.isKeyJustPressed(GLFW_KEY_BACKSPACE)) {
                            if (sb.length() > 0) sb.deleteCharAt(sb.length() - 1);
                        }
                        switch (mpEditingRow[0]) {
                            case Hud.MP_ROW_NAME -> mpName[0] = sb.toString();
                            case Hud.MP_ROW_HOST -> mpHost[0] = sb.toString();
                            default -> mpPort[0] = sb.toString();
                        }
                        if (input.isKeyJustPressed(GLFW_KEY_ENTER) || input.isKeyJustPressed(GLFW_KEY_ESCAPE)) {
                            mpEditingRow[0] = -1;
                        }
                    } else {
                        if (input.isKeyJustPressed(GLFW_KEY_UP) || input.isKeyJustPressed(GLFW_KEY_W)) {
                            mpSelection[0] = Math.floorMod(mpSelection[0] - 1, Hud.MP_ROW_COUNT);
                        }
                        if (input.isKeyJustPressed(GLFW_KEY_DOWN) || input.isKeyJustPressed(GLFW_KEY_S)) {
                            mpSelection[0] = Math.floorMod(mpSelection[0] + 1, Hud.MP_ROW_COUNT);
                        }
                        boolean clickedMp = false;
                        float mpLx = ((float) input.getMouseX() / window.getWidth() * 2f - 1f) * window.getAspectRatio();
                        float mpLy = 1f - (float) input.getMouseY() / window.getHeight() * 2f;
                        int hoverMp = hud.multiplayerRowAt(mpLx, mpLy);
                        if (hoverMp >= 0) {
                            mpSelection[0] = hoverMp;
                            clickedMp = input.isMouseJustPressed(GLFW_MOUSE_BUTTON_LEFT);
                        }
                        if (input.isKeyJustPressed(GLFW_KEY_ENTER) || input.isKeyJustPressed(GLFW_KEY_SPACE) || clickedMp) {
                            if (mpSelection[0] <= Hud.MP_ROW_PORT) {
                                mpEditingRow[0] = mpSelection[0];
                                input.consumeTypedChars();
                            } else if (mpSelection[0] == Hud.MP_ROW_HOST_SERVER) {
                                int port = parsePort(mpPort[0]);
                                hostAndPlay(port, saveRoot, mpName[0]);
                                mpConnecting[0] = true;
                                mpConnectingElapsed[0] = 0f;
                                window.setCursorCaptured(false);
                            } else if (mpSelection[0] == Hud.MP_ROW_CONNECT) {
                                int port = parsePort(mpPort[0]);
                                joinServer(mpHost[0], port, mpName[0]);
                                mpConnecting[0] = true;
                                mpConnectingElapsed[0] = 0f;
                                window.setCursorCaptured(false);
                            } else {
                                multiplayerOpen[0] = false;
                                mainMenuOpen[0] = true;
                                mpConnecting[0] = false;
                            }
                        }
                        if (input.isKeyJustPressed(GLFW_KEY_ESCAPE)) {
                            multiplayerOpen[0] = false;
                            mainMenuOpen[0] = true;
                            mpConnecting[0] = false;
                        }
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
                        } else if (mainMenuSelection[0] == Hud.MENU_MULTIPLAYER) {
                            multiplayerOpen[0] = true;
                            mpSelection[0] = 0;
                            mpEditingRow[0] = -1;
                            mpConnecting[0] = false;
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

            // Chat: T opens the line (multiplayer only), Enter sends, Esc cancels.
            if (netClient != null && !menuOpen[0] && !inventoryOpen[0] && !creativeOpen[0]
                    && input.isKeyJustPressed(GLFW_KEY_T)) {
                chatOpen[0] = !chatOpen[0];
                if (chatOpen[0]) {
                    chatText.setLength(0);
                    input.consumeTypedChars();
                }
            }
            if (chatOpen[0]) {
                String typed = input.consumeTypedChars();
                for (int i = 0; i < typed.length() && chatText.length() < 200; i++) {
                    char ch = typed.charAt(i);
                    if (ch == '\n' || ch == '\r') continue;
                    chatText.append(ch);
                }
                if (input.isKeyJustPressed(GLFW_KEY_BACKSPACE) && chatText.length() > 0) {
                    chatText.deleteCharAt(chatText.length() - 1);
                }
                if (input.isKeyJustPressed(GLFW_KEY_ENTER)) {
                    if (chatText.length() > 0) {
                        sendChat(chatText.toString());
                    }
                    chatOpen[0] = false;
                } else if (input.isKeyJustPressed(GLFW_KEY_ESCAPE)) {
                    chatOpen[0] = false;
                }
            }

            if (!menuOpen[0] && !inventoryOpen[0] && !creativeOpen[0] && !chatOpen[0]) {
                // Cold exposure factor: how cold the weather is this frame (snow
                // is chilly, a blizzard is freezing). Player weighs shelter and
                // nearby fires against it before it affects hunger/health.
                Weather w = climate.getWeather();
                float coldFactor = (w == Weather.SNOW ? 0.6f : w == Weather.BLIZZARD ? 1f : 0f)
                        * climate.getWeatherStrength();
                player.update(dt, input, world, coldFactor);


                // Advance remote players' smoothed poses toward their server targets.
                for (RemotePlayer rp : remotePlayers.values()) {
                    rp.tick(dt);
                }
                // Same for remote mobs.
                for (Mob rm : remoteMobs.values()) {
                    rm.tickRemote(dt);
                }

                // Dimension portals: walking into a NETHER_PORTAL or END_PORTAL block
                // teleports the player to the linked dimension (with a short cooldown
                // so they don't instantly bounce back through the arrival portal). In
                // multiplayer the server is authoritative and sends a DimensionChange
                // back; single player teleports locally.
                teleportCooldown[0] = Math.max(0f, teleportCooldown[0] - dt);
                if (teleportCooldown[0] <= 0f) {
                    Vector3f p = player.getPosition();
                    BlockType portal = world.getBlock((int) Math.floor(p.x), (int) Math.floor(p.y + 0.5f), (int) Math.floor(p.z));
                    if (!portal.isPortal()) {
                        portal = world.getBlock((int) Math.floor(p.x), (int) Math.floor(p.y + 1.5f), (int) Math.floor(p.z));
                    }
                    if (portal.isPortal()) {
                        if (netClient != null && netClient.isConnected()) {
                            try {
                                netClient.sendPortalUse((byte) currentDim[0].ordinal(), portal.id);
                            } catch (IOException e) {
                                netError = e.getMessage();
                            }
                        } else {
                            teleportThroughPortal(player, worlds, currentDim, portal);
                            world = worlds[currentDim[0].ordinal()];
                            showMessage(messages, "Welcome to " + currentDim[0].displayName(),
                                    new Vector4f(0.7f, 0.5f, 0.9f, 1f), 2.5f);
                        }
                        teleportCooldown[0] = PORTAL_COOLDOWN_SECONDS;
                    }
                }


                if (player.hasJustJumped()) audio.play(SoundEvent.JUMP);
                if (player.hasJustLanded()) audio.play(SoundEvent.LAND);
                if (player.isSubmerged() != wasSubmerged[0]) {
                    audio.play(SoundEvent.SPLASH);
                    wasSubmerged[0] = player.isSubmerged();
                    // Diving in (or surfacing) already just played a splash this frame -
                    // without this, swimStrokeTimer sitting at 0 (reset while not
                    // swimming) would immediately fire a second, redundant one below.
                    swimStrokeTimer[0] = SWIM_STROKE_INTERVAL;
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
                    if (swimStrokeTimer[0] <= 0f) {
                        swimStrokeTimer[0] = SWIM_STROKE_INTERVAL;
                        audio.play(SoundEvent.SPLASH, 0.5f);
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
                if (netClient != null && netClient.isConnected()) {
                    // Server-authoritative respawn: it moves us to overworld spawn and
                    // replies with a DimensionChange that switches world + position.
                    try {
                        netClient.sendRespawn();
                    } catch (IOException e) {
                        netError = e.getMessage();
                    }
                } else {
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
            // In multiplayer, mobs are simulated server-side, so the local client skips
            // this (shared mob damage arrives via PLAYER_DAMAGE packets instead).
            Vector3f playerPos = player.getPosition();
            if (netClient == null) {
                AABB playerBox = new AABB(playerPos.x - 0.3f, playerPos.y, playerPos.z - 0.3f,
                        playerPos.x + 0.3f, playerPos.y + 1.8f, playerPos.z + 0.3f);
                float mobDamage = world.updateMobs(dt, playerPos, playerBox, dayNightCycle.isNight(), loot);
                if (mobDamage > 0f) {
                    player.takeDamage(mobDamage);
                    audio.play(SoundEvent.HURT);
                }
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

            if (!menuOpen[0] && !inventoryOpen[0] && !creativeOpen[0] && !chatOpen[0]) {
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
                Mob targetedMob = raycastTargetMob(player, world);
                targetedMobRef[0] = targetedMob;

                // Attacking: holding left-click hits the targeted mob on a short cooldown
                // (mobs can't be hurt in spectator - no interaction).
                if (targetedMob != null && !mode.isSpectator()
                        && input.isMouseDown(GLFW_MOUSE_BUTTON_LEFT) && attackCooldown[0] <= 0f) {
                    // Creative kills in one hit; survival/adventure deal tool damage
                    // (a sword hits harder than a bare-handed punch).
                    float damage = mode.isCreative() ? targetedMob.getMaxHealth() : Mining.attackDamage(heldItem);
                    if (netClient != null && netClient.isConnected()) {
                        // Server-authoritative: send the swing; the server applies
                        // damage and broadcasts death/loot.
                        sendMobAttack(targetedMob, damage);
                    } else {
                        boolean killed = world.damageMob(targetedMob, damage, player.getPosition().x, player.getPosition().z, loot);
                        audio.playAt(killed ? SoundEvent.MOB_DEATH : SoundEvent.ATTACK,
                                targetedMob.position.x, targetedMob.position.y, targetedMob.position.z, 1f);
                    }
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
                            syncDoorToServer((byte) currentDim[0].ordinal(), world, hit.blockPos.x, hit.blockPos.y, hit.blockPos.z);
                        }
                    } else if (Door.isTrapdoor(targeted)) {
                        if (noMob && mode.canPlace()) {
                            Door.toggleSingle(world, world::setBlock, hit.blockPos.x, hit.blockPos.y, hit.blockPos.z);
                            audio.playAt(SoundEvent.DOOR, hit.blockPos.x + 0.5f, hit.blockPos.y + 0.5f, hit.blockPos.z + 0.5f, 1f);
                            handRenderer.triggerSwing();
                            syncBlockToServer((byte) currentDim[0].ordinal(), world, hit.blockPos.x, hit.blockPos.y, hit.blockPos.z, true);
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
                                    byte facing = 0;
                                    if (heldItem.isDirectional() || heldItem == BlockType.DOOR || heldItem == BlockType.TRAPDOOR) {
                                        Vector3f front = player.getCamera().getFront();
                                        facing = (byte) (Math.abs(front.x) >= Math.abs(front.z)
                                                ? (front.x >= 0 ? 3 : 2)
                                                : (front.z >= 0 ? 1 : 0));
                                        world.setBlockOrientation(p.x, p.y, p.z, facing);
                                        // A door is 2 blocks tall: also place the top half.
                                        if (heldItem == BlockType.DOOR && world.getBlock(p.x, p.y + 1, p.z) == BlockType.AIR) {
                                            world.setBlock(p.x, p.y + 1, p.z, BlockType.DOOR);
                                            world.setBlockOrientation(p.x, p.y + 1, p.z, facing);
                                        }
                                    }
                                    // Tell the server so every other client places it too.
                                    if (netClient != null && netClient.isConnected()) {
                                        byte dim = (byte) currentDim[0].ordinal();
                                        sendBlockChange(dim, p.x, p.y, p.z, intoFluid ? world.getOverlay(p.x, p.y, p.z) : heldItem,
                                                intoFluid ? (byte) 0 : facing, intoFluid);
                                        if (heldItem == BlockType.DOOR && world.getBlock(p.x, p.y + 1, p.z) == BlockType.DOOR) {
                                            sendBlockChange(dim, p.x, p.y + 1, p.z, BlockType.DOOR, facing, false);
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
            List<Mob> renderMobs = new ArrayList<>(world.getMobs());
            renderMobs.addAll(remoteMobs.values());
            mobRenderer.render(mobTextures, renderMobs, world.getArrows());
            if (!remotePlayers.isEmpty()) {
                // Only players in the same dimension are visible, like Minecraft.
                List<RemotePlayer> visible = new ArrayList<>();
                byte myDim = (byte) currentDim[0].ordinal();
                for (RemotePlayer rp : remotePlayers.values()) {
                    if (rp.dimension == myDim) visible.add(rp);
                }
                if (!visible.isEmpty()) {
                    playerRenderer.render(mobTextures, visible);
                }
            }
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
                            player.getStats().getBreath(), PlayerStats.MAX_BREATH,
                            player.isSubmerged(),
                            Inventory.HOTBAR_SIZE, window.getAspectRatio());
                }
                // A frost vignette fades in over everything as you freeze outside
                // in a storm (see Player/PlayerStats cold exposure).
                hud.renderFrostOverlay(player.getStats().getColdness());
            }
            hud.renderMessages(messages, window.getAspectRatio());
            // The chat input line, drawn under the messages while typing.
            if (chatOpen[0] && netClient != null) {
                hud.drawTextLeft("> " + chatText + "_", -0.95f, 0.12f, 0.04f, WHITE, window.getAspectRatio());
            }
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
                hud.drawTextLeft(String.format(Locale.ROOT, "Climate: %.1f C, %.0f%% humidity",
                                climate.temperatureFor(biome), climate.humidityFor(biome) * 100f),
                        -0.95f, y - (line++) * step, textSize, WHITE, aspect);
                hud.drawTextLeft(String.format(Locale.ROOT, "Cold exposure: %.0f%%",
                                player.getStats().getColdness() * 100f),
                        -0.95f, y - (line++) * step, textSize, WHITE, aspect);
                if (player.isSubmerged()) {
                    hud.drawTextLeft(String.format(Locale.ROOT, "Breath: %.1fs / %.0fs",
                                    player.getStats().getBreath(), PlayerStats.MAX_BREATH),
                            -0.95f, y - (line++) * step, textSize, WHITE, aspect);
                }
                hud.drawTextLeft(String.format(Locale.ROOT, "Chunks: %d visible / %d loaded (render distance %d)",
                                world.getVisibleChunkCount(), world.getLoadedChunkCount(), world.getRenderDistance()),
                        -0.95f, y - (line++) * step, textSize, WHITE, aspect);
                hud.drawTextLeft(String.format(Locale.ROOT, "Entities: %d mobs, %d items",
                                world.getMobs().size() + remoteMobs.size(), world.getItems().size()),
                        -0.95f, y - (line++) * step, textSize, WHITE, aspect);
                if (netClient != null) {
                    hud.drawTextLeft("Multiplayer: " + remotePlayers.size() + " other player(s), " + remoteMobs.size() + " shared mob(s)",
                            -0.95f, y - (line++) * step, textSize, WHITE, aspect);
                }
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
                } else if (multiplayerOpen[0]) {
                    hud.renderMultiplayerMenu(mpName[0], mpHost[0], mpPort[0], mpSelection[0], mpEditingRow[0], window.getAspectRatio());
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
        leaveMultiplayer(); // close any embedded server and client connection

        hud.destroy();
        guiTextures.destroy();
        itemRenderer.destroy();
        handRenderer.destroy();
        mobRenderer.destroy();
        playerRenderer.destroy();
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
