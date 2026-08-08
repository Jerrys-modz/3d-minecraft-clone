package com.minecraftclone.util;

import com.minecraftclone.world.BlockAccessor;
import com.minecraftclone.world.BlockType;
import org.joml.Vector3f;
import org.joml.Vector3i;

/** Small-step ray march used for block picking (break/place reach). */
public final class Raycaster {

    private Raycaster() {
    }

    public static final class Hit {
        public final Vector3i blockPos;   // the solid block that was hit
        public final Vector3i placePos;   // the empty cell just before it, for placing a new block
        public final Vector3f point;

        Hit(Vector3i blockPos, Vector3i placePos, Vector3f point) {
            this.blockPos = blockPos;
            this.placePos = placePos;
            this.point = point;
        }
    }

    public static Hit cast(BlockAccessor world, Vector3f origin, Vector3f direction, float maxDistance) {
        float step = 0.02f;
        Vector3f dir = new Vector3f(direction).normalize();

        int prevX = Integer.MIN_VALUE, prevY = Integer.MIN_VALUE, prevZ = Integer.MIN_VALUE;
        for (float t = 0; t <= maxDistance; t += step) {
            float px = origin.x + dir.x * t;
            float py = origin.y + dir.y * t;
            float pz = origin.z + dir.z * t;

            int bx = (int) Math.floor(px);
            int by = (int) Math.floor(py);
            int bz = (int) Math.floor(pz);

            BlockType block = world.getBlock(bx, by, bz);
            if (block != BlockType.AIR && block != BlockType.WATER) {
                Vector3i placePos = (prevX == Integer.MIN_VALUE)
                        ? new Vector3i(bx, by, bz)
                        : new Vector3i(prevX, prevY, prevZ);
                return new Hit(new Vector3i(bx, by, bz), placePos, new Vector3f(px, py, pz));
            }
            prevX = bx;
            prevY = by;
            prevZ = bz;
        }
        return null;
    }
}
