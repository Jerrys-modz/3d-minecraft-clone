#version 330 core

in vec2 fragUv;
in float fragLight;
in float fragBlockLight;
in float fragFluidFlow;
in vec2 fragFlowDir;
in float fragViewDistance;

uniform sampler2D atlas;
uniform vec3 fogColor;
uniform float fogStart;
uniform float fogEnd;
uniform float ambientBrightness; // day/night dimming, see DayNightCycle
uniform float time;              // seconds, free-running - drives flowing-water/lava animation
uniform float atlasGrid;         // tiles per side of the atlas (see TextureAtlas.GRID)

out vec4 outColor;

void main() {
    vec2 uv = fragUv;
    if (fragFluidFlow > 0.5 && length(fragFlowDir) > 0.001) {
        // Scroll the sampled texel along the flow direction (per-cell, away from
        // the nearest source) within its own tile only - never past the tile's
        // own edges into a neighbor. Wrapping relative to the tile's origin keeps
        // this safe on a shared atlas where a naive GL_REPEAT scroll would bleed
        // into whatever tile sits beside it. Both the band pattern and the
        // per-column wave are periodic in the tile (see TextureAtlas.paintFluidTile),
        // so the scroll wraps with no seam in either direction.
        float tileSize = 1.0 / atlasGrid;
        vec2 tileUV0 = floor(fragUv / tileSize) * tileSize;
        vec2 local = fragUv - tileUV0;
        vec2 scrolled = mod(local - time * 0.35 * tileSize * fragFlowDir, tileSize);
        uv = tileUV0 + scrolled;
    }

    vec4 texColor = texture(atlas, uv);
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
