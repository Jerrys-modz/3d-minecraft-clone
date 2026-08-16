package com.minecraftclone.engine;

/**
 * The in-game calendar: a running day counter (fed by the day/night cycle's
 * day index, see {@link DayNightCycle#getDayIndex()}) folded into seasons and
 * years, plus smooth per-season trait curves. Daylight fraction and temperature
 * blend from the current season toward the next across the season, so the
 * world's days lengthen and warm gradually instead of jumping on the first day
 * of a season.
 */
public class Calendar {

    /** Default days per season, used unless the world's settings say otherwise. */
    public static final int DAYS_PER_SEASON = 10;
    public static final int SEASONS_PER_YEAR = Season.values().length;

    private int daysPerSeason = DAYS_PER_SEASON;
    private int totalDay;

    /** The current day of the season, 1-based (1..{@link #getDaysPerSeason()}). */
    public int getDay() {
        return totalDay % daysPerSeason + 1;
    }

    /** The current season. */
    public Season getSeason() {
        return Season.values()[(totalDay / daysPerSeason) % SEASONS_PER_YEAR];
    }

    /** The current year, 1-based. */
    public int getYear() {
        return totalDay / getDaysPerYear() + 1;
    }

    /** 0..1 how far through the season we are - drives the smooth trait blending. */
    public float getSeasonProgress() {
        return (totalDay % daysPerSeason) / (float) daysPerSeason;
    }

    /** Total days elapsed (the raw counter). */
    public int getTotalDay() {
        return totalDay;
    }

    /** How many in-game days each season lasts (set from the world's settings). */
    public int getDaysPerSeason() {
        return daysPerSeason;
    }

    /** Days per full year (four seasons). */
    public int getDaysPerYear() {
        return daysPerSeason * SEASONS_PER_YEAR;
    }

    /** Sets the days per season (from the world's creation settings). */
    public void setDaysPerSeason(int days) {
        daysPerSeason = Math.max(1, days);
    }

    /** Resets to day 1 of spring, year 1 - call when a new world starts. */
    public void reset() {
        totalDay = 0;
    }

    /** Advances the calendar to the given total day count (from the day/night cycle). */
    public void update(int totalDay) {
        this.totalDay = totalDay;
    }

    /** Daylight fraction of the day/night cycle, blended toward the next season. */
    public float daylightFraction() {
        Season season = getSeason();
        return lerp(season.daylightFraction, season.next().daylightFraction, getSeasonProgress());
    }

    /** Seasonal temperature offset in degrees Celsius, blended toward the next season. */
    public float temperatureOffset() {
        Season season = getSeason();
        return lerp(season.temperatureOffset, season.next().temperatureOffset, getSeasonProgress());
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }
}
