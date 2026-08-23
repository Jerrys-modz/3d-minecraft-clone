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
 * fields. Each packet type has a static {@code encode} method building its
 * payload into a fresh buffer (simple and single-threaded over micro-optimized)
 * so the wire format is unit-testable in isolation (see {@code net/PacketsTest}).
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
    public static final byte OP_MOB_ATTACK = 8;     // C->S: swing at a mob (id + damage)
    public static final byte OP_PORTAL_USE = 9;     // C->S: standing in a portal block - server teleports us
    public static final byte OP_RESPAWN = 10;       // C->S: died - server sends us back to overworld spawn

    public static final byte OP_WELCOME = 11;       // S->C: join accepted, world identity + spawn
    public static final byte OP_REJECT = 12;        // S->C: join rejected (e.g. server full)
    public static final byte OP_PLAYER_JOINED = 13; // S->C: another player appeared
    public static final byte OP_PLAYER_LEFT = 14;   // S->C: another player left
    public static final byte OP_PLAYER_STATE = 15;  // S->C: another player's position/look
    public static final byte OP_BLOCK_CHANGE = 16;  // S->C: a block changed anywhere
    public static final byte OP_CHUNK_DATA = 17;    // S->C: full chunk contents
    public static final byte OP_CHUNK_ACK = 18;     // S->C: chunk exists (vanilla, matches seed) - no payload needed
    public static final byte OP_CHAT_MSG = 19;      // S->C: chat message from a player
    public static final byte OP_MOB_SPAWN = 20;     // S->C: a mob appeared
    public static final byte OP_MOB_STATE = 21;     // S->C: a mob's position/look/health
    public static final byte OP_MOB_REMOVE = 22;    // S->C: a mob died or despawned
    public static final byte OP_PLAYER_DAMAGE = 23; // S->C: a mob hurt you
    public static final byte OP_DIMENSION_CHANGE = 24; // S->C: teleported to another dimension at a position
    public static final byte OP_TIME_SYNC = 25;     // S->C: server-authoritative time of day
    public static final byte OP_PLAYER_DEATH = 26;  // S->C: another player died (respawned to overworld)
    public static final byte OP_CONTAINER_OPEN = 27;   // C->S: opening a chest/barrel/furnace - send me its contents
    public static final byte OP_CONTAINER_DATA = 28;   // S->C / C->S: full container snapshot (type + serialized slots)
    public static final byte OP_ITEM_ADD = 29;      // S->C: a dropped item exists (id + position + type/count)
    public static final byte OP_ITEM_REMOVE = 30;   // S->C: a dropped item is gone (picked up / expired)
    public static final byte OP_ITEM_PICKUP = 31;   // C->S: walk-over intent - server validates and answers GIVE
    public static final byte OP_ITEM_GIVE = 32;     // S->C: targeted - the pickup was granted, add it to your inventory
    public static final byte OP_ITEM_SPAWN = 33;    // C->S: I broke a block / died - spawn these drops server-side
    public static final byte OP_PLAYER_SYNC = 34;   // C->S: snapshot of my position/inventory/stats (PlayerSave lines)
    public static final byte OP_PLAYER_RESTORE = 35; // S->C: a saved snapshot for you, from a previous session
    public static final byte OP_PLAYER_ATTACK = 36;  // C->S: I swung at another player - server validates, target takes damage
    public static final byte OP_SLEEP_VOTE = 37;     // C->S: I'm in bed - count me toward the night-skip vote
    public static final byte OP_SLEEP_STATE = 38;    // S->C: how many players are in bed (sleeping/total)
    public static final byte OP_CASTING_OPERATION = 39; // C->S: validated mutation request for a casting station

    public static final byte CAST_IMPRINT = 0;
    public static final byte CAST_INSERT = 1;
    public static final byte CAST_TAKE_OUTPUTS = 2;

    // ---------------------------------------------------------------
    // Shared encode/decode helpers
    // ---------------------------------------------------------------

    /** The max length of any single packet payload. */
    static final int MAX_PAYLOAD = 1 << 20;

    /**
     * The largest container snapshot payload accepted (a chest is ~85 bytes, a
     * furnace under 40; anything bigger is corrupt or hostile).
     */
    static final int MAX_CONTAINER_PAYLOAD = 8192;

    /** Cap for a serialized player snapshot (36 slots of text is a few KB at most). */
    static final int MAX_PLAYER_SYNC_CHARS = 16384;

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

    public record PlaceBlock(byte dimension, int x, int y, int z, short blockId, byte orientation, boolean overlay) {
    }

    public record BreakBlock(byte dimension, int x, int y, int z, boolean overlay) {
    }

    public record Chat(String text) {
    }

    public record ChunkRequest(byte dimension, int cx, int cz) {
    }

    public record MobAttack(int mobId, float damage) {
    }

    public record MobSpawn(int mobId, byte typeId, float x, float y, float z, float yaw) {
    }

    public record MobState(int mobId, float x, float y, float z, float yaw, float health) {
    }

    public record MobRemove(int mobId, byte typeId, float x, float y, float z) {
    }

    public record PlayerDamage(float amount) {
    }

    public record PortalUse(byte dimension, short blockId) {
    }

    /** A player right-clicked a container: the server replies with a ContainerData snapshot. */
    public record ContainerOpen(byte dimension, int x, int y, int z) {
    }

    /**
     * A full container snapshot: the block-entity type name ("chest" / "barrel"
     * / "furnace") plus its serialized state (exactly the entity's
     * {@code writeTo} format, shared with the disk save). Sent by the server on
     * open and re-broadcast whenever another client pushes an update; clients
     * send the same packet to publish their changes when they close the GUI.
     * Casting stations are the exception: clients send {@link CastingOperation}
     * intents, and only the server publishes their snapshots.
     */
    public record ContainerData(byte dimension, int x, int y, int z, String type, byte[] payload) {
    }

    /** A requested mutation of a server-owned Casting Table or Casting Basin. */
    public record CastingOperation(byte dimension, int x, int y, int z, byte operation,
                                   short materialId, byte shapeOrdinal, int count) {
    }

    /** A dropped item now exists on the server (broadcast when spawned). */
    public record ItemAdd(int id, byte dimension, float x, float y, float z, short blockId, int count) {
    }

    /** A dropped item vanished (picked up by anyone, or expired). */
    public record ItemRemove(int id) {
    }

    /** A player walked over a dropped item and wants it. */
    public record ItemPickup(int id) {
    }

    /** Targeted to the picker: the pickup was granted - add this to your inventory. */
    public record ItemGive(int id, short blockId, int count) {
    }

    /** A client's local drop (block break / death loot) that should become server-authoritative. */
    public record ItemSpawn(byte dimension, float x, float y, float z, short blockId, int count) {
    }

    /**
     * The sender's full player snapshot (position, look, dimension, stats,
     * inventory, armor, durability, bed spawn) encoded as {@link
     * com.minecraftclone.player.PlayerSave} {@code key=value} lines. Sent
     * periodically by the client; the server keeps the latest per player and
     * mirrors it back as a PlayerRestore on their next join.
     */
    public record PlayerSync(String data) {
    }

    /** A previously-saved snapshot for this player - apply it instead of spawning fresh. */
    public record PlayerRestore(String data) {
    }

    /** A player swung at another player: the server validates and relays damage to the target. */
    public record PlayerAttack(int targetId, float damage) {
    }

    /** A player climbed into a bed at night: one vote toward skipping to morning. */
    public record SleepVote() {
    }

    /** How many players are currently in bed vs connected (drives the HUD notice). */
    public record SleepState(int sleeping, int total) {
    }

    public record Respawn() {
    }

    public record Welcome(int selfId, long seed, int worldType, boolean structures,
                          int seaLevelIndex, int terrainSizeIndex, int weeksPerMonth,
                          float spawnX, float spawnY, float spawnZ) {
    }

    public record PlayerJoined(int id, String name, byte dimension, float x, float y, float z, float yaw, float pitch) {
    }

    public record PlayerLeft(int id) {
    }

    public record PlayerState(int id, byte dimension, float x, float y, float z, float yaw, float pitch,
                              boolean onGround, boolean flying, boolean sprinting) {
    }

    public record BlockChange(byte dimension, int x, int y, int z, short blockId, byte orientation, boolean overlay) {
    }

    public record ChunkData(byte dimension, int cx, int cz, short[] blocks, short[] overlays, byte[] orientations) {
    }

    public record ChunkAck(byte dimension, int cx, int cz) {
    }

    public record ChatMsg(int id, String name, String text) {
    }

    public record DimensionChange(byte dimension, float x, float y, float z) {
    }

    public record TimeSync(float timeOfDay, int dayIndex) {
    }

    public record PlayerDeath(int id) {
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
        out.writeByte(place.dimension());
        out.writeInt(place.x());
        out.writeInt(place.y());
        out.writeInt(place.z());
        out.writeShort(place.blockId());
        out.writeByte(place.orientation());
        out.writeBoolean(place.overlay());
        out.close();
        return buf.toByteArray();
    }

    public static byte[] encodeBreakBlock(BreakBlock brk) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(buf);
        out.writeByte(OP_BREAK_BLOCK);
        out.writeByte(brk.dimension());
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

    public static byte[] encodeChunkRequest(byte dimension, int cx, int cz) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(buf);
        out.writeByte(OP_CHUNK_REQUEST);
        out.writeByte(dimension);
        out.writeInt(cx);
        out.writeInt(cz);
        out.close();
        return buf.toByteArray();
    }

    public static byte[] encodePortalUse(byte dimension, short blockId) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(buf);
        out.writeByte(OP_PORTAL_USE);
        out.writeByte(dimension);
        out.writeShort(blockId);
        out.close();
        return buf.toByteArray();
    }

    public static byte[] encodeContainerOpen(byte dimension, int x, int y, int z) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(buf);
        out.writeByte(OP_CONTAINER_OPEN);
        out.writeByte(dimension);
        out.writeInt(x);
        out.writeInt(y);
        out.writeInt(z);
        out.close();
        return buf.toByteArray();
    }

    public static byte[] encodeCastingOperation(CastingOperation operation) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(buf);
        out.writeByte(OP_CASTING_OPERATION);
        out.writeByte(operation.dimension());
        out.writeInt(operation.x());
        out.writeInt(operation.y());
        out.writeInt(operation.z());
        out.writeByte(operation.operation());
        out.writeShort(operation.materialId());
        out.writeByte(operation.shapeOrdinal());
        out.writeInt(operation.count());
        out.close();
        return buf.toByteArray();
    }

    public static byte[] encodeItemAdd(ItemAdd add) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(buf);
        out.writeByte(OP_ITEM_ADD);
        out.writeInt(add.id());
        out.writeByte(add.dimension());
        out.writeFloat(add.x());
        out.writeFloat(add.y());
        out.writeFloat(add.z());
        out.writeShort(add.blockId());
        out.writeByte(add.count());
        out.close();
        return buf.toByteArray();
    }

    public static byte[] encodeItemRemove(int id) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(buf);
        out.writeByte(OP_ITEM_REMOVE);
        out.writeInt(id);
        out.close();
        return buf.toByteArray();
    }

    public static byte[] encodeItemPickup(int id) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(buf);
        out.writeByte(OP_ITEM_PICKUP);
        out.writeInt(id);
        out.close();
        return buf.toByteArray();
    }

    public static byte[] encodeItemGive(ItemGive give) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(buf);
        out.writeByte(OP_ITEM_GIVE);
        out.writeInt(give.id());
        out.writeShort(give.blockId());
        out.writeByte(give.count());
        out.close();
        return buf.toByteArray();
    }

    public static byte[] encodeItemSpawn(ItemSpawn spawn) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(buf);
        out.writeByte(OP_ITEM_SPAWN);
        out.writeByte(spawn.dimension());
        out.writeFloat(spawn.x());
        out.writeFloat(spawn.y());
        out.writeFloat(spawn.z());
        out.writeShort(spawn.blockId());
        out.writeByte(spawn.count());
        out.close();
        return buf.toByteArray();
    }

    public static byte[] encodePlayerSync(String data) throws IOException {
        if (data.length() > MAX_PLAYER_SYNC_CHARS) {
            throw new IOException("Player sync payload too large: " + data.length());
        }
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(buf);
        out.writeByte(OP_PLAYER_SYNC);
        out.writeUTF(data);
        out.close();
        return buf.toByteArray();
    }

    public static byte[] encodePlayerRestore(String data) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(buf);
        out.writeByte(OP_PLAYER_RESTORE);
        out.writeUTF(data);
        out.close();
        return buf.toByteArray();
    }

    public static byte[] encodePlayerAttack(PlayerAttack attack) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(buf);
        out.writeByte(OP_PLAYER_ATTACK);
        out.writeInt(attack.targetId());
        out.writeFloat(attack.damage());
        out.close();
        return buf.toByteArray();
    }

    public static byte[] encodeSleepState(SleepState state) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(buf);
        out.writeByte(OP_SLEEP_STATE);
        out.writeByte(state.sleeping());
        out.writeByte(state.total());
        out.close();
        return buf.toByteArray();
    }

    public static byte[] encodeContainerData(ContainerData data) throws IOException {
        if (data.payload().length > MAX_CONTAINER_PAYLOAD) {
            throw new IOException("Container payload too large: " + data.payload().length);
        }
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(buf);
        out.writeByte(OP_CONTAINER_DATA);
        out.writeByte(data.dimension());
        out.writeInt(data.x());
        out.writeInt(data.y());
        out.writeInt(data.z());
        out.writeUTF(data.type());
        out.writeInt(data.payload().length);
        out.write(data.payload());
        out.close();
        return buf.toByteArray();
    }

    public static byte[] encodeRespawn() throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(buf);
        out.writeByte(OP_RESPAWN);
        out.close();
        return buf.toByteArray();
    }

    public static byte[] encodeMobAttack(MobAttack attack) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(buf);
        out.writeByte(OP_MOB_ATTACK);
        out.writeInt(attack.mobId());
        out.writeFloat(attack.damage());
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
        out.writeByte(joined.dimension());
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
        out.writeByte(state.dimension());
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
        out.writeByte(change.dimension());
        out.writeInt(change.x());
        out.writeInt(change.y());
        out.writeInt(change.z());
        out.writeShort(change.blockId());
        out.writeByte(change.orientation());
        out.writeBoolean(change.overlay());
        out.close();
        return buf.toByteArray();
    }

    public static byte[] encodeChunkData(ChunkData data) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(buf);
        out.writeByte(OP_CHUNK_DATA);
        out.writeByte(data.dimension());
        out.writeInt(data.cx());
        out.writeInt(data.cz());
        // Block and overlay ids are shorts on the wire (2 bytes each, little
        // work here - DataOutputStream is big-endian like every other field).
        out.writeInt(data.blocks().length);
        for (short id : data.blocks()) out.writeShort(id);
        out.writeInt(data.overlays().length);
        for (short id : data.overlays()) out.writeShort(id);
        out.writeInt(data.orientations().length);
        out.write(data.orientations());
        out.close();
        return buf.toByteArray();
    }

    public static byte[] encodeChunkAck(byte dimension, int cx, int cz) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(buf);
        out.writeByte(OP_CHUNK_ACK);
        out.writeByte(dimension);
        out.writeInt(cx);
        out.writeInt(cz);
        out.close();
        return buf.toByteArray();
    }

    public static byte[] encodeDimensionChange(DimensionChange change) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(buf);
        out.writeByte(OP_DIMENSION_CHANGE);
        out.writeByte(change.dimension());
        out.writeFloat(change.x());
        out.writeFloat(change.y());
        out.writeFloat(change.z());
        out.close();
        return buf.toByteArray();
    }

    public static byte[] encodeTimeSync(TimeSync sync) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(buf);
        out.writeByte(OP_TIME_SYNC);
        out.writeFloat(sync.timeOfDay());
        out.writeInt(sync.dayIndex());
        out.close();
        return buf.toByteArray();
    }

    public static byte[] encodePlayerDeath(PlayerDeath death) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(buf);
        out.writeByte(OP_PLAYER_DEATH);
        out.writeInt(death.id());
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

    public static byte[] encodeMobSpawn(MobSpawn spawn) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(buf);
        out.writeByte(OP_MOB_SPAWN);
        out.writeInt(spawn.mobId());
        out.writeByte(spawn.typeId());
        out.writeFloat(spawn.x());
        out.writeFloat(spawn.y());
        out.writeFloat(spawn.z());
        out.writeFloat(spawn.yaw());
        out.close();
        return buf.toByteArray();
    }

    public static byte[] encodeMobState(MobState state) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(buf);
        out.writeByte(OP_MOB_STATE);
        out.writeInt(state.mobId());
        out.writeFloat(state.x());
        out.writeFloat(state.y());
        out.writeFloat(state.z());
        out.writeFloat(state.yaw());
        out.writeFloat(state.health());
        out.close();
        return buf.toByteArray();
    }

    public static byte[] encodeMobRemove(MobRemove remove) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(buf);
        out.writeByte(OP_MOB_REMOVE);
        out.writeInt(remove.mobId());
        out.writeByte(remove.typeId());
        out.writeFloat(remove.x());
        out.writeFloat(remove.y());
        out.writeFloat(remove.z());
        out.close();
        return buf.toByteArray();
    }

    public static byte[] encodePlayerDamage(PlayerDamage damage) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(buf);
        out.writeByte(OP_PLAYER_DAMAGE);
        out.writeFloat(damage.amount());
        out.close();
        return buf.toByteArray();
    }

    // ---------------------------------------------------------------
    // Decoders (return null for unsupported opcodes)
    // ---------------------------------------------------------------

    /** A chunk carries exactly SIZE x HEIGHT x SIZE cells per array. */
    private static final int CHUNK_ARRAY_LENGTH =
            com.minecraftclone.world.Chunk.SIZE * com.minecraftclone.world.Chunk.HEIGHT
                    * com.minecraftclone.world.Chunk.SIZE;

    /** Chunk arrays must be exactly chunk-sized; anything else is a protocol error. */
    private static void requireChunkArrayLength(int length) throws IOException {
        if (length != CHUNK_ARRAY_LENGTH) {
            throw new IOException("Bad chunk array length: " + length);
        }
    }

    /** Reads the opcode from a payload and decodes the rest into the matching record. */
    public static Object decode(byte[] payload) throws IOException {
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(payload));
        byte op = in.readByte();
        return switch (op) {
            case OP_JOIN -> new Join(in.readUTF());
            case OP_MOVE -> new Move(in.readFloat(), in.readFloat(), in.readFloat(), in.readFloat(), in.readFloat(),
                    in.readBoolean(), in.readBoolean(), in.readBoolean());
            case OP_PLACE_BLOCK -> new PlaceBlock(in.readByte(), in.readInt(), in.readInt(), in.readInt(), in.readShort(), in.readByte(), in.readBoolean());
            case OP_BREAK_BLOCK -> new BreakBlock(in.readByte(), in.readInt(), in.readInt(), in.readInt(), in.readBoolean());
            case OP_CHAT -> new Chat(in.readUTF());
            case OP_CHUNK_REQUEST -> new ChunkRequest(in.readByte(), in.readInt(), in.readInt());
            case OP_MOB_ATTACK -> new MobAttack(in.readInt(), in.readFloat());
            case OP_PORTAL_USE -> new PortalUse(in.readByte(), in.readShort());
            case OP_CONTAINER_OPEN -> new ContainerOpen(in.readByte(), in.readInt(), in.readInt(), in.readInt());
            case OP_CASTING_OPERATION -> new CastingOperation(in.readByte(), in.readInt(), in.readInt(), in.readInt(),
                    in.readByte(), in.readShort(), in.readByte(), in.readInt());
            case OP_CONTAINER_DATA -> {
                byte dim = in.readByte();
                int x = in.readInt(), y = in.readInt(), z = in.readInt();
                String type = in.readUTF();
                int len = in.readInt();
                // Payload length comes off the wire: bound it before allocating.
                if (len < 0 || len > MAX_CONTAINER_PAYLOAD) {
                    throw new IOException("Bad container payload length: " + len);
                }
                byte[] containerPayload = new byte[len];
                in.readFully(containerPayload);
                yield new ContainerData(dim, x, y, z, type, containerPayload);
            }
            case OP_ITEM_ADD -> new ItemAdd(in.readInt(), in.readByte(), in.readFloat(), in.readFloat(),
                    in.readFloat(), in.readShort(), in.readUnsignedByte());
            case OP_ITEM_REMOVE -> new ItemRemove(in.readInt());
            case OP_ITEM_PICKUP -> new ItemPickup(in.readInt());
            case OP_ITEM_GIVE -> new ItemGive(in.readInt(), in.readShort(), in.readUnsignedByte());
            case OP_ITEM_SPAWN -> new ItemSpawn(in.readByte(), in.readFloat(), in.readFloat(),
                    in.readFloat(), in.readShort(), in.readUnsignedByte());
            case OP_PLAYER_SYNC -> new PlayerSync(in.readUTF());
            case OP_PLAYER_RESTORE -> new PlayerRestore(in.readUTF());
            case OP_PLAYER_ATTACK -> new PlayerAttack(in.readInt(), in.readFloat());
            case OP_SLEEP_VOTE -> new SleepVote();
            case OP_SLEEP_STATE -> new SleepState(in.readUnsignedByte(), in.readUnsignedByte());
            case OP_RESPAWN -> new Respawn();
            case OP_READY -> new Ready();
            case OP_WELCOME -> new Welcome(in.readInt(), in.readLong(), in.readInt(), in.readBoolean(),
                    in.readInt(), in.readInt(), in.readInt(), in.readFloat(), in.readFloat(), in.readFloat());
            case OP_REJECT -> new Reject(in.readUTF());
            case OP_PLAYER_JOINED -> new PlayerJoined(in.readInt(), in.readUTF(), in.readByte(), in.readFloat(), in.readFloat(),
                    in.readFloat(), in.readFloat(), in.readFloat());
            case OP_PLAYER_LEFT -> new PlayerLeft(in.readInt());
            case OP_PLAYER_STATE -> new PlayerState(in.readInt(), in.readByte(), in.readFloat(), in.readFloat(), in.readFloat(),
                    in.readFloat(), in.readFloat(), in.readBoolean(), in.readBoolean(), in.readBoolean());
            case OP_BLOCK_CHANGE -> new BlockChange(in.readByte(), in.readInt(), in.readInt(), in.readInt(), in.readShort(), in.readByte(), in.readBoolean());
            case OP_CHUNK_DATA -> {
                byte dim = in.readByte();
                int cx = in.readInt(), cz = in.readInt();
                // Array lengths come straight off the wire: clamp them to the
                // exact chunk volume before allocating, so a corrupt/hostile
                // frame can't trigger a huge allocation or a negative-size
                // exception here.
                int blockLen = in.readInt();
                requireChunkArrayLength(blockLen);
                short[] blocks = new short[blockLen];
                for (int i = 0; i < blockLen; i++) blocks[i] = in.readShort();
                int overlayLen = in.readInt();
                requireChunkArrayLength(overlayLen);
                short[] overlays = new short[overlayLen];
                for (int i = 0; i < overlayLen; i++) overlays[i] = in.readShort();
                int orientLen = in.readInt();
                requireChunkArrayLength(orientLen);
                byte[] orientations = new byte[orientLen];
                in.readFully(orientations);
                yield new ChunkData(dim, cx, cz, blocks, overlays, orientations);
            }
            case OP_CHUNK_ACK -> new ChunkAck(in.readByte(), in.readInt(), in.readInt());
            case OP_CHAT_MSG -> new ChatMsg(in.readInt(), in.readUTF(), in.readUTF());
            case OP_MOB_SPAWN -> new MobSpawn(in.readInt(), in.readByte(), in.readFloat(), in.readFloat(), in.readFloat(), in.readFloat());
            case OP_MOB_STATE -> new MobState(in.readInt(), in.readFloat(), in.readFloat(), in.readFloat(), in.readFloat(), in.readFloat());
            case OP_MOB_REMOVE -> new MobRemove(in.readInt(), in.readByte(), in.readFloat(), in.readFloat(), in.readFloat());
            case OP_PLAYER_DAMAGE -> new PlayerDamage(in.readFloat());
            case OP_DIMENSION_CHANGE -> new DimensionChange(in.readByte(), in.readFloat(), in.readFloat(), in.readFloat());
            case OP_TIME_SYNC -> new TimeSync(in.readFloat(), in.readInt());
            case OP_PLAYER_DEATH -> new PlayerDeath(in.readInt());
            default -> throw new IOException("Unknown opcode: " + op);
        };
    }

    public record Reject(String reason) {
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
