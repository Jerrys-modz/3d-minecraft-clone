package com.minecraftclone.net;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * The multiplayer client half of the wire protocol: connects to a
 * {@link GameServer}, sends the player's intents (join, move, place, break,
 * chat) and hands any server packets it receives back to the caller.
 * <p>
 * A single background reader thread decodes incoming packets into a
 * thread-safe queue, which the main loop drains once per frame - the first
 * genuinely-shared mutable state in the project, so it stays a plain
 * {@link ConcurrentLinkedQueue} of decoded records. All sends happen from the
 * main thread (synchronized on the socket's output stream), except chunk
 * requests, which fire from inside World's generation loop and are queued for
 * a small writer thread instead (a blocking write + flush mid-generation
 * would stall chunk streaming).
 */
public class NetClient implements AutoCloseable {

    private final Socket socket;
    private final DataOutputStream out;
    private final ConcurrentLinkedQueue<Object> incoming = new ConcurrentLinkedQueue<>();
    /** Outgoing chunk requests, drained by the writer thread off the hot path. */
    private final LinkedBlockingQueue<byte[]> outbound = new LinkedBlockingQueue<>();
    private static final int MAX_OUTBOUND_QUEUE = 4096;
    private volatile boolean disconnected;
    private volatile String disconnectReason;
    private Thread readerThread;
    private Thread writerThread;

    /** Connects (blocking, with a short timeout) but does not send anything yet. */
    public NetClient(String host, int port) throws IOException {
        this.socket = new Socket();
        socket.connect(new InetSocketAddress(host, port), 3000);
        socket.setTcpNoDelay(true);
        this.out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
        readerThread = new Thread(this::readLoop, "net-client-reader");
        readerThread.setDaemon(true);
        readerThread.start();
        writerThread = new Thread(this::writeLoop, "net-client-writer");
        writerThread.setDaemon(true);
        writerThread.start();
    }

    private void readLoop() {
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(socket.getInputStream()))) {
            while (!disconnected) {
                byte[] frame = Packets.readFrame(in);
                if (frame == null) break;
                incoming.add(Packets.decode(frame));
            }
        } catch (EOFException | SocketException e) {
            disconnectReason = "Connection closed by server.";
        } catch (IOException e) {
            disconnectReason = e.getMessage();
        } finally {
            disconnected = true;
            if (disconnectReason == null) disconnectReason = "Connection lost.";
        }
    }

    private void writeLoop() {
        try {
            while (!disconnected) {
                byte[] payload = outbound.poll(500, TimeUnit.MILLISECONDS);
                if (payload == null) continue;
                synchronized (out) {
                    Packets.writeFrame(out, payload);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (IOException e) {
            disconnected = true;
            if (disconnectReason == null) disconnectReason = e.getMessage();
        }
    }

    // ---------------------------------------------------------------
    // Sends (main thread)
    // ---------------------------------------------------------------

    public void sendJoin(String name) throws IOException {
        send(Packets.encodeJoin(name));
    }

    public void sendReady() throws IOException {
        send(Packets.encodeReady());
    }

    public void sendMove(Packets.Move move) throws IOException {
        send(Packets.encodeMove(move));
    }

    public void sendPlaceBlock(Packets.PlaceBlock place) throws IOException {
        send(Packets.encodePlaceBlock(place));
    }

    public void sendBreakBlock(Packets.BreakBlock brk) throws IOException {
        send(Packets.encodeBreakBlock(brk));
    }

    public void sendChat(String text) throws IOException {
        send(Packets.encodeChat(text));
    }

    public void sendChunkRequest(byte dimension, int cx, int cz) throws IOException {
        send(Packets.encodeChunkRequest(dimension, cx, cz));
    }

    /**
     * Queues a chunk request without touching the socket on the caller's
     * thread. World fires this from inside its generation loop for every newly
     * generated chunk; a blocking write (and flush) there would stall
     * streaming, so the writer thread sends these instead. Bounded so a
     * pathological burst can't grow memory without limit.
     */
    public void sendChunkRequestAsync(byte dimension, int cx, int cz) {
        if (disconnected) return;
        try {
            byte[] payload = Packets.encodeChunkRequest(dimension, cx, cz);
            if (outbound.size() < MAX_OUTBOUND_QUEUE) {
                outbound.add(payload);
            }
        } catch (IOException ignored) {
            // Encoding a fixed-size record cannot realistically fail; drop it.
        }
    }

    public void sendMobAttack(int mobId, float damage) throws IOException {
        send(Packets.encodeMobAttack(new Packets.MobAttack(mobId, damage)));
    }

    public void sendPortalUse(byte dimension, short blockId) throws IOException {
        send(Packets.encodePortalUse(dimension, blockId));
    }

    public void sendRespawn() throws IOException {
        send(Packets.encodeRespawn());
    }

    public void sendContainerOpen(byte dimension, int x, int y, int z) throws IOException {
        send(Packets.encodeContainerOpen(dimension, x, y, z));
    }

    public void sendContainerData(Packets.ContainerData data) throws IOException {
        send(Packets.encodeContainerData(data));
    }

    public void sendCastingOperation(Packets.CastingOperation operation) throws IOException {
        send(Packets.encodeCastingOperation(operation));
    }

    public void sendItemSpawn(Packets.ItemSpawn spawn) throws IOException {
        send(Packets.encodeItemSpawn(spawn));
    }

    public void sendItemPickup(int id) throws IOException {
        send(Packets.encodeItemPickup(id));
    }

    public void sendPlayerSync(String data) throws IOException {
        send(Packets.encodePlayerSync(data));
    }

    public void sendPlayerAttack(int targetId, float damage) throws IOException {
        send(Packets.encodePlayerAttack(new Packets.PlayerAttack(targetId, damage)));
    }

    public void sendSleepVote() throws IOException {
        send(Packets.opcodeOnly(Packets.OP_SLEEP_VOTE));
    }

    private void send(byte[] payload) throws IOException {
        if (disconnected) throw new IOException("Not connected.");
        synchronized (out) {
            Packets.writeFrame(out, payload);
        }
    }

    // ---------------------------------------------------------------
    // Receives (main thread, once per frame)
    // ---------------------------------------------------------------

    /** Returns the next decoded server packet, or null if none arrived since the last drain. */
    public Object poll() {
        return incoming.poll();
    }

    public boolean isConnected() {
        return !disconnected;
    }

    public boolean isDisconnected() {
        return disconnected;
    }

    public String getDisconnectReason() {
        return disconnectReason;
    }

    @Override
    public void close() {
        disconnected = true;
        try {
            socket.close();
        } catch (IOException ignored) {
        }
        for (Thread t : new Thread[]{readerThread, writerThread}) {
            if (t != null) {
                try {
                    t.join(500);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }
}
