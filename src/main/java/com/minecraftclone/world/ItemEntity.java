package com.minecraftclone.world;

import com.minecraftclone.player.ItemStack;
import com.minecraftclone.world.tinkers.TinkersItem;
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
    /** Optional Tinkers' payload; null for vanilla drops. */
    public final TinkersItem tinkersItem;
    public final Vector3f position = new Vector3f(); // center of the item
    public final Vector3f velocity = new Vector3f();
    public float age;

    public ItemEntity(BlockType type, int count, float x, float y, float z) {
        this(type, count, x, y, z, null);
    }

    public ItemEntity(BlockType type, int count, float x, float y, float z, TinkersItem tinkersItem) {
        this.type = type;
        this.count = count;
        this.tinkersItem = tinkersItem;
        this.position.set(x, y, z);
    }

    /** Reconstructs the inventory stack this entity represents. */
    public ItemStack asStack() {
        if (tinkersItem instanceof TinkersItem.Part p) return ItemStack.tinkersPart(p);
        if (tinkersItem instanceof TinkersItem.Tool t) return ItemStack.tinkersTool(t);
        return ItemStack.of(type, count);
    }

    public boolean canPickup() {
        return age >= PICKUP_DELAY;
    }

    public boolean isExpired() {
        return age >= DESPAWN_SECONDS;
    }
}
