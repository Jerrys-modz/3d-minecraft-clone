package com.minecraftclone.util;

import com.minecraftclone.world.BlockAccessor;
import com.minecraftclone.world.BlockType;
import org.joml.Vector3f;
import org.joml.Vector3i;

/**
 * Voxel ray traversal (Amanatides & Woo DDA) used for block picking (break/place
 * reach). Water is treated as pass-through, so you can reach the block behind
 * it; every other non-air block stops the ray.
 */
public final class Raycaster {

    private Raycaster() {
    }

    public static final class Hit {
        public final Vector3i blockPos;   // the solid block that was hit
        public final Vector3i placePos;   // the empty cell just before it, for placing a new block
        public final Vector3f point;      // the exact hit point along the ray

        Hit(Vector3i blockPos, Vector3i placePos, Vector3f point) {
            this.blockPos = blockPos;
            this.placePos = placePos;
            this.point = point;
        }
    }

    public static Hit cast(BlockAccessor world, Vector3f origin, Vector3f direction, float maxDistance) {
        // Normalize the direction so t advances in world units.
        float dx = direction.x, dy = direction.y, dz = direction.z;
        float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len == 0f) return null;
        dx /= len;
        dy /= len;
        dz /= len;

        int x = (int) Math.floor(origin.x);
        int y = (int) Math.floor(origin.y);
        int z = (int) Math.floor(origin.z);

        BlockType start = world.getBlock(x, y, z);
        if (!start.isPassThrough()) {
            // Origin is already inside a solid block.
            return new Hit(new Vector3i(x, y, z), new Vector3i(x, y, z), new Vector3f(origin));
        }

        int stepX = dx > 0 ? 1 : -1;
        int stepY = dy > 0 ? 1 : -1;
        int stepZ = dz > 0 ? 1 : -1;

        // t at which the ray crosses the next voxel boundary on each axis.
        float tMaxX = dx != 0 ? ((stepX > 0 ? (x + 1 - origin.x) : (origin.x - x)) / Math.abs(dx)) : Float.POSITIVE_INFINITY;
        float tMaxY = dy != 0 ? ((stepY > 0 ? (y + 1 - origin.y) : (origin.y - y)) / Math.abs(dy)) : Float.POSITIVE_INFINITY;
        float tMaxZ = dz != 0 ? ((stepZ > 0 ? (z + 1 - origin.z) : (origin.z - z)) / Math.abs(dz)) : Float.POSITIVE_INFINITY;
        float tDeltaX = dx != 0 ? Math.abs(1f / dx) : Float.POSITIVE_INFINITY;
        float tDeltaY = dy != 0 ? Math.abs(1f / dy) : Float.POSITIVE_INFINITY;
        float tDeltaZ = dz != 0 ? Math.abs(1f / dz) : Float.POSITIVE_INFINITY;

        int prevX = x, prevY = y, prevZ = z;
        float t = 0f;
        // The path crosses at most ~3 * maxDistance voxel boundaries, so this bound
        // is far more than enough for any reach distance the game uses.
        for (int i = 0; i < 1024 && t <= maxDistance; i++) {
            prevX = x;
            prevY = y;
            prevZ = z;
            if (tMaxX < tMaxY && tMaxX < tMaxZ) {
                x += stepX;
                t = tMaxX;
                tMaxX += tDeltaX;
            } else if (tMaxY < tMaxZ) {
                y += stepY;
                t = tMaxY;
                tMaxY += tDeltaY;
            } else {
                z += stepZ;
                t = tMaxZ;
                tMaxZ += tDeltaZ;
            }
            if (t > maxDistance) break;

            BlockType block = world.getBlock(x, y, z);
            if (!block.isPassThrough()) {
                return new Hit(new Vector3i(x, y, z), new Vector3i(prevX, prevY, prevZ),
                        new Vector3f(origin.x + dx * t, origin.y + dy * t, origin.z + dz * t));
            }
        }
        return null;
    }
}
