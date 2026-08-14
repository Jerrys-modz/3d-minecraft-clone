package com.minecraftclone.world;

import org.joml.Vector3f;

/**
 * A dropped item: a block/entity sitting in the world after a block is broken
 * or the player dies, until the player walks over it and picks it up. Items
 * fall with gravity, rest on solid blocks, bob and spin in place, and despawn
 * after a while. They're transient (not saved) - a dropped item left behind is
 * simply gone next time the game loads.
 */
public class ItemEntity {

    public static final float DESPAWN_SECONDS = 300f; // 5 minutes, like Minecraft
    private static final float PICKUP_DELAY = 0.5f;   // brief grace period after spawning

    public final BlockType type;
    public int count;
    public final Vector3f position = new Vector3f(); // center of the item
    public final Vector3f velocity = new Vector3f();
    public float age;

    public ItemEntity(BlockType type, int count, float x, float y, float z) {
        this.type = type;
        this.count = count;
        this.position.set(x, y, z);
    }

    public boolean canPickup() {
        return age >= PICKUP_DELAY;
    }

    public boolean isExpired() {
        return age >= DESPAWN_SECONDS;
    }
}
