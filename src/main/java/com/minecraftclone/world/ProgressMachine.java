package com.minecraftclone.world;

/**
 * Shared view of a machine whose GUI shows a burn/heat bar and a progress
 * arrow (the Furnace today, steam machines later). Lets the HUD render the
 * same bars without caring which machine is behind the container.
 */
public interface ProgressMachine {

    /** 0..1 how much of the current heat source remains - drives the flame. */
    float burnFraction();

    /** 0..1 how far the current item is toward completion - drives the arrow. */
    float progressFraction();
}
