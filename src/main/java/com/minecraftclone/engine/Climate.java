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

    /** How strongly rain raises a biome's humidity toward wet. */
    private static final float WETNESS_HUMIDITY_BOOST = 0.4f;
    private static final float WETNESS_RISE_PER_SECOND = 0.006f;
    private static final float WETNESS_DRAIN_PER_SECOND = 0.005f;
    private static final float FREEZING_C = 0f;
    /** World height treated as "sea level" for the altitude lapse (the normal world-gen default). */
    private static final float SEA_LEVEL_REFERENCE = 42f;
    /** Blocks of altitude per -1°C - mountains get cold with height. */
    private static final float ALTITUDE_LAPSE_BLOCKS = 8f;
    /** Blocks below the surface before the temperature is fully the biome's stable ground temperature. */
    private static final float UNDERGROUND_BLEND_BLOCKS = 10f;
    /** The most unreliable the forecast gets (the far end of the 7-day range). */
    private static final float MAX_FORECAST_ERROR = 0.45f;
    /**
     * Chance an already-precipitating hour's storm continues into the next
     * one, instead of the schedule re-rolling from scratch every single
     * hour. Real storms last several hours, not one at a time with clear
     * skies possibly right before and after - rolling every hour
     * independently made the schedule flicker rain on and off constantly,
     * which read as "it rains all the time" even when the actual fraction of
     * precipitating hours was reasonable. 0.6 averages out to a storm
     * lasting about 2.5 hours.
     */
    private static final float STORM_CONTINUE_CHANCE = 0.6f;
    // Precipitation odds for an hour that starts out dry: a small base
    // chance plus a humidity-scaled term, weighted by how much the season
    // favors wet weather relative to a "neutral" mid-season bias (so
    // seasonBias's own 0.35-0.55 range only ever nudges the chance up or
    // down a bit, not multiplies it outright).
    // <p>
    // These are the odds of a *fresh* storm starting, not the overall
    // fraction of precipitating hours - STORM_CONTINUE_CHANCE compounds on
    // top of them (a Markov chain's steady state is
    // start / (start + (1 - continue)), so a ~2.5h average storm roughly
    // triples the fresh-start chance's own weight in the long run). Backed
    // out from that formula to land the *overall* precipitating fraction
    // around 4% for a desert, ~12% for plains, ~17-21% for a rainforest/
    // swamp in an average season - the previous formula (up to 0.85
    // chance/hour, every hour re-rolled independently) made rain the common
    // case rather than the exception almost everywhere.
    private static final float BASE_PRECIPITATION_CHANCE = 0.01f;
    private static final float HUMIDITY_PRECIPITATION_WEIGHT = 0.10f;
    private static final float NEUTRAL_SEASON_BIAS = 0.45f; // Season.AUTUMN's precipitationBias
    private static final float MAX_PRECIPITATION_CHANCE = 0.25f;
    /** Loosest a precipitating hour's strength can roll - see {@link #rollHour}. */
    private static final float MIN_STRENGTH = 0.15f;
    /** Chance per second that a thunderstorm throws a lightning flash. */
    private static final float FLASH_CHANCE_PER_SECOND = 0.06f;
    /** How long a lightning flash brightens the sky (seconds). */
    private static final float FLASH_DURATION = 0.3f;
    /**
     * How long, in real seconds, a storm takes to roll fully in (or clear
     * fully out) - see {@link #displayedWeather}/{@link #displayedStrength}.
     * The schedule itself still changes hour by hour (an in-game hour is
     * ~25 real seconds at the default day length), but what's actually
     * rendered/heard/felt eases toward that target instead of snapping to it,
     * so a storm visibly builds (or fades) rather than switching on like a
     * light. A different weather type first fades the outgoing one out
     * before the incoming one starts ramping up, rather than cross-fading
     * rain straight into snow.
     */
    private static final float WEATHER_TRANSITION_SECONDS = 20f;

    private final Calendar calendar;
    private final DayNightCycle dayNightCycle;
    private final Random rnd = new Random();

    /** The actual (live) weather, hour by hour, anchored at the current day's midnight. */
    private final Weather[] hourly = new Weather[FORECAST_HOURS];
    private final float[] strength = new float[FORECAST_HOURS];
    private Biome currentBiome = Biome.PLAINS;
    private float wetness = 0f;
    private Weather forcedWeather; // autotest override (null = schedule-driven)
    private Float forcedTemperature; // autotest override (null = natural climate)
    /** The absolute in-game hour that {@code hourly[0]} represents (always a day's midnight). */
    private int startHour = -1;
    /** Counts down a lightning flash during a thunderstorm; 0 = no flash. */
    private float flashTimer;
    /** What {@link #getWeather()} actually reports right now - eases toward the schedule's target, see {@link #advanceWeatherTransition}. */
    private Weather displayedWeather = Weather.CLEAR;
    /** What {@link #getWeatherStrength()} actually reports right now - eases toward the schedule's target. */
    private float displayedStrength = 0f;

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
        advanceWeatherTransition(dt);
        Weather live = getWeather();
        if (live.isPrecipitation()) {
            wetness = Math.min(1f, wetness + dt * WETNESS_RISE_PER_SECOND);
        } else {
            wetness = Math.max(0f, wetness - dt * WETNESS_DRAIN_PER_SECOND);
        }
        // Lightning flashes during thunderstorms.
        if (live == Weather.THUNDERSTORM) {
            if (flashTimer > 0f) {
                flashTimer = Math.max(0f, flashTimer - dt);
            } else if (rnd.nextFloat() < dt * FLASH_CHANCE_PER_SECOND) {
                flashTimer = FLASH_DURATION;
            }
        } else {
            flashTimer = 0f;
        }
    }

    /**
     * Rolls the live weather for the given absolute in-game hour, from the
     * local climate. A precipitating previous hour has a good chance of
     * simply continuing (see {@link #STORM_CONTINUE_CHANCE}) - real storms
     * span several hours, not one at a time with the schedule re-rolling
     * fresh odds every single hour - so precipitation clusters into a handful
     * of multi-hour systems instead of flickering on and off constantly.
     */
    private void rollHour(int index, int absoluteHour) {
        int dayIndex = Math.floorDiv(absoluteHour, HOURS_PER_DAY);
        int hourOfDay = Math.floorMod(absoluteHour, HOURS_PER_DAY);
        Random r = new Random(hash(absoluteHour * 1000003L + 7919L));
        float temperature = forecastTemperature(dayIndex, hourOfDay);
        float humidity = baseHumidity(currentBiome);
        float seasonBias = calendar.seasonAt(dayIndex).precipitationBias;
        boolean stormContinuing = index > 0 && hourly[index - 1].isPrecipitation();
        float precipitationChance = precipitationChance(humidity, seasonBias, stormContinuing);
        if (r.nextFloat() < precipitationChance) {
            // Precipitation: snow below freezing, otherwise rain - with a chance
            // of upgrading to the severe storm (blizzard / thunderstorm) form.
            boolean freezing = temperature <= FREEZING_C;
            if (freezing) {
                boolean blizzard = r.nextFloat() < 0.25f;
                hourly[index] = blizzard ? Weather.BLIZZARD : Weather.SNOW;
            } else {
                boolean thunder = r.nextFloat() < 0.25f;
                hourly[index] = thunder ? Weather.THUNDERSTORM : Weather.RAIN;
            }
            if (stormContinuing) {
                // Drift from the previous hour's strength (a storm gradually
                // building or tapering) rather than re-rolling independently,
                // so a multi-hour system reads as one coherent event instead
                // of a random walk between light and heavy every hour.
                float drift = (r.nextFloat() * 2f - 1f) * 0.25f;
                strength[index] = Math.max(MIN_STRENGTH, clamp01(strength[index - 1] + drift));
            } else {
                strength[index] = MIN_STRENGTH + r.nextFloat() * (1f - MIN_STRENGTH);
            }
            if (hourly[index].isSevere()) {
                strength[index] = Math.max(strength[index], 0.75f); // storms are always heavy
            }
        } else {
            // Dry: fog can roll in, more likely in humid biomes.
            boolean fog = r.nextFloat() < 0.05f + humidity * 0.08f;
            hourly[index] = fog ? Weather.FOG : Weather.CLEAR;
            strength[index] = 0f;
        }
    }

    /**
     * The odds a given hour precipitates - pulled out as a pure function for
     * direct testing (see {@link #rollHour}). {@code stormContinuing} short-
     * circuits straight to {@link #STORM_CONTINUE_CHANCE}, ignoring humidity/
     * season entirely: once a storm is already going, whether it keeps going
     * doesn't depend on the odds that started it.
     */
    static float precipitationChance(float humidity, float seasonBias, boolean stormContinuing) {
        if (stormContinuing) return STORM_CONTINUE_CHANCE;
        return Math.min(MAX_PRECIPITATION_CHANCE, BASE_PRECIPITATION_CHANCE
                + humidity * HUMIDITY_PRECIPITATION_WEIGHT * (seasonBias / NEUTRAL_SEASON_BIAS));
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

    /**
     * Eases {@link #displayedWeather}/{@link #displayedStrength} toward the
     * schedule's current-hour target by up to {@code dt / WEATHER_TRANSITION_SECONDS}.
     * While the displayed and target weather match, strength eases directly
     * toward the target (a storm building or a lull easing within the same
     * weather type). When they differ, the <em>displayed</em> weather's
     * strength eases toward zero first; only once it actually reaches zero
     * does the display switch to the new weather (at zero strength, to then
     * ramp up itself next frame) - so, say, rain visibly tapers off before
     * snow starts falling, instead of the two cross-fading into each other.
     */
    private void advanceWeatherTransition(float dt) {
        if (forcedWeather != null) return; // forced weather is instantaneous - see getWeather()/getWeatherStrength()
        Weather target = startHour < 0 ? Weather.CLEAR : hourly[currentHourOfDay()];
        float targetStrength = startHour < 0 ? 0f : strength[currentHourOfDay()];
        TransitionState next = stepWeatherTransition(displayedWeather, displayedStrength,
                target, targetStrength, dt / WEATHER_TRANSITION_SECONDS);
        displayedWeather = next.weather();
        displayedStrength = next.strength();
    }

    /** {@link #displayedWeather}/{@link #displayedStrength} after one transition step - see {@link #advanceWeatherTransition}. */
    record TransitionState(Weather weather, float strength) {
    }

    /**
     * One step of the weather-transition state machine, pulled out as a pure
     * function so it's directly testable without driving the whole schedule.
     * Strength eases toward {@code targetStrength} by up to {@code maxDelta}
     * while the displayed and target weather match; when they differ, the
     * displayed weather's strength eases toward zero first, and only once it
     * actually reaches zero does the displayed weather switch to the target
     * (at zero strength, left to ramp up on a later step).
     */
    static TransitionState stepWeatherTransition(Weather displayedWeather, float displayedStrength,
                                                  Weather target, float targetStrength, float maxDelta) {
        if (target == displayedWeather) {
            return new TransitionState(displayedWeather, moveToward(displayedStrength, targetStrength, maxDelta));
        }
        float eased = moveToward(displayedStrength, 0f, maxDelta);
        return eased <= 0f ? new TransitionState(target, 0f) : new TransitionState(displayedWeather, eased);
    }

    private static float moveToward(float current, float target, float maxDelta) {
        if (Math.abs(target - current) <= maxDelta) return target;
        return current + Math.signum(target - current) * maxDelta;
    }

    /** The weather right now (what is actually happening) - eases toward the schedule's target, see {@link #advanceWeatherTransition}. */
    public Weather getWeather() {
        if (forcedWeather != null) return forcedWeather;
        return startHour < 0 ? Weather.CLEAR : displayedWeather;
    }

    /** How heavy the current weather is, 0..1 (0 for clear) - eases toward the schedule's target, see {@link #advanceWeatherTransition}. */
    public float getWeatherStrength() {
        if (forcedWeather != null) return forcedWeather.isPrecipitation() ? 0.8f : 0f;
        return startHour < 0 ? 0f : displayedStrength;
    }

    public boolean isPrecipitation() {
        return getWeather().isPrecipitation();
    }

    /** 0..1 how "wet" the world is right now - rain pushes it up, dry weather drains it. */
    public float getWetness() {
        return wetness;
    }

    /**
     * 0..1 how overcast the sky is right now - clear is 0, fog is a dim haze,
     * rain/snow are heavy and the severe storms (thunderstorm, blizzard) nearly
     * black out the sky. Drives the sky darkening and the ambient light.
     */
    public float getOvercast() {
        return switch (getWeather()) {
            case CLEAR -> 0f;
            case FOG -> 0.5f;
            case RAIN -> 0.35f + 0.45f * getWeatherStrength();
            case SNOW -> 0.45f + 0.45f * getWeatherStrength();
            case THUNDERSTORM -> 0.7f + 0.25f * getWeatherStrength();
            case BLIZZARD -> 0.8f + 0.2f * getWeatherStrength();
        };
    }

    /** 0..1 how bright a lightning flash is right now (0 except during a thunderstorm). */
    public float getFlashIntensity() {
        return flashTimer > 0f ? flashTimer / FLASH_DURATION : 0f;
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
            Weather chosen = actual;
            while (chosen == actual) {
                chosen = Weather.values()[r.nextInt(Weather.values().length)];
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
        int[] counts = new int[Weather.values().length];
        for (int i = start; i < end; i++) {
            counts[hourly[i].ordinal()]++;
        }
        int best = Weather.CLEAR.ordinal();
        for (int i = 0; i < counts.length; i++) {
            if (counts[i] > counts[best]) best = i;
        }
        return Weather.values()[best];
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
     * offset, a dip at night, and cooling during precipitation. An autotest
     * override (see {@link #forceTemperature}) short-circuits all of that.
     */
    public float temperatureFor(Biome biome) {
        if (forcedTemperature != null) return forcedTemperature;
        float base = baseTemperature(biome);
        float seasonal = calendar.temperatureOffset();
        float nightly = -6f * (1f - dayNightCycle.getDaylightFactor());
        Weather w = getWeather();
        float weather = (w == Weather.SNOW || w == Weather.BLIZZARD) ? -7f : (w == Weather.RAIN || w == Weather.THUNDERSTORM) ? -5f : 0f;
        return base + seasonal + nightly + weather;
    }

    /**
     * The temperature at a specific position in the world, refining the surface
     * {@link #temperatureFor(Biome)} with two terrain effects:
     * <ul>
     *   <li><b>Altitude</b> - it gets colder the higher you climb above sea level
     *       (a mountain peak is much colder than its foothills), see
     *       {@link #ALTITUDE_LAPSE_BLOCKS}.</li>
     *   <li><b>Underground stability</b> - below the surface the temperature
     *       blends toward the biome's baseline (its annual mean, stripped of the
     *       season/night/weather swings), so caves barely change across the year:
     *       warmer than the frozen surface in winter, cooler than the hot surface
     *       in summer.</li>
     * </ul>
     *
     * @param y        the world Y of the position
     * @param surfaceY the Y of the terrain surface directly above it (its column's top)
     */
    public float temperatureFor(Biome biome, float y, float surfaceY) {
        if (forcedTemperature != null) return forcedTemperature;
        float airTemp = temperatureFor(biome)
                - Math.max(0f, (y - SEA_LEVEL_REFERENCE) / ALTITUDE_LAPSE_BLOCKS);
        float depth = clamp01((surfaceY - y) / UNDERGROUND_BLEND_BLOCKS);
        return lerp(airTemp, baseTemperature(biome), depth);
    }

    /**
     * Converts a local temperature in °C to a 0..1 cold-exposure factor.
     *
     * <ul>
     *   <li>≥ 2 °C → 0.0 (fully warm)</li>
     *   <li>−20 °C → 1.0 (maximum cold, clamped)</li>
     * </ul>
     *
     * <p>This is the single source of truth for the formula used in the game
     * loop and in tests.
     */
    public static float coldFactor(float temperatureCelsius) {
        return Math.max(0f, Math.min(1f, (2f - temperatureCelsius) / 22f));
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

    /** Forces a temperature override in °C (autotest/screenshot hook); null leaves the natural climate alone. */
    public void forceTemperature(Float temperatureCelsius) {
        this.forcedTemperature = temperatureCelsius;
    }

    /** True while a temperature override is active (autotest hook). */
    public boolean hasForcedTemperature() {
        return forcedTemperature != null;
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
            case NETHER -> 36f; // hot, hellish underworld
            case END -> 8f;     // cool, airy void
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
            case NETHER -> 0.05f; // arid, scorched underworld
            case END -> 0.40f;    // sparse, dry void
        };
    }

    private static float clamp01(float v) {
        return Math.max(0f, Math.min(1f, v));
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }
}
