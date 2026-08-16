package com.minecraftclone.engine;

import com.minecraftclone.world.gen.TerrainGenerator.Biome;

import java.util.Random;

/**
 * The world's climate model. It drives two things:
 * <ul>
 *   <li><b>The actual weather</b> - a per-hour schedule covering the current day
 *       plus the next 7, rolled hour by hour as the calendar advances. It drives
 *       the rain/snow particles and the wetness that feeds humidity.</li>
 *   <li><b>The forecast</b> - a <i>prediction</i> shown to the player, derived
 *       from that schedule but deliberately imperfect: each predicted hour or day
 *       can be flipped to a different kind of weather, and the chance of that
 *       grows the further out it is (today is nearly exact, day 7 is a coin
 *       flip - like a real forecast). Predictions are seeded by absolute
 *       hour/day, so the forecast is stable within a day and refreshes as real
 *       time passes.</li>
 * </ul>
 * Per-biome base temperature and humidity vary with the season, the time of day
 * and the weather; precipitation falls as snow once the temperature is at or
 * below freezing.
 */
public class Climate {

    public static final int HOURS_PER_DAY = 24;
    public static final int FORECAST_DAYS = 7;
    /** Hours kept in the schedule: the current day plus the 7 future days. */
    public static final int BUFFER_DAYS = FORECAST_DAYS + 1;
    public static final int FORECAST_HOURS = BUFFER_DAYS * HOURS_PER_DAY;

    /** A single forecast slot: the predicted weather and its strength (0..1). */
    public record ForecastSlot(Weather weather, float strength) {
    }

    private static final float WETNESS_HUMIDITY_BOOST = 0.4f;
    private static final float WETNESS_RISE_PER_SECOND = 0.006f;
    private static final float WETNESS_DRAIN_PER_SECOND = 0.005f;
    private static final float FREEZING_C = 0f;
    /** The most unreliable the forecast gets (the far end of the 7-day range). */
    private static final float MAX_FORECAST_ERROR = 0.45f;

    private final Calendar calendar;
    private final DayNightCycle dayNightCycle;

    /** The actual (live) weather, hour by hour, anchored at the current day's midnight. */
    private final Weather[] hourly = new Weather[FORECAST_HOURS];
    private final float[] strength = new float[FORECAST_HOURS];
    private Biome currentBiome = Biome.PLAINS;
    private float wetness = 0f;
    private Weather forcedWeather; // autotest override (null = schedule-driven)
    /** The absolute in-game hour that {@code hourly[0]} represents (always a day's midnight). */
    private int startHour = -1;

    public Climate(Calendar calendar, DayNightCycle dayNightCycle) {
        this.calendar = calendar;
        this.dayNightCycle = dayNightCycle;
    }

    /** Advances the model: rolls the schedule forward as days pass and drifts wetness. */
    public void update(float dt, Biome playerBiome) {
        if (dt <= 0) return;
        currentBiome = playerBiome == null ? Biome.PLAINS : playerBiome;
        int dayStart = calendar.getTotalDay() * HOURS_PER_DAY;
        if (startHour < 0) {
            startHour = dayStart;
            for (int i = 0; i < FORECAST_HOURS; i++) {
                rollHour(i, startHour + i);
            }
        } else if (dayStart != startHour) {
            // The calendar advanced to a new day: shift the anchor forward (rolling
            // fresh hours at the far end) so the schedule stays a day-plus-7-days ahead.
            int shift = Math.min(FORECAST_HOURS, dayStart - startHour);
            for (int s = 0; s < shift; s++) {
                for (int i = 0; i < FORECAST_HOURS - 1; i++) {
                    hourly[i] = hourly[i + 1];
                    strength[i] = strength[i + 1];
                }
                startHour++;
                rollHour(FORECAST_HOURS - 1, startHour + FORECAST_HOURS - 1);
            }
        }
        Weather live = getWeather();
        if (live.isPrecipitation()) {
            wetness = Math.min(1f, wetness + dt * WETNESS_RISE_PER_SECOND);
        } else {
            wetness = Math.max(0f, wetness - dt * WETNESS_DRAIN_PER_SECOND);
        }
    }

