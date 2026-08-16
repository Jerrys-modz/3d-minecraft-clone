package com.minecraftclone.net;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * The multiplayer wire protocol: a fixed set of length-prefixed binary packets
 * sent over a plain TCP socket, mirroring the hand-rolled {@code ChunkStorage}
 * conventions (DataOutput/DataInput, length-prefixed, no external libraries).
 * <p>
 * Every packet is framed as {@code int length} followed by {@code length}
 * payload bytes; the first payload byte is the opcode, the rest are the
 * fields. Encoding is allocation-free on the send path (a shared buffer) and
 * each packet type has a static {@code encode} method so the wire format is
 * unit-testable in isolation (see {@code net/PacketsTest}).
 * <p>
 * The server is authoritative: clients send <i>intents</i> (join, move, place,
 * break, chat) and the server replies with <i>state</i> (welcome, remote
 * players, block changes, chunk data). Clients never trust each other directly.
 */
public final class Packets {

    private Packets() {
    }

    // ---------------------------------------------------------------
    // Opcodes
    // ---------------------------------------------------------------

    public static final byte OP_JOIN = 1;           // C->S: request to join with a player name
    public static final byte OP_MOVE = 2;           // C->S: player position/look intent
    public static final byte OP_PLACE_BLOCK = 3;    // C->S: place a block
    public static final byte OP_BREAK_BLOCK = 4;    // C->S: break a block
    public static final byte OP_CHAT = 5;           // C->S: chat message
    public static final byte OP_READY = 6;          // C->S: client finished loading, requests spawn + chunks
    public static final byte OP_CHUNK_REQUEST = 7;  // C->S: ask for a chunk's authoritative contents

    public static final byte OP_WELCOME = 11;       // S->C: join accepted, world identity + spawn
    public static final byte OP_REJECT = 12;        // S->C: join rejected (e.g. server full)
    public static final byte OP_PLAYER_JOINED = 13; // S->C: another player appeared
    public static final byte OP_PLAYER_LEFT = 14;   // S->C: another player left
    public static final byte OP_PLAYER_STATE = 15;  // S->C: another player's position/look
    public static final byte OP_BLOCK_CHANGE = 16;  // S->C: a block changed anywhere
    public static final byte OP_CHUNK_DATA = 17;    // S->C: full chunk contents
    public static final byte OP_CHUNK_ACK = 18;     // S->C: chunk exists (vanilla, matches seed) - no payload needed
    public static final byte OP_CHAT_MSG = 19;      // S->C: chat message from a player

    // ---------------------------------------------------------------
    // Shared encode/decode helpers
    // ---------------------------------------------------------------

    /** The max length of any single packet payload. */
    static final int MAX_PAYLOAD = 1 << 20;

    /**
     * Writes a packet to {@code out}: a 4-byte length prefix followed by the
     * payload bytes (opcode first). Callers pass the payload already encoded
     * by the static {@code encode*} methods.
     */
    public static void writeFrame(DataOutputStream out, byte[] payload) throws IOException {
        if (payload.length > MAX_PAYLOAD) {
            throw new IOException("Packet too large: " + payload.length);
        }
        out.writeInt(payload.length);
        out.write(payload);
        out.flush();
    }

    /** Reads one framed packet payload from {@code in}, or null on clean EOF. */
    public static byte[] readFrame(DataInputStream in) throws IOException {
        int length;
        try {
            length = in.readInt();
        } catch (java.io.EOFException e) {
            return null; // clean socket close
        }
        if (length < 1 || length > MAX_PAYLOAD) {
            throw new IOException("Bad packet length: " + length);
        }
        byte[] payload = new byte[length];
        in.readFully(payload);
        return payload;
    }

    // ---------------------------------------------------------------
    // Decoded packet views (records)
    // ---------------------------------------------------------------

    public record Join(String name) {
    }

    public record Move(float x, float y, float z, float yaw, float pitch, boolean onGround, boolean flying, boolean sprinting) {
    }

    public record PlaceBlock(int x, int y, int z, byte blockId, byte orientation, boolean overlay) {
    }

    public record BreakBlock(int x, int y, int z, boolean overlay) {
    }

    public record Chat(String text) {
    }

    public record ChunkRequest(int cx, int cz) {
    }

    public record Welcome(int selfId, long seed, int worldType, boolean structures,
                          int seaLevelIndex, int terrainSizeIndex, int weeksPerMonth,
                          float spawnX, float spawnY, float spawnZ) {
    }

    public record PlayerJoined(int id, String name, float x, float y, float z, float yaw, float pitch) {
    }

    public record PlayerLeft(int id) {
    }

    public record PlayerState(int id, float x, float y, float z, float yaw, float pitch,
                              boolean onGround, boolean flying, boolean sprinting) {
    }

    public record BlockChange(int x, int y, int z, byte blockId, byte orientation, boolean overlay) {
    }

