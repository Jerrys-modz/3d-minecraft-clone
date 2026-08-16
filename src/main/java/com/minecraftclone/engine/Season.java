package com.minecraftclone.engine;

/**
 * The four seasons of the in-game year. Each carries the traits the climate
 * and day/night systems read: how much warmer/colder it is than the annual
 * mean, how long the daylight half of the day/night cycle is (long days in
 * summer, short in winter), and how strongly it favours wet weather.
 */
public enum Season {

    SPRING("Spring", -4f, 0.50f, 0.55f),
    SUMMER("Summer", 10f, 0.62f, 0.40f),
    AUTUMN("Autumn", 0f, 0.50f, 0.45f),
    WINTER("Winter", -14f, 0.38f, 0.35f);

    /** Human-readable name for the HUD / debug overlay. */
    public final String displayName;
    /** Seasonal temperature shift in °C (blended between seasons by {@link Calendar}). */
    public final float temperatureOffset;
    /** Fraction of the 24h day/night cycle that is daylight - drives sunrise/sunset. */
    public final float daylightFraction;
    /** 0..1 tendency for this season to bring wet weather (used by the climate system). */
    public final float precipitationBias;

    Season(String displayName, float temperatureOffset, float daylightFraction, float precipitationBias) {
        this.displayName = displayName;
        this.temperatureOffset = temperatureOffset;
        this.daylightFraction = daylightFraction;
        this.precipitationBias = precipitationBias;
    }

    /** The season that follows this one. */
    public Season next() {
        Season[] values = values();
        return values[(ordinal() + 1) % values.length];
    }
}
