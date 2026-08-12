package com.minecraftclone.engine;

import org.joml.Matrix4f;
import org.joml.Vector3f;

/** First-person camera: position + yaw/pitch, builds view/projection matrices. */
public class Camera {

    private final Vector3f position = new Vector3f();
    private float yaw = -90f;   // facing -Z by default
    private float pitch = 0f;

    private final Matrix4f viewMatrix = new Matrix4f();
    private final Matrix4f projectionMatrix = new Matrix4f();

    public void setPosition(float x, float y, float z) {
        position.set(x, y, z);
    }

    public Vector3f getPosition() {
        return position;
    }

    public void setYaw(float yaw) {
        this.yaw = yaw;
    }

    public void setPitch(float pitch) {
        this.pitch = pitch;
    }

    public float getYaw() {
        return yaw;
    }

    public float getPitch() {
        return pitch;
    }

    public void addRotation(float deltaYaw, float deltaPitch) {
        yaw += deltaYaw;
        pitch += deltaPitch;
        pitch = Math.max(-89.9f, Math.min(89.9f, pitch));
        yaw = yaw % 360f;
    }

    /** Forward direction (normalized), ignoring roll. */
    public Vector3f getFront() {
        float yawRad = (float) Math.toRadians(yaw);
        float pitchRad = (float) Math.toRadians(pitch);
        Vector3f front = new Vector3f(
                (float) (Math.cos(yawRad) * Math.cos(pitchRad)),
                (float) Math.sin(pitchRad),
                (float) (Math.sin(yawRad) * Math.cos(pitchRad))
        );
        return front.normalize();
    }

    /** Forward direction projected onto the horizontal plane (for walking). */
    public Vector3f getFrontFlat() {
        float yawRad = (float) Math.toRadians(yaw);
        Vector3f front = new Vector3f((float) Math.cos(yawRad), 0, (float) Math.sin(yawRad));
        return front.normalize();
    }

    public Vector3f getRight() {
        return getFront().cross(new Vector3f(0, 1, 0)).normalize();
    }

    public Matrix4f getViewMatrix() {
        Vector3f front = getFront();
        Vector3f center = new Vector3f(position).add(front);
        return viewMatrix.identity().lookAt(position, center, new Vector3f(0, 1, 0));
    }

    public Matrix4f getProjectionMatrix(float fovDegrees, float aspectRatio, float near, float far) {
        return projectionMatrix.identity().perspective((float) Math.toRadians(fovDegrees), aspectRatio, near, far);
    }
}
