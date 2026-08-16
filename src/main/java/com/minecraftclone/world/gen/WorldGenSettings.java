package com.minecraftclone.world.gen;

import java.util.Random;

/**
 * Settings for a new world's terrain generation, edited from the main menu's
 * world-generation page (Minecraft-style "More World Options"): a seed, the
 * world type, whether structures (trees, cacti, ...) generate, the sea/water
 * level, and the terrain size. Persisted alongside the other settings as
 * {@code worldgen_*} lines so the next world keeps your choices.
 */
public class WorldGenSettings {

    public enum WorldType { DEFAULT, SUPERFLAT }

    public static final int ROW_NAME = 0;
    public static final int ROW_SEED = 1;
    public static final int ROW_WORLD_TYPE = 2;
    public static final int ROW_STRUCTURES = 3;
    public static final int ROW_SEA_LEVEL = 4;
    public static final int ROW_TERRAIN_SIZE = 5;
    public static final int ROW_WEEKS_PER_MONTH = 6;
    public static final int ROW_COUNT = 7;

    private static final int[] SEA_LEVELS = {34, 42, 50};
    private static final float[] TERRAIN_SIZES = {1f, 1.7f};
    private static final int[] WEEKS_PER_MONTH = {2, 3, 4};
    private static final String[] SEA_LEVEL_NAMES = {"Low", "Normal", "High"};
    private static final String[] TERRAIN_SIZE_NAMES = {"Normal", "Large"};
    private static final String[] WEEKS_PER_MONTH_NAMES = {"2 weeks", "3 weeks", "4 weeks"};

    private String name = "New World";
    private String seed = ""; // empty means a fresh random seed
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

    /** World type as its ordinal index (matches the persisted {@code worldgen_world_type} value). */
    public void setWorldType(int worldType) {
        this.worldType = Math.floorMod(worldType, WorldType.values().length);
    }

    public void setStructures(boolean structures) {
        this.structures = structures;
    }

    /** Sea level as its ordinal index (matches the persisted {@code worldgen_sea_level} value). */
    public void setSeaLevelIndex(int index) {
        this.seaLevelIndex = Math.floorMod(index, SEA_LEVELS.length);
    }

    /** Terrain size as its ordinal index (matches the persisted {@code worldgen_terrain_size} value). */
    public void setTerrainSizeIndex(int index) {
        this.terrainSizeIndex = Math.floorMod(index, TERRAIN_SIZES.length);
    }

    /** Weeks per month as its ordinal index (matches the persisted {@code worldgen_weeks_per_month} value). */
    public void setWeeksPerMonthIndex(int index) {
        this.weeksPerMonthIndex = Math.floorMod(index, WEEKS_PER_MONTH.length);
    }

    public boolean isSeedBlank() {
        return seed.isBlank();
    }

    /** The world type ordinal index (see {@link #setWorldType}). */
    public int getWorldTypeIndex() {
        return worldType;
    }

    public int getSeaLevelIndex() {
        return seaLevelIndex;
    }

    public int getTerrainSizeIndex() {
        return terrainSizeIndex;
    }

    public int getWeeksPerMonthIndex() {
        return weeksPerMonthIndex;
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
            case ROW_WORLD_TYPE -> worldType = Math.floorMod(worldType + Integer.signum(direction), WorldType.values().length);
            case ROW_STRUCTURES -> structures = !structures;
            case ROW_SEA_LEVEL -> seaLevelIndex = Math.floorMod(seaLevelIndex + Integer.signum(direction), SEA_LEVELS.length);
            case ROW_TERRAIN_SIZE -> terrainSizeIndex = Math.floorMod(terrainSizeIndex + Integer.signum(direction), TERRAIN_SIZES.length);
            case ROW_WEEKS_PER_MONTH -> weeksPerMonthIndex = Math.floorMod(weeksPerMonthIndex + Integer.signum(direction), WEEKS_PER_MONTH.length);
            default -> { /* seed is typed, not cycled */ }
        }
    }

    /** Appends the {@code worldgen_*} lines for persistence (see Settings.save). */
    public void saveLines(java.util.List<String> lines) {
        lines.add("worldgen_name=" + name);
        lines.add("worldgen_seed=" + seed);
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
