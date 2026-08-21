package com.minecraftclone.player;

import com.minecraftclone.world.BlockType;
import com.minecraftclone.world.Mining;
import org.joml.Vector3i;

/**
 * Tracks hold-to-break progress on whatever block the player is currently
 * aiming at: call {@link #update} once per frame with the current aim
 * target and whether the break button is held. Aiming at a different block,
 * releasing the button, or losing the ability to break the target (wrong
 * tool, bedrock) all reset progress back to zero.
 * <p>
 * While a break is in progress this also pulses {@link #pollHit()} on a
 * fixed interval so the caller can play a material hit sound and swing the
 * hand - Minecraft-style punch ticks, not just a sound at the moment the
 * block disappears.
 */
public class MiningController {

    /** Seconds between in-progress hit sounds / punch swings. */
    public static final float HIT_INTERVAL_SECONDS = 0.25f;

    private Vector3i target;
    private float progress;
    private int lastHitPulse = -1;
    private boolean hitThisUpdate;

    /**
     * @param targetPos  world position of the block being aimed at, or null if not aiming at anything
     * @param targetType the block type at targetPos (ignored if targetPos is null)
     * @param heldItem   the currently selected hotbar item
     * @param holding    whether the break button is held down this frame
     * @param dt         seconds since the last update
     * @return the break fraction after this update; the caller should treat a result >= 1
     *         as "broken this frame" and call {@link #reset()} once it has actually removed the block
     */
    public float update(Vector3i targetPos, BlockType targetType, BlockType heldItem, boolean holding, float dt) {
        ItemStack stack = (heldItem == null) ? ItemStack.EMPTY : ItemStack.of(heldItem, 1);
        return update(targetPos, targetType, stack, holding, dt);
    }

    /**
     * ItemStack-aware mining update: Tinkers' tools use their payload tier/speed
     * ({@link Mining#canBreakItem} / {@link Mining#breakTimeItem}) instead of the
     * sentinel {@code TINKERS_TOOL} BlockType, which has no {@code TOOLS} entry.
     */
    public float update(Vector3i targetPos, BlockType targetType, ItemStack heldItem, boolean holding, float dt) {
        hitThisUpdate = false;
        if (!holding || targetPos == null || targetType == BlockType.BEDROCK
                || !Mining.canBreakItem(targetType, heldItem)) {
            reset();
            return 0f;
        }

        if (target == null || target.x != targetPos.x || target.y != targetPos.y || target.z != targetPos.z) {
            target = new Vector3i(targetPos);
            progress = 0f;
            lastHitPulse = -1;
        }

        progress += dt;
        float required = Mining.breakTimeItem(targetType, heldItem);
        if (required <= 0f) return 1f;
        float fraction = progress / required;
        if (fraction >= 1f) {
            return 1f;
        }
        int pulse = (int) (progress / HIT_INTERVAL_SECONDS);
        if (pulse != lastHitPulse) {
            lastHitPulse = pulse;
            hitThisUpdate = true;
        }
        return fraction;
    }

    /**
     * True once per hit pulse after {@link #update}. Consuming it so a missed
     * read doesn't replay the same punch next frame.
     */
    public boolean pollHit() {
        boolean hit = hitThisUpdate;
        hitThisUpdate = false;
        return hit;
    }

    public void reset() {
        target = null;
        progress = 0f;
        lastHitPulse = -1;
        hitThisUpdate = false;
    }
}
