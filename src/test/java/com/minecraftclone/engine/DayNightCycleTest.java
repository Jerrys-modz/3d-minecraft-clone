package com.minecraftclone.engine;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DayNightCycleTest {

    @Test
    void defaultBalancedDayHasSunriseAndSunsetAtQuarterAndThreeQuarters() {
        DayNightCycle cycle = new DayNightCycle();
        assertEquals(0.25f, cycle.getSunriseTime(), 0.0001f);
        assertEquals(0.75f, cycle.getSunsetTime(), 0.0001f);
    }

    @Test
    void seasonalDaylightShiftsSunriseAndSunset() {
        DayNightCycle cycle = new DayNightCycle();
        cycle.setDaylightFraction(0.62f); // summer: long day
        assertEquals(0.19f, cycle.getSunriseTime(), 0.0001f);
        assertEquals(0.81f, cycle.getSunsetTime(), 0.0001f);

        cycle.setDaylightFraction(0.38f); // winter: short day
        assertEquals(0.31f, cycle.getSunriseTime(), 0.0001f);
        assertEquals(0.69f, cycle.getSunsetTime(), 0.0001f);
    }

    @Test
    void daylightIsFullAtNoonAndGoneInDeepNight() {
        DayNightCycle cycle = new DayNightCycle();
        cycle.setDaylightFraction(0.62f);
        cycle.setTime(0.5f); // noon
        assertEquals(1f, cycle.getDaylightFactor(), 0.0001f);
        cycle.setTime(0f);   // midnight
        assertEquals(0f, cycle.getDaylightFactor(), 0.0001f);
        cycle.setTime(0.1f); // before the summer sunrise (0.19)
        assertEquals(0f, cycle.getDaylightFactor(), 0.0001f);
    }

    @Test
    void sunIsOverheadAtNoon() {
        DayNightCycle cycle = new DayNightCycle();
        cycle.setDaylightFraction(0.5f);
        cycle.setTime(0.5f);
        float[] dir = {cycle.getSunDirection().x, cycle.getSunDirection().y, cycle.getSunDirection().z};
        assertTrue(dir[1] > 0.99f, "sun should be straight up at noon, got y=" + dir[1]);
        assertEquals(0f, dir[0], 0.01f);
    }

    @Test
    void sunDirectionIsContinuousAcrossMidnight() {
        DayNightCycle cycle = new DayNightCycle();
        cycle.setDaylightFraction(0.62f); // summer: long day, sun must still path through the night
        cycle.setTime(0.99f); // just before midnight
        float beforeX = cycle.getSunDirection().x;
        float beforeY = cycle.getSunDirection().y;
        cycle.setTime(0.01f); // just after midnight
        float afterX = cycle.getSunDirection().x;
        float afterY = cycle.getSunDirection().y;
        assertTrue(Math.abs(beforeX - afterX) < 0.2f, "sun should not jump in x at midnight");
        assertTrue(Math.abs(beforeY - afterY) < 0.2f, "sun should not jump in y at midnight");
        // At midnight the sun is straight below the horizon.
        cycle.setTime(0f);
        assertEquals(0f, cycle.getSunDirection().x, 0.02f);
        assertTrue(cycle.getSunDirection().y < -0.9f, "sun should be straight below at midnight");
    }

    @Test
    void dayCounterAdvancesOnMidnightWrap() {
        DayNightCycle cycle = new DayNightCycle();
        cycle.setTime(0.9999f);
        assertEquals(0, cycle.getDayIndex());
        cycle.update(1f); // a full second: 1/600 of the cycle, enough to cross midnight
        assertEquals(1, cycle.getDayIndex());
    }

    @Test
    void largeDeltaCountsEveryCompletedDay() {
        DayNightCycle cycle = new DayNightCycle();
        cycle.setTime(0.5f);
        cycle.update(2f * 600f); // two full day/night cycles in one frame
        assertEquals(2, cycle.getDayIndex());
        assertEquals(0.5f, cycle.getTime(), 0.0001f, "fractional remainder of the day is preserved");
        cycle.update(1200f); // two more
        assertEquals(4, cycle.getDayIndex());
    }
}
