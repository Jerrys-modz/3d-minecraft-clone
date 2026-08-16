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

        cal.update(cal.getDaysPerSeason() - 1); // last day of spring
        assertEquals(Season.SPRING, cal.getSeason());
        assertEquals(cal.getDaysPerSeason(), cal.getDay());

        cal.update(cal.getDaysPerSeason()); // first day of summer
        assertEquals(Season.SUMMER, cal.getSeason());
        assertEquals(1, cal.getDay());

        cal.update(3 * cal.getDaysPerSeason()); // first day of winter
        assertEquals(Season.WINTER, cal.getSeason());

        cal.update(4 * cal.getDaysPerSeason()); // spring again, year two
        assertEquals(Season.SPRING, cal.getSeason());
        assertEquals(2, cal.getYear());
    }

    @Test
    void yearCountsFourSeasons() {
        Calendar cal = new Calendar();
        cal.update(cal.getDaysPerYear() - 1);
        assertEquals(1, cal.getYear());
        cal.update(cal.getDaysPerYear());
        assertEquals(2, cal.getYear());
    }

    @Test
    void seasonProgressIsZeroToAlmostOneWithinASeason() {
        Calendar cal = new Calendar();
        cal.update(0);
        assertEquals(0f, cal.getSeasonProgress(), 0.0001f);
        cal.update(cal.getDaysPerSeason() - 1);
        assertEquals(0.9f, cal.getSeasonProgress(), 0.0001f);
        cal.update(cal.getDaysPerSeason());
        assertEquals(0f, cal.getSeasonProgress(), 0.0001f);
    }

    @Test
    void daylightAndTemperatureBlendTowardTheNextSeason() {
        Calendar cal = new Calendar();
        cal.update(0); // start of spring
        assertEquals(Season.SPRING.daylightFraction, cal.daylightFraction(), 0.0001f);
        assertEquals(Season.SPRING.temperatureOffset, cal.temperatureOffset(), 0.0001f);

        cal.update(cal.getDaysPerSeason() - 1); // deep into spring, trending to summer
        float blend = 0.9f;
        assertEquals(lerp(Season.SPRING.daylightFraction, Season.SUMMER.daylightFraction, blend),
                cal.daylightFraction(), 0.0001f);
        assertEquals(lerp(Season.SPRING.temperatureOffset, Season.SUMMER.temperatureOffset, blend),
                cal.temperatureOffset(), 0.0001f);
    }

    @Test
    void winterHasTheShortestDaylight() {
        Calendar cal = new Calendar();
        cal.update(3 * cal.getDaysPerSeason());
        assertEquals(Season.WINTER, cal.getSeason());
        assertEquals(Season.WINTER.daylightFraction, cal.daylightFraction(), 0.0001f);
        assertEquals(0.38f, cal.daylightFraction(), 0.0001f);
    }

    @Test
    void customDaysPerSeasonDrivesSeasonLength() {
        Calendar cal = new Calendar();
        cal.setDaysPerSeason(20);
        assertEquals(20, cal.getDaysPerSeason());
        cal.update(19);
        assertEquals(Season.SPRING, cal.getSeason());
        assertEquals(20, cal.getDay());
        cal.update(20);
        assertEquals(Season.SUMMER, cal.getSeason());
        assertEquals(1, cal.getDay());
        cal.update(80); // 4 seasons of 20
        assertEquals(2, cal.getYear());
    }

    @Test
    void resetReturnsToDayOneOfSpring() {
        Calendar cal = new Calendar();
        cal.update(37);
        assertEquals(Season.WINTER, cal.getSeason());
        cal.reset();
        assertEquals(0, cal.getTotalDay());
        assertEquals(Season.SPRING, cal.getSeason());
        assertEquals(1, cal.getDay());
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }
}
