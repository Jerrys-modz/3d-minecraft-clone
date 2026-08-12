#version 330 core

in vec2 fragUv;
in float fragLight;
in float fragBlockLight;
in float fragViewDistance;

uniform sampler2D atlas;
uniform vec3 fogColor;
uniform float fogStart;
uniform float fogEnd;
uniform float ambientBrightness; // day/night dimming, see DayNightCycle

out vec4 outColor;

void main() {
    vec4 texColor = texture(atlas, fragUv);
    if (texColor.a < 0.1) {
        discard;
    }

    // Local light sources (torches) set a brightness floor independent of
    // the day/night cycle, the same way Minecraft-style block light isn't
    // dimmed by the sky - a torch-lit wall stays lit at midnight.
    float ambient = max(ambientBrightness, fragBlockLight);
    vec3 shaded = texColor.rgb * fragLight * ambient;

    float fogFactor = clamp((fragViewDistance - fogStart) / (fogEnd - fogStart), 0.0, 1.0);
    vec3 finalColor = mix(shaded, fogColor, fogFactor);

    outColor = vec4(finalColor, texColor.a);
}