    /** Rolls the live weather for the given absolute in-game hour, from the local climate. */
    private void rollHour(int index, int absoluteHour) {
        int dayIndex = Math.floorDiv(absoluteHour, HOURS_PER_DAY);
        int hourOfDay = Math.floorMod(absoluteHour, HOURS_PER_DAY);
        Random r = new Random(hash(absoluteHour * 1000003L + 7919L));
        float temperature = forecastTemperature(dayIndex, hourOfDay);
        float humidity = baseHumidity(currentBiome);
        float seasonBias = calendar.seasonAt(dayIndex).precipitationBias;
        float precipitationChance = Math.min(0.85f, 0.10f + humidity * 0.35f * (seasonBias * 2f));
        boolean wet = r.nextFloat() < precipitationChance;
        hourly[index] = wet ? (temperature <= FREEZING_C ? Weather.SNOW : Weather.RAIN) : Weather.CLEAR;
        strength[index] = wet ? 0.3f + r.nextFloat() * 0.7f : 0f;
    }

    /** The temperature a future hour would see (base + season + nightly dip, before weather). */
    private float forecastTemperature(int dayIndex, int hourOfDay) {
        return baseTemperature(currentBiome)
                + calendar.temperatureOffsetAt(dayIndex)
                - 6f * (1f - dayNightCycle.getDaylightFactorAt(hourOfDay / (float) HOURS_PER_DAY));
    }

    // ------------------------------------------------------------------
    // Live weather
    // ------------------------------------------------------------------

    private int currentHourOfDay() {
        return (int) (dayNightCycle.getTime() * HOURS_PER_DAY);
    }

    /** The weather right now (what is actually happening). */
    public Weather getWeather() {
        if (forcedWeather != null) return forcedWeather;
        return startHour < 0 ? Weather.CLEAR : hourly[currentHourOfDay()];
    }

    /** How heavy the current weather is, 0..1 (0 for clear). */
    public float getWeatherStrength() {
        if (forcedWeather != null) return forcedWeather.isPrecipitation() ? 0.8f : 0f;
        return startHour < 0 ? 0f : strength[currentHourOfDay()];
    }

    public boolean isPrecipitation() {
        return getWeather().isPrecipitation();
    }

    /** 0..1 how "wet" the world is right now - rain pushes it up, dry weather drains it. */
    public float getWetness() {
        return wetness;
    }

    /** The next weather the live schedule brings, and how many in-game hours away. */
    public Weather nextWeatherChange() {
        Weather current = getWeather();
        for (int i = currentHourOfDay() + 1; i < FORECAST_HOURS; i++) {
            if (hourly[i] != current) return hourly[i];
        }
        return current;
    }

    public int hoursUntilChange() {
        Weather current = getWeather();
        for (int i = currentHourOfDay() + 1; i < FORECAST_HOURS; i++) {
            if (hourly[i] != current) return i - currentHourOfDay();
        }
        return FORECAST_HOURS - currentHourOfDay();
    }

    // ------------------------------------------------------------------
    // Forecast (predictions, imperfect further out)
    // ------------------------------------------------------------------

    /** The predicted weather for each hour (0-23) of the current day. */
    public ForecastSlot[] getHourlyForecast() {
        return getHourlyForecastForDay(0);
    }

    /**
     * The predicted weather for each hour (0-23) of the calendar day {@code
     * calendar.getTotalDay() + dayOffset} (0 = today). Past hours of today are
     * exact; the error grows the further ahead an hour lies.
     */
    public ForecastSlot[] getHourlyForecastForDay(int dayOffset) {
        if (startHour < 0) return new ForecastSlot[0];
        ForecastSlot[] out = new ForecastSlot[HOURS_PER_DAY];
        int now = calendar.getTotalDay() * HOURS_PER_DAY + currentHourOfDay();
        for (int h = 0; h < HOURS_PER_DAY; h++) {
            int absoluteHour = (calendar.getTotalDay() + dayOffset) * HOURS_PER_DAY + h;
            int index = absoluteHour - startHour;
            Weather actual = index >= 0 && index < FORECAST_HOURS ? hourly[index] : Weather.CLEAR;
            float actualStrength = index >= 0 && index < FORECAST_HOURS ? strength[index] : 0f;
            out[h] = predict(actual, actualStrength,
                    hash(calendar.getTotalDay() * 1000003L + absoluteHour * 31L + 7L),
                    hourlyForecastError(Math.max(0, absoluteHour - now)));
        }
        return out;
    }

