package com.minecraftclone.engine;

import com.minecraftclone.world.gen.TerrainGenerator.Biome;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClimateTest {

    private static int seasonDays() {
        return new Calendar().getDaysPerSeason();
    }

    private static Climate climateAtDay(int totalDay) {
        Calendar calendar = new Calendar();
        calendar.update(totalDay);
        DayNightCycle cycle = new DayNightCycle();
        cycle.setTime(0.5f); // noon, so the nightly dip doesn't confuse temperature checks
        return new Climate(calendar, cycle);
    }

    private static Climate rolledClimate(int totalDay) {
        Climate climate = climateAtDay(totalDay);
        climate.update(0.001f, Biome.PLAINS); // triggers the initial forecast roll
        return climate;
    }

    @Test
    void humidityStartsFromTheBiomeBase() {
        Climate climate = climateAtDay(0);
        assertEquals(0.08f, climate.humidityFor(Biome.DESERT), 0.001f);
        assertEquals(0.95f, climate.humidityFor(Biome.JUNGLE), 0.001f);
        assertEquals(0.45f, climate.humidityFor(Biome.PLAINS), 0.001f);
    }

    @Test
    void rainRaisesHumidityAndClearWeatherDrainsIt() {
        Climate climate = rolledClimate(0);
        float before = climate.humidityFor(Biome.PLAINS);
        climate.forceWeather(Weather.RAIN);
        climate.update(50f, Biome.PLAINS);
        assertTrue(climate.humidityFor(Biome.PLAINS) > before + 0.05f, "rain should lift humidity");

        float rainy = climate.humidityFor(Biome.PLAINS);
        climate.forceWeather(Weather.CLEAR);
        climate.update(100f, Biome.PLAINS);
        assertTrue(climate.humidityFor(Biome.PLAINS) < rainy, "dry weather should drain humidity");
    }

    @Test
    void winterIsMuchColderThanSummerInTheSameBiome() {
        Climate summer = climateAtDay(seasonDays());     // first day of summer
        Climate winter = climateAtDay(3 * seasonDays()); // first day of winter
        float summerTemp = summer.temperatureFor(Biome.PLAINS);
        float winterTemp = winter.temperatureFor(Biome.PLAINS);
        assertTrue(summerTemp > winterTemp + 15f, "winter should be far colder, got " + summerTemp + " vs " + winterTemp);
    }

    @Test
    void desertStaysHotAndSnowyFreezesInWinter() {
        Climate summer = climateAtDay(seasonDays());
        assertTrue(summer.temperatureFor(Biome.DESERT) > 30f, "summer desert is hot");

        Climate winter = climateAtDay(3 * seasonDays());
        assertTrue(winter.temperatureFor(Biome.SNOWY) < 0f, "winter snowfield is below freezing");
    }

    @Test
    void rainCoolsComparedToClear() {
        Climate climate = climateAtDay(0);
        climate.forceWeather(Weather.CLEAR);
        float clear = climate.temperatureFor(Biome.PLAINS);
        climate.forceWeather(Weather.RAIN);
        float rainy = climate.temperatureFor(Biome.PLAINS);
        assertTrue(rainy < clear, "rain should cool the air");
    }

    @Test
    void liveWeatherComesFromTheSchedule() {
        Climate climate = rolledClimate(0);
        Weather live = climate.getWeather();
        assertNotNull(live);
        // The current hour's forecast is exact (now is known) - the climate runs at
        // noon in the tests, so today's hour 12 matches the live weather.
        assertEquals(live, climate.getHourlyForecast()[12].weather());
    }

    @Test
    void overcastReflectsTheWeather() {
        Climate climate = rolledClimate(0);
        climate.forceWeather(Weather.CLEAR);
        assertEquals(0f, climate.getOvercast(), 0.0001f);
        climate.forceWeather(Weather.FOG);
        assertTrue(climate.getOvercast() > 0f, "fog dims the sky");
        climate.forceWeather(Weather.RAIN);
        float rain = climate.getOvercast();
        assertTrue(rain > 0f);
        climate.forceWeather(Weather.THUNDERSTORM);
        assertTrue(climate.getOvercast() > rain, "thunderstorm is darker than plain rain");
        climate.forceWeather(Weather.BLIZZARD);
        assertTrue(climate.getOvercast() >= 0.8f, "blizzard nearly blacks out the sky");
    }

    @Test
    void severeWeatherIsHeavy() {
        Climate climate = rolledClimate(0);
        climate.forceWeather(Weather.THUNDERSTORM);
        assertEquals(Weather.THUNDERSTORM, climate.getWeather());
        assertTrue(climate.isPrecipitation());
        assertTrue(climate.getWeatherStrength() >= 0.75f);
        assertEquals(0f, climate.getFlashIntensity(), "no flash before any time passes");
    }

    @Test
    void hourlyAndDailyForecastsArePopulated() {
        Climate climate = rolledClimate(0);
        assertEquals(24, climate.getHourlyForecast().length);
        assertEquals(24, climate.getHourlyForecastForDay(0).length);
        assertEquals(24, climate.getHourlyForecastForDay(6).length);
        assertEquals(7, climate.getDailyForecast().length);
        assertTrue(climate.getCurrentHourOfDay() >= 0 && climate.getCurrentHourOfDay() < 24);
        for (Climate.ForecastSlot slot : climate.getDailyForecast()) {
            assertNotNull(slot.weather());
        }
    }

    @Test
    void forcingWeatherOverridesLive() {
        Climate climate = rolledClimate(0);
        climate.forceWeather(Weather.RAIN);
        assertEquals(Weather.RAIN, climate.getWeather());
        assertTrue(climate.isPrecipitation());
    }

    @Test
    void largeDeltaKeepsTheForecastValid() {
        Climate climate = rolledClimate(0);
        climate.update(100_000f, Biome.PLAINS);
        assertEquals(24, climate.getHourlyForecast().length);
        assertEquals(7, climate.getDailyForecast().length);
        assertTrue(climate.getWetness() <= 1f);
    }

    @Test
    void dailyForecastReflectsFutureCalendarDays() {
        Climate climate = rolledClimate(0);
        assertEquals(0, climate.getDailyDayIndex(0));
        assertEquals(6, climate.getDailyDayIndex(6));
    }

    @Test
    void precipitationClassificationUsesAmbientTemperatureNotCurrentWeather() {
        // Regression: rollHour must classify precipitation using ambient temperature
        // (forecastTemperature: base + season + nightly dip) rather than whatever the
        // "live" weather happens to be - so forcing the current weather must not
        // retroactively change the already-rolled hourly forecast for other hours.
        Calendar calendar = new Calendar();
        calendar.update(3 * calendar.getDaysPerSeason() + 10); // mid-winter
        DayNightCycle cycle = new DayNightCycle();
        cycle.setTime(0.5f); // noon
        Climate climate = new Climate(calendar, cycle);
        // BEACH (base 22°C) in winter is just above freezing (~2°C ambient at noon),
        // so its forecast should include both rain and snow hours over the day as the
        // nightly dip pushes some hours below freezing.
        climate.update(0.001f, Biome.BEACH); // rolls the initial hourly schedule

        Climate.ForecastSlot[] before = climate.getHourlyForecast();
        climate.forceWeather(Weather.SNOW); // -7°C live effect; must not affect the roll
        Climate.ForecastSlot[] after = climate.getHourlyForecast();
        for (int h = 0; h < before.length; h++) {
            assertEquals(before[h].weather(), after[h].weather(),
                    "forcing the live weather must not change the already-rolled forecast");
        }
    }

    // ------------------------------------------------------------------
    // Weather transitions (storms roll in/out over WEATHER_TRANSITION_SECONDS
    // instead of snapping the instant the schedule's hour changes) - see
    // Climate.stepWeatherTransition, exercised directly since forceWeather()
    // (used by every other test above) deliberately bypasses this smoothing
    // for instantaneous, deterministic screenshots/tests.
    // ------------------------------------------------------------------

    @Test
    void sameWeatherStrengthEasesTowardTheTargetByAtMostMaxDelta() {
        Climate.TransitionState next = Climate.stepWeatherTransition(
                Weather.RAIN, 0.2f, Weather.RAIN, 0.8f, 0.1f);
        assertEquals(Weather.RAIN, next.weather());
        assertEquals(0.3f, next.strength(), 0.0001f, "capped at maxDelta, doesn't jump straight to the target");
    }

    @Test
    void strengthSnapsToTheTargetOnceWithinMaxDelta() {
        Climate.TransitionState next = Climate.stepWeatherTransition(
                Weather.RAIN, 0.75f, Weather.RAIN, 0.8f, 0.5f);
        assertEquals(Weather.RAIN, next.weather());
        assertEquals(0.8f, next.strength(), 0.0001f, "doesn't overshoot past the target");
    }

    @Test
    void aDifferentIncomingWeatherFadesTheOutgoingOneOutFirst() {
        // Snow is arriving, but rain is still showing at 0.5 strength - it
        // should fade toward zero rather than cross-fading straight to snow.
        Climate.TransitionState next = Climate.stepWeatherTransition(
                Weather.RAIN, 0.5f, Weather.SNOW, 0.9f, 0.2f);
        assertEquals(Weather.RAIN, next.weather(), "still showing the outgoing weather");
        assertEquals(0.3f, next.strength(), 0.0001f);
    }

    @Test
    void weatherSwitchesOnlyOnceTheOutgoingStrengthHitsZero() {
        // Close enough to zero that this step reaches it exactly.
        Climate.TransitionState next = Climate.stepWeatherTransition(
                Weather.RAIN, 0.15f, Weather.SNOW, 0.9f, 0.2f);
        assertEquals(Weather.SNOW, next.weather(), "switches once the outgoing weather has fully faded");
        assertEquals(0f, next.strength(), 0.0001f, "the incoming weather starts from zero, to ramp up on a later step");
    }

    @Test
    void liveWeatherOnASchedulePrecipitatingRightAwayBuildsUpFromZero() {
        // A fresh climate always starts displaying CLEAR/0 - even if the very
        // first rolled hour turns out to already be precipitating, the very
        // first update() only has a tiny dt to work with, so strength must
        // still start at (or very near) zero rather than jumping straight to
        // that hour's full intensity.
        Climate climate = climateAtDay(0);
        climate.update(0.001f, Biome.PLAINS);
        assertTrue(climate.getWeatherStrength() < 0.05f,
                "strength ramps up from zero even when the first hour is already precipitating");
    }

    @Test
    void forcedWeatherIsInstantAndSkipsTheRampEntirely() {
        // forceWeather() is the autotest/screenshot-verification override -
        // it must stay instantaneous, or every prior MCCLONE_AUTOTEST_WEATHER
        // screenshot (which reads getOvercast()/getWeatherStrength() a few
        // frames after forcing) would show a still-ramping-up sky.
        Climate climate = rolledClimate(0);
        climate.forceWeather(Weather.THUNDERSTORM);
        assertEquals(Weather.THUNDERSTORM, climate.getWeather());
        assertTrue(climate.getWeatherStrength() >= 0.75f, "full strength immediately, no ramp");
    }

    // ------------------------------------------------------------------
    // Precipitation frequency (Climate.precipitationChance) - regression
    // coverage for "it rains too much": a wetter biome/season should still
    // roll higher, but a storm continuing an existing one must ignore both
    // entirely and just use STORM_CONTINUE_CHANCE, and no combination should
    // ever exceed the hard cap.
    // ------------------------------------------------------------------

    @Test
    void higherHumidityMeansMoreRain() {
        float desert = Climate.precipitationChance(0.08f, 0.45f, false);
        float plains = Climate.precipitationChance(0.45f, 0.45f, false);
        float jungle = Climate.precipitationChance(0.95f, 0.45f, false);
        assertTrue(desert < plains, "desert " + desert + " should roll less than plains " + plains);
        assertTrue(plains < jungle, "plains " + plains + " should roll less than jungle " + jungle);
    }

    @Test
    void aWetterSeasonRaisesTheFreshStartChance() {
        float dryOdds = Climate.precipitationChance(0.45f, Season.WINTER.precipitationBias, false);
        float wetOdds = Climate.precipitationChance(0.45f, Season.SPRING.precipitationBias, false);
        assertTrue(wetOdds > dryOdds, "spring " + wetOdds + " should roll more than winter " + dryOdds);
    }

    @Test
    void freshStartChanceNeverExceedsTheCap() {
        // Even the wettest biome (humidity 1) in the wettest possible season.
        float chance = Climate.precipitationChance(1f, Season.SPRING.precipitationBias, false);
        assertTrue(chance <= 0.25f, "capped regardless of how humid/wet the inputs are, got " + chance);
    }

    @Test
    void aContinuingStormIgnoresHumidityAndSeasonEntirely() {
        // Once a storm is already going, a bone-dry desert in winter keeps it
        // going at exactly the same odds as a rainforest in spring.
        float desertWinter = Climate.precipitationChance(0.08f, Season.WINTER.precipitationBias, true);
        float jungleSpring = Climate.precipitationChance(0.95f, Season.SPRING.precipitationBias, true);
        assertEquals(desertWinter, jungleSpring, 0.0001f);
        // And that shared value should be well above any fresh-start chance -
        // that's the whole point of storms clustering into multi-hour systems.
        float freshJungleSpring = Climate.precipitationChance(0.95f, Season.SPRING.precipitationBias, false);
        assertTrue(desertWinter > freshJungleSpring);
    }

    @Test
    void forcedTemperatureShortCircuitsTheNaturalClimate() {
        Climate climate = rolledClimate(0);
        float natural = climate.temperatureFor(Biome.PLAINS);
        climate.forceTemperature(-12f);
        assertEquals(-12f, climate.temperatureFor(Biome.PLAINS), 0.001f);
        assertEquals(-12f, climate.temperatureFor(Biome.DESERT), 0.001f);
        assertTrue(climate.hasForcedTemperature());
        climate.forceTemperature(null);
        assertEquals(natural, climate.temperatureFor(Biome.PLAINS), 0.001f);
        assertFalse(climate.hasForcedTemperature());
    }

    @Test
    void mountainsGetColderWithAltitude() {
        Climate climate = climateAtDay(0); // spring, noon
        float seaLevel = climate.temperatureFor(Biome.MOUNTAIN, 42f, 42f);
        float summit = climate.temperatureFor(Biome.MOUNTAIN, 100f, 100f);
        assertTrue(summit < seaLevel, "a mountain summit is colder than its base");
        assertTrue(seaLevel - summit > 5f, "the lapse is meaningful over ~60 blocks of altitude");
    }

    @Test
    void cavesAreWarmerThanAFrozenSurfaceInWinter() {
        // Mid-winter, noon: the surface is well below freezing...
        Climate winter = climateAtDay(3 * new Calendar().getDaysPerSeason() + 10);
        float surface = winter.temperatureFor(Biome.SNOWY, 42f, 42f);
        assertTrue(surface < 0f, "a snowy surface in winter should be frozen: " + surface);
        // ...but 12 blocks underground the temperature is stable at the biome's
        // baseline - far warmer than the frozen surface above.
        float cave = winter.temperatureFor(Biome.SNOWY, 30f, 42f);
        assertTrue(cave > surface + 10f, "a deep cave stays much warmer than the frozen surface");
        assertEquals(-4f, cave, 1f, "a snowy cave hovers around the biome's annual mean");
    }

    @Test
    void cavesAreCoolerThanAHotSurfaceInSummer() {
        Climate summer = climateAtDay(new Calendar().getDaysPerSeason() * 1 + 5); // mid-summer
        float surface = summer.temperatureFor(Biome.DESERT, 42f, 42f);
        float cave = summer.temperatureFor(Biome.DESERT, 30f, 42f);
        assertTrue(cave < surface, "a desert cave is cooler than the summer surface");
    }
}
