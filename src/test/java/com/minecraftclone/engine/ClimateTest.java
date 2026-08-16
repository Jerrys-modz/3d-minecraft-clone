package com.minecraftclone.engine;

import com.minecraftclone.world.gen.TerrainGenerator.Biome;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
        assertTrue(live == Weather.CLEAR || live == Weather.RAIN || live == Weather.SNOW);
        // The current hour's forecast is exact (now is known) - the climate runs at
        // noon in the tests, so today's hour 12 matches the live weather.
        assertEquals(live, climate.getHourlyForecast()[12].weather());
    }

    @Test
    void hourlyAndDailyForecastsArePopulated() {
        Climate climate = rolledClimate(0);
        assertEquals(24, climate.getHourlyForecast().length);
        assertEquals(24, climate.getHourlyForecastForDay(0).length);
        assertEquals(24, climate.getHourlyForecastForDay(6).length);
        assertEquals(7, climate.getDailyForecast().length);
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
}
