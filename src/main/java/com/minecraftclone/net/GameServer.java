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
    private long lastTimeSync = System.nanoTime();

    /** The authoritative world for a client's current dimension. */
    private World worldOf(Client c) {
        int dim = c.dimension;
        if (dim < 0 || dim >= worlds.length) dim = DimensionType.OVERWORLD.ordinal();
        return worlds[dim];
    }

    public GameServer(int port, WorldGenSettings settings, long seed, Path saveDir) throws IOException {
        this.settings = settings;
        this.seed = seed;
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
                // A peer that connects but never speaks holds a thread forever
                // without this; once joined, clients talk every tick anyway.
                socket.setSoTimeout(JOIN_TIMEOUT_MILLIS);
                if (pending.size() >= MAX_PENDING_CONNECTIONS) {
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

            // Server-authoritative day/night drives hostile mob spawning.
            dayNightCycle.update(dt);

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
        float[] damage = world.updateMobsMulti(dt, positions, boxes, dayNightCycle.isNight(), rnd, settings.getDifficulty());
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
                }
            } catch (IOException e) {
                disconnect(client);
            }
            // READY is a no-op on the server: spawn + surrounding chunks were
            // already sent as part of the join handshake.
        }
    }

    private void handleJoin(Client client, Packets.Join join) throws IOException {
        if (client.joined) {
            // A second JOIN on the same connection would reassign its id and
            // orphan the old map entry - refuse it.
            send(client, Packets.encodeReject("Already joined."));
            return;
        }
        if (getPlayerCount() >= MAX_PLAYERS) {
            send(client, Packets.encodeReject("Server is full."));
            disconnect(client);
            return;
        }
        // The id must exist before the fallback name is built from it.
        client.id = nextId++;
        String name = join.name().trim();
        if (name.isEmpty()) name = "Player-" + client.id;
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
        if (now - client.lastAttackNanos < ATTACK_COOLDOWN_NANOS) return;
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
