package com.minecraftclone.util;

/** Axis-aligned bounding box, used for player-vs-world collision. */
public class AABB {
    public float minX, minY, minZ, maxX, maxY, maxZ;

    public AABB(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
        this.minX = minX;
        this.minY = minY;
        this.minZ = minZ;
        this.maxX = maxX;
        this.maxY = maxY;
        this.maxZ = maxZ;
    }

    public AABB offset(float dx, float dy, float dz) {
        return new AABB(minX + dx, minY + dy, minZ + dz, maxX + dx, maxY + dy, maxZ + dz);
    }

    public boolean intersects(AABB o) {
        return minX < o.maxX && maxX > o.minX
                && minY < o.maxY && maxY > o.minY
                && minZ < o.maxZ && maxZ > o.minZ;
    }
}
