package com.minecraftclone.engine;

import com.minecraftclone.world.gen.TerrainGenerator.Biome;

import java.util.Random;

/**
 * The world's climate model: per-biome base temperature and humidity that vary
 * with the season, the time of day and the weather; a weather state machine
 * (clear / rain / snow) driven by the local climate and the time of year; and a
 * short rolled-ahead forecast so the game knows what weather is coming.
 * <p>
 * Rain pushes a global "wetness" up (which raises humidity everywhere and makes
 * further rain more likely - rainy spells feed themselves), dry weather lets it
 * drain. Precipitation falls as snow once the current temperature is at or
 * below freezing.
 */
public class Climate {

    /** A stretch of weather with its remaining duration (seconds) and intensity (0..1). */
    public record WeatherEvent(Weather weather, float durationSeconds, float strength) {
    }

    /** How many weather events are tracked: the current one plus the rolled-ahead forecast. */
    private static final int FORECAST_HORIZON = 3;

    /** How strongly rain raises a biome's humidity toward wet. */
    private static final float WETNESS_HUMIDITY_BOOST = 0.4f;
    private static final float WETNESS_RISE_PER_SECOND = 0.006f;
    private static final float WETNESS_DRAIN_PER_SECOND = 0.005f;
    /** Below this temperature (after season/day/weather) precipitation falls as snow. */
    private static final float FREEZING_C = 0f;

    private final Calendar calendar;
    private final DayNightCycle dayNightCycle;
    private final Random rnd;

    private final WeatherEvent[] schedule = new WeatherEvent[FORECAST_HORIZON];
    private Biome currentBiome = Biome.PLAINS;
    private float wetness = 0f;
    /** True once the schedule has been rolled from a real player biome (see {@link #update}). */
    private boolean rolled = false;

    public Climate(Calendar calendar, DayNightCycle dayNightCycle) {
        this(calendar, dayNightCycle, new Random());
    }

    public Climate(Calendar calendar, DayNightCycle dayNightCycle, Random rnd) {
        this.calendar = calendar;
        this.dayNightCycle = dayNightCycle;
        this.rnd = rnd;
        for (int i = 0; i < FORECAST_HORIZON; i++) {
            schedule[i] = new WeatherEvent(Weather.CLEAR, 0f, 0f);
        }
        // The schedule is deliberately not rolled here: the first update brings the
        // player's actual spawn biome, which is what the weather should reflect.
    }

    /** Advances the model: drifts wetness, counts down the weather, rolls ahead when it changes. */
    public void update(float dt, Biome playerBiome) {
        if (dt <= 0) return;
        currentBiome = playerBiome == null ? Biome.PLAINS : playerBiome;
        if (!rolled) {
            Weather preservedWeather = schedule[0].weather;
            float preservedDuration = schedule[0].durationSeconds;
            float preservedStrength = schedule[0].strength;
            boolean wasForced = preservedDuration > 0;
            rollWeather(0);
            rollWeather(1);
            rollWeather(2);
            if (wasForced) {
                schedule[0] = new WeatherEvent(preservedWeather, preservedDuration, preservedStrength);
            }
            rolled = true;
        }
        // Consume dt in slices bounded by each event's remaining duration, so a big
        // frame delta rolls through every expired event (and applies wetness with
        // each slice's own weather) instead of discarding the overflow.
        while (dt > 0) {
            WeatherEvent current = schedule[0];
            if (current.durationSeconds <= 0) {
                shiftAndRoll();
                continue;
            }
            float slice = Math.min(dt, current.durationSeconds);
            applyWetness(slice, current.weather);
            schedule[0] = new WeatherEvent(current.weather, current.durationSeconds - slice, current.strength);
            dt -= slice;
        }
    }

    private void shiftAndRoll() {
        for (int i = 0; i < FORECAST_HORIZON - 1; i++) {
            schedule[i] = schedule[i + 1];
        }
        rollWeather(FORECAST_HORIZON - 1);
    }

    private void applyWetness(float dt, Weather weather) {
        if (weather.isPrecipitation()) {
            wetness = Math.min(1f, wetness + dt * WETNESS_RISE_PER_SECOND);
        } else {
            wetness = Math.max(0f, wetness - dt * WETNESS_DRAIN_PER_SECOND);
        }
    }

    private void rollWeather(int index) {
        float humidity = humidityFor(currentBiome);
        float temperature = temperatureFor(currentBiome);
        float seasonBias = calendar.getSeason().precipitationBias;
        // Wet, humid biomes and wet seasons bring precipitation; it falls as snow
        // in freezing temperatures.
        float precipitationChance = Math.min(0.8f, 0.10f + humidity * 0.35f * (seasonBias * 2f));
        boolean wet = rnd.nextFloat() < precipitationChance;
        Weather weather = wet ? (temperature <= FREEZING_C ? Weather.SNOW : Weather.RAIN) : Weather.CLEAR;
        float duration = wet ? 45f + rnd.nextFloat() * 120f : 90f + rnd.nextFloat() * 240f;
        float strength = wet ? 0.3f + rnd.nextFloat() * 0.7f : 0f;
        schedule[index] = new WeatherEvent(weather, duration, strength);
    }

