package com.minecraftclone.net;

import com.minecraftclone.engine.DayNightCycle;
import com.minecraftclone.util.AABB;
import com.minecraftclone.world.Barrel;
import com.minecraftclone.world.BlockEntity;
import com.minecraftclone.world.BlockType;
import com.minecraftclone.world.Chest;
import com.minecraftclone.world.Chunk;
import com.minecraftclone.world.DimensionType;
import com.minecraftclone.world.Furnace;
import com.minecraftclone.world.SteamBoilerEntity;
import com.minecraftclone.world.SteamFurnaceEntity;
import com.minecraftclone.world.ItemEntity;
import com.minecraftclone.world.Mob;
import com.minecraftclone.world.World;
import com.minecraftclone.world.gen.TerrainGenerator;
import com.minecraftclone.world.gen.WorldGenSettings;
import org.joml.Vector3f;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * The authoritative multiplayer server: owns the single shared {@link World}
 * (headless - no OpenGL context, chunks generated but never meshed), accepts
 * clients over a plain TCP socket, and relays state between them.
 * <p>
 * Threading: one accept thread, one reader thread per client (packets are
 * decoded there and pushed onto a shared queue), and one tick thread that
 * drains the queue and runs the world at a fixed rate. All socket writes
 * happen on the tick thread, so a per-client {@link DataOutputStream} is
 * written from exactly one thread.
 * <p>
 * The server is authoritative for the world: it applies every place/break and
 * broadcasts the resulting {@code BLOCK_CHANGE} to everyone (including the
 * sender, who applies it like any other client). Terrain is deterministic
 * from the seed, so a client generates vanilla chunks locally and only asks
 * the server for the chunks a player has actually modified.
 */
public class GameServer implements AutoCloseable {

    private static final float TICK_SECONDS = 1f / 20f; // 20 Hz server tick
    private static final float MOVE_BROADCAST_SECONDS = 1f / 20f; // relay moves at up to 20 Hz
    private static final float MOB_BROADCAST_SECONDS = 1f / 10f; // mob states at 10 Hz
    private static final float TIME_SYNC_SECONDS = 1f; // server time of day, once a second
    private static final float NETHER_SCALE = 8f; // Overworld <-> Nether coordinate mapping
    private static final int MAX_PLAYERS = 12;
    /** Sockets accepted but not yet joined (each holds a thread); capped well above MAX_PLAYERS. */
    private static final int MAX_PENDING_CONNECTIONS = MAX_PLAYERS * 2;
    /** A silent connection is dropped after this long without any packet. */
    private static final int JOIN_TIMEOUT_MILLIS = 30_000;
    /** Max blocks between a player and the cell they edit (a little past REACH_DISTANCE). */
    private static final float MAX_EDIT_DISTANCE_SQ = 8f * 8f;
    /** How far from their position a client may request/edit chunks (in chunks). */
    private static final int MAX_CHUNK_RADIUS = 12;
    /** Packets handled per tick; the rest wait for the next tick (FIFO, so nobody starves). */
    private static final int MAX_PACKETS_PER_TICK = 256;
    /** Queued-packet ceiling; a client pushing past it is treated as misbehaving. */
    private static final int MAX_QUEUED_PACKETS = 4096;
    /** Server-side swing cooldown; anything faster than the client's 0.45s is clamped here. */
    private static final long ATTACK_COOLDOWN_NANOS = (long) (0.4f * 1_000_000_000L);
    /** The active per-client swing cooldown (shared by mob and PvP attacks). */
    private long attackCooldownNanos = ATTACK_COOLDOWN_NANOS;

    /** Overrides the swing cooldown - tests widen it so assertions don't race tick scheduling. */
    void setAttackCooldownNanos(long nanos) {
        this.attackCooldownNanos = nanos;
    }

    /** Pins the server's time of day - tests use it to reach nighttime instantly. */
    void setTimeOfDayForTesting(float t) {
        dayNightCycle.setTime(t);
    }
    /** Largest mob-hit damage the server accepts (creative one-shots need getMaxHealth). */
    private static final float MAX_ATTACK_DAMAGE = 100f;

    /** One connected client. Written only from the tick thread; read from its own reader thread. */
    private static final class Client {
        final Socket socket;
        final DataOutputStream out;
        volatile int id = -1;
        volatile String name = "";
        volatile float x, y, z, yaw, pitch;
        volatile boolean onGround, flying, sprinting;
        volatile byte dimension = 0; // DimensionType ordinal
        volatile boolean joined;
        volatile boolean disconnected;
        volatile long lastAttackNanos;
        /** True while this player is in bed voting to skip the night (multiplayer sleep). */
        volatile boolean sleepVoted;

        Client(Socket socket, DataOutputStream out) {
            this.socket = socket;
            this.out = out;
        }
    }

    /** A decoded packet waiting to be processed on the tick thread. */
    private record Incoming(Client client, Object packet) {
    }

    private final ServerSocket serverSocket;
    /** One authoritative world per dimension (Overworld/Nether/End). */
    private final World[] worlds;
    private final WorldGenSettings settings;
    private final long seed;
    private final Path saveDir;
    private final Path playersDir;
    /** Operator settings (port, player cap, pvp, motd) - dedicated servers load server.properties. */
    private final ServerConfig config;
    /** One banned player name per line, persisted next to the worlds. */
    private final java.util.Set<String> bannedNames = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final float spawnX;
    private final float spawnZ;

    private final ConcurrentLinkedQueue<Incoming> inbox = new ConcurrentLinkedQueue<>();
    private final Map<Integer, Client> clients = new ConcurrentHashMap<>();
    /** Accepted-but-not-yet-joined connections (bounded so idle peers can't pile up threads). */
    private final java.util.Set<Client> pending = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private volatile int nextId = 1;
    private volatile boolean running;
    /** Rotates which player's area each dimension streams per tick (bounds generation work). */
    private int streamRotation;
    private Thread acceptThread;
    private Thread tickThread;
    /** Server-authoritative day/night (drives hostile spawning); GL-free. */
    private final DayNightCycle dayNightCycle = new DayNightCycle();
    private final Random rnd = new Random();
    /** Mob ids we've already told clients about, so natural despawns broadcast removals. */
    private final java.util.Set<Integer> knownMobIds = java.util.concurrent.ConcurrentHashMap.newKeySet();
    /** Item ids already announced with ITEM_ADD; anything vanishing gets a REMOVE. */
    private final java.util.Set<Integer> knownItemIds = java.util.concurrent.ConcurrentHashMap.newKeySet();
    /**
     * Operator actions (kick/ban/unban) queued by the console thread and
     * drained on the tick thread, so all client-state mutation stays on one
     * thread as the Client contract requires.
     */
    private final ConcurrentLinkedQueue<Runnable> adminCommands = new ConcurrentLinkedQueue<>();
    private long lastTimeSync = System.nanoTime();

    /** The authoritative world for a client's current dimension. */
    private World worldOf(Client c) {
        int dim = c.dimension;
        if (dim < 0 || dim >= worlds.length) dim = DimensionType.OVERWORLD.ordinal();
        return worlds[dim];
    }

    /**
     * Creates a game server using the specified listening port and world settings.
     *
     * @param port    the TCP port on which the server listens
     * @param settings the world-generation settings
     * @param seed    the world seed
     * @param saveDir the directory for persisted world and server data
     */
    public GameServer(int port, WorldGenSettings settings, long seed, Path saveDir) throws IOException {
        this(configForPort(port), settings, seed, saveDir);
    }

