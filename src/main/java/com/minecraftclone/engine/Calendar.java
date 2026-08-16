package com.minecraftclone.engine;

/**
 * The in-game calendar: a running day counter (fed by the day/night cycle's
 * day index, see {@link DayNightCycle#getDayIndex()}) folded into a full
 * day / week / month / season / year structure. A week is 7 days, a month is
 * 4 weeks (28 days), and a season spans a configurable number of months (1-3,
 * set from the world's creation settings). The day-of-week repeats every 7
 * days with a name; months carry real-world-style names.
 * <p>
 * Daylight fraction and temperature blend from the current season toward the
 * next across the season, so the world's days lengthen and warm gradually
 * instead of jumping on the first day of a season.
 */
public class Calendar {

    public static final int DAYS_PER_WEEK = 7;
    public static final int WEEKS_PER_MONTH = 4;
    public static final int DAYS_PER_MONTH = DAYS_PER_WEEK * WEEKS_PER_MONTH; // 28
    public static final int SEASONS_PER_YEAR = Season.values().length;
    /** Default months per season, used unless the world's settings say otherwise. */
    public static final int DEFAULT_MONTHS_PER_SEASON = 1;

    private static final String[] DAY_NAMES = {
            "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday",
    };
    /** Three named months per season, in season order (Spring first). */
    private static final String[] MONTH_NAMES = {
            "March", "April", "May",
            "June", "July", "August",
            "September", "October", "November",
            "December", "January", "February",
    };

    private int monthsPerSeason = DEFAULT_MONTHS_PER_SEASON;
    private int totalDay;

    /** The day of the week, 1-based (1 = Monday, 7 = Sunday). */
    public int getDayOfWeek() {
        return totalDay % DAYS_PER_WEEK + 1;
    }

    /** The day of the week's name, e.g. "Monday". */
    public String getDayOfWeekName() {
        return DAY_NAMES[totalDay % DAYS_PER_WEEK];
    }

    /** The day of the month, 1-based (1..{@link #DAYS_PER_MONTH}). */
    public int getDayOfMonth() {
        return totalDay % DAYS_PER_MONTH + 1;
    }

    /** The week of the month, 1-based (1..{@link #WEEKS_PER_MONTH}). */
    public int getWeekOfMonth() {
        return (totalDay % DAYS_PER_MONTH) / DAYS_PER_WEEK + 1;
    }

    /** The day of the month, 1-based - a convenient alias for {@link #getDayOfMonth()}. */
    public int getDay() {
        return getDayOfMonth();
    }

    /** The month within the current season, 0-based (0..months-per-season-1). */
    public int getMonthOfSeason() {
        return (totalDay % getDaysPerSeason()) / DAYS_PER_MONTH;
    }

    /** The current month's name, e.g. "March". */
    public String getMonthName() {
        int monthOfYear = getSeason().ordinal() * 3 + getMonthOfSeason();
        return MONTH_NAMES[monthOfYear];
    }

    /** The current season. */
    public Season getSeason() {
        return Season.values()[(totalDay / getDaysPerSeason()) % SEASONS_PER_YEAR];
    }

    /** The current year, 1-based. */
    public int getYear() {
        return totalDay / getDaysPerYear() + 1;
    }

    /** 0..1 how far through the season we are - drives the smooth trait blending. */
    public float getSeasonProgress() {
        return (totalDay % getDaysPerSeason()) / (float) getDaysPerSeason();
    }

    /** Total days elapsed (the raw counter). */
    public int getTotalDay() {
        return totalDay;
    }

    /** How many months each season lasts (set from the world's settings). */
    public int getMonthsPerSeason() {
        return monthsPerSeason;
    }

    /** Sets the months per season (from the world's creation settings). */
    public void setMonthsPerSeason(int months) {
        monthsPerSeason = Math.max(1, Math.min(3, months));
    }

    /** How many days each season lasts. */
    public int getDaysPerSeason() {
        return monthsPerSeason * DAYS_PER_MONTH;
    }

    /** Days per full year (four seasons). */
    public int getDaysPerYear() {
        return getDaysPerSeason() * SEASONS_PER_YEAR;
    }

    /** Days per month ({@link #DAYS_PER_MONTH}). */
    public int getDaysPerMonth() {
        return DAYS_PER_MONTH;
    }

    /** Resets to day 1 of the week/month, spring, year 1 - call when a new world starts. */
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
