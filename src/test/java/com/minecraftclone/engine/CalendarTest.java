package com.minecraftclone.engine;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CalendarTest {

    @Test
    void weeksAdvanceEverySevenDays() {
        Calendar cal = new Calendar();
        cal.update(0);
        assertEquals(1, cal.getDayOfWeek());
        assertEquals("Monday", cal.getDayOfWeekName());

        cal.update(6);
        assertEquals(7, cal.getDayOfWeek());
        assertEquals("Sunday", cal.getDayOfWeekName());

        cal.update(7);
        assertEquals(1, cal.getDayOfWeek(), "a new week starts on Monday");
        assertEquals("Monday", cal.getDayOfWeekName());
    }

    @Test
    void monthsAdvanceAfterTheConfiguredNumberOfWeeks() {
        Calendar cal = new Calendar(); // 4 weeks per month by default
        cal.update(0);
        assertEquals(1, cal.getDayOfMonth());
        assertEquals(1, cal.getWeekOfMonth());

        cal.update(cal.getDaysPerMonth() - 1); // last day of the month
        assertEquals(cal.getDaysPerMonth(), cal.getDayOfMonth());
        assertEquals(cal.getWeeksPerMonth(), cal.getWeekOfMonth());

        cal.update(cal.getDaysPerMonth()); // first day of next month
        assertEquals(1, cal.getDayOfMonth());
        assertEquals(1, cal.getWeekOfMonth());
    }

    @Test
    void seasonsAdvanceAfterThreeMonths() {
        Calendar cal = new Calendar();
        int season = cal.getDaysPerSeason(); // 3 months per season
        cal.update(0);
        assertEquals(Season.SPRING, cal.getSeason());
        assertEquals(1, cal.getDay());
        assertEquals(1, cal.getYear());

        cal.update(season); // first day of summer
        assertEquals(Season.SUMMER, cal.getSeason());
        assertEquals(1, cal.getDay());

        cal.update(3 * season); // first day of winter
        assertEquals(Season.WINTER, cal.getSeason());

        cal.update(4 * season); // spring again, year two
        assertEquals(Season.SPRING, cal.getSeason());
        assertEquals(2, cal.getYear());
    }

    @Test
    void monthNamesFollowTheSeasons() {
        Calendar cal = new Calendar();
        int month = cal.getDaysPerMonth();
        cal.update(0);
        assertEquals("March", cal.getMonthName());      // Spring month 1
        cal.update(month);
        assertEquals("April", cal.getMonthName());      // Spring month 2
        cal.update(3 * month);
        assertEquals("June", cal.getMonthName());       // Summer month 1
        cal.update(9 * month);
        assertEquals("December", cal.getMonthName());   // Winter month 1
    }

    @Test
    void customWeeksPerMonthLengthensMonthsAndSeasons() {
        Calendar cal = new Calendar();
        cal.setWeeksPerMonth(2);
        assertEquals(2, cal.getWeeksPerMonth());
        assertEquals(14, cal.getDaysPerMonth());
        assertEquals(42, cal.getDaysPerSeason());

        cal.update(0);
        assertEquals("March", cal.getMonthName());      // Spring month 1
        cal.update(14);
        assertEquals("April", cal.getMonthName());      // Spring month 2
        cal.update(42);
        assertEquals(Season.SUMMER, cal.getSeason());   // three Spring months done
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
        assertEquals((cal.getDaysPerSeason() - 1) / (float) cal.getDaysPerSeason(),
                cal.getSeasonProgress(), 0.0001f);
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
        float blend = (cal.getDaysPerSeason() - 1) / (float) cal.getDaysPerSeason();
        assertEquals(lerp(Season.SPRING.daylightFraction, Season.SUMMER.daylightFraction, blend),
                cal.daylightFraction(), 0.001f);
        assertEquals(lerp(Season.SPRING.temperatureOffset, Season.SUMMER.temperatureOffset, blend),
                cal.temperatureOffset(), 0.001f);
    }

    @Test
    void resetReturnsToDayOneOfSpring() {
        Calendar cal = new Calendar();
        cal.update(cal.getDaysPerSeason() + 10); // well into summer
        assertEquals(Season.SUMMER, cal.getSeason());
        cal.reset();
        assertEquals(0, cal.getTotalDay());
        assertEquals(Season.SPRING, cal.getSeason());
        assertEquals(1, cal.getDayOfWeek());
        assertEquals("Monday", cal.getDayOfWeekName());
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }
}
