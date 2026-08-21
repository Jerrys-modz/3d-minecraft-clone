package com.minecraftclone.net;

import com.minecraftclone.world.BlockType;
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
        else if (packet instanceof Packets.ChunkRequest p) payload = Packets.encodeChunkRequest(p.dimension(), p.cx(), p.cz());
        else if (packet instanceof Packets.MobAttack p) payload = Packets.encodeMobAttack(p);
        else if (packet instanceof Packets.PortalUse p) payload = Packets.encodePortalUse(p.dimension(), p.blockId());
        else if (packet instanceof Packets.Respawn p) payload = Packets.encodeRespawn();
        else if (packet instanceof Packets.Welcome p) payload = Packets.encodeWelcome(p);
        else if (packet instanceof Packets.PlayerJoined p) payload = Packets.encodePlayerJoined(p);
        else if (packet instanceof Packets.PlayerLeft p) payload = Packets.encodePlayerLeft(p);
        else if (packet instanceof Packets.PlayerState p) payload = Packets.encodePlayerState(p);
        else if (packet instanceof Packets.BlockChange p) payload = Packets.encodeBlockChange(p);
        else if (packet instanceof Packets.ChunkData p) payload = Packets.encodeChunkData(p);
        else if (packet instanceof Packets.ChunkAck p) payload = Packets.encodeChunkAck(p.dimension(), p.cx(), p.cz());
        else if (packet instanceof Packets.ChatMsg p) payload = Packets.encodeChatMsg(p);
        else if (packet instanceof Packets.MobSpawn p) payload = Packets.encodeMobSpawn(p);
        else if (packet instanceof Packets.MobState p) payload = Packets.encodeMobState(p);
        else if (packet instanceof Packets.MobRemove p) payload = Packets.encodeMobRemove(p);
        else if (packet instanceof Packets.PlayerDamage p) payload = Packets.encodePlayerDamage(p);
        else if (packet instanceof Packets.ContainerOpen p) payload = Packets.encodeContainerOpen(p.dimension(), p.x(), p.y(), p.z());
        else if (packet instanceof Packets.ContainerData p) payload = Packets.encodeContainerData(p);
        else if (packet instanceof Packets.ItemAdd p) payload = Packets.encodeItemAdd(p);
        else if (packet instanceof Packets.ItemRemove p) payload = Packets.encodeItemRemove(p.id());
        else if (packet instanceof Packets.ItemPickup p) payload = Packets.encodeItemPickup(p.id());
        else if (packet instanceof Packets.ItemGive p) payload = Packets.encodeItemGive(p);
        else if (packet instanceof Packets.ItemSpawn p) payload = Packets.encodeItemSpawn(p);
        else if (packet instanceof Packets.PlayerSync p) payload = Packets.encodePlayerSync(p.data());
        else if (packet instanceof Packets.PlayerRestore p) payload = Packets.encodePlayerRestore(p.data());
        else if (packet instanceof Packets.DimensionChange p) payload = Packets.encodeDimensionChange(p);
        else if (packet instanceof Packets.TimeSync p) payload = Packets.encodeTimeSync(p);
        else if (packet instanceof Packets.PlayerDeath p) payload = Packets.encodePlayerDeath(p);
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
        Packets.PlaceBlock in = new Packets.PlaceBlock((byte) 0, 10, 64, 20, (byte) 3, (byte) 2, false);
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
        Packets.BreakBlock in = new Packets.BreakBlock((byte) 0, 5, 63, 7, true);
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
        Packets.ChunkRequest in = new Packets.ChunkRequest((byte) 0, 3, -4);
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
        Packets.PlayerJoined in = new Packets.PlayerJoined(2, "Alex", (byte) 0, 1f, 64f, 2f, 45f, 10f);
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
        Packets.PlayerState in = new Packets.PlayerState(3, (byte) 0, 10f, 70f, 20f, -90f, 0f, true, false, false);
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
        Packets.BlockChange in = new Packets.BlockChange((byte) 0, 1, 64, 2, (byte) 4, (byte) 1, false);
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
        short[] blocks = new short[Chunk.SIZE * Chunk.HEIGHT * Chunk.SIZE];
        short[] overlays = new short[blocks.length];
        byte[] orientations = new byte[blocks.length];
        blocks[0] = 3; blocks[100] = 4; blocks[blocks.length - 1] = 900; // >127: ids are shorts now
        overlays[50] = 7;
        orientations[500] = 2;
        Packets.ChunkData in = new Packets.ChunkData((byte) 0, 4, -2, blocks, overlays, orientations);
        Packets.ChunkData out = assertInstanceOf(Packets.ChunkData.class, roundTrip(in));
        assertEquals(4, out.cx());
        assertEquals(-2, out.cz());
        assertArrayEquals(blocks, out.blocks());
        assertArrayEquals(overlays, out.overlays());
        assertArrayEquals(orientations, out.orientations());
    }

    @Test
    void chunkAckRoundTrips() throws Exception {
        Packets.ChunkAck in = new Packets.ChunkAck((byte) 0, 0, 0);
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
    void mobAttackRoundTrips() throws Exception {
        Packets.MobAttack in = new Packets.MobAttack(7, 4f);
        Packets.MobAttack out = assertInstanceOf(Packets.MobAttack.class, roundTrip(in));
        assertEquals(7, out.mobId());
        assertEquals(4f, out.damage());
    }

    @Test
    void mobSpawnRoundTrips() throws Exception {
        Packets.MobSpawn in = new Packets.MobSpawn(3, (byte) 1, 1f, 64f, 2f, 0.5f);
        Packets.MobSpawn out = assertInstanceOf(Packets.MobSpawn.class, roundTrip(in));
        assertEquals(3, out.mobId());
        assertEquals(1, out.typeId());
        assertEquals(1f, out.x());
        assertEquals(64f, out.y());
        assertEquals(2f, out.z());
        assertEquals(0.5f, out.yaw());
    }

    @Test
    void mobStateRoundTrips() throws Exception {
        Packets.MobState in = new Packets.MobState(4, 10f, 70f, 20f, 1.2f, 0f);
        Packets.MobState out = assertInstanceOf(Packets.MobState.class, roundTrip(in));
        assertEquals(4, out.mobId());
        assertEquals(10f, out.x());
        assertEquals(70f, out.y());
        assertEquals(20f, out.z());
        assertEquals(1.2f, out.yaw());
    }

    @Test
    void mobRemoveRoundTrips() throws Exception {
        Packets.MobRemove in = new Packets.MobRemove(2, (byte) 0, 5f, 64f, 5f);
        Packets.MobRemove out = assertInstanceOf(Packets.MobRemove.class, roundTrip(in));
        assertEquals(2, out.mobId());
        assertEquals(0, out.typeId());
        assertEquals(5f, out.x());
        assertEquals(64f, out.y());
        assertEquals(5f, out.z());
    }

    @Test
    void playerDamageRoundTrips() throws Exception {
        Packets.PlayerDamage in = new Packets.PlayerDamage(3.5f);
        Packets.PlayerDamage out = assertInstanceOf(Packets.PlayerDamage.class, roundTrip(in));
        assertEquals(3.5f, out.amount());
    }

    @Test
    void portalUseRoundTrips() throws Exception {
        Packets.PortalUse in = new Packets.PortalUse((byte) 1, BlockType.NETHER_PORTAL.id);
        Packets.PortalUse out = assertInstanceOf(Packets.PortalUse.class, roundTrip(in));
        assertEquals(1, out.dimension());
        assertEquals(BlockType.NETHER_PORTAL.id, out.blockId());
    }

    @Test
    void respawnRoundTrips() throws Exception {
        assertInstanceOf(Packets.Respawn.class, roundTrip(new Packets.Respawn()));
    }

    @Test
    void containerOpenRoundTrips() throws Exception {
        Packets.ContainerOpen in = new Packets.ContainerOpen((byte) 0, 7, -3, 2);
        Packets.ContainerOpen out = assertInstanceOf(Packets.ContainerOpen.class, roundTrip(in));
        assertEquals(0, out.dimension());
        assertEquals(7, out.x());
        assertEquals(-3, out.y());
        assertEquals(2, out.z());
    }

    @Test
    void containerDataRoundTripsAndRejectsBadLength() throws Exception {
        byte[] payload = {1, 2, 3, 4, 5};
        Packets.ContainerData in = new Packets.ContainerData((byte) 1, -9, 40, 12, "furnace", payload);
        Packets.ContainerData out = assertInstanceOf(Packets.ContainerData.class, roundTrip(in));
        assertEquals(1, out.dimension());
        assertEquals(-9, out.x());
        assertEquals(40, out.y());
        assertEquals(12, out.z());
        assertEquals("furnace", out.type());
        assertArrayEquals(payload, out.payload());
    }

    @Test
    void itemPacketsRoundTrip() throws Exception {
        Packets.ItemAdd add = assertInstanceOf(Packets.ItemAdd.class,
                roundTrip(new Packets.ItemAdd(42, (byte) 1, 1.5f, 64f, -3.25f, BlockType.STONE.id, 7)));
        assertEquals(42, add.id());
        assertEquals(1, add.dimension());
        assertEquals(1.5f, add.x());
        assertEquals(64f, add.y());
        assertEquals(-3.25f, add.z());
        assertEquals(BlockType.STONE.id, add.blockId());
        assertEquals(7, add.count());

        assertEquals(42, assertInstanceOf(Packets.ItemRemove.class, roundTrip(new Packets.ItemRemove(42))).id());
        assertEquals(42, assertInstanceOf(Packets.ItemPickup.class, roundTrip(new Packets.ItemPickup(42))).id());

        Packets.ItemGive give = assertInstanceOf(Packets.ItemGive.class,
                roundTrip(new Packets.ItemGive(42, BlockType.COAL.id, 3)));
        assertEquals(BlockType.COAL.id, give.blockId());
        assertEquals(3, give.count());

        Packets.ItemSpawn spawn = assertInstanceOf(Packets.ItemSpawn.class,
                roundTrip(new Packets.ItemSpawn((byte) 0, 0.5f, 40f, 2.5f, BlockType.DIRT.id, 1)));
        assertEquals(BlockType.DIRT.id, spawn.blockId());
        assertEquals(1, spawn.count());

        Packets.PlayerSync sync = assertInstanceOf(Packets.PlayerSync.class,
                roundTrip(new Packets.PlayerSync("pos_x=1\nhealth=20")));
        assertEquals("pos_x=1\nhealth=20", sync.data());
        Packets.PlayerRestore restore = assertInstanceOf(Packets.PlayerRestore.class,
                roundTrip(new Packets.PlayerRestore(sync.data())));
        assertEquals(sync.data(), restore.data());
    }

    @Test
    void dimensionChangeRoundTrips() throws Exception {
        Packets.DimensionChange in = new Packets.DimensionChange((byte) 1, 1f, 64f, 2f);
        Packets.DimensionChange out = assertInstanceOf(Packets.DimensionChange.class, roundTrip(in));
        assertEquals(1, out.dimension());
        assertEquals(1f, out.x());
        assertEquals(64f, out.y());
        assertEquals(2f, out.z());
    }

    @Test
    void timeSyncRoundTrips() throws Exception {
        Packets.TimeSync in = new Packets.TimeSync(0.5f, 3);
        Packets.TimeSync out = assertInstanceOf(Packets.TimeSync.class, roundTrip(in));
        assertEquals(0.5f, out.timeOfDay());
        assertEquals(3, out.dayIndex());
    }

    @Test
    void playerDeathRoundTrips() throws Exception {
        Packets.PlayerDeath in = new Packets.PlayerDeath(4);
        Packets.PlayerDeath out = assertInstanceOf(Packets.PlayerDeath.class, roundTrip(in));
        assertEquals(4, out.id());
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