    /** A config carrying just a port (used by the legacy port-based constructor). */
    private static ServerConfig configForPort(int port) {
        ServerConfig cfg = new ServerConfig();
        cfg.setPort(port);
        return cfg;
    }

    /**
     * Initializes the server, its dimension worlds, persisted bans, spawn location, and listening socket.
     *
     * @param config  server configuration, including the listening port
     * @param settings world-generation settings
     * @param seed    seed used to initialize the worlds
     * @param saveDir directory containing world and player data
     * @throws IOException if the server socket cannot be opened
     */
    public GameServer(ServerConfig config, WorldGenSettings settings, long seed, Path saveDir) throws IOException {
        this.config = config;
        this.settings = settings;
        this.seed = seed;
        this.saveDir = saveDir;
        this.playersDir = saveDir.resolve("players");
        loadBans();
        this.worlds = new World[DimensionType.values().length];
        for (DimensionType dim : DimensionType.values()) {
            World w = new World(seed, settings, null, saveDir, dim, true);
            w.setKeepChunks(true);
            w.setRenderDistance(8);
            worlds[dim.ordinal()] = w;
        }
        World world = worlds[DimensionType.OVERWORLD.ordinal()];
        // Generate a starting area around the origin so chunk requests near
        // spawn can be answered immediately and mob/block state is ready.
        for (int i = 0; i < 400; i++) {
            world.update(0, 0);
        }
        world.spawnInitialMobs(rnd, 0f, 0f, 10);
        float[] spawn = findSpawn();
        this.spawnX = spawn[0];
        this.spawnZ = spawn[1];
        this.serverSocket = new ServerSocket(config.getPort());
    }

    /**
     * Gets the local port used by the server socket.
     *
     * @return the server's local port
     */
    public int getPort() {
        return serverSocket.getLocalPort();
    }

    /** The operator config this server was built from (pvp flag, motd, player cap). */
    public ServerConfig getConfig() {
        return config;
    }

    /**
     * Gets the seed used to generate the server worlds.
     *
     * @return the world-generation seed
     */
    public long getSeed() {
        return seed;
    }

    public WorldGenSettings getSettings() {
        return settings;
    }

    public float getSpawnX() {
        return spawnX;
    }

    public float getSpawnZ() {
        return spawnZ;
    }

    public int getPlayerCount() {
        return (int) clients.values().stream().filter(c -> c.joined).count();
    }

    public void start() {
        running = true;
        acceptThread = new Thread(this::acceptLoop, "server-accept");
        acceptThread.setDaemon(true);
        acceptThread.start();
        tickThread = new Thread(this::tickLoop, "server-tick");
        tickThread.setDaemon(true);
        tickThread.start();
    }

    /**
     * Accepts incoming client connections and starts their reader threads.
     *
     * <p>New connections are subject to the pending-connection limit and join timeout.
     * Connections exceeding the limit are closed immediately.</p>
     */
    private void acceptLoop() {
        while (running) {
            try {
                Socket socket = serverSocket.accept();
                socket.setTcpNoDelay(true);
                // A peer that connects but never speaks holds a thread forever
                // without this; once joined, clients talk every tick anyway.
                socket.setSoTimeout(JOIN_TIMEOUT_MILLIS);
                if (pending.size() >= Math.max(config.getMaxPlayers() * 2, MAX_PENDING_CONNECTIONS)) {
                    // Too many half-open connections: refuse immediately rather
                    // than queueing another thread behind them.
                    socket.close();
                    continue;
                }
                DataOutputStream out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
                Client client = new Client(socket, out);
                pending.add(client);
                Thread reader = new Thread(() -> readLoop(client), "server-reader-" + socket.getPort());
                reader.setDaemon(true);
                reader.start();
            } catch (IOException e) {
                if (running) {
                    System.err.println("Server accept failed: " + e.getMessage());
                }
            }
        }
    }

