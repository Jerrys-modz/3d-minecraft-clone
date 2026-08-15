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

    /** How many in-game days each season lasts. */
    public static final int DAYS_PER_SEASON = 10;
    public static final int SEASONS_PER_YEAR = Season.values().length;
    public static final int DAYS_PER_YEAR = DAYS_PER_SEASON * SEASONS_PER_YEAR;

    private int totalDay;

    /** The current day of the season, 1-based (1..{@link #DAYS_PER_SEASON}). */
    public int getDay() {
        return totalDay % DAYS_PER_SEASON + 1;
    }

    /** The current season. */
    public Season getSeason() {
        return Season.values()[(totalDay / DAYS_PER_SEASON) % SEASONS_PER_YEAR];
    }

    /** The current year, 1-based. */
    public int getYear() {
        return totalDay / DAYS_PER_YEAR + 1;
    }

    /** 0..1 how far through the season we are - drives the smooth trait blending. */
    public float getSeasonProgress() {
        return (totalDay % DAYS_PER_SEASON) / (float) DAYS_PER_SEASON;
    }

    /** Total days elapsed (the raw counter). */
    public int getTotalDay() {
        return totalDay;
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

    /** Seasonal temperature offset on a -1..1 scale, blended toward the next season. */
    public float temperatureOffset() {
        Season season = getSeason();
        return lerp(season.temperatureOffset, season.next().temperatureOffset, getSeasonProgress());
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }
}
