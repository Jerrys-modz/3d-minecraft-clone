package com.minecraftclone.world;

/**
 * Anything that can supply EU to a machine — a {@link CoalGeneratorEntity}
 * running on fuel or a {@link BatteryBlockEntity} drawing on stored charge.
 *
 * <p>The Electric Furnace (and any future EU-consuming machine) talks to its
 * power supply through this interface, found by scanning direct adjacency and
 * then the cable-network perimeter.  This keeps the machine code independent
 * of the concrete source type.
 */
public interface EuSource {

    /** EU currently available to draw. */
    float euStored();

    /**
     * Draws up to {@code amount} EU.
     * Returns the amount actually drawn (may be less if nearly empty).
     */
    float drainEU(float amount);
}
