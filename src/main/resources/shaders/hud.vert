#version 330 core

layout (location = 0) in vec2 inPosition;
layout (location = 1) in vec2 inUv;

uniform mat4 transform;

out vec2 fragUv;

void main() {
    gl_Position = transform * vec4(inPosition, 0.0, 1.0);
    fragUv = inUv;
}
