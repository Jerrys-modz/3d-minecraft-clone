package com.minecraftclone.world;

/** Integer chunk-grid coordinates (chunk units, not block units). */
public record ChunkPos(int x, int z) {

    public double distanceSq(int ox, int oz) {
        double dx = x - ox;
        double dz = z - oz;
        return dx * dx + dz * dz;
    }
}
