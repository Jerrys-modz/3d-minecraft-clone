package com.minecraftclone.engine;

import com.minecraftclone.util.FloatArray;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WeatherParticlesTest {

    @Test
    void rainSpawnsParticlesScaledByStrength() {
        WeatherParticles rain = new WeatherParticles(new Random(1));
        rain.update(0.1f, 0, 0, 0, Weather.RAIN, 0f);
        assertEquals(0, rain.getRainCount(), "no rain at zero strength");

        WeatherParticles light = new WeatherParticles(new Random(1));
        light.update(0.1f, 0, 0, 0, Weather.RAIN, 0.3f);
        WeatherParticles heavy = new WeatherParticles(new Random(1));
        heavy.update(0.1f, 0, 0, 0, Weather.RAIN, 1f);
        assertTrue(light.getRainCount() > 0);
        assertTrue(heavy.getRainCount() > light.getRainCount(), "heavier rain keeps more drops in the air");
        assertTrue(heavy.getRainCount() <= WeatherParticles.MAX_RAIN);
    }

    @Test
    void rainFallsDownwardOverTime() {
        WeatherParticles rain = new WeatherParticles(new Random(2));
        // Small deltas so drops survive below the kill plane long enough to observe the fall.
        rain.update(0.1f, 0, 0, 0, Weather.RAIN, 1f);
        int count = rain.getRainCount();
        assertTrue(count > 0);

        FloatArray a = new FloatArray();
        rain.writeRain(a);
        float firstHigh = maxY(a, count);
        rain.update(0.1f, 0, 0, 0, Weather.RAIN, 1f);
        FloatArray b = new FloatArray();
        rain.writeRain(b);
        assertTrue(maxY(b, count) < firstHigh, "drops fall as time passes");
    }

    @Test
    void rainExpiresBelowTheEyeAndStaysBounded() {
        WeatherParticles rain = new WeatherParticles(new Random(3));
        // Huge delta: everything falls past the kill plane, then respawns.
        rain.update(100_000f, 0, 0, 0, Weather.RAIN, 1f);
        assertTrue(rain.getRainCount() >= 0);
        assertTrue(rain.getRainCount() <= WeatherParticles.MAX_RAIN);

        // No particle may sit far below the eye.
        FloatArray v = new FloatArray();
        rain.writeRain(v);
        int n = rain.getRainCount();
        for (int i = 0; i < n; i++) {
            float y = v.get(i * 6 + 1);
            assertTrue(y >= -6f, "drops below the eye should have expired, got y=" + y);
        }
    }

    @Test
    void snowFallsDownwardAndStaysBounded() {
        WeatherParticles snow = new WeatherParticles(new Random(4));
        snow.update(0.2f, 0, 0, 0, Weather.SNOW, 1f);
        int count = snow.getSnowCount();
        assertTrue(count > 0);

        FloatArray a = new FloatArray();
        snow.writeSnow(a);
        float firstHigh = maxSnowY(a, count);
        snow.update(0.2f, 0, 0, 0, Weather.SNOW, 1f);
        FloatArray b = new FloatArray();
        snow.writeSnow(b);
        assertTrue(maxSnowY(b, count) < firstHigh, "snow falls as time passes");
        assertTrue(count <= WeatherParticles.MAX_SNOW);
    }

    @Test
    void clearWeatherAndSwitchingBothEmptyTheOtherPool() {
        WeatherParticles particles = new WeatherParticles(new Random(5));
        particles.update(1f, 0, 0, 0, Weather.RAIN, 1f);
        assertTrue(particles.getRainCount() > 0);

        particles.update(0.01f, 0, 0, 0, Weather.SNOW, 1f);
        assertEquals(0, particles.getRainCount(), "switching to snow drops the rain pool");

        particles.update(0.01f, 0, 0, 0, Weather.CLEAR, 0f);
        assertEquals(0, particles.getSnowCount(), "clear weather empties the snow pool");
    }

    @Test
    void vertexWritersProduceTheRightAmounts() {
        WeatherParticles rain = new WeatherParticles(new Random(6));
        rain.update(1f, 0, 0, 0, Weather.RAIN, 0.5f);
        FloatArray out = new FloatArray();
        int drops = rain.writeRain(out);
        assertEquals(drops * 6, out.size(), "two 3-float endpoints per raindrop");

        WeatherParticles snow = new WeatherParticles(new Random(6));
        snow.update(1f, 0, 0, 0, Weather.SNOW, 0.5f);
        FloatArray s = new FloatArray();
        int flakes = snow.writeSnow(s);
        assertEquals(flakes * 36, s.size(), "two crossed quads, 12 vertices (36 floats) per flake");
    }

    private static float maxY(FloatArray v, int drops) {
        float max = -Float.MAX_VALUE;
        for (int i = 0; i < drops; i++) {
            max = Math.max(max, v.get(i * 6 + 1));
        }
        return max;
    }

    private static float maxSnowY(FloatArray v, int flakes) {
        float max = -Float.MAX_VALUE;
        for (int i = 0; i < flakes; i++) {
            for (int k = 0; k < 12; k++) {
                max = Math.max(max, v.get(i * 36 + k * 3 + 1));
            }
        }
        return max;
    }
}