    /** The weather right now. */
    public Weather getWeather() {
        return current().weather;
    }

    /** Seconds until the current weather gives way to the next. */
    public float getWeatherTimeLeft() {
        return Math.max(0f, current().durationSeconds);
    }

    /** How heavy the current weather is, 0..1 (0 for clear). */
    public float getWeatherStrength() {
        return current().strength;
    }

    public boolean isPrecipitation() {
        return getWeather().isPrecipitation();
    }

    /** The next weather in the forecast (what arrives when the current one ends). */
    public Weather getNextWeather() {
        return schedule[1].weather;
    }

    /** The weather after next - the far end of the forecast. */
    public Weather getNextNextWeather() {
        return schedule[2].weather;
    }

    /** A copy of the full forecast: current + two upcoming events, in order. */
    public WeatherEvent[] getForecast() {
        WeatherEvent[] copy = new WeatherEvent[FORECAST_HORIZON];
        for (int i = 0; i < FORECAST_HORIZON; i++) {
            copy[i] = schedule[i];
        }
        return copy;
    }

    /** 0..1 how "wet" the world is right now - rain pushes it up, dry weather drains it. */
    public float getWetness() {
        return wetness;
    }

    /**
     * Current temperature in °C at {@code biome}: its base, plus the seasonal
     * offset, a dip at night, and cooling during precipitation.
     */
    public float temperatureFor(Biome biome) {
        float base = baseTemperature(biome);
        float seasonal = calendar.temperatureOffset();
        float nightly = -6f * (1f - dayNightCycle.getDaylightFactor());
        float weather = getWeather() == Weather.SNOW ? -7f : getWeather() == Weather.RAIN ? -5f : 0f;
        return base + seasonal + nightly + weather;
    }

    /**
     * Current humidity 0..1 at {@code biome}: its base, lifted toward wet by
     * rain and drained back during dry weather.
     */
    public float humidityFor(Biome biome) {
        return clamp01(baseHumidity(biome) + wetness * WETNESS_HUMIDITY_BOOST);
    }

    private WeatherEvent current() {
        return schedule[0];
    }

    /** Forces the current weather (used by tests). */
    void setWeather(Weather weather, float durationSeconds, float strength) {
        schedule[0] = new WeatherEvent(weather, durationSeconds, strength);
    }

    /** Forces a weather state with sensible duration/strength - used by the autotest/screenshots. */
    public void forceWeather(Weather weather) {
        if (weather == null) return;
        setWeather(weather, weather.isPrecipitation() ? 120f : 300f, weather.isPrecipitation() ? 0.8f : 0f);
    }

    /** A biome's baseline temperature in °C. */
    private static float baseTemperature(Biome b) {
        return switch (b) {
            case OCEAN -> 16f;
            case FROZEN_OCEAN -> -8f;
            case BEACH -> 22f;
            case PLAINS -> 20f;
            case FOREST -> 18f;
            case DESERT -> 34f;
            case SAVANNA -> 30f;
            case BADLANDS -> 32f;
            case JUNGLE -> 29f;
            case TAIGA -> 6f;
            case SNOWY -> -4f;
            case TUNDRA -> -10f;
            case SWAMP -> 21f;
            case MUSHROOM_FIELD -> 17f;
            case CHERRY_GROVE -> 15f;
            case FLOWER_MEADOW -> 18f;
            case MOUNTAIN -> 4f;
        };
    }

    /** A biome's baseline humidity, 0 (arid) to 1 (saturated). */
    private static float baseHumidity(Biome b) {
        return switch (b) {
            case OCEAN -> 0.90f;
            case FROZEN_OCEAN -> 0.90f;
            case BEACH -> 0.35f;
            case PLAINS -> 0.45f;
            case FOREST -> 0.70f;
            case DESERT -> 0.08f;
            case SAVANNA -> 0.30f;
            case BADLANDS -> 0.15f;
            case JUNGLE -> 0.95f;
            case TAIGA -> 0.65f;
            case SNOWY -> 0.55f;
            case TUNDRA -> 0.35f;
            case SWAMP -> 0.95f;
            case MUSHROOM_FIELD -> 0.85f;
            case CHERRY_GROVE -> 0.60f;
            case FLOWER_MEADOW -> 0.50f;
            case MOUNTAIN -> 0.50f;
        };
    }

    private static float clamp01(float v) {
        return Math.max(0f, Math.min(1f, v));
    }
}