    /** The predicted weather for each of the next {@link #FORECAST_DAYS} days (index 0 = today). */
    public ForecastSlot[] getDailyForecast() {
        if (startHour < 0) return new ForecastSlot[0];
        ForecastSlot[] out = new ForecastSlot[FORECAST_DAYS];
        for (int d = 0; d < FORECAST_DAYS; d++) {
            int dayStart = (calendar.getTotalDay() + d) * HOURS_PER_DAY;
            int start = Math.max(startHour, dayStart);
            int end = Math.min(start + HOURS_PER_DAY, FORECAST_HOURS + startHour);
            int absoluteDay = calendar.getTotalDay() + d;
            out[d] = predict(dominantWeather(start - startHour, end - startHour),
                    peakStrength(start - startHour, end - startHour),
                    hash(calendar.getTotalDay() * 1000003L + absoluteDay * 31L + 11L),
                    dailyForecastError(d));
        }
        return out;
    }

    /** The in-game hour of day (0-23) right now - the forecast panel labels hours with it. */
    public int getCurrentHourOfDay() {
        return currentHourOfDay();
    }

    /** The absolute calendar day index that daily forecast slot {@code d} corresponds to (0 = today). */
    public int getDailyDayIndex(int d) {
        return calendar.getTotalDay() + d;
    }

    /**
     * Produces a forecast slot from the actual weather, flipping it to a
     * different kind of weather with probability {@code error} (deterministic
     * per slot, so the forecast is stable within a day).
     */
    private ForecastSlot predict(Weather actual, float actualStrength, long seed, float error) {
        Random r = new Random(seed);
        if (error > 0f && r.nextFloat() < error) {
            Weather[] others = {Weather.CLEAR, Weather.RAIN, Weather.SNOW};
            Weather chosen = actual;
            while (chosen == actual) {
                chosen = others[r.nextInt(others.length)];
            }
            return new ForecastSlot(chosen, chosen.isPrecipitation() ? 0.3f + r.nextFloat() * 0.7f : 0f);
        }
        return new ForecastSlot(actual, actualStrength);
    }

    /** How likely the hourly forecast is to be wrong for an hour {@code hoursAhead} (now is exact). */
    private static float hourlyForecastError(int hoursAhead) {
        return Math.min(MAX_FORECAST_ERROR, 0.02f * hoursAhead);
    }

    /** How likely the daily forecast is to be wrong for a day {@code daysAhead} (today is almost exact). */
    private static float dailyForecastError(int daysAhead) {
        return Math.min(MAX_FORECAST_ERROR, 0.05f + 0.06f * daysAhead);
    }

    private Weather dominantWeather(int start, int end) {
        int clear = 0, rain = 0, snow = 0;
        for (int i = start; i < end; i++) {
            switch (hourly[i]) {
                case CLEAR -> clear++;
                case RAIN -> rain++;
                case SNOW -> snow++;
            }
        }
        if (rain > clear && rain > snow) return Weather.RAIN;
        if (snow > clear && snow > rain) return Weather.SNOW;
        return Weather.CLEAR;
    }

    private float peakStrength(int start, int end) {
        float peak = 0f;
        for (int i = start; i < end; i++) {
            peak = Math.max(peak, strength[i]);
        }
        return peak;
    }

    private static long hash(long base) {
        long h = base * 0x9E3779B97F4A7C15L;
        h = (h ^ (h >>> 29)) * 0xBF58476D1CE4E5B9L;
        h = (h ^ (h >>> 32)) * 0x94D049BB133111EBL;
        return h ^ (h >>> 31);
    }

    // ------------------------------------------------------------------
    // Temperature & humidity
    // ------------------------------------------------------------------

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

    /** Forces a weather state (autotest/screenshot hook). */
    public void forceWeather(Weather weather) {
        this.forcedWeather = weather;
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
