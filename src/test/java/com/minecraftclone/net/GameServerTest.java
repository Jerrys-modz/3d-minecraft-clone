package com.minecraftclone.net;

import com.minecraftclone.world.BlockType;
import com.minecraftclone.world.CastingEntity;
import com.minecraftclone.world.World;
import com.minecraftclone.world.gen.WorldGenSettings;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end server test: spins up a real {@link GameServer} on an ephemeral
 * port, connects a real {@link NetClient}, and exercises the join handshake,
 * player state relay, block edits and chat - all over a local socket. This
 * exercises the whole read/queue/tick/broadcast path that the packet-codec
 * tests above can't reach.
 */
class GameServerTest {

    private GameServer server;
    private Path saveDir;

    @BeforeEach
    void setUp() throws Exception {
        saveDir = Files.createTempDirectory("mcloneserver");
        WorldGenSettings settings = new WorldGenSettings();
        settings.setSeedText("42");
        server = new GameServer(0, settings, settings.resolveSeed(), saveDir);
        server.start();
    }

    @AfterEach
    void tearDown() {
        server.close();
    }

    /** Polls the client's incoming queue until a packet of the given type arrives (or times out). */
    private static Object awaitPacket(NetClient client, Class<?> type) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadline) {
            Object packet = client.poll();
            if (packet != null && type.isInstance(packet)) return packet;
            Thread.sleep(10);
        }
        throw new AssertionError("Timed out waiting for " + type.getSimpleName());
    }

    /** Polls until a packet of the given type passes {@code matches} (draining earlier packets). */
    private static Object awaitPacketMatching(NetClient client, Class<?> type, java.util.function.Predicate<Object> matches) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadline) {
            Object packet = client.poll();
            if (packet != null && type.isInstance(packet) && matches.test(packet)) return packet;
            Thread.sleep(10);
        }
        throw new AssertionError("Timed out waiting for " + type.getSimpleName());
    }

    @Test
    void joinReceivesWelcomeAndSpawns() throws Exception {
        try (NetClient client = new NetClient("127.0.0.1", server.getPort())) {
            client.sendJoin("Steve");
            Packets.Welcome welcome = assertInstanceOf(Packets.Welcome.class, awaitPacket(client, Packets.Welcome.class));
            assertTrue(welcome.selfId() >= 1);
            assertEquals(42L, welcome.seed());
        }
    }

    @Test
    void twoClientsSeeEachOtherJoin() throws Exception {
        try (NetClient a = new NetClient("127.0.0.1", server.getPort());
             NetClient b = new NetClient("127.0.0.1", server.getPort())) {
            a.sendJoin("Alice");
            assertInstanceOf(Packets.Welcome.class, awaitPacket(a, Packets.Welcome.class));
            b.sendJoin("Bob");
            assertInstanceOf(Packets.Welcome.class, awaitPacket(b, Packets.Welcome.class));
            // Alice hears about Bob joining.
            Packets.PlayerJoined joined = assertInstanceOf(Packets.PlayerJoined.class,
                    awaitPacket(a, Packets.PlayerJoined.class));
            assertEquals("Bob", joined.name());
        }
    }

    @Test
    void moveIsRelayedToOtherClient() throws Exception {
        try (NetClient a = new NetClient("127.0.0.1", server.getPort());
             NetClient b = new NetClient("127.0.0.1", server.getPort())) {
            a.sendJoin("Alice");
            assertInstanceOf(Packets.Welcome.class, awaitPacket(a, Packets.Welcome.class));
            b.sendJoin("Bob");
            assertInstanceOf(Packets.Welcome.class, awaitPacket(b, Packets.Welcome.class));
            // Alice moves; Bob should receive her state.
            a.sendMove(new Packets.Move(100f, 70f, 200f, 45f, -10f, true, false, true, 20f, 20f, (short) 0));
            Packets.PlayerState state = assertInstanceOf(Packets.PlayerState.class,
                    awaitPacketMatching(b, Packets.PlayerState.class, p -> ((Packets.PlayerState) p).x() == 100f));
            assertEquals(100f, state.x());
            assertEquals(70f, state.y());
            assertEquals(200f, state.z());
            assertTrue(state.sprinting());
        }
    }

    @Test
    void blockEditIsBroadcast() throws Exception {
        try (NetClient a = new NetClient("127.0.0.1", server.getPort())) {
            a.sendJoin("Alice");
            Packets.Welcome welcome = assertInstanceOf(Packets.Welcome.class, awaitPacket(a, Packets.Welcome.class));
            // Place a block at the join spawn (within server-side reach); the
            // server echoes the authoritative change back.
            int px = (int) Math.floor(welcome.spawnX());
            int py = Math.round(welcome.spawnY());
            int pz = (int) Math.floor(welcome.spawnZ());
            a.sendPlaceBlock(new Packets.PlaceBlock((byte) 0, px, py, pz, BlockType.STONE.id, (byte) 0, false));
            Packets.BlockChange change = assertInstanceOf(Packets.BlockChange.class,
                    awaitPacket(a, Packets.BlockChange.class));
            assertEquals(px, change.x());
            assertEquals(py, change.y());
            assertEquals(pz, change.z());
            assertEquals(BlockType.STONE.id, change.blockId());
        }
    }

    @Test
    void chatIsRelayed() throws Exception {
        try (NetClient a = new NetClient("127.0.0.1", server.getPort());
             NetClient b = new NetClient("127.0.0.1", server.getPort())) {
            a.sendJoin("Alice");
            assertInstanceOf(Packets.Welcome.class, awaitPacket(a, Packets.Welcome.class));
            b.sendJoin("Bob");
            assertInstanceOf(Packets.Welcome.class, awaitPacket(b, Packets.Welcome.class));
            a.sendChat("hello");
            Packets.ChatMsg msg = assertInstanceOf(Packets.ChatMsg.class, awaitPacket(b, Packets.ChatMsg.class));
            assertEquals("Alice", msg.name());
            assertEquals("hello", msg.text());
        }
    }

    @Test
    void chunkRequestReturnsAckForVanillaChunk() throws Exception {
        try (NetClient a = new NetClient("127.0.0.1", server.getPort())) {
            a.sendJoin("Alice");
            assertInstanceOf(Packets.Welcome.class, awaitPacket(a, Packets.Welcome.class));
            a.sendChunkRequest((byte) 0, 0, 0); // never edited -> vanilla ack
            Packets.ChunkAck ack = assertInstanceOf(Packets.ChunkAck.class, awaitPacket(a, Packets.ChunkAck.class));
            assertEquals(0, ack.cx());
            assertEquals(0, ack.cz());
        }
    }

    @Test
    void modifiedChunkReturnsFullData() throws Exception {
        try (NetClient a = new NetClient("127.0.0.1", server.getPort())) {
            a.sendJoin("Alice");
            Packets.Welcome welcome = assertInstanceOf(Packets.Welcome.class, awaitPacket(a, Packets.Welcome.class));
            // Edit a block right at the join spawn (within server-side reach of
            // the player's tracked position), then request that chunk - the
            // server should send its full raw contents rather than an ack.
            int px = (int) Math.floor(welcome.spawnX());
            int py = Math.round(welcome.spawnY());
            int pz = (int) Math.floor(welcome.spawnZ());
            a.sendPlaceBlock(new Packets.PlaceBlock((byte) 0, px, py, pz, BlockType.STONE.id, (byte) 0, false));
            assertInstanceOf(Packets.BlockChange.class, awaitPacket(a, Packets.BlockChange.class));
            a.sendChunkRequest((byte) 0, World.worldToChunk(px), World.worldToChunk(pz));
            Packets.ChunkData data = assertInstanceOf(Packets.ChunkData.class, awaitPacket(a, Packets.ChunkData.class));
            assertEquals(World.worldToChunk(px), data.cx());
            assertEquals(World.worldToChunk(pz), data.cz());
            assertEquals(com.minecraftclone.world.Chunk.SIZE
                    * com.minecraftclone.world.Chunk.HEIGHT * com.minecraftclone.world.Chunk.SIZE,
                    data.blocks().length);
        }
    }

    @Test
    void mobSpawnsAreSentOnJoin() throws Exception {
        try (NetClient a = new NetClient("127.0.0.1", server.getPort())) {
            a.sendJoin("Alice");
            assertInstanceOf(Packets.Welcome.class, awaitPacket(a, Packets.Welcome.class));
            // The server seeds mobs at startup and sends them on join; at least
            // one must arrive for this fixed seed, and every arrival must be
            // well-formed (valid type ordinal, sane id).
            long deadline = System.currentTimeMillis() + 3000;
            boolean sawSpawn = false;
            while (System.currentTimeMillis() < deadline) {
                Object p = a.poll();
                if (p instanceof Packets.MobSpawn spawn) {
                    assertTrue(spawn.mobId() >= 1);
                    assertTrue(spawn.typeId() >= 0 && spawn.typeId() < com.minecraftclone.world.Mob.Type.values().length);
                    sawSpawn = true;
                    break;
                }
                Thread.sleep(10); // yield instead of busy-spinning against the tick thread
            }
            assertTrue(sawSpawn, "expected at least one seeded MobSpawn after joining");
        }
    }

    @Test
    void mobStateBroadcastArrives() throws Exception {
        try (NetClient a = new NetClient("127.0.0.1", server.getPort())) {
            a.sendJoin("Alice");
            assertInstanceOf(Packets.Welcome.class, awaitPacket(a, Packets.Welcome.class));
            // Mobs (server-seeded) broadcast their states at 10 Hz - expect one.
            Object state = awaitPacketMatching(a, Packets.MobState.class, p -> true);
            assertInstanceOf(Packets.MobState.class, state);
        }
    }

    @Test
    void timeSyncBroadcastArrives() throws Exception {
        try (NetClient a = new NetClient("127.0.0.1", server.getPort())) {
            a.sendJoin("Alice");
            assertInstanceOf(Packets.Welcome.class, awaitPacket(a, Packets.Welcome.class));
            // The server broadcasts its authoritative time once a second.
            Packets.TimeSync sync = assertInstanceOf(Packets.TimeSync.class,
                    awaitPacketMatching(a, Packets.TimeSync.class, p -> true));
            assertTrue(sync.timeOfDay() >= 0f && sync.timeOfDay() < 1f);
        }
    }

    @Test
    void portalUseTeleportsToNether() throws Exception {
        try (NetClient a = new NetClient("127.0.0.1", server.getPort())) {
            a.sendJoin("Alice");
            assertInstanceOf(Packets.Welcome.class, awaitPacket(a, Packets.Welcome.class));
            // Walking into a nether portal should come back as a DimensionChange.
            a.sendPortalUse((byte) 0, BlockType.NETHER_PORTAL.id);
            Packets.DimensionChange change = assertInstanceOf(Packets.DimensionChange.class,
                    awaitPacketMatching(a, Packets.DimensionChange.class, p -> true));
            assertEquals(1, change.dimension()); // NETHER ordinal
        }
    }

    @Test
    void chunkRequestIsDimensionAware() throws Exception {
        try (NetClient a = new NetClient("127.0.0.1", server.getPort())) {
            a.sendJoin("Alice");
            assertInstanceOf(Packets.Welcome.class, awaitPacket(a, Packets.Welcome.class));
            a.sendChunkRequest((byte) 1, 0, 0); // request nether chunk (0,0), never edited
            Packets.ChunkAck ack = assertInstanceOf(Packets.ChunkAck.class,
                    awaitPacketMatching(a, Packets.ChunkAck.class, p -> ((Packets.ChunkAck) p).dimension() == 1));
            assertEquals(1, ack.dimension());
            assertEquals(0, ack.cx());
            assertEquals(0, ack.cz());
        }
    }

    /** Joins, places a chest at the spawn cell (within reach), and waits for the echo. */
    private static Packets.Welcome placeChestAtSpawn(NetClient client) throws Exception {
        client.sendJoin("Alice");
        Packets.Welcome welcome = assertInstanceOf(Packets.Welcome.class, awaitPacket(client, Packets.Welcome.class));
        int px = (int) Math.floor(welcome.spawnX());
        int py = Math.round(welcome.spawnY());
        int pz = (int) Math.floor(welcome.spawnZ());
        client.sendPlaceBlock(new Packets.PlaceBlock((byte) 0, px, py, pz, BlockType.CHEST.id, (byte) 0, false));
        awaitPacket(client, Packets.BlockChange.class);
        return welcome;
    }

    @Test
    void tinkersStationContainerSyncs() throws Exception {
        try (NetClient a = new NetClient("127.0.0.1", server.getPort())) {
            a.sendJoin("Alice");
            Packets.Welcome welcome = assertInstanceOf(Packets.Welcome.class, awaitPacket(a, Packets.Welcome.class));
            int px = (int) Math.floor(welcome.spawnX());
            int py = Math.round(welcome.spawnY());
            int pz = (int) Math.floor(welcome.spawnZ());
            // Place a Tool Station and open it: the snapshot must come back
            // with the tool_station type (whitelist plumbing for Tinkers).
            a.sendPlaceBlock(new Packets.PlaceBlock((byte) 0, px, py, pz, BlockType.TOOL_STATION.id, (byte) 0, false));
            awaitPacket(a, Packets.BlockChange.class);
            a.sendContainerOpen((byte) 0, px, py, pz);
            Packets.ContainerData snap = assertInstanceOf(Packets.ContainerData.class,
                    awaitPacketMatching(a, Packets.ContainerData.class, p -> true));
            assertEquals(com.minecraftclone.world.tinkers.ToolStationEntity.TYPE, snap.type());
        }
    }

    @Test
    void castingOperationsProduceAuthoritativeSnapshots() throws Exception {
        try (NetClient client = new NetClient("127.0.0.1", server.getPort())) {
            client.sendJoin("Caster");
            Packets.Welcome welcome = assertInstanceOf(Packets.Welcome.class,
                    awaitPacket(client, Packets.Welcome.class));
            int x = (int) Math.floor(welcome.spawnX());
            int y = Math.round(welcome.spawnY());
            int z = (int) Math.floor(welcome.spawnZ());
            client.sendPlaceBlock(new Packets.PlaceBlock(
                    (byte) 0, x, y, z, BlockType.CASTING_TABLE.id, (byte) 0, false));
            awaitPacket(client, Packets.BlockChange.class);

            client.sendCastingOperation(new Packets.CastingOperation(
                    (byte) 0, x, y, z, Packets.CAST_IMPRINT, BlockType.PLANKS.id,
                    (byte) com.minecraftclone.world.tinkers.ToolPartType.PICK_HEAD.ordinal(), 1));
            Packets.ContainerData imprinted = assertInstanceOf(Packets.ContainerData.class,
                    awaitPacketMatching(client, Packets.ContainerData.class,
                            p -> ((Packets.ContainerData) p).x() == x));
            com.minecraftclone.world.CastingEntity state = new com.minecraftclone.world.CastingEntity(
                    BlockType.CASTING_TABLE, false);
            state.readFrom(new java.io.DataInputStream(new java.io.ByteArrayInputStream(imprinted.payload())));
            assertEquals(com.minecraftclone.world.tinkers.ToolPartType.PICK_HEAD, state.castShape());

            client.sendCastingOperation(new Packets.CastingOperation(
                    (byte) 0, x, y, z, Packets.CAST_INSERT, BlockType.IRON_INGOT.id, (byte) -1, 3));
            Packets.ContainerData inserted = assertInstanceOf(Packets.ContainerData.class,
                    awaitPacketMatching(client, Packets.ContainerData.class,
                            p -> ((Packets.ContainerData) p).x() == x));
            state.readFrom(new java.io.DataInputStream(new java.io.ByteArrayInputStream(inserted.payload())));
            assertEquals(BlockType.IRON_INGOT, state.inputType());
            assertEquals(3, state.inputCount());

            // A client-authored full snapshot cannot bypass the operation path.
            state.insertMaterial(BlockType.IRON_INGOT, CastingEntity.TABLE_INPUT_CAP);
            java.io.ByteArrayOutputStream forged = new java.io.ByteArrayOutputStream();
            state.writeTo(new java.io.DataOutputStream(forged));
            client.sendContainerData(new Packets.ContainerData(
                    (byte) 0, x, y, z, CastingEntity.TABLE_TYPE, forged.toByteArray()));
            client.sendContainerOpen((byte) 0, x, y, z);
            Packets.ContainerData unchanged = assertInstanceOf(Packets.ContainerData.class,
                    awaitPacketMatching(client, Packets.ContainerData.class,
                            p -> ((Packets.ContainerData) p).x() == x));
            state.readFrom(new java.io.DataInputStream(new java.io.ByteArrayInputStream(unchanged.payload())));
            assertEquals(3, state.inputCount());
        }
    }

    @Test
    void containerContentsSyncBetweenClients() throws Exception {
        try (NetClient a = new NetClient("127.0.0.1", server.getPort());
             NetClient b = new NetClient("127.0.0.1", server.getPort())) {
            // Alice places a chest at her spawn and opens it: an empty snapshot arrives.
            Packets.Welcome welcomeA = placeChestAtSpawn(a);
            int px = (int) Math.floor(welcomeA.spawnX());
            int py = Math.round(welcomeA.spawnY());
            int pz = (int) Math.floor(welcomeA.spawnZ());
            a.sendContainerOpen((byte) 0, px, py, pz);
            Packets.ContainerData empty = assertInstanceOf(Packets.ContainerData.class,
                    awaitPacketMatching(a, Packets.ContainerData.class, p -> true));
            assertEquals("chest", empty.type());
            com.minecraftclone.world.Chest checkEmpty = new com.minecraftclone.world.Chest();
            checkEmpty.readFrom(new java.io.DataInputStream(new java.io.ByteArrayInputStream(empty.payload())));
            assertNull(checkEmpty.typeOf(0));

            // Alice puts stone in slot 0 and "closes" the GUI by pushing an update.
            com.minecraftclone.world.Chest edited = new com.minecraftclone.world.Chest();
            edited.setSlot(0, BlockType.STONE, 5);
            java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream();
            edited.writeTo(new java.io.DataOutputStream(buf));
            a.sendContainerData(new Packets.ContainerData((byte) 0, px, py, pz, "chest", buf.toByteArray()));

            // Bob joins and opens the same chest: he sees Alice's stone.
            b.sendJoin("Bob");
            assertInstanceOf(Packets.Welcome.class, awaitPacket(b, Packets.Welcome.class));
            b.sendContainerOpen((byte) 0, px, py, pz);
            Packets.ContainerData filled = assertInstanceOf(Packets.ContainerData.class,
                    awaitPacketMatching(b, Packets.ContainerData.class, p -> true));
            com.minecraftclone.world.Chest seen = new com.minecraftclone.world.Chest();
            seen.readFrom(new java.io.DataInputStream(new java.io.ByteArrayInputStream(filled.payload())));
            assertEquals(BlockType.STONE, seen.typeOf(0));
            assertEquals(5, seen.countOf(0));

            // Alice also receives Bob-free rebroadcasts of her own update? No -
            // updates go to OTHERS - but she should get Bob's open-triggered none.
            // (Nothing further to assert for Alice here.)
        }
    }

    @Test
    void containerUpdateFromFarAwayIsRejected() throws Exception {
        try (NetClient a = new NetClient("127.0.0.1", server.getPort())) {
            Packets.Welcome welcome = placeChestAtSpawn(a);
            // A chest far outside reach: the server must not apply or echo anything.
            int farX = (int) Math.floor(welcome.spawnX()) + 200;
            com.minecraftclone.world.Chest edited = new com.minecraftclone.world.Chest();
            edited.setSlot(0, BlockType.DIAMOND_ORE, 64);
            java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream();
            edited.writeTo(new java.io.DataOutputStream(buf));
            a.sendContainerData(new Packets.ContainerData((byte) 0, farX,
                    Math.round(welcome.spawnY()), (int) Math.floor(welcome.spawnZ()), "chest", buf.toByteArray()));
            // Any ContainerData that DOES come back would be for our own earlier
            // traffic; give the server a moment and confirm nothing addressed to
            // the far cell arrives.
            long deadline = System.currentTimeMillis() + 1500;
            while (System.currentTimeMillis() < deadline) {
                Object p = a.poll();
                if (p instanceof Packets.ContainerData d && d.x() == farX) {
                    throw new AssertionError("Server accepted an out-of-reach container update");
                }
                Thread.sleep(10);
            }
        }
    }

    @Test
    void droppedItemsSyncAndGrantPickup() throws Exception {
        try (NetClient a = new NetClient("127.0.0.1", server.getPort());
             NetClient b = new NetClient("127.0.0.1", server.getPort())) {
            a.sendJoin("Alice");
            Packets.Welcome welcomeA = assertInstanceOf(Packets.Welcome.class,
                    awaitPacket(a, Packets.Welcome.class));
            b.sendJoin("Bob");
            assertInstanceOf(Packets.Welcome.class, awaitPacket(b, Packets.Welcome.class));

            // Alice reports breaking a block near her position: the drop becomes
            // server-authoritative and both clients hear about it.
            float ix = welcomeA.spawnX(), iy = welcomeA.spawnY(), iz = welcomeA.spawnZ();
            a.sendItemSpawn(new Packets.ItemSpawn((byte) 0, ix, iy, iz, BlockType.STONE.id, 4));
            Packets.ItemAdd addA = assertInstanceOf(Packets.ItemAdd.class,
                    awaitPacketMatching(a, Packets.ItemAdd.class, p -> true));
            assertTrue(addA.id() > 0);
            assertEquals(BlockType.STONE.id, addA.blockId());
            assertEquals(4, addA.count());
            Packets.ItemAdd addB = assertInstanceOf(Packets.ItemAdd.class,
                    awaitPacketMatching(b, Packets.ItemAdd.class, p -> p instanceof Packets.ItemAdd d && d.id() == addA.id()));
            assertEquals(addA.id(), addB.id());

            // A spawn far from Alice is rejected - no ADD ever arrives for it.
            a.sendItemSpawn(new Packets.ItemSpawn((byte) 0, ix + 500f, iy, iz, BlockType.DIRT.id, 2));
            long deadline = System.currentTimeMillis() + 1200;
            while (System.currentTimeMillis() < deadline) {
                Object p = b.poll();
                if (p instanceof Packets.ItemAdd d && d.blockId() == BlockType.DIRT.id) {
                    throw new AssertionError("Server accepted an out-of-range item spawn");
                }
                Thread.sleep(10);
            }

            // Bob stands on the item and picks it up: he gets GIVE, Alice gets REMOVE.
            b.sendMove(new Packets.Move(ix, iy, iz, 0f, 0f, true, false, false, 20f, 20f, (short) 0));
            b.sendItemPickup(addB.id());
            Packets.ItemGive give = assertInstanceOf(Packets.ItemGive.class,
                    awaitPacketMatching(b, Packets.ItemGive.class, p -> true));
            assertEquals(addB.id(), give.id());
            assertEquals(BlockType.STONE.id, give.blockId());
            assertEquals(4, give.count());
            assertInstanceOf(Packets.ItemRemove.class,
                    awaitPacketMatching(a, Packets.ItemRemove.class, p -> ((Packets.ItemRemove) p).id() == addA.id()));
        }
    }

    @Test
    void playerStatePersistsAcrossReconnect() throws Exception {
        String snapshot = String.join("\n", java.util.List.of(
                "pos_x=123.5", "pos_y=70", "pos_z=-45.25", "yaw=45", "pitch=10",
                "dim=NETHER", "selected=3", "flying=true",
                "health=15", "hunger=80", "thirst=60", "stamina=90",
                "slot.0=" + BlockType.STONE.name() + ",12"));

        // Session 1: Alice pushes a snapshot, then disconnects.
        try (NetClient a = new NetClient("127.0.0.1", server.getPort())) {
            a.sendJoin("Alice");
            assertInstanceOf(Packets.Welcome.class, awaitPacket(a, Packets.Welcome.class));
            a.sendPlayerSync(snapshot);
        }

        // Session 2: the same name rejoins and gets their state back.
        try (NetClient a = new NetClient("127.0.0.1", server.getPort())) {
            a.sendJoin("Alice");
            assertInstanceOf(Packets.Welcome.class, awaitPacket(a, Packets.Welcome.class));
            Packets.PlayerRestore restore = assertInstanceOf(Packets.PlayerRestore.class,
                    awaitPacketMatching(a, Packets.PlayerRestore.class, p -> true));
            assertTrue(restore.data().contains("pos_x=123.5"));
            assertTrue(restore.data().contains("dim=NETHER"));
        }
    }

    @Test
    void serverConfigPersistsAndApplies() throws Exception {
        Path cfgFile = saveDir.resolve(ServerConfig.FILE_NAME);
        // Round-trip: save with custom values, load them back.
        ServerConfig written = new ServerConfig();
        written.setPort(12345);
        written.setMaxPlayers(3);
        written.setPvp(false);
        written.setMotd("Welcome, traveler");
        written.save(cfgFile);
        ServerConfig read = ServerConfig.load(cfgFile);
        assertEquals(12345, read.getPort());
        assertEquals(3, read.getMaxPlayers());
        assertFalse(read.isPvpEnabled());
        assertEquals("Welcome, traveler", read.getMotd());

        // A missing file yields defaults; a malformed line is ignored.
        assertEquals(25565, ServerConfig.load(saveDir.resolve("nope.properties")).getPort());
        Files.writeString(cfgFile, "port=notanumber\npvp=maybe\nmotd=hi");
        ServerConfig lenient = ServerConfig.load(cfgFile);
        assertEquals(25565, lenient.getPort()); // bad number -> default
        assertTrue(lenient.isPvpEnabled());      // unparseable boolean -> default true
        assertEquals("hi", lenient.getMotd());
    }

    @Test
    void dedicatedServerHonorsConfigBansPvpAndMotd() throws Exception {
        ServerConfig cfg = new ServerConfig();
        cfg.setPort(0); // ephemeral: never collide with anything on 25565
        cfg.setMaxPlayers(1);
        cfg.setPvp(false);
        cfg.setMotd("Welcome to the test server");
        try (GameServer configured = new GameServer(cfg, new WorldGenSettings(),
                new WorldGenSettings().resolveSeed(), Files.createTempDirectory("mcloneserver2"))) {
            configured.start();

            // MOTD arrives as a chat line from "Server".
            try (NetClient a = new NetClient("127.0.0.1", configured.getPort())) {
                a.sendJoin("Alice");
                assertInstanceOf(Packets.Welcome.class, awaitPacket(a, Packets.Welcome.class));
                Packets.ChatMsg motd = assertInstanceOf(Packets.ChatMsg.class,
                        awaitPacketMatching(a, Packets.ChatMsg.class,
                                p -> ((Packets.ChatMsg) p).name().equals("Server")));
                assertEquals("Welcome to the test server", motd.text());
            }

            // max-players=1: the second concurrent join is rejected as full.
            // (Give the server a beat to process the previous client's disconnect.)
            Thread.sleep(500);
            try (NetClient a = new NetClient("127.0.0.1", configured.getPort())) {
                a.sendJoin("Alice");
                assertInstanceOf(Packets.Welcome.class, awaitPacket(a, Packets.Welcome.class));
                try (NetClient b = new NetClient("127.0.0.1", configured.getPort())) {
                    b.sendJoin("Bob");
                    Packets.Reject reject = assertInstanceOf(Packets.Reject.class,
                            awaitPacketMatching(b, Packets.Reject.class, p -> true));
                    assertTrue(reject.reason().contains("full"));
                }
            }

            // Room for more players again - the config is read live per join.
            cfg.setMaxPlayers(8);

            // PvP off: an in-reach swing relays no damage.
            try (NetClient a = new NetClient("127.0.0.1", configured.getPort());
                 NetClient b = new NetClient("127.0.0.1", configured.getPort())) {
                a.sendJoin("Alice");
                assertInstanceOf(Packets.Welcome.class, awaitPacket(a, Packets.Welcome.class));
                b.sendJoin("Bob");
                assertInstanceOf(Packets.Welcome.class, awaitPacket(b, Packets.Welcome.class));
                Packets.PlayerJoined bobJoin = assertInstanceOf(Packets.PlayerJoined.class,
                        awaitPacketMatching(a, Packets.PlayerJoined.class,
                                p -> ((Packets.PlayerJoined) p).name().equals("Bob")));
                b.sendMove(new Packets.Move(0.5f, 70f, 0.5f, 0f, 0f, true, false, false, 20f, 20f, (short) 0));
                Thread.sleep(200);
                a.sendPlayerAttack(bobJoin.id(), 5f);
                long deadline = System.currentTimeMillis() + 1200;
                while (System.currentTimeMillis() < deadline) {
                    Object p = b.poll();
                    if (p instanceof Packets.PlayerDamage d) {
                        throw new AssertionError("pvp=false still relayed player damage");
                    }
                    Thread.sleep(10);
                }
            }

            // Bans: persisted, checked at join, liftable.
            assertTrue(configured.ban("Griefer"));
            assertFalse(configured.isBanned("Innocent"));
            try (NetClient g = new NetClient("127.0.0.1", configured.getPort())) {
                g.sendJoin("Griefer");
                Packets.Reject banned = assertInstanceOf(Packets.Reject.class,
                        awaitPacketMatching(g, Packets.Reject.class, p -> true));
                assertTrue(banned.reason().contains("banned"));
            }
            assertTrue(configured.unban("Griefer"));
        }
    }

    @Test
    void sleepVoteSkipsNightWhenEveryoneIsInBed() throws Exception {        server.setTimeOfDayForTesting(0f); // midnight - night
        try (NetClient a = new NetClient("127.0.0.1", server.getPort());
             NetClient b = new NetClient("127.0.0.1", server.getPort())) {
            a.sendJoin("Alice");
            assertInstanceOf(Packets.Welcome.class, awaitPacket(a, Packets.Welcome.class));
            b.sendJoin("Bob");
            assertInstanceOf(Packets.Welcome.class, awaitPacket(b, Packets.Welcome.class));

            // Alice goes to bed: everyone hears 1/2.
            a.sendSleepVote();
            assertInstanceOf(Packets.SleepState.class,
                    awaitPacketMatching(a, Packets.SleepState.class, p -> ((Packets.SleepState) p).sleeping() == 1 && ((Packets.SleepState) p).total() == 2));
            assertInstanceOf(Packets.SleepState.class,
                    awaitPacketMatching(b, Packets.SleepState.class, p -> ((Packets.SleepState) p).sleeping() == 1 && ((Packets.SleepState) p).total() == 2));

            // Bob joins her: unanimous - the server skips to morning (a TimeSync
            // well past midnight) and the count resets to 0/2.
            b.sendSleepVote();
            assertInstanceOf(Packets.SleepState.class,
                    awaitPacketMatching(b, Packets.SleepState.class, p -> ((Packets.SleepState) p).sleeping() == 0 && ((Packets.SleepState) p).total() == 2));
            assertInstanceOf(Packets.TimeSync.class,
                    awaitPacketMatching(b, Packets.TimeSync.class, p -> ((Packets.TimeSync) p).timeOfDay() > 0.2f));
            assertInstanceOf(Packets.TimeSync.class,
                    awaitPacketMatching(a, Packets.TimeSync.class, p -> ((Packets.TimeSync) p).timeOfDay() > 0.2f));
        }
    }

    @Test
    void playerAttackRelaysDamageToTarget() throws Exception {
        // Widen the server-side swing cooldown for this test so the "second
        // swing is gated" assertion can't race tick-scheduling gaps.
        server.setAttackCooldownNanos(2_000_000_000L);
        try (NetClient a = new NetClient("127.0.0.1", server.getPort());
             NetClient b = new NetClient("127.0.0.1", server.getPort())) {
            a.sendJoin("Alice");
            Packets.Welcome welcomeA = assertInstanceOf(Packets.Welcome.class,
                    awaitPacketMatching(a, Packets.Welcome.class, p -> true));
            b.sendJoin("Bob");
            assertInstanceOf(Packets.Welcome.class, awaitPacket(b, Packets.Welcome.class));
            // Alice learns Bob's id from his join notice...
            Packets.PlayerJoined bobJoin = assertInstanceOf(Packets.PlayerJoined.class,
                    awaitPacketMatching(a, Packets.PlayerJoined.class, p -> ((Packets.PlayerJoined) p).name().equals("Bob")));
            // ...and Bob stands next to her so the reach check passes.
            b.sendMove(new Packets.Move(welcomeA.spawnX(), welcomeA.spawnY(), welcomeA.spawnZ(), 0f, 0f, true, false, false, 20f, 20f, (short) 0));

            a.sendPlayerAttack(bobJoin.id(), 2f);
            Packets.PlayerDamage hit = assertInstanceOf(Packets.PlayerDamage.class,
                    awaitPacketMatching(b, Packets.PlayerDamage.class, p -> ((Packets.PlayerDamage) p).amount() == 2f));
            assertEquals(2f, hit.amount());

            // A swing inside the server-side cooldown is dropped: it carries a
            // distinctive amount, and no such packet may EVER arrive.
            a.sendPlayerAttack(bobJoin.id(), 3f);
            long deadline = System.currentTimeMillis() + 1000;
            while (System.currentTimeMillis() < deadline) {
                Object p = b.poll();
                if (p instanceof Packets.PlayerDamage d && d.amount() == 3f) {
                    throw new AssertionError("Cooldown did not gate the second swing");
                }
                Thread.sleep(10);
            }

            // Out of reach: move Bob far away, then keep swinging with another
            // distinctive amount across more than one cooldown window. Nothing
            // should EVER be relayed - the move travels on Bob's socket, so
            // give it a beat to land before the first swing.
            b.sendMove(new Packets.Move(welcomeA.spawnX() + 100f, welcomeA.spawnY(), welcomeA.spawnZ(), 0f, 0f, true, false, false, 20f, 20f, (short) 0));
            Thread.sleep(300);
            long rejectWindowEnd = System.currentTimeMillis() + 2500;
            long nextSwing = System.currentTimeMillis();
            while (System.currentTimeMillis() < rejectWindowEnd) {
                Object p = b.poll();
                if (p instanceof Packets.PlayerDamage d && d.amount() == 4f) {
                    throw new AssertionError("Server relayed an out-of-reach player attack");
                }
                if (System.currentTimeMillis() >= nextSwing) {
                    nextSwing = System.currentTimeMillis() + 600;
                    a.sendPlayerAttack(bobJoin.id(), 4f);
                }
                Thread.sleep(20);
            }
        }
    }
}
