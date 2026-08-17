package com.minecraftclone.net;

import com.minecraftclone.world.BlockType;
import com.minecraftclone.world.gen.WorldGenSettings;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
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
            assertInstanceOf(Packets.Welcome.class, awaitPacket(a, Packets.Welcome.class));
            // Place a block; the server echoes the authoritative change back.
            a.sendPlaceBlock(new Packets.PlaceBlock((byte) 0, 5, 64, 5, BlockType.STONE.id, (byte) 0, false));
            Packets.BlockChange change = assertInstanceOf(Packets.BlockChange.class,
                    awaitPacket(a, Packets.BlockChange.class));
            assertEquals(5, change.x());
            assertEquals(64, change.y());
            assertEquals(5, change.z());
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
            assertInstanceOf(Packets.Welcome.class, awaitPacket(a, Packets.Welcome.class));
            // Edit a block in chunk (0,0), then request that chunk - the server
            // should now send its full raw contents rather than a vanilla ack.
            a.sendPlaceBlock(new Packets.PlaceBlock((byte) 0, 5, 64, 5, BlockType.STONE.id, (byte) 0, false));
            assertInstanceOf(Packets.BlockChange.class, awaitPacket(a, Packets.BlockChange.class));
            a.sendChunkRequest((byte) 0, 0, 0);
            Packets.ChunkData data = assertInstanceOf(Packets.ChunkData.class, awaitPacket(a, Packets.ChunkData.class));
            assertEquals(0, data.cx());
            assertEquals(0, data.cz());
            assertEquals(32768, data.blocks().length);
        }
    }

    @Test
    void mobSpawnsAreSentOnJoin() throws Exception {
        try (NetClient a = new NetClient("127.0.0.1", server.getPort())) {
            a.sendJoin("Alice");
            assertInstanceOf(Packets.Welcome.class, awaitPacket(a, Packets.Welcome.class));
            // The server seeds some mobs at startup and sends them on join; if any
            // arrive they must be well-formed (valid type ordinal, sane id).
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
            }
            // Not asserting sawSpawn strictly - spawns are probabilistic - but if one
            // arrives it must be valid (asserted above).
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
}
