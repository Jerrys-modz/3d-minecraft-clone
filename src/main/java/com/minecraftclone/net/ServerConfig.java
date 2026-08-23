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

    // All fields are volatile: the server's accept/tick threads read them
    // while operators (and tests) mutate the instance from other threads.
    private volatile int port = 25565;
    private volatile int maxPlayers = 12;
    private volatile boolean pvp = true;
    private volatile String motd = "";

    /**
     * Retrieves the configured server port.
     *
     * @return the server port
     */
    public int getPort() {
        return port;
    }

    /**
     * Sets the server port when it is within the valid port range.
     *
     * @param port the server port, from 0 through 65535; 0 allows the operating system to select an ephemeral port
     */
    public void setPort(int port) {
        // 0 = let the OS pick an ephemeral port (used by tests).
        if (port >= 0 && port <= 65535) this.port = port;
    }

    /**
     * Retrieves the maximum number of players allowed on the server.
     *
     * @return the configured maximum player count, with a minimum of 1
     */
    public int getMaxPlayers() {
        return Math.max(1, maxPlayers);
    }

    /**
     * Sets the maximum number of players allowed on the server.
     *
     * @param maxPlayers the maximum player count, from 1 through 100
     */
    public void setMaxPlayers(int maxPlayers) {
        if (maxPlayers >= 1 && maxPlayers <= 100) this.maxPlayers = maxPlayers;
    }

    /** Whether players can damage each other (mob attacks are unaffected). */
    public boolean isPvpEnabled() {
        return pvp;
    }

    /**
     * Sets whether player-versus-player combat is enabled.
     *
     * @param pvp {@code true} to enable player-versus-player combat; {@code false} to disable it
     */
    public void setPvp(boolean pvp) {
        this.pvp = pvp;
    }

    /** Shown to every joining player as a chat line from "Server" (may be empty). */
    public String getMotd() {
        return motd == null ? "" : motd;
    }

    /**
     * Sets the server's message of the day.
     *
     * @param motd the message, with newline characters replaced by spaces and surrounding whitespace removed
     */
    public void setMotd(String motd) {
        // One line only - newlines would break the properties format.
        this.motd = motd == null ? "" : motd.replace("\n", " ").replace("\r", " ").trim();
    }

    /**
     * Loads server settings from a UTF-8 properties file.
     *
     * @param file the configuration file to load
     * @return the loaded configuration, with defaults retained for missing or invalid settings
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

    /**
     * Parses a boolean value using case-insensitive {@code true} and {@code false} literals.
     *
     * @param val      the value to parse
     * @param fallback the value to return when {@code val} is not a recognized boolean literal
     * @return {@code true} or {@code false} for a recognized literal; otherwise, {@code fallback}
     */
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

    /**
     * Writes the current server configuration to a UTF-8 properties file and creates its parent directories.
     * The generated file includes comments describing each setting.
     *
     * @param file the path of the configuration file to write
     */
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
