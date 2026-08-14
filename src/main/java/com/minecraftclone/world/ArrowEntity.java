package com.minecraftclone.world;

import org.joml.Vector3f;

/**
 * An arrow fired by a skeleton at the player. A lightweight projectile: it
 * flies with a little gravity, sticks in (and despawns from) the first solid
 * block it meets, and hurts the player if it hits them. Transient - not saved,
 * like dropped items.
 */
public class ArrowEntity {

    public static final float LIFETIME = 2.5f; // seconds before it vanishes mid-air

    public final Vector3f position = new Vector3f();
    public final Vector3f velocity = new Vector3f();
    public float age;
    public boolean stuck; // hit a solid block; stops moving and despawns

    public ArrowEntity(float x, float y, float z, float vx, float vy, float vz) {
        this.position.set(x, y, z);
        this.velocity.set(vx, vy, vz);
    }
}
