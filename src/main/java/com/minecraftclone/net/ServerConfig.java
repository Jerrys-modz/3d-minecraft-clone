package com.minecraftclone.net;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/**
 * The dedicated server's configuration, persisted as {@code server.properties}
 * in its save dir. A missing file is written with defaults on first start, so
 * operators always have a template to edit: port, player cap, whether PvP
 * damage is allowed, and the message-of-the-day shown on join.
 * <p>
 * Deliberately hand-parsed key=value lines (one per setting) - the same
 * no-library convention as the rest of the game's persistence.
 */
public final class ServerConfig {

    public static final String FILE_NAME = "server.properties";

    private int port = 25565;
    private int maxPlayers = 12;
    private boolean pvp = true;
    private String motd = "";

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        // 0 = let the OS pick an ephemeral port (used by tests).
        if (port >= 0 && port <= 65535) this.port = port;
    }

    public int getMaxPlayers() {
        return Math.max(1, maxPlayers);
    }

    public void setMaxPlayers(int maxPlayers) {
        if (maxPlayers >= 1 && maxPlayers <= 100) this.maxPlayers = maxPlayers;
    }

    /** Whether players can damage each other (mob attacks are unaffected). */
    public boolean isPvpEnabled() {
        return pvp;
    }

    public void setPvp(boolean pvp) {
        this.pvp = pvp;
    }

    /** Shown to every joining player as a chat line from "Server" (may be empty). */
    public String getMotd() {
        return motd == null ? "" : motd;
    }

    public void setMotd(String motd) {
        // One line only - newlines would break the properties format.
        this.motd = motd == null ? "" : motd.replace("\n", " ").replace("\r", " ").trim();
    }

    /**
     * Loads a config from {@code file}, falling back to defaults for any
     * missing or malformed key. Never fails: an unreadable file just means
     * defaults.
     */
    public static ServerConfig load(Path file) {
        ServerConfig cfg = new ServerConfig();
        if (!Files.isRegularFile(file)) return cfg;
        try {
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
                int eq = trimmed.indexOf('=');
                if (eq <= 0) continue;
                String key = trimmed.substring(0, eq).trim().toLowerCase(Locale.ROOT);
                String val = trimmed.substring(eq + 1).trim();
                switch (key) {
                    case "port" -> parsePort(cfg, val);
                    case "max-players", "maxplayers" -> parseInt(cfg, val);
                    case "pvp" -> cfg.pvp = parseBool(val, cfg.pvp);
                    case "motd" -> cfg.setMotd(val);
                    default -> { /* unknown keys are ignored, not errors */ }
                }
            }
        } catch (IOException ignored) {
            // Unreadable config = defaults; the server still starts.
        }
        return cfg;
    }

    /** Strict boolean: only true/false count; anything else keeps the fallback. */
    private static boolean parseBool(String val, boolean fallback) {
        if (val.equalsIgnoreCase("true")) return true;
        if (val.equalsIgnoreCase("false")) return false;
        return fallback;
    }

    private static void parsePort(ServerConfig cfg, String val) {
        try {
            cfg.setPort(Integer.parseInt(val));
        } catch (NumberFormatException ignored) {
        }
    }

    private static void parseInt(ServerConfig cfg, String val) {
        try {
            cfg.setMaxPlayers(Integer.parseInt(val));
        } catch (NumberFormatException ignored) {
        }
    }

    /** Writes this config to {@code file} (creating parent dirs), so first-time operators have a template. */
    public void save(Path file) {
        List<String> lines = List.of(
                "# 3D Minecraft Clone dedicated server configuration",
                "# port: the TCP port clients connect on",
                "port=" + port,
                "# max-players: how many players may be joined at once",
                "max-players=" + maxPlayers,
                "# pvp: whether players can damage each other",
                "pvp=" + pvp,
                "# motd: message-of-the-day shown in chat when someone joins",
                "motd=" + motd);
        try {
            Files.createDirectories(file.getParent());
            Files.write(file, lines, StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.err.println("Could not write " + FILE_NAME + ": " + e.getMessage());
        }
    }
}
