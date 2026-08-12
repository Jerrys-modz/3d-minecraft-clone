#version 330 core

in vec2 fragUv;
in float fragLight;
in float fragViewDistance;

uniform sampler2D atlas;
uniform vec3 fogColor;
uniform float fogStart;
uniform float fogEnd;

out vec4 outColor;

void main() {
    vec4 texColor = texture(atlas, fragUv);
    if (texColor.a < 0.1) {
        discard;
    }

    vec3 shaded = texColor.rgb * fragLight;

    float fogFactor = clamp((fragViewDistance - fogStart) / (fogEnd - fogStart), 0.0, 1.0);
    vec3 finalColor = mix(shaded, fogColor, fogFactor);

    outColor = vec4(finalColor, texColor.a);
}
