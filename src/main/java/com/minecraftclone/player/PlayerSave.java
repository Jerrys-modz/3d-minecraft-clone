package com.minecraftclone.player;

import com.minecraftclone.world.BlockType;
import com.minecraftclone.world.DimensionType;
import com.minecraftclone.world.Mining;
import com.minecraftclone.world.tinkers.TinkersItem;
import com.minecraftclone.world.tinkers.ToolPartType;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Snapshot of the player written next to {@code world.txt} as {@code player.txt}.
 * Reloading a world restores position, look, dimension, inventory, stats and
 * the bed spawn instead of dropping you back at world spawn with empty pockets.
 */
public final class PlayerSave {

    public static final String FILE_NAME = "player.txt";

    private float x, y, z;
    private float yaw = -90f;
    private float pitch;
    private DimensionType dimension = DimensionType.OVERWORLD;
    private int selectedSlot;
    private boolean flying;
    private boolean spawnSet;
    private int spawnX, spawnY, spawnZ;
    private float health = PlayerStats.MAX_HEALTH;
    private float hunger = PlayerStats.MAX_HUNGER;
    private float thirst = PlayerStats.MAX_THIRST;
    private float stamina = PlayerStats.MAX_STAMINA;
    private final ItemStack[] slots = new ItemStack[Inventory.SIZE];
    private final BlockType[] armor = new BlockType[Inventory.ARMOR_SLOT_COUNT];
    private final Map<BlockType, Integer> durability = new EnumMap<>(BlockType.class);

    public float x() { return x; }
    public float y() { return y; }
    public float z() { return z; }
    public DimensionType dimension() { return dimension; }
    public int selectedSlot() { return selectedSlot; }
    public boolean hasSpawnPoint() { return spawnSet; }
    public int spawnX() { return spawnX; }
    public int spawnY() { return spawnY; }
    public int spawnZ() { return spawnZ; }

    public static PlayerSave capture(Player player, DimensionType dim, int selectedSlot) {
        PlayerSave s = new PlayerSave();
        var pos = player.getPosition();
        s.x = pos.x;
        s.y = pos.y;
        s.z = pos.z;
        s.yaw = player.getCamera().getYaw();
        s.pitch = player.getCamera().getPitch();
        s.dimension = dim == null ? DimensionType.OVERWORLD : dim;
        s.selectedSlot = Math.floorMod(selectedSlot, Inventory.HOTBAR_SIZE);
        s.flying = player.isFlying();
        s.spawnSet = player.hasSpawnPoint();
        s.spawnX = player.spawnX();
        s.spawnY = player.spawnY();
        s.spawnZ = player.spawnZ();
        PlayerStats st = player.getStats();
        s.health = st.getHealth();
        s.hunger = st.getHunger();
        s.thirst = st.getThirst();
        s.stamina = st.getStamina();
        Inventory inv = player.getInventory();
        for (int i = 0; i < Inventory.SIZE; i++) {
            s.slots[i] = inv.stackOf(i);
        }
        for (int i = 0; i < Inventory.ARMOR_SLOT_COUNT; i++) {
            s.armor[i] = inv.armorType(i);
        }
        s.durability.putAll(player.getDurability().snapshot());
        return s;
    }

    /**
     * Pushes this snapshot onto {@code player}. Game mode stays whatever
     * {@code world.txt} already applied. Returns the saved hotbar slot.
     */
    public int applyTo(Player player) {
        player.teleportTo(x, y, z);
        player.getCamera().setYaw(yaw);
        player.getCamera().setPitch(pitch);
        player.setFlying(flying);
        if (spawnSet) {
            player.setSpawnPoint(spawnX, spawnY, spawnZ);
        } else {
            player.clearSpawnPoint();
        }
        player.getStats().restore(health, hunger, thirst, stamina);
        Inventory inv = player.getInventory();
        inv.clear();
        inv.clearArmor();
        for (int i = 0; i < Inventory.SIZE; i++) {
            ItemStack stack = slots[i];
            if (stack != null && !stack.isEmpty()) {
                inv.setStack(i, stack);
            }
        }
        for (int i = 0; i < Inventory.ARMOR_SLOT_COUNT; i++) {
            if (armor[i] != null) {
                try {
                    inv.setArmor(i, armor[i]);
                } catch (IllegalArgumentException ignored) {
                    // Saved a piece in the wrong slot (or a removed armor type).
                }
            }
        }
        player.getDurability().restore(durability);
        return Math.floorMod(selectedSlot, Inventory.HOTBAR_SIZE);
    }

