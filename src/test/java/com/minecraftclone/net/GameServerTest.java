package com.minecraftclone.net;

import com.minecraftclone.world.BlockType;
import com.minecraftclone.world.World;
import com.minecraftclone.world.gen.WorldGenSettings;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
            a.sendMove(new Packets.Move(100f, 70f, 200f, 45f, -10f, true, false, true));
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
            b.sendMove(new Packets.Move(ix, iy, iz, 0f, 0f, true, false, false));
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
}
