#version 330 core

// Procedural sky: a vertical gradient that follows the sun, with a sun and moon
// disc, a field of stars at night, and a simple band of fbm clouds near the
// horizon. Everything is computed per-fragment from the world-space view ray,
// so the sky is consistent no matter which way you look.

in vec3 fragRayView;

uniform mat4 invView;       // view inverse -> world space (rotation part used)
uniform vec3 sunDir;        // normalized world-space sun direction
uniform float daylight;     // 0 = night, 1 = noon
uniform float cloudTime;    // drifting cloud phase (noise units), advances with the clock
uniform float cloudDensity; // 0..1 cloud amount (0 = no clouds)
uniform float cloudSpeed;   // cloud drift speed multiplier
uniform float stars;        // 1 = stars on, 0 = off
uniform vec3 zenithColor;   // sky at the top of the sky
uniform vec3 horizonColor;  // sky at the horizon
uniform vec3 nightColor;    // night sky at the top
uniform vec3 sunColor;
uniform vec3 moonColor;

out vec4 outColor;

float hash(vec3 p) {
    p = fract(p * 0.1031);
    p += dot(p, p.zyx + 31.32);
    return fract((p.x + p.y) * p.z);
}

// 2D value noise for clouds.
float vnoise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    float a = hash(vec3(i, 0.0));
    float b = hash(vec3(i + vec2(1.0, 0.0), 0.0));
    float c = hash(vec3(i + vec2(0.0, 1.0), 0.0));
    float d = hash(vec3(i + vec2(1.0, 1.0), 0.0));
    return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
}

float fbm(vec2 p) {
    float v = 0.0;
    float amp = 0.5;
    for (int i = 0; i < 4; i++) {
        v += amp * vnoise(p);
        p *= 2.0;
        amp *= 0.5;
    }
    return v;
}

void main() {
    vec3 dir = normalize(mat3(invView) * fragRayView);

    // Vertical gradient: zenith color on top, horizon color at the horizon.
    float up = clamp(dir.y, 0.0, 1.0);
    vec3 daySky = mix(horizonColor, zenithColor, pow(up, 0.55));
    vec3 nightSky = mix(vec3(0.02, 0.02, 0.05), nightColor, pow(up, 0.5));
    vec3 sky = mix(nightSky, daySky, daylight);

    // Sun disc + warm glow, positioned by the day/night cycle.
    float sunDot = max(dot(dir, sunDir), 0.0);
    float sun = smoothstep(0.9994, 0.9998, sunDot) + pow(sunDot, 120.0) * 0.35;
    sky += sunColor * sun * (0.3 + 0.7 * daylight);

    // Moon, opposite the sun.
    float moonDot = max(dot(dir, -sunDir), 0.0);
    sky += moonColor * smoothstep(0.9994, 0.9997, moonDot) * (1.0 - daylight);

    // Stars: sparse hash grid, only above the horizon and fading out by day.
    if (stars > 0.5 && dir.y > 0.05) {
        vec3 cell = floor(dir * 500.0);
        float star = step(0.9986, hash(cell));
        star *= 1.0 - smoothstep(0.05, 0.55, daylight);
        sky += star * vec3(0.9, 0.95, 1.0);
    }

    // Clouds: an organic field of domain-warped fbm that drifts with cloudTime
    // and changes its amount of cover from day to day (per-day hash). Thick
    // centers shade bright white and thin edges soft gray for a puffy,
    // lit-from-above look, and clouds thin out toward the zenith. Density is a
    // settings-controlled threshold: 0 = off, higher = more/fuller clouds.
    if (daylight > 0.1 && cloudDensity > 0.0) {
        float fade = smoothstep(0.0, 0.04, dir.y) * (1.0 - smoothstep(0.5, 0.85, dir.y));
        if (fade > 0.0) {
            float dayIndex = floor(cloudTime / 30.0);
            float dayCover = hash(vec3(dayIndex, 0.0, 0.0));

            vec2 p = dir.xz * 6.0 + vec2(cloudTime * cloudSpeed, cloudTime * cloudSpeed * 0.55);
            vec2 q = vec2(fbm(p), fbm(p + vec2(7.3, 3.1)));
            float cloud = fbm(p + 1.1 * q);

            float threshold = 0.62 - 0.20 * cloudDensity + 0.12 * dayCover;
            float cover = smoothstep(threshold, threshold + 0.18, cloud) * fade * daylight;

            vec3 cloudColor = mix(vec3(0.62, 0.66, 0.71), vec3(1.0, 0.99, 0.97),
                                  smoothstep(0.32, 0.8, cloud));
            sky = mix(sky, cloudColor, cover * 0.85);
        }
    }

    outColor = vec4(sky, 1.0);
}
