package com.minecraftclone.world.gen;

import com.minecraftclone.Difficulty;
import com.minecraftclone.GameMode;

import java.util.Random;

/**
 * Settings for a new world's terrain generation, edited from the main menu's
 * world-generation page (Minecraft-style "More World Options"): a seed, the
 * game mode, the difficulty, the world type, whether structures (trees, cacti, ...)
 * generate, the sea/water level, and the terrain size. Each world stores its
 * own copy in {@code world.txt} ({@code worldgen_*} lines). Create New World
 * always starts from a fresh {@link WorldGenSettings} with a newly rolled seed
 * — the last world's seed is never reused.
 */
public class WorldGenSettings {

    public enum WorldType { DEFAULT, SUPERFLAT }

    public static final int ROW_NAME = 0;
    public static final int ROW_SEED = 1;
    public static final int ROW_GAME_MODE = 2;
    public static final int ROW_DIFFICULTY = 3;
    public static final int ROW_WORLD_TYPE = 4;
    public static final int ROW_STRUCTURES = 5;
    public static final int ROW_SEA_LEVEL = 6;
    public static final int ROW_TERRAIN_SIZE = 7;
    public static final int ROW_WEEKS_PER_MONTH = 8;
    public static final int ROW_COUNT = 9;

    private static final int[] SEA_LEVELS = {34, 42, 50};
    private static final float[] TERRAIN_SIZES = {1f, 1.7f};
    private static final int[] WEEKS_PER_MONTH = {2, 3, 4};
    private static final String[] SEA_LEVEL_NAMES = {"Low", "Normal", "High"};
    private static final String[] TERRAIN_SIZE_NAMES = {"Normal", "Large"};
    private static final String[] WEEKS_PER_MONTH_NAMES = {"2 weeks", "3 weeks", "4 weeks"};

    private String name = "New World";
    private String seed = ""; // empty means a fresh random seed
    private int gameMode = 0; // GameMode ordinal; Survival by default
    private int difficulty = Difficulty.NORMAL.ordinal();
    private int worldType = 0; // WorldType ordinal
    private boolean structures = true;
    private int seaLevelIndex = 1; // Normal
    private int terrainSizeIndex = 0; // Normal
    private int weeksPerMonthIndex = 2; // 4 weeks

    public WorldGenSettings() {
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name == null || name.isBlank() ? "New World" : name.trim();
    }

    public String getSeedText() {
        return seed;
    }

    public void setSeedText(String seed) {
        this.seed = seed == null ? "" : seed.trim();
    }

    public boolean isSeedBlank() {
        return seed.isBlank();
    }

    /**
     * Fills the seed field with a newly generated random long so opening Create
     * New World never shows (or reuses) the previous world's seed.
     */
    public void rollFreshSeed() {
        seed = Long.toString(new Random().nextLong());
    }

    /**
     * The concrete world seed: a blank field gets a fresh random long, a numeric
     * field is used as-is, and any other text is hashed - Minecraft-style.
     */
    public long resolveSeed() {
        if (seed.isBlank()) {
            return new Random().nextLong();
        }
        try {
            return Long.parseLong(seed.trim());
        } catch (NumberFormatException e) {
            return seed.trim().hashCode();
        }
    }

    public GameMode getGameMode() {
        GameMode[] modes = GameMode.values();
        return modes[Math.max(0, Math.min(modes.length - 1, gameMode))];
    }

    public void setGameMode(GameMode mode) {
        this.gameMode = mode == null ? 0 : mode.ordinal();
    }

    public Difficulty getDifficulty() {
        Difficulty[] values = Difficulty.values();
        return values[Math.max(0, Math.min(values.length - 1, difficulty))];
    }

    public void setDifficulty(Difficulty value) {
        this.difficulty = value == null ? Difficulty.NORMAL.ordinal() : value.ordinal();
    }

    public WorldType getWorldType() {
        return WorldType.values()[worldType];
    }

    public boolean isSuperflat() {
        return worldType == WorldType.SUPERFLAT.ordinal();
    }

    public boolean hasStructures() {
        return structures;
    }

    /** The sea/water level in blocks (surfaces below this fill with water). */
    public int getSeaLevel() {
        return SEA_LEVELS[seaLevelIndex];
    }

    /** Terrain amplitude multiplier (1 = normal, larger = more extreme hills/mountains). */
    public float getTerrainSize() {
        return TERRAIN_SIZES[terrainSizeIndex];
    }

    /** How many weeks each month lasts (each week is 7 in-game days; see {@code engine.Calendar}). */
    public int getWeeksPerMonth() {
        return WEEKS_PER_MONTH[weeksPerMonthIndex];
    }