    private void readLoop(Client client) {
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(client.socket.getInputStream()))) {
            while (running && !client.disconnected) {
                byte[] frame = Packets.readFrame(in);
                if (frame == null) break;
                Object packet = Packets.decode(frame);
                if (inbox.size() > MAX_QUEUED_PACKETS) {
                    // This client is producing far faster than the tick can drain;
                    // drop them instead of letting the queue grow without bound.
                    System.err.println("Client " + client.name + " flooded the inbox; disconnecting");
                    disconnect(client);
                    break;
                }
                inbox.add(new Incoming(client, packet));
            }
        } catch (EOFException | SocketException | java.net.SocketTimeoutException e) {
            // peer closed, or said nothing for JOIN_TIMEOUT_MILLIS
        } catch (IOException e) {
            if (running) System.err.println("Client read error: " + e.getMessage());
        } finally {
            inbox.add(new Incoming(client, null)); // null packet = disconnected
        }
    }

    private void tickLoop() {
        long lastMoveBroadcast = System.nanoTime();
        long lastMobBroadcast = System.nanoTime();
        long lastTick = System.nanoTime();
        while (running) {
            long now = System.nanoTime();
            float dt = (now - lastTick) / 1_000_000_000f;
            lastTick = now;

            processInbox();
            runAdminCommands();

            // Server-authoritative day/night drives hostile mob spawning.
            dayNightCycle.update(dt);
            // When dawn breaks naturally, clear any leftover bed votes so they
            // don't carry into the following night.
            if (!dayNightCycle.isNight() && anySleepVotes()) {
                for (Client c : clients.values()) c.sleepVoted = false;
                broadcastSleepState();
            }

            // Stream chunks around ONE player per dimension per tick, rotating
            // through the group. world.update has its own per-call time budget,
            // so calling it once per player could blow the whole 50ms tick at
            // MAX_PLAYERS; rotating keeps the cost fixed while every player's
            // area still streams several times a second (the server never
            // unloads chunks, so nothing generated is lost).
            Map<Byte, List<Client>> byDimension = new java.util.HashMap<>();
            for (Client c : clients.values()) {
                if (c.joined) byDimension.computeIfAbsent(c.dimension, k -> new ArrayList<>()).add(c);
            }
            int slot = streamRotation++;
            for (List<Client> group : byDimension.values()) {
                Client c = group.get(Math.floorMod(slot, group.size()));
                worldOf(c).update(c.x, c.z);
            }

            // Advance block entities (furnaces smelting) server-side so the
            // authoritative world keeps working while nobody has one open.
            for (World w : worlds) {
                w.tickBlockEntities(dt);
                // Dropped items: physics + expiry, no pickup (clients request that).
                w.updateItems(dt, null, null);
            }

            broadcastNewItems();

            // Simulate overworld mobs against every connected player (nearest-player targeting).
            tickMobs(dt);

            // Relay player moves to everyone else at a steady cadence.
            if ((now - lastMoveBroadcast) / 1_000_000_000f >= MOVE_BROADCAST_SECONDS) {
                lastMoveBroadcast = now;
                broadcastMoves();
            }

            // Relay mob poses to everyone at a coarser cadence.
            if ((now - lastMobBroadcast) / 1_000_000_000f >= MOB_BROADCAST_SECONDS) {
                lastMobBroadcast = now;
                broadcastMobStates();
            }

            // Relay the server's authoritative time of day so every client shares the same sky.
            if ((now - lastTimeSync) / 1_000_000_000f >= TIME_SYNC_SECONDS) {
                lastTimeSync = now;
                broadcastTime();
            }

            // Sleep the remainder of the tick.
            long elapsed = System.nanoTime() - now;
            long sleepNanos = (long) (TICK_SECONDS * 1_000_000_000L) - elapsed;
            if (sleepNanos > 0) {
                try {
                    Thread.sleep(sleepNanos / 1_000_000L, (int) (sleepNanos % 1_000_000L));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    /** Advances overworld mobs and routes damage to whichever player each mob targeted. */
    private void tickMobs(float dt) {
        World world = worlds[DimensionType.OVERWORLD.ordinal()];
        List<Client> players = new ArrayList<>();
        List<Vector3f> positions = new ArrayList<>();
        List<AABB> boxes = new ArrayList<>();
        for (Client c : clients.values()) {
            if (!c.joined) continue;
            players.add(c);
            positions.add(new Vector3f(c.x, c.y, c.z));
            boxes.add(new AABB(c.x - 0.3f, c.y, c.z - 0.3f, c.x + 0.3f, c.y + 1.8f, c.z + 0.3f));
        }
        if (players.isEmpty()) return;
        World.MobDamageResult result = world.updateMobsMulti(dt, positions, boxes, dayNightCycle.isNight(), rnd, settings.getDifficulty());
        for (int i = 0; i < players.size(); i++) {
            if (result.damage()[i] > 0f) {
                try {
                    // Mob hits shove the player away from the mob that actually
                    // landed the hit (the world sim reports the attacker).
                    float kx = 0f, kz = 0f;
                    if (result.srcX()[i] != 0f || result.srcZ()[i] != 0f) {
                        float ddx = positions.get(i).x - result.srcX()[i];
                        float ddz = positions.get(i).z - result.srcZ()[i];
                        float len = (float) Math.sqrt(ddx * ddx + ddz * ddz);
                        if (len > 1e-4f) { kx = ddx / len; kz = ddz / len; }
                    }
                    send(players.get(i), Packets.encodePlayerDamage(
                            new Packets.PlayerDamage(result.damage()[i], kx, kz)));
                } catch (IOException e) {
                    disconnect(players.get(i));
                }
            }
        }
    }

    /** Sends every currently-loaded overworld mob's pose to all clients, plus spawn/removal diffs. */
    private void broadcastMobStates() {
        if (clients.isEmpty()) return;
        World world = worlds[DimensionType.OVERWORLD.ordinal()];
        List<Mob> mobs = world.getMobs();
        java.util.Set<Integer> live = java.util.concurrent.ConcurrentHashMap.newKeySet();
        for (Mob m : mobs) {
            live.add(m.id);
            // A mob the server just spawned but we haven't told clients about yet.
            if (!knownMobIds.contains(m.id)) {
                knownMobIds.add(m.id);
                try {
                    broadcastAll(Packets.encodeMobSpawn(new Packets.MobSpawn(
                            m.id, (byte) m.type.ordinal(), m.position.x, m.position.y, m.position.z, m.yaw)));
                } catch (IOException e) {
                    continue;
                }
            }
            byte[] payload;
            try {
                payload = Packets.encodeMobState(new Packets.MobState(
                        m.id, m.position.x, m.position.y, m.position.z, m.yaw, m.getHealth()));
            } catch (IOException e) {
                continue;
            }
            broadcastAll(payload);
        }
        // A previously-known mob that vanished (despawned / despawned at dawn) without
        // a kill packet - tell clients to remove it.
        for (int id : List.copyOf(knownMobIds)) {
            if (!live.contains(id)) {
                knownMobIds.remove(id);
                try {
                    broadcastAll(Packets.encodeMobRemove(new Packets.MobRemove(
                            id, (byte) 0, 0f, -1000f, 0f)));
                } catch (IOException e) {
                    // best-effort
                }
            }
        }
    }

    /** Sends a freshly-spawned overworld mob to a single client (used on join so they see existing mobs). */
    private void sendMobSpawns(Client client) {
        World world = worlds[DimensionType.OVERWORLD.ordinal()];
        for (Mob m : world.getMobs()) {
            try {
                send(client, Packets.encodeMobSpawn(new Packets.MobSpawn(
                        m.id, (byte) m.type.ordinal(), m.position.x, m.position.y, m.position.z, m.yaw)));
            } catch (IOException e) {
                disconnect(client);
                return;
            }
        }
    }

    private void processInbox() {
        // Cap the drain so one chatty client can't monopolize the tick: the
        // rest of the queue waits for the next tick (FIFO keeps it fair).
        int budget = MAX_PACKETS_PER_TICK;
        Incoming incoming;
        while (budget-- > 0 && (incoming = inbox.poll()) != null) {
            Client client = incoming.client();
            Object packet = incoming.packet();
            if (packet == null) {
                disconnect(client);
                continue;
            }
            try {
                if (packet instanceof Packets.Join join) {
                    handleJoin(client, join);
                } else if (packet instanceof Packets.Move move) {
                    handleMove(client, move);
                } else if (packet instanceof Packets.PlaceBlock place) {
                    handlePlace(client, place);
                } else if (packet instanceof Packets.BreakBlock brk) {
                    handleBreak(client, brk);
                } else if (packet instanceof Packets.Chat chat) {
                    handleChat(client, chat);
                } else if (packet instanceof Packets.ChunkRequest req) {
                    requestChunk(client, req.dimension(), req.cx(), req.cz());
                } else if (packet instanceof Packets.MobAttack attack) {
                    handleMobAttack(client, attack);
                } else if (packet instanceof Packets.PortalUse portal) {
                    handlePortalUse(client, portal);
                } else if (packet instanceof Packets.Respawn) {
                    handleRespawn(client);
                } else if (packet instanceof Packets.ContainerOpen open) {
                    handleContainerOpen(client, open);
                } else if (packet instanceof Packets.ContainerData data) {
                    handleContainerUpdate(client, data);
                } else if (packet instanceof Packets.CastingOperation operation) {
                    handleCastingOperation(client, operation);
                } else if (packet instanceof Packets.ItemSpawn spawn) {
                    handleItemSpawn(client, spawn);
                } else if (packet instanceof Packets.ItemPickup pickup) {
                    handleItemPickup(client, pickup);
                } else if (packet instanceof Packets.PlayerSync sync) {
                    handlePlayerSync(client, sync);
                } else if (packet instanceof Packets.PlayerAttack attack) {
                    handlePlayerAttack(client, attack);
                } else if (packet instanceof Packets.SleepVote) {
                    handleSleepVote(client);
                }
            } catch (IOException e) {
                disconnect(client);
            }
            // READY is a no-op on the server: spawn + surrounding chunks were
            // already sent as part of the join handshake.
        }
    }

    /**
     * Accepts a client join request, initializes the player's state, and sends
     * the player their welcome data and current world state.
     *
     * @param client the client requesting to join
     * @param join   the join request containing the player's requested name
     * @throws IOException if sending join data fails
     */
    private void handleJoin(Client client, Packets.Join join) throws IOException {
        if (client.joined) {
            // A second JOIN on the same connection would reassign its id and
            // orphan the old map entry - refuse it.
            send(client, Packets.encodeReject("Already joined."));
            return;
        }
        if (getPlayerCount() >= config.getMaxPlayers()) {
            send(client, Packets.encodeReject("Server is full."));
            disconnect(client);
            return;
        }
        // The id must exist before the fallback name is built from it.
        client.id = nextId++;
        String name = join.name().trim();
        if (!isSafePlayerName(name)) name = "Player-" + client.id;
        if (isBanned(name)) {
            send(client, Packets.encodeReject("You are banned from this server."));
            System.out.println("Rejected banned player: " + name);
            disconnect(client);
            return;
        }
        client.name = name;
        client.joined = true;
        pending.remove(client);
        client.dimension = (byte) DimensionType.OVERWORLD.ordinal();
        clients.put(client.id, client);

        World world = worldOf(client);

        // Spawn them a little above the surface so they fall in gently.
        float y = world.getSurfaceHeight((int) Math.floor(spawnX), (int) Math.floor(spawnZ)) + 2f;
        // Seed the tracked position with the spawn point: until the client's
        // first Move arrives, reach/chunk-radius checks measure against this
        // rather than an arbitrary origin.
        client.x = spawnX;
        client.y = y;
        client.z = spawnZ;

        // Tell the newcomer who they are and about the world.
        Packets.Welcome welcome = new Packets.Welcome(
                client.id, seed,
                settings.getWorldTypeIndex(), settings.hasStructures(),
                settings.getSeaLevelIndex(), settings.getTerrainSizeIndex(), settings.getWeeksPerMonthIndex(),
                spawnX, y, spawnZ);
        send(client, Packets.encodeWelcome(welcome));

        // Tell the newcomer about everyone already here.
        for (Client other : clients.values()) {
            if (other != client && other.joined) {
                send(client, Packets.encodePlayerJoined(new Packets.PlayerJoined(
                        other.id, other.name, other.dimension, other.x, other.y, other.z, other.yaw, other.pitch)));
            }
        }
        // Tell everyone else the newcomer appeared.
        broadcastOthers(client, Packets.encodePlayerJoined(new Packets.PlayerJoined(
                client.id, name, client.dimension, spawnX, y, spawnZ, 0f, 0f)));

        // Send the newcomer every currently-loaded overworld mob so the world isn't empty for them.
        sendMobSpawns(client);

        // Same for dropped items across every dimension.
        for (World w : worlds) {
            byte dim = (byte) worldOfDimension(w);
            for (ItemEntity e : w.getItems()) {
                if (e.tinkersItem != null || e.id <= 0) continue;
                try {
                    send(client, Packets.encodeItemAdd(new Packets.ItemAdd(
                            e.id, dim, e.position.x, e.position.y, e.position.z, e.type.id, e.count)));
                } catch (IOException ignored) {
                    // Fixed-size record; cannot realistically fail.
                }
            }
        }

        // If this player has a snapshot from a previous session, hand it back
        // so they reappear where they left off (position, inventory, stats).
        Path saved = playerFile(name);
        if (saved != null && java.nio.file.Files.isRegularFile(saved)) {
            try {
                String data = String.join("\n", java.nio.file.Files.readAllLines(saved));
                send(client, Packets.encodePlayerRestore(data));
                System.out.println("Restored saved player state for " + name);
            } catch (IOException e) {
                System.err.println("Could not read player file for " + name + ": " + e.getMessage());
            }
        }

        // Message of the day: delivered as a chat line from "Server" so it
        // lands in the same place every other message does.
        if (!config.getMotd().isEmpty()) {
            send(client, Packets.encodeChatMsg(new Packets.ChatMsg(0, "Server", config.getMotd())));
        }
        System.out.println(name + " joined (" + getPlayerCount() + " online)");
    }

    /**
     * Validates whether a player name uses an allowed format.
     *
     * @param name the player name to validate
     * @return {@code true} if the name contains 1 to 16 letters, digits, underscores,
     *         or dashes; {@code false} otherwise
     */
    private static boolean isSafePlayerName(String name) {
        if (name.isEmpty() || name.length() > 16) return false;
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (!(Character.isLetterOrDigit(c) || c == '_' || c == '-')) return false;
        }
        return true;
    }

    // ------------------------------------------------------------------
    // Operator administration (dedicated-server console)
    /**
     * Gets the path to the persisted banned-player list.
     *
     * @return the path to the banned-player file
     */

    private Path banFile() {
        return saveDir.resolve("banned-players.txt");
    }

    /** Loads the persisted ban list (one name per line); a missing file means nobody is banned. */
    private void loadBans() {
        Path file = banFile();
        if (!java.nio.file.Files.isRegularFile(file)) return;
        try {
            for (String line : java.nio.file.Files.readAllLines(file)) {
                String name = line.trim();
                if (!name.isEmpty()) bannedNames.add(name.toLowerCase(java.util.Locale.ROOT));
            }
        } catch (IOException e) {
            System.err.println("Could not read banned-players.txt: " + e.getMessage());
        }
    }

    /**
     * Persists the current banned-player names to the server's ban file.
     */
    private void saveBans() {
        try {
            java.nio.file.Files.createDirectories(saveDir);
            java.nio.file.Files.write(banFile(),
                    bannedNames.stream().sorted().toList(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.err.println("Could not write banned-players.txt: " + e.getMessage());
        }
    }

    /**
     * Checks whether a player name appears on the ban list.
     *
     * @param name the player name to check
     * @return {@code true} if the name is banned, {@code false} otherwise
     */
    public boolean isBanned(String name) {
        return name != null && bannedNames.contains(name.toLowerCase(java.util.Locale.ROOT));
    }

    /**
     * Persists a name ban and disconnects the matching online player.
     *
     * @return {@code true} if the ban was newly added, {@code false} otherwise
     */
    public boolean ban(String name) {
        if (!isSafePlayerName(name)) return false;
        boolean added = bannedNames.add(name.toLowerCase(java.util.Locale.ROOT));
        saveBans();
        kick(name);
        return added;
    }

    /** Lifts a ban; returns true when the name had been banned. */
    public boolean unban(String name) {
        boolean removed = name != null && bannedNames.remove(name.toLowerCase(java.util.Locale.ROOT));
        if (removed) saveBans();
        return removed;
    }

    /**
     * Provides the names of all banned players in sorted order.
     *
     * @return a sorted list of banned player names
     */
    public List<String> getBannedNames() {
        return bannedNames.stream().sorted().toList();
    }

    /**
     * Disconnects the joined player whose name matches the supplied name,
     * ignoring letter case.
     *
     * @param name the player name to search for
     * @return {@code true} if a matching player was disconnected, {@code false} otherwise
     */
    /**
     * Runs operator actions queued from the console thread on the tick
     * thread, keeping all client-state mutation single-threaded.
     */
    private void runAdminCommands() {
        Runnable cmd;
        while ((cmd = adminCommands.poll()) != null) {
            cmd.run();
        }
    }

    public boolean kick(String name) {
        for (Client c : clients.values()) {
            if (c.joined && c.name.equalsIgnoreCase(name)) {
                // Queue the actual disconnect for the tick thread: Client
                // state is only written there, and the console thread must
                // not race it.
                adminCommands.add(() -> {
                    try {
                        send(c, Packets.encodeReject("Kicked by operator."));
                    } catch (IOException ignored) {
                    }
                    disconnect(c);
                });
                System.out.println("Kicked " + c.name);
                return true;
            }
        }
        return false;
    }

    /**
     * The file holding {@code name}'s last-known player snapshot, or null when
     * the name isn't filename-safe (it would never have been written).
     */
    private Path playerFile(String name) {
        return isSafePlayerName(name) ? playersDir.resolve(name.toLowerCase(java.util.Locale.ROOT) + ".txt") : null;
    }

    /**
     * A client published its latest snapshot (position, inventory, stats...).
     * Kept in the server's save dir under the player's name, so it survives
     * server restarts and is handed back on their next join.
     */
    private void handlePlayerSync(Client client, Packets.PlayerSync sync) throws IOException {
        if (!client.joined || !isSafePlayerName(client.name)) return;
        if (sync.data().length() > Packets.MAX_PLAYER_SYNC_CHARS) return;
        try {
            java.nio.file.Files.createDirectories(playersDir);
            java.nio.file.Files.write(playerFile(client.name),
                    sync.data().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (IOException e) {
            System.err.println("Could not save player state for " + client.name + ": " + e.getMessage());
        }
    }

    private void handleMove(Client client, Packets.Move move) {
        if (!client.joined) return;
        client.x = move.x();
        client.y = move.y();
        client.z = move.z();
        client.yaw = move.yaw();
        client.pitch = move.pitch();
        client.onGround = move.onGround();
        client.flying = move.flying();
        client.sprinting = move.sprinting();
    }

    /**
     * True if the client may touch the given cell: within a Minecraft-ish reach
     * of their reported position. Without this a client could edit (and force
     * permanent generation of) chunks anywhere in the world.
     */
    private boolean canReach(Client client, int x, int y, int z) {
        float dx = x + 0.5f - client.x;
        float dy = y + 0.5f - client.y;
        float dz = z + 0.5f - client.z;
        return dx * dx + dy * dy + dz * dz <= MAX_EDIT_DISTANCE_SQ;
    }

    /** True if the client may ask for this chunk (bounded around their position). */
    private boolean canRequestChunk(Client client, int cx, int cz) {
        int pcx = World.worldToChunk((int) Math.floor(client.x));
        int pcz = World.worldToChunk((int) Math.floor(client.z));
        int dx = cx - pcx, dz = cz - pcz;
        return dx * dx + dz * dz <= MAX_CHUNK_RADIUS * MAX_CHUNK_RADIUS;
    }

    private void handlePlace(Client client, Packets.PlaceBlock place) throws IOException {
        if (!client.joined) return;
        BlockType type = place.blockId() < 0 ? null : BlockType.byId(place.blockId());
        if (type == null || type.isItem || place.y() < 0 || place.y() >= Chunk.HEIGHT) return;
        if (!canReach(client, place.x(), place.y(), place.z())) return;
        World world = worldOf(client);
        world.ensureChunk(World.worldToChunk(place.x()), World.worldToChunk(place.z()));
        if (place.overlay()) {
            world.setOverlay(place.x(), place.y(), place.z(), type);
        } else {
            world.setBlock(place.x(), place.y(), place.z(), type);
            if (place.orientation() != 0) {
                world.setBlockOrientation(place.x(), place.y(), place.z(), place.orientation());
            }
        }
        broadcastAll(Packets.encodeBlockChange(new Packets.BlockChange(
                client.dimension, place.x(), place.y(), place.z(), type.id, place.orientation(), place.overlay())));
    }

    private void handleBreak(Client client, Packets.BreakBlock brk) throws IOException {
        if (!client.joined) return;
        if (brk.y() < 0 || brk.y() >= Chunk.HEIGHT) return;
        if (!canReach(client, brk.x(), brk.y(), brk.z())) return;
        World world = worldOf(client);
        world.ensureChunk(World.worldToChunk(brk.x()), World.worldToChunk(brk.z()));
        if (brk.overlay()) {
            world.setOverlay(brk.x(), brk.y(), brk.z(), BlockType.AIR);
        } else {
            world.setBlock(brk.x(), brk.y(), brk.z(), BlockType.AIR);
        }
        broadcastAll(Packets.encodeBlockChange(new Packets.BlockChange(
                client.dimension, brk.x(), brk.y(), brk.z(), BlockType.AIR.id, (byte) 0, brk.overlay())));
    }

    private void handleChat(Client client, Packets.Chat chat) throws IOException {
        if (!client.joined) return;
        String text = chat.text().trim();
        if (text.isEmpty()) return;
        broadcastAll(Packets.encodeChatMsg(new Packets.ChatMsg(client.id, client.name, text)));
        System.out.println("<" + client.name + "> " + text);
    }

    /**
     * A player swung at a mob: apply the damage server-side, drop the loot if it
     * died, and broadcast the removal so every client sees the same result.
     * The server never trusts the client's numbers: damage is clamped, the
     * target must be within reach of the attacker's reported position, and a
     * per-client cooldown throttles spam.
     */
    private void handleMobAttack(Client client, Packets.MobAttack attack) throws IOException {
        if (!client.joined) return;
        float damage = attack.damage();
        if (!Float.isFinite(damage) || damage <= 0f) return;
        damage = Math.min(damage, MAX_ATTACK_DAMAGE);
        long now = System.nanoTime();
        if (now - client.lastAttackNanos < attackCooldownNanos) return;
        client.lastAttackNanos = now;
        World world = worlds[DimensionType.OVERWORLD.ordinal()];
        Mob mob = world.mobById(attack.mobId());
        if (mob == null) return;
        float dx = mob.position.x - client.x;
        float dy = mob.position.y - client.y;
        float dz = mob.position.z - client.z;
        if (dx * dx + dy * dy + dz * dz > MAX_EDIT_DISTANCE_SQ) return;
        boolean killed = world.damageMob(mob, damage, client.x, client.z, rnd);
        if (killed) {
            knownMobIds.remove(mob.id);
            broadcastAll(Packets.encodeMobRemove(new Packets.MobRemove(
                    mob.id, (byte) mob.type.ordinal(), mob.position.x, mob.position.y, mob.position.z)));
        }
    }

    /**
     * A player stepped into a portal block: teleport them to the linked dimension,
     * server-authoritative (nether coords scale 1:8, the End drops you on its central
     * island), and broadcast the move + dimension change so every client agrees.
     */
    private void handlePortalUse(Client client, Packets.PortalUse portal) throws IOException {
        if (!client.joined) return;
        DimensionType from = DimensionType.values()[portal.dimension() & 0xFF];
        BlockType portalBlock = BlockType.byId(portal.blockId());
        DimensionType to = DimensionType.portalDestination(portalBlock, from);
        World target = worlds[to.ordinal()];
        Vector3f pos = new Vector3f(client.x, client.y, client.z);

        float x, z;
        if (to == DimensionType.END) {
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

        // Generate the arrival area so the player has solid ground under them.
        for (int i = 0; i < 100; i++) {
            target.update(x, z);
        }
        int fx = (int) Math.floor(x);
        int fz = (int) Math.floor(z);
        int surfaceY = landingSurfaceY(target, fx, fz);

        // Spawn a matching return portal right at the landing spot (Minecraft does the
        // same), then nudge the player clear of it so they don't step straight back.
        if (to != DimensionType.OVERWORLD) {
            BlockType returnPortal = to == DimensionType.NETHER ? BlockType.NETHER_PORTAL : BlockType.END_PORTAL;
            target.setBlock(fx, surfaceY + 1, fz, returnPortal);
            x += 2.5f;
            fx = (int) Math.floor(x);
            int offsetY = landingSurfaceY(target, fx, fz);
            if (offsetY > 1) {
                surfaceY = offsetY;
            }
        }

        client.dimension = (byte) to.ordinal();
        client.x = x;
        client.y = surfaceY + 2f;
        client.z = z;
        send(client, Packets.encodeDimensionChange(new Packets.DimensionChange(client.dimension, client.x, client.y, client.z)));
        System.out.println(client.name + " teleported to " + to.displayName());
    }

    /** A player died: respawn them at the overworld world spawn, exactly like single player. */
    private void handleRespawn(Client client) throws IOException {
        if (!client.joined) return;
        client.dimension = (byte) DimensionType.OVERWORLD.ordinal();
        client.x = spawnX;
        client.z = spawnZ;
        World world = worldOf(client);
        client.y = world.getSurfaceHeight((int) Math.floor(spawnX), (int) Math.floor(spawnZ)) + 2f;
        client.yaw = 0f;
        client.pitch = 0f;
        send(client, Packets.encodeDimensionChange(new Packets.DimensionChange(client.dimension, client.x, client.y, client.z)));
        broadcastOthers(client, Packets.encodePlayerDeath(new Packets.PlayerDeath(client.id)));
    }

    /**
     * The block-entity types that sync over the wire. All formed containers
     * are included; the smeltery's entity only exists while its structure is
     * intact, so an unformed controller simply has nothing to snapshot.
     */
    private static boolean isSyncedContainer(BlockType block) {
        return block == BlockType.CHEST || block == BlockType.BARREL || block == BlockType.FURNACE
                || block == BlockType.PART_BUILDER || block == BlockType.TOOL_STATION
                || block == BlockType.SMELTERY_CONTROLLER
                || block == BlockType.CASTING_TABLE || block == BlockType.CASTING_BASIN
                || block == BlockType.STEAM_BOILER || block == BlockType.STEAM_FURNACE;
    }

    /** True if the block-entity type name matches the block actually at the cell. */
    private static boolean typeMatchesBlock(String type, BlockType block) {
        return switch (type) {
            case Chest.TYPE -> block == BlockType.CHEST;
            case Barrel.TYPE -> block == BlockType.BARREL;
            case Furnace.TYPE -> block == BlockType.FURNACE;
            case com.minecraftclone.world.tinkers.PartBuilderEntity.TYPE -> block == BlockType.PART_BUILDER;
            case com.minecraftclone.world.tinkers.ToolStationEntity.TYPE -> block == BlockType.TOOL_STATION;
            case com.minecraftclone.world.multiblock.SmelteryEntity.TYPE -> block == BlockType.SMELTERY_CONTROLLER;
            case com.minecraftclone.world.CastingEntity.TABLE_TYPE -> block == BlockType.CASTING_TABLE;
            case com.minecraftclone.world.CastingEntity.BASIN_TYPE -> block == BlockType.CASTING_BASIN;
            case SteamBoilerEntity.TYPE -> block == BlockType.STEAM_BOILER;
            case SteamFurnaceEntity.TYPE -> block == BlockType.STEAM_FURNACE;
            default -> false;
        };
    }

    /** Serializes a container's state using its own disk-save format. */
    private static byte[] snapshotEntity(BlockEntity entity) throws IOException {
        java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(buf);
        entity.writeTo(out);
        out.flush();
        return buf.toByteArray();
    }

    /** Restores a container's state from its serialized form (see {@link #snapshotEntity}). */
    private static BlockEntity restoreEntity(World world, int x, int y, int z, String type, byte[] payload)
            throws IOException {
        BlockEntity existing = world.blockEntityAt(x, y, z);
        // A smeltery entity only exists while its structure is formed - never
        // create one from the wire at a bare controller block, or a ghost
        // unformed entity would squat on the position.
        if (existing == null && com.minecraftclone.world.multiblock.SmelteryEntity.TYPE.equals(type)) {
            return null;
        }
        if (existing != null && existing.type().equals(type)) {
            try (DataInputStream in = new DataInputStream(new java.io.ByteArrayInputStream(payload))) {
                existing.readFrom(in);
            }
            return existing;
        }
        try (DataInputStream in = new DataInputStream(new java.io.ByteArrayInputStream(payload))) {
            return world.restoreBlockEntity(x, y, z, type, in);
        }
    }

    /**
     * A player right-clicked a container: validate it's a real container within
     * reach, create the authoritative entity if this is its first open, and
     * reply with a full snapshot.
     */
    private void handleContainerOpen(Client client, Packets.ContainerOpen open) throws IOException {
        if (!client.joined) return;
        if (open.dimension() < 0 || open.dimension() >= worlds.length
                || open.y() < 0 || open.y() >= Chunk.HEIGHT) return;
        if (!canReach(client, open.x(), open.y(), open.z())) return;
        World world = worlds[open.dimension()];
        BlockType block = world.getBlock(open.x(), open.y(), open.z());
        if (!isSyncedContainer(block)) return;
        world.ensureChunk(World.worldToChunk(open.x()), World.worldToChunk(open.z()));
        BlockEntity entity = switch (block) {
            case CHEST -> world.getOrCreateChest(open.x(), open.y(), open.z());
            case BARREL -> world.getOrCreateBarrel(open.x(), open.y(), open.z());
            case FURNACE -> world.getOrCreateFurnace(open.x(), open.y(), open.z());
            case PART_BUILDER -> world.getOrCreatePartBuilder(open.x(), open.y(), open.z());
            case TOOL_STATION -> world.getOrCreateToolStation(open.x(), open.y(), open.z());
            case SMELTERY_CONTROLLER -> {
                // Formed by structure detection, not getOrCreate - absent when
                // the shell is broken or hasn't been detected yet.
                yield world.blockEntityAt(open.x(), open.y(), open.z()) instanceof com.minecraftclone.world.multiblock.SmelteryEntity se ? se : null;
            }
            case CASTING_TABLE, CASTING_BASIN -> world.getOrCreateCasting(open.x(), open.y(), open.z());
            case STEAM_BOILER -> world.getOrCreateSteamBoiler(open.x(), open.y(), open.z());
            case STEAM_FURNACE -> world.getOrCreateSteamFurnace(open.x(), open.y(), open.z());
            default -> null;
        };
        if (entity == null) return;
        send(client, Packets.encodeContainerData(new Packets.ContainerData(
                open.dimension(), open.x(), open.y(), open.z(), entity.type(), snapshotEntity(entity))));
    }

    /**
     * A client closed a container GUI and publishes what it left inside: apply
     * it to the authoritative world (so the server and any later opener see the
     * same contents), persist it, and rebroadcast to everyone else - including
     * clients with that container open, whose GUI updates live.
     */
    private void handleContainerUpdate(Client client, Packets.ContainerData data) throws IOException {
        if (!client.joined) return;
        if (data.dimension() < 0 || data.dimension() >= worlds.length
                || data.y() < 0 || data.y() >= Chunk.HEIGHT) return;
        if (!canReach(client, data.x(), data.y(), data.z())) return;
        World world = worlds[data.dimension()];
        BlockType block = world.getBlock(data.x(), data.y(), data.z());
        // Casting stations accept operation intents only. A full client-authored
        // snapshot would bypass their input, output and material validation.
        if (block == BlockType.CASTING_TABLE || block == BlockType.CASTING_BASIN) return;
        if (!typeMatchesBlock(data.type(), block)) return;
        BlockEntity entity = restoreEntity(world, data.x(), data.y(), data.z(), data.type(), data.payload());
        if (entity == null) return;
        // Persist: the chunk may not be flagged modified (e.g. filling an
        // already-placed chest), so mark it explicitly.
        world.markChunkModifiedByPlayer(World.worldToChunk(data.x()), World.worldToChunk(data.z()));
        broadcastOthers(client, Packets.encodeContainerData(new Packets.ContainerData(
                data.dimension(), data.x(), data.y(), data.z(), data.type(), snapshotEntity(entity))));
    }

    /** Applies one validated casting intent and broadcasts the authoritative result. */
    private void handleCastingOperation(Client client, Packets.CastingOperation operation) throws IOException {
        if (!client.joined || operation.dimension() != client.dimension) return;
        if (operation.dimension() < 0 || operation.dimension() >= worlds.length
                || operation.y() < 0 || operation.y() >= Chunk.HEIGHT) return;
        if (!canReach(client, operation.x(), operation.y(), operation.z())) return;

        World world = worlds[operation.dimension()];
        BlockType block = world.getBlock(operation.x(), operation.y(), operation.z());
        if (block != BlockType.CASTING_TABLE && block != BlockType.CASTING_BASIN) return;
        com.minecraftclone.world.CastingEntity casting =
                world.getOrCreateCasting(operation.x(), operation.y(), operation.z());
        if (casting == null) return;

        boolean valid = false;
        boolean changed = false;
        BlockType material = BlockType.byId(operation.materialId());
        switch (operation.operation()) {
            case Packets.CAST_IMPRINT -> {
                int shape = operation.shapeOrdinal();
                com.minecraftclone.world.tinkers.ToolPartType[] shapes =
                        com.minecraftclone.world.tinkers.ToolPartType.values();
                if (shape >= 0 && shape < shapes.length
                        && com.minecraftclone.world.tinkers.TinkersRegistry.isMaterial(material)
                        && operation.count() == 1) {
                    valid = true;
                    changed = casting.imprintCast(com.minecraftclone.player.ItemStack.tinkersPart(
                            new com.minecraftclone.world.tinkers.TinkersItem.Part(shapes[shape], material)));
                }
            }
            case Packets.CAST_INSERT -> {
                if (com.minecraftclone.world.tinkers.TinkersRegistry.isMaterial(material)
                        && operation.count() > 0 && operation.count() <= casting.inputCapacity()) {
                    valid = true;
                    changed = casting.insertMaterial(material, operation.count()) > 0;
                }
            }
            case Packets.CAST_TAKE_OUTPUTS -> {
                if (operation.count() > 0 && operation.count() <= casting.outputCapacity()) {
                    valid = true;
                    changed = !casting.takeOutputs(operation.count()).isEmpty();
                }
            }
            default -> {
                return;
            }
        }
        if (!valid) return;
        if (changed) {
            world.markChunkModifiedByPlayer(World.worldToChunk(operation.x()), World.worldToChunk(operation.z()));
        }
        byte[] snapshot = Packets.encodeContainerData(new Packets.ContainerData(
                operation.dimension(), operation.x(), operation.y(), operation.z(), casting.type(), snapshotEntity(casting)));
        broadcastAll(snapshot);
    }

    /**
     * Announces dropped items to clients: anything new (mob loot the server
     * spawned, or a client's block-break drops) gets an ITEM_ADD with a fresh
     * id; anything that vanished (picked up, expired, fell out of the world)
     * gets an ITEM_REMOVE. Runs once per tick; cheap because both sides of the
     * diff are small sets.
     */
    private void broadcastNewItems() {
        for (World world : worlds) {
            byte dim = (byte) worldOfDimension(world);
            List<ItemEntity> items = world.getItems();
            java.util.Set<Integer> live = java.util.concurrent.ConcurrentHashMap.newKeySet();
            for (ItemEntity e : items) {
                if (e.tinkersItem != null) continue; // payload-carrying drops stay local to their owner
                if (e.id <= 0) e.id = world.allocateItemId();
                live.add(e.id);
                if (knownItemIds.add(e.id)) {
                    try {
                        broadcastAll(Packets.encodeItemAdd(new Packets.ItemAdd(
                                e.id, dim, e.position.x, e.position.y, e.position.z, e.type.id, e.count)));
                    } catch (IOException ignored) {
                        // Fixed-size record; cannot realistically fail.
                    }
                }
            }
            for (int id : List.copyOf(knownItemIds)) {
                // Ids are globally unique across dimensions, so a missing id in
                // this dimension's set means it's gone entirely.
                if (!live.contains(id) && !itemExistsInAnyWorld(id)) {
                    if (knownItemIds.remove(id)) {
                        try {
                            broadcastAll(Packets.encodeItemRemove(id));
                        } catch (IOException ignored) {
                            // Fixed-size record; cannot realistically fail.
                        }
                    }
                }
            }
        }
    }

    /** The ordinal of the dimension a world belongs to (worlds are indexed by it). */
    private int worldOfDimension(World world) {
        for (int i = 0; i < worlds.length; i++) {
            if (worlds[i] == world) return i;
        }
        return DimensionType.OVERWORLD.ordinal();
    }

    private boolean itemExistsInAnyWorld(int id) {
        for (World w : worlds) {
            if (w.itemById(id) != null) return true;
        }
        return false;
    }

    /**
     * A client broke a block (or died): spawn the drop server-side so everyone
     * shares one copy. Lightly validated - finite coords near the player,
     * sane count - since the client computes tool-dependent drop tables.
     */
    private void handleItemSpawn(Client client, Packets.ItemSpawn spawn) throws IOException {
        if (!client.joined) return;
        if (spawn.dimension() < 0 || spawn.dimension() >= worlds.length) return;
        int count = spawn.count();
        BlockType type = spawn.blockId() > 0 ? BlockType.byId(spawn.blockId()) : null;
        if (type == null || type.isItem || count < 1 || count > 99) return;
        float dx = spawn.x() - client.x, dy = spawn.y() - client.y, dz = spawn.z() - client.z;
        // Generous: death drops scatter around the body, breaks are within reach.
        if (!Float.isFinite(dx) || !Float.isFinite(dy) || !Float.isFinite(dz)) return;
        if (dx * dx + dy * dy + dz * dz > (MAX_EDIT_DISTANCE_SQ * 4f)) return;
        World world = worlds[spawn.dimension()];
        ItemEntity e = new ItemEntity(type, count, spawn.x(), spawn.y(), spawn.z());
        e.id = world.allocateItemId();
        e.velocity.set((rnd.nextFloat() - 0.5f) * 1.5f, 2.5f + rnd.nextFloat() * 1.5f,
                (rnd.nextFloat() - 0.5f) * 1.5f);
        world.getItems().add(e);
    }

    /** How far from their reported position a client may pick an item up (blocks). */
    private static final float MAX_PICKUP_DISTANCE_SQ = 3f * 3f;

    /**
     * Processes a player attack against another player when PvP and attack validation permit it.
     */
    private void handlePlayerAttack(Client client, Packets.PlayerAttack attack) throws IOException {
        if (!client.joined) return;
        if (!config.isPvpEnabled()) return; // server.properties: pvp=false
        float damage = attack.damage();
        if (!Float.isFinite(damage) || damage <= 0f) return;
        damage = Math.min(damage, MAX_ATTACK_DAMAGE);
        long now = System.nanoTime();
        if (now - client.lastAttackNanos < attackCooldownNanos) return;
        client.lastAttackNanos = now;
        Client target = clients.get(attack.targetId());
        if (target == null || !target.joined || target == client) return;
        if (target.dimension != client.dimension) return;
        float dx = target.x - client.x, dy = target.y - client.y, dz = target.z - client.z;
        if (dx * dx + dy * dy + dz * dz > MAX_EDIT_DISTANCE_SQ) return;
        // Knock the target away from the attacker.
        float len = (float) Math.sqrt(dx * dx + dz * dz);
        float kx = len > 1e-4f ? dx / len : 0f;
        float kz = len > 1e-4f ? dz / len : 0f;
        send(target, Packets.encodePlayerDamage(new Packets.PlayerDamage(damage, kx, kz)));
        System.out.println(client.name + " hit " + target.name + " for " + damage);
    }

    /** How many joined players are in bed, and the total joined (for SLEEP_STATE). */
    private int[] sleepCounts() {
        int sleeping = 0, total = 0;
        for (Client c : clients.values()) {
            if (!c.joined) continue;
            total++;
            if (c.sleepVoted) sleeping++;
        }
        return new int[]{sleeping, total};
    }

    /** True if any joined player currently has a bed vote outstanding. */
    private boolean anySleepVotes() {
        for (Client c : clients.values()) {
            if (c.joined && c.sleepVoted) return true;
        }
        return false;
    }

    private void broadcastSleepState() {
        int[] counts = sleepCounts();
        try {
            broadcastAll(Packets.encodeSleepState(new Packets.SleepState(counts[0], counts[1])));
        } catch (IOException ignored) {
            // Fixed-size record; cannot realistically fail.
        }
    }

    /**
     * A player climbed into a bed at night: count their vote, tell everyone,
     * and when EVERY connected player is in bed advance the authoritative
     * clock to morning (Minecraft's rule - one player sleeping shouldn't
     * skip someone else's night).
     */
    private void handleSleepVote(Client client) throws IOException {
        if (!client.joined || !dayNightCycle.isNight()) return;
        client.sleepVoted = true;
        System.out.println(client.name + " wants to sleep");
        broadcastSleepState();
        int[] counts = sleepCounts();
        if (counts[0] > 0 && counts[0] == counts[1]) {
            dayNightCycle.skipToMorning();
            for (Client c : clients.values()) c.sleepVoted = false;
            broadcastSleepState();
            broadcastTime(); // push morning immediately instead of waiting for the next 1s sync
            System.out.println("Everyone is asleep - skipping to morning");
        }
    }

    /**
     * A player walked over a dropped item: validate they're actually near it,
     * remove it, grant it to them alone, and tell everyone else it's gone.
     */
    private void handleItemPickup(Client client, Packets.ItemPickup pickup) throws IOException {
        if (!client.joined) return;
        World world = worldOf(client);
        ItemEntity item = world.itemById(pickup.id());
        if (item == null) return;
        if (!item.canPickup()) return;
        float dx = item.position.x - client.x, dy = item.position.y - client.y, dz = item.position.z - client.z;
        if (dx * dx + dy * dy + dz * dz > MAX_PICKUP_DISTANCE_SQ) return;
        world.removeItemById(item.id);
        send(client, Packets.encodeItemGive(new Packets.ItemGive(item.id, item.type.id, item.count)));
        broadcastOthers(client, Packets.encodeItemRemove(item.id));
    }

    /** Broadcasts the server-authoritative time of day so every client shares the same sky. */
    private void broadcastTime() {
        try {
            broadcastAll(Packets.encodeTimeSync(new Packets.TimeSync(
                    dayNightCycle.getTime(), dayNightCycle.getDayIndex())));
        } catch (IOException e) {
            // best-effort; next tick retries
        }
    }

    /** Highest non-air block in the given column of a world (like single-player landing logic). */
    private static int landingSurfaceY(World world, int x, int z) {
        for (int y = Chunk.HEIGHT - 1; y >= 0; y--) {
            if (world.getBlock(x, y, z) != BlockType.AIR) {
                return y;
            }
        }
        return 1;
    }

    private void broadcastMoves() {
        for (Client c : clients.values()) {
            if (!c.joined) continue;
            try {
                broadcastOthers(c, Packets.encodePlayerState(new Packets.PlayerState(
                        c.id, c.dimension, c.x, c.y, c.z, c.yaw, c.pitch, c.onGround, c.flying, c.sprinting)));
            } catch (IOException e) {
                disconnect(c);
            }
        }
    }

    private void disconnect(Client client) {
        if (client.disconnected) return;
        client.disconnected = true;
        pending.remove(client);
        boolean wasJoined = client.joined;
        clients.remove(client.id);
        if (wasJoined && client.sleepVoted) {
            // A sleeping player left: their vote vanishes with them, which can
            // complete (everyone left is asleep) or change the count.
            broadcastSleepState();
            int[] counts = sleepCounts();
            if (counts[0] > 0 && counts[0] == counts[1] && dayNightCycle.isNight()) {
                dayNightCycle.skipToMorning();
                for (Client c : clients.values()) c.sleepVoted = false;
                broadcastSleepState();
                broadcastTime();
            }
        }
        try {
            client.socket.close();
        } catch (IOException ignored) {
        }
        if (wasJoined) {
            try {
                broadcastOthers(client, Packets.encodePlayerLeft(new Packets.PlayerLeft(client.id)));
            } catch (IOException ignored) {
            }
            System.out.println(client.name + " left (" + getPlayerCount() + " online)");
        }
    }

    private void send(Client client, byte[] payload) {
        try {
            synchronized (client.out) {
                Packets.writeFrame(client.out, payload);
            }
        } catch (IOException e) {
            disconnect(client);
        }
    }

    private void broadcastAll(byte[] payload) {
        for (Client c : clients.values()) {
            if (c.joined) send(c, payload);
        }
    }

    private void broadcastOthers(Client except, byte[] payload) {
        for (Client c : clients.values()) {
            if (c.joined && c != except) send(c, payload);
        }
    }

    /** Finds a spawn column: a non-ocean/non-mountain biome as close to the origin as possible. */
    private float[] findSpawn() {
        World world = worlds[DimensionType.OVERWORLD.ordinal()];
        for (int r = 0; r <= 50; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
                    TerrainGenerator.Biome b = world.getBiome(dx, dz);
                    if (b == TerrainGenerator.Biome.OCEAN || b == TerrainGenerator.Biome.FROZEN_OCEAN
                            || b == TerrainGenerator.Biome.MOUNTAIN) continue;
                    return new float[]{dx + 0.5f, dz + 0.5f};
                }
            }
        }
        return new float[]{0.5f, 0.5f};
    }

    /** Sends a chunk to a client: full data if a player edited it, otherwise a vanilla ack. */
    private void requestChunk(Client client, byte dimension, int cx, int cz) throws IOException {
        if (client == null || !client.joined) return;
        // Reject out-of-range dimensions and far-away requests: ensureChunk on
        // a keepChunks world never unloads, so unbounded coordinates would let
        // one client grow the server's memory forever.
        if (dimension < 0 || dimension >= worlds.length) return;
        if (!canRequestChunk(client, cx, cz)) {
            send(client, Packets.encodeChunkAck(dimension, cx, cz));
            return;
        }
        World world = worlds[dimension];
        world.ensureChunk(cx, cz);
        if (world.isChunkModifiedByPlayer(cx, cz)) {
            short[] blocks = world.getChunkRawBlocks(cx, cz);
            short[] overlays = world.getChunkRawOverlays(cx, cz);
            byte[] orientations = world.getChunkRawOrientations(cx, cz);
            if (blocks != null) {
                send(client, Packets.encodeChunkData(new Packets.ChunkData(dimension, cx, cz, blocks, overlays, orientations)));
                return;
            }
        }
        send(client, Packets.encodeChunkAck(dimension, cx, cz));
    }

    @Override
    public void close() {
        running = false;
        // Close the listening socket FIRST: accept() only wakes on socket
        // close (it never polls the running flag), so joining the accept
        // thread before closing would always burn the full timeout.
        try {
            serverSocket.close();
        } catch (IOException ignored) {
        }
        try {
            if (acceptThread != null) acceptThread.join(1000);
            if (tickThread != null) tickThread.join(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        for (Client c : List.copyOf(clients.values())) {
            disconnect(c);
        }
        for (World w : worlds) {
            w.saveAllModified();
        }
    }

    /** The list of online player names (for the HUD / server info). */
    public List<String> getPlayerNames() {
        List<String> names = new ArrayList<>();
        for (Client c : clients.values()) {
            if (c.joined) names.add(c.name);
        }
        return names;
    }
}
