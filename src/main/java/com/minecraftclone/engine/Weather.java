package com.minecraftclone.engine;

/**
 * The kind of weather over the world, or forecast to arrive. Snow falls in
 * freezing temperatures (picked by the climate system rather than as a separate
 * biome trait); thunderstorms and blizzards are the severe, heavy versions of
 * rain and snow, and fog is a calm, wet haze that dims the sky without rain.
 */
public enum Weather {

    CLEAR("Clear"),
    RAIN("Rain"),
    SNOW("Snow"),
    THUNDERSTORM("Thunderstorm"),
    BLIZZARD("Blizzard"),
    FOG("Fog");

    public final String displayName;

    Weather(String displayName) {
        this.displayName = displayName;
    }

    /** True for any weather with falling precipitation (rain, snow or their severe forms). */
    public boolean isPrecipitation() {
        return this == RAIN || this == SNOW || this == THUNDERSTORM || this == BLIZZARD;
    }

    /** True for the severe storm forms. */
    public boolean isSevere() {
        return this == THUNDERSTORM || this == BLIZZARD;
    }
}
