package com.minecraftclone.engine;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CalendarTest {

    private static int seasonDays() {
        return new Calendar().getDaysPerSeason(); // default 1 month per season = 28 days
    }

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
    void monthsAdvanceEveryFourWeeks() {
        Calendar cal = new Calendar();
        cal.update(0);
        assertEquals(1, cal.getDayOfMonth());
        assertEquals(1, cal.getWeekOfMonth());

        cal.update(Calendar.DAYS_PER_MONTH - 1); // last day of the month
        assertEquals(Calendar.DAYS_PER_MONTH, cal.getDayOfMonth());
        assertEquals(Calendar.WEEKS_PER_MONTH, cal.getWeekOfMonth());

        cal.update(Calendar.DAYS_PER_MONTH); // first day of next month
        assertEquals(1, cal.getDayOfMonth());
        assertEquals(1, cal.getWeekOfMonth());
    }

    @Test
    void seasonsAdvanceAfterTheConfiguredMonths() {
        Calendar cal = new Calendar(); // 1 month per season
        int season = seasonDays();
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
        cal.update(0);
        assertEquals("March", cal.getMonthName());     // Spring
        cal.update(Calendar.DAYS_PER_MONTH);
        assertEquals("June", cal.getMonthName());      // Summer
        cal.update(3 * Calendar.DAYS_PER_MONTH);
        assertEquals("December", cal.getMonthName());  // Winter
    }

    @Test
    void customMonthsPerSeasonLengthensSeasons() {
        Calendar cal = new Calendar();
        cal.setMonthsPerSeason(2);
        assertEquals(2, cal.getMonthsPerSeason());
        assertEquals(2 * Calendar.DAYS_PER_MONTH, cal.getDaysPerSeason());

        cal.update(0);
        assertEquals("March", cal.getMonthName());      // Spring month 1
        cal.update(Calendar.DAYS_PER_MONTH);
        assertEquals("April", cal.getMonthName());      // Spring month 2
        cal.update(2 * Calendar.DAYS_PER_MONTH);        // two months of Spring done
        assertEquals(Season.SUMMER, cal.getSeason());
        cal.update(6 * Calendar.DAYS_PER_MONTH);        // six months: three seasons done
        assertEquals(Season.WINTER, cal.getSeason());
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
        assertEquals(0.964f, cal.getSeasonProgress(), 0.001f);
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
        cal.update(37);
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
