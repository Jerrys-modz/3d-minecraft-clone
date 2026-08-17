package com.minecraftclone.net;

import com.minecraftclone.engine.DayNightCycle;
import com.minecraftclone.util.AABB;
import com.minecraftclone.world.BlockType;
import com.minecraftclone.world.Chunk;
import com.minecraftclone.world.DimensionType;
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
    private static final int MAX_PLAYERS = 12;

    /** One connected client. Written only from the tick thread; read from its own reader thread. */
    private static final class Client {
        final Socket socket;
        final DataOutputStream out;
        volatile int id = -1;
        volatile String name = "";
        volatile float x, y, z, yaw, pitch;
        volatile boolean onGround, flying, sprinting;
        volatile boolean joined;
        volatile boolean disconnected;

        Client(Socket socket, DataOutputStream out) {
            this.socket = socket;
            this.out = out;
        }
    }

    /** A decoded packet waiting to be processed on the tick thread. */
    private record Incoming(Client client, Object packet) {
    }

    private final ServerSocket serverSocket;
    private final World world;
    private final WorldGenSettings settings;
    private final long seed;
    private final float spawnX;
    private final float spawnZ;

    private final ConcurrentLinkedQueue<Incoming> inbox = new ConcurrentLinkedQueue<>();
    private final Map<Integer, Client> clients = new ConcurrentHashMap<>();
    private volatile int nextId = 1;
    private volatile boolean running;
    private Thread acceptThread;
    private Thread tickThread;
    /** Server-authoritative day/night (drives hostile spawning); GL-free. */
    private final DayNightCycle dayNightCycle = new DayNightCycle();
    private final Random rnd = new Random();
    /** Mob ids we've already told clients about, so natural despawns broadcast removals. */
    private final java.util.Set<Integer> knownMobIds = java.util.concurrent.ConcurrentHashMap.newKeySet();

    public GameServer(int port, WorldGenSettings settings, long seed, Path saveDir) throws IOException {
        this.settings = settings;
        this.seed = seed;
        this.world = new World(seed, settings, null, saveDir, DimensionType.OVERWORLD, true);
        world.setKeepChunks(true);
        world.setRenderDistance(8);
        // Generate a starting area around the origin so chunk requests near
        // spawn can be answered immediately and mob/block state is ready.
        for (int i = 0; i < 400; i++) {
            world.update(0, 0);
        }
        world.spawnInitialMobs(rnd, 0f, 0f, 10);
        float[] spawn = findSpawn();
        this.spawnX = spawn[0];
        this.spawnZ = spawn[1];
        this.serverSocket = new ServerSocket(port);
    }

    public int getPort() {
        return serverSocket.getLocalPort();
    }

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

    private void acceptLoop() {
        while (running) {
            try {
                Socket socket = serverSocket.accept();
                socket.setTcpNoDelay(true);
                DataOutputStream out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
                Client client = new Client(socket, out);
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
                inbox.add(new Incoming(client, packet));
            }
        } catch (EOFException | SocketException e) {
            // peer closed
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

            // Server-authoritative day/night drives hostile mob spawning.
            dayNightCycle.update(dt);

            // Stream chunks around every connected player (server keeps them all).
            for (Client c : clients.values()) {
                if (c.joined) world.update(c.x, c.z);
            }

            // Simulate mobs against every connected player (nearest-player targeting).
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

    /** Advances mobs and routes damage to whichever player each mob targeted. */
    private void tickMobs(float dt) {
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
        float[] damage = world.updateMobsMulti(dt, positions, boxes, dayNightCycle.isNight(), rnd);
        for (int i = 0; i < players.size(); i++) {
            if (damage[i] > 0f) {
                try {
                    send(players.get(i), Packets.encodePlayerDamage(new Packets.PlayerDamage(damage[i])));
                } catch (IOException e) {
                    disconnect(players.get(i));
                }
            }
        }
    }

    /** Sends every currently-loaded mob's pose to all connected clients, plus spawn/removal diffs. */
    private void broadcastMobStates() {
        if (clients.isEmpty()) return;
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
                        m.id, m.position.x, m.position.y, m.position.z, m.yaw, 0f));
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

    /** Sends a freshly-spawned mob to a single client (used on join so they see existing mobs). */
    private void sendMobSpawns(Client client) {
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
        Incoming incoming;
        while ((incoming = inbox.poll()) != null) {
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
                    requestChunk(client, req.cx(), req.cz());
                } else if (packet instanceof Packets.MobAttack attack) {
                    handleMobAttack(client, attack);
                }
            } catch (IOException e) {
                disconnect(client);
            }
            // READY is a no-op on the server: spawn + surrounding chunks were
            // already sent as part of the join handshake.
        }
    }

    private void handleJoin(Client client, Packets.Join join) throws IOException {
        if (getPlayerCount() >= MAX_PLAYERS) {
            send(client, Packets.encodeReject("Server is full."));
            disconnect(client);
            return;
        }
        String name = join.name().trim();
        if (name.isEmpty()) name = "Player" + client.id;
        client.id = nextId++;
        client.name = name;
        client.joined = true;
        clients.put(client.id, client);

        // Spawn them a little above the surface so they fall in gently.
        float y = world.getSurfaceHeight((int) Math.floor(spawnX), (int) Math.floor(spawnZ)) + 2f;

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
                        other.id, other.name, other.x, other.y, other.z, other.yaw, other.pitch)));
            }
        }
        // Tell everyone else the newcomer appeared.
        broadcastOthers(client, Packets.encodePlayerJoined(new Packets.PlayerJoined(
                client.id, name, spawnX, y, spawnZ, 0f, 0f)));

        // Send the newcomer every currently-loaded mob so the world isn't empty for them.
        sendMobSpawns(client);

        System.out.println(name + " joined (" + getPlayerCount() + " online)");
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

    private void handlePlace(Client client, Packets.PlaceBlock place) throws IOException {
        if (!client.joined) return;
        BlockType type = place.blockId() < 0 ? null : BlockType.byId(place.blockId());
        if (type == null || type.isItem || place.y() < 0 || place.y() >= Chunk.HEIGHT) return;
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
                place.x(), place.y(), place.z(), type.id, place.orientation(), place.overlay())));
    }

    private void handleBreak(Client client, Packets.BreakBlock brk) throws IOException {
        if (!client.joined) return;
        world.ensureChunk(World.worldToChunk(brk.x()), World.worldToChunk(brk.z()));
        if (brk.overlay()) {
            world.setOverlay(brk.x(), brk.y(), brk.z(), BlockType.AIR);
        } else {
            world.setBlock(brk.x(), brk.y(), brk.z(), BlockType.AIR);
        }
        broadcastAll(Packets.encodeBlockChange(new Packets.BlockChange(
                brk.x(), brk.y(), brk.z(), BlockType.AIR.id, (byte) 0, brk.overlay())));
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
     */
    private void handleMobAttack(Client client, Packets.MobAttack attack) throws IOException {
        if (!client.joined) return;
        Mob mob = world.mobById(attack.mobId());
        if (mob == null) return;
        boolean killed = world.damageMob(mob, attack.damage(), client.x, client.z, rnd);
        if (killed) {
            knownMobIds.remove(mob.id);
            broadcastAll(Packets.encodeMobRemove(new Packets.MobRemove(
                    mob.id, (byte) mob.type.ordinal(), mob.position.x, mob.position.y, mob.position.z)));
        }
    }

    private void broadcastMoves() {
        for (Client c : clients.values()) {
            if (!c.joined) continue;
            try {
                broadcastOthers(c, Packets.encodePlayerState(new Packets.PlayerState(
                        c.id, c.x, c.y, c.z, c.yaw, c.pitch, c.onGround, c.flying, c.sprinting)));
            } catch (IOException e) {
                disconnect(c);
            }
        }
    }

    private void disconnect(Client client) {
        if (client.disconnected) return;
        client.disconnected = true;
        boolean wasJoined = client.joined;
        clients.remove(client.id);
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
    private void requestChunk(Client client, int cx, int cz) throws IOException {
        if (client == null || !client.joined) return;
        world.ensureChunk(cx, cz);
        if (world.isChunkModifiedByPlayer(cx, cz)) {
            byte[] blocks = world.getChunkRawBlocks(cx, cz);
            byte[] overlays = world.getChunkRawOverlays(cx, cz);
            byte[] orientations = world.getChunkRawOrientations(cx, cz);
            if (blocks != null) {
                send(client, Packets.encodeChunkData(new Packets.ChunkData(cx, cz, blocks, overlays, orientations)));
                return;
            }
        }
        send(client, Packets.encodeChunkAck(cx, cz));
    }

    @Override
    public void close() {
        running = false;
        try {
            if (acceptThread != null) acceptThread.join(1000);
            if (tickThread != null) tickThread.join(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        for (Client c : List.copyOf(clients.values())) {
            disconnect(c);
        }
        try {
            serverSocket.close();
        } catch (IOException ignored) {
        }
        world.saveAllModified();
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
