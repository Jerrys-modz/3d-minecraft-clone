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

/**
 * The multiplayer client half of the wire protocol: connects to a
 * {@link GameServer}, sends the player's intents (join, move, place, break,
 * chat) and hands any server packets it receives back to the caller.
 * <p>
 * A single background reader thread decodes incoming packets into a
 * thread-safe queue, which the main loop drains once per frame - the first
 * genuinely-shared mutable state in the project, so it stays a plain
 * {@link ConcurrentLinkedQueue} of decoded records. All sends happen from the
 * main thread (synchronized on the socket's output stream).
 */
public class NetClient implements AutoCloseable {

    private final Socket socket;
    private final DataOutputStream out;
    private final ConcurrentLinkedQueue<Object> incoming = new ConcurrentLinkedQueue<>();
    private volatile boolean disconnected;
    private volatile String disconnectReason;
    private Thread readerThread;

    /** Connects (blocking, with a short timeout) but does not send anything yet. */
    public NetClient(String host, int port) throws IOException {
        this.socket = new Socket();
        socket.connect(new InetSocketAddress(host, port), 3000);
        socket.setTcpNoDelay(true);
        this.out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
        readerThread = new Thread(this::readLoop, "net-client-reader");
        readerThread.setDaemon(true);
        readerThread.start();
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

    public void sendChunkRequest(int cx, int cz) throws IOException {
        send(Packets.encodeChunkRequest(cx, cz));
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
        if (readerThread != null) {
            try {
                readerThread.join(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
