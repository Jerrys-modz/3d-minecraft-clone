package com.minecraftclone.net;

import com.minecraftclone.world.Chunk;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Round-trips every packet type through its encoder and decoder to lock in the
 * wire format: a packet that encodes and decodes back to equal fields will stay
 * compatible across client/server builds even as the code changes.
 */
class PacketsTest {

    private static Object roundTrip(Object packet) throws Exception {
        byte[] payload;
        if (packet instanceof Packets.Join p) payload = Packets.encodeJoin(p.name());
        else if (packet instanceof Packets.Move p) payload = Packets.encodeMove(p);
        else if (packet instanceof Packets.PlaceBlock p) payload = Packets.encodePlaceBlock(p);
        else if (packet instanceof Packets.BreakBlock p) payload = Packets.encodeBreakBlock(p);
        else if (packet instanceof Packets.Chat p) payload = Packets.encodeChat(p.text());
        else if (packet instanceof Packets.ChunkRequest p) payload = Packets.encodeChunkRequest(p.cx(), p.cz());
        else if (packet instanceof Packets.Welcome p) payload = Packets.encodeWelcome(p);
        else if (packet instanceof Packets.PlayerJoined p) payload = Packets.encodePlayerJoined(p);
        else if (packet instanceof Packets.PlayerLeft p) payload = Packets.encodePlayerLeft(p);
        else if (packet instanceof Packets.PlayerState p) payload = Packets.encodePlayerState(p);
        else if (packet instanceof Packets.BlockChange p) payload = Packets.encodeBlockChange(p);
        else if (packet instanceof Packets.ChunkData p) payload = Packets.encodeChunkData(p);
        else if (packet instanceof Packets.ChunkAck p) payload = Packets.encodeChunkAck(p.cx(), p.cz());
        else if (packet instanceof Packets.ChatMsg p) payload = Packets.encodeChatMsg(p);
        else if (packet instanceof Packets.Ready) payload = Packets.opcodeOnly(Packets.OP_READY);
        else throw new IllegalArgumentException("No encoder for " + packet);
        // Frame it like the real socket path does, then read it back.
        ByteArrayOutputStream frame = new ByteArrayOutputStream();
        Packets.writeFrame(new DataOutputStream(frame), payload);
        byte[] read = Packets.readFrame(new DataInputStream(new ByteArrayInputStream(frame.toByteArray())));
        return Packets.decode(read);
    }

    @Test
    void joinRoundTrips() throws Exception {
        Packets.Join in = new Packets.Join("Steve");
        Packets.Join out = assertInstanceOf(Packets.Join.class, roundTrip(in));
        assertEquals("Steve", out.name());
    }

    @Test
    void moveRoundTrips() throws Exception {
        Packets.Move in = new Packets.Move(1.5f, 64.25f, -3f, 90f, -30f, true, false, true);
        Packets.Move out = assertInstanceOf(Packets.Move.class, roundTrip(in));
        assertEquals(in.x(), out.x());
        assertEquals(in.y(), out.y());
        assertEquals(in.z(), out.z());
        assertEquals(in.yaw(), out.yaw());
        assertEquals(in.pitch(), out.pitch());
        assertEquals(in.onGround(), out.onGround());
        assertEquals(in.flying(), out.flying());
        assertEquals(in.sprinting(), out.sprinting());
    }

    @Test
    void placeBlockRoundTrips() throws Exception {
        Packets.PlaceBlock in = new Packets.PlaceBlock(10, 64, 20, (byte) 3, (byte) 2, false);
        Packets.PlaceBlock out = assertInstanceOf(Packets.PlaceBlock.class, roundTrip(in));
        assertEquals(in.x(), out.x());
        assertEquals(in.y(), out.y());
        assertEquals(in.z(), out.z());
        assertEquals(in.blockId(), out.blockId());
        assertEquals(in.orientation(), out.orientation());
        assertEquals(in.overlay(), out.overlay());
    }

    @Test
    void breakBlockRoundTrips() throws Exception {
        Packets.BreakBlock in = new Packets.BreakBlock(5, 63, 7, true);
        Packets.BreakBlock out = assertInstanceOf(Packets.BreakBlock.class, roundTrip(in));
        assertEquals(in.x(), out.x());
        assertEquals(in.y(), out.y());
        assertEquals(in.z(), out.z());
        assertEquals(in.overlay(), out.overlay());
    }

    @Test
    void chatRoundTrips() throws Exception {
        Packets.Chat in = new Packets.Chat("hello world");
        Packets.Chat out = assertInstanceOf(Packets.Chat.class, roundTrip(in));
        assertEquals("hello world", out.text());
    }

    @Test
    void chunkRequestRoundTrips() throws Exception {
        Packets.ChunkRequest in = new Packets.ChunkRequest(3, -4);
        Packets.ChunkRequest out = assertInstanceOf(Packets.ChunkRequest.class, roundTrip(in));
        assertEquals(3, out.cx());
        assertEquals(-4, out.cz());
    }

