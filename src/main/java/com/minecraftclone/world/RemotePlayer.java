package com.minecraftclone.world;

import org.joml.Vector3f;

/**
 * A snapshot of another player connected to the same {@code GameServer} as
 * seen by this client: identity plus the last position/look the server
 * relayed. The rendered pose is smoothed toward the server-reported target
 * each frame (see {@link #tick}), so movement between the ~20 Hz state
 * updates the server sends appears fluid rather than stuttering.
 */
public class RemotePlayer {

    public final int id;
    public final String name;
    /** Rendered feet position (bottom-center), interpolated toward the target. */
    public final Vector3f position = new Vector3f();
    /** The raw position as last reported by the server. */
    public final Vector3f target = new Vector3f();
    /** Look angles in degrees as reported by the server (camera convention). */
    public float yawDegrees;
    public float pitchDegrees;
    /** Rendered look: yaw in the mob-renderer convention (radians, front = +Z). */
    public float renderYaw;
    public float renderPitchDegrees;
    public boolean onGround;
    public boolean flying;
    public boolean sprinting;

    public RemotePlayer(int id, String name) {
        this.id = id;
        this.name = name;
    }

    public void update(float x, float y, float z, float yaw, float pitch,
                       boolean onGround, boolean flying, boolean sprinting) {
        this.target.set(x, y, z);
        this.yawDegrees = yaw;
        this.pitchDegrees = pitch;
        this.onGround = onGround;
        this.flying = flying;
        this.sprinting = sprinting;
    }

    /** Smooths the rendered pose toward the server target; call once per frame. */
    public void tick(float dt) {
        float t = Math.min(1f, dt * 12f);
        position.lerp(target, t);
        float yaw = lerpDegrees(renderYawDegrees(), yawDegrees, t);
        // Store the rendered yaw in the mob-renderer convention (radians).
        this.renderYaw = (float) Math.toRadians(90f - yaw);
        this.renderPitchDegrees = pitchDegrees;
    }

    /** The currently-rendered yaw in degrees (tracked so yaw can lerp across the ±180 wrap). */
    private float renderYawDegrees() {
        return 90f - (float) Math.toDegrees(renderYaw);
    }

    private static float lerpDegrees(float a, float b, float t) {
        float delta = (b - a) % 360f;
        if (delta > 180f) delta -= 360f;
        if (delta < -180f) delta += 360f;
        return a + delta * t;
    }
}
