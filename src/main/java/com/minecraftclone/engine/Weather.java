package com.minecraftclone.engine;

/**
 * The kind of weather currently over the world, or forecast to arrive. Snow is
 * simply rain that falls in freezing temperatures - the climate system picks
 * it from the current temperature rather than as a separate biome trait.
 */
public enum Weather {

    CLEAR("Clear"),
    RAIN("Rain"),
    SNOW("Snow");

    public final String displayName;

    Weather(String displayName) {
        this.displayName = displayName;
    }

    /** True for any falling precipitation (rain or snow). */
    public boolean isPrecipitation() {
        return this != CLEAR;
    }
}