    public static String label(int row) {
        return switch (row) {
            case ROW_NAME -> "World name";
            case ROW_SEED -> "Seed";
            case ROW_GAME_MODE -> "Game mode";
            case ROW_DIFFICULTY -> "Difficulty";
            case ROW_WORLD_TYPE -> "World type";
            case ROW_STRUCTURES -> "Structures";
            case ROW_SEA_LEVEL -> "Sea level";
            case ROW_TERRAIN_SIZE -> "Terrain size";
            case ROW_WEEKS_PER_MONTH -> "Weeks per month";
            default -> "?";
        };
    }

    public String valueText(int row) {
        return switch (row) {
            case ROW_NAME -> name;
            case ROW_SEED -> seed.isBlank() ? "(random)" : seed;
            case ROW_GAME_MODE -> {
                String n = getGameMode().name();
                yield n.charAt(0) + n.substring(1).toLowerCase();
            }
            case ROW_DIFFICULTY -> getDifficulty().displayName();
            case ROW_WORLD_TYPE -> {
                String n = WorldType.values()[worldType].name();
                yield n.charAt(0) + n.substring(1).toLowerCase();
            }
            case ROW_STRUCTURES -> structures ? "ON" : "OFF";
            case ROW_SEA_LEVEL -> SEA_LEVEL_NAMES[seaLevelIndex];
            case ROW_TERRAIN_SIZE -> TERRAIN_SIZE_NAMES[terrainSizeIndex];
            case ROW_WEEKS_PER_MONTH -> WEEKS_PER_MONTH_NAMES[weeksPerMonthIndex];
            default -> "?";
        };
    }

    /** Cycles a discrete row forward (+1) or backward (-1); seed is edited via text input instead. */
    public void adjust(int row, int direction) {
        switch (row) {
            case ROW_GAME_MODE -> gameMode = Math.floorMod(gameMode + Integer.signum(direction), GameMode.values().length);
            case ROW_DIFFICULTY -> difficulty = Math.floorMod(difficulty + Integer.signum(direction), Difficulty.values().length);
            case ROW_WORLD_TYPE -> worldType = Math.floorMod(worldType + Integer.signum(direction), WorldType.values().length);
            case ROW_STRUCTURES -> structures = !structures;
            case ROW_SEA_LEVEL -> seaLevelIndex = Math.floorMod(seaLevelIndex + Integer.signum(direction), SEA_LEVELS.length);
            case ROW_TERRAIN_SIZE -> terrainSizeIndex = Math.floorMod(terrainSizeIndex + Integer.signum(direction), TERRAIN_SIZES.length);
            case ROW_WEEKS_PER_MONTH -> weeksPerMonthIndex = Math.floorMod(weeksPerMonthIndex + Integer.signum(direction), WEEKS_PER_MONTH.length);
            default -> { /* name/seed are typed, not cycled */ }
        }
    }

    /** Appends the {@code worldgen_*} lines written to that world's {@code world.txt}. */
    public void saveLines(java.util.List<String> lines) {
        lines.add("worldgen_name=" + name);
        lines.add("worldgen_seed=" + seed);
        lines.add("worldgen_game_mode=" + gameMode);
        lines.add("worldgen_difficulty=" + difficulty);
        lines.add("worldgen_world_type=" + worldType);
        lines.add("worldgen_structures=" + (structures ? 1 : 0));
        lines.add("worldgen_sea_level=" + seaLevelIndex);
        lines.add("worldgen_terrain_size=" + terrainSizeIndex);
        lines.add("worldgen_weeks_per_month=" + weeksPerMonthIndex);
    }

    /** Applies a persisted {@code worldgen_*} entry; unknown keys are ignored. */
    public void loadEntry(String fileKey, String value) {
        switch (fileKey) {
            case "worldgen_name" -> name = value.trim();
            case "worldgen_seed" -> seed = value.trim();
            case "worldgen_game_mode" -> gameMode = parseClamped(value, GameMode.values().length - 1);
            case "worldgen_difficulty" -> difficulty = parseClamped(value, Difficulty.values().length - 1);
            case "worldgen_world_type" -> worldType = parseClamped(value, WorldType.values().length - 1);
            case "worldgen_structures" -> structures = value.equals("1") || value.equalsIgnoreCase("true");
            case "worldgen_sea_level" -> seaLevelIndex = parseClamped(value, SEA_LEVELS.length - 1);
            case "worldgen_terrain_size" -> terrainSizeIndex = parseClamped(value, TERRAIN_SIZES.length - 1);
            case "worldgen_weeks_per_month" -> weeksPerMonthIndex = parseClamped(value, WEEKS_PER_MONTH.length - 1);
            default -> { /* ignore unknown keys (e.g. older days-per-season / months-per-season keys) */ }
        }
    }

    private static int parseClamped(String value, int max) {
        try {
            return Math.max(0, Math.min(max, Integer.parseInt(value.trim())));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
