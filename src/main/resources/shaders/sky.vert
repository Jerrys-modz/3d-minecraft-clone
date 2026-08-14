#version 330 core

// Fullscreen sky quad: generates a view-space ray per vertex (interpolated to
// fragments) using the inverse projection, so the fragment shader can do all
// the sky work in world space.

layout (location = 0) in vec2 inPosition; // NDC of the fullscreen quad

uniform mat4 invProj;

out vec3 fragRayView;

void main() {
    // Point on the far plane, in view space (w = 1 for the far plane).
    vec4 far = invProj * vec4(inPosition, 1.0, 1.0);
    fragRayView = far.xyz / far.w;
    // Pin the quad to the far plane so it always sits behind the world.
    gl_Position = vec4(inPosition, 1.0, 1.0);
}