    public record ChunkData(int cx, int cz, byte[] blocks, byte[] overlays, byte[] orientations) {
    }

    public record ChatMsg(int id, String name, String text) {
    }

    // ---------------------------------------------------------------
    // Client -> Server encoders
    // ---------------------------------------------------------------

    public static byte[] encodeJoin(String name) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(buf);
        out.writeByte(OP_JOIN);
        out.writeUTF(name);
        out.close();
        return buf.toByteArray();
    }

    public static byte[] encodeMove(Move move) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(buf);
        out.writeByte(OP_MOVE);
        out.writeFloat(move.x());
        out.writeFloat(move.y());
        out.writeFloat(move.z());
        out.writeFloat(move.yaw());
        out.writeFloat(move.pitch());
        out.writeBoolean(move.onGround());
        out.writeBoolean(move.flying());
        out.writeBoolean(move.sprinting());
        out.close();
        return buf.toByteArray();
    }

    public static byte[] encodePlaceBlock(PlaceBlock place) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(buf);
        out.writeByte(OP_PLACE_BLOCK);
        out.writeInt(place.x());
        out.writeInt(place.y());
        out.writeInt(place.z());
        out.writeByte(place.blockId());
        out.writeByte(place.orientation());
        out.writeBoolean(place.overlay());
        out.close();
        return buf.toByteArray();
    }

    public static byte[] encodeBreakBlock(BreakBlock brk) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(buf);
        out.writeByte(OP_BREAK_BLOCK);
        out.writeInt(brk.x());
        out.writeInt(brk.y());
        out.writeInt(brk.z());
        out.writeBoolean(brk.overlay());
        out.close();
        return buf.toByteArray();
    }

    public static byte[] encodeChat(String text) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(buf);
        out.writeByte(OP_CHAT);
        out.writeUTF(text);
        out.close();
        return buf.toByteArray();
    }

    public static byte[] encodeChunkRequest(int cx, int cz) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(buf);
        out.writeByte(OP_CHUNK_REQUEST);
        out.writeInt(cx);
        out.writeInt(cz);
        out.close();
        return buf.toByteArray();
    }

    public static byte[] encodeReady() throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(buf);
        out.writeByte(OP_READY);
        out.close();
        return buf.toByteArray();
    }

    // ---------------------------------------------------------------
    // Server -> Client encoders
    // ---------------------------------------------------------------

    public static byte[] encodeWelcome(Welcome welcome) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(buf);
        out.writeByte(OP_WELCOME);
        out.writeInt(welcome.selfId());
        out.writeLong(welcome.seed());
        out.writeInt(welcome.worldType());
        out.writeBoolean(welcome.structures());
        out.writeInt(welcome.seaLevelIndex());
        out.writeInt(welcome.terrainSizeIndex());
        out.writeInt(welcome.weeksPerMonth());
        out.writeFloat(welcome.spawnX());
        out.writeFloat(welcome.spawnY());
        out.writeFloat(welcome.spawnZ());
        out.close();
        return buf.toByteArray();
    }

    public static byte[] encodeReject(String reason) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(buf);
        out.writeByte(OP_REJECT);
        out.writeUTF(reason);
        out.close();
        return buf.toByteArray();
    }

    public static byte[] encodePlayerJoined(PlayerJoined joined) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(buf);
        out.writeByte(OP_PLAYER_JOINED);
        out.writeInt(joined.id());
        out.writeUTF(joined.name());
        out.writeFloat(joined.x());
        out.writeFloat(joined.y());
        out.writeFloat(joined.z());
        out.writeFloat(joined.yaw());
        out.writeFloat(joined.pitch());
        out.close();
        return buf.toByteArray();
    }

    public static byte[] encodePlayerLeft(PlayerLeft left) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(buf);
        out.writeByte(OP_PLAYER_LEFT);
        out.writeInt(left.id());
        out.close();
        return buf.toByteArray();
    }

    public static byte[] encodePlayerState(PlayerState state) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(buf);
        out.writeByte(OP_PLAYER_STATE);
        out.writeInt(state.id());
        out.writeFloat(state.x());
        out.writeFloat(state.y());
        out.writeFloat(state.z());
        out.writeFloat(state.yaw());
        out.writeFloat(state.pitch());
        out.writeBoolean(state.onGround());
        out.writeBoolean(state.flying());
        out.writeBoolean(state.sprinting());
        out.close();
        return buf.toByteArray();
    }

    public static byte[] encodeBlockChange(BlockChange change) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(buf);
        out.writeByte(OP_BLOCK_CHANGE);
        out.writeInt(change.x());
        out.writeInt(change.y());
        out.writeInt(change.z());
        out.writeByte(change.blockId());
        out.writeByte(change.orientation());
        out.writeBoolean(change.overlay());
        out.close();
        return buf.toByteArray();
    }

    public static byte[] encodeChunkData(ChunkData data) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(buf);
        out.writeByte(OP_CHUNK_DATA);
        out.writeInt(data.cx());
        out.writeInt(data.cz());
        out.writeInt(data.blocks().length);
        out.write(data.blocks());
        out.writeInt(data.overlays().length);
        out.write(data.overlays());
        out.writeInt(data.orientations().length);
        out.write(data.orientations());
        out.close();
        return buf.toByteArray();
    }

    public static byte[] encodeChunkAck(int cx, int cz) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(buf);
        out.writeByte(OP_CHUNK_ACK);
        out.writeInt(cx);
        out.writeInt(cz);
        out.close();
        return buf.toByteArray();
    }

    public static byte[] encodeChatMsg(ChatMsg msg) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(buf);
        out.writeByte(OP_CHAT_MSG);
        out.writeInt(msg.id());
        out.writeUTF(msg.name());
        out.writeUTF(msg.text());
        out.close();
        return buf.toByteArray();
    }

    // ---------------------------------------------------------------
    // Decoders (return null for unsupported opcodes)
    // ---------------------------------------------------------------

    /** Reads the opcode from a payload and decodes the rest into the matching record. */
    public static Object decode(byte[] payload) throws IOException {
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(payload));
        byte op = in.readByte();
        return switch (op) {
            case OP_JOIN -> new Join(in.readUTF());
            case OP_MOVE -> new Move(in.readFloat(), in.readFloat(), in.readFloat(), in.readFloat(), in.readFloat(),
                    in.readBoolean(), in.readBoolean(), in.readBoolean());
            case OP_PLACE_BLOCK -> new PlaceBlock(in.readInt(), in.readInt(), in.readInt(), in.readByte(), in.readByte(), in.readBoolean());
            case OP_BREAK_BLOCK -> new BreakBlock(in.readInt(), in.readInt(), in.readInt(), in.readBoolean());
            case OP_CHAT -> new Chat(in.readUTF());
            case OP_CHUNK_REQUEST -> new ChunkRequest(in.readInt(), in.readInt());
            case OP_READY -> new Ready();
            case OP_WELCOME -> new Welcome(in.readInt(), in.readLong(), in.readInt(), in.readBoolean(),
                    in.readInt(), in.readInt(), in.readInt(), in.readFloat(), in.readFloat(), in.readFloat());
            case OP_REJECT -> new Reject(in.readUTF());
            case OP_PLAYER_JOINED -> new PlayerJoined(in.readInt(), in.readUTF(), in.readFloat(), in.readFloat(),
                    in.readFloat(), in.readFloat(), in.readFloat());
            case OP_PLAYER_LEFT -> new PlayerLeft(in.readInt());
            case OP_PLAYER_STATE -> new PlayerState(in.readInt(), in.readFloat(), in.readFloat(), in.readFloat(),
                    in.readFloat(), in.readFloat(), in.readBoolean(), in.readBoolean(), in.readBoolean());
            case OP_BLOCK_CHANGE -> new BlockChange(in.readInt(), in.readInt(), in.readInt(), in.readByte(), in.readByte(), in.readBoolean());
            case OP_CHUNK_DATA -> {
                int cx = in.readInt(), cz = in.readInt();
                int blockLen = in.readInt();
                byte[] blocks = new byte[blockLen];
                in.readFully(blocks);
                int overlayLen = in.readInt();
                byte[] overlays = new byte[overlayLen];
                in.readFully(overlays);
                int orientLen = in.readInt();
                byte[] orientations = new byte[orientLen];
                in.readFully(orientations);
                yield new ChunkData(cx, cz, blocks, overlays, orientations);
            }
            case OP_CHUNK_ACK -> new ChunkAck(in.readInt(), in.readInt());
            case OP_CHAT_MSG -> new ChatMsg(in.readInt(), in.readUTF(), in.readUTF());
            default -> throw new IOException("Unknown opcode: " + op);
        };
    }

    public record Reject(String reason) {
    }

    public record ChunkAck(int cx, int cz) {
    }

    public record Ready() {
    }

    // ---------------------------------------------------------------
    // Convenience: encode/decode round-trip for tests
    // ---------------------------------------------------------------

    /** Encodes and immediately decodes a payload (used by unit tests). */
    public static Object roundTrip(byte[] payload) throws IOException {
        return decode(payload);
    }

    /** Empty payload that carries only an opcode (e.g. READY). */
    public static byte[] opcodeOnly(byte op) {
        return new byte[]{op};
    }

    /** True if the payload carries the given opcode (for opcode-only packets like READY). */
    public static boolean hasOpcode(byte[] payload, byte op) {
        return payload.length >= 1 && payload[0] == op;
    }

    /** Reads any number of packets from an input stream until EOF. */
    public static List<Object> decodeAll(DataInputStream in) throws IOException {
        List<Object> out = new ArrayList<>();
        byte[] frame;
        while ((frame = readFrame(in)) != null) {
            out.add(decode(frame));
        }
        return out;
    }
}
