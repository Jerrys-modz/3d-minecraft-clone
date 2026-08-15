package com.minecraftclone.engine;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CalendarTest {

    @Test
    void seasonsAdvanceEveryFixedNumberOfDays() {
        Calendar cal = new Calendar();
        cal.update(0);
        assertEquals(Season.SPRING, cal.getSeason());
        assertEquals(1, cal.getDay());
        assertEquals(1, cal.getYear());

        cal.update(Calendar.DAYS_PER_SEASON - 1); // last day of spring
        assertEquals(Season.SPRING, cal.getSeason());
        assertEquals(Calendar.DAYS_PER_SEASON, cal.getDay());

        cal.update(Calendar.DAYS_PER_SEASON); // first day of summer
        assertEquals(Season.SUMMER, cal.getSeason());
        assertEquals(1, cal.getDay());

        cal.update(3 * Calendar.DAYS_PER_SEASON); // first day of winter
        assertEquals(Season.WINTER, cal.getSeason());

        cal.update(4 * Calendar.DAYS_PER_SEASON); // spring again, year two
        assertEquals(Season.SPRING, cal.getSeason());
        assertEquals(2, cal.getYear());
    }

    @Test
    void yearCountsFourSeasons() {
        Calendar cal = new Calendar();
        cal.update(Calendar.DAYS_PER_YEAR - 1);
        assertEquals(1, cal.getYear());
        cal.update(Calendar.DAYS_PER_YEAR);
        assertEquals(2, cal.getYear());
    }

    @Test
    void seasonProgressIsZeroToAlmostOneWithinASeason() {
        Calendar cal = new Calendar();
        cal.update(0);
        assertEquals(0f, cal.getSeasonProgress(), 0.0001f);
        cal.update(Calendar.DAYS_PER_SEASON - 1);
        assertEquals(0.9f, cal.getSeasonProgress(), 0.0001f);
        cal.update(Calendar.DAYS_PER_SEASON);
        assertEquals(0f, cal.getSeasonProgress(), 0.0001f);
    }

    @Test
    void daylightAndTemperatureBlendTowardTheNextSeason() {
        Calendar cal = new Calendar();
        cal.update(0); // start of spring
        assertEquals(Season.SPRING.daylightFraction, cal.daylightFraction(), 0.0001f);
        assertEquals(Season.SPRING.temperatureOffset, cal.temperatureOffset(), 0.0001f);

        cal.update(Calendar.DAYS_PER_SEASON - 1); // deep into spring, trending to summer
        float blend = 0.9f;
        assertEquals(lerp(Season.SPRING.daylightFraction, Season.SUMMER.daylightFraction, blend),
                cal.daylightFraction(), 0.0001f);
        assertEquals(lerp(Season.SPRING.temperatureOffset, Season.SUMMER.temperatureOffset, blend),
                cal.temperatureOffset(), 0.0001f);
    }

    @Test
    void winterHasTheShortestDaylight() {
        Calendar cal = new Calendar();
        cal.update(3 * Calendar.DAYS_PER_SEASON);
        assertEquals(Season.WINTER, cal.getSeason());
        assertEquals(Season.WINTER.daylightFraction, cal.daylightFraction(), 0.0001f);
        assertEquals(0.38f, cal.daylightFraction(), 0.0001f);
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }
}