    @Test
    void welcomeRoundTrips() throws Exception {
        Packets.Welcome in = new Packets.Welcome(7, 123456789L, 0, true, 1, 0, 2, 0.5f, 64f, 0.5f);
        Packets.Welcome out = assertInstanceOf(Packets.Welcome.class, roundTrip(in));
        assertEquals(in.selfId(), out.selfId());
        assertEquals(in.seed(), out.seed());
        assertEquals(in.worldType(), out.worldType());
        assertEquals(in.structures(), out.structures());
        assertEquals(in.seaLevelIndex(), out.seaLevelIndex());
        assertEquals(in.terrainSizeIndex(), out.terrainSizeIndex());
        assertEquals(in.weeksPerMonth(), out.weeksPerMonth());
        assertEquals(in.spawnX(), out.spawnX());
        assertEquals(in.spawnY(), out.spawnY());
        assertEquals(in.spawnZ(), out.spawnZ());
    }

    @Test
    void playerJoinedRoundTrips() throws Exception {
        Packets.PlayerJoined in = new Packets.PlayerJoined(2, "Alex", 1f, 64f, 2f, 45f, 10f);
        Packets.PlayerJoined out = assertInstanceOf(Packets.PlayerJoined.class, roundTrip(in));
        assertEquals(in.id(), out.id());
        assertEquals("Alex", out.name());
        assertEquals(in.x(), out.x());
        assertEquals(in.y(), out.y());
        assertEquals(in.z(), out.z());
        assertEquals(in.yaw(), out.yaw());
        assertEquals(in.pitch(), out.pitch());
    }

    @Test
    void playerLeftRoundTrips() throws Exception {
        Packets.PlayerLeft in = new Packets.PlayerLeft(2);
        Packets.PlayerLeft out = assertInstanceOf(Packets.PlayerLeft.class, roundTrip(in));
        assertEquals(2, out.id());
    }

    @Test
    void playerStateRoundTrips() throws Exception {
        Packets.PlayerState in = new Packets.PlayerState(3, 10f, 70f, 20f, -90f, 0f, true, false, false);
        Packets.PlayerState out = assertInstanceOf(Packets.PlayerState.class, roundTrip(in));
        assertEquals(in.id(), out.id());
        assertEquals(in.x(), out.x());
        assertEquals(in.y(), out.y());
        assertEquals(in.z(), out.z());
        assertEquals(in.yaw(), out.yaw());
        assertEquals(in.pitch(), out.pitch());
        assertEquals(in.onGround(), out.onGround());
        assertEquals(in.flying(), out.flying());
        assertEquals(in.sprinting(), out.sprinting());
    }

    @Test
    void blockChangeRoundTrips() throws Exception {
        Packets.BlockChange in = new Packets.BlockChange(1, 64, 2, (byte) 4, (byte) 1, false);
        Packets.BlockChange out = assertInstanceOf(Packets.BlockChange.class, roundTrip(in));
        assertEquals(in.x(), out.x());
        assertEquals(in.y(), out.y());
        assertEquals(in.z(), out.z());
        assertEquals(in.blockId(), out.blockId());
        assertEquals(in.orientation(), out.orientation());
        assertEquals(in.overlay(), out.overlay());
    }

    @Test
    void chunkDataRoundTrips() throws Exception {
        byte[] blocks = new byte[Chunk.SIZE * Chunk.HEIGHT * Chunk.SIZE];
        byte[] overlays = new byte[blocks.length];
        byte[] orientations = new byte[blocks.length];
        blocks[0] = 3; blocks[100] = 4; blocks[blocks.length - 1] = 9;
        overlays[50] = 7;
        orientations[500] = 2;
        Packets.ChunkData in = new Packets.ChunkData(4, -2, blocks, overlays, orientations);
        Packets.ChunkData out = assertInstanceOf(Packets.ChunkData.class, roundTrip(in));
        assertEquals(4, out.cx());
        assertEquals(-2, out.cz());
        assertArrayEquals(blocks, out.blocks());
        assertArrayEquals(overlays, out.overlays());
        assertArrayEquals(orientations, out.orientations());
    }

    @Test
    void chunkAckRoundTrips() throws Exception {
        Packets.ChunkAck in = new Packets.ChunkAck(0, 0);
        Packets.ChunkAck out = assertInstanceOf(Packets.ChunkAck.class, roundTrip(in));
        assertEquals(0, out.cx());
        assertEquals(0, out.cz());
    }

    @Test
    void chatMsgRoundTrips() throws Exception {
        Packets.ChatMsg in = new Packets.ChatMsg(2, "Steve", "hi");
        Packets.ChatMsg out = assertInstanceOf(Packets.ChatMsg.class, roundTrip(in));
        assertEquals(2, out.id());
        assertEquals("Steve", out.name());
        assertEquals("hi", out.text());
    }

    @Test
    void readyRoundTrips() throws Exception {
        assertInstanceOf(Packets.Ready.class, roundTrip(new Packets.Ready()));
    }

    @Test
    void unknownOpcodeRejected() {
        byte[] bad = {127};
        assertThrows(Exception.class, () -> Packets.decode(bad));
    }

    @Test
    void oversizedFrameRejected() throws Exception {
        byte[] big = new byte[Packets.MAX_PAYLOAD + 1];
        assertThrows(Exception.class, () -> Packets.writeFrame(new DataOutputStream(new ByteArrayOutputStream()), big));
    }
}
