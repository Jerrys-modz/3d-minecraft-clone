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
    void forecastHoldsCurrentPlusUpcomingEvents() {
        Climate climate = rolledClimate(0);
        Climate.WeatherEvent[] forecast = climate.getForecast();
        assertEquals(5, forecast.length);
        assertEquals(climate.getWeather(), forecast[0].weather());
        assertEquals(climate.getNextWeather(), forecast[1].weather());
        assertTrue(forecast[0].durationSeconds() > 0);

        // Start times are cumulative: each upcoming event starts after the ones before it.
        float[] minutes = climate.getForecastStartMinutes();
        assertEquals(0f, minutes[0], 0.0001f);
        for (int i = 1; i < minutes.length; i++) {
            assertTrue(minutes[i] > minutes[i - 1], "event " + i + " starts after the previous one");
        }
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

    @Test
    void precipitationClassificationUsesAmbientTemperatureNotCurrentWeather() {
        // Regression: rollWeather must use ambient temperature (without the current
        // weather's cooling effect) to classify new precipitation as rain or snow,
        // so near-freezing conditions don't flip the classification based on whether
        // schedule[0] happens to be snowing right now.
        Calendar calendar = new Calendar();
        calendar.update(3 * Calendar.DAYS_PER_SEASON); // winter
        DayNightCycle cycle = new DayNightCycle();
        cycle.setTime(0.5f); // noon
        // A biome (TAIGA: base 6°C) that in winter at noon has ambient temp near
        // freezing: 6 + winter_offset ≈ 6 - 20 = -14°C, but could be around 0°C
        // depending on the day. We'll force snow weather with its -7°C effect:
        // if rollWeather used temperatureFor(TAIGA) it would see -14 - 7 = -21°C
        // and always produce snow; with ambient temperature it sees just the -14°C
        // and rolls correctly.
        Climate climate = new Climate(calendar, cycle, new java.util.Random(12345));
        climate.update(0.001f, Biome.TAIGA); // triggers first roll
        // Force current weather to be snow, which cools by 7°C.
        climate.setWeather(Weather.SNOW, 100f, 1f);
        // Now roll many new weather events. Some should be rain if the ambient
        // temperature is used (near 0°C in winter TAIGA at noon can be above
        // freezing without the snow effect), but all would be snow if the current
        // weather's -7°C was incorrectly included.
        int rainCount = 0;
        int snowCount = 0;
        for (int trial = 0; trial < 100; trial++) {
            // Pick a biome and day where ambient temp is just above freezing at noon.
            // MOUNTAIN (base 4°C) in early winter: 4°C + seasonal offset around -8°C
            // in early winter gives roughly -4°C ambient at noon, which is below
            // freezing. But BEACH (base 22°C) in winter: 22 - 20 = 2°C, above freezing.
            Calendar cal2 = new Calendar();
            cal2.update(3 * Calendar.DAYS_PER_SEASON + 10); // mid-winter
            DayNightCycle cycle2 = new DayNightCycle();
            cycle2.setTime(0.5f); // noon
            Climate climate2 = new Climate(cal2, cycle2, new java.util.Random(trial));
            climate2.update(0.001f, Biome.BEACH);
            climate2.setWeather(Weather.SNOW, 100f, 1f); // -7°C effect
            // Ambient temp for BEACH in mid-winter at noon is ~22 - 20 = 2°C (above 0).
            // With snow effect: 2 - 7 = -5°C (below 0).
            // Roll the next weather event multiple times to sample the randomness.
            for (int i = 0; i < 5; i++) {
                Climate.WeatherEvent[] forecast = climate2.getForecast();
                // Force a roll by advancing time past the current event.
                climate2.update(forecast[0].durationSeconds() + 1f, Biome.BEACH);
                Weather next = climate2.getWeather();
                if (next == Weather.RAIN) rainCount++;
                if (next == Weather.SNOW) snowCount++;
            }
        }
        // If ambient temperature is used correctly, we should see some rain (since
        // BEACH ambient is above freezing). If the current weather's cooling was
        // incorrectly included, we'd see only snow.
        assertTrue(rainCount > 0, "should see some rain when ambient temp is above freezing, got "
                + rainCount + " rain and " + snowCount + " snow");
    }
}
