package com.minecraftclone.world;

/** A block entity that needs its position and world (to find tanks, power, deposits). */
public interface WorldAttached {
    void attach(int x, int y, int z, World world);
}
