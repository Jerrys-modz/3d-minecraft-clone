#version 330 core

layout (location = 0) in vec3 inPosition;
layout (location = 1) in vec2 inUv;
layout (location = 2) in float inLight;
layout (location = 3) in float inBlockLight;

uniform mat4 projection;
uniform mat4 view;

out vec2 fragUv;
out float fragLight;
out float fragBlockLight;
out float fragViewDistance;

void main() {
    vec4 viewPos = view * vec4(inPosition, 1.0);
    gl_Position = projection * viewPos;
    fragUv = inUv;
    fragLight = inLight;
    fragBlockLight = inBlockLight;
    fragViewDistance = length(viewPos.xyz);
}
