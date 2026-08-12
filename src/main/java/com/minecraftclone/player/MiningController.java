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
 */
public class MiningController {

    private Vector3i target;
    private float progress;

    /**
     * @param targetPos  world position of the block being aimed at, or null if not aiming at anything
     * @param targetType the block type at targetPos (ignored if targetPos is null)
     * @param heldItem   the currently selected hotbar item
     * @param holding    whether the break button is held down this frame
     * @param dt         seconds since the last update
     * @return the break fraction after this update; the caller should treat a result &gt;= 1
     *         as "broken this frame" and call {@link #reset()} once it has actually removed the block
     */
    public float update(Vector3i targetPos, BlockType targetType, BlockType heldItem, boolean holding, float dt) {
        if (!holding || targetPos == null || targetType == BlockType.BEDROCK || !Mining.canBreak(targetType, heldItem)) {
            reset();
            return 0f;
        }

        if (target == null || target.x != targetPos.x || target.y != targetPos.y || target.z != targetPos.z) {
            target = new Vector3i(targetPos);
            progress = 0f;
        }

        progress += dt;
        float required = Mining.breakTimeSeconds(targetType, heldItem);
        if (required <= 0f) return 1f;
        return progress / required;
    }

    public void reset() {
        target = null;
        progress = 0f;
    }
}