    public void save(Path worldDir) {
        List<String> lines = toLines();
        try {
            Files.createDirectories(worldDir);
            Files.write(worldDir.resolve(FILE_NAME), lines, StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.err.println("Could not save player to " + worldDir + ": " + e.getMessage());
        }
    }

    /**
     * Encodes this snapshot as the {@code key=value} lines normally written to
     * {@code player.txt} - shared between disk saves and the multiplayer
     * player-sync packet so both carry exactly the same state.
     */
    public List<String> toLines() {
        return buildLines();
    }

    /** Rebuilds a snapshot from {@link #toLines()} output, or null if it has no position. */
    public static PlayerSave fromLines(List<String> lines) {
        PlayerSave s = new PlayerSave();
        if (!s.readLines(lines)) return null;
        return s;
    }

    private List<String> buildLines() {
        List<String> lines = new ArrayList<>();
        lines.add("pos_x=" + fmt(x));
        lines.add("pos_y=" + fmt(y));
        lines.add("pos_z=" + fmt(z));
        lines.add("yaw=" + fmt(yaw));
        lines.add("pitch=" + fmt(pitch));
        lines.add("dim=" + dimension.name());
        lines.add("selected=" + selectedSlot);
        lines.add("flying=" + flying);
        lines.add("spawn_set=" + spawnSet);
        if (spawnSet) {
            lines.add("spawn_x=" + spawnX);
            lines.add("spawn_y=" + spawnY);
            lines.add("spawn_z=" + spawnZ);
        }
        lines.add("health=" + fmt(health));
        lines.add("hunger=" + fmt(hunger));
        lines.add("thirst=" + fmt(thirst));
        lines.add("stamina=" + fmt(stamina));
        for (int i = 0; i < Inventory.SIZE; i++) {
            String encoded = encodeStack(slots[i]);
            if (encoded != null) lines.add("slot." + i + "=" + encoded);
        }
        for (int i = 0; i < Inventory.ARMOR_SLOT_COUNT; i++) {
            if (armor[i] != null) lines.add("armor." + i + "=" + armor[i].name());
        }
        for (var e : durability.entrySet()) {
            lines.add("dur." + e.getKey().name() + "=" + e.getValue());
        }
        return lines;
    }

    /**
     * Loads {@code player.txt} from {@code worldDir}, or {@code null} if the
     * file is missing or has no position (older worlds / empty file).
     */
    public static PlayerSave load(Path worldDir) {
        Path file = worldDir.resolve(FILE_NAME);
        if (!Files.isRegularFile(file)) return null;
        List<String> lines;
        try {
            lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return null;
        }
        PlayerSave s = new PlayerSave();
        return s.readLines(lines) ? s : null;
    }

    /** Parses {@code key=value} lines into this snapshot; returns false when no position was present. */
    private boolean readLines(List<String> lines) {
        boolean hasPos = false;
        for (String line : lines) {
            int eq = line.indexOf('=');
            if (eq <= 0) continue;
            String key = line.substring(0, eq).trim();
            String val = line.substring(eq + 1).trim();
            switch (key) {
                case "pos_x" -> { x = parseFloat(val, 0f); hasPos = true; }
                case "pos_y" -> y = parseFloat(val, 0f);
                case "pos_z" -> z = parseFloat(val, 0f);
                case "yaw" -> yaw = parseFloat(val, -90f);
                case "pitch" -> pitch = parseFloat(val, 0f);
                case "dim" -> dimension = parseDim(val);
                case "selected" -> selectedSlot = parseInt(val, 0);
                case "flying" -> flying = Boolean.parseBoolean(val);
                case "spawn_set" -> spawnSet = Boolean.parseBoolean(val);
                case "spawn_x" -> spawnX = parseInt(val, 0);
                case "spawn_y" -> spawnY = parseInt(val, 0);
                case "spawn_z" -> spawnZ = parseInt(val, 0);
                case "health" -> health = parseFloat(val, PlayerStats.MAX_HEALTH);
                case "hunger" -> hunger = parseFloat(val, PlayerStats.MAX_HUNGER);
                case "thirst" -> thirst = parseFloat(val, PlayerStats.MAX_THIRST);
                case "stamina" -> stamina = parseFloat(val, PlayerStats.MAX_STAMINA);
                default -> {
                    if (key.startsWith("slot.")) {
                        int i = parseInt(key.substring(5), -1);
                        if (i >= 0 && i < Inventory.SIZE) slots[i] = decodeStack(val);
                    } else if (key.startsWith("armor.")) {
                        int i = parseInt(key.substring(6), -1);
                        if (i >= 0 && i < Inventory.ARMOR_SLOT_COUNT) armor[i] = parseBlock(val);
                    } else if (key.startsWith("dur.")) {
                        BlockType t = parseBlock(key.substring(4));
                        if (t != null) durability.put(t, parseInt(val, 0));
                    }
                }
            }
        }
        return hasPos;
    }

    static String encodeStack(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        if (stack.isTinkersPart()) {
            TinkersItem.Part p = stack.tinkersPart();
            if (p == null) return null;
            return "TPART," + p.shape.name() + "," + p.material.name();
        }
        if (stack.isTinkersTool()) {
            TinkersItem.Tool t = stack.tinkersTool();
            if (t == null) return null;
            StringBuilder layers = new StringBuilder();
            for (TinkersItem.ToolLayer layer : t.layers) {
                if (layers.length() > 0) layers.append('+');
                layers.append(layer.shape().name()).append('/').append(layer.material().name());
            }
            return "TTOOL," + t.kind.name() + "," + t.remaining() + "," + layers;
        }
        return stack.type().name() + "," + stack.count();
    }

    static ItemStack decodeStack(String val) {
        if (val == null || val.isBlank()) return ItemStack.EMPTY;
        String[] parts = val.split(",", -1);
        if (parts.length == 0) return ItemStack.EMPTY;
        if ("TPART".equals(parts[0])) {
            if (parts.length < 3) return ItemStack.EMPTY;
            ToolPartType shape = parseEnum(ToolPartType.class, parts[1]);
            BlockType mat = parseBlock(parts[2]);
            if (shape == null || mat == null) return ItemStack.EMPTY;
            return ItemStack.tinkersPart(new TinkersItem.Part(shape, mat));
        }
        if ("TTOOL".equals(parts[0])) {
            if (parts.length < 4) return ItemStack.EMPTY;
            Mining.ToolKind kind = parseEnum(Mining.ToolKind.class, parts[1]);
            int remaining = parseInt(parts[2], 0);
            if (kind == null) return ItemStack.EMPTY;
            List<TinkersItem.ToolLayer> layers = new ArrayList<>();
            for (String layer : parts[3].split("\\+")) {
                int slash = layer.indexOf('/');
                if (slash <= 0) continue;
                ToolPartType shape = parseEnum(ToolPartType.class, layer.substring(0, slash));
                BlockType mat = parseBlock(layer.substring(slash + 1));
                if (shape != null && mat != null) {
                    layers.add(new TinkersItem.ToolLayer(shape, mat));
                }
            }
            if (layers.isEmpty()) return ItemStack.EMPTY;
            ItemStack stack = ItemStack.tinkersTool(new TinkersItem.Tool(kind, layers));
            TinkersItem.Tool tool = stack.tinkersTool();
            if (tool != null) tool.setRemaining(remaining);
            return stack;
        }
        if (parts.length < 2) return ItemStack.EMPTY;
        BlockType type = parseBlock(parts[0]);
        int count = parseInt(parts[1], 0);
        return type == null ? ItemStack.EMPTY : ItemStack.of(type, count);
    }

    private static String fmt(float v) {
        return String.format(Locale.ROOT, "%.4f", v);
    }

    private static float parseFloat(String s, float fallback) {
        try {
            return Float.parseFloat(s);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static int parseInt(String s, int fallback) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static DimensionType parseDim(String s) {
        try {
            return DimensionType.valueOf(s);
        } catch (IllegalArgumentException e) {
            return DimensionType.OVERWORLD;
        }
    }

    private static BlockType parseBlock(String s) {
        try {
            return BlockType.valueOf(s);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static <E extends Enum<E>> E parseEnum(Class<E> type, String s) {
        try {
            return Enum.valueOf(type, s);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
