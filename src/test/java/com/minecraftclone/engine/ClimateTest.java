package com.minecraftclone.engine;

import com.minecraftclone.world.gen.TerrainGenerator.Biome;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClimateTest {

    private static Climate climateAtDay(int totalDay) {
        Calendar calendar = new Calendar();
        calendar.update(totalDay);
        DayNightCycle cycle = new DayNightCycle();
        cycle.setTime(0.5f); // noon, so the nightly dip doesn't confuse temperature checks
        return new Climate(calendar, cycle);
    }

    @Test
    void humidityStartsFromTheBiomeBase() {
        Climate climate = climateAtDay(0);
        assertEquals(0.08f, climate.humidityFor(Biome.DESERT), 0.001f);
        assertEquals(0.95f, climate.humidityFor(Biome.JUNGLE), 0.001f);
        assertEquals(0.45f, climate.humidityFor(Biome.PLAINS), 0.001f);
    }

    /** A climate at the given calendar day whose initial schedule has already been rolled. */
    private static Climate rolledClimate(int totalDay) {
        Climate climate = climateAtDay(totalDay);
        climate.update(0.001f, Biome.PLAINS); // triggers the first roll, then stops
        return climate;
    }

    @Test
    void rainRaisesHumidityAndClearWeatherDrainsIt() {
        Climate climate = rolledClimate(0);
        float before = climate.humidityFor(Biome.PLAINS);
        climate.setWeather(Weather.RAIN, 100f, 1f);
        climate.update(50f, Biome.PLAINS);
        assertTrue(climate.humidityFor(Biome.PLAINS) > before + 0.05f, "rain should lift humidity");

        float rainy = climate.humidityFor(Biome.PLAINS);
        climate.setWeather(Weather.CLEAR, 200f, 0f);
        climate.update(100f, Biome.PLAINS);
        assertTrue(climate.humidityFor(Biome.PLAINS) < rainy, "dry weather should drain humidity");
    }

    @Test
    void winterIsMuchColderThanSummerInTheSameBiome() {
        Climate summer = climateAtDay(Calendar.DAYS_PER_SEASON);     // first day of summer
        Climate winter = climateAtDay(3 * Calendar.DAYS_PER_SEASON); // first day of winter
        float summerTemp = summer.temperatureFor(Biome.PLAINS);
        float winterTemp = winter.temperatureFor(Biome.PLAINS);
        assertTrue(summerTemp > winterTemp + 15f, "winter should be far colder, got " + summerTemp + " vs " + winterTemp);
    }

    @Test
    void desertStaysHotAndSnowyFreezesInWinter() {
        Climate summer = climateAtDay(Calendar.DAYS_PER_SEASON);
        assertTrue(summer.temperatureFor(Biome.DESERT) > 30f, "summer desert is hot");

        Climate winter = climateAtDay(3 * Calendar.DAYS_PER_SEASON);
        assertTrue(winter.temperatureFor(Biome.SNOWY) < 0f, "winter snowfield is below freezing");
    }

    @Test
    void rainCoolsComparedToClear() {
        Climate climate = rolledClimate(0);
        climate.setWeather(Weather.CLEAR, 200f, 0f);
        float clear = climate.temperatureFor(Biome.PLAINS);
        climate.setWeather(Weather.RAIN, 200f, 1f);
        float rainy = climate.temperatureFor(Biome.PLAINS);
        assertTrue(rainy < clear, "rain should cool the air");
    }

    @Test
    void forecastHoldsCurrentPlusTwoUpcomingEvents() {
        Climate climate = rolledClimate(0);
        Climate.WeatherEvent[] forecast = climate.getForecast();
        assertEquals(3, forecast.length);
        assertEquals(climate.getWeather(), forecast[0].weather());
        assertEquals(climate.getNextWeather(), forecast[1].weather());
        assertEquals(climate.getNextNextWeather(), forecast[2].weather());
        assertTrue(forecast[0].durationSeconds() > 0);
    }

    @Test
    void weatherEventuallyChangesAndShiftsTheForecast() {
        Climate climate = rolledClimate(0);
        climate.setWeather(Weather.RAIN, 0.5f, 1f);
        Weather wasNext = climate.getNextWeather(); // already rolled ahead before the rain expires
        climate.update(2f, Biome.PLAINS);
        // The short rain expired; the rolled-ahead next weather takes over.
        assertEquals(wasNext, climate.getWeather());
        assertTrue(climate.getWeatherTimeLeft() > 0);
    }

    @Test
    void scheduleIsOnlyRolledFromTheFirstRealBiome() {
        Climate climate = climateAtDay(0);
        // Before any update the schedule is still the empty placeholder (never rolled
        // against the default plains biome).
        assertEquals(0f, climate.getForecast()[0].durationSeconds(), 0.0001f);
        climate.update(1f, Biome.SNOWY);
        for (Climate.WeatherEvent event : climate.getForecast()) {
            assertTrue(event.durationSeconds() > 0, "every forecast event should be rolled from the spawn biome");
        }
    }

    @Test
    void largeDeltaRollsThroughEveryExpiredEvent() {
        Climate climate = climateAtDay(0);
        climate.setWeather(Weather.RAIN, 0.5f, 1f);
        climate.update(100_000f, Biome.PLAINS); // spans many weather events in one frame
        assertTrue(climate.getWeatherTimeLeft() > 0, "a fresh event should be running");
        assertTrue(climate.getWetness() <= 1f, "wetness stays bounded");
    }
}
