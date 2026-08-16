package com.minecraftclone.engine;

/**
 * The in-game calendar: a running day counter (fed by the day/night cycle's
 * day index, see {@link DayNightCycle#getDayIndex()}) folded into a full
 * day / week / month / season / year structure. A week is 7 days, a month
 * spans a configurable number of weeks (set from the world's creation
 * settings), a season is always 3 months, and a year is 4 seasons. The
 * day-of-week repeats every 7 days with a name; months carry
 * real-world-style names.
 * <p>
 * Daylight fraction and temperature blend from the current season toward the
 * next across the season, so the world's days lengthen and warm gradually
 * instead of jumping on the first day of a season.
 */
public class Calendar {

    public static final int DAYS_PER_WEEK = 7;
    public static final int MONTHS_PER_SEASON = 3;
    public static final int SEASONS_PER_YEAR = Season.values().length;
    /** Default weeks per month, used unless the world's settings say otherwise. */
    public static final int DEFAULT_WEEKS_PER_MONTH = 4;

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

    private int weeksPerMonth = DEFAULT_WEEKS_PER_MONTH;
    private int totalDay;

    /** The day of the week, 1-based (1 = Monday, 7 = Sunday). */
    public int getDayOfWeek() {
        return totalDay % DAYS_PER_WEEK + 1;
    }

    /** The day of the week's name, e.g. "Monday". */
    public String getDayOfWeekName() {
        return DAY_NAMES[totalDay % DAYS_PER_WEEK];
    }

    /** The day of the week's name for an arbitrary day count (used by the weather forecast). */
    public String dayOfWeekNameAt(int totalDay) {
        return DAY_NAMES[totalDay % DAYS_PER_WEEK];
    }

    /** The day of the month, 1-based (1..{@link #getDaysPerMonth()}). */
    public int getDayOfMonth() {
        return totalDay % getDaysPerMonth() + 1;
    }

    /** The week of the month, 1-based (1..{@link #getWeeksPerMonth()}). */
    public int getWeekOfMonth() {
        return (totalDay % getDaysPerMonth()) / DAYS_PER_WEEK + 1;
    }

    /** The day of the month, 1-based - a convenient alias for {@link #getDayOfMonth()}. */
    public int getDay() {
        return getDayOfMonth();
    }

    /** The month within the current season, 0-based (0..{@link #MONTHS_PER_SEASON}-1). */
    public int getMonthOfSeason() {
        return (totalDay % getDaysPerSeason()) / getDaysPerMonth();
    }

    /** The current month's name, e.g. "March". */
    public String getMonthName() {
        int monthOfYear = getSeason().ordinal() * MONTHS_PER_SEASON + getMonthOfSeason();
        return MONTH_NAMES[monthOfYear];
    }

    /** The current season. */
    public Season getSeason() {
        return seasonAt(totalDay);
    }

    /** The season for an arbitrary day count (used by the weather forecast). */
    public Season seasonAt(int totalDay) {
        return Season.values()[(totalDay / getDaysPerSeason()) % SEASONS_PER_YEAR];
    }

    /** 0..1 how far through the season we are - drives the smooth trait blending. */
    public float getSeasonProgress() {
        return seasonProgressAt(totalDay);
    }

    /** The current year, 1-based. */
    public int getYear() {
        return totalDay / getDaysPerYear() + 1;
    }

    /** Season progress (0..1) for an arbitrary day count. */
    public float seasonProgressAt(int totalDay) {
        return (totalDay % getDaysPerSeason()) / (float) getDaysPerSeason();
    }

    /** Total days elapsed (the raw counter). */
    public int getTotalDay() {
        return totalDay;
    }

    /** How many weeks each month lasts (set from the world's settings). */
    public int getWeeksPerMonth() {
        return weeksPerMonth;
    }

    /** Sets the weeks per month (from the world's creation settings). */
    public void setWeeksPerMonth(int weeks) {
        weeksPerMonth = Math.max(1, Math.min(8, weeks));
    }

    /** Days per month ({@code weeks-per-month} x 7). */
    public int getDaysPerMonth() {
        return weeksPerMonth * DAYS_PER_WEEK;
    }

    /** How many days each season lasts (3 months). */
    public int getDaysPerSeason() {
        return MONTHS_PER_SEASON * getDaysPerMonth();
    }

    /** Days per full year (four seasons). */
    public int getDaysPerYear() {
        return getDaysPerSeason() * SEASONS_PER_YEAR;
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
        return temperatureOffsetAt(totalDay);
    }

    /** Seasonal temperature offset for an arbitrary day count (used by the weather forecast). */
    public float temperatureOffsetAt(int totalDay) {
        Season season = seasonAt(totalDay);
        return lerp(season.temperatureOffset, season.next().temperatureOffset, seasonProgressAt(totalDay));
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }
}

